/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.corba.se.spi.orbutil.proxy ;

import java.io.Serializable ;

import java.util.Map ;
import java.util.LinkedHashMap ;

import java.lang.reflect.Proxy ;
import java.lang.reflect.Method ;
import java.lang.reflect.InvocationHandler ;

import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

public class CompositeInvocationHandlerImpl implements
    CompositeInvocationHandler
{
    private Map classToInvocationHandler = new LinkedHashMap() ;
    private InvocationHandler defaultHandler = null ;

    public void addInvocationHandler( Class interf,
        InvocationHandler handler )
    {
        classToInvocationHandler.put( interf, handler ) ;
    }

    public void setDefaultHandler( InvocationHandler handler )
    {
        defaultHandler = handler ;
    }

    public Object invoke( Object proxy, Method method, Object[] args )
        throws Throwable
    {
        // Note that the declaring class in method is the interface
        // in which the method was defined, not the proxy class.
        Class cls = method.getDeclaringClass() ;
        InvocationHandler handler =
            (InvocationHandler)classToInvocationHandler.get( cls ) ;

        if (handler == null) {
            if (defaultHandler != null)
                handler = defaultHandler ;
            else {
                ORBUtilSystemException wrapper = ORBUtilSystemException.get(
                    CORBALogDomains.UTIL ) ;
                throw wrapper.noInvocationHandler( "\"" + method.toString() +
                    "\"" ) ;
            }
        }

        // handler should never be null here.

        return handler.invoke( proxy, method, args ) ;
    }
}
