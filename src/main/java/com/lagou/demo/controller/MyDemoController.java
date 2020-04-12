package com.lagou.demo.controller;

import com.lagou.demo.service.MyDemoService;
import com.lagou.edu.mvcframework.annotations.MyAutowired;
import com.lagou.edu.mvcframework.annotations.MyController;
import com.lagou.edu.mvcframework.annotations.MyRequestMapping;
import com.lagou.edu.mvcframework.annotations.Security;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author xinyan.xie
 * @description
 * @date 2020/4/12
 */

@MyController
@Security(groups = {"zhangsan","lisi","wangwu"},value = "")
@MyRequestMapping("/myDemo")
public class MyDemoController {

    @MyAutowired
    private MyDemoService myDemoService;

    @MyRequestMapping("/myQuery")
    public String query(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,String username){
        return myDemoService.getName(username);
    }
}
