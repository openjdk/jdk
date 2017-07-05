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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import jdk.nashorn.internal.codegen.types.Type;

/**
 * An version of a JavaScript function, native or JavaScript.
 * Supports lazily generating a constructor version of the invocation.
 */
final class CompiledFunction implements Comparable<CompiledFunction> {

    /** The method type may be more specific than the invoker, if. e.g.
     *  the invoker is guarded, and a guard with a generic object only
     *  fallback, while the target is more specific, we still need the
     *  more specific type for sorting */
    private final MethodType   type;
    private final MethodHandle invoker;
    private MethodHandle constructor;

    CompiledFunction(final MethodType type, final MethodHandle invoker) {
        this(type, invoker, null);
    }

    CompiledFunction(final MethodType type, final MethodHandle invoker, final MethodHandle constructor) {
        this.type        = type;
        this.invoker     = invoker;
        this.constructor = constructor;
    }

    @Override
    public String toString() {
        return "<callSiteType= " + type + " invoker=" + invoker + " ctor=" + constructor + ">";
    }

    MethodHandle getInvoker() {
        return invoker;
    }

    MethodHandle getConstructor() {
        return constructor;
    }

    void setConstructor(final MethodHandle constructor) {
        this.constructor = constructor;
    }

    boolean hasConstructor() {
        return constructor != null;
    }

    MethodType type() {
        return type;
    }

    @Override
    public int compareTo(final CompiledFunction o) {
        return weight() - o.weight();
    }

    private int weight() {
        return weight(type());
    }

    private static int weight(final MethodType type) {
        if (isVarArgsType(type)) {
            return Integer.MAX_VALUE; //if there is a varargs it should be the heavist and last fallback
        }

        int weight = Type.typeFor(type.returnType()).getWeight();
        for (final Class<?> paramType : type.parameterArray()) {
            final int pweight = Type.typeFor(paramType).getWeight() * 2; //params are more important than call types as return values are always specialized
            weight += pweight;
        }
        return weight;
    }

    private static boolean isVarArgsType(final MethodType type) {
        assert type.parameterCount() >= 1 : type;
        return type.parameterType(type.parameterCount() - 1) == Object[].class;
    }

    boolean moreGenericThan(final CompiledFunction o) {
        return weight() > o.weight();
    }

    boolean moreGenericThan(final MethodType mt) {
        return weight() > weight(mt);
    }

    /**
     * Check whether a given method descriptor is compatible with this invocation.
     * It is compatible if the types are narrower than the invocation type so that
     * a semantically equivalent linkage can be performed.
     *
     * @param mt type to check against
     * @return
     */
    boolean typeCompatible(final MethodType mt) {
        final Class<?>[] wantedParams   = mt.parameterArray();
        final Class<?>[] existingParams = type().parameterArray();

        //if we are not examining a varargs type, the number of parameters must be the same
        if (wantedParams.length != existingParams.length && !isVarArgsType(mt)) {
            return false;
        }

        //we only go as far as the shortest array. the only chance to make this work if
        //parameters lengths do not match is if our type ends with a varargs argument.
        //then every trailing parameter in the given callsite can be folded into it, making
        //us compatible (albeit slower than a direct specialization)
        final int lastParamIndex = Math.min(wantedParams.length, existingParams.length);
        for (int i = 0; i < lastParamIndex; i++) {
            final Type w = Type.typeFor(wantedParams[i]);
            final Type e = Type.typeFor(existingParams[i]);

            //don't specialize on booleans, we have the "true" vs int 1 ambiguity in resolution
            //we also currently don't support boolean as a javascript function callsite type.
            //it will always box.
            if (w.isBoolean()) {
                return false;
            }

            //This callsite type has a vararg here. it will swallow all remaining args.
            //for consistency, check that it's the last argument
            if (e.isArray()) {
                return true;
            }

            //Our arguments must be at least as wide as the wanted one, if not wider
            if (Type.widest(w, e) != e) {
                //e.g. this invocation takes double and callsite says "object". reject. won't fit
                //but if invocation takes a double and callsite says "int" or "long" or "double", that's fine
                return false;
            }
        }

        return true; // anything goes for return type, take the convenient one and it will be upcasted thru dynalink magic.
    }



}
