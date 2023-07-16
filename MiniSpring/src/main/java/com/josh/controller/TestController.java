package com.josh.controller;

import com.framework.annotations.Autowired;
import com.framework.annotations.Controller;
import com.framework.annotations.RequestMapping;
import com.framework.annotations.RequestParam;
import com.josh.service.ITestService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@RequestMapping("/test")
public class TestController {

    @Autowired
    private ITestService iTestService;

    @RequestMapping("/query")
    public void query(HttpServletRequest req, HttpServletResponse resp, @RequestParam("name") String name){
        String result = iTestService.query(name);
        try{
            resp.getWriter().write(result);
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    @RequestMapping("/add")
    public void add(HttpServletRequest req, HttpServletResponse resp, @RequestParam("name") String name){
        System.out.println("add");
    }

    @RequestMapping("/remove")
    public void remove(HttpServletRequest req, HttpServletResponse resp, @RequestParam("name") String name){
        System.out.println("remove");
    }
}
