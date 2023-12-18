package org.idea.irpc.framework.core.common.cache;

import org.idea.irpc.framework.core.common.ChannelFuturePollingRef;
import org.idea.irpc.framework.core.common.ChannelFutureWrapper;
import org.idea.irpc.framework.core.common.RpcInvocation;
import org.idea.irpc.framework.core.common.config.ClientConfig;
import org.idea.irpc.framework.core.filter.client.ClientFilterChain;
import org.idea.irpc.framework.core.registy.URL;
import org.idea.irpc.framework.core.registy.zookeeper.AbstractRegister;
import org.idea.irpc.framework.core.router.IRouter;
import org.idea.irpc.framework.core.serialize.SerializeFactory;
import org.idea.irpc.framework.core.spi.ExtensionLoader;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 公用缓存 存储请求队列等公共信息
 *
 * @Author linhao
 * @Date created in 8:44 下午 2021/12/1
 */
public class CommonClientCache {

    // 客户端缓存的消息队列
    // client调用的server端方法的时候  发送消息给服务端 封装的消息体
    public static BlockingQueue<RpcInvocation> SEND_QUEUE = new ArrayBlockingQueue(5000);
    // 这个map是用来判断client发送方  和  server端接收方的消息体 的鉴别
    // 需要确定client收到自己调用server端方法的响应 而不是其他方法
    // key为 消息的uuid  value为客户端给出的响应
    public static Map<String, Object> RESP_MAP = new ConcurrentHashMap<>();
    //provider名称 --> 该服务有哪些集群URL
    public static List<URL> SUBSCRIBE_SERVICE_LIST = new ArrayList<>();
    //com.sise.test.service -> <<ip:host,urlString>,<ip:host,urlString>,<ip:host,urlString>>
    public static Map<String, Map<String,String>> URL_MAP = new ConcurrentHashMap<>();
    public static Set<String> SERVER_ADDRESS = new HashSet<>();
    // 每次进行远程调用的时候都是从这里面去选择服务提供者
    // key   为  service name
    // value 为  server端提供的channel  host port集合,可以供client进行选择
    public static Map<String, List<ChannelFutureWrapper>> CONNECT_MAP = new ConcurrentHashMap<>();
    // 随机请求的map
    public static Map<String, ChannelFutureWrapper[]> SERVICE_ROUTER_MAP = new ConcurrentHashMap<>();
    public static ChannelFuturePollingRef CHANNEL_FUTURE_POLLING_REF = new ChannelFuturePollingRef();
    // 路由器 提供一些方法  更新一下服务信息
    public static IRouter IROUTER;
    public static SerializeFactory CLIENT_SERIALIZE_FACTORY;
    public static ClientConfig CLIENT_CONFIG;
    public static ClientFilterChain CLIENT_FILTER_CHAIN;
    // 与zk通信的工具类
    public static AbstractRegister ABSTRACT_REGISTER;
    public static ExtensionLoader EXTENSION_LOADER = new ExtensionLoader();
    public static AtomicLong addReqTime = new AtomicLong(0);
}
