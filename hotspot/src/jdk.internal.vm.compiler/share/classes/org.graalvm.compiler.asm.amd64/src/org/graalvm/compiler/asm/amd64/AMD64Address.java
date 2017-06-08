/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.asm.amd64;

import jdk.vm.ci.code.Register;

import org.graalvm.compiler.asm.AbstractAddress;

/**
 * Represents an address in target machine memory, specified via some combination of a base
 * register, an index register, a displacement and a scale. Note that the base and index registers
 * may be a variable that will get a register assigned later by the register allocator.
 */
public final class AMD64Address extends AbstractAddress {

    private final Register base;
    private final Register index;
    private final Scale scale;
    private final int displacement;

    /**
     * The start of the instruction, i.e., the value that is used as the key for looking up
     * placeholder patching information. Only used for {@link AMD64Assembler#getPlaceholder
     * placeholder addresses}.
     */
    final int instructionStartPosition;

    /**
     * Creates an {@link AMD64Address} with given base register, no scaling and no displacement.
     *
     * @param base the base register
     */
    public AMD64Address(Register base) {
        this(base, Register.None, Scale.Times1, 0);
    }

    /**
     * Creates an {@link AMD64Address} with given base register, no scaling and a given
     * displacement.
     *
     * @param base the base register
     * @param displacement the displacement
     */
    public AMD64Address(Register base, int displacement) {
        this(base, Register.None, Scale.Times1, displacement);
    }

    /**
     * Creates an {@link AMD64Address} with given base and index registers, scaling and 0
     * displacement.
     *
     * @param base the base register
     * @param index the index register
     * @param scale the scaling factor
     */
    public AMD64Address(Register base, Register index, Scale scale) {
        this(base, index, scale, 0, -1);
    }

    /**
     * Creates an {@link AMD64Address} with given base and index registers, scaling and
     * displacement. This is the most general constructor.
     *
     * @param base the base register
     * @param index the index register
     * @param scale the scaling factor
     * @param displacement the displacement
     */
    public AMD64Address(Register base, Register index, Scale scale, int displacement) {
        this(base, index, scale, displacement, -1);
    }

    AMD64Address(Register base, Register index, Scale scale, int displacement, int instructionStartPosition) {
        this.base = base;
        this.index = index;
        this.scale = scale;
        this.displacement = displacement;
        this.instructionStartPosition = instructionStartPosition;

        assert scale != null;
    }

    /**
     * A scaling factor used in the SIB addressing mode.
     */
    public enum Scale {
        Times1(1, 0),
        Times2(2, 1),
        Times4(4, 2),
        Times8(8, 3);

        Scale(int value, int log2) {
            this.value = value;
            this.log2 = log2;
        }

        /**
         * The value (or multiplier) of this scale.
         */
        public final int value;

        /**
         * The {@linkplain #value value} of this scale log 2.
         */
        public final int log2;

        public static Scale fromInt(int scale) {
            switch (scale) {
                case 1:
                    return Times1;
                case 2:
                    return Times2;
                case 4:
                    return Times4;
                case 8:
                    return Times8;
                default:
                    return null;
            }
        }

        public static Scale fromShift(int shift) {
            switch (shift) {
                case 0:
                    return Times1;
                case 1:
                    return Times2;
                case 2:
                    return Times4;
                case 3:
                    return Times8;
                default:
                    return null;
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("[");
        String sep = "";
        if (!getBase().equals(Register.None)) {
            s.append(getBase());
            sep = " + ";
        }
        if (!getIndex().equals(Register.None)) {
            s.append(sep).append(getIndex()).append(" * ").append(getScale().value);
            sep = " + ";
        }
        if (getDisplacement() < 0) {
            s.append(" - ").append(-getDisplacement());
        } else if (getDisplacement() > 0) {
            s.append(sep).append(getDisplacement());
        }
        s.append("]");
        return s.toString();
    }

    /**
     * @return Base register that defines the start of the address computation. If not present, is
     *         denoted by {@link Register#None}.
     */
    public Register getBase() {
        return base;
    }

    /**
     * @return Index register, the value of which (possibly scaled by {@link #getScale}) is added to
     *         {@link #getBase}. If not present, is denoted by {@link Register#None}.
     */
    public Register getIndex() {
        return index;
    }

    /**
     * @return Scaling factor for indexing, dependent on target operand size.
     */
    public Scale getScale() {
        return scale;
    }

    /**
     * @return Optional additive displacement.
     */
    public int getDisplacement() {
        return displacement;
    }
}
