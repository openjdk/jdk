/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.ArrayDataPointerConstant;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

public final class AMD64MathIntrinsicUnaryOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64MathIntrinsicUnaryOp> TYPE = LIRInstructionClass.create(AMD64MathIntrinsicUnaryOp.class);

    public enum UnaryIntrinsicOpcode {
        LOG,
        LOG10,
        SIN,
        COS,
        TAN,
        EXP
    }

    @Opcode private final UnaryIntrinsicOpcode opcode;
    @Def protected Value result;
    @Use protected Value input;
    @Temp({REG, ILLEGAL}) protected Value xmm1Temp = Value.ILLEGAL;
    @Temp({REG, ILLEGAL}) protected Value xmm2Temp = Value.ILLEGAL;
    @Temp({REG, ILLEGAL}) protected Value xmm3Temp = Value.ILLEGAL;
    @Temp({REG, ILLEGAL}) protected Value xmm4Temp = Value.ILLEGAL;
    @Temp({REG, ILLEGAL}) protected Value xmm5Temp = Value.ILLEGAL;
    @Temp({REG, ILLEGAL}) protected Value xmm6Temp = Value.ILLEGAL;
    @Temp({REG, ILLEGAL}) protected Value xmm7Temp = Value.ILLEGAL;
    @Temp({REG, ILLEGAL}) protected Value xmm8Temp = Value.ILLEGAL;
    @Temp({REG, ILLEGAL}) protected Value xmm9Temp = Value.ILLEGAL;
    @Temp({REG, ILLEGAL}) protected Value xmm10Temp = Value.ILLEGAL;
    @Temp({REG, ILLEGAL}) protected Value gpr1Temp = Value.ILLEGAL;
    @Temp({REG, ILLEGAL}) protected Value gpr2Temp = Value.ILLEGAL;
    @Temp protected AllocatableValue rcxTemp;
    @Temp({REG, ILLEGAL}) protected Value gpr4Temp = Value.ILLEGAL;
    @Temp({REG, ILLEGAL}) protected Value gpr5Temp = Value.ILLEGAL;
    @Temp({REG, ILLEGAL}) protected Value gpr6Temp = Value.ILLEGAL;
    @Temp({REG, ILLEGAL}) protected Value gpr7Temp = Value.ILLEGAL;
    @Temp({REG, ILLEGAL}) protected Value gpr8Temp = Value.ILLEGAL;
    @Temp({REG, ILLEGAL}) protected Value gpr9Temp = Value.ILLEGAL;
    @Temp({REG, ILLEGAL}) protected Value gpr10Temp = Value.ILLEGAL;
    @Temp({STACK, ILLEGAL}) protected Value stackTemp = Value.ILLEGAL;

    CompilationResultBuilder internalCrb;

    public AMD64MathIntrinsicUnaryOp(LIRGeneratorTool tool, UnaryIntrinsicOpcode opcode, Value result, Value input, Value stackTemp) {
        super(TYPE);
        this.opcode = opcode;
        this.result = result;
        this.input = input;
        if (opcode == UnaryIntrinsicOpcode.LOG || opcode == UnaryIntrinsicOpcode.LOG10 ||
                        opcode == UnaryIntrinsicOpcode.SIN || opcode == UnaryIntrinsicOpcode.COS ||
                        opcode == UnaryIntrinsicOpcode.TAN || opcode == UnaryIntrinsicOpcode.EXP) {
            this.gpr1Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
            this.gpr2Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
            this.rcxTemp = AMD64.rcx.asValue(LIRKind.value(AMD64Kind.QWORD));
            this.gpr4Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
            this.xmm1Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm2Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm3Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm4Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm5Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm6Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm7Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));

            if (opcode == UnaryIntrinsicOpcode.EXP) {
                this.gpr5Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
                this.xmm8Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
                this.xmm9Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
                this.xmm10Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            }

            if (opcode == UnaryIntrinsicOpcode.TAN) {
                this.gpr5Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
                this.gpr6Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
                this.gpr7Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
                this.gpr8Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
                this.gpr9Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
                this.gpr10Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
            }

            if (opcode == UnaryIntrinsicOpcode.SIN || opcode == UnaryIntrinsicOpcode.COS) {
                this.gpr5Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
                this.gpr6Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
                this.gpr7Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
                this.gpr8Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
                this.gpr9Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
                this.gpr10Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
                this.xmm8Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
                this.xmm9Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            }

            this.stackTemp = stackTemp;
        }
    }

    public AMD64MathIntrinsicUnaryOp(LIRGeneratorTool tool, UnaryIntrinsicOpcode opcode, Value result, Value input) {
        this(tool, opcode, result, input, Value.ILLEGAL);
    }

    private void setCrb(CompilationResultBuilder crb) {
        internalCrb = crb;
    }

    private AMD64Address externalAddress(ArrayDataPointerConstant curPtr) {
        return (AMD64Address) internalCrb.recordDataReferenceInCode(curPtr);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        switch (opcode) {
            case LOG:
                logIntrinsic(asRegister(result, AMD64Kind.DOUBLE), asRegister(input, AMD64Kind.DOUBLE), crb, masm);
                break;
            case LOG10:
                log10Intrinsic(asRegister(result, AMD64Kind.DOUBLE), asRegister(input, AMD64Kind.DOUBLE), crb, masm);
                break;
            case SIN:
                sinIntrinsic(asRegister(result, AMD64Kind.DOUBLE), asRegister(input, AMD64Kind.DOUBLE), crb, masm);
                break;
            case COS:
                cosIntrinsic(asRegister(result, AMD64Kind.DOUBLE), asRegister(input, AMD64Kind.DOUBLE), crb, masm);
                break;
            case TAN:
                tanIntrinsic(asRegister(result, AMD64Kind.DOUBLE), asRegister(input, AMD64Kind.DOUBLE), crb, masm);
                break;
            case EXP:
                expIntrinsic(asRegister(result, AMD64Kind.DOUBLE), asRegister(input, AMD64Kind.DOUBLE), crb, masm);
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    private static int[] logTwoTable = {
                    0xfefa3800, 0x3fe62e42, 0x93c76730, 0x3d2ef357, 0xaa241800,
                    0x3fe5ee82, 0x0cda46be, 0x3d220238, 0x5c364800, 0x3fe5af40,
                    0xac10c9fb, 0x3d2dfa63, 0x26bb8c00, 0x3fe5707a, 0xff3303dd,
                    0x3d09980b, 0x26867800, 0x3fe5322e, 0x5d257531, 0x3d05ccc4,
                    0x835a5000, 0x3fe4f45a, 0x6d93b8fb, 0xbd2e6c51, 0x6f970c00,
                    0x3fe4b6fd, 0xed4c541c, 0x3cef7115, 0x27e8a400, 0x3fe47a15,
                    0xf94d60aa, 0xbd22cb6a, 0xf2f92400, 0x3fe43d9f, 0x481051f7,
                    0xbcfd984f, 0x2125cc00, 0x3fe4019c, 0x30f0c74c, 0xbd26ce79,
                    0x0c36c000, 0x3fe3c608, 0x7cfe13c2, 0xbd02b736, 0x17197800,
                    0x3fe38ae2, 0xbb5569a4, 0xbd218b7a, 0xad9d8c00, 0x3fe35028,
                    0x9527e6ac, 0x3d10b83f, 0x44340800, 0x3fe315da, 0xc5a0ed9c,
                    0xbd274e93, 0x57b0e000, 0x3fe2dbf5, 0x07b9dc11, 0xbd17a6e5,
                    0x6d0ec000, 0x3fe2a278, 0xe797882d, 0x3d206d2b, 0x1134dc00,
                    0x3fe26962, 0x05226250, 0xbd0b61f1, 0xd8bebc00, 0x3fe230b0,
                    0x6e48667b, 0x3d12fc06, 0x5fc61800, 0x3fe1f863, 0xc9fe81d3,
                    0xbd2a7242, 0x49ae6000, 0x3fe1c078, 0xed70e667, 0x3cccacde,
                    0x40f23c00, 0x3fe188ee, 0xf8ab4650, 0x3d14cc4e, 0xf6f29800,
                    0x3fe151c3, 0xa293ae49, 0xbd2edd97, 0x23c75c00, 0x3fe11af8,
                    0xbb9ddcb2, 0xbd258647, 0x8611cc00, 0x3fe0e489, 0x07801742,
                    0x3d1c2998, 0xe2d05400, 0x3fe0ae76, 0x887e7e27, 0x3d1f486b,
                    0x0533c400, 0x3fe078bf, 0x41edf5fd, 0x3d268122, 0xbe760400,
                    0x3fe04360, 0xe79539e0, 0xbd04c45f, 0xe5b20800, 0x3fe00e5a,
                    0xb1727b1c, 0xbd053ba3, 0xaf7a4800, 0x3fdfb358, 0x3c164935,
                    0x3d0085fa, 0xee031800, 0x3fdf4aa7, 0x6f014a8b, 0x3d12cde5,
                    0x56b41000, 0x3fdee2a1, 0x5a470251, 0x3d2f27f4, 0xc3ddb000,
                    0x3fde7b42, 0x5372bd08, 0xbd246550, 0x1a272800, 0x3fde148a,
                    0x07322938, 0xbd1326b2, 0x484c9800, 0x3fddae75, 0x60dc616a,
                    0xbd1ea42d, 0x46def800, 0x3fdd4902, 0xe9a767a8, 0x3d235baf,
                    0x18064800, 0x3fdce42f, 0x3ec7a6b0, 0xbd0797c3, 0xc7455800,
                    0x3fdc7ff9, 0xc15249ae, 0xbd29b6dd, 0x693fa000, 0x3fdc1c60,
                    0x7fe8e180, 0x3d2cec80, 0x1b80e000, 0x3fdbb961, 0xf40a666d,
                    0x3d27d85b, 0x04462800, 0x3fdb56fa, 0x2d841995, 0x3d109525,
                    0x5248d000, 0x3fdaf529, 0x52774458, 0xbd217cc5, 0x3c8ad800,
                    0x3fda93ed, 0xbea77a5d, 0x3d1e36f2, 0x0224f800, 0x3fda3344,
                    0x7f9d79f5, 0x3d23c645, 0xea15f000, 0x3fd9d32b, 0x10d0c0b0,
                    0xbd26279e, 0x43135800, 0x3fd973a3, 0xa502d9f0, 0xbd152313,
                    0x635bf800, 0x3fd914a8, 0x2ee6307d, 0xbd1766b5, 0xa88b3000,
                    0x3fd8b639, 0xe5e70470, 0xbd205ae1, 0x776dc800, 0x3fd85855,
                    0x3333778a, 0x3d2fd56f, 0x3bd81800, 0x3fd7fafa, 0xc812566a,
                    0xbd272090, 0x687cf800, 0x3fd79e26, 0x2efd1778, 0x3d29ec7d,
                    0x76c67800, 0x3fd741d8, 0x49dc60b3, 0x3d2d8b09, 0xe6af1800,
                    0x3fd6e60e, 0x7c222d87, 0x3d172165, 0x3e9c6800, 0x3fd68ac8,
                    0x2756eba0, 0x3d20a0d3, 0x0b3ab000, 0x3fd63003, 0xe731ae00,
                    0xbd2db623, 0xdf596000, 0x3fd5d5bd, 0x08a465dc, 0xbd0a0b2a,
                    0x53c8d000, 0x3fd57bf7, 0xee5d40ef, 0x3d1faded, 0x0738a000,
                    0x3fd522ae, 0x8164c759, 0x3d2ebe70, 0x9e173000, 0x3fd4c9e0,
                    0x1b0ad8a4, 0xbd2e2089, 0xc271c800, 0x3fd4718d, 0x0967d675,
                    0xbd2f27ce, 0x23d5e800, 0x3fd419b4, 0xec90e09d, 0x3d08e436,
                    0x77333000, 0x3fd3c252, 0xb606bd5c, 0x3d183b54, 0x76be1000,
                    0x3fd36b67, 0xb0f177c8, 0x3d116ecd, 0xe1d36000, 0x3fd314f1,
                    0xd3213cb8, 0xbd28e27a, 0x7cdc9000, 0x3fd2bef0, 0x4a5004f4,
                    0x3d2a9cfa, 0x1134d800, 0x3fd26962, 0xdf5bb3b6, 0x3d2c93c1,
                    0x6d0eb800, 0x3fd21445, 0xba46baea, 0x3d0a87de, 0x635a6800,
                    0x3fd1bf99, 0x5147bdb7, 0x3d2ca6ed, 0xcbacf800, 0x3fd16b5c,
                    0xf7a51681, 0x3d2b9acd, 0x8227e800, 0x3fd1178e, 0x63a5f01c,
                    0xbd2c210e, 0x67616000, 0x3fd0c42d, 0x163ceae9, 0x3d27188b,
                    0x604d5800, 0x3fd07138, 0x16ed4e91, 0x3cf89cdb, 0x5626c800,
                    0x3fd01eae, 0x1485e94a, 0xbd16f08c, 0x6cb3b000, 0x3fcf991c,
                    0xca0cdf30, 0x3d1bcbec, 0xe4dd0000, 0x3fcef5ad, 0x65bb8e11,
                    0xbcca2115, 0xffe71000, 0x3fce530e, 0x6041f430, 0x3cc21227,
                    0xb0d49000, 0x3fcdb13d, 0xf715b035, 0xbd2aff2a, 0xf2656000,
                    0x3fcd1037, 0x75b6f6e4, 0xbd084a7e, 0xc6f01000, 0x3fcc6ffb,
                    0xc5962bd2, 0xbcf1ec72, 0x383be000, 0x3fcbd087, 0x595412b6,
                    0xbd2d4bc4, 0x575bd000, 0x3fcb31d8, 0x4eace1aa, 0xbd0c358d,
                    0x3c8ae000, 0x3fca93ed, 0x50562169, 0xbd287243, 0x07089000,
                    0x3fc9f6c4, 0x6865817a, 0x3d29904d, 0xdcf70000, 0x3fc95a5a,
                    0x58a0ff6f, 0x3d07f228, 0xeb390000, 0x3fc8beaf, 0xaae92cd1,
                    0xbd073d54, 0x6551a000, 0x3fc823c1, 0x9a631e83, 0x3d1e0ddb,
                    0x85445000, 0x3fc7898d, 0x70914305, 0xbd1c6610, 0x8b757000,
                    0x3fc6f012, 0xe59c21e1, 0xbd25118d, 0xbe8c1000, 0x3fc6574e,
                    0x2c3c2e78, 0x3d19cf8b, 0x6b544000, 0x3fc5bf40, 0xeb68981c,
                    0xbd127023, 0xe4a1b000, 0x3fc527e5, 0xe5697dc7, 0x3d2633e8,
                    0x8333b000, 0x3fc4913d, 0x54fdb678, 0x3d258379, 0xa5993000,
                    0x3fc3fb45, 0x7e6a354d, 0xbd2cd1d8, 0xb0159000, 0x3fc365fc,
                    0x234b7289, 0x3cc62fa8, 0x0c868000, 0x3fc2d161, 0xcb81b4a1,
                    0x3d039d6c, 0x2a49c000, 0x3fc23d71, 0x8fd3df5c, 0x3d100d23,
                    0x7e23f000, 0x3fc1aa2b, 0x44389934, 0x3d2ca78e, 0x8227e000,
                    0x3fc1178e, 0xce2d07f2, 0x3d21ef78, 0xb59e4000, 0x3fc08598,
                    0x7009902c, 0xbd27e5dd, 0x39dbe000, 0x3fbfe891, 0x4fa10afd,
                    0xbd2534d6, 0x830a2000, 0x3fbec739, 0xafe645e0, 0xbd2dc068,
                    0x63844000, 0x3fbda727, 0x1fa71733, 0x3d1a8940, 0x01bc4000,
                    0x3fbc8858, 0xc65aacd3, 0x3d2646d1, 0x8dad6000, 0x3fbb6ac8,
                    0x2bf768e5, 0xbd139080, 0x40b1c000, 0x3fba4e76, 0xb94407c8,
                    0xbd0e42b6, 0x5d594000, 0x3fb9335e, 0x3abd47da, 0x3d23115c,
                    0x2f40e000, 0x3fb8197e, 0xf96ffdf7, 0x3d0f80dc, 0x0aeac000,
                    0x3fb700d3, 0xa99ded32, 0x3cec1e8d, 0x4d97a000, 0x3fb5e95a,
                    0x3c5d1d1e, 0xbd2c6906, 0x5d208000, 0x3fb4d311, 0x82f4e1ef,
                    0xbcf53a25, 0xa7d1e000, 0x3fb3bdf5, 0xa5db4ed7, 0x3d2cc85e,
                    0xa4472000, 0x3fb2aa04, 0xae9c697d, 0xbd20b6e8, 0xd1466000,
                    0x3fb1973b, 0x560d9e9b, 0xbd25325d, 0xb59e4000, 0x3fb08598,
                    0x7009902c, 0xbd17e5dd, 0xc006c000, 0x3faeea31, 0x4fc93b7b,
                    0xbd0e113e, 0xcdddc000, 0x3faccb73, 0x47d82807, 0xbd1a68f2,
                    0xd0fb0000, 0x3faaaef2, 0x353bb42e, 0x3d20fc1a, 0x149fc000,
                    0x3fa894aa, 0xd05a267d, 0xbd197995, 0xf2d4c000, 0x3fa67c94,
                    0xec19afa2, 0xbd029efb, 0xd42e0000, 0x3fa466ae, 0x75bdfd28,
                    0xbd2c1673, 0x2f8d0000, 0x3fa252f3, 0xe021b67b, 0x3d283e9a,
                    0x89e74000, 0x3fa0415d, 0x5cf1d753, 0x3d0111c0, 0xec148000,
                    0x3f9c63d2, 0x3f9eb2f3, 0x3d2578c6, 0x28c90000, 0x3f984925,
                    0x325a0c34, 0xbd2aa0ba, 0x25980000, 0x3f9432a9, 0x928637fe,
                    0x3d098139, 0x58938000, 0x3f902056, 0x06e2f7d2, 0xbd23dc5b,
                    0xa3890000, 0x3f882448, 0xda74f640, 0xbd275577, 0x75890000,
                    0x3f801015, 0x999d2be8, 0xbd10c76b, 0x59580000, 0x3f700805,
                    0xcb31c67b, 0x3d2166af, 0x00000000, 0x00000000, 0x00000000,
                    0x80000000
    };

    private static int[] logTwoData = {
                    0xfefa3800, 0x3fa62e42, 0x93c76730, 0x3ceef357
    };

    private static int[] coeffLogTwoData = {
                    0x92492492, 0x3fc24924, 0x00000000, 0xbfd00000, 0x3d6fb175,
                    0xbfc5555e, 0x55555555, 0x3fd55555, 0x9999999a, 0x3fc99999,
                    0x00000000, 0xbfe00000
    };

    /*
     * Copyright (c) 2014, 2016, Intel Corporation. All rights reserved. Intel Math Library (LIBM)
     * Source Code
     *
     * ALGORITHM DESCRIPTION - LOG() ---------------------
     *
     * x=2^k * mx, mx in [1,2)
     *
     * Get B~1/mx based on the output of rcpps instruction (B0) B = int((B0*2^7+0.5))/2^7
     *
     * Reduced argument: r=B*mx-1.0 (computed accurately in high and low parts)
     *
     * Result: k*log(2) - log(B) + p(r) if |x-1| >= small value (2^-6) and p(r) is a degree 7
     * polynomial -log(B) read from data table (high, low parts) Result is formed from high and low
     * parts.
     *
     * Special cases: log(NaN) = quiet NaN, and raise invalid exception log(+INF) = that INF log(0)
     * = -INF with divide-by-zero exception raised log(1) = +0 log(x) = NaN with invalid exception
     * raised if x < -0, including -INF
     *
     */

    public void logIntrinsic(Register dest, Register value, CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        ArrayDataPointerConstant logTwoTablePtr = new ArrayDataPointerConstant(logTwoTable, 16);
        ArrayDataPointerConstant logTwoDataPtr = new ArrayDataPointerConstant(logTwoData, 16);
        ArrayDataPointerConstant coeffLogTwoDataPtr = new ArrayDataPointerConstant(coeffLogTwoData, 16);

        Label bb0 = new Label();
        Label bb1 = new Label();
        Label bb2 = new Label();
        Label bb3 = new Label();
        Label bb4 = new Label();
        Label bb5 = new Label();
        Label bb6 = new Label();
        Label bb7 = new Label();
        Label bb8 = new Label();

        Register gpr1 = asRegister(gpr1Temp, AMD64Kind.QWORD);
        Register gpr2 = asRegister(gpr2Temp, AMD64Kind.QWORD);
        Register gpr3 = asRegister(rcxTemp, AMD64Kind.QWORD);
        Register gpr4 = asRegister(gpr4Temp, AMD64Kind.QWORD);

        Register temp1 = asRegister(xmm1Temp, AMD64Kind.DOUBLE);
        Register temp2 = asRegister(xmm2Temp, AMD64Kind.DOUBLE);
        Register temp3 = asRegister(xmm3Temp, AMD64Kind.DOUBLE);
        Register temp4 = asRegister(xmm4Temp, AMD64Kind.DOUBLE);
        Register temp5 = asRegister(xmm5Temp, AMD64Kind.DOUBLE);
        Register temp6 = asRegister(xmm6Temp, AMD64Kind.DOUBLE);
        Register temp7 = asRegister(xmm7Temp, AMD64Kind.DOUBLE);

        AMD64Address stackSlot = (AMD64Address) crb.asAddress(stackTemp);

        setCrb(crb);
        masm.movdq(stackSlot, value);
        if (dest.encoding != value.encoding) {
            masm.movdqu(dest, value);
        }
        masm.movq(gpr1, 0x3ff0000000000000L);
        masm.movdq(temp2, gpr1);
        masm.movq(gpr3, 0x77f0000000000000L);
        masm.movdq(temp3, gpr3);
        masm.movl(gpr2, 32768);
        masm.movdl(temp4, gpr2);
        masm.movq(gpr2, 0xffffe00000000000L);
        masm.movdq(temp5, gpr2);
        masm.movdqu(temp1, value);
        masm.pextrw(gpr1, dest, 3);
        masm.por(dest, temp2);
        masm.movl(gpr2, 16352);
        masm.psrlq(dest, 27);
        masm.leaq(gpr4, externalAddress(logTwoTablePtr));
        masm.psrld(dest, 2);
        masm.rcpps(dest, dest);
        masm.psllq(temp1, 12);
        masm.pshufd(temp6, temp5, 0xE4);
        masm.psrlq(temp1, 12);
        masm.subl(gpr1, 16);
        masm.cmpl(gpr1, 32736);
        masm.jcc(ConditionFlag.AboveEqual, bb0);

        masm.bind(bb1);
        masm.paddd(dest, temp4);
        masm.por(temp1, temp3);
        masm.movdl(gpr3, dest);
        masm.psllq(dest, 29);
        masm.pand(temp5, temp1);
        masm.pand(dest, temp6);
        masm.subsd(temp1, temp5);
        masm.mulpd(temp5, dest);
        masm.andl(gpr1, 32752);
        masm.subl(gpr1, gpr2);
        masm.cvtsi2sdl(temp7, gpr1);
        masm.mulsd(temp1, dest);
        masm.movdq(temp6, externalAddress(logTwoDataPtr));                                    // 0xfefa3800,
                                                                                              // 0x3fa62e42
        masm.movdqu(temp3, externalAddress(coeffLogTwoDataPtr));                              // 0x92492492,
                                                                                              // 0x3fc24924,
                                                                                              // 0x00000000,
                                                                                              // 0xbfd00000
        masm.subsd(temp5, temp2);
        masm.andl(gpr3, 16711680);
        masm.shrl(gpr3, 12);
        masm.movdqu(dest, new AMD64Address(gpr4, gpr3, Scale.Times1, 0));
        masm.leaq(gpr4, externalAddress(coeffLogTwoDataPtr));
        masm.movdqu(temp4, new AMD64Address(gpr4, 16));                                       // 0x3d6fb175,
                                                                                              // 0xbfc5555e,
                                                                                              // 0x55555555,
                                                                                              // 0x3fd55555
        masm.addsd(temp1, temp5);
        masm.movdqu(temp2, new AMD64Address(gpr4, 32));                                       // 0x9999999a,
                                                                                              // 0x3fc99999,
                                                                                              // 0x00000000,
                                                                                              // 0xbfe00000
        masm.mulsd(temp6, temp7);
        if (masm.supports(CPUFeature.SSE3)) {
            masm.movddup(temp5, temp1);
        } else {
            masm.movdqu(temp5, temp1);
            masm.movlhps(temp5, temp5);
        }
        masm.leaq(gpr4, externalAddress(logTwoDataPtr));
        masm.mulsd(temp7, new AMD64Address(gpr4, 8));                                         // 0x93c76730,
                                                                                              // 0x3ceef357
        masm.mulsd(temp3, temp1);
        masm.addsd(dest, temp6);
        masm.mulpd(temp4, temp5);
        masm.mulpd(temp5, temp5);
        if (masm.supports(CPUFeature.SSE3)) {
            masm.movddup(temp6, dest);
        } else {
            masm.movdqu(temp6, dest);
            masm.movlhps(temp6, temp6);
        }
        masm.addsd(dest, temp1);
        masm.addpd(temp4, temp2);
        masm.mulpd(temp3, temp5);
        masm.subsd(temp6, dest);
        masm.mulsd(temp4, temp1);
        masm.pshufd(temp2, dest, 0xEE);
        masm.addsd(temp1, temp6);
        masm.mulsd(temp5, temp5);
        masm.addsd(temp7, temp2);
        masm.addpd(temp4, temp3);
        masm.addsd(temp1, temp7);
        masm.mulpd(temp4, temp5);
        masm.addsd(temp1, temp4);
        masm.pshufd(temp5, temp4, 0xEE);
        masm.addsd(temp1, temp5);
        masm.addsd(dest, temp1);
        masm.jmp(bb8);

        masm.bind(bb0);
        masm.movdq(dest, stackSlot);
        masm.movdq(temp1, stackSlot);
        masm.addl(gpr1, 16);
        masm.cmpl(gpr1, 32768);
        masm.jcc(ConditionFlag.AboveEqual, bb2);

        masm.cmpl(gpr1, 16);
        masm.jcc(ConditionFlag.Below, bb3);

        masm.bind(bb4);
        masm.addsd(dest, dest);
        masm.jmp(bb8);

        masm.bind(bb5);
        masm.jcc(ConditionFlag.Above, bb4);

        masm.cmpl(gpr3, 0);
        masm.jcc(ConditionFlag.Above, bb4);

        masm.jmp(bb6);

        masm.bind(bb3);
        masm.xorpd(temp1, temp1);
        masm.addsd(temp1, dest);
        masm.movdl(gpr3, temp1);
        masm.psrlq(temp1, 32);
        masm.movdl(gpr2, temp1);
        masm.orl(gpr3, gpr2);
        masm.cmpl(gpr3, 0);
        masm.jcc(ConditionFlag.Equal, bb7);

        masm.xorpd(temp1, temp1);
        masm.movl(gpr1, 18416);
        masm.pinsrw(temp1, gpr1, 3);
        masm.mulsd(dest, temp1);
        masm.movdqu(temp1, dest);
        masm.pextrw(gpr1, dest, 3);
        masm.por(dest, temp2);
        masm.psrlq(dest, 27);
        masm.movl(gpr2, 18416);
        masm.psrld(dest, 2);
        masm.rcpps(dest, dest);
        masm.psllq(temp1, 12);
        masm.pshufd(temp6, temp5, 0xE4);
        masm.psrlq(temp1, 12);
        masm.jmp(bb1);

        masm.bind(bb2);
        masm.movdl(gpr3, temp1);
        masm.psrlq(temp1, 32);
        masm.movdl(gpr2, temp1);
        masm.addl(gpr2, gpr2);
        masm.cmpl(gpr2, -2097152);
        masm.jcc(ConditionFlag.AboveEqual, bb5);

        masm.orl(gpr3, gpr2);
        masm.cmpl(gpr3, 0);
        masm.jcc(ConditionFlag.Equal, bb7);

        masm.bind(bb6);
        masm.xorpd(temp1, temp1);
        masm.xorpd(dest, dest);
        masm.movl(gpr1, 32752);
        masm.pinsrw(temp1, gpr1, 3);
        masm.mulsd(dest, temp1);
        masm.jmp(bb8);

        masm.bind(bb7);
        masm.xorpd(temp1, temp1);
        masm.xorpd(dest, dest);
        masm.movl(gpr1, 49136);
        masm.pinsrw(dest, gpr1, 3);
        masm.divsd(dest, temp1);

        masm.bind(bb8);
    }

    private static int[] highmaskLogTen = {
                    0xf8000000, 0xffffffff, 0x00000000, 0xffffe000
    };

    private static int[] logTenE = {
                    0x00000000, 0x3fdbc000, 0xbf2e4108, 0x3f5a7a6c
    };

    private static int[] logTenTable = {
                    0x509f7800, 0x3fd34413, 0x1f12b358, 0x3d1fef31, 0x80333400,
                    0x3fd32418, 0xc671d9d0, 0xbcf542bf, 0x51195000, 0x3fd30442,
                    0x78a4b0c3, 0x3d18216a, 0x6fc79400, 0x3fd2e490, 0x80fa389d,
                    0xbc902869, 0x89d04000, 0x3fd2c502, 0x75c2f564, 0x3d040754,
                    0x4ddd1c00, 0x3fd2a598, 0xd219b2c3, 0xbcfa1d84, 0x6baa7c00,
                    0x3fd28651, 0xfd9abec1, 0x3d1be6d3, 0x94028800, 0x3fd2672d,
                    0xe289a455, 0xbd1ede5e, 0x78b86400, 0x3fd2482c, 0x6734d179,
                    0x3d1fe79b, 0xcca3c800, 0x3fd2294d, 0x981a40b8, 0xbced34ea,
                    0x439c5000, 0x3fd20a91, 0xcc392737, 0xbd1a9cc3, 0x92752c00,
                    0x3fd1ebf6, 0x03c9afe7, 0x3d1e98f8, 0x6ef8dc00, 0x3fd1cd7d,
                    0x71dae7f4, 0x3d08a86c, 0x8fe4dc00, 0x3fd1af25, 0xee9185a1,
                    0xbcff3412, 0xace59400, 0x3fd190ee, 0xc2cab353, 0x3cf17ed9,
                    0x7e925000, 0x3fd172d8, 0x6952c1b2, 0x3cf1521c, 0xbe694400,
                    0x3fd154e2, 0xcacb79ca, 0xbd0bdc78, 0x26cbac00, 0x3fd1370d,
                    0xf71f4de1, 0xbd01f8be, 0x72fa0800, 0x3fd11957, 0x55bf910b,
                    0x3c946e2b, 0x5f106000, 0x3fd0fbc1, 0x39e639c1, 0x3d14a84b,
                    0xa802a800, 0x3fd0de4a, 0xd3f31d5d, 0xbd178385, 0x0b992000,
                    0x3fd0c0f3, 0x3843106f, 0xbd1f602f, 0x486ce800, 0x3fd0a3ba,
                    0x8819497c, 0x3cef987a, 0x1de49400, 0x3fd086a0, 0x1caa0467,
                    0x3d0faec7, 0x4c30cc00, 0x3fd069a4, 0xa4424372, 0xbd1618fc,
                    0x94490000, 0x3fd04cc6, 0x946517d2, 0xbd18384b, 0xb7e84000,
                    0x3fd03006, 0xe0109c37, 0xbd19a6ac, 0x798a0c00, 0x3fd01364,
                    0x5121e864, 0xbd164cf7, 0x38ce8000, 0x3fcfedbf, 0x46214d1a,
                    0xbcbbc402, 0xc8e62000, 0x3fcfb4ef, 0xdab93203, 0x3d1e0176,
                    0x2cb02800, 0x3fcf7c5a, 0x2a2ea8e4, 0xbcfec86a, 0xeeeaa000,
                    0x3fcf43fd, 0xc18e49a4, 0x3cf110a8, 0x9bb6e800, 0x3fcf0bda,
                    0x923cc9c0, 0xbd15ce99, 0xc093f000, 0x3fced3ef, 0x4d4b51e9,
                    0x3d1a04c7, 0xec58f800, 0x3fce9c3c, 0x163cad59, 0x3cac8260,
                    0x9a907000, 0x3fce2d7d, 0x3fa93646, 0x3ce4a1c0, 0x37311000,
                    0x3fcdbf99, 0x32abd1fd, 0x3d07ea9d, 0x6744b800, 0x3fcd528c,
                    0x4dcbdfd4, 0xbd1b08e2, 0xe36de800, 0x3fcce653, 0x0b7b7f7f,
                    0xbd1b8f03, 0x77506800, 0x3fcc7aec, 0xa821c9fb, 0x3d13c163,
                    0x00ff8800, 0x3fcc1053, 0x536bca76, 0xbd074ee5, 0x70719800,
                    0x3fcba684, 0xd7da9b6b, 0xbd1fbf16, 0xc6f8d800, 0x3fcb3d7d,
                    0xe2220bb3, 0x3d1a295d, 0x16c15800, 0x3fcad53c, 0xe724911e,
                    0xbcf55822, 0x82533800, 0x3fca6dbc, 0x6d982371, 0x3cac567c,
                    0x3c19e800, 0x3fca06fc, 0x84d17d80, 0x3d1da204, 0x85ef8000,
                    0x3fc9a0f8, 0x54466a6a, 0xbd002204, 0xb0ac2000, 0x3fc93bae,
                    0xd601fd65, 0x3d18840c, 0x1bb9b000, 0x3fc8d71c, 0x7bf58766,
                    0xbd14f897, 0x34aae800, 0x3fc8733e, 0x3af6ac24, 0xbd0f5c45,
                    0x76d68000, 0x3fc81012, 0x4303e1a1, 0xbd1f9a80, 0x6af57800,
                    0x3fc7ad96, 0x43fbcb46, 0x3cf4c33e, 0xa6c51000, 0x3fc74bc7,
                    0x70f0eac5, 0xbd192e3b, 0xccab9800, 0x3fc6eaa3, 0xc0093dfe,
                    0xbd0faf15, 0x8b60b800, 0x3fc68a28, 0xde78d5fd, 0xbc9ea4ee,
                    0x9d987000, 0x3fc62a53, 0x962bea6e, 0xbd194084, 0xc9b0e800,
                    0x3fc5cb22, 0x888dd999, 0x3d1fe201, 0xe1634800, 0x3fc56c93,
                    0x16ada7ad, 0x3d1b1188, 0xc176c000, 0x3fc50ea4, 0x4159b5b5,
                    0xbcf09c08, 0x51766000, 0x3fc4b153, 0x84393d23, 0xbcf6a89c,
                    0x83695000, 0x3fc4549d, 0x9f0b8bbb, 0x3d1c4b8c, 0x538d5800,
                    0x3fc3f881, 0xf49df747, 0x3cf89b99, 0xc8138000, 0x3fc39cfc,
                    0xd503b834, 0xbd13b99f, 0xf0df0800, 0x3fc3420d, 0xf011b386,
                    0xbd05d8be, 0xe7466800, 0x3fc2e7b2, 0xf39c7bc2, 0xbd1bb94e,
                    0xcdd62800, 0x3fc28de9, 0x05e6d69b, 0xbd10ed05, 0xd015d800,
                    0x3fc234b0, 0xe29b6c9d, 0xbd1ff967, 0x224ea800, 0x3fc1dc06,
                    0x727711fc, 0xbcffb30d, 0x01540000, 0x3fc183e8, 0x39786c5a,
                    0x3cc23f57, 0xb24d9800, 0x3fc12c54, 0xc905a342, 0x3d003a1d,
                    0x82835800, 0x3fc0d54a, 0x9b9920c0, 0x3d03b25a, 0xc72ac000,
                    0x3fc07ec7, 0x46f26a24, 0x3cf0fa41, 0xdd35d800, 0x3fc028ca,
                    0x41d9d6dc, 0x3d034a65, 0x52474000, 0x3fbfa6a4, 0x44f66449,
                    0x3d19cad3, 0x2da3d000, 0x3fbefcb8, 0x67832999, 0x3d18400f,
                    0x32a10000, 0x3fbe53ce, 0x9c0e3b1a, 0xbcff62fd, 0x556b7000,
                    0x3fbdabe3, 0x02976913, 0xbcf8243b, 0x97e88000, 0x3fbd04f4,
                    0xec793797, 0x3d1c0578, 0x09647000, 0x3fbc5eff, 0x05fc0565,
                    0xbd1d799e, 0xc6426000, 0x3fbbb9ff, 0x4625f5ed, 0x3d1f5723,
                    0xf7afd000, 0x3fbb15f3, 0xdd5aae61, 0xbd1a7e1e, 0xd358b000,
                    0x3fba72d8, 0x3314e4d3, 0x3d17bc91, 0x9b1f5000, 0x3fb9d0ab,
                    0x9a4d514b, 0x3cf18c9b, 0x9cd4e000, 0x3fb92f69, 0x7e4496ab,
                    0x3cf1f96d, 0x31f4f000, 0x3fb88f10, 0xf56479e7, 0x3d165818,
                    0xbf628000, 0x3fb7ef9c, 0x26bf486d, 0xbd1113a6, 0xb526b000,
                    0x3fb7510c, 0x1a1c3384, 0x3ca9898d, 0x8e31e000, 0x3fb6b35d,
                    0xb3875361, 0xbd0661ac, 0xd01de000, 0x3fb6168c, 0x2a7cacfa,
                    0xbd1bdf10, 0x0af23000, 0x3fb57a98, 0xff868816, 0x3cf046d0,
                    0xd8ea0000, 0x3fb4df7c, 0x1515fbe7, 0xbd1fd529, 0xde3b2000,
                    0x3fb44538, 0x6e59a132, 0x3d1faeee, 0xc8df9000, 0x3fb3abc9,
                    0xf1322361, 0xbd198807, 0x505f1000, 0x3fb3132d, 0x0888e6ab,
                    0x3d1e5380, 0x359bd000, 0x3fb27b61, 0xdfbcbb22, 0xbcfe2724,
                    0x429ee000, 0x3fb1e463, 0x6eb4c58c, 0xbcfe4dd6, 0x4a673000,
                    0x3fb14e31, 0x4ce1ac9b, 0x3d1ba691, 0x28b96000, 0x3fb0b8c9,
                    0x8c7813b8, 0xbd0b3872, 0xc1f08000, 0x3fb02428, 0xc2bc8c2c,
                    0x3cb5ea6b, 0x05a1a000, 0x3faf209c, 0x72e8f18e, 0xbce8df84,
                    0xc0b5e000, 0x3fadfa6d, 0x9fdef436, 0x3d087364, 0xaf416000,
                    0x3facd5c2, 0x1068c3a9, 0x3d0827e7, 0xdb356000, 0x3fabb296,
                    0x120a34d3, 0x3d101a9f, 0x5dfea000, 0x3faa90e6, 0xdaded264,
                    0xbd14c392, 0x6034c000, 0x3fa970ad, 0x1c9d06a9, 0xbd1b705e,
                    0x194c6000, 0x3fa851e8, 0x83996ad9, 0xbd0117bc, 0xcf4ac000,
                    0x3fa73492, 0xb1a94a62, 0xbca5ea42, 0xd67b4000, 0x3fa618a9,
                    0x75aed8ca, 0xbd07119b, 0x9126c000, 0x3fa4fe29, 0x5291d533,
                    0x3d12658f, 0x6f4d4000, 0x3fa3e50e, 0xcd2c5cd9, 0x3d1d5c70,
                    0xee608000, 0x3fa2cd54, 0xd1008489, 0x3d1a4802, 0x9900e000,
                    0x3fa1b6f9, 0x54fb5598, 0xbd16593f, 0x06bb6000, 0x3fa0a1f9,
                    0x64ef57b4, 0xbd17636b, 0xb7940000, 0x3f9f1c9f, 0xee6a4737,
                    0x3cb5d479, 0x91aa0000, 0x3f9cf7f5, 0x3a16373c, 0x3d087114,
                    0x156b8000, 0x3f9ad5ed, 0x836c554a, 0x3c6900b0, 0xd4764000,
                    0x3f98b67f, 0xed12f17b, 0xbcffc974, 0x77dec000, 0x3f9699a7,
                    0x232ce7ea, 0x3d1e35bb, 0xbfbf4000, 0x3f947f5d, 0xd84ffa6e,
                    0x3d0e0a49, 0x82c7c000, 0x3f92679c, 0x8d170e90, 0xbd14d9f2,
                    0xadd20000, 0x3f90525d, 0x86d9f88e, 0x3cdeb986, 0x86f10000,
                    0x3f8c7f36, 0xb9e0a517, 0x3ce29faa, 0xb75c8000, 0x3f885e9e,
                    0x542568cb, 0xbd1f7bdb, 0x46b30000, 0x3f8442e8, 0xb954e7d9,
                    0x3d1e5287, 0xb7e60000, 0x3f802c07, 0x22da0b17, 0xbd19fb27,
                    0x6c8b0000, 0x3f7833e3, 0x821271ef, 0xbd190f96, 0x29910000,
                    0x3f701936, 0xbc3491a5, 0xbd1bcf45, 0x354a0000, 0x3f600fe3,
                    0xc0ff520a, 0xbd19d71c, 0x00000000, 0x00000000, 0x00000000,
                    0x00000000
    };

    private static int[] logTwoLogTenData = {
                    0x509f7800, 0x3f934413, 0x1f12b358, 0x3cdfef31
    };

    private static int[] coeffLogTenData = {
                    0xc1a5f12e, 0x40358874, 0x64d4ef0d, 0xc0089309, 0x385593b1,
                    0xc025c917, 0xdc963467, 0x3ffc6a02, 0x7f9d3aa1, 0x4016ab9f,
                    0xdc77b115, 0xbff27af2
    };

    /*
     * Copyright (c) 2014, 2016, Intel Corporation. All rights reserved. Intel Math Library (LIBM)
     * Source Code
     *
     * ALGORITHM DESCRIPTION - LOG10() ---------------------
     *
     * Let x=2^k * mx, mx in [1,2)
     *
     * Get B~1/mx based on the output of rcpss instruction (B0) B = int((B0*LH*2^7+0.5))/2^7 LH is a
     * short approximation for log10(e)
     *
     * Reduced argument: r=B*mx-LH (computed accurately in high and low parts)
     *
     * Result: k*log10(2) - log(B) + p(r) p(r) is a degree 7 polynomial -log(B) read from data table
     * (high, low parts) Result is formed from high and low parts
     *
     * Special cases: log10(0) = -INF with divide-by-zero exception raised log10(1) = +0 log10(x) =
     * NaN with invalid exception raised if x < -0, including -INF log10(+INF) = +INF
     *
     */

    public void log10Intrinsic(Register dest, Register value, CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        ArrayDataPointerConstant highmaskLogTenPtr = new ArrayDataPointerConstant(highmaskLogTen, 16);
        ArrayDataPointerConstant logTenEPtr = new ArrayDataPointerConstant(logTenE, 16);
        ArrayDataPointerConstant logTenTablePtr = new ArrayDataPointerConstant(logTenTable, 16);
        ArrayDataPointerConstant logTwoLogTenDataPtr = new ArrayDataPointerConstant(logTwoLogTenData, 16);
        ArrayDataPointerConstant coeffLogTenDataPtr = new ArrayDataPointerConstant(coeffLogTenData, 16);

        Label bb0 = new Label();
        Label bb1 = new Label();
        Label bb2 = new Label();
        Label bb3 = new Label();
        Label bb4 = new Label();
        Label bb5 = new Label();
        Label bb6 = new Label();
        Label bb7 = new Label();
        Label bb8 = new Label();

        Register gpr1 = asRegister(gpr1Temp, AMD64Kind.QWORD);
        Register gpr2 = asRegister(gpr2Temp, AMD64Kind.QWORD);
        Register gpr3 = asRegister(rcxTemp, AMD64Kind.QWORD);
        Register gpr4 = asRegister(gpr4Temp, AMD64Kind.QWORD);

        Register temp1 = asRegister(xmm1Temp, AMD64Kind.DOUBLE);
        Register temp2 = asRegister(xmm2Temp, AMD64Kind.DOUBLE);
        Register temp3 = asRegister(xmm3Temp, AMD64Kind.DOUBLE);
        Register temp4 = asRegister(xmm4Temp, AMD64Kind.DOUBLE);
        Register temp5 = asRegister(xmm5Temp, AMD64Kind.DOUBLE);
        Register temp6 = asRegister(xmm6Temp, AMD64Kind.DOUBLE);
        Register temp7 = asRegister(xmm7Temp, AMD64Kind.DOUBLE);

        AMD64Address stackSlot = (AMD64Address) crb.asAddress(stackTemp);

        setCrb(crb);
        masm.movdq(stackSlot, value);
        if (dest.encoding != value.encoding) {
            masm.movdqu(dest, value);
        }
        masm.movdqu(temp5, externalAddress(highmaskLogTenPtr));                               // 0xf8000000,
                                                                                              // 0xffffffff,
                                                                                              // 0x00000000,
                                                                                              // 0xffffe000
        masm.xorpd(temp2, temp2);
        masm.movl(gpr1, 16368);
        masm.pinsrw(temp2, gpr1, 3);
        masm.movl(gpr2, 1054736384);
        masm.movdl(temp7, gpr2);
        masm.xorpd(temp3, temp3);
        masm.movl(gpr3, 30704);
        masm.pinsrw(temp3, gpr3, 3);
        masm.movl(gpr3, 32768);
        masm.movdl(temp4, gpr3);
        masm.movdqu(temp1, value);
        masm.pextrw(gpr1, dest, 3);
        masm.por(dest, temp2);
        masm.movl(gpr2, 16352);
        masm.psrlq(dest, 27);
        masm.movdqu(temp2, externalAddress(logTenEPtr));                                      // 0x00000000,
                                                                                              // 0x3fdbc000,
                                                                                              // 0xbf2e4108,
                                                                                              // 0x3f5a7a6c
        masm.psrld(dest, 2);
        masm.rcpps(dest, dest);
        masm.psllq(temp1, 12);
        masm.pshufd(temp6, temp5, 0x4E);
        masm.psrlq(temp1, 12);
        masm.subl(gpr1, 16);
        masm.cmpl(gpr1, 32736);
        masm.jcc(ConditionFlag.AboveEqual, bb0);

        masm.bind(bb1);
        masm.mulss(dest, temp7);
        masm.por(temp1, temp3);
        masm.andpd(temp5, temp1);
        masm.paddd(dest, temp4);
        masm.movdqu(temp3, externalAddress(coeffLogTenDataPtr));                              // 0xc1a5f12e,
                                                                                              // 0x40358874,
                                                                                              // 0x64d4ef0d,
                                                                                              // 0xc0089309
        masm.leaq(gpr4, externalAddress(coeffLogTenDataPtr));
        masm.movdqu(temp4, new AMD64Address(gpr4, 16));                                       // 0x385593b1,
                                                                                              // 0xc025c917,
                                                                                              // 0xdc963467,
                                                                                              // 0x3ffc6a02
        masm.subsd(temp1, temp5);
        masm.movdl(gpr3, dest);
        masm.psllq(dest, 29);
        masm.andpd(dest, temp6);
        masm.movdq(temp6, externalAddress(logTwoLogTenDataPtr));                              // 0x509f7800,
                                                                                              // 0x3f934413
        masm.andl(gpr1, 32752);
        masm.subl(gpr1, gpr2);
        masm.cvtsi2sdl(temp7, gpr1);
        masm.mulpd(temp5, dest);
        masm.mulsd(temp1, dest);
        masm.subsd(temp5, temp2);
        masm.movdqu(temp2, new AMD64Address(gpr4, 32));                                       // 0x7f9d3aa1,
                                                                                              // 0x4016ab9f,
                                                                                              // 0xdc77b115,
                                                                                              // 0xbff27af2
        masm.leaq(gpr4, externalAddress(logTenTablePtr));
        masm.andl(gpr3, 16711680);
        masm.shrl(gpr3, 12);
        masm.movdqu(dest, new AMD64Address(gpr4, gpr3, Scale.Times1, -1504));
        masm.addsd(temp1, temp5);
        masm.mulsd(temp6, temp7);
        masm.pshufd(temp5, temp1, 0x44);
        masm.leaq(gpr4, externalAddress(logTwoLogTenDataPtr));
        masm.mulsd(temp7, new AMD64Address(gpr4, 8));                                         // 0x1f12b358,
                                                                                              // 0x3cdfef31
        masm.mulsd(temp3, temp1);
        masm.addsd(dest, temp6);
        masm.mulpd(temp4, temp5);
        masm.leaq(gpr4, externalAddress(logTenEPtr));
        masm.movdq(temp6, new AMD64Address(gpr4, 8));                                         // 0xbf2e4108,
                                                                                              // 0x3f5a7a6c
        masm.mulpd(temp5, temp5);
        masm.addpd(temp4, temp2);
        masm.mulpd(temp3, temp5);
        masm.pshufd(temp2, dest, 0xE4);
        masm.addsd(dest, temp1);
        masm.mulsd(temp4, temp1);
        masm.subsd(temp2, dest);
        masm.mulsd(temp6, temp1);
        masm.addsd(temp1, temp2);
        masm.pshufd(temp2, dest, 0xEE);
        masm.mulsd(temp5, temp5);
        masm.addsd(temp7, temp2);
        masm.addsd(temp1, temp6);
        masm.addpd(temp4, temp3);
        masm.addsd(temp1, temp7);
        masm.mulpd(temp4, temp5);
        masm.addsd(temp1, temp4);
        masm.pshufd(temp5, temp4, 0xEE);
        masm.addsd(temp1, temp5);
        masm.addsd(dest, temp1);
        masm.jmp(bb8);

        masm.bind(bb0);
        masm.movdq(dest, stackSlot);
        masm.movdq(temp1, stackSlot);
        masm.addl(gpr1, 16);
        masm.cmpl(gpr1, 32768);
        masm.jcc(ConditionFlag.AboveEqual, bb2);

        masm.cmpl(gpr1, 16);
        masm.jcc(ConditionFlag.Below, bb3);

        masm.bind(bb4);
        masm.addsd(dest, dest);
        masm.jmp(bb8);

        masm.bind(bb5);
        masm.jcc(ConditionFlag.Above, bb4);

        masm.cmpl(gpr3, 0);
        masm.jcc(ConditionFlag.Above, bb4);

        masm.jmp(bb6);

        masm.bind(bb3);
        masm.xorpd(temp1, temp1);
        masm.addsd(temp1, dest);
        masm.movdl(gpr3, temp1);
        masm.psrlq(temp1, 32);
        masm.movdl(gpr2, temp1);
        masm.orl(gpr3, gpr2);
        masm.cmpl(gpr3, 0);
        masm.jcc(ConditionFlag.Equal, bb7);

        masm.xorpd(temp1, temp1);
        masm.xorpd(temp2, temp2);
        masm.movl(gpr1, 18416);
        masm.pinsrw(temp1, gpr1, 3);
        masm.mulsd(dest, temp1);
        masm.movl(gpr1, 16368);
        masm.pinsrw(temp2, gpr1, 3);
        masm.movdqu(temp1, dest);
        masm.pextrw(gpr1, dest, 3);
        masm.por(dest, temp2);
        masm.movl(gpr2, 18416);
        masm.psrlq(dest, 27);
        masm.movdqu(temp2, externalAddress(logTenEPtr));                                      // 0x00000000,
                                                                                              // 0x3fdbc000,
                                                                                              // 0xbf2e4108,
                                                                                              // 0x3f5a7a6c
        masm.psrld(dest, 2);
        masm.rcpps(dest, dest);
        masm.psllq(temp1, 12);
        masm.pshufd(temp6, temp5, 0x4E);
        masm.psrlq(temp1, 12);
        masm.jmp(bb1);

        masm.bind(bb2);
        masm.movdl(gpr3, temp1);
        masm.psrlq(temp1, 32);
        masm.movdl(gpr2, temp1);
        masm.addl(gpr2, gpr2);
        masm.cmpl(gpr2, -2097152);
        masm.jcc(ConditionFlag.AboveEqual, bb5);

        masm.orl(gpr3, gpr2);
        masm.cmpl(gpr3, 0);
        masm.jcc(ConditionFlag.Equal, bb7);

        masm.bind(bb6);
        masm.xorpd(temp1, temp1);
        masm.xorpd(dest, dest);
        masm.movl(gpr1, 32752);
        masm.pinsrw(temp1, gpr1, 3);
        masm.mulsd(dest, temp1);
        masm.jmp(bb8);

        masm.bind(bb7);
        masm.xorpd(temp1, temp1);
        masm.xorpd(dest, dest);
        masm.movl(gpr1, 49136);
        masm.pinsrw(dest, gpr1, 3);
        masm.divsd(dest, temp1);

        masm.bind(bb8);
    }

    /*
     * Copyright (c) 2014, 2016, Intel Corporation. All rights reserved. Intel Math Library (LIBM)
     * Source Code
     *
     * ALGORITHM DESCRIPTION - SIN() ---------------------
     *
     * 1. RANGE REDUCTION
     *
     * We perform an initial range reduction from X to r with
     *
     * X =~= N * pi/32 + r
     *
     * so that |r| <= pi/64 + epsilon. We restrict inputs to those where |N| <= 932560. Beyond this,
     * the range reduction is insufficiently accurate. For extremely small inputs, denormalization
     * can occur internally, impacting performance. This means that the main path is actually only
     * taken for 2^-252 <= |X| < 90112.
     *
     * To avoid branches, we perform the range reduction to full accuracy each time.
     *
     * X - N * (P_1 + P_2 + P_3)
     *
     * where P_1 and P_2 are 32-bit numbers (so multiplication by N is exact) and P_3 is a 53-bit
     * number. Together, these approximate pi well enough for all cases in the restricted range.
     *
     * The main reduction sequence is:
     *
     * y = 32/pi * x N = integer(y) (computed by adding and subtracting off SHIFTER)
     *
     * m_1 = N * P_1 m_2 = N * P_2 r_1 = x - m_1 r = r_1 - m_2 (this r can be used for most of the
     * calculation)
     *
     * c_1 = r_1 - r m_3 = N * P_3 c_2 = c_1 - m_2 c = c_2 - m_3
     *
     * 2. MAIN ALGORITHM
     *
     * The algorithm uses a table lookup based on B = M * pi / 32 where M = N mod 64. The stored
     * values are: sigma closest power of 2 to cos(B) C_hl 53-bit cos(B) - sigma S_hi + S_lo 2 *
     * 53-bit sin(B)
     *
     * The computation is organized as follows:
     *
     * sin(B + r + c) = [sin(B) + sigma * r] + r * (cos(B) - sigma) + sin(B) * [cos(r + c) - 1] +
     * cos(B) * [sin(r + c) - r]
     *
     * which is approximately:
     *
     * [S_hi + sigma * r] + C_hl * r + S_lo + S_hi * [(cos(r) - 1) - r * c] + (C_hl + sigma) *
     * [(sin(r) - r) + c]
     *
     * and this is what is actually computed. We separate this sum into four parts:
     *
     * hi + med + pols + corr
     *
     * where
     *
     * hi = S_hi + sigma r med = C_hl * r pols = S_hi * (cos(r) - 1) + (C_hl + sigma) * (sin(r) - r)
     * corr = S_lo + c * ((C_hl + sigma) - S_hi * r)
     *
     * 3. POLYNOMIAL
     *
     * The polynomial S_hi * (cos(r) - 1) + (C_hl + sigma) * (sin(r) - r) can be rearranged freely,
     * since it is quite small, so we exploit parallelism to the fullest.
     *
     * psc4 = SC_4 * r_1 msc4 = psc4 * r r2 = r * r msc2 = SC_2 * r2 r4 = r2 * r2 psc3 = SC_3 + msc4
     * psc1 = SC_1 + msc2 msc3 = r4 * psc3 sincospols = psc1 + msc3 pols = sincospols * <S_hi * r^2
     * | (C_hl + sigma) * r^3>
     *
     * 4. CORRECTION TERM
     *
     * This is where the "c" component of the range reduction is taken into account; recall that
     * just "r" is used for most of the calculation.
     *
     * -c = m_3 - c_2 -d = S_hi * r - (C_hl + sigma) corr = -c * -d + S_lo
     *
     * 5. COMPENSATED SUMMATIONS
     *
     * The two successive compensated summations add up the high and medium parts, leaving just the
     * low parts to add up at the end.
     *
     * rs = sigma * r res_int = S_hi + rs k_0 = S_hi - res_int k_2 = k_0 + rs med = C_hl * r res_hi
     * = res_int + med k_1 = res_int - res_hi k_3 = k_1 + med
     *
     * 6. FINAL SUMMATION
     *
     * We now add up all the small parts:
     *
     * res_lo = pols(hi) + pols(lo) + corr + k_1 + k_3
     *
     * Now the overall result is just:
     *
     * res_hi + res_lo
     *
     * 7. SMALL ARGUMENTS
     *
     * If |x| < SNN (SNN meaning the smallest normal number), we simply perform 0.1111111 cdots 1111
     * * x. For SNN <= |x|, we do 2^-55 * (2^55 * x - x).
     *
     * Special cases: sin(NaN) = quiet NaN, and raise invalid exception sin(INF) = NaN and raise
     * invalid exception sin(+/-0) = +/-0
     *
     */

    public int[] oneHalf = {
                    0x00000000, 0x3fe00000, 0x00000000, 0x3fe00000
    };

    public int[] pTwo = {
                    0x1a600000, 0x3d90b461, 0x1a600000, 0x3d90b461
    };

    public int[] scFour = {
                    0xa556c734, 0x3ec71de3, 0x1a01a01a, 0x3efa01a0
    };

    public int[] cTable = {
                    0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
                    0x00000000, 0x00000000, 0x3ff00000, 0x176d6d31, 0xbf73b92e,
                    0xbc29b42c, 0x3fb917a6, 0xe0000000, 0xbc3e2718, 0x00000000,
                    0x3ff00000, 0x011469fb, 0xbf93ad06, 0x3c69a60b, 0x3fc8f8b8,
                    0xc0000000, 0xbc626d19, 0x00000000, 0x3ff00000, 0x939d225a,
                    0xbfa60bea, 0x2ed59f06, 0x3fd29406, 0xa0000000, 0xbc75d28d,
                    0x00000000, 0x3ff00000, 0x866b95cf, 0xbfb37ca1, 0xa6aea963,
                    0x3fd87de2, 0xe0000000, 0xbc672ced, 0x00000000, 0x3ff00000,
                    0x73fa1279, 0xbfbe3a68, 0x3806f63b, 0x3fde2b5d, 0x20000000,
                    0x3c5e0d89, 0x00000000, 0x3ff00000, 0x5bc57974, 0xbfc59267,
                    0x39ae68c8, 0x3fe1c73b, 0x20000000, 0x3c8b25dd, 0x00000000,
                    0x3ff00000, 0x53aba2fd, 0xbfcd0dfe, 0x25091dd6, 0x3fe44cf3,
                    0x20000000, 0x3c68076a, 0x00000000, 0x3ff00000, 0x99fcef32,
                    0x3fca8279, 0x667f3bcd, 0x3fe6a09e, 0x20000000, 0xbc8bdd34,
                    0x00000000, 0x3fe00000, 0x94247758, 0x3fc133cc, 0x6b151741,
                    0x3fe8bc80, 0x20000000, 0xbc82c5e1, 0x00000000, 0x3fe00000,
                    0x9ae68c87, 0x3fac73b3, 0x290ea1a3, 0x3fea9b66, 0xe0000000,
                    0x3c39f630, 0x00000000, 0x3fe00000, 0x7f909c4e, 0xbf9d4a2c,
                    0xf180bdb1, 0x3fec38b2, 0x80000000, 0xbc76e0b1, 0x00000000,
                    0x3fe00000, 0x65455a75, 0xbfbe0875, 0xcf328d46, 0x3fed906b,
                    0x20000000, 0x3c7457e6, 0x00000000, 0x3fe00000, 0x76acf82d,
                    0x3fa4a031, 0x56c62dda, 0x3fee9f41, 0xe0000000, 0x3c8760b1,
                    0x00000000, 0x3fd00000, 0x0e5967d5, 0xbfac1d1f, 0xcff75cb0,
                    0x3fef6297, 0x20000000, 0x3c756217, 0x00000000, 0x3fd00000,
                    0x0f592f50, 0xbf9ba165, 0xa3d12526, 0x3fefd88d, 0x40000000,
                    0xbc887df6, 0x00000000, 0x3fc00000, 0x00000000, 0x00000000,
                    0x00000000, 0x3ff00000, 0x00000000, 0x00000000, 0x00000000,
                    0x00000000, 0x0f592f50, 0x3f9ba165, 0xa3d12526, 0x3fefd88d,
                    0x40000000, 0xbc887df6, 0x00000000, 0xbfc00000, 0x0e5967d5,
                    0x3fac1d1f, 0xcff75cb0, 0x3fef6297, 0x20000000, 0x3c756217,
                    0x00000000, 0xbfd00000, 0x76acf82d, 0xbfa4a031, 0x56c62dda,
                    0x3fee9f41, 0xe0000000, 0x3c8760b1, 0x00000000, 0xbfd00000,
                    0x65455a75, 0x3fbe0875, 0xcf328d46, 0x3fed906b, 0x20000000,
                    0x3c7457e6, 0x00000000, 0xbfe00000, 0x7f909c4e, 0x3f9d4a2c,
                    0xf180bdb1, 0x3fec38b2, 0x80000000, 0xbc76e0b1, 0x00000000,
                    0xbfe00000, 0x9ae68c87, 0xbfac73b3, 0x290ea1a3, 0x3fea9b66,
                    0xe0000000, 0x3c39f630, 0x00000000, 0xbfe00000, 0x94247758,
                    0xbfc133cc, 0x6b151741, 0x3fe8bc80, 0x20000000, 0xbc82c5e1,
                    0x00000000, 0xbfe00000, 0x99fcef32, 0xbfca8279, 0x667f3bcd,
                    0x3fe6a09e, 0x20000000, 0xbc8bdd34, 0x00000000, 0xbfe00000,
                    0x53aba2fd, 0x3fcd0dfe, 0x25091dd6, 0x3fe44cf3, 0x20000000,
                    0x3c68076a, 0x00000000, 0xbff00000, 0x5bc57974, 0x3fc59267,
                    0x39ae68c8, 0x3fe1c73b, 0x20000000, 0x3c8b25dd, 0x00000000,
                    0xbff00000, 0x73fa1279, 0x3fbe3a68, 0x3806f63b, 0x3fde2b5d,
                    0x20000000, 0x3c5e0d89, 0x00000000, 0xbff00000, 0x866b95cf,
                    0x3fb37ca1, 0xa6aea963, 0x3fd87de2, 0xe0000000, 0xbc672ced,
                    0x00000000, 0xbff00000, 0x939d225a, 0x3fa60bea, 0x2ed59f06,
                    0x3fd29406, 0xa0000000, 0xbc75d28d, 0x00000000, 0xbff00000,
                    0x011469fb, 0x3f93ad06, 0x3c69a60b, 0x3fc8f8b8, 0xc0000000,
                    0xbc626d19, 0x00000000, 0xbff00000, 0x176d6d31, 0x3f73b92e,
                    0xbc29b42c, 0x3fb917a6, 0xe0000000, 0xbc3e2718, 0x00000000,
                    0xbff00000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
                    0x00000000, 0x00000000, 0x00000000, 0xbff00000, 0x176d6d31,
                    0x3f73b92e, 0xbc29b42c, 0xbfb917a6, 0xe0000000, 0x3c3e2718,
                    0x00000000, 0xbff00000, 0x011469fb, 0x3f93ad06, 0x3c69a60b,
                    0xbfc8f8b8, 0xc0000000, 0x3c626d19, 0x00000000, 0xbff00000,
                    0x939d225a, 0x3fa60bea, 0x2ed59f06, 0xbfd29406, 0xa0000000,
                    0x3c75d28d, 0x00000000, 0xbff00000, 0x866b95cf, 0x3fb37ca1,
                    0xa6aea963, 0xbfd87de2, 0xe0000000, 0x3c672ced, 0x00000000,
                    0xbff00000, 0x73fa1279, 0x3fbe3a68, 0x3806f63b, 0xbfde2b5d,
                    0x20000000, 0xbc5e0d89, 0x00000000, 0xbff00000, 0x5bc57974,
                    0x3fc59267, 0x39ae68c8, 0xbfe1c73b, 0x20000000, 0xbc8b25dd,
                    0x00000000, 0xbff00000, 0x53aba2fd, 0x3fcd0dfe, 0x25091dd6,
                    0xbfe44cf3, 0x20000000, 0xbc68076a, 0x00000000, 0xbff00000,
                    0x99fcef32, 0xbfca8279, 0x667f3bcd, 0xbfe6a09e, 0x20000000,
                    0x3c8bdd34, 0x00000000, 0xbfe00000, 0x94247758, 0xbfc133cc,
                    0x6b151741, 0xbfe8bc80, 0x20000000, 0x3c82c5e1, 0x00000000,
                    0xbfe00000, 0x9ae68c87, 0xbfac73b3, 0x290ea1a3, 0xbfea9b66,
                    0xe0000000, 0xbc39f630, 0x00000000, 0xbfe00000, 0x7f909c4e,
                    0x3f9d4a2c, 0xf180bdb1, 0xbfec38b2, 0x80000000, 0x3c76e0b1,
                    0x00000000, 0xbfe00000, 0x65455a75, 0x3fbe0875, 0xcf328d46,
                    0xbfed906b, 0x20000000, 0xbc7457e6, 0x00000000, 0xbfe00000,
                    0x76acf82d, 0xbfa4a031, 0x56c62dda, 0xbfee9f41, 0xe0000000,
                    0xbc8760b1, 0x00000000, 0xbfd00000, 0x0e5967d5, 0x3fac1d1f,
                    0xcff75cb0, 0xbfef6297, 0x20000000, 0xbc756217, 0x00000000,
                    0xbfd00000, 0x0f592f50, 0x3f9ba165, 0xa3d12526, 0xbfefd88d,
                    0x40000000, 0x3c887df6, 0x00000000, 0xbfc00000, 0x00000000,
                    0x00000000, 0x00000000, 0xbff00000, 0x00000000, 0x00000000,
                    0x00000000, 0x00000000, 0x0f592f50, 0xbf9ba165, 0xa3d12526,
                    0xbfefd88d, 0x40000000, 0x3c887df6, 0x00000000, 0x3fc00000,
                    0x0e5967d5, 0xbfac1d1f, 0xcff75cb0, 0xbfef6297, 0x20000000,
                    0xbc756217, 0x00000000, 0x3fd00000, 0x76acf82d, 0x3fa4a031,
                    0x56c62dda, 0xbfee9f41, 0xe0000000, 0xbc8760b1, 0x00000000,
                    0x3fd00000, 0x65455a75, 0xbfbe0875, 0xcf328d46, 0xbfed906b,
                    0x20000000, 0xbc7457e6, 0x00000000, 0x3fe00000, 0x7f909c4e,
                    0xbf9d4a2c, 0xf180bdb1, 0xbfec38b2, 0x80000000, 0x3c76e0b1,
                    0x00000000, 0x3fe00000, 0x9ae68c87, 0x3fac73b3, 0x290ea1a3,
                    0xbfea9b66, 0xe0000000, 0xbc39f630, 0x00000000, 0x3fe00000,
                    0x94247758, 0x3fc133cc, 0x6b151741, 0xbfe8bc80, 0x20000000,
                    0x3c82c5e1, 0x00000000, 0x3fe00000, 0x99fcef32, 0x3fca8279,
                    0x667f3bcd, 0xbfe6a09e, 0x20000000, 0x3c8bdd34, 0x00000000,
                    0x3fe00000, 0x53aba2fd, 0xbfcd0dfe, 0x25091dd6, 0xbfe44cf3,
                    0x20000000, 0xbc68076a, 0x00000000, 0x3ff00000, 0x5bc57974,
                    0xbfc59267, 0x39ae68c8, 0xbfe1c73b, 0x20000000, 0xbc8b25dd,
                    0x00000000, 0x3ff00000, 0x73fa1279, 0xbfbe3a68, 0x3806f63b,
                    0xbfde2b5d, 0x20000000, 0xbc5e0d89, 0x00000000, 0x3ff00000,
                    0x866b95cf, 0xbfb37ca1, 0xa6aea963, 0xbfd87de2, 0xe0000000,
                    0x3c672ced, 0x00000000, 0x3ff00000, 0x939d225a, 0xbfa60bea,
                    0x2ed59f06, 0xbfd29406, 0xa0000000, 0x3c75d28d, 0x00000000,
                    0x3ff00000, 0x011469fb, 0xbf93ad06, 0x3c69a60b, 0xbfc8f8b8,
                    0xc0000000, 0x3c626d19, 0x00000000, 0x3ff00000, 0x176d6d31,
                    0xbf73b92e, 0xbc29b42c, 0xbfb917a6, 0xe0000000, 0x3c3e2718,
                    0x00000000, 0x3ff00000
    };

    public int[] scTwo = {
                    0x11111111, 0x3f811111, 0x55555555, 0x3fa55555
    };

    public int[] scThree = {
                    0x1a01a01a, 0xbf2a01a0, 0x16c16c17, 0xbf56c16c
    };

    public int[] scOne = {
                    0x55555555, 0xbfc55555, 0x00000000, 0xbfe00000
    };

    public int[] piInvTable = {
                    0x00000000, 0x00000000, 0xa2f9836e, 0x4e441529, 0xfc2757d1,
                    0xf534ddc0, 0xdb629599, 0x3c439041, 0xfe5163ab, 0xdebbc561,
                    0xb7246e3a, 0x424dd2e0, 0x06492eea, 0x09d1921c, 0xfe1deb1c,
                    0xb129a73e, 0xe88235f5, 0x2ebb4484, 0xe99c7026, 0xb45f7e41,
                    0x3991d639, 0x835339f4, 0x9c845f8b, 0xbdf9283b, 0x1ff897ff,
                    0xde05980f, 0xef2f118b, 0x5a0a6d1f, 0x6d367ecf, 0x27cb09b7,
                    0x4f463f66, 0x9e5fea2d, 0x7527bac7, 0xebe5f17b, 0x3d0739f7,
                    0x8a5292ea, 0x6bfb5fb1, 0x1f8d5d08, 0x56033046, 0xfc7b6bab,
                    0xf0cfbc21
    };

    public int[] piFour = {
                    0x40000000, 0x3fe921fb, 0x18469899, 0x3e64442d
    };

    public int[] piThirtyTwoInv = {
                    0x6dc9c883, 0x40245f30
    };

    public int[] shifter = {
                    0x00000000, 0x43380000
    };

    public int[] signMask = {
                    0x00000000, 0x80000000
    };

    public int[] pThree = {
                    0x2e037073, 0x3b63198a
    };

    public int[] allOnes = {
                    0xffffffff, 0x3fefffff
    };

    public int[] twoPowFiftyFive = {
                    0x00000000, 0x43600000
    };

    public int[] twoPowFiftyFiveM = {
                    0x00000000, 0x3c800000
    };

    public int[] pOne = {
                    0x54400000, 0x3fb921fb
    };

    public void sinIntrinsic(Register dest, Register value, CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        ArrayDataPointerConstant oneHalfPtr = new ArrayDataPointerConstant(oneHalf, 16);
        ArrayDataPointerConstant pTwoPtr = new ArrayDataPointerConstant(pTwo, 16);
        ArrayDataPointerConstant scFourPtr = new ArrayDataPointerConstant(scFour, 16);
        ArrayDataPointerConstant cTablePtr = new ArrayDataPointerConstant(cTable, 16);
        ArrayDataPointerConstant scTwoPtr = new ArrayDataPointerConstant(scTwo, 16);
        ArrayDataPointerConstant scThreePtr = new ArrayDataPointerConstant(scThree, 16);
        ArrayDataPointerConstant scOnePtr = new ArrayDataPointerConstant(scOne, 16);
        ArrayDataPointerConstant piInvTablePtr = new ArrayDataPointerConstant(piInvTable, 16);
        ArrayDataPointerConstant piFourPtr = new ArrayDataPointerConstant(piFour, 16);
        ArrayDataPointerConstant piThirtyTwoInvPtr = new ArrayDataPointerConstant(piThirtyTwoInv, 8);
        ArrayDataPointerConstant shifterPtr = new ArrayDataPointerConstant(shifter, 8);
        ArrayDataPointerConstant signMaskPtr = new ArrayDataPointerConstant(signMask, 8);
        ArrayDataPointerConstant pThreePtr = new ArrayDataPointerConstant(pThree, 8);
        ArrayDataPointerConstant allOnesPtr = new ArrayDataPointerConstant(allOnes, 8);
        ArrayDataPointerConstant twoPowFiftyFivePtr = new ArrayDataPointerConstant(twoPowFiftyFive, 8);
        ArrayDataPointerConstant twoPowFiftyFiveMPtr = new ArrayDataPointerConstant(twoPowFiftyFiveM, 8);
        ArrayDataPointerConstant pOnePtr = new ArrayDataPointerConstant(pOne, 8);

        Label bb0 = new Label();
        Label bb1 = new Label();
        Label bb2 = new Label();
        Label bb4 = new Label();
        Label bb5 = new Label();
        Label bb6 = new Label();
        Label bb8 = new Label();
        Label bb9 = new Label();
        Label bb10 = new Label();
        Label bb11 = new Label();
        Label bb12 = new Label();
        Label bb13 = new Label();
        Label bb14 = new Label();
        Label bb15 = new Label();

        Register gpr1 = asRegister(gpr1Temp, AMD64Kind.QWORD);
        Register gpr2 = asRegister(gpr2Temp, AMD64Kind.QWORD);
        Register gpr3 = asRegister(rcxTemp, AMD64Kind.QWORD);
        Register gpr4 = asRegister(gpr4Temp, AMD64Kind.QWORD);
        Register gpr5 = asRegister(gpr5Temp, AMD64Kind.QWORD);
        Register gpr6 = asRegister(gpr6Temp, AMD64Kind.QWORD);
        Register gpr7 = asRegister(gpr7Temp, AMD64Kind.QWORD);
        Register gpr8 = asRegister(gpr8Temp, AMD64Kind.QWORD);
        Register gpr9 = asRegister(gpr9Temp, AMD64Kind.QWORD);
        Register gpr10 = asRegister(gpr10Temp, AMD64Kind.QWORD);

        Register temp1 = asRegister(xmm1Temp, AMD64Kind.DOUBLE);
        Register temp2 = asRegister(xmm2Temp, AMD64Kind.DOUBLE);
        Register temp3 = asRegister(xmm3Temp, AMD64Kind.DOUBLE);
        Register temp4 = asRegister(xmm4Temp, AMD64Kind.DOUBLE);
        Register temp5 = asRegister(xmm5Temp, AMD64Kind.DOUBLE);
        Register temp6 = asRegister(xmm6Temp, AMD64Kind.DOUBLE);
        Register temp7 = asRegister(xmm7Temp, AMD64Kind.DOUBLE);
        Register temp8 = asRegister(xmm8Temp, AMD64Kind.DOUBLE);
        Register temp9 = asRegister(xmm9Temp, AMD64Kind.DOUBLE);

        AMD64Address stackSlot = (AMD64Address) crb.asAddress(stackTemp);

        setCrb(crb);
        masm.movsd(stackSlot, value);
        if (dest.encoding != value.encoding) {
            masm.movdqu(dest, value);
        }

        masm.leaq(gpr1, stackSlot);
        masm.movl(gpr1, new AMD64Address(gpr1, 4));
        masm.movdq(temp1, externalAddress(piThirtyTwoInvPtr));                                // 0x6dc9c883,
                                                                                              // 0x40245f30
        masm.movdq(temp2, externalAddress(shifterPtr));                                       // 0x00000000,
                                                                                              // 0x43380000

        masm.andl(gpr1, 2147418112);
        masm.subl(gpr1, 808452096);
        masm.cmpl(gpr1, 281346048);
        masm.jcc(ConditionFlag.Above, bb0);

        masm.mulsd(temp1, dest);
        masm.movdqu(temp5, externalAddress(oneHalfPtr));                                      // 0x00000000,
                                                                                              // 0x3fe00000,
                                                                                              // 0x00000000,
                                                                                              // 0x3fe00000
        masm.movdq(temp4, externalAddress(signMaskPtr));                                      // 0x00000000,
                                                                                              // 0x80000000
        masm.pand(temp4, dest);
        masm.por(temp5, temp4);
        masm.addpd(temp1, temp5);
        masm.cvttsd2sil(gpr4, temp1);
        masm.cvtsi2sdl(temp1, gpr4);
        masm.movdqu(temp6, externalAddress(pTwoPtr));                                         // 0x1a600000,
                                                                                              // 0x3d90b461,
                                                                                              // 0x1a600000,
                                                                                              // 0x3d90b461
        masm.movq(gpr7, 0x3fb921fb54400000L);
        masm.movdq(temp3, gpr7);
        masm.movdqu(temp5, externalAddress(scFourPtr));                                       // 0xa556c734,
                                                                                              // 0x3ec71de3,
                                                                                              // 0x1a01a01a,
                                                                                              // 0x3efa01a0
        masm.pshufd(temp4, dest, 0x44);
        masm.mulsd(temp3, temp1);
        if (masm.supports(CPUFeature.SSE3)) {
            masm.movddup(temp1, temp1);
        } else {
            masm.movlhps(temp1, temp1);
        }
        masm.andl(gpr4, 63);
        masm.shll(gpr4, 5);
        masm.leaq(gpr1, externalAddress(cTablePtr));
        masm.addq(gpr1, gpr4);
        masm.movdqu(temp8, new AMD64Address(gpr1, 0));
        masm.mulpd(temp6, temp1);
        masm.mulsd(temp1, externalAddress(pThreePtr));                                        // 0x2e037073,
                                                                                              // 0x3b63198a
        masm.subsd(temp4, temp3);
        masm.subsd(dest, temp3);
        if (masm.supports(CPUFeature.SSE3)) {
            masm.movddup(temp3, temp4);
        } else {
            masm.movdqu(temp3, temp4);
            masm.movlhps(temp3, temp3);
        }
        masm.subsd(temp4, temp6);
        masm.pshufd(dest, dest, 0x44);
        masm.pshufd(temp7, temp8, 0xE);
        masm.movdqu(temp2, temp8);
        masm.movdqu(temp9, temp7);
        masm.mulpd(temp5, dest);
        masm.subpd(dest, temp6);
        masm.mulsd(temp7, temp4);
        masm.subsd(temp3, temp4);
        masm.mulpd(temp5, dest);
        masm.mulpd(dest, dest);
        masm.subsd(temp3, temp6);
        masm.movdqu(temp6, externalAddress(scTwoPtr));                                        // 0x11111111,
                                                                                              // 0x3f811111,
                                                                                              // 0x55555555,
                                                                                              // 0x3fa55555
        masm.subsd(temp1, temp3);
        masm.movdq(temp3, new AMD64Address(gpr1, 24));
        masm.addsd(temp2, temp3);
        masm.subsd(temp7, temp2);
        masm.mulsd(temp2, temp4);
        masm.mulpd(temp6, dest);
        masm.mulsd(temp3, temp4);
        masm.mulpd(temp2, dest);
        masm.mulpd(dest, dest);
        masm.addpd(temp5, externalAddress(scThreePtr));                                       // 0x1a01a01a,
                                                                                              // 0xbf2a01a0,
                                                                                              // 0x16c16c17,
                                                                                              // 0xbf56c16c
        masm.mulsd(temp4, temp8);
        masm.addpd(temp6, externalAddress(scOnePtr));                                         // 0x55555555,
                                                                                              // 0xbfc55555,
                                                                                              // 0x00000000,
                                                                                              // 0xbfe00000
        masm.mulpd(temp5, dest);
        masm.movdqu(dest, temp3);
        masm.addsd(temp3, temp9);
        masm.mulpd(temp1, temp7);
        masm.movdqu(temp7, temp4);
        masm.addsd(temp4, temp3);
        masm.addpd(temp6, temp5);
        masm.subsd(temp9, temp3);
        masm.subsd(temp3, temp4);
        masm.addsd(temp1, new AMD64Address(gpr1, 16));
        masm.mulpd(temp6, temp2);
        masm.addsd(temp9, dest);
        masm.addsd(temp3, temp7);
        masm.addsd(temp1, temp9);
        masm.addsd(temp1, temp3);
        masm.addsd(temp1, temp6);
        masm.unpckhpd(temp6, temp6);
        masm.movdqu(dest, temp4);
        masm.addsd(temp1, temp6);
        masm.addsd(dest, temp1);
        masm.jmp(bb15);

        masm.bind(bb14);
        masm.xorpd(temp1, temp1);
        masm.xorpd(dest, dest);
        masm.divsd(dest, temp1);
        masm.jmp(bb15);

        masm.bind(bb0);
        masm.jcc(ConditionFlag.Greater, bb1);

        masm.shrl(gpr1, 20);
        masm.cmpl(gpr1, 3325);
        masm.jcc(ConditionFlag.NotEqual, bb2);

        masm.mulsd(dest, externalAddress(allOnesPtr));                                        // 0xffffffff,
                                                                                              // 0x3fefffff
        masm.jmp(bb15);

        masm.bind(bb2);
        masm.movdq(temp3, externalAddress(twoPowFiftyFivePtr));                               // 0x00000000,
                                                                                              // 0x43600000
        masm.mulsd(temp3, dest);
        masm.subsd(temp3, dest);
        masm.mulsd(temp3, externalAddress(twoPowFiftyFiveMPtr));                              // 0x00000000,
                                                                                              // 0x3c800000
        masm.jmp(bb15);

        masm.bind(bb1);
        masm.pextrw(gpr3, dest, 3);
        masm.andl(gpr3, 32752);
        masm.cmpl(gpr3, 32752);
        masm.jcc(ConditionFlag.Equal, bb14);

        masm.subl(gpr3, 16224);
        masm.shrl(gpr3, 7);
        masm.andl(gpr3, 65532);
        masm.leaq(gpr10, externalAddress(piInvTablePtr));
        masm.addq(gpr3, gpr10);
        masm.movdq(gpr1, dest);
        masm.movl(gpr9, new AMD64Address(gpr3, 20));
        masm.movl(gpr7, new AMD64Address(gpr3, 24));
        masm.movl(gpr4, gpr1);
        masm.shrq(gpr1, 21);
        masm.orl(gpr1, Integer.MIN_VALUE);
        masm.shrl(gpr1, 11);
        masm.movl(gpr8, gpr9);
        masm.imulq(gpr9, gpr4);
        masm.imulq(gpr8, gpr1);
        masm.imulq(gpr7, gpr1);
        masm.movl(gpr5, new AMD64Address(gpr3, 16));
        masm.movl(gpr6, new AMD64Address(gpr3, 12));
        masm.movl(gpr10, gpr9);
        masm.shrq(gpr9, 32);
        masm.addq(gpr8, gpr9);
        masm.addq(gpr10, gpr7);
        masm.movl(gpr7, gpr10);
        masm.shrq(gpr10, 32);
        masm.addq(gpr8, gpr10);
        masm.movl(gpr9, gpr5);
        masm.imulq(gpr5, gpr4);
        masm.imulq(gpr9, gpr1);
        masm.movl(gpr10, gpr6);
        masm.imulq(gpr6, gpr4);
        masm.movl(gpr2, gpr5);
        masm.shrq(gpr5, 32);
        masm.addq(gpr8, gpr2);
        masm.movl(gpr2, gpr8);
        masm.shrq(gpr8, 32);
        masm.addq(gpr9, gpr5);
        masm.addq(gpr9, gpr8);
        masm.shlq(gpr2, 32);
        masm.orq(gpr7, gpr2);
        masm.imulq(gpr10, gpr1);
        masm.movl(gpr8, new AMD64Address(gpr3, 8));
        masm.movl(gpr5, new AMD64Address(gpr3, 4));
        masm.movl(gpr2, gpr6);
        masm.shrq(gpr6, 32);
        masm.addq(gpr9, gpr2);
        masm.movl(gpr2, gpr9);
        masm.shrq(gpr9, 32);
        masm.addq(gpr10, gpr6);
        masm.addq(gpr10, gpr9);
        masm.movq(gpr6, gpr8);
        masm.imulq(gpr8, gpr4);
        masm.imulq(gpr6, gpr1);
        masm.movl(gpr9, gpr8);
        masm.shrq(gpr8, 32);
        masm.addq(gpr10, gpr9);
        masm.movl(gpr9, gpr10);
        masm.shrq(gpr10, 32);
        masm.addq(gpr6, gpr8);
        masm.addq(gpr6, gpr10);
        masm.movq(gpr8, gpr5);
        masm.imulq(gpr5, gpr4);
        masm.imulq(gpr8, gpr1);
        masm.shlq(gpr9, 32);
        masm.orq(gpr9, gpr2);
        masm.movl(gpr1, new AMD64Address(gpr3, 0));
        masm.movl(gpr10, gpr5);
        masm.shrq(gpr5, 32);
        masm.addq(gpr6, gpr10);
        masm.movl(gpr10, gpr6);
        masm.shrq(gpr6, 32);
        masm.addq(gpr8, gpr5);
        masm.addq(gpr8, gpr6);
        masm.imulq(gpr4, gpr1);
        masm.pextrw(gpr2, dest, 3);
        masm.leaq(gpr6, externalAddress(piInvTablePtr));
        masm.subq(gpr3, gpr6);
        masm.addl(gpr3, gpr3);
        masm.addl(gpr3, gpr3);
        masm.addl(gpr3, gpr3);
        masm.addl(gpr3, 19);
        masm.movl(gpr5, 32768);
        masm.andl(gpr5, gpr2);
        masm.shrl(gpr2, 4);
        masm.andl(gpr2, 2047);
        masm.subl(gpr2, 1023);
        masm.subl(gpr3, gpr2);
        masm.addq(gpr8, gpr4);
        masm.movl(gpr4, gpr3);
        masm.addl(gpr4, 32);
        masm.cmpl(gpr3, 1);
        masm.jcc(ConditionFlag.Less, bb4);

        masm.negl(gpr3);
        masm.addl(gpr3, 29);
        masm.shll(gpr8);
        masm.movl(gpr6, gpr8);
        masm.andl(gpr8, 536870911);
        masm.testl(gpr8, 268435456);
        masm.jcc(ConditionFlag.NotEqual, bb5);

        masm.shrl(gpr8);
        masm.movl(gpr2, 0);
        masm.shlq(gpr8, 32);
        masm.orq(gpr8, gpr10);

        masm.bind(bb6);

        masm.cmpq(gpr8, 0);
        masm.jcc(ConditionFlag.Equal, bb8);

        masm.bind(bb9);
        masm.bsrq(gpr10, gpr8);
        masm.movl(gpr3, 29);
        masm.subl(gpr3, gpr10);
        masm.jcc(ConditionFlag.LessEqual, bb10);

        masm.shlq(gpr8);
        masm.movq(gpr1, gpr9);
        masm.shlq(gpr9);
        masm.addl(gpr4, gpr3);
        masm.negl(gpr3);
        masm.addl(gpr3, 64);
        masm.shrq(gpr1);
        masm.shrq(gpr7);
        masm.orq(gpr8, gpr1);
        masm.orq(gpr9, gpr7);

        masm.bind(bb11);
        masm.cvtsi2sdq(dest, gpr8);
        masm.shrq(gpr9, 1);
        masm.cvtsi2sdq(temp3, gpr9);
        masm.xorpd(temp4, temp4);
        masm.shll(gpr4, 4);
        masm.negl(gpr4);
        masm.addl(gpr4, 16368);
        masm.orl(gpr4, gpr5);
        masm.xorl(gpr4, gpr2);
        masm.pinsrw(temp4, gpr4, 3);
        masm.leaq(gpr1, externalAddress(piFourPtr));
        masm.movdqu(temp2, new AMD64Address(gpr1, 0));                                        // 0x40000000,
                                                                                              // 0x3fe921fb,
                                                                                              // 0x18469899,
                                                                                              // 0x3e64442d
        masm.xorpd(temp5, temp5);
        masm.subl(gpr4, 1008);
        masm.pinsrw(temp5, gpr4, 3);
        masm.mulsd(dest, temp4);
        masm.shll(gpr5, 16);
        masm.sarl(gpr5, 31);
        masm.mulsd(temp3, temp5);
        masm.movdqu(temp1, dest);
        masm.pshufd(temp6, temp2, 0xE);
        masm.mulsd(dest, temp2);
        masm.shrl(gpr6, 29);
        masm.addsd(temp1, temp3);
        masm.mulsd(temp3, temp2);
        masm.addl(gpr6, gpr5);
        masm.xorl(gpr6, gpr5);
        masm.mulsd(temp6, temp1);
        masm.movl(gpr1, gpr6);
        masm.addsd(temp6, temp3);
        masm.movdqu(temp2, dest);
        masm.addsd(dest, temp6);
        masm.subsd(temp2, dest);
        masm.addsd(temp6, temp2);

        masm.bind(bb12);
        masm.movdq(temp1, externalAddress(piThirtyTwoInvPtr));                                // 0x6dc9c883,
                                                                                              // 0x40245f30
        masm.mulsd(temp1, dest);
        masm.movdq(temp5, externalAddress(oneHalfPtr));                                       // 0x00000000,
                                                                                              // 0x3fe00000,
                                                                                              // 0x00000000,
                                                                                              // 0x3fe00000
        masm.movdq(temp4, externalAddress(signMaskPtr));                                      // 0x00000000,
                                                                                              // 0x80000000
        masm.pand(temp4, dest);
        masm.por(temp5, temp4);
        masm.addpd(temp1, temp5);
        masm.cvttsd2sil(gpr4, temp1);
        masm.cvtsi2sdl(temp1, gpr4);
        masm.movdq(temp3, externalAddress(pOnePtr));                                          // 0x54400000,
                                                                                              // 0x3fb921fb
        masm.movdqu(temp2, externalAddress(pTwoPtr));                                         // 0x1a600000,
                                                                                              // 0x3d90b461,
                                                                                              // 0x1a600000,
                                                                                              // 0x3d90b461
        masm.mulsd(temp3, temp1);
        masm.unpcklpd(temp1, temp1);
        masm.shll(gpr1, 3);
        masm.addl(gpr4, 1865216);
        masm.movdqu(temp4, dest);
        masm.addl(gpr4, gpr1);
        masm.andl(gpr4, 63);
        masm.movdqu(temp5, externalAddress(scFourPtr));                                       // 0x54400000,
                                                                                              // 0x3fb921fb
        masm.leaq(gpr1, externalAddress(cTablePtr));
        masm.shll(gpr4, 5);
        masm.addq(gpr1, gpr4);
        masm.movdqu(temp8, new AMD64Address(gpr1, 0));
        masm.mulpd(temp2, temp1);
        masm.subsd(dest, temp3);
        masm.mulsd(temp1, externalAddress(pThreePtr));                                        // 0x2e037073,
                                                                                              // 0x3b63198a
        masm.subsd(temp4, temp3);
        masm.unpcklpd(dest, dest);
        masm.movdqu(temp3, temp4);
        masm.subsd(temp4, temp2);
        masm.mulpd(temp5, dest);
        masm.subpd(dest, temp2);
        masm.pshufd(temp7, temp8, 0xE);
        masm.movdqu(temp9, temp7);
        masm.mulsd(temp7, temp4);
        masm.subsd(temp3, temp4);
        masm.mulpd(temp5, dest);
        masm.mulpd(dest, dest);
        masm.subsd(temp3, temp2);
        masm.movdqu(temp2, temp8);
        masm.subsd(temp1, temp3);
        masm.movdq(temp3, new AMD64Address(gpr1, 24));
        masm.addsd(temp2, temp3);
        masm.subsd(temp7, temp2);
        masm.subsd(temp1, temp6);
        masm.movdqu(temp6, externalAddress(scTwoPtr));                                        // 0x11111111,
                                                                                              // 0x3f811111,
                                                                                              // 0x55555555,
                                                                                              // 0x3fa55555
        masm.mulsd(temp2, temp4);
        masm.mulpd(temp6, dest);
        masm.mulsd(temp3, temp4);
        masm.mulpd(temp2, dest);
        masm.mulpd(dest, dest);
        masm.addpd(temp5, externalAddress(scThreePtr));                                       // 0x1a01a01a,
                                                                                              // 0xbf2a01a0,
                                                                                              // 0x16c16c17,
                                                                                              // 0xbf56c16c
        masm.mulsd(temp4, temp8);
        masm.addpd(temp6, externalAddress(scOnePtr));                                         // 0x55555555,
                                                                                              // 0xbfc55555,
                                                                                              // 0x00000000,
                                                                                              // 0xbfe00000
        masm.mulpd(temp5, dest);
        masm.movdqu(dest, temp3);
        masm.addsd(temp3, temp9);
        masm.mulpd(temp1, temp7);
        masm.movdqu(temp7, temp4);
        masm.addsd(temp4, temp3);
        masm.addpd(temp6, temp5);
        masm.subsd(temp9, temp3);
        masm.subsd(temp3, temp4);
        masm.addsd(temp1, new AMD64Address(gpr1, 16));
        masm.mulpd(temp6, temp2);
        masm.addsd(temp9, dest);
        masm.addsd(temp3, temp7);
        masm.addsd(temp1, temp9);
        masm.addsd(temp1, temp3);
        masm.addsd(temp1, temp6);
        masm.unpckhpd(temp6, temp6);
        masm.movdqu(dest, temp4);
        masm.addsd(temp1, temp6);
        masm.addsd(dest, temp1);
        masm.jmp(bb15);

        masm.bind(bb8);
        masm.addl(gpr4, 64);
        masm.movq(gpr8, gpr9);
        masm.movq(gpr9, gpr7);
        masm.movl(gpr7, 0);
        masm.cmpq(gpr8, 0);
        masm.jcc(ConditionFlag.NotEqual, bb9);

        masm.addl(gpr4, 64);
        masm.movq(gpr8, gpr9);
        masm.movq(gpr9, gpr7);
        masm.cmpq(gpr8, 0);
        masm.jcc(ConditionFlag.NotEqual, bb9);

        masm.xorpd(dest, dest);
        masm.xorpd(temp6, temp6);
        masm.jmp(bb12);

        masm.bind(bb10);
        masm.jcc(ConditionFlag.Equal, bb11);

        masm.negl(gpr3);
        masm.shrq(gpr9);
        masm.movq(gpr1, gpr8);
        masm.shrq(gpr8);
        masm.subl(gpr4, gpr3);
        masm.negl(gpr3);
        masm.addl(gpr3, 64);
        masm.shlq(gpr1);
        masm.orq(gpr9, gpr1);
        masm.jmp(bb11);

        masm.bind(bb4);
        masm.negl(gpr3);
        masm.shlq(gpr8, 32);
        masm.orq(gpr8, gpr10);
        masm.shlq(gpr8);
        masm.movq(gpr6, gpr8);
        masm.testl(gpr8, Integer.MIN_VALUE);
        masm.jcc(ConditionFlag.NotEqual, bb13);

        masm.shrl(gpr8);
        masm.movl(gpr2, 0);
        masm.shrq(gpr6, 3);
        masm.jmp(bb6);

        masm.bind(bb5);
        masm.shrl(gpr8);
        masm.movl(gpr2, 536870912);
        masm.shrl(gpr2);
        masm.shlq(gpr8, 32);
        masm.orq(gpr8, gpr10);
        masm.shlq(gpr2, 32);
        masm.addl(gpr6, 536870912);
        masm.movl(gpr3, 0);
        masm.movl(gpr10, 0);
        masm.subq(gpr3, gpr7);
        masm.sbbq(gpr10, gpr9);
        masm.sbbq(gpr2, gpr8);
        masm.movq(gpr7, gpr3);
        masm.movq(gpr9, gpr10);
        masm.movq(gpr8, gpr2);
        masm.movl(gpr2, 32768);
        masm.jmp(bb6);

        masm.bind(bb13);
        masm.shrl(gpr8);
        masm.movq(gpr2, 0x100000000L);
        masm.shrq(gpr2);
        masm.movl(gpr3, 0);
        masm.movl(gpr10, 0);
        masm.subq(gpr3, gpr7);
        masm.sbbq(gpr10, gpr9);
        masm.sbbq(gpr2, gpr8);
        masm.movq(gpr7, gpr3);
        masm.movq(gpr9, gpr10);
        masm.movq(gpr8, gpr2);
        masm.movl(gpr2, 32768);
        masm.shrq(gpr6, 3);
        masm.addl(gpr6, 536870912);
        masm.jmp(bb6);

        masm.bind(bb15);
    }

    /*
     * Copyright (c) 2014, 2016, Intel Corporation. All rights reserved. Intel Math Library (LIBM)
     * Source Code
     *
     * ALGORITHM DESCRIPTION - COS() ---------------------
     *
     * 1. RANGE REDUCTION
     *
     * We perform an initial range reduction from X to r with
     *
     * X =~= N * pi/32 + r
     *
     * so that |r| <= pi/64 + epsilon. We restrict inputs to those where |N| <= 932560. Beyond this,
     * the range reduction is insufficiently accurate. For extremely small inputs, denormalization
     * can occur internally, impacting performance. This means that the main path is actually only
     * taken for 2^-252 <= |X| < 90112.
     *
     * To avoid branches, we perform the range reduction to full accuracy each time.
     *
     * X - N * (P_1 + P_2 + P_3)
     *
     * where P_1 and P_2 are 32-bit numbers (so multiplication by N is exact) and P_3 is a 53-bit
     * number. Together, these approximate pi well enough for all cases in the restricted range.
     *
     * The main reduction sequence is:
     *
     * y = 32/pi * x N = integer(y) (computed by adding and subtracting off SHIFTER)
     *
     * m_1 = N * P_1 m_2 = N * P_2 r_1 = x - m_1 r = r_1 - m_2 (this r can be used for most of the
     * calculation)
     *
     * c_1 = r_1 - r m_3 = N * P_3 c_2 = c_1 - m_2 c = c_2 - m_3
     *
     * 2. MAIN ALGORITHM
     *
     * The algorithm uses a table lookup based on B = M * pi / 32 where M = N mod 64. The stored
     * values are: sigma closest power of 2 to cos(B) C_hl 53-bit cos(B) - sigma S_hi + S_lo 2 *
     * 53-bit sin(B)
     *
     * The computation is organized as follows:
     *
     * sin(B + r + c) = [sin(B) + sigma * r] + r * (cos(B) - sigma) + sin(B) * [cos(r + c) - 1] +
     * cos(B) * [sin(r + c) - r]
     *
     * which is approximately:
     *
     * [S_hi + sigma * r] + C_hl * r + S_lo + S_hi * [(cos(r) - 1) - r * c] + (C_hl + sigma) *
     * [(sin(r) - r) + c]
     *
     * and this is what is actually computed. We separate this sum into four parts:
     *
     * hi + med + pols + corr
     *
     * where
     *
     * hi = S_hi + sigma r med = C_hl * r pols = S_hi * (cos(r) - 1) + (C_hl + sigma) * (sin(r) - r)
     * corr = S_lo + c * ((C_hl + sigma) - S_hi * r)
     *
     * 3. POLYNOMIAL
     *
     * The polynomial S_hi * (cos(r) - 1) + (C_hl + sigma) * (sin(r) - r) can be rearranged freely,
     * since it is quite small, so we exploit parallelism to the fullest.
     *
     * psc4 = SC_4 * r_1 msc4 = psc4 * r r2 = r * r msc2 = SC_2 * r2 r4 = r2 * r2 psc3 = SC_3 + msc4
     * psc1 = SC_1 + msc2 msc3 = r4 * psc3 sincospols = psc1 + msc3 pols = sincospols * <S_hi * r^2
     * | (C_hl + sigma) * r^3>
     *
     * 4. CORRECTION TERM
     *
     * This is where the "c" component of the range reduction is taken into account; recall that
     * just "r" is used for most of the calculation.
     *
     * -c = m_3 - c_2 -d = S_hi * r - (C_hl + sigma) corr = -c * -d + S_lo
     *
     * 5. COMPENSATED SUMMATIONS
     *
     * The two successive compensated summations add up the high and medium parts, leaving just the
     * low parts to add up at the end.
     *
     * rs = sigma * r res_int = S_hi + rs k_0 = S_hi - res_int k_2 = k_0 + rs med = C_hl * r res_hi
     * = res_int + med k_1 = res_int - res_hi k_3 = k_1 + med
     *
     * 6. FINAL SUMMATION
     *
     * We now add up all the small parts:
     *
     * res_lo = pols(hi) + pols(lo) + corr + k_1 + k_3
     *
     * Now the overall result is just:
     *
     * res_hi + res_lo
     *
     * 7. SMALL ARGUMENTS
     *
     * Inputs with |X| < 2^-252 are treated specially as 1 - |x|.
     *
     * Special cases: cos(NaN) = quiet NaN, and raise invalid exception cos(INF) = NaN and raise
     * invalid exception cos(0) = 1
     *
     */

    public int[] one = {
                    0x00000000, 0x3ff00000
    };

    public void cosIntrinsic(Register dest, Register value, CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        ArrayDataPointerConstant oneHalfPtr = new ArrayDataPointerConstant(oneHalf, 16);
        ArrayDataPointerConstant pTwoPtr = new ArrayDataPointerConstant(pTwo, 16);
        ArrayDataPointerConstant scFourPtr = new ArrayDataPointerConstant(scFour, 16);
        ArrayDataPointerConstant cTablePtr = new ArrayDataPointerConstant(cTable, 16);
        ArrayDataPointerConstant scTwoPtr = new ArrayDataPointerConstant(scTwo, 16);
        ArrayDataPointerConstant scThreePtr = new ArrayDataPointerConstant(scThree, 16);
        ArrayDataPointerConstant scOnePtr = new ArrayDataPointerConstant(scOne, 16);
        ArrayDataPointerConstant piInvTablePtr = new ArrayDataPointerConstant(piInvTable, 16);
        ArrayDataPointerConstant piFourPtr = new ArrayDataPointerConstant(piFour, 16);
        ArrayDataPointerConstant piThirtyTwoInvPtr = new ArrayDataPointerConstant(piThirtyTwoInv, 8);
        ArrayDataPointerConstant signMaskPtr = new ArrayDataPointerConstant(signMask, 8);
        ArrayDataPointerConstant pThreePtr = new ArrayDataPointerConstant(pThree, 8);
        ArrayDataPointerConstant pOnePtr = new ArrayDataPointerConstant(pOne, 8);
        ArrayDataPointerConstant onePtr = new ArrayDataPointerConstant(one, 8);

        Label bb0 = new Label();
        Label bb1 = new Label();
        Label bb3 = new Label();
        Label bb4 = new Label();
        Label bb5 = new Label();
        Label bb6 = new Label();
        Label bb7 = new Label();
        Label bb8 = new Label();
        Label bb9 = new Label();
        Label bb10 = new Label();
        Label bb11 = new Label();
        Label bb12 = new Label();
        Label bb13 = new Label();
        Label bb14 = new Label();

        Register gpr1 = asRegister(gpr1Temp, AMD64Kind.QWORD);
        Register gpr2 = asRegister(gpr2Temp, AMD64Kind.QWORD);
        Register gpr3 = asRegister(rcxTemp, AMD64Kind.QWORD);
        Register gpr4 = asRegister(gpr4Temp, AMD64Kind.QWORD);
        Register gpr5 = asRegister(gpr5Temp, AMD64Kind.QWORD);
        Register gpr6 = asRegister(gpr6Temp, AMD64Kind.QWORD);
        Register gpr7 = asRegister(gpr7Temp, AMD64Kind.QWORD);
        Register gpr8 = asRegister(gpr8Temp, AMD64Kind.QWORD);
        Register gpr9 = asRegister(gpr9Temp, AMD64Kind.QWORD);
        Register gpr10 = asRegister(gpr10Temp, AMD64Kind.QWORD);

        Register temp1 = asRegister(xmm1Temp, AMD64Kind.DOUBLE);
        Register temp2 = asRegister(xmm2Temp, AMD64Kind.DOUBLE);
        Register temp3 = asRegister(xmm3Temp, AMD64Kind.DOUBLE);
        Register temp4 = asRegister(xmm4Temp, AMD64Kind.DOUBLE);
        Register temp5 = asRegister(xmm5Temp, AMD64Kind.DOUBLE);
        Register temp6 = asRegister(xmm6Temp, AMD64Kind.DOUBLE);
        Register temp7 = asRegister(xmm7Temp, AMD64Kind.DOUBLE);
        Register temp8 = asRegister(xmm8Temp, AMD64Kind.DOUBLE);
        Register temp9 = asRegister(xmm9Temp, AMD64Kind.DOUBLE);

        AMD64Address stackSlot = (AMD64Address) crb.asAddress(stackTemp);

        setCrb(crb);
        masm.movdq(stackSlot, value);
        if (dest.encoding != value.encoding) {
            masm.movdqu(dest, value);
        }

        masm.leaq(gpr1, stackSlot);
        masm.movl(gpr1, new AMD64Address(gpr1, 4));
        masm.movdq(temp1, externalAddress(piThirtyTwoInvPtr));                              // 0x6dc9c883,
                                                                                            // 0x40245f30

        masm.andl(gpr1, 2147418112);
        masm.subl(gpr1, 808452096);
        masm.cmpl(gpr1, 281346048);
        masm.jcc(ConditionFlag.Above, bb0);

        masm.mulsd(temp1, dest);
        masm.movdqu(temp5, externalAddress(oneHalfPtr));                                    // 0x00000000,
                                                                                            // 0x3fe00000,
                                                                                            // 0x00000000,
                                                                                            // 0x3fe00000
        masm.movdq(temp4, externalAddress(signMaskPtr));                                    // 0x00000000,
                                                                                            // 0x80000000
        masm.pand(temp4, dest);
        masm.por(temp5, temp4);
        masm.addpd(temp1, temp5);
        masm.cvttsd2sil(gpr4, temp1);
        masm.cvtsi2sdl(temp1, gpr4);
        masm.movdqu(temp2, externalAddress(pTwoPtr));                                       // 0x1a600000,
                                                                                            // 0x3d90b461,
                                                                                            // 0x1a600000,
                                                                                            // 0x3d90b461
        masm.movdq(temp3, externalAddress(pOnePtr));                                        // 0x54400000,
                                                                                            // 0x3fb921fb
        masm.mulsd(temp3, temp1);
        masm.unpcklpd(temp1, temp1);
        masm.addq(gpr4, 1865232);
        masm.movdqu(temp4, dest);
        masm.andq(gpr4, 63);
        masm.movdqu(temp5, externalAddress(scFourPtr));                                     // 0xa556c734,
                                                                                            // 0x3ec71de3,
                                                                                            // 0x1a01a01a,
                                                                                            // 0x3efa01a0
        masm.leaq(gpr1, externalAddress(cTablePtr));
        masm.shlq(gpr4, 5);
        masm.addq(gpr1, gpr4);
        masm.movdqu(temp8, new AMD64Address(gpr1, 0));
        masm.mulpd(temp2, temp1);
        masm.subsd(dest, temp3);
        masm.mulsd(temp1, externalAddress(pThreePtr));                                      // 0x2e037073,
                                                                                            // 0x3b63198a
        masm.subsd(temp4, temp3);
        masm.unpcklpd(dest, dest);
        masm.movdqu(temp3, temp4);
        masm.subsd(temp4, temp2);
        masm.mulpd(temp5, dest);
        masm.subpd(dest, temp2);
        masm.pshufd(temp7, temp8, 0xE);
        masm.movdqu(temp6, externalAddress(scTwoPtr));                                      // 0x11111111,
                                                                                            // 0x3f811111,
                                                                                            // 0x55555555,
                                                                                            // 0x3fa55555
        masm.mulsd(temp7, temp4);
        masm.subsd(temp3, temp4);
        masm.mulpd(temp5, dest);
        masm.mulpd(dest, dest);
        masm.subsd(temp3, temp2);
        masm.movdqu(temp2, temp8);
        masm.subsd(temp1, temp3);
        masm.movdq(temp3, new AMD64Address(gpr1, 24));
        masm.addsd(temp2, temp3);
        masm.subsd(temp7, temp2);
        masm.mulsd(temp2, temp4);
        masm.mulpd(temp6, dest);
        masm.mulsd(temp3, temp4);
        masm.mulpd(temp2, dest);
        masm.mulpd(dest, dest);
        masm.addpd(temp5, externalAddress(scThreePtr));                                     // 0x1a01a01a,
                                                                                            // 0xbf2a01a0,
                                                                                            // 0x16c16c17,
                                                                                            // 0xbf56c16c
        masm.mulsd(temp4, temp8);
        masm.pshufd(temp9, temp8, 0xE);
        masm.addpd(temp6, externalAddress(scOnePtr));                                       // 0x55555555,
                                                                                            // 0xbfc55555,
                                                                                            // 0x00000000,
                                                                                            // 0xbfe00000
        masm.mulpd(temp5, dest);
        masm.movdqu(dest, temp3);
        masm.addsd(temp3, temp9);
        masm.mulpd(temp1, temp7);
        masm.movdqu(temp7, temp4);
        masm.addsd(temp4, temp3);
        masm.addpd(temp6, temp5);
        masm.subsd(temp9, temp3);
        masm.subsd(temp3, temp4);
        masm.addsd(temp1, new AMD64Address(gpr1, 16));
        masm.mulpd(temp6, temp2);
        masm.addsd(dest, temp9);
        masm.addsd(temp3, temp7);
        masm.addsd(dest, temp1);
        masm.addsd(dest, temp3);
        masm.addsd(dest, temp6);
        masm.unpckhpd(temp6, temp6);
        masm.addsd(dest, temp6);
        masm.addsd(dest, temp4);
        masm.jmp(bb13);

        masm.bind(bb14);
        masm.xorpd(temp1, temp1);
        masm.xorpd(dest, dest);
        masm.divsd(dest, temp1);
        masm.jmp(bb13);

        masm.bind(bb0);
        masm.jcc(ConditionFlag.Greater, bb1);

        masm.pextrw(gpr1, dest, 3);
        masm.andl(gpr1, 32767);
        masm.pinsrw(dest, gpr1, 3);
        masm.movdq(temp1, externalAddress(onePtr));                                         // 0x00000000,
                                                                                            // 0x3ff00000
        masm.subsd(temp1, dest);
        masm.movdqu(dest, temp1);
        masm.jmp(bb13);

        masm.bind(bb1);
        masm.pextrw(gpr3, dest, 3);
        masm.andl(gpr3, 32752);
        masm.cmpl(gpr3, 32752);
        masm.jcc(ConditionFlag.Equal, bb14);

        masm.subl(gpr3, 16224);
        masm.shrl(gpr3, 7);
        masm.andl(gpr3, 65532);
        masm.leaq(gpr10, externalAddress(piInvTablePtr));
        masm.addq(gpr3, gpr10);
        masm.movdq(gpr1, dest);
        masm.movl(gpr9, new AMD64Address(gpr3, 20));
        masm.movl(gpr7, new AMD64Address(gpr3, 24));
        masm.movl(gpr4, gpr1);
        masm.shrq(gpr1, 21);
        masm.orl(gpr1, Integer.MIN_VALUE);
        masm.shrl(gpr1, 11);
        masm.movl(gpr8, gpr9);
        masm.imulq(gpr9, gpr4);
        masm.imulq(gpr8, gpr1);
        masm.imulq(gpr7, gpr1);
        masm.movl(gpr5, new AMD64Address(gpr3, 16));
        masm.movl(gpr6, new AMD64Address(gpr3, 12));
        masm.movl(gpr10, gpr9);
        masm.shrq(gpr9, 32);
        masm.addq(gpr8, gpr9);
        masm.addq(gpr10, gpr7);
        masm.movl(gpr7, gpr10);
        masm.shrq(gpr10, 32);
        masm.addq(gpr8, gpr10);
        masm.movl(gpr9, gpr5);
        masm.imulq(gpr5, gpr4);
        masm.imulq(gpr9, gpr1);
        masm.movl(gpr10, gpr6);
        masm.imulq(gpr6, gpr4);
        masm.movl(gpr2, gpr5);
        masm.shrq(gpr5, 32);
        masm.addq(gpr8, gpr2);
        masm.movl(gpr2, gpr8);
        masm.shrq(gpr8, 32);
        masm.addq(gpr9, gpr5);
        masm.addq(gpr9, gpr8);
        masm.shlq(gpr2, 32);
        masm.orq(gpr7, gpr2);
        masm.imulq(gpr10, gpr1);
        masm.movl(gpr8, new AMD64Address(gpr3, 8));
        masm.movl(gpr5, new AMD64Address(gpr3, 4));
        masm.movl(gpr2, gpr6);
        masm.shrq(gpr6, 32);
        masm.addq(gpr9, gpr2);
        masm.movl(gpr2, gpr9);
        masm.shrq(gpr9, 32);
        masm.addq(gpr10, gpr6);
        masm.addq(gpr10, gpr9);
        masm.movq(gpr6, gpr8);
        masm.imulq(gpr8, gpr4);
        masm.imulq(gpr6, gpr1);
        masm.movl(gpr9, gpr8);
        masm.shrq(gpr8, 32);
        masm.addq(gpr10, gpr9);
        masm.movl(gpr9, gpr10);
        masm.shrq(gpr10, 32);
        masm.addq(gpr6, gpr8);
        masm.addq(gpr6, gpr10);
        masm.movq(gpr8, gpr5);
        masm.imulq(gpr5, gpr4);
        masm.imulq(gpr8, gpr1);
        masm.shlq(gpr9, 32);
        masm.orq(gpr9, gpr2);
        masm.movl(gpr1, new AMD64Address(gpr3, 0));
        masm.movl(gpr10, gpr5);
        masm.shrq(gpr5, 32);
        masm.addq(gpr6, gpr10);
        masm.movl(gpr10, gpr6);
        masm.shrq(gpr6, 32);
        masm.addq(gpr8, gpr5);
        masm.addq(gpr8, gpr6);
        masm.imulq(gpr4, gpr1);
        masm.pextrw(gpr2, dest, 3);
        masm.leaq(gpr6, externalAddress(piInvTablePtr));
        masm.subq(gpr3, gpr6);
        masm.addl(gpr3, gpr3);
        masm.addl(gpr3, gpr3);
        masm.addl(gpr3, gpr3);
        masm.addl(gpr3, 19);
        masm.movl(gpr5, 32768);
        masm.andl(gpr5, gpr2);
        masm.shrl(gpr2, 4);
        masm.andl(gpr2, 2047);
        masm.subl(gpr2, 1023);
        masm.subl(gpr3, gpr2);
        masm.addq(gpr8, gpr4);
        masm.movl(gpr4, gpr3);
        masm.addl(gpr4, 32);
        masm.cmpl(gpr3, 1);
        masm.jcc(ConditionFlag.Less, bb3);

        masm.negl(gpr3);
        masm.addl(gpr3, 29);
        masm.shll(gpr8);
        masm.movl(gpr6, gpr8);
        masm.andl(gpr8, 536870911);
        masm.testl(gpr8, 268435456);
        masm.jcc(ConditionFlag.NotEqual, bb4);

        masm.shrl(gpr8);
        masm.movl(gpr2, 0);
        masm.shlq(gpr8, 32);
        masm.orq(gpr8, gpr10);

        masm.bind(bb5);

        masm.bind(bb6);
        masm.cmpq(gpr8, 0);
        masm.jcc(ConditionFlag.Equal, bb7);

        masm.bind(bb8);
        masm.bsrq(gpr10, gpr8);
        masm.movl(gpr3, 29);
        masm.subl(gpr3, gpr10);
        masm.jcc(ConditionFlag.LessEqual, bb9);

        masm.shlq(gpr8);
        masm.movq(gpr1, gpr9);
        masm.shlq(gpr9);
        masm.addl(gpr4, gpr3);
        masm.negl(gpr3);
        masm.addl(gpr3, 64);
        masm.shrq(gpr1);
        masm.shrq(gpr7);
        masm.orq(gpr8, gpr1);
        masm.orq(gpr9, gpr7);

        masm.bind(bb10);
        masm.cvtsi2sdq(dest, gpr8);
        masm.shrq(gpr9, 1);
        masm.cvtsi2sdq(temp3, gpr9);
        masm.xorpd(temp4, temp4);
        masm.shll(gpr4, 4);
        masm.negl(gpr4);
        masm.addl(gpr4, 16368);
        masm.orl(gpr4, gpr5);
        masm.xorl(gpr4, gpr2);
        masm.pinsrw(temp4, gpr4, 3);
        masm.leaq(gpr2, externalAddress(piFourPtr));
        masm.movdqu(temp2, new AMD64Address(gpr2, 0));                                      // 0x40000000,
                                                                                            // 0x3fe921fb,
                                                                                            // 0x18469899,
                                                                                            // 0x3e64442d
        masm.xorpd(temp5, temp5);
        masm.subl(gpr4, 1008);
        masm.pinsrw(temp5, gpr4, 3);
        masm.mulsd(dest, temp4);
        masm.shll(gpr5, 16);
        masm.sarl(gpr5, 31);
        masm.mulsd(temp3, temp5);
        masm.movdqu(temp1, dest);
        masm.mulsd(dest, temp2);
        masm.pshufd(temp6, temp2, 0xE);
        masm.shrl(gpr6, 29);
        masm.addsd(temp1, temp3);
        masm.mulsd(temp3, temp2);
        masm.addl(gpr6, gpr5);
        masm.xorl(gpr6, gpr5);
        masm.mulsd(temp6, temp1);
        masm.movl(gpr1, gpr6);
        masm.addsd(temp6, temp3);
        masm.movdqu(temp2, dest);
        masm.addsd(dest, temp6);
        masm.subsd(temp2, dest);
        masm.addsd(temp6, temp2);

        masm.bind(bb11);
        masm.movq(temp1, externalAddress(piThirtyTwoInvPtr));                               // 0x6dc9c883,
                                                                                            // 0x40245f30
        masm.mulsd(temp1, dest);
        masm.movdq(temp5, externalAddress(oneHalfPtr));                                     // 0x00000000,
                                                                                            // 0x3fe00000,
                                                                                            // 0x00000000,
                                                                                            // 0x3fe00000
        masm.movdq(temp4, externalAddress(signMaskPtr));                                    // 0x00000000,
                                                                                            // 0x80000000
        masm.pand(temp4, dest);
        masm.por(temp5, temp4);
        masm.addpd(temp1, temp5);
        masm.cvttsd2siq(gpr4, temp1);
        masm.cvtsi2sdq(temp1, gpr4);
        masm.movdq(temp3, externalAddress(pOnePtr));                                        // 0x54400000,
                                                                                            // 0x3fb921fb
        masm.movdqu(temp2, externalAddress(pTwoPtr));                                       // 0x1a600000,
                                                                                            // 0x3d90b461,
                                                                                            // 0x1a600000,
                                                                                            // 0x3d90b461
        masm.mulsd(temp3, temp1);
        masm.unpcklpd(temp1, temp1);
        masm.shll(gpr1, 3);
        masm.addl(gpr4, 1865232);
        masm.movdqu(temp4, dest);
        masm.addl(gpr4, gpr1);
        masm.andl(gpr4, 63);
        masm.movdqu(temp5, externalAddress(scFourPtr));                                     // 0xa556c734,
                                                                                            // 0x3ec71de3,
                                                                                            // 0x1a01a01a,
                                                                                            // 0x3efa01a0
        masm.leaq(gpr1, externalAddress(cTablePtr));
        masm.shll(gpr4, 5);
        masm.addq(gpr1, gpr4);
        masm.movdqu(temp8, new AMD64Address(gpr1, 0));
        masm.mulpd(temp2, temp1);
        masm.subsd(dest, temp3);
        masm.mulsd(temp1, externalAddress(pThreePtr));                                      // 0x2e037073,
                                                                                            // 0x3b63198a
        masm.subsd(temp4, temp3);
        masm.unpcklpd(dest, dest);
        masm.movdqu(temp3, temp4);
        masm.subsd(temp4, temp2);
        masm.mulpd(temp5, dest);
        masm.pshufd(temp7, temp8, 0xE);
        masm.movdqu(temp9, temp7);
        masm.subpd(dest, temp2);
        masm.mulsd(temp7, temp4);
        masm.subsd(temp3, temp4);
        masm.mulpd(temp5, dest);
        masm.mulpd(dest, dest);
        masm.subsd(temp3, temp2);
        masm.movdqu(temp2, temp8);
        masm.subsd(temp1, temp3);
        masm.movdq(temp3, new AMD64Address(gpr1, 24));
        masm.addsd(temp2, temp3);
        masm.subsd(temp7, temp2);
        masm.subsd(temp1, temp6);
        masm.movdqu(temp6, externalAddress(scTwoPtr));                                      // 0x11111111,
                                                                                            // 0x3f811111,
                                                                                            // 0x55555555,
                                                                                            // 0x3fa55555
        masm.mulsd(temp2, temp4);
        masm.mulpd(temp6, dest);
        masm.mulsd(temp3, temp4);
        masm.mulpd(temp2, dest);
        masm.mulpd(dest, dest);
        masm.addpd(temp5, externalAddress(scThreePtr));                                     // 0x1a01a01a,
                                                                                            // 0xbf2a01a0,
                                                                                            // 0x16c16c17,
                                                                                            // 0xbf56c16c
        masm.mulsd(temp4, temp8);
        masm.addpd(temp6, externalAddress(scOnePtr));                                       // 0x55555555,
                                                                                            // 0xbfc55555,
                                                                                            // 0x00000000,
                                                                                            // 0xbfe00000
        masm.mulpd(temp5, dest);
        masm.movdqu(dest, temp3);
        masm.addsd(temp3, temp9);
        masm.mulpd(temp1, temp7);
        masm.movdqu(temp7, temp4);
        masm.addsd(temp4, temp3);
        masm.addpd(temp6, temp5);
        masm.subsd(temp9, temp3);
        masm.subsd(temp3, temp4);
        masm.addsd(temp1, new AMD64Address(gpr1, 16));
        masm.mulpd(temp6, temp2);
        masm.addsd(temp9, dest);
        masm.addsd(temp3, temp7);
        masm.addsd(temp1, temp9);
        masm.addsd(temp1, temp3);
        masm.addsd(temp1, temp6);
        masm.unpckhpd(temp6, temp6);
        masm.movdqu(dest, temp4);
        masm.addsd(temp1, temp6);
        masm.addsd(dest, temp1);
        masm.jmp(bb13);

        masm.bind(bb7);
        masm.addl(gpr4, 64);
        masm.movq(gpr8, gpr9);
        masm.movq(gpr9, gpr7);
        masm.movl(gpr7, 0);
        masm.cmpq(gpr8, 0);
        masm.jcc(ConditionFlag.NotEqual, bb8);

        masm.addl(gpr4, 64);
        masm.movq(gpr8, gpr9);
        masm.movq(gpr9, gpr7);
        masm.cmpq(gpr8, 0);
        masm.jcc(ConditionFlag.NotEqual, bb8);

        masm.xorpd(dest, dest);
        masm.xorpd(temp6, temp6);
        masm.jmp(bb11);

        masm.bind(bb9);
        masm.jcc(ConditionFlag.Equal, bb10);

        masm.negl(gpr3);
        masm.shrq(gpr9);
        masm.movq(gpr1, gpr8);
        masm.shrq(gpr8);
        masm.subl(gpr4, gpr3);
        masm.negl(gpr3);
        masm.addl(gpr3, 64);
        masm.shlq(gpr1);
        masm.orq(gpr9, gpr1);
        masm.jmp(bb10);

        masm.bind(bb3);
        masm.negl(gpr3);
        masm.shlq(gpr8, 32);
        masm.orq(gpr8, gpr10);
        masm.shlq(gpr8);
        masm.movq(gpr6, gpr8);
        masm.testl(gpr8, Integer.MIN_VALUE);
        masm.jcc(ConditionFlag.NotEqual, bb12);

        masm.shrl(gpr8);
        masm.movl(gpr2, 0);
        masm.shrq(gpr6, 3);
        masm.jmp(bb6);

        masm.bind(bb4);
        masm.shrl(gpr8);
        masm.movl(gpr2, 536870912);
        masm.shrl(gpr2);
        masm.shlq(gpr8, 32);
        masm.orq(gpr8, gpr10);
        masm.shlq(gpr2, 32);
        masm.addl(gpr6, 536870912);
        masm.movl(gpr3, 0);
        masm.movl(gpr10, 0);
        masm.subq(gpr3, gpr7);
        masm.sbbq(gpr10, gpr9);
        masm.sbbq(gpr2, gpr8);
        masm.movq(gpr7, gpr3);
        masm.movq(gpr9, gpr10);
        masm.movq(gpr8, gpr2);
        masm.movl(gpr2, 32768);
        masm.jmp(bb5);

        masm.bind(bb12);
        masm.shrl(gpr8);
        masm.movq(gpr2, 0x100000000L);
        masm.shrq(gpr2);
        masm.movl(gpr3, 0);
        masm.movl(gpr10, 0);
        masm.subq(gpr3, gpr7);
        masm.sbbq(gpr10, gpr9);
        masm.sbbq(gpr2, gpr8);
        masm.movq(gpr7, gpr3);
        masm.movq(gpr9, gpr10);
        masm.movq(gpr8, gpr2);
        masm.movl(gpr2, 32768);
        masm.shrq(gpr6, 3);
        masm.addl(gpr6, 536870912);
        masm.jmp(bb6);

        masm.bind(bb13);
    }

    /*
     * Copyright (c) 2014, 2016, Intel Corporation. All rights reserved. Intel Math Library (LIBM)
     * Source Code
     *
     * ALGORITHM DESCRIPTION - TAN() ---------------------
     *
     * Polynomials coefficients and other constants.
     *
     * Note that in this algorithm, there is a different polynomial for each breakpoint, so there
     * are 32 sets of polynomial coefficients as well as 32 instances of the other constants.
     *
     * The polynomial coefficients and constants are offset from the start of the main block as
     * follows:
     *
     * 0: c8 | c0 16: c9 | c1 32: c10 | c2 48: c11 | c3 64: c12 | c4 80: c13 | c5 96: c14 | c6 112:
     * c15 | c7 128: T_hi 136: T_lo 144: Sigma 152: T_hl 160: Tau 168: Mask 176: (end of block)
     *
     * The total table size is therefore 5632 bytes.
     *
     * Note that c0 and c1 are always zero. We could try storing other constants here, and just
     * loading the low part of the SIMD register in these cases, after ensuring the high part is
     * zero.
     *
     * The higher terms of the polynomial are computed in the *low* part of the SIMD register. This
     * is so we can overlap the multiplication by r^8 and the unpacking of the other part.
     *
     * The constants are: T_hi + T_lo = accurate constant term in power series Sigma + T_hl =
     * accurate coefficient of r in power series (Sigma=1 bit) Tau = multiplier for the reciprocal,
     * always -1 or 0
     *
     * The basic reconstruction formula using these constants is:
     *
     * High = tau * recip_hi + t_hi Med = (sgn * r + t_hl * r)_hi Low = (sgn * r + t_hl * r)_lo +
     * tau * recip_lo + T_lo + (T_hl + sigma) * c + pol
     *
     * where pol = c0 + c1 * r + c2 * r^2 + ... + c15 * r^15
     *
     * (c0 = c1 = 0, but using them keeps SIMD regularity)
     *
     * We then do a compensated sum High + Med, add the low parts together and then do the final
     * sum.
     *
     * Here recip_hi + recip_lo is an accurate reciprocal of the remainder modulo pi/2
     *
     * Special cases: tan(NaN) = quiet NaN, and raise invalid exception tan(INF) = NaN and raise
     * invalid exception tan(+/-0) = +/-0
     *
     */

    private static int[] oneHalfTan = {
                    0x00000000, 0x3fe00000, 0x00000000, 0x3fe00000
    };

    private static int[] mulSixteen = {
                    0x00000000, 0x40300000, 0x00000000, 0x3ff00000
    };

    private static int[] signMaskTan = {
                    0x00000000, 0x80000000, 0x00000000, 0x80000000
    };

    private static int[] piThirtyTwoInvTan = {
                    0x6dc9c883, 0x3fe45f30, 0x6dc9c883, 0x40245f30
    };

    private static int[] pOneTan = {
                    0x54444000, 0x3fb921fb, 0x54440000, 0x3fb921fb
    };

    private static int[] pTwoTan = {
                    0x67674000, 0xbd32e7b9, 0x4c4c0000, 0x3d468c23
    };

    private static int[] pThreeTan = {
                    0x3707344a, 0x3aa8a2e0, 0x03707345, 0x3ae98a2e
    };

    private static int[] cTableTan = {
                    0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x882c10fa,
                    0x3f9664f4, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
                    0x00000000, 0x00000000, 0x55e6c23d, 0x3f8226e3, 0x55555555,
                    0x3fd55555, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
                    0x0e157de0, 0x3f6d6d3d, 0x11111111, 0x3fc11111, 0x00000000,
                    0x00000000, 0x00000000, 0x00000000, 0x452b75e3, 0x3f57da36,
                    0x1ba1ba1c, 0x3faba1ba, 0x00000000, 0x00000000, 0x00000000,
                    0x00000000, 0x00000000, 0x3ff00000, 0x00000000, 0x00000000,
                    0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x4e435f9b,
                    0x3f953f83, 0x00000000, 0x00000000, 0x3c6e8e46, 0x3f9b74ea,
                    0x00000000, 0x00000000, 0xda5b7511, 0x3f85ad63, 0xdc230b9b,
                    0x3fb97558, 0x26cb3788, 0x3f881308, 0x76fc4985, 0x3fd62ac9,
                    0x77bb08ba, 0x3f757c85, 0xb6247521, 0x3fb1381e, 0x5922170c,
                    0x3f754e95, 0x8746482d, 0x3fc27f83, 0x11055b30, 0x3f64e391,
                    0x3e666320, 0x3fa3e609, 0x0de9dae3, 0x3f6301df, 0x1f1dca06,
                    0x3fafa8ae, 0x8c5b2da2, 0x3fb936bb, 0x4e88f7a5, 0x3c587d05,
                    0x00000000, 0x3ff00000, 0xa8935dd9, 0x3f83dde2, 0x00000000,
                    0x00000000, 0x00000000, 0x00000000, 0x5a279ea3, 0x3faa3407,
                    0x00000000, 0x00000000, 0x432d65fa, 0x3fa70153, 0x00000000,
                    0x00000000, 0x891a4602, 0x3f9d03ef, 0xd62ca5f8, 0x3fca77d9,
                    0xb35f4628, 0x3f97a265, 0x433258fa, 0x3fd8cf51, 0xb58fd909,
                    0x3f8f88e3, 0x01771cea, 0x3fc2b154, 0xf3562f8e, 0x3f888f57,
                    0xc028a723, 0x3fc7370f, 0x20b7f9f0, 0x3f80f44c, 0x214368e9,
                    0x3fb6dfaa, 0x28891863, 0x3f79b4b6, 0x172dbbf0, 0x3fb6cb8e,
                    0xe0553158, 0x3fc975f5, 0x593fe814, 0x3c2ef5d3, 0x00000000,
                    0x3ff00000, 0x03dec550, 0x3fa44203, 0x00000000, 0x00000000,
                    0x00000000, 0x00000000, 0x9314533e, 0x3fbb8ec5, 0x00000000,
                    0x00000000, 0x09aa36d0, 0x3fb6d3f4, 0x00000000, 0x00000000,
                    0xdcb427fd, 0x3fb13950, 0xd87ab0bb, 0x3fd5335e, 0xce0ae8a5,
                    0x3fabb382, 0x79143126, 0x3fddba41, 0x5f2b28d4, 0x3fa552f1,
                    0x59f21a6d, 0x3fd015ab, 0x22c27d95, 0x3fa0e984, 0xe19fc6aa,
                    0x3fd0576c, 0x8f2c2950, 0x3f9a4898, 0xc0b3f22c, 0x3fc59462,
                    0x1883a4b8, 0x3f94b61c, 0x3f838640, 0x3fc30eb8, 0x355c63dc,
                    0x3fd36a08, 0x1dce993d, 0xbc6d704d, 0x00000000, 0x3ff00000,
                    0x2b82ab63, 0x3fb78e92, 0x00000000, 0x00000000, 0x00000000,
                    0x00000000, 0x56f37042, 0x3fccfc56, 0x00000000, 0x00000000,
                    0xaa563951, 0x3fc90125, 0x00000000, 0x00000000, 0x3d0e7c5d,
                    0x3fc50533, 0x9bed9b2e, 0x3fdf0ed9, 0x5fe7c47c, 0x3fc1f250,
                    0x96c125e5, 0x3fe2edd9, 0x5a02bbd8, 0x3fbe5c71, 0x86362c20,
                    0x3fda08b7, 0x4b4435ed, 0x3fb9d342, 0x4b494091, 0x3fd911bd,
                    0xb56658be, 0x3fb5e4c7, 0x93a2fd76, 0x3fd3c092, 0xda271794,
                    0x3fb29910, 0x3303df2b, 0x3fd189be, 0x99fcef32, 0x3fda8279,
                    0xb68c1467, 0x3c708b2f, 0x00000000, 0x3ff00000, 0x980c4337,
                    0x3fc5f619, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
                    0xcc03e501, 0x3fdff10f, 0x00000000, 0x00000000, 0x44a4e845,
                    0x3fddb63b, 0x00000000, 0x00000000, 0x3768ad9f, 0x3fdb72a4,
                    0x3dd01cca, 0x3fe5fdb9, 0xa61d2811, 0x3fd972b2, 0x5645ad0b,
                    0x3fe977f9, 0xd013b3ab, 0x3fd78ca3, 0xbf0bf914, 0x3fe4f192,
                    0x4d53e730, 0x3fd5d060, 0x3f8b9000, 0x3fe49933, 0xe2b82f08,
                    0x3fd4322a, 0x5936a835, 0x3fe27ae1, 0xb1c61c9b, 0x3fd2b3fb,
                    0xef478605, 0x3fe1659e, 0x190834ec, 0x3fe11ab7, 0xcdb625ea,
                    0xbc8e564b, 0x00000000, 0x3ff00000, 0xb07217e3, 0x3fd248f1,
                    0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x2b2c49d0,
                    0x3ff2de9c, 0x00000000, 0x00000000, 0x2655bc98, 0x3ff33e58,
                    0x00000000, 0x00000000, 0xff691fa2, 0x3ff3972e, 0xe93463bd,
                    0x3feeed87, 0x070e10a0, 0x3ff3f5b2, 0xf4d790a4, 0x3ff20c10,
                    0xa04e8ea3, 0x3ff4541a, 0x386accd3, 0x3ff1369e, 0x222a66dd,
                    0x3ff4b521, 0x22a9777e, 0x3ff20817, 0x52a04a6e, 0x3ff5178f,
                    0xddaa0031, 0x3ff22137, 0x4447d47c, 0x3ff57c01, 0x1e9c7f1d,
                    0x3ff29311, 0x2ab7f990, 0x3fe561b8, 0x209c7df1, 0x3c87a8c5,
                    0x00000000, 0x3ff00000, 0x4170bcc6, 0x3fdc92d8, 0x00000000,
                    0x00000000, 0x00000000, 0x00000000, 0xc7ab4d5a, 0x40085e24,
                    0x00000000, 0x00000000, 0xe93ea75d, 0x400b963d, 0x00000000,
                    0x00000000, 0x94a7f25a, 0x400f37e2, 0x4b6261cb, 0x3ff5f984,
                    0x5a9dd812, 0x4011aab0, 0x74c30018, 0x3ffaf5a5, 0x7f2ce8e3,
                    0x4013fe8b, 0xfe8e54fa, 0x3ffd7334, 0x670d618d, 0x4016a10c,
                    0x4db97058, 0x4000e012, 0x24df44dd, 0x40199c5f, 0x697d6ece,
                    0x4003006e, 0x83298b82, 0x401cfc4d, 0x19d490d6, 0x40058c19,
                    0x2ae42850, 0x3fea4300, 0x118e20e6, 0xbc7a6db8, 0x00000000,
                    0x40000000, 0xe33345b8, 0xbfd4e526, 0x00000000, 0x00000000,
                    0x00000000, 0x00000000, 0x65965966, 0x40219659, 0x00000000,
                    0x00000000, 0x882c10fa, 0x402664f4, 0x00000000, 0x00000000,
                    0x83cd3723, 0x402c8342, 0x00000000, 0x40000000, 0x55e6c23d,
                    0x403226e3, 0x55555555, 0x40055555, 0x34451939, 0x40371c96,
                    0xaaaaaaab, 0x400aaaaa, 0x0e157de0, 0x403d6d3d, 0x11111111,
                    0x40111111, 0xa738201f, 0x4042bbce, 0x05b05b06, 0x4015b05b,
                    0x452b75e3, 0x4047da36, 0x1ba1ba1c, 0x401ba1ba, 0x00000000,
                    0x3ff00000, 0x00000000, 0x00000000, 0x00000000, 0x40000000,
                    0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
                    0x00000000, 0x4f48b8d3, 0xbf33eaf9, 0x00000000, 0x00000000,
                    0x0cf7586f, 0x3f20b8ea, 0x00000000, 0x00000000, 0xd0258911,
                    0xbf0abaf3, 0x23e49fe9, 0xbfab5a8c, 0x2d53222e, 0x3ef60d15,
                    0x21169451, 0x3fa172b2, 0xbb254dbc, 0xbee1d3b5, 0xdbf93b8e,
                    0xbf84c7db, 0x05b4630b, 0x3ecd3364, 0xee9aada7, 0x3f743924,
                    0x794a8297, 0xbeb7b7b9, 0xe015f797, 0xbf5d41f5, 0xe41a4a56,
                    0x3ea35dfb, 0xe4c2a251, 0x3f49a2ab, 0x5af9e000, 0xbfce49ce,
                    0x8c743719, 0x3d1eb860, 0x00000000, 0x00000000, 0x1b4863cf,
                    0x3fd78294, 0x00000000, 0x3ff00000, 0x00000000, 0xfffffff8,
                    0x535ad890, 0xbf2b9320, 0x00000000, 0x00000000, 0x018fdf1f,
                    0x3f16d61d, 0x00000000, 0x00000000, 0x0359f1be, 0xbf0139e4,
                    0xa4317c6d, 0xbfa67e17, 0x82672d0f, 0x3eebb405, 0x2f1b621e,
                    0x3f9f455b, 0x51ccf238, 0xbed55317, 0xf437b9ac, 0xbf804bee,
                    0xc791a2b5, 0x3ec0e993, 0x919a1db2, 0x3f7080c2, 0x336a5b0e,
                    0xbeaa48a2, 0x0a268358, 0xbf55a443, 0xdfd978e4, 0x3e94b61f,
                    0xd7767a58, 0x3f431806, 0x2aea0000, 0xbfc9bbe8, 0x7723ea61,
                    0xbd3a2369, 0x00000000, 0x00000000, 0xdf7796ff, 0x3fd6e642,
                    0x00000000, 0x3ff00000, 0x00000000, 0xfffffff8, 0xb9ff07ce,
                    0xbf231c78, 0x00000000, 0x00000000, 0xa5517182, 0x3f0ff0e0,
                    0x00000000, 0x00000000, 0x790b4cbc, 0xbef66191, 0x848a46c6,
                    0xbfa21ac0, 0xb16435fa, 0x3ee1d3ec, 0x2a1aa832, 0x3f9c71ea,
                    0xfdd299ef, 0xbec9dd1a, 0x3f8dbaaf, 0xbf793363, 0x309fc6ea,
                    0x3eb415d6, 0xbee60471, 0x3f6b83ba, 0x94a0a697, 0xbe9dae11,
                    0x3e5c67b3, 0xbf4fd07b, 0x9a8f3e3e, 0x3e86bd75, 0xa4beb7a4,
                    0x3f3d1eb1, 0x29cfc000, 0xbfc549ce, 0xbf159358, 0xbd397b33,
                    0x00000000, 0x00000000, 0x871fee6c, 0x3fd666f0, 0x00000000,
                    0x3ff00000, 0x00000000, 0xfffffff8, 0x7d98a556, 0xbf1a3958,
                    0x00000000, 0x00000000, 0x9d88dc01, 0x3f0704c2, 0x00000000,
                    0x00000000, 0x73742a2b, 0xbeed054a, 0x58844587, 0xbf9c2a13,
                    0x55688a79, 0x3ed7a326, 0xee33f1d6, 0x3f9a48f4, 0xa8dc9888,
                    0xbebf8939, 0xaad4b5b8, 0xbf72f746, 0x9102efa1, 0x3ea88f82,
                    0xdabc29cf, 0x3f678228, 0x9289afb8, 0xbe90f456, 0x741fb4ed,
                    0xbf46f3a3, 0xa97f6663, 0x3e79b4bf, 0xca89ff3f, 0x3f36db70,
                    0xa8a2a000, 0xbfc0ee13, 0x3da24be1, 0xbd338b9f, 0x00000000,
                    0x00000000, 0x11cd6c69, 0x3fd601fd, 0x00000000, 0x3ff00000,
                    0x00000000, 0xfffffff8, 0x1a154b97, 0xbf116b01, 0x00000000,
                    0x00000000, 0x2d427630, 0x3f0147bf, 0x00000000, 0x00000000,
                    0xb93820c8, 0xbee264d4, 0xbb6cbb18, 0xbf94ab8c, 0x888d4d92,
                    0x3ed0568b, 0x60730f7c, 0x3f98b19b, 0xe4b1fb11, 0xbeb2f950,
                    0x22cf9f74, 0xbf6b21cd, 0x4a3ff0a6, 0x3e9f499e, 0xfd2b83ce,
                    0x3f64aad7, 0x637b73af, 0xbe83487c, 0xe522591a, 0xbf3fc092,
                    0xa158e8bc, 0x3e6e3aae, 0xe5e82ffa, 0x3f329d2f, 0xd636a000,
                    0xbfb9477f, 0xc2c2d2bc, 0xbd135ef9, 0x00000000, 0x00000000,
                    0xf2fdb123, 0x3fd5b566, 0x00000000, 0x3ff00000, 0x00000000,
                    0xfffffff8, 0xc41acb64, 0xbf05448d, 0x00000000, 0x00000000,
                    0xdbb03d6f, 0x3efb7ad2, 0x00000000, 0x00000000, 0x9e42962d,
                    0xbed5aea5, 0x2579f8ef, 0xbf8b2398, 0x288a1ed9, 0x3ec81441,
                    0xb0198dc5, 0x3f979a3a, 0x2fdfe253, 0xbea57cd3, 0x5766336f,
                    0xbf617caa, 0x600944c3, 0x3e954ed6, 0xa4e0aaf8, 0x3f62c646,
                    0x6b8fb29c, 0xbe74e3a3, 0xdc4c0409, 0xbf33f952, 0x9bffe365,
                    0x3e6301ec, 0xb8869e44, 0x3f2fc566, 0xe1e04000, 0xbfb0cc62,
                    0x016b907f, 0xbd119cbc, 0x00000000, 0x00000000, 0xe6b9d8fa,
                    0x3fd57fb3, 0x00000000, 0x3ff00000, 0x00000000, 0xfffffff8,
                    0x5daf22a6, 0xbef429d7, 0x00000000, 0x00000000, 0x06bca545,
                    0x3ef7a27d, 0x00000000, 0x00000000, 0x7211c19a, 0xbec41c3e,
                    0x956ed53e, 0xbf7ae3f4, 0xee750e72, 0x3ec3901b, 0x91d443f5,
                    0x3f96f713, 0x36661e6c, 0xbe936e09, 0x506f9381, 0xbf5122e8,
                    0xcb6dd43f, 0x3e9041b9, 0x6698b2ff, 0x3f61b0c7, 0x576bf12b,
                    0xbe625a8a, 0xe5a0e9dc, 0xbf23499d, 0x110384dd, 0x3e5b1c2c,
                    0x68d43db6, 0x3f2cb899, 0x6ecac000, 0xbfa0c414, 0xcd7dd58c,
                    0x3d13500f, 0x00000000, 0x00000000, 0x85a2c8fb, 0x3fd55fe0,
                    0x00000000, 0x3ff00000, 0x00000000, 0xfffffff8, 0x00000000,
                    0x00000000, 0x00000000, 0x00000000, 0x2bf70ebe, 0x3ef66a8f,
                    0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
                    0x00000000, 0xd644267f, 0x3ec22805, 0x16c16c17, 0x3f96c16c,
                    0x00000000, 0x00000000, 0x00000000, 0x00000000, 0xc4e09162,
                    0x3e8d6db2, 0xbc011567, 0x3f61566a, 0x00000000, 0x00000000,
                    0x00000000, 0x00000000, 0x1f79955c, 0x3e57da4e, 0x9334ef0b,
                    0x3f2bbd77, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
                    0x00000000, 0x00000000, 0x55555555, 0x3fd55555, 0x00000000,
                    0x3ff00000, 0x00000000, 0xfffffff8, 0x5daf22a6, 0x3ef429d7,
                    0x00000000, 0x00000000, 0x06bca545, 0x3ef7a27d, 0x00000000,
                    0x00000000, 0x7211c19a, 0x3ec41c3e, 0x956ed53e, 0x3f7ae3f4,
                    0xee750e72, 0x3ec3901b, 0x91d443f5, 0x3f96f713, 0x36661e6c,
                    0x3e936e09, 0x506f9381, 0x3f5122e8, 0xcb6dd43f, 0x3e9041b9,
                    0x6698b2ff, 0x3f61b0c7, 0x576bf12b, 0x3e625a8a, 0xe5a0e9dc,
                    0x3f23499d, 0x110384dd, 0x3e5b1c2c, 0x68d43db6, 0x3f2cb899,
                    0x6ecac000, 0x3fa0c414, 0xcd7dd58c, 0xbd13500f, 0x00000000,
                    0x00000000, 0x85a2c8fb, 0x3fd55fe0, 0x00000000, 0x3ff00000,
                    0x00000000, 0xfffffff8, 0xc41acb64, 0x3f05448d, 0x00000000,
                    0x00000000, 0xdbb03d6f, 0x3efb7ad2, 0x00000000, 0x00000000,
                    0x9e42962d, 0x3ed5aea5, 0x2579f8ef, 0x3f8b2398, 0x288a1ed9,
                    0x3ec81441, 0xb0198dc5, 0x3f979a3a, 0x2fdfe253, 0x3ea57cd3,
                    0x5766336f, 0x3f617caa, 0x600944c3, 0x3e954ed6, 0xa4e0aaf8,
                    0x3f62c646, 0x6b8fb29c, 0x3e74e3a3, 0xdc4c0409, 0x3f33f952,
                    0x9bffe365, 0x3e6301ec, 0xb8869e44, 0x3f2fc566, 0xe1e04000,
                    0x3fb0cc62, 0x016b907f, 0x3d119cbc, 0x00000000, 0x00000000,
                    0xe6b9d8fa, 0x3fd57fb3, 0x00000000, 0x3ff00000, 0x00000000,
                    0xfffffff8, 0x1a154b97, 0x3f116b01, 0x00000000, 0x00000000,
                    0x2d427630, 0x3f0147bf, 0x00000000, 0x00000000, 0xb93820c8,
                    0x3ee264d4, 0xbb6cbb18, 0x3f94ab8c, 0x888d4d92, 0x3ed0568b,
                    0x60730f7c, 0x3f98b19b, 0xe4b1fb11, 0x3eb2f950, 0x22cf9f74,
                    0x3f6b21cd, 0x4a3ff0a6, 0x3e9f499e, 0xfd2b83ce, 0x3f64aad7,
                    0x637b73af, 0x3e83487c, 0xe522591a, 0x3f3fc092, 0xa158e8bc,
                    0x3e6e3aae, 0xe5e82ffa, 0x3f329d2f, 0xd636a000, 0x3fb9477f,
                    0xc2c2d2bc, 0x3d135ef9, 0x00000000, 0x00000000, 0xf2fdb123,
                    0x3fd5b566, 0x00000000, 0x3ff00000, 0x00000000, 0xfffffff8,
                    0x7d98a556, 0x3f1a3958, 0x00000000, 0x00000000, 0x9d88dc01,
                    0x3f0704c2, 0x00000000, 0x00000000, 0x73742a2b, 0x3eed054a,
                    0x58844587, 0x3f9c2a13, 0x55688a79, 0x3ed7a326, 0xee33f1d6,
                    0x3f9a48f4, 0xa8dc9888, 0x3ebf8939, 0xaad4b5b8, 0x3f72f746,
                    0x9102efa1, 0x3ea88f82, 0xdabc29cf, 0x3f678228, 0x9289afb8,
                    0x3e90f456, 0x741fb4ed, 0x3f46f3a3, 0xa97f6663, 0x3e79b4bf,
                    0xca89ff3f, 0x3f36db70, 0xa8a2a000, 0x3fc0ee13, 0x3da24be1,
                    0x3d338b9f, 0x00000000, 0x00000000, 0x11cd6c69, 0x3fd601fd,
                    0x00000000, 0x3ff00000, 0x00000000, 0xfffffff8, 0xb9ff07ce,
                    0x3f231c78, 0x00000000, 0x00000000, 0xa5517182, 0x3f0ff0e0,
                    0x00000000, 0x00000000, 0x790b4cbc, 0x3ef66191, 0x848a46c6,
                    0x3fa21ac0, 0xb16435fa, 0x3ee1d3ec, 0x2a1aa832, 0x3f9c71ea,
                    0xfdd299ef, 0x3ec9dd1a, 0x3f8dbaaf, 0x3f793363, 0x309fc6ea,
                    0x3eb415d6, 0xbee60471, 0x3f6b83ba, 0x94a0a697, 0x3e9dae11,
                    0x3e5c67b3, 0x3f4fd07b, 0x9a8f3e3e, 0x3e86bd75, 0xa4beb7a4,
                    0x3f3d1eb1, 0x29cfc000, 0x3fc549ce, 0xbf159358, 0x3d397b33,
                    0x00000000, 0x00000000, 0x871fee6c, 0x3fd666f0, 0x00000000,
                    0x3ff00000, 0x00000000, 0xfffffff8, 0x535ad890, 0x3f2b9320,
                    0x00000000, 0x00000000, 0x018fdf1f, 0x3f16d61d, 0x00000000,
                    0x00000000, 0x0359f1be, 0x3f0139e4, 0xa4317c6d, 0x3fa67e17,
                    0x82672d0f, 0x3eebb405, 0x2f1b621e, 0x3f9f455b, 0x51ccf238,
                    0x3ed55317, 0xf437b9ac, 0x3f804bee, 0xc791a2b5, 0x3ec0e993,
                    0x919a1db2, 0x3f7080c2, 0x336a5b0e, 0x3eaa48a2, 0x0a268358,
                    0x3f55a443, 0xdfd978e4, 0x3e94b61f, 0xd7767a58, 0x3f431806,
                    0x2aea0000, 0x3fc9bbe8, 0x7723ea61, 0x3d3a2369, 0x00000000,
                    0x00000000, 0xdf7796ff, 0x3fd6e642, 0x00000000, 0x3ff00000,
                    0x00000000, 0xfffffff8, 0x4f48b8d3, 0x3f33eaf9, 0x00000000,
                    0x00000000, 0x0cf7586f, 0x3f20b8ea, 0x00000000, 0x00000000,
                    0xd0258911, 0x3f0abaf3, 0x23e49fe9, 0x3fab5a8c, 0x2d53222e,
                    0x3ef60d15, 0x21169451, 0x3fa172b2, 0xbb254dbc, 0x3ee1d3b5,
                    0xdbf93b8e, 0x3f84c7db, 0x05b4630b, 0x3ecd3364, 0xee9aada7,
                    0x3f743924, 0x794a8297, 0x3eb7b7b9, 0xe015f797, 0x3f5d41f5,
                    0xe41a4a56, 0x3ea35dfb, 0xe4c2a251, 0x3f49a2ab, 0x5af9e000,
                    0x3fce49ce, 0x8c743719, 0xbd1eb860, 0x00000000, 0x00000000,
                    0x1b4863cf, 0x3fd78294, 0x00000000, 0x3ff00000, 0x00000000,
                    0xfffffff8, 0x65965966, 0xc0219659, 0x00000000, 0x00000000,
                    0x882c10fa, 0x402664f4, 0x00000000, 0x00000000, 0x83cd3723,
                    0xc02c8342, 0x00000000, 0xc0000000, 0x55e6c23d, 0x403226e3,
                    0x55555555, 0x40055555, 0x34451939, 0xc0371c96, 0xaaaaaaab,
                    0xc00aaaaa, 0x0e157de0, 0x403d6d3d, 0x11111111, 0x40111111,
                    0xa738201f, 0xc042bbce, 0x05b05b06, 0xc015b05b, 0x452b75e3,
                    0x4047da36, 0x1ba1ba1c, 0x401ba1ba, 0x00000000, 0xbff00000,
                    0x00000000, 0x00000000, 0x00000000, 0x40000000, 0x00000000,
                    0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
                    0xc7ab4d5a, 0xc0085e24, 0x00000000, 0x00000000, 0xe93ea75d,
                    0x400b963d, 0x00000000, 0x00000000, 0x94a7f25a, 0xc00f37e2,
                    0x4b6261cb, 0xbff5f984, 0x5a9dd812, 0x4011aab0, 0x74c30018,
                    0x3ffaf5a5, 0x7f2ce8e3, 0xc013fe8b, 0xfe8e54fa, 0xbffd7334,
                    0x670d618d, 0x4016a10c, 0x4db97058, 0x4000e012, 0x24df44dd,
                    0xc0199c5f, 0x697d6ece, 0xc003006e, 0x83298b82, 0x401cfc4d,
                    0x19d490d6, 0x40058c19, 0x2ae42850, 0xbfea4300, 0x118e20e6,
                    0x3c7a6db8, 0x00000000, 0x40000000, 0xe33345b8, 0xbfd4e526,
                    0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x2b2c49d0,
                    0xbff2de9c, 0x00000000, 0x00000000, 0x2655bc98, 0x3ff33e58,
                    0x00000000, 0x00000000, 0xff691fa2, 0xbff3972e, 0xe93463bd,
                    0xbfeeed87, 0x070e10a0, 0x3ff3f5b2, 0xf4d790a4, 0x3ff20c10,
                    0xa04e8ea3, 0xbff4541a, 0x386accd3, 0xbff1369e, 0x222a66dd,
                    0x3ff4b521, 0x22a9777e, 0x3ff20817, 0x52a04a6e, 0xbff5178f,
                    0xddaa0031, 0xbff22137, 0x4447d47c, 0x3ff57c01, 0x1e9c7f1d,
                    0x3ff29311, 0x2ab7f990, 0xbfe561b8, 0x209c7df1, 0xbc87a8c5,
                    0x00000000, 0x3ff00000, 0x4170bcc6, 0x3fdc92d8, 0x00000000,
                    0x00000000, 0x00000000, 0x00000000, 0xcc03e501, 0xbfdff10f,
                    0x00000000, 0x00000000, 0x44a4e845, 0x3fddb63b, 0x00000000,
                    0x00000000, 0x3768ad9f, 0xbfdb72a4, 0x3dd01cca, 0xbfe5fdb9,
                    0xa61d2811, 0x3fd972b2, 0x5645ad0b, 0x3fe977f9, 0xd013b3ab,
                    0xbfd78ca3, 0xbf0bf914, 0xbfe4f192, 0x4d53e730, 0x3fd5d060,
                    0x3f8b9000, 0x3fe49933, 0xe2b82f08, 0xbfd4322a, 0x5936a835,
                    0xbfe27ae1, 0xb1c61c9b, 0x3fd2b3fb, 0xef478605, 0x3fe1659e,
                    0x190834ec, 0xbfe11ab7, 0xcdb625ea, 0x3c8e564b, 0x00000000,
                    0x3ff00000, 0xb07217e3, 0x3fd248f1, 0x00000000, 0x00000000,
                    0x00000000, 0x00000000, 0x56f37042, 0xbfccfc56, 0x00000000,
                    0x00000000, 0xaa563951, 0x3fc90125, 0x00000000, 0x00000000,
                    0x3d0e7c5d, 0xbfc50533, 0x9bed9b2e, 0xbfdf0ed9, 0x5fe7c47c,
                    0x3fc1f250, 0x96c125e5, 0x3fe2edd9, 0x5a02bbd8, 0xbfbe5c71,
                    0x86362c20, 0xbfda08b7, 0x4b4435ed, 0x3fb9d342, 0x4b494091,
                    0x3fd911bd, 0xb56658be, 0xbfb5e4c7, 0x93a2fd76, 0xbfd3c092,
                    0xda271794, 0x3fb29910, 0x3303df2b, 0x3fd189be, 0x99fcef32,
                    0xbfda8279, 0xb68c1467, 0xbc708b2f, 0x00000000, 0x3ff00000,
                    0x980c4337, 0x3fc5f619, 0x00000000, 0x00000000, 0x00000000,
                    0x00000000, 0x9314533e, 0xbfbb8ec5, 0x00000000, 0x00000000,
                    0x09aa36d0, 0x3fb6d3f4, 0x00000000, 0x00000000, 0xdcb427fd,
                    0xbfb13950, 0xd87ab0bb, 0xbfd5335e, 0xce0ae8a5, 0x3fabb382,
                    0x79143126, 0x3fddba41, 0x5f2b28d4, 0xbfa552f1, 0x59f21a6d,
                    0xbfd015ab, 0x22c27d95, 0x3fa0e984, 0xe19fc6aa, 0x3fd0576c,
                    0x8f2c2950, 0xbf9a4898, 0xc0b3f22c, 0xbfc59462, 0x1883a4b8,
                    0x3f94b61c, 0x3f838640, 0x3fc30eb8, 0x355c63dc, 0xbfd36a08,
                    0x1dce993d, 0x3c6d704d, 0x00000000, 0x3ff00000, 0x2b82ab63,
                    0x3fb78e92, 0x00000000, 0x00000000, 0x00000000, 0x00000000,
                    0x5a279ea3, 0xbfaa3407, 0x00000000, 0x00000000, 0x432d65fa,
                    0x3fa70153, 0x00000000, 0x00000000, 0x891a4602, 0xbf9d03ef,
                    0xd62ca5f8, 0xbfca77d9, 0xb35f4628, 0x3f97a265, 0x433258fa,
                    0x3fd8cf51, 0xb58fd909, 0xbf8f88e3, 0x01771cea, 0xbfc2b154,
                    0xf3562f8e, 0x3f888f57, 0xc028a723, 0x3fc7370f, 0x20b7f9f0,
                    0xbf80f44c, 0x214368e9, 0xbfb6dfaa, 0x28891863, 0x3f79b4b6,
                    0x172dbbf0, 0x3fb6cb8e, 0xe0553158, 0xbfc975f5, 0x593fe814,
                    0xbc2ef5d3, 0x00000000, 0x3ff00000, 0x03dec550, 0x3fa44203,
                    0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x4e435f9b,
                    0xbf953f83, 0x00000000, 0x00000000, 0x3c6e8e46, 0x3f9b74ea,
                    0x00000000, 0x00000000, 0xda5b7511, 0xbf85ad63, 0xdc230b9b,
                    0xbfb97558, 0x26cb3788, 0x3f881308, 0x76fc4985, 0x3fd62ac9,
                    0x77bb08ba, 0xbf757c85, 0xb6247521, 0xbfb1381e, 0x5922170c,
                    0x3f754e95, 0x8746482d, 0x3fc27f83, 0x11055b30, 0xbf64e391,
                    0x3e666320, 0xbfa3e609, 0x0de9dae3, 0x3f6301df, 0x1f1dca06,
                    0x3fafa8ae, 0x8c5b2da2, 0xbfb936bb, 0x4e88f7a5, 0xbc587d05,
                    0x00000000, 0x3ff00000, 0xa8935dd9, 0x3f83dde2, 0x00000000,
                    0x00000000, 0x00000000, 0x00000000
    };

    private static int[] maskThirtyFiveTan = {
                    0xfffc0000, 0xffffffff, 0x00000000, 0x00000000
    };

    private static int[] qElevenTan = {
                    0xb8fe4d77, 0x3f82609a
    };

    private static int[] qNineTan = {
                    0xbf847a43, 0x3f9664a0
    };

    private static int[] qSevenTan = {
                    0x52c4c8ab, 0x3faba1ba
    };

    private static int[] qFiveTan = {
                    0x11092746, 0x3fc11111
    };

    private static int[] qThreeTan = {
                    0x55555612, 0x3fd55555
    };

    private static int[] piInvTableTan = {
                    0x00000000, 0x00000000, 0xa2f9836e, 0x4e441529, 0xfc2757d1,
                    0xf534ddc0, 0xdb629599, 0x3c439041, 0xfe5163ab, 0xdebbc561,
                    0xb7246e3a, 0x424dd2e0, 0x06492eea, 0x09d1921c, 0xfe1deb1c,
                    0xb129a73e, 0xe88235f5, 0x2ebb4484, 0xe99c7026, 0xb45f7e41,
                    0x3991d639, 0x835339f4, 0x9c845f8b, 0xbdf9283b, 0x1ff897ff,
                    0xde05980f, 0xef2f118b, 0x5a0a6d1f, 0x6d367ecf, 0x27cb09b7,
                    0x4f463f66, 0x9e5fea2d, 0x7527bac7, 0xebe5f17b, 0x3d0739f7,
                    0x8a5292ea, 0x6bfb5fb1, 0x1f8d5d08, 0x56033046, 0xfc7b6bab,
                    0xf0cfbc21
    };

    private static int[] piFourTan = {
                    0x00000000, 0x3fe921fb, 0x4611a626, 0x3e85110b
    };

    private static int[] qqTwoTan = {
                    0x676733af, 0x3d32e7b9
    };

    private static int[] twoPowFiftyFiveTan = {
                    0x00000000, 0x43600000
    };

    private static int[] twoPowMFiftyFiveTan = {
                    0x00000000, 0x3c800000
    };

    public void tanIntrinsic(Register dest, Register value, CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        ArrayDataPointerConstant oneHalfTanPtr = new ArrayDataPointerConstant(oneHalfTan, 16);
        ArrayDataPointerConstant mulSixteenPtr = new ArrayDataPointerConstant(mulSixteen, 16);
        ArrayDataPointerConstant signMaskTanPtr = new ArrayDataPointerConstant(signMaskTan, 16);
        ArrayDataPointerConstant piThirtyTwoInvTanPtr = new ArrayDataPointerConstant(piThirtyTwoInvTan, 16);
        ArrayDataPointerConstant pOneTanPtr = new ArrayDataPointerConstant(pOneTan, 16);
        ArrayDataPointerConstant pTwoTanPtr = new ArrayDataPointerConstant(pTwoTan, 16);
        ArrayDataPointerConstant pThreeTanPtr = new ArrayDataPointerConstant(pThreeTan, 16);
        ArrayDataPointerConstant cTableTanPtr = new ArrayDataPointerConstant(cTableTan, 16);
        ArrayDataPointerConstant maskThirtyFiveTanPtr = new ArrayDataPointerConstant(maskThirtyFiveTan, 16);
        ArrayDataPointerConstant qElevenTanPtr = new ArrayDataPointerConstant(qElevenTan, 16);
        ArrayDataPointerConstant qNineTanPtr = new ArrayDataPointerConstant(qNineTan, 16);
        ArrayDataPointerConstant qSevenTanPtr = new ArrayDataPointerConstant(qSevenTan, 8);
        ArrayDataPointerConstant qFiveTanPtr = new ArrayDataPointerConstant(qFiveTan, 16);
        ArrayDataPointerConstant qThreeTanPtr = new ArrayDataPointerConstant(qThreeTan, 16);
        ArrayDataPointerConstant piInvTableTanPtr = new ArrayDataPointerConstant(piInvTableTan, 16);
        ArrayDataPointerConstant piFourTanPtr = new ArrayDataPointerConstant(piFourTan, 8);
        ArrayDataPointerConstant qqTwoTanPtr = new ArrayDataPointerConstant(qqTwoTan, 8);
        ArrayDataPointerConstant onePtr = new ArrayDataPointerConstant(one, 8);
        ArrayDataPointerConstant twoPowFiftyFiveTanPtr = new ArrayDataPointerConstant(twoPowFiftyFiveTan, 8);
        ArrayDataPointerConstant twoPowMFiftyFiveTanPtr = new ArrayDataPointerConstant(twoPowMFiftyFiveTan, 8);

        Label bb0 = new Label();
        Label bb1 = new Label();
        Label bb2 = new Label();
        Label bb3 = new Label();
        Label bb5 = new Label();
        Label bb6 = new Label();
        Label bb8 = new Label();
        Label bb9 = new Label();
        Label bb10 = new Label();
        Label bb11 = new Label();
        Label bb12 = new Label();
        Label bb13 = new Label();
        Label bb14 = new Label();
        Label bb15 = new Label();

        Register gpr1 = asRegister(gpr1Temp, AMD64Kind.QWORD);
        Register gpr2 = asRegister(gpr2Temp, AMD64Kind.QWORD);
        Register gpr3 = asRegister(rcxTemp, AMD64Kind.QWORD);
        Register gpr4 = asRegister(gpr4Temp, AMD64Kind.QWORD);
        Register gpr5 = asRegister(gpr5Temp, AMD64Kind.QWORD);
        Register gpr6 = asRegister(gpr6Temp, AMD64Kind.QWORD);
        Register gpr7 = asRegister(gpr7Temp, AMD64Kind.QWORD);
        Register gpr8 = asRegister(gpr8Temp, AMD64Kind.QWORD);
        Register gpr9 = asRegister(gpr9Temp, AMD64Kind.QWORD);
        Register gpr10 = asRegister(gpr10Temp, AMD64Kind.QWORD);

        Register temp1 = asRegister(xmm1Temp, AMD64Kind.DOUBLE);
        Register temp2 = asRegister(xmm2Temp, AMD64Kind.DOUBLE);
        Register temp3 = asRegister(xmm3Temp, AMD64Kind.DOUBLE);
        Register temp4 = asRegister(xmm4Temp, AMD64Kind.DOUBLE);
        Register temp5 = asRegister(xmm5Temp, AMD64Kind.DOUBLE);
        Register temp6 = asRegister(xmm6Temp, AMD64Kind.DOUBLE);
        Register temp7 = asRegister(xmm7Temp, AMD64Kind.DOUBLE);

        setCrb(crb);
        if (dest.encoding != value.encoding) {
            masm.movdqu(dest, value);
        }

        masm.pextrw(gpr1, dest, 3);
        masm.andl(gpr1, 32767);
        masm.subl(gpr1, 16314);
        masm.cmpl(gpr1, 270);
        masm.jcc(ConditionFlag.Above, bb0);

        masm.movdqu(temp5, externalAddress(oneHalfTanPtr));                                     // 0x00000000,
                                                                                                // 0x3fe00000,
                                                                                                // 0x00000000,
                                                                                                // 0x3fe00000
        masm.movdqu(temp6, externalAddress(mulSixteenPtr));                                     // 0x00000000,
                                                                                                // 0x40300000,
                                                                                                // 0x00000000,
                                                                                                // 0x3ff00000
        masm.unpcklpd(dest, dest);
        masm.movdqu(temp4, externalAddress(signMaskTanPtr));                                    // 0x00000000,
                                                                                                // 0x80000000,
                                                                                                // 0x00000000,
                                                                                                // 0x80000000
        masm.andpd(temp4, dest);
        masm.movdqu(temp1, externalAddress(piThirtyTwoInvTanPtr));                              // 0x6dc9c883,
                                                                                                // 0x3fe45f30,
                                                                                                // 0x6dc9c883,
                                                                                                // 0x40245f30
        masm.mulpd(temp1, dest);
        masm.por(temp5, temp4);
        masm.addpd(temp1, temp5);
        masm.movdqu(temp7, temp1);
        masm.unpckhpd(temp7, temp7);
        masm.cvttsd2sil(gpr4, temp7);
        masm.cvttpd2dq(temp1, temp1);
        masm.cvtdq2pd(temp1, temp1);
        masm.mulpd(temp1, temp6);
        masm.movdqu(temp3, externalAddress(pOneTanPtr));                                        // 0x54444000,
                                                                                                // 0x3fb921fb,
                                                                                                // 0x54440000,
                                                                                                // 0x3fb921fb
        masm.movdq(temp5, externalAddress(qqTwoTanPtr));                                        // 0x676733af,
                                                                                                // 0x3d32e7b9
        masm.addq(gpr4, 469248);
        masm.movdqu(temp4, externalAddress(pTwoTanPtr));                                        // 0x67674000,
                                                                                                // 0xbd32e7b9,
                                                                                                // 0x4c4c0000,
                                                                                                // 0x3d468c23
        masm.mulpd(temp3, temp1);
        masm.andq(gpr4, 31);
        masm.mulsd(temp5, temp1);
        masm.movq(gpr3, gpr4);
        masm.mulpd(temp4, temp1);
        masm.shlq(gpr3, 1);
        masm.subpd(dest, temp3);
        masm.mulpd(temp1, externalAddress(pThreeTanPtr));                                       // 0x3707344a,
                                                                                                // 0x3aa8a2e0,
                                                                                                // 0x03707345,
                                                                                                // 0x3ae98a2e
        masm.addq(gpr4, gpr3);
        masm.shlq(gpr3, 2);
        masm.addq(gpr4, gpr3);
        masm.addsd(temp5, dest);
        masm.movdqu(temp2, dest);
        masm.subpd(dest, temp4);
        masm.movdq(temp6, externalAddress(onePtr));                                             // 0x00000000,
                                                                                                // 0x3ff00000
        masm.shlq(gpr4, 4);
        masm.leaq(gpr1, externalAddress(cTableTanPtr));
        masm.andpd(temp5, externalAddress(maskThirtyFiveTanPtr));                               // 0xfffc0000,
                                                                                                // 0xffffffff,
                                                                                                // 0x00000000,
                                                                                                // 0x00000000
        masm.movdqu(temp3, dest);
        masm.addq(gpr1, gpr4);
        masm.subpd(temp2, dest);
        masm.unpckhpd(dest, dest);
        masm.divsd(temp6, temp5);
        masm.subpd(temp2, temp4);
        masm.movdqu(temp7, new AMD64Address(gpr1, 16));
        masm.subsd(temp3, temp5);
        masm.mulpd(temp7, dest);
        masm.subpd(temp2, temp1);
        masm.movdqu(temp1, new AMD64Address(gpr1, 48));
        masm.mulpd(temp1, dest);
        masm.movdqu(temp4, new AMD64Address(gpr1, 96));
        masm.mulpd(temp4, dest);
        masm.addsd(temp2, temp3);
        masm.movdqu(temp3, dest);
        masm.mulpd(dest, dest);
        masm.addpd(temp7, new AMD64Address(gpr1, 0));
        masm.addpd(temp1, new AMD64Address(gpr1, 32));
        masm.mulpd(temp1, dest);
        masm.addpd(temp4, new AMD64Address(gpr1, 80));
        masm.addpd(temp7, temp1);
        masm.movdqu(temp1, new AMD64Address(gpr1, 112));
        masm.mulpd(temp1, dest);
        masm.mulpd(dest, dest);
        masm.addpd(temp4, temp1);
        masm.movdqu(temp1, new AMD64Address(gpr1, 64));
        masm.mulpd(temp1, dest);
        masm.addpd(temp7, temp1);
        masm.movdqu(temp1, temp3);
        masm.mulpd(temp3, dest);
        masm.mulsd(dest, dest);
        masm.mulpd(temp1, new AMD64Address(gpr1, 144));
        masm.mulpd(temp4, temp3);
        masm.movdqu(temp3, temp1);
        masm.addpd(temp7, temp4);
        masm.movdqu(temp4, temp1);
        masm.mulsd(dest, temp7);
        masm.unpckhpd(temp7, temp7);
        masm.addsd(dest, temp7);
        masm.unpckhpd(temp1, temp1);
        masm.addsd(temp3, temp1);
        masm.subsd(temp4, temp3);
        masm.addsd(temp1, temp4);
        masm.movdqu(temp4, temp2);
        masm.movdq(temp7, new AMD64Address(gpr1, 144));
        masm.unpckhpd(temp2, temp2);
        masm.addsd(temp7, new AMD64Address(gpr1, 152));
        masm.mulsd(temp7, temp2);
        masm.addsd(temp7, new AMD64Address(gpr1, 136));
        masm.addsd(temp7, temp1);
        masm.addsd(dest, temp7);
        masm.movdq(temp7, externalAddress(onePtr));                                             // 0x00000000,
                                                                                                // 0x3ff00000
        masm.mulsd(temp4, temp6);
        masm.movdq(temp2, new AMD64Address(gpr1, 168));
        masm.andpd(temp2, temp6);
        masm.mulsd(temp5, temp2);
        masm.mulsd(temp6, new AMD64Address(gpr1, 160));
        masm.subsd(temp7, temp5);
        masm.subsd(temp2, new AMD64Address(gpr1, 128));
        masm.subsd(temp7, temp4);
        masm.mulsd(temp7, temp6);
        masm.movdqu(temp4, temp3);
        masm.subsd(temp3, temp2);
        masm.addsd(temp2, temp3);
        masm.subsd(temp4, temp2);
        masm.addsd(dest, temp4);
        masm.subsd(dest, temp7);
        masm.addsd(dest, temp3);
        masm.jmp(bb15);

        masm.bind(bb0);
        masm.jcc(ConditionFlag.Greater, bb1);

        masm.pextrw(gpr1, dest, 3);
        masm.movl(gpr4, gpr1);
        masm.andl(gpr1, 32752);
        masm.jcc(ConditionFlag.Equal, bb2);

        masm.andl(gpr4, 32767);
        masm.cmpl(gpr4, 15904);
        masm.jcc(ConditionFlag.Below, bb3);

        masm.movdqu(temp2, dest);
        masm.movdqu(temp3, dest);
        masm.movdq(temp1, externalAddress(qElevenTanPtr));                                      // 0xb8fe4d77,
                                                                                                // 0x3f82609a
        masm.mulsd(temp2, dest);
        masm.mulsd(temp3, temp2);
        masm.mulsd(temp1, temp2);
        masm.addsd(temp1, externalAddress(qNineTanPtr));                                        // 0xbf847a43,
                                                                                                // 0x3f9664a0
        masm.mulsd(temp1, temp2);
        masm.addsd(temp1, externalAddress(qSevenTanPtr));                                       // 0x52c4c8ab,
                                                                                                // 0x3faba1ba
        masm.mulsd(temp1, temp2);
        masm.addsd(temp1, externalAddress(qFiveTanPtr));                                        // 0x11092746,
                                                                                                // 0x3fc11111
        masm.mulsd(temp1, temp2);
        masm.addsd(temp1, externalAddress(qThreeTanPtr));                                       // 0x55555612,
                                                                                                // 0x3fd55555
        masm.mulsd(temp1, temp3);
        masm.addsd(dest, temp1);
        masm.jmp(bb15);

        masm.bind(bb3);
        masm.movdq(temp3, externalAddress(twoPowFiftyFiveTanPtr));                              // 0x00000000,
                                                                                                // 0x43600000
        masm.mulsd(temp3, dest);
        masm.addsd(dest, temp3);
        masm.mulsd(dest, externalAddress(twoPowMFiftyFiveTanPtr));                              // 0x00000000,
                                                                                                // 0x3c800000
        masm.jmp(bb15);

        masm.bind(bb14);
        masm.xorpd(temp1, temp1);
        masm.xorpd(dest, dest);
        masm.divsd(dest, temp1);
        masm.jmp(bb15);

        masm.bind(bb2);
        masm.movdqu(temp1, dest);
        masm.mulsd(temp1, temp1);
        masm.jmp(bb15);

        masm.bind(bb1);
        masm.pextrw(gpr3, dest, 3);
        masm.andl(gpr3, 32752);
        masm.cmpl(gpr3, 32752);
        masm.jcc(ConditionFlag.Equal, bb14);

        masm.subl(gpr3, 16224);
        masm.shrl(gpr3, 7);
        masm.andl(gpr3, 65532);
        masm.leaq(gpr10, externalAddress(piInvTableTanPtr));
        masm.addq(gpr3, gpr10);
        masm.movdq(gpr1, dest);
        masm.movl(gpr9, new AMD64Address(gpr3, 20));
        masm.movl(gpr7, new AMD64Address(gpr3, 24));
        masm.movl(gpr4, gpr1);
        masm.shrq(gpr1, 21);
        masm.orl(gpr1, Integer.MIN_VALUE);
        masm.shrl(gpr1, 11);
        masm.movl(gpr8, gpr9);
        masm.imulq(gpr9, gpr4);
        masm.imulq(gpr8, gpr1);
        masm.imulq(gpr7, gpr1);
        masm.movl(gpr5, new AMD64Address(gpr3, 16));
        masm.movl(gpr6, new AMD64Address(gpr3, 12));
        masm.movl(gpr10, gpr9);
        masm.shrq(gpr9, 32);
        masm.addq(gpr8, gpr9);
        masm.addq(gpr10, gpr7);
        masm.movl(gpr7, gpr10);
        masm.shrq(gpr10, 32);
        masm.addq(gpr8, gpr10);
        masm.movl(gpr9, gpr5);
        masm.imulq(gpr5, gpr4);
        masm.imulq(gpr9, gpr1);
        masm.movl(gpr10, gpr6);
        masm.imulq(gpr6, gpr4);
        masm.movl(gpr2, gpr5);
        masm.shrq(gpr5, 32);
        masm.addq(gpr8, gpr2);
        masm.movl(gpr2, gpr8);
        masm.shrq(gpr8, 32);
        masm.addq(gpr9, gpr5);
        masm.addq(gpr9, gpr8);
        masm.shlq(gpr2, 32);
        masm.orq(gpr7, gpr2);
        masm.imulq(gpr10, gpr1);
        masm.movl(gpr8, new AMD64Address(gpr3, 8));
        masm.movl(gpr5, new AMD64Address(gpr3, 4));
        masm.movl(gpr2, gpr6);
        masm.shrq(gpr6, 32);
        masm.addq(gpr9, gpr2);
        masm.movl(gpr2, gpr9);
        masm.shrq(gpr9, 32);
        masm.addq(gpr10, gpr6);
        masm.addq(gpr10, gpr9);
        masm.movq(gpr6, gpr8);
        masm.imulq(gpr8, gpr4);
        masm.imulq(gpr6, gpr1);
        masm.movl(gpr9, gpr8);
        masm.shrq(gpr8, 32);
        masm.addq(gpr10, gpr9);
        masm.movl(gpr9, gpr10);
        masm.shrq(gpr10, 32);
        masm.addq(gpr6, gpr8);
        masm.addq(gpr6, gpr10);
        masm.movq(gpr8, gpr5);
        masm.imulq(gpr5, gpr4);
        masm.imulq(gpr8, gpr1);
        masm.shlq(gpr9, 32);
        masm.orq(gpr9, gpr2);
        masm.movl(gpr1, new AMD64Address(gpr3, 0));
        masm.movl(gpr10, gpr5);
        masm.shrq(gpr5, 32);
        masm.addq(gpr6, gpr10);
        masm.movl(gpr10, gpr6);
        masm.shrq(gpr6, 32);
        masm.addq(gpr8, gpr5);
        masm.addq(gpr8, gpr6);
        masm.imulq(gpr4, gpr1);
        masm.pextrw(gpr2, dest, 3);
        masm.leaq(gpr6, externalAddress(piInvTableTanPtr));
        masm.subq(gpr3, gpr6);
        masm.addl(gpr3, gpr3);
        masm.addl(gpr3, gpr3);
        masm.addl(gpr3, gpr3);
        masm.addl(gpr3, 19);
        masm.movl(gpr5, 32768);
        masm.andl(gpr5, gpr2);
        masm.shrl(gpr2, 4);
        masm.andl(gpr2, 2047);
        masm.subl(gpr2, 1023);
        masm.subl(gpr3, gpr2);
        masm.addq(gpr8, gpr4);
        masm.movl(gpr4, gpr3);
        masm.addl(gpr4, 32);
        masm.cmpl(gpr3, 0);
        masm.jcc(ConditionFlag.Less, bb5);

        masm.negl(gpr3);
        masm.addl(gpr3, 29);
        masm.shll(gpr8);
        masm.movl(gpr6, gpr8);
        masm.andl(gpr8, 1073741823);
        masm.testl(gpr8, 536870912);
        masm.jcc(ConditionFlag.NotEqual, bb6);

        masm.shrl(gpr8);
        masm.movl(gpr2, 0);
        masm.shlq(gpr8, 32);
        masm.orq(gpr8, gpr10);

        masm.bind(bb8);
        masm.cmpq(gpr8, 0);
        masm.jcc(ConditionFlag.Equal, bb9);

        masm.bind(bb10);
        masm.bsrq(gpr10, gpr8);
        masm.movl(gpr3, 29);
        masm.subl(gpr3, gpr10);
        masm.jcc(ConditionFlag.LessEqual, bb11);

        masm.shlq(gpr8);
        masm.movq(gpr1, gpr9);
        masm.shlq(gpr9);
        masm.addl(gpr4, gpr3);
        masm.negl(gpr3);
        masm.addl(gpr3, 64);
        masm.shrq(gpr1);
        masm.shrq(gpr7);
        masm.orq(gpr8, gpr1);
        masm.orq(gpr9, gpr7);

        masm.bind(bb12);
        masm.cvtsi2sdq(dest, gpr8);
        masm.shrq(gpr9, 1);
        masm.cvtsi2sdq(temp3, gpr9);
        masm.xorpd(temp4, temp4);
        masm.shll(gpr4, 4);
        masm.negl(gpr4);
        masm.addl(gpr4, 16368);
        masm.orl(gpr4, gpr5);
        masm.xorl(gpr4, gpr2);
        masm.pinsrw(temp4, gpr4, 3);
        masm.leaq(gpr1, externalAddress(piFourTanPtr));
        masm.movdq(temp2, new AMD64Address(gpr1, 0));                                           // 0x00000000,
                                                                                                // 0x3fe921fb,
        masm.movdq(temp7, new AMD64Address(gpr1, 8));                                           // 0x4611a626,
                                                                                                // 0x3e85110b
        masm.xorpd(temp5, temp5);
        masm.subl(gpr4, 1008);
        masm.pinsrw(temp5, gpr4, 3);
        masm.mulsd(dest, temp4);
        masm.shll(gpr5, 16);
        masm.sarl(gpr5, 31);
        masm.mulsd(temp3, temp5);
        masm.movdqu(temp1, dest);
        masm.mulsd(dest, temp2);
        masm.shrl(gpr6, 30);
        masm.addsd(temp1, temp3);
        masm.mulsd(temp3, temp2);
        masm.addl(gpr6, gpr5);
        masm.xorl(gpr6, gpr5);
        masm.mulsd(temp7, temp1);
        masm.movl(gpr1, gpr6);
        masm.addsd(temp7, temp3);
        masm.movdqu(temp2, dest);
        masm.addsd(dest, temp7);
        masm.subsd(temp2, dest);
        masm.addsd(temp7, temp2);
        masm.movdqu(temp1, externalAddress(piThirtyTwoInvTanPtr));                              // 0x6dc9c883,
                                                                                                // 0x3fe45f30,
                                                                                                // 0x6dc9c883,
                                                                                                // 0x40245f30
        if (masm.supports(CPUFeature.SSE3)) {
            masm.movddup(dest, dest);
        } else {
            masm.movlhps(dest, dest);
        }
        masm.movdqu(temp4, externalAddress(signMaskTanPtr));                                    // 0x00000000,
                                                                                                // 0x80000000,
                                                                                                // 0x00000000,
                                                                                                // 0x80000000
        masm.andpd(temp4, dest);
        masm.mulpd(temp1, dest);
        if (masm.supports(CPUFeature.SSE3)) {
            masm.movddup(temp7, temp7);
        } else {
            masm.movlhps(temp7, temp7);
        }
        masm.movdqu(temp5, externalAddress(oneHalfTanPtr));                                     // 0x00000000,
                                                                                                // 0x3fe00000,
                                                                                                // 0x00000000,
                                                                                                // 0x3fe00000
        masm.movdqu(temp6, externalAddress(mulSixteenPtr));                                     // 0x00000000,
                                                                                                // 0x40300000,
                                                                                                // 0x00000000,
                                                                                                // 0x3ff00000
        masm.por(temp5, temp4);
        masm.addpd(temp1, temp5);
        masm.movdqu(temp5, temp1);
        masm.unpckhpd(temp5, temp5);
        masm.cvttsd2sil(gpr4, temp5);
        masm.cvttpd2dq(temp1, temp1);
        masm.cvtdq2pd(temp1, temp1);
        masm.mulpd(temp1, temp6);
        masm.movdqu(temp3, externalAddress(pOneTanPtr));                                        // 0x54444000,
                                                                                                // 0x3fb921fb,
                                                                                                // 0x54440000,
                                                                                                // 0x3fb921fb
        masm.movdq(temp5, externalAddress(qqTwoTanPtr));                                        // 0x676733af,
                                                                                                // 0x3d32e7b9
        masm.shll(gpr1, 4);
        masm.addl(gpr4, 469248);
        masm.movdqu(temp4, externalAddress(pTwoTanPtr));                                        // 0x67674000,
                                                                                                // 0xbd32e7b9,
                                                                                                // 0x4c4c0000,
                                                                                                // 0x3d468c23
        masm.mulpd(temp3, temp1);
        masm.addl(gpr4, gpr1);
        masm.andl(gpr4, 31);
        masm.mulsd(temp5, temp1);
        masm.movl(gpr3, gpr4);
        masm.mulpd(temp4, temp1);
        masm.shll(gpr3, 1);
        masm.subpd(dest, temp3);
        masm.mulpd(temp1, externalAddress(pThreeTanPtr));                                       // 0x3707344a,
                                                                                                // 0x3aa8a2e0,
                                                                                                // 0x03707345,
                                                                                                // 0x3ae98a2e
        masm.addl(gpr4, gpr3);
        masm.shll(gpr3, 2);
        masm.addl(gpr4, gpr3);
        masm.addsd(temp5, dest);
        masm.movdqu(temp2, dest);
        masm.subpd(dest, temp4);
        masm.movdq(temp6, externalAddress(onePtr));                                             // 0x00000000,
                                                                                                // 0x3ff00000
        masm.shll(gpr4, 4);
        masm.leaq(gpr1, externalAddress(cTableTanPtr));
        masm.andpd(temp5, externalAddress(maskThirtyFiveTanPtr));                               // 0xfffc0000,
                                                                                                // 0xffffffff,
                                                                                                // 0x00000000,
                                                                                                // 0x00000000
        masm.movdqu(temp3, dest);
        masm.addq(gpr1, gpr4);
        masm.subpd(temp2, dest);
        masm.unpckhpd(dest, dest);
        masm.divsd(temp6, temp5);
        masm.subpd(temp2, temp4);
        masm.subsd(temp3, temp5);
        masm.subpd(temp2, temp1);
        masm.movdqu(temp1, new AMD64Address(gpr1, 48));
        masm.addpd(temp2, temp7);
        masm.movdqu(temp7, new AMD64Address(gpr1, 16));
        masm.mulpd(temp7, dest);
        masm.movdqu(temp4, new AMD64Address(gpr1, 96));
        masm.mulpd(temp1, dest);
        masm.mulpd(temp4, dest);
        masm.addsd(temp2, temp3);
        masm.movdqu(temp3, dest);
        masm.mulpd(dest, dest);
        masm.addpd(temp7, new AMD64Address(gpr1, 0));
        masm.addpd(temp1, new AMD64Address(gpr1, 32));
        masm.mulpd(temp1, dest);
        masm.addpd(temp4, new AMD64Address(gpr1, 80));
        masm.addpd(temp7, temp1);
        masm.movdqu(temp1, new AMD64Address(gpr1, 112));
        masm.mulpd(temp1, dest);
        masm.mulpd(dest, dest);
        masm.addpd(temp4, temp1);
        masm.movdqu(temp1, new AMD64Address(gpr1, 64));
        masm.mulpd(temp1, dest);
        masm.addpd(temp7, temp1);
        masm.movdqu(temp1, temp3);
        masm.mulpd(temp3, dest);
        masm.mulsd(dest, dest);
        masm.mulpd(temp1, new AMD64Address(gpr1, 144));
        masm.mulpd(temp4, temp3);
        masm.movdqu(temp3, temp1);
        masm.addpd(temp7, temp4);
        masm.movdqu(temp4, temp1);
        masm.mulsd(dest, temp7);
        masm.unpckhpd(temp7, temp7);
        masm.addsd(dest, temp7);
        masm.unpckhpd(temp1, temp1);
        masm.addsd(temp3, temp1);
        masm.subsd(temp4, temp3);
        masm.addsd(temp1, temp4);
        masm.movdqu(temp4, temp2);
        masm.movdq(temp7, new AMD64Address(gpr1, 144));
        masm.unpckhpd(temp2, temp2);
        masm.addsd(temp7, new AMD64Address(gpr1, 152));
        masm.mulsd(temp7, temp2);
        masm.addsd(temp7, new AMD64Address(gpr1, 136));
        masm.addsd(temp7, temp1);
        masm.addsd(dest, temp7);
        masm.movdq(temp7, externalAddress(onePtr));                                             // 0x00000000,
                                                                                                // 0x3ff00000
        masm.mulsd(temp4, temp6);
        masm.movdq(temp2, new AMD64Address(gpr1, 168));
        masm.andpd(temp2, temp6);
        masm.mulsd(temp5, temp2);
        masm.mulsd(temp6, new AMD64Address(gpr1, 160));
        masm.subsd(temp7, temp5);
        masm.subsd(temp2, new AMD64Address(gpr1, 128));
        masm.subsd(temp7, temp4);
        masm.mulsd(temp7, temp6);
        masm.movdqu(temp4, temp3);
        masm.subsd(temp3, temp2);
        masm.addsd(temp2, temp3);
        masm.subsd(temp4, temp2);
        masm.addsd(dest, temp4);
        masm.subsd(dest, temp7);
        masm.addsd(dest, temp3);
        masm.jmp(bb15);

        masm.bind(bb9);
        masm.addl(gpr4, 64);
        masm.movq(gpr8, gpr9);
        masm.movq(gpr9, gpr7);
        masm.movl(gpr7, 0);
        masm.cmpq(gpr8, 0);
        masm.jcc(ConditionFlag.NotEqual, bb10);

        masm.addl(gpr4, 64);
        masm.movq(gpr8, gpr9);
        masm.movq(gpr9, gpr7);
        masm.cmpq(gpr8, 0);
        masm.jcc(ConditionFlag.NotEqual, bb10);

        masm.jmp(bb12);

        masm.bind(bb11);
        masm.jcc(ConditionFlag.Equal, bb12);

        masm.negl(gpr3);
        masm.shrq(gpr9);
        masm.movq(gpr1, gpr8);
        masm.shrq(gpr8);
        masm.subl(gpr4, gpr3);
        masm.negl(gpr3);
        masm.addl(gpr3, 64);
        masm.shlq(gpr1);
        masm.orq(gpr9, gpr1);
        masm.jmp(bb12);

        masm.bind(bb5);
        masm.notl(gpr3);
        masm.shlq(gpr8, 32);
        masm.orq(gpr8, gpr10);
        masm.shlq(gpr8);
        masm.movq(gpr6, gpr8);
        masm.testl(gpr8, Integer.MIN_VALUE);
        masm.jcc(ConditionFlag.NotEqual, bb13);

        masm.shrl(gpr8);
        masm.movl(gpr2, 0);
        masm.shrq(gpr6, 2);
        masm.jmp(bb8);

        masm.bind(bb6);
        masm.shrl(gpr8);
        masm.movl(gpr2, 1073741824);
        masm.shrl(gpr2);
        masm.shlq(gpr8, 32);
        masm.orq(gpr8, gpr10);
        masm.shlq(gpr2, 32);
        masm.addl(gpr6, 1073741824);
        masm.movl(gpr3, 0);
        masm.movl(gpr10, 0);
        masm.subq(gpr3, gpr7);
        masm.sbbq(gpr10, gpr9);
        masm.sbbq(gpr2, gpr8);
        masm.movq(gpr7, gpr3);
        masm.movq(gpr9, gpr10);
        masm.movq(gpr8, gpr2);
        masm.movl(gpr2, 32768);
        masm.jmp(bb8);

        masm.bind(bb13);
        masm.shrl(gpr8);
        masm.movq(gpr2, 0x100000000L);
        masm.shrq(gpr2);
        masm.movl(gpr3, 0);
        masm.movl(gpr10, 0);
        masm.subq(gpr3, gpr7);
        masm.sbbq(gpr10, gpr9);
        masm.sbbq(gpr2, gpr8);
        masm.movq(gpr7, gpr3);
        masm.movq(gpr9, gpr10);
        masm.movq(gpr8, gpr2);
        masm.movl(gpr2, 32768);
        masm.shrq(gpr6, 2);
        masm.addl(gpr6, 1073741824);
        masm.jmp(bb8);

        masm.bind(bb15);
    }

    /*
     * Copyright (c) 2014, 2016, Intel Corporation. All rights reserved. Intel Math Library (LIBM)
     * Source Code
     *
     * ALGORITHM DESCRIPTION - EXP() ---------------------
     *
     * Description: Let K = 64 (table size). x x/log(2) n e = 2 = 2 * T[j] * (1 + P(y)) where x =
     * m*log(2)/K + y, y in [-log(2)/K..log(2)/K] m = n*K + j, m,n,j - signed integer, j in
     * [-K/2..K/2] j/K values of 2 are tabulated as T[j] = T_hi[j] ( 1 + T_lo[j]).
     *
     * P(y) is a minimax polynomial approximation of exp(x)-1 on small interval
     * [-log(2)/K..log(2)/K] (were calculated by Maple V).
     *
     * To avoid problems with arithmetic overflow and underflow, n n1 n2 value of 2 is safely
     * computed as 2 * 2 where n1 in [-BIAS/2..BIAS/2] where BIAS is a value of exponent bias.
     *
     * Special cases: exp(NaN) = NaN exp(+INF) = +INF exp(-INF) = 0 exp(x) = 1 for subnormals for
     * finite argument, only exp(0)=1 is exact For IEEE double if x > 709.782712893383973096 then
     * exp(x) overflow if x < -745.133219101941108420 then exp(x) underflow
     *
     */

    private static int[] cvExp = {
                    0x652b82fe, 0x40571547, 0x652b82fe, 0x40571547, 0xfefa0000,
                    0x3f862e42, 0xfefa0000, 0x3f862e42, 0xbc9e3b3a, 0x3d1cf79a,
                    0xbc9e3b3a, 0x3d1cf79a, 0xfffffffe, 0x3fdfffff, 0xfffffffe,
                    0x3fdfffff, 0xe3289860, 0x3f56c15c, 0x555b9e25, 0x3fa55555,
                    0xc090cf0f, 0x3f811115, 0x55548ba1, 0x3fc55555
    };

    private static int[] shifterExp = {
                    0x00000000, 0x43380000, 0x00000000, 0x43380000
    };

    private static int[] mMaskExp = {
                    0xffffffc0, 0x00000000, 0xffffffc0, 0x00000000
    };

    private static int[] biasExp = {
                    0x0000ffc0, 0x00000000, 0x0000ffc0, 0x00000000
    };

    private static int[] tblAddrExp = {
                    0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x0e03754d,
                    0x3cad7bbf, 0x3e778060, 0x00002c9a, 0x3567f613, 0x3c8cd252,
                    0xd3158574, 0x000059b0, 0x61e6c861, 0x3c60f74e, 0x18759bc8,
                    0x00008745, 0x5d837b6c, 0x3c979aa6, 0x6cf9890f, 0x0000b558,
                    0x702f9cd1, 0x3c3ebe3d, 0x32d3d1a2, 0x0000e3ec, 0x1e63bcd8,
                    0x3ca3516e, 0xd0125b50, 0x00011301, 0x26f0387b, 0x3ca4c554,
                    0xaea92ddf, 0x0001429a, 0x62523fb6, 0x3ca95153, 0x3c7d517a,
                    0x000172b8, 0x3f1353bf, 0x3c8b898c, 0xeb6fcb75, 0x0001a35b,
                    0x3e3a2f5f, 0x3c9aecf7, 0x3168b9aa, 0x0001d487, 0x44a6c38d,
                    0x3c8a6f41, 0x88628cd6, 0x0002063b, 0xe3a8a894, 0x3c968efd,
                    0x6e756238, 0x0002387a, 0x981fe7f2, 0x3c80472b, 0x65e27cdd,
                    0x00026b45, 0x6d09ab31, 0x3c82f7e1, 0xf51fdee1, 0x00029e9d,
                    0x720c0ab3, 0x3c8b3782, 0xa6e4030b, 0x0002d285, 0x4db0abb6,
                    0x3c834d75, 0x0a31b715, 0x000306fe, 0x5dd3f84a, 0x3c8fdd39,
                    0xb26416ff, 0x00033c08, 0xcc187d29, 0x3ca12f8c, 0x373aa9ca,
                    0x000371a7, 0x738b5e8b, 0x3ca7d229, 0x34e59ff6, 0x0003a7db,
                    0xa72a4c6d, 0x3c859f48, 0x4c123422, 0x0003dea6, 0x259d9205,
                    0x3ca8b846, 0x21f72e29, 0x0004160a, 0x60c2ac12, 0x3c4363ed,
                    0x6061892d, 0x00044e08, 0xdaa10379, 0x3c6ecce1, 0xb5c13cd0,
                    0x000486a2, 0xbb7aafb0, 0x3c7690ce, 0xd5362a27, 0x0004bfda,
                    0x9b282a09, 0x3ca083cc, 0x769d2ca6, 0x0004f9b2, 0xc1aae707,
                    0x3ca509b0, 0x569d4f81, 0x0005342b, 0x18fdd78e, 0x3c933505,
                    0x36b527da, 0x00056f47, 0xe21c5409, 0x3c9063e1, 0xdd485429,
                    0x0005ab07, 0x2b64c035, 0x3c9432e6, 0x15ad2148, 0x0005e76f,
                    0x99f08c0a, 0x3ca01284, 0xb03a5584, 0x0006247e, 0x0073dc06,
                    0x3c99f087, 0x82552224, 0x00066238, 0x0da05571, 0x3c998d4d,
                    0x667f3bcc, 0x0006a09e, 0x86ce4786, 0x3ca52bb9, 0x3c651a2e,
                    0x0006dfb2, 0x206f0dab, 0x3ca32092, 0xe8ec5f73, 0x00071f75,
                    0x8e17a7a6, 0x3ca06122, 0x564267c8, 0x00075feb, 0x461e9f86,
                    0x3ca244ac, 0x73eb0186, 0x0007a114, 0xabd66c55, 0x3c65ebe1,
                    0x36cf4e62, 0x0007e2f3, 0xbbff67d0, 0x3c96fe9f, 0x994cce12,
                    0x00082589, 0x14c801df, 0x3c951f14, 0x9b4492ec, 0x000868d9,
                    0xc1f0eab4, 0x3c8db72f, 0x422aa0db, 0x0008ace5, 0x59f35f44,
                    0x3c7bf683, 0x99157736, 0x0008f1ae, 0x9c06283c, 0x3ca360ba,
                    0xb0cdc5e4, 0x00093737, 0x20f962aa, 0x3c95e8d1, 0x9fde4e4f,
                    0x00097d82, 0x2b91ce27, 0x3c71affc, 0x82a3f090, 0x0009c491,
                    0x589a2ebd, 0x3c9b6d34, 0x7b5de564, 0x000a0c66, 0x9ab89880,
                    0x3c95277c, 0xb23e255c, 0x000a5503, 0x6e735ab3, 0x3c846984,
                    0x5579fdbf, 0x000a9e6b, 0x92cb3387, 0x3c8c1a77, 0x995ad3ad,
                    0x000ae89f, 0xdc2d1d96, 0x3ca22466, 0xb84f15fa, 0x000b33a2,
                    0xb19505ae, 0x3ca1112e, 0xf2fb5e46, 0x000b7f76, 0x0a5fddcd,
                    0x3c74ffd7, 0x904bc1d2, 0x000bcc1e, 0x30af0cb3, 0x3c736eae,
                    0xdd85529c, 0x000c199b, 0xd10959ac, 0x3c84e08f, 0x2e57d14b,
                    0x000c67f1, 0x6c921968, 0x3c676b2c, 0xdcef9069, 0x000cb720,
                    0x36df99b3, 0x3c937009, 0x4a07897b, 0x000d072d, 0xa63d07a7,
                    0x3c74a385, 0xdcfba487, 0x000d5818, 0xd5c192ac, 0x3c8e5a50,
                    0x03db3285, 0x000da9e6, 0x1c4a9792, 0x3c98bb73, 0x337b9b5e,
                    0x000dfc97, 0x603a88d3, 0x3c74b604, 0xe78b3ff6, 0x000e502e,
                    0x92094926, 0x3c916f27, 0xa2a490d9, 0x000ea4af, 0x41aa2008,
                    0x3c8ec3bc, 0xee615a27, 0x000efa1b, 0x31d185ee, 0x3c8a64a9,
                    0x5b6e4540, 0x000f5076, 0x4d91cd9d, 0x3c77893b, 0x819e90d8,
                    0x000fa7c1
    };

    private static int[] allOnesExp = {
                    0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff
    };

    private static int[] expBias = {
                    0x00000000, 0x3ff00000, 0x00000000, 0x3ff00000
    };

    private static int[] xMaxExp = {
                    0xffffffff, 0x7fefffff
    };

    private static int[] xMinExp = {
                    0x00000000, 0x00100000
    };

    private static int[] infExp = {
                    0x00000000, 0x7ff00000
    };

    private static int[] zeroExp = {
                    0x00000000, 0x00000000
    };

    public void expIntrinsic(Register dest, Register value, CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        ArrayDataPointerConstant onePtr = new ArrayDataPointerConstant(one, 16);
        ArrayDataPointerConstant cvExpPtr = new ArrayDataPointerConstant(cvExp, 16);
        ArrayDataPointerConstant shifterExpPtr = new ArrayDataPointerConstant(shifterExp, 8);
        ArrayDataPointerConstant mMaskExpPtr = new ArrayDataPointerConstant(mMaskExp, 16);
        ArrayDataPointerConstant biasExpPtr = new ArrayDataPointerConstant(biasExp, 16);
        ArrayDataPointerConstant tblAddrExpPtr = new ArrayDataPointerConstant(tblAddrExp, 16);
        ArrayDataPointerConstant expBiasPtr = new ArrayDataPointerConstant(expBias, 8);
        ArrayDataPointerConstant xMaxExpPtr = new ArrayDataPointerConstant(xMaxExp, 8);
        ArrayDataPointerConstant xMinExpPtr = new ArrayDataPointerConstant(xMinExp, 8);
        ArrayDataPointerConstant infExpPtr = new ArrayDataPointerConstant(infExp, 8);
        ArrayDataPointerConstant zeroExpPtr = new ArrayDataPointerConstant(zeroExp, 8);
        ArrayDataPointerConstant allOnesExpPtr = new ArrayDataPointerConstant(allOnesExp, 8);

        Label bb0 = new Label();
        Label bb1 = new Label();
        Label bb2 = new Label();
        Label bb3 = new Label();
        Label bb4 = new Label();
        Label bb5 = new Label();
        Label bb7 = new Label();
        Label bb8 = new Label();
        Label bb9 = new Label();
        Label bb10 = new Label();
        Label bb11 = new Label();
        Label bb12 = new Label();
        Label bb14 = new Label();

        Register gpr1 = asRegister(gpr1Temp, AMD64Kind.QWORD);
        Register gpr2 = asRegister(gpr2Temp, AMD64Kind.QWORD);
        Register gpr3 = asRegister(rcxTemp, AMD64Kind.QWORD);
        Register gpr4 = asRegister(gpr4Temp, AMD64Kind.QWORD);
        Register gpr5 = asRegister(gpr5Temp, AMD64Kind.QWORD);

        Register temp1 = asRegister(xmm1Temp, AMD64Kind.DOUBLE);
        Register temp2 = asRegister(xmm2Temp, AMD64Kind.DOUBLE);
        Register temp3 = asRegister(xmm3Temp, AMD64Kind.DOUBLE);
        Register temp4 = asRegister(xmm4Temp, AMD64Kind.DOUBLE);
        Register temp5 = asRegister(xmm5Temp, AMD64Kind.DOUBLE);
        Register temp6 = asRegister(xmm6Temp, AMD64Kind.DOUBLE);
        Register temp7 = asRegister(xmm7Temp, AMD64Kind.DOUBLE);
        Register temp8 = asRegister(xmm8Temp, AMD64Kind.DOUBLE);
        Register temp9 = asRegister(xmm9Temp, AMD64Kind.DOUBLE);
        Register temp10 = asRegister(xmm10Temp, AMD64Kind.DOUBLE);

        AMD64Address stackSlot = (AMD64Address) crb.asAddress(stackTemp);

        setCrb(crb);
        masm.movsd(stackSlot, value);
        if (dest.encoding != value.encoding) {
            masm.movdqu(dest, value);
        }

        masm.movdqu(temp9, externalAddress(mMaskExpPtr));                                // 0xffffffc0,
                                                                                         // 0x00000000,
                                                                                         // 0xffffffc0,
                                                                                         // 0x00000000
        masm.movdqu(temp10, externalAddress(biasExpPtr));                                // 0x0000ffc0,
                                                                                         // 0x00000000,
                                                                                         // 0x0000ffc0,
                                                                                         // 0x00000000
        masm.unpcklpd(dest, dest);
        masm.leaq(gpr5, stackSlot);
        masm.leaq(gpr2, externalAddress(cvExpPtr));
        masm.movdqu(temp1, new AMD64Address(gpr2, 0));                                   // 0x652b82fe,
                                                                                         // 0x40571547,
                                                                                         // 0x652b82fe,
                                                                                         // 0x40571547
        masm.movdqu(temp6, externalAddress(shifterExpPtr));                              // 0x00000000,
                                                                                         // 0x43380000,
                                                                                         // 0x00000000,
                                                                                         // 0x43380000
        masm.movdqu(temp2, new AMD64Address(gpr2, 16));                                  // 0xfefa0000,
                                                                                         // 0x3f862e42,
                                                                                         // 0xfefa0000,
                                                                                         // 0x3f862e42
        masm.movdqu(temp3, new AMD64Address(gpr2, 32));                                  // 0xbc9e3b3a,
                                                                                         // 0x3d1cf79a,
                                                                                         // 0xbc9e3b3a,
                                                                                         // 0x3d1cf79a
        masm.pextrw(gpr1, dest, 3);
        masm.andl(gpr1, 32767);
        masm.movl(gpr4, 16527);
        masm.subl(gpr4, gpr1);
        masm.subl(gpr1, 15504);
        masm.orl(gpr4, gpr1);
        masm.cmpl(gpr4, Integer.MIN_VALUE);
        masm.jcc(ConditionFlag.AboveEqual, bb0);

        masm.leaq(gpr4, externalAddress(tblAddrExpPtr));
        masm.movdqu(temp8, new AMD64Address(gpr2, 48));                                  // 0xfffffffe,
                                                                                         // 0x3fdfffff,
                                                                                         // 0xfffffffe,
                                                                                         // 0x3fdfffff
        masm.movdqu(temp4, new AMD64Address(gpr2, 64));                                  // 0xe3289860,
                                                                                         // 0x3f56c15c,
                                                                                         // 0x555b9e25,
                                                                                         // 0x3fa55555
        masm.movdqu(temp5, new AMD64Address(gpr2, 80));                                  // 0xc090cf0f,
                                                                                         // 0x3f811115,
                                                                                         // 0x55548ba1,
                                                                                         // 0x3fc55555
        masm.mulpd(temp1, dest);
        masm.addpd(temp1, temp6);
        masm.movapd(temp7, temp1);
        masm.movdl(gpr1, temp1);
        masm.pand(temp7, temp9);
        masm.subpd(temp1, temp6);
        masm.mulpd(temp2, temp1);
        masm.mulpd(temp3, temp1);
        masm.paddq(temp7, temp10);
        masm.subpd(dest, temp2);
        masm.movl(gpr3, gpr1);
        masm.andl(gpr3, 63);
        masm.shll(gpr3, 4);
        masm.movdqu(temp2, new AMD64Address(gpr3, gpr4, Scale.Times1, 0));
        masm.sarl(gpr1, 6);
        masm.psllq(temp7, 46);
        masm.subpd(dest, temp3);
        masm.mulpd(temp4, dest);
        masm.movl(gpr4, gpr1);
        masm.movapd(temp6, dest);
        masm.movapd(temp1, dest);
        masm.mulpd(temp6, temp6);
        masm.mulpd(dest, temp6);
        masm.addpd(temp5, temp4);
        masm.mulsd(dest, temp6);
        masm.mulpd(temp6, temp8);
        masm.addsd(temp1, temp2);
        masm.unpckhpd(temp2, temp2);
        masm.mulpd(dest, temp5);
        masm.addsd(temp1, dest);
        masm.por(temp2, temp7);
        masm.unpckhpd(dest, dest);
        masm.addsd(dest, temp1);
        masm.addsd(dest, temp6);
        masm.addl(gpr4, 894);
        masm.cmpl(gpr4, 1916);
        masm.jcc(ConditionFlag.Above, bb1);

        masm.mulsd(dest, temp2);
        masm.addsd(dest, temp2);
        masm.jmp(bb14);

        masm.bind(bb1);
        masm.movdqu(temp6, externalAddress(expBiasPtr));                                 // 0x00000000,
                                                                                         // 0x3ff00000,
                                                                                         // 0x00000000,
                                                                                         // 0x3ff00000
        masm.xorpd(temp3, temp3);
        masm.movdqu(temp4, externalAddress(allOnesExpPtr));                              // 0xffffffff,
                                                                                         // 0xffffffff,
                                                                                         // 0xffffffff,
                                                                                         // 0xffffffff
        masm.movl(gpr4, -1022);
        masm.subl(gpr4, gpr1);
        masm.movdl(temp5, gpr4);
        masm.psllq(temp4, temp5);
        masm.movl(gpr3, gpr1);
        masm.sarl(gpr1, 1);
        masm.pinsrw(temp3, gpr1, 3);
        masm.psllq(temp3, 4);
        masm.psubd(temp2, temp3);
        masm.mulsd(dest, temp2);
        masm.cmpl(gpr4, 52);
        masm.jcc(ConditionFlag.Greater, bb2);

        masm.pand(temp4, temp2);
        masm.paddd(temp3, temp6);
        masm.subsd(temp2, temp4);
        masm.addsd(dest, temp2);
        masm.cmpl(gpr3, 1023);
        masm.jcc(ConditionFlag.GreaterEqual, bb3);

        masm.pextrw(gpr3, dest, 3);
        masm.andl(gpr3, 32768);
        masm.orl(gpr4, gpr3);
        masm.cmpl(gpr4, 0);
        masm.jcc(ConditionFlag.Equal, bb4);

        masm.movapd(temp6, dest);
        masm.addsd(dest, temp4);
        masm.mulsd(dest, temp3);
        masm.pextrw(gpr3, dest, 3);
        masm.andl(gpr3, 32752);
        masm.cmpl(gpr3, 0);
        masm.jcc(ConditionFlag.Equal, bb5);

        masm.jmp(bb14);

        masm.bind(bb5);
        masm.mulsd(temp6, temp3);
        masm.mulsd(temp4, temp3);
        masm.movdqu(dest, temp6);
        masm.pxor(temp6, temp4);
        masm.psrad(temp6, 31);
        masm.pshufd(temp6, temp6, 85);
        masm.psllq(dest, 1);
        masm.psrlq(dest, 1);
        masm.pxor(dest, temp6);
        masm.psrlq(temp6, 63);
        masm.paddq(dest, temp6);
        masm.paddq(dest, temp4);
        masm.jmp(bb14);

        masm.bind(bb4);
        masm.addsd(dest, temp4);
        masm.mulsd(dest, temp3);
        masm.jmp(bb14);

        masm.bind(bb3);
        masm.addsd(dest, temp4);
        masm.mulsd(dest, temp3);
        masm.pextrw(gpr3, dest, 3);
        masm.andl(gpr3, 32752);
        masm.cmpl(gpr3, 32752);
        masm.jcc(ConditionFlag.AboveEqual, bb7);

        masm.jmp(bb14);

        masm.bind(bb2);
        masm.paddd(temp3, temp6);
        masm.addpd(dest, temp2);
        masm.mulsd(dest, temp3);
        masm.jmp(bb14);

        masm.bind(bb8);
        masm.movsd(dest, externalAddress(xMaxExpPtr));                                   // 0xffffffff,
                                                                                         // 0x7fefffff
        masm.movsd(temp8, externalAddress(xMinExpPtr));                                  // 0x00000000,
                                                                                         // 0x00100000
        masm.cmpl(gpr1, 2146435072);
        masm.jcc(ConditionFlag.AboveEqual, bb9);

        masm.movl(gpr1, new AMD64Address(gpr5, 4));
        masm.cmpl(gpr1, Integer.MIN_VALUE);
        masm.jcc(ConditionFlag.AboveEqual, bb10);

        masm.mulsd(dest, dest);

        masm.bind(bb7);
        masm.jmp(bb14);

        masm.bind(bb10);
        masm.mulsd(dest, temp8);
        masm.jmp(bb14);

        masm.bind(bb9);
        masm.movl(gpr4, stackSlot);
        masm.cmpl(gpr1, 2146435072);
        masm.jcc(ConditionFlag.Above, bb11);

        masm.cmpl(gpr4, 0);
        masm.jcc(ConditionFlag.NotEqual, bb11);

        masm.movl(gpr1, new AMD64Address(gpr5, 4));
        masm.cmpl(gpr1, 2146435072);
        masm.jcc(ConditionFlag.NotEqual, bb12);

        masm.movsd(dest, externalAddress(infExpPtr));                                    // 0x00000000,
                                                                                         // 0x7ff00000
        masm.jmp(bb14);

        masm.bind(bb12);
        masm.movsd(dest, externalAddress(zeroExpPtr));                                   // 0x00000000,
                                                                                         // 0x00000000
        masm.jmp(bb14);

        masm.bind(bb11);
        masm.movsd(dest, stackSlot);
        masm.addsd(dest, dest);
        masm.jmp(bb14);

        masm.bind(bb0);
        masm.movl(gpr1, new AMD64Address(gpr5, 4));
        masm.andl(gpr1, 2147483647);
        masm.cmpl(gpr1, 1083179008);
        masm.jcc(ConditionFlag.AboveEqual, bb8);

        masm.addsd(dest, externalAddress(onePtr));                                       // 0x00000000,
                                                                                         // 0x3ff00000
        masm.bind(bb14);
    }
}
