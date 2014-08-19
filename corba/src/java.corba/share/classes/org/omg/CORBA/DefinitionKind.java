/*
 * Copyright (c) 1997, 2001, Oracle and/or its affiliates. All rights reserved.
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
 * File: ./org/omg/CORBA/DefinitionKind.java
 * From: ./ir.idl
 * Date: Fri Aug 28 16:03:31 1998
 *   By: idltojava Java IDL 1.2 Aug 11 1998 02:00:18
 */

package org.omg.CORBA;

/**
* The class that provides the constants used to identify the type of an
* Interface Repository object.  This class contains two kinds of constants,
* those that are an <code>int</code> and those that are an instance of the class
* <code>DefinitionKind</code>.  This class provides the method
* <code>from_int</code>, which given one
* of the <code>int</code> constants, creates the corresponding
* <code>DefinitionKind</code> instance.  It also provides the method
* <code>value</code>, which returns the <code>int</code> constant that
* is the value for a <code>DefinitionKind</code> instance.
*
* @see IRObject
*/

public class DefinitionKind implements org.omg.CORBA.portable.IDLEntity {

/**
 * The constant that indicates that an Interface Repository object
 * does not have a definition kind.
 */
        public static final int _dk_none = 0,

/**
 * The constant that indicates that the type of an Interface Repository object
 * may be any type.
 */
        _dk_all = 1,

/**
 * The constant that indicates that an Interface Repository object is an
 * attribute.
 */
        _dk_Attribute = 2,

/**
 * The constant that indicates that an Interface Repository object is a
 * constant.
 */
        _dk_Constant = 3,

/**
 * The constant that indicates that an Interface Repository object is an
 * exception.
 */

        _dk_Exception = 4,

/**
 * The constant that indicates that an Interface Repository object is an
 * interface.
 */

        _dk_Interface = 5,

/**
 * The constant that indicates that an Interface Repository object is a
 * module.
 */

        _dk_Module = 6,

/**
 * The constant that indicates that an Interface Repository object is an
 * operation.
 */

        _dk_Operation = 7,

/**
 * The constant that indicates that an Interface Repository object is a
 * Typedef.
 */

        _dk_Typedef = 8,

/**
 * The constant that indicates that an Interface Repository object is an
 * Alias.
 */

        _dk_Alias = 9,

/**
 * The constant that indicates that an Interface Repository object is a
 * Struct.
 */

        _dk_Struct = 10,

/**
 * The constant that indicates that an Interface Repository object is a
 * Union.
 */

        _dk_Union = 11,

/**
 * The constant that indicates that an Interface Repository object is an
 * Enum.
 */

        _dk_Enum = 12,

/**
 * The constant that indicates that an Interface Repository object is a
 * Primitive.
 */

        _dk_Primitive = 13,

/**
 * The constant that indicates that an Interface Repository object is a
 * String.
 */

        _dk_String = 14,

/**
 * The constant that indicates that an Interface Repository object is a
 * Sequence.
 */

        _dk_Sequence = 15,

/**
 * The constant that indicates that an Interface Repository object is an
 * Array.
 */

        _dk_Array = 16,

/**
 * The constant that indicates that an Interface Repository object is a
 * Repository.
 */

        _dk_Repository = 17,

/**
 * The constant that indicates that an Interface Repository object is a
 * Wstring.
 */

        _dk_Wstring = 18,

/**
 * The constant that indicates that an Interface Repository object is of type
 * Fixed.
 */

        _dk_Fixed = 19,

/**
 * The constant that indicates that an Interface Repository object is a
 * Value.
 */

        _dk_Value = 20,

/**
 * The constant that indicates that an Interface Repository object is a
 * ValueBox.
 */

        _dk_ValueBox = 21,

/**
 * The constant that indicates that an Interface Repository object is a
 * ValueMember.
 */

        _dk_ValueMember = 22,

/**
 * The constant that indicates that an Interface Repository object is of type
 * Native.
 */

        _dk_Native = 23,

/**
 * The constant that indicates that an Interface Repository object
 * is representing an abstract interface.
 */
        _dk_AbstractInterface = 24;

/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object has no definition kind.
 */

    public static final DefinitionKind dk_none = new DefinitionKind(_dk_none);

     /**
         * The wildcard <code>DefinitionKind</code> constant, useful
         * in all occasions where any
     * <code>DefinitionKind</code> is appropriate. The Container's
         * <code>contents</code> method
     * makes use of this constant to return all contained definitions of any kind.
         */

    public static final DefinitionKind dk_all = new DefinitionKind(_dk_all);

/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is an Attribute.
 */

    public static final DefinitionKind dk_Attribute = new DefinitionKind(_dk_Attribute);

/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is a constant.
 */

    public static final DefinitionKind dk_Constant = new DefinitionKind(_dk_Constant);


/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is an Exception.
 */

    public static final DefinitionKind dk_Exception = new DefinitionKind(_dk_Exception);

/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is an Interface.
 */

    public static final DefinitionKind dk_Interface = new DefinitionKind(_dk_Interface);

/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is a Module.
 */

