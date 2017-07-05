/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
package jdk.nashorn.internal.runtime;

import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import jdk.nashorn.internal.codegen.types.Type;

/**
 * This exception is thrown from an optimistic operation, e.g. an integer add,
 * that was to optimistic for what really took place. Typically things like
 * trying to get an array element that we want to be an int, and it was a double,
 * and an int add that actually overflows and needs a double for the representation
 */

@SuppressWarnings("serial")
public final class UnwarrantedOptimismException extends RuntimeException {
    /** Denotes an invalid program point */
    public static final int INVALID_PROGRAM_POINT = -1;

    /** The value for the first ordinary program point */
    public static final int FIRST_PROGRAM_POINT = 1;

    private Object returnValue;
    private final int    programPoint;
    private final Type   returnType;

    /**
     * Constructor without explicit return type. The return type is determined statically from the class of
     * the return value, and only canonical internal number representations are recognized. Use
     * {@link #createNarrowest} if you want to handle float and long values as numbers instead of objects.
     *
     * @param returnValue actual return value from the too narrow operation
     * @param programPoint program point where unwarranted optimism was detected
     */
    public UnwarrantedOptimismException(final Object returnValue, final int programPoint) {
        this(returnValue, programPoint, getReturnType(returnValue));
    }

    /**
     * Check if a program point is valid.
     * @param programPoint the program point
     * @return true if valid
     */
    public static boolean isValid(final int programPoint) {
        assert programPoint >= INVALID_PROGRAM_POINT;
        return programPoint != INVALID_PROGRAM_POINT;
    }

    private static Type getReturnType(final Object v) {
        if (v instanceof Double) {
            return Type.NUMBER;
        }
        assert !(v instanceof Integer) : v + " is an int"; // Can't have an unwarranted optimism exception with int
        return Type.OBJECT;
    }

    /**
     * Constructor with explicit return value type.
     * @param returnValue actual return value from the too narrow operation
     * @param programPoint program point where unwarranted optimism was detected
     * @param returnType type of the returned value. Used to disambiguate the return type. E.g. an {@code ObjectArrayData}
     * might return a {@link Double} for a particular element getter, but still throw this exception even if the call
     * site can accept a double, since the array's type is actually {@code Type#OBJECT}. In this case, it must
     * explicitly use this constructor to indicate its values are to be considered {@code Type#OBJECT} and not
     * {@code Type#NUMBER}.
     */
    public UnwarrantedOptimismException(final Object returnValue, final int programPoint, final Type returnType) {
        super("", null, false, Context.DEBUG);
        assert returnType != Type.OBJECT || returnValue == null || !Type.typeFor(returnValue.getClass()).isNumeric();
        assert returnType != Type.INT;
        this.returnValue  = returnValue;
        this.programPoint = programPoint;
        this.returnType   = returnType;
    }

    /**
     * Create an {@code UnwarrantedOptimismException} with the given return value and program point, narrowing
     * the type to {@code number} if the value is a float or a long that can be represented as double.
     *
     * @param returnValue the return value
     * @param programPoint the program point
     * @return the exception
     */
    public static UnwarrantedOptimismException createNarrowest(final Object returnValue, final int programPoint) {
        if (returnValue instanceof Float
                || (returnValue instanceof Long && JSType.isRepresentableAsDouble((Long) returnValue))) {
            return new UnwarrantedOptimismException(((Number) returnValue).doubleValue(), programPoint, Type.NUMBER);
        }
        return new UnwarrantedOptimismException(returnValue, programPoint);
    }

    /**
     * Get the return value. This is a destructive readout, after the method is invoked the return value is null'd out.
     * @return return value
     */
    public Object getReturnValueDestructive() {
        final Object retval = returnValue;
        returnValue = null;
        return retval;
    }

    Object getReturnValueNonDestructive() {
        return returnValue;
    }

    /**
     * Get the return type
     * @return return type
     */
    public Type getReturnType() {
        return returnType;
    }

    /**
     * Does this exception refer to an invalid program point? This might be OK if
     * we throw it, e.g. from a parameter guard
     * @return true if invalid program point specified
     */
    public boolean hasInvalidProgramPoint() {
        return programPoint == INVALID_PROGRAM_POINT;
    }

    /**
     * Get the program point
     * @return the program point
     */
    public int getProgramPoint() {
        return programPoint;
    }

    /**
     * Check if we ended up with a primitive return value (even though it may be
     * too wide for what we tried to do, e.g. double instead of int)
     * @return true if return value is primitive
     */
    public boolean hasPrimitiveReturnValue() {
        return returnValue instanceof Number || returnValue instanceof Boolean;
    }

    @Override
    public String getMessage() {
        return "UNWARRANTED OPTIMISM: [returnValue=" +
            returnValue +
            " (class=" +
            (returnValue == null ? "null" : returnValue.getClass().getSimpleName()) +
            (hasInvalidProgramPoint() ?
                " <invalid program point>" :
                (" @ program point #" + programPoint)) +
            ")]";
    }


    private void writeObject(final ObjectOutputStream out) throws NotSerializableException {
        throw new NotSerializableException(getClass().getName());
    }

    private void readObject(final ObjectInputStream in) throws NotSerializableException {
        throw new NotSerializableException(getClass().getName());
    }
}
