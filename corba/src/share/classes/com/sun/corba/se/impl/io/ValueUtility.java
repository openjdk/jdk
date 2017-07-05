/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package com.sun.corba.se.impl.io;

import com.sun.org.omg.CORBA.ValueDefPackage.FullValueDescription;
import com.sun.org.omg.CORBA.OperationDescription;
import com.sun.org.omg.CORBA.AttributeDescription;
import org.omg.CORBA.ValueMember;
import com.sun.org.omg.CORBA.Initializer;
import org.omg.CORBA.IDLType;
import com.sun.org.omg.CORBA._IDLTypeStub;
import org.omg.CORBA.ORB;
import org.omg.CORBA.TypeCodePackage.*;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.TCKind;
import java.lang.reflect.*;
import com.sun.corba.se.impl.util.RepositoryId;
import java.util.*;
import javax.rmi.CORBA.Util;
import javax.rmi.CORBA.ValueHandler;

/**
 * Holds utility methods for converting from ObjectStreamClass to
 * FullValueDescription and generating typecodes from ObjectStreamClass.
 **/
public class ValueUtility {

    public static final short PRIVATE_MEMBER = 0;
    public static final short PUBLIC_MEMBER = 1;

    private static final String primitiveConstants[] = {
        null,       // tk_null         0
        null,           // tk_void         1
        "S",            // tk_short        2
        "I",            // tk_long         3
        "S",            // tk_ushort       4
        "I",            // tk_ulong        5
        "F",            // tk_float        6
        "D",            // tk_double       7
        "Z",            // tk_boolean      8
        "C",            // tk_char         9
        "B",            // tk_octet        10
        null,           // tk_any          11
        null,           // tk_typecode     12
        null,           // tk_principal    13
        null,           // tk_objref       14
        null,           // tk_struct       15
        null,           // tk_union        16
        null,           // tk_enum         17
        null,           // tk_string       18
        null,           // tk_sequence     19
        null,           // tk_array        20
        null,           // tk_alias        21
        null,           // tk_except       22
        "J",            // tk_longlong     23
        "J",            // tk_ulonglong    24
        "D",            // tk_longdouble   25
        "C",            // tk_wchar        26
        null,           // tk_wstring      27
        null,       // tk_fixed        28
        null,       // tk_value        29
        null,       // tk_value_box    30
        null,       // tk_native       31
        null,       // tk_abstract_interface 32
    };

    static {
        sun.corba.SharedSecrets.setJavaCorbaAccess(new sun.corba.JavaCorbaAccess() {
            public ValueHandlerImpl newValueHandlerImpl() {
                return ValueHandlerImpl.getInstance();
            }
            public Class<?> loadClass(String className) throws ClassNotFoundException {
                if (Thread.currentThread().getContextClassLoader() != null) {
                    return Thread.currentThread().getContextClassLoader().
                        loadClass(className);
                } else {
                    return ClassLoader.getSystemClassLoader().loadClass(className);
                }
            }
        });
    }

    public static String getSignature(ValueMember member)
        throws ClassNotFoundException {

        // REVISIT.  Can the type be something that is
        // non-primitive yet not a value_box, value, or objref?
        // If so, should use ObjectStreamClass or throw
        // exception.

        if (member.type.kind().value() == TCKind._tk_value_box ||
            member.type.kind().value() == TCKind._tk_value ||
            member.type.kind().value() == TCKind._tk_objref) {
            Class c = RepositoryId.cache.getId(member.id).getClassFromType();
            return ObjectStreamClass.getSignature(c);

        } else {

            return primitiveConstants[member.type.kind().value()];
        }

    }

