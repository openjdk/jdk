/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "oops/methodData.hpp"
#include "opto/c2_MacroAssembler.hpp"
#include "opto/intrinsicnode.hpp"
#include "opto/output.hpp"
#include "opto/opcodes.hpp"
#include "opto/subnode.hpp"
#include "runtime/globals.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"
#include "utilities/sizes.hpp"

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#define STOP(error) stop(error)
#else
#define BLOCK_COMMENT(str) block_comment(str)
#define STOP(error) block_comment(error); stop(error)
#endif

// C2 compiled method's prolog code.
void C2_MacroAssembler::verified_entry(int framesize, int stack_bang_size, bool fp_mode_24b, bool is_stub) {
  assert(stack_bang_size >= framesize || stack_bang_size <= 0, "stack bang size incorrect");

  assert((framesize & (StackAlignmentInBytes-1)) == 0, "frame size not aligned");
  // Remove word for return addr
  framesize -= wordSize;
  stack_bang_size -= wordSize;

  // Calls to C2R adapters often do not accept exceptional returns.
  // We require that their callers must bang for them.  But be careful, because
  // some VM calls (such as call site linkage) can use several kilobytes of
  // stack.  But the stack safety zone should account for that.
  // See bugs 4446381, 4468289, 4497237.
  if (stack_bang_size > 0) {
    generate_stack_overflow_check(stack_bang_size);

    // We always push rbp, so that on return to interpreter rbp, will be
    // restored correctly and we can correct the stack.
    push(rbp);
    // Save caller's stack pointer into RBP if the frame pointer is preserved.
    if (PreserveFramePointer) {
      mov(rbp, rsp);
    }
    // Remove word for ebp
    framesize -= wordSize;

    // Create frame
    if (framesize) {
      subptr(rsp, framesize);
    }
  } else {
    subptr(rsp, framesize);

    // Save RBP register now.
    framesize -= wordSize;
    movptr(Address(rsp, framesize), rbp);
    // Save caller's stack pointer into RBP if the frame pointer is preserved.
    if (PreserveFramePointer) {
      movptr(rbp, rsp);
      if (framesize > 0) {
        addptr(rbp, framesize);
      }
    }
  }

  if (VerifyStackAtCalls) { // Majik cookie to verify stack depth
    framesize -= wordSize;
    movptr(Address(rsp, framesize), (int32_t)0xbadb100d);
  }

#ifdef ASSERT
  if (VerifyStackAtCalls) {
    Label L;
    push(rax);
    mov(rax, rsp);
    andptr(rax, StackAlignmentInBytes-1);
    cmpptr(rax, StackAlignmentInBytes-wordSize);
    pop(rax);
    jcc(Assembler::equal, L);
    STOP("Stack is not properly aligned!");
    bind(L);
  }
#endif

  if (!is_stub) {
    BarrierSetAssembler* bs = BarrierSet::barrier_set()->barrier_set_assembler();
    // We put the non-hot code of the nmethod entry barrier out-of-line in a stub.
    Label dummy_slow_path;
    Label dummy_continuation;
    Label* slow_path = &dummy_slow_path;
    Label* continuation = &dummy_continuation;
    if (!Compile::current()->output()->in_scratch_emit_size()) {
      // Use real labels from actual stub when not emitting code for the purpose of measuring its size
      C2EntryBarrierStub* stub = new (Compile::current()->comp_arena()) C2EntryBarrierStub();
      Compile::current()->output()->add_stub(stub);
      slow_path = &stub->entry();
      continuation = &stub->continuation();
    }
    bs->nmethod_entry_barrier(this, slow_path, continuation);
  }
}

inline Assembler::AvxVectorLen C2_MacroAssembler::vector_length_encoding(int vlen_in_bytes) {
  switch (vlen_in_bytes) {
    case  4: // fall-through
    case  8: // fall-through
    case 16: return Assembler::AVX_128bit;
    case 32: return Assembler::AVX_256bit;
    case 64: return Assembler::AVX_512bit;

    default: {
      ShouldNotReachHere();
      return Assembler::AVX_NoVec;
    }
  }
}

// fast_lock and fast_unlock used by C2

// Because the transitions from emitted code to the runtime
// monitorenter/exit helper stubs are so slow it's critical that
// we inline both the stack-locking fast path and the inflated fast path.
//
// See also: cmpFastLock and cmpFastUnlock.
//
// What follows is a specialized inline transliteration of the code
// in enter() and exit(). If we're concerned about I$ bloat another
// option would be to emit TrySlowEnter and TrySlowExit methods
// at startup-time.  These methods would accept arguments as
// (rax,=Obj, rbx=Self, rcx=box, rdx=Scratch) and return success-failure
// indications in the icc.ZFlag.  fast_lock and fast_unlock would simply
// marshal the arguments and emit calls to TrySlowEnter and TrySlowExit.
// In practice, however, the # of lock sites is bounded and is usually small.
// Besides the call overhead, TrySlowEnter and TrySlowExit might suffer
// if the processor uses simple bimodal branch predictors keyed by EIP
// Since the helper routines would be called from multiple synchronization
// sites.
//
// An even better approach would be write "MonitorEnter()" and "MonitorExit()"
// in java - using j.u.c and unsafe - and just bind the lock and unlock sites
// to those specialized methods.  That'd give us a mostly platform-independent
// implementation that the JITs could optimize and inline at their pleasure.
// Done correctly, the only time we'd need to cross to native could would be
// to park() or unpark() threads.  We'd also need a few more unsafe operators
// to (a) prevent compiler-JIT reordering of non-volatile accesses, and
// (b) explicit barriers or fence operations.
//
// TODO:
//
// *  Arrange for C2 to pass "Self" into fast_lock and fast_unlock in one of the registers (scr).
//    This avoids manifesting the Self pointer in the fast_lock and fast_unlock terminals.
//    Given TLAB allocation, Self is usually manifested in a register, so passing it into
//    the lock operators would typically be faster than reifying Self.
//
// *  Ideally I'd define the primitives as:
//       fast_lock   (nax Obj, nax box, EAX tmp, nax scr) where box, tmp and scr are KILLED.
//       fast_unlock (nax Obj, EAX box, nax tmp) where box and tmp are KILLED
//    Unfortunately ADLC bugs prevent us from expressing the ideal form.
//    Instead, we're stuck with a rather awkward and brittle register assignments below.
//    Furthermore the register assignments are overconstrained, possibly resulting in
//    sub-optimal code near the synchronization site.
//
// *  Eliminate the sp-proximity tests and just use "== Self" tests instead.
//    Alternately, use a better sp-proximity test.
//
// *  Currently ObjectMonitor._Owner can hold either an sp value or a (THREAD *) value.
//    Either one is sufficient to uniquely identify a thread.
//    TODO: eliminate use of sp in _owner and use get_thread(tr) instead.
//
// *  Intrinsify notify() and notifyAll() for the common cases where the
//    object is locked by the calling thread but the waitlist is empty.
//    avoid the expensive JNI call to JVM_Notify() and JVM_NotifyAll().
//
// *  use jccb and jmpb instead of jcc and jmp to improve code density.
//    But beware of excessive branch density on AMD Opterons.
//
// *  Both fast_lock and fast_unlock set the ICC.ZF to indicate success
//    or failure of the fast path.  If the fast path fails then we pass
//    control to the slow path, typically in C.  In fast_lock and
//    fast_unlock we often branch to DONE_LABEL, just to find that C2
//    will emit a conditional branch immediately after the node.
//    So we have branches to branches and lots of ICC.ZF games.
//    Instead, it might be better to have C2 pass a "FailureLabel"
//    into fast_lock and fast_unlock.  In the case of success, control
//    will drop through the node.  ICC.ZF is undefined at exit.
//    In the case of failure, the node will branch directly to the
//    FailureLabel


// obj: object to lock
// box: on-stack box address (displaced header location) - KILLED
// rax,: tmp -- KILLED
// scr: tmp -- KILLED
void C2_MacroAssembler::fast_lock(Register objReg, Register boxReg, Register tmpReg,
                                 Register scrReg, Register cx1Reg, Register cx2Reg, Register thread,
                                 Metadata* method_data) {
  assert(LockingMode != LM_LIGHTWEIGHT, "lightweight locking should use fast_lock_lightweight");
  // Ensure the register assignments are disjoint
  assert(tmpReg == rax, "");
  assert(cx1Reg == noreg, "");
  assert(cx2Reg == noreg, "");
  assert_different_registers(objReg, boxReg, tmpReg, scrReg);

  // Possible cases that we'll encounter in fast_lock
  // ------------------------------------------------
  // * Inflated
  //    -- unlocked
  //    -- Locked
  //       = by self
  //       = by other
  // * neutral
  // * stack-locked
  //    -- by self
  //       = sp-proximity test hits
  //       = sp-proximity test generates false-negative
  //    -- by other
  //

  Label IsInflated, DONE_LABEL, NO_COUNT, COUNT;

  if (DiagnoseSyncOnValueBasedClasses != 0) {
    load_klass(tmpReg, objReg, scrReg);
    testb(Address(tmpReg, Klass::misc_flags_offset()), KlassFlags::_misc_is_value_based_class);
    jcc(Assembler::notZero, DONE_LABEL);
  }

  movptr(tmpReg, Address(objReg, oopDesc::mark_offset_in_bytes()));          // [FETCH]
  testptr(tmpReg, markWord::monitor_value); // inflated vs stack-locked|neutral
  jcc(Assembler::notZero, IsInflated);

  if (LockingMode == LM_MONITOR) {
    // Clear ZF so that we take the slow path at the DONE label. objReg is known to be not 0.
    testptr(objReg, objReg);
  } else {
    assert(LockingMode == LM_LEGACY, "must be");
    // Attempt stack-locking ...
    orptr (tmpReg, markWord::unlocked_value);
    movptr(Address(boxReg, 0), tmpReg);          // Anticipate successful CAS
    lock();
    cmpxchgptr(boxReg, Address(objReg, oopDesc::mark_offset_in_bytes()));      // Updates tmpReg
    jcc(Assembler::equal, COUNT);           // Success

    // Recursive locking.
    // The object is stack-locked: markword contains stack pointer to BasicLock.
    // Locked by current thread if difference with current SP is less than one page.
    subptr(tmpReg, rsp);
    // Next instruction set ZFlag == 1 (Success) if difference is less then one page.
    andptr(tmpReg, (int32_t) (7 - (int)os::vm_page_size()) );
    movptr(Address(boxReg, 0), tmpReg);
  }
  jmp(DONE_LABEL);

  bind(IsInflated);
  // The object is inflated. tmpReg contains pointer to ObjectMonitor* + markWord::monitor_value

  // Unconditionally set box->_displaced_header = markWord::unused_mark().
  // Without cast to int32_t this style of movptr will destroy r10 which is typically obj.
  movptr(Address(boxReg, 0), checked_cast<int32_t>(markWord::unused_mark().value()));

  // It's inflated and we use scrReg for ObjectMonitor* in this section.
  movptr(boxReg, Address(r15_thread, JavaThread::monitor_owner_id_offset()));
  movq(scrReg, tmpReg);
  xorq(tmpReg, tmpReg);
  lock();
  cmpxchgptr(boxReg, Address(scrReg, OM_OFFSET_NO_MONITOR_VALUE_TAG(owner)));

  // Propagate ICC.ZF from CAS above into DONE_LABEL.
  jccb(Assembler::equal, COUNT);    // CAS above succeeded; propagate ZF = 1 (success)

  cmpptr(boxReg, rax);                // Check if we are already the owner (recursive lock)
  jccb(Assembler::notEqual, NO_COUNT);    // If not recursive, ZF = 0 at this point (fail)
  incq(Address(scrReg, OM_OFFSET_NO_MONITOR_VALUE_TAG(recursions)));
  xorq(rax, rax); // Set ZF = 1 (success) for recursive lock, denoting locking success
  bind(DONE_LABEL);

  // ZFlag == 1 count in fast path
  // ZFlag == 0 count in slow path
  jccb(Assembler::notZero, NO_COUNT); // jump if ZFlag == 0

  bind(COUNT);
  if (LockingMode == LM_LEGACY) {
    // Count monitors in fast path
    increment(Address(thread, JavaThread::held_monitor_count_offset()));
  }
  xorl(tmpReg, tmpReg); // Set ZF == 1

  bind(NO_COUNT);

  // At NO_COUNT the icc ZFlag is set as follows ...
  // fast_unlock uses the same protocol.
  // ZFlag == 1 -> Success
  // ZFlag == 0 -> Failure - force control through the slow path
}

// obj: object to unlock
// box: box address (displaced header location), killed.  Must be EAX.
// tmp: killed, cannot be obj nor box.
//
// Some commentary on balanced locking:
//
// fast_lock and fast_unlock are emitted only for provably balanced lock sites.
// Methods that don't have provably balanced locking are forced to run in the
// interpreter - such methods won't be compiled to use fast_lock and fast_unlock.
// The interpreter provides two properties:
// I1:  At return-time the interpreter automatically and quietly unlocks any
//      objects acquired the current activation (frame).  Recall that the
//      interpreter maintains an on-stack list of locks currently held by
//      a frame.
// I2:  If a method attempts to unlock an object that is not held by the
//      the frame the interpreter throws IMSX.
//
// Lets say A(), which has provably balanced locking, acquires O and then calls B().
// B() doesn't have provably balanced locking so it runs in the interpreter.
// Control returns to A() and A() unlocks O.  By I1 and I2, above, we know that O
// is still locked by A().
//
// The only other source of unbalanced locking would be JNI.  The "Java Native Interface:
// Programmer's Guide and Specification" claims that an object locked by jni_monitorenter
// should not be unlocked by "normal" java-level locking and vice-versa.  The specification
// doesn't specify what will occur if a program engages in such mixed-mode locking, however.
// Arguably given that the spec legislates the JNI case as undefined our implementation
// could reasonably *avoid* checking owner in fast_unlock().
// In the interest of performance we elide m->Owner==Self check in unlock.
// A perfectly viable alternative is to elide the owner check except when
// Xcheck:jni is enabled.

void C2_MacroAssembler::fast_unlock(Register objReg, Register boxReg, Register tmpReg) {
  assert(LockingMode != LM_LIGHTWEIGHT, "lightweight locking should use fast_unlock_lightweight");
  assert(boxReg == rax, "");
  assert_different_registers(objReg, boxReg, tmpReg);

  Label DONE_LABEL, Stacked, COUNT, NO_COUNT;

  if (LockingMode == LM_LEGACY) {
    cmpptr(Address(boxReg, 0), NULL_WORD);                            // Examine the displaced header
    jcc   (Assembler::zero, COUNT);                                   // 0 indicates recursive stack-lock
  }
  movptr(tmpReg, Address(objReg, oopDesc::mark_offset_in_bytes()));   // Examine the object's markword
  if (LockingMode != LM_MONITOR) {
    testptr(tmpReg, markWord::monitor_value);                         // Inflated?
    jcc(Assembler::zero, Stacked);
  }

  // It's inflated.

  // Despite our balanced locking property we still check that m->_owner == Self
  // as java routines or native JNI code called by this thread might
  // have released the lock.
  //
  // If there's no contention try a 1-0 exit.  That is, exit without
  // a costly MEMBAR or CAS.  See synchronizer.cpp for details on how
  // we detect and recover from the race that the 1-0 exit admits.
  //
  // Conceptually fast_unlock() must execute a STST|LDST "release" barrier
  // before it STs null into _owner, releasing the lock.  Updates
  // to data protected by the critical section must be visible before
  // we drop the lock (and thus before any other thread could acquire
  // the lock and observe the fields protected by the lock).
  // IA32's memory-model is SPO, so STs are ordered with respect to
  // each other and there's no need for an explicit barrier (fence).
  // See also http://gee.cs.oswego.edu/dl/jmm/cookbook.html.
  Label LSuccess, LNotRecursive;

  cmpptr(Address(tmpReg, OM_OFFSET_NO_MONITOR_VALUE_TAG(recursions)), 0);
  jccb(Assembler::equal, LNotRecursive);

  // Recursive inflated unlock
  decrement(Address(tmpReg, OM_OFFSET_NO_MONITOR_VALUE_TAG(recursions)));
  jmpb(LSuccess);

  bind(LNotRecursive);

  // Set owner to null.
  // Release to satisfy the JMM
  movptr(Address(tmpReg, OM_OFFSET_NO_MONITOR_VALUE_TAG(owner)), NULL_WORD);
  // We need a full fence after clearing owner to avoid stranding.
  // StoreLoad achieves this.
  membar(StoreLoad);

  // Check if the entry_list is empty.
  cmpptr(Address(tmpReg, OM_OFFSET_NO_MONITOR_VALUE_TAG(entry_list)), NULL_WORD);
  jccb(Assembler::zero, LSuccess);    // If so we are done.

  // Check if there is a successor.
  cmpptr(Address(tmpReg, OM_OFFSET_NO_MONITOR_VALUE_TAG(succ)), NULL_WORD);
  jccb(Assembler::notZero, LSuccess); // If so we are done.

  // Save the monitor pointer in the current thread, so we can try to
  // reacquire the lock in SharedRuntime::monitor_exit_helper().
  andptr(tmpReg, ~(int32_t)markWord::monitor_value);
  movptr(Address(r15_thread, JavaThread::unlocked_inflated_monitor_offset()), tmpReg);

  orl   (boxReg, 1);                      // set ICC.ZF=0 to indicate failure
  jmpb  (DONE_LABEL);

  bind  (LSuccess);
  testl (boxReg, 0);                      // set ICC.ZF=1 to indicate success
  jmpb  (DONE_LABEL);

  if (LockingMode == LM_LEGACY) {
    bind  (Stacked);
    movptr(tmpReg, Address (boxReg, 0));      // re-fetch
    lock();
    cmpxchgptr(tmpReg, Address(objReg, oopDesc::mark_offset_in_bytes())); // Uses RAX which is box
    // Intentional fall-thru into DONE_LABEL
  }

  bind(DONE_LABEL);

  // ZFlag == 1 count in fast path
  // ZFlag == 0 count in slow path
  jccb(Assembler::notZero, NO_COUNT);

  bind(COUNT);

  if (LockingMode == LM_LEGACY) {
    // Count monitors in fast path
    decrementq(Address(r15_thread, JavaThread::held_monitor_count_offset()));
  }

  xorl(tmpReg, tmpReg); // Set ZF == 1

  bind(NO_COUNT);
}

void C2_MacroAssembler::fast_lock_lightweight(Register obj, Register box, Register rax_reg,
                                              Register t, Register thread) {
  assert(LockingMode == LM_LIGHTWEIGHT, "must be");
  assert(rax_reg == rax, "Used for CAS");
  assert_different_registers(obj, box, rax_reg, t, thread);

  // Handle inflated monitor.
  Label inflated;
  // Finish fast lock successfully. ZF value is irrelevant.
  Label locked;
  // Finish fast lock unsuccessfully. MUST jump with ZF == 0
  Label slow_path;

  if (UseObjectMonitorTable) {
    // Clear cache in case fast locking succeeds or we need to take the slow-path.
    movptr(Address(box, BasicLock::object_monitor_cache_offset_in_bytes()), 0);
  }

  if (DiagnoseSyncOnValueBasedClasses != 0) {
    load_klass(rax_reg, obj, t);
    testb(Address(rax_reg, Klass::misc_flags_offset()), KlassFlags::_misc_is_value_based_class);
    jcc(Assembler::notZero, slow_path);
  }

  const Register mark = t;

  { // Lightweight Lock

    Label push;

    const Register top = UseObjectMonitorTable ? rax_reg : box;

    // Load the mark.
    movptr(mark, Address(obj, oopDesc::mark_offset_in_bytes()));

    // Prefetch top.
    movl(top, Address(thread, JavaThread::lock_stack_top_offset()));

    // Check for monitor (0b10).
    testptr(mark, markWord::monitor_value);
    jcc(Assembler::notZero, inflated);

    // Check if lock-stack is full.
    cmpl(top, LockStack::end_offset() - 1);
    jcc(Assembler::greater, slow_path);

    // Check if recursive.
    cmpptr(obj, Address(thread, top, Address::times_1, -oopSize));
    jccb(Assembler::equal, push);

    // Try to lock. Transition lock bits 0b01 => 0b00
    movptr(rax_reg, mark);
    orptr(rax_reg, markWord::unlocked_value);
    andptr(mark, ~(int32_t)markWord::unlocked_value);
    lock(); cmpxchgptr(mark, Address(obj, oopDesc::mark_offset_in_bytes()));
    jcc(Assembler::notEqual, slow_path);

    if (UseObjectMonitorTable) {
      // Need to reload top, clobbered by CAS.
      movl(top, Address(thread, JavaThread::lock_stack_top_offset()));
    }
    bind(push);
    // After successful lock, push object on lock-stack.
    movptr(Address(thread, top), obj);
    addl(Address(thread, JavaThread::lock_stack_top_offset()), oopSize);
    jmpb(locked);
  }

  { // Handle inflated monitor.
    bind(inflated);

    const Register monitor = t;

    if (!UseObjectMonitorTable) {
      assert(mark == monitor, "should be the same here");
    } else {
      // Uses ObjectMonitorTable.  Look for the monitor in the om_cache.
      // Fetch ObjectMonitor* from the cache or take the slow-path.
      Label monitor_found;

      // Load cache address
      lea(t, Address(thread, JavaThread::om_cache_oops_offset()));

      const int num_unrolled = 2;
      for (int i = 0; i < num_unrolled; i++) {
        cmpptr(obj, Address(t));
        jccb(Assembler::equal, monitor_found);
        increment(t, in_bytes(OMCache::oop_to_oop_difference()));
      }

      Label loop;

      // Search for obj in cache.
      bind(loop);

      // Check for match.
      cmpptr(obj, Address(t));
      jccb(Assembler::equal, monitor_found);

      // Search until null encountered, guaranteed _null_sentinel at end.
      cmpptr(Address(t), 1);
      jcc(Assembler::below, slow_path); // 0 check, but with ZF=0 when *t == 0
      increment(t, in_bytes(OMCache::oop_to_oop_difference()));
      jmpb(loop);

      // Cache hit.
      bind(monitor_found);
      movptr(monitor, Address(t, OMCache::oop_to_monitor_difference()));
    }
    const ByteSize monitor_tag = in_ByteSize(UseObjectMonitorTable ? 0 : checked_cast<int>(markWord::monitor_value));
    const Address recursions_address(monitor, ObjectMonitor::recursions_offset() - monitor_tag);
    const Address owner_address(monitor, ObjectMonitor::owner_offset() - monitor_tag);

    Label monitor_locked;
    // Lock the monitor.

    if (UseObjectMonitorTable) {
      // Cache the monitor for unlock before trashing box. On failure to acquire
      // the lock, the slow path will reset the entry accordingly (see CacheSetter).
      movptr(Address(box, BasicLock::object_monitor_cache_offset_in_bytes()), monitor);
    }

    // Try to CAS owner (no owner => current thread's _monitor_owner_id).
    xorptr(rax_reg, rax_reg);
    movptr(box, Address(thread, JavaThread::monitor_owner_id_offset()));
    lock(); cmpxchgptr(box, owner_address);
    jccb(Assembler::equal, monitor_locked);

    // Check if recursive.
    cmpptr(box, rax_reg);
    jccb(Assembler::notEqual, slow_path);

    // Recursive.
    increment(recursions_address);

    bind(monitor_locked);
  }

  bind(locked);
  // Set ZF = 1
  xorl(rax_reg, rax_reg);

#ifdef ASSERT
  // Check that locked label is reached with ZF set.
  Label zf_correct;
  Label zf_bad_zero;
  jcc(Assembler::zero, zf_correct);
  jmp(zf_bad_zero);
#endif

  bind(slow_path);
#ifdef ASSERT
  // Check that slow_path label is reached with ZF not set.
  jcc(Assembler::notZero, zf_correct);
  stop("Fast Lock ZF != 0");
  bind(zf_bad_zero);
  stop("Fast Lock ZF != 1");
  bind(zf_correct);
#endif
  // C2 uses the value of ZF to determine the continuation.
}

void C2_MacroAssembler::fast_unlock_lightweight(Register obj, Register reg_rax, Register t, Register thread) {
  assert(LockingMode == LM_LIGHTWEIGHT, "must be");
  assert(reg_rax == rax, "Used for CAS");
  assert_different_registers(obj, reg_rax, t);

  // Handle inflated monitor.
  Label inflated, inflated_check_lock_stack;
  // Finish fast unlock successfully.  MUST jump with ZF == 1
  Label unlocked, slow_path;

  const Register mark = t;
  const Register monitor = t;
  const Register top = UseObjectMonitorTable ? t : reg_rax;
  const Register box = reg_rax;

  Label dummy;
  C2FastUnlockLightweightStub* stub = nullptr;

  if (!Compile::current()->output()->in_scratch_emit_size()) {
    stub = new (Compile::current()->comp_arena()) C2FastUnlockLightweightStub(obj, mark, reg_rax, thread);
    Compile::current()->output()->add_stub(stub);
  }

  Label& push_and_slow_path = stub == nullptr ? dummy : stub->push_and_slow_path();

  { // Lightweight Unlock

    // Load top.
    movl(top, Address(thread, JavaThread::lock_stack_top_offset()));

    if (!UseObjectMonitorTable) {
      // Prefetch mark.
      movptr(mark, Address(obj, oopDesc::mark_offset_in_bytes()));
    }

    // Check if obj is top of lock-stack.
    cmpptr(obj, Address(thread, top, Address::times_1, -oopSize));
    // Top of lock stack was not obj. Must be monitor.
    jcc(Assembler::notEqual, inflated_check_lock_stack);

    // Pop lock-stack.
    DEBUG_ONLY(movptr(Address(thread, top, Address::times_1, -oopSize), 0);)
    subl(Address(thread, JavaThread::lock_stack_top_offset()), oopSize);

    // Check if recursive.
    cmpptr(obj, Address(thread, top, Address::times_1, -2 * oopSize));
    jcc(Assembler::equal, unlocked);

    // We elide the monitor check, let the CAS fail instead.

    if (UseObjectMonitorTable) {
      // Load mark.
      movptr(mark, Address(obj, oopDesc::mark_offset_in_bytes()));
    }

    // Try to unlock. Transition lock bits 0b00 => 0b01
    movptr(reg_rax, mark);
    andptr(reg_rax, ~(int32_t)markWord::lock_mask);
    orptr(mark, markWord::unlocked_value);
    lock(); cmpxchgptr(mark, Address(obj, oopDesc::mark_offset_in_bytes()));
    jcc(Assembler::notEqual, push_and_slow_path);
    jmp(unlocked);
  }


  { // Handle inflated monitor.
    bind(inflated_check_lock_stack);
#ifdef ASSERT
    Label check_done;
    subl(top, oopSize);
    cmpl(top, in_bytes(JavaThread::lock_stack_base_offset()));
    jcc(Assembler::below, check_done);
    cmpptr(obj, Address(thread, top));
    jccb(Assembler::notEqual, inflated_check_lock_stack);
    stop("Fast Unlock lock on stack");
    bind(check_done);
    if (UseObjectMonitorTable) {
      movptr(mark, Address(obj, oopDesc::mark_offset_in_bytes()));
    }
    testptr(mark, markWord::monitor_value);
    jccb(Assembler::notZero, inflated);
    stop("Fast Unlock not monitor");
#endif

    bind(inflated);

    if (!UseObjectMonitorTable) {
      assert(mark == monitor, "should be the same here");
    } else {
      // Uses ObjectMonitorTable.  Look for the monitor in our BasicLock on the stack.
      movptr(monitor, Address(box, BasicLock::object_monitor_cache_offset_in_bytes()));
      // null check with ZF == 0, no valid pointer below alignof(ObjectMonitor*)
      cmpptr(monitor, alignof(ObjectMonitor*));
      jcc(Assembler::below, slow_path);
    }
    const ByteSize monitor_tag = in_ByteSize(UseObjectMonitorTable ? 0 : checked_cast<int>(markWord::monitor_value));
    const Address recursions_address{monitor, ObjectMonitor::recursions_offset() - monitor_tag};
    const Address succ_address{monitor, ObjectMonitor::succ_offset() - monitor_tag};
    const Address entry_list_address{monitor, ObjectMonitor::entry_list_offset() - monitor_tag};
    const Address owner_address{monitor, ObjectMonitor::owner_offset() - monitor_tag};

    Label recursive;

    // Check if recursive.
    cmpptr(recursions_address, 0);
    jccb(Assembler::notZero, recursive);

    // Set owner to null.
    // Release to satisfy the JMM
    movptr(owner_address, NULL_WORD);
    // We need a full fence after clearing owner to avoid stranding.
    // StoreLoad achieves this.
    membar(StoreLoad);

    // Check if the entry_list is empty.
    cmpptr(entry_list_address, NULL_WORD);
    jccb(Assembler::zero, unlocked);    // If so we are done.

    // Check if there is a successor.
    cmpptr(succ_address, NULL_WORD);
    jccb(Assembler::notZero, unlocked); // If so we are done.

    // Save the monitor pointer in the current thread, so we can try to
    // reacquire the lock in SharedRuntime::monitor_exit_helper().
    if (!UseObjectMonitorTable) {
      andptr(monitor, ~(int32_t)markWord::monitor_value);
    }
    movptr(Address(thread, JavaThread::unlocked_inflated_monitor_offset()), monitor);

    orl(t, 1); // Fast Unlock ZF = 0
    jmpb(slow_path);

    // Recursive unlock.
    bind(recursive);
    decrement(recursions_address);
  }

  bind(unlocked);
  xorl(t, t); // Fast Unlock ZF = 1

#ifdef ASSERT
  // Check that unlocked label is reached with ZF set.
  Label zf_correct;
  Label zf_bad_zero;
  jcc(Assembler::zero, zf_correct);
  jmp(zf_bad_zero);
#endif

  bind(slow_path);
  if (stub != nullptr) {
    bind(stub->slow_path_continuation());
  }
#ifdef ASSERT
  // Check that stub->continuation() label is reached with ZF not set.
  jcc(Assembler::notZero, zf_correct);
  stop("Fast Unlock ZF != 0");
  bind(zf_bad_zero);
  stop("Fast Unlock ZF != 1");
  bind(zf_correct);
#endif
  // C2 uses the value of ZF to determine the continuation.
}

static void abort_verify_int_in_range(uint idx, jint val, jint lo, jint hi) {
  fatal("Invalid CastII, idx: %u, val: %d, lo: %d, hi: %d", idx, val, lo, hi);
}

static void reconstruct_frame_pointer_helper(MacroAssembler* masm, Register dst) {
  const int framesize = Compile::current()->output()->frame_size_in_bytes();
  masm->movptr(dst, rsp);
  if (framesize > 2 * wordSize) {
    masm->addptr(dst, framesize - 2 * wordSize);
  }
}

void C2_MacroAssembler::reconstruct_frame_pointer(Register rtmp) {
  if (PreserveFramePointer) {
    // frame pointer is valid
#ifdef ASSERT
    // Verify frame pointer value in rbp.
    reconstruct_frame_pointer_helper(this, rtmp);
    Label L_success;
    cmpq(rbp, rtmp);
    jccb(Assembler::equal, L_success);
    STOP("frame pointer mismatch");
    bind(L_success);
#endif // ASSERT
  } else {
    reconstruct_frame_pointer_helper(this, rbp);
  }
}

void C2_MacroAssembler::verify_int_in_range(uint idx, const TypeInt* t, Register val) {
  jint lo = t->_lo;
  jint hi = t->_hi;
  assert(lo < hi, "type should not be empty or constant, idx: %u, lo: %d, hi: %d", idx, lo, hi);
  if (t == TypeInt::INT) {
    return;
  }

  BLOCK_COMMENT("CastII {");
  Label fail;
  Label succeed;
  if (hi == max_jint) {
    cmpl(val, lo);
    jccb(Assembler::greaterEqual, succeed);
  } else {
    if (lo != min_jint) {
      cmpl(val, lo);
      jccb(Assembler::less, fail);
    }
    cmpl(val, hi);
    jccb(Assembler::lessEqual, succeed);
  }

  bind(fail);
  movl(c_rarg0, idx);
  movl(c_rarg1, val);
  movl(c_rarg2, lo);
  movl(c_rarg3, hi);
  reconstruct_frame_pointer(rscratch1);
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, abort_verify_int_in_range)));
  hlt();
  bind(succeed);
  BLOCK_COMMENT("} // CastII");
}

static void abort_verify_long_in_range(uint idx, jlong val, jlong lo, jlong hi) {
  fatal("Invalid CastLL, idx: %u, val: " JLONG_FORMAT ", lo: " JLONG_FORMAT ", hi: " JLONG_FORMAT, idx, val, lo, hi);
}

void C2_MacroAssembler::verify_long_in_range(uint idx, const TypeLong* t, Register val, Register tmp) {
  jlong lo = t->_lo;
  jlong hi = t->_hi;
  assert(lo < hi, "type should not be empty or constant, idx: %u, lo: " JLONG_FORMAT ", hi: " JLONG_FORMAT, idx, lo, hi);
  if (t == TypeLong::LONG) {
    return;
  }

  BLOCK_COMMENT("CastLL {");
  Label fail;
  Label succeed;

  auto cmp_val = [&](jlong bound) {
    if (is_simm32(bound)) {
      cmpq(val, checked_cast<int>(bound));
    } else {
      mov64(tmp, bound);
      cmpq(val, tmp);
    }
  };

  if (hi == max_jlong) {
    cmp_val(lo);
    jccb(Assembler::greaterEqual, succeed);
  } else {
    if (lo != min_jlong) {
      cmp_val(lo);
      jccb(Assembler::less, fail);
    }
    cmp_val(hi);
    jccb(Assembler::lessEqual, succeed);
  }

  bind(fail);
  movl(c_rarg0, idx);
  movq(c_rarg1, val);
  mov64(c_rarg2, lo);
  mov64(c_rarg3, hi);
  reconstruct_frame_pointer(rscratch1);
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, abort_verify_long_in_range)));
  hlt();
  bind(succeed);
  BLOCK_COMMENT("} // CastLL");
}

//-------------------------------------------------------------------------------------------
// Generic instructions support for use in .ad files C2 code generation

void C2_MacroAssembler::vabsnegd(int opcode, XMMRegister dst, XMMRegister src) {
  if (dst != src) {
    movdqu(dst, src);
  }
  if (opcode == Op_AbsVD) {
    andpd(dst, ExternalAddress(StubRoutines::x86::vector_double_sign_mask()), noreg);
  } else {
    assert((opcode == Op_NegVD),"opcode should be Op_NegD");
    xorpd(dst, ExternalAddress(StubRoutines::x86::vector_double_sign_flip()), noreg);
  }
}

void C2_MacroAssembler::vabsnegd(int opcode, XMMRegister dst, XMMRegister src, int vector_len) {
  if (opcode == Op_AbsVD) {
    vandpd(dst, src, ExternalAddress(StubRoutines::x86::vector_double_sign_mask()), vector_len, noreg);
  } else {
    assert((opcode == Op_NegVD),"opcode should be Op_NegD");
    vxorpd(dst, src, ExternalAddress(StubRoutines::x86::vector_double_sign_flip()), vector_len, noreg);
  }
}

void C2_MacroAssembler::vabsnegf(int opcode, XMMRegister dst, XMMRegister src) {
  if (dst != src) {
    movdqu(dst, src);
  }
  if (opcode == Op_AbsVF) {
    andps(dst, ExternalAddress(StubRoutines::x86::vector_float_sign_mask()), noreg);
  } else {
    assert((opcode == Op_NegVF),"opcode should be Op_NegF");
    xorps(dst, ExternalAddress(StubRoutines::x86::vector_float_sign_flip()), noreg);
  }
}

void C2_MacroAssembler::vabsnegf(int opcode, XMMRegister dst, XMMRegister src, int vector_len) {
  if (opcode == Op_AbsVF) {
    vandps(dst, src, ExternalAddress(StubRoutines::x86::vector_float_sign_mask()), vector_len, noreg);
  } else {
    assert((opcode == Op_NegVF),"opcode should be Op_NegF");
    vxorps(dst, src, ExternalAddress(StubRoutines::x86::vector_float_sign_flip()), vector_len, noreg);
  }
}

void C2_MacroAssembler::pminmax(int opcode, BasicType elem_bt, XMMRegister dst, XMMRegister src, XMMRegister tmp) {
  assert(opcode == Op_MinV || opcode == Op_MaxV, "sanity");
  assert(tmp == xnoreg || elem_bt == T_LONG, "unused");

  if (opcode == Op_MinV) {
    if (elem_bt == T_BYTE) {
      pminsb(dst, src);
    } else if (elem_bt == T_SHORT) {
      pminsw(dst, src);
    } else if (elem_bt == T_INT) {
      pminsd(dst, src);
    } else {
      assert(elem_bt == T_LONG, "required");
      assert(tmp == xmm0, "required");
      assert_different_registers(dst, src, tmp);
      movdqu(xmm0, dst);
      pcmpgtq(xmm0, src);
      blendvpd(dst, src);  // xmm0 as mask
    }
  } else { // opcode == Op_MaxV
    if (elem_bt == T_BYTE) {
      pmaxsb(dst, src);
    } else if (elem_bt == T_SHORT) {
      pmaxsw(dst, src);
    } else if (elem_bt == T_INT) {
      pmaxsd(dst, src);
    } else {
      assert(elem_bt == T_LONG, "required");
      assert(tmp == xmm0, "required");
      assert_different_registers(dst, src, tmp);
      movdqu(xmm0, src);
      pcmpgtq(xmm0, dst);
      blendvpd(dst, src);  // xmm0 as mask
    }
  }
}

void C2_MacroAssembler::vpuminmax(int opcode, BasicType elem_bt, XMMRegister dst,
                                  XMMRegister src1, Address src2, int vlen_enc) {
  assert(opcode == Op_UMinV || opcode == Op_UMaxV, "sanity");
  if (opcode == Op_UMinV) {
    switch(elem_bt) {
      case T_BYTE:  vpminub(dst, src1, src2, vlen_enc); break;
      case T_SHORT: vpminuw(dst, src1, src2, vlen_enc); break;
      case T_INT:   vpminud(dst, src1, src2, vlen_enc); break;
      case T_LONG:  evpminuq(dst, k0, src1, src2, false, vlen_enc); break;
      default: fatal("Unsupported type %s", type2name(elem_bt)); break;
    }
  } else {
    assert(opcode == Op_UMaxV, "required");
    switch(elem_bt) {
      case T_BYTE:  vpmaxub(dst, src1, src2, vlen_enc); break;
      case T_SHORT: vpmaxuw(dst, src1, src2, vlen_enc); break;
      case T_INT:   vpmaxud(dst, src1, src2, vlen_enc); break;
      case T_LONG:  evpmaxuq(dst, k0, src1, src2, false, vlen_enc); break;
      default: fatal("Unsupported type %s", type2name(elem_bt)); break;
    }
  }
}

