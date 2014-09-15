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

import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.JSType.getAccessorTypeIndex;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.isValid;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.support.TypeUtilities;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;

/**
 * Optimistic return value filters
 */
public final class OptimisticReturnFilters {
    private static final MethodHandle[] ENSURE_INT;
    private static final MethodHandle[] ENSURE_LONG;
    private static final MethodHandle[] ENSURE_NUMBER;

    private static final int BOOLEAN_TYPE_INDEX;
    private static final int CHAR_TYPE_INDEX;
    private static final int FLOAT_TYPE_INDEX;
    private static final int VOID_TYPE_INDEX;

    static {
        final MethodHandle INT_DOUBLE = findOwnMH("ensureInt", int.class, double.class, int.class);
        ENSURE_INT = new MethodHandle[] {
                null,
                findOwnMH("ensureInt", int.class, long.class, int.class),
                INT_DOUBLE,
                findOwnMH("ensureInt", int.class, Object.class, int.class),
                findOwnMH("ensureInt", int.class, int.class),
                findOwnMH("ensureInt", int.class, boolean.class, int.class),
                findOwnMH("ensureInt", int.class, char.class, int.class),
                INT_DOUBLE.asType(INT_DOUBLE.type().changeParameterType(0, float.class)),
        };

        VOID_TYPE_INDEX = ENSURE_INT.length - 4;
        BOOLEAN_TYPE_INDEX = ENSURE_INT.length - 3;
        CHAR_TYPE_INDEX = ENSURE_INT.length - 2;
        FLOAT_TYPE_INDEX = ENSURE_INT.length - 1;

        final MethodHandle LONG_DOUBLE = findOwnMH("ensureLong", long.class, double.class, int.class);
        ENSURE_LONG = new MethodHandle[] {
                null,
                null,
                LONG_DOUBLE,
                findOwnMH("ensureLong", long.class, Object.class, int.class),
                ENSURE_INT[VOID_TYPE_INDEX].asType(ENSURE_INT[VOID_TYPE_INDEX].type().changeReturnType(long.class)),
                ENSURE_INT[BOOLEAN_TYPE_INDEX].asType(ENSURE_INT[BOOLEAN_TYPE_INDEX].type().changeReturnType(long.class)),
                ENSURE_INT[CHAR_TYPE_INDEX].asType(ENSURE_INT[CHAR_TYPE_INDEX].type().changeReturnType(long.class)),
                LONG_DOUBLE.asType(LONG_DOUBLE.type().changeParameterType(0, float.class)),
            };

        ENSURE_NUMBER = new MethodHandle[] {
                null,
                null,
                null,
                findOwnMH("ensureNumber", double.class, Object.class, int.class),
                ENSURE_INT[VOID_TYPE_INDEX].asType(ENSURE_INT[VOID_TYPE_INDEX].type().changeReturnType(double.class)),
                ENSURE_INT[BOOLEAN_TYPE_INDEX].asType(ENSURE_INT[BOOLEAN_TYPE_INDEX].type().changeReturnType(double.class)),
                ENSURE_INT[CHAR_TYPE_INDEX].asType(ENSURE_INT[CHAR_TYPE_INDEX].type().changeReturnType(double.class)),
                null
        };
    }

    /**
     * Given a method handle and an expected return type, perform return value filtering
     * according to the optimistic type coercion rules
     * @param mh method handle
     * @param expectedReturnType expected return type
     * @param programPoint program point
     * @return filtered method
     */
    public static MethodHandle filterOptimisticReturnValue(final MethodHandle mh, final Class<?> expectedReturnType, final int programPoint) {
        if(!isValid(programPoint)) {
            return mh;
        }

        final MethodType type = mh.type();
        final Class<?> actualReturnType = type.returnType();
        if(TypeUtilities.isConvertibleWithoutLoss(actualReturnType, expectedReturnType)) {
            return mh;
        }

        final MethodHandle guard = getOptimisticTypeGuard(expectedReturnType, actualReturnType);
        return guard == null ? mh : MH.filterReturnValue(mh, MH.insertArguments(guard, guard.type().parameterCount() - 1, programPoint));
    }

