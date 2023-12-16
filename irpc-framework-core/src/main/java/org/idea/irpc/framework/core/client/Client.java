package org.idea.irpc.framework.core.client;

import com.alibaba.fastjson.JSON;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.idea.irpc.framework.core.common.*;
import org.idea.irpc.framework.core.common.config.ClientConfig;
import org.idea.irpc.framework.core.common.config.PropertiesBootstrap;
import org.idea.irpc.framework.core.common.event.IRpcListenerLoader;
import org.idea.irpc.framework.core.common.utils.CommonUtils;
import org.idea.irpc.framework.core.proxy.javassist.JavassistProxyFactory;
import org.idea.irpc.framework.core.proxy.jdk.JDKProxyFactory;
import org.idea.irpc.framework.core.registy.URL;
import org.idea.irpc.framework.core.registy.zookeeper.AbstractRegister;
import org.idea.irpc.framework.core.registy.zookeeper.ZookeeperRegister;
import org.idea.irpc.framework.core.server.DataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.idea.irpc.framework.core.common.cache.CommonClientCache.*;

/**
 * @Author linhao
 * @Date created in 8:22 上午 2021/11/29
 */
public class Client {

    private Logger logger = LoggerFactory.getLogger(Client.class);

    public static EventLoopGroup clientGroup = new NioEventLoopGroup();

    private ClientConfig clientConfig;

    private AbstractRegister abstractRegister;

    private IRpcListenerLoader iRpcListenerLoader;

    private Bootstrap bootstrap = new Bootstrap();

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public ClientConfig getClientConfig() {
        return clientConfig;
    }


    public void setClientConfig(ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public RpcReference initClientApplication() {
        EventLoopGroup clientGroup = new NioEventLoopGroup();
        bootstrap.group(clientGroup);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new RpcEncoder());
                ch.pipeline().addLast(new RpcDecoder());
                ch.pipeline().addLast(new ClientHandler());
            }
        });
        iRpcListenerLoader = new IRpcListenerLoader();
        // 初始化 zk节点update listener
        // 这个listener会当server服务方地址发生变化时 及时更新client本地缓存
        iRpcListenerLoader.init();
        this.clientConfig = PropertiesBootstrap.loadClientConfigFromLocal();
        RpcReference rpcReference;
        if ("javassist".equals(clientConfig.getProxyType())) {
            rpcReference = new RpcReference(new JavassistProxyFactory());
        } else {
            rpcReference = new RpcReference(new JDKProxyFactory());
        }
        return rpcReference;
    }

    /**
     * 启动服务之前需要预先订阅对应的dubbo服务
     *
     * @param serviceBean
     */
    public void doSubscribeService(Class serviceBean) {
        if (abstractRegister == null) {
            // 创建与zk通信的工具类
            abstractRegister = new ZookeeperRegister(clientConfig.getRegisterAddr());
        }
        URL url = new URL();
        url.setApplicationName(clientConfig.getApplicationName());
        url.setServiceName(serviceBean.getName());
        url.addParameter("host", CommonUtils.getIpAddress());
        abstractRegister.subscribe(url);
    }

    /**
     * 开始和各个provider建立连接
     */
    public void doConnectServer() {
        for (String providerServiceName : SUBSCRIBE_SERVICE_LIST) {
            // 通过服务名找到zk上节点信息
            List<String> providerIps = abstractRegister.getProviderIps(providerServiceName);
            for (String providerIp : providerIps) {
                try {
                    // client与 zk节点中的存放的真实server端服务进行连接
                    ConnectionHandler.connect(providerServiceName, providerIp);
                } catch (InterruptedException e) {
                    logger.error("[doConnectServer] connect fail ", e);
                }
            }
            URL url = new URL();
            url.setServiceName(providerServiceName);
            // zk开始监听server端服务信息的变化
            abstractRegister.doAfterSubscribe(url);
        }
    }


    /**
     * 开启发送线程
     *
     * @param
     */
    public void startClient() {
        Thread asyncSendJob = new Thread(new AsyncSendJob());
        asyncSendJob.start();
    }

    class AsyncSendJob implements Runnable {

        public AsyncSendJob() {
        }

        @Override
        public void run() {
            while (true) {
                try {
                    //阻塞模式
                    RpcInvocation data = SEND_QUEUE.take();
                    String json = JSON.toJSONString(data);
                    RpcProtocol rpcProtocol = new RpcProtocol(json.getBytes());
                    ChannelFuture channelFuture = ConnectionHandler.getChannelFuture(data.getTargetServiceName());
                    channelFuture.channel().writeAndFlush(rpcProtocol);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public static void main(String[] args) throws Throwable {
        Client client = new Client();
        // 下面两行代码的意思是: 获取代理对象，设置缓存信息，用于订阅时调用
        RpcReference rpcReference = client.initClientApplication();
        DataService dataService = rpcReference.get(DataService.class);
        // 订阅某个服务,添加到本地缓存 SUBSCRIBE_SERVICE_LIST
        client.doSubscribeService(DataService.class);
        ConnectionHandler.setBootstrap(client.getBootstrap());
        // 订阅服务，从SUBSCRIBE_SERVICE_LIST根据服务名获取 所需要的订阅的服务信息，添加注册中心的监听(zk节点监听)
        // 根据服务生产者信息，建立连接ChannelFuture，建立的ChannelFuture放入CONNECT_MAP
        client.doConnectServer();
        // 开启异步线程，发送函数请求，通过SEND_QUEUE进行通信
        client.startClient();
        for (int i = 0; i < 100; i++) {
            try {
                // 被代理层invoke方法，增强功能(拦截),将请求放入队列SEND_QUEUE中
                // 异步线程asyncSendJob 接收到 SEND_QUEUE数据，发起netty调用; 在invoke方法中 3*1000时间内”死循环获取“ RESP_MAP缓存中的响应数据
                // 在clientHandler中响应数据放入到 RESP_MAP中
                String result = dataService.sendData("test");
                System.out.println(result);
                Thread.sleep(1000);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
