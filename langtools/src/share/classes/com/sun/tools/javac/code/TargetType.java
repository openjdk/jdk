/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import static com.sun.tools.javac.code.TargetType.TargetAttribute.*;

import java.util.EnumSet;
import java.util.Set;

/**
 * Describes the type of program element an extended annotation (or extended
 * compound attribute) targets.
 *
 * By comparison, a Tree.Kind has enum values for all elements in the AST, and
 * it does not provide enough resolution for type arguments (i.e., whether an
 * annotation targets a type argument in a local variable, method return type,
 * or a typecast).
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public enum TargetType {

    //
    // Some target types are commented out, because Java doesn't permit such
    // targets.  They are included here to confirm that their omission is
    // intentional omission not an accidental omission.
    //

    /** For annotations on typecasts. */
    TYPECAST(0x00, IsLocal),

    /** For annotations on a type argument or nested array of a typecast. */
    TYPECAST_GENERIC_OR_ARRAY(0x01, HasLocation, IsLocal),

    /** For annotations on type tests. */
    INSTANCEOF(0x02, IsLocal),

    /** For annotations on a type argument or nested array of a type test. */
    INSTANCEOF_GENERIC_OR_ARRAY(0x03, HasLocation, IsLocal),

    /** For annotations on object creation expressions. */
    NEW(0x04, IsLocal),

    /**
     * For annotations on a type argument or nested array of an object creation
     * expression.
     */
    NEW_GENERIC_OR_ARRAY(0x05, HasLocation, IsLocal),


    /** For annotations on the method receiver. */
    METHOD_RECEIVER(0x06),

    // invalid location
    //@Deprecated METHOD_RECEIVER_GENERIC_OR_ARRAY(0x07, HasLocation),

    /** For annotations on local variables. */
    LOCAL_VARIABLE(0x08, IsLocal),

    /** For annotations on a type argument or nested array of a local. */
    LOCAL_VARIABLE_GENERIC_OR_ARRAY(0x09, HasLocation, IsLocal),

    // handled by regular annotations
    //@Deprecated METHOD_RETURN(0x0A),

    /**
     * For annotations on a type argument or nested array of a method return
     * type.
     */
    METHOD_RETURN_GENERIC_OR_ARRAY(0x0B, HasLocation),

    // handled by regular annotations
    //@Deprecated METHOD_PARAMETER(0x0C),

    /** For annotations on a type argument or nested array of a method parameter. */
    METHOD_PARAMETER_GENERIC_OR_ARRAY(0x0D, HasLocation),

    // handled by regular annotations
    //@Deprecated FIELD(0x0E),

    /** For annotations on a type argument or nested array of a field. */
    FIELD_GENERIC_OR_ARRAY(0x0F, HasLocation),

    /** For annotations on a bound of a type parameter of a class. */
    CLASS_TYPE_PARAMETER_BOUND(0x10, HasBound, HasParameter),

    /**
     * For annotations on a type argument or nested array of a bound of a type
     * parameter of a class.
     */
    CLASS_TYPE_PARAMETER_BOUND_GENERIC_OR_ARRAY(0x11, HasBound, HasLocation, HasParameter),

    /** For annotations on a bound of a type parameter of a method. */
    METHOD_TYPE_PARAMETER_BOUND(0x12, HasBound, HasParameter),

    /**
     * For annotations on a type argument or nested array of a bound of a type
     * parameter of a method.
     */
    METHOD_TYPE_PARAMETER_BOUND_GENERIC_OR_ARRAY(0x13, HasBound, HasLocation, HasParameter),

    /** For annotations on the type of an "extends" or "implements" clause. */
    CLASS_EXTENDS(0x14),

    /** For annotations on the inner type of an "extends" or "implements" clause. */
    CLASS_EXTENDS_GENERIC_OR_ARRAY(0x15, HasLocation),

    /** For annotations on a throws clause in a method declaration. */
    THROWS(0x16),

    // invalid location
    //@Deprecated THROWS_GENERIC_OR_ARRAY(0x17, HasLocation),

    /** For annotations in type arguments of object creation expressions. */
    NEW_TYPE_ARGUMENT(0x18, IsLocal),
    NEW_TYPE_ARGUMENT_GENERIC_OR_ARRAY(0x19, HasLocation, IsLocal),

    METHOD_TYPE_ARGUMENT(0x1A, IsLocal),
    METHOD_TYPE_ARGUMENT_GENERIC_OR_ARRAY(0x1B, HasLocation, IsLocal),

    WILDCARD_BOUND(0x1C, HasBound),
    WILDCARD_BOUND_GENERIC_OR_ARRAY(0x1D, HasBound, HasLocation),

    CLASS_LITERAL(0x1E, IsLocal),
    CLASS_LITERAL_GENERIC_OR_ARRAY(0x1F, HasLocation, IsLocal),

    METHOD_TYPE_PARAMETER(0x20, HasParameter),

    // invalid location
    //@Deprecated METHOD_TYPE_PARAMETER_GENERIC_OR_ARRAY(0x21, HasLocation, HasParameter),

    CLASS_TYPE_PARAMETER(0x22, HasParameter),

    // invalid location
    //@Deprecated CLASS_TYPE_PARAMETER_GENERIC_OR_ARRAY(0x23, HasLocation, HasParameter),

    /** For annotations with an unknown target. */
    UNKNOWN(-1);

    static final int MAXIMUM_TARGET_TYPE_VALUE = 0x22;

    private final int targetTypeValue;
    private Set<TargetAttribute> flags;

    TargetType(int targetTypeValue, TargetAttribute... attributes) {
        if (targetTypeValue < Byte.MIN_VALUE
                || targetTypeValue > Byte.MAX_VALUE)
                throw new AssertionError("attribute type value needs to be a byte: " + targetTypeValue);
        this.targetTypeValue = (byte)targetTypeValue;
        flags = EnumSet.noneOf(TargetAttribute.class);
        for (TargetAttribute attr : attributes)
            flags.add(attr);
    }

    /**
     * Returns whether or not this TargetType represents an annotation whose
     * target is an inner type of a generic or array type.
     *
     * @return true if this TargetType represents an annotation on an inner
     *         type, false otherwise
     */
    public boolean hasLocation() {
        return flags.contains(HasLocation);
    }

    public TargetType getGenericComplement() {
        if (hasLocation())
            return this;
        else
            return fromTargetTypeValue(targetTypeValue() + 1);
    }

    /**
     * Returns whether or not this TargetType represents an annotation whose
     * target has a parameter index.
     *
     * @return true if this TargetType has a parameter index,
     *         false otherwise
     */
    public boolean hasParameter() {
        return flags.contains(HasParameter);
    }

    /**
     * Returns whether or not this TargetType represents an annotation whose
     * target is a type parameter bound.
     *
     * @return true if this TargetType represents an type parameter bound
     *         annotation, false otherwise
     */
    public boolean hasBound() {
        return flags.contains(HasBound);
    }

    /**
     * Returns whether or not this TargetType represents an annotation whose
     * target is exclusively a tree in a method body
     *
     * Note: wildcard bound targets could target a local tree and a class
     * member declaration signature tree
     */
    public boolean isLocal() {
        return flags.contains(IsLocal);
    }

    public int targetTypeValue() {
        return this.targetTypeValue;
    }

    private static TargetType[] targets = null;

    private static TargetType[] buildTargets() {
        TargetType[] targets = new TargetType[MAXIMUM_TARGET_TYPE_VALUE + 1];
        TargetType[] alltargets = values();
        for (TargetType target : alltargets) {
            if (target.targetTypeValue >= 0)
                targets[target.targetTypeValue] = target;
        }
        for (int i = 0; i <= MAXIMUM_TARGET_TYPE_VALUE; ++i) {
            if (targets[i] == null)
                targets[i] = UNKNOWN;
        }
        return targets;
    }

    public static boolean isValidTargetTypeValue(int tag) {
        if (targets == null)
            targets = buildTargets();

        if (((byte)tag) == ((byte)UNKNOWN.targetTypeValue))
            return true;

        return (tag >= 0 && tag < targets.length);
    }

    public static TargetType fromTargetTypeValue(int tag) {
        if (targets == null)
            targets = buildTargets();

        if (((byte)tag) == ((byte)UNKNOWN.targetTypeValue))
            return UNKNOWN;

        if (tag < 0 || tag >= targets.length)
            throw new IllegalArgumentException("Unknown TargetType: " + tag);
        return targets[tag];
    }

    static enum TargetAttribute {
        HasLocation, HasParameter, HasBound, IsLocal;
    }
}
