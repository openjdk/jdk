/*
 * Copyright (c) 1999, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.javax.rmi;

import java.lang.reflect.Method ;

import javax.rmi.CORBA.Tie;
import javax.rmi.CORBA.Util;

import java.rmi.RemoteException;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;

import java.util.Properties;

import org.omg.CORBA.ORB;
import org.omg.CORBA.portable.Delegate;
import org.omg.CORBA.SystemException;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.server.RemoteStub;
import java.rmi.server.ExportException;

import java.net.URL;

import com.sun.corba.se.impl.util.JDKBridge;
import com.sun.corba.se.impl.util.Utility;
import com.sun.corba.se.impl.util.RepositoryId;

import com.sun.corba.se.spi.presentation.rmi.StubAdapter;

import java.security.AccessController;
import com.sun.corba.se.impl.orbutil.GetPropertyAction;

/**
 * Server implementation objects may either inherit from
 * javax.rmi.PortableRemoteObject or they may implement a remote interface
 * and then use the exportObject method to register themselves as a server object.
 * The toStub method takes a server implementation and returns a stub that
 * can be used to access that server object.
 * The connect method makes a Remote object ready for remote communication.
 * The unexportObject method is used to deregister a server object, allowing it to become
 * available for garbage collection.
 * The narrow method takes an object reference or abstract interface type and
 * attempts to narrow it to conform to
 * the given interface. If the operation is successful the result will be an
 * object of the specified type, otherwise an exception will be thrown.
 */
