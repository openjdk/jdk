/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.asm.sparc;

import java.util.TreeMap;

import org.graalvm.compiler.asm.Assembler.InstructionCounter;

public class SPARCInstructionCounter implements InstructionCounter {
    // Use a treemap to keep the order in the output
    private static final TreeMap<String, SPARCInstructionMatch> INSTRUCTION_MATCHER = new TreeMap<>();

    static {
        // @formatter:off
        INSTRUCTION_MATCHER.put("nop", new SPARCInstructionMatch(0xFFFF_FFFF, 0x0100_0000));
        INSTRUCTION_MATCHER.put("st", new OP3LowBitsMatcher(0b11, 0x4, 0x5, 0x6, 0x7, 0xe, 0xf));
        INSTRUCTION_MATCHER.put("ld", new OP3LowBitsMatcher(0b11, 0x0, 0x1, 0x2, 0x3, 0x8, 0x9, 0xa, 0xb, 0xc, 0xd));
        INSTRUCTION_MATCHER.put("all", new SPARCInstructionMatch(0x0, 0x0));
        // @formatter:on
    }

    private final SPARCAssembler asm;

    public SPARCInstructionCounter(SPARCAssembler asm) {
        super();
        this.asm = asm;
    }

    @Override
    public int[] countInstructions(String[] instructionTypes, int beginPc, int endPc) {
        SPARCInstructionMatch[] matchers = new SPARCInstructionMatch[instructionTypes.length];
        for (int i = 0; i < instructionTypes.length; i++) {
            String typeName = instructionTypes[i];
            matchers[i] = INSTRUCTION_MATCHER.get(typeName);
            if (matchers[i] == null) {
                throw new IllegalArgumentException(String.format("Unknown instruction class %s, supported types are: %s", typeName, INSTRUCTION_MATCHER.keySet()));
            }
        }
        return countBetween(matchers, beginPc, endPc);
    }

    private int[] countBetween(SPARCInstructionMatch[] matchers, int startPc, int endPc) {
        int[] counts = new int[matchers.length];
        for (int p = startPc; p < endPc; p += 4) {
            int instr = asm.getInt(p);
            for (int i = 0; i < matchers.length; i++) {
                SPARCInstructionMatch matcher = matchers[i];
                if (matcher.matches(instr)) {
                    counts[i]++;
                }
            }
        }
        return counts;
    }

    @Override
    public String[] getSupportedInstructionTypes() {
        return INSTRUCTION_MATCHER.keySet().toArray(new String[0]);
    }

    /**
     * Tests the lower 3 bits of the op3 field.
     */
    private static class OP3LowBitsMatcher extends SPARCInstructionMatch {
        private final int[] op3b03;
        private final int op;

        OP3LowBitsMatcher(int op, int... op3b03) {
            super(0, 0);
            this.op = op;
            this.op3b03 = op3b03;
        }

        @Override
        public boolean matches(int instruction) {
            if (instruction >>> 30 != op) {
                return false;
            }
            int op3lo = (instruction >> 19) & ((1 << 4) - 1);
            for (int op3Part : op3b03) {
                if (op3Part == op3lo) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class SPARCInstructionMatch {
        private final int mask;
        private final int[] patterns;

        SPARCInstructionMatch(int mask, int... patterns) {
            super();
            this.mask = mask;
            this.patterns = patterns;
        }

        public boolean matches(int instruction) {
            for (int pattern : patterns) {
                if ((instruction & mask) == pattern) {
                    return true;
                }
            }
            return false;
        }
    }
}
