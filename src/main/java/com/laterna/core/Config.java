package com.laterna.core;

import com.laterna.annotations.Inject;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Inject
public class Config {
    private static final Map<String, Object> cache = new ConcurrentHashMap<>();;

    private static volatile Config instance;

    public static Config getInstance() {
        if (instance == null) {
            synchronized (DIContainer.class) {
                if (instance == null) {
                    instance = new Config();
                }
            }
        }
        return instance;
    }

    public  Object get(String key) {
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        Properties props = parseProperties();
        Object value = convertValue(props.getProperty(key));

        cache.put(key, value);

        return value;
    }

    public Object get(String key, Object defaultValue) {
        Object obj = get(key);

        return obj == null ? defaultValue : obj;
    }

    private Properties parseProperties() {
        try {
            Properties defaultProps = parseConcreteProperties("application.properties");
            String mode = defaultProps.getProperty("mode");

            if(mode.equalsIgnoreCase("dev")) {
                return parseConcreteProperties("application-dev.properties");
            } else if(mode.equalsIgnoreCase("prod")) {
                return parseConcreteProperties("application-prod.properties");
            }

            throw new RuntimeException("Unknown mode " + mode + " or file doesn't exist");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Properties parseConcreteProperties(String propertiesFile) {
        try (InputStream inputStream = Config.class.getClassLoader().getResourceAsStream(propertiesFile))  {
            if (inputStream == null) {
                throw new RuntimeException("Config file doesn't exist");
            }

            Properties props = new Properties();
            props.load(inputStream);

            inputStream.close();
            return props;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object convertValue(String value) {
        if (value == null) {
            return null;
        }

        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {}

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {}

        return value;
    }
}
