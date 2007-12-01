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

package com.sun.corba.se.spi.orb ;

import org.omg.CORBA.portable.OutputStream ;

public interface ORBVersion extends Comparable
{
    byte FOREIGN = 0 ;          // ORB from another vendor
    byte OLD = 1 ;              // JDK 1.3.0 or earlier
    byte NEW = 2 ;              // JDK 1.3.1 FCS
    byte JDK1_3_1_01 = 3;       // JDK1_3_1_01 patch
    byte NEWER = 10 ;           // JDK 1.4.x
    byte PEORB = 20 ;           // PEORB in JDK 1.5, S1AS 8, J2EE 1.4

    byte getORBType() ;

    void write( OutputStream os ) ;

    public boolean lessThan( ORBVersion version ) ;
}
