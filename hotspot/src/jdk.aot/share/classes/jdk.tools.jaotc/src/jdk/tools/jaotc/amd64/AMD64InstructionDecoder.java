/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jaotc.amd64;

import jdk.tools.jaotc.InstructionDecoder;

import jdk.vm.ci.code.TargetDescription;

public final class AMD64InstructionDecoder extends InstructionDecoder {

    private boolean targetIs64Bit;
    private int currentEndOfInstruction;

    private static class Prefix {

        // segment overrides
        public static final int CSSegment = 0x2e;
        public static final int SSSegment = 0x36;
        public static final int DSSegment = 0x3e;
        public static final int ESSegment = 0x26;
        public static final int FSSegment = 0x64;
        public static final int GSSegment = 0x65;
        public static final int REX = 0x40;
        public static final int REXB = 0x41;
        public static final int REXX = 0x42;
        public static final int REXXB = 0x43;
        public static final int REXR = 0x44;
        public static final int REXRB = 0x45;
        public static final int REXRX = 0x46;
        public static final int REXRXB = 0x47;
        public static final int REXW = 0x48;
        public static final int REXWB = 0x49;
        public static final int REXWX = 0x4A;
        public static final int REXWXB = 0x4B;
        public static final int REXWR = 0x4C;
        public static final int REXWRB = 0x4D;
        public static final int REXWRX = 0x4E;
        public static final int REXWRXB = 0x4F;
        public static final int VEX_3BYTES = 0xC4;
        public static final int VEX_2BYTES = 0xC5;
    }

    public static class VexPrefix {
        public static final int VEX_R = 0x80;
        public static final int VEX_W = 0x80;
    }

    public static class VexOpcode {
        public static final int VEX_OPCODE_NONE = 0x0;
        public static final int VEX_OPCODE_0F = 0x1;
        public static final int VEX_OPCODE_0F_38 = 0x2;
        public static final int VEX_OPCODE_0F_3A = 0x3;
        public static final int VEX_OPCODE_MASK = 0x1F;
    }

    public AMD64InstructionDecoder(TargetDescription target) {
        this.targetIs64Bit = target.wordSize == 8;
    }

    @Override
    public int currentEndOfInstruction() {
        return currentEndOfInstruction;
    }

