/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.amd64;

import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.Value;

/**
 * This interface can be used to generate AMD64 LIR for arithmetic operations.
 */
public interface AMD64ArithmeticLIRGeneratorTool extends ArithmeticLIRGeneratorTool {

    Value emitCountLeadingZeros(Value value);

    Value emitCountTrailingZeros(Value value);

    Value emitLogicalAndNot(Value value1, Value value2);

    Value emitLowestSetIsolatedBit(Value value);

    Value emitGetMaskUpToLowestSetBit(Value value);

    Value emitResetLowestSetBit(Value value);

    enum RoundingMode {
        NEAREST(0),
        DOWN(1),
        UP(2),
        TRUNCATE(3);

        public final int encoding;

        RoundingMode(int encoding) {
            this.encoding = encoding;
        }
    }

    Value emitRound(Value value, RoundingMode mode);

    void emitCompareOp(AMD64Kind cmpKind, Variable left, Value right);
}
