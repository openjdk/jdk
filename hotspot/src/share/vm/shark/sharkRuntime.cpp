/*
 * Copyright (c) 1999, 2012, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2008, 2009, 2010 Red Hat, Inc.
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

#include "precompiled.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/thread.hpp"
#include "shark/llvmHeaders.hpp"
#include "shark/sharkRuntime.hpp"
#ifdef TARGET_ARCH_zero
# include "stack_zero.inline.hpp"
#endif

using namespace llvm;

JRT_ENTRY(int, SharkRuntime::find_exception_handler(JavaThread* thread,
                                                    int*        indexes,
                                                    int         num_indexes))
  constantPoolHandle pool(thread, method(thread)->constants());
  KlassHandle exc_klass(thread, ((oop) tos_at(thread, 0))->klass());

  for (int i = 0; i < num_indexes; i++) {
    Klass* tmp = pool->klass_at(indexes[i], CHECK_0);
    KlassHandle chk_klass(thread, tmp);

    if (exc_klass() == chk_klass())
      return i;

    if (exc_klass()->is_subtype_of(chk_klass()))
      return i;
  }

  return -1;
JRT_END

JRT_ENTRY(void, SharkRuntime::monitorenter(JavaThread*      thread,
                                           BasicObjectLock* lock))
  if (PrintBiasedLockingStatistics)
    Atomic::inc(BiasedLocking::slow_path_entry_count_addr());

  Handle object(thread, lock->obj());
  assert(Universe::heap()->is_in_reserved_or_null(object()), "should be");
  if (UseBiasedLocking) {
    // Retry fast entry if bias is revoked to avoid unnecessary inflation
    ObjectSynchronizer::fast_enter(object, lock->lock(), true, CHECK);
  } else {
    ObjectSynchronizer::slow_enter(object, lock->lock(), CHECK);
  }
  assert(Universe::heap()->is_in_reserved_or_null(lock->obj()), "should be");
JRT_END

JRT_ENTRY(void, SharkRuntime::monitorexit(JavaThread*      thread,
                                          BasicObjectLock* lock))
  Handle object(thread, lock->obj());
  assert(Universe::heap()->is_in_reserved_or_null(object()), "should be");
  if (lock == NULL || object()->is_unlocked()) {
    THROW(vmSymbols::java_lang_IllegalMonitorStateException());
  }
  ObjectSynchronizer::slow_exit(object(), lock->lock(), thread);
JRT_END

JRT_ENTRY(void, SharkRuntime::new_instance(JavaThread* thread, int index))
  Klass* k_oop = method(thread)->constants()->klass_at(index, CHECK);
  instanceKlassHandle klass(THREAD, k_oop);

  // Make sure we are not instantiating an abstract klass
  klass->check_valid_for_instantiation(true, CHECK);

  // Make sure klass is initialized
  klass->initialize(CHECK);

  // At this point the class may not be fully initialized
  // because of recursive initialization. If it is fully
  // initialized & has_finalized is not set, we rewrite
  // it into its fast version (Note: no locking is needed
  // here since this is an atomic byte write and can be
  // done more than once).
  //
  // Note: In case of classes with has_finalized we don't
  //       rewrite since that saves us an extra check in
  //       the fast version which then would call the
  //       slow version anyway (and do a call back into
  //       Java).
  //       If we have a breakpoint, then we don't rewrite
  //       because the _breakpoint bytecode would be lost.
  oop obj = klass->allocate_instance(CHECK);
  thread->set_vm_result(obj);
JRT_END

JRT_ENTRY(void, SharkRuntime::newarray(JavaThread* thread,
                                       BasicType   type,
                                       int         size))
  oop obj = oopFactory::new_typeArray(type, size, CHECK);
  thread->set_vm_result(obj);
JRT_END

JRT_ENTRY(void, SharkRuntime::anewarray(JavaThread* thread,
                                        int         index,
                                        int         size))
  Klass* klass = method(thread)->constants()->klass_at(index, CHECK);
  objArrayOop obj = oopFactory::new_objArray(klass, size, CHECK);
  thread->set_vm_result(obj);
JRT_END

JRT_ENTRY(void, SharkRuntime::multianewarray(JavaThread* thread,
                                             int         index,
                                             int         ndims,
                                             int*        dims))
  Klass* klass = method(thread)->constants()->klass_at(index, CHECK);
  oop obj = ArrayKlass::cast(klass)->multi_allocate(ndims, dims, CHECK);
  thread->set_vm_result(obj);
JRT_END

JRT_ENTRY(void, SharkRuntime::register_finalizer(JavaThread* thread,
                                                 oop         object))
  assert(object->is_oop(), "should be");
  assert(object->klass()->has_finalizer(), "should have");
  InstanceKlass::register_finalizer(instanceOop(object), CHECK);
JRT_END

JRT_ENTRY(void, SharkRuntime::throw_ArithmeticException(JavaThread* thread,
                                                        const char* file,
                                                        int         line))
  Exceptions::_throw_msg(
    thread, file, line,
    vmSymbols::java_lang_ArithmeticException(),
    "");
JRT_END

JRT_ENTRY(void, SharkRuntime::throw_ArrayIndexOutOfBoundsException(
                                                     JavaThread* thread,
                                                     const char* file,
                                                     int         line,
                                                     int         index))
  char msg[jintAsStringSize];
  snprintf(msg, sizeof(msg), "%d", index);
  Exceptions::_throw_msg(
    thread, file, line,
    vmSymbols::java_lang_ArrayIndexOutOfBoundsException(),
    msg);
JRT_END

JRT_ENTRY(void, SharkRuntime::throw_ClassCastException(JavaThread* thread,
                                                       const char* file,
                                                       int         line))
  Exceptions::_throw_msg(
    thread, file, line,
    vmSymbols::java_lang_ClassCastException(),
    "");
JRT_END

JRT_ENTRY(void, SharkRuntime::throw_NullPointerException(JavaThread* thread,
                                                         const char* file,
                                                         int         line))
  Exceptions::_throw_msg(
    thread, file, line,
    vmSymbols::java_lang_NullPointerException(),
    "");
JRT_END

// Non-VM calls
// Nothing in these must ever GC!

void SharkRuntime::dump(const char *name, intptr_t value) {
  oop valueOop = (oop) value;
  tty->print("%s = ", name);
  if (valueOop->is_oop(true))
    valueOop->print_on(tty);
  else if (value >= ' ' && value <= '~')
    tty->print("'%c' (%d)", value, value);
  else
    tty->print("%p", value);
  tty->print_cr("");
}

bool SharkRuntime::is_subtype_of(Klass* check_klass, Klass* object_klass) {
  return object_klass->is_subtype_of(check_klass);
}

int SharkRuntime::uncommon_trap(JavaThread* thread, int trap_request) {
  Thread *THREAD = thread;

  // In C2, uncommon_trap_blob creates a frame, so all the various
  // deoptimization functions expect to find the frame of the method
  // being deopted one frame down on the stack.  We create a dummy
  // frame to mirror this.
  FakeStubFrame *stubframe = FakeStubFrame::build(CHECK_0);
  thread->push_zero_frame(stubframe);

  // Initiate the trap
  thread->set_last_Java_frame();
  Deoptimization::UnrollBlock *urb =
    Deoptimization::uncommon_trap(thread, trap_request);
  thread->reset_last_Java_frame();

  // Pop our dummy frame and the frame being deoptimized
  thread->pop_zero_frame();
  thread->pop_zero_frame();

  // Push skeleton frames
  int number_of_frames = urb->number_of_frames();
  for (int i = 0; i < number_of_frames; i++) {
    intptr_t size = urb->frame_sizes()[i];
    InterpreterFrame *frame = InterpreterFrame::build(size, CHECK_0);
    thread->push_zero_frame(frame);
  }

  // Push another dummy frame
  stubframe = FakeStubFrame::build(CHECK_0);
  thread->push_zero_frame(stubframe);

  // Fill in the skeleton frames
  thread->set_last_Java_frame();
  Deoptimization::unpack_frames(thread, Deoptimization::Unpack_uncommon_trap);
  thread->reset_last_Java_frame();

  // Pop our dummy frame
  thread->pop_zero_frame();

  // Fall back into the interpreter
  return number_of_frames;
}

FakeStubFrame* FakeStubFrame::build(TRAPS) {
  ZeroStack *stack = ((JavaThread *) THREAD)->zero_stack();
  stack->overflow_check(header_words, CHECK_NULL);

  stack->push(0); // next_frame, filled in later
  intptr_t *fp = stack->sp();
  assert(fp - stack->sp() == next_frame_off, "should be");

  stack->push(FAKE_STUB_FRAME);
  assert(fp - stack->sp() == frame_type_off, "should be");

  return (FakeStubFrame *) fp;
}
