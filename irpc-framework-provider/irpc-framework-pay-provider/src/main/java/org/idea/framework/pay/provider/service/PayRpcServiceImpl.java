package org.idea.framework.pay.provider.service;

import org.idea.irpc.framework.interfaces.pay.PayRpcService;
import org.idea.irpc.framework.spring.starter.common.IRpcService;

import java.util.Arrays;
import java.util.List;

/**
 * @Author linhao
 * @Date created in 10:58 上午 2022/3/19
 */
// 在容器启动环节中，将带有这些 @IRpcService注解的类给注入到容器内部。
@IRpcService
public class PayRpcServiceImpl implements PayRpcService {

    @Override
    public boolean doPay() {
        System.out.println("pay1");
        return true;
    }

    @Override
    public List<String> getPayHistoryByGoodNo(String goodNo) {
        System.out.println("getPayHistoryByGoodNo");
        return Arrays.asList(goodNo + "-pay-001", goodNo + "-pay-002");
    }

}