void C2_MacroAssembler::vpuminmaxq(int opcode, XMMRegister dst, XMMRegister src1, XMMRegister src2, XMMRegister xtmp1, XMMRegister xtmp2, int vlen_enc) {
  // For optimality, leverage a full vector width of 512 bits
  // for operations over smaller vector sizes on AVX512 targets.
  if (VM_Version::supports_evex() && !VM_Version::supports_avx512vl()) {
    if (opcode == Op_UMaxV) {
      evpmaxuq(dst, k0, src1, src2, false, Assembler::AVX_512bit);
    } else {
      assert(opcode == Op_UMinV, "required");
      evpminuq(dst, k0, src1, src2, false, Assembler::AVX_512bit);
    }
  } else {
    // T1 = -1
    vpcmpeqq(xtmp1, xtmp1, xtmp1, vlen_enc);
    // T1 = -1 << 63
    vpsllq(xtmp1, xtmp1, 63, vlen_enc);
    // Convert SRC2 to signed value i.e. T2 = T1 + SRC2
    vpaddq(xtmp2, xtmp1, src2, vlen_enc);
    // Convert SRC1 to signed value i.e. T1 = T1 + SRC1
    vpaddq(xtmp1, xtmp1, src1, vlen_enc);
    // Mask = T2 > T1
    vpcmpgtq(xtmp1, xtmp2, xtmp1, vlen_enc);
    if (opcode == Op_UMaxV) {
      // Res = Mask ? Src2 : Src1
      vpblendvb(dst, src1, src2, xtmp1, vlen_enc);
    } else {
      // Res = Mask ? Src1 : Src2
      vpblendvb(dst, src2, src1, xtmp1, vlen_enc);
    }
  }
}

void C2_MacroAssembler::vpuminmax(int opcode, BasicType elem_bt, XMMRegister dst,
                                  XMMRegister src1, XMMRegister src2, int vlen_enc) {
  assert(opcode == Op_UMinV || opcode == Op_UMaxV, "sanity");
  if (opcode == Op_UMinV) {
    switch(elem_bt) {
      case T_BYTE:  vpminub(dst, src1, src2, vlen_enc); break;
      case T_SHORT: vpminuw(dst, src1, src2, vlen_enc); break;
      case T_INT:   vpminud(dst, src1, src2, vlen_enc); break;
      case T_LONG:  evpminuq(dst, k0, src1, src2, false, vlen_enc); break;
      default: fatal("Unsupported type %s", type2name(elem_bt)); break;
    }
  } else {
    assert(opcode == Op_UMaxV, "required");
    switch(elem_bt) {
      case T_BYTE:  vpmaxub(dst, src1, src2, vlen_enc); break;
      case T_SHORT: vpmaxuw(dst, src1, src2, vlen_enc); break;
      case T_INT:   vpmaxud(dst, src1, src2, vlen_enc); break;
      case T_LONG:  evpmaxuq(dst, k0, src1, src2, false, vlen_enc); break;
      default: fatal("Unsupported type %s", type2name(elem_bt)); break;
    }
  }
}

void C2_MacroAssembler::vpminmax(int opcode, BasicType elem_bt,
                                 XMMRegister dst, XMMRegister src1, XMMRegister src2,
                                 int vlen_enc) {
  assert(opcode == Op_MinV || opcode == Op_MaxV, "sanity");

  if (opcode == Op_MinV) {
    if (elem_bt == T_BYTE) {
      vpminsb(dst, src1, src2, vlen_enc);
    } else if (elem_bt == T_SHORT) {
      vpminsw(dst, src1, src2, vlen_enc);
    } else if (elem_bt == T_INT) {
      vpminsd(dst, src1, src2, vlen_enc);
    } else {
      assert(elem_bt == T_LONG, "required");
      if (UseAVX > 2 && (vlen_enc == Assembler::AVX_512bit || VM_Version::supports_avx512vl())) {
        vpminsq(dst, src1, src2, vlen_enc);
      } else {
        assert_different_registers(dst, src1, src2);
        vpcmpgtq(dst, src1, src2, vlen_enc);
        vblendvpd(dst, src1, src2, dst, vlen_enc);
      }
    }
  } else { // opcode == Op_MaxV
    if (elem_bt == T_BYTE) {
      vpmaxsb(dst, src1, src2, vlen_enc);
    } else if (elem_bt == T_SHORT) {
      vpmaxsw(dst, src1, src2, vlen_enc);
    } else if (elem_bt == T_INT) {
      vpmaxsd(dst, src1, src2, vlen_enc);
    } else {
      assert(elem_bt == T_LONG, "required");
      if (UseAVX > 2 && (vlen_enc == Assembler::AVX_512bit || VM_Version::supports_avx512vl())) {
        vpmaxsq(dst, src1, src2, vlen_enc);
      } else {
        assert_different_registers(dst, src1, src2);
        vpcmpgtq(dst, src1, src2, vlen_enc);
        vblendvpd(dst, src2, src1, dst, vlen_enc);
      }
    }
  }
}

// Float/Double min max

void C2_MacroAssembler::vminmax_fp(int opcode, BasicType elem_bt,
                                   XMMRegister dst, XMMRegister a, XMMRegister b,
                                   XMMRegister tmp, XMMRegister atmp, XMMRegister btmp,
                                   int vlen_enc) {
  assert(UseAVX > 0, "required");
  assert(opcode == Op_MinV || opcode == Op_MinReductionV ||
         opcode == Op_MaxV || opcode == Op_MaxReductionV, "sanity");
  assert(elem_bt == T_FLOAT || elem_bt == T_DOUBLE, "sanity");
  assert_different_registers(a, tmp, atmp, btmp);
  assert_different_registers(b, tmp, atmp, btmp);

  bool is_min = (opcode == Op_MinV || opcode == Op_MinReductionV);
  bool is_double_word = is_double_word_type(elem_bt);

  /* Note on 'non-obvious' assembly sequence:
   *
   * While there are vminps/vmaxps instructions, there are two important differences between hardware
   * and Java on how they handle floats:
   *  a. -0.0 and +0.0 are considered equal (vminps/vmaxps will return second parameter when inputs are equal)
   *  b. NaN is not necesarily propagated (vminps/vmaxps will return second parameter when either input is NaN)
   *
   * It is still more efficient to use vminps/vmaxps, but with some pre/post-processing:
   *  a. -0.0/+0.0: Bias negative (positive) numbers to second parameter before vminps (vmaxps)
   *                (only useful when signs differ, noop otherwise)
   *  b. NaN: Check if it was the first parameter that had the NaN (with vcmp[UNORD_Q])

   *  Following pseudo code describes the algorithm for max[FD] (Min algorithm is on similar lines):
   *   btmp = (b < +0.0) ? a : b
   *   atmp = (b < +0.0) ? b : a
   *   Tmp  = Max_Float(atmp , btmp)
   *   Res  = (atmp == NaN) ? atmp : Tmp
   */

  void (MacroAssembler::*vblend)(XMMRegister, XMMRegister, XMMRegister, XMMRegister, int, bool, XMMRegister);
  void (MacroAssembler::*vmaxmin)(XMMRegister, XMMRegister, XMMRegister, int);
  void (MacroAssembler::*vcmp)(XMMRegister, XMMRegister, XMMRegister, int, int);
  XMMRegister mask;

  if (!is_double_word && is_min) {
    mask = a;
    vblend = &MacroAssembler::vblendvps;
    vmaxmin = &MacroAssembler::vminps;
    vcmp = &MacroAssembler::vcmpps;
  } else if (!is_double_word && !is_min) {
    mask = b;
    vblend = &MacroAssembler::vblendvps;
    vmaxmin = &MacroAssembler::vmaxps;
    vcmp = &MacroAssembler::vcmpps;
  } else if (is_double_word && is_min) {
    mask = a;
    vblend = &MacroAssembler::vblendvpd;
    vmaxmin = &MacroAssembler::vminpd;
    vcmp = &MacroAssembler::vcmppd;
  } else {
    assert(is_double_word && !is_min, "sanity");
    mask = b;
    vblend = &MacroAssembler::vblendvpd;
    vmaxmin = &MacroAssembler::vmaxpd;
    vcmp = &MacroAssembler::vcmppd;
  }

  // Make sure EnableX86ECoreOpts isn't disabled on register overlaps
  XMMRegister maxmin, scratch;
  if (dst == btmp) {
    maxmin = btmp;
    scratch = tmp;
  } else {
    maxmin = tmp;
    scratch = btmp;
  }

  bool precompute_mask = EnableX86ECoreOpts && UseAVX>1;
  if (precompute_mask && !is_double_word) {
    vpsrad(tmp, mask, 32, vlen_enc);
    mask = tmp;
  } else if (precompute_mask && is_double_word) {
    vpxor(tmp, tmp, tmp, vlen_enc);
    vpcmpgtq(tmp, tmp, mask, vlen_enc);
    mask = tmp;
  }

  (this->*vblend)(atmp, a, b, mask, vlen_enc, !precompute_mask, btmp);
  (this->*vblend)(btmp, b, a, mask, vlen_enc, !precompute_mask, tmp);
  (this->*vmaxmin)(maxmin, atmp, btmp, vlen_enc);
  (this->*vcmp)(scratch, atmp, atmp, Assembler::UNORD_Q, vlen_enc);
  (this->*vblend)(dst, maxmin, atmp, scratch, vlen_enc, false, scratch);
}

void C2_MacroAssembler::evminmax_fp(int opcode, BasicType elem_bt,
                                    XMMRegister dst, XMMRegister a, XMMRegister b,
                                    KRegister ktmp, XMMRegister atmp, XMMRegister btmp,
                                    int vlen_enc) {
  assert(UseAVX > 2, "required");
  assert(opcode == Op_MinV || opcode == Op_MinReductionV ||
         opcode == Op_MaxV || opcode == Op_MaxReductionV, "sanity");
  assert(elem_bt == T_FLOAT || elem_bt == T_DOUBLE, "sanity");
  assert_different_registers(dst, a, atmp, btmp);
  assert_different_registers(dst, b, atmp, btmp);

  bool is_min = (opcode == Op_MinV || opcode == Op_MinReductionV);
  bool is_double_word = is_double_word_type(elem_bt);
  bool merge = true;

  if (!is_double_word && is_min) {
    evpmovd2m(ktmp, a, vlen_enc);
    evblendmps(atmp, ktmp, a, b, merge, vlen_enc);
    evblendmps(btmp, ktmp, b, a, merge, vlen_enc);
    vminps(dst, atmp, btmp, vlen_enc);
    evcmpps(ktmp, k0, atmp, atmp, Assembler::UNORD_Q, vlen_enc);
    evmovdqul(dst, ktmp, atmp, merge, vlen_enc);
  } else if (!is_double_word && !is_min) {
    evpmovd2m(ktmp, b, vlen_enc);
    evblendmps(atmp, ktmp, a, b, merge, vlen_enc);
    evblendmps(btmp, ktmp, b, a, merge, vlen_enc);
    vmaxps(dst, atmp, btmp, vlen_enc);
    evcmpps(ktmp, k0, atmp, atmp, Assembler::UNORD_Q, vlen_enc);
    evmovdqul(dst, ktmp, atmp, merge, vlen_enc);
  } else if (is_double_word && is_min) {
    evpmovq2m(ktmp, a, vlen_enc);
    evblendmpd(atmp, ktmp, a, b, merge, vlen_enc);
    evblendmpd(btmp, ktmp, b, a, merge, vlen_enc);
    vminpd(dst, atmp, btmp, vlen_enc);
    evcmppd(ktmp, k0, atmp, atmp, Assembler::UNORD_Q, vlen_enc);
    evmovdquq(dst, ktmp, atmp, merge, vlen_enc);
  } else {
    assert(is_double_word && !is_min, "sanity");
    evpmovq2m(ktmp, b, vlen_enc);
    evblendmpd(atmp, ktmp, a, b, merge, vlen_enc);
    evblendmpd(btmp, ktmp, b, a, merge, vlen_enc);
    vmaxpd(dst, atmp, btmp, vlen_enc);
    evcmppd(ktmp, k0, atmp, atmp, Assembler::UNORD_Q, vlen_enc);
    evmovdquq(dst, ktmp, atmp, merge, vlen_enc);
  }
}

void C2_MacroAssembler::vminmax_fp(int opc, BasicType elem_bt, XMMRegister dst, KRegister mask,
                                   XMMRegister src1, XMMRegister src2, int vlen_enc) {
  assert(opc == Op_MinV || opc == Op_MinReductionV ||
         opc == Op_MaxV || opc == Op_MaxReductionV, "sanity");

  int imm8 = (opc == Op_MinV || opc == Op_MinReductionV) ? AVX10_MINMAX_MIN_COMPARE_SIGN
                                                         : AVX10_MINMAX_MAX_COMPARE_SIGN;
  if (elem_bt == T_FLOAT) {
    evminmaxps(dst, mask, src1, src2, true, imm8, vlen_enc);
  } else {
    assert(elem_bt == T_DOUBLE, "");
    evminmaxpd(dst, mask, src1, src2, true, imm8, vlen_enc);
  }
}

// Float/Double signum
void C2_MacroAssembler::signum_fp(int opcode, XMMRegister dst, XMMRegister zero, XMMRegister one) {
  assert(opcode == Op_SignumF || opcode == Op_SignumD, "sanity");

  Label DONE_LABEL;

  if (opcode == Op_SignumF) {
    ucomiss(dst, zero);
    jcc(Assembler::equal, DONE_LABEL);    // handle special case +0.0/-0.0, if argument is +0.0/-0.0, return argument
    jcc(Assembler::parity, DONE_LABEL);   // handle special case NaN, if argument NaN, return NaN
    movflt(dst, one);
    jcc(Assembler::above, DONE_LABEL);
    xorps(dst, ExternalAddress(StubRoutines::x86::vector_float_sign_flip()), noreg);
  } else if (opcode == Op_SignumD) {
    ucomisd(dst, zero);
    jcc(Assembler::equal, DONE_LABEL);    // handle special case +0.0/-0.0, if argument is +0.0/-0.0, return argument
    jcc(Assembler::parity, DONE_LABEL);   // handle special case NaN, if argument NaN, return NaN
    movdbl(dst, one);
    jcc(Assembler::above, DONE_LABEL);
    xorpd(dst, ExternalAddress(StubRoutines::x86::vector_double_sign_flip()), noreg);
  }

  bind(DONE_LABEL);
}

void C2_MacroAssembler::vextendbw(bool sign, XMMRegister dst, XMMRegister src) {
  if (sign) {
    pmovsxbw(dst, src);
  } else {
    pmovzxbw(dst, src);
  }
}

void C2_MacroAssembler::vextendbw(bool sign, XMMRegister dst, XMMRegister src, int vector_len) {
  if (sign) {
    vpmovsxbw(dst, src, vector_len);
  } else {
    vpmovzxbw(dst, src, vector_len);
  }
}

void C2_MacroAssembler::vextendbd(bool sign, XMMRegister dst, XMMRegister src, int vector_len) {
  if (sign) {
    vpmovsxbd(dst, src, vector_len);
  } else {
    vpmovzxbd(dst, src, vector_len);
  }
}

void C2_MacroAssembler::vextendwd(bool sign, XMMRegister dst, XMMRegister src, int vector_len) {
  if (sign) {
    vpmovsxwd(dst, src, vector_len);
  } else {
    vpmovzxwd(dst, src, vector_len);
  }
}

void C2_MacroAssembler::vprotate_imm(int opcode, BasicType etype, XMMRegister dst, XMMRegister src,
                                     int shift, int vector_len) {
  if (opcode == Op_RotateLeftV) {
    if (etype == T_INT) {
      evprold(dst, src, shift, vector_len);
    } else {
      assert(etype == T_LONG, "expected type T_LONG");
      evprolq(dst, src, shift, vector_len);
    }
  } else {
    assert(opcode == Op_RotateRightV, "opcode should be Op_RotateRightV");
    if (etype == T_INT) {
      evprord(dst, src, shift, vector_len);
    } else {
      assert(etype == T_LONG, "expected type T_LONG");
      evprorq(dst, src, shift, vector_len);
    }
  }
}

void C2_MacroAssembler::vprotate_var(int opcode, BasicType etype, XMMRegister dst, XMMRegister src,
                                     XMMRegister shift, int vector_len) {
  if (opcode == Op_RotateLeftV) {
    if (etype == T_INT) {
      evprolvd(dst, src, shift, vector_len);
    } else {
      assert(etype == T_LONG, "expected type T_LONG");
      evprolvq(dst, src, shift, vector_len);
    }
  } else {
    assert(opcode == Op_RotateRightV, "opcode should be Op_RotateRightV");
    if (etype == T_INT) {
      evprorvd(dst, src, shift, vector_len);
    } else {
      assert(etype == T_LONG, "expected type T_LONG");
      evprorvq(dst, src, shift, vector_len);
    }
  }
}

void C2_MacroAssembler::vshiftd_imm(int opcode, XMMRegister dst, int shift) {
  if (opcode == Op_RShiftVI) {
    psrad(dst, shift);
  } else if (opcode == Op_LShiftVI) {
    pslld(dst, shift);
  } else {
    assert((opcode == Op_URShiftVI),"opcode should be Op_URShiftVI");
    psrld(dst, shift);
  }
}

void C2_MacroAssembler::vshiftd(int opcode, XMMRegister dst, XMMRegister shift) {
  switch (opcode) {
    case Op_RShiftVI:  psrad(dst, shift); break;
    case Op_LShiftVI:  pslld(dst, shift); break;
    case Op_URShiftVI: psrld(dst, shift); break;

    default: assert(false, "%s", NodeClassNames[opcode]);
  }
}

void C2_MacroAssembler::vshiftd_imm(int opcode, XMMRegister dst, XMMRegister nds, int shift, int vector_len) {
  if (opcode == Op_RShiftVI) {
    vpsrad(dst, nds, shift, vector_len);
  } else if (opcode == Op_LShiftVI) {
    vpslld(dst, nds, shift, vector_len);
  } else {
    assert((opcode == Op_URShiftVI),"opcode should be Op_URShiftVI");
    vpsrld(dst, nds, shift, vector_len);
  }
}

void C2_MacroAssembler::vshiftd(int opcode, XMMRegister dst, XMMRegister src, XMMRegister shift, int vlen_enc) {
  switch (opcode) {
    case Op_RShiftVI:  vpsrad(dst, src, shift, vlen_enc); break;
    case Op_LShiftVI:  vpslld(dst, src, shift, vlen_enc); break;
    case Op_URShiftVI: vpsrld(dst, src, shift, vlen_enc); break;

    default: assert(false, "%s", NodeClassNames[opcode]);
  }
}

void C2_MacroAssembler::vshiftw(int opcode, XMMRegister dst, XMMRegister shift) {
  switch (opcode) {
    case Op_RShiftVB:  // fall-through
    case Op_RShiftVS:  psraw(dst, shift); break;

    case Op_LShiftVB:  // fall-through
    case Op_LShiftVS:  psllw(dst, shift);   break;

    case Op_URShiftVS: // fall-through
    case Op_URShiftVB: psrlw(dst, shift);  break;

    default: assert(false, "%s", NodeClassNames[opcode]);
  }
}

void C2_MacroAssembler::vshiftw(int opcode, XMMRegister dst, XMMRegister src, XMMRegister shift, int vlen_enc) {
  switch (opcode) {
    case Op_RShiftVB:  // fall-through
    case Op_RShiftVS:  vpsraw(dst, src, shift, vlen_enc); break;

    case Op_LShiftVB:  // fall-through
    case Op_LShiftVS:  vpsllw(dst, src, shift, vlen_enc); break;

    case Op_URShiftVS: // fall-through
    case Op_URShiftVB: vpsrlw(dst, src, shift, vlen_enc); break;

    default: assert(false, "%s", NodeClassNames[opcode]);
  }
}

void C2_MacroAssembler::vshiftq(int opcode, XMMRegister dst, XMMRegister shift) {
  switch (opcode) {
    case Op_RShiftVL:  psrlq(dst, shift); break; // using srl to implement sra on pre-avs512 systems
    case Op_LShiftVL:  psllq(dst, shift); break;
    case Op_URShiftVL: psrlq(dst, shift); break;

    default: assert(false, "%s", NodeClassNames[opcode]);
  }
}

void C2_MacroAssembler::vshiftq_imm(int opcode, XMMRegister dst, int shift) {
  if (opcode == Op_RShiftVL) {
    psrlq(dst, shift);  // using srl to implement sra on pre-avs512 systems
  } else if (opcode == Op_LShiftVL) {
    psllq(dst, shift);
  } else {
    assert((opcode == Op_URShiftVL),"opcode should be Op_URShiftVL");
    psrlq(dst, shift);
  }
}

void C2_MacroAssembler::vshiftq(int opcode, XMMRegister dst, XMMRegister src, XMMRegister shift, int vlen_enc) {
  switch (opcode) {
    case Op_RShiftVL: evpsraq(dst, src, shift, vlen_enc); break;
    case Op_LShiftVL:  vpsllq(dst, src, shift, vlen_enc); break;
    case Op_URShiftVL: vpsrlq(dst, src, shift, vlen_enc); break;

    default: assert(false, "%s", NodeClassNames[opcode]);
  }
}

void C2_MacroAssembler::vshiftq_imm(int opcode, XMMRegister dst, XMMRegister nds, int shift, int vector_len) {
  if (opcode == Op_RShiftVL) {
    evpsraq(dst, nds, shift, vector_len);
  } else if (opcode == Op_LShiftVL) {
    vpsllq(dst, nds, shift, vector_len);
  } else {
    assert((opcode == Op_URShiftVL),"opcode should be Op_URShiftVL");
    vpsrlq(dst, nds, shift, vector_len);
  }
}

void C2_MacroAssembler::varshiftd(int opcode, XMMRegister dst, XMMRegister src, XMMRegister shift, int vlen_enc) {
  switch (opcode) {
    case Op_RShiftVB:  // fall-through
    case Op_RShiftVS:  // fall-through
    case Op_RShiftVI:  vpsravd(dst, src, shift, vlen_enc); break;

    case Op_LShiftVB:  // fall-through
    case Op_LShiftVS:  // fall-through
    case Op_LShiftVI:  vpsllvd(dst, src, shift, vlen_enc); break;

    case Op_URShiftVB: // fall-through
    case Op_URShiftVS: // fall-through
    case Op_URShiftVI: vpsrlvd(dst, src, shift, vlen_enc); break;

    default: assert(false, "%s", NodeClassNames[opcode]);
  }
}

void C2_MacroAssembler::varshiftw(int opcode, XMMRegister dst, XMMRegister src, XMMRegister shift, int vlen_enc) {
  switch (opcode) {
    case Op_RShiftVB:  // fall-through
    case Op_RShiftVS:  evpsravw(dst, src, shift, vlen_enc); break;

    case Op_LShiftVB:  // fall-through
    case Op_LShiftVS:  evpsllvw(dst, src, shift, vlen_enc); break;

    case Op_URShiftVB: // fall-through
    case Op_URShiftVS: evpsrlvw(dst, src, shift, vlen_enc); break;

    default: assert(false, "%s", NodeClassNames[opcode]);
  }
}

void C2_MacroAssembler::varshiftq(int opcode, XMMRegister dst, XMMRegister src, XMMRegister shift, int vlen_enc, XMMRegister tmp) {
  assert(UseAVX >= 2, "required");
  switch (opcode) {
    case Op_RShiftVL: {
      if (UseAVX > 2) {
        assert(tmp == xnoreg, "not used");
        if (!VM_Version::supports_avx512vl()) {
          vlen_enc = Assembler::AVX_512bit;
        }
        evpsravq(dst, src, shift, vlen_enc);
      } else {
        vmovdqu(tmp, ExternalAddress(StubRoutines::x86::vector_long_sign_mask()));
        vpsrlvq(dst, src, shift, vlen_enc);
        vpsrlvq(tmp, tmp, shift, vlen_enc);
        vpxor(dst, dst, tmp, vlen_enc);
        vpsubq(dst, dst, tmp, vlen_enc);
      }
      break;
    }
    case Op_LShiftVL: {
      assert(tmp == xnoreg, "not used");
      vpsllvq(dst, src, shift, vlen_enc);
      break;
    }
    case Op_URShiftVL: {
      assert(tmp == xnoreg, "not used");
      vpsrlvq(dst, src, shift, vlen_enc);
      break;
    }
    default: assert(false, "%s", NodeClassNames[opcode]);
  }
}

// Variable shift src by shift using vtmp and scratch as TEMPs giving word result in dst
void C2_MacroAssembler::varshiftbw(int opcode, XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len, XMMRegister vtmp) {
  assert(opcode == Op_LShiftVB ||
         opcode == Op_RShiftVB ||
         opcode == Op_URShiftVB, "%s", NodeClassNames[opcode]);
  bool sign = (opcode != Op_URShiftVB);
  assert(vector_len == 0, "required");
  vextendbd(sign, dst, src, 1);
  vpmovzxbd(vtmp, shift, 1);
  varshiftd(opcode, dst, dst, vtmp, 1);
  vpand(dst, dst, ExternalAddress(StubRoutines::x86::vector_int_to_byte_mask()), 1, noreg);
  vextracti128_high(vtmp, dst);
  vpackusdw(dst, dst, vtmp, 0);
}

// Variable shift src by shift using vtmp and scratch as TEMPs giving byte result in dst
void C2_MacroAssembler::evarshiftb(int opcode, XMMRegister dst, XMMRegister src, XMMRegister shift, int vector_len, XMMRegister vtmp) {
  assert(opcode == Op_LShiftVB ||
         opcode == Op_RShiftVB ||
         opcode == Op_URShiftVB, "%s", NodeClassNames[opcode]);
  bool sign = (opcode != Op_URShiftVB);
  int ext_vector_len = vector_len + 1;
  vextendbw(sign, dst, src, ext_vector_len);
  vpmovzxbw(vtmp, shift, ext_vector_len);
  varshiftw(opcode, dst, dst, vtmp, ext_vector_len);
  vpand(dst, dst, ExternalAddress(StubRoutines::x86::vector_short_to_byte_mask()), ext_vector_len, noreg);
  if (vector_len == 0) {
    vextracti128_high(vtmp, dst);
    vpackuswb(dst, dst, vtmp, vector_len);
  } else {
    vextracti64x4_high(vtmp, dst);
    vpackuswb(dst, dst, vtmp, vector_len);
    vpermq(dst, dst, 0xD8, vector_len);
  }
}

void C2_MacroAssembler::insert(BasicType typ, XMMRegister dst, Register val, int idx) {
  switch(typ) {
    case T_BYTE:
      pinsrb(dst, val, idx);
      break;
    case T_SHORT:
      pinsrw(dst, val, idx);
      break;
    case T_INT:
      pinsrd(dst, val, idx);
      break;
    case T_LONG:
      pinsrq(dst, val, idx);
      break;
    default:
      assert(false,"Should not reach here.");
      break;
  }
}

void C2_MacroAssembler::vinsert(BasicType typ, XMMRegister dst, XMMRegister src, Register val, int idx) {
  switch(typ) {
    case T_BYTE:
      vpinsrb(dst, src, val, idx);
      break;
    case T_SHORT:
      vpinsrw(dst, src, val, idx);
      break;
    case T_INT:
      vpinsrd(dst, src, val, idx);
      break;
    case T_LONG:
      vpinsrq(dst, src, val, idx);
      break;
    default:
      assert(false,"Should not reach here.");
      break;
  }
}

void C2_MacroAssembler::vgather8b_masked(BasicType elem_bt, XMMRegister dst,
                                         Register base, Register idx_base,
                                         Register mask, Register mask_idx,
                                         Register rtmp, int vlen_enc) {
  vpxor(dst, dst, dst, vlen_enc);
  if (elem_bt == T_SHORT) {
    for (int i = 0; i < 4; i++) {
      // dst[i] = mask[i] ? src[idx_base[i]] : 0
      Label skip_load;
      btq(mask, mask_idx);
      jccb(Assembler::carryClear, skip_load);
      movl(rtmp, Address(idx_base, i * 4));
      pinsrw(dst, Address(base, rtmp, Address::times_2), i);
      bind(skip_load);
      incq(mask_idx);
    }
  } else {
    assert(elem_bt == T_BYTE, "");
    for (int i = 0; i < 8; i++) {
      // dst[i] = mask[i] ? src[idx_base[i]] : 0
      Label skip_load;
      btq(mask, mask_idx);
      jccb(Assembler::carryClear, skip_load);
      movl(rtmp, Address(idx_base, i * 4));
      pinsrb(dst, Address(base, rtmp), i);
      bind(skip_load);
      incq(mask_idx);
    }
  }
}

void C2_MacroAssembler::vgather8b(BasicType elem_bt, XMMRegister dst,
                                  Register base, Register idx_base,
                                  Register rtmp, int vlen_enc) {
  vpxor(dst, dst, dst, vlen_enc);
  if (elem_bt == T_SHORT) {
    for (int i = 0; i < 4; i++) {
      // dst[i] = src[idx_base[i]]
      movl(rtmp, Address(idx_base, i * 4));
      pinsrw(dst, Address(base, rtmp, Address::times_2), i);
    }
  } else {
    assert(elem_bt == T_BYTE, "");
    for (int i = 0; i < 8; i++) {
      // dst[i] = src[idx_base[i]]
      movl(rtmp, Address(idx_base, i * 4));
      pinsrb(dst, Address(base, rtmp), i);
    }
  }
}

/*
 * Gather using hybrid algorithm, first partially unroll scalar loop
 * to accumulate values from gather indices into a quad-word(64bit) slice.
 * A slice may hold 8 bytes or 4 short values. This is followed by a vector
 * permutation to place the slice into appropriate vector lane
 * locations in destination vector. Following pseudo code describes the
 * algorithm in detail:
 *
 * DST_VEC = ZERO_VEC
 * PERM_INDEX = {0, 1, 2, 3, 4, 5, 6, 7, 8..}
 * TWO_VEC    = {2, 2, 2, 2, 2, 2, 2, 2, 2..}
 * FOREACH_ITER:
 *     TMP_VEC_64 = PICK_SUB_WORDS_FROM_GATHER_INDICES
 *     TEMP_PERM_VEC = PERMUTE TMP_VEC_64 PERM_INDEX
 *     DST_VEC = DST_VEC OR TEMP_PERM_VEC
 *     PERM_INDEX = PERM_INDEX - TWO_VEC
 *
 * With each iteration, doubleword permute indices (0,1) corresponding
 * to gathered quadword gets right shifted by two lane positions.
 *
 */
void C2_MacroAssembler::vgather_subword(BasicType elem_ty, XMMRegister dst,
                                        Register base, Register idx_base,
                                        Register mask, XMMRegister xtmp1,
                                        XMMRegister xtmp2, XMMRegister temp_dst,
                                        Register rtmp, Register mask_idx,
                                        Register length, int vector_len, int vlen_enc) {
  Label GATHER8_LOOP;
  assert(is_subword_type(elem_ty), "");
  movl(length, vector_len);
  vpxor(xtmp1, xtmp1, xtmp1, vlen_enc); // xtmp1 = {0, ...}
  vpxor(dst, dst, dst, vlen_enc); // dst = {0, ...}
  vallones(xtmp2, vlen_enc);
  vpsubd(xtmp2, xtmp1, xtmp2, vlen_enc);
  vpslld(xtmp2, xtmp2, 1, vlen_enc); // xtmp2 = {2, 2, ...}
  load_iota_indices(xtmp1, vector_len * type2aelembytes(elem_ty), T_INT); // xtmp1 = {0, 1, 2, ...}

  bind(GATHER8_LOOP);
    // TMP_VEC_64(temp_dst) = PICK_SUB_WORDS_FROM_GATHER_INDICES
    if (mask == noreg) {
      vgather8b(elem_ty, temp_dst, base, idx_base, rtmp, vlen_enc);
    } else {
      vgather8b_masked(elem_ty, temp_dst, base, idx_base, mask, mask_idx, rtmp, vlen_enc);
    }
    // TEMP_PERM_VEC(temp_dst) = PERMUTE TMP_VEC_64(temp_dst) PERM_INDEX(xtmp1)
    vpermd(temp_dst, xtmp1, temp_dst, vlen_enc == Assembler::AVX_512bit ? vlen_enc : Assembler::AVX_256bit);
    // PERM_INDEX(xtmp1) = PERM_INDEX(xtmp1) - TWO_VEC(xtmp2)
    vpsubd(xtmp1, xtmp1, xtmp2, vlen_enc);
    // DST_VEC = DST_VEC OR TEMP_PERM_VEC
    vpor(dst, dst, temp_dst, vlen_enc);
    addptr(idx_base,  32 >> (type2aelembytes(elem_ty) - 1));
    subl(length, 8 >> (type2aelembytes(elem_ty) - 1));
    jcc(Assembler::notEqual, GATHER8_LOOP);
}

void C2_MacroAssembler::vgather(BasicType typ, XMMRegister dst, Register base, XMMRegister idx, XMMRegister mask, int vector_len) {
  switch(typ) {
    case T_INT:
      vpgatherdd(dst, Address(base, idx, Address::times_4), mask, vector_len);
      break;
    case T_FLOAT:
      vgatherdps(dst, Address(base, idx, Address::times_4), mask, vector_len);
      break;
    case T_LONG:
      vpgatherdq(dst, Address(base, idx, Address::times_8), mask, vector_len);
      break;
    case T_DOUBLE:
      vgatherdpd(dst, Address(base, idx, Address::times_8), mask, vector_len);
      break;
    default:
      assert(false,"Should not reach here.");
      break;
  }
}

void C2_MacroAssembler::evgather(BasicType typ, XMMRegister dst, KRegister mask, Register base, XMMRegister idx, int vector_len) {
  switch(typ) {
    case T_INT:
      evpgatherdd(dst, mask, Address(base, idx, Address::times_4), vector_len);
      break;
    case T_FLOAT:
      evgatherdps(dst, mask, Address(base, idx, Address::times_4), vector_len);
      break;
    case T_LONG:
      evpgatherdq(dst, mask, Address(base, idx, Address::times_8), vector_len);
      break;
    case T_DOUBLE:
      evgatherdpd(dst, mask, Address(base, idx, Address::times_8), vector_len);
      break;
    default:
      assert(false,"Should not reach here.");
      break;
  }
}

void C2_MacroAssembler::evscatter(BasicType typ, Register base, XMMRegister idx, KRegister mask, XMMRegister src, int vector_len) {
  switch(typ) {
    case T_INT:
      evpscatterdd(Address(base, idx, Address::times_4), mask, src, vector_len);
      break;
    case T_FLOAT:
      evscatterdps(Address(base, idx, Address::times_4), mask, src, vector_len);
      break;
    case T_LONG:
      evpscatterdq(Address(base, idx, Address::times_8), mask, src, vector_len);
      break;
    case T_DOUBLE:
      evscatterdpd(Address(base, idx, Address::times_8), mask, src, vector_len);
      break;
    default:
      assert(false,"Should not reach here.");
      break;
  }
}

void C2_MacroAssembler::load_vector_mask(XMMRegister dst, XMMRegister src, int vlen_in_bytes, BasicType elem_bt, bool is_legacy) {
  if (vlen_in_bytes <= 16) {
    pxor (dst, dst);
    psubb(dst, src);
    switch (elem_bt) {
      case T_BYTE:   /* nothing to do */ break;
      case T_SHORT:  pmovsxbw(dst, dst); break;
      case T_INT:    pmovsxbd(dst, dst); break;
      case T_FLOAT:  pmovsxbd(dst, dst); break;
      case T_LONG:   pmovsxbq(dst, dst); break;
      case T_DOUBLE: pmovsxbq(dst, dst); break;

      default: assert(false, "%s", type2name(elem_bt));
    }
  } else {
    assert(!is_legacy || !is_subword_type(elem_bt) || vlen_in_bytes < 64, "");
    int vlen_enc = vector_length_encoding(vlen_in_bytes);

    vpxor (dst, dst, dst, vlen_enc);
    vpsubb(dst, dst, src, is_legacy ? AVX_256bit : vlen_enc);

    switch (elem_bt) {
      case T_BYTE:   /* nothing to do */            break;
      case T_SHORT:  vpmovsxbw(dst, dst, vlen_enc); break;
      case T_INT:    vpmovsxbd(dst, dst, vlen_enc); break;
      case T_FLOAT:  vpmovsxbd(dst, dst, vlen_enc); break;
      case T_LONG:   vpmovsxbq(dst, dst, vlen_enc); break;
      case T_DOUBLE: vpmovsxbq(dst, dst, vlen_enc); break;

      default: assert(false, "%s", type2name(elem_bt));
    }
  }
}

void C2_MacroAssembler::load_vector_mask(KRegister dst, XMMRegister src, XMMRegister xtmp, bool novlbwdq, int vlen_enc) {
  if (novlbwdq) {
    vpmovsxbd(xtmp, src, vlen_enc);
    evpcmpd(dst, k0, xtmp, ExternalAddress(StubRoutines::x86::vector_int_mask_cmp_bits()),
            Assembler::eq, true, vlen_enc, noreg);
  } else {
    vpxor(xtmp, xtmp, xtmp, vlen_enc);
    vpsubb(xtmp, xtmp, src, vlen_enc);
    evpmovb2m(dst, xtmp, vlen_enc);
  }
}

void C2_MacroAssembler::load_vector(BasicType bt, XMMRegister dst, Address src, int vlen_in_bytes) {
  if (is_integral_type(bt)) {
    switch (vlen_in_bytes) {
      case 4:  movdl(dst, src);   break;
      case 8:  movq(dst, src);    break;
      case 16: movdqu(dst, src);  break;
      case 32: vmovdqu(dst, src); break;
      case 64: evmovdqul(dst, src, Assembler::AVX_512bit); break;
      default: ShouldNotReachHere();
    }
  } else {
    switch (vlen_in_bytes) {
      case 4:  movflt(dst, src); break;
      case 8:  movdbl(dst, src); break;
      case 16: movups(dst, src); break;
      case 32: vmovups(dst, src, Assembler::AVX_256bit); break;
      case 64: vmovups(dst, src, Assembler::AVX_512bit); break;
      default: ShouldNotReachHere();
    }
  }
}

void C2_MacroAssembler::load_vector(BasicType bt, XMMRegister dst, AddressLiteral src, int vlen_in_bytes, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    load_vector(bt, dst, as_Address(src), vlen_in_bytes);
  } else {
    lea(rscratch, src);
    load_vector(bt, dst, Address(rscratch, 0), vlen_in_bytes);
  }
}

void C2_MacroAssembler::load_constant_vector(BasicType bt, XMMRegister dst, InternalAddress src, int vlen) {
  int vlen_enc = vector_length_encoding(vlen);
  if (VM_Version::supports_avx()) {
    if (bt == T_LONG) {
      if (VM_Version::supports_avx2()) {
        vpbroadcastq(dst, src, vlen_enc);
      } else {
        vmovddup(dst, src, vlen_enc);
      }
    } else if (bt == T_DOUBLE) {
      if (vlen_enc != Assembler::AVX_128bit) {
        vbroadcastsd(dst, src, vlen_enc, noreg);
      } else {
        vmovddup(dst, src, vlen_enc);
      }
    } else {
      if (VM_Version::supports_avx2() && is_integral_type(bt)) {
        vpbroadcastd(dst, src, vlen_enc);
      } else {
        vbroadcastss(dst, src, vlen_enc);
      }
    }
  } else if (VM_Version::supports_sse3()) {
    movddup(dst, src);
  } else {
    load_vector(bt, dst, src, vlen);
  }
}

