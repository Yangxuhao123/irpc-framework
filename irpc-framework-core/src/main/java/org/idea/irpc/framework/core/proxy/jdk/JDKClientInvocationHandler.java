package org.idea.irpc.framework.core.proxy.jdk;

import org.idea.irpc.framework.core.common.RpcInvocation;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.idea.irpc.framework.core.common.cache.CommonClientCache.RESP_MAP;
import static org.idea.irpc.framework.core.common.cache.CommonClientCache.SEND_QUEUE;

/**
 * 各种代理工厂统一使用这个InvocationHandler
 *
 * @Author linhao
 * @Date created in 6:59 下午 2021/12/5
 */
public class JDKClientInvocationHandler implements InvocationHandler {

    private final static Object OBJECT = new Object();

    private Class<?> clazz;

    public JDKClientInvocationHandler(Class<?> clazz) {
        this.clazz = clazz;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        RpcInvocation rpcInvocation = new RpcInvocation();
        rpcInvocation.setArgs(args);
        rpcInvocation.setTargetMethod(method.getName());
        rpcInvocation.setTargetServiceName(clazz.getName());
        rpcInvocation.setUuid(UUID.randomUUID().toString());
        // client调用server 发送消息之前
        // 会被这个代理对象 将消息封装成 rpcInvocation  存在本地缓存中
        RESP_MAP.put(rpcInvocation.getUuid(), OBJECT);
        // 同时也将rpcInvocation 放入本地队列中
        SEND_QUEUE.add(rpcInvocation);
        // ......
        // 其实这中间 异步线程AsyncSendJob  发起rpc调用
        // 服务端发送响应给client之后，客户端的channelHandler的channelRead方法会把数据放到 RESP_MAP 中
        long beginTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - beginTime < 3*1000) {
            Object object = RESP_MAP.get(rpcInvocation.getUuid());
            if (object instanceof RpcInvocation) {
                return ((RpcInvocation)object).getResponse();
            }
        }
        throw new TimeoutException("client wait server's response timeout!");
    }
}
