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

package com.sun.corba.se.impl.dynamicany;

import org.omg.CORBA.Any;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.TCKind;
import org.omg.CORBA.portable.OutputStream;
//import org.omg.CORBA.ORBPackage.*;
import org.omg.CORBA.TypeCodePackage.BadKind;
import org.omg.CORBA.TypeCodePackage.Bounds;
import org.omg.CORBA.portable.InputStream;
import org.omg.DynamicAny.*;
import org.omg.DynamicAny.DynAnyPackage.TypeMismatch;
import org.omg.DynamicAny.DynAnyFactoryPackage.InconsistentTypeCode;
import java.math.BigDecimal;
import com.sun.corba.se.impl.corba.AnyImpl;

import com.sun.corba.se.spi.orb.ORB ;
import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

public class DynAnyUtil
{
    static boolean isConsistentType(TypeCode typeCode) {
        int kind = typeCode.kind().value();
        return (kind != TCKind._tk_Principal &&
                kind != TCKind._tk_native &&
                kind != TCKind._tk_abstract_interface);
    }

    static boolean isConstructedDynAny(DynAny dynAny) {
        // DynFixed is constructed but not a subclass of DynAnyConstructedImpl
        //return (dynAny instanceof DynAnyConstructedImpl);
        int kind = dynAny.type().kind().value();
        return (kind == TCKind._tk_sequence ||
                kind == TCKind._tk_struct ||
                kind == TCKind._tk_array ||
                kind == TCKind._tk_union ||
                kind == TCKind._tk_enum ||
                kind == TCKind._tk_fixed ||
                kind == TCKind._tk_value ||
                kind == TCKind._tk_value_box);
    }

    static DynAny createMostDerivedDynAny(Any any, ORB orb, boolean copyValue)
        throws org.omg.DynamicAny.DynAnyFactoryPackage.InconsistentTypeCode
    {
        if (any == null || ! DynAnyUtil.isConsistentType(any.type()))
            throw new org.omg.DynamicAny.DynAnyFactoryPackage.InconsistentTypeCode();

        switch (any.type().kind().value()) {
            case TCKind._tk_sequence:
                return new DynSequenceImpl(orb, any, copyValue);
            case TCKind._tk_struct:
                return new DynStructImpl(orb, any, copyValue);
            case TCKind._tk_array:
                return new DynArrayImpl(orb, any, copyValue);
            case TCKind._tk_union:
                return new DynUnionImpl(orb, any, copyValue);
            case TCKind._tk_enum:
                return new DynEnumImpl(orb, any, copyValue);
            case TCKind._tk_fixed:
                return new DynFixedImpl(orb, any, copyValue);
            case TCKind._tk_value:
                return new DynValueImpl(orb, any, copyValue);
            case TCKind._tk_value_box:
                return new DynValueBoxImpl(orb, any, copyValue);
            default:
                return new DynAnyBasicImpl(orb, any, copyValue);
        }
    }

    static DynAny createMostDerivedDynAny(TypeCode typeCode, ORB orb)
        throws org.omg.DynamicAny.DynAnyFactoryPackage.InconsistentTypeCode
    {
        if (typeCode == null || ! DynAnyUtil.isConsistentType(typeCode))
            throw new org.omg.DynamicAny.DynAnyFactoryPackage.InconsistentTypeCode();

        switch (typeCode.kind().value()) {
            case TCKind._tk_sequence:
                return new DynSequenceImpl(orb, typeCode);
            case TCKind._tk_struct:
                return new DynStructImpl(orb, typeCode);
            case TCKind._tk_array:
                return new DynArrayImpl(orb, typeCode);
            case TCKind._tk_union:
                return new DynUnionImpl(orb, typeCode);
            case TCKind._tk_enum:
                return new DynEnumImpl(orb, typeCode);
            case TCKind._tk_fixed:
                return new DynFixedImpl(orb, typeCode);
            case TCKind._tk_value:
                return new DynValueImpl(orb, typeCode);
            case TCKind._tk_value_box:
                return new DynValueBoxImpl(orb, typeCode);
            default:
                return new DynAnyBasicImpl(orb, typeCode);
        }
    }

    // Extracts a member value according to the given TypeCode from the given complex Any
    // (at the Anys current internal stream position, consuming the anys stream on the way)
    // and returns it wrapped into a new Any
/*
    static Any extractAnyFromAny(TypeCode memberType, Any any, ORB orb) {
        // Moved this functionality into AnyImpl because it is needed for Any.equal()
        return ((AnyImpl)any).extractAny(memberType, orb);
    }
*/

    // Extracts a member value according to the given TypeCode from the given complex Any
    // (at the Anys current internal stream position, consuming the anys stream on the way)
    // and returns it wrapped into a new Any
    static Any extractAnyFromStream(TypeCode memberType, InputStream input, ORB orb) {
        return AnyImpl.extractAnyFromStream(memberType, input, orb);
    }

    // Creates a default Any of the given type.
    static Any createDefaultAnyOfType(TypeCode typeCode, ORB orb) {
        ORBUtilSystemException wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_PRESENTATION ) ;