void C2_MacroAssembler::load_iota_indices(XMMRegister dst, int vlen_in_bytes, BasicType bt) {
  // The iota indices are ordered by type B/S/I/L/F/D, and the offset between two types is 64.
  int offset = exact_log2(type2aelembytes(bt)) << 6;
  if (is_floating_point_type(bt)) {
    offset += 128;
  }
  ExternalAddress addr(StubRoutines::x86::vector_iota_indices() + offset);
  load_vector(T_BYTE, dst, addr, vlen_in_bytes);
}

// Reductions for vectors of bytes, shorts, ints, longs, floats, and doubles.

void C2_MacroAssembler::reduce_operation_128(BasicType typ, int opcode, XMMRegister dst, XMMRegister src) {
  int vector_len = Assembler::AVX_128bit;

  switch (opcode) {
    case Op_AndReductionV:  pand(dst, src); break;
    case Op_OrReductionV:   por (dst, src); break;
    case Op_XorReductionV:  pxor(dst, src); break;
    case Op_MinReductionV:
      switch (typ) {
        case T_BYTE:        pminsb(dst, src); break;
        case T_SHORT:       pminsw(dst, src); break;
        case T_INT:         pminsd(dst, src); break;
        case T_LONG:        assert(UseAVX > 2, "required");
                            vpminsq(dst, dst, src, Assembler::AVX_128bit); break;
        default:            assert(false, "wrong type");
      }
      break;
    case Op_MaxReductionV:
      switch (typ) {
        case T_BYTE:        pmaxsb(dst, src); break;
        case T_SHORT:       pmaxsw(dst, src); break;
        case T_INT:         pmaxsd(dst, src); break;
        case T_LONG:        assert(UseAVX > 2, "required");
                            vpmaxsq(dst, dst, src, Assembler::AVX_128bit); break;
        default:            assert(false, "wrong type");
      }
      break;
    case Op_AddReductionVF: addss(dst, src); break;
    case Op_AddReductionVD: addsd(dst, src); break;
    case Op_AddReductionVI:
      switch (typ) {
        case T_BYTE:        paddb(dst, src); break;
        case T_SHORT:       paddw(dst, src); break;
        case T_INT:         paddd(dst, src); break;
        default:            assert(false, "wrong type");
      }
      break;
    case Op_AddReductionVL: paddq(dst, src); break;
    case Op_MulReductionVF: mulss(dst, src); break;
    case Op_MulReductionVD: mulsd(dst, src); break;
    case Op_MulReductionVI:
      switch (typ) {
        case T_SHORT:       pmullw(dst, src); break;
        case T_INT:         pmulld(dst, src); break;
        default:            assert(false, "wrong type");
      }
      break;
    case Op_MulReductionVL: assert(UseAVX > 2, "required");
                            evpmullq(dst, dst, src, vector_len); break;
    default:                assert(false, "wrong opcode");
  }
}

void C2_MacroAssembler::unordered_reduce_operation_128(BasicType typ, int opcode, XMMRegister dst, XMMRegister src) {
  switch (opcode) {
    case Op_AddReductionVF: addps(dst, src); break;
    case Op_AddReductionVD: addpd(dst, src); break;
    case Op_MulReductionVF: mulps(dst, src); break;
    case Op_MulReductionVD: mulpd(dst, src); break;
    default:                assert(false, "%s", NodeClassNames[opcode]);
  }
}

void C2_MacroAssembler::reduce_operation_256(BasicType typ, int opcode, XMMRegister dst,  XMMRegister src1, XMMRegister src2) {
  int vector_len = Assembler::AVX_256bit;

  switch (opcode) {
    case Op_AndReductionV:  vpand(dst, src1, src2, vector_len); break;
    case Op_OrReductionV:   vpor (dst, src1, src2, vector_len); break;
    case Op_XorReductionV:  vpxor(dst, src1, src2, vector_len); break;
    case Op_MinReductionV:
      switch (typ) {
        case T_BYTE:        vpminsb(dst, src1, src2, vector_len); break;
        case T_SHORT:       vpminsw(dst, src1, src2, vector_len); break;
        case T_INT:         vpminsd(dst, src1, src2, vector_len); break;
        case T_LONG:        assert(UseAVX > 2, "required");
                            vpminsq(dst, src1, src2, vector_len); break;
        default:            assert(false, "wrong type");
      }
      break;
    case Op_MaxReductionV:
      switch (typ) {
        case T_BYTE:        vpmaxsb(dst, src1, src2, vector_len); break;
        case T_SHORT:       vpmaxsw(dst, src1, src2, vector_len); break;
        case T_INT:         vpmaxsd(dst, src1, src2, vector_len); break;
        case T_LONG:        assert(UseAVX > 2, "required");
                            vpmaxsq(dst, src1, src2, vector_len); break;
        default:            assert(false, "wrong type");
      }
      break;
    case Op_AddReductionVI:
      switch (typ) {
        case T_BYTE:        vpaddb(dst, src1, src2, vector_len); break;
        case T_SHORT:       vpaddw(dst, src1, src2, vector_len); break;
        case T_INT:         vpaddd(dst, src1, src2, vector_len); break;
        default:            assert(false, "wrong type");
      }
      break;
    case Op_AddReductionVL: vpaddq(dst, src1, src2, vector_len); break;
    case Op_MulReductionVI:
      switch (typ) {
        case T_SHORT:       vpmullw(dst, src1, src2, vector_len); break;
        case T_INT:         vpmulld(dst, src1, src2, vector_len); break;
        default:            assert(false, "wrong type");
      }
      break;
    case Op_MulReductionVL: evpmullq(dst, src1, src2, vector_len); break;
    default:                assert(false, "wrong opcode");
  }
}

void C2_MacroAssembler::unordered_reduce_operation_256(BasicType typ, int opcode, XMMRegister dst,  XMMRegister src1, XMMRegister src2) {
  int vector_len = Assembler::AVX_256bit;

  switch (opcode) {
    case Op_AddReductionVF: vaddps(dst, src1, src2, vector_len); break;
    case Op_AddReductionVD: vaddpd(dst, src1, src2, vector_len); break;
    case Op_MulReductionVF: vmulps(dst, src1, src2, vector_len); break;
    case Op_MulReductionVD: vmulpd(dst, src1, src2, vector_len); break;
    default:                assert(false, "%s", NodeClassNames[opcode]);
  }
}

void C2_MacroAssembler::reduce_fp(int opcode, int vlen,
                                  XMMRegister dst, XMMRegister src,
                                  XMMRegister vtmp1, XMMRegister vtmp2) {
  switch (opcode) {
    case Op_AddReductionVF:
    case Op_MulReductionVF:
      reduceF(opcode, vlen, dst, src, vtmp1, vtmp2);
      break;

    case Op_AddReductionVD:
    case Op_MulReductionVD:
      reduceD(opcode, vlen, dst, src, vtmp1, vtmp2);
      break;

    default: assert(false, "wrong opcode");
  }
}

void C2_MacroAssembler::unordered_reduce_fp(int opcode, int vlen,
                                            XMMRegister dst, XMMRegister src,
                                            XMMRegister vtmp1, XMMRegister vtmp2) {
  switch (opcode) {
    case Op_AddReductionVF:
    case Op_MulReductionVF:
      unorderedReduceF(opcode, vlen, dst, src, vtmp1, vtmp2);
      break;

    case Op_AddReductionVD:
    case Op_MulReductionVD:
      unorderedReduceD(opcode, vlen, dst, src, vtmp1, vtmp2);
      break;

    default: assert(false, "%s", NodeClassNames[opcode]);
  }
}

void C2_MacroAssembler::reduceB(int opcode, int vlen,
                             Register dst, Register src1, XMMRegister src2,
                             XMMRegister vtmp1, XMMRegister vtmp2) {
  switch (vlen) {
    case  8: reduce8B (opcode, dst, src1, src2, vtmp1, vtmp2); break;
    case 16: reduce16B(opcode, dst, src1, src2, vtmp1, vtmp2); break;
    case 32: reduce32B(opcode, dst, src1, src2, vtmp1, vtmp2); break;
    case 64: reduce64B(opcode, dst, src1, src2, vtmp1, vtmp2); break;

    default: assert(false, "wrong vector length");
  }
}

void C2_MacroAssembler::mulreduceB(int opcode, int vlen,
                             Register dst, Register src1, XMMRegister src2,
                             XMMRegister vtmp1, XMMRegister vtmp2) {
  switch (vlen) {
    case  8: mulreduce8B (opcode, dst, src1, src2, vtmp1, vtmp2); break;
    case 16: mulreduce16B(opcode, dst, src1, src2, vtmp1, vtmp2); break;
    case 32: mulreduce32B(opcode, dst, src1, src2, vtmp1, vtmp2); break;
    case 64: mulreduce64B(opcode, dst, src1, src2, vtmp1, vtmp2); break;

    default: assert(false, "wrong vector length");
  }
}

void C2_MacroAssembler::reduceS(int opcode, int vlen,
                             Register dst, Register src1, XMMRegister src2,
                             XMMRegister vtmp1, XMMRegister vtmp2) {
  switch (vlen) {
    case  4: reduce4S (opcode, dst, src1, src2, vtmp1, vtmp2); break;
    case  8: reduce8S (opcode, dst, src1, src2, vtmp1, vtmp2); break;
    case 16: reduce16S(opcode, dst, src1, src2, vtmp1, vtmp2); break;
    case 32: reduce32S(opcode, dst, src1, src2, vtmp1, vtmp2); break;

    default: assert(false, "wrong vector length");
  }
}

void C2_MacroAssembler::reduceI(int opcode, int vlen,
                             Register dst, Register src1, XMMRegister src2,
                             XMMRegister vtmp1, XMMRegister vtmp2) {
  switch (vlen) {
    case  2: reduce2I (opcode, dst, src1, src2, vtmp1, vtmp2); break;
    case  4: reduce4I (opcode, dst, src1, src2, vtmp1, vtmp2); break;
    case  8: reduce8I (opcode, dst, src1, src2, vtmp1, vtmp2); break;
    case 16: reduce16I(opcode, dst, src1, src2, vtmp1, vtmp2); break;

    default: assert(false, "wrong vector length");
  }
}

void C2_MacroAssembler::reduceL(int opcode, int vlen,
                             Register dst, Register src1, XMMRegister src2,
                             XMMRegister vtmp1, XMMRegister vtmp2) {
  switch (vlen) {
    case 2: reduce2L(opcode, dst, src1, src2, vtmp1, vtmp2); break;
    case 4: reduce4L(opcode, dst, src1, src2, vtmp1, vtmp2); break;
    case 8: reduce8L(opcode, dst, src1, src2, vtmp1, vtmp2); break;

    default: assert(false, "wrong vector length");
  }
}

void C2_MacroAssembler::reduceF(int opcode, int vlen, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2) {
  switch (vlen) {
    case 2:
      assert(vtmp2 == xnoreg, "");
      reduce2F(opcode, dst, src, vtmp1);
      break;
    case 4:
      assert(vtmp2 == xnoreg, "");
      reduce4F(opcode, dst, src, vtmp1);
      break;
    case 8:
      reduce8F(opcode, dst, src, vtmp1, vtmp2);
      break;
    case 16:
      reduce16F(opcode, dst, src, vtmp1, vtmp2);
      break;
    default: assert(false, "wrong vector length");
  }
}

void C2_MacroAssembler::reduceD(int opcode, int vlen, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2) {
  switch (vlen) {
    case 2:
      assert(vtmp2 == xnoreg, "");
      reduce2D(opcode, dst, src, vtmp1);
      break;
    case 4:
      reduce4D(opcode, dst, src, vtmp1, vtmp2);
      break;
    case 8:
      reduce8D(opcode, dst, src, vtmp1, vtmp2);
      break;
    default: assert(false, "wrong vector length");
  }
}

void C2_MacroAssembler::unorderedReduceF(int opcode, int vlen, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2) {
  switch (vlen) {
    case 2:
      assert(vtmp1 == xnoreg, "");
      assert(vtmp2 == xnoreg, "");
      unorderedReduce2F(opcode, dst, src);
      break;
    case 4:
      assert(vtmp2 == xnoreg, "");
      unorderedReduce4F(opcode, dst, src, vtmp1);
      break;
    case 8:
      unorderedReduce8F(opcode, dst, src, vtmp1, vtmp2);
      break;
    case 16:
      unorderedReduce16F(opcode, dst, src, vtmp1, vtmp2);
      break;
    default: assert(false, "wrong vector length");
  }
}

void C2_MacroAssembler::unorderedReduceD(int opcode, int vlen, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2) {
  switch (vlen) {
    case 2:
      assert(vtmp1 == xnoreg, "");
      assert(vtmp2 == xnoreg, "");
      unorderedReduce2D(opcode, dst, src);
      break;
    case 4:
      assert(vtmp2 == xnoreg, "");
      unorderedReduce4D(opcode, dst, src, vtmp1);
      break;
    case 8:
      unorderedReduce8D(opcode, dst, src, vtmp1, vtmp2);
      break;
    default: assert(false, "wrong vector length");
  }
}

void C2_MacroAssembler::reduce2I(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2) {
  if (opcode == Op_AddReductionVI) {
    if (vtmp1 != src2) {
      movdqu(vtmp1, src2);
    }
    phaddd(vtmp1, vtmp1);
  } else {
    pshufd(vtmp1, src2, 0x1);
    reduce_operation_128(T_INT, opcode, vtmp1, src2);
  }
  movdl(vtmp2, src1);
  reduce_operation_128(T_INT, opcode, vtmp1, vtmp2);
  movdl(dst, vtmp1);
}

void C2_MacroAssembler::reduce4I(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2) {
  if (opcode == Op_AddReductionVI) {
    if (vtmp1 != src2) {
      movdqu(vtmp1, src2);
    }
    phaddd(vtmp1, src2);
    reduce2I(opcode, dst, src1, vtmp1, vtmp1, vtmp2);
  } else {
    pshufd(vtmp2, src2, 0xE);
    reduce_operation_128(T_INT, opcode, vtmp2, src2);
    reduce2I(opcode, dst, src1, vtmp2, vtmp1, vtmp2);
  }
}

void C2_MacroAssembler::reduce8I(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2) {
  if (opcode == Op_AddReductionVI) {
    vphaddd(vtmp1, src2, src2, Assembler::AVX_256bit);
    vextracti128_high(vtmp2, vtmp1);
    vpaddd(vtmp1, vtmp1, vtmp2, Assembler::AVX_128bit);
    reduce2I(opcode, dst, src1, vtmp1, vtmp1, vtmp2);
  } else {
    vextracti128_high(vtmp1, src2);
    reduce_operation_128(T_INT, opcode, vtmp1, src2);
    reduce4I(opcode, dst, src1, vtmp1, vtmp1, vtmp2);
  }
}

void C2_MacroAssembler::reduce16I(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2) {
  vextracti64x4_high(vtmp2, src2);
  reduce_operation_256(T_INT, opcode, vtmp2, vtmp2, src2);
  reduce8I(opcode, dst, src1, vtmp2, vtmp1, vtmp2);
}

void C2_MacroAssembler::reduce8B(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2) {
  pshufd(vtmp2, src2, 0x1);
  reduce_operation_128(T_BYTE, opcode, vtmp2, src2);
  movdqu(vtmp1, vtmp2);
  psrldq(vtmp1, 2);
  reduce_operation_128(T_BYTE, opcode, vtmp1, vtmp2);
  movdqu(vtmp2, vtmp1);
  psrldq(vtmp2, 1);
  reduce_operation_128(T_BYTE, opcode, vtmp1, vtmp2);
  movdl(vtmp2, src1);
  pmovsxbd(vtmp1, vtmp1);
  reduce_operation_128(T_INT, opcode, vtmp1, vtmp2);
  pextrb(dst, vtmp1, 0x0);
  movsbl(dst, dst);
}

void C2_MacroAssembler::reduce16B(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2) {
  pshufd(vtmp1, src2, 0xE);
  reduce_operation_128(T_BYTE, opcode, vtmp1, src2);
  reduce8B(opcode, dst, src1, vtmp1, vtmp1, vtmp2);
}

void C2_MacroAssembler::reduce32B(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2) {
  vextracti128_high(vtmp2, src2);
  reduce_operation_128(T_BYTE, opcode, vtmp2, src2);
  reduce16B(opcode, dst, src1, vtmp2, vtmp1, vtmp2);
}

void C2_MacroAssembler::reduce64B(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2) {
  vextracti64x4_high(vtmp1, src2);
  reduce_operation_256(T_BYTE, opcode, vtmp1, vtmp1, src2);
  reduce32B(opcode, dst, src1, vtmp1, vtmp1, vtmp2);
}

void C2_MacroAssembler::mulreduce8B(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2) {
  pmovsxbw(vtmp2, src2);
  reduce8S(opcode, dst, src1, vtmp2, vtmp1, vtmp2);
}

void C2_MacroAssembler::mulreduce16B(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2) {
  if (UseAVX > 1) {
    int vector_len = Assembler::AVX_256bit;
    vpmovsxbw(vtmp1, src2, vector_len);
    reduce16S(opcode, dst, src1, vtmp1, vtmp1, vtmp2);
  } else {
    pmovsxbw(vtmp2, src2);
    reduce8S(opcode, dst, src1, vtmp2, vtmp1, vtmp2);
    pshufd(vtmp2, src2, 0x1);
    pmovsxbw(vtmp2, src2);
    reduce8S(opcode, dst, dst, vtmp2, vtmp1, vtmp2);
  }
}

void C2_MacroAssembler::mulreduce32B(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2) {
  if (UseAVX > 2 && VM_Version::supports_avx512bw()) {
    int vector_len = Assembler::AVX_512bit;
    vpmovsxbw(vtmp1, src2, vector_len);
    reduce32S(opcode, dst, src1, vtmp1, vtmp1, vtmp2);
  } else {
    assert(UseAVX >= 2,"Should not reach here.");
    mulreduce16B(opcode, dst, src1, src2, vtmp1, vtmp2);
    vextracti128_high(vtmp2, src2);
    mulreduce16B(opcode, dst, dst, vtmp2, vtmp1, vtmp2);
  }
}

void C2_MacroAssembler::mulreduce64B(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2) {
  mulreduce32B(opcode, dst, src1, src2, vtmp1, vtmp2);
  vextracti64x4_high(vtmp2, src2);
  mulreduce32B(opcode, dst, dst, vtmp2, vtmp1, vtmp2);
}

void C2_MacroAssembler::reduce4S(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2) {
  if (opcode == Op_AddReductionVI) {
    if (vtmp1 != src2) {
      movdqu(vtmp1, src2);
    }
    phaddw(vtmp1, vtmp1);
    phaddw(vtmp1, vtmp1);
  } else {
    pshufd(vtmp2, src2, 0x1);
    reduce_operation_128(T_SHORT, opcode, vtmp2, src2);
    movdqu(vtmp1, vtmp2);
    psrldq(vtmp1, 2);
    reduce_operation_128(T_SHORT, opcode, vtmp1, vtmp2);
  }
  movdl(vtmp2, src1);
  pmovsxwd(vtmp1, vtmp1);
  reduce_operation_128(T_INT, opcode, vtmp1, vtmp2);
  pextrw(dst, vtmp1, 0x0);
  movswl(dst, dst);
}

void C2_MacroAssembler::reduce8S(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2) {
  if (opcode == Op_AddReductionVI) {
    if (vtmp1 != src2) {
      movdqu(vtmp1, src2);
    }
    phaddw(vtmp1, src2);
  } else {
    pshufd(vtmp1, src2, 0xE);
    reduce_operation_128(T_SHORT, opcode, vtmp1, src2);
  }
  reduce4S(opcode, dst, src1, vtmp1, vtmp1, vtmp2);
}

void C2_MacroAssembler::reduce16S(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2) {
  if (opcode == Op_AddReductionVI) {
    int vector_len = Assembler::AVX_256bit;
    vphaddw(vtmp2, src2, src2, vector_len);
    vpermq(vtmp2, vtmp2, 0xD8, vector_len);
  } else {
    vextracti128_high(vtmp2, src2);
    reduce_operation_128(T_SHORT, opcode, vtmp2, src2);
  }
  reduce8S(opcode, dst, src1, vtmp2, vtmp1, vtmp2);
}

void C2_MacroAssembler::reduce32S(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2) {
  int vector_len = Assembler::AVX_256bit;
  vextracti64x4_high(vtmp1, src2);
  reduce_operation_256(T_SHORT, opcode, vtmp1, vtmp1, src2);
  reduce16S(opcode, dst, src1, vtmp1, vtmp1, vtmp2);
}

void C2_MacroAssembler::reduce2L(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2) {
  pshufd(vtmp2, src2, 0xE);
  reduce_operation_128(T_LONG, opcode, vtmp2, src2);
  movdq(vtmp1, src1);
  reduce_operation_128(T_LONG, opcode, vtmp1, vtmp2);
  movdq(dst, vtmp1);
}

void C2_MacroAssembler::reduce4L(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2) {
  vextracti128_high(vtmp1, src2);
  reduce_operation_128(T_LONG, opcode, vtmp1, src2);
  reduce2L(opcode, dst, src1, vtmp1, vtmp1, vtmp2);
}

void C2_MacroAssembler::reduce8L(int opcode, Register dst, Register src1, XMMRegister src2, XMMRegister vtmp1, XMMRegister vtmp2) {
  vextracti64x4_high(vtmp2, src2);
  reduce_operation_256(T_LONG, opcode, vtmp2, vtmp2, src2);
  reduce4L(opcode, dst, src1, vtmp2, vtmp1, vtmp2);
}

void C2_MacroAssembler::genmask(KRegister dst, Register len, Register temp) {
  mov64(temp, -1L);
  bzhiq(temp, temp, len);
  kmovql(dst, temp);
}

void C2_MacroAssembler::reduce2F(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp) {
  reduce_operation_128(T_FLOAT, opcode, dst, src);
  pshufd(vtmp, src, 0x1);
  reduce_operation_128(T_FLOAT, opcode, dst, vtmp);
}

void C2_MacroAssembler::reduce4F(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp) {
  reduce2F(opcode, dst, src, vtmp);
  pshufd(vtmp, src, 0x2);
  reduce_operation_128(T_FLOAT, opcode, dst, vtmp);
  pshufd(vtmp, src, 0x3);
  reduce_operation_128(T_FLOAT, opcode, dst, vtmp);
}

void C2_MacroAssembler::reduce8F(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2) {
  reduce4F(opcode, dst, src, vtmp2);
  vextractf128_high(vtmp2, src);
  reduce4F(opcode, dst, vtmp2, vtmp1);
}

void C2_MacroAssembler::reduce16F(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2) {
  reduce8F(opcode, dst, src, vtmp1, vtmp2);
  vextracti64x4_high(vtmp1, src);
  reduce8F(opcode, dst, vtmp1, vtmp1, vtmp2);
}

void C2_MacroAssembler::unorderedReduce2F(int opcode, XMMRegister dst, XMMRegister src) {
  pshufd(dst, src, 0x1);
  reduce_operation_128(T_FLOAT, opcode, dst, src);
}

void C2_MacroAssembler::unorderedReduce4F(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp) {
  pshufd(vtmp, src, 0xE);
  unordered_reduce_operation_128(T_FLOAT, opcode, vtmp, src);
  unorderedReduce2F(opcode, dst, vtmp);
}

void C2_MacroAssembler::unorderedReduce8F(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2) {
  vextractf128_high(vtmp1, src);
  unordered_reduce_operation_128(T_FLOAT, opcode, vtmp1, src);
  unorderedReduce4F(opcode, dst, vtmp1, vtmp2);
}

void C2_MacroAssembler::unorderedReduce16F(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2) {
  vextractf64x4_high(vtmp2, src);
  unordered_reduce_operation_256(T_FLOAT, opcode, vtmp2, vtmp2, src);
  unorderedReduce8F(opcode, dst, vtmp2, vtmp1, vtmp2);
}

void C2_MacroAssembler::reduce2D(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp) {
  reduce_operation_128(T_DOUBLE, opcode, dst, src);
  pshufd(vtmp, src, 0xE);
  reduce_operation_128(T_DOUBLE, opcode, dst, vtmp);
}

void C2_MacroAssembler::reduce4D(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2) {
  reduce2D(opcode, dst, src, vtmp2);
  vextractf128_high(vtmp2, src);
  reduce2D(opcode, dst, vtmp2, vtmp1);
}

void C2_MacroAssembler::reduce8D(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2) {
  reduce4D(opcode, dst, src, vtmp1, vtmp2);
  vextracti64x4_high(vtmp1, src);
  reduce4D(opcode, dst, vtmp1, vtmp1, vtmp2);
}

void C2_MacroAssembler::unorderedReduce2D(int opcode, XMMRegister dst, XMMRegister src) {
  pshufd(dst, src, 0xE);
  reduce_operation_128(T_DOUBLE, opcode, dst, src);
}

void C2_MacroAssembler::unorderedReduce4D(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp) {
  vextractf128_high(vtmp, src);
  unordered_reduce_operation_128(T_DOUBLE, opcode, vtmp, src);
  unorderedReduce2D(opcode, dst, vtmp);
}

void C2_MacroAssembler::unorderedReduce8D(int opcode, XMMRegister dst, XMMRegister src, XMMRegister vtmp1, XMMRegister vtmp2) {
  vextractf64x4_high(vtmp2, src);
  unordered_reduce_operation_256(T_DOUBLE, opcode, vtmp2, vtmp2, src);
  unorderedReduce4D(opcode, dst, vtmp2, vtmp1);
}

void C2_MacroAssembler::evmovdqu(BasicType type, KRegister kmask, XMMRegister dst, Address src, bool merge, int vector_len) {
  MacroAssembler::evmovdqu(type, kmask, dst, src, merge, vector_len);
}

void C2_MacroAssembler::evmovdqu(BasicType type, KRegister kmask, Address dst, XMMRegister src, bool merge, int vector_len) {
  MacroAssembler::evmovdqu(type, kmask, dst, src, merge, vector_len);
}

void C2_MacroAssembler::evmovdqu(BasicType type, KRegister kmask, XMMRegister dst, XMMRegister src, bool merge, int vector_len) {
  MacroAssembler::evmovdqu(type, kmask, dst, src, merge, vector_len);
}

void C2_MacroAssembler::vmovmask(BasicType elem_bt, XMMRegister dst, Address src, XMMRegister mask,
                                 int vec_enc) {
  switch(elem_bt) {
    case T_INT:
    case T_FLOAT:
      vmaskmovps(dst, src, mask, vec_enc);
      break;
    case T_LONG:
    case T_DOUBLE:
      vmaskmovpd(dst, src, mask, vec_enc);
      break;
    default:
      fatal("Unsupported type %s", type2name(elem_bt));
      break;
  }
}

void C2_MacroAssembler::vmovmask(BasicType elem_bt, Address dst, XMMRegister src, XMMRegister mask,
                                 int vec_enc) {
  switch(elem_bt) {
    case T_INT:
    case T_FLOAT:
      vmaskmovps(dst, src, mask, vec_enc);
      break;
    case T_LONG:
    case T_DOUBLE:
      vmaskmovpd(dst, src, mask, vec_enc);
      break;
    default:
      fatal("Unsupported type %s", type2name(elem_bt));
      break;
  }
}

void C2_MacroAssembler::reduceFloatMinMax(int opcode, int vlen, bool is_dst_valid,
                                          XMMRegister dst, XMMRegister src,
                                          XMMRegister tmp, XMMRegister atmp, XMMRegister btmp,
                                          XMMRegister xmm_0, XMMRegister xmm_1) {
  const int permconst[] = {1, 14};
  XMMRegister wsrc = src;
  XMMRegister wdst = xmm_0;
  XMMRegister wtmp = (xmm_1 == xnoreg) ? xmm_0: xmm_1;

  int vlen_enc = Assembler::AVX_128bit;
  if (vlen == 16) {
    vlen_enc = Assembler::AVX_256bit;
  }

  for (int i = log2(vlen) - 1; i >=0; i--) {
    if (i == 0 && !is_dst_valid) {
      wdst = dst;
    }
    if (i == 3) {
      vextracti64x4_high(wtmp, wsrc);
    } else if (i == 2) {
      vextracti128_high(wtmp, wsrc);
    } else { // i = [0,1]
      vpermilps(wtmp, wsrc, permconst[i], vlen_enc);
    }

    if (VM_Version::supports_avx10_2()) {
      vminmax_fp(opcode, T_FLOAT, wdst, k0, wtmp, wsrc, vlen_enc);
    } else {
      vminmax_fp(opcode, T_FLOAT, wdst, wtmp, wsrc, tmp, atmp, btmp, vlen_enc);
    }
    wsrc = wdst;
    vlen_enc = Assembler::AVX_128bit;
  }
  if (is_dst_valid) {
    if (VM_Version::supports_avx10_2()) {
      vminmax_fp(opcode, T_FLOAT, dst, k0, wdst, dst, Assembler::AVX_128bit);
    } else {
      vminmax_fp(opcode, T_FLOAT, dst, wdst, dst, tmp, atmp, btmp, Assembler::AVX_128bit);
    }
  }
}

void C2_MacroAssembler::reduceDoubleMinMax(int opcode, int vlen, bool is_dst_valid, XMMRegister dst, XMMRegister src,
                                        XMMRegister tmp, XMMRegister atmp, XMMRegister btmp,
                                        XMMRegister xmm_0, XMMRegister xmm_1) {
  XMMRegister wsrc = src;
  XMMRegister wdst = xmm_0;
  XMMRegister wtmp = (xmm_1 == xnoreg) ? xmm_0: xmm_1;
  int vlen_enc = Assembler::AVX_128bit;
  if (vlen == 8) {
    vlen_enc = Assembler::AVX_256bit;
  }
  for (int i = log2(vlen) - 1; i >=0; i--) {
    if (i == 0 && !is_dst_valid) {
      wdst = dst;
    }
    if (i == 1) {
      vextracti128_high(wtmp, wsrc);
    } else if (i == 2) {
      vextracti64x4_high(wtmp, wsrc);
    } else {
      assert(i == 0, "%d", i);
      vpermilpd(wtmp, wsrc, 1, vlen_enc);
    }

    if (VM_Version::supports_avx10_2()) {
      vminmax_fp(opcode, T_DOUBLE, wdst, k0, wtmp, wsrc, vlen_enc);
    } else {
      vminmax_fp(opcode, T_DOUBLE, wdst, wtmp, wsrc, tmp, atmp, btmp, vlen_enc);
    }

    wsrc = wdst;
    vlen_enc = Assembler::AVX_128bit;
  }

  if (is_dst_valid) {
    if (VM_Version::supports_avx10_2()) {
      vminmax_fp(opcode, T_DOUBLE, dst, k0, wdst, dst, Assembler::AVX_128bit);
    } else {
      vminmax_fp(opcode, T_DOUBLE, dst, wdst, dst, tmp, atmp, btmp, Assembler::AVX_128bit);
    }
  }
}

void C2_MacroAssembler::extract(BasicType bt, Register dst, XMMRegister src, int idx) {
  switch (bt) {
    case T_BYTE:  pextrb(dst, src, idx); break;
    case T_SHORT: pextrw(dst, src, idx); break;
    case T_INT:   pextrd(dst, src, idx); break;
    case T_LONG:  pextrq(dst, src, idx); break;

    default:
      assert(false,"Should not reach here.");
      break;
  }
}

XMMRegister C2_MacroAssembler::get_lane(BasicType typ, XMMRegister dst, XMMRegister src, int elemindex) {
  int esize =  type2aelembytes(typ);
  int elem_per_lane = 16/esize;
  int lane = elemindex / elem_per_lane;
  int eindex = elemindex % elem_per_lane;

  if (lane >= 2) {
    assert(UseAVX > 2, "required");
    vextractf32x4(dst, src, lane & 3);
    return dst;
  } else if (lane > 0) {
    assert(UseAVX > 0, "required");
    vextractf128(dst, src, lane);
    return dst;
  } else {
    return src;
  }
}

void C2_MacroAssembler::movsxl(BasicType typ, Register dst) {
  if (typ == T_BYTE) {
    movsbl(dst, dst);
  } else if (typ == T_SHORT) {
    movswl(dst, dst);
  }
}

void C2_MacroAssembler::get_elem(BasicType typ, Register dst, XMMRegister src, int elemindex) {
  int esize =  type2aelembytes(typ);
  int elem_per_lane = 16/esize;
  int eindex = elemindex % elem_per_lane;
  assert(is_integral_type(typ),"required");

  if (eindex == 0) {
    if (typ == T_LONG) {
      movq(dst, src);
    } else {
      movdl(dst, src);
      movsxl(typ, dst);
    }
  } else {
    extract(typ, dst, src, eindex);
    movsxl(typ, dst);
  }
}

void C2_MacroAssembler::get_elem(BasicType typ, XMMRegister dst, XMMRegister src, int elemindex, XMMRegister vtmp) {
  int esize =  type2aelembytes(typ);
  int elem_per_lane = 16/esize;
  int eindex = elemindex % elem_per_lane;
  assert((typ == T_FLOAT || typ == T_DOUBLE),"required");

  if (eindex == 0) {
    movq(dst, src);
  } else {
    if (typ == T_FLOAT) {
      if (UseAVX == 0) {
        movdqu(dst, src);
        shufps(dst, dst, eindex);
      } else {
        vshufps(dst, src, src, eindex, Assembler::AVX_128bit);
      }
    } else {
      if (UseAVX == 0) {
        movdqu(dst, src);
        psrldq(dst, eindex*esize);
      } else {
        vpsrldq(dst, src, eindex*esize, Assembler::AVX_128bit);
      }
      movq(dst, dst);
    }
  }
  // Zero upper bits
  if (typ == T_FLOAT) {
    if (UseAVX == 0) {
      assert(vtmp != xnoreg, "required.");
      movdqu(vtmp, ExternalAddress(StubRoutines::x86::vector_32_bit_mask()), noreg);
      pand(dst, vtmp);
    } else {
      vpand(dst, dst, ExternalAddress(StubRoutines::x86::vector_32_bit_mask()), Assembler::AVX_128bit, noreg);
    }
  }
}

void C2_MacroAssembler::evpcmp(BasicType typ, KRegister kdmask, KRegister ksmask, XMMRegister src1, XMMRegister src2, int comparison, int vector_len) {
  switch(typ) {
    case T_BYTE:
    case T_BOOLEAN:
      evpcmpb(kdmask, ksmask, src1, src2, comparison, /*signed*/ true, vector_len);
      break;
    case T_SHORT:
    case T_CHAR:
      evpcmpw(kdmask, ksmask, src1, src2, comparison, /*signed*/ true, vector_len);
      break;
    case T_INT:
    case T_FLOAT:
      evpcmpd(kdmask, ksmask, src1, src2, comparison, /*signed*/ true, vector_len);
      break;
    case T_LONG:
    case T_DOUBLE:
      evpcmpq(kdmask, ksmask, src1, src2, comparison, /*signed*/ true, vector_len);
      break;
    default:
      assert(false,"Should not reach here.");
      break;
  }
}

void C2_MacroAssembler::evpcmp(BasicType typ, KRegister kdmask, KRegister ksmask, XMMRegister src1, AddressLiteral src2, int comparison, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src2), "missing");

  switch(typ) {
    case T_BOOLEAN:
    case T_BYTE:
      evpcmpb(kdmask, ksmask, src1, src2, comparison, /*signed*/ true, vector_len, rscratch);
      break;
    case T_CHAR:
    case T_SHORT:
      evpcmpw(kdmask, ksmask, src1, src2, comparison, /*signed*/ true, vector_len, rscratch);
      break;
    case T_INT:
    case T_FLOAT:
      evpcmpd(kdmask, ksmask, src1, src2, comparison, /*signed*/ true, vector_len, rscratch);
      break;
    case T_LONG:
    case T_DOUBLE:
      evpcmpq(kdmask, ksmask, src1, src2, comparison, /*signed*/ true, vector_len, rscratch);
      break;
    default:
      assert(false,"Should not reach here.");
      break;
  }
}

void C2_MacroAssembler::evpblend(BasicType typ, XMMRegister dst, KRegister kmask, XMMRegister src1, XMMRegister src2, bool merge, int vector_len) {
  switch(typ) {
    case T_BYTE:
      evpblendmb(dst, kmask, src1, src2, merge, vector_len);
      break;
    case T_SHORT:
      evpblendmw(dst, kmask, src1, src2, merge, vector_len);
      break;
    case T_INT:
    case T_FLOAT:
      evpblendmd(dst, kmask, src1, src2, merge, vector_len);
      break;
    case T_LONG:
    case T_DOUBLE:
      evpblendmq(dst, kmask, src1, src2, merge, vector_len);
      break;
    default:
      assert(false,"Should not reach here.");
      break;
  }
}

void C2_MacroAssembler::vectortest(BasicType bt, XMMRegister src1, XMMRegister src2, XMMRegister vtmp, int vlen_in_bytes) {
  assert(vlen_in_bytes <= 32, "");
  int esize = type2aelembytes(bt);
  if (vlen_in_bytes == 32) {
    assert(vtmp == xnoreg, "required.");
    if (esize >= 4) {
      vtestps(src1, src2, AVX_256bit);
    } else {
      vptest(src1, src2, AVX_256bit);
    }
    return;
  }
  if (vlen_in_bytes < 16) {
    // Duplicate the lower part to fill the whole register,
    // Don't need to do so for src2
    assert(vtmp != xnoreg, "required");
    int shuffle_imm = (vlen_in_bytes == 4) ? 0x00 : 0x04;
    pshufd(vtmp, src1, shuffle_imm);
  } else {
    assert(vtmp == xnoreg, "required");
    vtmp = src1;
  }
  if (esize >= 4 && VM_Version::supports_avx()) {
    vtestps(vtmp, src2, AVX_128bit);
  } else {
    ptest(vtmp, src2);
  }
}

void C2_MacroAssembler::vpadd(BasicType elem_bt, XMMRegister dst, XMMRegister src1, XMMRegister src2, int vlen_enc) {
#ifdef ASSERT
  bool is_bw = ((elem_bt == T_BYTE) || (elem_bt == T_SHORT));
  bool is_bw_supported = VM_Version::supports_avx512bw();
  if (is_bw && !is_bw_supported) {
    assert(vlen_enc != Assembler::AVX_512bit, "required");
    assert((dst->encoding() < 16) && (src1->encoding() < 16) && (src2->encoding() < 16),
           "XMM register should be 0-15");
  }
#endif // ASSERT
  switch (elem_bt) {
    case T_BYTE: vpaddb(dst, src1, src2, vlen_enc); return;
    case T_SHORT: vpaddw(dst, src1, src2, vlen_enc); return;
    case T_INT: vpaddd(dst, src1, src2, vlen_enc); return;
    case T_FLOAT: vaddps(dst, src1, src2, vlen_enc); return;
    case T_LONG: vpaddq(dst, src1, src2, vlen_enc); return;
    case T_DOUBLE: vaddpd(dst, src1, src2, vlen_enc); return;
    default: fatal("Unsupported type %s", type2name(elem_bt)); return;
  }
}

