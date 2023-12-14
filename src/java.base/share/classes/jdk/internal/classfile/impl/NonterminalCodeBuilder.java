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

import java.util.Optional;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Label;
import java.lang.classfile.constantpool.ConstantPoolBuilder;

public abstract sealed class NonterminalCodeBuilder implements CodeBuilder
    permits ChainedCodeBuilder, BlockCodeBuilderImpl {
    protected final TerminalCodeBuilder terminal;
    protected final CodeBuilder parent;

    public NonterminalCodeBuilder(CodeBuilder parent) {
        this.parent = parent;
        this.terminal = switch (parent) {
            case NonterminalCodeBuilder cb -> cb.terminal;
            case TerminalCodeBuilder cb -> cb;
        };
    }

    @Override
    public int receiverSlot() {
        return terminal.receiverSlot();
    }

    @Override
    public int parameterSlot(int paramNo) {
        return terminal.parameterSlot(paramNo);
    }

    @Override
    public ConstantPoolBuilder constantPool() {
        return terminal.constantPool();
    }

    @Override
    public Optional<CodeModel> original() {
        return terminal.original();
    }

    @Override
    public Label newLabel() {
        return terminal.newLabel();
    }
}
