package com.laterna.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laterna.annotations.Controller;
import com.laterna.annotations.PathVariable;
import com.laterna.annotations.Route;
import com.laterna.services.Simple;
import com.laterna.services.SimpleService;
import org.reflections.Reflections;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DispatcherServlet extends HttpServlet {
    private static final Map<String, MethodHandler> routes = new HashMap<>();

    public DispatcherServlet() {
        DIContainer.getInstance().register(Simple.class, SimpleService.class);
        initializeControllers();
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
        handleRequest(req, resp);
    }

    public void handleRequest(HttpServletRequest req, HttpServletResponse resp) {
        for (String route : routes.keySet()) {
            if (route.contains("{")) {
                // Если маршрут содержит переменные (например, {id}), используем регулярное выражение
                String routeRegex = convertToRegex(route);
                Pattern pattern = Pattern.compile(routeRegex);
                Matcher matcher = pattern.matcher(req.getRequestURI());

                if (matcher.matches()) {
                    MethodHandler handler = routes.get(route);

                    try {
                        Object[] args = extractPathVariables(matcher, handler.method);

                        Object result = handler.invoke(args);

                        if (result != null) {
                            sendJsonResponse(resp, result);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to execute route: " + route, e);
                    }

                    return;
                }
            } else {
                String key = req.getMethod() + ":" + req.getRequestURI();
                if (key.equals(route)) {
                    MethodHandler handler = routes.get(route);

                    try {
                        Object result = handler.invoke();

                        if (result != null) {
                            sendJsonResponse(resp, result);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to method handler invoke: " + handler, e);
                    }
                }
            }
        }

        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    private void sendJsonResponse(HttpServletResponse resp, Object result) {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        ObjectMapper mapper = new ObjectMapper();

        try {
            resp.getWriter().write(mapper.writeValueAsString(result));
        } catch (IOException e) {
            throw new RuntimeException("Failed to send json response", e);
        }
    }


    private Object[] extractPathVariables(Matcher matcher, Method method) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(PathVariable.class)) {
                String value = matcher.group(i + 1); // matcher.group(1) для первой переменной
                args[i] = convertType(value, parameters[i].getType());
            }
        }

        return args;
    }

    private Object convertType(String value, Class<?> type) {
        if (type == Long.class) {
            return Long.parseLong(value);
        }

        return value;
    }

    private String convertToRegex(String route) {
        return route.replaceAll("\\{\\w+}", "(\\\\w+)");
    }

    public void initializeControllers() {
        Reflections reflections = new Reflections("com.laterna.controllers");

        Set<Class<?>> controllerClasses = reflections.getTypesAnnotatedWith(Controller.class);

        for (Class<?> clazz : controllerClasses) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Route.class)) {
                    Route route = method.getAnnotation(Route.class);

                    String key = route.method() + ":" + route.uri();

                    routes.put(key, new MethodHandler(clazz, method));

                    System.out.println("Registered route: " + key);
                }
            }
        }
    }

    private static class MethodHandler {
        private final Class<?> controllerClass;
        private final Method method;

        public MethodHandler(Class<?> controllerClass, Method method) {
            this.controllerClass = controllerClass;
            this.method = method;
        }

        public Object invoke(Object... args) throws Exception {
            Object controllerInstance = DIContainer.getInstance().getInject(controllerClass);

            return method.invoke(controllerInstance, args);
        }
    }
}