public class PortableRemoteObject
        implements javax.rmi.CORBA.PortableRemoteObjectDelegate {

    /**
     * Makes a server object ready to receive remote calls. Note
     * that subclasses of PortableRemoteObject do not need to call this
     * method, as it is called by the constructor.
     * @param obj the server object to export.
     * @exception RemoteException if export fails.
     */
    public void exportObject(Remote obj)
        throws RemoteException {

        if (obj == null) {
            throw new NullPointerException("invalid argument");
        }

        // Has this object already been exported to IIOP?

        if (Util.getTie(obj) != null) {

            // Yes, so this is an error...

            throw new ExportException (obj.getClass().getName() + " already exported");
        }

        // Can we load a Tie?

        Tie theTie = Utility.loadTie(obj);

        if (theTie != null) {

            // Yes, so export it to IIOP...

            Util.registerTarget(theTie,obj);

        } else {

            // No, so export to JRMP. If this is called twice for the
            // same object, it will throw an ExportException...

            UnicastRemoteObject.exportObject(obj);
        }
    }

    /**
     * Returns a stub for the given server object.
     * @param obj the server object for which a stub is required. Must either be a subclass
     * of PortableRemoteObject or have been previously the target of a call to
     * {@link #exportObject}.
     * @return the most derived stub for the object.
     * @exception NoSuchObjectException if a stub cannot be located for the given server object.
     */
    public Remote toStub (Remote obj)
        throws NoSuchObjectException
    {
        Remote result = null;
        if (obj == null) {
            throw new NullPointerException("invalid argument");
        }

        // If the class is already an IIOP stub then return it.
        if (StubAdapter.isStub( obj )) {
            return obj;
        }

        // If the class is already a JRMP stub then return it.
        if (obj instanceof java.rmi.server.RemoteStub) {
            return obj;
        }

        // Has it been exported to IIOP?
        Tie theTie = Util.getTie(obj);

        if (theTie != null) {
            result = Utility.loadStub(theTie,null,null,true);
        } else {
            if (Utility.loadTie(obj) == null) {
                result = java.rmi.server.RemoteObject.toStub(obj);
            }
        }

        if (result == null) {
            throw new NoSuchObjectException("object not exported");
        }

        return result;
    }

    /**
     * Deregisters a server object from the runtime, allowing the object to become
     * available for garbage collection.
     * @param obj the object to unexport.
     * @exception NoSuchObjectException if the remote object is not
     * currently exported.
     */
    public void unexportObject(Remote obj)
        throws NoSuchObjectException {

        if (obj == null) {
            throw new NullPointerException("invalid argument");
        }

        if (StubAdapter.isStub(obj) ||
            obj instanceof java.rmi.server.RemoteStub) {
            throw new NoSuchObjectException(
                "Can only unexport a server object.");
        }

        Tie theTie = Util.getTie(obj);
        if (theTie != null) {
            Util.unexportObject(obj);
        } else {
            if (Utility.loadTie(obj) == null) {
                UnicastRemoteObject.unexportObject(obj,true);
            } else {
                throw new NoSuchObjectException("Object not exported.");
            }
        }
    }

    /**
     * Checks to ensure that an object of a remote or abstract interface type
     * can be cast to a desired type.
     * @param narrowFrom the object to check.
     * @param narrowTo the desired type.
     * @return an object which can be cast to the desired type.
     * @throws ClassCastException if narrowFrom cannot be cast to narrowTo.
     */
    public java.lang.Object narrow ( java.lang.Object narrowFrom,
        java.lang.Class narrowTo) throws ClassCastException
    {
        java.lang.Object result = null;

        if (narrowFrom == null)
            return null;

        if (narrowTo == null)
            throw new NullPointerException("invalid argument");

        try {
            if (narrowTo.isAssignableFrom(narrowFrom.getClass()))
                return narrowFrom;

            // Is narrowTo an interface that might be
            // implemented by a servant running on iiop?
            if (narrowTo.isInterface() &&
                narrowTo != java.io.Serializable.class &&
                narrowTo != java.io.Externalizable.class) {

                org.omg.CORBA.Object narrowObj
                    = (org.omg.CORBA.Object) narrowFrom;

                // Create an id from the narrowTo type...
                String id = RepositoryId.createForAnyType(narrowTo);

                if (narrowObj._is_a(id)) {
                    return Utility.loadStub(narrowObj,narrowTo);
                } else {
                    throw new ClassCastException( "Object is not of remote type " +
                        narrowTo.getName() ) ;
                }
            } else {
                throw new ClassCastException( "Class " + narrowTo.getName() +
                    " is not a valid remote interface" ) ;
            }
        } catch(Exception error) {
            ClassCastException cce = new ClassCastException() ;
            cce.initCause( error ) ;
            throw cce ;
        }
    }

    /**
     * Makes a Remote object ready for remote communication. This normally
     * happens implicitly when the object is sent or received as an argument
     * on a remote method call, but in some circumstances it is useful to
     * perform this action by making an explicit call.  See the
     * {@link Stub#connect} method for more information.
     * @param target the object to connect.
     * @param source a previously connected object.
     * @throws RemoteException if <code>source</code> is not connected
     * or if <code>target</code> is already connected to a different ORB than
     * <code>source</code>.
     */
    public void connect (Remote target, Remote source)
        throws RemoteException
    {
        if (target == null || source == null) {
            throw new NullPointerException("invalid argument");
        }

        ORB orb = null;
        try {
            if (StubAdapter.isStub( source )) {
                orb = StubAdapter.getORB( source ) ;
            } else {
                // Is this a servant that was exported to iiop?
                Tie tie = Util.getTie(source);
                if (tie == null) {
                    /* loadTie always succeeds for dynamic RMI-IIOP
                    // No, can we get a tie for it?  If not,
                    // assume that source is a JRMP object...
                    if (Utility.loadTie(source) != null) {
                        // Yes, so it is an iiop object which
                        // has not been exported...
                        throw new RemoteException(
                            "'source' object not exported");
                    }
                    */
                } else {
                    orb = tie.orb();
                }
            }
        } catch (SystemException e) {
            throw new RemoteException("'source' object not connected", e );
        }

        boolean targetIsIIOP = false ;
        Tie targetTie = null;
        if (StubAdapter.isStub(target)) {
            targetIsIIOP = true;
        } else {
            targetTie = Util.getTie(target);
            if (targetTie != null) {
                targetIsIIOP = true;
            } else {
                /* loadTie always succeeds for dynamic RMI-IIOP
                if (Utility.loadTie(target) != null) {
                    throw new RemoteException("'target' servant not exported");
                }
                */
            }
        }

        if (!targetIsIIOP) {
            // Yes. Do we have an ORB from the source object?
            // If not, we're done - there is nothing to do to
            // connect a JRMP object. If so, it is an error because
            // the caller mixed JRMP and IIOP...
            if (orb != null) {
                throw new RemoteException(
                    "'source' object exported to IIOP, 'target' is JRMP");
            }
        } else {
            // The target object is IIOP. Make sure we have a
            // valid ORB from the source object...
            if (orb == null) {
                throw new RemoteException(
                    "'source' object is JRMP, 'target' is IIOP");
            }

            // And, finally, connect it up...
            try {
                if (targetTie != null) {
                    // Is the tie already connected?
                    try {
                        ORB existingOrb = targetTie.orb();

                        // Yes. Is it the same orb?
                        if (existingOrb == orb) {

                            // Yes, so nothing to do...
                            return;
                        } else {
                            // No, so this is an error...
                            throw new RemoteException(
                                "'target' object was already connected");
                        }
                    } catch (SystemException e) {}

                    // No, so do it...
                    targetTie.orb(orb);
                } else {
                    StubAdapter.connect( target, orb ) ;
                }
            } catch (SystemException e) {

                // The stub or tie was already connected...
                throw new RemoteException(
                    "'target' object was already connected", e );
            }
        }
    }
}
