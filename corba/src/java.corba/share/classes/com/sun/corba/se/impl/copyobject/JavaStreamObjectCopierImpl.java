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

package com.sun.corba.se.impl.copyobject ;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.MarshalException;

import java.io.InputStream ;
import java.io.OutputStream ;
import java.io.ByteArrayInputStream ;
import java.io.ByteArrayOutputStream ;
import java.io.ObjectInputStream ;
import java.io.ObjectOutputStream ;

import org.omg.CORBA.ORB ;

import com.sun.corba.se.spi.copyobject.ObjectCopier ;
import com.sun.corba.se.impl.util.Utility;

public class JavaStreamObjectCopierImpl implements ObjectCopier {

    public JavaStreamObjectCopierImpl( ORB orb )
    {
        this.orb = orb ;
    }

    public Object copy(Object obj) {
        if (obj instanceof Remote) {
            // Yes, so make sure it is connected and converted
            // to a stub (if needed)...
            return Utility.autoConnect(obj,orb,true);
        }

        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream( 10000 ) ;
            ObjectOutputStream oos = new ObjectOutputStream( os ) ;
            oos.writeObject( obj ) ;

            byte[] arr = os.toByteArray() ;
            InputStream is = new ByteArrayInputStream( arr ) ;
            ObjectInputStream ois = new ObjectInputStream( is ) ;

            return ois.readObject();
        } catch (Exception exc) {
            System.out.println( "Failed with exception:" + exc ) ;
            return null ;
        }
    }

    private ORB orb;
}
