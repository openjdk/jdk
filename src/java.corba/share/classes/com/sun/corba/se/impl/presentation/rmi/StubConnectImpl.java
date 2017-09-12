/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.presentation.rmi ;

import java.rmi.RemoteException;

import javax.rmi.CORBA.Tie;

import org.omg.CORBA.ORB;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.BAD_OPERATION;
import org.omg.CORBA.BAD_INV_ORDER;

import org.omg.CORBA.portable.ObjectImpl;
import org.omg.CORBA.portable.Delegate;

import com.sun.corba.se.spi.presentation.rmi.StubAdapter;

import com.sun.corba.se.spi.logging.CORBALogDomains ;

import com.sun.corba.se.impl.util.Utility;

import com.sun.corba.se.impl.ior.StubIORImpl ;

import com.sun.corba.se.impl.logging.UtilSystemException ;

import com.sun.corba.se.impl.corba.CORBAObjectImpl ;

public abstract class StubConnectImpl
{
    static UtilSystemException wrapper = UtilSystemException.get(
        CORBALogDomains.RMIIIOP ) ;

    /** Connect the stub to the orb if necessary.
    * @param ior The StubIORImpl for this stub (may be null)
    * @param proxy The externally visible stub seen by the user (may be the same as stub)
    * @param stub The stub implementation that extends ObjectImpl
    * @param orb The ORB to which we connect the stub.
    */
    public static StubIORImpl connect( StubIORImpl ior, org.omg.CORBA.Object proxy,
        org.omg.CORBA.portable.ObjectImpl stub, ORB orb ) throws RemoteException
    {
        Delegate del = null ;

        try {
            try {
                del = StubAdapter.getDelegate( stub );

                if (del.orb(stub) != orb)
                    throw wrapper.connectWrongOrb() ;
            } catch (org.omg.CORBA.BAD_OPERATION err) {
                if (ior == null) {
                    // No IOR, can we get a Tie for this stub?
                    Tie tie = (javax.rmi.CORBA.Tie) Utility.getAndForgetTie(proxy);
                    if (tie == null)
                        throw wrapper.connectNoTie() ;

                    // Is the tie already connected?  If it is, check that it's
                    // connected to the same ORB, otherwise connect it.
                    ORB existingOrb = orb ;
                    try {
                        existingOrb = tie.orb();
                    } catch (BAD_OPERATION exc) {
                        // Thrown when tie is an ObjectImpl and its delegate is not set.
                        tie.orb(orb);
                    } catch (BAD_INV_ORDER exc) {
                        // Thrown when tie is a Servant and its delegate is not set.
                        tie.orb(orb);
                    }

                    if (existingOrb != orb)
                        throw wrapper.connectTieWrongOrb() ;

                    // Get the delegate for the stub from the tie.
                    del = StubAdapter.getDelegate( tie ) ;
                    ObjectImpl objref = new CORBAObjectImpl() ;
                    objref._set_delegate( del ) ;
                    ior = new StubIORImpl( objref ) ;
                } else {
                    // ior is initialized, so convert ior to an object, extract
                    // the delegate, and set it on ourself
                    del = ior.getDelegate( orb ) ;
                }

                StubAdapter.setDelegate( stub, del ) ;
            }
        } catch (SystemException exc) {
            throw new RemoteException("CORBA SystemException", exc );
        }

        return ior ;
    }
}
