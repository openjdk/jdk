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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;
import org.omg.CORBA.ORB;

/**
 * Supports delegation for method implementations in {@link Stub}.
 * A delegate is an instance of a class that implements this
 * interface and provides a replacement implementation for all the
 * methods of <code>javax.rmi.CORBA.Stub</code>.  If delegation is
 * enabled, each stub has an associated delegate.
 *
 * Delegates are enabled by providing the delegate's class name as the
 * value of the
 * <code>javax.rmi.CORBA.StubClass</code>
 * system property.
 *
 * @see Stub
 */
public interface StubDelegate {

    /**
     * Delegation call for {@link Stub#hashCode}.
     */
    int hashCode(Stub self);

    /**
     * Delegation call for {@link Stub#equals}.
     */
    boolean equals(Stub self, java.lang.Object obj);

    /**
     * Delegation call for {@link Stub#toString}.
     */
    String toString(Stub self);

    /**
     * Delegation call for {@link Stub#connect}.
     */
    void connect(Stub self, ORB orb)
        throws RemoteException;

    // _REVISIT_ cannot link to Stub.readObject directly... why not?
    /**
     * Delegation call for
     * <a href="{@docRoot}/serialized-form.html#javax.rmi.CORBA.Stub"><code>Stub.readObject(java.io.ObjectInputStream)</code></a>.
     */
    void readObject(Stub self, ObjectInputStream s)
        throws IOException, ClassNotFoundException;

    // _REVISIT_ cannot link to Stub.writeObject directly... why not?
    /**
     * Delegation call for
     * <a href="{@docRoot}/serialized-form.html#javax.rmi.CORBA.Stub"><code>Stub.writeObject(java.io.ObjectOutputStream)</code></a>.
     */
    void writeObject(Stub self, ObjectOutputStream s)
        throws IOException;

}
