/*
 * Copyright (c) 1999, 2001, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package javax.rmi.CORBA;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import javax.rmi.CORBA.Tie;
import javax.rmi.CORBA.ValueHandler;
import org.omg.CORBA.ORB;
import org.omg.CORBA.portable.InputStream;
import org.omg.CORBA.portable.OutputStream;
import org.omg.CORBA.SystemException;

/**
 * Supports delegation for method implementations in {@link Util}.  The
 * delegate is a singleton instance of a class that implements this
 * interface and provides a replacement implementation for all the
 * methods of <code>javax.rmi.CORBA.Util</code>.
 *
 * Delegation is enabled by providing the delegate's class name as the
 * value of the
 * <code>javax.rmi.CORBA.UtilClass</code>
 * system property.
 *
 * @see Util
 */
public interface UtilDelegate {

    /**
     * Delegation call for {@link Util#mapSystemException}.
     */
    RemoteException mapSystemException(SystemException ex);

    /**
     * Delegation call for {@link Util#writeAny}.
     */
    void writeAny(OutputStream out, Object obj);

    /**
     * Delegation call for {@link Util#readAny}.
     */
    java.lang.Object readAny(InputStream in);

    /**
     * Delegation call for {@link Util#writeRemoteObject}.
     */
    void writeRemoteObject(OutputStream out, Object obj);

    /**
     * Delegation call for {@link Util#writeAbstractObject}.
     */
    void writeAbstractObject(OutputStream out, Object obj);

    /**
     * Delegation call for {@link Util#registerTarget}.
     */
    void registerTarget(Tie tie, Remote target);

    /**
     * Delegation call for {@link Util#unexportObject}.
     */
    void unexportObject(Remote target) throws java.rmi.NoSuchObjectException;

    /**
     * Delegation call for {@link Util#getTie}.
     */
    Tie getTie(Remote target);

    /**
     * Delegation call for {@link Util#createValueHandler}.
     */
    ValueHandler createValueHandler();

    /**
     * Delegation call for {@link Util#getCodebase}.
     */
    String getCodebase(Class clz);

    /**
     * Delegation call for {@link Util#loadClass}.
     */
    Class loadClass(String className, String remoteCodebase, ClassLoader loader)
        throws ClassNotFoundException;

    /**
     * Delegation call for {@link Util#isLocal}.
     */
    boolean isLocal(Stub stub) throws RemoteException;

    /**
     * Delegation call for {@link Util#wrapException}.
     */
    RemoteException wrapException(Throwable obj);

    /**
     * Delegation call for {@link Util#copyObject}.
     */
    Object copyObject(Object obj, ORB orb) throws RemoteException;

    /**
     * Delegation call for {@link Util#copyObjects}.
     */
    Object[] copyObjects(Object[] obj, ORB orb) throws RemoteException;

}
