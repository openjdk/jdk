/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * The Java mapping of the IDL enum <code>TCKind</code>, which
 * specifies the kind of a <code>TypeCode</code> object.  There is
 * one kind for each primitive and essential IDL data type.
 * <P>
 * The class <code>TCKind</code> consists of:
 * <UL>
 * <LI>a set of <code>int</code> constants, one for each
 * kind of IDL data type.  These <code>int</code> constants
 * make it possible to use a <code>switch</code> statement.
 * <LI>a set of <code>TCKind</code> constants, one for each
 * kind of IDL data type.  The <code>value</code> field for
 * each <code>TCKind</code> instance is initialized with
 * the <code>int</code> constant that corresponds with
 * the IDL data type that the instance represents.
 * <LI>the method <code>from_int</code>for converting
 * an <code>int</code> to its
 * corresponding <code>TCKind</code> instance
 * <P>Example:
 * <PRE>
 *      org.omg.CORBA.TCKind k = org.omg.CORBA.TCKind.from_int(
 *                         org.omg.CORBA.TCKind._tk_string);
 * </PRE>
 * The variable <code>k</code> represents the <code>TCKind</code>
 * instance for the IDL type <code>string</code>, which is
 * <code>tk_string</code>.
 *
 * <LI>the method <code>value</code> for accessing the
 * <code>_value</code> field of a <code>TCKind</code> constant
 * <P>Example:
 * <PRE>
 *   int i = org.omg.CORBA.TCKind.tk_char.value();
 * </PRE>
 * The variable <code>i</code> represents 9, the value for the
 * IDL data type <code>char</code>.
 * </UL>
 * <P>The <code>value</code> field of a <code>TCKind</code> instance
 * is the CDR encoding used for a <code>TypeCode</code> object in
 * an IIOP message.
 */

public class TCKind {

    /**
     * The <code>int</code> constant for a <code>null</code> IDL data type.
     */
    public static final int _tk_null = 0;

    /**
     * The <code>int</code> constant for the IDL data type <code>void</code>.
     */
    public static final int _tk_void = 1;

    /**
     * The <code>int</code> constant for the IDL data type <code>short</code>.
     */
    public static final int _tk_short = 2;

    /**
     * The <code>int</code> constant for the IDL data type <code>long</code>.
     */
    public static final int _tk_long = 3;

    /**
     * The <code>int</code> constant for the IDL data type <code>ushort</code>.
     */
    public static final int _tk_ushort = 4;

    /**
     * The <code>int</code> constant for the IDL data type <code>ulong</code>.
     */
    public static final int _tk_ulong = 5;

    /**
     * The <code>int</code> constant for the IDL data type <code>float</code>.
     */
    public static final int _tk_float = 6;

    /**
     * The <code>int</code> constant for the IDL data type <code>double</code>.
     */
    public static final int _tk_double = 7;

    /**
     * The <code>int</code> constant for the IDL data type <code>boolean</code>.
     */
    public static final int _tk_boolean = 8;

    /**
     * The <code>int</code> constant for the IDL data type <code>char</code>.
     */
    public static final int _tk_char = 9;

    /**
     * The <code>int</code> constant for the IDL data type <code>octet</code>.
     */
    public static final int _tk_octet = 10;

    /**
     * The <code>int</code> constant for the IDL data type <code>any</code>.
     */
    public static final int _tk_any = 11;

    /**
     * The <code>int</code> constant for the IDL data type <code>TypeCode</code>.
     */
    public static final int _tk_TypeCode = 12;

    /**
     * The <code>int</code> constant for the IDL data type <code>Principal</code>.
     */
    public static final int _tk_Principal = 13;

    /**
     * The <code>int</code> constant for the IDL data type <code>objref</code>.
     */
    public static final int _tk_objref = 14;

    /**
     * The <code>int</code> constant for the IDL data type <code>struct</code>.
     */
    public static final int _tk_struct = 15;

    /**
     * The <code>int</code> constant for the IDL data type <code>union</code>.
     */
    public static final int _tk_union = 16;

    /**
     * The <code>int</code> constant for the IDL data type <code>enum</code>.
     */
    public static final int _tk_enum = 17;

    /**
     * The <code>int</code> constant for the IDL data type <code>string</code>.
     */
    public static final int _tk_string = 18;

