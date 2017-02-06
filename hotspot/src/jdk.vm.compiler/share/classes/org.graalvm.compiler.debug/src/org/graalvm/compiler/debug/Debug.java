/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.debug;

import static org.graalvm.compiler.debug.DelegatingDebugConfig.Feature.INTERCEPT;
import static org.graalvm.compiler.debug.DelegatingDebugConfig.Feature.LOG_METHOD;
import static java.util.FormattableFlags.LEFT_JUSTIFY;
import static java.util.FormattableFlags.UPPERCASE;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.graalvm.compiler.debug.DelegatingDebugConfig.Level;
import org.graalvm.compiler.debug.internal.CounterImpl;
import org.graalvm.compiler.debug.internal.DebugHistogramImpl;
import org.graalvm.compiler.debug.internal.DebugScope;
import org.graalvm.compiler.debug.internal.MemUseTrackerImpl;
import org.graalvm.compiler.debug.internal.TimerImpl;
import org.graalvm.compiler.debug.internal.method.MethodMetricsImpl;
import org.graalvm.compiler.serviceprovider.GraalServices;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Scope based debugging facility.
 *
 * This facility is {@linkplain #isEnabled() enabled} if any of the following hold when the
 * {@link Debug} class is initialized:
 * <ul>
 * <li>assertions are enabled for the {@link Debug} class</li>
 * <li>{@link Debug#params}{@code .enable} is {@code true}</li>
 * </ul>
 */
public class Debug {

    private static final Params params = new Params();

    static {
        // Load the service providers that may want to modify any of the
        // parameters encapsulated by the Initialization class below.
        for (DebugInitializationParticipant p : GraalServices.load(DebugInitializationParticipant.class)) {
            p.apply(params);
        }
    }

    /**
     * The parameters for configuring the initialization of {@link Debug} class.
     */
    public static class Params {
        public boolean enable;
        public boolean enableMethodFilter;
        public boolean enableUnscopedTimers;
        public boolean enableUnscopedCounters;
        public boolean enableUnscopedMethodMetrics;
        public boolean enableUnscopedMemUseTrackers;
        public boolean interceptCount;
        public boolean interceptTime;
        public boolean interceptMem;
    }

    @SuppressWarnings("all")
    private static boolean initialize() {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        return assertionsEnabled || params.enable || GraalDebugConfig.Options.ForceDebugEnable.getValue();
    }

    private static final boolean ENABLED = initialize();

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static boolean isDumpEnabledForMethod() {
        if (!ENABLED) {
            return false;
        }
        DebugConfig config = DebugScope.getConfig();
        if (config == null) {
            return false;
        }
        return config.isDumpEnabledForMethod();
    }

    public static final int BASIC_LOG_LEVEL = 1;
    public static final int INFO_LOG_LEVEL = 2;
    public static final int VERBOSE_LOG_LEVEL = 3;
    public static final int DETAILED_LOG_LEVEL = 4;
    public static final int VERY_DETAILED_LOG_LEVEL = 5;

    public static boolean isDumpEnabled(int dumpLevel) {
        return ENABLED && DebugScope.getInstance().isDumpEnabled(dumpLevel);
    }

    /**
     * Determines if verification is enabled in the current method, regardless of the
     * {@linkplain Debug#currentScope() current debug scope}.
     *
     * @see Debug#verify(Object, String)
     */
    public static boolean isVerifyEnabledForMethod() {
        if (!ENABLED) {
            return false;
        }
        DebugConfig config = DebugScope.getConfig();
        if (config == null) {
            return false;
        }
        return config.isVerifyEnabledForMethod();
    }

    /**
     * Determines if verification is enabled in the {@linkplain Debug#currentScope() current debug
     * scope}.
     *
     * @see Debug#verify(Object, String)
     */
    public static boolean isVerifyEnabled() {
        return ENABLED && DebugScope.getInstance().isVerifyEnabled();
    }

    public static boolean isCountEnabled() {
        return ENABLED && DebugScope.getInstance().isCountEnabled();
    }

    public static boolean isTimeEnabled() {
        return ENABLED && DebugScope.getInstance().isTimeEnabled();
    }

    public static boolean isMemUseTrackingEnabled() {
        return ENABLED && DebugScope.getInstance().isMemUseTrackingEnabled();
    }

    public static boolean isLogEnabledForMethod() {
        if (!ENABLED) {
            return false;
        }
        DebugConfig config = DebugScope.getConfig();
        if (config == null) {
            return false;
        }
        return config.isLogEnabledForMethod();
    }

    public static boolean isLogEnabled() {
        return isLogEnabled(BASIC_LOG_LEVEL);
    }

    public static boolean isLogEnabled(int logLevel) {
        return ENABLED && DebugScope.getInstance().isLogEnabled(logLevel);
    }

    public static boolean isMethodMeterEnabled() {
        return ENABLED && DebugScope.getInstance().isMethodMeterEnabled();
    }

    @SuppressWarnings("unused")
    public static Runnable decorateDebugRoot(Runnable runnable, String name, DebugConfig config) {
        return runnable;
    }

    @SuppressWarnings("unused")
    public static <T> Callable<T> decorateDebugRoot(Callable<T> callable, String name, DebugConfig config) {
        return callable;
    }

    @SuppressWarnings("unused")
    public static Runnable decorateScope(Runnable runnable, String name, Object... context) {
        return runnable;
    }

    @SuppressWarnings("unused")
    public static <T> Callable<T> decorateScope(Callable<T> callable, String name, Object... context) {
        return callable;
    }

    /**
     * Gets a string composed of the names in the current nesting of debug
     * {@linkplain #scope(Object) scopes} separated by {@code '.'}.
     */
    public static String currentScope() {
        if (ENABLED) {
            return DebugScope.getInstance().getQualifiedName();
        } else {
            return "";
        }
    }

    /**
     * Represents a debug scope entered by {@link Debug#scope(Object)} or
     * {@link Debug#sandbox(CharSequence, DebugConfig, Object...)}. Leaving the scope is achieved
     * via {@link #close()}.
     */
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * Creates and enters a new debug scope which will be a child of the current debug scope.
     * <p>
     * It is recommended to use the try-with-resource statement for managing entering and leaving
     * debug scopes. For example:
     *
     * <pre>
     * try (Scope s = Debug.scope(&quot;InliningGraph&quot;, inlineeGraph)) {
     *     ...
     * } catch (Throwable e) {
     *     throw Debug.handle(e);
     * }
     * </pre>
     *
     * The {@code name} argument is subject to the following type based conversion before having
     * {@link Object#toString()} called on it:
     *
     * <pre>
     *     Type          | Conversion
     * ------------------+-----------------
     *  java.lang.Class  | arg.getSimpleName()
     *                   |
     * </pre>
     *
     * @param name the name of the new scope
     * @param contextObjects an array of object to be appended to the {@linkplain #context()
     *            current} debug context
     * @throws Throwable used to enforce a catch block.
     * @return the scope entered by this method which will be exited when its {@link Scope#close()}
     *         method is called
     */
    public static Scope scope(Object name, Object[] contextObjects) throws Throwable {
        if (ENABLED) {
            return DebugScope.getInstance().scope(convertFormatArg(name).toString(), null, contextObjects);
        } else {
            return null;
        }
    }

    /**
     * Similar to {@link #scope(Object, Object[])} but without context objects. Therefore the catch
     * block can be omitted.
     *
     * @see #scope(Object, Object[])
     */
    public static Scope scope(Object name) {
        if (ENABLED) {
            return DebugScope.getInstance().scope(convertFormatArg(name).toString(), null);
        } else {
            return null;
        }
    }

    public static Scope methodMetricsScope(Object name, DebugScope.ExtraInfo metaInfo, boolean newId, Object... context) {
        if (ENABLED) {
            return DebugScope.getInstance().enhanceWithExtraInfo(convertFormatArg(name).toString(), metaInfo, newId, context);
        } else {
            return null;
        }
    }

    /**
     * @see #scope(Object, Object[])
     * @param context an object to be appended to the {@linkplain #context() current} debug context
     */
    public static Scope scope(Object name, Object context) throws Throwable {
        if (ENABLED) {
            return DebugScope.getInstance().scope(convertFormatArg(name).toString(), null, context);
        } else {
            return null;
        }
    }

    /**
     * @see #scope(Object, Object[])
     * @param context1 first object to be appended to the {@linkplain #context() current} debug
     *            context
     * @param context2 second object to be appended to the {@linkplain #context() current} debug
     *            context
     */
    public static Scope scope(Object name, Object context1, Object context2) throws Throwable {
        if (ENABLED) {
            return DebugScope.getInstance().scope(convertFormatArg(name).toString(), null, context1, context2);
        } else {
            return null;
        }
    }

    /**
     * @see #scope(Object, Object[])
     * @param context1 first object to be appended to the {@linkplain #context() current} debug
     *            context
     * @param context2 second object to be appended to the {@linkplain #context() current} debug
     *            context
     * @param context3 third object to be appended to the {@linkplain #context() current} debug
     *            context
     */
    public static Scope scope(Object name, Object context1, Object context2, Object context3) throws Throwable {
        if (ENABLED) {
            return DebugScope.getInstance().scope(convertFormatArg(name).toString(), null, context1, context2, context3);
        } else {
            return null;
        }
    }

    /**
     * Creates and enters a new debug scope which will be disjoint from the current debug scope.
     * <p>
     * It is recommended to use the try-with-resource statement for managing entering and leaving
     * debug scopes. For example:
     *
     * <pre>
     * try (Scope s = Debug.sandbox(&quot;CompilingStub&quot;, null, stubGraph)) {
     *     ...
     * } catch (Throwable e) {
     *     throw Debug.handle(e);
     * }
     * </pre>
     *
     * @param name the name of the new scope
     * @param config the debug configuration to use for the new scope
     * @param context objects to be appended to the {@linkplain #context() current} debug context
     * @return the scope entered by this method which will be exited when its {@link Scope#close()}
     *         method is called
     */
    public static Scope sandbox(CharSequence name, DebugConfig config, Object... context) throws Throwable {
        if (ENABLED) {
            DebugConfig sandboxConfig = config == null ? silentConfig() : config;
            return DebugScope.getInstance().scope(name, sandboxConfig, context);
        } else {
            return null;
        }
    }

    public static Scope forceLog() throws Throwable {
        ArrayList<Object> context = new ArrayList<>();
        for (Object obj : context()) {
            context.add(obj);
        }
        return Debug.sandbox("forceLog", new DelegatingDebugConfig().override(Level.LOG, Integer.MAX_VALUE).enable(LOG_METHOD), context.toArray());
    }

    /**
     * Opens a scope in which exception {@linkplain DebugConfig#interceptException(Throwable)
     * interception} is disabled. It is recommended to use the try-with-resource statement for
     * managing entering and leaving such scopes:
     *
     * <pre>
     * try (DebugConfigScope s = Debug.disableIntercept()) {
     *     ...
     * }
     * </pre>
     *
     * This is particularly useful to suppress extraneous output in JUnit tests that are expected to
     * throw an exception.
     */
    public static DebugConfigScope disableIntercept() {
        return Debug.setConfig(new DelegatingDebugConfig().disable(INTERCEPT));
    }

    /**
     * Handles an exception in the context of the debug scope just exited. The just exited scope
     * must have the current scope as its parent which will be the case if the try-with-resource
     * pattern recommended by {@link #scope(Object)} and
     * {@link #sandbox(CharSequence, DebugConfig, Object...)} is used
     *
     * @see #scope(Object, Object[])
     * @see #sandbox(CharSequence, DebugConfig, Object...)
     */
    public static RuntimeException handle(Throwable exception) {
        if (ENABLED) {
            return DebugScope.getInstance().handle(exception);
        } else {
            if (exception instanceof Error) {
                throw (Error) exception;
            }
            if (exception instanceof RuntimeException) {
                throw (RuntimeException) exception;
            }
            throw new RuntimeException(exception);
        }
    }

    public static void log(String msg) {
        log(BASIC_LOG_LEVEL, msg);
    }

    /**
     * Prints a message to the current debug scope's logging stream if logging is enabled.
     *
     * @param msg the message to log
     */
    public static void log(int logLevel, String msg) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, msg);
        }
    }

    public static void log(String format, Object arg) {
        log(BASIC_LOG_LEVEL, format, arg);
    }

    /**
     * Prints a message to the current debug scope's logging stream if logging is enabled.
     *
     * @param format a format string
     * @param arg the argument referenced by the format specifiers in {@code format}
     */
    public static void log(int logLevel, String format, Object arg) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg);
        }
    }

    public static void log(String format, int arg) {
        log(BASIC_LOG_LEVEL, format, arg);
    }

    /**
     * Prints a message to the current debug scope's logging stream if logging is enabled.
     *
     * @param format a format string
     * @param arg the argument referenced by the format specifiers in {@code format}
     */
    public static void log(int logLevel, String format, int arg) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg);
        }
    }

    public static void log(String format, Object arg1, Object arg2) {
        log(BASIC_LOG_LEVEL, format, arg1, arg2);
    }

    /**
     * @see #log(int, String, Object)
     */
    public static void log(int logLevel, String format, Object arg1, Object arg2) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg1, arg2);
        }
    }

    public static void log(String format, int arg1, Object arg2) {
        log(BASIC_LOG_LEVEL, format, arg1, arg2);
    }

    /**
     * @see #log(int, String, Object)
     */
    public static void log(int logLevel, String format, int arg1, Object arg2) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg1, arg2);
        }
    }

    public static void log(String format, Object arg1, int arg2) {
        log(BASIC_LOG_LEVEL, format, arg1, arg2);
    }

    /**
     * @see #log(int, String, Object)
     */
    public static void log(int logLevel, String format, Object arg1, int arg2) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg1, arg2);
        }
    }

    public static void log(String format, int arg1, int arg2) {
        log(BASIC_LOG_LEVEL, format, arg1, arg2);
    }

    /**
     * @see #log(int, String, Object)
     */
    public static void log(int logLevel, String format, int arg1, int arg2) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg1, arg2);
        }
    }

    public static void log(String format, Object arg1, Object arg2, Object arg3) {
        log(BASIC_LOG_LEVEL, format, arg1, arg2, arg3);
    }

    /**
     * @see #log(int, String, Object)
     */
    public static void log(int logLevel, String format, Object arg1, Object arg2, Object arg3) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg1, arg2, arg3);
        }
    }

    public static void log(String format, int arg1, int arg2, int arg3) {
        log(BASIC_LOG_LEVEL, format, arg1, arg2, arg3);
    }

    /**
     * @see #log(int, String, Object)
     */
    public static void log(int logLevel, String format, int arg1, int arg2, int arg3) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg1, arg2, arg3);
        }
    }

    public static void log(String format, Object arg1, Object arg2, Object arg3, Object arg4) {
        log(BASIC_LOG_LEVEL, format, arg1, arg2, arg3, arg4);
    }

    /**
     * @see #log(int, String, Object)
     */
    public static void log(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg1, arg2, arg3, arg4);
        }
    }

    public static void log(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        log(BASIC_LOG_LEVEL, format, arg1, arg2, arg3, arg4, arg5);
    }

    /**
     * @see #log(int, String, Object)
     */
    public static void log(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg1, arg2, arg3, arg4, arg5);
        }
    }

    public static void log(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
        log(BASIC_LOG_LEVEL, format, arg1, arg2, arg3, arg4, arg5, arg6);
    }

    /**
     * @see #log(int, String, Object)
     */
    public static void log(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg1, arg2, arg3, arg4, arg5, arg6);
        }
    }

    public static void log(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7) {
        log(BASIC_LOG_LEVEL, format, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
    }

    public static void log(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8) {
        log(BASIC_LOG_LEVEL, format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
    }

    /**
     * @see #log(int, String, Object)
     */
    public static void log(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
        }
    }

    public static void log(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
        }
    }

    public static void log(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9) {
        log(BASIC_LOG_LEVEL, format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
    }

    public static void log(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
        }
    }

    public static void log(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10) {
        log(BASIC_LOG_LEVEL, format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
    }

    public static void log(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10) {
        if (ENABLED) {
            DebugScope.getInstance().log(logLevel, format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
        }
    }

    public static void logv(String format, Object... args) {
        logv(BASIC_LOG_LEVEL, format, args);
    }

    /**
     * Prints a message to the current debug scope's logging stream. This method must only be called
     * if debugging is {@linkplain Debug#isEnabled() enabled} as it incurs allocation at the call
     * site. If possible, call one of the other {@code log()} methods in this class that take a
     * fixed number of parameters.
     *
     * @param format a format string
     * @param args the arguments referenced by the format specifiers in {@code format}
     */
    public static void logv(int logLevel, String format, Object... args) {
        if (!ENABLED) {
            throw new InternalError("Use of Debug.logv() must be guarded by a test of Debug.isEnabled()");
        }
        DebugScope.getInstance().log(logLevel, format, args);
    }

    /**
     * This override exists to catch cases when {@link #log(String, Object)} is called with one
     * argument bound to a varargs method parameter. It will bind to this method instead of the
     * single arg variant and produce a deprecation warning instead of silently wrapping the
     * Object[] inside of another Object[].
     */
    @Deprecated
    public static void log(String format, Object[] args) {
        assert false : "shouldn't use this";
        log(BASIC_LOG_LEVEL, format, args);
    }

    /**
     * This override exists to catch cases when {@link #log(int, String, Object)} is called with one
     * argument bound to a varargs method parameter. It will bind to this method instead of the
     * single arg variant and produce a deprecation warning instead of silently wrapping the
     * Object[] inside of another Object[].
     */
    @Deprecated
    public static void log(int logLevel, String format, Object[] args) {
        assert false : "shouldn't use this";
        logv(logLevel, format, args);
    }

    public static void dump(int dumpLevel, Object object, String msg) {
        if (ENABLED && DebugScope.getInstance().isDumpEnabled(dumpLevel)) {
            DebugScope.getInstance().dump(dumpLevel, object, msg);
        }
    }

    public static void dump(int dumpLevel, Object object, String format, Object arg) {
        if (ENABLED && DebugScope.getInstance().isDumpEnabled(dumpLevel)) {
            DebugScope.getInstance().dump(dumpLevel, object, format, arg);
        }
    }

    public static void dump(int dumpLevel, Object object, String format, Object arg1, Object arg2) {
        if (ENABLED && DebugScope.getInstance().isDumpEnabled(dumpLevel)) {
            DebugScope.getInstance().dump(dumpLevel, object, format, arg1, arg2);
        }
    }

    public static void dump(int dumpLevel, Object object, String format, Object arg1, Object arg2, Object arg3) {
        if (ENABLED && DebugScope.getInstance().isDumpEnabled(dumpLevel)) {
            DebugScope.getInstance().dump(dumpLevel, object, format, arg1, arg2, arg3);
        }
    }

    /**
     * This override exists to catch cases when {@link #dump(int, Object, String, Object)} is called
     * with one argument bound to a varargs method parameter. It will bind to this method instead of
     * the single arg variant and produce a deprecation warning instead of silently wrapping the
     * Object[] inside of another Object[].
     */
    @Deprecated
    public static void dump(int dumpLevel, Object object, String format, Object[] args) {
        assert false : "shouldn't use this";
        if (ENABLED && DebugScope.getInstance().isDumpEnabled(dumpLevel)) {
            DebugScope.getInstance().dump(dumpLevel, object, format, args);
        }
    }

    /**
     * Calls all {@link DebugVerifyHandler}s in the current {@linkplain DebugScope#getConfig()
     * config} to perform verification on a given object.
     *
     * @param object object to verify
     * @param message description of verification context
     *
     * @see DebugVerifyHandler#verify(Object, String)
     */
    public static void verify(Object object, String message) {
        if (ENABLED && DebugScope.getInstance().isVerifyEnabled()) {
            DebugScope.getInstance().verify(object, message);
        }
    }

    /**
     * Calls all {@link DebugVerifyHandler}s in the current {@linkplain DebugScope#getConfig()
     * config} to perform verification on a given object.
     *
     * @param object object to verify
     * @param format a format string for the description of the verification context
     * @param arg the argument referenced by the format specifiers in {@code format}
     *
     * @see DebugVerifyHandler#verify(Object, String)
     */
    public static void verify(Object object, String format, Object arg) {
        if (ENABLED && DebugScope.getInstance().isVerifyEnabled()) {
            DebugScope.getInstance().verify(object, format, arg);
        }
    }

    /**
     * This override exists to catch cases when {@link #verify(Object, String, Object)} is called
     * with one argument bound to a varargs method parameter. It will bind to this method instead of
     * the single arg variant and produce a deprecation warning instead of silently wrapping the
     * Object[] inside of another Object[].
     */
    @Deprecated
    public static void verify(Object object, String format, Object[] args) {
        assert false : "shouldn't use this";
        if (ENABLED && DebugScope.getInstance().isVerifyEnabled()) {
            DebugScope.getInstance().verify(object, format, args);
        }
    }

    /**
     * Opens a new indentation level (by adding some spaces) based on the current indentation level.
     * This should be used in a {@linkplain Indent try-with-resources} pattern.
     *
     * @return an object that reverts to the current indentation level when
     *         {@linkplain Indent#close() closed} or null if debugging is disabled
     * @see #logAndIndent(int, String)
     * @see #logAndIndent(int, String, Object)
     */
    public static Indent indent() {
        if (ENABLED) {
            DebugScope scope = DebugScope.getInstance();
            return scope.pushIndentLogger();
        }
        return null;
    }

    public static Indent logAndIndent(String msg) {
        return logAndIndent(BASIC_LOG_LEVEL, msg);
    }

    /**
     * A convenience function which combines {@link #log(String)} and {@link #indent()}.
     *
     * @param msg the message to log
     * @return an object that reverts to the current indentation level when
     *         {@linkplain Indent#close() closed} or null if debugging is disabled
     */
    public static Indent logAndIndent(int logLevel, String msg) {
        if (ENABLED && Debug.isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, msg);
        }
        return null;
    }

    public static Indent logAndIndent(String format, Object arg) {
        return logAndIndent(BASIC_LOG_LEVEL, format, arg);
    }

    /**
     * A convenience function which combines {@link #log(String, Object)} and {@link #indent()}.
     *
     * @param format a format string
     * @param arg the argument referenced by the format specifiers in {@code format}
     * @return an object that reverts to the current indentation level when
     *         {@linkplain Indent#close() closed} or null if debugging is disabled
     */
    public static Indent logAndIndent(int logLevel, String format, Object arg) {
        if (ENABLED && Debug.isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg);
        }
        return null;
    }

    public static Indent logAndIndent(String format, int arg) {
        return logAndIndent(BASIC_LOG_LEVEL, format, arg);
    }

    /**
     * A convenience function which combines {@link #log(String, Object)} and {@link #indent()}.
     *
     * @param format a format string
     * @param arg the argument referenced by the format specifiers in {@code format}
     * @return an object that reverts to the current indentation level when
     *         {@linkplain Indent#close() closed} or null if debugging is disabled
     */
    public static Indent logAndIndent(int logLevel, String format, int arg) {
        if (ENABLED && Debug.isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg);
        }
        return null;
    }

    public static Indent logAndIndent(String format, int arg1, Object arg2) {
        return logAndIndent(BASIC_LOG_LEVEL, format, arg1, arg2);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public static Indent logAndIndent(int logLevel, String format, int arg1, Object arg2) {
        if (ENABLED && Debug.isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2);
        }
        return null;
    }

    public static Indent logAndIndent(String format, Object arg1, int arg2) {
        return logAndIndent(BASIC_LOG_LEVEL, format, arg1, arg2);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public static Indent logAndIndent(int logLevel, String format, Object arg1, int arg2) {
        if (ENABLED && Debug.isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2);
        }
        return null;
    }

    public static Indent logAndIndent(String format, int arg1, int arg2) {
        return logAndIndent(BASIC_LOG_LEVEL, format, arg1, arg2);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public static Indent logAndIndent(int logLevel, String format, int arg1, int arg2) {
        if (ENABLED && Debug.isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2);
        }
        return null;
    }

    public static Indent logAndIndent(String format, Object arg1, Object arg2) {
        return logAndIndent(BASIC_LOG_LEVEL, format, arg1, arg2);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public static Indent logAndIndent(int logLevel, String format, Object arg1, Object arg2) {
        if (ENABLED && Debug.isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2);
        }
        return null;
    }

    public static Indent logAndIndent(String format, Object arg1, Object arg2, Object arg3) {
        return logAndIndent(BASIC_LOG_LEVEL, format, arg1, arg2, arg3);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public static Indent logAndIndent(int logLevel, String format, Object arg1, Object arg2, Object arg3) {
        if (ENABLED && Debug.isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2, arg3);
        }
        return null;
    }

    public static Indent logAndIndent(String format, int arg1, int arg2, int arg3) {
        return logAndIndent(BASIC_LOG_LEVEL, format, arg1, arg2, arg3);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public static Indent logAndIndent(int logLevel, String format, int arg1, int arg2, int arg3) {
        if (ENABLED && Debug.isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2, arg3);
        }
        return null;
    }

    public static Indent logAndIndent(String format, Object arg1, int arg2, int arg3) {
        return logAndIndent(BASIC_LOG_LEVEL, format, arg1, arg2, arg3);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public static Indent logAndIndent(int logLevel, String format, Object arg1, int arg2, int arg3) {
        if (ENABLED && Debug.isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2, arg3);
        }
        return null;
    }

    public static Indent logAndIndent(String format, Object arg1, Object arg2, Object arg3, Object arg4) {
        return logAndIndent(BASIC_LOG_LEVEL, format, arg1, arg2, arg3, arg4);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public static Indent logAndIndent(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4) {
        if (ENABLED && Debug.isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2, arg3, arg4);
        }
        return null;
    }

    public static Indent logAndIndent(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        return logAndIndent(BASIC_LOG_LEVEL, format, arg1, arg2, arg3, arg4, arg5);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public static Indent logAndIndent(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        if (ENABLED && Debug.isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2, arg3, arg4, arg5);
        }
        return null;
    }

    public static Indent logAndIndent(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
        return logAndIndent(BASIC_LOG_LEVEL, format, arg1, arg2, arg3, arg4, arg5, arg6);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public static Indent logAndIndent(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
        if (ENABLED && Debug.isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2, arg3, arg4, arg5, arg6);
        }
        return null;
    }

    /**
     * A convenience function which combines {@link #logv(int, String, Object...)} and
     * {@link #indent()}.
     *
     * @param format a format string
     * @param args the arguments referenced by the format specifiers in {@code format}
     * @return an object that reverts to the current indentation level when
     *         {@linkplain Indent#close() closed} or null if debugging is disabled
     */
    public static Indent logvAndIndent(int logLevel, String format, Object... args) {
        if (ENABLED) {
            if (Debug.isLogEnabled(logLevel)) {
                return logvAndIndentInternal(logLevel, format, args);
            }
            return null;
        }
        throw new InternalError("Use of Debug.logvAndIndent() must be guarded by a test of Debug.isEnabled()");
    }

    private static Indent logvAndIndentInternal(int logLevel, String format, Object... args) {
        assert ENABLED && Debug.isLogEnabled(logLevel) : "must have checked Debug.isLogEnabled()";
        DebugScope scope = DebugScope.getInstance();
        scope.log(logLevel, format, args);
        return scope.pushIndentLogger();
    }

    /**
     * This override exists to catch cases when {@link #logAndIndent(String, Object)} is called with
     * one argument bound to a varargs method parameter. It will bind to this method instead of the
     * single arg variant and produce a deprecation warning instead of silently wrapping the
     * Object[] inside of another Object[].
     */
    @Deprecated
    public static void logAndIndent(String format, Object[] args) {
        assert false : "shouldn't use this";
        logAndIndent(BASIC_LOG_LEVEL, format, args);
    }

    /**
     * This override exists to catch cases when {@link #logAndIndent(int, String, Object)} is called
     * with one argument bound to a varargs method parameter. It will bind to this method instead of
     * the single arg variant and produce a deprecation warning instead of silently wrapping the
     * Object[] inside of another Object[].
     */
    @Deprecated
    public static void logAndIndent(int logLevel, String format, Object[] args) {
        assert false : "shouldn't use this";
        logvAndIndent(logLevel, format, args);
    }

    public static Iterable<Object> context() {
        if (ENABLED) {
            return DebugScope.getInstance().getCurrentContext();
        } else {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> contextSnapshot(Class<T> clazz) {
        if (ENABLED) {
            List<T> result = new ArrayList<>();
            for (Object o : context()) {
                if (clazz.isInstance(o)) {
                    result.add((T) o);
                }
            }
            return result;
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Searches the current debug scope, bottom up, for a context object that is an instance of a
     * given type. The first such object found is returned.
     */
    @SuppressWarnings("unchecked")
    public static <T> T contextLookup(Class<T> clazz) {
        if (ENABLED) {
            for (Object o : context()) {
                if (clazz.isInstance(o)) {
                    return ((T) o);
                }
            }
        }
        return null;
    }

    /**
     * Creates a {@linkplain DebugMemUseTracker memory use tracker} that is enabled iff debugging is
     * {@linkplain #isEnabled() enabled}.
     * <p>
     * A disabled tracker has virtually no overhead.
     */
    public static DebugMemUseTracker memUseTracker(CharSequence name) {
        if (!isUnconditionalMemUseTrackingEnabled && !ENABLED) {
            return VOID_MEM_USE_TRACKER;
        }
        return createMemUseTracker("%s", name, null);
    }

    /**
     * Creates a debug memory use tracker. Invoking this method is equivalent to:
     *
     * <pre>
     * Debug.memUseTracker(format, arg, null)
     * </pre>
     *
     * except that the string formatting only happens if mem tracking is enabled.
     *
     * @see #counter(String, Object, Object)
     */
    public static DebugMemUseTracker memUseTracker(String format, Object arg) {
        if (!isUnconditionalMemUseTrackingEnabled && !ENABLED) {
            return VOID_MEM_USE_TRACKER;
        }
        return createMemUseTracker(format, arg, null);
    }

    /**
     * Creates a debug memory use tracker. Invoking this method is equivalent to:
     *
     * <pre>
     * Debug.memUseTracker(String.format(format, arg1, arg2))
     * </pre>
     *
     * except that the string formatting only happens if memory use tracking is enabled. In
     * addition, each argument is subject to the following type based conversion before being passed
     * as an argument to {@link String#format(String, Object...)}:
     *
     * <pre>
     *     Type          | Conversion
     * ------------------+-----------------
     *  java.lang.Class  | arg.getSimpleName()
     *                   |
     * </pre>
     *
     * @see #memUseTracker(CharSequence)
     */
    public static DebugMemUseTracker memUseTracker(String format, Object arg1, Object arg2) {
        if (!isUnconditionalMemUseTrackingEnabled && !ENABLED) {
            return VOID_MEM_USE_TRACKER;
        }
        return createMemUseTracker(format, arg1, arg2);
    }

    private static DebugMemUseTracker createMemUseTracker(String format, Object arg1, Object arg2) {
        String name = formatDebugName(format, arg1, arg2);
        return DebugValueFactory.createMemUseTracker(name, !isUnconditionalMemUseTrackingEnabled);
    }

    /**
     * Creates a {@linkplain DebugCounter counter} that is enabled iff debugging is
     * {@linkplain #isEnabled() enabled} or the system property whose name is formed by adding
     * {@value #ENABLE_COUNTER_PROPERTY_NAME_PREFIX} to {@code name} is
     * {@linkplain Boolean#getBoolean(String) true}. If the latter condition is true, then the
     * returned counter is {@linkplain DebugCounter#isConditional() unconditional} otherwise it is
     * conditional.
     * <p>
     * A disabled counter has virtually no overhead.
     */
    public static DebugCounter counter(CharSequence name) {
        if (!areUnconditionalCountersEnabled() && !ENABLED) {
            return VOID_COUNTER;
        }
        return createCounter("%s", name, null);
    }

    /**
     * Creates a {@link DebugMethodMetrics metric} that is enabled iff debugging is
     * {@link #isEnabled() enabled}.
     */
    public static DebugMethodMetrics methodMetrics(ResolvedJavaMethod method) {
        if (isMethodMeterEnabled() && method != null) {
            return MethodMetricsImpl.getMethodMetrics(method);
        }
        return VOID_MM;
    }

    public static String applyFormattingFlagsAndWidth(String s, int flags, int width) {
        if (flags == 0 && width < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s);

        // apply width and justification
        int len = sb.length();
        if (len < width) {
            for (int i = 0; i < width - len; i++) {
                if ((flags & LEFT_JUSTIFY) == LEFT_JUSTIFY) {
                    sb.append(' ');
                } else {
                    sb.insert(0, ' ');
                }
            }
        }

        String res = sb.toString();
        if ((flags & UPPERCASE) == UPPERCASE) {
            res = res.toUpperCase();
        }
        return res;
    }

    /**
     * Creates a debug counter. Invoking this method is equivalent to:
     *
     * <pre>
     * Debug.counter(format, arg, null)
     * </pre>
     *
     * except that the string formatting only happens if count is enabled.
     *
     * @see #counter(String, Object, Object)
     */
    public static DebugCounter counter(String format, Object arg) {
        if (!areUnconditionalCountersEnabled() && !ENABLED) {
            return VOID_COUNTER;
        }
        return createCounter(format, arg, null);
    }

    /**
     * Creates a debug counter. Invoking this method is equivalent to:
     *
     * <pre>
     * Debug.counter(String.format(format, arg1, arg2))
     * </pre>
     *
     * except that the string formatting only happens if count is enabled. In addition, each
     * argument is subject to the following type based conversion before being passed as an argument
     * to {@link String#format(String, Object...)}:
     *
     * <pre>
     *     Type          | Conversion
     * ------------------+-----------------
     *  java.lang.Class  | arg.getSimpleName()
     *                   |
     * </pre>
     *
     * @see #counter(CharSequence)
     */
    public static DebugCounter counter(String format, Object arg1, Object arg2) {
        if (!areUnconditionalCountersEnabled() && !ENABLED) {
            return VOID_COUNTER;
        }
        return createCounter(format, arg1, arg2);
    }

    private static DebugCounter createCounter(String format, Object arg1, Object arg2) {
        String name = formatDebugName(format, arg1, arg2);
        boolean conditional = enabledCounters == null || !findMatch(enabledCounters, enabledCountersSubstrings, name);
        if (!ENABLED && conditional) {
            return VOID_COUNTER;
        }
        return DebugValueFactory.createCounter(name, conditional);
    }

    /**
     * Changes the debug configuration for the current thread.
     *
     * @param config new configuration to use for the current thread
     * @return an object that when {@linkplain DebugConfigScope#close() closed} will restore the
     *         debug configuration for the current thread to what it was before this method was
     *         called
     */
    public static DebugConfigScope setConfig(DebugConfig config) {
        if (ENABLED) {
            return new DebugConfigScope(config);
        } else {
            return null;
        }
    }

    /**
     * Creates an object for counting value frequencies.
     */
    public static DebugHistogram createHistogram(String name) {
        return new DebugHistogramImpl(name);
    }

    public static DebugConfig silentConfig() {
        return fixedConfig(0, 0, false, false, false, false, false, Collections.<DebugDumpHandler> emptyList(), Collections.<DebugVerifyHandler> emptyList(), null);
    }

    public static DebugConfig fixedConfig(final int logLevel, final int dumpLevel, final boolean isCountEnabled, final boolean isMemUseTrackingEnabled, final boolean isTimerEnabled,
                    final boolean isVerifyEnabled, final boolean isMMEnabled, final Collection<DebugDumpHandler> dumpHandlers, final Collection<DebugVerifyHandler> verifyHandlers,
                    final PrintStream output) {
        return new DebugConfig() {

            @Override
            public int getLogLevel() {
                return logLevel;
            }

            @Override
            public boolean isLogEnabledForMethod() {
                return logLevel > 0;
            }

            @Override
            public boolean isCountEnabled() {
                return isCountEnabled;
            }

            @Override
            public boolean isMemUseTrackingEnabled() {
                return isMemUseTrackingEnabled;
            }

            @Override
            public int getDumpLevel() {
                return dumpLevel;
            }

            @Override
            public boolean isDumpEnabledForMethod() {
                return dumpLevel > 0;
            }

            @Override
            public boolean isVerifyEnabled() {
                return isVerifyEnabled;
            }

            @Override
            public boolean isVerifyEnabledForMethod() {
                return isVerifyEnabled;
            }

            @Override
            public boolean isMethodMeterEnabled() {
                return isMMEnabled;
            }

            @Override
            public boolean isTimeEnabled() {
                return isTimerEnabled;
            }

            @Override
            public RuntimeException interceptException(Throwable e) {
                return null;
            }

            @Override
            public Collection<DebugDumpHandler> dumpHandlers() {
                return dumpHandlers;
            }

            @Override
            public Collection<DebugVerifyHandler> verifyHandlers() {
                return verifyHandlers;
            }

            @Override
            public PrintStream output() {
                return output;
            }

            @Override
            public void addToContext(Object o) {
            }

            @Override
            public void removeFromContext(Object o) {
            }
        };
    }

    private static final DebugCounter VOID_COUNTER = new DebugCounter() {

        @Override
        public void increment() {
        }

        @Override
        public void add(long value) {
        }

        @Override
        public void setConditional(boolean flag) {
            throw new InternalError("Cannot make void counter conditional");
        }

        @Override
        public boolean isConditional() {
            return false;
        }

        @Override
        public long getCurrentValue() {
            return 0L;
        }
    };

    private static final DebugMethodMetrics VOID_MM = new DebugMethodMetrics() {

        @Override
        public void addToMetric(long value, String metricName) {
        }

        @Override
        public void addToMetric(long value, String format, Object arg1) {
        }

        @Override
        public void addToMetric(long value, String format, Object arg1, Object arg2) {
        }

        @Override
        public void addToMetric(long value, String format, Object arg1, Object arg2, Object arg3) {
        }

        @Override
        public void incrementMetric(String metricName) {
        }

        @Override
        public void incrementMetric(String format, Object arg1) {
        }

        @Override
        public void incrementMetric(String format, Object arg1, Object arg2) {
        }

        @Override
        public void incrementMetric(String format, Object arg1, Object arg2, Object arg3) {
        }

        @Override
        public long getCurrentMetricValue(String metricName) {
            return 0;
        }

        @Override
        public long getCurrentMetricValue(String format, Object arg1) {
            return 0;
        }

        @Override
        public long getCurrentMetricValue(String format, Object arg1, Object arg2) {
            return 0;
        }

        @Override
        public long getCurrentMetricValue(String format, Object arg1, Object arg2, Object arg3) {
            return 0;
        }

        @Override
        public ResolvedJavaMethod getMethod() {
            return null;
        }

    };

    private static final DebugMemUseTracker VOID_MEM_USE_TRACKER = new DebugMemUseTracker() {

        @Override
        public DebugCloseable start() {
            return DebugCloseable.VOID_CLOSEABLE;
        }

        @Override
        public long getCurrentValue() {
            return 0;
        }
    };

    /**
     * @see #timer(CharSequence)
     */
    public static final String ENABLE_TIMER_PROPERTY_NAME_PREFIX = "graaldebug.timer.";

    /**
     * @see #counter(CharSequence)
     */
    public static final String ENABLE_COUNTER_PROPERTY_NAME_PREFIX = "graaldebug.counter.";

    /**
     * Set of unconditionally enabled counters. Possible values and their meanings:
     * <ul>
     * <li>{@code null}: no unconditionally enabled counters</li>
     * <li>{@code isEmpty()}: all counters are unconditionally enabled</li>
     * <li>{@code !isEmpty()}: use {@link #findMatch(Set, Set, String)} on this set and
     * {@link #enabledCountersSubstrings} to determine which counters are unconditionally enabled
     * </li>
     * </ul>
     */
    private static final Set<String> enabledCounters;

    /**
     * Set of unconditionally enabled timers. Same interpretation of values as for
     * {@link #enabledCounters}.
     */
    private static final Set<String> enabledTimers;

    private static final Set<String> enabledCountersSubstrings = new HashSet<>();
    private static final Set<String> enabledTimersSubstrings = new HashSet<>();

    /**
     * Specifies if all mem use trackers are unconditionally enabled.
     */
    private static final boolean isUnconditionalMemUseTrackingEnabled;

    static {
        Set<String> counters = new HashSet<>();
        Set<String> timers = new HashSet<>();
        parseCounterAndTimerSystemProperties(counters, timers, enabledCountersSubstrings, enabledTimersSubstrings);
        counters = counters.isEmpty() && enabledCountersSubstrings.isEmpty() ? null : counters;
        timers = timers.isEmpty() && enabledTimersSubstrings.isEmpty() ? null : timers;
        if (counters == null && params.enableUnscopedCounters && !params.enableMethodFilter) {
            counters = Collections.emptySet();
        }
        if (timers == null && params.enableUnscopedTimers && !params.enableMethodFilter) {
            timers = Collections.emptySet();
        }
        enabledCounters = counters;
        enabledTimers = timers;
        isUnconditionalMemUseTrackingEnabled = params.enableUnscopedMemUseTrackers;
        DebugValueFactory = initDebugValueFactory();
    }

    private static DebugValueFactory initDebugValueFactory() {
        return new DebugValueFactory() {

            @Override
            public DebugTimer createTimer(String name, boolean conditional) {
                return new TimerImpl(name, conditional, params.interceptTime);
            }

            @Override
            public DebugCounter createCounter(String name, boolean conditional) {
                return CounterImpl.create(name, conditional, params.interceptCount);
            }

            @Override
            public DebugMethodMetrics createMethodMetrics(ResolvedJavaMethod method) {
                return MethodMetricsImpl.getMethodMetrics(method);
            }

            @Override
            public DebugMemUseTracker createMemUseTracker(String name, boolean conditional) {
                return new MemUseTrackerImpl(name, conditional, params.interceptMem);
            }
        };
    }

    private static DebugValueFactory DebugValueFactory;

    public static void setDebugValueFactory(DebugValueFactory factory) {
        Objects.requireNonNull(factory);
        DebugValueFactory = factory;
    }

    public static DebugValueFactory getDebugValueFactory() {
        return DebugValueFactory;
    }

    private static boolean findMatch(Set<String> haystack, Set<String> haystackSubstrings, String needle) {
        if (haystack.isEmpty() && haystackSubstrings.isEmpty()) {
            // Empty haystack means match all
            return true;
        }
        if (haystack.contains(needle)) {
            return true;
        }
        if (!haystackSubstrings.isEmpty()) {
            for (String h : haystackSubstrings) {
                if (needle.startsWith(h)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean areUnconditionalTimersEnabled() {
        return enabledTimers != null;
    }

    public static boolean areUnconditionalCountersEnabled() {
        return enabledCounters != null;
    }

    public static boolean isMethodFilteringEnabled() {
        return params.enableMethodFilter;
    }

    public static boolean areUnconditionalMethodMetricsEnabled() {
        // we do not collect mm substrings
        return params.enableUnscopedMethodMetrics;
    }

    protected static void parseCounterAndTimerSystemProperties(Set<String> counters, Set<String> timers, Set<String> countersSubstrings, Set<String> timersSubstrings) {
        do {
            try {
                for (Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
                    String name = e.getKey().toString();
                    if (name.startsWith(ENABLE_COUNTER_PROPERTY_NAME_PREFIX) && Boolean.parseBoolean(e.getValue().toString())) {
                        if (name.endsWith("*")) {
                            countersSubstrings.add(name.substring(ENABLE_COUNTER_PROPERTY_NAME_PREFIX.length(), name.length() - 1));
                        } else {
                            counters.add(name.substring(ENABLE_COUNTER_PROPERTY_NAME_PREFIX.length()));
                        }
                    }
                    if (name.startsWith(ENABLE_TIMER_PROPERTY_NAME_PREFIX) && Boolean.parseBoolean(e.getValue().toString())) {
                        if (name.endsWith("*")) {
                            timersSubstrings.add(name.substring(ENABLE_TIMER_PROPERTY_NAME_PREFIX.length(), name.length() - 1));
                        } else {
                            timers.add(name.substring(ENABLE_TIMER_PROPERTY_NAME_PREFIX.length()));
                        }
                    }
                }
                return;
            } catch (ConcurrentModificationException e) {
                // Iterating over the system properties may race with another thread that is
                // updating the system properties. Simply try again in this case.
            }
        } while (true);
    }

    /**
     * Creates a {@linkplain DebugTimer timer} that is enabled iff debugging is
     * {@linkplain #isEnabled() enabled} or the system property whose name is formed by adding
     * {@value #ENABLE_TIMER_PROPERTY_NAME_PREFIX} to {@code name} is
     * {@linkplain Boolean#getBoolean(String) true}. If the latter condition is true, then the
     * returned timer is {@linkplain DebugCounter#isConditional() unconditional} otherwise it is
     * conditional.
     * <p>
     * A disabled timer has virtually no overhead.
     */
    public static DebugTimer timer(CharSequence name) {
        if (!areUnconditionalTimersEnabled() && !ENABLED) {
            return VOID_TIMER;
        }
        return createTimer("%s", name, null);
    }

    /**
     * Creates a debug timer. Invoking this method is equivalent to:
     *
     * <pre>
     * Debug.timer(format, arg, null)
     * </pre>
     *
     * except that the string formatting only happens if timing is enabled.
     *
     * @see #timer(String, Object, Object)
     */
    public static DebugTimer timer(String format, Object arg) {
        if (!areUnconditionalTimersEnabled() && !ENABLED) {
            return VOID_TIMER;
        }
        return createTimer(format, arg, null);
    }

    /**
     * Creates a debug timer. Invoking this method is equivalent to:
     *
     * <pre>
     * Debug.timer(String.format(format, arg1, arg2))
     * </pre>
     *
     * except that the string formatting only happens if timing is enabled. In addition, each
     * argument is subject to the following type based conversion before being passed as an argument
     * to {@link String#format(String, Object...)}:
     *
     * <pre>
     *     Type          | Conversion
     * ------------------+-----------------
     *  java.lang.Class  | arg.getSimpleName()
     *                   |
     * </pre>
     *
     * @see #timer(CharSequence)
     */
    public static DebugTimer timer(String format, Object arg1, Object arg2) {
        if (!areUnconditionalTimersEnabled() && !ENABLED) {
            return VOID_TIMER;
        }
        return createTimer(format, arg1, arg2);
    }

    /**
     * There are paths where construction of formatted class names are common and the code below is
     * surprisingly expensive, so compute it once and cache it.
     */
    private static final ClassValue<String> formattedClassName = new ClassValue<String>() {
        @Override
        protected String computeValue(Class<?> c) {
            final String simpleName = c.getSimpleName();
            Class<?> enclosingClass = c.getEnclosingClass();
            if (enclosingClass != null) {
                String prefix = "";
                while (enclosingClass != null) {
                    prefix = enclosingClass.getSimpleName() + "_" + prefix;
                    enclosingClass = enclosingClass.getEnclosingClass();
                }
                return prefix + simpleName;
            } else {
                return simpleName;
            }
        }
    };

    public static Object convertFormatArg(Object arg) {
        if (arg instanceof Class) {
            return formattedClassName.get((Class<?>) arg);
        }
        return arg;
    }

    private static String formatDebugName(String format, Object arg1, Object arg2) {
        return String.format(format, convertFormatArg(arg1), convertFormatArg(arg2));
    }

    private static DebugTimer createTimer(String format, Object arg1, Object arg2) {
        String name = formatDebugName(format, arg1, arg2);
        boolean conditional = enabledTimers == null || !findMatch(enabledTimers, enabledTimersSubstrings, name);
        if (!ENABLED && conditional) {
            return VOID_TIMER;
        }
        return DebugValueFactory.createTimer(name, conditional);
    }

    private static final DebugTimer VOID_TIMER = new DebugTimer() {

        @Override
        public DebugCloseable start() {
            return DebugCloseable.VOID_CLOSEABLE;
        }

        @Override
        public void setConditional(boolean flag) {
            throw new InternalError("Cannot make void timer conditional");
        }

        @Override
        public boolean isConditional() {
            return false;
        }

        @Override
        public long getCurrentValue() {
            return 0L;
        }

        @Override
        public TimeUnit getTimeUnit() {
            return null;
        }
    };
}