void C2_MacroAssembler::vpbroadcast(BasicType elem_bt, XMMRegister dst, Register src, int vlen_enc) {
  assert(UseAVX >= 2, "required");
  bool is_bw = ((elem_bt == T_BYTE) || (elem_bt == T_SHORT));
  bool is_vl = vlen_enc != Assembler::AVX_512bit;
  if ((UseAVX > 2) &&
      (!is_bw || VM_Version::supports_avx512bw()) &&
      (!is_vl || VM_Version::supports_avx512vl())) {
    switch (elem_bt) {
      case T_BYTE: evpbroadcastb(dst, src, vlen_enc); return;
      case T_SHORT: evpbroadcastw(dst, src, vlen_enc); return;
      case T_FLOAT: case T_INT: evpbroadcastd(dst, src, vlen_enc); return;
      case T_DOUBLE: case T_LONG: evpbroadcastq(dst, src, vlen_enc); return;
      default: fatal("Unsupported type %s", type2name(elem_bt)); return;
    }
  } else {
    assert(vlen_enc != Assembler::AVX_512bit, "required");
    assert((dst->encoding() < 16),"XMM register should be 0-15");
    switch (elem_bt) {
      case T_BYTE: movdl(dst, src); vpbroadcastb(dst, dst, vlen_enc); return;
      case T_SHORT: movdl(dst, src); vpbroadcastw(dst, dst, vlen_enc); return;
      case T_INT: movdl(dst, src); vpbroadcastd(dst, dst, vlen_enc); return;
      case T_FLOAT: movdl(dst, src); vbroadcastss(dst, dst, vlen_enc); return;
      case T_LONG: movdq(dst, src); vpbroadcastq(dst, dst, vlen_enc); return;
      case T_DOUBLE: movdq(dst, src); vbroadcastsd(dst, dst, vlen_enc); return;
      default: fatal("Unsupported type %s", type2name(elem_bt)); return;
    }
  }
}

void C2_MacroAssembler::vconvert_b2x(BasicType to_elem_bt, XMMRegister dst, XMMRegister src, int vlen_enc) {
  switch (to_elem_bt) {
    case T_SHORT:
      vpmovsxbw(dst, src, vlen_enc);
      break;
    case T_INT:
      vpmovsxbd(dst, src, vlen_enc);
      break;
    case T_FLOAT:
      vpmovsxbd(dst, src, vlen_enc);
      vcvtdq2ps(dst, dst, vlen_enc);
      break;
    case T_LONG:
      vpmovsxbq(dst, src, vlen_enc);
      break;
    case T_DOUBLE: {
      int mid_vlen_enc = (vlen_enc == Assembler::AVX_512bit) ? Assembler::AVX_256bit : Assembler::AVX_128bit;
      vpmovsxbd(dst, src, mid_vlen_enc);
      vcvtdq2pd(dst, dst, vlen_enc);
      break;
    }
    default:
      fatal("Unsupported type %s", type2name(to_elem_bt));
      break;
  }
}

//-------------------------------------------------------------------------------------------

// IndexOf for constant substrings with size >= 8 chars
// which don't need to be loaded through stack.
void C2_MacroAssembler::string_indexofC8(Register str1, Register str2,
                                         Register cnt1, Register cnt2,
                                         int int_cnt2,  Register result,
                                         XMMRegister vec, Register tmp,
                                         int ae) {
  ShortBranchVerifier sbv(this);
  assert(UseSSE42Intrinsics, "SSE4.2 intrinsics are required");
  assert(ae != StrIntrinsicNode::LU, "Invalid encoding");

  // This method uses the pcmpestri instruction with bound registers
  //   inputs:
  //     xmm - substring
  //     rax - substring length (elements count)
  //     mem - scanned string
  //     rdx - string length (elements count)
  //     0xd - mode: 1100 (substring search) + 01 (unsigned shorts)
  //     0xc - mode: 1100 (substring search) + 00 (unsigned bytes)
  //   outputs:
  //     rcx - matched index in string
  assert(cnt1 == rdx && cnt2 == rax && tmp == rcx, "pcmpestri");
  int mode   = (ae == StrIntrinsicNode::LL) ? 0x0c : 0x0d; // bytes or shorts
  int stride = (ae == StrIntrinsicNode::LL) ? 16 : 8; //UU, UL -> 8
  Address::ScaleFactor scale1 = (ae == StrIntrinsicNode::LL) ? Address::times_1 : Address::times_2;
  Address::ScaleFactor scale2 = (ae == StrIntrinsicNode::UL) ? Address::times_1 : scale1;

  Label RELOAD_SUBSTR, SCAN_TO_SUBSTR, SCAN_SUBSTR,
        RET_FOUND, RET_NOT_FOUND, EXIT, FOUND_SUBSTR,
        MATCH_SUBSTR_HEAD, RELOAD_STR, FOUND_CANDIDATE;

  // Note, inline_string_indexOf() generates checks:
  // if (substr.count > string.count) return -1;
  // if (substr.count == 0) return 0;
  assert(int_cnt2 >= stride, "this code is used only for cnt2 >= 8 chars");

  // Load substring.
  if (ae == StrIntrinsicNode::UL) {
    pmovzxbw(vec, Address(str2, 0));
  } else {
    movdqu(vec, Address(str2, 0));
  }
  movl(cnt2, int_cnt2);
  movptr(result, str1); // string addr

  if (int_cnt2 > stride) {
    jmpb(SCAN_TO_SUBSTR);

    // Reload substr for rescan, this code
    // is executed only for large substrings (> 8 chars)
    bind(RELOAD_SUBSTR);
    if (ae == StrIntrinsicNode::UL) {
      pmovzxbw(vec, Address(str2, 0));
    } else {
      movdqu(vec, Address(str2, 0));
    }
    negptr(cnt2); // Jumped here with negative cnt2, convert to positive

    bind(RELOAD_STR);
    // We came here after the beginning of the substring was
    // matched but the rest of it was not so we need to search
    // again. Start from the next element after the previous match.

    // cnt2 is number of substring reminding elements and
    // cnt1 is number of string reminding elements when cmp failed.
    // Restored cnt1 = cnt1 - cnt2 + int_cnt2
    subl(cnt1, cnt2);
    addl(cnt1, int_cnt2);
    movl(cnt2, int_cnt2); // Now restore cnt2

    decrementl(cnt1);     // Shift to next element
    cmpl(cnt1, cnt2);
    jcc(Assembler::negative, RET_NOT_FOUND);  // Left less then substring

    addptr(result, (1<<scale1));

  } // (int_cnt2 > 8)

  // Scan string for start of substr in 16-byte vectors
  bind(SCAN_TO_SUBSTR);
  pcmpestri(vec, Address(result, 0), mode);
  jccb(Assembler::below, FOUND_CANDIDATE);   // CF == 1
  subl(cnt1, stride);
  jccb(Assembler::lessEqual, RET_NOT_FOUND); // Scanned full string
  cmpl(cnt1, cnt2);
  jccb(Assembler::negative, RET_NOT_FOUND);  // Left less then substring
  addptr(result, 16);
  jmpb(SCAN_TO_SUBSTR);

  // Found a potential substr
  bind(FOUND_CANDIDATE);
  // Matched whole vector if first element matched (tmp(rcx) == 0).
  if (int_cnt2 == stride) {
    jccb(Assembler::overflow, RET_FOUND);    // OF == 1
  } else { // int_cnt2 > 8
    jccb(Assembler::overflow, FOUND_SUBSTR);
  }
  // After pcmpestri tmp(rcx) contains matched element index
  // Compute start addr of substr
  lea(result, Address(result, tmp, scale1));

  // Make sure string is still long enough
  subl(cnt1, tmp);
  cmpl(cnt1, cnt2);
  if (int_cnt2 == stride) {
    jccb(Assembler::greaterEqual, SCAN_TO_SUBSTR);
  } else { // int_cnt2 > 8
    jccb(Assembler::greaterEqual, MATCH_SUBSTR_HEAD);
  }
  // Left less then substring.

  bind(RET_NOT_FOUND);
  movl(result, -1);
  jmp(EXIT);

  if (int_cnt2 > stride) {
    // This code is optimized for the case when whole substring
    // is matched if its head is matched.
    bind(MATCH_SUBSTR_HEAD);
    pcmpestri(vec, Address(result, 0), mode);
    // Reload only string if does not match
    jcc(Assembler::noOverflow, RELOAD_STR); // OF == 0

    Label CONT_SCAN_SUBSTR;
    // Compare the rest of substring (> 8 chars).
    bind(FOUND_SUBSTR);
    // First 8 chars are already matched.
    negptr(cnt2);
    addptr(cnt2, stride);

    bind(SCAN_SUBSTR);
    subl(cnt1, stride);
    cmpl(cnt2, -stride); // Do not read beyond substring
    jccb(Assembler::lessEqual, CONT_SCAN_SUBSTR);
    // Back-up strings to avoid reading beyond substring:
    // cnt1 = cnt1 - cnt2 + 8
    addl(cnt1, cnt2); // cnt2 is negative
    addl(cnt1, stride);
    movl(cnt2, stride); negptr(cnt2);
    bind(CONT_SCAN_SUBSTR);
    if (int_cnt2 < (int)G) {
      int tail_off1 = int_cnt2<<scale1;
      int tail_off2 = int_cnt2<<scale2;
      if (ae == StrIntrinsicNode::UL) {
        pmovzxbw(vec, Address(str2, cnt2, scale2, tail_off2));
      } else {
        movdqu(vec, Address(str2, cnt2, scale2, tail_off2));
      }
      pcmpestri(vec, Address(result, cnt2, scale1, tail_off1), mode);
    } else {
      // calculate index in register to avoid integer overflow (int_cnt2*2)
      movl(tmp, int_cnt2);
      addptr(tmp, cnt2);
      if (ae == StrIntrinsicNode::UL) {
        pmovzxbw(vec, Address(str2, tmp, scale2, 0));
      } else {
        movdqu(vec, Address(str2, tmp, scale2, 0));
      }
      pcmpestri(vec, Address(result, tmp, scale1, 0), mode);
    }
    // Need to reload strings pointers if not matched whole vector
    jcc(Assembler::noOverflow, RELOAD_SUBSTR); // OF == 0
    addptr(cnt2, stride);
    jcc(Assembler::negative, SCAN_SUBSTR);
    // Fall through if found full substring

  } // (int_cnt2 > 8)

  bind(RET_FOUND);
  // Found result if we matched full small substring.
  // Compute substr offset
  subptr(result, str1);
  if (ae == StrIntrinsicNode::UU || ae == StrIntrinsicNode::UL) {
    shrl(result, 1); // index
  }
  bind(EXIT);

} // string_indexofC8

// Small strings are loaded through stack if they cross page boundary.
void C2_MacroAssembler::string_indexof(Register str1, Register str2,
                                       Register cnt1, Register cnt2,
                                       int int_cnt2,  Register result,
                                       XMMRegister vec, Register tmp,
                                       int ae) {
  ShortBranchVerifier sbv(this);
  assert(UseSSE42Intrinsics, "SSE4.2 intrinsics are required");
  assert(ae != StrIntrinsicNode::LU, "Invalid encoding");

  //
  // int_cnt2 is length of small (< 8 chars) constant substring
  // or (-1) for non constant substring in which case its length
  // is in cnt2 register.
  //
  // Note, inline_string_indexOf() generates checks:
  // if (substr.count > string.count) return -1;
  // if (substr.count == 0) return 0;
  //
  int stride = (ae == StrIntrinsicNode::LL) ? 16 : 8; //UU, UL -> 8
  assert(int_cnt2 == -1 || (0 < int_cnt2 && int_cnt2 < stride), "should be != 0");
  // This method uses the pcmpestri instruction with bound registers
  //   inputs:
  //     xmm - substring
  //     rax - substring length (elements count)
  //     mem - scanned string
  //     rdx - string length (elements count)
  //     0xd - mode: 1100 (substring search) + 01 (unsigned shorts)
  //     0xc - mode: 1100 (substring search) + 00 (unsigned bytes)
  //   outputs:
  //     rcx - matched index in string
  assert(cnt1 == rdx && cnt2 == rax && tmp == rcx, "pcmpestri");
  int mode = (ae == StrIntrinsicNode::LL) ? 0x0c : 0x0d; // bytes or shorts
  Address::ScaleFactor scale1 = (ae == StrIntrinsicNode::LL) ? Address::times_1 : Address::times_2;
  Address::ScaleFactor scale2 = (ae == StrIntrinsicNode::UL) ? Address::times_1 : scale1;

  Label RELOAD_SUBSTR, SCAN_TO_SUBSTR, SCAN_SUBSTR, ADJUST_STR,
        RET_FOUND, RET_NOT_FOUND, CLEANUP, FOUND_SUBSTR,
        FOUND_CANDIDATE;

  { //========================================================
    // We don't know where these strings are located
    // and we can't read beyond them. Load them through stack.
    Label BIG_STRINGS, CHECK_STR, COPY_SUBSTR, COPY_STR;

    movptr(tmp, rsp); // save old SP

    if (int_cnt2 > 0) {     // small (< 8 chars) constant substring
      if (int_cnt2 == (1>>scale2)) { // One byte
        assert((ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UL), "Only possible for latin1 encoding");
        load_unsigned_byte(result, Address(str2, 0));
        movdl(vec, result); // move 32 bits
      } else if (ae == StrIntrinsicNode::LL && int_cnt2 == 3) {  // Three bytes
        // Not enough header space in 32-bit VM: 12+3 = 15.
        movl(result, Address(str2, -1));
        shrl(result, 8);
        movdl(vec, result); // move 32 bits
      } else if (ae != StrIntrinsicNode::UL && int_cnt2 == (2>>scale2)) {  // One char
        load_unsigned_short(result, Address(str2, 0));
        movdl(vec, result); // move 32 bits
      } else if (ae != StrIntrinsicNode::UL && int_cnt2 == (4>>scale2)) { // Two chars
        movdl(vec, Address(str2, 0)); // move 32 bits
      } else if (ae != StrIntrinsicNode::UL && int_cnt2 == (8>>scale2)) { // Four chars
        movq(vec, Address(str2, 0));  // move 64 bits
      } else { // cnt2 = { 3, 5, 6, 7 } || (ae == StrIntrinsicNode::UL && cnt2 ={2, ..., 7})
        // Array header size is 12 bytes in 32-bit VM
        // + 6 bytes for 3 chars == 18 bytes,
        // enough space to load vec and shift.
        assert(HeapWordSize*TypeArrayKlass::header_size() >= 12,"sanity");
        if (ae == StrIntrinsicNode::UL) {
          int tail_off = int_cnt2-8;
          pmovzxbw(vec, Address(str2, tail_off));
          psrldq(vec, -2*tail_off);
        }
        else {
          int tail_off = int_cnt2*(1<<scale2);
          movdqu(vec, Address(str2, tail_off-16));
          psrldq(vec, 16-tail_off);
        }
      }
    } else { // not constant substring
      cmpl(cnt2, stride);
      jccb(Assembler::aboveEqual, BIG_STRINGS); // Both strings are big enough

      // We can read beyond string if srt+16 does not cross page boundary
      // since heaps are aligned and mapped by pages.
      assert(os::vm_page_size() < (int)G, "default page should be small");
      movl(result, str2); // We need only low 32 bits
      andl(result, ((int)os::vm_page_size()-1));
      cmpl(result, ((int)os::vm_page_size()-16));
      jccb(Assembler::belowEqual, CHECK_STR);

      // Move small strings to stack to allow load 16 bytes into vec.
      subptr(rsp, 16);
      int stk_offset = wordSize-(1<<scale2);
      push(cnt2);

      bind(COPY_SUBSTR);
      if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UL) {
        load_unsigned_byte(result, Address(str2, cnt2, scale2, -1));
        movb(Address(rsp, cnt2, scale2, stk_offset), result);
      } else if (ae == StrIntrinsicNode::UU) {
        load_unsigned_short(result, Address(str2, cnt2, scale2, -2));
        movw(Address(rsp, cnt2, scale2, stk_offset), result);
      }
      decrement(cnt2);
      jccb(Assembler::notZero, COPY_SUBSTR);

      pop(cnt2);
      movptr(str2, rsp);  // New substring address
    } // non constant

    bind(CHECK_STR);
    cmpl(cnt1, stride);
    jccb(Assembler::aboveEqual, BIG_STRINGS);

    // Check cross page boundary.
    movl(result, str1); // We need only low 32 bits
    andl(result, ((int)os::vm_page_size()-1));
    cmpl(result, ((int)os::vm_page_size()-16));
    jccb(Assembler::belowEqual, BIG_STRINGS);

    subptr(rsp, 16);
    int stk_offset = -(1<<scale1);
    if (int_cnt2 < 0) { // not constant
      push(cnt2);
      stk_offset += wordSize;
    }
    movl(cnt2, cnt1);

    bind(COPY_STR);
    if (ae == StrIntrinsicNode::LL) {
      load_unsigned_byte(result, Address(str1, cnt2, scale1, -1));
      movb(Address(rsp, cnt2, scale1, stk_offset), result);
    } else {
      load_unsigned_short(result, Address(str1, cnt2, scale1, -2));
      movw(Address(rsp, cnt2, scale1, stk_offset), result);
    }
    decrement(cnt2);
    jccb(Assembler::notZero, COPY_STR);

    if (int_cnt2 < 0) { // not constant
      pop(cnt2);
    }
    movptr(str1, rsp);  // New string address

    bind(BIG_STRINGS);
    // Load substring.
    if (int_cnt2 < 0) { // -1
      if (ae == StrIntrinsicNode::UL) {
        pmovzxbw(vec, Address(str2, 0));
      } else {
        movdqu(vec, Address(str2, 0));
      }
      push(cnt2);       // substr count
      push(str2);       // substr addr
      push(str1);       // string addr
    } else {
      // Small (< 8 chars) constant substrings are loaded already.
      movl(cnt2, int_cnt2);
    }
    push(tmp);  // original SP

  } // Finished loading

  //========================================================
  // Start search
  //

  movptr(result, str1); // string addr

  if (int_cnt2  < 0) {  // Only for non constant substring
    jmpb(SCAN_TO_SUBSTR);

    // SP saved at sp+0
    // String saved at sp+1*wordSize
    // Substr saved at sp+2*wordSize
    // Substr count saved at sp+3*wordSize

    // Reload substr for rescan, this code
    // is executed only for large substrings (> 8 chars)
    bind(RELOAD_SUBSTR);
    movptr(str2, Address(rsp, 2*wordSize));
    movl(cnt2, Address(rsp, 3*wordSize));
    if (ae == StrIntrinsicNode::UL) {
      pmovzxbw(vec, Address(str2, 0));
    } else {
      movdqu(vec, Address(str2, 0));
    }
    // We came here after the beginning of the substring was
    // matched but the rest of it was not so we need to search
    // again. Start from the next element after the previous match.
    subptr(str1, result); // Restore counter
    if (ae == StrIntrinsicNode::UU || ae == StrIntrinsicNode::UL) {
      shrl(str1, 1);
    }
    addl(cnt1, str1);
    decrementl(cnt1);   // Shift to next element
    cmpl(cnt1, cnt2);
    jcc(Assembler::negative, RET_NOT_FOUND);  // Left less then substring

    addptr(result, (1<<scale1));
  } // non constant

  // Scan string for start of substr in 16-byte vectors
  bind(SCAN_TO_SUBSTR);
  assert(cnt1 == rdx && cnt2 == rax && tmp == rcx, "pcmpestri");
  pcmpestri(vec, Address(result, 0), mode);
  jccb(Assembler::below, FOUND_CANDIDATE);   // CF == 1
  subl(cnt1, stride);
  jccb(Assembler::lessEqual, RET_NOT_FOUND); // Scanned full string
  cmpl(cnt1, cnt2);
  jccb(Assembler::negative, RET_NOT_FOUND);  // Left less then substring
  addptr(result, 16);

  bind(ADJUST_STR);
  cmpl(cnt1, stride); // Do not read beyond string
  jccb(Assembler::greaterEqual, SCAN_TO_SUBSTR);
  // Back-up string to avoid reading beyond string.
  lea(result, Address(result, cnt1, scale1, -16));
  movl(cnt1, stride);
  jmpb(SCAN_TO_SUBSTR);

  // Found a potential substr
  bind(FOUND_CANDIDATE);
  // After pcmpestri tmp(rcx) contains matched element index

  // Make sure string is still long enough
  subl(cnt1, tmp);
  cmpl(cnt1, cnt2);
  jccb(Assembler::greaterEqual, FOUND_SUBSTR);
  // Left less then substring.

  bind(RET_NOT_FOUND);
  movl(result, -1);
  jmp(CLEANUP);

  bind(FOUND_SUBSTR);
  // Compute start addr of substr
  lea(result, Address(result, tmp, scale1));
  if (int_cnt2 > 0) { // Constant substring
    // Repeat search for small substring (< 8 chars)
    // from new point without reloading substring.
    // Have to check that we don't read beyond string.
    cmpl(tmp, stride-int_cnt2);
    jccb(Assembler::greater, ADJUST_STR);
    // Fall through if matched whole substring.
  } else { // non constant
    assert(int_cnt2 == -1, "should be != 0");

    addl(tmp, cnt2);
    // Found result if we matched whole substring.
    cmpl(tmp, stride);
    jcc(Assembler::lessEqual, RET_FOUND);

    // Repeat search for small substring (<= 8 chars)
    // from new point 'str1' without reloading substring.
    cmpl(cnt2, stride);
    // Have to check that we don't read beyond string.
    jccb(Assembler::lessEqual, ADJUST_STR);

    Label CHECK_NEXT, CONT_SCAN_SUBSTR, RET_FOUND_LONG;
    // Compare the rest of substring (> 8 chars).
    movptr(str1, result);

    cmpl(tmp, cnt2);
    // First 8 chars are already matched.
    jccb(Assembler::equal, CHECK_NEXT);

    bind(SCAN_SUBSTR);
    pcmpestri(vec, Address(str1, 0), mode);
    // Need to reload strings pointers if not matched whole vector
    jcc(Assembler::noOverflow, RELOAD_SUBSTR); // OF == 0

    bind(CHECK_NEXT);
    subl(cnt2, stride);
    jccb(Assembler::lessEqual, RET_FOUND_LONG); // Found full substring
    addptr(str1, 16);
    if (ae == StrIntrinsicNode::UL) {
      addptr(str2, 8);
    } else {
      addptr(str2, 16);
    }
    subl(cnt1, stride);
    cmpl(cnt2, stride); // Do not read beyond substring
    jccb(Assembler::greaterEqual, CONT_SCAN_SUBSTR);
    // Back-up strings to avoid reading beyond substring.

    if (ae == StrIntrinsicNode::UL) {
      lea(str2, Address(str2, cnt2, scale2, -8));
      lea(str1, Address(str1, cnt2, scale1, -16));
    } else {
      lea(str2, Address(str2, cnt2, scale2, -16));
      lea(str1, Address(str1, cnt2, scale1, -16));
    }
    subl(cnt1, cnt2);
    movl(cnt2, stride);
    addl(cnt1, stride);
    bind(CONT_SCAN_SUBSTR);
    if (ae == StrIntrinsicNode::UL) {
      pmovzxbw(vec, Address(str2, 0));
    } else {
      movdqu(vec, Address(str2, 0));
    }
    jmp(SCAN_SUBSTR);

    bind(RET_FOUND_LONG);
    movptr(str1, Address(rsp, wordSize));
  } // non constant

  bind(RET_FOUND);
  // Compute substr offset
  subptr(result, str1);
  if (ae == StrIntrinsicNode::UU || ae == StrIntrinsicNode::UL) {
    shrl(result, 1); // index
  }
  bind(CLEANUP);
  pop(rsp); // restore SP

} // string_indexof

void C2_MacroAssembler::string_indexof_char(Register str1, Register cnt1, Register ch, Register result,
                                            XMMRegister vec1, XMMRegister vec2, XMMRegister vec3, Register tmp) {
  ShortBranchVerifier sbv(this);
  assert(UseSSE42Intrinsics, "SSE4.2 intrinsics are required");

  int stride = 8;

  Label FOUND_CHAR, SCAN_TO_CHAR, SCAN_TO_CHAR_LOOP,
        SCAN_TO_8_CHAR, SCAN_TO_8_CHAR_LOOP, SCAN_TO_16_CHAR_LOOP,
        RET_NOT_FOUND, SCAN_TO_8_CHAR_INIT,
        FOUND_SEQ_CHAR, DONE_LABEL;

  movptr(result, str1);
  if (UseAVX >= 2) {
    cmpl(cnt1, stride);
    jcc(Assembler::less, SCAN_TO_CHAR);
    cmpl(cnt1, 2*stride);
    jcc(Assembler::less, SCAN_TO_8_CHAR_INIT);
    movdl(vec1, ch);
    vpbroadcastw(vec1, vec1, Assembler::AVX_256bit);
    vpxor(vec2, vec2);
    movl(tmp, cnt1);
    andl(tmp, 0xFFFFFFF0);  //vector count (in chars)
    andl(cnt1,0x0000000F);  //tail count (in chars)

    bind(SCAN_TO_16_CHAR_LOOP);
    vmovdqu(vec3, Address(result, 0));
    vpcmpeqw(vec3, vec3, vec1, 1);
    vptest(vec2, vec3);
    jcc(Assembler::carryClear, FOUND_CHAR);
    addptr(result, 32);
    subl(tmp, 2*stride);
    jcc(Assembler::notZero, SCAN_TO_16_CHAR_LOOP);
    jmp(SCAN_TO_8_CHAR);
    bind(SCAN_TO_8_CHAR_INIT);
    movdl(vec1, ch);
    pshuflw(vec1, vec1, 0x00);
    pshufd(vec1, vec1, 0);
    pxor(vec2, vec2);
  }
  bind(SCAN_TO_8_CHAR);
  cmpl(cnt1, stride);
  jcc(Assembler::less, SCAN_TO_CHAR);
  if (UseAVX < 2) {
    movdl(vec1, ch);
    pshuflw(vec1, vec1, 0x00);
    pshufd(vec1, vec1, 0);
    pxor(vec2, vec2);
  }
  movl(tmp, cnt1);
  andl(tmp, 0xFFFFFFF8);  //vector count (in chars)
  andl(cnt1,0x00000007);  //tail count (in chars)

  bind(SCAN_TO_8_CHAR_LOOP);
  movdqu(vec3, Address(result, 0));
  pcmpeqw(vec3, vec1);
  ptest(vec2, vec3);
  jcc(Assembler::carryClear, FOUND_CHAR);
  addptr(result, 16);
  subl(tmp, stride);
  jcc(Assembler::notZero, SCAN_TO_8_CHAR_LOOP);
  bind(SCAN_TO_CHAR);
  testl(cnt1, cnt1);
  jcc(Assembler::zero, RET_NOT_FOUND);
  bind(SCAN_TO_CHAR_LOOP);
  load_unsigned_short(tmp, Address(result, 0));
  cmpl(ch, tmp);
  jccb(Assembler::equal, FOUND_SEQ_CHAR);
  addptr(result, 2);
  subl(cnt1, 1);
  jccb(Assembler::zero, RET_NOT_FOUND);
  jmp(SCAN_TO_CHAR_LOOP);

  bind(RET_NOT_FOUND);
  movl(result, -1);
  jmpb(DONE_LABEL);

  bind(FOUND_CHAR);
  if (UseAVX >= 2) {
    vpmovmskb(tmp, vec3);
  } else {
    pmovmskb(tmp, vec3);
  }
  bsfl(ch, tmp);
  addptr(result, ch);

  bind(FOUND_SEQ_CHAR);
  subptr(result, str1);
  shrl(result, 1);

  bind(DONE_LABEL);
} // string_indexof_char

void C2_MacroAssembler::stringL_indexof_char(Register str1, Register cnt1, Register ch, Register result,
                                            XMMRegister vec1, XMMRegister vec2, XMMRegister vec3, Register tmp) {
  ShortBranchVerifier sbv(this);
  assert(UseSSE42Intrinsics, "SSE4.2 intrinsics are required");

  int stride = 16;

  Label FOUND_CHAR, SCAN_TO_CHAR_INIT, SCAN_TO_CHAR_LOOP,
        SCAN_TO_16_CHAR, SCAN_TO_16_CHAR_LOOP, SCAN_TO_32_CHAR_LOOP,
        RET_NOT_FOUND, SCAN_TO_16_CHAR_INIT,
        FOUND_SEQ_CHAR, DONE_LABEL;

  movptr(result, str1);
  if (UseAVX >= 2) {
    cmpl(cnt1, stride);
    jcc(Assembler::less, SCAN_TO_CHAR_INIT);
    cmpl(cnt1, stride*2);
    jcc(Assembler::less, SCAN_TO_16_CHAR_INIT);
    movdl(vec1, ch);
    vpbroadcastb(vec1, vec1, Assembler::AVX_256bit);
    vpxor(vec2, vec2);
    movl(tmp, cnt1);
    andl(tmp, 0xFFFFFFE0);  //vector count (in chars)
    andl(cnt1,0x0000001F);  //tail count (in chars)

    bind(SCAN_TO_32_CHAR_LOOP);
    vmovdqu(vec3, Address(result, 0));
    vpcmpeqb(vec3, vec3, vec1, Assembler::AVX_256bit);
    vptest(vec2, vec3);
    jcc(Assembler::carryClear, FOUND_CHAR);
    addptr(result, 32);
    subl(tmp, stride*2);
    jcc(Assembler::notZero, SCAN_TO_32_CHAR_LOOP);
    jmp(SCAN_TO_16_CHAR);

    bind(SCAN_TO_16_CHAR_INIT);
    movdl(vec1, ch);
    pxor(vec2, vec2);
    pshufb(vec1, vec2);
  }

  bind(SCAN_TO_16_CHAR);
  cmpl(cnt1, stride);
  jcc(Assembler::less, SCAN_TO_CHAR_INIT);//less than 16 entries left
  if (UseAVX < 2) {
    movdl(vec1, ch);
    pxor(vec2, vec2);
    pshufb(vec1, vec2);
  }
  movl(tmp, cnt1);
  andl(tmp, 0xFFFFFFF0);  //vector count (in bytes)
  andl(cnt1,0x0000000F);  //tail count (in bytes)

  bind(SCAN_TO_16_CHAR_LOOP);
  movdqu(vec3, Address(result, 0));
  pcmpeqb(vec3, vec1);
  ptest(vec2, vec3);
  jcc(Assembler::carryClear, FOUND_CHAR);
  addptr(result, 16);
  subl(tmp, stride);
  jcc(Assembler::notZero, SCAN_TO_16_CHAR_LOOP);//last 16 items...

  bind(SCAN_TO_CHAR_INIT);
  testl(cnt1, cnt1);
  jcc(Assembler::zero, RET_NOT_FOUND);
  bind(SCAN_TO_CHAR_LOOP);
  load_unsigned_byte(tmp, Address(result, 0));
  cmpl(ch, tmp);
  jccb(Assembler::equal, FOUND_SEQ_CHAR);
  addptr(result, 1);
  subl(cnt1, 1);
  jccb(Assembler::zero, RET_NOT_FOUND);
  jmp(SCAN_TO_CHAR_LOOP);

  bind(RET_NOT_FOUND);
  movl(result, -1);
  jmpb(DONE_LABEL);

  bind(FOUND_CHAR);
  if (UseAVX >= 2) {
    vpmovmskb(tmp, vec3);
  } else {
    pmovmskb(tmp, vec3);
  }
  bsfl(ch, tmp);
  addptr(result, ch);

  bind(FOUND_SEQ_CHAR);
  subptr(result, str1);

  bind(DONE_LABEL);
} // stringL_indexof_char

int C2_MacroAssembler::arrays_hashcode_elsize(BasicType eltype) {
  switch (eltype) {
  case T_BOOLEAN: return sizeof(jboolean);
  case T_BYTE:  return sizeof(jbyte);
  case T_SHORT: return sizeof(jshort);
  case T_CHAR:  return sizeof(jchar);
  case T_INT:   return sizeof(jint);
  default:
    ShouldNotReachHere();
    return -1;
  }
}

void C2_MacroAssembler::arrays_hashcode_elload(Register dst, Address src, BasicType eltype) {
  switch (eltype) {
  // T_BOOLEAN used as surrogate for unsigned byte
  case T_BOOLEAN: movzbl(dst, src);   break;
  case T_BYTE:    movsbl(dst, src);   break;
  case T_SHORT:   movswl(dst, src);   break;
  case T_CHAR:    movzwl(dst, src);   break;
  case T_INT:     movl(dst, src);     break;
  default:
    ShouldNotReachHere();
  }
}

void C2_MacroAssembler::arrays_hashcode_elvload(XMMRegister dst, Address src, BasicType eltype) {
  load_vector(eltype, dst, src, arrays_hashcode_elsize(eltype) * 8);
}

void C2_MacroAssembler::arrays_hashcode_elvload(XMMRegister dst, AddressLiteral src, BasicType eltype) {
  load_vector(eltype, dst, src, arrays_hashcode_elsize(eltype) * 8);
}

void C2_MacroAssembler::arrays_hashcode_elvcast(XMMRegister dst, BasicType eltype) {
  const int vlen = Assembler::AVX_256bit;
  switch (eltype) {
  case T_BOOLEAN: vector_unsigned_cast(dst, dst, vlen, T_BYTE, T_INT);  break;
  case T_BYTE:      vector_signed_cast(dst, dst, vlen, T_BYTE, T_INT);  break;
  case T_SHORT:     vector_signed_cast(dst, dst, vlen, T_SHORT, T_INT); break;
  case T_CHAR:    vector_unsigned_cast(dst, dst, vlen, T_SHORT, T_INT); break;
  case T_INT:
    // do nothing
    break;
  default:
    ShouldNotReachHere();
  }
}

void C2_MacroAssembler::arrays_hashcode(Register ary1, Register cnt1, Register result,
                                        Register index, Register tmp2, Register tmp3, XMMRegister vnext,
                                        XMMRegister vcoef0, XMMRegister vcoef1, XMMRegister vcoef2, XMMRegister vcoef3,
                                        XMMRegister vresult0, XMMRegister vresult1, XMMRegister vresult2, XMMRegister vresult3,
                                        XMMRegister vtmp0, XMMRegister vtmp1, XMMRegister vtmp2, XMMRegister vtmp3,
                                        BasicType eltype) {
  ShortBranchVerifier sbv(this);
  assert(UseAVX >= 2, "AVX2 intrinsics are required");
  assert_different_registers(ary1, cnt1, result, index, tmp2, tmp3);
  assert_different_registers(vnext, vcoef0, vcoef1, vcoef2, vcoef3, vresult0, vresult1, vresult2, vresult3, vtmp0, vtmp1, vtmp2, vtmp3);

  Label SHORT_UNROLLED_BEGIN, SHORT_UNROLLED_LOOP_BEGIN,
        SHORT_UNROLLED_LOOP_EXIT,
        UNROLLED_SCALAR_LOOP_BEGIN, UNROLLED_SCALAR_SKIP, UNROLLED_SCALAR_RESUME,
        UNROLLED_VECTOR_LOOP_BEGIN,
        END;
  switch (eltype) {
  case T_BOOLEAN: BLOCK_COMMENT("arrays_hashcode(unsigned byte) {"); break;
  case T_CHAR:    BLOCK_COMMENT("arrays_hashcode(char) {");          break;
  case T_BYTE:    BLOCK_COMMENT("arrays_hashcode(byte) {");          break;
  case T_SHORT:   BLOCK_COMMENT("arrays_hashcode(short) {");         break;
  case T_INT:     BLOCK_COMMENT("arrays_hashcode(int) {");           break;
  default:        BLOCK_COMMENT("arrays_hashcode {");                break;
  }

  // For "renaming" for readibility of the code
  const XMMRegister vcoef[] = { vcoef0, vcoef1, vcoef2, vcoef3 },
                    vresult[] = { vresult0, vresult1, vresult2, vresult3 },
                    vtmp[] = { vtmp0, vtmp1, vtmp2, vtmp3 };

  const int elsize = arrays_hashcode_elsize(eltype);

  /*
    if (cnt1 >= 2) {
      if (cnt1 >= 32) {
        UNROLLED VECTOR LOOP
      }
      UNROLLED SCALAR LOOP
    }
    SINGLE SCALAR
   */

  cmpl(cnt1, 32);
  jcc(Assembler::less, SHORT_UNROLLED_BEGIN);

  // cnt1 >= 32 && generate_vectorized_loop
  xorl(index, index);

  // vresult = IntVector.zero(I256);
  for (int idx = 0; idx < 4; idx++) {
    vpxor(vresult[idx], vresult[idx]);
  }
  // vnext = IntVector.broadcast(I256, power_of_31_backwards[0]);
  Register bound = tmp2;
  Register next = tmp3;
  lea(tmp2, ExternalAddress(StubRoutines::x86::arrays_hashcode_powers_of_31() + (0 * sizeof(jint))));
  movl(next, Address(tmp2, 0));
  movdl(vnext, next);
  vpbroadcastd(vnext, vnext, Assembler::AVX_256bit);

  // index = 0;
  // bound = cnt1 & ~(32 - 1);
  movl(bound, cnt1);
  andl(bound, ~(32 - 1));
  // for (; index < bound; index += 32) {
  bind(UNROLLED_VECTOR_LOOP_BEGIN);
  // result *= next;
  imull(result, next);
  // loop fission to upfront the cost of fetching from memory, OOO execution
  // can then hopefully do a better job of prefetching
  for (int idx = 0; idx < 4; idx++) {
    arrays_hashcode_elvload(vtmp[idx], Address(ary1, index, Address::times(elsize), 8 * idx * elsize), eltype);
  }
  // vresult = vresult * vnext + ary1[index+8*idx:index+8*idx+7];
  for (int idx = 0; idx < 4; idx++) {
    vpmulld(vresult[idx], vresult[idx], vnext, Assembler::AVX_256bit);
    arrays_hashcode_elvcast(vtmp[idx], eltype);
    vpaddd(vresult[idx], vresult[idx], vtmp[idx], Assembler::AVX_256bit);
  }
  // index += 32;
  addl(index, 32);
  // index < bound;
  cmpl(index, bound);
  jcc(Assembler::less, UNROLLED_VECTOR_LOOP_BEGIN);
  // }

  lea(ary1, Address(ary1, bound, Address::times(elsize)));
  subl(cnt1, bound);
  // release bound

  // vresult *= IntVector.fromArray(I256, power_of_31_backwards, 1);
  for (int idx = 0; idx < 4; idx++) {
    lea(tmp2, ExternalAddress(StubRoutines::x86::arrays_hashcode_powers_of_31() + ((8 * idx + 1) * sizeof(jint))));
    arrays_hashcode_elvload(vcoef[idx], Address(tmp2, 0), T_INT);
    vpmulld(vresult[idx], vresult[idx], vcoef[idx], Assembler::AVX_256bit);
  }
  // result += vresult.reduceLanes(ADD);
  for (int idx = 0; idx < 4; idx++) {
    reduceI(Op_AddReductionVI, 256/(sizeof(jint) * 8), result, result, vresult[idx], vtmp[(idx * 2 + 0) % 4], vtmp[(idx * 2 + 1) % 4]);
  }

  // } else if (cnt1 < 32) {

  bind(SHORT_UNROLLED_BEGIN);
  // int i = 1;
  movl(index, 1);
  cmpl(index, cnt1);
  jcc(Assembler::greaterEqual, SHORT_UNROLLED_LOOP_EXIT);

  // for (; i < cnt1 ; i += 2) {
  bind(SHORT_UNROLLED_LOOP_BEGIN);
  movl(tmp3, 961);
  imull(result, tmp3);
  arrays_hashcode_elload(tmp2, Address(ary1, index, Address::times(elsize), -elsize), eltype);
  movl(tmp3, tmp2);
  shll(tmp3, 5);
  subl(tmp3, tmp2);
  addl(result, tmp3);
  arrays_hashcode_elload(tmp3, Address(ary1, index, Address::times(elsize)), eltype);
  addl(result, tmp3);
  addl(index, 2);
  cmpl(index, cnt1);
  jccb(Assembler::less, SHORT_UNROLLED_LOOP_BEGIN);

  // }
  // if (i >= cnt1) {
  bind(SHORT_UNROLLED_LOOP_EXIT);
  jccb(Assembler::greater, END);
  movl(tmp2, result);
  shll(result, 5);
  subl(result, tmp2);
  arrays_hashcode_elload(tmp3, Address(ary1, index, Address::times(elsize), -elsize), eltype);
  addl(result, tmp3);
  // }
  bind(END);

  BLOCK_COMMENT("} // arrays_hashcode");

} // arrays_hashcode

