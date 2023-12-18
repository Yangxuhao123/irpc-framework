package org.idea.irpc.framework.core.client;

import com.alibaba.fastjson.JSON;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import org.idea.irpc.framework.core.common.RpcDecoder;
import org.idea.irpc.framework.core.common.RpcEncoder;
import org.idea.irpc.framework.core.common.RpcInvocation;
import org.idea.irpc.framework.core.common.RpcProtocol;
import org.idea.irpc.framework.core.common.config.ClientConfig;
import org.idea.irpc.framework.core.common.config.PropertiesBootstrap;
import org.idea.irpc.framework.core.common.event.IRpcListenerLoader;
import org.idea.irpc.framework.core.common.utils.CommonUtils;
import org.idea.irpc.framework.core.filter.IClientFilter;
import org.idea.irpc.framework.core.filter.client.ClientFilterChain;
import org.idea.irpc.framework.core.proxy.ProxyFactory;
import org.idea.irpc.framework.core.registy.RegistryService;
import org.idea.irpc.framework.core.registy.URL;
import org.idea.irpc.framework.core.registy.zookeeper.AbstractRegister;
import org.idea.irpc.framework.core.router.IRouter;
import org.idea.irpc.framework.core.serialize.SerializeFactory;
import org.idea.irpc.framework.interfaces.DataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.idea.irpc.framework.core.common.cache.CommonClientCache.*;
import static org.idea.irpc.framework.core.common.constants.RpcConstants.DEFAULT_DECODE_CHAR;
import static org.idea.irpc.framework.core.spi.ExtensionLoader.EXTENSION_LOADER_CLASS_CACHE;

/**
 * @Author linhao
 * @Date created in 8:22 上午 2021/11/29
 */
public class Client {

    private Logger logger = LoggerFactory.getLogger(Client.class);

    private ClientConfig clientConfig;

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

