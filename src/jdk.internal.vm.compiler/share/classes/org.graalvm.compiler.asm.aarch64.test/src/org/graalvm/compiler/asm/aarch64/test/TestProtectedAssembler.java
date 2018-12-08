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



package org.graalvm.compiler.asm.aarch64.test;

import org.graalvm.compiler.asm.AbstractAddress;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;

/**
 * Cheat so that we can test protected functions of assembler.
 */
class TestProtectedAssembler extends AArch64Assembler {

    TestProtectedAssembler(TargetDescription target) {
        super(target);
    }

    @Override
    protected void cbnz(int size, Register reg, int imm21, int pos) {
        super.cbnz(size, reg, imm21, pos);
    }

    @Override
    protected void cbz(int size, Register reg, int imm21, int pos) {
        super.cbz(size, reg, imm21, pos);
    }

    @Override
    public void ands(int size, Register dst, Register src, long bimm) {
        super.ands(size, dst, src, bimm);
    }

    @Override
    protected void b(ConditionFlag condition, int imm21) {
        super.b(condition, imm21);
    }

    @Override
    protected void b(ConditionFlag condition, int imm21, int pos) {
        super.b(condition, imm21, pos);
    }

    @Override
    protected void cbnz(int size, Register reg, int imm21) {
        super.cbnz(size, reg, imm21);
    }

    @Override
    protected void cbz(int size, Register reg, int imm21) {
        super.cbz(size, reg, imm21);
    }

    @Override
    protected void b(int imm28) {
        super.b(imm28);
    }

    @Override
    protected void b(int imm28, int pos) {
        super.b(imm28, pos);
    }

    @Override
    public void bl(int imm28) {
        super.bl(imm28);
    }

    @Override
    public void blr(Register reg) {
        super.blr(reg);
    }

    @Override
    protected void br(Register reg) {
        super.br(reg);
    }

    @Override
    public void ret(Register reg) {
        super.ret(reg);
    }

    @Override
    public void ldr(int srcSize, Register rt, AArch64Address address) {
        super.ldr(srcSize, rt, address);
    }

    @Override
    public void ldrs(int targetSize, int srcSize, Register rt, AArch64Address address) {
        super.ldrs(targetSize, srcSize, rt, address);
    }

    @Override
    public void str(int destSize, Register rt, AArch64Address address) {
        super.str(destSize, rt, address);
    }

    @Override
    protected void ldxr(int size, Register rt, Register rn) {
        super.ldxr(size, rt, rn);
    }

    @Override
    protected void stxr(int size, Register rs, Register rt, Register rn) {
        super.stxr(size, rs, rt, rn);
    }

    @Override
    protected void ldar(int size, Register rt, Register rn) {
        super.ldar(size, rt, rn);
    }

    @Override
    protected void stlr(int size, Register rt, Register rn) {
        super.stlr(size, rt, rn);
    }

    @Override
    public void ldaxr(int size, Register rt, Register rn) {
        super.ldaxr(size, rt, rn);
    }

    @Override
    public void stlxr(int size, Register rs, Register rt, Register rn) {
        super.stlxr(size, rs, rt, rn);
    }

    @Override
    public void adr(Register dst, int imm21) {
        super.adr(dst, imm21);
    }

    @Override
    protected void add(int size, Register dst, Register src, int aimm) {
        super.add(size, dst, src, aimm);
    }

    @Override
    protected void adds(int size, Register dst, Register src, int aimm) {
        super.adds(size, dst, src, aimm);
    }

    @Override
    protected void sub(int size, Register dst, Register src, int aimm) {
        super.sub(size, dst, src, aimm);
    }

    @Override
    protected void subs(int size, Register dst, Register src, int aimm) {
        super.subs(size, dst, src, aimm);
    }

    @Override
    public void and(int size, Register dst, Register src, long bimm) {
        super.and(size, dst, src, bimm);
    }

    @Override
    public void eor(int size, Register dst, Register src, long bimm) {
        super.eor(size, dst, src, bimm);
    }

    @Override
    protected void orr(int size, Register dst, Register src, long bimm) {
        super.orr(size, dst, src, bimm);
    }

    @Override
    protected void movz(int size, Register dst, int uimm16, int shiftAmt) {
        super.movz(size, dst, uimm16, shiftAmt);
    }

    @Override
    protected void movn(int size, Register dst, int uimm16, int shiftAmt) {
        super.movn(size, dst, uimm16, shiftAmt);
    }

