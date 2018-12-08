/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.amd64;

import static org.graalvm.compiler.hotspot.HotSpotBackend.Options.GraalArithmeticStubs;
import static org.graalvm.compiler.hotspot.amd64.AMD64HotSpotMathIntrinsicOp.IntrinsicOpcode.COS;
import static org.graalvm.compiler.hotspot.amd64.AMD64HotSpotMathIntrinsicOp.IntrinsicOpcode.LOG;
import static org.graalvm.compiler.hotspot.amd64.AMD64HotSpotMathIntrinsicOp.IntrinsicOpcode.LOG10;
import static org.graalvm.compiler.hotspot.amd64.AMD64HotSpotMathIntrinsicOp.IntrinsicOpcode.SIN;
import static org.graalvm.compiler.hotspot.amd64.AMD64HotSpotMathIntrinsicOp.IntrinsicOpcode.TAN;

import org.graalvm.compiler.core.amd64.AMD64ArithmeticLIRGenerator;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.hotspot.HotSpotBackend.Options;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGenerator;

import jdk.vm.ci.meta.Value;

/**
 * Lowering of selected {@link Math} routines that depends on the value of
 * {@link Options#GraalArithmeticStubs}.
 */
public class AMD64HotSpotMaths implements AMD64ArithmeticLIRGenerator.Maths {

    @Override
    public Variable emitLog(LIRGenerator gen, Value input, boolean base10) {
        if (GraalArithmeticStubs.getValue(gen.getResult().getLIR().getOptions())) {
            return null;
        }
        Variable result = gen.newVariable(LIRKind.combine(input));
        gen.append(new AMD64HotSpotMathIntrinsicOp(base10 ? LOG10 : LOG, result, gen.asAllocatable(input)));
        return result;
    }

    @Override
    public Variable emitCos(LIRGenerator gen, Value input) {
        if (GraalArithmeticStubs.getValue(gen.getResult().getLIR().getOptions())) {
            return null;
        }
        Variable result = gen.newVariable(LIRKind.combine(input));
        gen.append(new AMD64HotSpotMathIntrinsicOp(COS, result, gen.asAllocatable(input)));
        return result;
    }

    @Override
    public Variable emitSin(LIRGenerator gen, Value input) {
        if (GraalArithmeticStubs.getValue(gen.getResult().getLIR().getOptions())) {
            return null;
        }
        Variable result = gen.newVariable(LIRKind.combine(input));
        gen.append(new AMD64HotSpotMathIntrinsicOp(SIN, result, gen.asAllocatable(input)));
        return result;
    }

    @Override
    public Variable emitTan(LIRGenerator gen, Value input) {
        if (GraalArithmeticStubs.getValue(gen.getResult().getLIR().getOptions())) {
            return null;
        }
        Variable result = gen.newVariable(LIRKind.combine(input));
        gen.append(new AMD64HotSpotMathIntrinsicOp(TAN, result, gen.asAllocatable(input)));
        return result;
    }

}
