/*
 * Copyright (c) 1997, 2000, Oracle and/or its affiliates. All rights reserved.
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
/*
 * File: ./org/omg/CORBA/UnionMember.java
 * From: ./ir.idl
 * Date: Fri Aug 28 16:03:31 1998
 *   By: idltojava Java IDL 1.2 Aug 11 1998 02:00:18
 */

package org.omg.CORBA;

/**
 * A description in the Interface Repository of a member of an IDL union.
 */
public final class UnionMember implements org.omg.CORBA.portable.IDLEntity {
    //  instance variables

    /**
     * The name of the union member described by this
     * <code>UnionMember</code> object.
     * @serial
     */
    public String name;

    /**
     * The label of the union member described by this
     * <code>UnionMember</code> object.
     * @serial
     */
    public org.omg.CORBA.Any label;

    /**
     * The type of the union member described by this
     * <code>UnionMember</code> object.
     * @serial
     */
    public org.omg.CORBA.TypeCode type;

    /**
     * The typedef that represents the IDL type of the union member described by this
     * <code>UnionMember</code> object.
     * @serial
     */
    public org.omg.CORBA.IDLType type_def;

    //  constructors

    /**
     * Constructs a new <code>UnionMember</code> object with its fields initialized
     * to null.
     */
    public UnionMember() { }

    /**
     * Constructs a new <code>UnionMember</code> object with its fields initialized
     * to the given values.
     *
     * @param __name a <code>String</code> object with the name of this
     *        <code>UnionMember</code> object
     * @param __label an <code>Any</code> object with the label of this
     *        <code>UnionMember</code> object
     * @param __type a <code>TypeCode</code> object describing the type of this
     *        <code>UnionMember</code> object
     * @param __type_def an <code>IDLType</code> object that represents the
     *        IDL type of this <code>UnionMember</code> object
     */
    public UnionMember(String __name, org.omg.CORBA.Any __label, org.omg.CORBA.TypeCode __type, org.omg.CORBA.IDLType __type_def) {
        name = __name;
        label = __label;
        type = __type;
        type_def = __type_def;
    }
}
