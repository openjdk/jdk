/*
 * Copyright (c) 1998, 2004, Oracle and/or its affiliates. All rights reserved.
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


package org.omg.CORBA;

/**
 *  Represents a <code>DynAny</code> object that is associated
 *  with an IDL fixed type.
 * @deprecated Use the new <a href="../DynamicAny/DynFixed.html">DynFixed</a> instead
 */
@Deprecated
public interface DynFixed extends org.omg.CORBA.Object, org.omg.CORBA.DynAny
{
    /**
     * Returns the value of the fixed type represented in this
     * <code>DynFixed</code> object.
     *
     * @return the value as a byte array
         * @see #set_value
     */
    public byte[] get_value();

    /**
     * Sets the given fixed type instance as the value for this
     * <code>DynFixed</code> object.
     *
     * @param val the value of the fixed type as a byte array
         * @throws org.omg.CORBA.DynAnyPackage.InvalidValue if the given
         *         argument is bad
         * @see #get_value
     */
    public void set_value(byte[] val)
        throws org.omg.CORBA.DynAnyPackage.InvalidValue;
}
