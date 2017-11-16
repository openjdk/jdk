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
package org.graalvm.compiler.asm.amd64;

import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static org.graalvm.compiler.asm.amd64.AMD64AsmOptions.UseIncDec;
import static org.graalvm.compiler.asm.amd64.AMD64AsmOptions.UseXmmLoadAndClearUpper;
import static org.graalvm.compiler.asm.amd64.AMD64AsmOptions.UseXmmRegToRegMoveAll;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.core.common.NumUtil;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;

/**
 * This class implements commonly used X86 code patterns.
 */
public class AMD64MacroAssembler extends AMD64Assembler {

    public AMD64MacroAssembler(TargetDescription target) {
        super(target);
    }

    public final void decrementq(Register reg, int value) {
        if (value == Integer.MIN_VALUE) {
            subq(reg, value);
            return;
        }
        if (value < 0) {
            incrementq(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            decq(reg);
        } else {
            subq(reg, value);
        }
    }

    public final void decrementq(AMD64Address dst, int value) {
        if (value == Integer.MIN_VALUE) {
            subq(dst, value);
            return;
        }
        if (value < 0) {
            incrementq(dst, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            decq(dst);
        } else {
            subq(dst, value);
        }
    }

    public void incrementq(Register reg, int value) {
        if (value == Integer.MIN_VALUE) {
            addq(reg, value);
            return;
        }
        if (value < 0) {
            decrementq(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            incq(reg);
        } else {
            addq(reg, value);
        }
    }

    public final void incrementq(AMD64Address dst, int value) {
        if (value == Integer.MIN_VALUE) {
            addq(dst, value);
            return;
        }
        if (value < 0) {
            decrementq(dst, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            incq(dst);
        } else {
            addq(dst, value);
        }
    }

    public final void movptr(Register dst, AMD64Address src) {
        movq(dst, src);
    }

    public final void movptr(AMD64Address dst, Register src) {
        movq(dst, src);
    }

    public final void movptr(AMD64Address dst, int src) {
        movslq(dst, src);
    }

    public final void cmpptr(Register src1, Register src2) {
        cmpq(src1, src2);
    }

    public final void cmpptr(Register src1, AMD64Address src2) {
        cmpq(src1, src2);
    }

    public final void decrementl(Register reg) {
        decrementl(reg, 1);
    }

    public final void decrementl(Register reg, int value) {
        if (value == Integer.MIN_VALUE) {
            subl(reg, value);
            return;
        }
        if (value < 0) {
            incrementl(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            decl(reg);
        } else {
            subl(reg, value);
        }
    }

    public final void decrementl(AMD64Address dst, int value) {
        if (value == Integer.MIN_VALUE) {
            subl(dst, value);
            return;
        }
        if (value < 0) {
            incrementl(dst, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            decl(dst);
        } else {
            subl(dst, value);
        }
    }

    public final void incrementl(Register reg, int value) {
        if (value == Integer.MIN_VALUE) {
            addl(reg, value);
            return;
        }
        if (value < 0) {
            decrementl(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            incl(reg);
        } else {
            addl(reg, value);
        }
    }

    public final void incrementl(AMD64Address dst, int value) {
        if (value == Integer.MIN_VALUE) {
            addl(dst, value);
            return;
        }
        if (value < 0) {
            decrementl(dst, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && UseIncDec) {
            incl(dst);
        } else {
            addl(dst, value);
        }
    }

    public void movflt(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        if (UseXmmRegToRegMoveAll) {
            movaps(dst, src);
        } else {
            movss(dst, src);
        }
    }

    public void movflt(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        movss(dst, src);
    }

    public void movflt(AMD64Address dst, Register src) {
        assert src.getRegisterCategory().equals(AMD64.XMM);
        movss(dst, src);
    }

    public void movdbl(Register dst, Register src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM) && src.getRegisterCategory().equals(AMD64.XMM);
        if (UseXmmRegToRegMoveAll) {
            movapd(dst, src);
        } else {
            movsd(dst, src);
        }
    }

    public void movdbl(Register dst, AMD64Address src) {
        assert dst.getRegisterCategory().equals(AMD64.XMM);
        if (UseXmmLoadAndClearUpper) {
            movsd(dst, src);
        } else {
            movlpd(dst, src);
        }
    }

    public void movdbl(AMD64Address dst, Register src) {
        assert src.getRegisterCategory().equals(AMD64.XMM);
        movsd(dst, src);
    }

    /**
     * Non-atomic write of a 64-bit constant to memory. Do not use if the address might be a
     * volatile field!
     */
    public final void movlong(AMD64Address dst, long src) {
        if (NumUtil.isInt(src)) {
            AMD64MIOp.MOV.emit(this, OperandSize.QWORD, dst, (int) src);
        } else {
            AMD64Address high = new AMD64Address(dst.getBase(), dst.getIndex(), dst.getScale(), dst.getDisplacement() + 4);
            movl(dst, (int) (src & 0xFFFFFFFF));
            movl(high, (int) (src >> 32));
        }

    }

    public final void setl(ConditionFlag cc, Register dst) {
        setb(cc, dst);
        movzbl(dst, dst);
    }

    public final void setq(ConditionFlag cc, Register dst) {
        setb(cc, dst);
        movzbq(dst, dst);
    }

    public final void flog(Register dest, Register value, boolean base10) {
        if (base10) {
            fldlg2();
        } else {
            fldln2();
        }
        AMD64Address tmp = trigPrologue(value);
        fyl2x();
        trigEpilogue(dest, tmp);
    }

    public final void fsin(Register dest, Register value) {
        AMD64Address tmp = trigPrologue(value);
        fsin();
        trigEpilogue(dest, tmp);
    }

    public final void fcos(Register dest, Register value) {
        AMD64Address tmp = trigPrologue(value);
        fcos();
        trigEpilogue(dest, tmp);
    }

    public final void ftan(Register dest, Register value) {
        AMD64Address tmp = trigPrologue(value);
        fptan();
        fstp(0); // ftan pushes 1.0 in addition to the actual result, pop
        trigEpilogue(dest, tmp);
    }

    public final void fpop() {
        ffree(0);
        fincstp();
    }

    private AMD64Address trigPrologue(Register value) {
        assert value.getRegisterCategory().equals(AMD64.XMM);
        AMD64Address tmp = new AMD64Address(AMD64.rsp);
        subq(AMD64.rsp, AMD64Kind.DOUBLE.getSizeInBytes());
        movdbl(tmp, value);
        fldd(tmp);
        return tmp;
    }

    private void trigEpilogue(Register dest, AMD64Address tmp) {
        assert dest.getRegisterCategory().equals(AMD64.XMM);
        fstpd(tmp);
        movdbl(dest, tmp);
        addq(AMD64.rsp, AMD64Kind.DOUBLE.getSizeInBytes());
    }

    // IndexOf for constant substrings with size >= 8 chars
    // which don't need to be loaded through stack.
    public void stringIndexofC8(Register str1, Register str2,
                    Register cnt1, Register cnt2,
                    int intCnt2, Register result,
                    Register vec, Register tmp) {
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
        movdqu(vec, new AMD64Address(str2, 0));
        movl(cnt2, intCnt2);
        movq(result, str1); // string addr

        if (intCnt2 > 8) {
            jmpb(scanToSubstr);

            // Reload substr for rescan, this code
            // is executed only for large substrings (> 8 chars)
            bind(reloadSubstr);
            movdqu(vec, new AMD64Address(str2, 0));
            negq(cnt2); // Jumped here with negative cnt2, convert to positive

            bind(reloadStr);
            // We came here after the beginning of the substring was
            // matched but the rest of it was not so we need to search
            // again. Start from the next element after the previous match.

            // cnt2 is number of substring reminding elements and
            // cnt1 is number of string reminding elements when cmp failed.
            // Restored cnt1 = cnt1 - cnt2 + int_cnt2
            subl(cnt1, cnt2);
            addl(cnt1, intCnt2);
            movl(cnt2, intCnt2); // Now restore cnt2

            decrementl(cnt1, 1);     // Shift to next element
            cmpl(cnt1, cnt2);
            jccb(ConditionFlag.Negative, retNotFound);  // Left less then substring

            addq(result, 2);

        } // (int_cnt2 > 8)

        // Scan string for start of substr in 16-byte vectors
        bind(scanToSubstr);
        pcmpestri(vec, new AMD64Address(result, 0), 0x0d);
        jccb(ConditionFlag.Below, foundCandidate);   // CF == 1
        subl(cnt1, 8);
        jccb(ConditionFlag.LessEqual, retNotFound); // Scanned full string
        cmpl(cnt1, cnt2);
        jccb(ConditionFlag.Negative, retNotFound);  // Left less then substring
        addq(result, 16);
        jmpb(scanToSubstr);

        // Found a potential substr
        bind(foundCandidate);
        // Matched whole vector if first element matched (tmp(rcx) == 0).
        if (intCnt2 == 8) {
            jccb(ConditionFlag.Overflow, retFound);    // OF == 1
        } else { // int_cnt2 > 8
            jccb(ConditionFlag.Overflow, foundSubstr);
        }
        // After pcmpestri tmp(rcx) contains matched element index
        // Compute start addr of substr
        leaq(result, new AMD64Address(result, tmp, Scale.Times2, 0));

        // Make sure string is still long enough
        subl(cnt1, tmp);
        cmpl(cnt1, cnt2);
        if (intCnt2 == 8) {
            jccb(ConditionFlag.GreaterEqual, scanToSubstr);
        } else { // int_cnt2 > 8
            jccb(ConditionFlag.GreaterEqual, matchSubstrHead);
        }
        // Left less then substring.

        bind(retNotFound);
        movl(result, -1);
        jmpb(exit);

        if (intCnt2 > 8) {
            // This code is optimized for the case when whole substring
            // is matched if its head is matched.
            bind(matchSubstrHead);
            pcmpestri(vec, new AMD64Address(result, 0), 0x0d);
            // Reload only string if does not match
            jccb(ConditionFlag.NoOverflow, reloadStr); // OF == 0

            Label contScanSubstr = new Label();
            // Compare the rest of substring (> 8 chars).
            bind(foundSubstr);
            // First 8 chars are already matched.
            negq(cnt2);
            addq(cnt2, 8);

            bind(scanSubstr);
            subl(cnt1, 8);
            cmpl(cnt2, -8); // Do not read beyond substring
            jccb(ConditionFlag.LessEqual, contScanSubstr);
            // Back-up strings to avoid reading beyond substring:
            // cnt1 = cnt1 - cnt2 + 8
            addl(cnt1, cnt2); // cnt2 is negative
            addl(cnt1, 8);
            movl(cnt2, 8);
            negq(cnt2);
            bind(contScanSubstr);
            if (intCnt2 < 1024 * 1024 * 1024) {
                movdqu(vec, new AMD64Address(str2, cnt2, Scale.Times2, intCnt2 * 2));
                pcmpestri(vec, new AMD64Address(result, cnt2, Scale.Times2, intCnt2 * 2), 0x0d);
            } else {
                // calculate index in register to avoid integer overflow (int_cnt2*2)
                movl(tmp, intCnt2);
                addq(tmp, cnt2);
                movdqu(vec, new AMD64Address(str2, tmp, Scale.Times2, 0));
                pcmpestri(vec, new AMD64Address(result, tmp, Scale.Times2, 0), 0x0d);
            }
            // Need to reload strings pointers if not matched whole vector
            jcc(ConditionFlag.NoOverflow, reloadSubstr); // OF == 0
            addq(cnt2, 8);
            jcc(ConditionFlag.Negative, scanSubstr);
            // Fall through if found full substring

        } // (int_cnt2 > 8)

        bind(retFound);
        // Found result if we matched full small substring.
        // Compute substr offset
        subq(result, str1);
        shrl(result, 1); // index
        bind(exit);

    } // string_indexofC8

    // Small strings are loaded through stack if they cross page boundary.
    public void stringIndexOf(Register str1, Register str2,
                    Register cnt1, Register cnt2,
                    int intCnt2, Register result,
                    Register vec, Register tmp, int vmPageSize) {
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

        movq(tmp, rsp); // save old SP

        if (intCnt2 > 0) {     // small (< 8 chars) constant substring
            if (intCnt2 == 1) {  // One char
                movzwl(result, new AMD64Address(str2, 0));
                movdl(vec, result); // move 32 bits
            } else if (intCnt2 == 2) { // Two chars
                movdl(vec, new AMD64Address(str2, 0)); // move 32 bits
            } else if (intCnt2 == 4) { // Four chars
                movq(vec, new AMD64Address(str2, 0));  // move 64 bits
            } else { // cnt2 = { 3, 5, 6, 7 }
                // Array header size is 12 bytes in 32-bit VM
                // + 6 bytes for 3 chars == 18 bytes,
                // enough space to load vec and shift.
                movdqu(vec, new AMD64Address(str2, (intCnt2 * 2) - 16));
                psrldq(vec, 16 - (intCnt2 * 2));
            }
        } else { // not constant substring
            cmpl(cnt2, 8);
            jccb(ConditionFlag.AboveEqual, bigStrings); // Both strings are big enough

            // We can read beyond string if str+16 does not cross page boundary
            // since heaps are aligned and mapped by pages.
            assert vmPageSize < 1024 * 1024 * 1024 : "default page should be small";
            movl(result, str2); // We need only low 32 bits
            andl(result, (vmPageSize - 1));
            cmpl(result, (vmPageSize - 16));
            jccb(ConditionFlag.BelowEqual, checkStr);

            // Move small strings to stack to allow load 16 bytes into vec.
            subq(rsp, 16);
            int stackOffset = wordSize - 2;
            push(cnt2);

            bind(copySubstr);
            movzwl(result, new AMD64Address(str2, cnt2, Scale.Times2, -2));
            movw(new AMD64Address(rsp, cnt2, Scale.Times2, stackOffset), result);
            decrementl(cnt2, 1);
            jccb(ConditionFlag.NotZero, copySubstr);

            pop(cnt2);
            movq(str2, rsp);  // New substring address
        } // non constant

        bind(checkStr);
        cmpl(cnt1, 8);
        jccb(ConditionFlag.AboveEqual, bigStrings);

        // Check cross page boundary.
        movl(result, str1); // We need only low 32 bits
        andl(result, (vmPageSize - 1));
        cmpl(result, (vmPageSize - 16));
        jccb(ConditionFlag.BelowEqual, bigStrings);

        subq(rsp, 16);
        int stackOffset = -2;
        if (intCnt2 < 0) { // not constant
            push(cnt2);
            stackOffset += wordSize;
        }
        movl(cnt2, cnt1);

        bind(copyStr);
        movzwl(result, new AMD64Address(str1, cnt2, Scale.Times2, -2));
        movw(new AMD64Address(rsp, cnt2, Scale.Times2, stackOffset), result);
        decrementl(cnt2, 1);
        jccb(ConditionFlag.NotZero, copyStr);

        if (intCnt2 < 0) { // not constant
            pop(cnt2);
        }
        movq(str1, rsp);  // New string address

        bind(bigStrings);
        // Load substring.
        if (intCnt2 < 0) { // -1
            movdqu(vec, new AMD64Address(str2, 0));
            push(cnt2);       // substr count
            push(str2);       // substr addr
            push(str1);       // string addr
        } else {
            // Small (< 8 chars) constant substrings are loaded already.
            movl(cnt2, intCnt2);
        }
        push(tmp);  // original SP
        // Finished loading

        // ========================================================
        // Start search
        //

        movq(result, str1); // string addr

        if (intCnt2 < 0) {  // Only for non constant substring
            jmpb(scanToSubstr);

            // SP saved at sp+0
            // String saved at sp+1*wordSize
            // Substr saved at sp+2*wordSize
            // Substr count saved at sp+3*wordSize

            // Reload substr for rescan, this code
            // is executed only for large substrings (> 8 chars)
            bind(reloadSubstr);
            movq(str2, new AMD64Address(rsp, 2 * wordSize));
            movl(cnt2, new AMD64Address(rsp, 3 * wordSize));
            movdqu(vec, new AMD64Address(str2, 0));
            // We came here after the beginning of the substring was
            // matched but the rest of it was not so we need to search
            // again. Start from the next element after the previous match.
            subq(str1, result); // Restore counter
            shrl(str1, 1);
            addl(cnt1, str1);
            decrementl(cnt1);   // Shift to next element
            cmpl(cnt1, cnt2);
            jccb(ConditionFlag.Negative, retNotFound);  // Left less then substring

            addq(result, 2);
        } // non constant

        // Scan string for start of substr in 16-byte vectors
        bind(scanToSubstr);
        assert cnt1.equals(rdx) && cnt2.equals(rax) && tmp.equals(rcx) : "pcmpestri";
        pcmpestri(vec, new AMD64Address(result, 0), 0x0d);
        jccb(ConditionFlag.Below, foundCandidate);   // CF == 1
        subl(cnt1, 8);
        jccb(ConditionFlag.LessEqual, retNotFound); // Scanned full string
        cmpl(cnt1, cnt2);
        jccb(ConditionFlag.Negative, retNotFound);  // Left less then substring
        addq(result, 16);

        bind(adjustStr);
        cmpl(cnt1, 8); // Do not read beyond string
        jccb(ConditionFlag.GreaterEqual, scanToSubstr);
        // Back-up string to avoid reading beyond string.
        leaq(result, new AMD64Address(result, cnt1, Scale.Times2, -16));
        movl(cnt1, 8);
        jmpb(scanToSubstr);

        // Found a potential substr
        bind(foundCandidate);
        // After pcmpestri tmp(rcx) contains matched element index

        // Make sure string is still long enough
        subl(cnt1, tmp);
        cmpl(cnt1, cnt2);
        jccb(ConditionFlag.GreaterEqual, foundSubstr);
        // Left less then substring.

        bind(retNotFound);
        movl(result, -1);
        jmpb(cleanup);

        bind(foundSubstr);
        // Compute start addr of substr
        leaq(result, new AMD64Address(result, tmp, Scale.Times2));

        if (intCnt2 > 0) { // Constant substring
            // Repeat search for small substring (< 8 chars)
            // from new point without reloading substring.
            // Have to check that we don't read beyond string.
            cmpl(tmp, 8 - intCnt2);
            jccb(ConditionFlag.Greater, adjustStr);
            // Fall through if matched whole substring.
        } else { // non constant
            assert intCnt2 == -1 : "should be != 0";

            addl(tmp, cnt2);
            // Found result if we matched whole substring.
            cmpl(tmp, 8);
            jccb(ConditionFlag.LessEqual, retFound);

            // Repeat search for small substring (<= 8 chars)
            // from new point 'str1' without reloading substring.
            cmpl(cnt2, 8);
            // Have to check that we don't read beyond string.
            jccb(ConditionFlag.LessEqual, adjustStr);

            Label checkNext = new Label();
            Label contScanSubstr = new Label();
            Label retFoundLong = new Label();
            // Compare the rest of substring (> 8 chars).
            movq(str1, result);

            cmpl(tmp, cnt2);
            // First 8 chars are already matched.
            jccb(ConditionFlag.Equal, checkNext);

            bind(scanSubstr);
            pcmpestri(vec, new AMD64Address(str1, 0), 0x0d);
            // Need to reload strings pointers if not matched whole vector
            jcc(ConditionFlag.NoOverflow, reloadSubstr); // OF == 0

            bind(checkNext);
            subl(cnt2, 8);
            jccb(ConditionFlag.LessEqual, retFoundLong); // Found full substring
            addq(str1, 16);
            addq(str2, 16);
            subl(cnt1, 8);
            cmpl(cnt2, 8); // Do not read beyond substring
            jccb(ConditionFlag.GreaterEqual, contScanSubstr);
            // Back-up strings to avoid reading beyond substring.
            leaq(str2, new AMD64Address(str2, cnt2, Scale.Times2, -16));
            leaq(str1, new AMD64Address(str1, cnt2, Scale.Times2, -16));
            subl(cnt1, cnt2);
            movl(cnt2, 8);
            addl(cnt1, 8);
            bind(contScanSubstr);
            movdqu(vec, new AMD64Address(str2, 0));
            jmpb(scanSubstr);

            bind(retFoundLong);
            movq(str1, new AMD64Address(rsp, wordSize));
        } // non constant

        bind(retFound);
        // Compute substr offset
        subq(result, str1);
        shrl(result, 1); // index

        bind(cleanup);
        pop(rsp); // restore SP

    }

}
