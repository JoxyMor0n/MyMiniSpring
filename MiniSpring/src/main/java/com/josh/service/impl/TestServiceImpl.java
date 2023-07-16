package com.josh.service.impl;

import com.framework.annotations.Service;
import com.josh.service.ITestService;

@Service
public class TestServiceImpl implements ITestService {
    @Override
    public String query(String name) {
        return "Hello " + name + " !";
    }
}
