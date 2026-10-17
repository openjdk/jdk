/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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


package jdk.internal.logger.dynamic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MutableCallSite;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import jdk.internal.access.JavaUtilLoggingAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.logger.LazyLoggers;
import sun.util.logging.PlatformLogger;
import sun.util.logging.PlatformLogger.ConfigurableBridge.LoggerConfiguration;

public class DormantLogger {

    public enum Output {
        LOGGER,
        STDOUT,
        STDERR;

        static Output parse(String value) {
            return Output.valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    static class LoggerInfo {
        public final WeakReference<System.Logger> logger;
        public final PlatformLogger.Level level;
        public final Output output;
        public final MutableCallSite loggingCallSite;
        public final MethodHandle loggingTest;

        public LoggerInfo(System.Logger logger,
                          PlatformLogger.Level level,
                          Output output,
                          MutableCallSite loggingCallSite,
                          MethodHandle loggingTest) {
            this.logger = new WeakReference<>(logger);
            this.level = level;
            this.output = output;
            this.loggingCallSite = loggingCallSite;
            this.loggingTest = loggingTest;
        }

        LoggerInfo with(PlatformLogger.Level level, Output output) {
            System.Logger l = logger.get();
            if (l == null) {
                return this;
            }
            return new LoggerInfo(l, level, output, loggingCallSite, loggingTest);
        }
    }

    private static final ConcurrentHashMap<String, LoggerInfo> dormantLoggers
            = new ConcurrentHashMap<>();

    /**
     * Returns a dormant logger that can be managed remotely via
     * jcmd. This method is similar to
     * {@link LazyLoggers#getLazyLogger(String, Module)}, but attempts to set the
     * initial log level to {@link PlatformLogger.Level#OFF}.
     * Backend level configuration uses JDK-specific logging interfaces;
     * {@code System.Logger} does not provide a level-setting method. A custom
     * {@code LoggerFinder}'s logger retains control of its filtering and delivery.
     * The supplied call site controls whether guarded logging code is entered.
     *
     * @param name the name of the logger
     * @param module the module on behalf of which the logger is created
     * @return a dormant logger that can be managed remotely
     * @throws NullPointerException if {@code name}, {@code module},
     *         {@code loggingCallSite}, or {@code loggingTest} is null
     * @throws IllegalStateException if a live dormant logger with the same
     *         name has already been registered with a different call site
     */
    public static System.Logger of(String name,
                                   Module module,
                                   MutableCallSite loggingCallSite,
                                   MethodHandle loggingTest) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(module, "module must not be null");
        Objects.requireNonNull(loggingCallSite, "loggingCallSite must not be null");
        Objects.requireNonNull(loggingTest, "loggingTest must not be null");

        // let's loop just in case we have to protect against competing threads registering
        while (true) {
            LoggerInfo info = dormantLoggers.get(name);
            if (info != null) {
                System.Logger logger = info.logger.get();
                if (logger == null) {
                    dormantLoggers.remove(name, info);
                    continue;
                }
                if (info.loggingCallSite != loggingCallSite
                        || info.loggingTest != loggingTest) {
                    throw new IllegalStateException(
                            "Dormant logger already registered: " + name);
                }
                return logger;
            }

            System.Logger newLogger = LazyLoggers.getLazyLogger(name, module);
            if (LazyLoggers.getConcreteLogger(newLogger) instanceof LoggerConfiguration lc) {
                lc.setPlatformLevel(PlatformLogger.Level.OFF);
            }
            LoggerInfo newInfo = new LoggerInfo(newLogger,
                    PlatformLogger.Level.OFF, Output.LOGGER,
                    loggingCallSite, loggingTest);
            if (dormantLoggers.putIfAbsent(name, newInfo) == null) {
                return newLogger;
            }
        }
    }

    /**
     * Returns a cached dormant logger if it exists.
     * Removes entries if the associated System.Logger no longer exists
     *
     * @param logger the name of the logger
     * @return the cached dormant logger, or null if not found
     */
    private static System.Logger getCachedDormantLogger(String logger) {
        LoggerInfo info = dormantLoggers.get(logger);
        if (info == null) {
            return null;
        }
        System.Logger loggerObj = info.logger.get();
        if (loggerObj == null) {
            dormantLoggers.remove(logger);
            return null;
        }
        return loggerObj;
    }

    /**
     * Sets the level for a dormant logger.
     * <p>
     * If the underlying logger was created via DormantLogger call and is
     * an instance of a JDK Logger, its platform level will be updated directly.
     * A custom {@code LoggerFinder}'s backend level cannot be configured through
     * {@code System.Logger}; its configuration remains application-owned.
     * <p>
     * After updating the logging level, this method notifies any interested
     * parties about the level change using the
     * {@link #notifyLevelChange(String)} method.
     *
     * @param logger the name of the logger whose level should be changed
     * @param level the new logging level as a string representation of
     *             {@link System.Logger.Level}
     * @return a status message describing the result
     */
    public static String setDormantLoggerLevel(String logger, String level) {
        return setDormantLoggerLevel(logger, level, Output.LOGGER.name());
    }

