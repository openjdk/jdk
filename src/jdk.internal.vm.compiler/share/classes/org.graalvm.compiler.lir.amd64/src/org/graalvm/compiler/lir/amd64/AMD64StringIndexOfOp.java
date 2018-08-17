/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.Value;

/**
  */
@Opcode("AMD64_STRING_INDEX_OF")
public final class AMD64StringIndexOfOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64StringIndexOfOp> TYPE = LIRInstructionClass.create(AMD64StringIndexOfOp.class);

    @Def({REG}) protected Value resultValue;
    @Alive({REG}) protected Value charPtr1Value;
    @Alive({REG}) protected Value charPtr2Value;
    @Use({REG}) protected RegisterValue cnt1Value;
    @Temp({REG}) protected RegisterValue cnt1ValueT;
    @Use({REG}) protected RegisterValue cnt2Value;
    @Temp({REG}) protected RegisterValue cnt2ValueT;
    @Temp({REG}) protected Value temp1;
    @Temp({REG, ILLEGAL}) protected Value vectorTemp1;

    private final int intCnt2;

    private final int vmPageSize;

    public AMD64StringIndexOfOp(LIRGeneratorTool tool, Value result, Value charPtr1, Value charPtr2, RegisterValue cnt1, RegisterValue cnt2, RegisterValue temp1, RegisterValue vectorTemp1,
                    int intCnt2, int vmPageSize) {
        super(TYPE);
        assert ((AMD64) tool.target().arch).getFeatures().contains(CPUFeature.SSE4_2);
        resultValue = result;
        charPtr1Value = charPtr1;
        charPtr2Value = charPtr2;
        /*
         * The count values are inputs but are also killed like temporaries so need both Use and
         * Temp annotations, which will only work with fixed registers.
         */
        cnt1Value = cnt1;
        cnt1ValueT = cnt1;
        cnt2Value = cnt2;
        cnt2ValueT = cnt2;
        assert asRegister(cnt1).equals(rdx) && asRegister(cnt2).equals(rax) && asRegister(temp1).equals(rcx) : "fixed register usage required";

        this.temp1 = temp1;
        this.vectorTemp1 = vectorTemp1;
        this.intCnt2 = intCnt2;
        this.vmPageSize = vmPageSize;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register charPtr1 = asRegister(charPtr1Value);
        Register charPtr2 = asRegister(charPtr2Value);
        Register cnt1 = asRegister(cnt1Value);
        Register cnt2 = asRegister(cnt2Value);
        Register result = asRegister(resultValue);
        Register vec = asRegister(vectorTemp1);
        Register tmp = asRegister(temp1);
        if (intCnt2 >= 8) {
            // IndexOf for constant substrings with size >= 8 chars which don't need to be loaded
            // through stack.
            stringIndexofC8(masm, charPtr1, charPtr2, cnt1, cnt2, result, vec, tmp);
        } else {
            // Small strings are loaded through stack if they cross page boundary.
            stringIndexOf(masm, charPtr1, charPtr2, cnt1, cnt2, result, vec, tmp);
        }
    }

    private void stringIndexofC8(AMD64MacroAssembler masm, Register charPtr1, Register charPtr2, Register cnt1, Register cnt2, Register result, Register vec, Register tmp) {
        // assert(UseSSE42Intrinsics, "SSE4.2 is required");

        // This method uses pcmpestri inxtruction with bound registers
        // inputs:
        // xmm - substring
        // rax - substring length (elements count)
        // mem - scanned string
        // rdx - string length (elements count)
        // 0xd - mode: 1100 (substring search) + 01 (unsigned shorts)
        // outputs:
        // rcx - matched index in string
        assert cnt1.equals(rdx) && cnt2.equals(rax) && tmp.equals(rcx) : "pcmpestri";

        Label reloadSubstr = new Label();
        Label scanToSubstr = new Label();
        Label scanSubstr = new Label();
        Label retFound = new Label();
        Label retNotFound = new Label();
        Label exit = new Label();
        Label foundSubstr = new Label();
        Label matchSubstrHead = new Label();
        Label reloadStr = new Label();
        Label foundCandidate = new Label();

        // Note, inline_string_indexOf() generates checks:
        // if (substr.count > string.count) return -1;
        // if (substr.count == 0) return 0;
        assert intCnt2 >= 8 : "this code isused only for cnt2 >= 8 chars";

        // Load substring.
        masm.movdqu(vec, new AMD64Address(charPtr2, 0));
        masm.movl(cnt2, intCnt2);
        masm.movq(result, charPtr1); // string addr

        if (intCnt2 > 8) {
            masm.jmpb(scanToSubstr);

            // Reload substr for rescan, this code
            // is executed only for large substrings (> 8 chars)
            masm.bind(reloadSubstr);
            masm.movdqu(vec, new AMD64Address(charPtr2, 0));
            masm.negq(cnt2); // Jumped here with negative cnt2, convert to positive

            masm.bind(reloadStr);
            // We came here after the beginning of the substring was
            // matched but the rest of it was not so we need to search
            // again. Start from the next element after the previous match.

            // cnt2 is number of substring reminding elements and
            // cnt1 is number of string reminding elements when cmp failed.
            // Restored cnt1 = cnt1 - cnt2 + int_cnt2
            masm.subl(cnt1, cnt2);
            masm.addl(cnt1, intCnt2);
            masm.movl(cnt2, intCnt2); // Now restore cnt2

            masm.decrementl(cnt1, 1);     // Shift to next element
            masm.cmpl(cnt1, cnt2);
            masm.jccb(ConditionFlag.Negative, retNotFound);  // Left less then substring

            masm.addq(result, 2);

        } // (int_cnt2 > 8)

        // Scan string for start of substr in 16-byte vectors
        masm.bind(scanToSubstr);
        masm.pcmpestri(vec, new AMD64Address(result, 0), 0x0d);
        masm.jccb(ConditionFlag.Below, foundCandidate);   // CF == 1
        masm.subl(cnt1, 8);
        masm.jccb(ConditionFlag.LessEqual, retNotFound); // Scanned full string
        masm.cmpl(cnt1, cnt2);
        masm.jccb(ConditionFlag.Negative, retNotFound);  // Left less then substring
        masm.addq(result, 16);
        masm.jmpb(scanToSubstr);

        // Found a potential substr
        masm.bind(foundCandidate);
        // Matched whole vector if first element matched (tmp(rcx) == 0).
        if (intCnt2 == 8) {
            masm.jccb(ConditionFlag.Overflow, retFound);    // OF == 1
        } else { // int_cnt2 > 8
            masm.jccb(ConditionFlag.Overflow, foundSubstr);
        }
        // After pcmpestri tmp(rcx) contains matched element index
        // Compute start addr of substr
        masm.leaq(result, new AMD64Address(result, tmp, Scale.Times2, 0));

        // Make sure string is still long enough
        masm.subl(cnt1, tmp);
        masm.cmpl(cnt1, cnt2);
        if (intCnt2 == 8) {
            masm.jccb(ConditionFlag.GreaterEqual, scanToSubstr);
        } else { // int_cnt2 > 8
            masm.jccb(ConditionFlag.GreaterEqual, matchSubstrHead);
        }
        // Left less then substring.

        masm.bind(retNotFound);
        masm.movl(result, -1);
        masm.jmpb(exit);

        if (intCnt2 > 8) {
            // This code is optimized for the case when whole substring
            // is matched if its head is matched.
            masm.bind(matchSubstrHead);
            masm.pcmpestri(vec, new AMD64Address(result, 0), 0x0d);
            // Reload only string if does not match
            masm.jccb(ConditionFlag.NoOverflow, reloadStr); // OF == 0

            Label contScanSubstr = new Label();
            // Compare the rest of substring (> 8 chars).
            masm.bind(foundSubstr);
            // First 8 chars are already matched.
            masm.negq(cnt2);
            masm.addq(cnt2, 8);

            masm.bind(scanSubstr);
            masm.subl(cnt1, 8);
            masm.cmpl(cnt2, -8); // Do not read beyond substring
            masm.jccb(ConditionFlag.LessEqual, contScanSubstr);
            // Back-up strings to avoid reading beyond substring:
            // cnt1 = cnt1 - cnt2 + 8
            masm.addl(cnt1, cnt2); // cnt2 is negative
            masm.addl(cnt1, 8);
            masm.movl(cnt2, 8);
            masm.negq(cnt2);
            masm.bind(contScanSubstr);
            if (intCnt2 < 1024 * 1024 * 1024) {
                masm.movdqu(vec, new AMD64Address(charPtr2, cnt2, Scale.Times2, intCnt2 * 2));
                masm.pcmpestri(vec, new AMD64Address(result, cnt2, Scale.Times2, intCnt2 * 2), 0x0d);
            } else {
                // calculate index in register to avoid integer overflow (int_cnt2*2)
                masm.movl(tmp, intCnt2);
                masm.addq(tmp, cnt2);
                masm.movdqu(vec, new AMD64Address(charPtr2, tmp, Scale.Times2, 0));
                masm.pcmpestri(vec, new AMD64Address(result, tmp, Scale.Times2, 0), 0x0d);
            }
            // Need to reload strings pointers if not matched whole vector
            masm.jcc(ConditionFlag.NoOverflow, reloadSubstr); // OF == 0
            masm.addq(cnt2, 8);
            masm.jcc(ConditionFlag.Negative, scanSubstr);
            // Fall through if found full substring

        } // (int_cnt2 > 8)

        masm.bind(retFound);
        // Found result if we matched full small substring.
        // Compute substr offset
        masm.subq(result, charPtr1);
        masm.shrl(result, 1); // index
        masm.bind(exit);
    }

    private void stringIndexOf(AMD64MacroAssembler masm, Register charPtr1, Register charPtr2, Register cnt1, Register cnt2, Register result, Register vec, Register tmp) {
        //
        // int_cnt2 is length of small (< 8 chars) constant substring
        // or (-1) for non constant substring in which case its length
        // is in cnt2 register.
        //
        // Note, inline_string_indexOf() generates checks:
        // if (substr.count > string.count) return -1;
        // if (substr.count == 0) return 0;
        //
        assert intCnt2 == -1 || (0 < intCnt2 && intCnt2 < 8) : "should be != 0";

        // This method uses pcmpestri instruction with bound registers
        // inputs:
        // xmm - substring
        // rax - substring length (elements count)
        // mem - scanned string
        // rdx - string length (elements count)
        // 0xd - mode: 1100 (substring search) + 01 (unsigned shorts)
        // outputs:
        // rcx - matched index in string
        assert cnt1.equals(rdx) && cnt2.equals(rax) && tmp.equals(rcx) : "pcmpestri";

        Label reloadSubstr = new Label();
        Label scanToSubstr = new Label();
        Label scanSubstr = new Label();
        Label adjustStr = new Label();
        Label retFound = new Label();
        Label retNotFound = new Label();
        Label cleanup = new Label();
        Label foundSubstr = new Label();
        Label foundCandidate = new Label();

        int wordSize = 8;
        // We don't know where these strings are located
        // and we can't read beyond them. Load them through stack.
        Label bigStrings = new Label();
        Label checkStr = new Label();
        Label copySubstr = new Label();
        Label copyStr = new Label();

        masm.movq(tmp, rsp); // save old SP

        if (intCnt2 > 0) {     // small (< 8 chars) constant substring
            if (intCnt2 == 1) {  // One char
                masm.movzwl(result, new AMD64Address(charPtr2, 0));
                masm.movdl(vec, result); // move 32 bits
            } else if (intCnt2 == 2) { // Two chars
                masm.movdl(vec, new AMD64Address(charPtr2, 0)); // move 32 bits
            } else if (intCnt2 == 4) { // Four chars
                masm.movq(vec, new AMD64Address(charPtr2, 0));  // move 64 bits
            } else { // cnt2 = { 3, 5, 6, 7 }
                // Array header size is 12 bytes in 32-bit VM
                // + 6 bytes for 3 chars == 18 bytes,
                // enough space to load vec and shift.
                masm.movdqu(vec, new AMD64Address(charPtr2, (intCnt2 * 2) - 16));
                masm.psrldq(vec, 16 - (intCnt2 * 2));
            }
        } else { // not constant substring
            masm.cmpl(cnt2, 8);
            masm.jccb(ConditionFlag.AboveEqual, bigStrings); // Both strings are big enough

            // We can read beyond string if str+16 does not cross page boundary
            // since heaps are aligned and mapped by pages.
            assert vmPageSize < 1024 * 1024 * 1024 : "default page should be small";
            masm.movl(result, charPtr2); // We need only low 32 bits
            masm.andl(result, (vmPageSize - 1));
            masm.cmpl(result, (vmPageSize - 16));
            masm.jccb(ConditionFlag.BelowEqual, checkStr);

            // Move small strings to stack to allow load 16 bytes into vec.
            masm.subq(rsp, 16);
            int stackOffset = wordSize - 2;
            masm.push(cnt2);

            masm.bind(copySubstr);
            masm.movzwl(result, new AMD64Address(charPtr2, cnt2, Scale.Times2, -2));
            masm.movw(new AMD64Address(rsp, cnt2, Scale.Times2, stackOffset), result);
            masm.decrementl(cnt2, 1);
            masm.jccb(ConditionFlag.NotZero, copySubstr);

            masm.pop(cnt2);
            masm.movq(charPtr2, rsp);  // New substring address
        } // non constant

        masm.bind(checkStr);
        masm.cmpl(cnt1, 8);
        masm.jccb(ConditionFlag.AboveEqual, bigStrings);

        // Check cross page boundary.
        masm.movl(result, charPtr1); // We need only low 32 bits
        masm.andl(result, (vmPageSize - 1));
        masm.cmpl(result, (vmPageSize - 16));
        masm.jccb(ConditionFlag.BelowEqual, bigStrings);

        masm.subq(rsp, 16);
        int stackOffset = -2;
        if (intCnt2 < 0) { // not constant
            masm.push(cnt2);
            stackOffset += wordSize;
        }
        masm.movl(cnt2, cnt1);

        masm.bind(copyStr);
        masm.movzwl(result, new AMD64Address(charPtr1, cnt2, Scale.Times2, -2));
        masm.movw(new AMD64Address(rsp, cnt2, Scale.Times2, stackOffset), result);
        masm.decrementl(cnt2, 1);
        masm.jccb(ConditionFlag.NotZero, copyStr);

        if (intCnt2 < 0) { // not constant
            masm.pop(cnt2);
        }
        masm.movq(charPtr1, rsp);  // New string address

        masm.bind(bigStrings);
        // Load substring.
        if (intCnt2 < 0) { // -1
            masm.movdqu(vec, new AMD64Address(charPtr2, 0));
            masm.push(cnt2);       // substr count
            masm.push(charPtr2);       // substr addr
            masm.push(charPtr1);       // string addr
        } else {
            // Small (< 8 chars) constant substrings are loaded already.
            masm.movl(cnt2, intCnt2);
        }
        masm.push(tmp);  // original SP
        // Finished loading

        // ========================================================
        // Start search
        //

        masm.movq(result, charPtr1); // string addr

        if (intCnt2 < 0) {  // Only for non constant substring
            masm.jmpb(scanToSubstr);

            // SP saved at sp+0
            // String saved at sp+1*wordSize
            // Substr saved at sp+2*wordSize
            // Substr count saved at sp+3*wordSize

            // Reload substr for rescan, this code
            // is executed only for large substrings (> 8 chars)
            masm.bind(reloadSubstr);
            masm.movq(charPtr2, new AMD64Address(rsp, 2 * wordSize));
            masm.movl(cnt2, new AMD64Address(rsp, 3 * wordSize));
            masm.movdqu(vec, new AMD64Address(charPtr2, 0));
            // We came here after the beginning of the substring was
            // matched but the rest of it was not so we need to search
            // again. Start from the next element after the previous match.
            masm.subq(charPtr1, result); // Restore counter
            masm.shrl(charPtr1, 1);
            masm.addl(cnt1, charPtr1);
            masm.decrementl(cnt1);   // Shift to next element
            masm.cmpl(cnt1, cnt2);
            masm.jccb(ConditionFlag.Negative, retNotFound);  // Left less then substring

            masm.addq(result, 2);
        } // non constant

        // Scan string for start of substr in 16-byte vectors
        masm.bind(scanToSubstr);
        assert cnt1.equals(rdx) && cnt2.equals(rax) && tmp.equals(rcx) : "pcmpestri";
        masm.pcmpestri(vec, new AMD64Address(result, 0), 0x0d);
        masm.jccb(ConditionFlag.Below, foundCandidate);   // CF == 1
        masm.subl(cnt1, 8);
        masm.jccb(ConditionFlag.LessEqual, retNotFound); // Scanned full string
        masm.cmpl(cnt1, cnt2);
        masm.jccb(ConditionFlag.Negative, retNotFound);  // Left less then substring
        masm.addq(result, 16);

        masm.bind(adjustStr);
        masm.cmpl(cnt1, 8); // Do not read beyond string
        masm.jccb(ConditionFlag.GreaterEqual, scanToSubstr);
        // Back-up string to avoid reading beyond string.
        masm.leaq(result, new AMD64Address(result, cnt1, Scale.Times2, -16));
        masm.movl(cnt1, 8);
        masm.jmpb(scanToSubstr);

        // Found a potential substr
        masm.bind(foundCandidate);
        // After pcmpestri tmp(rcx) contains matched element index

        // Make sure string is still long enough
        masm.subl(cnt1, tmp);
        masm.cmpl(cnt1, cnt2);
        masm.jccb(ConditionFlag.GreaterEqual, foundSubstr);
        // Left less then substring.

        masm.bind(retNotFound);
        masm.movl(result, -1);
        masm.jmpb(cleanup);

        masm.bind(foundSubstr);
        // Compute start addr of substr
        masm.leaq(result, new AMD64Address(result, tmp, Scale.Times2));

        if (intCnt2 > 0) { // Constant substring
            // Repeat search for small substring (< 8 chars)
            // from new point without reloading substring.
            // Have to check that we don't read beyond string.
            masm.cmpl(tmp, 8 - intCnt2);
            masm.jccb(ConditionFlag.Greater, adjustStr);
            // Fall through if matched whole substring.
        } else { // non constant
            assert intCnt2 == -1 : "should be != 0";
            masm.addl(tmp, cnt2);
            // Found result if we matched whole substring.
            masm.cmpl(tmp, 8);
            masm.jccb(ConditionFlag.LessEqual, retFound);

            // Repeat search for small substring (<= 8 chars)
            // from new point 'str1' without reloading substring.
            masm.cmpl(cnt2, 8);
            // Have to check that we don't read beyond string.
            masm.jccb(ConditionFlag.LessEqual, adjustStr);

            Label checkNext = new Label();
            Label contScanSubstr = new Label();
            Label retFoundLong = new Label();
            // Compare the rest of substring (> 8 chars).
            masm.movq(charPtr1, result);

            masm.cmpl(tmp, cnt2);
            // First 8 chars are already matched.
            masm.jccb(ConditionFlag.Equal, checkNext);

            masm.bind(scanSubstr);
            masm.pcmpestri(vec, new AMD64Address(charPtr1, 0), 0x0d);
            // Need to reload strings pointers if not matched whole vector
            masm.jcc(ConditionFlag.NoOverflow, reloadSubstr); // OF == 0

            masm.bind(checkNext);
            masm.subl(cnt2, 8);
            masm.jccb(ConditionFlag.LessEqual, retFoundLong); // Found full substring
            masm.addq(charPtr1, 16);
            masm.addq(charPtr2, 16);
            masm.subl(cnt1, 8);
            masm.cmpl(cnt2, 8); // Do not read beyond substring
            masm.jccb(ConditionFlag.GreaterEqual, contScanSubstr);
            // Back-up strings to avoid reading beyond substring.
            masm.leaq(charPtr2, new AMD64Address(charPtr2, cnt2, Scale.Times2, -16));
            masm.leaq(charPtr1, new AMD64Address(charPtr1, cnt2, Scale.Times2, -16));
            masm.subl(cnt1, cnt2);
            masm.movl(cnt2, 8);
            masm.addl(cnt1, 8);
            masm.bind(contScanSubstr);
            masm.movdqu(vec, new AMD64Address(charPtr2, 0));
            masm.jmpb(scanSubstr);

            masm.bind(retFoundLong);
            masm.movq(charPtr1, new AMD64Address(rsp, wordSize));
        } // non constant

        masm.bind(retFound);
        // Compute substr offset
        masm.subq(result, charPtr1);
        masm.shrl(result, 1); // index

        masm.bind(cleanup);
        masm.pop(rsp); // restore SP
    }

}
