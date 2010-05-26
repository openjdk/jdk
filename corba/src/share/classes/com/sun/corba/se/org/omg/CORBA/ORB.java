/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.org.omg.CORBA ;

import org.omg.CORBA.ORBPackage.InvalidName ;

/** This ORB class is defined to provide a home for the
* register_initial_reference operation, which is added by
* portable interceptors and CORBA 2.4.  It is added here
* until there is an official Java language mapping for the
* method.
*/
abstract public class ORB extends org.omg.CORBA_2_3.ORB
{
    /**
     * If this operation is called with an id, <code>"Y"</code>, and an
     * object, <code>YY</code>, then a subsequent call to
     * <code>ORB.resolve_initial_references( "Y" )</code> will
     * return object <code>YY</code>.
     *
     * @param id The ID by which the initial reference will be known.
     * @param obj The initial reference itself.
     * @throws InvalidName if this operation is called with an empty string id
     *     or this operation is called with an id that is already registered,
     *     including the default names defined by OMG.
     * @throws BAD_PARAM if the obj parameter is null.
     */
    public void register_initial_reference( String id,
                                            org.omg.CORBA.Object obj )
        throws InvalidName
    {
        throw new org.omg.CORBA.NO_IMPLEMENT();
    }
}
