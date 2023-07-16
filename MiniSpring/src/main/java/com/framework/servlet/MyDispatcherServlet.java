package com.framework.servlet;

import com.framework.annotations.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.stream.Stream;

public class MyDispatcherServlet extends HttpServlet {

    //ioc container
    private Map<String, Object> ioc = new HashMap<String, Object>();

    //load config object
    private Properties contextConfig = new Properties();

    //scan all class name with package name
    private List<String> classList = new ArrayList<>();

    //mapping url and methods
    private Map<String, Method> handlerMapping = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception Detail:" + Arrays.toString(e.getStackTrace()));
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //load config
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //scan classes
        doScanner(contextConfig.getProperty("scanPackage"));

        //create instances in IOC container
        doInstance();

        //inject dependencies
        doAutowired();

        //initialize handlermapping url
        doInitHandlerMapping();

        System.out.println("MySpring framework is initialized!");

    }

    /**
     * @param req
     * @param resp
     * @throws Exception
     * @Description Receive request from browser and execute methods
     */
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath, "");

        if (!handlerMapping.containsKey(url)) {
            resp.getWriter().write("Exception Code : 404\nNot Found!");
            return;
        }

        Map<String, String[]> params = req.getParameterMap();
        Method method = handlerMapping.get(url);
        //拿到方法的形参列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        //定义实参列表
        Object[] paramValues = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (HttpServletRequest.class == parameterType) {
                paramValues[i] = req;
            } else if (HttpServletResponse.class == parameterType) {
                paramValues[i] = resp;
            } else {
                Annotation[][] parameterAnnotations = method.getParameterAnnotations();
                for (Annotation v : parameterAnnotations[i]) {
                    if (v instanceof RequestParam) {
                        String paramName = ((RequestParam) v).value();
                        if (!"".equals(paramName.trim())) {
                            String value = params.get(paramName)[0];
                            //获取指定构造方法
                            Constructor constructor = parameterType.getConstructor(new Class[]{String.class});
                            paramValues[i] = constructor.newInstance(value);
                        }
                    }
                }
            }
        }

        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        //方法调用
        method.invoke(ioc.get(beanName), paramValues);
    }

    /**
     * @param contextConfigLocation
     * @Description load context config file
     */
    private void doLoadConfig(String contextConfigLocation) {
        InputStream resource = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(resource);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            //terminate the file stream
            if (resource != null) {
                try {
                    resource.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * @param scanPackage
     * @Description scan classes in assigned package and add them into classList
     */
    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classFile = new File(url.getFile());
        for (File file : classFile.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                String className = (scanPackage + "." + file.getName()).replace(".class", "");
                classList.add(className);
            }
        }
    }

    /**
     * @Description create new instance for every class with @controller or @service annotation
     */
    private void doInstance() {
        classList.forEach(o -> {
            try {
                Class<?> classes = Class.forName(o);

                if (classes.isAnnotationPresent(Controller.class)) {
                    String beanName = toLowerFirstCase(classes.getSimpleName());
                    ioc.put(beanName, classes.newInstance());
                } else if (classes.isAnnotationPresent(Service.class)) {
                    String beanName = toLowerFirstCase(classes.getSimpleName());

                    Service service = classes.getAnnotation(Service.class);
                    if (!"".equals(service.value())) {
                        beanName = service.value();
                    }

                    //if the class implements an interface, it has to create its instance
                    for (Class<?> i : classes.getInterfaces()) {
                        if (ioc.containsKey(i)) {
                            throw new Exception("This bean already exists");
                        }
                        beanName = i.getName();
                    }
                    ioc.put(beanName, classes.newInstance());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * @Description for Dependencies injection
     */
    private void doAutowired() {
        ioc.forEach((k, v) -> {
            Field[] fields = v.getClass().getDeclaredFields();
            Stream.of(fields)
                    .filter(f -> f.isAnnotationPresent(Autowired.class))
                    .forEach(f -> {
                        Autowired annotation = f.getAnnotation(Autowired.class);
                        String beanName = annotation.value().trim();
                        //by type inject dependencies
                        if ("".equals(beanName)) {
                            beanName = f.getType().getName();
                        }

                        f.setAccessible(true);
                        try {
                            f.set(v, ioc.get(beanName));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }

                    });
        });
    }


    private void doInitHandlerMapping() {
        ioc.forEach((k, v) -> {
            Class<?> classes = v.getClass();
            if (classes.isAnnotationPresent(Controller.class)) {
                String baseUrl = "";

                if (classes.isAnnotationPresent(RequestMapping.class)) {
                    RequestMapping annotation = classes.getAnnotation(RequestMapping.class);
                    baseUrl = annotation.value();
                }

                Method[] methods = classes.getMethods();

                String finalUrl = baseUrl;
                Stream.of(methods)
                        .filter(m -> m.isAnnotationPresent(RequestMapping.class))
                        .forEach(m -> {
                            RequestMapping annotation = m.getAnnotation(RequestMapping.class);
                            String url = (finalUrl + annotation.value()).replaceAll("/+", "/");
                            handlerMapping.put(url, m);
                        });
            }
        });
    }

    /**
     * @param className
     * @return
     * @Description change the first letter to lower case
     */
    private String toLowerFirstCase(String className) {
        char[] chars = className.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }
}
