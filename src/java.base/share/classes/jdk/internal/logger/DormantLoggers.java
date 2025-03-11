package jdk.internal.logger;

import jdk.internal.access.JavaUtilLoggingAccess;
import jdk.internal.access.SharedSecrets;
import sun.security.ssl.SSLLogger;
import sun.util.logging.PlatformLogger;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DormantLoggers {

    static class LoggerInfo {
        public final WeakReference<System.Logger> logger;
        public final PlatformLogger.Level level;

        public LoggerInfo(System.Logger logger, PlatformLogger.Level level) {
            this.logger = new WeakReference<>(logger);
            this.level = level;
        }
    }

    private static final ConcurrentHashMap<String, LoggerInfo> dormantLoggers
            = new ConcurrentHashMap<>();

    /**
     * Returns a dormant logger that can be managed remotely via
     * jcmd. This method is similar to
     * {@link #getLazyLogger(String, Module)}, but attempts to set the
     * initial log level to {@link PlatformLogger.Level#OFF}.
     *
     * @param name the name of the logger
     * @param module the module on behalf of which the logger is created
     * @return a dormant logger that can be managed remotely
     */
    public static System.Logger getDormantLogger(String name, Module module) {
        return dormantLoggers.computeIfAbsent(name, k -> {
            var newLogger = LazyLoggers.getLazyLogger(k, module);
            PlatformLogger.Level level = PlatformLogger.Level.OFF;
            if (newLogger instanceof LazyLoggers.JdkLazyLogger lazy &&
                    lazy.loggerAccessor.wrapped() instanceof
                        PlatformLogger.ConfigurableBridge.LoggerConfiguration lc) {
                lc.setPlatformLevel(level);
            }
            return new LoggerInfo(newLogger, level);
        }).logger.get();
    }

    /**
     * Returns a cached dormant logger if it exists in the cache.
     *
     * @param logger the name of the logger
     * @return the cached dormant logger, or null if not found
     */
    public static System.Logger getCachedDormantLogger(String logger) {
        return dormantLoggers.get(logger).logger.get();
    }

    /**
     * Sets the logging level for a dormant logger.
     *
     * If the specified logger is an instance of {@link LazyLoggers.JdkLazyLogger},
     * its platform level will be updated directly. Otherwise, the level change will be
     * propagated through the Java Util Logging API.
     *
     * After updating the logging level, this method notifies any interested parties
     * about the level change using the {@link #notifyLevelChange(String)} method.
     *
     * @param logger the name of the logger whose level should be changed
     * @param level the new logging level as a string representation of
     *             {@link PlatformLogger.Level}
     */
    public static void setDormantLoggerLevel(String logger, String level) {
        var l = getCachedDormantLogger(logger);
        if (l == null) {
            dormantLoggers.remove(logger);
            return;
        }
        if (l instanceof LazyLoggers.JdkLazyLogger lazy &&
                lazy.loggerAccessor.wrapped() instanceof PlatformLogger.ConfigurableBridge.LoggerConfiguration lc) {
            PlatformLogger.Level newLevel = PlatformLogger.Level.valueOf(level);
            // keep copy of Logger Levels since the JUL framework resets System.Loggers
            // to Level INFO when initialized. This value is only used for environment
            // where JavaUtilLoggingAccess is null (JUL framework not loaded)
            dormantLoggers.replace(logger, new LoggerInfo(l, newLevel));
            lc.setPlatformLevel(newLevel);
        } else {
            JavaUtilLoggingAccess jla = SharedSecrets.getJavaUtilLoggingAccess();
            if (jla != null) {
                // j.u.logging impl has been triggered
                jla.setLevel(logger, level);
            }

        }
        notifyLevelChange(logger);
    }

    /**
     * Retrieves the current logging level of the given System.Logger instance.
     *
     * @param l the System.Logger instance to retrieve the logging level from
     * @return the current logging level, or null if the logger does not support level retrieval
     */
    public static PlatformLogger.Level getLevel(System.Logger l) {
        if (l instanceof LazyLoggers.JdkLazyLogger lazy &&
                lazy.loggerAccessor.wrapped() instanceof PlatformLogger.ConfigurableBridge.LoggerConfiguration lc) {
            return lc.getPlatformLevel();
        } else {
            JavaUtilLoggingAccess jla = SharedSecrets.getJavaUtilLoggingAccess();
            if (jla != null) {
                // j.u.logging impl has been triggered
                PlatformLogger.Level lev = jla.getLevel(l.getName());
                return lev;
            }
        }
        return null;
    }

    public static void notifyLevelChange(String loggerName) {
        switch (loggerName) {
            case "javax.net.ssl" -> SSLLogger.notifyLevelChange();
        }
    }

    public static void reloadLevels() {
        System.err.println("RELOADING LEVELS");
        JavaUtilLoggingAccess jla = SharedSecrets.getJavaUtilLoggingAccess();
        assert(jla != null);
        Iterator<Map.Entry<String, LoggerInfo >> iter = dormantLoggers.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, LoggerInfo> entry = iter.next();
            System.Logger sl = getCachedDormantLogger(entry.getKey());
            if (sl != null) {
                jla.setLevel(entry.getKey(), entry.getValue().level.name());
            } else {
                // Logger was GC'ed, remove the reference
                iter.remove();
            }
        }
    }
}
