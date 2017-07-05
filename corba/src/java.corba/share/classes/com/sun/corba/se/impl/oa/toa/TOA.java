/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.corba.se.spi.oa.ObjectAdapter ;

/** The Transient Object Adapter is used for standard RMI-IIOP and Java-IDL
 * (legacy JDK 1.2) object implementations.  Its protocol for managing objects is very
 * simple: just connect and disconnect.  There is only a single TOA instance per ORB,
 * and its lifetime is the same as the ORB.  The TOA instance is always ready to receive
 * messages except when the ORB is shutting down.
 */
public interface TOA extends ObjectAdapter {
    /** Connect the given servant to the ORB by allocating a transient object key
     *  and creating an IOR and object reference using the current factory.
     */
    void connect( org.omg.CORBA.Object servant ) ;

    /** Disconnect the object from this ORB.
    */
    void disconnect( org.omg.CORBA.Object obj ) ;
}
