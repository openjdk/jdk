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

package jdk.nashorn.internal.codegen;

import static jdk.nashorn.internal.codegen.CompilerConstants.staticCallNoLookup;
import static jdk.nashorn.internal.codegen.types.Type.BOOLEAN;
import static jdk.nashorn.internal.codegen.types.Type.INT;
import static jdk.nashorn.internal.lookup.Lookup.MH;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.HashMap;
import java.util.Map;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.RuntimeNode.Request;
import jdk.nashorn.internal.lookup.Lookup;
import jdk.nashorn.internal.lookup.MethodHandleFactory;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.linker.Bootstrap;

/**
 * Optimistic call site that assumes its Object arguments to be of a boxed type.
 * Gradually reverts to wider boxed types if the assumption for the RuntimeNode
 * is proven wrong. Finally reverts to the generic ScriptRuntime method.
 *
 * This is used from the CodeGenerator when we have a runtime node, but 1 or more
 * primitive arguments. This class generated appropriate specializations, for example
 * {@code Object a === int b} is a good idea to specialize to {@code ((Integer)a).intValue() == b}
 * surrounded by catch blocks that will try less narrow specializations
 */
public final class RuntimeCallSite extends MutableCallSite {
    static final Call BOOTSTRAP = staticCallNoLookup(Bootstrap.class, "runtimeBootstrap", CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class);

    private static final MethodHandle NEXT = findOwnMH("next",  MethodHandle.class, String.class);

    private final RuntimeNode.Request request;

    /**
     * A specialized runtime node, i.e. on where we know at least one more specific type than object
     */
    static final class SpecializedRuntimeNode {
        private static final char REQUEST_SEPARATOR = ':';

        private final RuntimeNode.Request request;

        private final Type[] parameterTypes;

        private final Type   returnType;

        /**
         * Constructor.
         *
         * @param request        runtime node request to specialize
         * @param parameterTypes parameter types of the call site
         * @param returnType     return type of the call site
         */
        SpecializedRuntimeNode(final RuntimeNode.Request request, final Type[] parameterTypes, final Type returnType) {
            this.request        = request;
            this.parameterTypes = parameterTypes;
            this.returnType     = returnType;
        }

        /**
         * The first type to try to use for this genrated runtime node
         *
         * @return a type
         */
        public Type firstTypeGuess() {
            Type widest = Type.UNKNOWN;
            for (final Type type : parameterTypes) {
                if (type.isObject()) {
                    continue;
                }
                widest = Type.widest(type, widest);
            }
            widest = Type.widest(widest, firstTypeGuessForObject(request));

            return widest;
        }

        private static Type firstTypeGuessForObject(final Request request) {
            switch (request) {
            case ADD:
                return INT;
            default:
                return BOOLEAN;
            }
        }

        Request getRequest() {
            return request;
        }

        Type[] getParameterTypes() {
            return parameterTypes;
        }

        Type getReturnType() {
            return returnType;
        }

        private static char descFor(final Type type) {
            if (type.isObject()) {
                return 'O';
            }
            return type.getDescriptor().charAt(0);
        }

        @Override
        public boolean equals(final Object other) {
            if (other instanceof SpecializedRuntimeNode) {
                final SpecializedRuntimeNode otherNode = (SpecializedRuntimeNode)other;

                if (!otherNode.getReturnType().equals(getReturnType())) {
                    return false;
                }

                if (getParameterTypes().length != otherNode.getParameterTypes().length) {
                    return false;
                }

                for (int i = 0; i < getParameterTypes().length; i++) {
                    if (!Type.areEquivalent(getParameterTypes()[i], otherNode.getParameterTypes()[i])) {
                        return false;
                    }
                }

                return otherNode.getRequest().equals(getRequest());
            }

            return false;
        }

        @Override
        public int hashCode() {
            int hashCode = getRequest().toString().hashCode();
            hashCode ^= getReturnType().hashCode();
            for (final Type type : getParameterTypes()) {
                hashCode ^= type.hashCode();
            }
            return hashCode;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append(getRequest().toString());
            sb.append(REQUEST_SEPARATOR);
            sb.append(descFor(getReturnType()));

            for (final Type type : getParameterTypes()) {
                sb.append(descFor(type));
            }

            return sb.toString();
        }

        String getName(final Type extraType) {
            return toString() + "_" + descFor(extraType);
        }

        String getInitialName() {
            return getName(firstTypeGuess());
        }
    }


    /**
     * Constructor
     *
     * @param type method type for call site
     * @param name name of runtime call
     */
    public RuntimeCallSite(final MethodType type, final String name) {
        super(type);
        this.request = Request.valueOf(name.substring(0, name.indexOf(SpecializedRuntimeNode.REQUEST_SEPARATOR)));
        setTarget(makeMethod(name));
    }