    @Override
    protected void movk(int size, Register dst, int uimm16, int pos) {
        super.movk(size, dst, uimm16, pos);
    }

    @Override
    public void bfm(int size, Register dst, Register src, int r, int s) {
        super.bfm(size, dst, src, r, s);
    }

    @Override
    public void ubfm(int size, Register dst, Register src, int r, int s) {
        super.ubfm(size, dst, src, r, s);
    }

    @Override
    protected void sbfm(int size, Register dst, Register src, int r, int s) {
        super.sbfm(size, dst, src, r, s);
    }

    @Override
    protected void extr(int size, Register dst, Register src1, Register src2, int lsb) {
        super.extr(size, dst, src1, src2, lsb);
    }

    @Override
    public void adds(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int imm) {
        super.adds(size, dst, src1, src2, shiftType, imm);
    }

    @Override
    public void subs(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int imm) {
        super.subs(size, dst, src1, src2, shiftType, imm);
    }

    @Override
    protected void add(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int imm) {
        super.add(size, dst, src1, src2, shiftType, imm);
    }

    @Override
    protected void sub(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int imm) {
        super.sub(size, dst, src1, src2, shiftType, imm);
    }

    @Override
    public void add(int size, Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt) {
        super.add(size, dst, src1, src2, extendType, shiftAmt);
    }

    @Override
    protected void adds(int size, Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt) {
        super.adds(size, dst, src1, src2, extendType, shiftAmt);
    }

    @Override
    protected void sub(int size, Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt) {
        super.sub(size, dst, src1, src2, extendType, shiftAmt);
    }

    @Override
    public void subs(int size, Register dst, Register src1, Register src2, ExtendType extendType, int shiftAmt) {
        super.subs(size, dst, src1, src2, extendType, shiftAmt);
    }

    @Override
    protected void and(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        super.and(size, dst, src1, src2, shiftType, shiftAmt);
    }

    @Override
    protected void ands(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        super.ands(size, dst, src1, src2, shiftType, shiftAmt);
    }

    @Override
    protected void bic(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        super.bic(size, dst, src1, src2, shiftType, shiftAmt);
    }

    @Override
    protected void bics(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        super.bics(size, dst, src1, src2, shiftType, shiftAmt);
    }

    @Override
    protected void eon(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        super.eon(size, dst, src1, src2, shiftType, shiftAmt);
    }

    @Override
    protected void eor(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        super.eor(size, dst, src1, src2, shiftType, shiftAmt);
    }

    @Override
    protected void orr(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        super.orr(size, dst, src1, src2, shiftType, shiftAmt);
    }

    @Override
    protected void orn(int size, Register dst, Register src1, Register src2, ShiftType shiftType, int shiftAmt) {
        super.orn(size, dst, src1, src2, shiftType, shiftAmt);
    }

    @Override
    protected void asr(int size, Register dst, Register src1, Register src2) {
        super.asr(size, dst, src1, src2);
    }

    @Override
    protected void lsl(int size, Register dst, Register src1, Register src2) {
        super.lsl(size, dst, src1, src2);
    }

    @Override
    protected void lsr(int size, Register dst, Register src1, Register src2) {
        super.lsr(size, dst, src1, src2);
    }

    @Override
    protected void ror(int size, Register dst, Register src1, Register src2) {
        super.ror(size, dst, src1, src2);
    }

    @Override
    protected void cls(int size, Register dst, Register src) {
        super.cls(size, dst, src);
    }

    @Override
    public void clz(int size, Register dst, Register src) {
        super.clz(size, dst, src);
    }

    @Override
    public void rbit(int size, Register dst, Register src) {
        super.rbit(size, dst, src);
    }

    @Override
    public void rev(int size, Register dst, Register src) {
        super.rev(size, dst, src);
    }

    @Override
    protected void csel(int size, Register dst, Register src1, Register src2, ConditionFlag condition) {
        super.csel(size, dst, src1, src2, condition);
    }

    @Override
    protected void csneg(int size, Register dst, Register src1, Register src2, ConditionFlag condition) {
        super.csneg(size, dst, src1, src2, condition);
    }

    @Override
    protected void csinc(int size, Register dst, Register src1, Register src2, ConditionFlag condition) {
        super.csinc(size, dst, src1, src2, condition);
    }

    @Override
    protected void madd(int size, Register dst, Register src1, Register src2, Register src3) {
        super.madd(size, dst, src1, src2, src3);
    }

