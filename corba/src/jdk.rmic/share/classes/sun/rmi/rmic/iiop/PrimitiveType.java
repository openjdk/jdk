/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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

package sun.rmi.rmic.iiop;

import sun.tools.java.CompilerError;
import sun.tools.java.Identifier;
import sun.tools.java.ClassDefinition;

/**
 * PrimitiveType wraps primitive types and void.
 * <p>
 * The static forPrimitive(...) method must be used to obtain an instance, and
 * will return null if the type is non-conforming.
 *
 * @author      Bryan Atsatt
 */
public class PrimitiveType extends Type {

    //_____________________________________________________________________
    // Public Interfaces
    //_____________________________________________________________________

    /**
     * Create a PrimitiveType object for the given type.
     *
     * If the type is not a properly formed or if some other error occurs, the
     * return value will be null, and errors will have been reported to the
     * supplied BatchEnvironment.
     */
    public static PrimitiveType forPrimitive(sun.tools.java.Type type,
                                             ContextStack stack) {

        if (stack.anyErrors()) return null;

        // Do we already have it?

        Type existing = getType(type,stack);

        if (existing != null) {

            if (!(existing instanceof PrimitiveType)) return null; // False hit.

            // Yep, so return it...

            return (PrimitiveType) existing;
        }

        int typeCode;

        switch (type.getTypeCode()) {
        case TC_VOID:           typeCode = TYPE_VOID; break;
        case TC_BOOLEAN:        typeCode = TYPE_BOOLEAN; break;
        case TC_BYTE:           typeCode = TYPE_BYTE; break;
        case TC_CHAR:           typeCode = TYPE_CHAR; break;
        case TC_SHORT:          typeCode = TYPE_SHORT; break;
        case TC_INT:            typeCode = TYPE_INT; break;
        case TC_LONG:           typeCode = TYPE_LONG; break;
        case TC_FLOAT:          typeCode = TYPE_FLOAT; break;
        case TC_DOUBLE:         typeCode = TYPE_DOUBLE; break;
        default: return null;
        }

        PrimitiveType it = new PrimitiveType(stack,typeCode);

        // Add it...

        putType(type,it,stack);

        // Do the stack thing in case tracing on...

        stack.push(it);
        stack.pop(true);

        return it;
    }

    /**
     * Return signature for this type  (e.g. com.acme.Dynamite
     * would return "com.acme.Dynamite", byte = "B")
     */
    public String getSignature() {
        switch (getTypeCode()) {
        case TYPE_VOID:         return SIG_VOID;
        case TYPE_BOOLEAN:      return SIG_BOOLEAN;
        case TYPE_BYTE:         return SIG_BYTE;
        case TYPE_CHAR:         return SIG_CHAR;
        case TYPE_SHORT:    return SIG_SHORT;
        case TYPE_INT:          return SIG_INT;
        case TYPE_LONG:         return SIG_LONG;
        case TYPE_FLOAT:        return SIG_FLOAT;
        case TYPE_DOUBLE:       return SIG_DOUBLE;
        default:            return null;
        }
    }

    /**
     * Return a string describing this type.
     */
    public String getTypeDescription () {
        return "Primitive";
    }

    /**
     * IDL_Naming
     * Return the fully qualified IDL name for this type (e.g. com.acme.Dynamite would
     * return "com::acme::Dynamite").
     * @param global If true, prepends "::".
     */
    public String getQualifiedIDLName(boolean global) {
        return super.getQualifiedIDLName(false);
    }

    //_____________________________________________________________________
    // Subclass/Internal Interfaces
    //_____________________________________________________________________

    /*
     * Load a Class instance. Return null if fail.
     */
    protected Class loadClass() {
        switch (getTypeCode()) {
        case TYPE_VOID:         return Null.class;
        case TYPE_BOOLEAN:      return boolean.class;
        case TYPE_BYTE:         return byte.class;
        case TYPE_CHAR:         return char.class;
        case TYPE_SHORT:        return short.class;
        case TYPE_INT:          return int.class;
        case TYPE_LONG:         return long.class;
        case TYPE_FLOAT:        return float.class;
        case TYPE_DOUBLE:       return double.class;
        default:            throw new CompilerError("Not a primitive type");
        }
    }

    /**
     * IDL_Naming
     * Create an PrimitiveType instance for the given class.
     */
    private PrimitiveType(ContextStack stack, int typeCode) {
        super(stack,typeCode | TM_PRIMITIVE);

        // Validate type and set names...

        String idlName = IDLNames.getTypeName(typeCode,false);
        Identifier id = null;

        switch (typeCode) {
        case TYPE_VOID:         id = idVoid; break;
        case TYPE_BOOLEAN:      id = idBoolean; break;
        case TYPE_BYTE:         id = idByte; break;
        case TYPE_CHAR:         id = idChar; break;
        case TYPE_SHORT:        id = idShort; break;
        case TYPE_INT:          id = idInt; break;
        case TYPE_LONG:         id = idLong; break;
        case TYPE_FLOAT:        id = idFloat; break;
        case TYPE_DOUBLE:       id = idDouble; break;
        default:            throw new CompilerError("Not a primitive type");
        }

        setNames(id,null,idlName);
        setRepositoryID();
    }
}

class Null {}