    private String nextName(final String requestName) {
        if (requestName.equals(request.toString())) {
            return null;
        }

        final char[] c = requestName.toCharArray();
        final int last = c.length - 1;

        if (c[last - 1] != '_') {
            return null;
        }

        switch (c[last]) {
        case 'Z':
            c[last] = 'I';
            break;
        case 'I':
            c[last] = 'J';
            break;
        case 'J':
            c[last] = 'D';
            break;
        case 'D':
        default:
            return request.toString();
        }

        return new String(c);
    }

    private boolean isSpecialized(final String requestName) {
        return nextName(requestName) != null;
    }

    private MethodHandle makeMethod(final String requestName) {
        MethodHandle mh;

        if (isSpecialized(requestName)) {
            final Class<?> boxedType;
            final Class<?> primitiveType;

            switch (requestName.charAt(requestName.length() - 1)) {
            case 'Z':
                boxedType = Boolean.class;
                primitiveType = int.class;
                break;
            case 'I':
                boxedType = Integer.class;
                primitiveType = int.class;
                break;
            case 'J':
                boxedType = Long.class;
                primitiveType = long.class;
                break;
            case 'D':
                boxedType = Number.class;
                primitiveType = double.class;
                break;
            default:
                throw new RuntimeException("should not reach here");
            }

            final boolean isStrictCmp = (request == Request.EQ_STRICT || request == Request.NE_STRICT);

            if (isStrictCmp &&
                    (boxedType != Boolean.class &&
                        (type().parameterType(0) == boolean.class ||
                         type().parameterType(1) == boolean.class))) {
                // number and boolean are never strictly equal, e.g. 0 !== false
                mh = MH.dropArguments(MH.constant(boolean.class, request == Request.NE_STRICT), 0, type().parameterArray());
            } else {
                mh = METHODS.get(request.nonStrictName() + primitiveType.getSimpleName());
                // unbox objects

                for (int i = 0; i < type().parameterCount(); i++) {
                    if (!type().parameterType(i).isPrimitive()) {
                        mh = MH.filterArguments(mh, i, UNBOX.get(boxedType));
                    }
                }

                mh = Lookup.filterReturnType(mh, type().returnType());
                mh = MH.explicitCastArguments(mh, type());
            }

            final MethodHandle fallback = MH.foldArguments(MethodHandles.exactInvoker(type()), MH.insertArguments(NEXT, 0, this, requestName));

            MethodHandle guard;
            if (type().parameterType(0).isPrimitive()) {
                guard = MH.insertArguments(
                            MH.dropArguments(CHECKCAST, 1, type().parameterType(0)), 0, boxedType);
            } else if (type().parameterType(1).isPrimitive()) {
                guard = MH.insertArguments(
                            MH.dropArguments(CHECKCAST, 2, type().parameterType(1)), 0, boxedType);
            } else {
                assert !type().parameterType(0).isPrimitive() && !type().parameterType(1).isPrimitive();
                guard = MH.insertArguments(CHECKCAST2, 0, boxedType);
            }

            if (request == Request.ADD && boxedType == Integer.class) {
                // int add needs additional overflow check
                MethodHandle addcheck = ADDCHECK;
                for (int i = 0; i < type().parameterCount(); i++) {
                    if (!type().parameterType(i).isPrimitive()) {
                        addcheck = MH.filterArguments(addcheck, i, UNBOX.get(boxedType));
                    }
                }
                addcheck = MH.explicitCastArguments(addcheck, type().changeReturnType(boolean.class));
                guard    = MH.guardWithTest(upcastGuard(guard), addcheck,
                                MH.dropArguments(MH.constant(boolean.class, false), 0, type().parameterArray()));
            }

            return MH.guardWithTest(upcastGuard(guard), mh, fallback);
        }

        // generic fallback
        return MH.explicitCastArguments(Lookup.filterReturnType(GENERIC_METHODS.get(request.name()), type().returnType()), type());
    }

    private MethodHandle upcastGuard(final MethodHandle guard) {
        return MH.asType(guard, type().changeReturnType(boolean.class));
    }

    /**
     * This is public just so that the generated specialization code can
     * use it to get the next wider typed method
     *
     * Do not call directly
     *
     * @param name current name (with type) of runtime call at the call site
     * @return next wider specialization method for this RuntimeCallSite
     */
   public MethodHandle next(final String name) {
        final MethodHandle next = makeMethod(nextName(name));
        setTarget(next);
        return next;
    }

