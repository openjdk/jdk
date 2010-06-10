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

import javax.rmi.CORBA.Tie ;

import java.lang.reflect.InvocationHandler ;
import java.lang.reflect.Proxy ;

import com.sun.corba.se.spi.presentation.rmi.PresentationManager ;
import com.sun.corba.se.spi.presentation.rmi.DynamicStub ;
import com.sun.corba.se.spi.presentation.rmi.StubAdapter ;

import com.sun.corba.se.spi.orbutil.proxy.InvocationHandlerFactory ;
import com.sun.corba.se.spi.orbutil.proxy.LinkedInvocationHandler ;

public abstract class StubFactoryBase implements PresentationManager.StubFactory
{
    private String[] typeIds = null ;

    protected final PresentationManager.ClassData classData ;

    protected StubFactoryBase( PresentationManager.ClassData classData )
    {
        this.classData = classData ;
    }

    public synchronized String[] getTypeIds()
    {
        if (typeIds == null) {
            if (classData == null) {
                org.omg.CORBA.Object stub = makeStub() ;
                typeIds = StubAdapter.getTypeIds( stub ) ;
            } else {
                typeIds = classData.getTypeIds() ;
            }
        }

        return typeIds ;
    }
}
