/*
 * Copyright (c) 1998, 2001, Oracle and/or its affiliates. All rights reserved.
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
 * Associates  a name with a value that is an
 * attribute of an IDL struct, and is used in the <tt>DynStruct</tt> APIs.
 */

public final class NameValuePair implements org.omg.CORBA.portable.IDLEntity {

    /**
     * The name to be associated with a value by this <code>NameValuePair</code> object.
     */
    public String id;

    /**
     * The value to be associated with a name by this <code>NameValuePair</code> object.
     */
    public org.omg.CORBA.Any value;

    /**
     * Constructs an empty <code>NameValuePair</code> object.
     * To associate a name with a value after using this constructor, the fields
     * of this object have to be accessed individually.
     */
    public NameValuePair() { }

    /**
     * Constructs a <code>NameValuePair</code> object that associates
     * the given name with the given <code>org.omg.CORBA.Any</code> object.
     * @param __id the name to be associated with the given <code>Any</code> object
     * @param __value the <code>Any</code> object to be associated with the given name
     */
    public NameValuePair(String __id, org.omg.CORBA.Any __value) {
        id = __id;
        value = __value;
    }
}
