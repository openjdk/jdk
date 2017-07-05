/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ObjectInputStream ;
import java.io.ObjectOutputStream ;
import java.io.IOException ;
import java.io.Serializable ;

import java.rmi.RemoteException ;

import javax.rmi.CORBA.Tie ;

import org.omg.CORBA_2_3.portable.ObjectImpl ;

import org.omg.CORBA.portable.Delegate ;
import org.omg.CORBA.portable.OutputStream ;

import org.omg.CORBA.SystemException ;
import org.omg.CORBA.ORB ;

import com.sun.corba.se.spi.orbutil.proxy.InvocationHandlerFactory ;
import com.sun.corba.se.spi.presentation.rmi.PresentationManager ;
import com.sun.corba.se.spi.presentation.rmi.StubAdapter ;
import com.sun.corba.se.spi.presentation.rmi.DynamicStub ;
import com.sun.corba.se.impl.presentation.rmi.StubConnectImpl ;
import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.impl.logging.UtilSystemException ;
import com.sun.corba.se.impl.ior.StubIORImpl ;
import com.sun.corba.se.impl.util.RepositoryId ;
import com.sun.corba.se.impl.util.JDKBridge ;
import com.sun.corba.se.impl.util.Utility ;

// XXX Do we need _get_codebase?
public class DynamicStubImpl extends ObjectImpl
    implements DynamicStub, Serializable
{
    private static final long serialVersionUID = 4852612040012087675L;

    private String[] typeIds ;
    private StubIORImpl ior ;
    private DynamicStub self = null ;  // The actual DynamicProxy for this stub.

    public void setSelf( DynamicStub self )
    {
        // XXX Should probably only allow this once.
        this.self = self ;
    }

    public DynamicStub getSelf()
    {
        return self ;
    }

    public DynamicStubImpl( String[] typeIds )
    {
        this.typeIds = typeIds ;
        ior = null ;
    }

    public void setDelegate( Delegate delegate )
    {
        _set_delegate( delegate ) ;
    }

    public Delegate getDelegate()
    {
        return _get_delegate() ;
    }

    public ORB getORB()
    {
        return (ORB)_orb() ;
    }

    public String[] _ids()
    {
        return typeIds ;
    }

    public String[] getTypeIds()
    {
        return _ids() ;
    }

    public void connect( ORB orb ) throws RemoteException
    {
        ior = StubConnectImpl.connect( ior, self, this, orb ) ;
    }

    public boolean isLocal()
    {
        return _is_local() ;
    }

    public OutputStream request( String operation,
        boolean responseExpected )
    {
        return _request( operation, responseExpected ) ;
    }

    private void readObject( ObjectInputStream stream ) throws
        IOException, ClassNotFoundException
    {
        ior = new StubIORImpl() ;
        ior.doRead( stream ) ;
    }

    private void writeObject( ObjectOutputStream stream ) throws
        IOException
    {
        if (ior == null)
            ior = new StubIORImpl( this ) ;
        ior.doWrite( stream ) ;
    }

    public Object readResolve()
    {
        String repositoryId = ior.getRepositoryId() ;
        String cname = RepositoryId.cache.getId( repositoryId ).getClassName() ;

        Class cls = null ;

        try {
            cls = JDKBridge.loadClass( cname, null, null ) ;
        } catch (ClassNotFoundException exc) {
            // XXX log this
        }

        PresentationManager pm =
            com.sun.corba.se.spi.orb.ORB.getPresentationManager() ;
        PresentationManager.ClassData classData = pm.getClassData( cls ) ;
        InvocationHandlerFactoryImpl ihfactory =
            (InvocationHandlerFactoryImpl)classData.getInvocationHandlerFactory() ;
        return ihfactory.getInvocationHandler( this ) ;
    }
}