// helper function for string_compare
void C2_MacroAssembler::load_next_elements(Register elem1, Register elem2, Register str1, Register str2,
                                           Address::ScaleFactor scale, Address::ScaleFactor scale1,
                                           Address::ScaleFactor scale2, Register index, int ae) {
  if (ae == StrIntrinsicNode::LL) {
    load_unsigned_byte(elem1, Address(str1, index, scale, 0));
    load_unsigned_byte(elem2, Address(str2, index, scale, 0));
  } else if (ae == StrIntrinsicNode::UU) {
    load_unsigned_short(elem1, Address(str1, index, scale, 0));
    load_unsigned_short(elem2, Address(str2, index, scale, 0));
  } else {
    load_unsigned_byte(elem1, Address(str1, index, scale1, 0));
    load_unsigned_short(elem2, Address(str2, index, scale2, 0));
  }
}

// Compare strings, used for char[] and byte[].
void C2_MacroAssembler::string_compare(Register str1, Register str2,
                                       Register cnt1, Register cnt2, Register result,
                                       XMMRegister vec1, int ae, KRegister mask) {
  ShortBranchVerifier sbv(this);
  Label LENGTH_DIFF_LABEL, POP_LABEL, DONE_LABEL, WHILE_HEAD_LABEL;
  Label COMPARE_WIDE_VECTORS_LOOP_FAILED;  // used only AVX3
  int stride, stride2, adr_stride, adr_stride1, adr_stride2;
  int stride2x2 = 0x40;
  Address::ScaleFactor scale = Address::no_scale;
  Address::ScaleFactor scale1 = Address::no_scale;
  Address::ScaleFactor scale2 = Address::no_scale;

  if (ae != StrIntrinsicNode::LL) {
    stride2x2 = 0x20;
  }

  if (ae == StrIntrinsicNode::LU || ae == StrIntrinsicNode::UL) {
    shrl(cnt2, 1);
  }
  // Compute the minimum of the string lengths and the
  // difference of the string lengths (stack).
  // Do the conditional move stuff
  movl(result, cnt1);
  subl(cnt1, cnt2);
  push(cnt1);
  cmov32(Assembler::lessEqual, cnt2, result);    // cnt2 = min(cnt1, cnt2)

  // Is the minimum length zero?
  testl(cnt2, cnt2);
  jcc(Assembler::zero, LENGTH_DIFF_LABEL);
  if (ae == StrIntrinsicNode::LL) {
    // Load first bytes
    load_unsigned_byte(result, Address(str1, 0));  // result = str1[0]
    load_unsigned_byte(cnt1, Address(str2, 0));    // cnt1   = str2[0]
  } else if (ae == StrIntrinsicNode::UU) {
    // Load first characters
    load_unsigned_short(result, Address(str1, 0));
    load_unsigned_short(cnt1, Address(str2, 0));
  } else {
    load_unsigned_byte(result, Address(str1, 0));
    load_unsigned_short(cnt1, Address(str2, 0));
  }
  subl(result, cnt1);
  jcc(Assembler::notZero,  POP_LABEL);

  if (ae == StrIntrinsicNode::UU) {
    // Divide length by 2 to get number of chars
    shrl(cnt2, 1);
  }
  cmpl(cnt2, 1);
  jcc(Assembler::equal, LENGTH_DIFF_LABEL);

  // Check if the strings start at the same location and setup scale and stride
  if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
    cmpptr(str1, str2);
    jcc(Assembler::equal, LENGTH_DIFF_LABEL);
    if (ae == StrIntrinsicNode::LL) {
      scale = Address::times_1;
      stride = 16;
    } else {
      scale = Address::times_2;
      stride = 8;
    }
  } else {
    scale1 = Address::times_1;
    scale2 = Address::times_2;
    // scale not used
    stride = 8;
  }

  if (UseAVX >= 2 && UseSSE42Intrinsics) {
    Label COMPARE_WIDE_VECTORS, VECTOR_NOT_EQUAL, COMPARE_WIDE_TAIL, COMPARE_SMALL_STR;
    Label COMPARE_WIDE_VECTORS_LOOP, COMPARE_16_CHARS, COMPARE_INDEX_CHAR;
    Label COMPARE_WIDE_VECTORS_LOOP_AVX2;
    Label COMPARE_TAIL_LONG;
    Label COMPARE_WIDE_VECTORS_LOOP_AVX3;  // used only AVX3

    int pcmpmask = 0x19;
    if (ae == StrIntrinsicNode::LL) {
      pcmpmask &= ~0x01;
    }

    // Setup to compare 16-chars (32-bytes) vectors,
    // start from first character again because it has aligned address.
    if (ae == StrIntrinsicNode::LL) {
      stride2 = 32;
    } else {
      stride2 = 16;
    }
    if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
      adr_stride = stride << scale;
    } else {
      adr_stride1 = 8;  //stride << scale1;
      adr_stride2 = 16; //stride << scale2;
    }

    assert(result == rax && cnt2 == rdx && cnt1 == rcx, "pcmpestri");
    // rax and rdx are used by pcmpestri as elements counters
    movl(result, cnt2);
    andl(cnt2, ~(stride2-1));   // cnt2 holds the vector count
    jcc(Assembler::zero, COMPARE_TAIL_LONG);

    // fast path : compare first 2 8-char vectors.
    bind(COMPARE_16_CHARS);
    if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
      movdqu(vec1, Address(str1, 0));
    } else {
      pmovzxbw(vec1, Address(str1, 0));
    }
    pcmpestri(vec1, Address(str2, 0), pcmpmask);
    jccb(Assembler::below, COMPARE_INDEX_CHAR);

    if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
      movdqu(vec1, Address(str1, adr_stride));
      pcmpestri(vec1, Address(str2, adr_stride), pcmpmask);
    } else {
      pmovzxbw(vec1, Address(str1, adr_stride1));
      pcmpestri(vec1, Address(str2, adr_stride2), pcmpmask);
    }
    jccb(Assembler::aboveEqual, COMPARE_WIDE_VECTORS);
    addl(cnt1, stride);

    // Compare the characters at index in cnt1
    bind(COMPARE_INDEX_CHAR); // cnt1 has the offset of the mismatching character
    load_next_elements(result, cnt2, str1, str2, scale, scale1, scale2, cnt1, ae);
    subl(result, cnt2);
    jmp(POP_LABEL);

    // Setup the registers to start vector comparison loop
    bind(COMPARE_WIDE_VECTORS);
    if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
      lea(str1, Address(str1, result, scale));
      lea(str2, Address(str2, result, scale));
    } else {
      lea(str1, Address(str1, result, scale1));
      lea(str2, Address(str2, result, scale2));
    }
    subl(result, stride2);
    subl(cnt2, stride2);
    jcc(Assembler::zero, COMPARE_WIDE_TAIL);
    negptr(result);

    //  In a loop, compare 16-chars (32-bytes) at once using (vpxor+vptest)
    bind(COMPARE_WIDE_VECTORS_LOOP);

    if ((AVX3Threshold == 0) && VM_Version::supports_avx512vlbw()) { // trying 64 bytes fast loop
      cmpl(cnt2, stride2x2);
      jccb(Assembler::below, COMPARE_WIDE_VECTORS_LOOP_AVX2);
      testl(cnt2, stride2x2-1);   // cnt2 holds the vector count
      jccb(Assembler::notZero, COMPARE_WIDE_VECTORS_LOOP_AVX2);   // means we cannot subtract by 0x40

      bind(COMPARE_WIDE_VECTORS_LOOP_AVX3); // the hottest loop
      if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
        evmovdquq(vec1, Address(str1, result, scale), Assembler::AVX_512bit);
        evpcmpeqb(mask, vec1, Address(str2, result, scale), Assembler::AVX_512bit); // k7 == 11..11, if operands equal, otherwise k7 has some 0
      } else {
        vpmovzxbw(vec1, Address(str1, result, scale1), Assembler::AVX_512bit);
        evpcmpeqb(mask, vec1, Address(str2, result, scale2), Assembler::AVX_512bit); // k7 == 11..11, if operands equal, otherwise k7 has some 0
      }
      kortestql(mask, mask);
      jcc(Assembler::aboveEqual, COMPARE_WIDE_VECTORS_LOOP_FAILED);     // miscompare
      addptr(result, stride2x2);  // update since we already compared at this addr
      subl(cnt2, stride2x2);      // and sub the size too
      jccb(Assembler::notZero, COMPARE_WIDE_VECTORS_LOOP_AVX3);

      vpxor(vec1, vec1);
      jmpb(COMPARE_WIDE_TAIL);
    }//if (VM_Version::supports_avx512vlbw())

    bind(COMPARE_WIDE_VECTORS_LOOP_AVX2);
    if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
      vmovdqu(vec1, Address(str1, result, scale));
      vpxor(vec1, Address(str2, result, scale));
    } else {
      vpmovzxbw(vec1, Address(str1, result, scale1), Assembler::AVX_256bit);
      vpxor(vec1, Address(str2, result, scale2));
    }
    vptest(vec1, vec1);
    jcc(Assembler::notZero, VECTOR_NOT_EQUAL);
    addptr(result, stride2);
    subl(cnt2, stride2);
    jcc(Assembler::notZero, COMPARE_WIDE_VECTORS_LOOP);
    // clean upper bits of YMM registers
    vpxor(vec1, vec1);

    // compare wide vectors tail
    bind(COMPARE_WIDE_TAIL);
    testptr(result, result);
    jcc(Assembler::zero, LENGTH_DIFF_LABEL);

    movl(result, stride2);
    movl(cnt2, result);
    negptr(result);
    jmp(COMPARE_WIDE_VECTORS_LOOP_AVX2);

    // Identifies the mismatching (higher or lower)16-bytes in the 32-byte vectors.
    bind(VECTOR_NOT_EQUAL);
    // clean upper bits of YMM registers
    vpxor(vec1, vec1);
    if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
      lea(str1, Address(str1, result, scale));
      lea(str2, Address(str2, result, scale));
    } else {
      lea(str1, Address(str1, result, scale1));
      lea(str2, Address(str2, result, scale2));
    }
    jmp(COMPARE_16_CHARS);

    // Compare tail chars, length between 1 to 15 chars
    bind(COMPARE_TAIL_LONG);
    movl(cnt2, result);
    cmpl(cnt2, stride);
    jcc(Assembler::less, COMPARE_SMALL_STR);

    if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
      movdqu(vec1, Address(str1, 0));
    } else {
      pmovzxbw(vec1, Address(str1, 0));
    }
    pcmpestri(vec1, Address(str2, 0), pcmpmask);
    jcc(Assembler::below, COMPARE_INDEX_CHAR);
    subptr(cnt2, stride);
    jcc(Assembler::zero, LENGTH_DIFF_LABEL);
    if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
      lea(str1, Address(str1, result, scale));
      lea(str2, Address(str2, result, scale));
    } else {
      lea(str1, Address(str1, result, scale1));
      lea(str2, Address(str2, result, scale2));
    }
    negptr(cnt2);
    jmpb(WHILE_HEAD_LABEL);

    bind(COMPARE_SMALL_STR);
  } else if (UseSSE42Intrinsics) {
    Label COMPARE_WIDE_VECTORS, VECTOR_NOT_EQUAL, COMPARE_TAIL;
    int pcmpmask = 0x19;
    // Setup to compare 8-char (16-byte) vectors,
    // start from first character again because it has aligned address.
    movl(result, cnt2);
    andl(cnt2, ~(stride - 1));   // cnt2 holds the vector count
    if (ae == StrIntrinsicNode::LL) {
      pcmpmask &= ~0x01;
    }
    jcc(Assembler::zero, COMPARE_TAIL);
    if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
      lea(str1, Address(str1, result, scale));
      lea(str2, Address(str2, result, scale));
    } else {
      lea(str1, Address(str1, result, scale1));
      lea(str2, Address(str2, result, scale2));
    }
    negptr(result);

    // pcmpestri
    //   inputs:
    //     vec1- substring
    //     rax - negative string length (elements count)
    //     mem - scanned string
    //     rdx - string length (elements count)
    //     pcmpmask - cmp mode: 11000 (string compare with negated result)
    //               + 00 (unsigned bytes) or  + 01 (unsigned shorts)
    //   outputs:
    //     rcx - first mismatched element index
    assert(result == rax && cnt2 == rdx && cnt1 == rcx, "pcmpestri");

    bind(COMPARE_WIDE_VECTORS);
    if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
      movdqu(vec1, Address(str1, result, scale));
      pcmpestri(vec1, Address(str2, result, scale), pcmpmask);
    } else {
      pmovzxbw(vec1, Address(str1, result, scale1));
      pcmpestri(vec1, Address(str2, result, scale2), pcmpmask);
    }
    // After pcmpestri cnt1(rcx) contains mismatched element index

    jccb(Assembler::below, VECTOR_NOT_EQUAL);  // CF==1
    addptr(result, stride);
    subptr(cnt2, stride);
    jccb(Assembler::notZero, COMPARE_WIDE_VECTORS);

    // compare wide vectors tail
    testptr(result, result);
    jcc(Assembler::zero, LENGTH_DIFF_LABEL);

    movl(cnt2, stride);
    movl(result, stride);
    negptr(result);
    if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
      movdqu(vec1, Address(str1, result, scale));
      pcmpestri(vec1, Address(str2, result, scale), pcmpmask);
    } else {
      pmovzxbw(vec1, Address(str1, result, scale1));
      pcmpestri(vec1, Address(str2, result, scale2), pcmpmask);
    }
    jccb(Assembler::aboveEqual, LENGTH_DIFF_LABEL);

    // Mismatched characters in the vectors
    bind(VECTOR_NOT_EQUAL);
    addptr(cnt1, result);
    load_next_elements(result, cnt2, str1, str2, scale, scale1, scale2, cnt1, ae);
    subl(result, cnt2);
    jmpb(POP_LABEL);

    bind(COMPARE_TAIL); // limit is zero
    movl(cnt2, result);
    // Fallthru to tail compare
  }
  // Shift str2 and str1 to the end of the arrays, negate min
  if (ae == StrIntrinsicNode::LL || ae == StrIntrinsicNode::UU) {
    lea(str1, Address(str1, cnt2, scale));
    lea(str2, Address(str2, cnt2, scale));
  } else {
    lea(str1, Address(str1, cnt2, scale1));
    lea(str2, Address(str2, cnt2, scale2));
  }
  decrementl(cnt2);  // first character was compared already
  negptr(cnt2);

  // Compare the rest of the elements
  bind(WHILE_HEAD_LABEL);
  load_next_elements(result, cnt1, str1, str2, scale, scale1, scale2, cnt2, ae);
  subl(result, cnt1);
  jccb(Assembler::notZero, POP_LABEL);
  increment(cnt2);
  jccb(Assembler::notZero, WHILE_HEAD_LABEL);

  // Strings are equal up to min length.  Return the length difference.
  bind(LENGTH_DIFF_LABEL);
  pop(result);
  if (ae == StrIntrinsicNode::UU) {
    // Divide diff by 2 to get number of chars
    sarl(result, 1);
  }
  jmpb(DONE_LABEL);

  if (VM_Version::supports_avx512vlbw()) {

    bind(COMPARE_WIDE_VECTORS_LOOP_FAILED);

    kmovql(cnt1, mask);
    notq(cnt1);
    bsfq(cnt2, cnt1);
    if (ae != StrIntrinsicNode::LL) {
      // Divide diff by 2 to get number of chars
      sarl(cnt2, 1);
    }
    addq(result, cnt2);
    if (ae == StrIntrinsicNode::LL) {
      load_unsigned_byte(cnt1, Address(str2, result));
      load_unsigned_byte(result, Address(str1, result));
    } else if (ae == StrIntrinsicNode::UU) {
      load_unsigned_short(cnt1, Address(str2, result, scale));
      load_unsigned_short(result, Address(str1, result, scale));
    } else {
      load_unsigned_short(cnt1, Address(str2, result, scale2));
      load_unsigned_byte(result, Address(str1, result, scale1));
    }
    subl(result, cnt1);
    jmpb(POP_LABEL);
  }//if (VM_Version::supports_avx512vlbw())

  // Discard the stored length difference
  bind(POP_LABEL);
  pop(cnt1);

  // That's it
  bind(DONE_LABEL);
  if(ae == StrIntrinsicNode::UL) {
    negl(result);
  }

}

// Search for Non-ASCII character (Negative byte value) in a byte array,
// return the index of the first such character, otherwise the length
// of the array segment searched.
//   ..\jdk\src\java.base\share\classes\java\lang\StringCoding.java
//   @IntrinsicCandidate
//   public static int countPositives(byte[] ba, int off, int len) {
//     for (int i = off; i < off + len; i++) {
//       if (ba[i] < 0) {
//         return i - off;
//       }
//     }
//     return len;
//   }
void C2_MacroAssembler::count_positives(Register ary1, Register len,
  Register result, Register tmp1,
  XMMRegister vec1, XMMRegister vec2, KRegister mask1, KRegister mask2) {
  // rsi: byte array
  // rcx: len
  // rax: result
  ShortBranchVerifier sbv(this);
  assert_different_registers(ary1, len, result, tmp1);
  assert_different_registers(vec1, vec2);
  Label ADJUST, TAIL_ADJUST, DONE, TAIL_START, CHAR_ADJUST, COMPARE_CHAR, COMPARE_VECTORS, COMPARE_BYTE;

  movl(result, len); // copy
  // len == 0
  testl(len, len);
  jcc(Assembler::zero, DONE);

  if ((AVX3Threshold == 0) && (UseAVX > 2) && // AVX512
    VM_Version::supports_avx512vlbw() &&
    VM_Version::supports_bmi2()) {

    Label test_64_loop, test_tail, BREAK_LOOP;
    movl(tmp1, len);
    vpxor(vec2, vec2, vec2, Assembler::AVX_512bit);

    andl(tmp1, 0x0000003f); // tail count (in chars) 0x3F
    andl(len,  0xffffffc0); // vector count (in chars)
    jccb(Assembler::zero, test_tail);

    lea(ary1, Address(ary1, len, Address::times_1));
    negptr(len);

    bind(test_64_loop);
    // Check whether our 64 elements of size byte contain negatives
    evpcmpgtb(mask1, vec2, Address(ary1, len, Address::times_1), Assembler::AVX_512bit);
    kortestql(mask1, mask1);
    jcc(Assembler::notZero, BREAK_LOOP);

    addptr(len, 64);
    jccb(Assembler::notZero, test_64_loop);

    bind(test_tail);
    // bail out when there is nothing to be done
    testl(tmp1, -1);
    jcc(Assembler::zero, DONE);


    // check the tail for absense of negatives
    // ~(~0 << len) applied up to two times (for 32-bit scenario)
    {
      Register tmp3_aliased = len;
      mov64(tmp3_aliased, 0xFFFFFFFFFFFFFFFF);
      shlxq(tmp3_aliased, tmp3_aliased, tmp1);
      notq(tmp3_aliased);
      kmovql(mask2, tmp3_aliased);
    }

    evpcmpgtb(mask1, mask2, vec2, Address(ary1, 0), Assembler::AVX_512bit);
    ktestq(mask1, mask2);
    jcc(Assembler::zero, DONE);

    // do a full check for negative registers in the tail
    movl(len, tmp1); // tmp1 holds low 6-bit from original len;
                     // ary1 already pointing to the right place
    jmpb(TAIL_START);

    bind(BREAK_LOOP);
    // At least one byte in the last 64 byte block was negative.
    // Set up to look at the last 64 bytes as if they were a tail
    lea(ary1, Address(ary1, len, Address::times_1));
    addptr(result, len);
    // Ignore the very last byte: if all others are positive,
    // it must be negative, so we can skip right to the 2+1 byte
    // end comparison at this point
    orl(result, 63);
    movl(len, 63);
    // Fallthru to tail compare
  } else {

    if (UseAVX >= 2) {
      // With AVX2, use 32-byte vector compare
      Label COMPARE_WIDE_VECTORS, BREAK_LOOP;

      // Compare 32-byte vectors
      testl(len, 0xffffffe0);   // vector count (in bytes)
      jccb(Assembler::zero, TAIL_START);

      andl(len, 0xffffffe0);
      lea(ary1, Address(ary1, len, Address::times_1));
      negptr(len);

      movl(tmp1, 0x80808080);   // create mask to test for Unicode chars in vector
      movdl(vec2, tmp1);
      vpbroadcastd(vec2, vec2, Assembler::AVX_256bit);

      bind(COMPARE_WIDE_VECTORS);
      vmovdqu(vec1, Address(ary1, len, Address::times_1));
      vptest(vec1, vec2);
      jccb(Assembler::notZero, BREAK_LOOP);
      addptr(len, 32);
      jccb(Assembler::notZero, COMPARE_WIDE_VECTORS);

      testl(result, 0x0000001f);   // any bytes remaining?
      jcc(Assembler::zero, DONE);

      // Quick test using the already prepared vector mask
      movl(len, result);
      andl(len, 0x0000001f);
      vmovdqu(vec1, Address(ary1, len, Address::times_1, -32));
      vptest(vec1, vec2);
      jcc(Assembler::zero, DONE);
      // There are zeros, jump to the tail to determine exactly where
      jmpb(TAIL_START);

      bind(BREAK_LOOP);
      // At least one byte in the last 32-byte vector is negative.
      // Set up to look at the last 32 bytes as if they were a tail
      lea(ary1, Address(ary1, len, Address::times_1));
      addptr(result, len);
      // Ignore the very last byte: if all others are positive,
      // it must be negative, so we can skip right to the 2+1 byte
      // end comparison at this point
      orl(result, 31);
      movl(len, 31);
      // Fallthru to tail compare
    } else if (UseSSE42Intrinsics) {
      // With SSE4.2, use double quad vector compare
      Label COMPARE_WIDE_VECTORS, BREAK_LOOP;

      // Compare 16-byte vectors
      testl(len, 0xfffffff0);   // vector count (in bytes)
      jcc(Assembler::zero, TAIL_START);

      andl(len, 0xfffffff0);
      lea(ary1, Address(ary1, len, Address::times_1));
      negptr(len);

      movl(tmp1, 0x80808080);
      movdl(vec2, tmp1);
      pshufd(vec2, vec2, 0);

      bind(COMPARE_WIDE_VECTORS);
      movdqu(vec1, Address(ary1, len, Address::times_1));
      ptest(vec1, vec2);
      jccb(Assembler::notZero, BREAK_LOOP);
      addptr(len, 16);
      jccb(Assembler::notZero, COMPARE_WIDE_VECTORS);

      testl(result, 0x0000000f); // len is zero, any bytes remaining?
      jcc(Assembler::zero, DONE);

      // Quick test using the already prepared vector mask
      movl(len, result);
      andl(len, 0x0000000f);   // tail count (in bytes)
      movdqu(vec1, Address(ary1, len, Address::times_1, -16));
      ptest(vec1, vec2);
      jcc(Assembler::zero, DONE);
      jmpb(TAIL_START);

      bind(BREAK_LOOP);
      // At least one byte in the last 16-byte vector is negative.
      // Set up and look at the last 16 bytes as if they were a tail
      lea(ary1, Address(ary1, len, Address::times_1));
      addptr(result, len);
      // Ignore the very last byte: if all others are positive,
      // it must be negative, so we can skip right to the 2+1 byte
      // end comparison at this point
      orl(result, 15);
      movl(len, 15);
      // Fallthru to tail compare
    }
  }

  bind(TAIL_START);
  // Compare 4-byte vectors
  andl(len, 0xfffffffc); // vector count (in bytes)
  jccb(Assembler::zero, COMPARE_CHAR);

  lea(ary1, Address(ary1, len, Address::times_1));
  negptr(len);

  bind(COMPARE_VECTORS);
  movl(tmp1, Address(ary1, len, Address::times_1));
  andl(tmp1, 0x80808080);
  jccb(Assembler::notZero, TAIL_ADJUST);
  addptr(len, 4);
  jccb(Assembler::notZero, COMPARE_VECTORS);

  // Compare trailing char (final 2-3 bytes), if any
  bind(COMPARE_CHAR);

  testl(result, 0x2);   // tail  char
  jccb(Assembler::zero, COMPARE_BYTE);
  load_unsigned_short(tmp1, Address(ary1, 0));
  andl(tmp1, 0x00008080);
  jccb(Assembler::notZero, CHAR_ADJUST);
  lea(ary1, Address(ary1, 2));

  bind(COMPARE_BYTE);
  testl(result, 0x1);   // tail  byte
  jccb(Assembler::zero, DONE);
  load_unsigned_byte(tmp1, Address(ary1, 0));
  testl(tmp1, 0x00000080);
  jccb(Assembler::zero, DONE);
  subptr(result, 1);
  jmpb(DONE);

  bind(TAIL_ADJUST);
  // there are negative bits in the last 4 byte block.
  // Adjust result and check the next three bytes
  addptr(result, len);
  orl(result, 3);
  lea(ary1, Address(ary1, len, Address::times_1));
  jmpb(COMPARE_CHAR);

  bind(CHAR_ADJUST);
  // We are looking at a char + optional byte tail, and found that one
  // of the bytes in the char is negative. Adjust the result, check the
  // first byte and readjust if needed.
  andl(result, 0xfffffffc);
  testl(tmp1, 0x00000080); // little-endian, so lowest byte comes first
  jccb(Assembler::notZero, DONE);
  addptr(result, 1);

  // That's it
  bind(DONE);
  if (UseAVX >= 2) {
    // clean upper bits of YMM registers
    vpxor(vec1, vec1);
    vpxor(vec2, vec2);
  }
}

// Compare char[] or byte[] arrays aligned to 4 bytes or substrings.
void C2_MacroAssembler::arrays_equals(bool is_array_equ, Register ary1, Register ary2,
                                      Register limit, Register result, Register chr,
                                      XMMRegister vec1, XMMRegister vec2, bool is_char,
                                      KRegister mask, bool expand_ary2) {
  // for expand_ary2, limit is the (smaller) size of the second array.
  ShortBranchVerifier sbv(this);
  Label TRUE_LABEL, FALSE_LABEL, DONE, COMPARE_VECTORS, COMPARE_CHAR, COMPARE_BYTE;

  assert((!expand_ary2) || ((expand_ary2) && (UseAVX == 2)),
         "Expansion only implemented for AVX2");

  int length_offset  = arrayOopDesc::length_offset_in_bytes();
  int base_offset    = arrayOopDesc::base_offset_in_bytes(is_char ? T_CHAR : T_BYTE);

  Address::ScaleFactor scaleFactor = expand_ary2 ? Address::times_2 : Address::times_1;
  int scaleIncr = expand_ary2 ? 8 : 16;

  if (is_array_equ) {
    // Check the input args
    cmpoop(ary1, ary2);
    jcc(Assembler::equal, TRUE_LABEL);

    // Need additional checks for arrays_equals.
    testptr(ary1, ary1);
    jcc(Assembler::zero, FALSE_LABEL);
    testptr(ary2, ary2);
    jcc(Assembler::zero, FALSE_LABEL);

    // Check the lengths
    movl(limit, Address(ary1, length_offset));
    cmpl(limit, Address(ary2, length_offset));
    jcc(Assembler::notEqual, FALSE_LABEL);
  }

  // count == 0
  testl(limit, limit);
  jcc(Assembler::zero, TRUE_LABEL);

  if (is_array_equ) {
    // Load array address
    lea(ary1, Address(ary1, base_offset));
    lea(ary2, Address(ary2, base_offset));
  }

  if (is_array_equ && is_char) {
    // arrays_equals when used for char[].
    shll(limit, 1);      // byte count != 0
  }
  movl(result, limit); // copy

  if (UseAVX >= 2) {
    // With AVX2, use 32-byte vector compare
    Label COMPARE_WIDE_VECTORS, COMPARE_WIDE_VECTORS_16, COMPARE_TAIL, COMPARE_TAIL_16;

    // Compare 32-byte vectors
    if (expand_ary2) {
      andl(result, 0x0000000f);  //   tail count (in bytes)
      andl(limit, 0xfffffff0);   // vector count (in bytes)
      jcc(Assembler::zero, COMPARE_TAIL);
    } else {
      andl(result, 0x0000001f);  //   tail count (in bytes)
      andl(limit, 0xffffffe0);   // vector count (in bytes)
      jcc(Assembler::zero, COMPARE_TAIL_16);
    }

    lea(ary1, Address(ary1, limit, scaleFactor));
    lea(ary2, Address(ary2, limit, Address::times_1));
    negptr(limit);

    if ((AVX3Threshold == 0) && VM_Version::supports_avx512vlbw()) { // trying 64 bytes fast loop
      Label COMPARE_WIDE_VECTORS_LOOP_AVX2, COMPARE_WIDE_VECTORS_LOOP_AVX3;

      cmpl(limit, -64);
      jcc(Assembler::greater, COMPARE_WIDE_VECTORS_LOOP_AVX2);

      bind(COMPARE_WIDE_VECTORS_LOOP_AVX3); // the hottest loop

      evmovdquq(vec1, Address(ary1, limit, Address::times_1), Assembler::AVX_512bit);
      evpcmpeqb(mask, vec1, Address(ary2, limit, Address::times_1), Assembler::AVX_512bit);
      kortestql(mask, mask);
      jcc(Assembler::aboveEqual, FALSE_LABEL);     // miscompare
      addptr(limit, 64);  // update since we already compared at this addr
      cmpl(limit, -64);
      jccb(Assembler::lessEqual, COMPARE_WIDE_VECTORS_LOOP_AVX3);

      // At this point we may still need to compare -limit+result bytes.
      // We could execute the next two instruction and just continue via non-wide path:
      //  cmpl(limit, 0);
      //  jcc(Assembler::equal, COMPARE_TAIL);  // true
      // But since we stopped at the points ary{1,2}+limit which are
      // not farther than 64 bytes from the ends of arrays ary{1,2}+result
      // (|limit| <= 32 and result < 32),
      // we may just compare the last 64 bytes.
      //
      addptr(result, -64);   // it is safe, bc we just came from this area
      evmovdquq(vec1, Address(ary1, result, Address::times_1), Assembler::AVX_512bit);
      evpcmpeqb(mask, vec1, Address(ary2, result, Address::times_1), Assembler::AVX_512bit);
      kortestql(mask, mask);
      jcc(Assembler::aboveEqual, FALSE_LABEL);     // miscompare

      jmp(TRUE_LABEL);

      bind(COMPARE_WIDE_VECTORS_LOOP_AVX2);

    }//if (VM_Version::supports_avx512vlbw())

    bind(COMPARE_WIDE_VECTORS);
    vmovdqu(vec1, Address(ary1, limit, scaleFactor));
    if (expand_ary2) {
      vpmovzxbw(vec2, Address(ary2, limit, Address::times_1), Assembler::AVX_256bit);
    } else {
      vmovdqu(vec2, Address(ary2, limit, Address::times_1));
    }
    vpxor(vec1, vec2);

    vptest(vec1, vec1);
    jcc(Assembler::notZero, FALSE_LABEL);
    addptr(limit, scaleIncr * 2);
    jcc(Assembler::notZero, COMPARE_WIDE_VECTORS);

    testl(result, result);
    jcc(Assembler::zero, TRUE_LABEL);

    vmovdqu(vec1, Address(ary1, result, scaleFactor, -32));
    if (expand_ary2) {
      vpmovzxbw(vec2, Address(ary2, result, Address::times_1, -16), Assembler::AVX_256bit);
    } else {
      vmovdqu(vec2, Address(ary2, result, Address::times_1, -32));
    }
    vpxor(vec1, vec2);

    vptest(vec1, vec1);
    jcc(Assembler::notZero, FALSE_LABEL);
    jmp(TRUE_LABEL);

    bind(COMPARE_TAIL_16); // limit is zero
    movl(limit, result);

    // Compare 16-byte chunks
    andl(result, 0x0000000f);  //   tail count (in bytes)
    andl(limit, 0xfffffff0);   // vector count (in bytes)
    jcc(Assembler::zero, COMPARE_TAIL);

    lea(ary1, Address(ary1, limit, scaleFactor));
    lea(ary2, Address(ary2, limit, Address::times_1));
    negptr(limit);

    bind(COMPARE_WIDE_VECTORS_16);
    movdqu(vec1, Address(ary1, limit, scaleFactor));
    if (expand_ary2) {
      vpmovzxbw(vec2, Address(ary2, limit, Address::times_1), Assembler::AVX_128bit);
    } else {
      movdqu(vec2, Address(ary2, limit, Address::times_1));
    }
    pxor(vec1, vec2);

    ptest(vec1, vec1);
    jcc(Assembler::notZero, FALSE_LABEL);
    addptr(limit, scaleIncr);
    jcc(Assembler::notZero, COMPARE_WIDE_VECTORS_16);

    bind(COMPARE_TAIL); // limit is zero
    movl(limit, result);
    // Fallthru to tail compare
  } else if (UseSSE42Intrinsics) {
    // With SSE4.2, use double quad vector compare
    Label COMPARE_WIDE_VECTORS, COMPARE_TAIL;

    // Compare 16-byte vectors
    andl(result, 0x0000000f);  //   tail count (in bytes)
    andl(limit, 0xfffffff0);   // vector count (in bytes)
    jcc(Assembler::zero, COMPARE_TAIL);

    lea(ary1, Address(ary1, limit, Address::times_1));
    lea(ary2, Address(ary2, limit, Address::times_1));
    negptr(limit);

    bind(COMPARE_WIDE_VECTORS);
    movdqu(vec1, Address(ary1, limit, Address::times_1));
    movdqu(vec2, Address(ary2, limit, Address::times_1));
    pxor(vec1, vec2);

    ptest(vec1, vec1);
    jcc(Assembler::notZero, FALSE_LABEL);
    addptr(limit, 16);
    jcc(Assembler::notZero, COMPARE_WIDE_VECTORS);

    testl(result, result);
    jcc(Assembler::zero, TRUE_LABEL);

    movdqu(vec1, Address(ary1, result, Address::times_1, -16));
    movdqu(vec2, Address(ary2, result, Address::times_1, -16));
    pxor(vec1, vec2);

    ptest(vec1, vec1);
    jccb(Assembler::notZero, FALSE_LABEL);
    jmpb(TRUE_LABEL);

    bind(COMPARE_TAIL); // limit is zero
    movl(limit, result);
    // Fallthru to tail compare
  }

  // Compare 4-byte vectors
  if (expand_ary2) {
    testl(result, result);
    jccb(Assembler::zero, TRUE_LABEL);
  } else {
    andl(limit, 0xfffffffc); // vector count (in bytes)
    jccb(Assembler::zero, COMPARE_CHAR);
  }

  lea(ary1, Address(ary1, limit, scaleFactor));
  lea(ary2, Address(ary2, limit, Address::times_1));
  negptr(limit);

  bind(COMPARE_VECTORS);
  if (expand_ary2) {
    // There are no "vector" operations for bytes to shorts
    movzbl(chr, Address(ary2, limit, Address::times_1));
    cmpw(Address(ary1, limit, Address::times_2), chr);
    jccb(Assembler::notEqual, FALSE_LABEL);
    addptr(limit, 1);
    jcc(Assembler::notZero, COMPARE_VECTORS);
    jmp(TRUE_LABEL);
  } else {
    movl(chr, Address(ary1, limit, Address::times_1));
    cmpl(chr, Address(ary2, limit, Address::times_1));
    jccb(Assembler::notEqual, FALSE_LABEL);
    addptr(limit, 4);
    jcc(Assembler::notZero, COMPARE_VECTORS);
  }

  // Compare trailing char (final 2 bytes), if any
  bind(COMPARE_CHAR);
  testl(result, 0x2);   // tail  char
  jccb(Assembler::zero, COMPARE_BYTE);
  load_unsigned_short(chr, Address(ary1, 0));
  load_unsigned_short(limit, Address(ary2, 0));
  cmpl(chr, limit);
  jccb(Assembler::notEqual, FALSE_LABEL);

  if (is_array_equ && is_char) {
    bind(COMPARE_BYTE);
  } else {
    lea(ary1, Address(ary1, 2));
    lea(ary2, Address(ary2, 2));

    bind(COMPARE_BYTE);
    testl(result, 0x1);   // tail  byte
    jccb(Assembler::zero, TRUE_LABEL);
    load_unsigned_byte(chr, Address(ary1, 0));
    load_unsigned_byte(limit, Address(ary2, 0));
    cmpl(chr, limit);
    jccb(Assembler::notEqual, FALSE_LABEL);
  }
  bind(TRUE_LABEL);
  movl(result, 1);   // return true
  jmpb(DONE);

  bind(FALSE_LABEL);
  xorl(result, result); // return false

  // That's it
  bind(DONE);
  if (UseAVX >= 2) {
    // clean upper bits of YMM registers
    vpxor(vec1, vec1);
    vpxor(vec2, vec2);
  }
}

static void convertF2I_slowpath(C2_MacroAssembler& masm, C2GeneralStub<Register, XMMRegister, address>& stub) {
#define __ masm.
  Register dst = stub.data<0>();
  XMMRegister src = stub.data<1>();
  address target = stub.data<2>();
  __ bind(stub.entry());
  __ subptr(rsp, 8);
  __ movdbl(Address(rsp), src);
  __ call(RuntimeAddress(target));
  // APX REX2 encoding for pop(dst) increases the stub size by 1 byte.
  __ pop(dst);
  __ jmp(stub.continuation());
#undef __
}

