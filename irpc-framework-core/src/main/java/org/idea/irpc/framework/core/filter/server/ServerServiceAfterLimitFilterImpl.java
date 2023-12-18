package org.idea.irpc.framework.core.filter.server;

import org.idea.irpc.framework.core.common.RpcInvocation;
import org.idea.irpc.framework.core.common.ServerServiceSemaphoreWrapper;
import org.idea.irpc.framework.core.common.annotations.SPI;
import org.idea.irpc.framework.core.filter.IServerFilter;


import static org.idea.irpc.framework.core.common.cache.CommonServerCache.SERVER_SERVICE_SEMAPHORE_MAP;

/**
 * @Author linhao
 * @Date created in 11:52 上午 2022/3/6
 */
@SPI("after")
public class ServerServiceAfterLimitFilterImpl implements IServerFilter {

    @Override
    public void doFilter(RpcInvocation rpcInvocation) {
        String serviceName = rpcInvocation.getTargetServiceName();
        ServerServiceSemaphoreWrapper serverServiceSemaphoreWrapper = SERVER_SERVICE_SEMAPHORE_MAP.get(serviceName);
        // 在before过滤器中进行acquire
        serverServiceSemaphoreWrapper.getSemaphore().release();
    }
}
