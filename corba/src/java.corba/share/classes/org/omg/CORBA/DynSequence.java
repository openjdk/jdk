/*
 * Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * The representation of a <code>DynAny</code> object that is associated
 * with an IDL sequence.
 * @deprecated Use the new <a href="../DynamicAny/DynSequence.html">DynSequence</a> instead
 */
@Deprecated
public interface DynSequence extends org.omg.CORBA.Object, org.omg.CORBA.DynAny
{

    /**
     * Returns the length of the sequence represented by this
     * <code>DynFixed</code> object.
     *
     * @return the length of the sequence
     */
    public int length();

    /**
     * Sets the length of the sequence represented by this
     * <code>DynFixed</code> object to the given argument.
     *
     * @param arg the length of the sequence
     */
    public void length(int arg);

    /**
     * Returns the value of every element in this sequence.
     *
     * @return an array of <code>Any</code> objects containing the values in
         *         the sequence
         * @see #set_elements
     */
    public org.omg.CORBA.Any[] get_elements();

    /**
     * Sets the values of all elements in this sequence with the given
         * array.
     *
     * @param value the array of <code>Any</code> objects to be set
     * @exception org.omg.CORBA.DynAnyPackage.InvalidSeq if the array
     * of values is bad
         * @see #get_elements
     */
    public void set_elements(org.omg.CORBA.Any[] value)
        throws org.omg.CORBA.DynAnyPackage.InvalidSeq;
}