    public static final DefinitionKind dk_Module = new DefinitionKind(_dk_Module);

/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is an Operation.
 */

    public static final DefinitionKind dk_Operation = new DefinitionKind(_dk_Operation);

/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is a Typedef.
 */

    public static final DefinitionKind dk_Typedef = new DefinitionKind(_dk_Typedef);

/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is an Alias.
 */

    public static final DefinitionKind dk_Alias = new DefinitionKind(_dk_Alias);

/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is a Struct.
 */

    public static final DefinitionKind dk_Struct = new DefinitionKind(_dk_Struct);

/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is a Union.
 */

    public static final DefinitionKind dk_Union = new DefinitionKind(_dk_Union);

/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is an Enum.
 */

    public static final DefinitionKind dk_Enum = new DefinitionKind(_dk_Enum);

/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is a Primitive.
 */

    public static final DefinitionKind dk_Primitive = new DefinitionKind(_dk_Primitive);

/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is a String.
 */

    public static final DefinitionKind dk_String = new DefinitionKind(_dk_String);

/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is a Sequence.
 */

    public static final DefinitionKind dk_Sequence = new DefinitionKind(_dk_Sequence);

/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is an Array.
 */

    public static final DefinitionKind dk_Array = new DefinitionKind(_dk_Array);


/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is a Repository.
 */

    public static final DefinitionKind dk_Repository = new DefinitionKind(_dk_Repository);


/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is a Wstring.
 */

    public static final DefinitionKind dk_Wstring = new DefinitionKind(_dk_Wstring);

/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is a Fixed value.
 */

    public static final DefinitionKind dk_Fixed = new DefinitionKind(_dk_Fixed);

/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is a Value.
 */

    public static final DefinitionKind dk_Value = new DefinitionKind(_dk_Value);

/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is a ValueBox.
 */

    public static final DefinitionKind dk_ValueBox = new DefinitionKind(_dk_ValueBox);

/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is a ValueMember.
 */

    public static final DefinitionKind dk_ValueMember = new DefinitionKind(_dk_ValueMember);


/**
 * The static instance of <code>DefinitionKind</code> indicating that an
 * Interface Repository object is a Native value.
 */

    public static final DefinitionKind dk_Native = new DefinitionKind(_dk_Native);


/**
* The static instance of <code>DefinitionKind</code> indicating that an
* Interface Repository object represents an abstract interface.
*/
    public static final DefinitionKind dk_AbstractInterface = new DefinitionKind(_dk_AbstractInterface);


     /**
     * Returns the <code>int</code> constant identifying the type of an IR object.
     * @return the <code>int</code> constant from the class
         * <code>DefinitionKind</code> that is the value of this
         * <code>DefinitionKind</code> instance
     */

    public int value() {
        return _value;
    }


     /**
     * Creates a <code>DefinitionKind</code> instance corresponding to the given code
.
     * @param i one of the <code>int</code> constants from the class
         * <code>DefinitionKind</code>
         * @return the <code>DefinitionKind</code> instance corresponding
         *         to the given code
         * @throws org.omg.CORBA.BAD_PARAM if the given parameter is not
 one
         *         of the <code>int</code> constants from the class
         *         <code>DefinitionKind</code>
     */

    public static DefinitionKind from_int(int i) {
        switch (i) {
        case _dk_none:
            return dk_none;
        case _dk_all:
            return dk_all;
        case _dk_Attribute:
            return dk_Attribute;
        case _dk_Constant:
            return dk_Constant;
        case _dk_Exception:
            return dk_Exception;
        case _dk_Interface:
            return dk_Interface;
        case _dk_Module:
            return dk_Module;
        case _dk_Operation:
            return dk_Operation;
        case _dk_Typedef:
            return dk_Typedef;
        case _dk_Alias:
            return dk_Alias;
        case _dk_Struct:
            return dk_Struct;
        case _dk_Union:
            return dk_Union;
        case _dk_Enum:
            return dk_Enum;
        case _dk_Primitive:
            return dk_Primitive;
        case _dk_String:
            return dk_String;
        case _dk_Sequence:
            return dk_Sequence;
        case _dk_Array:
            return dk_Array;
        case _dk_Repository:
            return dk_Repository;
        case _dk_Wstring:
            return dk_Wstring;
        case _dk_Fixed:
            return dk_Fixed;
        case _dk_Value:
            return dk_Value;
        case _dk_ValueBox:
            return dk_ValueBox;
        case _dk_ValueMember:
            return dk_ValueMember;
        case _dk_Native:
            return dk_Native;
        default:
            throw new org.omg.CORBA.BAD_PARAM();
        }
    }

   /**
    * Constructs a <code>DefinitionKind</code> object with its <code>_value</code>
    * field initialized with the given value.
    * @param _value one of the <code>int</code> constants defined in the
    *                   class <code>DefinitionKind</code>
    */

    protected DefinitionKind(int _value){
        this._value = _value;
    }

     /**
      * The field that holds a value for a <code>DefinitionKind</code> object.
      * @serial
      */

    private int _value;
}
