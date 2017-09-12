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
 * Enables {@code org.omg.CORBA.Any} values to be dynamically
 * interpreted (traversed) and
 * constructed. A {@code DynAny} object is associated with a data value
 * which may correspond to a copy of the value inserted into an {@code Any}.
 * The {@code DynAny} APIs enable traversal of the data value associated with an
 * Any at runtime and extraction of the primitive constituents of the
 * data value.
 * @deprecated Use the new <a href="../DynamicAny/DynAny.html">DynAny</a> instead
 */
@Deprecated
public interface DynAny extends org.omg.CORBA.Object
{
    /**
     * Returns the {@code TypeCode} of the object inserted into
     * this {@code DynAny}.
     *
     * @return the {@code TypeCode} object.
     */
    public org.omg.CORBA.TypeCode type() ;

    /**
     * Copy the contents from one Dynamic Any into another.
     *
     * @param dyn_any the {@code DynAny} object whose contents
     *                are assigned to this {@code DynAny}.
     * @throws org.omg.CORBA.DynAnyPackage.Invalid if the source
     * {@code DynAny} is invalid
     */
    public void assign(org.omg.CORBA.DynAny dyn_any)
        throws org.omg.CORBA.DynAnyPackage.Invalid;

    /**
     * Make a {@code DynAny} object from an {@code Any}
     * object.
     *
     * @param value the {@code Any} object.
     * @throws org.omg.CORBA.DynAnyPackage.Invalid if the source
     * {@code Any} object is empty or bad
     */
    public void from_any(org.omg.CORBA.Any value)
        throws org.omg.CORBA.DynAnyPackage.Invalid;

    /**
     * Convert a {@code DynAny} object to an {@code Any}
     * object.
     *
     * @return the {@code Any} object.
     * @throws org.omg.CORBA.DynAnyPackage.Invalid if this
     * {@code DynAny} is empty or bad.
     *            created or does not contain a meaningful value
     */
    public org.omg.CORBA.Any to_any()
        throws org.omg.CORBA.DynAnyPackage.Invalid;

    /**
     * Destroys this {@code DynAny} object and frees any resources
     * used to represent the data value associated with it. This method
     * also destroys all {@code DynAny} objects obtained from it.
     * <p>
     * Destruction of {@code DynAny} objects should be handled with
     * care, taking into account issues dealing with the representation of
     * data values associated with {@code DynAny} objects.  A programmer
     * who wants to destroy a {@code DynAny} object but still be able
     * to manipulate some component of the data value associated with it,
     * should first create a {@code DynAny} object for the component
     * and then make a copy of the created {@code DynAny} object.
     */
    public void destroy() ;

    /**
     * Clones this {@code DynAny} object.
     *
     * @return a copy of this {@code DynAny} object
     */
    public org.omg.CORBA.DynAny copy() ;

    /**
     * Inserts the given {@code boolean} as the value for this
     * {@code DynAny} object.
     *
     * <p> If this method is called on a constructed {@code DynAny}
     * object, it initializes the next component of the constructed data
     * value associated with this {@code DynAny} object.
     *
     * @param value the {@code boolean} to insert into this
     *              {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.InvalidValue
     *            if the value inserted is not consistent with the type
     *            of the accessed component in this {@code DynAny} object
     */
    public void insert_boolean(boolean value)
        throws org.omg.CORBA.DynAnyPackage.InvalidValue;

    /**
     * Inserts the given {@code byte} as the value for this
     * {@code DynAny} object.
     *
     * <p> If this method is called on a constructed {@code DynAny}
     * object, it initializes the next component of the constructed data
     * value associated with this {@code DynAny} object.
     *
     * @param value the {@code byte} to insert into this
     *              {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.InvalidValue
     *            if the value inserted is not consistent with the type
     *            of the accessed component in this {@code DynAny} object
     */
    public void insert_octet(byte value)
        throws org.omg.CORBA.DynAnyPackage.InvalidValue;

    /**
     * Inserts the given {@code char} as the value for this
     * {@code DynAny} object.
     *
     * <p> If this method is called on a constructed {@code DynAny}
     * object, it initializes the next component of the constructed data
     * value associated with this {@code DynAny} object.
     *
     * @param value the {@code char} to insert into this
     *              {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.InvalidValue
     *            if the value inserted is not consistent with the type
     *            of the accessed component in this {@code DynAny} object
     */
    public void insert_char(char value)
        throws org.omg.CORBA.DynAnyPackage.InvalidValue;

