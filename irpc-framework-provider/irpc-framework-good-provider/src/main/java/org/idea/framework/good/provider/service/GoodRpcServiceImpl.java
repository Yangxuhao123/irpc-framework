package org.idea.framework.good.provider.service;

import org.idea.irpc.framework.interfaces.good.GoodRpcService;
import org.idea.irpc.framework.spring.starter.common.IRpcService;

import java.util.Arrays;
import java.util.List;

/**
 * @Author linhao
 * @Date created in 10:59 上午 2022/3/19
 */
// 在容器启动环节中，将带有这些 @IRpcService注解的类给注入到容器内部。
@IRpcService
public class GoodRpcServiceImpl implements GoodRpcService {

    @Override
    public boolean decreaseStock() {
        return true;
    }

    @Override
    public List<String> selectGoodsNoByUserId(String userId) {
        return Arrays.asList(userId + "-good-01", userId + "-good-02");
    }
}