    @Override
    @SuppressWarnings("fallthrough")
    public void decodePosition(final byte[] code, int pcOffset) {
        assert pcOffset >= 0 && pcOffset < code.length;

        // Decode the given instruction, and return the Pointer of
        // an embedded 32-bit operand word.

        // If "which" is WhichOperand.disp32operand, selects the displacement portion
        // of an effective Pointer specifier.
        // If "which" is imm64Operand, selects the trailing immediate constant.
        // If "which" is WhichOperand.call32operand, selects the displacement of a call or jump.
        // Caller is responsible for ensuring that there is such an operand,
        // and that it is 32/64 bits wide.

        // If "which" is endPcOperand, find the end of the instruction.

        int ip = pcOffset;
        boolean is64bit = false;

        boolean hasDisp32 = false;
        int tailSize = 0; // other random bytes (#32, #16, etc.) at end of insn

        boolean againAfterPrefix = true;

        while (againAfterPrefix) {
            againAfterPrefix = false;
            switch (0xFF & code[ip++]) {

                // These convenience macros generate groups of "case" labels for the switch.

                case Prefix.CSSegment:
                case Prefix.SSSegment:
                case Prefix.DSSegment:
                case Prefix.ESSegment:
                case Prefix.FSSegment:
                case Prefix.GSSegment:
                    // Seems dubious
                    assert !targetIs64Bit : "shouldn't have that prefix";
                    assert ip == pcOffset + 1 : "only one prefix allowed";
                    againAfterPrefix = true;
                    break;

                case 0x67:
                case Prefix.REX:
                case Prefix.REXB:
                case Prefix.REXX:
                case Prefix.REXXB:
                case Prefix.REXR:
                case Prefix.REXRB:
                case Prefix.REXRX:
                case Prefix.REXRXB:
                    assert targetIs64Bit : "64bit prefixes";
                    againAfterPrefix = true;
                    break;

                case Prefix.REXW:
                case Prefix.REXWB:
                case Prefix.REXWX:
                case Prefix.REXWXB:
                case Prefix.REXWR:
                case Prefix.REXWRB:
                case Prefix.REXWRX:
                case Prefix.REXWRXB:
                    assert targetIs64Bit : "64bit prefixes";
                    is64bit = true;
                    againAfterPrefix = true;
                    break;

                case 0xFF: // pushq a; decl a; incl a; call a; jmp a
                case 0x88: // movb a, r
                case 0x89: // movl a, r
                case 0x8A: // movb r, a
                case 0x8B: // movl r, a
                case 0x8F: // popl a
                    hasDisp32 = true;
                    break;

                case 0x68: // pushq #32
                    currentEndOfInstruction = ip + 4;
                    return; // not produced by emitOperand

                case 0x66: // movw ... (size prefix)
                    boolean againAfterSizePrefix2 = true;
                    while (againAfterSizePrefix2) {
                        againAfterSizePrefix2 = false;
                        switch (0xFF & code[ip++]) {
                            case Prefix.REX:
                            case Prefix.REXB:
                            case Prefix.REXX:
                            case Prefix.REXXB:
                            case Prefix.REXR:
                            case Prefix.REXRB:
                            case Prefix.REXRX:
                            case Prefix.REXRXB:
                            case Prefix.REXW:
                            case Prefix.REXWB:
                            case Prefix.REXWX:
                            case Prefix.REXWXB:
                            case Prefix.REXWR:
                            case Prefix.REXWRB:
                            case Prefix.REXWRX:
                            case Prefix.REXWRXB:
                                assert targetIs64Bit : "64bit prefix found";
                                againAfterSizePrefix2 = true;
                                break;
                            case 0x8B: // movw r, a
                            case 0x89: // movw a, r
                                hasDisp32 = true;
                                break;
                            case 0xC7: // movw a, #16
                                hasDisp32 = true;
                                tailSize = 2; // the imm16
                                break;
                            case 0x0F: // several SSE/SSE2 variants
                                ip--; // reparse the 0x0F
                                againAfterPrefix = true;
                                break;
                            default:
                                throw new InternalError("should not reach here");
                        }
                    }
                    break;

                case 0xB8: // movl/q r, #32/#64(oop?)
                case 0xB9:
                case 0xBA:
                case 0xBB:
                case 0xBC:
                case 0xBD:
                case 0xBE:
                case 0xBF:
                    currentEndOfInstruction = ip + (is64bit ? 8 : 4);
                    return;

                case 0x69: // imul r, a, #32
                case 0xC7: // movl a, #32(oop?)
                    tailSize = 4;
                    hasDisp32 = true; // has both kinds of operands!
                    break;

                case 0x0F: // movx..., etc.
                    switch (0xFF & code[ip++]) {
                        case 0x3A: // pcmpestri
                            ip++; // skip opcode
                            tailSize = 1;
                            hasDisp32 = true; // has both kinds of operands!
                            break;

                        case 0x38: // ptest, pmovzxbw
                            ip++; // skip opcode
                            hasDisp32 = true; // has both kinds of operands!
                            break;

                        case 0x70: // pshufd r, r/a, #8
                            hasDisp32 = true; // has both kinds of operands!
                            tailSize = 1;
                            break;

                        case 0x73: // psrldq r, #8
                            tailSize = 1;
                            break;

                        case 0x12: // movlps
                        case 0x28: // movaps
                        case 0x2E: // ucomiss
                        case 0x2F: // comiss
                        case 0x54: // andps
                        case 0x55: // andnps
                        case 0x56: // orps
                        case 0x57: // xorps
                        case 0x58: // addpd
                        case 0x59: // mulpd
                        case 0x6E: // movd
                        case 0x7E: // movd
                        case 0xAE: // ldmxcsr, stmxcsr, fxrstor, fxsave, clflush
                        case 0xFE: // paddd
                            // 64bit side says it these have both operands but that doesn't
                            // appear to be true
                            hasDisp32 = true;
                            break;

                        case 0xAD: // shrd r, a, %cl
                        case 0xAF: // imul r, a
                        case 0xBE: // movsbl r, a (movsxb)
                        case 0xBF: // movswl r, a (movsxw)
                        case 0xB6: // movzbl r, a (movzxb)
                        case 0xB7: // movzwl r, a (movzxw)
                        case 0x40: // cmovl cc, r, a
                        case 0x41:
                        case 0x42:
                        case 0x43:
                        case 0x44:
                        case 0x45:
                        case 0x46:
                        case 0x47:
                        case 0x48:
                        case 0x49:
                        case 0x4A:
                        case 0x4B:
                        case 0x4C:
                        case 0x4D:
                        case 0x4E:
                        case 0x4F:
                        case 0xB0: // cmpxchgb
                        case 0xB1: // cmpxchg
                        case 0xC1: // xaddl
                        case 0xC7: // cmpxchg8
                        case 0x90: // setcc a
                        case 0x91:
                        case 0x92:
                        case 0x93:
                        case 0x94:
                        case 0x95:
                        case 0x96:
                        case 0x97:
                        case 0x98:
                        case 0x99:
                        case 0x9A:
                        case 0x9B:
                        case 0x9C:
                        case 0x9D:
                        case 0x9E:
                        case 0x9F:
                            hasDisp32 = true;
                            // fall out of the switch to decode the Pointer
                            break;

                        case 0xC4: // pinsrw r, a, #8
                            hasDisp32 = true;
                            tailSize = 1;  // the imm8
                            break;

                        case 0xC5: // pextrw r, r, #8
                            tailSize = 1;  // the imm8
                            break;

                        case 0xAC: // shrd r, a, #8
                            hasDisp32 = true;
                            tailSize = 1; // the imm8
                            break;

                        case 0x80: // jcc rdisp32
                        case 0x81:
                        case 0x82:
                        case 0x83:
                        case 0x84:
                        case 0x85:
                        case 0x86:
                        case 0x87:
                        case 0x88:
                        case 0x89:
                        case 0x8A:
                        case 0x8B:
                        case 0x8C:
                        case 0x8D:
                        case 0x8E:
                        case 0x8F:
                            currentEndOfInstruction = ip + 4;
                            return;
                        default:
                            throw new InternalError("should not reach here");
                    }
                    break;

                case 0x81: // addl a, #32; addl r, #32
                    // also: orl, adcl, sbbl, andl, subl, xorl, cmpl
                    // on 32bit in the case of cmpl, the imm might be an oop
                    tailSize = 4;
                    hasDisp32 = true; // has both kinds of operands!
                    break;

                case 0x83: // addl a, #8; addl r, #8
                    // also: orl, adcl, sbbl, andl, subl, xorl, cmpl
                    hasDisp32 = true; // has both kinds of operands!
                    tailSize = 1;
                    break;

                case 0x9B:
                    switch (0xFF & code[ip++]) {
                        case 0xD9: // fnstcw a
                            hasDisp32 = true;
                            break;
                        default:
                            throw new InternalError("should not reach here");
                    }
                    break;

                case 0x00: // addb a, r; addl a, r; addb r, a; addl r, a
                case 0x01:
                case 0x02:
                case 0x03:
                case 0x10: // adc...
                case 0x11:
                case 0x12:
                case 0x13:
                case 0x20: // and...
                case 0x21:
                case 0x22:
                case 0x23:
                case 0x30: // xor...
                case 0x31:
                case 0x32:
                case 0x33:
                case 0x08: // or...
                case 0x09:
                case 0x0a:
                case 0x0b:
                case 0x18: // sbb...
                case 0x19:
                case 0x1a:
                case 0x1b:
                case 0x28: // sub...
                case 0x29:
                case 0x2a:
                case 0x2b:
                case 0xF7: // mull a
                case 0x8D: // lea r, a
                case 0x87: // xchg r, a
                case 0x38: // cmp...
                case 0x39:
                case 0x3a:
                case 0x3b:
                case 0x85: // test r, a
                    hasDisp32 = true; // has both kinds of operands!
                    break;

                case 0xC1: // sal a, #8; sar a, #8; shl a, #8; shr a, #8
                case 0xC6: // movb a, #8
                case 0x80: // cmpb a, #8
                case 0x6B: // imul r, a, #8
                    hasDisp32 = true; // has both kinds of operands!
                    tailSize = 1; // the imm8
                    break;

                case Prefix.VEX_3BYTES:
                case Prefix.VEX_2BYTES:
                    assert ip == pcOffset + 1 : "no prefixes allowed";
                    int vex_opcode;
                    // First byte
                    if ((code[pcOffset] & 0xFF) == Prefix.VEX_3BYTES) {
                        vex_opcode = VexOpcode.VEX_OPCODE_MASK & code[ip];
                        ip++; // third byte
                        is64bit = ((VexPrefix.VEX_W & code[ip]) == VexPrefix.VEX_W);
                    } else {
                        vex_opcode = VexOpcode.VEX_OPCODE_0F;
                    }
                    ip++; // opcode
                    // To find the end of instruction (which == end_pc_operand).
                    switch (vex_opcode) {
                        case VexOpcode.VEX_OPCODE_0F:
                            switch (0xFF & code[ip]) {
                                case 0x70: // pshufd r, r/a, #8
                                case 0x71: // ps[rl|ra|ll]w r, #8
                                case 0x72: // ps[rl|ra|ll]d r, #8
                                case 0x73: // ps[rl|ra|ll]q r, #8
                                case 0xC2: // cmp[ps|pd|ss|sd] r, r, r/a, #8
                                case 0xC4: // pinsrw r, r, r/a, #8
                                case 0xC5: // pextrw r/a, r, #8
                                case 0xC6: // shufp[s|d] r, r, r/a, #8
                                    tailSize = 1;  // the imm8
                                    break;
                                default:
                                    ; // no imm8
                            }
                            break;
                        case VexOpcode.VEX_OPCODE_0F_3A:
                            tailSize = 1;
                            break;
                        default:
                            throw new InternalError("should not reach here");
                    }
                    ip++; // skip opcode
                    hasDisp32 = true;
                    break;

                case 0xE8: // call rdisp32
                case 0xE9: // jmp rdisp32
                    currentEndOfInstruction = ip + 4;
                    return;

                case 0xD1: // sal a, 1; sar a, 1; shl a, 1; shr a, 1
                case 0xD3: // sal a, %cl; sar a, %cl; shl a, %cl; shr a, %cl
                case 0xD9: // fldS a; fstS a; fstpS a; fldcw a
                case 0xDD: // fldD a; fstD a; fstpD a
                case 0xDB: // fildS a; fistpS a; fldX a; fstpX a
                case 0xDF: // fildD a; fistpD a
                case 0xD8: // faddS a; fsubrS a; fmulS a; fdivrS a; fcompS a
                case 0xDC: // faddD a; fsubrD a; fmulD a; fdivrD a; fcompD a
                case 0xDE: // faddpD a; fsubrpD a; fmulpD a; fdivrpD a; fcomppD a
                    hasDisp32 = true;
                    break;

                case 0xF0: // Lock
                    againAfterPrefix = true;
                    break;

                case 0xF3: // For SSE
                case 0xF2: // For SSE2
                    switch (0xFF & code[ip++]) {
                        case Prefix.REX:
                        case Prefix.REXB:
                        case Prefix.REXX:
                        case Prefix.REXXB:
                        case Prefix.REXR:
                        case Prefix.REXRB:
                        case Prefix.REXRX:
                        case Prefix.REXRXB:
                        case Prefix.REXW:
                        case Prefix.REXWB:
                        case Prefix.REXWX:
                        case Prefix.REXWXB:
                        case Prefix.REXWR:
                        case Prefix.REXWRB:
                        case Prefix.REXWRX:
                        case Prefix.REXWRXB:
                            assert targetIs64Bit : "found 64bit prefix";
                            ip++;
                            ip++;
                            break;
                        default:
                            ip++;
                    }
                    hasDisp32 = true; // has both kinds of operands!
                    break;

                default:
                    throw new InternalError("should not reach here");
            }
        }

        assert hasDisp32 : "(tw) not sure if this holds: instruction has no disp32 field";

        // parse the output of emitOperand
        int op2 = 0xFF & code[ip++];
        int base = op2 & 0x07;
        int op3 = -1;
        int b100 = 4;
        int b101 = 5;
        if (base == b100 && (op2 >> 6) != 3) {
            op3 = 0xFF & code[ip++];
            base = op3 & 0x07; // refetch the base
        }
        // now ip points at the disp (if any)

        switch (op2 >> 6) {
            case 0:
                // [00 reg 100][ss index base]
                // [00 reg 100][00 100 esp]
                // [00 reg base]
                // [00 reg 100][ss index 101][disp32]
                // [00 reg 101] [disp32]
                if (base == b101) {
                    ip += 4; // skip the disp32
                }
                break;

            case 1:
                // [01 reg 100][ss index base][disp8]
                // [01 reg 100][00 100 esp][disp8]
                // [01 reg base] [disp8]
                ip += 1; // skip the disp8
                break;

            case 2:
                // [10 reg 100][ss index base][disp32]
                // [10 reg 100][00 100 esp][disp32]
                // [10 reg base] [disp32]
                ip += 4; // skip the disp32
                break;

            case 3:
                // [11 reg base] (not a memory addressing mode)
                break;
        }

        currentEndOfInstruction = ip + tailSize;
    }

}
