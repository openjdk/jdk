/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.javac.code;

import com.sun.tools.javac.util.*;

/** A type annotation position.
*
*  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
*  you write code that depends on this, you do so at your own risk.
*  This code and its internal interfaces are subject to change or
*  deletion without notice.</b>
*/
public class TypeAnnotationPosition {

    public TargetType type = TargetType.UNKNOWN;

    // For generic/array types.
    public List<Integer> location = List.nil();

    // Tree position.
    public int pos = -1;

    // For typecasts, type tests, new (and locals, as start_pc).
    public boolean isValidOffset = false;
    public int offset = -1;

    // For locals. arrays same length
    public int[] lvarOffset = new int[] { -1 };
    public int[] lvarLength = new int[] { -1 };
    public int[] lvarIndex = new int[] { -1 };

    // For type parameter bound
    public int bound_index = -1;

    // For type parameter and method parameter
    public int parameter_index = -1;

    // For class extends, implements, and throws classes
    public int type_index = -2;

    // For wildcards
    public TypeAnnotationPosition wildcard_position = null;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        sb.append(type);

        switch (type) {
        // type case
        case TYPECAST:
        case TYPECAST_GENERIC_OR_ARRAY:
            // object creation
        case INSTANCEOF:
        case INSTANCEOF_GENERIC_OR_ARRAY:
            // new expression
        case NEW:
        case NEW_GENERIC_OR_ARRAY:
        case NEW_TYPE_ARGUMENT:
        case NEW_TYPE_ARGUMENT_GENERIC_OR_ARRAY:
            sb.append(", offset = ");
            sb.append(offset);
            break;
            // local variable
        case LOCAL_VARIABLE:
        case LOCAL_VARIABLE_GENERIC_OR_ARRAY:
            sb.append(", {");
            for (int i = 0; i < lvarOffset.length; ++i) {
                if (i != 0) sb.append("; ");
                sb.append(", start_pc = ");
                sb.append(lvarOffset[i]);
                sb.append(", length = ");
                sb.append(lvarLength[i]);
                sb.append(", index = ");
                sb.append(lvarIndex[i]);
            }
            sb.append("}");
            break;
            // method receiver
        case METHOD_RECEIVER:
            // Do nothing
            break;
            // type parameters
        case CLASS_TYPE_PARAMETER:
        case METHOD_TYPE_PARAMETER:
            sb.append(", param_index = ");
            sb.append(parameter_index);
            break;
            // type parameters bound
        case CLASS_TYPE_PARAMETER_BOUND:
        case CLASS_TYPE_PARAMETER_BOUND_GENERIC_OR_ARRAY:
        case METHOD_TYPE_PARAMETER_BOUND:
        case METHOD_TYPE_PARAMETER_BOUND_GENERIC_OR_ARRAY:
            sb.append(", param_index = ");
            sb.append(parameter_index);
            sb.append(", bound_index = ");
            sb.append(bound_index);
            break;
            // wildcard
        case WILDCARD_BOUND:
        case WILDCARD_BOUND_GENERIC_OR_ARRAY:
            sb.append(", wild_card = ");
            sb.append(wildcard_position);
            break;
            // Class extends and implements clauses
        case CLASS_EXTENDS:
        case CLASS_EXTENDS_GENERIC_OR_ARRAY:
            sb.append(", type_index = ");
            sb.append(type_index);
            break;
            // throws
        case THROWS:
            sb.append(", type_index = ");
            sb.append(type_index);
            break;
        case CLASS_LITERAL:
            sb.append(", offset = ");
            sb.append(offset);
            break;
            // method parameter: not specified
        case METHOD_PARAMETER_GENERIC_OR_ARRAY:
            sb.append(", param_index = ");
            sb.append(parameter_index);
            break;
            // method type argument: wasn't specified
        case METHOD_TYPE_ARGUMENT:
        case METHOD_TYPE_ARGUMENT_GENERIC_OR_ARRAY:
            sb.append(", offset = ");
            sb.append(offset);
            sb.append(", type_index = ");
            sb.append(type_index);
            break;
            // We don't need to worry abut these
        case METHOD_RETURN_GENERIC_OR_ARRAY:
        case FIELD_GENERIC_OR_ARRAY:
            break;
        case UNKNOWN:
            break;
        default:
            //                throw new AssertionError("unknown type: " + type);
        }

        // Append location data for generics/arrays.
        if (type.hasLocation()) {
            sb.append(", location = (");
            sb.append(location);
            sb.append(")");
        }

        sb.append(", pos = ");
        sb.append(pos);

        sb.append(']');
        return sb.toString();
    }

    /**
     * Indicates whether the target tree of the annotation has been optimized
     * away from classfile or not.
     * @return true if the target has not been optimized away
     */
    public boolean emitToClassfile() {
        if (type == TargetType.WILDCARD_BOUND
            || type == TargetType.WILDCARD_BOUND_GENERIC_OR_ARRAY)
            return wildcard_position.isValidOffset;
        else
            return !type.isLocal() || isValidOffset;
    }
}
