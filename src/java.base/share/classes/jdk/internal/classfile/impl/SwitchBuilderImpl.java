/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.Label;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import jdk.internal.classfile.CodeBuilder.BlockCodeBuilder;
import jdk.internal.classfile.instruction.SwitchCase;

public final class SwitchBuilderImpl implements CodeBuilder.SwitchBuilder {

    private record HandlerBlock(Label l, Consumer<CodeBuilder.BlockCodeBuilder> h) {}

    final boolean tableSwitch;
    final BlockCodeBuilder b;
    final Set<Integer> values;
    final List<SwitchCase> cases;
    final List<HandlerBlock> handlers;
    Label defaultLabel;
    Consumer<CodeBuilder.BlockCodeBuilder> defaultHandler;

    public SwitchBuilderImpl(BlockCodeBuilder b, boolean tableSwitch) {
        this.b = b;
        this.tableSwitch = tableSwitch;
        this.values = new HashSet<>();
        this.cases = new ArrayList<>();
        this.handlers = new ArrayList<>();
    }

    public void finish() {
        if (defaultLabel == null) {
            defaultLabel = b.breakLabel();
        }
        if (tableSwitch) {
            b.tableswitch(defaultLabel, cases);
        } else {
            b.lookupswitch(defaultLabel, cases);
        }
        for (var h : handlers) {
            b.labelBinding(h.l);
            h.h.accept(b);
        }
    }

    @Override
    public CodeBuilder.SwitchBuilder switchCase(int caseValue, Consumer<CodeBuilder.BlockCodeBuilder> caseHandler) {
        return switchCase(List.of(caseValue), caseHandler);
    }

    @Override
    public CodeBuilder.SwitchBuilder switchCase(List<Integer> caseValues, Consumer<CodeBuilder.BlockCodeBuilder> caseHandler) {
        Objects.requireNonNull(caseValues);
        Objects.requireNonNull(caseHandler);

        var label = b.newLabel();
        handlers.add(new HandlerBlock(label, caseHandler));
        for (int v : caseValues) {
            if (!values.add(v)) {
                throw new IllegalArgumentException("Existing switch case block handles the same value: " + v);
            }
            cases.add(SwitchCase.of(v, label));
        }
        return this;
    }

    @Override
    public CodeBuilder.SwitchBuilder defaultCase(Consumer<CodeBuilder.BlockCodeBuilder> defaultHandler) {
        if (this.defaultHandler != null) {
            throw new IllegalArgumentException("Default switch handler has been already set");
        }
        this.defaultHandler = defaultHandler;
        this.defaultLabel = b.newLabel();
        handlers.add(new HandlerBlock(defaultLabel, defaultHandler));
        return this;
    }
}
