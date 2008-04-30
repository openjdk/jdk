/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
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
