/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.constopt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.StandardOp.LoadConstantOp;
import org.graalvm.compiler.lir.Variable;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;

/**
 * Represents def-use tree of a constant.
 */
class DefUseTree {
    private final LoadConstantOp instruction;
    private final AbstractBlockBase<?> block;
    private final List<UseEntry> uses;

    DefUseTree(LIRInstruction instruction, AbstractBlockBase<?> block) {
        assert LoadConstantOp.isLoadConstantOp(instruction) : "Not a LoadConstantOp: " + instruction;
        this.instruction = LoadConstantOp.asLoadConstantOp(instruction);
        this.block = block;
        this.uses = new ArrayList<>();
    }

    public Variable getVariable() {
        return (Variable) instruction.getResult();
    }

    public Constant getConstant() {
        return instruction.getConstant();
    }

    public LIRInstruction getInstruction() {
        return (LIRInstruction) instruction;
    }

    public AbstractBlockBase<?> getBlock() {
        return block;
    }

    @Override
    public String toString() {
        return "DefUseTree [" + instruction + "|" + block + "," + uses + "]";
    }

    public void addUsage(AbstractBlockBase<?> b, LIRInstruction inst, Value value) {
        uses.add(new UseEntry(b, inst, value));
    }

    public int usageCount() {
        return uses.size();
    }

    public void forEach(Consumer<? super UseEntry> action) {
        uses.forEach(action);
    }

}
