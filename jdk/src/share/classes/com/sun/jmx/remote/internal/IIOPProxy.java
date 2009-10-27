/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.jmx.remote.internal;

import java.util.Properties;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;

/**
 * An interface to a subset of the RMI-IIOP and CORBA APIs to avoid a
 * static dependencies on the types defined by these APIs.
 */

public interface IIOPProxy {

    /**
     * Returns true if the given object is a Stub.
     */
    boolean isStub(Object obj);

    /**
     * Returns the Delegate to which the given Stub delegates.
     */
    Object getDelegate(Object stub);

    /**
     * Sets the Delegate for a given Stub.
     */
    void setDelegate(Object stub, Object delegate);

    /**
     * Returns the ORB associated with the given stub
     *
     * @throws  UnsupportedOperationException
     *          if the object does not support the operation that
     *          was invoked
     */
    Object getOrb(Object stub);

    /**
     * Connects the Stub to the given ORB.
     */
    void connect(Object stub, Object orb) throws RemoteException;

    /**
     * Returns true if the given object is an ORB.
     */
    boolean isOrb(Object obj);

    /**
     * Creates, and returns, a new ORB instance.
     */
    Object createOrb(String[] args, Properties props);

    /**
     * Converts a string, produced by the object_to_string method, back
     * to a CORBA object reference.
     */
    Object stringToObject(Object orb, String str);

    /**
     * Converts the given CORBA object reference to a string.
     */
    String objectToString(Object orb, Object obj);

    /**
     * Checks to ensure that an object of a remote or abstract interface
     * type can be cast to a desired type.
     */
    <T> T narrow(Object narrowFrom, Class<T> narrowTo);

    /**
     * Makes a server object ready to receive remote calls
     */
    void exportObject(Remote obj) throws RemoteException;

    /**
     * Deregisters a server object from the runtime.
     */
    void unexportObject(Remote obj) throws NoSuchObjectException;

    /**
     * Returns a stub for the given server object.
     */
    Remote toStub(Remote obj) throws NoSuchObjectException;
}
