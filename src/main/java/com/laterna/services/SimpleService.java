package com.laterna.services;

public class SimpleService implements Simple {
    @Override
    public void process() {
        System.out.println("processing");
    }
}
