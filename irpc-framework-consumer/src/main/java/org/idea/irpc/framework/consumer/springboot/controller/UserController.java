package org.idea.irpc.framework.consumer.springboot.controller;

import org.idea.irpc.framework.interfaces.OrderService;
import org.idea.irpc.framework.interfaces.UserService;
import org.idea.irpc.framework.spring.starter.common.IRpcReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


/**
 * @Author linhao
 * @Date created in 5:43 下午 2022/3/8
 */
@RestController
@RequestMapping(value = "/user")
public class UserController {

    @IRpcReference
    private UserService userService;

    /**
     * 验证各类参数配置是否异常
     */
    @IRpcReference(group = "order-group",serviceToken = "order-token",url = "127.0.0.1:9090")
    private OrderService orderService;

    @GetMapping(value = "/test")
    public void test(){
        userService.test();
    }

    @GetMapping(value = "/get-order-no")
    public List<String> getOrderNo(){
        List<String> result =  orderService.getOrderNoList();
        System.out.println(result);
        return result;
    }


}
