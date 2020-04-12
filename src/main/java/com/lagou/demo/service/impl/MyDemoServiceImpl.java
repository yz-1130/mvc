package com.lagou.demo.service.impl;

import com.lagou.demo.service.MyDemoService;
import com.lagou.edu.mvcframework.annotations.MyService;

import java.lang.annotation.Annotation;

/**
 * @author xinyan.xie
 * @description
 * @date 2020/4/12
 */
@MyService("MyDemoSeivice")
public class MyDemoServiceImpl implements MyDemoService {


    @Override
    public String getName(String username) {
        System.out.println("用户："+username+"-------"+"正常进入界面");
        return username;
    }
}
