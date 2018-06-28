/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

public final class AMD64MathIntrinsicBinaryOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64MathIntrinsicBinaryOp> TYPE = LIRInstructionClass.create(AMD64MathIntrinsicBinaryOp.class);

    public enum BinaryIntrinsicOpcode {
        POW
    }

    @Opcode private final BinaryIntrinsicOpcode opcode;
    @Def protected Value result;
    @Use protected Value input;
    @Use protected Value secondInput;
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

    CompilationResultBuilder internalCrb;

    public AMD64MathIntrinsicBinaryOp(LIRGeneratorTool tool, BinaryIntrinsicOpcode opcode, Value result, Value input, Value alternateInput) {
        super(TYPE);
        this.opcode = opcode;
        this.result = result;
        this.input = input;
        this.secondInput = alternateInput;
        if (opcode == BinaryIntrinsicOpcode.POW) {
            this.gpr1Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
            this.gpr2Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
            this.rcxTemp = AMD64.rcx.asValue(LIRKind.value(AMD64Kind.QWORD));
            this.gpr4Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
            this.gpr5Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
            this.gpr6Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
            this.gpr7Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
            this.gpr8Temp = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));

            this.xmm1Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm2Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm3Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm4Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm5Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm6Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm7Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm8Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm9Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
            this.xmm10Temp = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
        }
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
            case POW:
                powIntrinsic(asRegister(result, AMD64Kind.DOUBLE), asRegister(input, AMD64Kind.DOUBLE), asRegister(secondInput, AMD64Kind.DOUBLE), crb, masm);
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    /*
     * Copyright (c) 2014, 2016, Intel Corporation. All rights reserved. Intel Math Library (LIBM)
     * Source Code
     *
     * ALGORITHM DESCRIPTION - POW() ---------------------
     *
     * Let x=2^k * mx, mx in [1,2)
     *
     * log2(x) calculation:
     *
     * Get B~1/mx based on the output of rcpps instruction (B0) B = int((B0*LH*2^9+0.5))/2^9 LH is a
     * short approximation for log2(e)
     *
     * Reduced argument, scaled by LH: r=B*mx-LH (computed accurately in high and low parts)
     *
     * log2(x) result: k - log2(B) + p(r) p(r) is a degree 8 polynomial -log2(B) read from data
     * table (high, low parts) log2(x) is formed from high and low parts For |x| in [1-1/32,
     * 1+1/16), a slower but more accurate computation based om the same table design is performed.
     *
     * Main path is taken if | floor(log2(|log2(|x|)|) + floor(log2|y|) | < 8, to filter out all
     * potential OF/UF cases. exp2(y*log2(x)) is computed using an 8-bit index table and a degree 5
     * polynomial
     *
     * Special cases: pow(-0,y) = -INF and raises the divide-by-zero exception for y an odd integer
     * < 0. pow(-0,y) = +INF and raises the divide-by-zero exception for y < 0 and not an odd
     * integer. pow(-0,y) = -0 for y an odd integer > 0. pow(-0,y) = +0 for y > 0 and not an odd
     * integer. pow(-1,-INF) = NaN. pow(+1,y) = NaN for any y, even a NaN. pow(x,-0) = 1 for any x,
     * even a NaN. pow(x,y) = a NaN and raises the invalid exception for finite x < 0 and finite
     * non-integer y. pow(x,-INF) = +INF for |x|<1. pow(x,-INF) = +0 for |x|>1. pow(x,+INF) = +0 for
     * |x|<1. pow(x,+INF) = +INF for |x|>1. pow(-INF,y) = -0 for y an odd integer < 0. pow(-INF,y) =
     * +0 for y < 0 and not an odd integer. pow(-INF,y) = -INF for y an odd integer > 0. pow(-INF,y)
     * = +INF for y > 0 and not an odd integer. pow(+INF,y) = +0 for y <0. pow(+INF,y) = +INF for y
     * >0.
     *
     */

    private static int[] highSigMask = {
                    0x00000000, 0xfffff800, 0x00000000, 0xfffff800
    };

    private static int[] logTwoE = {
                    0x00000000, 0x3ff72000, 0x161bb241, 0xbf5dabe1
    };

    private static int[] highmaskY = {
                    0x00000000, 0xfffffff8, 0x00000000, 0xffffffff
    };

    private static int[] tExp = {
                    0x00000000, 0x3ff00000, 0x00000000, 0x3b700000, 0xfa5abcbf,
                    0x3ff00b1a, 0xa7609f71, 0xbc84f6b2, 0xa9fb3335, 0x3ff0163d,
                    0x9ab8cdb7, 0x3c9b6129, 0x143b0281, 0x3ff02168, 0x0fc54eb6,
                    0xbc82bf31, 0x3e778061, 0x3ff02c9a, 0x535b085d, 0xbc719083,
                    0x2e11bbcc, 0x3ff037d4, 0xeeade11a, 0x3c656811, 0xe86e7f85,
                    0x3ff04315, 0x1977c96e, 0xbc90a31c, 0x72f654b1, 0x3ff04e5f,
                    0x3aa0d08c, 0x3c84c379, 0xd3158574, 0x3ff059b0, 0xa475b465,
                    0x3c8d73e2, 0x0e3c1f89, 0x3ff0650a, 0x5799c397, 0xbc95cb7b,
                    0x29ddf6de, 0x3ff0706b, 0xe2b13c27, 0xbc8c91df, 0x2b72a836,
                    0x3ff07bd4, 0x54458700, 0x3c832334, 0x18759bc8, 0x3ff08745,
                    0x4bb284ff, 0x3c6186be, 0xf66607e0, 0x3ff092bd, 0x800a3fd1,
                    0xbc968063, 0xcac6f383, 0x3ff09e3e, 0x18316136, 0x3c914878,
                    0x9b1f3919, 0x3ff0a9c7, 0x873d1d38, 0x3c85d16c, 0x6cf9890f,
                    0x3ff0b558, 0x4adc610b, 0x3c98a62e, 0x45e46c85, 0x3ff0c0f1,
                    0x06d21cef, 0x3c94f989, 0x2b7247f7, 0x3ff0cc92, 0x16e24f71,
                    0x3c901edc, 0x23395dec, 0x3ff0d83b, 0xe43f316a, 0xbc9bc14d,
                    0x32d3d1a2, 0x3ff0e3ec, 0x27c57b52, 0x3c403a17, 0x5fdfa9c5,
                    0x3ff0efa5, 0xbc54021b, 0xbc949db9, 0xaffed31b, 0x3ff0fb66,
                    0xc44ebd7b, 0xbc6b9bed, 0x28d7233e, 0x3ff10730, 0x1692fdd5,
                    0x3c8d46eb, 0xd0125b51, 0x3ff11301, 0x39449b3a, 0xbc96c510,
                    0xab5e2ab6, 0x3ff11edb, 0xf703fb72, 0xbc9ca454, 0xc06c31cc,
                    0x3ff12abd, 0xb36ca5c7, 0xbc51b514, 0x14f204ab, 0x3ff136a8,
                    0xba48dcf0, 0xbc67108f, 0xaea92de0, 0x3ff1429a, 0x9af1369e,
                    0xbc932fbf, 0x934f312e, 0x3ff14e95, 0x39bf44ab, 0xbc8b91e8,
                    0xc8a58e51, 0x3ff15a98, 0xb9eeab0a, 0x3c82406a, 0x5471c3c2,
                    0x3ff166a4, 0x82ea1a32, 0x3c58f23b, 0x3c7d517b, 0x3ff172b8,
                    0xb9d78a76, 0xbc819041, 0x8695bbc0, 0x3ff17ed4, 0xe2ac5a64,
                    0x3c709e3f, 0x388c8dea, 0x3ff18af9, 0xd1970f6c, 0xbc911023,
                    0x58375d2f, 0x3ff19726, 0x85f17e08, 0x3c94aadd, 0xeb6fcb75,
                    0x3ff1a35b, 0x7b4968e4, 0x3c8e5b4c, 0xf8138a1c, 0x3ff1af99,
                    0xa4b69280, 0x3c97bf85, 0x84045cd4, 0x3ff1bbe0, 0x352ef607,
                    0xbc995386, 0x95281c6b, 0x3ff1c82f, 0x8010f8c9, 0x3c900977,
                    0x3168b9aa, 0x3ff1d487, 0x00a2643c, 0x3c9e016e, 0x5eb44027,
                    0x3ff1e0e7, 0x088cb6de, 0xbc96fdd8, 0x22fcd91d, 0x3ff1ed50,
                    0x027bb78c, 0xbc91df98, 0x8438ce4d, 0x3ff1f9c1, 0xa097af5c,
                    0xbc9bf524, 0x88628cd6, 0x3ff2063b, 0x814a8495, 0x3c8dc775,
                    0x3578a819, 0x3ff212be, 0x2cfcaac9, 0x3c93592d, 0x917ddc96,
                    0x3ff21f49, 0x9494a5ee, 0x3c82a97e, 0xa27912d1, 0x3ff22bdd,
                    0x5577d69f, 0x3c8d34fb, 0x6e756238, 0x3ff2387a, 0xb6c70573,
                    0x3c99b07e, 0xfb82140a, 0x3ff2451f, 0x911ca996, 0x3c8acfcc,
                    0x4fb2a63f, 0x3ff251ce, 0xbef4f4a4, 0x3c8ac155, 0x711ece75,
                    0x3ff25e85, 0x4ac31b2c, 0x3c93e1a2, 0x65e27cdd, 0x3ff26b45,
                    0x9940e9d9, 0x3c82bd33, 0x341ddf29, 0x3ff2780e, 0x05f9e76c,
                    0x3c9e067c, 0xe1f56381, 0x3ff284df, 0x8c3f0d7e, 0xbc9a4c3a,
                    0x7591bb70, 0x3ff291ba, 0x28401cbd, 0xbc82cc72, 0xf51fdee1,
                    0x3ff29e9d, 0xafad1255, 0x3c8612e8, 0x66d10f13, 0x3ff2ab8a,
                    0x191690a7, 0xbc995743, 0xd0dad990, 0x3ff2b87f, 0xd6381aa4,
                    0xbc410adc, 0x39771b2f, 0x3ff2c57e, 0xa6eb5124, 0xbc950145,
                    0xa6e4030b, 0x3ff2d285, 0x54db41d5, 0x3c900247, 0x1f641589,
                    0x3ff2df96, 0xfbbce198, 0x3c9d16cf, 0xa93e2f56, 0x3ff2ecaf,
                    0x45d52383, 0x3c71ca0f, 0x4abd886b, 0x3ff2f9d2, 0x532bda93,
                    0xbc653c55, 0x0a31b715, 0x3ff306fe, 0xd23182e4, 0x3c86f46a,
                    0xedeeb2fd, 0x3ff31432, 0xf3f3fcd1, 0x3c8959a3, 0xfc4cd831,
                    0x3ff32170, 0x8e18047c, 0x3c8a9ce7, 0x3ba8ea32, 0x3ff32eb8,
                    0x3cb4f318, 0xbc9c45e8, 0xb26416ff, 0x3ff33c08, 0x843659a6,
                    0x3c932721, 0x66e3fa2d, 0x3ff34962, 0x930881a4, 0xbc835a75,
                    0x5f929ff1, 0x3ff356c5, 0x5c4e4628, 0xbc8b5cee, 0xa2de883b,
                    0x3ff36431, 0xa06cb85e, 0xbc8c3144, 0x373aa9cb, 0x3ff371a7,
                    0xbf42eae2, 0xbc963aea, 0x231e754a, 0x3ff37f26, 0x9eceb23c,
                    0xbc99f5ca, 0x6d05d866, 0x3ff38cae, 0x3c9904bd, 0xbc9e958d,
                    0x1b7140ef, 0x3ff39a40, 0xfc8e2934, 0xbc99a9a5, 0x34e59ff7,
                    0x3ff3a7db, 0xd661f5e3, 0xbc75e436, 0xbfec6cf4, 0x3ff3b57f,
                    0xe26fff18, 0x3c954c66, 0xc313a8e5, 0x3ff3c32d, 0x375d29c3,
                    0xbc9efff8, 0x44ede173, 0x3ff3d0e5, 0x8c284c71, 0x3c7fe8d0,
                    0x4c123422, 0x3ff3dea6, 0x11f09ebc, 0x3c8ada09, 0xdf1c5175,
                    0x3ff3ec70, 0x7b8c9bca, 0xbc8af663, 0x04ac801c, 0x3ff3fa45,
                    0xf956f9f3, 0xbc97d023, 0xc367a024, 0x3ff40822, 0xb6f4d048,
                    0x3c8bddf8, 0x21f72e2a, 0x3ff4160a, 0x1c309278, 0xbc5ef369,
                    0x2709468a, 0x3ff423fb, 0xc0b314dd, 0xbc98462d, 0xd950a897,
                    0x3ff431f5, 0xe35f7999, 0xbc81c7dd, 0x3f84b9d4, 0x3ff43ffa,
                    0x9704c003, 0x3c8880be, 0x6061892d, 0x3ff44e08, 0x04ef80d0,
                    0x3c489b7a, 0x42a7d232, 0x3ff45c20, 0x82fb1f8e, 0xbc686419,
                    0xed1d0057, 0x3ff46a41, 0xd1648a76, 0x3c9c944b, 0x668b3237,
                    0x3ff4786d, 0xed445733, 0xbc9c20f0, 0xb5c13cd0, 0x3ff486a2,
                    0xb69062f0, 0x3c73c1a3, 0xe192aed2, 0x3ff494e1, 0x5e499ea0,
                    0xbc83b289, 0xf0d7d3de, 0x3ff4a32a, 0xf3d1be56, 0x3c99cb62,
                    0xea6db7d7, 0x3ff4b17d, 0x7f2897f0, 0xbc8125b8, 0xd5362a27,
                    0x3ff4bfda, 0xafec42e2, 0x3c7d4397, 0xb817c114, 0x3ff4ce41,
                    0x690abd5d, 0x3c905e29, 0x99fddd0d, 0x3ff4dcb2, 0xbc6a7833,
                    0x3c98ecdb, 0x81d8abff, 0x3ff4eb2d, 0x2e5d7a52, 0xbc95257d,
                    0x769d2ca7, 0x3ff4f9b2, 0xd25957e3, 0xbc94b309, 0x7f4531ee,
                    0x3ff50841, 0x49b7465f, 0x3c7a249b, 0xa2cf6642, 0x3ff516da,
                    0x69bd93ef, 0xbc8f7685, 0xe83f4eef, 0x3ff5257d, 0x43efef71,
                    0xbc7c998d, 0x569d4f82, 0x3ff5342b, 0x1db13cad, 0xbc807abe,
                    0xf4f6ad27, 0x3ff542e2, 0x192d5f7e, 0x3c87926d, 0xca5d920f,
                    0x3ff551a4, 0xefede59b, 0xbc8d689c, 0xdde910d2, 0x3ff56070,
                    0x168eebf0, 0xbc90fb6e, 0x36b527da, 0x3ff56f47, 0x011d93ad,
                    0x3c99bb2c, 0xdbe2c4cf, 0x3ff57e27, 0x8a57b9c4, 0xbc90b98c,
                    0xd497c7fd, 0x3ff58d12, 0x5b9a1de8, 0x3c8295e1, 0x27ff07cc,
                    0x3ff59c08, 0xe467e60f, 0xbc97e2ce, 0xdd485429, 0x3ff5ab07,
                    0x054647ad, 0x3c96324c, 0xfba87a03, 0x3ff5ba11, 0x4c233e1a,
                    0xbc9b77a1, 0x8a5946b7, 0x3ff5c926, 0x816986a2, 0x3c3c4b1b,
                    0x90998b93, 0x3ff5d845, 0xa8b45643, 0xbc9cd6a7, 0x15ad2148,
                    0x3ff5e76f, 0x3080e65e, 0x3c9ba6f9, 0x20dceb71, 0x3ff5f6a3,
                    0xe3cdcf92, 0xbc89eadd, 0xb976dc09, 0x3ff605e1, 0x9b56de47,
                    0xbc93e242, 0xe6cdf6f4, 0x3ff6152a, 0x4ab84c27, 0x3c9e4b3e,
                    0xb03a5585, 0x3ff6247e, 0x7e40b497, 0xbc9383c1, 0x1d1929fd,
                    0x3ff633dd, 0xbeb964e5, 0x3c984710, 0x34ccc320, 0x3ff64346,
                    0x759d8933, 0xbc8c483c, 0xfebc8fb7, 0x3ff652b9, 0xc9a73e09,
                    0xbc9ae3d5, 0x82552225, 0x3ff66238, 0x87591c34, 0xbc9bb609,
                    0xc70833f6, 0x3ff671c1, 0x586c6134, 0xbc8e8732, 0xd44ca973,
                    0x3ff68155, 0x44f73e65, 0x3c6038ae, 0xb19e9538, 0x3ff690f4,
                    0x9aeb445d, 0x3c8804bd, 0x667f3bcd, 0x3ff6a09e, 0x13b26456,
                    0xbc9bdd34, 0xfa75173e, 0x3ff6b052, 0x2c9a9d0e, 0x3c7a38f5,
                    0x750bdabf, 0x3ff6c012, 0x67ff0b0d, 0xbc728956, 0xddd47645,
                    0x3ff6cfdc, 0xb6f17309, 0x3c9c7aa9, 0x3c651a2f, 0x3ff6dfb2,
                    0x683c88ab, 0xbc6bbe3a, 0x98593ae5, 0x3ff6ef92, 0x9e1ac8b2,
                    0xbc90b974, 0xf9519484, 0x3ff6ff7d, 0x25860ef6, 0xbc883c0f,
                    0x66f42e87, 0x3ff70f74, 0xd45aa65f, 0x3c59d644, 0xe8ec5f74,
                    0x3ff71f75, 0x86887a99, 0xbc816e47, 0x86ead08a, 0x3ff72f82,
                    0x2cd62c72, 0xbc920aa0, 0x48a58174, 0x3ff73f9a, 0x6c65d53c,
                    0xbc90a8d9, 0x35d7cbfd, 0x3ff74fbd, 0x618a6e1c, 0x3c9047fd,
                    0x564267c9, 0x3ff75feb, 0x57316dd3, 0xbc902459, 0xb1ab6e09,
                    0x3ff77024, 0x169147f8, 0x3c9b7877, 0x4fde5d3f, 0x3ff78069,
                    0x0a02162d, 0x3c9866b8, 0x38ac1cf6, 0x3ff790b9, 0x62aadd3e,
                    0x3c9349a8, 0x73eb0187, 0x3ff7a114, 0xee04992f, 0xbc841577,
                    0x0976cfdb, 0x3ff7b17b, 0x8468dc88, 0xbc9bebb5, 0x0130c132,
                    0x3ff7c1ed, 0xd1164dd6, 0x3c9f124c, 0x62ff86f0, 0x3ff7d26a,
                    0xfb72b8b4, 0x3c91bddb, 0x36cf4e62, 0x3ff7e2f3, 0xba15797e,
                    0x3c705d02, 0x8491c491, 0x3ff7f387, 0xcf9311ae, 0xbc807f11,
                    0x543e1a12, 0x3ff80427, 0x626d972b, 0xbc927c86, 0xadd106d9,
                    0x3ff814d2, 0x0d151d4d, 0x3c946437, 0x994cce13, 0x3ff82589,
                    0xd41532d8, 0xbc9d4c1d, 0x1eb941f7, 0x3ff8364c, 0x31df2bd5,
                    0x3c999b9a, 0x4623c7ad, 0x3ff8471a, 0xa341cdfb, 0xbc88d684,
                    0x179f5b21, 0x3ff857f4, 0xf8b216d0, 0xbc5ba748, 0x9b4492ed,
                    0x3ff868d9, 0x9bd4f6ba, 0xbc9fc6f8, 0xd931a436, 0x3ff879ca,
                    0xd2db47bd, 0x3c85d2d7, 0xd98a6699, 0x3ff88ac7, 0xf37cb53a,
                    0x3c9994c2, 0xa478580f, 0x3ff89bd0, 0x4475202a, 0x3c9d5395,
                    0x422aa0db, 0x3ff8ace5, 0x56864b27, 0x3c96e9f1, 0xbad61778,
                    0x3ff8be05, 0xfc43446e, 0x3c9ecb5e, 0x16b5448c, 0x3ff8cf32,
                    0x32e9e3aa, 0xbc70d55e, 0x5e0866d9, 0x3ff8e06a, 0x6fc9b2e6,
                    0xbc97114a, 0x99157736, 0x3ff8f1ae, 0xa2e3976c, 0x3c85cc13,
                    0xd0282c8a, 0x3ff902fe, 0x85fe3fd2, 0x3c9592ca, 0x0b91ffc6,
                    0x3ff9145b, 0x2e582524, 0xbc9dd679, 0x53aa2fe2, 0x3ff925c3,
                    0xa639db7f, 0xbc83455f, 0xb0cdc5e5, 0x3ff93737, 0x81b57ebc,
                    0xbc675fc7, 0x2b5f98e5, 0x3ff948b8, 0x797d2d99, 0xbc8dc3d6,
                    0xcbc8520f, 0x3ff95a44, 0x96a5f039, 0xbc764b7c, 0x9a7670b3,
                    0x3ff96bdd, 0x7f19c896, 0xbc5ba596, 0x9fde4e50, 0x3ff97d82,
                    0x7c1b85d1, 0xbc9d185b, 0xe47a22a2, 0x3ff98f33, 0xa24c78ec,
                    0x3c7cabda, 0x70ca07ba, 0x3ff9a0f1, 0x91cee632, 0xbc9173bd,
                    0x4d53fe0d, 0x3ff9b2bb, 0x4df6d518, 0xbc9dd84e, 0x82a3f090,
                    0x3ff9c491, 0xb071f2be, 0x3c7c7c46, 0x194bb8d5, 0x3ff9d674,
                    0xa3dd8233, 0xbc9516be, 0x19e32323, 0x3ff9e863, 0x78e64c6e,
                    0x3c7824ca, 0x8d07f29e, 0x3ff9fa5e, 0xaaf1face, 0xbc84a9ce,
                    0x7b5de565, 0x3ffa0c66, 0x5d1cd533, 0xbc935949, 0xed8eb8bb,
                    0x3ffa1e7a, 0xee8be70e, 0x3c9c6618, 0xec4a2d33, 0x3ffa309b,
                    0x7ddc36ab, 0x3c96305c, 0x80460ad8, 0x3ffa42c9, 0x589fb120,
                    0xbc9aa780, 0xb23e255d, 0x3ffa5503, 0xdb8d41e1, 0xbc9d2f6e,
                    0x8af46052, 0x3ffa674a, 0x30670366, 0x3c650f56, 0x1330b358,
                    0x3ffa799e, 0xcac563c7, 0x3c9bcb7e, 0x53c12e59, 0x3ffa8bfe,
                    0xb2ba15a9, 0xbc94f867, 0x5579fdbf, 0x3ffa9e6b, 0x0ef7fd31,
                    0x3c90fac9, 0x21356eba, 0x3ffab0e5, 0xdae94545, 0x3c889c31,
                    0xbfd3f37a, 0x3ffac36b, 0xcae76cd0, 0xbc8f9234, 0x3a3c2774,
                    0x3ffad5ff, 0xb6b1b8e5, 0x3c97ef3b, 0x995ad3ad, 0x3ffae89f,
                    0x345dcc81, 0x3c97a1cd, 0xe622f2ff, 0x3ffafb4c, 0x0f315ecd,
                    0xbc94b2fc, 0x298db666, 0x3ffb0e07, 0x4c80e425, 0xbc9bdef5,
                    0x6c9a8952, 0x3ffb20ce, 0x4a0756cc, 0x3c94dd02, 0xb84f15fb,
                    0x3ffb33a2, 0x3084d708, 0xbc62805e, 0x15b749b1, 0x3ffb4684,
                    0xe9df7c90, 0xbc7f763d, 0x8de5593a, 0x3ffb5972, 0xbbba6de3,
                    0xbc9c71df, 0x29f1c52a, 0x3ffb6c6e, 0x52883f6e, 0x3c92a8f3,
                    0xf2fb5e47, 0x3ffb7f76, 0x7e54ac3b, 0xbc75584f, 0xf22749e4,
                    0x3ffb928c, 0x54cb65c6, 0xbc9b7216, 0x30a1064a, 0x3ffba5b0,
                    0x0e54292e, 0xbc9efcd3, 0xb79a6f1f, 0x3ffbb8e0, 0xc9696205,
                    0xbc3f52d1, 0x904bc1d2, 0x3ffbcc1e, 0x7a2d9e84, 0x3c823dd0,
                    0xc3f3a207, 0x3ffbdf69, 0x60ea5b53, 0xbc3c2623, 0x5bd71e09,
                    0x3ffbf2c2, 0x3f6b9c73, 0xbc9efdca, 0x6141b33d, 0x3ffc0628,
                    0xa1fbca34, 0xbc8d8a5a, 0xdd85529c, 0x3ffc199b, 0x895048dd,
                    0x3c811065, 0xd9fa652c, 0x3ffc2d1c, 0x17c8a5d7, 0xbc96e516,
                    0x5fffd07a, 0x3ffc40ab, 0xe083c60a, 0x3c9b4537, 0x78fafb22,
                    0x3ffc5447, 0x2493b5af, 0x3c912f07, 0x2e57d14b, 0x3ffc67f1,
                    0xff483cad, 0x3c92884d, 0x8988c933, 0x3ffc7ba8, 0xbe255559,
                    0xbc8e76bb, 0x9406e7b5, 0x3ffc8f6d, 0x48805c44, 0x3c71acbc,
                    0x5751c4db, 0x3ffca340, 0xd10d08f5, 0xbc87f2be, 0xdcef9069,
                    0x3ffcb720, 0xd1e949db, 0x3c7503cb, 0x2e6d1675, 0x3ffccb0f,
                    0x86009092, 0xbc7d220f, 0x555dc3fa, 0x3ffcdf0b, 0x53829d72,
                    0xbc8dd83b, 0x5b5bab74, 0x3ffcf315, 0xb86dff57, 0xbc9a08e9,
                    0x4a07897c, 0x3ffd072d, 0x43797a9c, 0xbc9cbc37, 0x2b08c968,
                    0x3ffd1b53, 0x219a36ee, 0x3c955636, 0x080d89f2, 0x3ffd2f87,
                    0x719d8578, 0xbc9d487b, 0xeacaa1d6, 0x3ffd43c8, 0xbf5a1614,
                    0x3c93db53, 0xdcfba487, 0x3ffd5818, 0xd75b3707, 0x3c82ed02,
                    0xe862e6d3, 0x3ffd6c76, 0x4a8165a0, 0x3c5fe87a, 0x16c98398,
                    0x3ffd80e3, 0x8beddfe8, 0xbc911ec1, 0x71ff6075, 0x3ffd955d,
                    0xbb9af6be, 0x3c9a052d, 0x03db3285, 0x3ffda9e6, 0x696db532,
                    0x3c9c2300, 0xd63a8315, 0x3ffdbe7c, 0x926b8be4, 0xbc9b76f1,
                    0xf301b460, 0x3ffdd321, 0x78f018c3, 0x3c92da57, 0x641c0658,
                    0x3ffde7d5, 0x8e79ba8f, 0xbc9ca552, 0x337b9b5f, 0x3ffdfc97,
                    0x4f184b5c, 0xbc91a5cd, 0x6b197d17, 0x3ffe1167, 0xbd5c7f44,
                    0xbc72b529, 0x14f5a129, 0x3ffe2646, 0x817a1496, 0xbc97b627,
                    0x3b16ee12, 0x3ffe3b33, 0x31fdc68b, 0xbc99f4a4, 0xe78b3ff6,
                    0x3ffe502e, 0x80a9cc8f, 0x3c839e89, 0x24676d76, 0x3ffe6539,
                    0x7522b735, 0xbc863ff8, 0xfbc74c83, 0x3ffe7a51, 0xca0c8de2,
                    0x3c92d522, 0x77cdb740, 0x3ffe8f79, 0x80b054b1, 0xbc910894,
                    0xa2a490da, 0x3ffea4af, 0x179c2893, 0xbc9e9c23, 0x867cca6e,
                    0x3ffeb9f4, 0x2293e4f2, 0x3c94832f, 0x2d8e67f1, 0x3ffecf48,
                    0xb411ad8c, 0xbc9c93f3, 0xa2188510, 0x3ffee4aa, 0xa487568d,
                    0x3c91c68d, 0xee615a27, 0x3ffefa1b, 0x86a4b6b0, 0x3c9dc7f4,
                    0x1cb6412a, 0x3fff0f9c, 0x65181d45, 0xbc932200, 0x376bba97,
                    0x3fff252b, 0xbf0d8e43, 0x3c93a1a5, 0x48dd7274, 0x3fff3ac9,
                    0x3ed837de, 0xbc795a5a, 0x5b6e4540, 0x3fff5076, 0x2dd8a18b,
                    0x3c99d3e1, 0x798844f8, 0x3fff6632, 0x3539343e, 0x3c9fa37b,
                    0xad9cbe14, 0x3fff7bfd, 0xd006350a, 0xbc9dbb12, 0x02243c89,
                    0x3fff91d8, 0xa779f689, 0xbc612ea8, 0x819e90d8, 0x3fffa7c1,
                    0xf3a5931e, 0x3c874853, 0x3692d514, 0x3fffbdba, 0x15098eb6,
                    0xbc796773, 0x2b8f71f1, 0x3fffd3c2, 0x966579e7, 0x3c62eb74,
                    0x6b2a23d9, 0x3fffe9d9, 0x7442fde3, 0x3c74a603
    };

    private static int[] eCoeff = {
                    0xe78a6731, 0x3f55d87f, 0xd704a0c0, 0x3fac6b08, 0x6fba4e77,
                    0x3f83b2ab, 0xff82c58f, 0x3fcebfbd, 0xfefa39ef, 0x3fe62e42,
                    0x00000000, 0x00000000
    };

    private static int[] coeffH = {
                    0x00000000, 0xbfd61a00, 0x00000000, 0xbf5dabe1
    };

    private static int[] highmaskLogX = {
                    0xf8000000, 0xffffffff, 0x00000000, 0xfffff800
    };

    private static int[] halfmask = {
                    0xf8000000, 0xffffffff, 0xf8000000, 0xffffffff
    };

    private static int[] coeffPow = {
                    0x6dc96112, 0xbf836578, 0xee241472, 0xbf9b0301, 0x9f95985a,
                    0xbfb528db, 0xb3841d2a, 0xbfd619b6, 0x518775e3, 0x3f9004f2,
                    0xac8349bb, 0x3fa76c9b, 0x486ececc, 0x3fc4635e, 0x161bb241,
                    0xbf5dabe1, 0x9f95985a, 0xbfb528db, 0xf8b5787d, 0x3ef2531e,
                    0x486ececb, 0x3fc4635e, 0x412055cc, 0xbdd61bb2
    };

    private static int[] lTblPow = {
                    0x00000000, 0x3ff00000, 0x00000000, 0x00000000, 0x20000000,
                    0x3feff00a, 0x96621f95, 0x3e5b1856, 0xe0000000, 0x3fefe019,
                    0xe5916f9e, 0xbe325278, 0x00000000, 0x3fefd02f, 0x859a1062,
                    0x3e595fb7, 0xc0000000, 0x3fefc049, 0xb245f18f, 0xbe529c38,
                    0xe0000000, 0x3fefb069, 0xad2880a7, 0xbe501230, 0x60000000,
                    0x3fefa08f, 0xc8e72420, 0x3e597bd1, 0x80000000, 0x3fef90ba,
                    0xc30c4500, 0xbe5d6c75, 0xe0000000, 0x3fef80ea, 0x02c63f43,
                    0x3e2e1318, 0xc0000000, 0x3fef7120, 0xb3d4cccc, 0xbe44c52a,
                    0x00000000, 0x3fef615c, 0xdbd91397, 0xbe4e7d6c, 0xa0000000,
                    0x3fef519c, 0x65c5cd68, 0xbe522dc8, 0xa0000000, 0x3fef41e2,
                    0x46d1306c, 0xbe5a840e, 0xe0000000, 0x3fef322d, 0xd2980e94,
                    0x3e5071af, 0xa0000000, 0x3fef227e, 0x773abade, 0xbe5891e5,
                    0xa0000000, 0x3fef12d4, 0xdc6bf46b, 0xbe5cccbe, 0xe0000000,
                    0x3fef032f, 0xbc7247fa, 0xbe2bab83, 0x80000000, 0x3feef390,
                    0xbcaa1e46, 0xbe53bb3b, 0x60000000, 0x3feee3f6, 0x5f6c682d,
                    0xbe54c619, 0x80000000, 0x3feed461, 0x5141e368, 0xbe4b6d86,
                    0xe0000000, 0x3feec4d1, 0xec678f76, 0xbe369af6, 0x80000000,
                    0x3feeb547, 0x41301f55, 0xbe2d4312, 0x60000000, 0x3feea5c2,
                    0x676da6bd, 0xbe4d8dd0, 0x60000000, 0x3fee9642, 0x57a891c4,
                    0x3e51f991, 0xa0000000, 0x3fee86c7, 0xe4eb491e, 0x3e579bf9,
                    0x20000000, 0x3fee7752, 0xfddc4a2c, 0xbe3356e6, 0xc0000000,
                    0x3fee67e1, 0xd75b5bf1, 0xbe449531, 0x80000000, 0x3fee5876,
                    0xbd423b8e, 0x3df54fe4, 0x60000000, 0x3fee4910, 0x330e51b9,
                    0x3e54289c, 0x80000000, 0x3fee39af, 0x8651a95f, 0xbe55aad6,
                    0xa0000000, 0x3fee2a53, 0x5e98c708, 0xbe2fc4a9, 0xe0000000,
                    0x3fee1afc, 0x0989328d, 0x3e23958c, 0x40000000, 0x3fee0bab,
                    0xee642abd, 0xbe425dd8, 0xa0000000, 0x3fedfc5e, 0xc394d236,
                    0x3e526362, 0x20000000, 0x3feded17, 0xe104aa8e, 0x3e4ce247,
                    0xc0000000, 0x3fedddd4, 0x265a9be4, 0xbe5bb77a, 0x40000000,
                    0x3fedce97, 0x0ecac52f, 0x3e4a7cb1, 0xe0000000, 0x3fedbf5e,
                    0x124cb3b8, 0x3e257024, 0x80000000, 0x3fedb02b, 0xe6d4febe,
                    0xbe2033ee, 0x20000000, 0x3feda0fd, 0x39cca00e, 0xbe3ddabc,
                    0xc0000000, 0x3fed91d3, 0xef8a552a, 0xbe543390, 0x40000000,
                    0x3fed82af, 0xb8e85204, 0x3e513850, 0xe0000000, 0x3fed738f,
                    0x3d59fe08, 0xbe5db728, 0x40000000, 0x3fed6475, 0x3aa7ead1,
                    0x3e58804b, 0xc0000000, 0x3fed555f, 0xf8a35ba9, 0xbe5298b0,
                    0x00000000, 0x3fed464f, 0x9a88dd15, 0x3e5a8cdb, 0x40000000,
                    0x3fed3743, 0xb0b0a190, 0x3e598635, 0x80000000, 0x3fed283c,
                    0xe2113295, 0xbe5c1119, 0x80000000, 0x3fed193a, 0xafbf1728,
                    0xbe492e9c, 0x60000000, 0x3fed0a3d, 0xe4a4ccf3, 0x3e19b90e,
                    0x20000000, 0x3fecfb45, 0xba3cbeb8, 0x3e406b50, 0xc0000000,
                    0x3fecec51, 0x110f7ddd, 0x3e0d6806, 0x40000000, 0x3fecdd63,
                    0x7dd7d508, 0xbe5a8943, 0x80000000, 0x3fecce79, 0x9b60f271,
                    0xbe50676a, 0x80000000, 0x3fecbf94, 0x0b9ad660, 0x3e59174f,
                    0x60000000, 0x3fecb0b4, 0x00823d9c, 0x3e5bbf72, 0x20000000,
                    0x3feca1d9, 0x38a6ec89, 0xbe4d38f9, 0x80000000, 0x3fec9302,
                    0x3a0b7d8e, 0x3e53dbfd, 0xc0000000, 0x3fec8430, 0xc6826b34,
                    0xbe27c5c9, 0xc0000000, 0x3fec7563, 0x0c706381, 0xbe593653,
                    0x60000000, 0x3fec669b, 0x7df34ec7, 0x3e461ab5, 0xe0000000,
                    0x3fec57d7, 0x40e5e7e8, 0xbe5c3dae, 0x00000000, 0x3fec4919,
                    0x5602770f, 0xbe55219d, 0xc0000000, 0x3fec3a5e, 0xec7911eb,
                    0x3e5a5d25, 0x60000000, 0x3fec2ba9, 0xb39ea225, 0xbe53c00b,
                    0x80000000, 0x3fec1cf8, 0x967a212e, 0x3e5a8ddf, 0x60000000,
                    0x3fec0e4c, 0x580798bd, 0x3e5f53ab, 0x00000000, 0x3febffa5,
                    0xb8282df6, 0xbe46b874, 0x20000000, 0x3febf102, 0xe33a6729,
                    0x3e54963f, 0x00000000, 0x3febe264, 0x3b53e88a, 0xbe3adce1,
                    0x60000000, 0x3febd3ca, 0xc2585084, 0x3e5cde9f, 0x80000000,
                    0x3febc535, 0xa335c5ee, 0xbe39fd9c, 0x20000000, 0x3febb6a5,
                    0x7325b04d, 0x3e42ba15, 0x60000000, 0x3feba819, 0x1564540f,
                    0x3e3a9f35, 0x40000000, 0x3feb9992, 0x83fff592, 0xbe5465ce,
                    0xa0000000, 0x3feb8b0f, 0xb9da63d3, 0xbe4b1a0a, 0x80000000,
                    0x3feb7c91, 0x6d6f1ea4, 0x3e557657, 0x00000000, 0x3feb6e18,
                    0x5e80a1bf, 0x3e4ddbb6, 0x00000000, 0x3feb5fa3, 0x1c9eacb5,
                    0x3e592877, 0xa0000000, 0x3feb5132, 0x6d40beb3, 0xbe51858c,
                    0xa0000000, 0x3feb42c6, 0xd740c67b, 0x3e427ad2, 0x40000000,
                    0x3feb345f, 0xa3e0ccee, 0xbe5c2fc4, 0x40000000, 0x3feb25fc,
                    0x8e752b50, 0xbe3da3c2, 0xc0000000, 0x3feb179d, 0xa892e7de,
                    0x3e1fb481, 0xc0000000, 0x3feb0943, 0x21ed71e9, 0xbe365206,
                    0x20000000, 0x3feafaee, 0x0e1380a3, 0x3e5c5b7b, 0x20000000,
                    0x3feaec9d, 0x3c3d640e, 0xbe5dbbd0, 0x60000000, 0x3feade50,
                    0x8f97a715, 0x3e3a8ec5, 0x20000000, 0x3fead008, 0x23ab2839,
                    0x3e2fe98a, 0x40000000, 0x3feac1c4, 0xf4bbd50f, 0x3e54d8f6,
                    0xe0000000, 0x3feab384, 0x14757c4d, 0xbe48774c, 0xc0000000,
                    0x3feaa549, 0x7c7b0eea, 0x3e5b51bb, 0x20000000, 0x3fea9713,
                    0xf56f7013, 0x3e386200, 0xe0000000, 0x3fea88e0, 0xbe428ebe,
                    0xbe514af5, 0xe0000000, 0x3fea7ab2, 0x8d0e4496, 0x3e4f9165,
                    0x60000000, 0x3fea6c89, 0xdbacc5d5, 0xbe5c063b, 0x20000000,
                    0x3fea5e64, 0x3f19d970, 0xbe5a0c8c, 0x20000000, 0x3fea5043,
                    0x09ea3e6b, 0x3e5065dc, 0x80000000, 0x3fea4226, 0x78df246c,
                    0x3e5e05f6, 0x40000000, 0x3fea340e, 0x4057d4a0, 0x3e431b2b,
                    0x40000000, 0x3fea25fa, 0x82867bb5, 0x3e4b76be, 0xa0000000,
                    0x3fea17ea, 0x9436f40a, 0xbe5aad39, 0x20000000, 0x3fea09df,
                    0x4b5253b3, 0x3e46380b, 0x00000000, 0x3fe9fbd8, 0x8fc52466,
                    0xbe386f9b, 0x20000000, 0x3fe9edd5, 0x22d3f344, 0xbe538347,
                    0x60000000, 0x3fe9dfd6, 0x1ac33522, 0x3e5dbc53, 0x00000000,
                    0x3fe9d1dc, 0xeabdff1d, 0x3e40fc0c, 0xe0000000, 0x3fe9c3e5,
                    0xafd30e73, 0xbe585e63, 0xe0000000, 0x3fe9b5f3, 0xa52f226a,
                    0xbe43e8f9, 0x20000000, 0x3fe9a806, 0xecb8698d, 0xbe515b36,
                    0x80000000, 0x3fe99a1c, 0xf2b4e89d, 0x3e48b62b, 0x20000000,
                    0x3fe98c37, 0x7c9a88fb, 0x3e44414c, 0x00000000, 0x3fe97e56,
                    0xda015741, 0xbe5d13ba, 0xe0000000, 0x3fe97078, 0x5fdace06,
                    0x3e51b947, 0x00000000, 0x3fe962a0, 0x956ca094, 0x3e518785,
                    0x40000000, 0x3fe954cb, 0x01164c1d, 0x3e5d5b57, 0xc0000000,
                    0x3fe946fa, 0xe63b3767, 0xbe4f84e7, 0x40000000, 0x3fe9392e,
                    0xe57cc2a9, 0x3e34eda3, 0xe0000000, 0x3fe92b65, 0x8c75b544,
                    0x3e5766a0, 0xc0000000, 0x3fe91da1, 0x37d1d087, 0xbe5e2ab1,
                    0x80000000, 0x3fe90fe1, 0xa953dc20, 0x3e5fa1f3, 0x80000000,
                    0x3fe90225, 0xdbd3f369, 0x3e47d6db, 0xa0000000, 0x3fe8f46d,
                    0x1c9be989, 0xbe5e2b0a, 0xa0000000, 0x3fe8e6b9, 0x3c93d76a,
                    0x3e5c8618, 0xe0000000, 0x3fe8d909, 0x2182fc9a, 0xbe41aa9e,
                    0x20000000, 0x3fe8cb5e, 0xe6b3539d, 0xbe530d19, 0x60000000,
                    0x3fe8bdb6, 0x49e58cc3, 0xbe3bb374, 0xa0000000, 0x3fe8b012,
                    0xa7cfeb8f, 0x3e56c412, 0x00000000, 0x3fe8a273, 0x8d52bc19,
                    0x3e1429b8, 0x60000000, 0x3fe894d7, 0x4dc32c6c, 0xbe48604c,
                    0xc0000000, 0x3fe8873f, 0x0c868e56, 0xbe564ee5, 0x00000000,
                    0x3fe879ac, 0x56aee828, 0x3e5e2fd8, 0x60000000, 0x3fe86c1c,
                    0x7ceab8ec, 0x3e493365, 0xc0000000, 0x3fe85e90, 0x78d4dadc,
                    0xbe4f7f25, 0x00000000, 0x3fe85109, 0x0ccd8280, 0x3e31e7a2,
                    0x40000000, 0x3fe84385, 0x34ba4e15, 0x3e328077, 0x80000000,
                    0x3fe83605, 0xa670975a, 0xbe53eee5, 0xa0000000, 0x3fe82889,
                    0xf61b77b2, 0xbe43a20a, 0xa0000000, 0x3fe81b11, 0x13e6643b,
                    0x3e5e5fe5, 0xc0000000, 0x3fe80d9d, 0x82cc94e8, 0xbe5ff1f9,
                    0xa0000000, 0x3fe8002d, 0x8a0c9c5d, 0xbe42b0e7, 0x60000000,
                    0x3fe7f2c1, 0x22a16f01, 0x3e5d9ea0, 0x20000000, 0x3fe7e559,
                    0xc38cd451, 0x3e506963, 0xc0000000, 0x3fe7d7f4, 0x9902bc71,
                    0x3e4503d7, 0x40000000, 0x3fe7ca94, 0xdef2a3c0, 0x3e3d98ed,
                    0xa0000000, 0x3fe7bd37, 0xed49abb0, 0x3e24c1ff, 0xe0000000,
                    0x3fe7afde, 0xe3b0be70, 0xbe40c467, 0x00000000, 0x3fe7a28a,
                    0xaf9f193c, 0xbe5dff6c, 0xe0000000, 0x3fe79538, 0xb74cf6b6,
                    0xbe258ed0, 0xa0000000, 0x3fe787eb, 0x1d9127c7, 0x3e345fb0,
                    0x40000000, 0x3fe77aa2, 0x1028c21d, 0xbe4619bd, 0xa0000000,
                    0x3fe76d5c, 0x7cb0b5e4, 0x3e40f1a2, 0xe0000000, 0x3fe7601a,
                    0x2b1bc4ad, 0xbe32e8bb, 0xe0000000, 0x3fe752dc, 0x6839f64e,
                    0x3e41f57b, 0xc0000000, 0x3fe745a2, 0xc4121f7e, 0xbe52c40a,
                    0x60000000, 0x3fe7386c, 0xd6852d72, 0xbe5c4e6b, 0xc0000000,
                    0x3fe72b39, 0x91d690f7, 0xbe57f88f, 0xe0000000, 0x3fe71e0a,
                    0x627a2159, 0xbe4425d5, 0xc0000000, 0x3fe710df, 0x50a54033,
                    0x3e422b7e, 0x60000000, 0x3fe703b8, 0x3b0b5f91, 0x3e5d3857,
                    0xe0000000, 0x3fe6f694, 0x84d628a2, 0xbe51f090, 0x00000000,
                    0x3fe6e975, 0x306d8894, 0xbe414d83, 0xe0000000, 0x3fe6dc58,
                    0x30bf24aa, 0xbe4650ca, 0x80000000, 0x3fe6cf40, 0xd4628d69,
                    0xbe5db007, 0xc0000000, 0x3fe6c22b, 0xa2aae57b, 0xbe31d279,
                    0xc0000000, 0x3fe6b51a, 0x860edf7e, 0xbe2d4c4a, 0x80000000,
                    0x3fe6a80d, 0xf3559341, 0xbe5f7e98, 0xe0000000, 0x3fe69b03,
                    0xa885899e, 0xbe5c2011, 0xe0000000, 0x3fe68dfd, 0x2bdc6d37,
                    0x3e224a82, 0xa0000000, 0x3fe680fb, 0xc12ad1b9, 0xbe40cf56,
                    0x00000000, 0x3fe673fd, 0x1bcdf659, 0xbdf52f2d, 0x00000000,
                    0x3fe66702, 0x5df10408, 0x3e5663e0, 0xc0000000, 0x3fe65a0a,
                    0xa4070568, 0xbe40b12f, 0x00000000, 0x3fe64d17, 0x71c54c47,
                    0x3e5f5e8b, 0x00000000, 0x3fe64027, 0xbd4b7e83, 0x3e42ead6,
                    0xa0000000, 0x3fe6333a, 0x61598bd2, 0xbe4c48d4, 0xc0000000,
                    0x3fe62651, 0x6f538d61, 0x3e548401, 0xa0000000, 0x3fe6196c,
                    0x14344120, 0xbe529af6, 0x00000000, 0x3fe60c8b, 0x5982c587,
                    0xbe3e1e4f, 0x00000000, 0x3fe5ffad, 0xfe51d4ea, 0xbe4c897a,
                    0x80000000, 0x3fe5f2d2, 0xfd46ebe1, 0x3e552e00, 0xa0000000,
                    0x3fe5e5fb, 0xa4695699, 0x3e5ed471, 0x60000000, 0x3fe5d928,
                    0x80d118ae, 0x3e456b61, 0xa0000000, 0x3fe5cc58, 0x304c330b,
                    0x3e54dc29, 0x80000000, 0x3fe5bf8c, 0x0af2dedf, 0xbe3aa9bd,
                    0xe0000000, 0x3fe5b2c3, 0x15fc9258, 0xbe479a37, 0xc0000000,
                    0x3fe5a5fe, 0x9292c7ea, 0x3e188650, 0x20000000, 0x3fe5993d,
                    0x33b4d380, 0x3e5d6d93, 0x20000000, 0x3fe58c7f, 0x02fd16c7,
                    0x3e2fe961, 0xa0000000, 0x3fe57fc4, 0x4a05edb6, 0xbe4d55b4,
                    0xa0000000, 0x3fe5730d, 0x3d443abb, 0xbe5e6954, 0x00000000,
                    0x3fe5665a, 0x024acfea, 0x3e50e61b, 0x00000000, 0x3fe559aa,
                    0xcc9edd09, 0xbe325403, 0x60000000, 0x3fe54cfd, 0x1fe26950,
                    0x3e5d500e, 0x60000000, 0x3fe54054, 0x6c5ae164, 0xbe4a79b4,
                    0xc0000000, 0x3fe533ae, 0x154b0287, 0xbe401571, 0xa0000000,
                    0x3fe5270c, 0x0673f401, 0xbe56e56b, 0xe0000000, 0x3fe51a6d,
                    0x751b639c, 0x3e235269, 0xa0000000, 0x3fe50dd2, 0x7c7b2bed,
                    0x3ddec887, 0xc0000000, 0x3fe5013a, 0xafab4e17, 0x3e5e7575,
                    0x60000000, 0x3fe4f4a6, 0x2e308668, 0x3e59aed6, 0x80000000,
                    0x3fe4e815, 0xf33e2a76, 0xbe51f184, 0xe0000000, 0x3fe4db87,
                    0x839f3e3e, 0x3e57db01, 0xc0000000, 0x3fe4cefd, 0xa9eda7bb,
                    0x3e535e0f, 0x00000000, 0x3fe4c277, 0x2a8f66a5, 0x3e5ce451,
                    0xc0000000, 0x3fe4b5f3, 0x05192456, 0xbe4e8518, 0xc0000000,
                    0x3fe4a973, 0x4aa7cd1d, 0x3e46784a, 0x40000000, 0x3fe49cf7,
                    0x8e23025e, 0xbe5749f2, 0x00000000, 0x3fe4907e, 0x18d30215,
                    0x3e360f39, 0x20000000, 0x3fe48408, 0x63dcf2f3, 0x3e5e00fe,
                    0xc0000000, 0x3fe47795, 0x46182d09, 0xbe5173d9, 0xa0000000,
                    0x3fe46b26, 0x8f0e62aa, 0xbe48f281, 0xe0000000, 0x3fe45eba,
                    0x5775c40c, 0xbe56aad4, 0x60000000, 0x3fe45252, 0x0fe25f69,
                    0x3e48bd71, 0x40000000, 0x3fe445ed, 0xe9989ec5, 0x3e590d97,
                    0x80000000, 0x3fe4398b, 0xb3d9ffe3, 0x3e479dbc, 0x20000000,
                    0x3fe42d2d, 0x388e4d2e, 0xbe5eed80, 0xe0000000, 0x3fe420d1,
                    0x6f797c18, 0x3e554b4c, 0x20000000, 0x3fe4147a, 0x31048bb4,
                    0xbe5b1112, 0x80000000, 0x3fe40825, 0x2efba4f9, 0x3e48ebc7,
                    0x40000000, 0x3fe3fbd4, 0x50201119, 0x3e40b701, 0x40000000,
                    0x3fe3ef86, 0x0a4db32c, 0x3e551de8, 0xa0000000, 0x3fe3e33b,
                    0x0c9c148b, 0xbe50c1f6, 0x20000000, 0x3fe3d6f4, 0xc9129447,
                    0x3e533fa0, 0x00000000, 0x3fe3cab0, 0xaae5b5a0, 0xbe22b68e,
                    0x20000000, 0x3fe3be6f, 0x02305e8a, 0xbe54fc08, 0x60000000,
                    0x3fe3b231, 0x7f908258, 0x3e57dc05, 0x00000000, 0x3fe3a5f7,
                    0x1a09af78, 0x3e08038b, 0xe0000000, 0x3fe399bf, 0x490643c1,
                    0xbe5dbe42, 0xe0000000, 0x3fe38d8b, 0x5e8ad724, 0xbe3c2b72,
                    0x20000000, 0x3fe3815b, 0xc67196b6, 0x3e1713cf, 0xa0000000,
                    0x3fe3752d, 0x6182e429, 0xbe3ec14c, 0x40000000, 0x3fe36903,
                    0xab6eb1ae, 0x3e5a2cc5, 0x40000000, 0x3fe35cdc, 0xfe5dc064,
                    0xbe5c5878, 0x40000000, 0x3fe350b8, 0x0ba6b9e4, 0x3e51619b,
                    0x80000000, 0x3fe34497, 0x857761aa, 0x3e5fff53, 0x00000000,
                    0x3fe3387a, 0xf872d68c, 0x3e484f4d, 0xa0000000, 0x3fe32c5f,
                    0x087e97c2, 0x3e52842e, 0x80000000, 0x3fe32048, 0x73d6d0c0,
                    0xbe503edf, 0x80000000, 0x3fe31434, 0x0c1456a1, 0xbe5f72ad,
                    0xa0000000, 0x3fe30823, 0x83a1a4d5, 0xbe5e65cc, 0xe0000000,
                    0x3fe2fc15, 0x855a7390, 0xbe506438, 0x40000000, 0x3fe2f00b,
                    0xa2898287, 0x3e3d22a2, 0xe0000000, 0x3fe2e403, 0x8b56f66f,
                    0xbe5aa5fd, 0x80000000, 0x3fe2d7ff, 0x52db119a, 0x3e3a2e3d,
                    0x60000000, 0x3fe2cbfe, 0xe2ddd4c0, 0xbe586469, 0x40000000,
                    0x3fe2c000, 0x6b01bf10, 0x3e352b9d, 0x40000000, 0x3fe2b405,
                    0xb07a1cdf, 0x3e5c5cda, 0x80000000, 0x3fe2a80d, 0xc7b5f868,
                    0xbe5668b3, 0xc0000000, 0x3fe29c18, 0x185edf62, 0xbe563d66,
                    0x00000000, 0x3fe29027, 0xf729e1cc, 0x3e59a9a0, 0x80000000,
                    0x3fe28438, 0x6433c727, 0xbe43cc89, 0x00000000, 0x3fe2784d,
                    0x41782631, 0xbe30750c, 0xa0000000, 0x3fe26c64, 0x914911b7,
                    0xbe58290e, 0x40000000, 0x3fe2607f, 0x3dcc73e1, 0xbe4269cd,
                    0x00000000, 0x3fe2549d, 0x2751bf70, 0xbe5a6998, 0xc0000000,
                    0x3fe248bd, 0x4248b9fb, 0xbe4ddb00, 0x80000000, 0x3fe23ce1,
                    0xf35cf82f, 0x3e561b71, 0x60000000, 0x3fe23108, 0x8e481a2d,
                    0x3e518fb9, 0x60000000, 0x3fe22532, 0x5ab96edc, 0xbe5fafc5,
                    0x40000000, 0x3fe2195f, 0x80943911, 0xbe07f819, 0x40000000,
                    0x3fe20d8f, 0x386f2d6c, 0xbe54ba8b, 0x40000000, 0x3fe201c2,
                    0xf29664ac, 0xbe5eb815, 0x20000000, 0x3fe1f5f8, 0x64f03390,
                    0x3e5e320c, 0x20000000, 0x3fe1ea31, 0x747ff696, 0x3e5ef0a5,
                    0x40000000, 0x3fe1de6d, 0x3e9ceb51, 0xbe5f8d27, 0x20000000,
                    0x3fe1d2ac, 0x4ae0b55e, 0x3e5faa21, 0x20000000, 0x3fe1c6ee,
                    0x28569a5e, 0x3e598a4f, 0x20000000, 0x3fe1bb33, 0x54b33e07,
                    0x3e46130a, 0x20000000, 0x3fe1af7b, 0x024f1078, 0xbe4dbf93,
                    0x00000000, 0x3fe1a3c6, 0xb0783bfa, 0x3e419248, 0xe0000000,
                    0x3fe19813, 0x2f02b836, 0x3e4e02b7, 0xc0000000, 0x3fe18c64,
                    0x28dec9d4, 0x3e09064f, 0x80000000, 0x3fe180b8, 0x45cbf406,
                    0x3e5b1f46, 0x40000000, 0x3fe1750f, 0x03d9964c, 0x3e5b0a79,
                    0x00000000, 0x3fe16969, 0x8b5b882b, 0xbe238086, 0xa0000000,
                    0x3fe15dc5, 0x73bad6f8, 0xbdf1fca4, 0x20000000, 0x3fe15225,
                    0x5385769c, 0x3e5e8d76, 0xa0000000, 0x3fe14687, 0x1676dc6b,
                    0x3e571d08, 0x20000000, 0x3fe13aed, 0xa8c41c7f, 0xbe598a25,
                    0x60000000, 0x3fe12f55, 0xc4e1aaf0, 0x3e435277, 0xa0000000,
                    0x3fe123c0, 0x403638e1, 0xbe21aa7c, 0xc0000000, 0x3fe1182e,
                    0x557a092b, 0xbdd0116b, 0xc0000000, 0x3fe10c9f, 0x7d779f66,
                    0x3e4a61ba, 0xc0000000, 0x3fe10113, 0x2b09c645, 0xbe5d586e,
                    0x20000000, 0x3fe0ea04, 0xea2cad46, 0x3e5aa97c, 0x20000000,
                    0x3fe0d300, 0x23190e54, 0x3e50f1a7, 0xa0000000, 0x3fe0bc07,
                    0x1379a5a6, 0xbe51619d, 0x60000000, 0x3fe0a51a, 0x926a3d4a,
                    0x3e5cf019, 0xa0000000, 0x3fe08e38, 0xa8c24358, 0x3e35241e,
                    0x20000000, 0x3fe07762, 0x24317e7a, 0x3e512cfa, 0x00000000,
                    0x3fe06097, 0xfd9cf274, 0xbe55bef3, 0x00000000, 0x3fe049d7,
                    0x3689b49d, 0xbe36d26d, 0x40000000, 0x3fe03322, 0xf72ef6c4,
                    0xbe54cd08, 0xa0000000, 0x3fe01c78, 0x23702d2d, 0xbe5900bf,
                    0x00000000, 0x3fe005da, 0x3f59c14c, 0x3e57d80b, 0x40000000,
                    0x3fdfde8d, 0xad67766d, 0xbe57fad4, 0x40000000, 0x3fdfb17c,
                    0x644f4ae7, 0x3e1ee43b, 0x40000000, 0x3fdf8481, 0x903234d2,
                    0x3e501a86, 0x40000000, 0x3fdf579c, 0xafe9e509, 0xbe267c3e,
                    0x00000000, 0x3fdf2acd, 0xb7dfda0b, 0xbe48149b, 0x40000000,
                    0x3fdefe13, 0x3b94305e, 0x3e5f4ea7, 0x80000000, 0x3fded16f,
                    0x5d95da61, 0xbe55c198, 0x00000000, 0x3fdea4e1, 0x406960c9,
                    0xbdd99a19, 0x00000000, 0x3fde7868, 0xd22f3539, 0x3e470c78,
                    0x80000000, 0x3fde4c04, 0x83eec535, 0xbe3e1232, 0x40000000,
                    0x3fde1fb6, 0x3dfbffcb, 0xbe4b7d71, 0x40000000, 0x3fddf37d,
                    0x7e1be4e0, 0xbe5b8f8f, 0x40000000, 0x3fddc759, 0x46dae887,
                    0xbe350458, 0x80000000, 0x3fdd9b4a, 0xed6ecc49, 0xbe5f0045,
                    0x80000000, 0x3fdd6f50, 0x2e9e883c, 0x3e2915da, 0x80000000,
                    0x3fdd436b, 0xf0bccb32, 0x3e4a68c9, 0x80000000, 0x3fdd179b,
                    0x9bbfc779, 0xbe54a26a, 0x00000000, 0x3fdcebe0, 0x7cea33ab,
                    0x3e43c6b7, 0x40000000, 0x3fdcc039, 0xe740fd06, 0x3e5526c2,
                    0x40000000, 0x3fdc94a7, 0x9eadeb1a, 0xbe396d8d, 0xc0000000,
                    0x3fdc6929, 0xf0a8f95a, 0xbe5c0ab2, 0x80000000, 0x3fdc3dc0,
                    0x6ee2693b, 0x3e0992e6, 0xc0000000, 0x3fdc126b, 0x5ac6b581,
                    0xbe2834b6, 0x40000000, 0x3fdbe72b, 0x8cc226ff, 0x3e3596a6,
                    0x00000000, 0x3fdbbbff, 0xf92a74bb, 0x3e3c5813, 0x00000000,
                    0x3fdb90e7, 0x479664c0, 0xbe50d644, 0x00000000, 0x3fdb65e3,
                    0x5004975b, 0xbe55258f, 0x00000000, 0x3fdb3af3, 0xe4b23194,
                    0xbe588407, 0xc0000000, 0x3fdb1016, 0xe65d4d0a, 0x3e527c26,
                    0x80000000, 0x3fdae54e, 0x814fddd6, 0x3e5962a2, 0x40000000,
                    0x3fdaba9a, 0xe19d0913, 0xbe562f4e, 0x80000000, 0x3fda8ff9,
                    0x43cfd006, 0xbe4cfdeb, 0x40000000, 0x3fda656c, 0x686f0a4e,
                    0x3e5e47a8, 0xc0000000, 0x3fda3af2, 0x7200d410, 0x3e5e1199,
                    0xc0000000, 0x3fda108c, 0xabd2266e, 0x3e5ee4d1, 0x40000000,
                    0x3fd9e63a, 0x396f8f2c, 0x3e4dbffb, 0x00000000, 0x3fd9bbfb,
                    0xe32b25dd, 0x3e5c3a54, 0x40000000, 0x3fd991cf, 0x431e4035,
                    0xbe457925, 0x80000000, 0x3fd967b6, 0x7bed3dd3, 0x3e40c61d,
                    0x00000000, 0x3fd93db1, 0xd7449365, 0x3e306419, 0x80000000,
                    0x3fd913be, 0x1746e791, 0x3e56fcfc, 0x40000000, 0x3fd8e9df,
                    0xf3a9028b, 0xbe5041b9, 0xc0000000, 0x3fd8c012, 0x56840c50,
                    0xbe26e20a, 0x40000000, 0x3fd89659, 0x19763102, 0xbe51f466,
                    0x80000000, 0x3fd86cb2, 0x7032de7c, 0xbe4d298a, 0x80000000,
                    0x3fd8431e, 0xdeb39fab, 0xbe4361eb, 0x40000000, 0x3fd8199d,
                    0x5d01cbe0, 0xbe5425b3, 0x80000000, 0x3fd7f02e, 0x3ce99aa9,
                    0x3e146fa8, 0x80000000, 0x3fd7c6d2, 0xd1a262b9, 0xbe5a1a69,
                    0xc0000000, 0x3fd79d88, 0x8606c236, 0x3e423a08, 0x80000000,
                    0x3fd77451, 0x8fd1e1b7, 0x3e5a6a63, 0xc0000000, 0x3fd74b2c,
                    0xe491456a, 0x3e42c1ca, 0x40000000, 0x3fd7221a, 0x4499a6d7,
                    0x3e36a69a, 0x00000000, 0x3fd6f91a, 0x5237df94, 0xbe0f8f02,
                    0x00000000, 0x3fd6d02c, 0xb6482c6e, 0xbe5abcf7, 0x00000000,
                    0x3fd6a750, 0x1919fd61, 0xbe57ade2, 0x00000000, 0x3fd67e86,
                    0xaa7a994d, 0xbe3f3fbd, 0x00000000, 0x3fd655ce, 0x67db014c,
                    0x3e33c550, 0x00000000, 0x3fd62d28, 0xa82856b7, 0xbe1409d1,
                    0xc0000000, 0x3fd60493, 0x1e6a300d, 0x3e55d899, 0x80000000,
                    0x3fd5dc11, 0x1222bd5c, 0xbe35bfc0, 0xc0000000, 0x3fd5b3a0,
                    0x6e8dc2d3, 0x3e5d4d79, 0x00000000, 0x3fd58b42, 0xe0e4ace6,
                    0xbe517303, 0x80000000, 0x3fd562f4, 0xb306e0a8, 0x3e5edf0f,
                    0xc0000000, 0x3fd53ab8, 0x6574bc54, 0x3e5ee859, 0x80000000,
                    0x3fd5128e, 0xea902207, 0x3e5f6188, 0xc0000000, 0x3fd4ea75,
                    0x9f911d79, 0x3e511735, 0x80000000, 0x3fd4c26e, 0xf9c77397,
                    0xbe5b1643, 0x40000000, 0x3fd49a78, 0x15fc9258, 0x3e479a37,
                    0x80000000, 0x3fd47293, 0xd5a04dd9, 0xbe426e56, 0xc0000000,
                    0x3fd44abf, 0xe04042f5, 0x3e56f7c6, 0x40000000, 0x3fd422fd,
                    0x1d8bf2c8, 0x3e5d8810, 0x00000000, 0x3fd3fb4c, 0x88a8ddee,
                    0xbe311454, 0xc0000000, 0x3fd3d3ab, 0x3e3b5e47, 0xbe5d1b72,
                    0x40000000, 0x3fd3ac1c, 0xc2ab5d59, 0x3e31b02b, 0xc0000000,
                    0x3fd3849d, 0xd4e34b9e, 0x3e51cb2f, 0x40000000, 0x3fd35d30,
                    0x177204fb, 0xbe2b8cd7, 0x80000000, 0x3fd335d3, 0xfcd38c82,
                    0xbe4356e1, 0x80000000, 0x3fd30e87, 0x64f54acc, 0xbe4e6224,
                    0x00000000, 0x3fd2e74c, 0xaa7975d9, 0x3e5dc0fe, 0x80000000,
                    0x3fd2c021, 0x516dab3f, 0xbe50ffa3, 0x40000000, 0x3fd29907,
                    0x2bfb7313, 0x3e5674a2, 0xc0000000, 0x3fd271fd, 0x0549fc99,
                    0x3e385d29, 0xc0000000, 0x3fd24b04, 0x55b63073, 0xbe500c6d,
                    0x00000000, 0x3fd2241c, 0x3f91953a, 0x3e389977, 0xc0000000,
                    0x3fd1fd43, 0xa1543f71, 0xbe3487ab, 0xc0000000, 0x3fd1d67b,
                    0x4ec8867c, 0x3df6a2dc, 0x00000000, 0x3fd1afc4, 0x4328e3bb,
                    0x3e41d9c0, 0x80000000, 0x3fd1891c, 0x2e1cda84, 0x3e3bdd87,
                    0x40000000, 0x3fd16285, 0x4b5331ae, 0xbe53128e, 0x00000000,
                    0x3fd13bfe, 0xb9aec164, 0xbe52ac98, 0xc0000000, 0x3fd11586,
                    0xd91e1316, 0xbe350630, 0x80000000, 0x3fd0ef1f, 0x7cacc12c,
                    0x3e3f5219, 0x40000000, 0x3fd0c8c8, 0xbce277b7, 0x3e3d30c0,
                    0x00000000, 0x3fd0a281, 0x2a63447d, 0xbe541377, 0x80000000,
                    0x3fd07c49, 0xfac483b5, 0xbe5772ec, 0xc0000000, 0x3fd05621,
                    0x36b8a570, 0xbe4fd4bd, 0xc0000000, 0x3fd03009, 0xbae505f7,
                    0xbe450388, 0x80000000, 0x3fd00a01, 0x3e35aead, 0xbe5430fc,
                    0x80000000, 0x3fcfc811, 0x707475ac, 0x3e38806e, 0x80000000,
                    0x3fcf7c3f, 0xc91817fc, 0xbe40ccea, 0x80000000, 0x3fcf308c,
                    0xae05d5e9, 0xbe4919b8, 0x80000000, 0x3fcee4f8, 0xae6cc9e6,
                    0xbe530b94, 0x00000000, 0x3fce9983, 0x1efe3e8e, 0x3e57747e,
                    0x00000000, 0x3fce4e2d, 0xda78d9bf, 0xbe59a608, 0x00000000,
                    0x3fce02f5, 0x8abe2c2e, 0x3e4a35ad, 0x00000000, 0x3fcdb7dc,
                    0x1495450d, 0xbe0872cc, 0x80000000, 0x3fcd6ce1, 0x86ee0ba0,
                    0xbe4f59a0, 0x00000000, 0x3fcd2205, 0xe81ca888, 0x3e5402c3,
                    0x00000000, 0x3fccd747, 0x3b4424b9, 0x3e5dfdc3, 0x80000000,
                    0x3fcc8ca7, 0xd305b56c, 0x3e202da6, 0x00000000, 0x3fcc4226,
                    0x399a6910, 0xbe482a1c, 0x80000000, 0x3fcbf7c2, 0x747f7938,
                    0xbe587372, 0x80000000, 0x3fcbad7c, 0x6fc246a0, 0x3e50d83d,
                    0x00000000, 0x3fcb6355, 0xee9e9be5, 0xbe5c35bd, 0x80000000,
                    0x3fcb194a, 0x8416c0bc, 0x3e546d4f, 0x00000000, 0x3fcacf5e,
                    0x49f7f08f, 0x3e56da76, 0x00000000, 0x3fca858f, 0x5dc30de2,
                    0x3e5f390c, 0x00000000, 0x3fca3bde, 0x950583b6, 0xbe5e4169,
                    0x80000000, 0x3fc9f249, 0x33631553, 0x3e52aeb1, 0x00000000,
                    0x3fc9a8d3, 0xde8795a6, 0xbe59a504, 0x00000000, 0x3fc95f79,
                    0x076bf41e, 0x3e5122fe, 0x80000000, 0x3fc9163c, 0x2914c8e7,
                    0x3e3dd064, 0x00000000, 0x3fc8cd1d, 0x3a30eca3, 0xbe21b4aa,
                    0x80000000, 0x3fc8841a, 0xb2a96650, 0xbe575444, 0x80000000,
                    0x3fc83b34, 0x2376c0cb, 0xbe2a74c7, 0x80000000, 0x3fc7f26b,
                    0xd8a0b653, 0xbe5181b6, 0x00000000, 0x3fc7a9bf, 0x32257882,
                    0xbe4a78b4, 0x00000000, 0x3fc7612f, 0x1eee8bd9, 0xbe1bfe9d,
                    0x80000000, 0x3fc718bb, 0x0c603cc4, 0x3e36fdc9, 0x80000000,
                    0x3fc6d064, 0x3728b8cf, 0xbe1e542e, 0x80000000, 0x3fc68829,
                    0xc79a4067, 0x3e5c380f, 0x00000000, 0x3fc6400b, 0xf69eac69,
                    0x3e550a84, 0x80000000, 0x3fc5f808, 0xb7a780a4, 0x3e5d9224,
                    0x80000000, 0x3fc5b022, 0xad9dfb1e, 0xbe55242f, 0x00000000,
                    0x3fc56858, 0x659b18be, 0xbe4bfda3, 0x80000000, 0x3fc520a9,
                    0x66ee3631, 0xbe57d769, 0x80000000, 0x3fc4d916, 0x1ec62819,
                    0x3e2427f7, 0x80000000, 0x3fc4919f, 0xdec25369, 0xbe435431,
                    0x00000000, 0x3fc44a44, 0xa8acfc4b, 0xbe3c62e8, 0x00000000,
                    0x3fc40304, 0xcf1d3eab, 0xbdfba29f, 0x80000000, 0x3fc3bbdf,
                    0x79aba3ea, 0xbdf1b7c8, 0x80000000, 0x3fc374d6, 0xb8d186da,
                    0xbe5130cf, 0x80000000, 0x3fc32de8, 0x9d74f152, 0x3e2285b6,
                    0x00000000, 0x3fc2e716, 0x50ae7ca9, 0xbe503920, 0x80000000,
                    0x3fc2a05e, 0x6caed92e, 0xbe533924, 0x00000000, 0x3fc259c2,
                    0x9cb5034e, 0xbe510e31, 0x80000000, 0x3fc21340, 0x12c4d378,
                    0xbe540b43, 0x80000000, 0x3fc1ccd9, 0xcc418706, 0x3e59887a,
                    0x00000000, 0x3fc1868e, 0x921f4106, 0xbe528e67, 0x80000000,
                    0x3fc1405c, 0x3969441e, 0x3e5d8051, 0x00000000, 0x3fc0fa46,
                    0xd941ef5b, 0x3e5f9079, 0x80000000, 0x3fc0b44a, 0x5a3e81b2,
                    0xbe567691, 0x00000000, 0x3fc06e69, 0x9d66afe7, 0xbe4d43fb,
                    0x00000000, 0x3fc028a2, 0x0a92a162, 0xbe52f394, 0x00000000,
                    0x3fbfc5ea, 0x209897e5, 0x3e529e37, 0x00000000, 0x3fbf3ac5,
                    0x8458bd7b, 0x3e582831, 0x00000000, 0x3fbeafd5, 0xb8d8b4b8,
                    0xbe486b4a, 0x00000000, 0x3fbe2518, 0xe0a3b7b6, 0x3e5bafd2,
                    0x00000000, 0x3fbd9a90, 0x2bf2710e, 0x3e383b2b, 0x00000000,
                    0x3fbd103c, 0x73eb6ab7, 0xbe56d78d, 0x00000000, 0x3fbc861b,
                    0x32ceaff5, 0xbe32dc5a, 0x00000000, 0x3fbbfc2e, 0xbee04cb7,
                    0xbe4a71a4, 0x00000000, 0x3fbb7274, 0x35ae9577, 0x3e38142f,
                    0x00000000, 0x3fbae8ee, 0xcbaddab4, 0xbe5490f0, 0x00000000,
                    0x3fba5f9a, 0x95ce1114, 0x3e597c71, 0x00000000, 0x3fb9d67a,
                    0x6d7c0f78, 0x3e3abc2d, 0x00000000, 0x3fb94d8d, 0x2841a782,
                    0xbe566cbc, 0x00000000, 0x3fb8c4d2, 0x6ed429c6, 0xbe3cfff9,
                    0x00000000, 0x3fb83c4a, 0xe4a49fbb, 0xbe552964, 0x00000000,
                    0x3fb7b3f4, 0x2193d81e, 0xbe42fa72, 0x00000000, 0x3fb72bd0,
                    0xdd70c122, 0x3e527a8c, 0x00000000, 0x3fb6a3df, 0x03108a54,
                    0xbe450393, 0x00000000, 0x3fb61c1f, 0x30ff7954, 0x3e565840,
                    0x00000000, 0x3fb59492, 0xdedd460c, 0xbe5422b5, 0x00000000,
                    0x3fb50d36, 0x950f9f45, 0xbe5313f6, 0x00000000, 0x3fb4860b,
                    0x582cdcb1, 0x3e506d39, 0x00000000, 0x3fb3ff12, 0x7216d3a6,
                    0x3e4aa719, 0x00000000, 0x3fb3784a, 0x57a423fd, 0x3e5a9b9f,
                    0x00000000, 0x3fb2f1b4, 0x7a138b41, 0xbe50b418, 0x00000000,
                    0x3fb26b4e, 0x2fbfd7ea, 0x3e23a53e, 0x00000000, 0x3fb1e519,
                    0x18913ccb, 0x3e465fc1, 0x00000000, 0x3fb15f15, 0x7ea24e21,
                    0x3e042843, 0x00000000, 0x3fb0d941, 0x7c6d9c77, 0x3e59f61e,
                    0x00000000, 0x3fb0539e, 0x114efd44, 0x3e4ccab7, 0x00000000,
                    0x3faf9c56, 0x1777f657, 0x3e552f65, 0x00000000, 0x3fae91d2,
                    0xc317b86a, 0xbe5a61e0, 0x00000000, 0x3fad87ac, 0xb7664efb,
                    0xbe41f64e, 0x00000000, 0x3fac7de6, 0x5d3d03a9, 0x3e0807a0,
                    0x00000000, 0x3fab7480, 0x743c38eb, 0xbe3726e1, 0x00000000,
                    0x3faa6b78, 0x06a253f1, 0x3e5ad636, 0x00000000, 0x3fa962d0,
                    0xa35f541b, 0x3e5a187a, 0x00000000, 0x3fa85a88, 0x4b86e446,
                    0xbe508150, 0x00000000, 0x3fa7529c, 0x2589cacf, 0x3e52938a,
                    0x00000000, 0x3fa64b10, 0xaf6b11f2, 0xbe3454cd, 0x00000000,
                    0x3fa543e2, 0x97506fef, 0xbe5fdec5, 0x00000000, 0x3fa43d10,
                    0xe75f7dd9, 0xbe388dd3, 0x00000000, 0x3fa3369c, 0xa4139632,
                    0xbdea5177, 0x00000000, 0x3fa23086, 0x352d6f1e, 0xbe565ad6,
                    0x00000000, 0x3fa12acc, 0x77449eb7, 0xbe50d5c7, 0x00000000,
                    0x3fa0256e, 0x7478da78, 0x3e404724, 0x00000000, 0x3f9e40dc,
                    0xf59cef7f, 0xbe539d0a, 0x00000000, 0x3f9c3790, 0x1511d43c,
                    0x3e53c2c8, 0x00000000, 0x3f9a2f00, 0x9b8bff3c, 0xbe43b3e1,
                    0x00000000, 0x3f982724, 0xad1e22a5, 0x3e46f0bd, 0x00000000,
                    0x3f962000, 0x130d9356, 0x3e475ba0, 0x00000000, 0x3f941994,
                    0x8f86f883, 0xbe513d0b, 0x00000000, 0x3f9213dc, 0x914d0dc8,
                    0xbe534335, 0x00000000, 0x3f900ed8, 0x2d73e5e7, 0xbe22ba75,
                    0x00000000, 0x3f8c1510, 0xc5b7d70e, 0x3e599c5d, 0x00000000,
                    0x3f880de0, 0x8a27857e, 0xbe3d28c8, 0x00000000, 0x3f840810,
                    0xda767328, 0x3e531b3d, 0x00000000, 0x3f8003b0, 0x77bacaf3,
                    0xbe5f04e3, 0x00000000, 0x3f780150, 0xdf4b0720, 0x3e5a8bff,
                    0x00000000, 0x3f6ffc40, 0x34c48e71, 0xbe3fcd99, 0x00000000,
                    0x3f5ff6c0, 0x1ad218af, 0xbe4c78a7, 0x00000000, 0x00000000,
                    0x00000000, 0x80000000
    };

    private static int[] logTwoPow = {
                    0xfefa39ef, 0x3fe62e42, 0xfefa39ef, 0xbfe62e42
    };

    public void powIntrinsic(Register dest, Register value1, Register value2, CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        ArrayDataPointerConstant highSigMaskPtr = new ArrayDataPointerConstant(highSigMask, 16);
        ArrayDataPointerConstant logTwoEPtr = new ArrayDataPointerConstant(logTwoE, 16);
        ArrayDataPointerConstant highmaskYPtr = new ArrayDataPointerConstant(highmaskY, 16);
        ArrayDataPointerConstant tExpPtr = new ArrayDataPointerConstant(tExp, 16);
        ArrayDataPointerConstant eCoeffPtr = new ArrayDataPointerConstant(eCoeff, 16);
        ArrayDataPointerConstant coeffHPtr = new ArrayDataPointerConstant(coeffH, 16);
        ArrayDataPointerConstant highmaskLogXPtr = new ArrayDataPointerConstant(highmaskLogX, 16);
        ArrayDataPointerConstant halfmaskPtr = new ArrayDataPointerConstant(halfmask, 8);
        ArrayDataPointerConstant coeffPowPtr = new ArrayDataPointerConstant(coeffPow, 16);
        ArrayDataPointerConstant lTblPowPtr = new ArrayDataPointerConstant(lTblPow, 16);
        ArrayDataPointerConstant logTwoPowPtr = new ArrayDataPointerConstant(logTwoPow, 8);

        Label bb0 = new Label();
        Label bb1 = new Label();
        Label bb2 = new Label();
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
        Label bb15 = new Label();
        Label bb16 = new Label();
        Label bb18 = new Label();
        Label bb19 = new Label();
        Label bb20 = new Label();
        Label bb21 = new Label();
        Label bb22 = new Label();
        Label bb23 = new Label();
        Label bb24 = new Label();
        Label bb25 = new Label();
        Label bb26 = new Label();
        Label bb27 = new Label();
        Label bb28 = new Label();
        Label bb29 = new Label();
        Label bb30 = new Label();
        Label bb31 = new Label();
        Label bb32 = new Label();
        Label bb33 = new Label();
        Label bb34 = new Label();
        Label bb35 = new Label();
        Label bb36 = new Label();
        Label bb37 = new Label();
        Label bb38 = new Label();
        Label bb39 = new Label();
        Label bb40 = new Label();
        Label bb41 = new Label();
        Label bb42 = new Label();
        Label bb43 = new Label();
        Label bb44 = new Label();
        Label bb45 = new Label();
        Label bb46 = new Label();
        Label bb47 = new Label();
        Label bb48 = new Label();
        Label bb49 = new Label();
        Label bb50 = new Label();
        Label bb51 = new Label();
        Label bb53 = new Label();
        Label bb54 = new Label();
        Label bb55 = new Label();
        Label bb56 = new Label();

        Register gpr1 = asRegister(gpr1Temp, AMD64Kind.QWORD);
        Register gpr2 = asRegister(gpr2Temp, AMD64Kind.QWORD);
        Register gpr3 = asRegister(rcxTemp, AMD64Kind.QWORD);
        Register gpr4 = asRegister(gpr4Temp, AMD64Kind.QWORD);
        Register gpr5 = asRegister(gpr5Temp, AMD64Kind.QWORD);
        Register gpr6 = asRegister(gpr6Temp, AMD64Kind.QWORD);
        Register gpr7 = asRegister(gpr7Temp, AMD64Kind.QWORD);
        Register gpr8 = asRegister(gpr8Temp, AMD64Kind.QWORD);

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

        setCrb(crb);
        masm.movdqu(temp10, value1);
        masm.movsd(temp8, value2);
        if (dest.encoding != value1.encoding) {
            masm.movdqu(dest, value1);
        }

        masm.movq(temp9, externalAddress(logTwoEPtr));       // 0x00000000,
                                                             // 0x3ff72000
        masm.pextrw(gpr1, dest, 3);
        masm.xorpd(temp2, temp2);
        masm.movq(gpr2, 0x3ff0000000000000L);
        masm.movdq(temp2, gpr2);
        masm.movl(gpr5, 1069088768);
        masm.movdq(temp7, gpr5);
        masm.xorpd(temp1, temp1);
        masm.movq(gpr6, 0x77f0000000000000L);
        masm.movdq(temp1, gpr6);
        masm.movdqu(temp3, dest);
        masm.movl(gpr4, 32752);
        masm.andl(gpr4, gpr1);
        masm.subl(gpr4, 16368);
        masm.movl(gpr3, gpr4);
        masm.sarl(gpr4, 31);
        masm.addl(gpr3, gpr4);
        masm.xorl(gpr3, gpr4);
        masm.por(dest, temp2);
        masm.movdqu(temp6, externalAddress(highSigMaskPtr)); // 0x00000000,
                                                             // 0xfffff800,
                                                             // 0x00000000,
                                                             // 0xfffff800
        masm.psrlq(dest, 27);
        masm.psrld(dest, 2);
        masm.addl(gpr3, 16);
        masm.bsrl(gpr3, gpr3);
        masm.rcpps(dest, dest);
        masm.psllq(temp3, 12);
        masm.movl(gpr7, 8192);
        masm.movdq(temp4, gpr7);
        masm.psrlq(temp3, 12);
        masm.subl(gpr1, 16);
        masm.cmpl(gpr1, 32736);
        masm.jcc(ConditionFlag.AboveEqual, bb0);

        masm.movl(gpr5, 0);

        masm.bind(bb1);
        masm.mulss(dest, temp7);
        masm.movl(gpr4, -1);
        masm.subl(gpr3, 4);
        masm.shll(gpr4);
        masm.shlq(gpr4, 32);
        masm.movdq(temp5, gpr4);
        masm.por(temp3, temp1);
        masm.subl(gpr1, 16351);
        masm.cmpl(gpr1, 1);
        masm.jcc(ConditionFlag.BelowEqual, bb2);

        masm.paddd(dest, temp4);
        masm.pand(temp5, temp3);
        masm.movdl(gpr4, dest);
        masm.psllq(dest, 29);

        masm.bind(bb3);
        masm.subsd(temp3, temp5);
        masm.pand(dest, temp6);
        masm.subl(gpr1, 1);
        masm.sarl(gpr1, 4);
        masm.cvtsi2sdl(temp7, gpr1);
        masm.mulpd(temp5, dest);

        masm.bind(bb4);
        masm.mulsd(temp3, dest);
        masm.leaq(gpr8, externalAddress(coeffPowPtr));
        masm.movdqu(temp1, new AMD64Address(gpr8, 0));       // 0x6dc96112,
                                                             // 0xbf836578,
                                                             // 0xee241472,
                                                             // 0xbf9b0301
        masm.movdqu(temp4, new AMD64Address(gpr8, 16));      // 0x9f95985a,
                                                             // 0xbfb528db,
                                                             // 0xb3841d2a,
                                                             // 0xbfd619b6
        masm.movdqu(temp6, new AMD64Address(gpr8, 32));      // 0x518775e3,
                                                             // 0x3f9004f2,
                                                             // 0xac8349bb,
                                                             // 0x3fa76c9b
        masm.movdqu(dest, new AMD64Address(gpr8, 48));       // 0x486ececc,
                                                             // 0x3fc4635e,
                                                             // 0x161bb241,
                                                             // 0xbf5dabe1
        masm.subsd(temp5, temp9);
        masm.movl(gpr3, gpr1);
        masm.sarl(gpr1, 31);
        masm.addl(gpr3, gpr1);
        masm.xorl(gpr1, gpr3);
        masm.addl(gpr1, 1);
        masm.bsrl(gpr1, gpr1);
        masm.unpcklpd(temp5, temp3);
        masm.addsd(temp3, temp5);
        masm.leaq(gpr7, externalAddress(lTblPowPtr));
        masm.andl(gpr4, 16760832);
        masm.shrl(gpr4, 10);
        masm.addpd(temp5, new AMD64Address(gpr7, gpr4, Scale.Times1, -3648));
        masm.pshufd(temp2, temp3, 0x44);
        masm.mulsd(temp3, temp3);
        masm.mulpd(temp1, temp2);
        masm.mulpd(temp4, temp2);
        masm.addsd(temp5, temp7);
        masm.mulsd(temp2, temp3);
        masm.addpd(temp6, temp1);
        masm.mulsd(temp3, temp3);
        masm.addpd(dest, temp4);
        masm.movdqu(temp1, temp8);
        masm.pextrw(gpr3, temp8, 3);
        masm.pshufd(temp7, temp5, 0xEE);
        masm.movq(temp4, externalAddress(highmaskYPtr));     // 0x00000000,
                                                             // 0xfffffff8
        masm.mulpd(temp6, temp2);
        masm.pshufd(temp3, temp3, 0x44);
        masm.mulpd(dest, temp2);
        masm.shll(gpr1, 4);
        masm.subl(gpr1, 15872);
        masm.andl(gpr3, 32752);
        masm.addl(gpr1, gpr3);
        masm.mulpd(temp3, temp6);
        masm.cmpl(gpr1, 624);
        masm.jcc(ConditionFlag.AboveEqual, bb5);

        masm.xorpd(temp6, temp6);
        masm.movl(gpr4, 17080);
        masm.pinsrw(temp6, gpr4, 3);
        masm.movdqu(temp2, temp1);
        masm.pand(temp4, temp1);
        masm.subsd(temp1, temp4);
        masm.mulsd(temp4, temp5);
        masm.addsd(dest, temp7);
        masm.mulsd(temp1, temp5);
        masm.movdqu(temp7, temp6);
        masm.addsd(temp6, temp4);
        masm.leaq(gpr7, externalAddress(tExpPtr));
        masm.addpd(temp3, dest);
        masm.movdl(gpr4, temp6);
        masm.movl(gpr3, gpr4);
        masm.andl(gpr4, 255);
        masm.addl(gpr4, gpr4);
        masm.movdqu(temp5, new AMD64Address(gpr7, gpr4, Scale.Times8, 0));
        masm.subsd(temp6, temp7);
        masm.pshufd(dest, temp3, 0xEE);
        masm.subsd(temp4, temp6);
        masm.addsd(dest, temp3);
        masm.addsd(temp4, temp1);
        masm.mulsd(temp2, dest);
        masm.leaq(gpr8, externalAddress(eCoeffPtr));
        masm.movdqu(temp7, new AMD64Address(gpr8, 0));       // 0xe78a6731,
                                                             // 0x3f55d87f,
                                                             // 0xd704a0c0,
                                                             // 0x3fac6b08
        masm.movdqu(temp3, new AMD64Address(gpr8, 16));      // 0x6fba4e77,
                                                             // 0x3f83b2ab,
                                                             // 0xff82c58f,
                                                             // 0x3fcebfbd
        masm.shll(gpr3, 12);
        masm.xorl(gpr3, gpr5);
        masm.andl(gpr3, -1048576);
        masm.movdq(temp6, gpr3);
        masm.addsd(temp2, temp4);
        masm.movq(gpr2, 0x3fe62e42fefa39efL);
        masm.movdq(temp1, gpr2);
        masm.pshufd(dest, temp2, 0x44);
        masm.pshufd(temp4, temp2, 0x44);
        masm.mulsd(temp1, temp2);
        masm.pshufd(temp6, temp6, 0x11);
        masm.mulpd(dest, dest);
        masm.mulpd(temp7, temp4);
        masm.paddd(temp5, temp6);
        masm.mulsd(temp1, temp5);
        masm.pshufd(temp6, temp5, 0xEE);
        masm.mulsd(dest, dest);
        masm.addpd(temp3, temp7);
        masm.addsd(temp1, temp6);
        masm.mulpd(dest, temp3);
        masm.pshufd(temp3, dest, 0xEE);
        masm.mulsd(dest, temp5);
        masm.mulsd(temp3, temp5);
        masm.addsd(dest, temp1);
        masm.addsd(dest, temp3);
        masm.addsd(dest, temp5);
        masm.jmp(bb56);

        masm.bind(bb0);
        masm.addl(gpr1, 16);
        masm.movl(gpr4, 32752);
        masm.andl(gpr4, gpr1);
        masm.cmpl(gpr4, 32752);
        masm.jcc(ConditionFlag.Equal, bb6);

        masm.testl(gpr1, 32768);
        masm.jcc(ConditionFlag.NotEqual, bb7);

        masm.bind(bb8);
        masm.movdqu(dest, temp10);
        masm.movdqu(temp3, temp10);
        masm.movdl(gpr4, temp3);
        masm.psrlq(temp3, 32);
        masm.movdl(gpr3, temp3);
        masm.orl(gpr4, gpr3);
        masm.cmpl(gpr4, 0);
        masm.jcc(ConditionFlag.Equal, bb9);

        masm.xorpd(temp3, temp3);
        masm.movl(gpr1, 18416);
        masm.pinsrw(temp3, gpr1, 3);
        masm.mulsd(dest, temp3);
        masm.xorpd(temp2, temp2);
        masm.movl(gpr1, 16368);
        masm.pinsrw(temp2, gpr1, 3);
        masm.movdqu(temp3, dest);
        masm.pextrw(gpr1, dest, 3);
        masm.por(dest, temp2);
        masm.movl(gpr3, 18416);
        masm.psrlq(dest, 27);
        masm.psrld(dest, 2);
        masm.rcpps(dest, dest);
        masm.psllq(temp3, 12);
        masm.movdqu(temp6, externalAddress(highSigMaskPtr)); // 0x00000000,
                                                             // 0xfffff800,
                                                             // 0x00000000,
                                                             // 0xfffff800
        masm.psrlq(temp3, 12);
        masm.mulss(dest, temp7);
        masm.movl(gpr4, -1024);
        masm.movdl(temp5, gpr4);
        masm.por(temp3, temp1);
        masm.paddd(dest, temp4);
        masm.psllq(temp5, 32);
        masm.movdl(gpr4, dest);
        masm.psllq(dest, 29);
        masm.pand(temp5, temp3);
        masm.movl(gpr5, 0);
        masm.pand(dest, temp6);
        masm.subsd(temp3, temp5);
        masm.andl(gpr1, 32752);
        masm.subl(gpr1, 18416);
        masm.sarl(gpr1, 4);
        masm.cvtsi2sdl(temp7, gpr1);
        masm.mulpd(temp5, dest);
        masm.jmp(bb4);

        masm.bind(bb10);
        masm.movdqu(dest, temp10);
        masm.movdqu(temp3, temp10);
        masm.movdl(gpr4, temp3);
        masm.psrlq(temp3, 32);
        masm.movdl(gpr3, temp3);
        masm.orl(gpr4, gpr3);
        masm.cmpl(gpr4, 0);
        masm.jcc(ConditionFlag.Equal, bb9);

        masm.xorpd(temp3, temp3);
        masm.movl(gpr1, 18416);
        masm.pinsrw(temp3, gpr1, 3);
        masm.mulsd(dest, temp3);
        masm.xorpd(temp2, temp2);
        masm.movl(gpr1, 16368);
        masm.pinsrw(temp2, gpr1, 3);
        masm.movdqu(temp3, dest);
        masm.pextrw(gpr1, dest, 3);
        masm.por(dest, temp2);
        masm.movl(gpr3, 18416);
        masm.psrlq(dest, 27);
        masm.psrld(dest, 2);
        masm.rcpps(dest, dest);
        masm.psllq(temp3, 12);
        masm.movdqu(temp6, externalAddress(highSigMaskPtr)); // 0x00000000,
                                                             // 0xfffff800,
                                                             // 0x00000000,
                                                             // 0xfffff800
        masm.psrlq(temp3, 12);
        masm.mulss(dest, temp7);
        masm.movl(gpr4, -1024);
        masm.movdl(temp5, gpr4);
        masm.por(temp3, temp1);
        masm.paddd(dest, temp4);
        masm.psllq(temp5, 32);
        masm.movdl(gpr4, dest);
        masm.psllq(dest, 29);
        masm.pand(temp5, temp3);
        masm.movl(gpr5, Integer.MIN_VALUE);
        masm.pand(dest, temp6);
        masm.subsd(temp3, temp5);
        masm.andl(gpr1, 32752);
        masm.subl(gpr1, 18416);
        masm.sarl(gpr1, 4);
        masm.cvtsi2sdl(temp7, gpr1);
        masm.mulpd(temp5, dest);
        masm.jmp(bb4);

        masm.bind(bb5);
        masm.cmpl(gpr1, 0);
        masm.jcc(ConditionFlag.Less, bb11);

        masm.cmpl(gpr1, 752);
        masm.jcc(ConditionFlag.AboveEqual, bb12);

        masm.addsd(dest, temp7);
        masm.movq(temp4, externalAddress(halfmaskPtr));      // 0xf8000000,
                                                             // 0xffffffff
        masm.addpd(temp3, dest);
        masm.xorpd(temp6, temp6);
        masm.movl(gpr1, 17080);
        masm.pinsrw(temp6, gpr1, 3);
        masm.pshufd(dest, temp3, 0xEE);
        masm.addsd(dest, temp3);
        masm.movdqu(temp3, temp5);
        masm.addsd(temp5, dest);
        masm.subsd(temp3, temp5);
        masm.movdqu(temp7, temp5);
        masm.pand(temp5, temp4);
        masm.movdqu(temp2, temp1);
        masm.pand(temp4, temp1);
        masm.subsd(temp7, temp5);
        masm.addsd(dest, temp3);
        masm.subsd(temp1, temp4);
        masm.mulsd(temp4, temp5);
        masm.addsd(dest, temp7);
        masm.mulsd(temp2, dest);
        masm.movdqu(temp7, temp6);
        masm.mulsd(temp1, temp5);
        masm.addsd(temp6, temp4);
        masm.movdl(gpr1, temp6);
        masm.subsd(temp6, temp7);
        masm.leaq(gpr7, externalAddress(tExpPtr));
        masm.movl(gpr3, gpr1);
        masm.andl(gpr1, 255);
        masm.addl(gpr1, gpr1);
        masm.movdqu(temp5, new AMD64Address(gpr7, gpr1, Scale.Times8, 0));
        masm.addsd(temp2, temp1);
        masm.leaq(gpr8, externalAddress(eCoeffPtr));
        masm.movdqu(temp7, new AMD64Address(gpr8, 0));       // 0xe78a6731,
                                                             // 0x3f55d87f,
                                                             // 0xd704a0c0,
                                                             // 0x3fac6b08
        masm.movdqu(temp3, new AMD64Address(gpr8, 16));      // 0x6fba4e77,
                                                             // 0x3f83b2ab,
                                                             // 0xff82c58f,
                                                             // 0x3fcebfbd
        masm.subsd(temp4, temp6);
        masm.pextrw(gpr4, temp6, 3);
        masm.addsd(temp2, temp4);
        masm.sarl(gpr3, 8);
        masm.movl(gpr1, gpr3);
        masm.sarl(gpr3, 1);
        masm.subl(gpr1, gpr3);
        masm.shll(gpr3, 20);
        masm.xorl(gpr3, gpr5);
        masm.movdl(temp6, gpr3);
        masm.movq(temp1, new AMD64Address(gpr8, 32));        // 0xfefa39ef,
                                                             // 0x3fe62e42
        masm.andl(gpr4, 32767);
        masm.cmpl(gpr4, 16529);
        masm.jcc(ConditionFlag.Above, bb12);

        masm.pshufd(dest, temp2, 0x44);
        masm.pshufd(temp4, temp2, 0x44);
        masm.mulpd(dest, dest);
        masm.mulpd(temp7, temp4);
        masm.pshufd(temp6, temp6, 0x11);
        masm.mulsd(temp1, temp2);
        masm.mulsd(dest, dest);
        masm.paddd(temp5, temp6);
        masm.addpd(temp3, temp7);
        masm.mulsd(temp1, temp5);
        masm.pshufd(temp6, temp5, 0xEE);
        masm.mulpd(dest, temp3);
        masm.addsd(temp1, temp6);
        masm.pshufd(temp3, dest, 0xEE);
        masm.mulsd(dest, temp5);
        masm.mulsd(temp3, temp5);
        masm.shll(gpr1, 4);
        masm.xorpd(temp4, temp4);
        masm.addl(gpr1, 16368);
        masm.pinsrw(temp4, gpr1, 3);
        masm.addsd(dest, temp1);
        masm.addsd(dest, temp3);
        masm.movdqu(temp1, dest);
        masm.addsd(dest, temp5);
        masm.mulsd(dest, temp4);
        masm.pextrw(gpr1, dest, 3);
        masm.andl(gpr1, 32752);
        masm.jcc(ConditionFlag.Equal, bb13);

        masm.cmpl(gpr1, 32752);
        masm.jcc(ConditionFlag.Equal, bb14);

        masm.jmp(bb56);

        masm.bind(bb6);
        masm.movdqu(temp1, temp8);
        masm.movdqu(dest, temp10);
        masm.movdqu(temp2, dest);
        masm.movdl(gpr1, temp2);
        masm.psrlq(temp2, 20);
        masm.movdl(gpr4, temp2);
        masm.orl(gpr1, gpr4);
        masm.jcc(ConditionFlag.Equal, bb15);

        masm.movdl(gpr1, temp1);
        masm.psrlq(temp1, 32);
        masm.movdl(gpr4, temp1);
        masm.movl(gpr3, gpr4);
        masm.addl(gpr4, gpr4);
        masm.orl(gpr1, gpr4);
        masm.jcc(ConditionFlag.Equal, bb16);

        masm.addsd(dest, dest);
        masm.jmp(bb56);

        masm.bind(bb16);
        masm.xorpd(dest, dest);
        masm.movl(gpr1, 16368);
        masm.pinsrw(dest, gpr1, 3);
        masm.jmp(bb56);

        masm.bind(bb18);
        masm.addpd(dest, temp8);
        masm.jmp(bb56);

        masm.bind(bb15);
        masm.movdl(gpr1, temp1);
        masm.movdqu(temp2, temp1);
        masm.psrlq(temp1, 32);
        masm.movdl(gpr4, temp1);
        masm.movl(gpr3, gpr4);
        masm.addl(gpr4, gpr4);
        masm.orl(gpr1, gpr4);
        masm.jcc(ConditionFlag.Equal, bb19);

        masm.pextrw(gpr1, temp2, 3);
        masm.andl(gpr1, 32752);
        masm.cmpl(gpr1, 32752);
        masm.jcc(ConditionFlag.NotEqual, bb20);

        masm.movdl(gpr1, temp2);
        masm.psrlq(temp2, 20);
        masm.movdl(gpr4, temp2);
        masm.orl(gpr1, gpr4);
        masm.jcc(ConditionFlag.NotEqual, bb18);

        masm.bind(bb20);
        masm.pextrw(gpr1, dest, 3);
        masm.testl(gpr1, 32768);
        masm.jcc(ConditionFlag.NotEqual, bb21);

        masm.testl(gpr3, Integer.MIN_VALUE);
        masm.jcc(ConditionFlag.NotZero, bb22);

        masm.jmp(bb56);

        masm.bind(bb23);
        masm.movdl(gpr1, temp8);
        masm.testl(gpr1, 1);
        masm.jcc(ConditionFlag.NotEqual, bb24);

        masm.testl(gpr1, 2);
        masm.jcc(ConditionFlag.NotEqual, bb25);

        masm.jmp(bb24);

        masm.bind(bb21);
        masm.shrl(gpr3, 20);
        masm.andl(gpr3, 2047);
        masm.cmpl(gpr3, 1075);
        masm.jcc(ConditionFlag.Above, bb24);

        masm.jcc(ConditionFlag.Equal, bb26);

        masm.cmpl(gpr3, 1074);
        masm.jcc(ConditionFlag.Above, bb23);

        masm.cmpl(gpr3, 1023);
        masm.jcc(ConditionFlag.Below, bb24);

        masm.movdqu(temp1, temp8);
        masm.movl(gpr1, 17208);
        masm.xorpd(temp3, temp3);
        masm.pinsrw(temp3, gpr1, 3);
        masm.movdqu(temp4, temp3);
        masm.addsd(temp3, temp1);
        masm.subsd(temp4, temp3);
        masm.addsd(temp1, temp4);
        masm.pextrw(gpr1, temp1, 3);
        masm.andl(gpr1, 32752);
        masm.jcc(ConditionFlag.NotEqual, bb24);

        masm.movdl(gpr1, temp3);
        masm.andl(gpr1, 1);
        masm.jcc(ConditionFlag.Equal, bb24);

        masm.bind(bb25);
        masm.pextrw(gpr1, temp8, 3);
        masm.andl(gpr1, 32768);
        masm.jcc(ConditionFlag.NotEqual, bb27);

        masm.jmp(bb56);

        masm.bind(bb27);
        masm.xorpd(dest, dest);
        masm.movl(gpr1, 32768);
        masm.pinsrw(dest, gpr1, 3);
        masm.jmp(bb56);

        masm.bind(bb24);
        masm.pextrw(gpr1, temp8, 3);
        masm.andl(gpr1, 32768);
        masm.jcc(ConditionFlag.NotEqual, bb22);

        masm.xorpd(dest, dest);
        masm.movl(gpr1, 32752);
        masm.pinsrw(dest, gpr1, 3);
        masm.jmp(bb56);

        masm.bind(bb26);
        masm.movdl(gpr1, temp8);
        masm.andl(gpr1, 1);
        masm.jcc(ConditionFlag.Equal, bb24);

        masm.jmp(bb25);

        masm.bind(bb28);
        masm.movdl(gpr1, temp1);
        masm.psrlq(temp1, 20);
        masm.movdl(gpr4, temp1);
        masm.orl(gpr1, gpr4);
        masm.jcc(ConditionFlag.Equal, bb29);

        masm.addsd(dest, temp8);
        masm.jmp(bb56);

        masm.bind(bb29);
        masm.movdqu(dest, temp10);
        masm.pextrw(gpr1, dest, 3);
        masm.cmpl(gpr1, 49136);
        masm.jcc(ConditionFlag.NotEqual, bb30);

        masm.movdl(gpr3, dest);
        masm.psrlq(dest, 20);
        masm.movdl(gpr4, dest);
        masm.orl(gpr3, gpr4);
        masm.jcc(ConditionFlag.NotEqual, bb30);

        masm.xorpd(dest, dest);
        masm.movl(gpr1, 32760);
        masm.pinsrw(dest, gpr1, 3);
        masm.jmp(bb56);

        masm.bind(bb30);
        masm.andl(gpr1, 32752);
        masm.subl(gpr1, 16368);
        masm.pextrw(gpr4, temp8, 3);
        masm.xorpd(dest, dest);
        masm.xorl(gpr1, gpr4);
        masm.andl(gpr1, 32768);
        masm.jcc(ConditionFlag.Equal, bb31);

        masm.jmp(bb56);

        masm.bind(bb31);
        masm.movl(gpr3, 32752);
        masm.pinsrw(dest, gpr3, 3);
        masm.jmp(bb56);

        masm.bind(bb32);
        masm.movdl(gpr1, temp1);
        masm.cmpl(gpr4, 17184);
        masm.jcc(ConditionFlag.Above, bb33);

        masm.testl(gpr1, 1);
        masm.jcc(ConditionFlag.NotEqual, bb34);

        masm.testl(gpr1, 2);
        masm.jcc(ConditionFlag.Equal, bb35);

        masm.jmp(bb36);

        masm.bind(bb33);
        masm.testl(gpr1, 1);
        masm.jcc(ConditionFlag.Equal, bb35);

        masm.jmp(bb36);

        masm.bind(bb7);
        masm.movdqu(temp2, temp10);
        masm.movdl(gpr1, temp2);
        masm.psrlq(temp2, 31);
        masm.movdl(gpr3, temp2);
        masm.orl(gpr1, gpr3);
        masm.jcc(ConditionFlag.Equal, bb9);

        masm.pextrw(gpr4, temp8, 3);
        masm.movdl(gpr1, temp8);
        masm.movdqu(temp2, temp8);
        masm.psrlq(temp2, 32);
        masm.movdl(gpr3, temp2);
        masm.addl(gpr3, gpr3);
        masm.orl(gpr3, gpr1);
        masm.jcc(ConditionFlag.Equal, bb37);

        masm.andl(gpr4, 32752);
        masm.cmpl(gpr4, 32752);
        masm.jcc(ConditionFlag.Equal, bb28);

        masm.cmpl(gpr4, 17200);
        masm.jcc(ConditionFlag.Above, bb35);

        masm.cmpl(gpr4, 17184);
        masm.jcc(ConditionFlag.AboveEqual, bb32);

        masm.cmpl(gpr4, 16368);
        masm.jcc(ConditionFlag.Below, bb34);

        masm.movl(gpr1, 17208);
        masm.xorpd(temp2, temp2);
        masm.pinsrw(temp2, gpr1, 3);
        masm.movdqu(temp4, temp2);
        masm.addsd(temp2, temp1);
        masm.subsd(temp4, temp2);
        masm.addsd(temp1, temp4);
        masm.pextrw(gpr1, temp1, 3);
        masm.andl(gpr1, 32767);
        masm.jcc(ConditionFlag.NotEqual, bb34);

        masm.movdl(gpr1, temp2);
        masm.andl(gpr1, 1);
        masm.jcc(ConditionFlag.Equal, bb35);

        masm.bind(bb36);
        masm.xorpd(temp1, temp1);
        masm.movl(gpr4, 30704);
        masm.pinsrw(temp1, gpr4, 3);
        masm.pextrw(gpr1, temp10, 3);
        masm.movl(gpr4, 8192);
        masm.movdl(temp4, gpr4);
        masm.andl(gpr1, 32767);
        masm.subl(gpr1, 16);
        masm.jcc(ConditionFlag.Less, bb10);

        masm.movl(gpr4, gpr1);
        masm.andl(gpr4, 32752);
        masm.subl(gpr4, 16368);
        masm.movl(gpr3, gpr4);
        masm.sarl(gpr4, 31);
        masm.addl(gpr3, gpr4);
        masm.xorl(gpr3, gpr4);
        masm.addl(gpr3, 16);
        masm.bsrl(gpr3, gpr3);
        masm.movl(gpr5, Integer.MIN_VALUE);
        masm.jmp(bb1);

        masm.bind(bb34);
        masm.xorpd(temp1, temp1);
        masm.movl(gpr1, 32752);
        masm.pinsrw(temp1, gpr1, 3);
        masm.xorpd(dest, dest);
        masm.mulsd(dest, temp1);
        masm.jmp(bb56);

        masm.bind(bb35);
        masm.xorpd(temp1, temp1);
        masm.movl(gpr4, 30704);
        masm.pinsrw(temp1, gpr4, 3);
        masm.pextrw(gpr1, temp10, 3);
        masm.movl(gpr4, 8192);
        masm.movdl(temp4, gpr4);
        masm.andl(gpr1, 32767);
        masm.subl(gpr1, 16);
        masm.jcc(ConditionFlag.Less, bb8);

        masm.movl(gpr4, gpr1);
        masm.andl(gpr4, 32752);
        masm.subl(gpr4, 16368);
        masm.movl(gpr3, gpr4);
        masm.sarl(gpr4, 31);
        masm.addl(gpr3, gpr4);
        masm.xorl(gpr3, gpr4);
        masm.addl(gpr3, 16);
        masm.bsrl(gpr3, gpr3);
        masm.movl(gpr5, 0);
        masm.jmp(bb1);

        masm.bind(bb19);
        masm.xorpd(dest, dest);
        masm.movl(gpr1, 16368);
        masm.pinsrw(dest, gpr1, 3);
        masm.jmp(bb56);

        masm.bind(bb22);
        masm.xorpd(dest, dest);
        masm.jmp(bb56);

        masm.bind(bb11);
        masm.addl(gpr1, 384);
        masm.cmpl(gpr1, 0);
        masm.jcc(ConditionFlag.Less, bb38);

        masm.mulsd(temp5, temp1);
        masm.addsd(dest, temp7);
        masm.shrl(gpr5, 31);
        masm.addpd(temp3, dest);
        masm.pshufd(dest, temp3, 0xEE);
        masm.addsd(temp3, dest);
        masm.leaq(gpr7, externalAddress(logTwoPowPtr));      // 0xfefa39ef,
                                                             // 0x3fe62e42,
                                                             // 0xfefa39ef,
                                                             // 0xbfe62e42
        masm.movq(temp4, new AMD64Address(gpr7, gpr5, Scale.Times8, 0));
        masm.mulsd(temp1, temp3);
        masm.xorpd(dest, dest);
        masm.movl(gpr1, 16368);
        masm.shll(gpr5, 15);
        masm.orl(gpr1, gpr5);
        masm.pinsrw(dest, gpr1, 3);
        masm.addsd(temp5, temp1);
        masm.mulsd(temp5, temp4);
        masm.addsd(dest, temp5);
        masm.jmp(bb56);

        masm.bind(bb38);

        masm.bind(bb37);
        masm.xorpd(dest, dest);
        masm.movl(gpr1, 16368);
        masm.pinsrw(dest, gpr1, 3);
        masm.jmp(bb56);

        masm.bind(bb39);
        masm.xorpd(dest, dest);
        masm.movl(gpr1, 16368);
        masm.pinsrw(dest, gpr1, 3);
        masm.jmp(bb56);

        masm.bind(bb9);
        masm.movdqu(temp2, temp8);
        masm.pextrw(gpr1, temp8, 3);
        masm.andl(gpr1, 32752);
        masm.cmpl(gpr1, 32752);
        masm.jcc(ConditionFlag.NotEqual, bb40);

        masm.movdl(gpr1, temp2);
        masm.psrlq(temp2, 20);
        masm.movdl(gpr4, temp2);
        masm.orl(gpr1, gpr4);
        masm.jcc(ConditionFlag.NotEqual, bb18);

        masm.bind(bb40);
        masm.movdl(gpr1, temp1);
        masm.psrlq(temp1, 32);
        masm.movdl(gpr4, temp1);
        masm.movl(gpr3, gpr4);
        masm.addl(gpr4, gpr4);
        masm.orl(gpr1, gpr4);
        masm.jcc(ConditionFlag.Equal, bb39);

        masm.shrl(gpr4, 21);
        masm.cmpl(gpr4, 1075);
        masm.jcc(ConditionFlag.Above, bb41);

        masm.jcc(ConditionFlag.Equal, bb42);

        masm.cmpl(gpr4, 1023);
        masm.jcc(ConditionFlag.Below, bb41);

        masm.movdqu(temp1, temp8);
        masm.movl(gpr1, 17208);
        masm.xorpd(temp3, temp3);
        masm.pinsrw(temp3, gpr1, 3);
        masm.movdqu(temp4, temp3);
        masm.addsd(temp3, temp1);
        masm.subsd(temp4, temp3);
        masm.addsd(temp1, temp4);
        masm.pextrw(gpr1, temp1, 3);
        masm.andl(gpr1, 32752);
        masm.jcc(ConditionFlag.NotEqual, bb41);

        masm.movdl(gpr1, temp3);
        masm.andl(gpr1, 1);
        masm.jcc(ConditionFlag.Equal, bb41);

        masm.bind(bb43);
        masm.movdqu(dest, temp10);
        masm.testl(gpr3, Integer.MIN_VALUE);
        masm.jcc(ConditionFlag.NotEqual, bb44);

        masm.jmp(bb56);

        masm.bind(bb42);
        masm.movdl(gpr1, temp8);
        masm.testl(gpr1, 1);
        masm.jcc(ConditionFlag.NotEqual, bb43);

        masm.bind(bb41);
        masm.testl(gpr3, Integer.MIN_VALUE);
        masm.jcc(ConditionFlag.Equal, bb22);

        masm.xorpd(dest, dest);

        masm.bind(bb44);
        masm.movl(gpr1, 16368);
        masm.xorpd(temp1, temp1);
        masm.pinsrw(temp1, gpr1, 3);
        masm.divsd(temp1, dest);
        masm.movdqu(dest, temp1);
        masm.jmp(bb56);

        masm.bind(bb12);
        masm.pextrw(gpr1, temp10, 3);
        masm.pextrw(gpr4, temp8, 3);
        masm.movl(gpr3, 32752);
        masm.andl(gpr3, gpr4);
        masm.cmpl(gpr3, 32752);
        masm.jcc(ConditionFlag.Equal, bb45);

        masm.andl(gpr1, 32752);
        masm.subl(gpr1, 16368);
        masm.xorl(gpr4, gpr1);
        masm.testl(gpr4, 32768);
        masm.jcc(ConditionFlag.NotEqual, bb46);

        masm.bind(bb47);
        masm.movl(gpr1, 32736);
        masm.pinsrw(dest, gpr1, 3);
        masm.shrl(gpr5, 16);
        masm.orl(gpr1, gpr5);
        masm.pinsrw(temp1, gpr1, 3);
        masm.mulsd(dest, temp1);

        masm.bind(bb14);
        masm.jmp(bb56);

        masm.bind(bb46);
        masm.movl(gpr1, 16);
        masm.pinsrw(dest, gpr1, 3);
        masm.mulsd(dest, dest);
        masm.testl(gpr3, Integer.MIN_VALUE);
        masm.jcc(ConditionFlag.Equal, bb48);

        masm.movq(gpr2, 0x8000000000000000L);
        masm.movdq(temp2, gpr2);
        masm.xorpd(dest, temp2);

        masm.bind(bb48);
        masm.jmp(bb56);

        masm.bind(bb13);
        masm.pextrw(gpr3, temp5, 3);
        masm.pextrw(gpr4, temp4, 3);
        masm.movl(gpr1, -1);
        masm.andl(gpr3, 32752);
        masm.subl(gpr3, 16368);
        masm.andl(gpr4, 32752);
        masm.addl(gpr4, gpr3);
        masm.movl(gpr3, -31);
        masm.sarl(gpr4, 4);
        masm.subl(gpr3, gpr4);
        masm.jcc(ConditionFlag.LessEqual, bb49);

        masm.cmpl(gpr3, 20);
        masm.jcc(ConditionFlag.Above, bb50);

        masm.shll(gpr1);

        masm.bind(bb49);
        masm.movdl(dest, gpr1);
        masm.psllq(dest, 32);
        masm.pand(dest, temp5);
        masm.subsd(temp5, dest);
        masm.addsd(temp5, temp1);
        masm.mulsd(dest, temp4);
        masm.mulsd(temp5, temp4);
        masm.addsd(dest, temp5);

        masm.bind(bb50);
        masm.jmp(bb48);

        masm.bind(bb2);
        masm.pextrw(gpr3, temp8, 3);
        masm.movl(gpr4, Integer.MIN_VALUE);
        masm.movdl(temp1, gpr4);
        masm.xorpd(temp7, temp7);
        masm.paddd(dest, temp4);
        masm.movdl(gpr4, dest);
        masm.psllq(dest, 29);
        masm.paddq(temp1, temp3);
        masm.pand(temp5, temp1);
        masm.andl(gpr3, 32752);
        masm.cmpl(gpr3, 16560);
        masm.jcc(ConditionFlag.Less, bb3);

        masm.leaq(gpr7, externalAddress(lTblPowPtr));
        masm.leaq(gpr8, externalAddress(coeffHPtr));
        masm.movdqu(temp4, new AMD64Address(gpr8, 0));         // 0x00000000,
                                                               // 0xbfd61a00,
                                                               // 0x00000000,
                                                               // 0xbf5dabe1
        masm.pand(dest, temp6);
        masm.subsd(temp3, temp5);
        masm.addl(gpr1, 16351);
        masm.shrl(gpr1, 4);
        masm.subl(gpr1, 1022);
        masm.cvtsi2sdl(temp7, gpr1);
        masm.mulpd(temp5, dest);
        masm.mulsd(temp3, dest);
        masm.subsd(temp5, temp9);
        masm.pshufd(temp1, temp4, 0xE);
        masm.pshufd(temp2, temp3, 0x44);
        masm.unpcklpd(temp5, temp3);
        masm.addsd(temp3, temp5);
        masm.andl(gpr4, 16760832);
        masm.shrl(gpr4, 10);
        masm.addpd(temp7, new AMD64Address(gpr7, gpr4, Scale.Times1, -3648));
        masm.movdqu(temp6, temp4);
        masm.mulsd(temp4, temp5);
        masm.movdqu(dest, temp1);
        masm.mulsd(dest, temp5);
        masm.mulsd(temp6, temp2);
        masm.mulsd(temp1, temp2);
        masm.movdqu(temp2, temp5);
        masm.mulsd(temp4, temp5);
        masm.addsd(temp5, dest);
        masm.movdqu(dest, temp7);
        masm.addsd(temp2, temp3);
        masm.addsd(temp7, temp5);
        masm.mulsd(temp6, temp2);
        masm.subsd(dest, temp7);
        masm.movdqu(temp2, temp7);
        masm.addsd(temp7, temp4);
        masm.addsd(dest, temp5);
        masm.subsd(temp2, temp7);
        masm.addsd(temp4, temp2);
        masm.pshufd(temp2, temp5, 0xEE);
        masm.movdqu(temp5, temp7);
        masm.addsd(temp7, temp2);
        masm.addsd(temp4, dest);
        masm.leaq(gpr8, externalAddress(coeffPowPtr));
        masm.movdqu(dest, new AMD64Address(gpr8, 0));        // 0x6dc96112,
                                                             // 0xbf836578,
                                                             // 0xee241472,
                                                             // 0xbf9b0301
        masm.subsd(temp5, temp7);
        masm.addsd(temp6, temp4);
        masm.movdqu(temp4, temp7);
        masm.addsd(temp5, temp2);
        masm.addsd(temp7, temp1);
        masm.movdqu(temp2, new AMD64Address(gpr8, 64));      // 0x486ececc,
                                                             // 0x3fc4635e,
                                                             // 0x161bb241,
                                                             // 0xbf5dabe1
        masm.subsd(temp4, temp7);
        masm.addsd(temp6, temp5);
        masm.addsd(temp4, temp1);
        masm.pshufd(temp5, temp7, 0xEE);
        masm.movapd(temp1, temp7);
        masm.addsd(temp7, temp5);
        masm.subsd(temp1, temp7);
        masm.addsd(temp1, temp5);
        masm.movdqu(temp5, new AMD64Address(gpr8, 80));      // 0x9f95985a,
                                                             // 0xbfb528db,
                                                             // 0xf8b5787d,
                                                             // 0x3ef2531e
        masm.pshufd(temp3, temp3, 0x44);
        masm.addsd(temp6, temp4);
        masm.addsd(temp6, temp1);
        masm.movdqu(temp1, new AMD64Address(gpr8, 32));      // 0x9f95985a,
                                                             // 0xbfb528db,
                                                             // 0xb3841d2a,
                                                             // 0xbfd619b6
        masm.mulpd(dest, temp3);
        masm.mulpd(temp2, temp3);
        masm.pshufd(temp4, temp3, 0x44);
        masm.mulpd(temp3, temp3);
        masm.addpd(dest, temp1);
        masm.addpd(temp5, temp2);
        masm.mulsd(temp4, temp3);
        masm.movq(temp2, externalAddress(highmaskLogXPtr));  // 0xf8000000,
                                                             // 0xffffffff
        masm.mulpd(temp3, temp3);
        masm.movdqu(temp1, temp8);
        masm.pextrw(gpr3, temp8, 3);
        masm.mulpd(dest, temp4);
        masm.pextrw(gpr1, temp7, 3);
        masm.mulpd(temp5, temp4);
        masm.mulpd(dest, temp3);
        masm.leaq(gpr8, externalAddress(highmaskYPtr));
        masm.movq(temp4, new AMD64Address(gpr8, 8));         // 0x00000000,
                                                             // 0xffffffff
        masm.pand(temp2, temp7);
        masm.addsd(temp5, temp6);
        masm.subsd(temp7, temp2);
        masm.addpd(temp5, dest);
        masm.andl(gpr1, 32752);
        masm.subl(gpr1, 16368);
        masm.andl(gpr3, 32752);
        masm.cmpl(gpr3, 32752);
        masm.jcc(ConditionFlag.Equal, bb45);

        masm.addl(gpr3, gpr1);
        masm.cmpl(gpr3, 16576);
        masm.jcc(ConditionFlag.AboveEqual, bb51);

        masm.pshufd(dest, temp5, 0xEE);
        masm.pand(temp4, temp1);
        masm.movdqu(temp3, temp1);
        masm.addsd(temp5, dest);
        masm.subsd(temp1, temp4);
        masm.xorpd(temp6, temp6);
        masm.movl(gpr4, 17080);
        masm.pinsrw(temp6, gpr4, 3);
        masm.addsd(temp7, temp5);
        masm.mulsd(temp4, temp2);
        masm.mulsd(temp1, temp2);
        masm.movdqu(temp5, temp6);
        masm.mulsd(temp3, temp7);
        masm.addsd(temp6, temp4);
        masm.addsd(temp1, temp3);
        masm.leaq(gpr8, externalAddress(eCoeffPtr));
        masm.movdqu(temp7, new AMD64Address(gpr8, 0));       // 0xe78a6731,
                                                             // 0x3f55d87f,
                                                             // 0xd704a0c0,
                                                             // 0x3fac6b08
        masm.movdl(gpr4, temp6);
        masm.subsd(temp6, temp5);
        masm.leaq(gpr7, externalAddress(tExpPtr));
        masm.movl(gpr3, gpr4);
        masm.andl(gpr4, 255);
        masm.addl(gpr4, gpr4);
        masm.movdqu(temp5, new AMD64Address(gpr7, gpr4, Scale.Times8, 0));
        masm.movdqu(temp3, new AMD64Address(gpr8, 16));      // 0x6fba4e77,
                                                             // 0x3f83b2ab,
                                                             // 0xff82c58f,
                                                             // 0x3fcebfbd
        masm.movq(temp2, new AMD64Address(gpr8, 32));        // 0xfefa39ef,
                                                             // 0x3fe62e42
        masm.subsd(temp4, temp6);
        masm.addsd(temp4, temp1);
        masm.pextrw(gpr4, temp6, 3);
        masm.shrl(gpr3, 8);
        masm.movl(gpr1, gpr3);
        masm.shrl(gpr3, 1);
        masm.subl(gpr1, gpr3);
        masm.shll(gpr3, 20);
        masm.movdl(temp6, gpr3);
        masm.pshufd(dest, temp4, 0x44);
        masm.pshufd(temp1, temp4, 0x44);
        masm.mulpd(dest, dest);
        masm.mulpd(temp7, temp1);
        masm.pshufd(temp6, temp6, 0x11);
        masm.mulsd(temp2, temp4);
        masm.andl(gpr4, 32767);
        masm.cmpl(gpr4, 16529);
        masm.jcc(ConditionFlag.Above, bb12);

        masm.mulsd(dest, dest);
        masm.paddd(temp5, temp6);
        masm.addpd(temp3, temp7);
        masm.mulsd(temp2, temp5);
        masm.pshufd(temp6, temp5, 0xEE);
        masm.mulpd(dest, temp3);
        masm.addsd(temp2, temp6);
        masm.pshufd(temp3, dest, 0xEE);
        masm.addl(gpr1, 1023);
        masm.shll(gpr1, 20);
        masm.orl(gpr1, gpr5);
        masm.movdl(temp4, gpr1);
        masm.mulsd(dest, temp5);
        masm.mulsd(temp3, temp5);
        masm.addsd(dest, temp2);
        masm.psllq(temp4, 32);
        masm.addsd(dest, temp3);
        masm.movdqu(temp1, dest);
        masm.addsd(dest, temp5);
        masm.mulsd(dest, temp4);
        masm.pextrw(gpr1, dest, 3);
        masm.andl(gpr1, 32752);
        masm.jcc(ConditionFlag.Equal, bb13);

        masm.cmpl(gpr1, 32752);
        masm.jcc(ConditionFlag.Equal, bb14);

        masm.jmp(bb56);

        masm.bind(bb45);
        masm.movdqu(dest, temp10);
        masm.xorpd(temp2, temp2);
        masm.movl(gpr1, 49136);
        masm.pinsrw(temp2, gpr1, 3);
        masm.addsd(temp2, dest);
        masm.pextrw(gpr1, temp2, 3);
        masm.cmpl(gpr1, 0);
        masm.jcc(ConditionFlag.NotEqual, bb53);

        masm.xorpd(dest, dest);
        masm.movl(gpr1, 32760);
        masm.pinsrw(dest, gpr1, 3);
        masm.jmp(bb56);

        masm.bind(bb53);
        masm.movdqu(temp1, temp8);
        masm.movdl(gpr4, temp1);
        masm.movdqu(temp3, temp1);
        masm.psrlq(temp3, 20);
        masm.movdl(gpr3, temp3);
        masm.orl(gpr3, gpr4);
        masm.jcc(ConditionFlag.Equal, bb54);

        masm.addsd(temp1, temp1);
        masm.movdqu(dest, temp1);
        masm.jmp(bb56);

        masm.bind(bb51);
        masm.pextrw(gpr1, temp1, 3);
        masm.pextrw(gpr3, temp2, 3);
        masm.xorl(gpr1, gpr3);
        masm.testl(gpr1, 32768);
        masm.jcc(ConditionFlag.Equal, bb47);

        masm.jmp(bb46);

        masm.bind(bb54);
        masm.pextrw(gpr1, dest, 3);
        masm.andl(gpr1, 32752);
        masm.pextrw(gpr4, temp1, 3);
        masm.xorpd(dest, dest);
        masm.subl(gpr1, 16368);
        masm.xorl(gpr1, gpr4);
        masm.testl(gpr1, 32768);
        masm.jcc(ConditionFlag.Equal, bb55);

        masm.jmp(bb56);

        masm.bind(bb55);
        masm.movl(gpr4, 32752);
        masm.pinsrw(dest, gpr4, 3);
        masm.jmp(bb56);

        masm.bind(bb56);
    }
}
