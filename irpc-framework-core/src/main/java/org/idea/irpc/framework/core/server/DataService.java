package org.idea.irpc.framework.core.server;

import java.util.List;

public interface DataService {
    String sendData(String body);

    List<String> getList();

    void testError();

    String testErrorV2();
}
