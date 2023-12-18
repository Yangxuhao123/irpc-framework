package org.idea.irpc.framework.core.common.cache;

import io.netty.util.internal.ConcurrentSet;
import org.idea.irpc.framework.core.common.ServerServiceSemaphoreWrapper;
import org.idea.irpc.framework.core.common.config.ServerConfig;
import org.idea.irpc.framework.core.dispatcher.ServerChannelDispatcher;
import org.idea.irpc.framework.core.filter.server.ServerAfterFilterChain;
import org.idea.irpc.framework.core.filter.server.ServerBeforeFilterChain;
import org.idea.irpc.framework.core.registy.URL;
import org.idea.irpc.framework.core.registy.zookeeper.AbstractRegister;
import org.idea.irpc.framework.core.serialize.SerializeFactory;
import org.idea.irpc.framework.core.server.ServiceWrapper;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * @Author linhao
 * @Date created in 8:45 下午 2021/12/1
 */
public class CommonServerCache {
    // 这里存放提供服务信息
    // key    为 serviceName 比如dataService
    // value  为 具体的实现类
    public static final Map<String,Object> PROVIDER_CLASS_MAP = new ConcurrentHashMap<>();
    // 服务端提供的各种服务信息  zk里面封装的各种信息(比如分组、权重)
    // 为了后面zk注册
    public static final Set<URL> PROVIDER_URL_SET = new ConcurrentSet<>();
    // 注册器
    public static AbstractRegister REGISTRY_SERVICE;
    // 序列化工厂
    public static SerializeFactory SERVER_SERIALIZE_FACTORY;
    public static ServerConfig SERVER_CONFIG;
    // 前置过滤器
    public static ServerBeforeFilterChain SERVER_BEFORE_FILTER_CHAIN;
    // 后置过滤器
    public static ServerAfterFilterChain SERVER_AFTER_FILTER_CHAIN;
    // key   为 serviceName
    // value 为 服务的信息(服务的包装类  比如鉴权 限流信息)
    public static final Map<String, ServiceWrapper> PROVIDER_SERVICE_WRAPPER_MAP = new ConcurrentHashMap<>();
    public static Boolean IS_STARTED = false;
    // 之前是将处理事件的逻辑放在了handler里面，有可能造成超时;
    // 现在单独抽出来事件分发机制，将处理业务逻辑放入到业务线程池中
    public static ServerChannelDispatcher SERVER_CHANNEL_DISPATCHER = new ServerChannelDispatcher();
    // 限流配置
    public static final Map<String, ServerServiceSemaphoreWrapper> SERVER_SERVICE_SEMAPHORE_MAP = new ConcurrentHashMap<>(64);
}
