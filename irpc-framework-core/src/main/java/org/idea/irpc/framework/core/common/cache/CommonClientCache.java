package org.idea.irpc.framework.core.common.cache;

import io.netty.channel.ChannelFuture;
import org.idea.irpc.framework.core.common.ChannelFutureWrapper;
import org.idea.irpc.framework.core.common.RpcInvocation;
import org.idea.irpc.framework.core.common.config.ClientConfig;
import org.idea.irpc.framework.core.registy.URL;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公用缓存 存储请求队列等公共信息
 *
 * @Author linhao
 * @Date created in 8:44 下午 2021/12/1
 */
public class CommonClientCache {

    // client调用的server端方法的时候  发送消息给服务端 封装的消息体
    public static BlockingQueue<RpcInvocation> SEND_QUEUE = new ArrayBlockingQueue(100);
    // 这个map是用来判断client发送方  和  server端接收方的消息体 的鉴别
    // 需要确定client收到自己调用server端方法的响应 而不是其他方法
    public static Map<String,Object> RESP_MAP = new ConcurrentHashMap<>();
    public static ClientConfig CLIENT_CONFIG;
    // provider名称 --> 该服务有哪些集群URL
    // 这里面存放的是服务名  比如dataservice
    public static List<String> SUBSCRIBE_SERVICE_LIST = new ArrayList<>();
    public static Map<String, List<URL>> URL_MAP = new ConcurrentHashMap<>();

    // 这里面存放的是真实提供服务的server地址
    public static Set<String> SERVER_ADDRESS = new HashSet<>();

    // 每次进行远程调用的时候都是从这里面去选择服务提供者
    // key 为  service name
    // value 为  server端提供的channel  host port集合,可以供client进行选择
    public static Map<String, List<ChannelFutureWrapper>> CONNECT_MAP = new ConcurrentHashMap<>();


}