    /**
     * Inserts the given {@code short} as the value for this
     * {@code DynAny} object.
     *
     * <p> If this method is called on a constructed {@code DynAny}
     * object, it initializes the next component of the constructed data
     * value associated with this {@code DynAny} object.
     *
     * @param value the {@code short} to insert into this
     *              {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.InvalidValue
     *            if the value inserted is not consistent with the type
     *            of the accessed component in this {@code DynAny} object
     */
    public void insert_short(short value)
        throws org.omg.CORBA.DynAnyPackage.InvalidValue;

    /**
     * Inserts the given {@code short} as the value for this
     * {@code DynAny} object.
     *
     * <p> If this method is called on a constructed {@code DynAny}
     * object, it initializes the next component of the constructed data
     * value associated with this {@code DynAny} object.
     *
     * @param value the {@code short} to insert into this
     *              {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.InvalidValue
     *            if the value inserted is not consistent with the type
     *            of the accessed component in this {@code DynAny} object
     */
    public void insert_ushort(short value)
        throws org.omg.CORBA.DynAnyPackage.InvalidValue;

    /**
     * Inserts the given {@code int} as the value for this
     * {@code DynAny} object.
     *
     * <p> If this method is called on a constructed {@code DynAny}
     * object, it initializes the next component of the constructed data
     * value associated with this {@code DynAny} object.
     *
     * @param value the {@code int} to insert into this
     *              {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.InvalidValue
     *            if the value inserted is not consistent with the type
     *            of the accessed component in this {@code DynAny} object
     */
    public void insert_long(int value)
        throws org.omg.CORBA.DynAnyPackage.InvalidValue;

    /**
     * Inserts the given {@code int} as the value for this
     * {@code DynAny} object.
     *
     * <p> If this method is called on a constructed {@code DynAny}
     * object, it initializes the next component of the constructed data
     * value associated with this {@code DynAny} object.
     *
     * @param value the {@code int} to insert into this
     *              {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.InvalidValue
     *            if the value inserted is not consistent with the type
     *            of the accessed component in this {@code DynAny} object
     */
    public void insert_ulong(int value)
        throws org.omg.CORBA.DynAnyPackage.InvalidValue;

    /**
     * Inserts the given {@code float} as the value for this
     * {@code DynAny} object.
     *
     * <p> If this method is called on a constructed {@code DynAny}
     * object, it initializes the next component of the constructed data
     * value associated with this {@code DynAny} object.
     *
     * @param value the {@code float} to insert into this
     *              {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.InvalidValue
     *            if the value inserted is not consistent with the type
     *            of the accessed component in this {@code DynAny} object
     */
    public void insert_float(float value)
        throws org.omg.CORBA.DynAnyPackage.InvalidValue;

    /**
     * Inserts the given {@code double} as the value for this
     * {@code DynAny} object.
     *
     * <p> If this method is called on a constructed {@code DynAny}
     * object, it initializes the next component of the constructed data
     * value associated with this {@code DynAny} object.
     *
     * @param value the {@code double} to insert into this
     *              {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.InvalidValue
     *            if the value inserted is not consistent with the type
     *            of the accessed component in this {@code DynAny} object
     */
    public void insert_double(double value)
        throws org.omg.CORBA.DynAnyPackage.InvalidValue;

    /**
     * Inserts the given {@code String} object as the value for this
     * {@code DynAny} object.
     *
     * <p> If this method is called on a constructed {@code DynAny}
     * object, it initializes the next component of the constructed data
     * value associated with this {@code DynAny} object.
     *
     * @param value the {@code String} to insert into this
     *              {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.InvalidValue
     *            if the value inserted is not consistent with the type
     *            of the accessed component in this {@code DynAny} object
     */
    public void insert_string(String value)
        throws org.omg.CORBA.DynAnyPackage.InvalidValue;

    /**
     * Inserts the given {@code org.omg.CORBA.Object} as the value for this
     * {@code DynAny} object.
     *
     * <p> If this method is called on a constructed {@code DynAny}
     * object, it initializes the next component of the constructed data
     * value associated with this {@code DynAny} object.
     *
     * @param value the {@code org.omg.CORBA.Object} to insert into this
     *              {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.InvalidValue
     *            if the value inserted is not consistent with the type
     *            of the accessed component in this {@code DynAny} object
     */
    public void insert_reference(org.omg.CORBA.Object value)
        throws org.omg.CORBA.DynAnyPackage.InvalidValue;

