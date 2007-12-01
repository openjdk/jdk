/*
 * Copyright 2001-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.impl.oa.poa ;

abstract class POAPolicyMediatorFactory {
    // create an appropriate policy mediator based on the policies.
    // Note that the policies object has already been validated before
    // this call, so it can only contain valid combinations of POA policies.
    static POAPolicyMediator create( Policies policies, POAImpl poa )
    {
        if (policies.retainServants()) {
            if (policies.useActiveMapOnly())
                return new POAPolicyMediatorImpl_R_AOM( policies, poa ) ;
            else if (policies.useDefaultServant())
                return new POAPolicyMediatorImpl_R_UDS( policies, poa ) ;
            else if (policies.useServantManager())
                return new POAPolicyMediatorImpl_R_USM( policies, poa ) ;
            else
                throw poa.invocationWrapper().pmfCreateRetain() ;
        } else {
            if (policies.useDefaultServant())
                return new POAPolicyMediatorImpl_NR_UDS( policies, poa ) ;
            else if (policies.useServantManager())
                return new POAPolicyMediatorImpl_NR_USM( policies, poa ) ;
            else
                throw poa.invocationWrapper().pmfCreateNonRetain() ;
        }
    }
}