        Any returnValue = orb.create_any();
        // The spec for DynAny differs from Any on initialization via type code:
        // - false for boolean
        // - zero for numeric types
        // - zero for types octet, char, and wchar
        // - the empty string for string and wstring
        // - nil for object references
        // - a type code with a TCKind value of tk_null for type codes
        // - for Any values, an Any containing a type code with a TCKind value of tk_null
        //   type and no value
        switch (typeCode.kind().value()) {
            case TCKind._tk_boolean:
                // false for boolean
                returnValue.insert_boolean(false);
                break;
            case TCKind._tk_short:
                // zero for numeric types
                returnValue.insert_short((short)0);
                break;
            case TCKind._tk_ushort:
                // zero for numeric types
                returnValue.insert_ushort((short)0);
                break;
            case TCKind._tk_long:
                // zero for numeric types
                returnValue.insert_long(0);
                break;
            case TCKind._tk_ulong:
                // zero for numeric types
                returnValue.insert_ulong(0);
                break;
            case TCKind._tk_longlong:
                // zero for numeric types
                returnValue.insert_longlong((long)0);
                break;
            case TCKind._tk_ulonglong:
                // zero for numeric types
                returnValue.insert_ulonglong((long)0);
                break;
            case TCKind._tk_float:
                // zero for numeric types
                returnValue.insert_float((float)0.0);
                break;
            case TCKind._tk_double:
                // zero for numeric types
                returnValue.insert_double((double)0.0);
                break;
            case TCKind._tk_octet:
                // zero for types octet, char, and wchar
                returnValue.insert_octet((byte)0);
                break;
            case TCKind._tk_char:
                // zero for types octet, char, and wchar
                returnValue.insert_char((char)0);
                break;
            case TCKind._tk_wchar:
                // zero for types octet, char, and wchar
                returnValue.insert_wchar((char)0);
                break;
            case TCKind._tk_string:
                // the empty string for string and wstring
                // Make sure that type code for bounded strings gets respected
                returnValue.type(typeCode);
                // Doesn't erase the type of bounded string
                returnValue.insert_string("");
                break;
            case TCKind._tk_wstring:
                // the empty string for string and wstring
                // Make sure that type code for bounded strings gets respected
                returnValue.type(typeCode);
                // Doesn't erase the type of bounded string
                returnValue.insert_wstring("");
                break;
            case TCKind._tk_objref:
                // nil for object references
                returnValue.insert_Object(null);
                break;
            case TCKind._tk_TypeCode:
                // a type code with a TCKind value of tk_null for type codes
                // We can reuse the type code that's already in the any.
                returnValue.insert_TypeCode(returnValue.type());
                break;
            case TCKind._tk_any:
                // for Any values, an Any containing a type code with a TCKind value
                // of tk_null type and no value.
                // This is exactly what the default AnyImpl constructor provides.
                // _REVISIT_ Note that this inner Any is considered uninitialized.
                returnValue.insert_any(orb.create_any());
                break;
            case TCKind._tk_struct:
            case TCKind._tk_union:
            case TCKind._tk_enum:
            case TCKind._tk_sequence:
            case TCKind._tk_array:
            case TCKind._tk_except:
            case TCKind._tk_value:
            case TCKind._tk_value_box:
                // There are no default value for complex types since there is no
                // concept of a hierarchy of Anys. Only DynAnys can be arrange in
                // a hierarchy to mirror the TypeCode hierarchy.
                // See DynAnyConstructedImpl.initializeComponentsFromTypeCode()
                // on how this DynAny hierarchy is created from TypeCodes.
                returnValue.type(typeCode);
                break;
            case TCKind._tk_fixed:
                returnValue.insert_fixed(new BigDecimal("0.0"), typeCode);
                break;
            case TCKind._tk_native:
            case TCKind._tk_alias:
            case TCKind._tk_void:
            case TCKind._tk_Principal:
            case TCKind._tk_abstract_interface:
                returnValue.type(typeCode);
                break;
            case TCKind._tk_null:
                // Any is already initialized to null
                break;
            case TCKind._tk_longdouble:
                // Unspecified for Java
                throw wrapper.tkLongDoubleNotSupported() ;
            default:
                throw wrapper.typecodeNotSupported() ;
        }
        return returnValue;
    }
/*
    static Any setTypeOfAny(TypeCode typeCode, Any value) {
        if (value != null) {
            value.read_value(value.create_input_stream(), typeCode);
        }
        return value;
    }
*/
    static Any copy(Any inAny, ORB orb) {
        return new AnyImpl(orb, inAny);
    }

/*
    static Any copy(Any inAny, ORB orb) {
        Any outAny = null;
        if (inAny != null && orb != null) {
            outAny = orb.create_any();
            outAny.read_value(inAny.create_input_stream(), inAny.type());
            // isInitialized is set to true
        }
        return outAny;
    }
*/

    static DynAny convertToNative(DynAny dynAny, ORB orb) {
        if (dynAny instanceof DynAnyImpl) {
            return dynAny;
        } else {
            // if copy flag wasn't true we would be using our DynAny with
            // a foreign Any in it.
            try {
                return createMostDerivedDynAny(dynAny.to_any(), orb, true);
            } catch (InconsistentTypeCode ictc) {
                return null;
            }
        }
    }

    static boolean isInitialized(Any any) {
        // Returning simply the value of Any.isInitialized() is not enough.
        // The DynAny spec says that Anys containing null strings do not contain
        // a "legal value" (see ptc 99-10-07, 9.2.3.3)
        boolean isInitialized = ((AnyImpl)any).isInitialized();
        switch (any.type().kind().value()) {
            case TCKind._tk_string:
                return (isInitialized && (any.extract_string() != null));
            case TCKind._tk_wstring:
                return (isInitialized && (any.extract_wstring() != null));
        }
        return isInitialized;
    }

    // This is a convenient method to reset the current component to where it was
    // before we changed it. See DynAnyConstructedImpl.equal for use.
    static boolean set_current_component(DynAny dynAny, DynAny currentComponent) {
        if (currentComponent != null) {
            try {
                dynAny.rewind();
                do {
                    if (dynAny.current_component() == currentComponent)
                        return true;
                } while (dynAny.next());
            } catch (TypeMismatch tm) { /* impossible */ }
        }
        return false;
    }
}
