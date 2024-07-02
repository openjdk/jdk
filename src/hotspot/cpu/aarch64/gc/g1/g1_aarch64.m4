dnl Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
// BEGIN This section of the file is automatically generated. Do not edit --------------

// This section is generated from g1_aarch64.m4

define(`STOREP_INSN',
`
// This pattern is generated automatically from g1_aarch64.m4.
// DO NOT EDIT ANYTHING IN THIS SECTION OF THE FILE
instruct g1StoreP$1(indirect mem, iRegP src, iRegPNoSp tmp1, iRegPNoSp tmp2, iRegPNoSp tmp3, rFlagsReg cr)
%{
  predicate(UseG1GC && ifelse($1,Volatile,'needs_releasing_store(n)`,'!needs_releasing_store(n)`) && n->as_Store()->barrier_data() != 0);
  match(Set mem (StoreP mem src));
  effect(TEMP tmp1, TEMP tmp2, TEMP tmp3, KILL cr);
  ins_cost(ifelse($1,Volatile,VOLATILE_REF_COST,INSN_COST));
  format %{ "$2  $src, $mem\t# ptr" %}
  ins_encode %{
    g1_pre_write_barrier(masm, this,
                         $mem$$Register /* obj */,
                         $tmp1$$Register /* pre_val */,
                         $tmp2$$Register /* tmp1 */,
                         $tmp3$$Register /* tmp2 */,
                         RegSet::of($mem$$Register, $src$$Register) /* preserve */);
    __ $2($src$$Register, $mem$$Register);
    g1_post_write_barrier(masm, this,
                          $mem$$Register /* store_addr */,
                          $src$$Register /* new_val */,
                          $tmp2$$Register /* tmp1 */,
                          $tmp3$$Register /* tmp2 */);
  %}
  ins_pipe(ifelse($1,Volatile,pipe_class_memory,istore_reg_mem));
%}')dnl
STOREP_INSN(,str)
STOREP_INSN(Volatile,stlr)
dnl
define(`STOREN_INSN',
`
// This pattern is generated automatically from g1_aarch64.m4.
// DO NOT EDIT ANYTHING IN THIS SECTION OF THE FILE
instruct g1StoreN$1(indirect mem, iRegN src, iRegPNoSp tmp1, iRegPNoSp tmp2, iRegPNoSp tmp3, rFlagsReg cr)
%{
  predicate(UseG1GC && ifelse($1,Volatile,'needs_releasing_store(n)`,'!needs_releasing_store(n)`) && n->as_Store()->barrier_data() != 0);
  match(Set mem (StoreN mem src));
  effect(TEMP tmp1, TEMP tmp2, TEMP tmp3, KILL cr);
  ins_cost(ifelse($1,Volatile,VOLATILE_REF_COST,INSN_COST));
  format %{ "$2  $src, $mem\t# compressed ptr" %}
  ins_encode %{
    g1_pre_write_barrier(masm, this,
                         $mem$$Register /* obj */,
                         $tmp1$$Register /* pre_val */,
                         $tmp2$$Register /* tmp1 */,
                         $tmp3$$Register /* tmp2 */,
                         RegSet::of($mem$$Register, $src$$Register) /* preserve */);
    __ $2($src$$Register, $mem$$Register);
    if ((barrier_data() & G1C2BarrierPost) != 0) {
      if ((barrier_data() & G1C2BarrierPostNotNull) == 0) {
        __ decode_heap_oop($tmp1$$Register, $src$$Register);
      } else {
        __ decode_heap_oop_not_null($tmp1$$Register, $src$$Register);
      }
    }
    g1_post_write_barrier(masm, this,
                          $mem$$Register /* store_addr */,
                          $tmp1$$Register /* new_val */,
                          $tmp2$$Register /* tmp1 */,
                          $tmp3$$Register /* tmp2 */);
  %}
  ins_pipe(ifelse($1,Volatile,pipe_class_memory,istore_reg_mem));
%}')dnl
STOREN_INSN(,strw)

// This pattern is generated automatically from g1_aarch64.m4.
// DO NOT EDIT ANYTHING IN THIS SECTION OF THE FILE
instruct g1EncodePAndStoreN(indirect mem, iRegP src, iRegPNoSp tmp1, iRegPNoSp tmp2, iRegPNoSp tmp3, rFlagsReg cr)
%{
  predicate(UseG1GC && !needs_releasing_store(n) && n->as_Store()->barrier_data() != 0);
  match(Set mem (StoreN mem (EncodeP src)));
  effect(TEMP tmp1, TEMP tmp2, TEMP tmp3, KILL cr);
  ins_cost(INSN_COST);
  format %{ "encode_heap_oop $tmp1, $src\n\t"
            "strw  $tmp1, $mem\t# compressed ptr" %}
  ins_encode %{
    g1_pre_write_barrier(masm, this,
                         $mem$$Register /* obj */,
                         $tmp1$$Register /* pre_val */,
                         $tmp2$$Register /* tmp1 */,
                         $tmp3$$Register /* tmp2 */,
                         RegSet::of($mem$$Register, $src$$Register) /* preserve */);
    if ((barrier_data() & G1C2BarrierPostNotNull) == 0) {
      __ encode_heap_oop($tmp1$$Register, $src$$Register);
    } else {
      __ encode_heap_oop_not_null($tmp1$$Register, $src$$Register);
    }
    __ strw($tmp1$$Register, $mem$$Register);
    g1_post_write_barrier(masm, this,
                          $mem$$Register /* store_addr */,
                          $src$$Register /* new_val */,
                          $tmp2$$Register /* tmp1 */,
                          $tmp3$$Register /* tmp2 */);
  %}
  ins_pipe(istore_reg_mem);
%}

// Very few of the total executed stores are volatile (less than 1% across
// multiple benchmark suites), no need to define an encode-and-store version.
STOREN_INSN(Volatile,stlrw)
dnl
define(`CAEP_INSN',
`
// This pattern is generated automatically from g1_aarch64.m4.
// DO NOT EDIT ANYTHING IN THIS SECTION OF THE FILE
instruct g1CompareAndExchangeP$1(iRegPNoSp res, indirect mem, iRegP oldval, iRegP newval, iRegPNoSp tmp1, iRegPNoSp tmp2, iRegPNoSp tmp3, rFlagsReg cr)
%{
  predicate(UseG1GC && ifelse($1,Acq,'needs_acquiring_load_exclusive(n)`,'!needs_acquiring_load_exclusive(n)`) && n->as_LoadStore()->barrier_data() != 0);
  match(Set res (CompareAndExchangeP mem (Binary oldval newval)));
  effect(TEMP res, TEMP tmp1, TEMP tmp2, TEMP tmp3, KILL cr);
  ins_cost(ifelse($1,Acq,VOLATILE_REF_COST,2 * VOLATILE_REF_COST));
  format %{ "cmpxchg$2 $res = $mem, $oldval, $newval\t# ptr" %}
  ins_encode %{
    assert_different_registers($oldval$$Register, $mem$$Register);
    assert_different_registers($newval$$Register, $mem$$Register);
    g1_pre_write_barrier(masm, this,
                         $mem$$Register /* obj */,
                         $tmp1$$Register /* pre_val */,
                         $tmp2$$Register /* tmp1 */,
                         $tmp3$$Register /* tmp2 */,
                         RegSet::of($mem$$Register, $oldval$$Register, $newval$$Register) /* preserve */,
                         RegSet::of($res$$Register) /* no_preserve */);
    __ mov($tmp1$$Register, $oldval$$Register);
    __ mov($tmp2$$Register, $newval$$Register);
    __ cmpxchg($mem$$Register, $tmp1$$Register, $tmp2$$Register, Assembler::xword,
               $3 /* acquire */, true /* release */, false /* weak */, $res$$Register);
    g1_post_write_barrier(masm, this,
                          $mem$$Register /* store_addr */,
                          $tmp2$$Register /* new_val */,
                          $tmp1$$Register /* tmp1 */,
                          $tmp3$$Register /* tmp2 */);
  %}
  ins_pipe(pipe_slow);
%}')dnl
CAEP_INSN(,,false)
CAEP_INSN(Acq,_acq,true)
dnl
define(`CAEN_INSN',
`
// This pattern is generated automatically from g1_aarch64.m4.
// DO NOT EDIT ANYTHING IN THIS SECTION OF THE FILE
instruct g1CompareAndExchangeN$1(iRegNNoSp res, indirect mem, iRegN oldval, iRegN newval, iRegPNoSp tmp1, iRegPNoSp tmp2, iRegPNoSp tmp3, rFlagsReg cr)
%{
  predicate(UseG1GC && ifelse($1,Acq,'needs_acquiring_load_exclusive(n)`,'!needs_acquiring_load_exclusive(n)`) && n->as_LoadStore()->barrier_data() != 0);
  match(Set res (CompareAndExchangeN mem (Binary oldval newval)));
  effect(TEMP res, TEMP tmp1, TEMP tmp2, TEMP tmp3, KILL cr);
  ins_cost(ifelse($1,Acq,VOLATILE_REF_COST,2 * VOLATILE_REF_COST));
  format %{ "cmpxchg$2 $res = $mem, $oldval, $newval\t# narrow oop" %}
  ins_encode %{
    assert_different_registers($oldval$$Register, $mem$$Register);
    assert_different_registers($newval$$Register, $mem$$Register);
    g1_pre_write_barrier(masm, this,
                         $mem$$Register /* obj */,
                         $tmp1$$Register /* pre_val */,
                         $tmp2$$Register /* tmp1 */,
                         $tmp3$$Register /* tmp2 */,
                         RegSet::of($mem$$Register, $oldval$$Register, $newval$$Register) /* preserve */,
                         RegSet::of($res$$Register) /* no_preserve */);
    __ mov($tmp1$$Register, $oldval$$Register);
    __ mov($tmp2$$Register, $newval$$Register);
    __ cmpxchg($mem$$Register, $tmp1$$Register, $tmp2$$Register, Assembler::word,
               $3 /* acquire */, true /* release */, false /* weak */, $res$$Register);
    __ decode_heap_oop($tmp2$$Register);
    g1_post_write_barrier(masm, this,
                          $mem$$Register /* store_addr */,
                          $tmp2$$Register /* new_val */,
                          $tmp1$$Register /* tmp1 */,
                          $tmp3$$Register /* tmp2 */);
  %}
  ins_pipe(pipe_slow);
%}')dnl
CAEN_INSN(,,false)
CAEN_INSN(Acq,_acq,true)
dnl
define(`CASP_INSN',
`
// This pattern is generated automatically from g1_aarch64.m4.
// DO NOT EDIT ANYTHING IN THIS SECTION OF THE FILE
instruct g1CompareAndSwapP$1(iRegINoSp res, indirect mem, iRegP newval, iRegPNoSp tmp1, iRegPNoSp tmp2, iRegPNoSp tmp3, iRegP oldval, rFlagsReg cr)
%{
  predicate(UseG1GC && ifelse($1,Acq,'needs_acquiring_load_exclusive(n)`,'!needs_acquiring_load_exclusive(n)`) && n->as_LoadStore()->barrier_data() != 0);
  match(Set res (CompareAndSwapP mem (Binary oldval newval)));
  match(Set res (WeakCompareAndSwapP mem (Binary oldval newval)));
  effect(TEMP res, TEMP tmp1, TEMP tmp2, TEMP tmp3, KILL cr);
  ins_cost(ifelse($1,Acq,VOLATILE_REF_COST,2 * VOLATILE_REF_COST));
  format %{ "cmpxchg$2 $mem, $oldval, $newval\t# (ptr)\n\t"
            "cset $res, EQ" %}
  ins_encode %{
    assert_different_registers($oldval$$Register, $mem$$Register);
    assert_different_registers($newval$$Register, $mem$$Register);
    g1_pre_write_barrier(masm, this,
                         $mem$$Register /* obj */,
                         $tmp1$$Register /* pre_val */,
                         $tmp2$$Register /* tmp1 */,
                         $tmp3$$Register /* tmp2 */,
                         RegSet::of($mem$$Register, $oldval$$Register, $newval$$Register) /* preserve */,
                         RegSet::of($res$$Register) /* no_preserve */);
    __ mov($tmp1$$Register, $oldval$$Register);
    __ mov($tmp2$$Register, $newval$$Register);
    __ cmpxchg($mem$$Register, $tmp1$$Register, $tmp2$$Register, Assembler::xword,
               $3 /* acquire */, true /* release */, false /* weak */, noreg);
    __ cset($res$$Register, Assembler::EQ);
    g1_post_write_barrier(masm, this,
                          $mem$$Register /* store_addr */,
                          $tmp2$$Register /* new_val */,
                          $tmp1$$Register /* tmp1 */,
                          $tmp3$$Register /* tmp2 */);
  %}
  ins_pipe(pipe_slow);
%}')dnl
CASP_INSN(,,false)
CASP_INSN(Acq,_acq,true)
dnl
define(`CASN_INSN',
`
// This pattern is generated automatically from g1_aarch64.m4.
// DO NOT EDIT ANYTHING IN THIS SECTION OF THE FILE
instruct g1CompareAndSwapN$1(iRegINoSp res, indirect mem, iRegN newval, iRegPNoSp tmp1, iRegPNoSp tmp2, iRegPNoSp tmp3, iRegN oldval, rFlagsReg cr)
%{
  predicate(UseG1GC && ifelse($1,Acq,'needs_acquiring_load_exclusive(n)`,'!needs_acquiring_load_exclusive(n)`) && n->as_LoadStore()->barrier_data() != 0);
  match(Set res (CompareAndSwapN mem (Binary oldval newval)));
  match(Set res (WeakCompareAndSwapN mem (Binary oldval newval)));
  effect(TEMP res, TEMP tmp1, TEMP tmp2, TEMP tmp3, KILL cr);
  ins_cost(ifelse($1,Acq,VOLATILE_REF_COST,2 * VOLATILE_REF_COST));
  format %{ "cmpxchg$2 $mem, $oldval, $newval\t# (narrow oop)\n\t"
            "cset $res, EQ" %}
  ins_encode %{
    assert_different_registers($oldval$$Register, $mem$$Register);
    assert_different_registers($newval$$Register, $mem$$Register);
    g1_pre_write_barrier(masm, this,
                         $mem$$Register /* obj */,
                         $tmp1$$Register /* pre_val */,
                         $tmp2$$Register /* tmp1 */,
                         $tmp3$$Register /* tmp2 */,
                         RegSet::of($mem$$Register, $oldval$$Register, $newval$$Register) /* preserve */,
                         RegSet::of($res$$Register) /* no_preserve */);
    __ mov($tmp1$$Register, $oldval$$Register);
    __ mov($tmp2$$Register, $newval$$Register);
    __ cmpxchg($mem$$Register, $tmp1$$Register, $tmp2$$Register, Assembler::word,
               $3 /* acquire */, true /* release */, false /* weak */, noreg);
    __ cset($res$$Register, Assembler::EQ);
    __ decode_heap_oop($tmp2$$Register);
    g1_post_write_barrier(masm, this,
                          $mem$$Register /* store_addr */,
                          $tmp2$$Register /* new_val */,
                          $tmp1$$Register /* tmp1 */,
                          $tmp3$$Register /* tmp2 */);
  %}
  ins_pipe(pipe_slow);
%}')dnl
CASN_INSN(,,false)
CASN_INSN(Acq,_acq,true)
dnl
define(`XCHGP_INSN',
`
// This pattern is generated automatically from g1_aarch64.m4.
// DO NOT EDIT ANYTHING IN THIS SECTION OF THE FILE
instruct g1XChgP$1(indirect mem, iRegP newval, iRegPNoSp tmp1, iRegPNoSp tmp2, iRegPNoSp preval, rFlagsReg cr)
%{
  predicate(UseG1GC && ifelse($1,Acq,'needs_acquiring_load_exclusive(n)`,'!needs_acquiring_load_exclusive(n)`) && n->as_LoadStore()->barrier_data() != 0);
  match(Set preval (GetAndSetP mem newval));
  effect(TEMP preval, TEMP tmp1, TEMP tmp2, KILL cr);
  ins_cost(ifelse($1,Acq,VOLATILE_REF_COST,2 * VOLATILE_REF_COST));
  format %{ "atomic_xchg$2  $preval, $newval, [$mem]" %}
  ins_encode %{
    assert_different_registers($mem$$Register, $newval$$Register);
    g1_pre_write_barrier(masm, this,
                         $mem$$Register /* obj */,
                         $preval$$Register /* pre_val (as a temporary register) */,
                         $tmp1$$Register /* tmp1 */,
                         $tmp2$$Register /* tmp2 */,
                         RegSet::of($mem$$Register, $preval$$Register, $newval$$Register) /* preserve */);
    __ $3($preval$$Register, $newval$$Register, $mem$$Register);
    g1_post_write_barrier(masm, this,
                          $mem$$Register /* store_addr */,
                          $newval$$Register /* new_val */,
                          $tmp1$$Register /* tmp1 */,
                          $tmp2$$Register /* tmp2 */);
  %}
  ins_pipe(pipe_serial);
%}')dnl
XCHGP_INSN(,,atomic_xchg)
XCHGP_INSN(Acq,_acq,atomic_xchgal)
dnl
define(`XCHGN_INSN',
`
// This pattern is generated automatically from g1_aarch64.m4.
// DO NOT EDIT ANYTHING IN THIS SECTION OF THE FILE
instruct g1XChgN$1(indirect mem, iRegN newval, iRegPNoSp tmp1, iRegPNoSp tmp2, iRegPNoSp tmp3, iRegNNoSp preval, rFlagsReg cr)
%{
  predicate(UseG1GC && ifelse($1,Acq,'needs_acquiring_load_exclusive(n)`,'!needs_acquiring_load_exclusive(n)`) && n->as_LoadStore()->barrier_data() != 0);
  match(Set preval (GetAndSetN mem newval));
  effect(TEMP preval, TEMP tmp1, TEMP tmp2, TEMP tmp3, KILL cr);
  ins_cost(ifelse($1,Acq,VOLATILE_REF_COST,2 * VOLATILE_REF_COST));
  format %{ "$2 $preval, $newval, [$mem]" %}
  ins_encode %{
    assert_different_registers($mem$$Register, $newval$$Register);
    g1_pre_write_barrier(masm, this,
                         $mem$$Register /* obj */,
                         $tmp1$$Register /* pre_val */,
                         $tmp2$$Register /* tmp1 */,
                         $tmp3$$Register /* tmp2 */,
                         RegSet::of($mem$$Register, $preval$$Register, $newval$$Register) /* preserve */);
    __ $3($preval$$Register, $newval$$Register, $mem$$Register);
    __ decode_heap_oop($tmp1$$Register, $newval$$Register);
    g1_post_write_barrier(masm, this,
                          $mem$$Register /* store_addr */,
                          $tmp1$$Register /* new_val */,
                          $tmp2$$Register /* tmp1 */,
                          $tmp3$$Register /* tmp2 */);
  %}
  ins_pipe(pipe_serial);
%}')dnl
XCHGN_INSN(,atomic_xchgw,atomic_xchgw)
XCHGN_INSN(Acq,atomic_xchgw_acq,atomic_xchgalw)
dnl
define(`LOADP_INSN',
`
// This pattern is generated automatically from g1_aarch64.m4.
// DO NOT EDIT ANYTHING IN THIS SECTION OF THE FILE
instruct g1LoadP$1(iRegPNoSp dst, indirect mem, iRegPNoSp tmp1, iRegPNoSp tmp2, rFlagsReg cr)
%{
  predicate(UseG1GC && ifelse($1,Volatile,'needs_acquiring_load(n)`,'!needs_acquiring_load(n)`) && n->as_Load()->barrier_data() != 0);
  match(Set dst (LoadP mem));
  effect(TEMP dst, TEMP tmp1, TEMP tmp2, KILL cr);
  ins_cost(ifelse($1,Volatile,VOLATILE_REF_COST,4 * INSN_COST));
  format %{ "$2  $dst, $mem\t# ptr" %}
  ins_encode %{
    __ $2($dst$$Register, $mem$$Register);
    g1_pre_write_barrier(masm, this,
                         noreg /* obj */,
                         $dst$$Register /* pre_val */,
                         $tmp1$$Register /* tmp1 */,
                         $tmp2$$Register /* tmp2 */);
  %}
  ins_pipe(ifelse($1,Volatile,pipe_serial,iload_reg_mem));
%}')dnl
LOADP_INSN(,ldr)
LOADP_INSN(Volatile,ldar)
dnl
define(`LOADN_INSN',
`
// This pattern is generated automatically from g1_aarch64.m4.
// DO NOT EDIT ANYTHING IN THIS SECTION OF THE FILE
instruct g1LoadN$1(iRegNNoSp dst, indirect mem, iRegPNoSp tmp1, iRegPNoSp tmp2, iRegPNoSp tmp3, rFlagsReg cr)
%{
  predicate(UseG1GC && ifelse($1,Volatile,'needs_acquiring_load(n)`,'!needs_acquiring_load(n)`) && n->as_Load()->barrier_data() != 0);
  match(Set dst (LoadN mem));
  effect(TEMP dst, TEMP tmp1, TEMP tmp2, TEMP tmp3, KILL cr);
  ins_cost(ifelse($1,Volatile,VOLATILE_REF_COST,4 * INSN_COST));
  format %{ "$2  $dst, $mem\t# compressed ptr" %}
  ins_encode %{
    __ $2($dst$$Register, $mem$$Register);
    if ((barrier_data() & G1C2BarrierPre) != 0) {
      __ decode_heap_oop($tmp1$$Register, $dst$$Register);
      g1_pre_write_barrier(masm, this,
                           noreg /* obj */,
                           $tmp1$$Register /* pre_val */,
                           $tmp2$$Register /* tmp1 */,
                           $tmp3$$Register /* tmp2 */);
    }
  %}
  ins_pipe(ifelse($1,Volatile,pipe_serial,iload_reg_mem));
%}')dnl
LOADN_INSN(,ldrw)
LOADN_INSN(Volatile,ldarw)

// END This section of the file is automatically generated. Do not edit --------------
