/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


package sun.util.logging;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.MessageFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import sun.misc.JavaLangAccess;
import sun.misc.SharedSecrets;

/**
 * Platform logger provides an API for the JRE components to log
 * messages.  This enables the runtime components to eliminate the
 * static dependency of the logging facility and also defers the
 * java.util.logging initialization until it is enabled.
 * In addition, the PlatformLogger API can be used if the logging
 * module does not exist.
 *
 * If the logging facility is not enabled, the platform loggers
 * will output log messages per the default logging configuration
 * (see below). In this implementation, it does not log the
 * the stack frame information issuing the log message.
 *
 * When the logging facility is enabled (at startup or runtime),
 * the java.util.logging.Logger will be created for each platform
 * logger and all log messages will be forwarded to the Logger
 * to handle.
 *
 * Logging facility is "enabled" when one of the following
 * conditions is met:
 * 1) a system property "java.util.logging.config.class" or
 *    "java.util.logging.config.file" is set
 * 2) java.util.logging.LogManager or java.util.logging.Logger
 *    is referenced that will trigger the logging initialization.
 *
 * Default logging configuration:
 *   global logging level = INFO
 *   handlers = java.util.logging.ConsoleHandler
 *   java.util.logging.ConsoleHandler.level = INFO
 *   java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter
 *
 * Limitation:
 * <JAVA_HOME>/lib/logging.properties is the system-wide logging
 * configuration defined in the specification and read in the
 * default case to configure any java.util.logging.Logger instances.
 * Platform loggers will not detect if <JAVA_HOME>/lib/logging.properties
 * is modified. In other words, unless the java.util.logging API
 * is used at runtime or the logging system properties is set,
 * the platform loggers will use the default setting described above.
 * The platform loggers are designed for JDK developers use and
 * this limitation can be workaround with setting
 * -Djava.util.logging.config.file system property.
 *
 * @since 1.7
 */
public class PlatformLogger {
    // Same values as java.util.logging.Level for easy mapping
    public static final int OFF     = Integer.MAX_VALUE;
    public static final int SEVERE  = 1000;
    public static final int WARNING = 900;
    public static final int INFO    = 800;
    public static final int CONFIG  = 700;
    public static final int FINE    = 500;
    public static final int FINER   = 400;
    public static final int FINEST  = 300;
    public static final int ALL     = Integer.MIN_VALUE;

    private static final int defaultLevel = INFO;
    private static boolean loggingEnabled;
    static {
        loggingEnabled = AccessController.doPrivileged(
            new PrivilegedAction<Boolean>() {
                public Boolean run() {
                    String cname = System.getProperty("java.util.logging.config.class");
                    String fname = System.getProperty("java.util.logging.config.file");
                    return (cname != null || fname != null);
                }
            });
    }

    // Table of known loggers.  Maps names to PlatformLoggers.
    private static Map<String,WeakReference<PlatformLogger>> loggers =
        new HashMap<>();

    /**
     * Returns a PlatformLogger of a given name.
     */
    public static synchronized PlatformLogger getLogger(String name) {
        PlatformLogger log = null;
        WeakReference<PlatformLogger> ref = loggers.get(name);
        if (ref != null) {
            log = ref.get();
        }
        if (log == null) {
            log = new PlatformLogger(name);
            loggers.put(name, new WeakReference<>(log));
        }
        return log;
    }

    /**
     * Initialize java.util.logging.Logger objects for all platform loggers.
     * This method is called from LogManager.readPrimordialConfiguration().
     */
    public static synchronized void redirectPlatformLoggers() {
        if (loggingEnabled || !LoggingSupport.isAvailable()) return;

        loggingEnabled = true;
        for (Map.Entry<String, WeakReference<PlatformLogger>> entry : loggers.entrySet()) {
            WeakReference<PlatformLogger> ref = entry.getValue();
            PlatformLogger plog = ref.get();
            if (plog != null) {
                plog.newJavaLogger();
            }
        }
    }

    /**
     * Creates a new JavaLogger that the platform logger uses
     */
    private void newJavaLogger() {
        logger = new JavaLogger(logger.name, logger.effectiveLevel);
    }

    // logger may be replaced with a JavaLogger object
    // when the logging facility is enabled
    private volatile LoggerProxy logger;

    private PlatformLogger(String name) {
        if (loggingEnabled) {
            this.logger = new JavaLogger(name);
        } else {
            this.logger = new LoggerProxy(name);
        }
    }

    /**
     * A convenience method to test if the logger is turned off.
     * (i.e. its level is OFF).
     */
    public boolean isEnabled() {
        return logger.isEnabled();
    }

    /**
     * Gets the name for this platform logger.
     */
    public String getName() {
        return logger.name;
    }

    /**
     * Returns true if a message of the given level would actually
     * be logged by this logger.
     */
    public boolean isLoggable(int level) {
        return logger.isLoggable(level);
    }

    /**
     * Gets the current log level.  Returns 0 if the current effective level
     * is not set (equivalent to Logger.getLevel() returns null).
     */
    public int getLevel() {
        return logger.getLevel();
    }

