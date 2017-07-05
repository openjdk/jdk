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

import java.util.ArrayList;
import java.util.List;

import static jdk.nashorn.internal.codegen.CompilerConstants.SCOPE;

import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.SplitNode;
import jdk.nashorn.internal.runtime.Scope;

/**
 * Emitter used for splitting methods. Needs to keep track of if there are jump targets
 * outside the current split node. All external jump targets encountered at method
 * emission are logged, and {@code CodeGenerator#leaveSplitNode(SplitNode)} creates
 * an appropriate jump table when the SplitNode has been iterated through
 */
public class SplitMethodEmitter extends MethodEmitter {

    private final SplitNode splitNode;

    private final List<Label> externalTargets = new ArrayList<>();

    SplitMethodEmitter(final ClassEmitter classEmitter, final MethodVisitor mv, SplitNode splitNode) {
        super(classEmitter, mv);
        this.splitNode = splitNode;
    }

    @Override
    void splitAwareGoto(final LexicalContext lc, final Label label) {
        assert splitNode != null;
        final int index = findExternalTarget(lc, label);
        if (index >= 0) {
            loadCompilerConstant(SCOPE);
            checkcast(Scope.class);
            load(index + 1);
            invoke(Scope.SET_SPLIT_STATE);
            loadUndefined(Type.OBJECT);
            _return(functionNode.getReturnType());
            return;
        }
        super.splitAwareGoto(lc, label);
    }

    private int findExternalTarget(final LexicalContext lc, final Label label) {
        final int index = externalTargets.indexOf(label);

        if (index >= 0) {
            return index;
        }

        if (lc.isExternalTarget(splitNode, label)) {
            externalTargets.add(label);
            return externalTargets.size() - 1;
        }
        return -1;
    }

    @Override
    MethodEmitter registerReturn() {
        setHasReturn();
        loadCompilerConstant(SCOPE);
        checkcast(Scope.class);
        load(0);
        invoke(Scope.SET_SPLIT_STATE);
        return this;
    }

    @Override
    final List<Label> getExternalTargets() {
        return externalTargets;
    }
}
