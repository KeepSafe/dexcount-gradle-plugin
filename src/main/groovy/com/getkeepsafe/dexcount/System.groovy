package com.getkeepsafe.dexcount

interface System {
    String env(String name);
    String property(String key);
    String property(String key, String defaultValue);

    static final class Real implements System {
        @Override String env(String name) {
            return java.lang.System.getenv(name)
        }

        @Override String property(String key) {
            return java.lang.System.getProperty(key);
        }

        @Override String property(String key, String defaultValue) {
            return java.lang.System.getProperty(key, defaultValue);
        }
    }
}