void C2_MacroAssembler::convertF2I(BasicType dst_bt, BasicType src_bt, Register dst, XMMRegister src) {
  assert(dst_bt == T_INT || dst_bt == T_LONG, "");
  assert(src_bt == T_FLOAT || src_bt == T_DOUBLE, "");

  address slowpath_target;
  if (dst_bt == T_INT) {
    if (src_bt == T_FLOAT) {
      cvttss2sil(dst, src);
      cmpl(dst, 0x80000000);
      slowpath_target = StubRoutines::x86::f2i_fixup();
    } else {
      cvttsd2sil(dst, src);
      cmpl(dst, 0x80000000);
      slowpath_target = StubRoutines::x86::d2i_fixup();
    }
  } else {
    if (src_bt == T_FLOAT) {
      cvttss2siq(dst, src);
      cmp64(dst, ExternalAddress(StubRoutines::x86::double_sign_flip()));
      slowpath_target = StubRoutines::x86::f2l_fixup();
    } else {
      cvttsd2siq(dst, src);
      cmp64(dst, ExternalAddress(StubRoutines::x86::double_sign_flip()));
      slowpath_target = StubRoutines::x86::d2l_fixup();
    }
  }

  // Using the APX extended general purpose registers increases the instruction encoding size by 1 byte.
  int max_size = 23 + (UseAPX ? 1 : 0);
  auto stub = C2CodeStub::make<Register, XMMRegister, address>(dst, src, slowpath_target, max_size, convertF2I_slowpath);
  jcc(Assembler::equal, stub->entry());
  bind(stub->continuation());
}

void C2_MacroAssembler::evmasked_op(int ideal_opc, BasicType eType, KRegister mask, XMMRegister dst,
                                    XMMRegister src1, int imm8, bool merge, int vlen_enc) {
  switch(ideal_opc) {
    case Op_LShiftVS:
      Assembler::evpsllw(dst, mask, src1, imm8, merge, vlen_enc); break;
    case Op_LShiftVI:
      Assembler::evpslld(dst, mask, src1, imm8, merge, vlen_enc); break;
    case Op_LShiftVL:
      Assembler::evpsllq(dst, mask, src1, imm8, merge, vlen_enc); break;
    case Op_RShiftVS:
      Assembler::evpsraw(dst, mask, src1, imm8, merge, vlen_enc); break;
    case Op_RShiftVI:
      Assembler::evpsrad(dst, mask, src1, imm8, merge, vlen_enc); break;
    case Op_RShiftVL:
      Assembler::evpsraq(dst, mask, src1, imm8, merge, vlen_enc); break;
    case Op_URShiftVS:
      Assembler::evpsrlw(dst, mask, src1, imm8, merge, vlen_enc); break;
    case Op_URShiftVI:
      Assembler::evpsrld(dst, mask, src1, imm8, merge, vlen_enc); break;
    case Op_URShiftVL:
      Assembler::evpsrlq(dst, mask, src1, imm8, merge, vlen_enc); break;
    case Op_RotateRightV:
      evrord(eType, dst, mask, src1, imm8, merge, vlen_enc); break;
    case Op_RotateLeftV:
      evrold(eType, dst, mask, src1, imm8, merge, vlen_enc); break;
    default:
      fatal("Unsupported operation  %s", NodeClassNames[ideal_opc]);
      break;
  }
}

void C2_MacroAssembler::evmasked_saturating_op(int ideal_opc, BasicType elem_bt, KRegister mask, XMMRegister dst, XMMRegister src1,
                                               XMMRegister src2, bool is_unsigned, bool merge, int vlen_enc) {
  if (is_unsigned) {
    evmasked_saturating_unsigned_op(ideal_opc, elem_bt, mask, dst, src1, src2, merge, vlen_enc);
  } else {
    evmasked_saturating_signed_op(ideal_opc, elem_bt, mask, dst, src1, src2, merge, vlen_enc);
  }
}

void C2_MacroAssembler::evmasked_saturating_signed_op(int ideal_opc, BasicType elem_bt, KRegister mask, XMMRegister dst,
                                                      XMMRegister src1, XMMRegister src2, bool merge, int vlen_enc) {
  switch (elem_bt) {
    case T_BYTE:
      if (ideal_opc == Op_SaturatingAddV) {
        evpaddsb(dst, mask, src1, src2, merge, vlen_enc);
      } else {
        assert(ideal_opc == Op_SaturatingSubV, "");
        evpsubsb(dst, mask, src1, src2, merge, vlen_enc);
      }
      break;
    case T_SHORT:
      if (ideal_opc == Op_SaturatingAddV) {
        evpaddsw(dst, mask, src1, src2, merge, vlen_enc);
      } else {
        assert(ideal_opc == Op_SaturatingSubV, "");
        evpsubsw(dst, mask, src1, src2, merge, vlen_enc);
      }
      break;
    default:
      fatal("Unsupported type %s", type2name(elem_bt));
      break;
  }
}

void C2_MacroAssembler::evmasked_saturating_unsigned_op(int ideal_opc, BasicType elem_bt, KRegister mask, XMMRegister dst,
                                                        XMMRegister src1, XMMRegister src2, bool merge, int vlen_enc) {
  switch (elem_bt) {
    case T_BYTE:
      if (ideal_opc == Op_SaturatingAddV) {
        evpaddusb(dst, mask, src1, src2, merge, vlen_enc);
      } else {
        assert(ideal_opc == Op_SaturatingSubV, "");
        evpsubusb(dst, mask, src1, src2, merge, vlen_enc);
      }
      break;
    case T_SHORT:
      if (ideal_opc == Op_SaturatingAddV) {
        evpaddusw(dst, mask, src1, src2, merge, vlen_enc);
      } else {
        assert(ideal_opc == Op_SaturatingSubV, "");
        evpsubusw(dst, mask, src1, src2, merge, vlen_enc);
      }
      break;
    default:
      fatal("Unsupported type %s", type2name(elem_bt));
      break;
  }
}

void C2_MacroAssembler::evmasked_saturating_op(int ideal_opc, BasicType elem_bt, KRegister mask, XMMRegister dst, XMMRegister src1,
                                               Address src2, bool is_unsigned, bool merge, int vlen_enc) {
  if (is_unsigned) {
    evmasked_saturating_unsigned_op(ideal_opc, elem_bt, mask, dst, src1, src2, merge, vlen_enc);
  } else {
    evmasked_saturating_signed_op(ideal_opc, elem_bt, mask, dst, src1, src2, merge, vlen_enc);
  }
}

void C2_MacroAssembler::evmasked_saturating_signed_op(int ideal_opc, BasicType elem_bt, KRegister mask, XMMRegister dst,
                                                      XMMRegister src1, Address src2, bool merge, int vlen_enc) {
  switch (elem_bt) {
    case T_BYTE:
      if (ideal_opc == Op_SaturatingAddV) {
        evpaddsb(dst, mask, src1, src2, merge, vlen_enc);
      } else {
        assert(ideal_opc == Op_SaturatingSubV, "");
        evpsubsb(dst, mask, src1, src2, merge, vlen_enc);
      }
      break;
    case T_SHORT:
      if (ideal_opc == Op_SaturatingAddV) {
        evpaddsw(dst, mask, src1, src2, merge, vlen_enc);
      } else {
        assert(ideal_opc == Op_SaturatingSubV, "");
        evpsubsw(dst, mask, src1, src2, merge, vlen_enc);
      }
      break;
    default:
      fatal("Unsupported type %s", type2name(elem_bt));
      break;
  }
}

void C2_MacroAssembler::evmasked_saturating_unsigned_op(int ideal_opc, BasicType elem_bt, KRegister mask, XMMRegister dst,
                                                        XMMRegister src1, Address src2, bool merge, int vlen_enc) {
  switch (elem_bt) {
    case T_BYTE:
      if (ideal_opc == Op_SaturatingAddV) {
        evpaddusb(dst, mask, src1, src2, merge, vlen_enc);
      } else {
        assert(ideal_opc == Op_SaturatingSubV, "");
        evpsubusb(dst, mask, src1, src2, merge, vlen_enc);
      }
      break;
    case T_SHORT:
      if (ideal_opc == Op_SaturatingAddV) {
        evpaddusw(dst, mask, src1, src2, merge, vlen_enc);
      } else {
        assert(ideal_opc == Op_SaturatingSubV, "");
        evpsubusw(dst, mask, src1, src2, merge, vlen_enc);
      }
      break;
    default:
      fatal("Unsupported type %s", type2name(elem_bt));
      break;
  }
}

