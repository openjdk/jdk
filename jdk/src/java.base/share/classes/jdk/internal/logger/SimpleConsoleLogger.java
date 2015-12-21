/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.logger;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.StackWalker.StackFrame;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.lang.System.Logger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import sun.util.logging.PlatformLogger;
import sun.util.logging.PlatformLogger.ConfigurableBridge.LoggerConfiguration;

/**
 * A simple console logger to emulate the behavior of JUL loggers when
 * in the default configuration. SimpleConsoleLoggers are also used when
 * JUL is not present and no DefaultLoggerFinder is installed.
 */
public class SimpleConsoleLogger extends LoggerConfiguration
    implements Logger, PlatformLogger.Bridge, PlatformLogger.ConfigurableBridge {

    static final PlatformLogger.Level DEFAULT_LEVEL = PlatformLogger.Level.INFO;

    final String name;
    volatile PlatformLogger.Level  level;
    final boolean usePlatformLevel;
    SimpleConsoleLogger(String name, boolean usePlatformLevel) {
        this.name = name;
        this.usePlatformLevel = usePlatformLevel;
    }

    @Override
    public String getName() {
        return name;
    }

    private Enum<?> logLevel(PlatformLogger.Level level) {
        return usePlatformLevel ? level : level.systemLevel();
    }

    private Enum<?> logLevel(Level level) {
        return usePlatformLevel ? PlatformLogger.toPlatformLevel(level) : level;
    }

    // ---------------------------------------------------
    //                 From Logger
    // ---------------------------------------------------

    @Override
    public boolean isLoggable(Level level) {
        return isLoggable(PlatformLogger.toPlatformLevel(level));
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String key, Throwable thrown) {
        if (isLoggable(level)) {
            if (bundle != null) {
                key = bundle.getString(key);
            }
            publish(getCallerInfo(), logLevel(level), key, thrown);
        }
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String format, Object... params) {
        if (isLoggable(level)) {
            if (bundle != null) {
                format = bundle.getString(format);
            }
            publish(getCallerInfo(), logLevel(level), format, params);
        }
    }

    // ---------------------------------------------------
    //             From PlatformLogger.Bridge
    // ---------------------------------------------------

    @Override
    public boolean isLoggable(PlatformLogger.Level level) {
        final PlatformLogger.Level effectiveLevel =  effectiveLevel();
        return level != PlatformLogger.Level.OFF
                && level.ordinal() >= effectiveLevel.ordinal();
    }

    @Override
    public boolean isEnabled() {
        return level != PlatformLogger.Level.OFF;
    }

    @Override
    public void log(PlatformLogger.Level level, String msg) {
        if (isLoggable(level)) {
            publish(getCallerInfo(), logLevel(level), msg);
        }
    }

    @Override
    public void log(PlatformLogger.Level level, String msg, Throwable thrown) {
        if (isLoggable(level)) {
            publish(getCallerInfo(), logLevel(level), msg, thrown);
        }
    }

    @Override
    public void log(PlatformLogger.Level level, String msg, Object... params) {
        if (isLoggable(level)) {
            publish(getCallerInfo(), logLevel(level), msg, params);
        }
    }

    private PlatformLogger.Level effectiveLevel() {
        if (level == null) return DEFAULT_LEVEL;
        return level;
    }

    @Override
    public PlatformLogger.Level getPlatformLevel() {
        return level;
    }

    @Override
    public void setPlatformLevel(PlatformLogger.Level newLevel) {
        level = newLevel;
    }

    @Override
    public LoggerConfiguration getLoggerConfiguration() {
        return this;
    }

    /**
     * Default platform logging support - output messages to System.err -
     * equivalent to ConsoleHandler with SimpleFormatter.
     */
    static PrintStream outputStream() {
        return System.err;
    }

    // Returns the caller's class and method's name; best effort
    // if cannot infer, return the logger's name.
    private String getCallerInfo() {
        Optional<StackWalker.StackFrame> frame = new CallerFinder().get();
        if (frame.isPresent()) {
            return frame.get().getClassName() + " " + frame.get().getMethodName();
        } else {
            return name;
        }
    }

    /*
     * CallerFinder is a stateful predicate.
     */
    static final class CallerFinder implements Predicate<StackWalker.StackFrame> {
        private static final StackWalker WALKER;
        static {
            final PrivilegedAction<StackWalker> action = new PrivilegedAction<>() {
                @Override
                public StackWalker run() {
                    return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
                }
            };
            WALKER = AccessController.doPrivileged(action);
        }

        /**
         * Returns StackFrame of the caller's frame.
         * @return StackFrame of the caller's frame.
         */
        Optional<StackWalker.StackFrame> get() {
            return WALKER.walk((s) -> s.filter(this).findFirst());
        }

        private boolean lookingForLogger = true;
        /**
         * Returns true if we have found the caller's frame, false if the frame
         * must be skipped.
         *
         * @param t The frame info.
         * @return true if we have found the caller's frame, false if the frame
         * must be skipped.
         */
        @Override
        public boolean test(StackWalker.StackFrame t) {
            final String cname = t.getClassName();
            // We should skip all frames until we have found the logger,
            // because these frames could be frames introduced by e.g. custom
            // sub classes of Handler.
            if (lookingForLogger) {
                // Skip all frames until we have found the first logger frame.
                lookingForLogger = !isLoggerImplFrame(cname);
                return false;
            }
            // Continue walking until we've found the relevant calling frame.
            // Skips logging/logger infrastructure.
            return !isFilteredFrame(t);
        }

        private boolean isLoggerImplFrame(String cname) {
            return (cname.equals("sun.util.logging.PlatformLogger") ||
                    cname.equals("jdk.internal.logger.SimpleConsoleLogger"));
        }
    }

    private String getCallerInfo(String sourceClassName, String sourceMethodName) {
        if (sourceClassName == null) return name;
        if (sourceMethodName == null) return sourceClassName;
        return sourceClassName + " " + sourceMethodName;
    }

    private String toString(Throwable thrown) {
        String throwable = "";
        if (thrown != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println();
            thrown.printStackTrace(pw);
            pw.close();
            throwable = sw.toString();
        }
        return throwable;
    }

    private synchronized String format(Enum<?> level,
            String msg, Throwable thrown, String callerInfo) {

        ZonedDateTime zdt = ZonedDateTime.now();
        String throwable = toString(thrown);

        return String.format(Formatting.formatString,
                         zdt,
                         callerInfo,
                         name,
                         level.name(),
                         msg,
                         throwable);
    }

    // publish accepts both PlatformLogger Levels and LoggerFinder Levels.
    private void publish(String callerInfo, Enum<?> level, String msg) {
        outputStream().print(format(level, msg, null, callerInfo));
    }
    // publish accepts both PlatformLogger Levels and LoggerFinder Levels.
    private void publish(String callerInfo, Enum<?> level, String msg, Throwable thrown) {
        outputStream().print(format(level, msg, thrown, callerInfo));
    }
    // publish accepts both PlatformLogger Levels and LoggerFinder Levels.
    private void publish(String callerInfo, Enum<?> level, String msg, Object... params) {
        msg = params == null || params.length == 0 ? msg
                : Formatting.formatMessage(msg, params);
        outputStream().print(format(level, msg, null, callerInfo));
    }

    public static SimpleConsoleLogger makeSimpleLogger(String name, boolean usePlatformLevel) {
        return new SimpleConsoleLogger(name, usePlatformLevel);
    }

    public static SimpleConsoleLogger makeSimpleLogger(String name) {
        return new SimpleConsoleLogger(name, false);
    }

    public static String getSimpleFormat(Function<String, String> defaultPropertyGetter) {
        return Formatting.getSimpleFormat(defaultPropertyGetter);
    }

    public static boolean isFilteredFrame(StackFrame st) {
        return Formatting.isFilteredFrame(st);
    }

    @Override
    public void log(PlatformLogger.Level level, Supplier<String> msgSupplier) {
        if (isLoggable(level)) {
            publish(getCallerInfo(), logLevel(level), msgSupplier.get());
        }
    }

    @Override
    public void log(PlatformLogger.Level level, Throwable thrown,
            Supplier<String> msgSupplier) {
        if (isLoggable(level)) {
            publish(getCallerInfo(), logLevel(level), msgSupplier.get(), thrown);
        }
    }

    @Override
    public void logp(PlatformLogger.Level level, String sourceClass,
            String sourceMethod, String msg) {
        if (isLoggable(level)) {
            publish(getCallerInfo(sourceClass, sourceMethod), logLevel(level), msg);
        }
    }

    @Override
    public void logp(PlatformLogger.Level level, String sourceClass,
            String sourceMethod, Supplier<String> msgSupplier) {
        if (isLoggable(level)) {
            publish(getCallerInfo(sourceClass, sourceMethod), logLevel(level), msgSupplier.get());
        }
    }

    @Override
    public void logp(PlatformLogger.Level level, String sourceClass, String sourceMethod,
            String msg, Object... params) {
        if (isLoggable(level)) {
            publish(getCallerInfo(sourceClass, sourceMethod), logLevel(level), msg, params);
        }
    }

    @Override
    public void logp(PlatformLogger.Level level, String sourceClass,
            String sourceMethod, String msg, Throwable thrown) {
        if (isLoggable(level)) {
            publish(getCallerInfo(sourceClass, sourceMethod), logLevel(level), msg, thrown);
        }
    }

    @Override
    public void logp(PlatformLogger.Level level, String sourceClass,
            String sourceMethod, Throwable thrown, Supplier<String> msgSupplier) {
        if (isLoggable(level)) {
            publish(getCallerInfo(sourceClass, sourceMethod), logLevel(level), msgSupplier.get(), thrown);
        }
    }

    @Override
    public void logrb(PlatformLogger.Level level, String sourceClass,
            String sourceMethod, ResourceBundle bundle, String key, Object... params) {
        if (isLoggable(level)) {
            String msg = bundle == null ? key : bundle.getString(key);
            publish(getCallerInfo(sourceClass, sourceMethod), logLevel(level), msg, params);
        }
    }

    @Override
    public void logrb(PlatformLogger.Level level, String sourceClass,
            String sourceMethod, ResourceBundle bundle, String key, Throwable thrown) {
        if (isLoggable(level)) {
            String msg = bundle == null ? key : bundle.getString(key);
            publish(getCallerInfo(sourceClass, sourceMethod), logLevel(level), msg, thrown);
        }
    }

    @Override
    public void logrb(PlatformLogger.Level level, ResourceBundle bundle,
            String key, Object... params) {
        if (isLoggable(level)) {
            String msg = bundle == null ? key : bundle.getString(key);
            publish(getCallerInfo(), logLevel(level), msg, params);
        }
    }

    @Override
    public void logrb(PlatformLogger.Level level, ResourceBundle bundle,
            String key, Throwable thrown) {
        if (isLoggable(level)) {
            String msg = bundle == null ? key : bundle.getString(key);
            publish(getCallerInfo(), logLevel(level), msg, thrown);
        }
    }

    private static final class Formatting {
        static final String DEFAULT_FORMAT =
            "%1$tb %1$td, %1$tY %1$tl:%1$tM:%1$tS %1$Tp %2$s%n%4$s: %5$s%6$s%n";
        static final String FORMAT_PROP_KEY =
            "java.util.logging.SimpleFormatter.format";
        static final String formatString = getSimpleFormat(null);

        // Make it easier to wrap Logger...
        static private final String[] skips;
        static {
            String additionalPkgs = AccessController.doPrivileged(
                (PrivilegedAction<String>)
                () -> System.getProperty("jdk.logger.packages"));
            skips = additionalPkgs == null ? new String[0] : additionalPkgs.split(",");

        }

        static boolean isFilteredFrame(StackFrame st) {
            // skip logging/logger infrastructure
            if (System.Logger.class.isAssignableFrom(st.getDeclaringClass())) {
                return true;
            }

            // fast escape path: all the prefixes below start with 's' or 'j' and
            // have more than 12 characters.
            final String cname = st.getClassName();
            char c = cname.length() < 12 ? 0 : cname.charAt(0);
            if (c == 's') {
                // skip internal machinery classes
                if (cname.startsWith("sun.util.logging."))   return true;
                if (cname.startsWith("sun.rmi.runtime.Log")) return true;
            } else if (c == 'j') {
                // Message delayed at Bootstrap: no need to go further up.
                if (cname.startsWith("jdk.internal.logger.BootstrapLogger$LogEvent")) return false;
                // skip public machinery classes
                if (cname.startsWith("jdk.internal.logger."))          return true;
                if (cname.startsWith("java.util.logging."))            return true;
                if (cname.startsWith("java.lang.invoke.MethodHandle")) return true;
                if (cname.startsWith("java.security.AccessController")) return true;
            }

            // check additional prefixes if any are specified.
            if (skips.length > 0) {
                for (int i=0; i<skips.length; i++) {
                    if (!skips[i].isEmpty() && cname.startsWith(skips[i])) {
                        return true;
                    }
                }
            }

            return false;
        }

        static String getSimpleFormat(Function<String, String> defaultPropertyGetter) {
            // Using a lambda here causes
            //    jdk/test/java/lang/invoke/lambda/LogGeneratedClassesTest.java
            // to fail - because that test has a testcase which somehow references
            // PlatformLogger and counts the number of generated lambda classes
            // So we explicitely use new PrivilegedAction<String> here.
            String format =
                AccessController.doPrivileged(new PrivilegedAction<String>() {
                @Override
                public String run() {
                    return System.getProperty(FORMAT_PROP_KEY);
                }
            });
            if (format == null && defaultPropertyGetter != null) {
                format = defaultPropertyGetter.apply(FORMAT_PROP_KEY);
            }
            if (format != null) {
                try {
                    // validate the user-defined format string
                    String.format(format, ZonedDateTime.now(), "", "", "", "", "");
                } catch (IllegalArgumentException e) {
                    // illegal syntax; fall back to the default format
                    format = DEFAULT_FORMAT;
                }
            } else {
                format = DEFAULT_FORMAT;
            }
            return format;
        }


        // Copied from java.util.logging.Formatter.formatMessage
        static String formatMessage(String format, Object... parameters) {
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
                //
                boolean isJavaTestFormat = false;
                final int len = format.length();
                for (int i=0; i<len-2; i++) {
                    final char c = format.charAt(i);
                    if (c == '{') {
                        final int d = format.charAt(i+1);
                        if (d >= '0' && d <= '9') {
                            isJavaTestFormat = true;
                            break;
                        }
                    }
                }
                if (isJavaTestFormat) {
                    return java.text.MessageFormat.format(format, parameters);
                }
                return format;
            } catch (Exception ex) {
                // Formatting failed: use format string.
                return format;
            }
        }
    }
}
