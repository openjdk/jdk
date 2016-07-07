// Sundry CAS operations.  Note that release is always true,
// regardless of the memory ordering of the CAS.  This is because we
// need the volatile case to be sequentially consistent but there is
// no trailing StoreLoad barrier emitted by C2.  Unfortunately we
// can't check the type of memory ordering here, so we always emit a
// STLXR.

define(`CAS_INSN',
`
instruct compareAndExchange$1$5(iReg$2_R0 res, indirect mem, iReg$2_R2 oldval, iReg$2_R3 newval, rFlagsReg cr) %{
  match(Set res (CompareAndExchange$1 mem (Binary oldval newval)));
  ifelse($5,Acq,'  predicate(needs_acquiring_load_exclusive(n));
  ins_cost(VOLATILE_REF_COST);`,'  ins_cost(2 * VOLATILE_REF_COST);`)
  effect(KILL cr);
  format %{
    "cmpxchg $res = $mem, $oldval, $newval\t# ($3, weak) if $mem == $oldval then $mem <-- $newval"
  %}
  ins_encode %{
    __ cmpxchg($mem$$Register, $oldval$$Register, $newval$$Register,
               Assembler::$4, /*acquire*/ ifelse($5,Acq,true,false), /*release*/ true,
               /*weak*/ false, $res$$Register);
  %}
  ins_pipe(pipe_slow);
%}')dnl
define(`CAS_INSN4',
`
instruct compareAndExchange$1$7(iReg$2_R0 res, indirect mem, iReg$2_R2 oldval, iReg$2_R3 newval, rFlagsReg cr) %{
  match(Set res (CompareAndExchange$1 mem (Binary oldval newval)));
  ifelse($7,Acq,'  predicate(needs_acquiring_load_exclusive(n));
  ins_cost(VOLATILE_REF_COST);`,'  ins_cost(2 * VOLATILE_REF_COST);`)
  effect(KILL cr);
  format %{
    "cmpxchg $res = $mem, $oldval, $newval\t# ($3, weak) if $mem == $oldval then $mem <-- $newval"
  %}
  ins_encode %{
    __ $5(rscratch2, $oldval$$Register);
    __ cmpxchg($mem$$Register, rscratch2, $newval$$Register,
               Assembler::$4, /*acquire*/ ifelse($5,Acq,true,false), /*release*/ true,
               /*weak*/ false, $res$$Register);
    __ $6($res$$Register, $res$$Register);
  %}
  ins_pipe(pipe_slow);
%}')dnl
CAS_INSN4(B,I,byte,byte,uxtbw,sxtbw)
CAS_INSN4(S,I,short,halfword,uxthw,sxthw)
CAS_INSN(I,I,int,word)
CAS_INSN(L,L,long,xword)
CAS_INSN(N,N,narrow oop,word)
CAS_INSN(P,P,ptr,xword)
dnl
dnl CAS_INSN4(B,I,byte,byte,uxtbw,sxtbw,Acq)
dnl CAS_INSN4(S,I,short,halfword,uxthw,sxthw,Acq)
dnl CAS_INSN(I,I,int,word,Acq)
dnl CAS_INSN(L,L,long,xword,Acq)
dnl CAS_INSN(N,N,narrow oop,word,Acq)
dnl CAS_INSN(P,P,ptr,xword,Acq)
dnl
define(`CAS_INSN2',
`
instruct weakCompareAndSwap$1$6(iRegINoSp res, indirect mem, iReg$2 oldval, iReg$2 newval, rFlagsReg cr) %{
  match(Set res (WeakCompareAndSwap$1 mem (Binary oldval newval)));
  ifelse($6,Acq,'  predicate(needs_acquiring_load_exclusive(n));
  ins_cost(VOLATILE_REF_COST);`,'  ins_cost(2 * VOLATILE_REF_COST);`)
  effect(KILL cr);
  format %{
    "cmpxchg $res = $mem, $oldval, $newval\t# ($3, weak) if $mem == $oldval then $mem <-- $newval"
    "csetw $res, EQ\t# $res <-- (EQ ? 1 : 0)"
  %}
  ins_encode %{
    __ uxt$5(rscratch2, $oldval$$Register);
    __ cmpxchg($mem$$Register, rscratch2, $newval$$Register,
               Assembler::$4, /*acquire*/ ifelse($6,Acq,true,false), /*release*/ true,
               /*weak*/ true, noreg);
    __ csetw($res$$Register, Assembler::EQ);
  %}
  ins_pipe(pipe_slow);
%}')dnl
define(`CAS_INSN3',
`
instruct weakCompareAndSwap$1$5(iRegINoSp res, indirect mem, iReg$2 oldval, iReg$2 newval, rFlagsReg cr) %{
  match(Set res (WeakCompareAndSwap$1 mem (Binary oldval newval)));
  ifelse($5,Acq,'  predicate(needs_acquiring_load_exclusive(n));
  ins_cost(VOLATILE_REF_COST);`,'  ins_cost(2 * VOLATILE_REF_COST);`)
  effect(KILL cr);
  format %{
    "cmpxchg $res = $mem, $oldval, $newval\t# ($3, weak) if $mem == $oldval then $mem <-- $newval"
    "csetw $res, EQ\t# $res <-- (EQ ? 1 : 0)"
  %}
  ins_encode %{
    __ cmpxchg($mem$$Register, $oldval$$Register, $newval$$Register,
               Assembler::$4, /*acquire*/ ifelse($5,Acq,true,false), /*release*/ true,
               /*weak*/ true, noreg);
    __ csetw($res$$Register, Assembler::EQ);
  %}
  ins_pipe(pipe_slow);
%}')dnl
CAS_INSN2(B,I,byte,byte,bw)
CAS_INSN2(S,I,short,halfword,hw)
CAS_INSN3(I,I,int,word)
CAS_INSN3(L,L,long,xword)
CAS_INSN3(N,N,narrow oop,word)
CAS_INSN3(P,P,ptr,xword)
dnl CAS_INSN2(B,I,byte,byte,bw,Acq)
dnl CAS_INSN2(S,I,short,halfword,hw,Acq)
dnl CAS_INSN3(I,I,int,word,Acq)
dnl CAS_INSN3(L,L,long,xword,Acq)
dnl CAS_INSN3(N,N,narrow oop,word,Acq)
dnl CAS_INSN3(P,P,ptr,xword,Acq)
dnl