    public static FullValueDescription translate(ORB orb, ObjectStreamClass osc, ValueHandler vh){

        // Create FullValueDescription
        FullValueDescription result = new FullValueDescription();
        Class className = osc.forClass();

        ValueHandlerImpl vhandler = (com.sun.corba.se.impl.io.ValueHandlerImpl) vh;
        String repId = vhandler.createForAnyType(className);

        // Set FVD name
        result.name = vhandler.getUnqualifiedName(repId);
        if (result.name == null)
            result.name = "";

        // Set FVD id _REVISIT_ : Manglings
        result.id = vhandler.getRMIRepositoryID(className);
        if (result.id == null)
            result.id = "";

        // Set FVD is_abstract
        result.is_abstract = ObjectStreamClassCorbaExt.isAbstractInterface(className);

        // Set FVD is_custom
        result.is_custom = osc.hasWriteObject() || osc.isExternalizable();

        // Set FVD defined_in _REVISIT_ : Manglings
        result.defined_in = vhandler.getDefinedInId(repId);
        if (result.defined_in == null)
            result.defined_in = "";

        // Set FVD version
        result.version = vhandler.getSerialVersionUID(repId);
        if (result.version == null)
            result.version = "";

        // Skip FVD operations - N/A
        result.operations = new OperationDescription[0];

        // Skip FVD attributed - N/A
        result.attributes = new AttributeDescription[0];

        // Set FVD members
        // Maps classes to repositoryIDs strings. This is used to detect recursive types.
        IdentityKeyValueStack createdIDs = new IdentityKeyValueStack();
        // Stores all types created for resolving indirect types at the end.
        result.members = translateMembers(orb, osc, vh, createdIDs);

        // Skip FVD initializers - N/A
        result.initializers = new Initializer[0];

        Class interfaces[] = osc.forClass().getInterfaces();
        int abstractCount = 0;

        // Skip FVD supported_interfaces
        result.supported_interfaces =  new String[interfaces.length];
        for (int interfaceIndex = 0; interfaceIndex < interfaces.length;
             interfaceIndex++) {
            result.supported_interfaces[interfaceIndex] =
                vhandler.createForAnyType(interfaces[interfaceIndex]);

            if ((!(java.rmi.Remote.class.isAssignableFrom(interfaces[interfaceIndex]))) ||
                (!Modifier.isPublic(interfaces[interfaceIndex].getModifiers())))
                abstractCount++;
        }

        // Skip FVD abstract_base_values - N/A
        result.abstract_base_values = new String[abstractCount];
        for (int interfaceIndex = 0; interfaceIndex < interfaces.length;
             interfaceIndex++) {
            if ((!(java.rmi.Remote.class.isAssignableFrom(interfaces[interfaceIndex]))) ||
                (!Modifier.isPublic(interfaces[interfaceIndex].getModifiers())))
                result.abstract_base_values[interfaceIndex] =
                    vhandler.createForAnyType(interfaces[interfaceIndex]);

        }

        result.is_truncatable = false;

        // Set FVD base_value
        Class superClass = osc.forClass().getSuperclass();
        if (java.io.Serializable.class.isAssignableFrom(superClass))
            result.base_value = vhandler.getRMIRepositoryID(superClass);
        else
            result.base_value = "";

        // Set FVD type
        //result.type = createTypeCodeForClass(orb, osc.forClass());
        result.type = orb.get_primitive_tc(TCKind.tk_value); //11638

        return result;

    }

