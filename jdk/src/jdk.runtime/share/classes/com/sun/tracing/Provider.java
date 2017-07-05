/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
