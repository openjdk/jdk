/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.impl;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.TypeKind;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ExceptionCatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class BufferedCodeBuilder
        implements TerminalCodeBuilder {
    private final SplitConstantPool constantPool;
    private final ClassFileImpl context;
    private final List<CodeElement> elements = new ArrayList<>();
    private final LabelImpl startLabel, endLabel;
    private final MethodInfo methodInfo;
    private boolean finished;
    private int maxLocals;

    public BufferedCodeBuilder(MethodInfo methodInfo,
                               SplitConstantPool constantPool,
                               ClassFileImpl context,
                               CodeModel original) {
        this.constantPool = constantPool;
        this.context = context;
        this.startLabel = new LabelImpl(this, -1);
        this.endLabel = new LabelImpl(this, -1);
        this.methodInfo = methodInfo;
        this.maxLocals = TerminalCodeBuilder.setupTopLocal(methodInfo, original);
        elements.add(startLabel);
    }

    @Override
    public Label newLabel() {
        return new LabelImpl(this, -1);
    }

    @Override
    public Label startLabel() {
        return startLabel;
    }

    @Override
    public Label endLabel() {
        return endLabel;
    }

    @Override
    public int receiverSlot() {
        return methodInfo.receiverSlot();
    }

    @Override
    public int parameterSlot(int paramNo) {
        return methodInfo.parameterSlot(paramNo);
    }

    public int curTopLocal() {
        return maxLocals;
    }

    @Override
    public int allocateLocal(TypeKind typeKind) {
        int retVal = maxLocals;
        maxLocals += typeKind.slotSize();
        return retVal;
    }

    @Override
    public Label getLabel(int bci) {
        throw new UnsupportedOperationException("Lookup by BCI not supported by BufferedCodeBuilder");
    }

    @Override
    public int labelToBci(Label label) {
        throw new UnsupportedOperationException("Label mapping not supported by BufferedCodeBuilder");
    }

    @Override
    public void setLabelTarget(Label label, int bci) {
        throw new UnsupportedOperationException("Label mapping not supported by BufferedCodeBuilder");
    }

    @Override
    public ConstantPoolBuilder constantPool() {
        return constantPool;
    }

    @Override
    public CodeBuilder with(CodeElement element) {
        if (finished)
            throw new IllegalStateException("Can't add elements after traversal");
        elements.add(element);
        return this;
    }

    @Override
    public String toString() {
        return String.format("CodeModel[id=%d]", System.identityHashCode(this));
    }

    public BufferedCodeBuilder run(Consumer<? super CodeBuilder> handler) {
        handler.accept(this);
        return this;
    }

    public CodeModel toModel() {
        if (!finished) {
            elements.add(endLabel);
            finished = true;
        }
        return new Model();
    }

    public final class Model
            extends AbstractUnboundModel<CodeElement>
            implements CodeModel {

        private Model() {
            super(BufferedCodeBuilder.this.elements);
        }

        @Override
        public List<ExceptionCatch> exceptionHandlers() {
            return elements.stream()
                           .<ExceptionCatch>mapMulti((x, sink) -> {
                               if (x instanceof ExceptionCatch ec) {
                                   sink.accept(ec);
                               }
                           })
                           .toList();
        }

        int curTopLocal() {
            return BufferedCodeBuilder.this.curTopLocal();
        }

        @Override
        public Optional<MethodModel> parent() {
            return Optional.empty();
        }

        @Override
        public void writeTo(DirectMethodBuilder builder) {
            builder.withCode(new Consumer<>() {
                @Override
                public void accept(CodeBuilder cb) {
                    forEach(cb);
                }
            });
        }

        @Override
        public String toString() {
            return String.format("CodeModel[id=%s]", Integer.toHexString(System.identityHashCode(this)));
        }
    }
}
