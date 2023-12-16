package org.idea.irpc.framework.core.common.cache;

import org.idea.irpc.framework.core.registy.URL;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @Author linhao
 * @Date created in 8:45 下午 2021/12/1
 */
public class CommonServerCache {
    // 这里存放提供服务的信息
    // key为service名 比如dataservice   value为具体的实现类
    public static final Map<String,Object> PROVIDER_CLASS_MAP = new HashMap<>();

    // 服务端提供的各种服务信息  zk里面的各种信息
    public static final Set<URL> PROVIDER_URL_SET = new HashSet<>();
}
