package loggerfinder;

import java.lang.*;
import java.util.*;

public class SimpleLoggerFinder extends System.LoggerFinder {

    static {
        try {
            long sleep = new Random().nextLong(1000L) + 1L;
            System.out.println("Logger finder service load sleep value: " + sleep);
            // simulate a slow load service
            Thread.sleep(sleep);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
     @Override
     public System.Logger getLogger(String name, Module module) {
         return new NoOpLogger(name);
     }

    private static class NoOpLogger implements System.Logger {
        private final String name;

        public NoOpLogger(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isLoggable(Level level) {
            return true;
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
            System.out.println("TEST LOGGER: " + msg);
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String format, Object... params) {
            System.out.println("TEST LOGGER: " + Arrays.asList(params));

        }
    }
}