    /**
     * Sets the log level.
     */
    public void setLevel(int newLevel) {
        logger.setLevel(newLevel);
    }

    /**
     * Logs a SEVERE message.
     */
    public void severe(String msg) {
        logger.doLog(SEVERE, msg);
    }

    public void severe(String msg, Throwable t) {
        logger.doLog(SEVERE, msg, t);
    }

    public void severe(String msg, Object... params) {
        logger.doLog(SEVERE, msg, params);
    }

    /**
     * Logs a WARNING message.
     */
    public void warning(String msg) {
        logger.doLog(WARNING, msg);
    }

    public void warning(String msg, Throwable t) {
        logger.doLog(WARNING, msg, t);
    }

    public void warning(String msg, Object... params) {
        logger.doLog(WARNING, msg, params);
    }

    /**
     * Logs an INFO message.
     */
    public void info(String msg) {
        logger.doLog(INFO, msg);
    }

    public void info(String msg, Throwable t) {
        logger.doLog(INFO, msg, t);
    }

    public void info(String msg, Object... params) {
        logger.doLog(INFO, msg, params);
    }

    /**
     * Logs a CONFIG message.
     */
    public void config(String msg) {
        logger.doLog(CONFIG, msg);
    }

    public void config(String msg, Throwable t) {
        logger.doLog(CONFIG, msg, t);
    }

    public void config(String msg, Object... params) {
        logger.doLog(CONFIG, msg, params);
    }

    /**
     * Logs a FINE message.
     */
    public void fine(String msg) {
        logger.doLog(FINE, msg);
    }

    public void fine(String msg, Throwable t) {
        logger.doLog(FINE, msg, t);
    }

    public void fine(String msg, Object... params) {
        logger.doLog(FINE, msg, params);
    }

    /**
     * Logs a FINER message.
     */
    public void finer(String msg) {
        logger.doLog(FINER, msg);
    }

    public void finer(String msg, Throwable t) {
        logger.doLog(FINER, msg, t);
    }

    public void finer(String msg, Object... params) {
        logger.doLog(FINER, msg, params);
    }

    /**
     * Logs a FINEST message.
     */
    public void finest(String msg) {
        logger.doLog(FINEST, msg);
    }

    public void finest(String msg, Throwable t) {
        logger.doLog(FINEST, msg, t);
    }

    public void finest(String msg, Object... params) {
        logger.doLog(FINEST, msg, params);
    }

    /**
     * Default platform logging support - output messages to
     * System.err - equivalent to ConsoleHandler with SimpleFormatter.
     */
    static class LoggerProxy {
        private static final PrintStream defaultStream = System.err;
        private static final String lineSeparator = AccessController.doPrivileged(
            new PrivilegedAction<String>() {
                public String run() {
                    return System.getProperty("line.separator");
                }
            });

        final String name;
        volatile int levelValue;
        volatile int effectiveLevel = 0; // current effective level value

        LoggerProxy(String name) {
            this(name, defaultLevel);
        }

        LoggerProxy(String name, int level) {
            this.name = name;
            this.levelValue = level == 0 ? defaultLevel : level;
        }

        boolean isEnabled() {
            return levelValue != OFF;
        }

        int getLevel() {
            return effectiveLevel;
        }

        void setLevel(int newLevel) {
            levelValue = newLevel;
            effectiveLevel = newLevel;
        }

        void doLog(int level, String msg) {
            if (level < levelValue || levelValue == OFF) {
                return;
            }
            defaultStream.println(format(level, msg, null));
        }

        void doLog(int level, String msg, Throwable thrown) {
            if (level < levelValue || levelValue == OFF) {
                return;
            }
            defaultStream.println(format(level, msg, thrown));
        }

        void doLog(int level, String msg, Object... params) {
            if (level < levelValue || levelValue == OFF) {
                return;
            }
            String newMsg = formatMessage(msg, params);
            defaultStream.println(format(level, newMsg, null));
        }

        public boolean isLoggable(int level) {
            if (level < levelValue || levelValue == OFF) {
                return false;
            }
            return true;
        }

        private static final String format = "{0,date} {0,time}";

        private Object args[] = new Object[1];
        private MessageFormat formatter;
        private Date dat;

        // Copied from java.util.logging.Formatter.formatMessage
        private String formatMessage(String format, Object... parameters) {
            // Do the formatting.
            try {
                if (parameters == null || parameters.length == 0) {
                    // No parameters.  Just return format string.
                    return format;
                }
                // Is it a java.text style format?
                // Ideally we could match with
                // Pattern.compile("\\{\\d").matcher(format).find())
                // However the cost is 14% higher, so we cheaply check for
                // 1 of the first 4 parameters
                if (format.indexOf("{0") >= 0 || format.indexOf("{1") >=0 ||
                            format.indexOf("{2") >=0|| format.indexOf("{3") >=0) {
                    return java.text.MessageFormat.format(format, parameters);
                }
                return format;
            } catch (Exception ex) {
                // Formatting failed: use format string.
                return format;
            }
        }

