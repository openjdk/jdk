/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.impl.copyobject ;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.MarshalException;

import org.omg.CORBA_2_3.portable.InputStream;
import org.omg.CORBA_2_3.portable.OutputStream;
import org.omg.CORBA.ORB ;

import com.sun.corba.se.spi.copyobject.ObjectCopier ;
import com.sun.corba.se.impl.util.Utility;

public class ORBStreamObjectCopierImpl implements ObjectCopier {

    public ORBStreamObjectCopierImpl( ORB orb )
    {
        this.orb = orb ;
    }

    public Object copy(Object obj) {
        if (obj instanceof Remote) {
            // Yes, so make sure it is connected and converted
            // to a stub (if needed)...
            return Utility.autoConnect(obj,orb,true);
        }

        OutputStream out = (OutputStream)orb.create_output_stream();
        out.write_value((Serializable)obj);
        InputStream in = (InputStream)out.create_input_stream();
        return in.read_value();
    }

    private ORB orb;
}
