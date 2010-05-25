/*
 * Copyright (c) 1999, 2003, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.corba.se.spi.oa;

import javax.rmi.CORBA.Tie ;

import org.omg.CORBA.portable.ServantObject;

import org.omg.PortableServer.Servant;

import org.omg.PortableServer.ServantLocatorPackage.CookieHolder;

import com.sun.corba.se.spi.oa.ObjectAdapter ;
import com.sun.corba.se.spi.copyobject.ObjectCopierFactory ;

/** This class is a holder for the information required to implement POACurrent.
* It is also used for the ServantObject that is returned by _servant_preinvoke calls.
* This allows us to avoid allocating an extra object on each collocated invocation.
*/
public class OAInvocationInfo extends ServantObject {
    // This is the container object for the servant.
    // In the RMI-IIOP case, it is the RMI-IIOP Tie, and the servant is the
    // target of the Tie.
    // In all other cases, it is the same as the Servant.
    private java.lang.Object    servantContainer ;

    // These fields are to support standard OMG APIs.
    private ObjectAdapter       oa;
    private byte[]              oid;

    // These fields are to support the Object adapter implementation.
    private CookieHolder        cookieHolder;
    private String              operation;

    // This is the copier to be used by javax.rmi.CORBA.Util.copyObject(s)
    // For the current request.
    private ObjectCopierFactory factory ;

    public OAInvocationInfo(ObjectAdapter oa, byte[] id )
    {
        this.oa = oa;
        this.oid  = id;
    }

    // Copy constructor of sorts; used in local optimization path
    public OAInvocationInfo( OAInvocationInfo info, String operation )
    {
        this.servant            = info.servant ;
        this.servantContainer   = info.servantContainer ;
        this.cookieHolder       = info.cookieHolder ;
        this.oa                 = info.oa;
        this.oid                = info.oid;
        this.factory            = info.factory ;

        this.operation          = operation;
    }

    //getters
    public ObjectAdapter    oa()                    { return oa ; }
    public byte[]           id()                    { return oid ; }
    public Object           getServantContainer()   { return servantContainer ; }

    // Create CookieHolder on demand.  This is only called by a single
    // thread, so no synchronization is needed.
    public CookieHolder     getCookieHolder()
    {
        if (cookieHolder == null)
            cookieHolder = new CookieHolder() ;

        return cookieHolder;
    }

    public String           getOperation()      { return operation; }
    public ObjectCopierFactory  getCopierFactory()      { return factory; }

    //setters
    public void setOperation( String operation )    { this.operation = operation ; }
    public void setCopierFactory( ObjectCopierFactory factory )    { this.factory = factory ; }

    public void setServant(Object servant)
    {
        servantContainer = servant ;
        if (servant instanceof Tie)
            this.servant = ((Tie)servant).getTarget() ;
        else
            this.servant = servant;
    }
}