    @Override
    protected void msub(int size, Register dst, Register src1, Register src2, Register src3) {
        super.msub(size, dst, src1, src2, src3);
    }

    @Override
    public void sdiv(int size, Register dst, Register src1, Register src2) {
        super.sdiv(size, dst, src1, src2);
    }

    @Override
    public void udiv(int size, Register dst, Register src1, Register src2) {
        super.udiv(size, dst, src1, src2);
    }

    @Override
    public void fldr(int size, Register rt, AArch64Address address) {
        super.fldr(size, rt, address);
    }

    @Override
    public void fstr(int size, Register rt, AArch64Address address) {
        super.fstr(size, rt, address);
    }

    @Override
    protected void fmov(int size, Register dst, Register src) {
        super.fmov(size, dst, src);
    }

    @Override
    protected void fmovFpu2Cpu(int size, Register dst, Register src) {
        super.fmovFpu2Cpu(size, dst, src);
    }

    @Override
    protected void fmovCpu2Fpu(int size, Register dst, Register src) {
        super.fmovCpu2Fpu(size, dst, src);
    }

    @Override
    protected void fmov(int size, Register dst, double imm) {
        super.fmov(size, dst, imm);
    }

    @Override
    public void fcvt(int srcSize, Register dst, Register src) {
        super.fcvt(srcSize, dst, src);
    }

    @Override
    public void fcvtzs(int targetSize, int srcSize, Register dst, Register src) {
        super.fcvtzs(targetSize, srcSize, dst, src);
    }

    @Override
    public void scvtf(int targetSize, int srcSize, Register dst, Register src) {
        super.scvtf(targetSize, srcSize, dst, src);
    }

    @Override
    protected void frintz(int size, Register dst, Register src) {
        super.frintz(size, dst, src);
    }

    @Override
    public void fabs(int size, Register dst, Register src) {
        super.fabs(size, dst, src);
    }

    @Override
    public void fneg(int size, Register dst, Register src) {
        super.fneg(size, dst, src);
    }

    @Override
    public void fsqrt(int size, Register dst, Register src) {
        super.fsqrt(size, dst, src);
    }

    @Override
    public void fadd(int size, Register dst, Register src1, Register src2) {
        super.fadd(size, dst, src1, src2);
    }

    @Override
    public void fsub(int size, Register dst, Register src1, Register src2) {
        super.fsub(size, dst, src1, src2);
    }

    @Override
    public void fmul(int size, Register dst, Register src1, Register src2) {
        super.fmul(size, dst, src1, src2);
    }

    @Override
    public void fdiv(int size, Register dst, Register src1, Register src2) {
        super.fdiv(size, dst, src1, src2);
    }

    @Override
    protected void fmadd(int size, Register dst, Register src1, Register src2, Register src3) {
        super.fmadd(size, dst, src1, src2, src3);
    }

    @Override
    protected void fmsub(int size, Register dst, Register src1, Register src2, Register src3) {
        super.fmsub(size, dst, src1, src2, src3);
    }

    @Override
    public void fcmp(int size, Register src1, Register src2) {
        super.fcmp(size, src1, src2);
    }

    @Override
    public void fccmp(int size, Register src1, Register src2, int uimm4, ConditionFlag condition) {
        super.fccmp(size, src1, src2, uimm4, condition);
    }

    @Override
    public void fcmpZero(int size, Register src) {
        super.fcmpZero(size, src);
    }

    @Override
    protected void fcsel(int size, Register dst, Register src1, Register src2, ConditionFlag condition) {
        super.fcsel(size, dst, src1, src2, condition);
    }

    @Override
    protected void hlt(int uimm16) {
        super.hlt(uimm16);
    }

    @Override
    protected void brk(int uimm16) {
        super.brk(uimm16);
    }

    @Override
    protected void hint(SystemHint hint) {
        super.hint(hint);
    }

    @Override
    protected void clrex() {
        super.clrex();
    }

    @Override
    public void dmb(BarrierKind barrierKind) {
        super.dmb(barrierKind);
    }

    @Override
    public void align(int modulus) {
    }

    @Override
    public void jmp(Label l) {
    }

    @Override
    protected void patchJumpTarget(int branch, int jumpTarget) {

    }

    @Override
    public AbstractAddress makeAddress(Register base, int displacement) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AbstractAddress getPlaceholder(int instructionStartPosition) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void ensureUniquePC() {
        throw new UnsupportedOperationException();
    }
}
