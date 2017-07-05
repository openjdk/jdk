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
import java.util.List;

/**
 * This is a subclass that represents a script function that may not be regenerated.
 * This is used for example for bound functions and builtins.
 */
final class FinalScriptFunctionData extends ScriptFunctionData {

    private static final long serialVersionUID = -930632846167768864L;

    /**
     * Constructor - used for bind
     *
     * @param name      name
     * @param arity     arity
     * @param functions precompiled code
     * @param flags     {@link ScriptFunctionData} flags
     */
    FinalScriptFunctionData(final String name, final int arity, final List<CompiledFunction> functions, final int flags) {
        super(name, arity, flags);
        code.addAll(functions);
        assert !needsCallee();
    }

    /**
     * Constructor - used from ScriptFunction. This assumes that we have code already for the
     * method (typically a native method) and possibly specializations.
     *
     * @param name  name
     * @param mh    method handle for generic version of method
     * @param specs specializations
     * @param flags {@link ScriptFunctionData} flags
     */
    FinalScriptFunctionData(final String name, final MethodHandle mh, final MethodHandle[] specs, final int flags) {
        super(name, methodHandleArity(mh), flags);

        addInvoker(mh);
        if (specs != null) {
            for (final MethodHandle spec : specs) {
                addInvoker(spec);
            }
        }
    }

    @Override
    boolean isRecompilable() {
        return false;
    }

    @Override
    protected boolean needsCallee() {
        final boolean needsCallee = code.getFirst().needsCallee();
        assert allNeedCallee(needsCallee);
        return needsCallee;
    }

    private boolean allNeedCallee(final boolean needCallee) {
        for (final CompiledFunction inv : code) {
            if(inv.needsCallee() != needCallee) {
                return false;
            }
        }
        return true;
    }

    @Override
    MethodType getGenericType() {
        // We need to ask the code for its generic type. We can't just rely on this function data's arity, as it's not
        // actually correct for lots of built-ins. E.g. ECMAScript 5.1 section 15.5.3.2 prescribes that
        // Script.fromCharCode([char0[, char1[, ...]]]) has a declared arity of 1 even though it's a variable arity
        // method.
        int max = 0;
        for(final CompiledFunction fn: code) {
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

    private void addInvoker(final MethodHandle mh) {
        assert !needsCallee(mh);
        if (isConstructor(mh)) {
            // only nasgen constructors: (boolean, self, args) are subject to binding a boolean newObj. isConstructor
            // is too conservative a check. However, isConstructor(mh) always implies isConstructor param
            assert isConstructor();
            code.add(CompiledFunction.createBuiltInConstructor(mh));
        } else {
            code.add(new CompiledFunction(mh));
        }
    }

    private static int methodHandleArity(final MethodHandle mh) {
        if (isVarArg(mh)) {
            return MAX_ARITY;
        }

        //drop self, callee and boolean constructor flag to get real arity
        return mh.type().parameterCount() - 1 - (needsCallee(mh) ? 1 : 0) - (isConstructor(mh) ? 1 : 0);
    }

    private static boolean isConstructor(final MethodHandle mh) {
        return mh.type().parameterCount() >= 1 && mh.type().parameterType(0) == boolean.class;
    }

}
