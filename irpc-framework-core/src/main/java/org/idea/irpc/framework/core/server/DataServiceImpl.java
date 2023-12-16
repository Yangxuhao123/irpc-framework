package org.idea.irpc.framework.core.server;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author linhao
 * @Date created in 5:07 下午 2021/12/5
 */
public class DataServiceImpl implements DataService {

    @Override
    public String sendData(String body) {
        System.out.println("这里是服务提供者，body is " + body);
        return "success";
    }

    @Override
    public List<String> getList() {
        ArrayList arrayList = new ArrayList();
        arrayList.add("idea1");
        arrayList.add("idea2");
        arrayList.add("idea3");
        return arrayList;
    }
}