    /**
     * Given a guarded invocation and a callsite descriptor, perform return value filtering
     * according to the optimistic type coercion rules, using the return value from the descriptor
     * @param inv the invocation
     * @param desc the descriptor
     * @return filtered invocation
     */
    public static GuardedInvocation filterOptimisticReturnValue(final GuardedInvocation inv, final CallSiteDescriptor desc) {
        if(!NashornCallSiteDescriptor.isOptimistic(desc)) {
            return inv;
        }
        return inv.replaceMethods(filterOptimisticReturnValue(inv.getInvocation(), desc.getMethodType().returnType(),
                NashornCallSiteDescriptor.getProgramPoint(desc)), inv.getGuard());
    }

    private static MethodHandle getOptimisticTypeGuard(final Class<?> actual, final Class<?> provable) {
        final MethodHandle guard;
        final int provableTypeIndex = getProvableTypeIndex(provable);
        if (actual == int.class) {
            guard = ENSURE_INT[provableTypeIndex];
        } else if (actual == long.class) {
            guard = ENSURE_LONG[provableTypeIndex];
        } else if (actual == double.class) {
            guard = ENSURE_NUMBER[provableTypeIndex];
        } else {
            guard = null;
            assert !actual.isPrimitive() : actual + ", " + provable;
        }
        if(guard != null && !(provable.isPrimitive())) {
            // Make sure filtering a MethodHandle(...)String works with a filter MethodHandle(Object, int)... Note that
            // if the return type of the method is incompatible with Number, then the guard will always throw an
            // UnwarrantedOperationException when invoked, but we must link it anyway as we need the guarded function to
            // successfully execute and return the non-convertible return value that it'll put into the thrown
            // UnwarrantedOptimismException.
            return guard.asType(guard.type().changeParameterType(0, provable));
        }
        return guard;
    }

    private static int getProvableTypeIndex(final Class<?> provable) {
        final int accTypeIndex = getAccessorTypeIndex(provable);
        if(accTypeIndex != -1) {
            return accTypeIndex;
        } else if(provable == boolean.class) {
            return BOOLEAN_TYPE_INDEX;
        } else if(provable == void.class) {
            return VOID_TYPE_INDEX;
        } else if(provable == byte.class || provable == short.class) {
            return 0; // never needs a guard, as it's assignable to int
        } else if(provable == char.class) {
            return CHAR_TYPE_INDEX;
        } else if(provable == float.class) {
            return FLOAT_TYPE_INDEX;
        }
        throw new AssertionError(provable.getName());
    }

    //maps staticallyProvableCallSiteType to actualCallSiteType, throws exception if impossible
    @SuppressWarnings("unused")
    private static int ensureInt(final long arg, final int programPoint) {
        if (JSType.isRepresentableAsInt(arg)) {
            return (int)arg;
        }
        throw new UnwarrantedOptimismException(arg, programPoint);
    }

    @SuppressWarnings("unused")
    private static int ensureInt(final double arg, final int programPoint) {
        if (JSType.isRepresentableAsInt(arg) && !JSType.isNegativeZero(arg)) {
            return (int)arg;
        }
        throw new UnwarrantedOptimismException(arg, programPoint);
    }