    /**
     * Inserts the given {@code org.omg.CORBA.TypeCode} as the value for this
     * {@code DynAny} object.
     *
     * <p> If this method is called on a constructed {@code DynAny}
     * object, it initializes the next component of the constructed data
     * value associated with this {@code DynAny} object.
     *
     * @param value the {@code org.omg.CORBA.TypeCode} to insert into this
     *              {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.InvalidValue
     *            if the value inserted is not consistent with the type
     *            of the accessed component in this {@code DynAny} object
     */
    public void insert_typecode(org.omg.CORBA.TypeCode value)
        throws org.omg.CORBA.DynAnyPackage.InvalidValue;

    /**
     * Inserts the given {@code long} as the value for this
     * {@code DynAny} object.
     *
     * <p> If this method is called on a constructed {@code DynAny}
     * object, it initializes the next component of the constructed data
     * value associated with this {@code DynAny} object.
     *
     * @param value the {@code long} to insert into this
     *              {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.InvalidValue
     *            if the value inserted is not consistent with the type
     *            of the accessed component in this {@code DynAny} object
     */
    public void insert_longlong(long value)
        throws org.omg.CORBA.DynAnyPackage.InvalidValue;

    /**
     * Inserts the given {@code long} as the value for this
     * {@code DynAny} object.
     *
     * <p> If this method is called on a constructed {@code DynAny}
     * object, it initializes the next component of the constructed data
     * value associated with this {@code DynAny} object.
     *
     * @param value the {@code long} to insert into this
     *              {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.InvalidValue
     *            if the value inserted is not consistent with the type
     *            of the accessed component in this {@code DynAny} object
     */
    public void insert_ulonglong(long value)
        throws org.omg.CORBA.DynAnyPackage.InvalidValue;

    /**
     * Inserts the given {@code char} as the value for this
     * {@code DynAny} object.
     *
     * <p> If this method is called on a constructed {@code DynAny}
     * object, it initializes the next component of the constructed data
     * value associated with this {@code DynAny} object.
     *
     * @param value the {@code char} to insert into this
     *              {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.InvalidValue
     *            if the value inserted is not consistent with the type
     *            of the accessed component in this {@code DynAny} object
     */
    public void insert_wchar(char value)
        throws org.omg.CORBA.DynAnyPackage.InvalidValue;

    /**
     * Inserts the given {@code String} as the value for this
     * {@code DynAny} object.
     *
     * <p> If this method is called on a constructed {@code DynAny}
     * object, it initializes the next component of the constructed data
     * value associated with this {@code DynAny} object.
     *
     * @param value the {@code String} to insert into this
     *              {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.InvalidValue
     *            if the value inserted is not consistent with the type
     *            of the accessed component in this {@code DynAny} object
     */
    public void insert_wstring(String value)
        throws org.omg.CORBA.DynAnyPackage.InvalidValue;

    /**
     * Inserts the given {@code org.omg.CORBA.Any} object as the value for this
     * {@code DynAny} object.
     *
     * <p> If this method is called on a constructed {@code DynAny}
     * object, it initializes the next component of the constructed data
     * value associated with this {@code DynAny} object.
     *
     * @param value the {@code org.omg.CORBA.Any} object to insert into this
     *              {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.InvalidValue
     *            if the value inserted is not consistent with the type
     *            of the accessed component in this {@code DynAny} object
     */
    public void insert_any(org.omg.CORBA.Any value)
        throws org.omg.CORBA.DynAnyPackage.InvalidValue;

    // orbos 98-01-18: Objects By Value -- begin

    /**
     * Inserts the given {@code java.io.Serializable} object as the value for this
     * {@code DynAny} object.
     *
     * <p> If this method is called on a constructed {@code DynAny}
     * object, it initializes the next component of the constructed data
     * value associated with this {@code DynAny} object.
     *
     * @param value the {@code java.io.Serializable} object to insert into this
     *              {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.InvalidValue
     *            if the value inserted is not consistent with the type
     *            of the accessed component in this {@code DynAny} object
     */
    public void insert_val(java.io.Serializable value)
        throws org.omg.CORBA.DynAnyPackage.InvalidValue;

    /**
     * Retrieves the {@code java.io.Serializable} object contained
     * in this {@code DynAny} object.
     *
     * @return the {@code java.io.Serializable} object that is the
     *         value for this {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.TypeMismatch
     *               if the type code of the accessed component in this
     *               {@code DynAny} object is not equivalent to
     *               the type code for a {@code java.io.Serializable} object
     */
    public java.io.Serializable get_val()
        throws org.omg.CORBA.DynAnyPackage.TypeMismatch;

