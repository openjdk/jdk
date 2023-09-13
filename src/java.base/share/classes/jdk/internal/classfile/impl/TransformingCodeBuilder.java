/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.classfile.CodeModel;
import java.util.Optional;
import java.util.function.Consumer;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.classfile.constantpool.ConstantPoolBuilder;

public final class TransformingCodeBuilder implements TerminalCodeBuilder {

    final CodeBuilder delegate;
    final Consumer<CodeElement> consumer;

    public TransformingCodeBuilder(CodeBuilder delegate, Consumer<CodeElement> consumer) {
        this.delegate = delegate;
        this.consumer = consumer;
    }

    @Override
    public CodeBuilder with(CodeElement e) {
        consumer.accept(e);
        return this;
    }

    @Override
    public Optional<CodeModel> original() {
        return delegate.original();
    }

    @Override
    public Label newLabel() {
        return delegate.newLabel();
    }

    @Override
    public Label startLabel() {
        return delegate.startLabel();
    }

    @Override
    public Label endLabel() {
        return delegate.endLabel();
    }

    @Override
    public int receiverSlot() {
        return delegate.receiverSlot();
    }

    @Override
    public int parameterSlot(int paramNo) {
        return delegate.parameterSlot(paramNo);
    }

    @Override
    public int allocateLocal(TypeKind typeKind) {
        return delegate.allocateLocal(typeKind);
    }

    @Override
    public ConstantPoolBuilder constantPool() {
        return delegate.constantPool();
    }
}
