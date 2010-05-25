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

package com.sun.corba.se.impl.presentation.rmi;

import java.rmi.Remote ;
import javax.rmi.CORBA.Tie ;

import javax.rmi.CORBA.Util;

import org.omg.CORBA.CompletionStatus;

import org.omg.CORBA.portable.IDLEntity ;

import com.sun.corba.se.spi.presentation.rmi.PresentationManager;
import com.sun.corba.se.spi.presentation.rmi.PresentationDefaults;

import com.sun.corba.se.spi.orb.ORB;

import com.sun.corba.se.spi.logging.CORBALogDomains ;

import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

public abstract class StubFactoryFactoryDynamicBase extends
    StubFactoryFactoryBase
{
    protected final ORBUtilSystemException wrapper ;

    public StubFactoryFactoryDynamicBase()
    {
        wrapper = ORBUtilSystemException.get(
            CORBALogDomains.RPC_PRESENTATION ) ;
    }

    public PresentationManager.StubFactory createStubFactory(
        String className, boolean isIDLStub, String remoteCodeBase,
        Class expectedClass, ClassLoader classLoader)
    {
        Class cls = null ;

        try {
            cls = Util.loadClass( className, remoteCodeBase, classLoader ) ;
        } catch (ClassNotFoundException exc) {
            throw wrapper.classNotFound3(
                CompletionStatus.COMPLETED_MAYBE, exc, className ) ;
        }

        PresentationManager pm = ORB.getPresentationManager() ;

        if (IDLEntity.class.isAssignableFrom( cls ) &&
            !Remote.class.isAssignableFrom( cls )) {
            // IDL stubs must always use static factories.
            PresentationManager.StubFactoryFactory sff =
                pm.getStubFactoryFactory( false ) ;
            PresentationManager.StubFactory sf =
                sff.createStubFactory( className, true, remoteCodeBase,
                    expectedClass, classLoader ) ;
            return sf ;
        } else {
            PresentationManager.ClassData classData = pm.getClassData( cls ) ;
            return makeDynamicStubFactory( pm, classData, classLoader ) ;
        }
    }

    public abstract PresentationManager.StubFactory makeDynamicStubFactory(
        PresentationManager pm, PresentationManager.ClassData classData,
        ClassLoader classLoader ) ;

    public Tie getTie( Class cls )
    {
        PresentationManager pm = ORB.getPresentationManager() ;
        return new ReflectiveTie( pm, wrapper ) ;
    }

    public boolean createsDynamicStubs()
    {
        return true ;
    }
}