    // orbos 98-01-18: Objects By Value -- end

    /**
     * Retrieves the {@code boolean} contained
     * in this {@code DynAny} object.
     *
     * @return the {@code boolean} that is the
     *         value for this {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.TypeMismatch
     *               if the type code of the accessed component in this
     *               {@code DynAny} object is not equivalent to
     *               the type code for a {@code boolean}
     */
    public boolean get_boolean()
        throws org.omg.CORBA.DynAnyPackage.TypeMismatch;


    /**
     * Retrieves the {@code byte} contained
     * in this {@code DynAny} object.
     *
     * @return the {@code byte} that is the
     *         value for this {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.TypeMismatch
     *               if the type code of the accessed component in this
     *               {@code DynAny} object is not equivalent to
     *               the type code for a {@code byte}
     */
    public byte get_octet()
        throws org.omg.CORBA.DynAnyPackage.TypeMismatch;

    /**
     * Retrieves the {@code char} contained
     * in this {@code DynAny} object.
     *
     * @return the {@code char} that is the
     *         value for this {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.TypeMismatch
     *               if the type code of the accessed component in this
     *               {@code DynAny} object is not equivalent to
     *               the type code for a {@code char}
     */
    public char get_char()
        throws org.omg.CORBA.DynAnyPackage.TypeMismatch;


    /**
     * Retrieves the {@code short} contained
     * in this {@code DynAny} object.
     *
     * @return the {@code short} that is the
     *         value for this {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.TypeMismatch
     *               if the type code of the accessed component in this
     *               {@code DynAny} object is not equivalent to
     *               the type code for a {@code short}
     */
    public short get_short()
        throws org.omg.CORBA.DynAnyPackage.TypeMismatch;


    /**
     * Retrieves the {@code short} contained
     * in this {@code DynAny} object.
     *
     * @return the {@code short} that is the
     *         value for this {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.TypeMismatch
     *               if the type code of the accessed component in this
     *               {@code DynAny} object is not equivalent to
     *               the type code for a {@code short}
     */
    public short get_ushort()
        throws org.omg.CORBA.DynAnyPackage.TypeMismatch;


    /**
     * Retrieves the {@code int} contained
     * in this {@code DynAny} object.
     *
     * @return the {@code int} that is the
     *         value for this {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.TypeMismatch
     *               if the type code of the accessed component in this
     *               {@code DynAny} object is not equivalent to
     *               the type code for a {@code int}
     */
    public int get_long()
        throws org.omg.CORBA.DynAnyPackage.TypeMismatch;


    /**
     * Retrieves the {@code int} contained
     * in this {@code DynAny} object.
     *
     * @return the {@code int} that is the
     *         value for this {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.TypeMismatch
     *               if the type code of the accessed component in this
     *               {@code DynAny} object is not equivalent to
     *               the type code for a {@code int}
     */
    public int get_ulong()
        throws org.omg.CORBA.DynAnyPackage.TypeMismatch;


    /**
     * Retrieves the {@code float} contained
     * in this {@code DynAny} object.
     *
     * @return the {@code float} that is the
     *         value for this {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.TypeMismatch
     *               if the type code of the accessed component in this
     *               {@code DynAny} object is not equivalent to
     *               the type code for a {@code float}
     */
    public float get_float()
        throws org.omg.CORBA.DynAnyPackage.TypeMismatch;


    /**
     * Retrieves the {@code double} contained
     * in this {@code DynAny} object.
     *
     * @return the {@code double} that is the
     *         value for this {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.TypeMismatch
     *               if the type code of the accessed component in this
     *               {@code DynAny} object is not equivalent to
     *               the type code for a {@code double}
     */
    public double get_double()
        throws org.omg.CORBA.DynAnyPackage.TypeMismatch;


    /**
     * Retrieves the {@code String} contained
     * in this {@code DynAny} object.
     *
     * @return the {@code String} that is the
     *         value for this {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.TypeMismatch
     *               if the type code of the accessed component in this
     *               {@code DynAny} object is not equivalent to
     *               the type code for a {@code String}
     */
    public String get_string()
        throws org.omg.CORBA.DynAnyPackage.TypeMismatch;


    /**
     * Retrieves the {@code org.omg.CORBA.Other} contained
     * in this {@code DynAny} object.
     *
     * @return the {@code org.omg.CORBA.Other} that is the
     *         value for this {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.TypeMismatch
     *               if the type code of the accessed component in this
     *               {@code DynAny} object is not equivalent to
     *               the type code for an {@code org.omg.CORBA.Other}
     */
    public org.omg.CORBA.Object get_reference()
        throws org.omg.CORBA.DynAnyPackage.TypeMismatch;


