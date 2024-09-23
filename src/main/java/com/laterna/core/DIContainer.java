package com.laterna.core;

import com.laterna.annotations.Autowired;
import com.laterna.annotations.Inject;
import com.laterna.exceptions.CyclicalDependencyException;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class DIContainer {
    private final Map<Class<?>, Object> injects = new ConcurrentHashMap<>();
    private final Map<Class<?>, Class<?>> typeMappings = new ConcurrentHashMap<>();
    private final ThreadLocal<Set<Class<?>>> creating = ThreadLocal.withInitial(HashSet::new);

    private static volatile DIContainer instance;

    private DIContainer() {
        initialize(Config.getInstance().get("package").toString());
    }

    public static DIContainer getInstance() {
        if (instance == null) {
            synchronized (DIContainer.class) {
                if (instance == null) {
                    instance = new DIContainer();
                }
            }
        }
        return instance;
    }

    public void initialize(String basePackage) {
        Reflections reflections = new Reflections(basePackage);

        Set<Class<?>> combinedClasses = reflections.getTypesAnnotatedWith(Inject.class);

        for (Class<?> clazz : combinedClasses) {
            Object instance = createInstance(clazz);

            if(instance != null) {
                injects.put(clazz, instance);
                injectDependencies(instance);
            }
        }
    }

    public <T> T getInject(Class<T> clazz) {
        Object instance = injects.containsKey(clazz) ? injects.get(clazz) : createInstance(clazz);

        return clazz.cast(instance);
    }

    public <T> void register(Class<T> baseClass, Class<? extends T> implClass) {
        typeMappings.put(baseClass, implClass);
    }

    private Object createInstance(Class<?> clazz) {
        if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
            if (!typeMappings.containsKey(clazz)) {
                throw new RuntimeException("No implementation registered for " + clazz.getName());
            }
            clazz = typeMappings.get(clazz);
        }

        if(creating.get().contains(clazz)) {
            throw new CyclicalDependencyException();
        }
        creating.get().add(clazz);

        if(injects.containsKey(clazz)) {
            return injects.get(clazz);
        }

        try {
            Object instance = injectConstructor(clazz);
            injectMethods(instance);

            injects.put(clazz, instance);
            injectDependencies(instance);

            return instance;
        }  catch (Exception e) {
            throw new RuntimeException("Failed to instantiate: " + e.getMessage(), e);
        } finally {
            creating.get().remove(clazz);
        }
    }

    private Object injectConstructor(Class<?> clazz) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        Constructor<?> constructor = getAutowiredConstructor(clazz) ;

        if(constructor == null) {
            constructor = getFirstConstructor(clazz);
        }

        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] params = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            params[i] = getInject(parameterTypes[i]);
        }

        return constructor.newInstance(params);
    }

    private void injectMethods(Object instance) throws InvocationTargetException, IllegalAccessException {
        Method[] methods = instance.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if(method.isAnnotationPresent(Autowired.class)) {
                Class<?>[] methodParameterTypes = method.getParameterTypes();
                Object[] methodParams = new Object[methodParameterTypes.length];

                for (int i = 0; i < methodParameterTypes.length; i++) {
                    methodParams[i] = getInject(methodParameterTypes[i]);
                }

                method.setAccessible(true);
                method.invoke(instance, methodParams);
            }
        }
    }

    private Constructor<?> getAutowiredConstructor(Class<?> clazz) {
        for(Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if(constructor.isAnnotationPresent(Autowired.class)) {
                return constructor;
            }
        }

        return null;
    }

    private Constructor<?> getFirstConstructor(Class<?> clazz) {
        return clazz.getDeclaredConstructors()[0];
    }

    private void injectDependencies(Object instance) {
        Class<?> clazz = instance.getClass();

        for (Field field : clazz.getDeclaredFields()) {
            if(field.isAnnotationPresent(Autowired.class)) {
                Object dependency = getInject(field.getType());
                if(dependency != null) {
                    try {
                        field.setAccessible(true);
                        field.set(instance, dependency);
                    } catch (IllegalAccessException e) {
                        System.err.println("Failed to inject dependency into " + field.getName() + ": " + e.getMessage());
                    }
                }
            }
        }
    }
}
