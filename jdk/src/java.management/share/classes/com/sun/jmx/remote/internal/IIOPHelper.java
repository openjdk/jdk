/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jmx.remote.internal;

import java.util.Properties;
import java.io.IOException;
import java.rmi.Remote;
import java.rmi.NoSuchObjectException;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * A helper class for RMI-IIOP and CORBA APIs.
 */

public final class IIOPHelper {
    private IIOPHelper() { }

    // loads IIOPProxy implementation class if available
    private static final String IMPL_CLASS =
        "com.sun.jmx.remote.protocol.iiop.IIOPProxyImpl";
    private static final IIOPProxy proxy =
        AccessController.doPrivileged(new PrivilegedAction<IIOPProxy>() {
            public IIOPProxy run() {
                try {
                    Class<?> c = Class.forName(IMPL_CLASS, true,
                                               IIOPHelper.class.getClassLoader());
                    return (IIOPProxy)c.newInstance();
                } catch (ClassNotFoundException cnf) {
                    return null;
                } catch (InstantiationException e) {
                    throw new AssertionError(e);
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }});

    /**
     * Returns true if RMI-IIOP and CORBA is available.
     */
    public static boolean isAvailable() {
        return proxy != null;
    }

    private static void ensureAvailable() {
        if (proxy == null)
            throw new AssertionError("Should not here");
    }

    /**
     * Returns true if the given object is a Stub.
     */
    public static boolean isStub(Object obj) {
        return (proxy == null) ? false : proxy.isStub(obj);
    }

    /**
     * Returns the Delegate to which the given Stub delegates.
     */
    public static Object getDelegate(Object stub) {
        ensureAvailable();
        return proxy.getDelegate(stub);
    }

    /**
     * Sets the Delegate for a given Stub.
     */
    public static void setDelegate(Object stub, Object delegate) {
        ensureAvailable();
        proxy.setDelegate(stub, delegate);
    }

    /**
     * Returns the ORB associated with the given stub
     *
     * @throws  UnsupportedOperationException
     *          if the object does not support the operation that
     *          was invoked
     */
    public static Object getOrb(Object stub) {
        ensureAvailable();
        return proxy.getOrb(stub);
    }

    /**
     * Connects the Stub to the given ORB.
     */
    public static void connect(Object stub, Object orb)
        throws IOException
    {
        if (proxy == null)
            throw new IOException("Connection to ORB failed, RMI/IIOP not available");
        proxy.connect(stub, orb);
    }

    /**
     * Returns true if the given object is an ORB.
     */
    public static boolean isOrb(Object obj) {
        return (proxy == null) ? false : proxy.isOrb(obj);
    }

    /**
     * Creates, and returns, a new ORB instance.
     */
    public static Object createOrb(String[] args, Properties props)
        throws IOException
    {
        if (proxy == null)
            throw new IOException("ORB initialization failed, RMI/IIOP not available");
        return proxy.createOrb(args, props);
    }

    /**
     * Converts a string, produced by the object_to_string method, back
     * to a CORBA object reference.
     */
    public static Object stringToObject(Object orb, String str) {
        ensureAvailable();
        return proxy.stringToObject(orb, str);
    }

    /**
     * Converts the given CORBA object reference to a string.
     */
    public static String objectToString(Object orb, Object obj) {
        ensureAvailable();
        return proxy.objectToString(orb, obj);
    }

    /**
     * Checks to ensure that an object of a remote or abstract interface
     * type can be cast to a desired type.
     */
    public static <T> T narrow(Object narrowFrom, Class<T> narrowTo) {
        ensureAvailable();
        return proxy.narrow(narrowFrom, narrowTo);
    }

    /**
     * Makes a server object ready to receive remote calls
     */
    public static void exportObject(Remote obj) throws IOException {
        if (proxy == null)
            throw new IOException("RMI object cannot be exported, RMI/IIOP not available");
        proxy.exportObject(obj);
    }

    /**
     * Deregisters a server object from the runtime.
     */
    public static void unexportObject(Remote obj) throws IOException {
        if (proxy == null)
            throw new NoSuchObjectException("Object not exported");
        proxy.unexportObject(obj);
    }

    /**
     * Returns a stub for the given server object.
     */
    public static Remote toStub(Remote obj) throws IOException {
        if (proxy == null)
            throw new NoSuchObjectException("Object not exported");
        return proxy.toStub(obj);
    }
}