    /** Method cache */
    private static final Map<String, MethodHandle> METHODS;

    /** Generic method cache */
    private static final Map<String, MethodHandle> GENERIC_METHODS;

    /** Unbox cache */
    private static final Map<Class<?>, MethodHandle> UNBOX;

    private static final MethodHandle CHECKCAST  = findOwnMH("checkcast", boolean.class, Class.class, Object.class);
    private static final MethodHandle CHECKCAST2 = findOwnMH("checkcast", boolean.class, Class.class, Object.class, Object.class);
    private static final MethodHandle ADDCHECK   = findOwnMH("ADDcheck",  boolean.class, int.class, int.class);

    /**
     * Build maps of correct boxing operations
     */
    static {
        UNBOX = new HashMap<>();
        UNBOX.put(Boolean.class, findOwnMH("unboxZ", int.class, Object.class));
        UNBOX.put(Integer.class, findOwnMH("unboxI", int.class, Object.class));
        UNBOX.put(Long.class,    findOwnMH("unboxJ", long.class, Object.class));
        UNBOX.put(Number.class,  findOwnMH("unboxD", double.class, Object.class));

        METHODS = new HashMap<>();

        for (final Request req : Request.values()) {
            if (req.canSpecialize()) {
                if (req.name().endsWith("_STRICT")) {
                    continue;
                }

                final boolean isCmp = Request.isComparison(req);

                METHODS.put(req.name() + "int",    findOwnMH(req.name(), (isCmp ? boolean.class : int.class),  int.class, int.class));
                METHODS.put(req.name() + "long",   findOwnMH(req.name(), (isCmp ? boolean.class : long.class), long.class, long.class));
                METHODS.put(req.name() + "double", findOwnMH(req.name(), (isCmp ? boolean.class : double.class), double.class, double.class));
            }
        }

        GENERIC_METHODS = new HashMap<>();
        for (final Request req : Request.values()) {
            if (req.canSpecialize()) {
                GENERIC_METHODS.put(req.name(), MH.findStatic(MethodHandles.lookup(), ScriptRuntime.class, req.name(),
                        MH.type(req.getReturnType().getTypeClass(), Object.class, Object.class)));
            }
        }
    }

    /**
     * Specialized version of != operator for two int arguments. Do not call directly.
     * @param a int
     * @param b int
     * @return a != b
     */
    public static boolean NE(final int a, final int b) {
        return a != b;
    }

    /**
     * Specialized version of != operator for two double arguments. Do not call directly.
     * @param a double
     * @param b double
     * @return a != b
     */
    public static boolean NE(final double a, final double b) {
        return a != b;
    }

    /**
     * Specialized version of != operator for two long arguments. Do not call directly.
     * @param a long
     * @param b long
     * @return a != b
     */
    public static boolean NE(final long a, final long b) {
        return a != b;
    }

    /**
     * Specialized version of == operator for two int arguments. Do not call directly.
     * @param a int
     * @param b int
     * @return a == b
     */
    public static boolean EQ(final int a, final int b) {
        return a == b;
    }

    /**
     * Specialized version of == operator for two double arguments. Do not call directly.
     * @param a double
     * @param b double
     * @return a == b
     */
    public static boolean EQ(final double a, final double b) {
        return a == b;
    }

    /**
     * Specialized version of == operator for two long arguments. Do not call directly.
     * @param a long
     * @param b long
     * @return a == b
     */
    public static boolean EQ(final long a, final long b) {
        return a == b;
    }

    /**
     * Specialized version of {@literal <} operator for two int arguments. Do not call directly.
     * @param a int
     * @param b int
     * @return a {@code <} b
     */
    public static boolean LT(final int a, final int b) {
        return a < b;
    }

    /**
     * Specialized version of {@literal <} operator for two double arguments. Do not call directly.
     * @param a double
     * @param b double
     * @return a {@literal <} b
     */
    public static boolean LT(final double a, final double b) {
        return a < b;
    }

    /**
     * Specialized version of {@literal <} operator for two long arguments. Do not call directly.
     * @param a long
     * @param b long
     * @return a {@literal <} b
     */
    public static boolean LT(final long a, final long b) {
        return a < b;
    }

    /**
     * Specialized version of {@literal <=} operator for two int arguments. Do not call directly.
     * @param a int
     * @param b int
     * @return a {@literal <=} b
     */
    public static boolean LE(final int a, final int b) {
        return a <= b;
    }

    /**
     * Specialized version of {@literal <=} operator for two double arguments. Do not call directly.
     * @param a double
     * @param b double
     * @return a {@literal <=} b
     */
    public static boolean LE(final double a, final double b) {
        return a <= b;
    }

