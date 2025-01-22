/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.System.Logger;
import java.lang.System.LoggerFinder;
import java.util.Iterator;
import java.util.Locale;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.BooleanSupplier;

import jdk.internal.vm.annotation.Stable;

/**
 * Helper class used to load the {@link java.lang.System.LoggerFinder}.
 */
public final class LoggerFinderLoader {
    private static volatile System.LoggerFinder service;
    private static final Object lock = new int[0];

    // This is used to control how the LoggerFinderLoader handles
    // errors when instantiating the LoggerFinder provider.
    // ERROR => throws ServiceConfigurationError
    // WARNING => Do not fail, use plain default (simple logger) implementation,
    //            prints warning on console. (this is the default)
    // DEBUG => Do not fail, use plain default (simple logger) implementation,
    //          prints warning and exception stack trace on console.
    // QUIET => Do not fail and stay silent.
    private static enum ErrorPolicy { ERROR, WARNING, DEBUG, QUIET }

    // This class is static and cannot be instantiated.
    private LoggerFinderLoader() {
        throw new InternalError("LoggerFinderLoader cannot be instantiated");
    }

    // record the loadingThread while loading the backend
    static volatile Thread loadingThread;
    // Return the loaded LoggerFinder, or load it if not already loaded.
    private static System.LoggerFinder service() {
        if (service != null) return service;
        // ensure backend is detected before attempting to load the finder
        BootstrapLogger.ensureBackendDetected();
        synchronized(lock) {
            if (service != null) return service;
            Thread currentThread = Thread.currentThread();
            if (loadingThread == currentThread) {
                // recursive attempt to load the backend while loading the backend
                // use a temporary logger finder that returns special BootstrapLogger
                // which will wait until loading is finished
                return TemporaryLoggerFinder.INSTANCE;
            }
            loadingThread = currentThread;
            try {
                service = loadLoggerFinder();
            } finally {
                loadingThread = null;
            }
        }
        // Since the LoggerFinder is already loaded - we can stop using
        // temporary loggers.
        BootstrapLogger.redirectTemporaryLoggers();
        return service;
    }

    // returns true if called by the thread that loads the LoggerFinder, while
    // loading the LoggerFinder.
    static boolean isLoadingThread() {
        return loadingThread != null && loadingThread == Thread.currentThread();
    }

    // Get configuration error policy
    private static ErrorPolicy configurationErrorPolicy() {
        String errorPolicy = System.getProperty("jdk.logger.finder.error");
        if (errorPolicy == null || errorPolicy.isEmpty()) {
            return ErrorPolicy.WARNING;
        }
        try {
            return ErrorPolicy.valueOf(errorPolicy.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException x) {
            return ErrorPolicy.WARNING;
        }
    }

    // Whether multiple provider should be considered as an error.
    // This is further submitted to the configuration error policy.
    private static boolean ensureSingletonProvider() {
        return Boolean.getBoolean("jdk.logger.finder.singleton");
    }

    private static Iterator<System.LoggerFinder> findLoggerFinderProviders() {
        return ServiceLoader.load(System.LoggerFinder.class,
                        ClassLoader.getSystemClassLoader()).iterator();
    }

    public static final class TemporaryLoggerFinder extends LoggerFinder {
        private TemporaryLoggerFinder() {}
        @Stable
        private LoggerFinder loadedService;

        private static final BooleanSupplier isLoadingThread = new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return LoggerFinderLoader.isLoadingThread();
            }
        };
        private static final TemporaryLoggerFinder INSTANCE = new TemporaryLoggerFinder();

        @Override
        public Logger getLogger(String name, Module module) {
            if (loadedService == null) {
                loadedService = service;
                if (loadedService == null) {
                    return LazyLoggers.makeLazyLogger(name, module, isLoadingThread);
                }
            }
            assert loadedService != null;
            assert !LoggerFinderLoader.isLoadingThread();
            assert loadedService != this;
            return LazyLoggers.getLogger(name, module);
        }
    }

    // Loads the LoggerFinder using ServiceLoader. If no LoggerFinder
    // is found returns the default (possibly JUL based) implementation
    private static System.LoggerFinder loadLoggerFinder() {
        System.LoggerFinder result;
        try {
            final Iterator<System.LoggerFinder> iterator =
                    findLoggerFinderProviders();
            if (iterator.hasNext()) {
                result = iterator.next();
                if (iterator.hasNext() && ensureSingletonProvider()) {
                    throw new ServiceConfigurationError(
                            "More than one LoggerFinder implementation");
                }
            } else {
                result = loadDefaultImplementation();
            }
        } catch (Error | RuntimeException x) {
            // next caller will get the plain default impl (not linked
            // to java.util.logging)
            service = result = new DefaultLoggerFinder();
            ErrorPolicy errorPolicy = configurationErrorPolicy();
            if (errorPolicy == ErrorPolicy.ERROR) {
                // rethrow any exception as a ServiceConfigurationError.
                if (x instanceof Error) {
                    throw x;
                } else {
                    throw new ServiceConfigurationError(
                        "Failed to instantiate LoggerFinder provider; Using default.", x);
                }
            } else if (errorPolicy != ErrorPolicy.QUIET) {
                // if QUIET just silently use the plain default impl
                // otherwise, log a warning, possibly adding the exception
                // stack trace (if DEBUG is specified).
                SimpleConsoleLogger logger =
                        new SimpleConsoleLogger("jdk.internal.logger", false);
                logger.log(System.Logger.Level.WARNING,
                        "Failed to instantiate LoggerFinder provider; Using default.");
                if (errorPolicy == ErrorPolicy.DEBUG) {
                    logger.log(System.Logger.Level.WARNING,
                        "Exception raised trying to instantiate LoggerFinder", x);
                }
            }
        }
        return result;
    }

    private static System.LoggerFinder loadDefaultImplementation() {
        final Iterator<DefaultLoggerFinder> iterator =
                ServiceLoader.loadInstalled(DefaultLoggerFinder.class).iterator();

        DefaultLoggerFinder result = null;
        try {
            if (iterator.hasNext()) {
                result = iterator.next();
            }
        } catch (RuntimeException x) {
            throw new ServiceConfigurationError(
                    "Failed to instantiate default LoggerFinder", x);
        }
        if (result == null) {
            result = new DefaultLoggerFinder();
        }
        return result;
    }

    public static System.LoggerFinder getLoggerFinder() {
        return service();
    }

}
