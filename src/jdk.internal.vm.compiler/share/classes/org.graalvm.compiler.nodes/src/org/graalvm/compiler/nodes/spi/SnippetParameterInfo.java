/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.nodes.spi;

import static jdk.vm.ci.services.Services.IS_BUILDING_NATIVE_IMAGE;

import org.graalvm.compiler.api.replacements.Snippet;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Metadata required for processing of snippets.
 */
public class SnippetParameterInfo {

    public SnippetParameterInfo(ResolvedJavaMethod method) {
        assert method.getAnnotation(Snippet.class) != null : method + " must be annotated with @" + Snippet.class.getSimpleName();
        int parameterCount = method.getSignature().getParameterCount(method.hasReceiver());
        if (parameterCount > Integer.SIZE) {
            throw new UnsupportedOperationException("too many arguments");
        }
        this.count = parameterCount;
        int constant = 0;
        int varargs = 0;
        int nonNull = 0;
        int offset = method.hasReceiver() ? 1 : 0;
        for (int i = offset; i < count; i++) {
            int bit = 1 << i;
            if (method.getParameterAnnotation(Snippet.ConstantParameter.class, i - offset) != null) {
                constant |= bit;
            }
            if (method.getParameterAnnotation(Snippet.VarargsParameter.class, i - offset) != null) {
                varargs |= bit;
            }
            if (method.getParameterAnnotation(Snippet.NonNullParameter.class, i - offset) != null) {
                nonNull |= bit;
            }

            assert ((constant & bit) == 0) || ((varargs & bit) == 0) : "Parameter cannot be annotated with both @" + Snippet.ConstantParameter.class.getSimpleName() + " and @" +
                            Snippet.VarargsParameter.class.getSimpleName();
        }
        if (method.hasReceiver()) {
            // Receiver must be constant.
            assert (constant & 1) == 0;
            constant |= 1;
        }
        this.constantParametersBits = constant;
        this.varargsParametersBits = varargs;
        this.nonNullParametersBits = nonNull;

        if (IS_BUILDING_NATIVE_IMAGE) {
            // Capture the names during image building in case the image wants them.
            initNames(method, count);
        } else {
            // Retrieve the names only when assertions are turned on.
            assert initNames(method, count);
        }
    }

    private final int count;
    private final int constantParametersBits;
    private final int varargsParametersBits;
    private final int nonNullParametersBits;

    public int getParameterCount() {
        return count;
    }

    public boolean isConstantParameter(int paramIdx) {
        return ((constantParametersBits >>> paramIdx) & 1) != 0;
    }

    public boolean isVarargsParameter(int paramIdx) {
        return ((varargsParametersBits >>> paramIdx) & 1) != 0;
    }

    public boolean isNonNullParameter(int paramIdx) {
        return ((nonNullParametersBits >>> paramIdx) & 1) != 0;
    }

    public String getParameterName(int paramIdx) {
        if (names != null) {
            return names[paramIdx];
        }
        return null;
    }

    /**
     * The parameter names, taken from the local variables table. Only used for assertion checking,
     * so use only within an assert statement.
     */
    String[] names;

    private boolean initNames(ResolvedJavaMethod method, int parameterCount) {
        names = new String[parameterCount];
        int offset = 0;
        if (method.hasReceiver()) {
            names[0] = "this";
            offset = 1;
        }
        ResolvedJavaMethod.Parameter[] params = method.getParameters();
        if (params != null) {
            for (int i = offset; i < names.length; i++) {
                if (params[i - offset].isNamePresent()) {
                    names[i] = params[i - offset].getName();
                }
            }
        } else {
            int slotIdx = 0;
            LocalVariableTable localVariableTable = method.getLocalVariableTable();
            if (localVariableTable != null) {
                for (int i = 0; i < names.length; i++) {
                    Local local = localVariableTable.getLocal(slotIdx, 0);
                    if (local != null) {
                        names[i] = local.getName();
                    }
                    JavaKind kind = method.getSignature().getParameterKind(i);
                    slotIdx += kind.getSlotCount();
                }
            }
        }
        return true;
    }

    public void clearNames() {
        names = null;
    }
}
