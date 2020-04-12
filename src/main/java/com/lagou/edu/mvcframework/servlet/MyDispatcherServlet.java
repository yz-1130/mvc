package com.lagou.edu.mvcframework.servlet;

import com.lagou.demo.controller.MyDemoController;
import com.lagou.edu.mvcframework.annotations.MyAutowired;
import com.lagou.edu.mvcframework.annotations.MyController;
import com.lagou.edu.mvcframework.annotations.MyRequestMapping;
import com.lagou.edu.mvcframework.annotations.MyService;
import com.lagou.edu.mvcframework.annotations.Security;
import com.lagou.edu.mvcframework.pojo.MyHandler;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.ws.http.HTTPException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author xinyan.xie
 * @description 自定义映射转发
 * @date 2020/4/12
 */
public class MyDispatcherServlet extends HttpServlet {


    private   Properties  properties = new Properties();

    private Map<String,Object> ioc  = new HashMap<>();

    //handlerMapping
   // private Map<String,Method> handlerMapping = new HashMap<>();//存储url和method之间的关系

    private List<MyHandler>  handlerMapping = new ArrayList<>();

    //请求进来的username
    private final static String  USER_NAME  =" ${userName}";

    //存储注解允许存储的租户
    private  List<String> userNameList = new ArrayList<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        //1。加载配置文件 MySpringMvc.properties
        String contextConfigLocation = config.getInitParameter("contextConfigLocation");
        doLoadConfig(contextConfigLocation);
        
        //2。扫描注解和相关类
        doScanAnnocation(properties.getProperty("scanPackage"));

        //3。初始化相应对bean，添加到Ioc容器，基于注解
        try {
            doInstance();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }

        //4。实现依赖注入
        doAutowired();

        //5。构造一个handlerMapping处理器映射器

        initHandlerMapping();
        //6。等待请求进入，处理请求

        //7.拦截非目标用户的请求