    /**
     * The <code>int</code> constant for the IDL data type <code>sequence</code>.
     */
    public static final int _tk_sequence = 19;

    /**
     * The <code>int</code> constant for the IDL data type <code>array</code>.
     */
    public static final int _tk_array = 20;

    /**
     * The <code>int</code> constant for the IDL data type <code>alias</code>.
     */
    public static final int _tk_alias = 21;

    /**
     * The <code>int</code> constant for the IDL data type <code>except</code>.
     */
    public static final int _tk_except = 22;

    /**
     * The <code>int</code> constant for the IDL data type <code>longlong</code>.
     */
    public static final int _tk_longlong = 23;

    /**
     * The <code>int</code> constant for the IDL data type <code>ulonglong</code>.
     */
    public static final int _tk_ulonglong = 24;

    /**
     * The <code>int</code> constant for the IDL data type <code>longdouble</code>.
     */
    public static final int _tk_longdouble = 25;

    /**
     * The <code>int</code> constant for the IDL data type <code>wchar</code>.
     */
    public static final int _tk_wchar = 26;

    /**
     * The <code>int</code> constant for the IDL data type <code>wstring</code>.
     */
    public static final int _tk_wstring = 27;

    /**
     * The <code>int</code> constant for the IDL data type <code>fixed</code>.
     */
    public static final int _tk_fixed = 28;

    /**
     * The <code>int</code> constant for the IDL data type <code>value</code>.
     */
    public static final int _tk_value = 29;             // orbos 98-01-18: Objects By Value

    /**
     * The <code>int</code> constant for the IDL data type <code>value_box</code>.
     */
    public static final int _tk_value_box = 30; // orbos 98-01-18: Objects By Value

    /**
     * The <code>int</code> constant for the IDL data type <code>native</code>.
     */
    public static final int _tk_native = 31;        // Verify

