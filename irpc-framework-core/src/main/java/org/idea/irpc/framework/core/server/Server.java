package org.idea.irpc.framework.core.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.idea.irpc.framework.core.common.RpcDecoder;
import org.idea.irpc.framework.core.common.RpcEncoder;
import org.idea.irpc.framework.core.common.ServerServiceSemaphoreWrapper;
import org.idea.irpc.framework.core.common.annotations.SPI;
import org.idea.irpc.framework.core.common.config.PropertiesBootstrap;
import org.idea.irpc.framework.core.common.config.ServerConfig;
import org.idea.irpc.framework.core.common.event.IRpcListenerLoader;
import org.idea.irpc.framework.core.common.utils.CommonUtils;
import org.idea.irpc.framework.core.filter.IServerFilter;
import org.idea.irpc.framework.core.filter.server.ServerAfterFilterChain;
import org.idea.irpc.framework.core.filter.server.ServerBeforeFilterChain;
import org.idea.irpc.framework.core.registy.RegistryService;
import org.idea.irpc.framework.core.registy.URL;
import org.idea.irpc.framework.core.registy.zookeeper.AbstractRegister;
import org.idea.irpc.framework.core.serialize.SerializeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

import static org.idea.irpc.framework.core.common.cache.CommonClientCache.EXTENSION_LOADER;
import static org.idea.irpc.framework.core.common.cache.CommonServerCache.*;
import static org.idea.irpc.framework.core.common.constants.RpcConstants.*;
import static org.idea.irpc.framework.core.spi.ExtensionLoader.EXTENSION_LOADER_CLASS_CACHE;

/**
 * @Author linhao
 * @Date created in 8:12 上午 2021/11/29
 */
