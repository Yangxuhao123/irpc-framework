package org.idea.irpc.framework.spring.starter.common;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * @Author linhao
 * @Date created in 7:27 下午 2022/3/7
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
// 让Spring容器在扫描到@Component注解的时候同时将带有@IRpcService注解的bean也一同加载到容器当中。
public @interface IRpcService {

    int limit() default 0;

    String group() default "default";

    String serviceToken() default "";

}