        System.out.println("========================================");
        System.out.println("My MVC INIT END=========================");
    }




    private void initHandlerMapping() {
        if(ioc.isEmpty()){ return;}
        for(Map.Entry<String,Object> entry:ioc.entrySet()){
            Class<?> aClass = entry.getValue().getClass();
            if(!aClass.isAnnotationPresent(MyController.class)){continue;}
            String baseUrl = "";
            if(aClass.isAnnotationPresent(MyRequestMapping.class)){
                MyRequestMapping annotation = aClass.getAnnotation(MyRequestMapping.class);
                baseUrl = annotation.value(); //等同于demo
            }
            if(aClass.isAnnotationPresent(Security.class)){
                Security annotation = aClass.getAnnotation(Security.class);
                userNameList = Arrays.asList(annotation.groups());
            }
            //获取方法
            Method[] methods = aClass.getMethods();
            for (int i = 0; i < methods.length; i++) {
                Method method =  methods[i];
                //如果方法没有标示就不处理
                if(!method.isAnnotationPresent(MyRequestMapping.class)){continue;}
                //如果方法标示就要处理，获取注解的值
                MyRequestMapping annotation = method.getAnnotation(MyRequestMapping.class);
                String methodUrl = annotation.value();
                String url = baseUrl+methodUrl;


                //把method所有信息封装成为i 工业handler

                MyHandler myHandler = new MyHandler(entry.getValue(),method, Pattern.compile(url));

                //处理计算参数信息、计算方法的位置信息
                Parameter[] parameters = method.getParameters();
                for (int j = 0; j < parameters.length; j++) {
                    Parameter parameter =  parameters[j];
                    if(parameter.getType() == HttpServletRequest.class || parameter.getType() == HttpServletResponse.class){
                        myHandler.getParamIndexMapping().put(parameter.getType().getSimpleName(),j);
                    } else {
                        myHandler.getParamIndexMapping().put(parameter.getName(),j);
                    }
                }
                //建立url和mehod的映射关系
                handlerMapping.add(myHandler);

            }
        }
    }

    //基于注解的依赖注入。判断是否有注解
    private void doAutowired() {
        //判断容器中是否有注解
        if(ioc.isEmpty()){
            return;
        }
        //  遍历ioc容器。是否有需要的注解字段
        for(Map.Entry<String,Object> entry:ioc.entrySet()){
            //获取bean对象中的字段
            Field[] declaredFields = entry.getValue().getClass().getDeclaredFields();
            for (int i = 0; i < declaredFields.length; i++) {
                Field declaredField = declaredFields[i];
                if(!declaredField.isAnnotationPresent(MyAutowired.class)){
                    continue;
                }
                MyAutowired annotation = declaredField.getAnnotation(MyAutowired.class);
                String beanName = annotation.value();
                if("".equals(beanName.trim())){
                    //没有配置具体的bean id ，那就需要根据当前字段类型进行注入（接口注入）
                    beanName = declaredField.getType().getName();

                    //开启赋值
                    declaredField.setAccessible(true);

                    try {
                        declaredField.set(entry.getValue(),ioc.get(beanName));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }

        }

    }


    //ioc容器
    //基于className缓存的类的权限定类名，以及反射技术，完成对象的创建和管理
    private void doInstance() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        if(classNameList.size()==0){
            return;
        }
        for (int i = 0; i < classNameList.size(); i++) {
            String className =  classNameList.get(i);  //com.lagou.demo.controller.MyController
            //反射
            Class<?> aclass = Class.forName(className);
            //区分controller、区分service
            if(aclass.isAnnotationPresent(MyController.class)){
                String simpleName = aclass.getSimpleName();
                String lowerFirstSimpleName = lowerFirst(simpleName);//myController
                //实例化
                Object newInstance = aclass.newInstance();
                ioc.put(lowerFirstSimpleName,newInstance);
            } else if(aclass.isAnnotationPresent(Security.class)){
                String simpleName = aclass.getSimpleName();
                Object newInstance = aclass.newInstance();
                ioc.put(simpleName,newInstance);
            }


            else if(aclass.isAnnotationPresent(MyService.class)){
                MyService annotation = aclass.getAnnotation(MyService.class);
                String beanName = annotation.value();
                if(!beanName.equals("")){
                    ioc.put(beanName,aclass.newInstance());
                } else {
                    beanName = lowerFirst(aclass.getSimpleName());
                    ioc.put(beanName,aclass.newInstance());
                }

                //service层往往都是面向接口开发，此时再以接口名为id，放入一份对象到ioc中，便于后期根据接口类型注入
                Class<?>[] interfaces = aclass.getInterfaces();
                for (int j = 0; j < interfaces.length; j++) {
                    Class<?> anInterface = interfaces[j];
                    ioc.put(anInterface.getName(),aclass.newInstance());
                }

                } else {
                continue;
            }
            }


        }




    //首字母小写
    public String lowerFirst(String str){
        char[] chars = str.toCharArray();
        if('A' <= chars[0] && chars[0] <= 'Z' ){
            chars[0] += 32;
        }
        return String.valueOf(chars);
    }

    private List<String> classNameList = new ArrayList<>();
    //扫描类
    //scanPackage：com.lagou.dmmo.-》磁盘上的文件夹
    private void doScanAnnocation(String scanPackage) {
        //递归扫描包。多文件夹下多文件
        String scanPackagePath = Thread.currentThread().getContextClassLoader().getResource("").getPath() + scanPackage.replaceAll("\\.", "/");
        File file = new File(scanPackagePath);

        File [] files = file.listFiles();

        for(File file1:files){
            if(file1.isDirectory()){
                doScanAnnocation(scanPackage+"."+file1.getName());//com.lagou.demo.controller
            } else if(file1.getName().endsWith(".class")){
                String className = scanPackage + "." + file1.getName().replaceAll(".class", "");
                classNameList.add(className);
            }
        }


    }

    //加载配置文件
    private void doLoadConfig(String contextConfigLocation) {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            properties.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //接收处理请求
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doGet(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //处理请求、找到对应的method方法。进行调用
       /* String requestURI = req.getRequestURI();
        Method method = handlerMapping.get(requestURI);
        method.invoke();//此处参数无法完成调用，没有缓存对象====改造inithandelMapping*/

       MyHandler handler = getHandler(req);

       //校验权限，该用户是否有请求权限
        try {
            doCheckPermission(req);
        } catch (Exception e) {
            System.out.println("========="+e.getMessage());
            return ;
        }


        if(handler == null){
           resp.getWriter().write("找你嗷嗷呢？");
           return;
       }
       //参数绑定
        Class<?>[] parameterTypes = handler.getMethod().getParameterTypes();

        Object[] paraValues = new Object[parameterTypes.length];

        Map<String, String[]> parameterMap = req.getParameterMap();

        // 遍历request中所有参数  （填充除了request，response之外的参数）
        for(Map.Entry<String,String[]> param: parameterMap.entrySet()) {
            // name=1&name=2   name [1,2]
            String value = StringUtils.join(param.getValue(), ",");  // 如同 1,2

            // 如果参数和方法中的参数匹配上了，填充数据
            if(!handler.getParamIndexMapping().containsKey(param.getKey())) {continue;}

            // 方法形参确实有该参数，找到它的索引位置，对应的把参数值放入paraValues
            Integer index = handler.getParamIndexMapping().get(param.getKey());//name在第 2 个位置

            paraValues[index] = value;  // 把前台传递过来的参数值填充到对应的位置去

        }

        int requestIndex = handler.getParamIndexMapping().get(HttpServletRequest.class.getSimpleName()); // 0
        paraValues[requestIndex] = req;


        int responseIndex = handler.getParamIndexMapping().get(HttpServletResponse.class.getSimpleName()); // 1
        paraValues[responseIndex] = resp;


        //最终调用
        try {
            handler.getMethod().invoke(handler.getController(),paraValues);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

    }

    private void doCheckPermission(HttpServletRequest req) throws Exception {
        //取出请求参数带的值
        String username = req.getParameter("username");
        System.out.println("开始校验用户权限，用户："+username);
        System.out.println("拥有权限的用户："+userNameList);

        //取出注解@Sercurity中的带入带值
           if(!userNameList.contains(username)){
               throw new Exception("该用户没有访问权限、用户名："+username);
           }


        //匹配值、若是允许用户，则可以进入，否则输出404


    }

    private MyHandler getHandler(HttpServletRequest req) {
        if(handlerMapping.isEmpty()){ return null;}

        String requestURI = req.getRequestURI();

        for(MyHandler myHandler:handlerMapping){
            Matcher matcher = myHandler.getPattern().matcher(requestURI);
            if(!matcher.matches()){
                continue;
            }
            return myHandler;

        }
            return null;
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPut(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doDelete(req, resp);
    }
}
