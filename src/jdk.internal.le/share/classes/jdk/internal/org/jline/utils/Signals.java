/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Objects;

/**
 * Signal handling utilities for terminal applications.
 *
 * <p>
 * The Signals class provides utilities for registering and handling system signals
 * in a platform-independent way. It allows terminal applications to respond to
 * signals such as SIGINT (Ctrl+C), SIGTSTP (Ctrl+Z), and others, without having
 * to use platform-specific code.
 * </p>
 *
 * <p>
 * This class uses reflection to access the underlying signal handling mechanisms
 * of the JVM, which may vary depending on the platform and JVM implementation.
 * It provides a consistent API for signal handling across different environments.
 * </p>
 *
 * <p>
 * Signal handling is particularly important for terminal applications that need
 * to respond to user interrupts or that need to perform cleanup operations when
 * the application is terminated.
 * </p>
 *
 * @since 3.0
 */
public final class Signals {

    private Signals() {}

    /**
     * Registers a handler for the specified signal.
     *
     * <p>
     * This method registers a handler for the specified signal. The handler will be
     * called when the signal is received. The method returns an object that can be
     * used to unregister the handler later.
     * </p>
     *
     * <p>
     * Signal names are platform-dependent, but common signals include:
     * </p>
     * <ul>
     *   <li>INT - Interrupt signal (typically Ctrl+C)</li>
     *   <li>TERM - Termination signal</li>
     *   <li>HUP - Hangup signal</li>
     *   <li>CONT - Continue signal</li>
     *   <li>STOP - Stop signal (typically Ctrl+Z)</li>
     *   <li>WINCH - Window change signal</li>
     * </ul>
     *
     * <p>Example usage:</p>
     * <pre>
     * Object handle = Signals.register("INT", () -> {
     *     System.out.println("Caught SIGINT");
     *     // Perform cleanup
     * });
     *
     * // Later, when no longer needed
     * Signals.unregister("INT", handle);
     * </pre>
     *
     * @param name the signal name (e.g., "INT", "TERM", "HUP")
     * @param handler the callback to run when the signal is received
     * @return an object that can be used to unregister the handler
     * @see #unregister(String, Object)
     *         method to unregister the handler
     */
    public static Object register(String name, Runnable handler) {
        Objects.requireNonNull(handler);
        return register(name, handler, handler.getClass().getClassLoader());
    }

    public static Object register(String name, final Runnable handler, ClassLoader loader) {
        try {
            Class<?> signalHandlerClass = Class.forName("sun.misc.SignalHandler");
            // Implement signal handler
            Object signalHandler =
                    Proxy.newProxyInstance(loader, new Class<?>[] {signalHandlerClass}, (proxy, method, args) -> {
                        // only method we are proxying is handle()
                        if (method.getDeclaringClass() == Object.class) {
                            if ("toString".equals(method.getName())) {
                                return handler.toString();
                            }
                        } else if (method.getDeclaringClass() == signalHandlerClass) {
                            Log.trace(() -> "Calling handler " + toString(handler) + " for signal " + name);
                            handler.run();
                        }
                        return null;
                    });
            return doRegister(name, signalHandler);
        } catch (Exception e) {
            // Ignore this one too, if the above failed, the signal API is incompatible with what we're expecting
            Log.debug("Error registering handler for signal ", name, e);
            return null;
        }
    }

    public static Object registerDefault(String name) {
        try {
            Class<?> signalHandlerClass = Class.forName("sun.misc.SignalHandler");
            return doRegister(name, signalHandlerClass.getField("SIG_DFL").get(null));
        } catch (Exception e) {
            // Ignore this one too, if the above failed, the signal API is incompatible with what we're expecting
            Log.debug("Error registering default handler for signal ", name, e);
            return null;
        }
    }

    public static void unregister(String name, Object previous) {
        try {
            // We should make sure the current signal is the one we registered
            if (previous != null) {
                doRegister(name, previous);
            }
        } catch (Exception e) {
            // Ignore
            Log.debug("Error unregistering handler for signal ", name, e);
        }
    }

    private static Object doRegister(String name, Object handler) throws Exception {
        Log.trace(() -> "Registering signal " + name + " with handler " + toString(handler));
        Class<?> signalClass = Class.forName("sun.misc.Signal");
        Constructor<?> constructor = signalClass.getConstructor(String.class);
        Object signal;
        try {
            signal = constructor.newInstance(name);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IllegalArgumentException) {
                Log.trace(() -> "Ignoring unsupported signal " + name);
            } else {
                Log.debug("Error registering handler for signal ", name, e);
            }
            return null;
        }
        Class<?> signalHandlerClass = Class.forName("sun.misc.SignalHandler");
        return signalClass.getMethod("handle", signalClass, signalHandlerClass).invoke(null, signal, handler);
    }

    @SuppressWarnings("")
    private static String toString(Object handler) {
        try {
            Class<?> signalHandlerClass = Class.forName("sun.misc.SignalHandler");
            if (handler == signalHandlerClass.getField("SIG_DFL").get(null)) {
                return "SIG_DFL";
            }
            if (handler == signalHandlerClass.getField("SIG_IGN").get(null)) {
                return "SIG_IGN";
            }
        } catch (Throwable t) {
            // ignore
        }
        return handler != null ? handler.toString() : "null";
    }
}