    public RpcReference initClientApplication() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
        EventLoopGroup clientGroup = new NioEventLoopGroup();
        bootstrap.group(clientGroup);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ByteBuf delimiter = Unpooled.copiedBuffer(DEFAULT_DECODE_CHAR.getBytes());
                ch.pipeline().addLast(new DelimiterBasedFrameDecoder(clientConfig.getMaxServerRespDataSize(), delimiter));
                ch.pipeline().addLast(new RpcEncoder());
                ch.pipeline().addLast(new RpcDecoder());
                ch.pipeline().addLast(new ClientHandler());
            }
        });
        // 监听zk节点上服务的变化
        iRpcListenerLoader = new IRpcListenerLoader();
        // 初始化 zk节点update listener
        // 这个listener会当server服务方地址发生变化时 及时更新client本地缓存
        iRpcListenerLoader.init();
        this.clientConfig = PropertiesBootstrap.loadClientConfigFromLocal();
        CLIENT_CONFIG = this.clientConfig;
        // spi扩展的加载部分
        this.initClientConfig();
        EXTENSION_LOADER.loadExtension(ProxyFactory.class);
        String proxyType = clientConfig.getProxyType();
        LinkedHashMap<String, Class> classMap = EXTENSION_LOADER_CLASS_CACHE.get(ProxyFactory.class.getName());
        Class proxyClassType = classMap.get(proxyType);
        ProxyFactory proxyFactory = (ProxyFactory) proxyClassType.newInstance();
        return new RpcReference(proxyFactory);
    }

    /**
     * 启动服务之前需要预先订阅对应的dubbo服务
     *
     * @param serviceBean
     */
    public void doSubscribeService(Class serviceBean) {
        if (ABSTRACT_REGISTER == null) {
            try {
                EXTENSION_LOADER.loadExtension(RegistryService.class);
                Map<String, Class> registerMap = EXTENSION_LOADER_CLASS_CACHE.get(RegistryService.class.getName());
                Class registerClass = registerMap.get(clientConfig.getRegisterType());
                // 创建与zk通信的工具类
                ABSTRACT_REGISTER = (AbstractRegister) registerClass.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("registryServiceType unKnow,error is ", e);
            }
        }
        URL url = new URL();
        url.setApplicationName(clientConfig.getApplicationName());
        url.setServiceName(serviceBean.getName());
        url.addParameter("host", CommonUtils.getIpAddress());
        Map<String, String> result = ABSTRACT_REGISTER.getServiceWeightMap(serviceBean.getName());
        URL_MAP.put(serviceBean.getName(), result);
        ABSTRACT_REGISTER.subscribe(url);
    }

    /**
     * 开始和各个provider建立连接，同时监听各个providerNode节点的变化（child变化和nodeData的变化）
     */
    public void doConnectServer() {
        for (URL providerURL : SUBSCRIBE_SERVICE_LIST) {
            // 通过服务名找到zk上节点信息
            List<String> providerIps = ABSTRACT_REGISTER.getProviderIps(providerURL.getServiceName());
            for (String providerIp : providerIps) {
                try {
                    // client与 zk节点中的存放的真实server端服务进行连接
                    ConnectionHandler.connect(providerURL.getServiceName(), providerIp);
                } catch (InterruptedException e) {
                    logger.error("[doConnectServer] connect fail ", e);
                }
            }
            URL url = new URL();
            url.addParameter("servicePath", providerURL.getServiceName() + "/provider");
            url.addParameter("providerIps", JSON.toJSONString(providerIps));
            // zk开始监听server端服务信息的变化
            ABSTRACT_REGISTER.doAfterSubscribe(url);
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

        private AtomicLong atomicLong = new AtomicLong(0);

        public AsyncSendJob() {
        }

        @Override
        public void run() {
            while (true) {
                try {
                    //阻塞模式
                    RpcInvocation rpcInvocation = SEND_QUEUE.take();
                    // 经过过滤器链 和 负载策略选择合适的channelFuture
                    ChannelFuture channelFuture = ConnectionHandler.getChannelFuture(rpcInvocation);
                    if (channelFuture != null) {
                        Channel channel = channelFuture.channel();
                        // 如果出现服务端中断的情况需要兼容下
                        if (!channel.isOpen()) {
                            throw new RuntimeException("aim channel is not open!rpcInvocation is " + rpcInvocation);
                        }
                        RpcProtocol rpcProtocol = new RpcProtocol(CLIENT_SERIALIZE_FACTORY.serialize(rpcInvocation));
                        channel.writeAndFlush(rpcProtocol);
                    }
                } catch (Exception e) {
                    logger.error("[AsyncSendJob] e is ", e);
                }
            }
        }
    }

    /**
     * 后续可以考虑加入spi
     */
    private void initClientConfig() throws IOException, IllegalAccessException, ClassNotFoundException, InstantiationException {
        //初始化路由策略
        EXTENSION_LOADER.loadExtension(IRouter.class);
        String routerStrategy = clientConfig.getRouterStrategy();
        LinkedHashMap<String, Class> iRouterMap = EXTENSION_LOADER_CLASS_CACHE.get(IRouter.class.getName());
        Class iRouterClass = iRouterMap.get(routerStrategy);
        if (iRouterClass == null) {
            throw new RuntimeException("no match routerStrategy for " + routerStrategy);
        }
        IROUTER = (IRouter) iRouterClass.newInstance();

        //初始化序列化框架
        EXTENSION_LOADER.loadExtension(SerializeFactory.class);
        String clientSerialize = clientConfig.getClientSerialize();
        LinkedHashMap<String, Class> serializeMap = EXTENSION_LOADER_CLASS_CACHE.get(SerializeFactory.class.getName());
        Class serializeFactoryClass = serializeMap.get(clientSerialize);
        if (serializeFactoryClass == null) {
            throw new RuntimeException("no match serialize type for " + clientSerialize);
        }
        CLIENT_SERIALIZE_FACTORY = (SerializeFactory) serializeFactoryClass.newInstance();

        //初始化过滤链
        EXTENSION_LOADER.loadExtension(IClientFilter.class);
        ClientFilterChain clientFilterChain = new ClientFilterChain();
        LinkedHashMap<String, Class> iClientMap = EXTENSION_LOADER_CLASS_CACHE.get(IClientFilter.class.getName());
        for (String implClassName : iClientMap.keySet()) {
            Class iClientFilterClass = iClientMap.get(implClassName);
            if (iClientFilterClass == null) {
                throw new RuntimeException("no match iClientFilter for " + iClientFilterClass);
            }
            clientFilterChain.addClientFilter((IClientFilter) iClientFilterClass.newInstance());
        }
        CLIENT_FILTER_CHAIN = clientFilterChain;
    }


    public static void main(String[] args) throws Throwable {
        Client client = new Client();
        // 获取代理对象，设置缓存信息，用于订阅时调用
        RpcReference rpcReference = client.initClientApplication();
        RpcReferenceWrapper<DataService> rpcReferenceWrapper = new RpcReferenceWrapper<>();
        rpcReferenceWrapper.setAimClass(DataService.class);
        rpcReferenceWrapper.setGroup("dev");
        rpcReferenceWrapper.setServiceToken("token-a");
//        rpcReferenceWrapper.setUrl("192.168.43.227:9093");
        //在初始化之前必须要设置对应的上下文
        DataService dataService = rpcReference.get(rpcReferenceWrapper);
        // // 订阅某个服务,添加到本地缓存 SUBSCRIBE_SERVICE_LIST
        client.doSubscribeService(DataService.class);
        ConnectionHandler.setBootstrap(client.getBootstrap());
        // 订阅服务，从SUBSCRIBE_SERVICE_LIST根据服务名获取 所需要的订阅的服务信息，添加注册中心的监听(zk节点监听)
        // 根据服务生产者信息，建立连接ChannelFuture，建立的ChannelFuture放入CONNECT_MAP
        client.doConnectServer();
        // 开启异步线程，发送函数请求，通过SEND_QUEUE进行通信
        client.startClient();
        for (int i = 0; i < 10000; i++) {
            try {
                // 被代理层invoke方法，增强功能(拦截),将请求放入队列SEND_QUEUE中
                // 异步线程asyncSendJob 接收到 SEND_QUEUE数据，发起netty调用; 在invoke方法中 3*1000时间内”死循环获取“ RESP_MAP缓存中的响应数据
                // 在clientHandler中响应数据放入到 RESP_MAP中
                String result = dataService.sendData("test");
                System.out.println(result);
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
