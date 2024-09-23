package com.laterna.controllers;

import com.laterna.annotations.Controller;
import com.laterna.annotations.Route;
import com.laterna.enums.RouteMethod;

@Controller
public class UserController {
    @Route(uri = "/users", method = RouteMethod.POST)
    public void get() {
        System.out.println("get method from user controller");
    }
}
