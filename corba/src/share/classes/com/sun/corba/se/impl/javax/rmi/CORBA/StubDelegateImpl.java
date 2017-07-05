/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.javax.rmi.CORBA;

import java.io.IOException;

import java.rmi.RemoteException;

import javax.rmi.CORBA.Tie;

import org.omg.CORBA.ORB;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.BAD_OPERATION;
import org.omg.CORBA.BAD_INV_ORDER;

import org.omg.CORBA.portable.Delegate;
import org.omg.CORBA.portable.OutputStream;
import org.omg.CORBA.portable.InputStream;

import com.sun.corba.se.spi.presentation.rmi.StubAdapter;

import com.sun.corba.se.spi.logging.CORBALogDomains ;

import com.sun.corba.se.impl.util.Utility;

import com.sun.corba.se.impl.ior.StubIORImpl ;
import com.sun.corba.se.impl.presentation.rmi.StubConnectImpl ;

import com.sun.corba.se.impl.logging.UtilSystemException ;

/**
 * Base class from which all static RMI-IIOP stubs must inherit.
 */
public class StubDelegateImpl implements javax.rmi.CORBA.StubDelegate
{
    static UtilSystemException wrapper = UtilSystemException.get(
        CORBALogDomains.RMIIIOP ) ;

    private StubIORImpl ior ;

    public StubIORImpl getIOR()
    {
        return ior ;
    }

    public StubDelegateImpl()
    {
        ior = null ;
    }

    /**
     * Sets the IOR components if not already set.
     */
    private void init (javax.rmi.CORBA.Stub self)
    {
        // If the Stub is not connected to an ORB, BAD_OPERATION exception
        // will be raised by the code below.
        if (ior == null)
            ior = new StubIORImpl( self ) ;
    }

    /**
     * Returns a hash code value for the object which is the same for all stubs
     * that represent the same remote object.
     * @return the hash code value.
     */
    public int hashCode(javax.rmi.CORBA.Stub self)
    {
        init(self);
        return ior.hashCode() ;
    }

    /**
     * Compares two stubs for equality. Returns <code>true</code> when used to compare stubs
     * that represent the same remote object, and <code>false</code> otherwise.
     * @param obj the reference object with which to compare.
     * @return <code>true</code> if this object is the same as the <code>obj</code>
     *          argument; <code>false</code> otherwise.
     */
    public boolean equals(javax.rmi.CORBA.Stub self, java.lang.Object obj)
    {
        if (self == obj) {
            return true;
        }

        if (!(obj instanceof javax.rmi.CORBA.Stub)) {
            return false;
        }

        // no need to call init() because of calls to hashCode() below

        javax.rmi.CORBA.Stub other = (javax.rmi.CORBA.Stub) obj;
        if (other.hashCode() != self.hashCode()) {
            return false;
        }

        // hashCodes being the same does not mean equality. The stubs still
        // could be pointing to different IORs. So, do a literal comparison.
        // Apparently the ONLY way to do this (other than using private
        // reflection)  toString, because it is not possible to directly
        // access the StubDelegateImpl from the Stub.
        return self.toString().equals( other.toString() ) ;
    }

    public boolean equals( Object obj )
    {
        if (this == obj)
            return true ;

        if (!(obj instanceof StubDelegateImpl))
            return false ;

        StubDelegateImpl other = (StubDelegateImpl)obj ;

        if (ior == null)
            return ior == other.ior ;
        else
            return ior.equals( other.ior ) ;
    }

    public int hashCode() {
        if (ior == null) {
            return 0;
        } else {
            return ior.hashCode();
        }
    }

    /**
     * Returns a string representation of this stub. Returns the same string
     * for all stubs that represent the same remote object.
     * @return a string representation of this stub.
     */
    public String toString(javax.rmi.CORBA.Stub self)
    {
        if (ior == null)
            return null ;
        else
            return ior.toString() ;
    }

    /**
     * Connects this stub to an ORB. Required after the stub is deserialized
     * but not after it is demarshalled by an ORB stream. If an unconnected
     * stub is passed to an ORB stream for marshalling, it is implicitly
     * connected to that ORB. Application code should not call this method
     * directly, but should call the portable wrapper method
     * {@link javax.rmi.PortableRemoteObject#connect}.
     * @param orb the ORB to connect to.
     * @exception RemoteException if the stub is already connected to a different
     * ORB, or if the stub does not represent an exported remote or local object.
     */
    public void connect(javax.rmi.CORBA.Stub self, ORB orb)
        throws RemoteException
    {
        ior = StubConnectImpl.connect( ior, self, self, orb ) ;
    }

    /**
     * Serialization method to restore the IOR state.
     */
    public void readObject(javax.rmi.CORBA.Stub self,
        java.io.ObjectInputStream stream) throws IOException, ClassNotFoundException
    {
        if (ior == null)
            ior = new StubIORImpl() ;

        ior.doRead( stream ) ;
    }

    /**
     * Serialization method to save the IOR state.
     * @serialData The length of the IOR type ID (int), followed by the IOR type ID
     * (byte array encoded using ISO8859-1), followed by the number of IOR profiles
     * (int), followed by the IOR profiles.  Each IOR profile is written as a
     * profile tag (int), followed by the length of the profile data (int), followed
     * by the profile data (byte array).
     */
    public void writeObject(javax.rmi.CORBA.Stub self,
        java.io.ObjectOutputStream stream) throws IOException
    {
        init(self);
        ior.doWrite( stream ) ;
    }
}
