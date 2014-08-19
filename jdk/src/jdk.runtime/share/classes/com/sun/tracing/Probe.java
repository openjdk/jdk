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
 * The {@code Probe} interface represents a tracepoint.
 *
 * A {@code Probe} instance is obtained by calling the
 * {@code Provider.getProbe()} method of a provider instance created by
 * {@code ProviderFactory.createProvider()}.  A {@code Probe} can be used to
 * trigger a probe manually (provided the correct arguments are passed to
 * it), or to check a probe to see if anything is currently tracing it.
 * <p>
 * A tracing check can be used to avoid lengthy work that might be
 * needed to set up the probe's arguments.  However, checking
 * whether the probe is enabled generally takes the same amount of time
 * as actually triggering the probe. So, you should only check a probe's status
 * without triggering it if setting up the arguments is very expensive.
 * <p>
 * Users do not need to implement this interface: instances are
 * created automatically by the system when a {@code Provider)} instance is
 * created.
 * <p>
 * @since 1.7
 */

public interface Probe {
    /**
     * Checks whether there is an active trace of this probe.
     *
     * @return true if an active trace is detected.
     */
    boolean isEnabled();

    /**
     * Determines whether a tracepoint is enabled.
     *
     * Typically, users do not need to use this method. It is called
     * automatically when a Provider's instance method is called. Calls to
     * this method expect the arguments to match the declared parameters for
     * the method associated with the probe.
     *
     * @param args the parameters to pass to the method.
     * @throws IllegalArgumentException if the provided parameters do not
     * match the method declaration for this probe.
     */
    void trigger(Object ... args);
}
