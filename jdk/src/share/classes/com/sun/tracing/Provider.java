/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package com.sun.tracing;

/**
 * {@code Provider} is a superinterface for user-defined tracing providers.
 * <p>
 * To define tracepoints, users must extend this interface
 * and then use a {@code ProviderFactory} to create an instance of the
 * newly-defined interface.  Each method in the defined interface represents a
 * tracepoint (or probe), which can be triggered by calling the associated
 * method on the returned instance.
 * <p>
 * This interface also contains a {@code getProbe()} method, which can be
 * used to get direct handles to the {@code Probe} objects themselves.
 * {@code Probe} objects can be triggered manually, or they can be queried to
 * check their state.
 * <p>
 * When an application has finished triggering probes, it should call
 * {@code dispose()} to free up any system resources associated with the
 * Provider.
 * <p>
 * All methods declared in a subclass of this interface should have a
 * {@code void} return type. Methods can have parameters, and when called the
 * values of the arguments will be passed to the tracing implementation.
 * If any methods do not have a {@code void} return type, an
 * {@code java.lang.IllegalArgumentException} will be thrown when the
 * provider is registered.
 * @since 1.7
 */

public interface Provider {
    /**
     * Retrieves a reference to a Probe object, which is used to check status
     * or to trigger the probe manually.
     *
     * If the provided method parameter is not a method of the provider
     * interface,  or if the provider interface has been disposed, then
     * this returns null
     *
     * @param method a method declared in the provider.
     * @return the specified probe represented by that method, or null.
     */
    Probe getProbe(java.lang.reflect.Method method);

    /**
     * Disposes system resources associated with this provider.
     *
     * After calling this method, triggering the probes will have no effect.
     * Additional calls to this method after the first call are ignored.
     */
    void dispose();
}