    private static ValueMember[] translateMembers (ORB orb,
                                                   ObjectStreamClass osc,
                                                   ValueHandler vh,
                                                   IdentityKeyValueStack createdIDs)
    {
        ValueHandlerImpl vhandler = (com.sun.corba.se.impl.io.ValueHandlerImpl) vh;
        ObjectStreamField fields[] = osc.getFields();
        int fieldsLength = fields.length;
        ValueMember[] members = new ValueMember[fieldsLength];
        // Note : fields come out of ObjectStreamClass in correct order for
        // writing.  So, we will create the same order in the members array.
        for (int i = 0; i < fieldsLength; i++) {
            String valRepId = vhandler.getRMIRepositoryID(fields[i].getClazz());
            members[i] = new ValueMember();
            members[i].name = fields[i].getName();
            members[i].id = valRepId; // _REVISIT_ : Manglings
            members[i].defined_in = vhandler.getDefinedInId(valRepId);// _REVISIT_ : Manglings
            members[i].version = "1.0";
            members[i].type_def = new _IDLTypeStub(); // _REVISIT_ : IDLType implementation missing

            if (fields[i].getField() == null) {
                // When using serialPersistentFields, the class may
                // no longer have an actual Field that corresponds
                // to one of the items.  The Java to IDL spec
                // ptc-00-01-06 1.3.5.6 says that the IDL field
                // should be private in this case.
                members[i].access = PRIVATE_MEMBER;
            } else {
                int m = fields[i].getField().getModifiers();
                if (Modifier.isPublic(m))
                    members[i].access = PUBLIC_MEMBER;
                else
                    members[i].access = PRIVATE_MEMBER;
            }

            switch (fields[i].getTypeCode()) {
            case 'B':
                members[i].type = orb.get_primitive_tc(TCKind.tk_octet); //11638
                break;
            case 'C':
                members[i].type
                    = orb.get_primitive_tc(vhandler.getJavaCharTCKind()); // 11638
                break;
            case 'F':
                members[i].type = orb.get_primitive_tc(TCKind.tk_float); //11638
                break;
            case 'D' :
                members[i].type = orb.get_primitive_tc(TCKind.tk_double); //11638
                break;
            case 'I':
                members[i].type = orb.get_primitive_tc(TCKind.tk_long); //11638
                break;
            case 'J':
                members[i].type = orb.get_primitive_tc(TCKind.tk_longlong); //11638
                break;
            case 'S':
                members[i].type = orb.get_primitive_tc(TCKind.tk_short); //11638
                break;
            case 'Z':
                members[i].type = orb.get_primitive_tc(TCKind.tk_boolean); //11638
                break;
        // case '[':
        //      members[i].type = orb.get_primitive_tc(TCKind.tk_value_box); //11638
        //      members[i].id = RepositoryId.createForAnyType(fields[i].getType());
        //      break;
            default:
                members[i].type = createTypeCodeForClassInternal(orb, fields[i].getClazz(), vhandler,
                                  createdIDs);
                members[i].id = vhandler.createForAnyType(fields[i].getType());
                break;
            } // end switch

        } // end for loop

        return members;
    }

    private static boolean exists(String str, String strs[]){
        for (int i = 0; i < strs.length; i++)
            if (str.equals(strs[i]))
                return true;

        return false;
    }

    public static boolean isAssignableFrom(String clzRepositoryId, FullValueDescription type,
                                           com.sun.org.omg.SendingContext.CodeBase sender){

        if (exists(clzRepositoryId, type.supported_interfaces))
            return true;

        if (clzRepositoryId.equals(type.id))
            return true;

        if ((type.base_value != null) &&
            (!type.base_value.equals(""))) {
            FullValueDescription parent = sender.meta(type.base_value);

            return isAssignableFrom(clzRepositoryId, parent, sender);
        }

        return false;

    }

    public static TypeCode createTypeCodeForClass (ORB orb, java.lang.Class c, ValueHandler vh) {
        // Maps classes to repositoryIDs strings. This is used to detect recursive types.
        IdentityKeyValueStack createdIDs = new IdentityKeyValueStack();
        // Stores all types created for resolving indirect types at the end.
        TypeCode tc = createTypeCodeForClassInternal(orb, c, vh, createdIDs);
        return tc;
    }

    private static TypeCode createTypeCodeForClassInternal (ORB orb,
                                                            java.lang.Class c,
                                                            ValueHandler vh,
                                                            IdentityKeyValueStack createdIDs)
    {
        // This wrapper method is the protection against infinite recursion.
        TypeCode tc = null;
        String id = (String)createdIDs.get(c);
        if (id != null) {
            return orb.create_recursive_tc(id);
        } else {
            id = vh.getRMIRepositoryID(c);
            if (id == null) id = "";
            // cache the rep id BEFORE creating a new typecode.
            // so that recursive tc can look up the rep id.
            createdIDs.push(c, id);
            tc = createTypeCodeInternal(orb, c, vh, id, createdIDs);
            createdIDs.pop();
            return tc;
        }
    }

    // Maintains a stack of key-value pairs. Compares elements using == operator.
    private static class IdentityKeyValueStack {
        private static class KeyValuePair {
            Object key;
            Object value;
            KeyValuePair(Object key, Object value) {
                this.key = key;
                this.value = value;
            }
            boolean equals(KeyValuePair pair) {
                return pair.key == this.key;
            }
        }

