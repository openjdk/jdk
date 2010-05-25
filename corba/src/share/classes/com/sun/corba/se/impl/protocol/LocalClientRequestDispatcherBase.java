/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.protocol;

import org.omg.CORBA.portable.ServantObject;

import com.sun.corba.se.spi.protocol.LocalClientRequestDispatcher;
import com.sun.corba.se.spi.protocol.RequestDispatcherRegistry;

import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.ior.IOR ;
import com.sun.corba.se.spi.oa.ObjectAdapterFactory;

import com.sun.corba.se.spi.ior.ObjectAdapterId;
import com.sun.corba.se.spi.ior.TaggedProfile;
import com.sun.corba.se.spi.ior.ObjectKeyTemplate;
import com.sun.corba.se.spi.ior.ObjectId;

public abstract class LocalClientRequestDispatcherBase implements LocalClientRequestDispatcher
{
    protected ORB orb;
    int scid;

    // Cached information needed for local dispatch
    protected boolean servantIsLocal ;
    protected ObjectAdapterFactory oaf ;
    protected ObjectAdapterId oaid ;
    protected byte[] objectId ;

    // If isNextIsLocalValid.get() == Boolean.TRUE,
    // the next call to isLocal should be valid
    protected static ThreadLocal isNextCallValid = new ThreadLocal() {
            protected synchronized Object initialValue() {
                return Boolean.TRUE;
            }
        };

    protected LocalClientRequestDispatcherBase(ORB orb, int scid, IOR ior)
    {
        this.orb = orb ;

        TaggedProfile prof = ior.getProfile() ;
        servantIsLocal = orb.getORBData().isLocalOptimizationAllowed() &&
            prof.isLocal();

        ObjectKeyTemplate oktemp = prof.getObjectKeyTemplate() ;
        this.scid = oktemp.getSubcontractId() ;
        RequestDispatcherRegistry sreg = orb.getRequestDispatcherRegistry() ;
        oaf = sreg.getObjectAdapterFactory( scid ) ;
        oaid = oktemp.getObjectAdapterId() ;
        ObjectId oid = prof.getObjectId() ;
        objectId = oid.getId() ;
    }

    public byte[] getObjectId()
    {
        return objectId ;
    }

    public boolean is_local(org.omg.CORBA.Object self)
    {
        return false;
    }

    /*
    * Possible paths through
    * useLocalInvocation/servant_preinvoke/servant_postinvoke:
    *
    * A: call useLocalInvocation
    * If useLocalInvocation returns false, servant_preinvoke is not called.
    * If useLocalInvocation returns true,
    * call servant_preinvoke
    *   If servant_preinvoke returns null,
    *       goto A
    *   else
    *       (local invocation proceeds normally)
    *       servant_postinvoke is called
    *
    */
    public boolean useLocalInvocation( org.omg.CORBA.Object self )
    {
        if (isNextCallValid.get() == Boolean.TRUE)
            return servantIsLocal ;
        else
            isNextCallValid.set( Boolean.TRUE ) ;

        return false ;
    }

    /** Check that the servant in info (which must not be null) is
    * an instance of the expectedType.  If not, set the thread local flag
    * and return false.
    */
    protected boolean checkForCompatibleServant( ServantObject so,
        Class expectedType )
    {
        if (so == null)
            return false ;

        // Normally, this test will never fail.  However, if the servant
        // and the stub were loaded in different class loaders, this test
        // will fail.
        if (!expectedType.isInstance( so.servant )) {
            isNextCallValid.set( Boolean.FALSE ) ;

            // When servant_preinvoke returns null, the stub will
            // recursively re-invoke itself.  Thus, the next call made from
            // the stub is another useLocalInvocation call.
            return false ;
        }

        return true ;
    }

}

// End of file.
