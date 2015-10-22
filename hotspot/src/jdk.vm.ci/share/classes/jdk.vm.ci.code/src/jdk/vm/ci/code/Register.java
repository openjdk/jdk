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
package jdk.vm.ci.code;

import jdk.vm.ci.meta.*;

/**
 * Represents a target machine register.
 */
public final class Register implements Comparable<Register> {

    public static final RegisterCategory SPECIAL = new RegisterCategory("SPECIAL");

    /**
     * Invalid register.
     */
    public static final Register None = new Register(-1, -1, "noreg", SPECIAL);

    /**
     * Frame pointer of the current method. All spill slots and outgoing stack-based arguments are
     * addressed relative to this register.
     */
    public static final Register Frame = new Register(-2, -2, "framereg", SPECIAL);

    public static final Register CallerFrame = new Register(-3, -3, "callerframereg", SPECIAL);

    /**
     * The identifier for this register that is unique across all the registers in a
     * {@link Architecture}. A valid register has {@code number > 0}.
     */
    public final int number;

    /**
     * The mnemonic of this register.
     */
    public final String name;

    /**
     * The actual encoding in a target machine instruction for this register, which may or may not
     * be the same as {@link #number}.
     */
    public final int encoding;

    /**
     * The assembler calls this method to get the register's encoding.
     */
    public int encoding() {
        return encoding;
    }

    /**
     * A platform specific register category that describes which values can be stored in a
     * register.
     */
    private final RegisterCategory registerCategory;

    /**
     * A platform specific register type that describes which values can be stored in a register.
     */
    public static class RegisterCategory {

        private final String name;

        private final int referenceMapOffset;
        private final int referenceMapShift;

        public RegisterCategory(String name) {
            this(name, 0, 0);
        }

        public RegisterCategory(String name, int referenceMapOffset) {
            this(name, referenceMapOffset, 0);
        }

        public RegisterCategory(String name, int referenceMapOffset, int referenceMapShift) {
            this.name = name;
            this.referenceMapOffset = referenceMapOffset;
            this.referenceMapShift = referenceMapShift;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public int hashCode() {
            return 23 + name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof RegisterCategory) {
                RegisterCategory that = (RegisterCategory) obj;
                return this.referenceMapOffset == that.referenceMapOffset && this.referenceMapShift == that.referenceMapShift && this.name.equals(that.name);
            }
            return false;
        }
    }

    /**
     * Creates a {@link Register} instance.
     *
     * @param number unique identifier for the register
     * @param encoding the target machine encoding for the register
     * @param name the mnemonic name for the register
     * @param registerCategory the register category
     */
    public Register(int number, int encoding, String name, RegisterCategory registerCategory) {
        this.number = number;
        this.name = name;
        this.registerCategory = registerCategory;
        this.encoding = encoding;
    }

    public RegisterCategory getRegisterCategory() {
        return registerCategory;
    }

    /**
     * Get the start index of this register in the {@link ReferenceMap}.
     */
    public int getReferenceMapIndex() {
        return (encoding << registerCategory.referenceMapShift) + registerCategory.referenceMapOffset;
    }

    /**
     * Gets this register as a {@linkplain RegisterValue value} with a specified kind.
     *
     * @param kind the specified kind
     * @return the {@link RegisterValue}
     */
    public RegisterValue asValue(LIRKind kind) {
        return new RegisterValue(kind, this);
    }

    /**
     * Gets this register as a {@linkplain RegisterValue value} with no particular kind.
     *
     * @return a {@link RegisterValue} with {@link JavaKind#Illegal} kind.
     */
    public RegisterValue asValue() {
        return asValue(LIRKind.Illegal);
    }

    /**
     * Determines if this is a valid register.
     *
     * @return {@code true} iff this register is valid
     */
    public boolean isValid() {
        return number >= 0;
    }

    /**
     * Gets the maximum register {@linkplain #number number} in a given set of registers.
     *
     * @param registers the set of registers to process
     * @return the maximum register number for any register in {@code registers}
     */
    public static int maxRegisterNumber(Register[] registers) {
        int max = Integer.MIN_VALUE;
        for (Register r : registers) {
            if (r.number > max) {
                max = r.number;
            }
        }
        return max;
    }

    /**
     * Gets the maximum register {@linkplain #encoding encoding} in a given set of registers.
     *
     * @param registers the set of registers to process
     * @return the maximum register encoding for any register in {@code registers}
     */
    public static int maxRegisterEncoding(Register[] registers) {
        int max = Integer.MIN_VALUE;
        for (Register r : registers) {
            if (r.encoding > max) {
                max = r.encoding;
            }
        }
        return max;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int compareTo(Register o) {
        if (number < o.number) {
            return -1;
        }
        if (number > o.number) {
            return 1;
        }
        return 0;
    }

    @Override
    public int hashCode() {
        return 17 + name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Register) {
            Register other = (Register) obj;
            if (number == other.number) {
                assert name.equals(other.name);
                assert encoding == other.encoding;
                assert registerCategory.equals(other.registerCategory);
                return true;
            }
        }
        return false;
    }
}