        Stack pairs = null;

        Object get(Object key) {
            if (pairs == null) {
                return null;
            }
            for (Iterator i = pairs.iterator(); i.hasNext();) {
                KeyValuePair pair = (KeyValuePair)i.next();
                if (pair.key == key) {
                    return pair.value;
                }
            }
            return null;
        }

        void push(Object key, Object value) {
            if (pairs == null) {
                pairs = new Stack();
            }
            pairs.push(new KeyValuePair(key, value));
        }

        void pop() {
            pairs.pop();
        }
    }

    private static TypeCode createTypeCodeInternal (ORB orb,
                                                    java.lang.Class c,
                                                    ValueHandler vh,
                                                    String id,
                                                    IdentityKeyValueStack createdIDs)
    {
        if ( c.isArray() ) {
            // Arrays - may recurse for multi-dimensional arrays
            Class componentClass = c.getComponentType();
            TypeCode embeddedType;
            if ( componentClass.isPrimitive() ){
                embeddedType
                    = ValueUtility.getPrimitiveTypeCodeForClass(orb,
                                                                componentClass,
                                                                vh);
            } else {
                embeddedType = createTypeCodeForClassInternal(orb, componentClass, vh,
                                                              createdIDs);
            }
            TypeCode t = orb.create_sequence_tc (0, embeddedType);
            return orb.create_value_box_tc (id, "Sequence", t);
        } else if ( c == java.lang.String.class ) {
            // Strings
            TypeCode t = orb.create_string_tc (0);
            return orb.create_value_box_tc (id, "StringValue", t);
        } else if (java.rmi.Remote.class.isAssignableFrom(c)) {
            return orb.get_primitive_tc(TCKind.tk_objref);
        } else if (org.omg.CORBA.Object.class.isAssignableFrom(c)) {
            return orb.get_primitive_tc(TCKind.tk_objref);
        }

        // Anything else

        ObjectStreamClass osc = ObjectStreamClass.lookup(c);

        if (osc == null) {
            return orb.create_value_box_tc (id, "Value", orb.get_primitive_tc (TCKind.tk_value));
        }

        // type modifier
        // REVISIT truncatable and abstract?
        short modifier = (osc.isCustomMarshaled() ? org.omg.CORBA.VM_CUSTOM.value : org.omg.CORBA.VM_NONE.value);

        // concrete base
        TypeCode base = null;
        Class superClass = c.getSuperclass();
        if (superClass != null && java.io.Serializable.class.isAssignableFrom(superClass)) {
            base = createTypeCodeForClassInternal(orb, superClass, vh, createdIDs);
        }

        // members
        ValueMember[] members = translateMembers (orb, osc, vh, createdIDs);

        return orb.create_value_tc(id, c.getName(), modifier, base, members);
    }

    public static TypeCode getPrimitiveTypeCodeForClass (ORB orb,
                                                         Class c,
                                                         ValueHandler vh) {

        if (c == Integer.TYPE) {
            return orb.get_primitive_tc (TCKind.tk_long);
        } else if (c == Byte.TYPE) {
            return orb.get_primitive_tc (TCKind.tk_octet);
        } else if (c == Long.TYPE) {
            return orb.get_primitive_tc (TCKind.tk_longlong);
        } else if (c == Float.TYPE) {
            return orb.get_primitive_tc (TCKind.tk_float);
        } else if (c == Double.TYPE) {
            return orb.get_primitive_tc (TCKind.tk_double);
        } else if (c == Short.TYPE) {
            return orb.get_primitive_tc (TCKind.tk_short);
        } else if (c == Character.TYPE) {
            return orb.get_primitive_tc (((ValueHandlerImpl)vh).getJavaCharTCKind());
        } else if (c == Boolean.TYPE) {
            return orb.get_primitive_tc (TCKind.tk_boolean);
        } else {
            // _REVISIT_ Not sure if this is right.
            return orb.get_primitive_tc (TCKind.tk_any);
        }
    }
}
