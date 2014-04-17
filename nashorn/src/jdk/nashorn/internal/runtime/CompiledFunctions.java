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

import java.lang.invoke.MethodType;
import java.util.LinkedList;

/**
 * This is a list of code versions of a function.
 * The list is sorted in ascending order of generic descriptors
 */
final class CompiledFunctions {

    private final String name;
    final LinkedList<CompiledFunction> functions = new LinkedList<>();

    CompiledFunctions(final String name) {
        this.name = name;
    }

    void add(final CompiledFunction f) {
        functions.add(f);
    }

    void addAll(final CompiledFunctions fs) {
        functions.addAll(fs.functions);
    }

    boolean isEmpty() {
        return functions.isEmpty();
    }

    int size() {
        return functions.size();
    }

    @Override
    public String toString() {
        return '\'' + name + "' code=" + functions;
    }

    /**
     * Used to find an apply to call version that fits this callsite.
     * We cannot just, as in the normal matcher case, return e.g. (Object, Object, int)
     * for (Object, Object, int, int, int) or we will destroy the semantics and get
     * a function that, when padded with undefineds, behaves differently
     * @param type actual call site type
     * @return apply to call that perfectly fits this callsite or null if none found
     */
    CompiledFunction lookupExactApplyToCall(final MethodType type) {
        for (final CompiledFunction cf : functions) {
            if (!cf.isApplyToCall()) {
                continue;
            }

            final MethodType cftype = cf.type();
            if (cftype.parameterCount() != type.parameterCount()) {
                continue;
            }

            final Class<?>[] paramTypes = new Class<?>[cftype.parameterCount()];
            for (int i = 0; i < cftype.parameterCount(); i++) {
                paramTypes[i] = cftype.parameterType(i).isPrimitive() ? cftype.parameterType(i) : Object.class;
            }

            if (MH.type(cftype.returnType(), paramTypes).equals(type)) {
                return cf;
            }
        }

        return null;
    }

    private CompiledFunction pick(final MethodType callSiteType, final boolean canPickVarArg) {
        for (final CompiledFunction candidate : functions) {
            if (candidate.matchesCallSite(callSiteType, false)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Returns the compiled function best matching the requested call site method type
     * @param callSiteType
     * @param recompilable
     * @param hasThis
     * @return
     */
    CompiledFunction best(final MethodType callSiteType, final boolean recompilable) {
        assert callSiteType.parameterCount() >= 2 : callSiteType; // Must have at least (callee, this)
        assert callSiteType.parameterType(0).isAssignableFrom(ScriptFunction.class) : callSiteType; // Callee must be assignable from script function

        if (recompilable) {
            final CompiledFunction candidate = pick(callSiteType, false);
            if (candidate != null) {
                return candidate;
            }
            return pick(callSiteType, true); //try vararg last
        }

        CompiledFunction best = null;
        for(final CompiledFunction candidate: functions) {
            if(candidate.betterThanFinal(best, callSiteType)) {
                best = candidate;
            }
        }

        return best;
    }

    /**
     * Returns true if functions managed by this {@code CompiledFunctions} require a callee. This method is only safe to
     * be invoked for a {@code CompiledFunctions} that is not empty. As such, it should only be used from
     * {@link FinalScriptFunctionData} and not from {@link RecompilableScriptFunctionData}.
     * @return true if the functions need a callee, false otherwise.
     */
    boolean needsCallee() {
        final boolean needsCallee = functions.getFirst().needsCallee();
        assert allNeedCallee(needsCallee);
        return needsCallee;
    }

    private boolean allNeedCallee(final boolean needCallee) {
        for (final CompiledFunction inv : functions) {
            if(inv.needsCallee() != needCallee) {
                return false;
            }
        }
        return true;
    }

    /**
     * If this CompiledFunctions object belongs to a {@code FinalScriptFunctionData}, get a method type for a generic
     * invoker. It will either be a vararg type, if any of the contained functions is vararg, or a generic type of the
     * arity of the largest arity of all functions.
     * @return the method type for the generic invoker
     */
    MethodType getFinalGenericType() {
        int max = 0;
        for(final CompiledFunction fn: functions) {
            final MethodType t = fn.type();
            if(ScriptFunctionData.isVarArg(t)) {
                // 2 for (callee, this, args[])
                return MethodType.genericMethodType(2, true);
            }
            final int paramCount = t.parameterCount() - (ScriptFunctionData.needsCallee(t) ? 1 : 0);
            if(paramCount > max) {
                max = paramCount;
            }
        }
        // +1 for callee
        return MethodType.genericMethodType(max + 1);
    }

}
