package com.laterna.controllers;

import com.laterna.annotations.Autowired;
import com.laterna.annotations.Controller;
import com.laterna.annotations.Route;
import com.laterna.core.Config;
import com.laterna.enums.RouteMethod;
import com.laterna.services.Simple;
import com.laterna.services.SimpleService;

import java.util.ArrayList;
import java.util.List;

@Controller
public class SimpleController {
    @Autowired
    private Simple service;

    @Route(uri = "/hello", method = RouteMethod.GET)
    public List<String> hello() {
        List<String> list = new ArrayList<>();
        list.add("asd");
        service.process();
        return list;
    }

    @Route(uri = "/ibjec", method = RouteMethod.GET)
    public String ibjec() {
        System.out.println("hello");

        return "xuy";
    }

    @Autowired
    public void setAsd(Simple service) {

    }
}
