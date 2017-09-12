dnl Copyright (c) 2014, Red Hat Inc. All rights reserved.
dnl DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
dnl
dnl This code is free software; you can redistribute it and/or modify it
dnl under the terms of the GNU General Public License version 2 only, as
dnl published by the Free Software Foundation.
dnl
dnl This code is distributed in the hope that it will be useful, but WITHOUT
dnl ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
dnl FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
dnl version 2 for more details (a copy is included in the LICENSE file that
dnl accompanied this code).
dnl
dnl You should have received a copy of the GNU General Public License version
dnl 2 along with this work; if not, write to the Free Software Foundation,
dnl Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
dnl
dnl Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
dnl or visit www.oracle.com if you need additional information or have any
dnl questions.
dnl
dnl 
dnl Process this file with m4 ad_encode.m4 to generate the load/store
dnl patterns used in aarch64.ad.
dnl
define(choose, `loadStore($1, &MacroAssembler::$3, $2, $4,
               $5, $6, $7, $8);dnl

  %}')dnl
define(access, `
    $3Register $1_reg = as_$3Register($$1$$reg);
    $4choose(MacroAssembler(&cbuf), $1_reg,$2,$mem->opcode(),
        as_Register($mem$$base),$mem$$index,$mem$$scale,$mem$$disp)')dnl
define(load,`
  enc_class aarch64_enc_$2($1 dst, memory mem) %{dnl
access(dst,$2,$3)')dnl
load(iRegI,ldrsbw)
load(iRegI,ldrsb)
load(iRegI,ldrb)
load(iRegL,ldrb)
load(iRegI,ldrshw)
load(iRegI,ldrsh)
load(iRegI,ldrh)
load(iRegL,ldrh)
load(iRegI,ldrw)
load(iRegL,ldrw)
load(iRegL,ldrsw)
load(iRegL,ldr)
load(vRegF,ldrs,Float)
load(vRegD,ldrd,Float)
define(STORE,`
  enc_class aarch64_enc_$2($1 src, memory mem) %{dnl
access(src,$2,$3,$4)')dnl
define(STORE0,`
  enc_class aarch64_enc_$2`'0(memory mem) %{
    MacroAssembler _masm(&cbuf);
    choose(_masm,zr,$2,$mem->opcode(),
        as_$3Register($mem$$base),$mem$$index,$mem$$scale,$mem$$disp)')dnl
STORE(iRegI,strb)
STORE0(iRegI,strb)
STORE(iRegI,strh)
STORE0(iRegI,strh)
STORE(iRegI,strw)
STORE0(iRegI,strw)
STORE(iRegL,str,,
`// we sometimes get asked to store the stack pointer into the
    // current thread -- we cannot do that directly on AArch64
    if (src_reg == r31_sp) {
      MacroAssembler _masm(&cbuf);
      assert(as_Register($mem$$base) == rthread, "unexpected store for sp");
      __ mov(rscratch2, sp);
      src_reg = rscratch2;
    }
    ')
STORE0(iRegL,str)
STORE(vRegF,strs,Float)
STORE(vRegD,strd,Float)

  enc_class aarch64_enc_strw_immn(immN src, memory mem) %{
    MacroAssembler _masm(&cbuf);
    address con = (address)$src$$constant;
    // need to do this the hard way until we can manage relocs
    // for 32 bit constants
    __ movoop(rscratch2, (jobject)con);
    if (con) __ encode_heap_oop_not_null(rscratch2);
    choose(_masm,rscratch2,strw,$mem->opcode(),
        as_Register($mem$$base),$mem$$index,$mem$$scale,$mem$$disp)

  enc_class aarch64_enc_strw_immnk(immN src, memory mem) %{
    MacroAssembler _masm(&cbuf);
    address con = (address)$src$$constant;
    // need to do this the hard way until we can manage relocs
    // for 32 bit constants
    __ movoop(rscratch2, (jobject)con);
    __ encode_klass_not_null(rscratch2);
    choose(_masm,rscratch2,strw,$mem->opcode(),
        as_Register($mem$$base),$mem$$index,$mem$$scale,$mem$$disp)