        private synchronized String format(int level, String msg, Throwable thrown) {
            StringBuffer sb = new StringBuffer();
            // Minimize memory allocations here.
            if (dat == null) {
                dat = new Date();
                formatter = new MessageFormat(format);
            }
            dat.setTime(System.currentTimeMillis());
            args[0] = dat;
            StringBuffer text = new StringBuffer();
            formatter.format(args, text, null);
            sb.append(text);
            sb.append(" ");
            sb.append(getCallerInfo());
            sb.append(lineSeparator);
            sb.append(PlatformLogger.getLevelName(level));
            sb.append(": ");
            sb.append(msg);
            if (thrown != null) {
                try {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    thrown.printStackTrace(pw);
                    pw.close();
                    sb.append(sw.toString());
                } catch (Exception ex) {
                    throw new AssertionError(ex);
                }
            }

            return sb.toString();
        }

        // Returns the caller's class and method's name; best effort
        // if cannot infer, return the logger's name.
        private String getCallerInfo() {
            String sourceClassName = null;
            String sourceMethodName = null;

            JavaLangAccess access = SharedSecrets.getJavaLangAccess();
            Throwable throwable = new Throwable();
            int depth = access.getStackTraceDepth(throwable);

            String logClassName = "sun.util.logging.PlatformLogger";
            boolean lookingForLogger = true;
            for (int ix = 0; ix < depth; ix++) {
                // Calling getStackTraceElement directly prevents the VM
                // from paying the cost of building the entire stack frame.
                StackTraceElement frame =
                    access.getStackTraceElement(throwable, ix);
                String cname = frame.getClassName();
                if (lookingForLogger) {
                    // Skip all frames until we have found the first logger frame.
                    if (cname.equals(logClassName)) {
                        lookingForLogger = false;
                    }
                } else {
                    if (!cname.equals(logClassName)) {
                        // We've found the relevant frame.
                        sourceClassName = cname;
                        sourceMethodName = frame.getMethodName();
                        break;
                    }
                }
            }

            if (sourceClassName != null) {
                return sourceClassName + " " + sourceMethodName;
            } else {
                return name;
            }
        }
    }

    /**
     * JavaLogger forwards all the calls to its corresponding
     * java.util.logging.Logger object.
     */
    static class JavaLogger extends LoggerProxy {
        private static final Map<Integer, Object> levelObjects =
            new HashMap<>();

        static {
            if (LoggingSupport.isAvailable()) {
                // initialize the map to Level objects
                getLevelObjects();
            }
        }

        private static void getLevelObjects() {
            // get all java.util.logging.Level objects
            int[] levelArray = new int[] {OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, ALL};
            for (int l : levelArray) {
                Object level = LoggingSupport.parseLevel(getLevelName(l));
                levelObjects.put(l, level);
            }
        }

        private final Object javaLogger;
        JavaLogger(String name) {
            this(name, 0);
        }

        JavaLogger(String name, int level) {
            super(name, level);
            this.javaLogger = LoggingSupport.getLogger(name);
            if (level != 0) {
                // level has been updated and so set the Logger's level
                LoggingSupport.setLevel(javaLogger, levelObjects.get(level));
            }
        }

       /**
        * Let Logger.log() do the filtering since if the level of a
        * platform logger is altered directly from
        * java.util.logging.Logger.setLevel(), the levelValue will
        * not be updated.
        */
        void doLog(int level, String msg) {
            LoggingSupport.log(javaLogger, levelObjects.get(level), msg);
        }

        void doLog(int level, String msg, Throwable t) {
            LoggingSupport.log(javaLogger, levelObjects.get(level), msg, t);
        }

        void doLog(int level, String msg, Object... params) {
            // only pass String objects to the j.u.l.Logger which may
            // be created by untrusted code
            int len = (params != null) ? params.length : 0;
            Object[] sparams = new String[len];
            for (int i = 0; i < len; i++) {
                sparams [i] = String.valueOf(params[i]);
            }
            LoggingSupport.log(javaLogger, levelObjects.get(level), msg, sparams);
        }

        boolean isEnabled() {
            Object level = LoggingSupport.getLevel(javaLogger);
            return level == null || level.equals(levelObjects.get(OFF)) == false;
        }

        int getLevel() {
            Object level = LoggingSupport.getLevel(javaLogger);
            if (level != null) {
                for (Map.Entry<Integer, Object> l : levelObjects.entrySet()) {
                    if (level == l.getValue()) {
                        return l.getKey();
                    }
                }
            }
            return 0;
        }

        void setLevel(int newLevel) {
            levelValue = newLevel;
            LoggingSupport.setLevel(javaLogger, levelObjects.get(newLevel));
        }

        public boolean isLoggable(int level) {
            return LoggingSupport.isLoggable(javaLogger, levelObjects.get(level));
        }
    }

    private static String getLevelName(int level) {
        switch (level) {
            case OFF     : return "OFF";
            case SEVERE  : return "SEVERE";
            case WARNING : return "WARNING";
            case INFO    : return "INFO";
            case CONFIG  : return "CONFIG";
            case FINE    : return "FINE";
            case FINER   : return "FINER";
            case FINEST  : return "FINEST";
            case ALL     : return "ALL";
            default      : return "UNKNOWN";
        }
    }

}
