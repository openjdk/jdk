/*
 * Copyright (c) 2002, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.oa.toa ;

import java.util.Map ;
import java.util.HashMap ;

import org.omg.CORBA.INTERNAL ;
import org.omg.CORBA.CompletionStatus ;

import com.sun.corba.se.spi.oa.ObjectAdapterFactory ;
import com.sun.corba.se.spi.oa.ObjectAdapter ;

import com.sun.corba.se.spi.orb.ORB ;

import com.sun.corba.se.spi.ior.ObjectAdapterId ;

import com.sun.corba.se.impl.oa.toa.TOAImpl ;
import com.sun.corba.se.impl.oa.toa.TransientObjectManager ;

import com.sun.corba.se.impl.javax.rmi.CORBA.Util ;

import com.sun.corba.se.impl.ior.ObjectKeyTemplateBase ;

import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

public class TOAFactory implements ObjectAdapterFactory
{
    private ORB orb ;
    private ORBUtilSystemException wrapper ;

    private TOAImpl toa ;
    private Map codebaseToTOA ;
    private TransientObjectManager tom ;

    public ObjectAdapter find ( ObjectAdapterId oaid )
    {
        if (oaid.equals( ObjectKeyTemplateBase.JIDL_OAID )  )
            // Return the dispatch-only TOA, which can dispatch
            // request for objects created by any TOA.
            return getTOA() ;
        else
            throw wrapper.badToaOaid() ;
    }

    public void init( ORB orb )
    {
        this.orb = orb ;
        wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.OA_LIFECYCLE ) ;
        tom = new TransientObjectManager( orb ) ;
        codebaseToTOA = new HashMap() ;
    }

    public void shutdown( boolean waitForCompletion )
    {
        if (Util.isInstanceDefined()) {
            Util.getInstance().unregisterTargetsForORB(orb);
        }
    }

    public synchronized TOA getTOA( String codebase )
    {
        TOA toa = (TOA)(codebaseToTOA.get( codebase )) ;
        if (toa == null) {
            toa = new TOAImpl( orb, tom, codebase ) ;

            codebaseToTOA.put( codebase, toa ) ;
        }

        return toa ;
    }

    public synchronized TOA getTOA()
    {
        if (toa == null)
            // The dispatch-only TOA is not used for creating
            // objrefs, so its codebase can be null (and must
            // be, since we do not have a servant at this point)
            toa = new TOAImpl( orb, tom, null ) ;

        return toa ;
    }

    public ORB getORB()
    {
        return orb ;
    }
} ;
