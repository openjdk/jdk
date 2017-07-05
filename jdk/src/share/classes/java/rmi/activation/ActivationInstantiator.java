/*
 * Copyright (c) 1997, 2005, Oracle and/or its affiliates. All rights reserved.
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

package java.rmi.activation;

import java.rmi.MarshalledObject;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * An <code>ActivationInstantiator</code> is responsible for creating
 * instances of "activatable" objects. A concrete subclass of
 * <code>ActivationGroup</code> implements the <code>newInstance</code>
 * method to handle creating objects within the group.
 *
 * @author      Ann Wollrath
 * @see         ActivationGroup
 * @since       1.2
 */
public interface ActivationInstantiator extends Remote {

   /**
    * The activator calls an instantiator's <code>newInstance</code>
    * method in order to recreate in that group an object with the
    * activation identifier, <code>id</code>, and descriptor,
    * <code>desc</code>. The instantiator is responsible for: <ul>
    *
    * <li> determining the class for the object using the descriptor's
    * <code>getClassName</code> method,
    *
    * <li> loading the class from the code location obtained from the
    * descriptor (using the <code>getLocation</code> method),
    *
    * <li> creating an instance of the class by invoking the special
    * "activation" constructor of the object's class that takes two
    * arguments: the object's <code>ActivationID</code>, and the
    * <code>MarshalledObject</code> containing object specific
    * initialization data, and
    *
    * <li> returning a MarshalledObject containing the stub for the
    * remote object it created </ul>
    *
    * @param id the object's activation identifier
    * @param desc the object's descriptor
    * @return a marshalled object containing the serialized
    * representation of remote object's stub
    * @exception ActivationException if object activation fails
    * @exception RemoteException if remote call fails
    * @since 1.2
    */
    public MarshalledObject<? extends Remote> newInstance(ActivationID id,
                                                          ActivationDesc desc)
        throws ActivationException, RemoteException;
}