    /**
     * Retrieves the {@code org.omg.CORBA.TypeCode} contained
     * in this {@code DynAny} object.
     *
     * @return the {@code org.omg.CORBA.TypeCode} that is the
     *         value for this {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.TypeMismatch
     *               if the type code of the accessed component in this
     *               {@code DynAny} object is not equivalent to
     *               the type code for a {@code org.omg.CORBA.TypeCode}
     */
    public org.omg.CORBA.TypeCode get_typecode()
        throws org.omg.CORBA.DynAnyPackage.TypeMismatch;


    /**
     * Retrieves the {@code long} contained
     * in this {@code DynAny} object.
     *
     * @return the {@code long} that is the
     *         value for this {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.TypeMismatch
     *               if the type code of the accessed component in this
     *               {@code DynAny} object is not equivalent to
     *               the type code for a {@code long}
     */
    public long get_longlong()
        throws org.omg.CORBA.DynAnyPackage.TypeMismatch;


    /**
     * Retrieves the {@code long} contained
     * in this {@code DynAny} object.
     *
     * @return the {@code long} that is the
     *         value for this {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.TypeMismatch
     *               if the type code of the accessed component in this
     *               {@code DynAny} object is not equivalent to
     *               the type code for a {@code long}
     */
    public long get_ulonglong()
        throws org.omg.CORBA.DynAnyPackage.TypeMismatch;


    /**
     * Retrieves the {@code char} contained
     * in this {@code DynAny} object.
     *
     * @return the {@code char} that is the
     *         value for this {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.TypeMismatch
     *               if the type code of the accessed component in this
     *               {@code DynAny} object is not equivalent to
     *               the type code for a {@code char}
     */
    public char get_wchar()
        throws org.omg.CORBA.DynAnyPackage.TypeMismatch;


    /**
     * Retrieves the {@code String} contained
     * in this {@code DynAny} object.
     *
     * @return the {@code String} that is the
     *         value for this {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.TypeMismatch
     *               if the type code of the accessed component in this
     *               {@code DynAny} object is not equivalent to
     *               the type code for a {@code String}
     */
    public String get_wstring()
        throws org.omg.CORBA.DynAnyPackage.TypeMismatch;


    /**
     * Retrieves the {@code org.omg.CORBA.Any} contained
     * in this {@code DynAny} object.
     *
     * @return the {@code org.omg.CORBA.Any} that is the
     *         value for this {@code DynAny} object
     * @throws org.omg.CORBA.DynAnyPackage.TypeMismatch
     *               if the type code of the accessed component in this
     *               {@code DynAny} object is not equivalent to
     *               the type code for an {@code org.omg.CORBA.Any}
     */
    public org.omg.CORBA.Any get_any()
        throws org.omg.CORBA.DynAnyPackage.TypeMismatch;

    /**
     * Returns a {@code DynAny} object reference that can
     * be used to get/set the value of the component currently accessed.
     * The appropriate {@code insert} method
     * can be called on the resulting {@code DynAny} object
     * to initialize the component.
     * The appropriate {@code get} method
     * can be called on the resulting {@code DynAny} object
     * to extract the value of the component.
         *
         * @return a {@code DynAny} object reference that can be
         *         used to retrieve or set the value of the component currently
         *         accessed
     */
    public org.omg.CORBA.DynAny current_component() ;

    /**
     * Moves to the next component of this {@code DynAny} object.
     * This method is used for iterating through the components of
     * a constructed type, effectively moving a pointer from one
     * component to the next.  The pointer starts out on the first
     * component when a {@code DynAny} object is created.
     *
     * @return {@code true} if the pointer points to a component;
     * {@code false} if there are no more components or this
     * {@code DynAny} is associated with a basic type rather than
     * a constructed type
     */
    public boolean next() ;

    /**
     * Moves the internal pointer to the given index. Logically, this method
     * sets a new offset for this pointer.
     *
     * @param index an {@code int} indicating the position to which
     *              the pointer should move.  The first position is 0.
     * @return {@code true} if the pointer points to a component;
     * {@code false} if there is no component at the designated
     * index.  If this {@code DynAny} object is associated with a
     * basic type, this method returns {@code false} for any index
     * other than 0.
     */
    public boolean seek(int index) ;

    /**
     * Moves the internal pointer to the first component.
     */
    public void rewind() ;
}
