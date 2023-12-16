package org.idea.irpc.framework.core.client;

import com.alibaba.fastjson.JSON;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import org.idea.irpc.framework.core.common.RpcInvocation;
import org.idea.irpc.framework.core.common.RpcProtocol;

import static org.idea.irpc.framework.core.common.cache.CommonClientCache.RESP_MAP;

/**
 * @Author linhao
 * @Date created in 8:21 下午 2021/11/24
 */
public class ClientHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        RpcProtocol rpcProtocol = (RpcProtocol) msg;
        byte[] reqContent = rpcProtocol.getContent();
        String json = new String(reqContent,0,reqContent.length);
        RpcInvocation rpcInvocation = JSON.parseObject(json,RpcInvocation.class);
        // 这里从本地缓存中获取key 判断从server端获取到的消息
        // 是不是我发送消息出去的相应
        if(!RESP_MAP.containsKey(rpcInvocation.getUuid())){
            throw new IllegalArgumentException("server response is error!");
        }
        RESP_MAP.put(rpcInvocation.getUuid(),rpcInvocation);
        ReferenceCountUtil.release(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        Channel channel = ctx.channel();
        if(channel.isActive()){
            ctx.close();
        }
    }
}
