/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.impl.verifier;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import jdk.internal.classfile.impl.ClassHierarchyImpl;
import jdk.internal.classfile.impl.Util;

import static jdk.internal.classfile.impl.verifier.VerifierImpl.*;

/// From `verificationType.cpp`.
class VerificationType {

    private static final int BitsPerByte = 8;

    static final int
            ITEM_Top = 0,
            ITEM_Integer = 1,
            ITEM_Float = 2,
            ITEM_Double = 3,
            ITEM_Long = 4,
            ITEM_Null = 5,
            ITEM_UninitializedThis = 6,
            ITEM_Object = 7,
            ITEM_Uninitialized = 8,
            ITEM_Bogus = -1;

    VerificationType(String sym) {
        _data = 0x100;
        _sym = sym;
    }
    public VerificationType(int data, String sym) {
        _data = data;
        _sym = sym;
    }
    private final int _data;
    private final String _sym;

    @Override
    public int hashCode() {
        return _sym == null ? _data : _sym.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof VerificationType ? (_data == ((VerificationType)obj)._data) && Objects.equals(_sym, ((VerificationType)obj)._sym) : false;
    }

    private static final Map<VerificationType, String> _constantsMap = new IdentityHashMap<>(18);

    @Override
    public String toString() {
        if (_constantsMap.isEmpty()) {
            for (Field f : VerificationType.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.getType() == VerificationType.class) try {
                    _constantsMap.put((VerificationType)f.get(null), f.getName());
                } catch (IllegalAccessException ignore) {}
            }
        }
        if (_sym != null) return _sym;
        if ((_data & 0xff) == Uninitialized) return "uninit@" + (_data >> 8);
        return _constantsMap.getOrDefault(this, java.lang.Integer.toHexString(_data));
    }

    String name() {
        return _sym;
    }
    private static final int
            ITEM_Boolean = 9, ITEM_Byte = 10, ITEM_Short = 11, ITEM_Char = 12,
            ITEM_Long_2nd = 13, ITEM_Double_2nd = 14;

    private static final int
                            TypeMask                     = 0x00000003,
                            // Topmost types encoding
                            Reference                    = 0x0,                // _sym contains the name
                            Primitive                    = 0x1,                // see below for primitive list
                            Uninitialized            = 0x2,                // 0x00ffff00 contains bci
                            TypeQuery                    = 0x3,                // Meta-types used for category testing
                            // Utility flags
                            ReferenceFlag            = 0x00,             // For reference query types
                            Category1Flag            = 0x01,             // One-word values
                            Category2Flag            = 0x02,             // First word of a two-word value
                            Category2_2ndFlag    = 0x04,             // Second word of a two-word value
                            // special reference values
                            Null                             = 0x00000000, // A reference with a 0 sym is null
                            // Primitives categories (the second byte determines the category)
                            Category1                    = (Category1Flag         << BitsPerByte) | Primitive,
                            Category2                    = (Category2Flag         << BitsPerByte) | Primitive,
                            Category2_2nd            = (Category2_2ndFlag << BitsPerByte) | Primitive,
                            // Primitive values (type discriminator stored in most-significant bytes)
                            // Bogus needs the " | Primitive".    Else, isReference(Bogus) returns TRUE.
                            Bogus                            = (ITEM_Bogus            << 2 * BitsPerByte) | Primitive,
                            Boolean                        = (ITEM_Boolean        << 2 * BitsPerByte) | Category1,
                            Byte                             = (ITEM_Byte             << 2 * BitsPerByte) | Category1,
                            Short                            = (ITEM_Short            << 2 * BitsPerByte) | Category1,
                            Char                             = (ITEM_Char             << 2 * BitsPerByte) | Category1,
                            Integer                        = (ITEM_Integer        << 2 * BitsPerByte) | Category1,
                            Float                            = (ITEM_Float            << 2 * BitsPerByte) | Category1,
                            Long                             = (ITEM_Long             << 2 * BitsPerByte) | Category2,
                            Double                         = (ITEM_Double         << 2 * BitsPerByte) | Category2,
                            Long_2nd                     = (ITEM_Long_2nd     << 2 * BitsPerByte) | Category2_2nd,
                            Double_2nd                 = (ITEM_Double_2nd << 2 * BitsPerByte) | Category2_2nd,
                            // Used by Uninitialized (second and third bytes hold the bci)
                            BciMask                        = 0xffff << BitsPerByte,
                            // A bci of -1 is an Uninitialized-This
                            BciForThis = 0xffff,
                            // Query values
                            ReferenceQuery         = (ReferenceFlag         << BitsPerByte) | TypeQuery,
                            Category1Query         = (Category1Flag         << BitsPerByte) | TypeQuery,
                            Category2Query         = (Category2Flag         << BitsPerByte) | TypeQuery,
                            Category2_2ndQuery = (Category2_2ndFlag << BitsPerByte) | TypeQuery;

    VerificationType(int raw_data) {
        this._data = raw_data;
        this._sym = null;
    }

    static final VerificationType bogus_type = new VerificationType(Bogus),
            null_type = new VerificationType(Null),
            integer_type = new VerificationType(Integer),
            float_type = new VerificationType(Float),
            long_type = new VerificationType(Long),
            long2_type = new VerificationType(Long_2nd),
            double_type = new VerificationType(Double),
            boolean_type = new VerificationType(Boolean),
            byte_type = new VerificationType(Byte),
            char_type = new VerificationType(Char),
            short_type = new VerificationType(Short),
            double2_type = new VerificationType(Double_2nd),
            // "check" types are used for queries.    A "check" type is not assignable
            // to anything, but the specified types are assignable to a "check".    For
            // example, any category1 primitive is assignable to category1_check and
            // any reference is assignable to reference_check.
            reference_check = new VerificationType(ReferenceQuery),
            category1_check = new VerificationType(Category1Query),
            category2_check = new VerificationType(Category2Query);

    static VerificationType reference_type(String sh) {
        return new VerificationType(sh);
    }

    static VerificationType uninitialized_type(int bci) {
        return new VerificationType(bci << 1 * BitsPerByte | Uninitialized);
    }

    static final VerificationType uninitialized_this_type = uninitialized_type(BciForThis);

    boolean is_bogus() {
        return (_data == Bogus);
    }

    boolean is_null() {
        return (_data == Null);
    }

    boolean is_integer() {
        return (_data == Integer);
    }

    boolean is_long() {
        return (_data == Long);
    }

    boolean is_double() {
        return (_data == Double);
    }

    boolean is_long2() {
        return (_data == Long_2nd );
    }

    boolean is_double2() {
        return (_data == Double_2nd);
    }

    boolean is_reference() {
        return ((_data & TypeMask) == Reference);
    }

    boolean is_category1(VerifierImpl context) {
        // This should return true for all one-word types, which are category1
        // primitives, and references (including uninitialized refs).    Though
        // the 'query' types should technically return 'false' here, if we
        // allow this to return true, we can perform the test using only
        // 2 operations rather than 8 (3 masks, 3 compares and 2 logical 'ands').
        // Since no one should call this on a query type anyway, this is ok.
        if(is_check()) context.verifyError("Must not be a check type (wrong value returned)");
        // should only return false if it's a primitive, and the category1 flag
        // is not set.
        return ((_data & Category1) != Primitive);
    }

    boolean is_category2() {
        return ((_data & Category2) == Category2);
    }

    boolean is_category2_2nd() {
        return ((_data & Category2_2nd) == Category2_2nd);
    }

    boolean is_check() {
        return (_data & TypeQuery) == TypeQuery;
    }

    boolean is_x_array(char sig) {
        return is_null() || (is_array() &&(name().charAt(1) == sig));
    }

    boolean is_int_array() {
        return is_x_array(JVM_SIGNATURE_INT);
    }

    boolean is_byte_array() {
        return is_x_array(JVM_SIGNATURE_BYTE);
    }

    boolean is_bool_array() {
        return is_x_array(JVM_SIGNATURE_BOOLEAN);
    }

    boolean is_char_array() {
        return is_x_array(JVM_SIGNATURE_CHAR);
    }

    boolean is_short_array() {
        return is_x_array(JVM_SIGNATURE_SHORT);
    }

    boolean is_long_array() {
        return is_x_array(JVM_SIGNATURE_LONG);
    }

    boolean is_float_array() {
        return is_x_array(JVM_SIGNATURE_FLOAT);
    }

    boolean is_double_array() {
        return is_x_array(JVM_SIGNATURE_DOUBLE);
    }

    boolean is_object_array() {
        return is_x_array(JVM_SIGNATURE_CLASS);
    }

    boolean is_array_array() {
        return is_x_array(JVM_SIGNATURE_ARRAY);
    }

    boolean is_reference_array() {
        return is_object_array() || is_array_array();
    }

    boolean is_object() {
        return (is_reference() && !is_null() && name().length() >= 1 && name().charAt(0) != JVM_SIGNATURE_ARRAY);
    }

    boolean is_array() {
        return (is_reference() && !is_null() && name().length() >= 2 && name().charAt(0) == JVM_SIGNATURE_ARRAY);
    }

    boolean is_uninitialized() {
        return ((_data & Uninitialized) == Uninitialized);
    }

    boolean is_uninitialized_this(VerifierImpl context) {
        return is_uninitialized() && bci(context) == BciForThis;
    }

    VerificationType to_category2_2nd(VerifierImpl context) {
        if (!(is_category2())) context.verifyError("Must be a double word");
        return is_long() ? long2_type : double2_type;
    }

    int bci(VerifierImpl context) {
        if (!(is_uninitialized())) context.verifyError("Must be uninitialized type");
        return ((_data & BciMask) >> 1 * BitsPerByte);
    }

    boolean is_assignable_from(VerificationType from, VerifierImpl context) {
        boolean ret = _is_assignable_from(from, context);
        context.errorContext = ret ? "" : String.format("(%s is not assignable from %s)", this, from);
        return ret;
    }

    private boolean _is_assignable_from(VerificationType from, VerifierImpl context) {
        if (equals(from) || is_bogus()) {
            return true;
        } else {
            switch(_data) {
                case Category1Query:
                    return from.is_category1(context);
                case Category2Query:
                    return from.is_category2();
                case Category2_2ndQuery:
                    return from.is_category2_2nd();
                case ReferenceQuery:
                    return from.is_reference() || from.is_uninitialized();
                case Boolean:
                case Byte:
                case Char:
                case Short:
                    return from.is_integer();
                default:
                    if (is_reference() && from.is_reference()) {
                        return is_reference_assignable_from(from, context, null);
                    } else {
                        return false;
                    }
            }
        }
    }

    // Check to see if one array component type is assignable to another.
    // Same as is_assignable_from() except int primitives must be identical.
    boolean is_component_assignable_from(VerificationType from, VerifierImpl context) {
        if (equals(from) || is_bogus()) {
            return true;
        } else {
            switch (_data) {
                case Boolean:
                case Byte:
                case Char:
                case Short:
                    return false;
                default:
                    return is_assignable_from(from, context);
            }
        }
    }

    int dimensions(VerifierImpl context) {
        if (!(is_array())) context.verifyError("Must be an array");
        int index = 0;
        while (name().charAt(index) == JVM_SIGNATURE_ARRAY) index++;
        return index;
    }

    static VerificationType from_tag(int tag, VerifierImpl context) {
        switch (tag) {
            case ITEM_Top:         return bogus_type;
            case ITEM_Integer: return integer_type;
            case ITEM_Float:     return float_type;
            case ITEM_Double:    return double_type;
            case ITEM_Long:        return long_type;
            case ITEM_Null:        return null_type;
            default:
                context.verifyError("Should not reach here");
                return bogus_type;
        }
    }

    boolean resolve_and_check_assignability(ClassHierarchyImpl assignResolver, String target_name, String from_name,
                                            boolean from_is_array, boolean from_is_object, boolean[] target_is_interface) {
        //let's delegate assignability to SPI
        var targetClass = Util.toClassDesc(target_name);
        boolean isInterface = assignResolver.isInterface(targetClass);

        if (target_is_interface != null) {
            target_is_interface[0] = isInterface;
        }

        if (isInterface) {
            return !from_is_array || "java/lang/Cloneable".equals(target_name) || "java/io/Serializable".equals(target_name);
        } else if (from_is_object) {
            return assignResolver.isAssignableFrom(targetClass, Util.toClassDesc(from_name));
        }
        return false;
    }

    boolean is_reference_assignable_from(VerificationType from, VerifierImpl context, boolean[] target_is_interface) {
        ClassHierarchyImpl clsTree = context.class_hierarchy();
        if (from.is_null()) {
            return true;
        } else if (is_null()) {
            return false;
        } else if (name().equals(from.name())) {
            return true;
        } else if (is_object()) {
            if (VerifierImpl.java_lang_Object.equals(name())) {
                return true;
            }
            return resolve_and_check_assignability(clsTree, name(), from.name(), from.is_array(), from.is_object(), target_is_interface);
        } else if (is_array() && from.is_array()) {
            VerificationType comp_this = get_component(context);
            VerificationType comp_from = from.get_component(context);
            if (!comp_this.is_bogus() && !comp_from.is_bogus()) {
                return comp_this.is_component_assignable_from(comp_from, context);
            }
        }
        return false;
    }

    VerificationType get_component(VerifierImpl context) {
        if (!(is_array() && name().length() >= 2)) context.verifyError("Must be a valid array");
        var ss = new VerificationSignature(name(), false, context);
        ss.skipArrayPrefix(1);
        switch (ss.type()) {
            case T_BOOLEAN: return VerificationType.boolean_type;
            case T_BYTE:        return VerificationType.byte_type;
            case T_CHAR:        return VerificationType.char_type;
            case T_SHORT:     return VerificationType.short_type;
            case T_INT:         return VerificationType.integer_type;
            case T_LONG:        return VerificationType.long_type;
            case T_FLOAT:     return VerificationType.float_type;
            case T_DOUBLE:    return VerificationType.double_type;
            case T_ARRAY:
            case T_OBJECT: {
                if (!(ss.isReference())) context.verifyError("Unchecked verifier input");
                String component = ss.asSymbol();
                return VerificationType.reference_type(component);
         }
         default:
             return VerificationType.bogus_type;
        }
    }
}