    /**
     * Sets the level and output for a dormant logger.
     *
     * @param logger the name of the logger whose level should be changed
     * @param level the new logging level as a string representation of
     *             {@link System.Logger.Level}
     * @param output the output mode: {@code logger}, {@code stdout}, or
     *               {@code stderr}
     * @return a status message describing the result
     */
    public static String setDormantLoggerLevel(String logger, String level, String output) {
        var l = getCachedDormantLogger(logger);
        if (l == null) {
            dormantLoggers.remove(logger);
            return "Dormant logger \"" + logger
                    + "\" is not registered; no change made.";
        }

        Output out = Output.parse(output);
        PlatformLogger.Level pLevel =
                PlatformLogger.toPlatformLevel(
                        System.Logger.Level.valueOf(level.toUpperCase(Locale.ROOT)));

        dormantLoggers.computeIfPresent(logger,
                (name, info) -> info.with(pLevel, out));

        if (out == Output.LOGGER
                && LazyLoggers.getConcreteLogger(l) instanceof LoggerConfiguration lc) {
            lc.setPlatformLevel(pLevel);
        }

        JavaUtilLoggingAccess jla = SharedSecrets.getJavaUtilLoggingAccess();
        if (jla != null && out == Output.LOGGER) {
            // Configure the same-named JUL logger and its handlers once JUL
            // is initialized. This does not configure an arbitrary custom
            // LoggerFinder's backend, even if JUL is also in use.
            jla.setLevel(logger, pLevel.name());
        } else if (jla != null) {
            // Direct output bypasses JUL. Remove any handler installed by a
            // previous activation with logger output.
            jla.removeDormantLoggerHandlers(logger);
        }
        notifyLevelChange(logger);
        return "Dormant logger \"" + logger + "\" updated: level="
                + level.toUpperCase(Locale.ROOT) + ", output="
                + out.name().toLowerCase(Locale.ROOT) + ".";
    }

    /**
     * Retrieves the current logging level of the given System.Logger instance.
     *
     * @param l the System.Logger instance to retrieve the logging level from
     * @return the current logging level, or null if the logger does not support
     *     level retrieval
     */
    public static PlatformLogger.Level getLevel(System.Logger l) {
        LoggerInfo loggerInfo = dormantLoggers.get(l.getName());
        if (loggerInfo != null) {
            return getEffectiveLevel(l.getName(), l);
        }
        if (LazyLoggers.getConcreteLogger(l) instanceof LoggerConfiguration lc) {
            return lc.getPlatformLevel();
        } else {
            JavaUtilLoggingAccess jla = SharedSecrets.getJavaUtilLoggingAccess();
            if (jla != null) {
                // j.u.logging impl has been triggered
                return jla.getLevel(l.getName());
            }
        }
        return null;
    }

    public static Output getOutput(System.Logger l) {
        LoggerInfo loggerInfo = dormantLoggers.get(l.getName());
        return loggerInfo == null ? Output.LOGGER : loggerInfo.output;
    }

    public static boolean useLoggerOutput(System.Logger l) {
        return getOutput(l) == Output.LOGGER;
    }

    public static boolean isLoggable(System.Logger l, System.Logger.Level level) {
        PlatformLogger.Level effectiveLevel = getEffectiveLevel(l.getName(), l);
        return effectiveLevel != PlatformLogger.Level.OFF
                && PlatformLogger.toPlatformLevel(level).intValue()
                        >= effectiveLevel.intValue();
    }

    public static void notifyLevelChange(String loggerName) {
        var loggerInfo = dormantLoggers.get(loggerName);
        if (loggerInfo == null) {
            // no dormant logger exists for this logger
            return;
        }
        MutableCallSite mcs = loggerInfo.loggingCallSite;
        PlatformLogger.Level level = getEffectiveLevel(loggerName,
                loggerInfo.logger.get());
        if (level != PlatformLogger.Level.OFF) {
            mcs.setTarget(loggerInfo.loggingTest);
        } else {
            mcs.setTarget(MethodHandles.constant(boolean.class, false));
        }
        MutableCallSite.syncAll(new MutableCallSite[]{mcs});
    }

    private static PlatformLogger.Level getEffectiveLevel(String loggerName,
                                                          System.Logger logger) {
        LoggerInfo loggerInfo = dormantLoggers.get(loggerName);
        if (loggerInfo == null) {
            return PlatformLogger.Level.OFF;
        }
        if (loggerInfo.output != Output.LOGGER) {
            return loggerInfo.level;
        }
        JavaUtilLoggingAccess jla = SharedSecrets.getJavaUtilLoggingAccess();
        return (jla == null || logger == null) ? loggerInfo.level
                : jla.getLevel(loggerName);
    }

    public static void reloadLevels() {
        JavaUtilLoggingAccess jla = SharedSecrets.getJavaUtilLoggingAccess();
        assert(jla != null);
        dormantLoggers.keySet().removeIf(name -> getCachedDormantLogger(name) == null);
        // Apply stored levels only for logger output. Direct output must not
        // configure JUL levels or handlers when JUL is initialized later.
        dormantLoggers.forEach((name, info) -> {
            System.Logger sl = getCachedDormantLogger(name);
            if (sl != null && info.output == Output.LOGGER) {
                jla.setLevel(name, info.level.name());
            }
        });
    }
}