    /**
     * Returns the argument value as an int. If the argument is not a wrapper for a primitive numeric type
     * with a value that can be exactly represented as an int, throw an {@link UnwarrantedOptimismException}.
     * This method is only public so that generated script code can use it. See {code CodeGenerator.ENSURE_INT}.
     * @param arg the original argument.
     * @param programPoint the program point used in the exception
     * @return the value of the argument as an int.
     * @throws UnwarrantedOptimismException if the argument is not a wrapper for a primitive numeric type with
     * a value that can be exactly represented as an int.
     */
    public static int ensureInt(final Object arg, final int programPoint) {
        // NOTE: this doesn't delegate to ensureInt(double, int) as in that case if arg were a Long, it would throw a
        // (potentially imprecise) Double in the UnwarrantedOptimismException. This way, it will put the correct valued
        // Long into the exception.
        if (isPrimitiveNumberWrapper(arg)) {
            final double d = ((Number)arg).doubleValue();
            if (JSType.isRepresentableAsInt(d) && !JSType.isNegativeZero(d)) {
                return (int)d;
            }
        }
        throw new UnwarrantedOptimismException(arg, programPoint);
    }

    private static boolean isPrimitiveNumberWrapper(final Object obj) {
        if (obj == null) {
            return false;
        }
        final Class<?> c = obj.getClass();
        return c == Integer.class || c == Double.class || c == Long.class ||
               c ==   Float.class || c ==  Short.class || c == Byte.class;
    }

    @SuppressWarnings("unused")
    private static int ensureInt(final boolean arg, final int programPoint) {
        throw new UnwarrantedOptimismException(arg, programPoint, Type.OBJECT);
    }

    @SuppressWarnings("unused")
    private static int ensureInt(final char arg, final int programPoint) {
        throw new UnwarrantedOptimismException(arg, programPoint, Type.OBJECT);
    }

    @SuppressWarnings("unused")
    private static int ensureInt(final int programPoint) {
        // Turns a void into UNDEFINED
        throw new UnwarrantedOptimismException(ScriptRuntime.UNDEFINED, programPoint, Type.OBJECT);
    }

    private static long ensureLong(final double arg, final int programPoint) {
        if (JSType.isRepresentableAsLong(arg) && !JSType.isNegativeZero(arg)) {
            return (long)arg;
        }
        throw new UnwarrantedOptimismException(arg, programPoint);
    }

    /**
     * Returns the argument value as a long. If the argument is not a wrapper for a primitive numeric type
     * with a value that can be exactly represented as a long, throw an {@link UnwarrantedOptimismException}.
     * This method is only public so that generated script code can use it. See {code CodeGenerator.ENSURE_LONG}.
     * @param arg the original argument.
     * @param programPoint the program point used in the exception
     * @return the value of the argument as a long.
     * @throws UnwarrantedOptimismException if the argument is not a wrapper for a primitive numeric type with
     * a value that can be exactly represented as a long
     */
    public static long ensureLong(final Object arg, final int programPoint) {
        if (arg != null) {
            final Class<?> c = arg.getClass();
            if (c == Long.class) {
                // Must check for Long separately, as Long.doubleValue() isn't precise.
                return ((Long)arg).longValue();
            } else if (c == Integer.class || c == Double.class || c == Float.class || c == Short.class ||
                    c == Byte.class) {
                return ensureLong(((Number)arg).doubleValue(), programPoint);
            }
        }
        throw new UnwarrantedOptimismException(arg, programPoint);
    }

    /**
     * Returns the argument value as a double. If the argument is not a a wrapper for a primitive numeric type
     * throw an {@link UnwarrantedOptimismException}.This method is only public so that generated script code
     * can use it. See {code CodeGenerator.ENSURE_NUMBER}.
     * @param arg the original argument.
     * @param programPoint the program point used in the exception
     * @return the value of the argument as a double.
     * @throws UnwarrantedOptimismException if the argument is not a wrapper for a primitive numeric type.
     */
    public static double ensureNumber(final Object arg, final int programPoint) {
        if (isPrimitiveNumberWrapper(arg)) {
            return ((Number)arg).doubleValue();
        }
        throw new UnwarrantedOptimismException(arg, programPoint);
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), OptimisticReturnFilters.class, name, MH.type(rtype, types));
    }
}
