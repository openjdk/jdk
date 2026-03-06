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
package jdk.jpackage.internal.log;

import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.SelfContainedException;
import jdk.jpackage.internal.util.function.ExceptionBox;

final class Utils {

    private Utils() {
    }

    @SuppressWarnings("unchecked")
    static <T extends Logger> T discardingLogger(Class<T> type) {
        return (T)Proxy.newProxyInstance(Utils.class.getClassLoader(), new Class<?>[]{type}, new LoggerHandler<>(type, false) {

            @Override
            protected void invokeLoggerMethod(Object proxy, Method method, Object[] args) {
                throw ExceptionBox.reachedUnreachable();
            }

        });
    }

    @SuppressWarnings("unchecked")
    static <T extends Logger> T teeLogger(Class<T> type, List<? extends T> loggers) {

        var enabledLoggers = loggers.stream().filter(Logger::enabled).toList();
        if (enabledLoggers.isEmpty()) {
            return discardingLogger(type);
        } else if (enabledLoggers.size() == 1) {
            return enabledLoggers.getFirst();
        }

        return (T)Proxy.newProxyInstance(Utils.class.getClassLoader(), new Class<?>[]{type}, new LoggerHandler<>(type, true) {

            @Override
            protected void invokeLoggerMethod(Object proxy, Method method, Object[] args) throws Throwable {
                for (var logger : enabledLoggers) {
                    method.invoke(logger, args);
                }
            }

        });
    }

    static Consumer<String> toStringConsumer(System.Logger logger, System.Logger.Level level) {
        Objects.requireNonNull(logger);
        Objects.requireNonNull(level);
        return str -> {
            logger.log(level, str);
        };
    }

    static boolean isSelfContained(Throwable t) {
        return t.getClass().getAnnotation(SelfContainedException.class) != null;
    }

    static String toString(Throwable t) {
        if (isSelfContained(t)) {
            return t.getMessage();
        } else {
            return t.toString();
        }
    }

    static void writeWithoutTrailingLineSeparator(StringWriter writer, Consumer<String> sink) {
        var buf = writer.getBuffer();
        var lineSeparator = System.lineSeparator();
        if (buf.length() >= lineSeparator.length()) {
            var tailChars = new char[lineSeparator.length()];
            buf.getChars(buf.length() - tailChars.length, buf.length(), tailChars, 0);
            if (Arrays.equals(tailChars, lineSeparator.toCharArray())) {
                buf.setLength(buf.length() - tailChars.length);
            }
        }
        sink.accept(buf.toString());
    }

    private static abstract class LoggerHandler<T extends Logger> implements InvocationHandler {

        protected LoggerHandler(Class<T> loggerType, boolean loggerEnabled) {
            Objects.requireNonNull(loggerType);
            if (!loggerType.isInterface() ) {
                throw new IllegalArgumentException(String.format("%s is not an interface", loggerType));
            }

            loggerMethods = unfoldInterface(loggerType).flatMap(interfaceType -> {
                return Stream.of(interfaceType.getMethods());
            }).collect(Collectors.toSet());

            this.loggerEnabled = loggerEnabled;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (loggerMethods.contains(method)) {
                var returnType = method.getReturnType();

                if (method.getName().equals("enabled") && returnType.equals(boolean.class) && method.getParameterCount() == 0) {
                    return loggerEnabled;
                } else if (returnType.equals(void.class)) {
                    if (loggerEnabled) {
                        invokeLoggerMethod(proxy, method, args);
                    }
                    return null;
                } else {
                    throw new AssertionError(String.format("Don't know how to handle %s", method));
                }
            } else {
                // Presumably this is java.lang.Objects's method. Redirect it to this instance.
                return method.invoke(this, args);
            }
        }

        protected abstract void invokeLoggerMethod(Object proxy, Method method, Object[] args) throws Throwable;

        private final Set<Method> loggerMethods;
        private final boolean loggerEnabled;
    }

    private static Stream<Class<?>> unfoldInterface(Class<?> interfaceType) {
        return Stream.concat(
                Stream.of(interfaceType),
                Stream.of(interfaceType.getInterfaces()
        ).flatMap(Utils::unfoldInterface));
    }

    static final Consumer<String> DISCARDER = _ -> {};
}
