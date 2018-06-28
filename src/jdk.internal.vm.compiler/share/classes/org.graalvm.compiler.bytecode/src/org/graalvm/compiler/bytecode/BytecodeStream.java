/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.bytecode;

/**
 * A utility class that makes iterating over bytecodes and reading operands simpler and less error
 * prone. For example, it handles the {@link Bytecodes#WIDE} instruction and wide variants of
 * instructions internally.
 */
public final class BytecodeStream {

    private final byte[] code;
    private int opcode;
    private int curBCI;
    private int nextBCI;

    /**
     * Creates a new {@code BytecodeStream} for the specified bytecode.
     *
     * @param code the array of bytes that contains the bytecode
     */
    public BytecodeStream(byte[] code) {
        assert code != null;
        this.code = code;
        setBCI(0);
    }

    /**
     * Advances to the next bytecode.
     */
    public void next() {
        setBCI(nextBCI);
    }

    /**
     * Gets the next bytecode index (no side-effects).
     *
     * @return the next bytecode index
     */
    public int nextBCI() {
        return nextBCI;
    }

    /**
     * Gets the current bytecode index.
     *
     * @return the current bytecode index
     */
    public int currentBCI() {
        return curBCI;
    }

    /**
     * Gets the bytecode index of the end of the code.
     *
     * @return the index of the end of the code
     */
    public int endBCI() {
        return code.length;
    }

    /**
     * Gets the current opcode. This method will never return the {@link Bytecodes#WIDE WIDE}
     * opcode, but will instead return the opcode that is modified by the {@code WIDE} opcode.
     *
     * @return the current opcode; {@link Bytecodes#END} if at or beyond the end of the code
     */
    public int currentBC() {
        if (opcode == Bytecodes.WIDE) {
            return Bytes.beU1(code, curBCI + 1);
        } else {
            return opcode;
        }
    }

    /**
     * Reads the index of a local variable for one of the load or store instructions. The WIDE
     * modifier is handled internally.
     *
     * @return the index of the local variable
     */
    public int readLocalIndex() {
        // read local variable index for load/store
        if (opcode == Bytecodes.WIDE) {
            return Bytes.beU2(code, curBCI + 2);
        }
        return Bytes.beU1(code, curBCI + 1);
    }

    /**
     * Read the delta for an {@link Bytecodes#IINC} bytecode.
     *
     * @return the delta for the {@code IINC}
     */
    public int readIncrement() {
        // read the delta for the iinc bytecode
        if (opcode == Bytecodes.WIDE) {
            return Bytes.beS2(code, curBCI + 4);
        }
        return Bytes.beS1(code, curBCI + 2);
    }

    /**
     * Read the destination of a {@link Bytecodes#GOTO} or {@code IF} instructions.
     *
     * @return the destination bytecode index
     */
    public int readBranchDest() {
        // reads the destination for a branch bytecode
        if (opcode == Bytecodes.GOTO_W || opcode == Bytecodes.JSR_W) {
            return curBCI + Bytes.beS4(code, curBCI + 1);
        } else {
            return curBCI + Bytes.beS2(code, curBCI + 1);
        }
    }

    /**
     * Read a signed 4-byte integer from the bytecode stream at the specified bytecode index.
     *
     * @param bci the bytecode index
     * @return the integer value
     */
    public int readInt(int bci) {
        // reads a 4-byte signed value
        return Bytes.beS4(code, bci);
    }

    /**
     * Reads an unsigned, 1-byte value from the bytecode stream at the specified bytecode index.
     *
     * @param bci the bytecode index
     * @return the byte
     */
    public int readUByte(int bci) {
        return Bytes.beU1(code, bci);
    }

    /**
     * Reads a constant pool index for the current instruction.
     *
     * @return the constant pool index
     */
    public char readCPI() {
        if (opcode == Bytecodes.LDC) {
            return (char) Bytes.beU1(code, curBCI + 1);
        }
        return (char) Bytes.beU2(code, curBCI + 1);
    }

    /**
     * Reads a constant pool index for an invokedynamic instruction.
     *
     * @return the constant pool index
     */
    public int readCPI4() {
        assert opcode == Bytecodes.INVOKEDYNAMIC;
        return Bytes.beS4(code, curBCI + 1);
    }

    /**
     * Reads a signed, 1-byte value for the current instruction (e.g. BIPUSH).
     *
     * @return the byte
     */
    public byte readByte() {
        return code[curBCI + 1];
    }

    /**
     * Reads a signed, 2-byte short for the current instruction (e.g. SIPUSH).
     *
     * @return the short value
     */
    public short readShort() {
        return (short) Bytes.beS2(code, curBCI + 1);
    }

    /**
     * Sets the bytecode index to the specified value. If {@code bci} is beyond the end of the
     * array, {@link #currentBC} will return {@link Bytecodes#END} and other methods may throw
     * {@link ArrayIndexOutOfBoundsException}.
     *
     * @param bci the new bytecode index
     */
    public void setBCI(int bci) {
        curBCI = bci;
        if (curBCI < code.length) {
            opcode = Bytes.beU1(code, bci);
            assert opcode < Bytecodes.BREAKPOINT : "illegal bytecode";
            nextBCI = bci + lengthOf();
        } else {
            opcode = Bytecodes.END;
            nextBCI = curBCI;
        }
    }

    /**
     * Gets the length of the current bytecode.
     */
    private int lengthOf() {
        int length = Bytecodes.lengthOf(opcode);
        if (length == 0) {
            switch (opcode) {
                case Bytecodes.TABLESWITCH: {
                    return new BytecodeTableSwitch(this, curBCI).size();
                }
                case Bytecodes.LOOKUPSWITCH: {
                    return new BytecodeLookupSwitch(this, curBCI).size();
                }
                case Bytecodes.WIDE: {
                    int opc = Bytes.beU1(code, curBCI + 1);
                    if (opc == Bytecodes.RET) {
                        return 4;
                    } else if (opc == Bytecodes.IINC) {
                        return 6;
                    } else {
                        return 4; // a load or store bytecode
                    }
                }
                default:
                    throw new Error("unknown variable-length bytecode: " + opcode);
            }
        }
        return length;
    }
}
