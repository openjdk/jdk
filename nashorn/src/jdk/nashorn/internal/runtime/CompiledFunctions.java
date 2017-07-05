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
import java.util.Iterator;
import java.util.TreeSet;

/**
 * This is a list of code versions of a function.
 * The list is sorted in ascending order of generic descriptors
 */
@SuppressWarnings("serial")
final class CompiledFunctions extends TreeSet<CompiledFunction> {

    private CompiledFunction generic;

    CompiledFunction best(final MethodType type) {
        final Iterator<CompiledFunction> iter = iterator();
        while (iter.hasNext()) {
            final CompiledFunction next = iter.next();
            if (next.typeCompatible(type)) {
                return next;
            }
        }
        return generic();
    }

    boolean needsCallee() {
        return ScriptFunctionData.needsCallee(mostGeneric().getInvoker());
    }

    CompiledFunction mostGeneric() {
        return last();
    }

    CompiledFunction generic() {
        CompiledFunction gen = this.generic;
        if (gen == null) {
            gen = this.generic = makeGeneric(mostGeneric());
        }
        return gen;
    }

    private static CompiledFunction makeGeneric(final CompiledFunction func) {
        final MethodHandle invoker = composeGenericMethod(func.getInvoker());
        final MethodHandle constructor = func.hasConstructor() ? composeGenericMethod(func.getConstructor()) : null;
        return new CompiledFunction(invoker.type(), invoker, constructor);
    }

    /**
     * Takes a method handle, and returns a potentially different method handle that can be used in
     * {@code ScriptFunction#invoke(Object, Object...)} or {code ScriptFunction#construct(Object, Object...)}.
     * The returned method handle will be sure to return {@code Object}, and will have all its parameters turned into
     * {@code Object} as well, except for the following ones:
     * <ul>
     *   <li>a last parameter of type {@code Object[]} which is used for vararg functions,</li>
     *   <li>the first argument, which is forced to be {@link ScriptFunction}, in case the function receives itself
     *   (callee) as an argument.</li>
     * </ul>
     *
     * @param mh the original method handle
     *
     * @return the new handle, conforming to the rules above.
     */
    private static MethodHandle composeGenericMethod(final MethodHandle mh) {
        final MethodType type = mh.type();
        final boolean isVarArg = ScriptFunctionData.isVarArg(mh);
        final int paramCount = isVarArg ? type.parameterCount() - 1 : type.parameterCount();

        MethodType newType = MethodType.genericMethodType(paramCount, isVarArg);

        if (ScriptFunctionData.needsCallee(mh)) {
            newType = newType.changeParameterType(0, ScriptFunction.class);
        }
        return type.equals(newType) ? mh : mh.asType(newType);
    }

    /**
     * Is the given type even more specific than this entire list? That means
     * we have an opportunity for more specific versions of the method
     * through lazy code generation
     *
     * @param type type to check against
     * @return true if the given type is more specific than all invocations available
     */
    boolean isLessSpecificThan(final MethodType type) {
        return best(type).moreGenericThan(type);
    }

}
