/*
 * Copyright 2000-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.spi.ior;

import org.omg.CORBA_2_3.portable.OutputStream ;

import com.sun.corba.se.spi.ior.Writeable ;
import com.sun.corba.se.spi.ior.WriteContents ;
import com.sun.corba.se.spi.orb.ORB ;

import com.sun.corba.se.impl.ior.EncapsulationUtility ;

/** Provide support for properly reading and writing Identifiable objects
* that are also encapsulations (tagged profiles and components).
*/
public abstract class IdentifiableBase implements Identifiable,
    WriteContents
{
    /** Write the data for this object as a CDR encapsulation.
    * This is used for writing tagged components and profiles.
    * These data types must be written out as encapsulations,
    * which means that we need to first write the data out to
    * an encapsulation stream, then extract the data and write
    * it to os as an array of octets.
    */
    final public void write( OutputStream os )
    {
        EncapsulationUtility.writeEncapsulation( (WriteContents)this, os ) ;
    }
}