void C2_MacroAssembler::evmasked_op(int ideal_opc, BasicType eType, KRegister mask, XMMRegister dst,
                                    XMMRegister src1, XMMRegister src2, bool merge, int vlen_enc,
                                    bool is_varshift) {
  switch (ideal_opc) {
    case Op_AddVB:
      evpaddb(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_AddVS:
      evpaddw(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_AddVI:
      evpaddd(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_AddVL:
      evpaddq(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_AddVF:
      evaddps(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_AddVD:
      evaddpd(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_SubVB:
      evpsubb(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_SubVS:
      evpsubw(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_SubVI:
      evpsubd(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_SubVL:
      evpsubq(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_SubVF:
      evsubps(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_SubVD:
      evsubpd(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_MulVS:
      evpmullw(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_MulVI:
      evpmulld(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_MulVL:
      evpmullq(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_MulVF:
      evmulps(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_MulVD:
      evmulpd(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_DivVF:
      evdivps(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_DivVD:
      evdivpd(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_SqrtVF:
      evsqrtps(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_SqrtVD:
      evsqrtpd(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_AbsVB:
      evpabsb(dst, mask, src2, merge, vlen_enc); break;
    case Op_AbsVS:
      evpabsw(dst, mask, src2, merge, vlen_enc); break;
    case Op_AbsVI:
      evpabsd(dst, mask, src2, merge, vlen_enc); break;
    case Op_AbsVL:
      evpabsq(dst, mask, src2, merge, vlen_enc); break;
    case Op_FmaVF:
      evpfma213ps(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_FmaVD:
      evpfma213pd(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_VectorRearrange:
      evperm(eType, dst, mask, src2, src1, merge, vlen_enc); break;
    case Op_LShiftVS:
      evpsllw(dst, mask, src1, src2, merge, vlen_enc, is_varshift); break;
    case Op_LShiftVI:
      evpslld(dst, mask, src1, src2, merge, vlen_enc, is_varshift); break;
    case Op_LShiftVL:
      evpsllq(dst, mask, src1, src2, merge, vlen_enc, is_varshift); break;
    case Op_RShiftVS:
      evpsraw(dst, mask, src1, src2, merge, vlen_enc, is_varshift); break;
    case Op_RShiftVI:
      evpsrad(dst, mask, src1, src2, merge, vlen_enc, is_varshift); break;
    case Op_RShiftVL:
      evpsraq(dst, mask, src1, src2, merge, vlen_enc, is_varshift); break;
    case Op_URShiftVS:
      evpsrlw(dst, mask, src1, src2, merge, vlen_enc, is_varshift); break;
    case Op_URShiftVI:
      evpsrld(dst, mask, src1, src2, merge, vlen_enc, is_varshift); break;
    case Op_URShiftVL:
      evpsrlq(dst, mask, src1, src2, merge, vlen_enc, is_varshift); break;
    case Op_RotateLeftV:
      evrold(eType, dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_RotateRightV:
      evrord(eType, dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_MaxV:
      evpmaxs(eType, dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_MinV:
      evpmins(eType, dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_UMinV:
      evpminu(eType, dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_UMaxV:
      evpmaxu(eType, dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_XorV:
      evxor(eType, dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_OrV:
      evor(eType, dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_AndV:
      evand(eType, dst, mask, src1, src2, merge, vlen_enc); break;
    default:
      fatal("Unsupported operation  %s", NodeClassNames[ideal_opc]);
      break;
  }
}

void C2_MacroAssembler::evmasked_op(int ideal_opc, BasicType eType, KRegister mask, XMMRegister dst,
                                    XMMRegister src1, Address src2, bool merge, int vlen_enc) {
  switch (ideal_opc) {
    case Op_AddVB:
      evpaddb(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_AddVS:
      evpaddw(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_AddVI:
      evpaddd(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_AddVL:
      evpaddq(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_AddVF:
      evaddps(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_AddVD:
      evaddpd(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_SubVB:
      evpsubb(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_SubVS:
      evpsubw(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_SubVI:
      evpsubd(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_SubVL:
      evpsubq(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_SubVF:
      evsubps(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_SubVD:
      evsubpd(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_MulVS:
      evpmullw(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_MulVI:
      evpmulld(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_MulVL:
      evpmullq(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_MulVF:
      evmulps(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_MulVD:
      evmulpd(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_DivVF:
      evdivps(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_DivVD:
      evdivpd(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_FmaVF:
      evpfma213ps(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_FmaVD:
      evpfma213pd(dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_MaxV:
      evpmaxs(eType, dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_MinV:
      evpmins(eType, dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_UMaxV:
      evpmaxu(eType, dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_UMinV:
      evpminu(eType, dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_XorV:
      evxor(eType, dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_OrV:
      evor(eType, dst, mask, src1, src2, merge, vlen_enc); break;
    case Op_AndV:
      evand(eType, dst, mask, src1, src2, merge, vlen_enc); break;
    default:
      fatal("Unsupported operation  %s", NodeClassNames[ideal_opc]);
      break;
  }
}

void C2_MacroAssembler::masked_op(int ideal_opc, int mask_len, KRegister dst,
                                  KRegister src1, KRegister src2) {
  BasicType etype = T_ILLEGAL;
  switch(mask_len) {
    case 2:
    case 4:
    case 8:  etype = T_BYTE; break;
    case 16: etype = T_SHORT; break;
    case 32: etype = T_INT; break;
    case 64: etype = T_LONG; break;
    default: fatal("Unsupported type"); break;
  }
  assert(etype != T_ILLEGAL, "");
  switch(ideal_opc) {
    case Op_AndVMask:
      kand(etype, dst, src1, src2); break;
    case Op_OrVMask:
      kor(etype, dst, src1, src2); break;
    case Op_XorVMask:
      kxor(etype, dst, src1, src2); break;
    default:
      fatal("Unsupported masked operation"); break;
  }
}

/*
 * Following routine handles special floating point values(NaN/Inf/-Inf/Max/Min) for casting operation.
 * If src is NaN, the result is 0.
 * If the src is negative infinity or any value less than or equal to the value of Integer.MIN_VALUE,
 * the result is equal to the value of Integer.MIN_VALUE.
 * If the src is positive infinity or any value greater than or equal to the value of Integer.MAX_VALUE,
 * the result is equal to the value of Integer.MAX_VALUE.
 */
void C2_MacroAssembler::vector_cast_float_to_int_special_cases_avx(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                                                   XMMRegister xtmp2, XMMRegister xtmp3, XMMRegister xtmp4,
                                                                   Register rscratch, AddressLiteral float_sign_flip,
                                                                   int vec_enc) {
  assert(rscratch != noreg || always_reachable(float_sign_flip), "missing");
  Label done;
  vmovdqu(xtmp1, float_sign_flip, vec_enc, rscratch);
  vpcmpeqd(xtmp2, dst, xtmp1, vec_enc);
  vptest(xtmp2, xtmp2, vec_enc);
  jccb(Assembler::equal, done);

  vpcmpeqd(xtmp4, xtmp4, xtmp4, vec_enc);
  vpxor(xtmp1, xtmp1, xtmp4, vec_enc);

  vpxor(xtmp4, xtmp4, xtmp4, vec_enc);
  vcmpps(xtmp3, src, src, Assembler::UNORD_Q, vec_enc);
  vblendvps(dst, dst, xtmp4, xtmp3, vec_enc);

  // Recompute the mask for remaining special value.
  vpxor(xtmp2, xtmp2, xtmp3, vec_enc);
  // Extract SRC values corresponding to TRUE mask lanes.
  vpand(xtmp4, xtmp2, src, vec_enc);
  // Flip mask bits so that MSB bit of MASK lanes corresponding to +ve special
  // values are set.
  vpxor(xtmp3, xtmp2, xtmp4, vec_enc);

  vblendvps(dst, dst, xtmp1, xtmp3, vec_enc);
  bind(done);
}

void C2_MacroAssembler::vector_cast_float_to_int_special_cases_evex(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                                                    XMMRegister xtmp2, KRegister ktmp1, KRegister ktmp2,
                                                                    Register rscratch, AddressLiteral float_sign_flip,
                                                                    int vec_enc) {
  assert(rscratch != noreg || always_reachable(float_sign_flip), "missing");
  Label done;
  evmovdqul(xtmp1, k0, float_sign_flip, false, vec_enc, rscratch);
  Assembler::evpcmpeqd(ktmp1, k0, xtmp1, dst, vec_enc);
  kortestwl(ktmp1, ktmp1);
  jccb(Assembler::equal, done);

  vpxor(xtmp2, xtmp2, xtmp2, vec_enc);
  evcmpps(ktmp2, k0, src, src, Assembler::UNORD_Q, vec_enc);
  evmovdqul(dst, ktmp2, xtmp2, true, vec_enc);

  kxorwl(ktmp1, ktmp1, ktmp2);
  evcmpps(ktmp1, ktmp1, src, xtmp2, Assembler::NLT_UQ, vec_enc);
  vpternlogd(xtmp2, 0x11, xtmp1, xtmp1, vec_enc);
  evmovdqul(dst, ktmp1, xtmp2, true, vec_enc);
  bind(done);
}

void C2_MacroAssembler::vector_cast_float_to_long_special_cases_evex(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                                                     XMMRegister xtmp2, KRegister ktmp1, KRegister ktmp2,
                                                                     Register rscratch, AddressLiteral double_sign_flip,
                                                                     int vec_enc) {
  assert(rscratch != noreg || always_reachable(double_sign_flip), "missing");

  Label done;
  evmovdquq(xtmp1, k0, double_sign_flip, false, vec_enc, rscratch);
  Assembler::evpcmpeqq(ktmp1, k0, xtmp1, dst, vec_enc);
  kortestwl(ktmp1, ktmp1);
  jccb(Assembler::equal, done);

  vpxor(xtmp2, xtmp2, xtmp2, vec_enc);
  evcmpps(ktmp2, k0, src, src, Assembler::UNORD_Q, vec_enc);
  evmovdquq(dst, ktmp2, xtmp2, true, vec_enc);

  kxorwl(ktmp1, ktmp1, ktmp2);
  evcmpps(ktmp1, ktmp1, src, xtmp2, Assembler::NLT_UQ, vec_enc);
  vpternlogq(xtmp2, 0x11, xtmp1, xtmp1, vec_enc);
  evmovdquq(dst, ktmp1, xtmp2, true, vec_enc);
  bind(done);
}

void C2_MacroAssembler::vector_cast_double_to_int_special_cases_evex(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                                                     XMMRegister xtmp2, KRegister ktmp1, KRegister ktmp2,
                                                                     Register rscratch, AddressLiteral float_sign_flip,
                                                                     int vec_enc) {
  assert(rscratch != noreg || always_reachable(float_sign_flip), "missing");
  Label done;
  evmovdquq(xtmp1, k0, float_sign_flip, false, vec_enc, rscratch);
  Assembler::evpcmpeqd(ktmp1, k0, xtmp1, dst, vec_enc);
  kortestwl(ktmp1, ktmp1);
  jccb(Assembler::equal, done);

  vpxor(xtmp2, xtmp2, xtmp2, vec_enc);
  evcmppd(ktmp2, k0, src, src, Assembler::UNORD_Q, vec_enc);
  evmovdqul(dst, ktmp2, xtmp2, true, vec_enc);

  kxorwl(ktmp1, ktmp1, ktmp2);
  evcmppd(ktmp1, ktmp1, src, xtmp2, Assembler::NLT_UQ, vec_enc);
  vpternlogq(xtmp2, 0x11, xtmp1, xtmp1, vec_enc);
  evmovdqul(dst, ktmp1, xtmp2, true, vec_enc);
  bind(done);
}

/*
 * Following routine handles special floating point values(NaN/Inf/-Inf/Max/Min) for casting operation.
 * If src is NaN, the result is 0.
 * If the src is negative infinity or any value less than or equal to the value of Long.MIN_VALUE,
 * the result is equal to the value of Long.MIN_VALUE.
 * If the src is positive infinity or any value greater than or equal to the value of Long.MAX_VALUE,
 * the result is equal to the value of Long.MAX_VALUE.
 */
void C2_MacroAssembler::vector_cast_double_to_long_special_cases_evex(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                                                      XMMRegister xtmp2, KRegister ktmp1, KRegister ktmp2,
                                                                      Register rscratch, AddressLiteral double_sign_flip,
                                                                      int vec_enc) {
  assert(rscratch != noreg || always_reachable(double_sign_flip), "missing");

  Label done;
  evmovdqul(xtmp1, k0, double_sign_flip, false, vec_enc, rscratch);
  evpcmpeqq(ktmp1, xtmp1, dst, vec_enc);
  kortestwl(ktmp1, ktmp1);
  jccb(Assembler::equal, done);

  vpxor(xtmp2, xtmp2, xtmp2, vec_enc);
  evcmppd(ktmp2, k0, src, src, Assembler::UNORD_Q, vec_enc);
  evmovdquq(dst, ktmp2, xtmp2, true, vec_enc);

  kxorwl(ktmp1, ktmp1, ktmp2);
  evcmppd(ktmp1, ktmp1, src, xtmp2, Assembler::NLT_UQ, vec_enc);
  vpternlogq(xtmp2, 0x11, xtmp1, xtmp1, vec_enc);
  evmovdquq(dst, ktmp1, xtmp2, true, vec_enc);
  bind(done);
}

void C2_MacroAssembler::vector_crosslane_doubleword_pack_avx(XMMRegister dst, XMMRegister src, XMMRegister zero,
                                                             XMMRegister xtmp, int index, int vec_enc) {
   assert(vec_enc < Assembler::AVX_512bit, "");
   if (vec_enc == Assembler::AVX_256bit) {
     vextractf128_high(xtmp, src);
     vshufps(dst, src, xtmp, index, vec_enc);
   } else {
     vshufps(dst, src, zero, index, vec_enc);
   }
}

void C2_MacroAssembler::vector_cast_double_to_int_special_cases_avx(XMMRegister dst, XMMRegister src, XMMRegister xtmp1, XMMRegister xtmp2,
                                                                    XMMRegister xtmp3, XMMRegister xtmp4, XMMRegister xtmp5, Register rscratch,
                                                                    AddressLiteral float_sign_flip, int src_vec_enc) {
  assert(rscratch != noreg || always_reachable(float_sign_flip), "missing");

  Label done;
  // Compare the destination lanes with float_sign_flip
  // value to get mask for all special values.
  movdqu(xtmp1, float_sign_flip, rscratch);
  vpcmpeqd(xtmp2, dst, xtmp1, Assembler::AVX_128bit);
  ptest(xtmp2, xtmp2);
  jccb(Assembler::equal, done);

  // Flip float_sign_flip to get max integer value.
  vpcmpeqd(xtmp4, xtmp4, xtmp4, Assembler::AVX_128bit);
  pxor(xtmp1, xtmp4);

  // Set detination lanes corresponding to unordered source lanes as zero.
  vpxor(xtmp4, xtmp4, xtmp4, src_vec_enc);
  vcmppd(xtmp3, src, src, Assembler::UNORD_Q, src_vec_enc);

  // Shuffle mask vector and pack lower doubles word from each quadword lane.
  vector_crosslane_doubleword_pack_avx(xtmp3, xtmp3, xtmp4, xtmp5, 0x88, src_vec_enc);
  vblendvps(dst, dst, xtmp4, xtmp3, Assembler::AVX_128bit);

  // Recompute the mask for remaining special value.
  pxor(xtmp2, xtmp3);
  // Extract mask corresponding to non-negative source lanes.
  vcmppd(xtmp3, src, xtmp4, Assembler::NLT_UQ, src_vec_enc);

  // Shuffle mask vector and pack lower doubles word from each quadword lane.
  vector_crosslane_doubleword_pack_avx(xtmp3, xtmp3, xtmp4, xtmp5, 0x88, src_vec_enc);
  pand(xtmp3, xtmp2);

  // Replace destination lanes holding special value(0x80000000) with max int
  // if corresponding source lane holds a +ve value.
  vblendvps(dst, dst, xtmp1, xtmp3, Assembler::AVX_128bit);
  bind(done);
}


void C2_MacroAssembler::vector_cast_int_to_subword(BasicType to_elem_bt, XMMRegister dst, XMMRegister zero,
                                                   XMMRegister xtmp, Register rscratch, int vec_enc) {
  switch(to_elem_bt) {
    case T_SHORT:
      assert(rscratch != noreg || always_reachable(ExternalAddress(StubRoutines::x86::vector_int_to_short_mask())), "missing");
      vpand(dst, dst, ExternalAddress(StubRoutines::x86::vector_int_to_short_mask()), vec_enc, rscratch);
      vpackusdw(dst, dst, zero, vec_enc);
      if (vec_enc == Assembler::AVX_256bit) {
        vector_crosslane_doubleword_pack_avx(dst, dst, zero, xtmp, 0x44, vec_enc);
      }
      break;
    case  T_BYTE:
      assert(rscratch != noreg || always_reachable(ExternalAddress(StubRoutines::x86::vector_int_to_byte_mask())), "missing");
      vpand(dst, dst, ExternalAddress(StubRoutines::x86::vector_int_to_byte_mask()), vec_enc, rscratch);
      vpackusdw(dst, dst, zero, vec_enc);
      if (vec_enc == Assembler::AVX_256bit) {
        vector_crosslane_doubleword_pack_avx(dst, dst, zero, xtmp, 0x44, vec_enc);
      }
      vpackuswb(dst, dst, zero, vec_enc);
      break;
    default: assert(false, "%s", type2name(to_elem_bt));
  }
}

/*
 * Algorithm for vector D2L and F2I conversions:-
 * a) Perform vector D2L/F2I cast.
 * b) Choose fast path if none of the result vector lane contains 0x80000000 value.
 *    It signifies that source value could be any of the special floating point
 *    values(NaN,-Inf,Inf,Max,-Min).
 * c) Set destination to zero if source is NaN value.
 * d) Replace 0x80000000 with MaxInt if source lane contains a +ve value.
 */

void C2_MacroAssembler::vector_castF2X_avx(BasicType to_elem_bt, XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                           XMMRegister xtmp2, XMMRegister xtmp3, XMMRegister xtmp4,
                                           AddressLiteral float_sign_flip, Register rscratch, int vec_enc) {
  int to_elem_sz = type2aelembytes(to_elem_bt);
  assert(to_elem_sz <= 4, "");
  vcvttps2dq(dst, src, vec_enc);
  vector_cast_float_to_int_special_cases_avx(dst, src, xtmp1, xtmp2, xtmp3, xtmp4, rscratch, float_sign_flip, vec_enc);
  if (to_elem_sz < 4) {
    vpxor(xtmp4, xtmp4, xtmp4, vec_enc);
    vector_cast_int_to_subword(to_elem_bt, dst, xtmp4, xtmp3, rscratch, vec_enc);
  }
}

void C2_MacroAssembler::vector_castF2X_evex(BasicType to_elem_bt, XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                            XMMRegister xtmp2, KRegister ktmp1, KRegister ktmp2, AddressLiteral float_sign_flip,
                                            Register rscratch, int vec_enc) {
  int to_elem_sz = type2aelembytes(to_elem_bt);
  assert(to_elem_sz <= 4, "");
  vcvttps2dq(dst, src, vec_enc);
  vector_cast_float_to_int_special_cases_evex(dst, src, xtmp1, xtmp2, ktmp1, ktmp2, rscratch, float_sign_flip, vec_enc);
  switch(to_elem_bt) {
    case T_INT:
      break;
    case T_SHORT:
      evpmovdw(dst, dst, vec_enc);
      break;
    case T_BYTE:
      evpmovdb(dst, dst, vec_enc);
      break;
    default: assert(false, "%s", type2name(to_elem_bt));
  }
}

void C2_MacroAssembler::vector_castF2L_evex(XMMRegister dst, XMMRegister src, XMMRegister xtmp1, XMMRegister xtmp2,
                                            KRegister ktmp1, KRegister ktmp2, AddressLiteral double_sign_flip,
                                            Register rscratch, int vec_enc) {
  evcvttps2qq(dst, src, vec_enc);
  vector_cast_float_to_long_special_cases_evex(dst, src, xtmp1, xtmp2, ktmp1, ktmp2, rscratch, double_sign_flip, vec_enc);
}

// Handling for downcasting from double to integer or sub-word types on AVX2.
void C2_MacroAssembler::vector_castD2X_avx(BasicType to_elem_bt, XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                           XMMRegister xtmp2, XMMRegister xtmp3, XMMRegister xtmp4, XMMRegister xtmp5,
                                           AddressLiteral float_sign_flip, Register rscratch, int vec_enc) {
  int to_elem_sz = type2aelembytes(to_elem_bt);
  assert(to_elem_sz < 8, "");
  vcvttpd2dq(dst, src, vec_enc);
  vector_cast_double_to_int_special_cases_avx(dst, src, xtmp1, xtmp2, xtmp3, xtmp4, xtmp5, rscratch,
                                              float_sign_flip, vec_enc);
  if (to_elem_sz < 4) {
    // xtmp4 holds all zero lanes.
    vector_cast_int_to_subword(to_elem_bt, dst, xtmp4, xtmp5, rscratch, Assembler::AVX_128bit);
  }
}

void C2_MacroAssembler::vector_castD2X_evex(BasicType to_elem_bt, XMMRegister dst, XMMRegister src,
                                            XMMRegister xtmp1, XMMRegister xtmp2, KRegister ktmp1,
                                            KRegister ktmp2, AddressLiteral sign_flip,
                                            Register rscratch, int vec_enc) {
  if (VM_Version::supports_avx512dq()) {
    evcvttpd2qq(dst, src, vec_enc);
    vector_cast_double_to_long_special_cases_evex(dst, src, xtmp1, xtmp2, ktmp1, ktmp2, rscratch, sign_flip, vec_enc);
    switch(to_elem_bt) {
      case T_LONG:
        break;
      case T_INT:
        evpmovsqd(dst, dst, vec_enc);
        break;
      case T_SHORT:
        evpmovsqd(dst, dst, vec_enc);
        evpmovdw(dst, dst, vec_enc);
        break;
      case T_BYTE:
        evpmovsqd(dst, dst, vec_enc);
        evpmovdb(dst, dst, vec_enc);
        break;
      default: assert(false, "%s", type2name(to_elem_bt));
    }
  } else {
    assert(type2aelembytes(to_elem_bt) <= 4, "");
    vcvttpd2dq(dst, src, vec_enc);
    vector_cast_double_to_int_special_cases_evex(dst, src, xtmp1, xtmp2, ktmp1, ktmp2, rscratch, sign_flip, vec_enc);
    switch(to_elem_bt) {
      case T_INT:
        break;
      case T_SHORT:
        evpmovdw(dst, dst, vec_enc);
        break;
      case T_BYTE:
        evpmovdb(dst, dst, vec_enc);
        break;
      default: assert(false, "%s", type2name(to_elem_bt));
    }
  }
}

void C2_MacroAssembler::vector_round_double_evex(XMMRegister dst, XMMRegister src,
                                                 AddressLiteral double_sign_flip, AddressLiteral new_mxcsr, int vec_enc,
                                                 Register tmp, XMMRegister xtmp1, XMMRegister xtmp2, KRegister ktmp1, KRegister ktmp2) {
  // Perform floor(val+0.5) operation under the influence of MXCSR.RC mode roundTowards -inf.
  // and re-instantiate original MXCSR.RC mode after that.
  ldmxcsr(new_mxcsr, tmp /*rscratch*/);

  mov64(tmp, julong_cast(0.5L));
  evpbroadcastq(xtmp1, tmp, vec_enc);
  vaddpd(xtmp1, src , xtmp1, vec_enc);
  evcvtpd2qq(dst, xtmp1, vec_enc);
  vector_cast_double_to_long_special_cases_evex(dst, src, xtmp1, xtmp2, ktmp1, ktmp2, tmp /*rscratch*/,
                                                double_sign_flip, vec_enc);;

  ldmxcsr(ExternalAddress(StubRoutines::x86::addr_mxcsr_std()), tmp /*rscratch*/);
}

void C2_MacroAssembler::vector_round_float_evex(XMMRegister dst, XMMRegister src,
                                                AddressLiteral float_sign_flip, AddressLiteral new_mxcsr, int vec_enc,
                                                Register tmp, XMMRegister xtmp1, XMMRegister xtmp2, KRegister ktmp1, KRegister ktmp2) {
  // Perform floor(val+0.5) operation under the influence of MXCSR.RC mode roundTowards -inf.
  // and re-instantiate original MXCSR.RC mode after that.
  ldmxcsr(new_mxcsr, tmp /*rscratch*/);

  movl(tmp, jint_cast(0.5));
  movq(xtmp1, tmp);
  vbroadcastss(xtmp1, xtmp1, vec_enc);
  vaddps(xtmp1, src , xtmp1, vec_enc);
  vcvtps2dq(dst, xtmp1, vec_enc);
  vector_cast_float_to_int_special_cases_evex(dst, src, xtmp1, xtmp2, ktmp1, ktmp2, tmp /*rscratch*/,
                                              float_sign_flip, vec_enc);

  ldmxcsr(ExternalAddress(StubRoutines::x86::addr_mxcsr_std()), tmp /*rscratch*/);
}

void C2_MacroAssembler::vector_round_float_avx(XMMRegister dst, XMMRegister src,
                                               AddressLiteral float_sign_flip, AddressLiteral new_mxcsr, int vec_enc,
                                               Register tmp, XMMRegister xtmp1, XMMRegister xtmp2, XMMRegister xtmp3, XMMRegister xtmp4) {
  // Perform floor(val+0.5) operation under the influence of MXCSR.RC mode roundTowards -inf.
  // and re-instantiate original MXCSR.RC mode after that.
  ldmxcsr(new_mxcsr, tmp /*rscratch*/);

  movl(tmp, jint_cast(0.5));
  movq(xtmp1, tmp);
  vbroadcastss(xtmp1, xtmp1, vec_enc);
  vaddps(xtmp1, src , xtmp1, vec_enc);
  vcvtps2dq(dst, xtmp1, vec_enc);
  vector_cast_float_to_int_special_cases_avx(dst, src, xtmp1, xtmp2, xtmp3, xtmp4, tmp /*rscratch*/, float_sign_flip, vec_enc);

  ldmxcsr(ExternalAddress(StubRoutines::x86::addr_mxcsr_std()), tmp /*rscratch*/);
}

void C2_MacroAssembler::vector_unsigned_cast(XMMRegister dst, XMMRegister src, int vlen_enc,
                                             BasicType from_elem_bt, BasicType to_elem_bt) {
  switch (from_elem_bt) {
    case T_BYTE:
      switch (to_elem_bt) {
        case T_SHORT: vpmovzxbw(dst, src, vlen_enc); break;
        case T_INT:   vpmovzxbd(dst, src, vlen_enc); break;
        case T_LONG:  vpmovzxbq(dst, src, vlen_enc); break;
        default: ShouldNotReachHere();
      }
      break;
    case T_SHORT:
      switch (to_elem_bt) {
        case T_INT:  vpmovzxwd(dst, src, vlen_enc); break;
        case T_LONG: vpmovzxwq(dst, src, vlen_enc); break;
        default: ShouldNotReachHere();
      }
      break;
    case T_INT:
      assert(to_elem_bt == T_LONG, "");
      vpmovzxdq(dst, src, vlen_enc);
      break;
    default:
      ShouldNotReachHere();
  }
}

void C2_MacroAssembler::vector_signed_cast(XMMRegister dst, XMMRegister src, int vlen_enc,
                                           BasicType from_elem_bt, BasicType to_elem_bt) {
  switch (from_elem_bt) {
    case T_BYTE:
      switch (to_elem_bt) {
        case T_SHORT: vpmovsxbw(dst, src, vlen_enc); break;
        case T_INT:   vpmovsxbd(dst, src, vlen_enc); break;
        case T_LONG:  vpmovsxbq(dst, src, vlen_enc); break;
        default: ShouldNotReachHere();
      }
      break;
    case T_SHORT:
      switch (to_elem_bt) {
        case T_INT:  vpmovsxwd(dst, src, vlen_enc); break;
        case T_LONG: vpmovsxwq(dst, src, vlen_enc); break;
        default: ShouldNotReachHere();
      }
      break;
    case T_INT:
      assert(to_elem_bt == T_LONG, "");
      vpmovsxdq(dst, src, vlen_enc);
      break;
    default:
      ShouldNotReachHere();
  }
}

void C2_MacroAssembler::vector_mask_cast(XMMRegister dst, XMMRegister src,
                                         BasicType dst_bt, BasicType src_bt, int vlen) {
  int vlen_enc = vector_length_encoding(MAX2(type2aelembytes(src_bt), type2aelembytes(dst_bt)) * vlen);
  assert(vlen_enc != AVX_512bit, "");

  int dst_bt_size = type2aelembytes(dst_bt);
  int src_bt_size = type2aelembytes(src_bt);
  if (dst_bt_size > src_bt_size) {
    switch (dst_bt_size / src_bt_size) {
      case 2: vpmovsxbw(dst, src, vlen_enc); break;
      case 4: vpmovsxbd(dst, src, vlen_enc); break;
      case 8: vpmovsxbq(dst, src, vlen_enc); break;
      default: ShouldNotReachHere();
    }
  } else {
    assert(dst_bt_size < src_bt_size, "");
    switch (src_bt_size / dst_bt_size) {
      case 2: {
        if (vlen_enc == AVX_128bit) {
          vpacksswb(dst, src, src, vlen_enc);
        } else {
          vpacksswb(dst, src, src, vlen_enc);
          vpermq(dst, dst, 0x08, vlen_enc);
        }
        break;
      }
      case 4: {
        if (vlen_enc == AVX_128bit) {
          vpackssdw(dst, src, src, vlen_enc);
          vpacksswb(dst, dst, dst, vlen_enc);
        } else {
          vpackssdw(dst, src, src, vlen_enc);
          vpermq(dst, dst, 0x08, vlen_enc);
          vpacksswb(dst, dst, dst, AVX_128bit);
        }
        break;
      }
      case 8: {
        if (vlen_enc == AVX_128bit) {
          vpshufd(dst, src, 0x08, vlen_enc);
          vpackssdw(dst, dst, dst, vlen_enc);
          vpacksswb(dst, dst, dst, vlen_enc);
        } else {
          vpshufd(dst, src, 0x08, vlen_enc);
          vpermq(dst, dst, 0x08, vlen_enc);
          vpackssdw(dst, dst, dst, AVX_128bit);
          vpacksswb(dst, dst, dst, AVX_128bit);
        }
        break;
      }
      default: ShouldNotReachHere();
    }
  }
}

void C2_MacroAssembler::evpternlog(XMMRegister dst, int func, KRegister mask, XMMRegister src2, XMMRegister src3,
                                   bool merge, BasicType bt, int vlen_enc) {
  if (bt == T_INT) {
    evpternlogd(dst, func, mask, src2, src3, merge, vlen_enc);
  } else {
    assert(bt == T_LONG, "");
    evpternlogq(dst, func, mask, src2, src3, merge, vlen_enc);
  }
}

void C2_MacroAssembler::evpternlog(XMMRegister dst, int func, KRegister mask, XMMRegister src2, Address src3,
                                   bool merge, BasicType bt, int vlen_enc) {
  if (bt == T_INT) {
    evpternlogd(dst, func, mask, src2, src3, merge, vlen_enc);
  } else {
    assert(bt == T_LONG, "");
    evpternlogq(dst, func, mask, src2, src3, merge, vlen_enc);
  }
}

void C2_MacroAssembler::vector_long_to_maskvec(XMMRegister dst, Register src, Register rtmp1,
                                               Register rtmp2, XMMRegister xtmp, int mask_len,
                                               int vec_enc) {
  int index = 0;
  int vindex = 0;
  mov64(rtmp1, 0x0101010101010101L);
  pdepq(rtmp1, src, rtmp1);
  if (mask_len > 8) {
    movq(rtmp2, src);
    vpxor(xtmp, xtmp, xtmp, vec_enc);
    movq(xtmp, rtmp1);
  }
  movq(dst, rtmp1);

  mask_len -= 8;
  while (mask_len > 0) {
    assert ((mask_len & 0x7) == 0, "mask must be multiple of 8");
    index++;
    if ((index % 2) == 0) {
      pxor(xtmp, xtmp);
    }
    mov64(rtmp1, 0x0101010101010101L);
    shrq(rtmp2, 8);
    pdepq(rtmp1, rtmp2, rtmp1);
    pinsrq(xtmp, rtmp1, index % 2);
    vindex = index / 2;
    if (vindex) {
      // Write entire 16 byte vector when both 64 bit
      // lanes are update to save redundant instructions.
      if (index % 2) {
        vinsertf128(dst, dst, xtmp, vindex);
      }
    } else {
      vmovdqu(dst, xtmp);
    }
    mask_len -= 8;
  }
}

void C2_MacroAssembler::vector_mask_operation_helper(int opc, Register dst, Register tmp, int masklen) {
  switch(opc) {
    case Op_VectorMaskTrueCount:
      popcntq(dst, tmp);
      break;
    case Op_VectorMaskLastTrue:
      if (VM_Version::supports_lzcnt()) {
        lzcntq(tmp, tmp);
        movl(dst, 63);
        subl(dst, tmp);
      } else {
        movl(dst, -1);
        bsrq(tmp, tmp);
        cmov32(Assembler::notZero, dst, tmp);
      }
      break;
    case Op_VectorMaskFirstTrue:
      if (VM_Version::supports_bmi1()) {
        if (masklen < 32) {
          orl(tmp, 1 << masklen);
          tzcntl(dst, tmp);
        } else if (masklen == 32) {
          tzcntl(dst, tmp);
        } else {
          assert(masklen == 64, "");
          tzcntq(dst, tmp);
        }
      } else {
        if (masklen < 32) {
          orl(tmp, 1 << masklen);
          bsfl(dst, tmp);
        } else {
          assert(masklen == 32 || masklen == 64, "");
          movl(dst, masklen);
          if (masklen == 32)  {
            bsfl(tmp, tmp);
          } else {
            bsfq(tmp, tmp);
          }
          cmov32(Assembler::notZero, dst, tmp);
        }
      }
      break;
    case Op_VectorMaskToLong:
      assert(dst == tmp, "Dst and tmp should be the same for toLong operations");
      break;
    default: assert(false, "Unhandled mask operation");
  }
}

void C2_MacroAssembler::vector_mask_operation(int opc, Register dst, KRegister mask, Register tmp,
                                              int masklen, int masksize, int vec_enc) {
  assert(VM_Version::supports_popcnt(), "");

  if(VM_Version::supports_avx512bw()) {
    kmovql(tmp, mask);
  } else {
    assert(masklen <= 16, "");
    kmovwl(tmp, mask);
  }

  // Mask generated out of partial vector comparisons/replicate/mask manipulation
  // operations needs to be clipped.
  if (masksize < 16 && opc != Op_VectorMaskFirstTrue) {
    andq(tmp, (1 << masklen) - 1);
  }

  vector_mask_operation_helper(opc, dst, tmp, masklen);
}

void C2_MacroAssembler::vector_mask_operation(int opc, Register dst, XMMRegister mask, XMMRegister xtmp,
                                              Register tmp, int masklen, BasicType bt, int vec_enc) {
  assert((vec_enc == AVX_128bit && VM_Version::supports_avx()) ||
         (vec_enc == AVX_256bit && (VM_Version::supports_avx2() || type2aelembytes(bt) >= 4)), "");
  assert(VM_Version::supports_popcnt(), "");

  bool need_clip = false;
  switch(bt) {
    case T_BOOLEAN:
      // While masks of other types contain 0, -1; boolean masks contain lane values of 0, 1
      vpxor(xtmp, xtmp, xtmp, vec_enc);
      vpsubb(xtmp, xtmp, mask, vec_enc);
      vpmovmskb(tmp, xtmp, vec_enc);
      need_clip = masklen < 16;
      break;
    case T_BYTE:
      vpmovmskb(tmp, mask, vec_enc);
      need_clip = masklen < 16;
      break;
    case T_SHORT:
      vpacksswb(xtmp, mask, mask, vec_enc);
      if (masklen >= 16) {
        vpermpd(xtmp, xtmp, 8, vec_enc);
      }
      vpmovmskb(tmp, xtmp, Assembler::AVX_128bit);
      need_clip = masklen < 16;
      break;
    case T_INT:
    case T_FLOAT:
      vmovmskps(tmp, mask, vec_enc);
      need_clip = masklen < 4;
      break;
    case T_LONG:
    case T_DOUBLE:
      vmovmskpd(tmp, mask, vec_enc);
      need_clip = masklen < 2;
      break;
    default: assert(false, "Unhandled type, %s", type2name(bt));
  }

  // Mask generated out of partial vector comparisons/replicate/mask manipulation
  // operations needs to be clipped.
  if (need_clip && opc != Op_VectorMaskFirstTrue) {
    // need_clip implies masklen < 32
    andq(tmp, (1 << masklen) - 1);
  }

  vector_mask_operation_helper(opc, dst, tmp, masklen);
}

void C2_MacroAssembler::vector_mask_compress(KRegister dst, KRegister src, Register rtmp1,
                                             Register rtmp2, int mask_len) {
  kmov(rtmp1, src);
  andq(rtmp1, (0xFFFFFFFFFFFFFFFFUL >> (64 - mask_len)));
  mov64(rtmp2, -1L);
  pextq(rtmp2, rtmp2, rtmp1);
  kmov(dst, rtmp2);
}

void C2_MacroAssembler::vector_compress_expand_avx2(int opcode, XMMRegister dst, XMMRegister src,
                                                    XMMRegister mask, Register rtmp, Register rscratch,
                                                    XMMRegister permv, XMMRegister xtmp, BasicType bt,
                                                    int vec_enc) {
  assert(type2aelembytes(bt) >= 4, "");
  assert(opcode == Op_CompressV || opcode == Op_ExpandV, "");
  address compress_perm_table = nullptr;
  address expand_perm_table = nullptr;
  if (type2aelembytes(bt) == 8) {
    compress_perm_table = StubRoutines::x86::compress_perm_table64();
    expand_perm_table  = StubRoutines::x86::expand_perm_table64();
    vmovmskpd(rtmp, mask, vec_enc);
  } else {
    compress_perm_table = StubRoutines::x86::compress_perm_table32();
    expand_perm_table = StubRoutines::x86::expand_perm_table32();
    vmovmskps(rtmp, mask, vec_enc);
  }
  shlq(rtmp, 5); // for 32 byte permute row.
  if (opcode == Op_CompressV) {
    lea(rscratch, ExternalAddress(compress_perm_table));
  } else {
    lea(rscratch, ExternalAddress(expand_perm_table));
  }
  addptr(rtmp, rscratch);
  vmovdqu(permv, Address(rtmp));
  vpermps(dst, permv, src, Assembler::AVX_256bit);
  vpxor(xtmp, xtmp, xtmp, vec_enc);
  // Blend the result with zero vector using permute mask, each column entry
  // in a permute table row contains either a valid permute index or a -1 (default)
  // value, this can potentially be used as a blending mask after
  // compressing/expanding the source vector lanes.
  vblendvps(dst, dst, xtmp, permv, vec_enc, true, permv);
}

void C2_MacroAssembler::vector_compress_expand(int opcode, XMMRegister dst, XMMRegister src, KRegister mask,
                                               bool merge, BasicType bt, int vec_enc) {
  if (opcode == Op_CompressV) {
    switch(bt) {
    case T_BYTE:
      evpcompressb(dst, mask, src, merge, vec_enc);
      break;
    case T_CHAR:
    case T_SHORT:
      evpcompressw(dst, mask, src, merge, vec_enc);
      break;
    case T_INT:
      evpcompressd(dst, mask, src, merge, vec_enc);
      break;
    case T_FLOAT:
      evcompressps(dst, mask, src, merge, vec_enc);
      break;
    case T_LONG:
      evpcompressq(dst, mask, src, merge, vec_enc);
      break;
    case T_DOUBLE:
      evcompresspd(dst, mask, src, merge, vec_enc);
      break;
    default:
      fatal("Unsupported type %s", type2name(bt));
      break;
    }
  } else {
    assert(opcode == Op_ExpandV, "");
    switch(bt) {
    case T_BYTE:
      evpexpandb(dst, mask, src, merge, vec_enc);
      break;
    case T_CHAR:
    case T_SHORT:
      evpexpandw(dst, mask, src, merge, vec_enc);
      break;
    case T_INT:
      evpexpandd(dst, mask, src, merge, vec_enc);
      break;
    case T_FLOAT:
      evexpandps(dst, mask, src, merge, vec_enc);
      break;
    case T_LONG:
      evpexpandq(dst, mask, src, merge, vec_enc);
      break;
    case T_DOUBLE:
      evexpandpd(dst, mask, src, merge, vec_enc);
      break;
    default:
      fatal("Unsupported type %s", type2name(bt));
      break;
    }
  }
}

void C2_MacroAssembler::vector_signum_evex(int opcode, XMMRegister dst, XMMRegister src, XMMRegister zero, XMMRegister one,
                                           KRegister ktmp1, int vec_enc) {
  if (opcode == Op_SignumVD) {
    vsubpd(dst, zero, one, vec_enc);
    // if src < 0 ? -1 : 1
    evcmppd(ktmp1, k0, src, zero, Assembler::LT_OQ, vec_enc);
    evblendmpd(dst, ktmp1, one, dst, true, vec_enc);
    // if src == NaN, -0.0 or 0.0 return src.
    evcmppd(ktmp1, k0, src, zero, Assembler::EQ_UQ, vec_enc);
    evblendmpd(dst, ktmp1, dst, src, true, vec_enc);
  } else {
    assert(opcode == Op_SignumVF, "");
    vsubps(dst, zero, one, vec_enc);
    // if src < 0 ? -1 : 1
    evcmpps(ktmp1, k0, src, zero, Assembler::LT_OQ, vec_enc);
    evblendmps(dst, ktmp1, one, dst, true, vec_enc);
    // if src == NaN, -0.0 or 0.0 return src.
    evcmpps(ktmp1, k0, src, zero, Assembler::EQ_UQ, vec_enc);
    evblendmps(dst, ktmp1, dst, src, true, vec_enc);
  }
}

void C2_MacroAssembler::vector_signum_avx(int opcode, XMMRegister dst, XMMRegister src, XMMRegister zero, XMMRegister one,
                                          XMMRegister xtmp1, int vec_enc) {
  if (opcode == Op_SignumVD) {
    vsubpd(dst, zero, one, vec_enc);
    // if src < 0 ? -1 : 1
    vblendvpd(dst, one, dst, src, vec_enc, true, xtmp1);
    // if src == NaN, -0.0 or 0.0 return src.
    vcmppd(xtmp1, src, zero, Assembler::EQ_UQ, vec_enc);
    vblendvpd(dst, dst, src, xtmp1, vec_enc, false, xtmp1);
  } else {
    assert(opcode == Op_SignumVF, "");
    vsubps(dst, zero, one, vec_enc);
    // if src < 0 ? -1 : 1
    vblendvps(dst, one, dst, src, vec_enc, true, xtmp1);
    // if src == NaN, -0.0 or 0.0 return src.
    vcmpps(xtmp1, src, zero, Assembler::EQ_UQ, vec_enc);
    vblendvps(dst, dst, src, xtmp1, vec_enc, false, xtmp1);
  }
}

void C2_MacroAssembler::vector_maskall_operation(KRegister dst, Register src, int mask_len) {
  if (VM_Version::supports_avx512bw()) {
    if (mask_len > 32) {
      kmovql(dst, src);
    } else {
      kmovdl(dst, src);
      if (mask_len != 32) {
        kshiftrdl(dst, dst, 32 - mask_len);
      }
    }
  } else {
    assert(mask_len <= 16, "");
    kmovwl(dst, src);
    if (mask_len != 16) {
      kshiftrwl(dst, dst, 16 - mask_len);
    }
  }
}

void C2_MacroAssembler::vbroadcast(BasicType bt, XMMRegister dst, int imm32, Register rtmp, int vec_enc) {
  int lane_size = type2aelembytes(bt);
  if ((is_non_subword_integral_type(bt) && VM_Version::supports_avx512vl()) ||
      (is_subword_type(bt) && VM_Version::supports_avx512vlbw())) {
    movptr(rtmp, imm32);
    switch(lane_size) {
      case 1 : evpbroadcastb(dst, rtmp, vec_enc); break;
      case 2 : evpbroadcastw(dst, rtmp, vec_enc); break;
      case 4 : evpbroadcastd(dst, rtmp, vec_enc); break;
      case 8 : evpbroadcastq(dst, rtmp, vec_enc); break;
      fatal("Unsupported lane size %d", lane_size);
      break;
    }
  } else {
    movptr(rtmp, imm32);
    movq(dst, rtmp);
    switch(lane_size) {
      case 1 : vpbroadcastb(dst, dst, vec_enc); break;
      case 2 : vpbroadcastw(dst, dst, vec_enc); break;
      case 4 : vpbroadcastd(dst, dst, vec_enc); break;
      case 8 : vpbroadcastq(dst, dst, vec_enc); break;
      fatal("Unsupported lane size %d", lane_size);
      break;
    }
  }
}

//
// Following is lookup table based popcount computation algorithm:-
//       Index   Bit set count
//     [ 0000 ->   0,
//       0001 ->   1,
//       0010 ->   1,
//       0011 ->   2,
//       0100 ->   1,
//       0101 ->   2,
//       0110 ->   2,
//       0111 ->   3,
//       1000 ->   1,
//       1001 ->   2,
//       1010 ->   3,
//       1011 ->   3,
//       1100 ->   2,
//       1101 ->   3,
//       1111 ->   4 ]
//  a. Count the number of 1s in 4 LSB bits of each byte. These bits are used as
//     shuffle indices for lookup table access.
//  b. Right shift each byte of vector lane by 4 positions.
//  c. Count the number of 1s in 4 MSB bits each byte. These bits are used as
//     shuffle indices for lookup table access.
//  d. Add the bitset count of upper and lower 4 bits of each byte.
//  e. Unpack double words to quad words and compute sum of absolute difference of bitset
//     count of all the bytes of a quadword.
//  f. Perform step e. for upper 128bit vector lane.
//  g. Pack the bitset count of quadwords back to double word.
//  h. Unpacking and packing operations are not needed for 64bit vector lane.

void C2_MacroAssembler::vector_popcount_byte(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                             XMMRegister xtmp2, Register rtmp, int vec_enc) {
  assert((vec_enc == Assembler::AVX_512bit && VM_Version::supports_avx512bw()) || VM_Version::supports_avx2(), "");
  vbroadcast(T_INT, xtmp1, 0x0F0F0F0F, rtmp, vec_enc);
  vpsrlw(dst, src, 4, vec_enc);
  vpand(dst, dst, xtmp1, vec_enc);
  vpand(xtmp1, src, xtmp1, vec_enc);
  vmovdqu(xtmp2, ExternalAddress(StubRoutines::x86::vector_popcount_lut()), vec_enc, noreg);
  vpshufb(xtmp1, xtmp2, xtmp1, vec_enc);
  vpshufb(dst, xtmp2, dst, vec_enc);
  vpaddb(dst, dst, xtmp1, vec_enc);
}

void C2_MacroAssembler::vector_popcount_int(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                            XMMRegister xtmp2, Register rtmp, int vec_enc) {
  vector_popcount_byte(xtmp1, src, dst, xtmp2, rtmp, vec_enc);
  // Following code is as per steps e,f,g and h of above algorithm.
  vpxor(xtmp2, xtmp2, xtmp2, vec_enc);
  vpunpckhdq(dst, xtmp1, xtmp2, vec_enc);
  vpsadbw(dst, dst, xtmp2, vec_enc);
  vpunpckldq(xtmp1, xtmp1, xtmp2, vec_enc);
  vpsadbw(xtmp1, xtmp1, xtmp2, vec_enc);
  vpackuswb(dst, xtmp1, dst, vec_enc);
}

void C2_MacroAssembler::vector_popcount_short(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                              XMMRegister xtmp2, Register rtmp, int vec_enc) {
  vector_popcount_byte(xtmp1, src, dst, xtmp2, rtmp, vec_enc);
  // Add the popcount of upper and lower bytes of word.
  vbroadcast(T_INT, xtmp2, 0x00FF00FF, rtmp, vec_enc);
  vpsrlw(dst, xtmp1, 8, vec_enc);
  vpand(xtmp1, xtmp1, xtmp2, vec_enc);
  vpaddw(dst, dst, xtmp1, vec_enc);
}

void C2_MacroAssembler::vector_popcount_long(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                             XMMRegister xtmp2, Register rtmp, int vec_enc) {
  vector_popcount_byte(xtmp1, src, dst, xtmp2, rtmp, vec_enc);
  vpxor(xtmp2, xtmp2, xtmp2, vec_enc);
  vpsadbw(dst, xtmp1, xtmp2, vec_enc);
}

void C2_MacroAssembler::vector_popcount_integral(BasicType bt, XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                                 XMMRegister xtmp2, Register rtmp, int vec_enc) {
  switch(bt) {
    case T_LONG:
      vector_popcount_long(dst, src, xtmp1, xtmp2, rtmp, vec_enc);
      break;
    case T_INT:
      vector_popcount_int(dst, src, xtmp1, xtmp2, rtmp, vec_enc);
      break;
    case T_CHAR:
    case T_SHORT:
      vector_popcount_short(dst, src, xtmp1, xtmp2, rtmp, vec_enc);
      break;
    case T_BYTE:
    case T_BOOLEAN:
      vector_popcount_byte(dst, src, xtmp1, xtmp2, rtmp, vec_enc);
      break;
    default:
      fatal("Unsupported type %s", type2name(bt));
      break;
  }
}

void C2_MacroAssembler::vector_popcount_integral_evex(BasicType bt, XMMRegister dst, XMMRegister src,
                                                      KRegister mask, bool merge, int vec_enc) {
  assert(VM_Version::supports_avx512vl() || vec_enc == Assembler::AVX_512bit, "");
  switch(bt) {
    case T_LONG:
      assert(VM_Version::supports_avx512_vpopcntdq(), "");
      evpopcntq(dst, mask, src, merge, vec_enc);
      break;
    case T_INT:
      assert(VM_Version::supports_avx512_vpopcntdq(), "");
      evpopcntd(dst, mask, src, merge, vec_enc);
      break;
    case T_CHAR:
    case T_SHORT:
      assert(VM_Version::supports_avx512_bitalg(), "");
      evpopcntw(dst, mask, src, merge, vec_enc);
      break;
    case T_BYTE:
    case T_BOOLEAN:
      assert(VM_Version::supports_avx512_bitalg(), "");
      evpopcntb(dst, mask, src, merge, vec_enc);
      break;
    default:
      fatal("Unsupported type %s", type2name(bt));
      break;
  }
}

// Bit reversal algorithm first reverses the bits of each byte followed by
// a byte level reversal for multi-byte primitive types (short/int/long).
// Algorithm performs a lookup table access to get reverse bit sequence
// corresponding to a 4 bit value. Thus a reverse bit sequence for a byte
// is obtained by swapping the reverse bit sequences of upper and lower
// nibble of a byte.
void C2_MacroAssembler::vector_reverse_bit(BasicType bt, XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                           XMMRegister xtmp2, Register rtmp, int vec_enc) {
  if (VM_Version::supports_avx512vlbw()) {

    // Get the reverse bit sequence of lower nibble of each byte.
    vmovdqu(xtmp1, ExternalAddress(StubRoutines::x86::vector_reverse_bit_lut()), vec_enc, noreg);
    vbroadcast(T_INT, xtmp2, 0x0F0F0F0F, rtmp, vec_enc);
    evpandq(dst, xtmp2, src, vec_enc);
    vpshufb(dst, xtmp1, dst, vec_enc);
    vpsllq(dst, dst, 4, vec_enc);

    // Get the reverse bit sequence of upper nibble of each byte.
    vpandn(xtmp2, xtmp2, src, vec_enc);
    vpsrlq(xtmp2, xtmp2, 4, vec_enc);
    vpshufb(xtmp2, xtmp1, xtmp2, vec_enc);

    // Perform logical OR operation b/w left shifted reverse bit sequence of lower nibble and
    // right shifted reverse bit sequence of upper nibble to obtain the reverse bit sequence of each byte.
    evporq(xtmp2, dst, xtmp2, vec_enc);
    vector_reverse_byte(bt, dst, xtmp2, vec_enc);

  } else if(vec_enc == Assembler::AVX_512bit) {
    // Shift based bit reversal.
    assert(bt == T_LONG || bt == T_INT, "");

    // Swap lower and upper nibble of each byte.
    vector_swap_nbits(4, 0x0F0F0F0F, xtmp1, src, xtmp2, rtmp, vec_enc);

    // Swap two least and most significant bits of each nibble.
    vector_swap_nbits(2, 0x33333333, dst, xtmp1, xtmp2, rtmp, vec_enc);

    // Swap adjacent pair of bits.
    evmovdqul(xtmp1, k0, dst, true, vec_enc);
    vector_swap_nbits(1, 0x55555555, dst, xtmp1, xtmp2, rtmp, vec_enc);

    evmovdqul(xtmp1, k0, dst, true, vec_enc);
    vector_reverse_byte64(bt, dst, xtmp1, xtmp1, xtmp2, rtmp, vec_enc);
  } else {
    vmovdqu(xtmp1, ExternalAddress(StubRoutines::x86::vector_reverse_bit_lut()), vec_enc, rtmp);
    vbroadcast(T_INT, xtmp2, 0x0F0F0F0F, rtmp, vec_enc);

    // Get the reverse bit sequence of lower nibble of each byte.
    vpand(dst, xtmp2, src, vec_enc);
    vpshufb(dst, xtmp1, dst, vec_enc);
    vpsllq(dst, dst, 4, vec_enc);

    // Get the reverse bit sequence of upper nibble of each byte.
    vpandn(xtmp2, xtmp2, src, vec_enc);
    vpsrlq(xtmp2, xtmp2, 4, vec_enc);
    vpshufb(xtmp2, xtmp1, xtmp2, vec_enc);

    // Perform logical OR operation b/w left shifted reverse bit sequence of lower nibble and
    // right shifted reverse bit sequence of upper nibble to obtain the reverse bit sequence of each byte.
    vpor(xtmp2, dst, xtmp2, vec_enc);
    vector_reverse_byte(bt, dst, xtmp2, vec_enc);
  }
}

void C2_MacroAssembler::vector_reverse_bit_gfni(BasicType bt, XMMRegister dst, XMMRegister src, AddressLiteral mask, int vec_enc,
                                                XMMRegister xtmp, Register rscratch) {
  assert(VM_Version::supports_gfni(), "");
  assert(rscratch != noreg || always_reachable(mask), "missing");

  // Galois field instruction based bit reversal based on following algorithm.
  // http://0x80.pl/articles/avx512-galois-field-for-bit-shuffling.html
  vpbroadcastq(xtmp, mask, vec_enc, rscratch);
  vgf2p8affineqb(xtmp, src, xtmp, 0, vec_enc);
  vector_reverse_byte(bt, dst, xtmp, vec_enc);
}

void C2_MacroAssembler::vector_swap_nbits(int nbits, int bitmask, XMMRegister dst, XMMRegister src,
                                          XMMRegister xtmp1, Register rtmp, int vec_enc) {
  vbroadcast(T_INT, xtmp1, bitmask, rtmp, vec_enc);
  evpandq(dst, xtmp1, src, vec_enc);
  vpsllq(dst, dst, nbits, vec_enc);
  vpandn(xtmp1, xtmp1, src, vec_enc);
  vpsrlq(xtmp1, xtmp1, nbits, vec_enc);
  evporq(dst, dst, xtmp1, vec_enc);
}

void C2_MacroAssembler::vector_reverse_byte64(BasicType bt, XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                              XMMRegister xtmp2, Register rtmp, int vec_enc) {
  // Shift based bit reversal.
  assert(VM_Version::supports_evex(), "");
  switch(bt) {
    case T_LONG:
      // Swap upper and lower double word of each quad word.
      evprorq(xtmp1, k0, src, 32, true, vec_enc);
      evprord(xtmp1, k0, xtmp1, 16, true, vec_enc);
      vector_swap_nbits(8, 0x00FF00FF, dst, xtmp1, xtmp2, rtmp, vec_enc);
      break;
    case T_INT:
      // Swap upper and lower word of each double word.
      evprord(xtmp1, k0, src, 16, true, vec_enc);
      vector_swap_nbits(8, 0x00FF00FF, dst, xtmp1, xtmp2, rtmp, vec_enc);
      break;
    case T_CHAR:
    case T_SHORT:
      // Swap upper and lower byte of each word.
      vector_swap_nbits(8, 0x00FF00FF, dst, src, xtmp2, rtmp, vec_enc);
      break;
    case T_BYTE:
      evmovdquq(dst, k0, src, true, vec_enc);
      break;
    default:
      fatal("Unsupported type %s", type2name(bt));
      break;
  }
}

void C2_MacroAssembler::vector_reverse_byte(BasicType bt, XMMRegister dst, XMMRegister src, int vec_enc) {
  if (bt == T_BYTE) {
    if (VM_Version::supports_avx512vl() || vec_enc == Assembler::AVX_512bit) {
      evmovdquq(dst, k0, src, true, vec_enc);
    } else {
      vmovdqu(dst, src);
    }
    return;
  }
  // Perform byte reversal by shuffling the bytes of a multi-byte primitive type using
  // pre-computed shuffle indices.
  switch(bt) {
    case T_LONG:
      vmovdqu(dst, ExternalAddress(StubRoutines::x86::vector_reverse_byte_perm_mask_long()), vec_enc, noreg);
      break;
    case T_INT:
      vmovdqu(dst, ExternalAddress(StubRoutines::x86::vector_reverse_byte_perm_mask_int()), vec_enc, noreg);
      break;
    case T_CHAR:
    case T_SHORT:
      vmovdqu(dst, ExternalAddress(StubRoutines::x86::vector_reverse_byte_perm_mask_short()), vec_enc, noreg);
      break;
    default:
      fatal("Unsupported type %s", type2name(bt));
      break;
  }
  vpshufb(dst, src, dst, vec_enc);
}

void C2_MacroAssembler::vector_count_leading_zeros_evex(BasicType bt, XMMRegister dst, XMMRegister src,
                                                        XMMRegister xtmp1, XMMRegister xtmp2, XMMRegister xtmp3,
                                                        KRegister ktmp, Register rtmp, bool merge, int vec_enc) {
  assert(is_integral_type(bt), "");
  assert(VM_Version::supports_avx512vl() || vec_enc == Assembler::AVX_512bit, "");
  assert(VM_Version::supports_avx512cd(), "");
  switch(bt) {
    case T_LONG:
      evplzcntq(dst, ktmp, src, merge, vec_enc);
      break;
    case T_INT:
      evplzcntd(dst, ktmp, src, merge, vec_enc);
      break;
    case T_SHORT:
      vpternlogd(xtmp1, 0xff, xtmp1, xtmp1, vec_enc);
      vpunpcklwd(xtmp2, xtmp1, src, vec_enc);
      evplzcntd(xtmp2, ktmp, xtmp2, merge, vec_enc);
      vpunpckhwd(dst, xtmp1, src, vec_enc);
      evplzcntd(dst, ktmp, dst, merge, vec_enc);
      vpackusdw(dst, xtmp2, dst, vec_enc);
      break;
    case T_BYTE:
      // T1 = Compute leading zero counts of 4 LSB bits of each byte by
      // accessing the lookup table.
      // T2 = Compute leading zero counts of 4 MSB bits of each byte by
      // accessing the lookup table.
      // Add T1 to T2 if 4 MSB bits of byte are all zeros.
      assert(VM_Version::supports_avx512bw(), "");
      evmovdquq(xtmp1, ExternalAddress(StubRoutines::x86::vector_count_leading_zeros_lut()), vec_enc, rtmp);
      vbroadcast(T_INT, dst, 0x0F0F0F0F, rtmp, vec_enc);
      vpand(xtmp2, dst, src, vec_enc);
      vpshufb(xtmp2, xtmp1, xtmp2, vec_enc);
      vpsrlw(xtmp3, src, 4, vec_enc);
      vpand(xtmp3, dst, xtmp3, vec_enc);
      vpshufb(dst, xtmp1, xtmp3, vec_enc);
      vpxor(xtmp1, xtmp1, xtmp1, vec_enc);
      evpcmpeqb(ktmp, xtmp1, xtmp3, vec_enc);
      evpaddb(dst, ktmp, dst, xtmp2, true, vec_enc);
      break;
    default:
      fatal("Unsupported type %s", type2name(bt));
      break;
  }
}

void C2_MacroAssembler::vector_count_leading_zeros_byte_avx(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                                            XMMRegister xtmp2, XMMRegister xtmp3, Register rtmp, int vec_enc) {
  vmovdqu(xtmp1, ExternalAddress(StubRoutines::x86::vector_count_leading_zeros_lut()), rtmp);
  vbroadcast(T_INT, xtmp2, 0x0F0F0F0F, rtmp, vec_enc);
  // T1 = Compute leading zero counts of 4 LSB bits of each byte by
  // accessing the lookup table.
  vpand(dst, xtmp2, src, vec_enc);
  vpshufb(dst, xtmp1, dst, vec_enc);
  // T2 = Compute leading zero counts of 4 MSB bits of each byte by
  // accessing the lookup table.
  vpsrlw(xtmp3, src, 4, vec_enc);
  vpand(xtmp3, xtmp2, xtmp3, vec_enc);
  vpshufb(xtmp2, xtmp1, xtmp3, vec_enc);
  // Add T1 to T2 if 4 MSB bits of byte are all zeros.
  vpxor(xtmp1, xtmp1, xtmp1, vec_enc);
  vpcmpeqb(xtmp3, xtmp1, xtmp3, vec_enc);
  vpaddb(dst, dst, xtmp2, vec_enc);
  vpblendvb(dst, xtmp2, dst, xtmp3, vec_enc);
}

void C2_MacroAssembler::vector_count_leading_zeros_short_avx(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                                             XMMRegister xtmp2, XMMRegister xtmp3, Register rtmp, int vec_enc) {
  vector_count_leading_zeros_byte_avx(dst, src, xtmp1, xtmp2, xtmp3, rtmp, vec_enc);
  // Add zero counts of lower byte and upper byte of a word if
  // upper byte holds a zero value.
  vpsrlw(xtmp3, src, 8, vec_enc);
  // xtmp1 is set to all zeros by vector_count_leading_zeros_byte_avx.
  vpcmpeqw(xtmp3, xtmp1, xtmp3, vec_enc);
  vpsllw(xtmp2, dst, 8, vec_enc);
  vpaddw(xtmp2, xtmp2, dst, vec_enc);
  vpblendvb(dst, dst, xtmp2, xtmp3, vec_enc);
  vpsrlw(dst, dst, 8, vec_enc);
}

void C2_MacroAssembler::vector_count_leading_zeros_int_avx(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                                           XMMRegister xtmp2, XMMRegister xtmp3, int vec_enc) {
  // Since IEEE 754 floating point format represents mantissa in 1.0 format
  // hence biased exponent can be used to compute leading zero count as per
  // following formula:-
  // LZCNT = 31 - (biased_exp - 127)
  // Special handling has been introduced for Zero, Max_Int and -ve source values.

  // Broadcast 0xFF
  vpcmpeqd(xtmp1, xtmp1, xtmp1, vec_enc);
  vpsrld(xtmp1, xtmp1, 24, vec_enc);

  // Remove the bit to the right of the highest set bit ensuring that the conversion to float cannot round up to a higher
  // power of 2, which has a higher exponent than the input. This transformation is valid as only the highest set bit
  // contributes to the leading number of zeros.
  vpsrld(xtmp2, src, 1, vec_enc);
  vpandn(xtmp3, xtmp2, src, vec_enc);

  // Extract biased exponent.
  vcvtdq2ps(dst, xtmp3, vec_enc);
  vpsrld(dst, dst, 23, vec_enc);
  vpand(dst, dst, xtmp1, vec_enc);

  // Broadcast 127.
  vpsrld(xtmp1, xtmp1, 1, vec_enc);
  // Exponent = biased_exp - 127
  vpsubd(dst, dst, xtmp1, vec_enc);

  // Exponent_plus_one = Exponent + 1
  vpsrld(xtmp3, xtmp1, 6, vec_enc);
  vpaddd(dst, dst, xtmp3, vec_enc);

  // Replace -ve exponent with zero, exponent is -ve when src
  // lane contains a zero value.
  vpxor(xtmp2, xtmp2, xtmp2, vec_enc);
  vblendvps(dst, dst, xtmp2, dst, vec_enc);

  // Rematerialize broadcast 32.
  vpslld(xtmp1, xtmp3, 5, vec_enc);
  // Exponent is 32 if corresponding source lane contains max_int value.
  vpcmpeqd(xtmp2, dst, xtmp1, vec_enc);
  // LZCNT = 32 - exponent_plus_one
  vpsubd(dst, xtmp1, dst, vec_enc);

  // Replace LZCNT with a value 1 if corresponding source lane
  // contains max_int value.
  vpblendvb(dst, dst, xtmp3, xtmp2, vec_enc);

  // Replace biased_exp with 0 if source lane value is less than zero.
  vpxor(xtmp2, xtmp2, xtmp2, vec_enc);
  vblendvps(dst, dst, xtmp2, src, vec_enc);
}

void C2_MacroAssembler::vector_count_leading_zeros_long_avx(XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                                            XMMRegister xtmp2, XMMRegister xtmp3, Register rtmp, int vec_enc) {
  vector_count_leading_zeros_short_avx(dst, src, xtmp1, xtmp2, xtmp3, rtmp, vec_enc);
  // Add zero counts of lower word and upper word of a double word if
  // upper word holds a zero value.
  vpsrld(xtmp3, src, 16, vec_enc);
  // xtmp1 is set to all zeros by vector_count_leading_zeros_byte_avx.
  vpcmpeqd(xtmp3, xtmp1, xtmp3, vec_enc);
  vpslld(xtmp2, dst, 16, vec_enc);
  vpaddd(xtmp2, xtmp2, dst, vec_enc);
  vpblendvb(dst, dst, xtmp2, xtmp3, vec_enc);
  vpsrld(dst, dst, 16, vec_enc);
  // Add zero counts of lower doubleword and upper doubleword of a
  // quadword if upper doubleword holds a zero value.
  vpsrlq(xtmp3, src, 32, vec_enc);
  vpcmpeqq(xtmp3, xtmp1, xtmp3, vec_enc);
  vpsllq(xtmp2, dst, 32, vec_enc);
  vpaddq(xtmp2, xtmp2, dst, vec_enc);
  vpblendvb(dst, dst, xtmp2, xtmp3, vec_enc);
  vpsrlq(dst, dst, 32, vec_enc);
}

void C2_MacroAssembler::vector_count_leading_zeros_avx(BasicType bt, XMMRegister dst, XMMRegister src,
                                                       XMMRegister xtmp1, XMMRegister xtmp2, XMMRegister xtmp3,
                                                       Register rtmp, int vec_enc) {
  assert(is_integral_type(bt), "unexpected type");
  assert(vec_enc < Assembler::AVX_512bit, "");
  switch(bt) {
    case T_LONG:
      vector_count_leading_zeros_long_avx(dst, src, xtmp1, xtmp2, xtmp3, rtmp, vec_enc);
      break;
    case T_INT:
      vector_count_leading_zeros_int_avx(dst, src, xtmp1, xtmp2, xtmp3, vec_enc);
      break;
    case T_SHORT:
      vector_count_leading_zeros_short_avx(dst, src, xtmp1, xtmp2, xtmp3, rtmp, vec_enc);
      break;
    case T_BYTE:
      vector_count_leading_zeros_byte_avx(dst, src, xtmp1, xtmp2, xtmp3, rtmp, vec_enc);
      break;
    default:
      fatal("Unsupported type %s", type2name(bt));
      break;
  }
}

void C2_MacroAssembler::vpsub(BasicType bt, XMMRegister dst, XMMRegister src1, XMMRegister src2, int vec_enc) {
  switch(bt) {
    case T_BYTE:
      vpsubb(dst, src1, src2, vec_enc);
      break;
    case T_SHORT:
      vpsubw(dst, src1, src2, vec_enc);
      break;
    case T_INT:
      vpsubd(dst, src1, src2, vec_enc);
      break;
    case T_LONG:
      vpsubq(dst, src1, src2, vec_enc);
      break;
    default:
      fatal("Unsupported type %s", type2name(bt));
      break;
  }
}

// Trailing zero count computation is based on leading zero count operation as per
// following equation. All AVX3 targets support AVX512CD feature which offers
// direct vector instruction to compute leading zero count.
//      CTZ = PRIM_TYPE_WIDHT - CLZ((x - 1) & ~x)
void C2_MacroAssembler::vector_count_trailing_zeros_evex(BasicType bt, XMMRegister dst, XMMRegister src,
                                                         XMMRegister xtmp1, XMMRegister xtmp2, XMMRegister xtmp3,
                                                         XMMRegister xtmp4, KRegister ktmp, Register rtmp, int vec_enc) {
  assert(is_integral_type(bt), "");
  // xtmp = -1
  vpternlogd(xtmp4, 0xff, xtmp4, xtmp4, vec_enc);
  // xtmp = xtmp + src
  vpadd(bt, xtmp4, xtmp4, src, vec_enc);
  // xtmp = xtmp & ~src
  vpternlogd(xtmp4, 0x40, xtmp4, src, vec_enc);
  vector_count_leading_zeros_evex(bt, dst, xtmp4, xtmp1, xtmp2, xtmp3, ktmp, rtmp, true, vec_enc);
  vbroadcast(bt, xtmp4, 8 * type2aelembytes(bt), rtmp, vec_enc);
  vpsub(bt, dst, xtmp4, dst, vec_enc);
}

// Trailing zero count computation for AVX2 targets is based on popcount operation as per following equation
//      CTZ = PRIM_TYPE_WIDHT - POPC(x | -x)
void C2_MacroAssembler::vector_count_trailing_zeros_avx(BasicType bt, XMMRegister dst, XMMRegister src, XMMRegister xtmp1,
                                                        XMMRegister xtmp2, XMMRegister xtmp3, Register rtmp, int vec_enc) {
  assert(is_integral_type(bt), "");
  // xtmp = 0
  vpxor(xtmp3 , xtmp3, xtmp3, vec_enc);
  // xtmp = 0 - src
  vpsub(bt, xtmp3, xtmp3, src, vec_enc);
  // xtmp = xtmp | src
  vpor(xtmp3, xtmp3, src, vec_enc);
  vector_popcount_integral(bt, dst, xtmp3, xtmp1, xtmp2, rtmp, vec_enc);
  vbroadcast(bt, xtmp1, 8 * type2aelembytes(bt), rtmp, vec_enc);
  vpsub(bt, dst, xtmp1, dst, vec_enc);
}

void C2_MacroAssembler::udivI(Register rax, Register divisor, Register rdx) {
  Label done;
  Label neg_divisor_fastpath;
  cmpl(divisor, 0);
  jccb(Assembler::less, neg_divisor_fastpath);
  xorl(rdx, rdx);
  divl(divisor);
  jmpb(done);
  bind(neg_divisor_fastpath);
  // Fastpath for divisor < 0:
  // quotient = (dividend & ~(dividend - divisor)) >>> (Integer.SIZE - 1)
  // See Hacker's Delight (2nd ed), section 9.3 which is implemented in java.lang.Long.divideUnsigned()
  movl(rdx, rax);
  subl(rdx, divisor);
  if (VM_Version::supports_bmi1()) {
    andnl(rax, rdx, rax);
  } else {
    notl(rdx);
    andl(rax, rdx);
  }
  shrl(rax, 31);
  bind(done);
}

void C2_MacroAssembler::umodI(Register rax, Register divisor, Register rdx) {
  Label done;
  Label neg_divisor_fastpath;
  cmpl(divisor, 0);
  jccb(Assembler::less, neg_divisor_fastpath);
  xorl(rdx, rdx);
  divl(divisor);
  jmpb(done);
  bind(neg_divisor_fastpath);
  // Fastpath when divisor < 0:
  // remainder = dividend - (((dividend & ~(dividend - divisor)) >> (Integer.SIZE - 1)) & divisor)
  // See Hacker's Delight (2nd ed), section 9.3 which is implemented in java.lang.Long.remainderUnsigned()
  movl(rdx, rax);
  subl(rax, divisor);
  if (VM_Version::supports_bmi1()) {
    andnl(rax, rax, rdx);
  } else {
    notl(rax);
    andl(rax, rdx);
  }
  sarl(rax, 31);
  andl(rax, divisor);
  subl(rdx, rax);
  bind(done);
}

void C2_MacroAssembler::udivmodI(Register rax, Register divisor, Register rdx, Register tmp) {
  Label done;
  Label neg_divisor_fastpath;

  cmpl(divisor, 0);
  jccb(Assembler::less, neg_divisor_fastpath);
  xorl(rdx, rdx);
  divl(divisor);
  jmpb(done);
  bind(neg_divisor_fastpath);
  // Fastpath for divisor < 0:
  // quotient = (dividend & ~(dividend - divisor)) >>> (Integer.SIZE - 1)
  // remainder = dividend - (((dividend & ~(dividend - divisor)) >> (Integer.SIZE - 1)) & divisor)
  // See Hacker's Delight (2nd ed), section 9.3 which is implemented in
  // java.lang.Long.divideUnsigned() and java.lang.Long.remainderUnsigned()
  movl(rdx, rax);
  subl(rax, divisor);
  if (VM_Version::supports_bmi1()) {
    andnl(rax, rax, rdx);
  } else {
    notl(rax);
    andl(rax, rdx);
  }
  movl(tmp, rax);
  shrl(rax, 31); // quotient
  sarl(tmp, 31);
  andl(tmp, divisor);
  subl(rdx, tmp); // remainder
  bind(done);
}

void C2_MacroAssembler::reverseI(Register dst, Register src, XMMRegister xtmp1,
                                 XMMRegister xtmp2, Register rtmp) {
  if(VM_Version::supports_gfni()) {
    // Galois field instruction based bit reversal based on following algorithm.
    // http://0x80.pl/articles/avx512-galois-field-for-bit-shuffling.html
    mov64(rtmp, 0x8040201008040201L);
    movq(xtmp1, src);
    movq(xtmp2, rtmp);
    gf2p8affineqb(xtmp1, xtmp2, 0);
    movq(dst, xtmp1);
  } else {
    // Swap even and odd numbered bits.
    movl(rtmp, src);
    andl(rtmp, 0x55555555);
    shll(rtmp, 1);
    movl(dst, src);
    andl(dst, 0xAAAAAAAA);
    shrl(dst, 1);
    orl(dst, rtmp);

    // Swap LSB and MSB 2 bits of each nibble.
    movl(rtmp, dst);
    andl(rtmp, 0x33333333);
    shll(rtmp, 2);
    andl(dst, 0xCCCCCCCC);
    shrl(dst, 2);
    orl(dst, rtmp);

    // Swap LSB and MSB 4 bits of each byte.
    movl(rtmp, dst);
    andl(rtmp, 0x0F0F0F0F);
    shll(rtmp, 4);
    andl(dst, 0xF0F0F0F0);
    shrl(dst, 4);
    orl(dst, rtmp);
  }
  bswapl(dst);
}

void C2_MacroAssembler::reverseL(Register dst, Register src, XMMRegister xtmp1,
                                 XMMRegister xtmp2, Register rtmp1, Register rtmp2) {
  if(VM_Version::supports_gfni()) {
    // Galois field instruction based bit reversal based on following algorithm.
    // http://0x80.pl/articles/avx512-galois-field-for-bit-shuffling.html
    mov64(rtmp1, 0x8040201008040201L);
    movq(xtmp1, src);
    movq(xtmp2, rtmp1);
    gf2p8affineqb(xtmp1, xtmp2, 0);
    movq(dst, xtmp1);
  } else {
    // Swap even and odd numbered bits.
    movq(rtmp1, src);
    mov64(rtmp2, 0x5555555555555555L);
    andq(rtmp1, rtmp2);
    shlq(rtmp1, 1);
    movq(dst, src);
    notq(rtmp2);
    andq(dst, rtmp2);
    shrq(dst, 1);
    orq(dst, rtmp1);

    // Swap LSB and MSB 2 bits of each nibble.
    movq(rtmp1, dst);
    mov64(rtmp2, 0x3333333333333333L);
    andq(rtmp1, rtmp2);
    shlq(rtmp1, 2);
    notq(rtmp2);
    andq(dst, rtmp2);
    shrq(dst, 2);
    orq(dst, rtmp1);

    // Swap LSB and MSB 4 bits of each byte.
    movq(rtmp1, dst);
    mov64(rtmp2, 0x0F0F0F0F0F0F0F0FL);
    andq(rtmp1, rtmp2);
    shlq(rtmp1, 4);
    notq(rtmp2);
    andq(dst, rtmp2);
    shrq(dst, 4);
    orq(dst, rtmp1);
  }
  bswapq(dst);
}

void C2_MacroAssembler::udivL(Register rax, Register divisor, Register rdx) {
  Label done;
  Label neg_divisor_fastpath;
  cmpq(divisor, 0);
  jccb(Assembler::less, neg_divisor_fastpath);
  xorl(rdx, rdx);
  divq(divisor);
  jmpb(done);
  bind(neg_divisor_fastpath);
  // Fastpath for divisor < 0:
  // quotient = (dividend & ~(dividend - divisor)) >>> (Long.SIZE - 1)
  // See Hacker's Delight (2nd ed), section 9.3 which is implemented in java.lang.Long.divideUnsigned()
  movq(rdx, rax);
  subq(rdx, divisor);
  if (VM_Version::supports_bmi1()) {
    andnq(rax, rdx, rax);
  } else {
    notq(rdx);
    andq(rax, rdx);
  }
  shrq(rax, 63);
  bind(done);
}

void C2_MacroAssembler::umodL(Register rax, Register divisor, Register rdx) {
  Label done;
  Label neg_divisor_fastpath;
  cmpq(divisor, 0);
  jccb(Assembler::less, neg_divisor_fastpath);
  xorq(rdx, rdx);
  divq(divisor);
  jmp(done);
  bind(neg_divisor_fastpath);
  // Fastpath when divisor < 0:
  // remainder = dividend - (((dividend & ~(dividend - divisor)) >> (Long.SIZE - 1)) & divisor)
  // See Hacker's Delight (2nd ed), section 9.3 which is implemented in java.lang.Long.remainderUnsigned()
  movq(rdx, rax);
  subq(rax, divisor);
  if (VM_Version::supports_bmi1()) {
    andnq(rax, rax, rdx);
  } else {
    notq(rax);
    andq(rax, rdx);
  }
  sarq(rax, 63);
  andq(rax, divisor);
  subq(rdx, rax);
  bind(done);
}

void C2_MacroAssembler::udivmodL(Register rax, Register divisor, Register rdx, Register tmp) {
  Label done;
  Label neg_divisor_fastpath;
  cmpq(divisor, 0);
  jccb(Assembler::less, neg_divisor_fastpath);
  xorq(rdx, rdx);
  divq(divisor);
  jmp(done);
  bind(neg_divisor_fastpath);
  // Fastpath for divisor < 0:
  // quotient = (dividend & ~(dividend - divisor)) >>> (Long.SIZE - 1)
  // remainder = dividend - (((dividend & ~(dividend - divisor)) >> (Long.SIZE - 1)) & divisor)
  // See Hacker's Delight (2nd ed), section 9.3 which is implemented in
  // java.lang.Long.divideUnsigned() and java.lang.Long.remainderUnsigned()
  movq(rdx, rax);
  subq(rax, divisor);
  if (VM_Version::supports_bmi1()) {
    andnq(rax, rax, rdx);
  } else {
    notq(rax);
    andq(rax, rdx);
  }
  movq(tmp, rax);
  shrq(rax, 63); // quotient
  sarq(tmp, 63);
  andq(tmp, divisor);
  subq(rdx, tmp); // remainder
  bind(done);
}

void C2_MacroAssembler::rearrange_bytes(XMMRegister dst, XMMRegister shuffle, XMMRegister src, XMMRegister xtmp1,
                                        XMMRegister xtmp2, XMMRegister xtmp3, Register rtmp, KRegister ktmp,
                                        int vlen_enc) {
  assert(VM_Version::supports_avx512bw(), "");
  // Byte shuffles are inlane operations and indices are determined using
  // lower 4 bit of each shuffle lane, thus all shuffle indices are
  // normalized to index range 0-15. This makes sure that all the multiples
  // of an index value are placed at same relative position in 128 bit
  // lane i.e. elements corresponding to shuffle indices 16, 32 and 64
  // will be 16th element in their respective 128 bit lanes.
  movl(rtmp, 16);
  evpbroadcastb(xtmp1, rtmp, vlen_enc);

  // Compute a mask for shuffle vector by comparing indices with expression INDEX < 16,
  // Broadcast first 128 bit lane across entire vector, shuffle the vector lanes using
  // original shuffle indices and move the shuffled lanes corresponding to true
  // mask to destination vector.
  evpcmpb(ktmp, k0, shuffle, xtmp1, Assembler::lt, true, vlen_enc);
  evshufi64x2(xtmp2, src, src, 0x0, vlen_enc);
  evpshufb(dst, ktmp, xtmp2, shuffle, false, vlen_enc);

  // Perform above steps with lane comparison expression as INDEX >= 16 && INDEX < 32
  // and broadcasting second 128 bit lane.
  evpcmpb(ktmp, k0, shuffle,  xtmp1, Assembler::nlt, true, vlen_enc);
  vpsllq(xtmp2, xtmp1, 0x1, vlen_enc);
  evpcmpb(ktmp, ktmp, shuffle, xtmp2, Assembler::lt, true, vlen_enc);
  evshufi64x2(xtmp3, src, src, 0x55, vlen_enc);
  evpshufb(dst, ktmp, xtmp3, shuffle, true, vlen_enc);

  // Perform above steps with lane comparison expression as INDEX >= 32 && INDEX < 48
  // and broadcasting third 128 bit lane.
  evpcmpb(ktmp, k0, shuffle,  xtmp2, Assembler::nlt, true, vlen_enc);
  vpaddb(xtmp1, xtmp1, xtmp2, vlen_enc);
  evpcmpb(ktmp, ktmp, shuffle,  xtmp1, Assembler::lt, true, vlen_enc);
  evshufi64x2(xtmp3, src, src, 0xAA, vlen_enc);
  evpshufb(dst, ktmp, xtmp3, shuffle, true, vlen_enc);

  // Perform above steps with lane comparison expression as INDEX >= 48 && INDEX < 64
  // and broadcasting third 128 bit lane.
  evpcmpb(ktmp, k0, shuffle,  xtmp1, Assembler::nlt, true, vlen_enc);
  vpsllq(xtmp2, xtmp2, 0x1, vlen_enc);
  evpcmpb(ktmp, ktmp, shuffle,  xtmp2, Assembler::lt, true, vlen_enc);
  evshufi64x2(xtmp3, src, src, 0xFF, vlen_enc);
  evpshufb(dst, ktmp, xtmp3, shuffle, true, vlen_enc);
}

void C2_MacroAssembler::vector_rearrange_int_float(BasicType bt, XMMRegister dst,
                                                   XMMRegister shuffle, XMMRegister src, int vlen_enc) {
  if (vlen_enc == AVX_128bit) {
    vpermilps(dst, src, shuffle, vlen_enc);
  } else if (bt == T_INT) {
    vpermd(dst, shuffle, src, vlen_enc);
  } else {
    assert(bt == T_FLOAT, "");
    vpermps(dst, shuffle, src, vlen_enc);
  }
}

void C2_MacroAssembler::efp16sh(int opcode, XMMRegister dst, XMMRegister src1, XMMRegister src2) {
  switch(opcode) {
    case Op_AddHF: vaddsh(dst, src1, src2); break;
    case Op_SubHF: vsubsh(dst, src1, src2); break;
    case Op_MulHF: vmulsh(dst, src1, src2); break;
    case Op_DivHF: vdivsh(dst, src1, src2); break;
    default: assert(false, "%s", NodeClassNames[opcode]); break;
  }
}

void C2_MacroAssembler::vector_saturating_op(int ideal_opc, BasicType elem_bt, XMMRegister dst, XMMRegister src1, XMMRegister src2, int vlen_enc) {
  switch(elem_bt) {
    case T_BYTE:
      if (ideal_opc == Op_SaturatingAddV) {
        vpaddsb(dst, src1, src2, vlen_enc);
      } else {
        assert(ideal_opc == Op_SaturatingSubV, "");
        vpsubsb(dst, src1, src2, vlen_enc);
      }
      break;
    case T_SHORT:
      if (ideal_opc == Op_SaturatingAddV) {
        vpaddsw(dst, src1, src2, vlen_enc);
      } else {
        assert(ideal_opc == Op_SaturatingSubV, "");
        vpsubsw(dst, src1, src2, vlen_enc);
      }
      break;
    default:
      fatal("Unsupported type %s", type2name(elem_bt));
      break;
  }
}

void C2_MacroAssembler::vector_saturating_unsigned_op(int ideal_opc, BasicType elem_bt, XMMRegister dst, XMMRegister src1, XMMRegister src2, int vlen_enc) {
  switch(elem_bt) {
    case T_BYTE:
      if (ideal_opc == Op_SaturatingAddV) {
        vpaddusb(dst, src1, src2, vlen_enc);
      } else {
        assert(ideal_opc == Op_SaturatingSubV, "");
        vpsubusb(dst, src1, src2, vlen_enc);
      }
      break;
    case T_SHORT:
      if (ideal_opc == Op_SaturatingAddV) {
        vpaddusw(dst, src1, src2, vlen_enc);
      } else {
        assert(ideal_opc == Op_SaturatingSubV, "");
        vpsubusw(dst, src1, src2, vlen_enc);
      }
      break;
    default:
      fatal("Unsupported type %s", type2name(elem_bt));
      break;
  }
}

void C2_MacroAssembler::vector_sub_dq_saturating_unsigned_evex(BasicType elem_bt, XMMRegister dst, XMMRegister src1,
                                                              XMMRegister src2, KRegister ktmp, int vlen_enc) {
  // For unsigned subtraction, overflow happens when magnitude of second input is greater than first input.
  // overflow_mask = Inp1 <u Inp2
  evpcmpu(elem_bt, ktmp,  src2, src1, Assembler::lt, vlen_enc);
  // Res = overflow_mask ? Zero : INP1 - INP2 (non-commutative and non-associative)
  evmasked_op(elem_bt == T_INT ? Op_SubVI : Op_SubVL, elem_bt, ktmp, dst, src1, src2, false, vlen_enc, false);
}

void C2_MacroAssembler::vector_sub_dq_saturating_unsigned_avx(BasicType elem_bt, XMMRegister dst, XMMRegister src1, XMMRegister src2,
                                                              XMMRegister xtmp1, XMMRegister xtmp2, int vlen_enc) {
  // Emulate unsigned comparison using signed comparison
  // Mask = Inp1 <u Inp2 => Inp1 + MIN_VALUE < Inp2 + MIN_VALUE
  vpgenmin_value(elem_bt, xtmp1, xtmp1, vlen_enc, true);
  vpadd(elem_bt, xtmp2, src1, xtmp1, vlen_enc);
  vpadd(elem_bt, xtmp1, src2, xtmp1, vlen_enc);

  vpcmpgt(elem_bt, xtmp2, xtmp1, xtmp2, vlen_enc);

  // Res = INP1 - INP2 (non-commutative and non-associative)
  vpsub(elem_bt, dst, src1, src2, vlen_enc);
  // Res = Mask ? Zero : Res
  vpxor(xtmp1, xtmp1, xtmp1, vlen_enc);
  vpblendvb(dst, dst, xtmp1, xtmp2, vlen_enc);
}

void C2_MacroAssembler::vector_add_dq_saturating_unsigned_evex(BasicType elem_bt, XMMRegister dst, XMMRegister src1, XMMRegister src2,
                                                               XMMRegister xtmp1, XMMRegister xtmp2, KRegister ktmp, int vlen_enc) {
  // Unsigned values ranges comprise of only +ve numbers, thus there exist only an upper bound saturation.
  // overflow_mask = (SRC1 + SRC2) <u (SRC1 | SRC2)
  // Res = Signed Add INP1, INP2
  vpadd(elem_bt, dst, src1, src2, vlen_enc);
  // T1 = SRC1 | SRC2
  vpor(xtmp1, src1, src2, vlen_enc);
  // Max_Unsigned = -1
  vpternlogd(xtmp2, 0xff, xtmp2, xtmp2, vlen_enc);
  // Unsigned compare:  Mask = Res <u T1
  evpcmpu(elem_bt, ktmp, dst, xtmp1, Assembler::lt, vlen_enc);
  // res  = Mask ? Max_Unsigned : Res
  evpblend(elem_bt, dst, ktmp,  dst, xtmp2, true, vlen_enc);
}

//
// Section 2-13 Hacker's Delight list following overflow detection check for saturating
// unsigned addition operation.
//    overflow_mask = ((a & b) | ((a | b) & ~( a + b))) >>> 31 == 1
//
// We empirically determined its semantic equivalence to following reduced expression
//    overflow_mask =  (a + b) <u (a | b)
//
// and also verified it though Alive2 solver.
// (https://alive2.llvm.org/ce/z/XDQ7dY)
//

void C2_MacroAssembler::vector_add_dq_saturating_unsigned_avx(BasicType elem_bt, XMMRegister dst, XMMRegister src1, XMMRegister src2,
                                                              XMMRegister xtmp1, XMMRegister xtmp2, XMMRegister xtmp3, int vlen_enc) {
  // Res = Signed Add INP1, INP2
  vpadd(elem_bt, dst, src1, src2, vlen_enc);
  // Compute T1 = INP1 | INP2
  vpor(xtmp3, src1, src2, vlen_enc);
  // T1 = Minimum signed value.
  vpgenmin_value(elem_bt, xtmp2, xtmp1, vlen_enc, true);
  // Convert T1 to signed value, T1 = T1 + MIN_VALUE
  vpadd(elem_bt, xtmp3, xtmp3, xtmp2, vlen_enc);
  // Convert Res to signed value, Res<s> = Res + MIN_VALUE
  vpadd(elem_bt, xtmp2, xtmp2, dst, vlen_enc);
  // Compute overflow detection mask = Res<1> <s T1
  if (elem_bt == T_INT) {
    vpcmpgtd(xtmp3, xtmp3, xtmp2, vlen_enc);
  } else {
    assert(elem_bt == T_LONG, "");
    vpcmpgtq(xtmp3, xtmp3, xtmp2, vlen_enc);
  }
  vpblendvb(dst, dst, xtmp1, xtmp3, vlen_enc);
}

void C2_MacroAssembler::evpmovq2m_emu(KRegister ktmp, XMMRegister src, XMMRegister xtmp1, XMMRegister xtmp2,
                                      int vlen_enc, bool xtmp2_hold_M1) {
  if (VM_Version::supports_avx512dq()) {
    evpmovq2m(ktmp, src, vlen_enc);
  } else {
    assert(VM_Version::supports_evex(), "");
    if (!xtmp2_hold_M1) {
      vpternlogq(xtmp2, 0xff, xtmp2, xtmp2, vlen_enc);
    }
    evpsraq(xtmp1, src, 63, vlen_enc);
    evpcmpeqq(ktmp, k0, xtmp1, xtmp2, vlen_enc);
  }
}

void C2_MacroAssembler::evpmovd2m_emu(KRegister ktmp, XMMRegister src, XMMRegister xtmp1, XMMRegister xtmp2,
                                      int vlen_enc, bool xtmp2_hold_M1) {
  if (VM_Version::supports_avx512dq()) {
    evpmovd2m(ktmp, src, vlen_enc);
  } else {
    assert(VM_Version::supports_evex(), "");
    if (!xtmp2_hold_M1) {
      vpternlogd(xtmp2, 0xff, xtmp2, xtmp2, vlen_enc);
    }
    vpsrad(xtmp1, src, 31, vlen_enc);
    Assembler::evpcmpeqd(ktmp, k0, xtmp1, xtmp2, vlen_enc);
  }
}


void C2_MacroAssembler::vpsign_extend_dq(BasicType elem_bt, XMMRegister dst, XMMRegister src, int vlen_enc) {
  if (elem_bt == T_LONG) {
    if (VM_Version::supports_evex()) {
      evpsraq(dst, src, 63, vlen_enc);
    } else {
      vpsrad(dst, src, 31, vlen_enc);
      vpshufd(dst, dst, 0xF5, vlen_enc);
    }
  } else {
    assert(elem_bt == T_INT, "");
    vpsrad(dst, src, 31, vlen_enc);
  }
}

void C2_MacroAssembler::vpgenmax_value(BasicType elem_bt, XMMRegister dst, XMMRegister allones, int vlen_enc, bool compute_allones) {
  if (compute_allones) {
    if (VM_Version::supports_avx512vl() || vlen_enc == Assembler::AVX_512bit) {
      vpternlogd(allones, 0xff, allones, allones, vlen_enc);
    } else {
      vpcmpeqq(allones, allones, allones, vlen_enc);
    }
  }
  if (elem_bt == T_LONG) {
    vpsrlq(dst, allones, 1, vlen_enc);
  } else {
    assert(elem_bt == T_INT, "");
    vpsrld(dst, allones, 1, vlen_enc);
  }
}

void C2_MacroAssembler::vpgenmin_value(BasicType elem_bt, XMMRegister dst, XMMRegister allones, int vlen_enc, bool compute_allones) {
  if (compute_allones) {
    if (VM_Version::supports_avx512vl() || vlen_enc == Assembler::AVX_512bit) {
      vpternlogd(allones, 0xff, allones, allones, vlen_enc);
    } else {
      vpcmpeqq(allones, allones, allones, vlen_enc);
    }
  }
  if (elem_bt == T_LONG) {
    vpsllq(dst, allones, 63, vlen_enc);
  } else {
    assert(elem_bt == T_INT, "");
    vpslld(dst, allones, 31, vlen_enc);
  }
}

void C2_MacroAssembler::evpcmpu(BasicType elem_bt, KRegister kmask,  XMMRegister src1, XMMRegister src2,
                                Assembler::ComparisonPredicate cond, int vlen_enc) {
  switch(elem_bt) {
    case T_LONG:  evpcmpuq(kmask, src1, src2, cond, vlen_enc); break;
    case T_INT:   evpcmpud(kmask, src1, src2, cond, vlen_enc); break;
    case T_SHORT: evpcmpuw(kmask, src1, src2, cond, vlen_enc); break;
    case T_BYTE:  evpcmpub(kmask, src1, src2, cond, vlen_enc); break;
    default: fatal("Unsupported type %s", type2name(elem_bt)); break;
  }
}

void C2_MacroAssembler::vpcmpgt(BasicType elem_bt, XMMRegister dst, XMMRegister src1, XMMRegister src2, int vlen_enc) {
  switch(elem_bt) {
    case  T_LONG:  vpcmpgtq(dst, src1, src2, vlen_enc); break;
    case  T_INT:   vpcmpgtd(dst, src1, src2, vlen_enc); break;
    case  T_SHORT: vpcmpgtw(dst, src1, src2, vlen_enc); break;
    case  T_BYTE:  vpcmpgtb(dst, src1, src2, vlen_enc); break;
    default: fatal("Unsupported type %s", type2name(elem_bt)); break;
  }
}

void C2_MacroAssembler::evpmov_vec_to_mask(BasicType elem_bt, KRegister ktmp, XMMRegister src, XMMRegister xtmp1,
                                           XMMRegister xtmp2, int vlen_enc, bool xtmp2_hold_M1) {
  if (elem_bt == T_LONG) {
    evpmovq2m_emu(ktmp, src, xtmp1, xtmp2, vlen_enc, xtmp2_hold_M1);
  } else {
    assert(elem_bt == T_INT, "");
    evpmovd2m_emu(ktmp, src, xtmp1, xtmp2, vlen_enc, xtmp2_hold_M1);
  }
}

void C2_MacroAssembler::vector_addsub_dq_saturating_evex(int ideal_opc, BasicType elem_bt, XMMRegister dst, XMMRegister src1,
                                                         XMMRegister src2, XMMRegister xtmp1, XMMRegister xtmp2,
                                                         KRegister ktmp1, KRegister ktmp2, int vlen_enc) {
  assert(elem_bt == T_INT || elem_bt == T_LONG, "");
  // Addition/Subtraction happens over two's compliment representation of numbers and is agnostic to signed'ness.
  // Overflow detection based on Hacker's delight section 2-13.
  if (ideal_opc == Op_SaturatingAddV) {
    // res = src1 + src2
    vpadd(elem_bt, dst, src1, src2, vlen_enc);
    // Overflow occurs if result polarity does not comply with equivalent polarity inputs.
    // overflow = (((res ^ src1) & (res ^ src2)) >>> 31(I)/63(L)) == 1
    vpxor(xtmp1, dst, src1, vlen_enc);
    vpxor(xtmp2, dst, src2, vlen_enc);
    vpand(xtmp2, xtmp1, xtmp2, vlen_enc);
  } else {
    assert(ideal_opc == Op_SaturatingSubV, "");
    // res = src1 - src2
    vpsub(elem_bt, dst, src1, src2, vlen_enc);
    // Overflow occurs when both inputs have opposite polarity and
    // result polarity does not comply with first input polarity.
    // overflow = ((src1 ^ src2) & (res ^ src1) >>> 31(I)/63(L)) == 1;
    vpxor(xtmp1, src1, src2, vlen_enc);
    vpxor(xtmp2, dst, src1, vlen_enc);
    vpand(xtmp2, xtmp1, xtmp2, vlen_enc);
  }

  // Compute overflow detection mask.
  evpmov_vec_to_mask(elem_bt, ktmp1, xtmp2, xtmp2, xtmp1, vlen_enc);
  // Note: xtmp1 hold -1 in all its lanes after above call.

  // Compute mask based on first input polarity.
  evpmov_vec_to_mask(elem_bt, ktmp2, src1, xtmp2, xtmp1, vlen_enc, true);

  vpgenmax_value(elem_bt, xtmp2, xtmp1, vlen_enc, true);
  vpgenmin_value(elem_bt, xtmp1, xtmp1, vlen_enc);

  // Compose a vector of saturating (MAX/MIN) values, where lanes corresponding to
  // set bits in first input polarity mask holds a min value.
  evpblend(elem_bt, xtmp2, ktmp2, xtmp2, xtmp1, true, vlen_enc);
  // Blend destination lanes with saturated values using overflow detection mask.
  evpblend(elem_bt, dst, ktmp1, dst, xtmp2, true, vlen_enc);
}


void C2_MacroAssembler::vector_addsub_dq_saturating_avx(int ideal_opc, BasicType elem_bt, XMMRegister dst, XMMRegister src1,
                                                        XMMRegister src2, XMMRegister xtmp1, XMMRegister xtmp2,
                                                        XMMRegister xtmp3, XMMRegister xtmp4, int vlen_enc) {
  assert(elem_bt == T_INT || elem_bt == T_LONG, "");
  // Addition/Subtraction happens over two's compliment representation of numbers and is agnostic to signed'ness.
  // Overflow detection based on Hacker's delight section 2-13.
  if (ideal_opc == Op_SaturatingAddV) {
    // res = src1 + src2
    vpadd(elem_bt, dst, src1, src2, vlen_enc);
    // Overflow occurs if result polarity does not comply with equivalent polarity inputs.
    // overflow = (((res ^ src1) & (res ^ src2)) >>> 31(I)/63(L)) == 1
    vpxor(xtmp1, dst, src1, vlen_enc);
    vpxor(xtmp2, dst, src2, vlen_enc);
    vpand(xtmp2, xtmp1, xtmp2, vlen_enc);
  } else {
    assert(ideal_opc == Op_SaturatingSubV, "");
    // res = src1 - src2
    vpsub(elem_bt, dst, src1, src2, vlen_enc);
    // Overflow occurs when both inputs have opposite polarity and
    // result polarity does not comply with first input polarity.
    // overflow = ((src1 ^ src2) & (res ^ src1) >>> 31(I)/63(L)) == 1;
    vpxor(xtmp1, src1, src2, vlen_enc);
    vpxor(xtmp2, dst, src1, vlen_enc);
    vpand(xtmp2, xtmp1, xtmp2, vlen_enc);
  }

  // Sign-extend to compute overflow detection mask.
  vpsign_extend_dq(elem_bt, xtmp3, xtmp2, vlen_enc);

  vpcmpeqd(xtmp1, xtmp1, xtmp1, vlen_enc);
  vpgenmax_value(elem_bt, xtmp2, xtmp1, vlen_enc);
  vpgenmin_value(elem_bt, xtmp1, xtmp1, vlen_enc);

  // Compose saturating min/max vector using first input polarity mask.
  vpsign_extend_dq(elem_bt, xtmp4, src1, vlen_enc);
  vpblendvb(xtmp1, xtmp2, xtmp1, xtmp4, vlen_enc);

  // Blend result with saturating vector using overflow detection mask.
  vpblendvb(dst, dst, xtmp1, xtmp3, vlen_enc);
}

void C2_MacroAssembler::vector_saturating_op(int ideal_opc, BasicType elem_bt, XMMRegister dst, XMMRegister src1, Address src2, int vlen_enc) {
  switch(elem_bt) {
    case T_BYTE:
      if (ideal_opc == Op_SaturatingAddV) {
        vpaddsb(dst, src1, src2, vlen_enc);
      } else {
        assert(ideal_opc == Op_SaturatingSubV, "");
        vpsubsb(dst, src1, src2, vlen_enc);
      }
      break;
    case T_SHORT:
      if (ideal_opc == Op_SaturatingAddV) {
        vpaddsw(dst, src1, src2, vlen_enc);
      } else {
        assert(ideal_opc == Op_SaturatingSubV, "");
        vpsubsw(dst, src1, src2, vlen_enc);
      }
      break;
    default:
      fatal("Unsupported type %s", type2name(elem_bt));
      break;
  }
}

void C2_MacroAssembler::vector_saturating_unsigned_op(int ideal_opc, BasicType elem_bt, XMMRegister dst, XMMRegister src1, Address src2, int vlen_enc) {
  switch(elem_bt) {
    case T_BYTE:
      if (ideal_opc == Op_SaturatingAddV) {
        vpaddusb(dst, src1, src2, vlen_enc);
      } else {
        assert(ideal_opc == Op_SaturatingSubV, "");
        vpsubusb(dst, src1, src2, vlen_enc);
      }
      break;
    case T_SHORT:
      if (ideal_opc == Op_SaturatingAddV) {
        vpaddusw(dst, src1, src2, vlen_enc);
      } else {
        assert(ideal_opc == Op_SaturatingSubV, "");
        vpsubusw(dst, src1, src2, vlen_enc);
      }
      break;
    default:
      fatal("Unsupported type %s", type2name(elem_bt));
      break;
  }
}

void C2_MacroAssembler::select_from_two_vectors_evex(BasicType elem_bt, XMMRegister dst, XMMRegister src1,
                                                     XMMRegister src2, int vlen_enc) {
  switch(elem_bt) {
    case T_BYTE:
      evpermi2b(dst, src1, src2, vlen_enc);
      break;
    case T_SHORT:
      evpermi2w(dst, src1, src2, vlen_enc);
      break;
    case T_INT:
      evpermi2d(dst, src1, src2, vlen_enc);
      break;
    case T_LONG:
      evpermi2q(dst, src1, src2, vlen_enc);
      break;
    case T_FLOAT:
      evpermi2ps(dst, src1, src2, vlen_enc);
      break;
    case T_DOUBLE:
      evpermi2pd(dst, src1, src2, vlen_enc);
      break;
    default:
      fatal("Unsupported type %s", type2name(elem_bt));
      break;
  }
}

void C2_MacroAssembler::vector_saturating_op(int ideal_opc, BasicType elem_bt, XMMRegister dst, XMMRegister src1, XMMRegister src2, bool is_unsigned, int vlen_enc) {
  if (is_unsigned) {
    vector_saturating_unsigned_op(ideal_opc, elem_bt, dst, src1, src2, vlen_enc);
  } else {
    vector_saturating_op(ideal_opc, elem_bt, dst, src1, src2, vlen_enc);
  }
}

void C2_MacroAssembler::vector_saturating_op(int ideal_opc, BasicType elem_bt, XMMRegister dst, XMMRegister src1, Address src2, bool is_unsigned, int vlen_enc) {
  if (is_unsigned) {
    vector_saturating_unsigned_op(ideal_opc, elem_bt, dst, src1, src2, vlen_enc);
  } else {
    vector_saturating_op(ideal_opc, elem_bt, dst, src1, src2, vlen_enc);
  }
}

void C2_MacroAssembler::evfp16ph(int opcode, XMMRegister dst, XMMRegister src1, XMMRegister src2, int vlen_enc) {
  switch(opcode) {
    case Op_AddVHF: evaddph(dst, src1, src2, vlen_enc); break;
    case Op_SubVHF: evsubph(dst, src1, src2, vlen_enc); break;
    case Op_MulVHF: evmulph(dst, src1, src2, vlen_enc); break;
    case Op_DivVHF: evdivph(dst, src1, src2, vlen_enc); break;
    default: assert(false, "%s", NodeClassNames[opcode]); break;
  }
}

void C2_MacroAssembler::evfp16ph(int opcode, XMMRegister dst, XMMRegister src1, Address src2, int vlen_enc) {
  switch(opcode) {
    case Op_AddVHF: evaddph(dst, src1, src2, vlen_enc); break;
    case Op_SubVHF: evsubph(dst, src1, src2, vlen_enc); break;
    case Op_MulVHF: evmulph(dst, src1, src2, vlen_enc); break;
    case Op_DivVHF: evdivph(dst, src1, src2, vlen_enc); break;
    default: assert(false, "%s", NodeClassNames[opcode]); break;
  }
}

void C2_MacroAssembler::scalar_max_min_fp16(int opcode, XMMRegister dst, XMMRegister src1, XMMRegister src2,
                                            KRegister ktmp, XMMRegister xtmp1, XMMRegister xtmp2) {
  vector_max_min_fp16(opcode, dst, src1, src2, ktmp, xtmp1, xtmp2, Assembler::AVX_128bit);
}

void C2_MacroAssembler::vector_max_min_fp16(int opcode, XMMRegister dst, XMMRegister src1, XMMRegister src2,
                                            KRegister ktmp, XMMRegister xtmp1, XMMRegister xtmp2, int vlen_enc) {
  if (opcode == Op_MaxVHF || opcode == Op_MaxHF) {
    // Move sign bits of src2 to mask register.
    evpmovw2m(ktmp, src2, vlen_enc);
    // xtmp1 = src2 < 0 ? src2 : src1
    evpblendmw(xtmp1, ktmp, src1, src2, true, vlen_enc);
    // xtmp2 = src2 < 0 ? ? src1 : src2
    evpblendmw(xtmp2, ktmp, src2, src1, true, vlen_enc);
    // Idea behind above swapping is to make seconds source operand a +ve value.
    // As per instruction semantic, if the values being compared are both 0.0s (of either sign), the value in
    // the second source operand is returned. If only one value is a NaN (SNaN or QNaN) for this instruction,
    // the second source operand, either a NaN or a valid floating-point value, is returned
    // dst = max(xtmp1, xtmp2)
    evmaxph(dst, xtmp1, xtmp2, vlen_enc);
    // isNaN = is_unordered_quiet(xtmp1)
    evcmpph(ktmp, k0, xtmp1, xtmp1, Assembler::UNORD_Q, vlen_enc);
    // Final result is same as first source if its a NaN value,
    // in case second operand holds a NaN value then as per above semantics
    // result is same as second operand.
    Assembler::evmovdquw(dst, ktmp, xtmp1, true, vlen_enc);
  } else {
    assert(opcode == Op_MinVHF || opcode == Op_MinHF, "");
    // Move sign bits of src1 to mask register.
    evpmovw2m(ktmp, src1, vlen_enc);
    // xtmp1 = src1 < 0 ? src2 : src1
    evpblendmw(xtmp1, ktmp, src1, src2, true, vlen_enc);
    // xtmp2 = src1 < 0 ? src1 : src2
    evpblendmw(xtmp2, ktmp, src2, src1, true, vlen_enc);
    // Idea behind above swapping is to make seconds source operand a -ve value.
    // As per instruction semantics, if the values being compared are both 0.0s (of either sign), the value in
    // the second source operand is returned.
    // If only one value is a NaN (SNaN or QNaN) for this instruction, the second source operand, either a NaN
    // or a valid floating-point value, is written to the result.
    // dst = min(xtmp1, xtmp2)
    evminph(dst, xtmp1, xtmp2, vlen_enc);
    // isNaN = is_unordered_quiet(xtmp1)
    evcmpph(ktmp, k0, xtmp1, xtmp1, Assembler::UNORD_Q, vlen_enc);
    // Final result is same as first source if its a NaN value,
    // in case second operand holds a NaN value then as per above semantics
    // result is same as second operand.
    Assembler::evmovdquw(dst, ktmp, xtmp1, true, vlen_enc);
  }
}