public class Server {

    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);

    private static EventLoopGroup bossGroup = null;

    private static EventLoopGroup workerGroup = null;

    private ServerConfig serverConfig;

    private static IRpcListenerLoader iRpcListenerLoader;


    public ServerConfig getServerConfig() {
        return serverConfig;
    }

    public void setServerConfig(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    public void startApplication() throws InterruptedException {
        // bossGroup只处理accept事件
        bossGroup = new NioEventLoopGroup();
        ThreadFactory threadFactory = new DefaultThreadFactory("irpc-NettyServerWorker", true);
        int core = Runtime.getRuntime().availableProcessors() + 1;
        System.out.println("core is " + core);
        // workerGroup本来io事件
        // 后来为了避免阻塞 将具体的业务处理逻辑交给了业务线程池
        workerGroup = new NioEventLoopGroup(Math.min(core, 32), threadFactory);
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup);
        bootstrap.channel(NioServerSocketChannel.class);
        // 有数据立即发送
        bootstrap.option(ChannelOption.TCP_NODELAY, true);
        // 保持连接数
        bootstrap.option(ChannelOption.SO_BACKLOG, 1024);
        bootstrap.option(ChannelOption.SO_SNDBUF, 16 * 1024)
                .option(ChannelOption.SO_RCVBUF, 16 * 1024)
                //长链接
                .option(ChannelOption.SO_KEEPALIVE, true);

        // 服务端采用单一长连接的模式，这里所支持的最大连接数应该和机器本身的性能有关
        // 连接防护的handler应该绑定在Main-Reactor上
        bootstrap.handler(new MaxConnectionLimitHandler(serverConfig.getMaxConnections()));
        bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                LOGGER.info("初始化provider过程");
                // 限定接收消息的最大大小
                ByteBuf delimiter = Unpooled.copiedBuffer(DEFAULT_DECODE_CHAR.getBytes());
                ch.pipeline().addLast(new DelimiterBasedFrameDecoder(serverConfig.getMaxServerRequestData(), delimiter));
                ch.pipeline().addLast(new RpcEncoder());
                ch.pipeline().addLast(new RpcDecoder());
                // 这里面需要注意出现堵塞的情况发生，建议将核心业务内容分配给业务线程池处理
                ch.pipeline().addLast(new ServerHandler());
            }
        });
        // 根据配置文件中的zk服务信息 进行实际注册
        this.batchExportUrl();
        //开始准备接收请求的任务
        SERVER_CHANNEL_DISPATCHER.startDataConsume();
        bootstrap.bind(serverConfig.getServerPort()).sync();
        IS_STARTED = true;
        LOGGER.info("[startApplication] server is started!");
    }

    public void initServerConfig() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
        // 从本地配置文件加载配置
        ServerConfig serverConfig = PropertiesBootstrap.loadServerConfigFromLocal();
        this.setServerConfig(serverConfig);
        SERVER_CONFIG = serverConfig;
        // 初始化线程池和队列的配置
        // 之前是将处理事件的逻辑放在了handler里面，有可能造成超时;
        // 现在单独抽出来事件分发机制，将处理业务逻辑放入到业务线程池中
        // 这里的队列不是线程池中的队列 是单独的业务队列 长度为512
        SERVER_CHANNEL_DISPATCHER.init(SERVER_CONFIG.getServerQueueSize(), SERVER_CONFIG.getServerBizThreadNums());
        // 序列化技术初始化
        String serverSerialize = serverConfig.getServerSerialize();
        // 通过spi扩展技术 将配置文件中所有的信息加载到本地缓存(大Map)中
        EXTENSION_LOADER.loadExtension(SerializeFactory.class);
        // 从本地缓存(这个大Map中有很多信息，比如序列化等)拿到class文件的小Map
        LinkedHashMap<String, Class> serializeFactoryClassMap = EXTENSION_LOADER_CLASS_CACHE.get(SerializeFactory.class.getName());
        // 从小Map中拿到class文件
        Class serializeFactoryClass = serializeFactoryClassMap.get(serverSerialize);
        if (serializeFactoryClass == null) {
            throw new RuntimeException("no match serialize type for " + serverSerialize);
        }
        // 实例化 序列化工厂
        SERVER_SERIALIZE_FACTORY = (SerializeFactory) serializeFactoryClass.newInstance();
        // 过滤链技术初始化
        EXTENSION_LOADER.loadExtension(IServerFilter.class);
        // 从大Map中拿到关于过滤器的小Map信息
        LinkedHashMap<String, Class> iServerFilterClassMap = EXTENSION_LOADER_CLASS_CACHE.get(IServerFilter.class.getName());
        ServerBeforeFilterChain serverBeforeFilterChain = new ServerBeforeFilterChain();
        ServerAfterFilterChain serverAfterFilterChain = new ServerAfterFilterChain();
        // 过滤器初始化环节新增 前置过滤器和后置过滤器
        for (String iServerFilterKey : iServerFilterClassMap.keySet()) {
            Class iServerFilterClass = iServerFilterClassMap.get(iServerFilterKey);
            if (iServerFilterClass == null) {
                throw new RuntimeException("no match iServerFilter type for " + iServerFilterKey);
            }
            // 这里将过滤器 分为前置过滤器 和 后置过滤器
            // 以注解值作为区分
            SPI spi = (SPI) iServerFilterClass.getDeclaredAnnotation(SPI.class);
            if (spi != null && "before".equals(spi.value())) {
                serverBeforeFilterChain.addServerFilter((IServerFilter) iServerFilterClass.newInstance());
            } else if (spi != null && "after".equals(spi.value())) {
                serverAfterFilterChain.addServerFilter((IServerFilter) iServerFilterClass.newInstance());
            }
        }
        SERVER_AFTER_FILTER_CHAIN = serverAfterFilterChain;
        SERVER_BEFORE_FILTER_CHAIN = serverBeforeFilterChain;
    }

    /**
     * 暴露服务信息
     *
     * @param serviceWrapper
     */
    public void exportService(ServiceWrapper serviceWrapper) {
        Object serviceBean = serviceWrapper.getServiceObj();
        if (serviceBean.getClass().getInterfaces().length == 0) {
            throw new RuntimeException("service must had interfaces!");
        }
        Class[] classes = serviceBean.getClass().getInterfaces();
        if (classes.length > 1) {
            throw new RuntimeException("service must only had one interfaces!");
        }
        if (REGISTRY_SERVICE == null) {
            try {
                // 还是通过spi插件的方式来实例化注册器
                EXTENSION_LOADER.loadExtension(RegistryService.class);
                Map<String, Class> registryClassMap = EXTENSION_LOADER_CLASS_CACHE.get(RegistryService.class.getName());
                Class registryClass = registryClassMap.get(serverConfig.getRegisterType());
                REGISTRY_SERVICE = (AbstractRegister) registryClass.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("registryServiceType unKnow,error is ", e);
            }
        }
        // 默认选择该对象的第一个实现接口
        Class interfaceClass = classes[0];
        // 存放提供服务信息的本地缓存
        // key    为 serviceName 比如dataService
        // value  为 具体的实现类
        PROVIDER_CLASS_MAP.put(interfaceClass.getName(), serviceBean);
        URL url = new URL();
        url.setServiceName(interfaceClass.getName());
        url.setApplicationName(serverConfig.getApplicationName());
        url.addParameter("host", CommonUtils.getIpAddress());
        url.addParameter("port", String.valueOf(serverConfig.getServerPort()));
        url.addParameter("group", String.valueOf(serviceWrapper.getGroup()));
        url.addParameter("limit", String.valueOf(serviceWrapper.getLimit()));
        // 设置服务端的限流器
        SERVER_SERVICE_SEMAPHORE_MAP.put(interfaceClass.getName(), new ServerServiceSemaphoreWrapper(serviceWrapper.getLimit()));
        // 服务端提供的各种服务信息  zk里面封装的各种信息
        PROVIDER_URL_SET.add(url);
        if (CommonUtils.isNotEmpty(serviceWrapper.getServiceToken())) {
            // 服务包装类的本地缓存
            // key   为 serviceName
            // value 为 服务的信息(服务的包装类  比如鉴权 限流信息)
            PROVIDER_SERVICE_WRAPPER_MAP.put(interfaceClass.getName(), serviceWrapper);
        }
    }

    /**
     * 批量暴露URL
     */
    public void batchExportUrl() {
        Thread task = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                for (URL url : PROVIDER_URL_SET) {
                    REGISTRY_SERVICE.register(url);
                    LOGGER.info("[Server] export service {}", url.getServiceName());
                }
            }
        });
        task.start();
    }

    public static void main(String[] args) throws InterruptedException, ClassNotFoundException, IOException, InstantiationException, IllegalAccessException {
        Server server = new Server();
        // 根据配置文件加载到本地缓存
        server.initServerConfig();
        iRpcListenerLoader = new IRpcListenerLoader();
        // 这里注册了三个zk监听器 zk上注册的provider信息
        iRpcListenerLoader.init();
        // 将服务信息 和 服务基本配置信息(比如组名,token,限流量封装成对象)
        ServiceWrapper dataServiceServiceWrapper = new ServiceWrapper(new DataServiceImpl(), "dev");
        dataServiceServiceWrapper.setServiceToken("token-a");
        dataServiceServiceWrapper.setLimit(2);
        ServiceWrapper userServiceServiceWrapper = new ServiceWrapper(new UserServiceImpl(), "dev");
        userServiceServiceWrapper.setServiceToken("token-b");
        userServiceServiceWrapper.setLimit(2);
        // 增加服务包装信息到本地缓存
        server.exportService(dataServiceServiceWrapper);
        server.exportService(userServiceServiceWrapper);
        ApplicationShutdownHook.registryShutdownHook();
        // 根据配置文件中的zk服务信息进行实际注册
        server.startApplication();
    }
}