    /**
     * The <code>int</code> constant for the IDL data type <code>abstract interface</code>.
     */
    public static final int _tk_abstract_interface = 32;


    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_null}.
     */
    public static final TCKind tk_null = new TCKind(_tk_null);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_void}.
     */
    public static final TCKind tk_void = new TCKind(_tk_void);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_short}.
     */
    public static final TCKind tk_short = new TCKind(_tk_short);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_long}.
     */
    public static final TCKind tk_long = new TCKind(_tk_long);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_ushort}.
     */
    public static final TCKind tk_ushort = new TCKind(_tk_ushort);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_ulong}.
     */
    public static final TCKind tk_ulong = new TCKind(_tk_ulong);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_float}.
     */
    public static final TCKind tk_float = new TCKind(_tk_float);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_double}.
     */
    public static final TCKind tk_double = new TCKind(_tk_double);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_boolean}.
     */
    public static final TCKind tk_boolean = new TCKind(_tk_boolean);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_char}.
     */
    public static final TCKind tk_char = new TCKind(_tk_char);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_octet}.
     */
    public static final TCKind tk_octet = new TCKind(_tk_octet);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_any}.
     */
    public static final TCKind tk_any = new TCKind(_tk_any);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_TypeCode}.
     */
    public static final TCKind tk_TypeCode = new TCKind(_tk_TypeCode);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_Principal}.
     */
    public static final TCKind tk_Principal = new TCKind(_tk_Principal);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_objref}.
     */
    public static final TCKind tk_objref = new TCKind(_tk_objref);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_struct}.
     */
    public static final TCKind tk_struct = new TCKind(_tk_struct);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_union}.
     */
    public static final TCKind tk_union = new TCKind(_tk_union);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_enum}.
     */
    public static final TCKind tk_enum = new TCKind(_tk_enum);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_string}.
     */
    public static final TCKind tk_string = new TCKind(_tk_string);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_sequence}.
     */
    public static final TCKind tk_sequence = new TCKind(_tk_sequence);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_array}.
     */
    public static final TCKind tk_array = new TCKind(_tk_array);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_alias}.
     */
    public static final TCKind tk_alias = new TCKind(_tk_alias);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_except}.
     */
    public static final TCKind tk_except = new TCKind(_tk_except);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_longlong}.
     */
    public static final TCKind tk_longlong = new TCKind(_tk_longlong);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_ulonglong}.
     */
    public static final TCKind tk_ulonglong = new TCKind(_tk_ulonglong);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_longdouble}.
     */
    public static final TCKind tk_longdouble = new TCKind(_tk_longdouble);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_wchar}.
     */
    public static final TCKind tk_wchar = new TCKind(_tk_wchar);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_wstring}.
     */
    public static final TCKind tk_wstring = new TCKind(_tk_wstring);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_fixed}.
     */
    public static final TCKind tk_fixed = new TCKind(_tk_fixed);

    // orbos 98-01-18: Objects By Value -- begin

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_value}.
     */
    public static final TCKind tk_value = new TCKind(_tk_value);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_value_box}.
     */
    public static final TCKind tk_value_box = new TCKind(_tk_value_box);
    // orbos 98-01-18: Objects By Value -- end

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_native}.
     */
    public static final TCKind tk_native = new TCKind(_tk_native);

    /**
     * The <code>TCKind</code> constant whose <code>value</code> field is
     * initialized with {@code TCKind._tk_abstract_interface}.
     */
    public static final TCKind tk_abstract_interface = new TCKind(_tk_abstract_interface);




    /**
     * Retrieves the value of this <code>TCKind</code> instance.
     *
     * @return  the <code>int</code> that represents the kind of
     * IDL data type for this <code>TCKind</code> instance
     */
    public int value() {
        return _value;
    }

    /**
     * Converts the given <code>int</code> to the corresponding
     * <code>TCKind</code> instance.
     *
     * @param i the <code>int</code> to convert.  It must be one of
     *         the <code>int</code> constants in the class
     *         <code>TCKind</code>.
     * @return  the <code>TCKind</code> instance whose <code>value</code>
     * field matches the given <code>int</code>
     * @exception  BAD_PARAM  if the given <code>int</code> does not
     * match the <code>_value</code> field of
     * any <code>TCKind</code> instance
     */
    public static TCKind from_int(int i) {
        switch (i) {
        case _tk_null:
            return tk_null;
        case _tk_void:
            return tk_void;
        case _tk_short:
            return tk_short;
        case _tk_long:
            return tk_long;
        case _tk_ushort:
            return tk_ushort;
        case _tk_ulong:
            return tk_ulong;
        case _tk_float:
            return tk_float;
        case _tk_double:
            return tk_double;
        case _tk_boolean:
            return tk_boolean;
        case _tk_char:
            return tk_char;
        case _tk_octet:
            return tk_octet;
        case _tk_any:
            return tk_any;
        case _tk_TypeCode:
            return tk_TypeCode;
        case _tk_Principal:
            return tk_Principal;
        case _tk_objref:
            return tk_objref;
        case _tk_struct:
            return tk_struct;
        case _tk_union:
            return tk_union;
        case _tk_enum:
            return tk_enum;
        case _tk_string:
            return tk_string;
        case _tk_sequence:
            return tk_sequence;
        case _tk_array:
            return tk_array;
        case _tk_alias:
            return tk_alias;
        case _tk_except:
            return tk_except;
        case _tk_longlong:
            return tk_longlong;
        case _tk_ulonglong:
            return tk_ulonglong;
        case _tk_longdouble:
            return tk_longdouble;
        case _tk_wchar:
            return tk_wchar;
        case _tk_wstring:
            return tk_wstring;
        case _tk_fixed:
            return tk_fixed;
        case _tk_value:         // orbos 98-01-18: Objects By Value
            return tk_value;
        case _tk_value_box:     // orbos 98-01-18: Objects By Value
            return tk_value_box;
        case _tk_native:
            return tk_native;
        case _tk_abstract_interface:
            return tk_abstract_interface;
        default:
            throw new org.omg.CORBA.BAD_PARAM();
        }
    }


    /**
    * Creates a new <code>TCKind</code> instance initialized with the given
    * <code>int</code>.
    * @deprecated Do not use this constructor as this method should be private
    * according to the OMG specification. Use {@link #from_int(int)} instead.
    *
    * @param  _value the <code>int</code> to convert.  It must be one of
    *         the <code>int</code> constants in the class
    *         <code>TCKind</code>.
    */
    @Deprecated
    protected TCKind(int _value){
        this._value = _value;
    }
    private int _value;
}
