/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.gen;

import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.StandardOp.SaveRegistersOp;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

public interface DiagnosticLIRGeneratorTool {
    LIRInstruction createBenchmarkCounter(String name, String group, Value increment);

    LIRInstruction createMultiBenchmarkCounter(String[] names, String[] groups, Value[] increments);

    /**
     * Creates a {@link SaveRegistersOp} that fills a given set of registers with known garbage
     * value.
     *
     * The set of registers actually touched might be {@link SaveRegistersOp#remove reduced} later.
     *
     * @param zappedRegisters registers to be zapped
     * @param zapValues values used for zapping
     *
     * @see DiagnosticLIRGeneratorTool#createZapRegisters()
     */
    SaveRegistersOp createZapRegisters(Register[] zappedRegisters, JavaConstant[] zapValues);

    /**
     * Creates a {@link SaveRegistersOp} that fills all
     * {@link RegisterConfig#getAllocatableRegisters() allocatable registers} with a
     * {@link LIRGenerator#zapValueForKind known garbage value}.
     *
     * The set of registers actually touched might be {@link SaveRegistersOp#remove reduced} later.
     *
     * @see DiagnosticLIRGeneratorTool#createZapRegisters(Register[], JavaConstant[])
     */
    SaveRegistersOp createZapRegisters();

    /**
     * Marker interface for {@link LIRInstruction instructions} that should be succeeded with a
     * {@link DiagnosticLIRGeneratorTool#createZapRegisters() ZapRegisterOp} if assertions are
     * enabled.
     */
    interface ZapRegistersAfterInstruction {
    }

    /**
     * Marker interface for {@link LIRInstruction instructions} that should be preceded with a
     * {@link DiagnosticLIRGeneratorTool#zapArgumentSpace ZapArgumentSpaceOp} if assertions are
     * enabled.
     */
    interface ZapStackArgumentSpaceBeforeInstruction {
    }

    LIRInstruction createZapArgumentSpace(StackSlot[] zappedStack, JavaConstant[] zapValues);

    LIRInstruction zapArgumentSpace();
}
