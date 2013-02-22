/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.code;

import java.util.Iterator;

import com.sun.tools.javac.util.*;

/** A type annotation position.
*
*  <p><b>This is NOT part of any supported API.
*  If you write code that depends on this, you do so at your own risk.
*  This code and its internal interfaces are subject to change or
*  deletion without notice.</b>
*/
// Code duplicated in com.sun.tools.classfile.TypeAnnotation.Position
public class TypeAnnotationPosition {

    public enum TypePathEntryKind {
        ARRAY(0),
        INNER_TYPE(1),
        WILDCARD(2),
        TYPE_ARGUMENT(3);

        public final int tag;

        private TypePathEntryKind(int tag) {
            this.tag = tag;
        }
    }

    public static class TypePathEntry {
        /** The fixed number of bytes per TypePathEntry. */
        public static final int bytesPerEntry = 2;

        public final TypePathEntryKind tag;
        public final int arg;

        public static final TypePathEntry ARRAY = new TypePathEntry(TypePathEntryKind.ARRAY);
        public static final TypePathEntry INNER_TYPE = new TypePathEntry(TypePathEntryKind.INNER_TYPE);
        public static final TypePathEntry WILDCARD = new TypePathEntry(TypePathEntryKind.WILDCARD);

        private TypePathEntry(TypePathEntryKind tag) {
            Assert.check(tag == TypePathEntryKind.ARRAY ||
                    tag == TypePathEntryKind.INNER_TYPE ||
                    tag == TypePathEntryKind.WILDCARD,
                    "Invalid TypePathEntryKind: " + tag);
            this.tag = tag;
            this.arg = 0;
        }

        public TypePathEntry(TypePathEntryKind tag, int arg) {
            Assert.check(tag == TypePathEntryKind.TYPE_ARGUMENT,
                    "Invalid TypePathEntryKind: " + tag);
            this.tag = tag;
            this.arg = arg;
        }

        public static TypePathEntry fromBinary(int tag, int arg) {
            Assert.check(arg == 0 || tag == TypePathEntryKind.TYPE_ARGUMENT.tag,
                    "Invalid TypePathEntry tag/arg: " + tag + "/" + arg);
            switch (tag) {
            case 0:
                return ARRAY;
            case 1:
                return INNER_TYPE;
            case 2:
                return WILDCARD;
            case 3:
                return new TypePathEntry(TypePathEntryKind.TYPE_ARGUMENT, arg);
            default:
                Assert.error("Invalid TypePathEntryKind tag: " + tag);
                return null;
            }
        }

        @Override
        public String toString() {
            return tag.toString() +
                    (tag == TypePathEntryKind.TYPE_ARGUMENT ? ("(" + arg + ")") : "");
        }

        @Override
        public boolean equals(Object other) {
            if (! (other instanceof TypePathEntry)) {
                return false;
            }
            TypePathEntry tpe = (TypePathEntry) other;
            return this.tag == tpe.tag && this.arg == tpe.arg;
        }

        @Override
        public int hashCode() {
            return this.tag.hashCode() * 17 + this.arg;
        }
    }

    public TargetType type = TargetType.UNKNOWN;

    // For generic/array types.
    public List<TypePathEntry> location = List.nil();

    // Tree position.
    public int pos = -1;

    // For type casts, type tests, new, locals (as start_pc),
    // and method and constructor reference type arguments.
    public boolean isValidOffset = false;
    public int offset = -1;

    // For locals. arrays same length
    public int[] lvarOffset = null;
    public int[] lvarLength = null;
    public int[] lvarIndex = null;

    // For type parameter bound
    public int bound_index = Integer.MIN_VALUE;

    // For type parameter and method parameter
    public int parameter_index = Integer.MIN_VALUE;

    // For class extends, implements, and throws clauses
    public int type_index = Integer.MIN_VALUE;

    // For exception parameters, index into exception table
    public int exception_index = Integer.MIN_VALUE;

    public TypeAnnotationPosition() {}

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        sb.append(type);

        switch (type) {
        // instanceof
        case INSTANCEOF:
        // new expression
        case NEW:
        // constructor/method reference receiver
        case CONSTRUCTOR_REFERENCE:
        case METHOD_REFERENCE:
            sb.append(", offset = ");
            sb.append(offset);
            break;
        // local variable
        case LOCAL_VARIABLE:
        // resource variable
        case RESOURCE_VARIABLE:
            if (lvarOffset == null) {
                sb.append(", lvarOffset is null!");
                break;
            }
            sb.append(", {");
            for (int i = 0; i < lvarOffset.length; ++i) {
                if (i != 0) sb.append("; ");
                sb.append("start_pc = ");
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
        // type parameter
        case CLASS_TYPE_PARAMETER:
        case METHOD_TYPE_PARAMETER:
            sb.append(", param_index = ");
            sb.append(parameter_index);
            break;
        // type parameter bound
        case CLASS_TYPE_PARAMETER_BOUND:
        case METHOD_TYPE_PARAMETER_BOUND:
            sb.append(", param_index = ");
            sb.append(parameter_index);
            sb.append(", bound_index = ");
            sb.append(bound_index);
            break;
        // class extends or implements clause
        case CLASS_EXTENDS:
            sb.append(", type_index = ");
            sb.append(type_index);
            break;
        // throws
        case THROWS:
            sb.append(", type_index = ");
            sb.append(type_index);
            break;
        // exception parameter
        case EXCEPTION_PARAMETER:
            sb.append(", exception_index = ");
            sb.append(exception_index);
            break;
        // method parameter
        case METHOD_FORMAL_PARAMETER:
            sb.append(", param_index = ");
            sb.append(parameter_index);
            break;
        // type cast
        case CAST:
        // method/constructor/reference type argument
        case CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT:
        case METHOD_INVOCATION_TYPE_ARGUMENT:
        case CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT:
        case METHOD_REFERENCE_TYPE_ARGUMENT:
            sb.append(", offset = ");
            sb.append(offset);
            sb.append(", type_index = ");
            sb.append(type_index);
            break;
        // We don't need to worry about these
        case METHOD_RETURN:
        case FIELD:
            break;
        case UNKNOWN:
            sb.append(", position UNKNOWN!");
            break;
        default:
            Assert.error("Unknown target type: " + type);
        }

        // Append location data for generics/arrays.
        if (!location.isEmpty()) {
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
        return !type.isLocal() || isValidOffset;
    }

    /**
     * Decode the binary representation for a type path and set
     * the {@code location} field.
     *
     * @param list The bytecode representation of the type path.
     */
    public static List<TypePathEntry> getTypePathFromBinary(java.util.List<Integer> list) {
        ListBuffer<TypePathEntry> loc = ListBuffer.lb();
        Iterator<Integer> iter = list.iterator();
        while (iter.hasNext()) {
            Integer fst = iter.next();
            Assert.check(iter.hasNext(), "Could not decode type path: " + list);
            Integer snd = iter.next();
            loc = loc.append(TypePathEntry.fromBinary(fst, snd));
        }
        return loc.toList();
    }

    public static List<Integer> getBinaryFromTypePath(java.util.List<TypePathEntry> locs) {
        ListBuffer<Integer> loc = ListBuffer.lb();
        for (TypePathEntry tpe : locs) {
            loc = loc.append(tpe.tag.tag);
            loc = loc.append(tpe.arg);
        }
        return loc.toList();
    }
}