    /**
     * Specialized version of {@literal <=} operator for two long arguments. Do not call directly.
     * @param a long
     * @param b long
     * @return a {@literal <=} b
     */
    public static boolean LE(final long a, final long b) {
        return a <= b;
    }

    /**
     * Specialized version of {@literal >} operator for two int arguments. Do not call directly.
     * @param a int
     * @param b int
     * @return a {@literal >} b
     */
    public static boolean GT(final int a, final int b) {
        return a > b;
    }

    /**
     * Specialized version of {@literal >} operator for two double arguments. Do not call directly.
     * @param a double
     * @param b double
     * @return a {@literal >} b
     */
    public static boolean GT(final double a, final double b) {
        return a > b;
    }

    /**
     * Specialized version of {@literal >} operator for two long arguments. Do not call directly.
     * @param a long
     * @param b long
     * @return a {@literal >} b
     */
    public static boolean GT(final long a, final long b) {
        return a > b;
    }

    /**
     * Specialized version of {@literal >=} operator for two int arguments. Do not call directly.
     * @param a int
     * @param b int
     * @return a {@literal >=} b
     */
    public static boolean GE(final int a, final int b) {
        return a >= b;
    }

    /**
     * Specialized version of {@literal >=} operator for two double arguments. Do not call directly.
     * @param a double
     * @param b double
     * @return a {@literal >=} b
     */
    public static boolean GE(final double a, final double b) {
        return a >= b;
    }

    /**
     * Specialized version of {@literal >=} operator for two long arguments. Do not call directly.
     * @param a long
     * @param b long
     * @return a {@code >=} b
     */
    public static boolean GE(final long a, final long b) {
        return a >= b;
    }

    /**
     * Specialized version of + operator for two int arguments. Do not call directly.
     * @param a int
     * @param b int
     * @return a + b
     */
    public static int ADD(final int a, final int b) {
        return a + b;
    }

    /**
     * Specialized version of + operator for two long arguments. Do not call directly.
     * @param a long
     * @param b long
     * @return a + b
     */
    public static long ADD(final long a, final long b) {
        return a + b;
    }

    /**
     * Specialized version of + operator for two double arguments. Do not call directly.
     * @param a double
     * @param b double
     * @return a + b
     */
    public static double ADD(final double a, final double b) {
        return a + b;
    }

    /**
     * Check that ints are addition compatible, i.e. their sum is equal to the sum
     * of them cast to long. Otherwise the addition will overflow. Do not call directly.
     *
     * @param a int
     * @param b int
     *
     * @return true if addition does not overflow
     */
    public static boolean ADDcheck(final int a, final int b) {
        return (a + b == (long)a + (long)b);
    }

    /**
     * Checkcast used for specialized ops. Do not call directly
     *
     * @param type to to check against
     * @param obj  object to check for type
     *
     * @return true if type check holds
     */
    public static boolean checkcast(final Class<?> type, final Object obj) {
        return type.isInstance(obj);
    }

    /**
     * Checkcast used for specialized ops. Do not call directly
     *
     * @param type type to check against
     * @param objA first object to check against type
     * @param objB second object to check against type
     *
     * @return true if type check holds for both objects
     */
    public static boolean checkcast(final Class<?> type, final Object objA, final Object objB) {
        return type.isInstance(objA) && type.isInstance(objB);
    }

    /**
     * Unbox a java.lang.Boolean. Do not call directly
     * @param obj object to cast to int and unbox
     * @return an int value for the boolean, 1 is true, 0 is false
     */
    public static int unboxZ(final Object obj) {
        return (boolean)obj ? 1 : 0;
    }

    /**
     * Unbox a java.lang.Integer. Do not call directly
     * @param obj object to cast to int and unbox
     * @return an int
     */
    public static int unboxI(final Object obj) {
        return (int)obj;
    }

    /**
     * Unbox a java.lang.Long. Do not call directly
     * @param obj object to cast to long and unbox
     * @return a long
     */
    public static long unboxJ(final Object obj) {
        return (long)obj;
    }

    /**
     * Unbox a java.lang.Number. Do not call directly
     * @param obj object to cast to Number and unbox
     * @return a double
     */
    public static double unboxD(final Object obj) {
        return ((Number)obj).doubleValue();
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        try {
            return MH.findStatic(MethodHandles.lookup(), RuntimeCallSite.class, name, MH.type(rtype, types));
        } catch (final MethodHandleFactory.LookupException e) {
            return MH.findVirtual(MethodHandles.lookup(), RuntimeCallSite.class, name, MH.type(rtype, types));
        }
    }

}
