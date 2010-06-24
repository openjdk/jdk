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

import javax.rmi.CORBA.Tie;

import org.omg.CORBA.portable.ServantObject;

import com.sun.corba.se.spi.orb.ORB ;

import com.sun.corba.se.spi.protocol.LocalClientRequestDispatcherFactory ;
import com.sun.corba.se.spi.protocol.LocalClientRequestDispatcher ;

import com.sun.corba.se.spi.ior.IOR ;

import com.sun.corba.se.impl.protocol.LocalClientRequestDispatcherBase ;

import com.sun.corba.se.pept.broker.Broker;

public class JIDLLocalCRDImpl extends LocalClientRequestDispatcherBase
{
    public JIDLLocalCRDImpl( ORB orb, int scid, IOR ior )
    {
        super( (com.sun.corba.se.spi.orb.ORB)orb, scid, ior ) ;
    }

    protected ServantObject servant;

    public ServantObject servant_preinvoke(org.omg.CORBA.Object self,
                                           String operation,
                                           Class expectedType)
    {
        if (!checkForCompatibleServant( servant, expectedType ))
            return null ;

        return servant;
    }

    public void servant_postinvoke( org.omg.CORBA.Object self,
        ServantObject servant )
    {
        // NO-OP
    }

    // REVISIT - This is called from TOAImpl.
    public void setServant( java.lang.Object servant )
    {
        if (servant != null && servant instanceof Tie) {
            this.servant = new ServantObject();
            this.servant.servant = ((Tie)servant).getTarget();
        } else {
            this.servant = null;
        }
    }

    public void unexport() {
        // DO NOT set the IOR to null.  (Un)exporting is only concerns
        // the servant not the IOR.  If the ior is set to null then
        // null pointer exceptions happen during an colocated invocation.
        // It is better to let the invocation proceed and get OBJECT_NOT_EXIST
        // from the server side.
        //ior = null;
        servant = null;
    }
}

// End of file.
