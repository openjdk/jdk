/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/vmSymbols.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "oops/method.inline.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiThreadState.inline.hpp"
#include "runtime/continuation.hpp"
#include "runtime/continuationEntry.inline.hpp"
#include "runtime/continuationHelper.inline.hpp"
#include "runtime/continuationJavaClasses.inline.hpp"
#include "runtime/continuationWrapper.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/mountUnmountDisabler.hpp"
#include "runtime/osThread.hpp"
#include "runtime/vframe.inline.hpp"
#include "runtime/vframe_hp.hpp"

// defined in continuationFreezeThaw.cpp
extern "C" jint JNICALL CONT_isPinned0(JNIEnv* env, jobject cont_scope);

JVM_ENTRY(void, CONT_pin(JNIEnv* env, jclass cls)) {
  if (!Continuation::pin(JavaThread::thread_from_jni_environment(env))) {
     THROW_MSG(vmSymbols::java_lang_IllegalStateException(), "pin overflow");
  }
}
JVM_END

JVM_ENTRY(void, CONT_unpin(JNIEnv* env, jclass cls)) {
  if (!Continuation::unpin(JavaThread::thread_from_jni_environment(env))) {
     THROW_MSG(vmSymbols::java_lang_IllegalStateException(), "pin underflow");
  }
}
JVM_END

class UnmountBeginMark : public StackObj {
  Handle _vthread;
  JavaThread* _current;
  freeze_result _result;
  bool _failed;

 public:
  UnmountBeginMark(JavaThread* t) :
    _vthread(t, t->vthread()), _current(t), _result(freeze_pinned_native), _failed(false) {
    assert(!_current->is_in_vthread_transition(), "must be");

    MountUnmountDisabler::start_transition(_current, _vthread(), false /*is_mount*/, false /*is_thread_start*/);

    // Don't preempt if there is a pending popframe or earlyret operation. This can
    // be installed in in process_at_transition_start() so we need to check it here.
    if (JvmtiExport::can_pop_frame() || JvmtiExport::can_force_early_return()) {
      JvmtiThreadState* state = _current->jvmti_thread_state();
      if (_current->has_pending_popframe() || (state != nullptr && state->is_earlyret_pending())) {
        _failed = true;
      }
    }

    // Don't preempt in case there is an async exception installed since
    // we would incorrectly throw it during the unmount logic in the carrier.
    if (_current->has_async_exception_condition()) {
      _failed = true;
    }
  }
  ~UnmountBeginMark() {
    assert(!_current->is_suspended()
           JVMTI_ONLY(|| (_current->is_vthread_transition_disabler() && _result != freeze_ok)), "must be");
    assert(_current->is_in_vthread_transition(), "must be");

    if (_result != freeze_ok) {
      // Undo transition
      MountUnmountDisabler::end_transition(_current, _vthread(), true /*is_mount*/, false /*is_thread_start*/);
    }
  }
  void set_result(freeze_result res) { _result = res; }
  bool failed() { return _failed; }
};

#if INCLUDE_JVMTI
static bool is_vthread_safe_to_preempt_for_jvmti(JavaThread* current) {
  if (current->is_in_vthread_transition()) {
    // We are at the end of a mount transition.
    return false;
  }
  return true;
}
#endif // INCLUDE_JVMTI

static bool is_vthread_safe_to_preempt(JavaThread* current, oop vthread) {
  assert(java_lang_VirtualThread::is_instance(vthread), "");
  if (java_lang_VirtualThread::state(vthread) != java_lang_VirtualThread::RUNNING) {  // inside transition
    return false;
  }
  return JVMTI_ONLY(is_vthread_safe_to_preempt_for_jvmti(current)) NOT_JVMTI(true);
}

typedef freeze_result (*FreezeContFnT)(JavaThread*, intptr_t*);

static void verify_preempt_preconditions(JavaThread* current, oop continuation) {
  assert(current == JavaThread::current(), "no support for external preemption");
  assert(current->has_last_Java_frame(), "");
  assert(!current->preempting(), "");
  assert(current->last_continuation() != nullptr, "");
  assert(current->last_continuation()->cont_oop(current) == continuation, "");
  assert(Continuation::continuation_scope(continuation) == java_lang_VirtualThread::vthread_scope(), "");
  assert(!current->has_pending_exception(), "");
}

freeze_result Continuation::try_preempt(JavaThread* current, oop continuation) {
  verify_preempt_preconditions(current, continuation);

  if (!is_vthread_safe_to_preempt(current, current->vthread())) {
    return freeze_pinned_native;
  }

  UnmountBeginMark ubm(current);
  if (ubm.failed()) return freeze_pinned_native;
  freeze_result res = CAST_TO_FN_PTR(FreezeContFnT, freeze_preempt_entry())(current, current->last_Java_sp());
  log_trace(continuations, preempt)("try_preempt: %d", res);
  ubm.set_result(res);

  if (current->has_pending_exception()) {
    assert(res == freeze_exception, "expecting an exception result from freeze");
    // We don't want to throw exceptions, especially when returning
    // from monitorenter since the compiler does not expect one. We
    // just ignore the exception and pin the vthread to the carrier.
    current->clear_pending_exception();
  }
  return res;
}

#ifndef PRODUCT
static jlong java_tid(JavaThread* thread) {
  return java_lang_Thread::thread_id(thread->threadObj());
}
#endif

ContinuationEntry* Continuation::get_continuation_entry_for_continuation(JavaThread* thread, oop continuation) {
  if (thread == nullptr || continuation == nullptr) {
    return nullptr;
  }

  for (ContinuationEntry* entry = thread->last_continuation(); entry != nullptr; entry = entry->parent()) {
    if (continuation == entry->cont_oop(thread)) {
      return entry;
    }
  }
  return nullptr;
}

static bool is_on_stack(JavaThread* thread, const ContinuationEntry* entry) {
  if (entry == nullptr) {
    return false;
  }

  assert(thread->is_in_full_stack((address)entry), "");
  return true;
  // return false if called when transitioning to Java on return from freeze
  // return !thread->has_last_Java_frame() || thread->last_Java_sp() < cont->entry_sp();
}

bool Continuation::is_continuation_mounted(JavaThread* thread, oop continuation) {
  return is_on_stack(thread, get_continuation_entry_for_continuation(thread, continuation));
}

// When walking the virtual stack, this method returns true
// iff the frame is a thawed continuation frame whose
// caller is still frozen on the h-stack.
// The continuation object can be extracted from the thread.
bool Continuation::is_cont_barrier_frame(const frame& f) {
  assert(f.is_interpreted_frame() || f.cb() != nullptr, "");
  if (!Continuations::enabled()) return false;
  return is_return_barrier_entry(f.is_interpreted_frame() ? ContinuationHelper::InterpretedFrame::return_pc(f)
                                                          : ContinuationHelper::CompiledFrame::return_pc(f));
}

bool Continuation::is_return_barrier_entry(const address pc) {
  if (!Continuations::enabled()) return false;
  return pc == StubRoutines::cont_returnBarrier();
}

bool Continuation::is_continuation_enterSpecial(const frame& f) {
  if (f.cb() == nullptr || !f.cb()->is_nmethod()) {
    return false;
  }
  Method* m = f.cb()->as_nmethod()->method();
  return (m != nullptr && m->is_continuation_enter_intrinsic());
}

bool Continuation::is_continuation_entry_frame(const frame& f, const RegisterMap *map) {
  // we can do this because the entry frame is never inlined
  Method* m = (map != nullptr && map->in_cont() && f.is_interpreted_frame())
                  ? map->stack_chunk()->interpreter_frame_method(f)
                  : ContinuationHelper::Frame::frame_method(f);
  return m != nullptr && m->intrinsic_id() == vmIntrinsics::_Continuation_enter;
}

// The parameter `sp` should be the actual sp and not the unextended sp because at
// least on PPC64 unextended_sp < sp is possible as interpreted frames are trimmed
// to the actual size of the expression stack before calls. The problem there is
// that even unextended_sp < entry_sp < sp is possible for an interpreted frame.
static inline bool is_sp_in_continuation(const ContinuationEntry* entry, intptr_t* const sp) {
  // entry_sp() returns the unextended_sp which is always greater or equal to the actual sp
  return entry->entry_sp() > sp;
}

bool Continuation::is_frame_in_continuation(const ContinuationEntry* entry, const frame& f) {
  return is_sp_in_continuation(entry, f.sp());
}

ContinuationEntry* Continuation::get_continuation_entry_for_sp(JavaThread* thread, intptr_t* const sp) {
  assert(thread != nullptr, "");
  ContinuationEntry* entry = thread->last_continuation();
  while (entry != nullptr && !is_sp_in_continuation(entry, sp)) {
    entry = entry->parent();
  }
  return entry;
}

ContinuationEntry* Continuation::get_continuation_entry_for_entry_frame(JavaThread* thread, const frame& f) {
  assert(is_continuation_enterSpecial(f), "");
  ContinuationEntry* entry = (ContinuationEntry*)f.unextended_sp();
  assert(entry == get_continuation_entry_for_sp(thread, f.sp()-2), "mismatched entry");
  return entry;
}

bool Continuation::is_frame_in_continuation(JavaThread* thread, const frame& f) {
  return f.is_heap_frame() || (get_continuation_entry_for_sp(thread, f.sp()) != nullptr);
}

static frame continuation_top_frame(const ContinuationWrapper& cont, RegisterMap* map) {
  stackChunkOop chunk = cont.last_nonempty_chunk();
  map->set_stack_chunk(chunk);
  return chunk != nullptr ? chunk->top_frame(map) : frame();
}

bool Continuation::has_last_Java_frame(oop continuation, frame* frame, RegisterMap* map) {
  ContinuationWrapper cont(continuation);
  if (!cont.is_empty()) {
    *frame = continuation_top_frame(cont, map);
    return true;
  } else {
    return false;
  }
}

frame Continuation::last_frame(oop continuation, RegisterMap *map) {
  assert(map != nullptr, "a map must be given");
  return continuation_top_frame(ContinuationWrapper(continuation), map);
}

frame Continuation::top_frame(const frame& callee, RegisterMap* map) {
  assert(map != nullptr, "");
  ContinuationEntry* ce = get_continuation_entry_for_sp(map->thread(), callee.sp());
  assert(ce != nullptr, "");
  oop continuation = ce->cont_oop(map->thread());
  ContinuationWrapper cont(continuation);
  return continuation_top_frame(cont, map);
}

javaVFrame* Continuation::last_java_vframe(Handle continuation, RegisterMap *map) {
  assert(map != nullptr, "a map must be given");
  if (!ContinuationWrapper(continuation()).is_empty()) {
    frame f = last_frame(continuation(), map);
    for (vframe* vf = vframe::new_vframe(&f, map, nullptr); vf; vf = vf->sender()) {
      if (vf->is_java_frame()) {
        return javaVFrame::cast(vf);
      }
    }
  }
  return nullptr;
}

frame Continuation::continuation_parent_frame(RegisterMap* map) {
  assert(map->in_cont(), "");
  ContinuationWrapper cont(map);
  assert(map->thread() != nullptr || !cont.is_mounted(), "");

  log_develop_trace(continuations)("continuation_parent_frame");
  if (map->update_map()) {
    // we need to register the link address for the entry frame
    if (cont.entry() != nullptr) {
      cont.entry()->update_register_map(map);
    } else {
      map->clear();
    }
  }

  if (!cont.is_mounted()) { // When we're walking an unmounted continuation and reached the end
    oop parent = jdk_internal_vm_Continuation::parent(cont.continuation());
    stackChunkOop chunk = parent != nullptr ? ContinuationWrapper(parent).last_nonempty_chunk() : nullptr;
    if (chunk != nullptr) {
      return chunk->top_frame(map);
    }

    map->set_stack_chunk(nullptr);
    return frame();
  }

  map->set_stack_chunk(nullptr);

#if (defined(X86) || defined(AARCH64) || defined(RISCV64) || defined(PPC64)) && !defined(ZERO)
  frame sender(cont.entrySP(), cont.entryFP(), cont.entryPC());
#else
  frame sender = frame();
  Unimplemented();
#endif

  return sender;
}

oop Continuation::continuation_scope(oop continuation) {
  return continuation != nullptr ? jdk_internal_vm_Continuation::scope(continuation) : nullptr;
}

bool Continuation::is_scope_bottom(oop cont_scope, const frame& f, const RegisterMap* map) {
  if (cont_scope == nullptr || !is_continuation_entry_frame(f, map)) {
    return false;
  }

  oop continuation;
  if (map->in_cont()) {
    continuation = map->cont();
  } else {
    ContinuationEntry* ce = get_continuation_entry_for_sp(map->thread(), f.sp());
    if (ce == nullptr) {
      return false;
    }
    continuation = ce->cont_oop(map->thread());
  }
  if (continuation == nullptr) {
    return false;
  }

  oop sc = continuation_scope(continuation);
  assert(sc != nullptr, "");
  return sc == cont_scope;
}

bool Continuation::is_in_usable_stack(address addr, const RegisterMap* map) {
  ContinuationWrapper cont(map);
  stackChunkOop chunk = cont.find_chunk_by_address(addr);
  return chunk != nullptr ? chunk->is_usable_in_chunk(addr) : false;
}

bool Continuation::pin(JavaThread* current) {
  ContinuationEntry* ce = current->last_continuation();
  if (ce == nullptr) {
    return true; // no continuation mounted
  }
  return ce->pin();
}

bool Continuation::unpin(JavaThread* current) {
  ContinuationEntry* ce = current->last_continuation();
  if (ce == nullptr) {
    return true; // no continuation mounted
  }
  return ce->unpin();
}

frame Continuation::continuation_bottom_sender(JavaThread* thread, const frame& callee, intptr_t* sender_sp) {
  assert (thread != nullptr, "");
  ContinuationEntry* ce = get_continuation_entry_for_sp(thread, callee.sp());
  assert(ce != nullptr, "callee.sp(): " INTPTR_FORMAT, p2i(callee.sp()));

  log_develop_debug(continuations)("continuation_bottom_sender: [" JLONG_FORMAT "] [%d] callee: " INTPTR_FORMAT
    " sender_sp: " INTPTR_FORMAT,
    java_tid(thread), thread->osthread()->thread_id(), p2i(callee.sp()), p2i(sender_sp));

  frame entry = ce->to_frame();
  if (callee.is_interpreted_frame()) {
    entry.set_sp(sender_sp); // sp != unextended_sp
  }
  return entry;
}

address Continuation::get_top_return_pc_post_barrier(JavaThread* thread, address pc) {
  ContinuationEntry* ce;
  if (thread != nullptr && is_return_barrier_entry(pc) && (ce = thread->last_continuation()) != nullptr) {
    return ce->entry_pc();
  }
  return pc;
}

void Continuation::set_cont_fastpath_thread_state(JavaThread* thread) {
  assert(thread != nullptr, "");
  bool fast = !thread->is_interp_only_mode();
  thread->set_cont_fastpath_thread_state(fast);
}

void Continuation::notify_deopt(JavaThread* thread, intptr_t* sp) {
  ContinuationEntry* entry = thread->last_continuation();

  if (entry == nullptr) {
    return;
  }

  if (is_sp_in_continuation(entry, sp)) {
    thread->push_cont_fastpath(sp);
    return;
  }

  ContinuationEntry* prev;
  do {
    prev = entry;
    entry = entry->parent();
  } while (entry != nullptr && !is_sp_in_continuation(entry, sp));

  if (entry == nullptr) {
    return;
  }
  assert(is_sp_in_continuation(entry, sp), "");
  if (sp > prev->parent_cont_fastpath()) {
    prev->set_parent_cont_fastpath(sp);
  }
}

#ifndef PRODUCT
void Continuation::describe(FrameValues &values) {
  JavaThread* thread = JavaThread::active();
  if (thread != nullptr) {
    for (ContinuationEntry* ce = thread->last_continuation(); ce != nullptr; ce = ce->parent()) {
      intptr_t* bottom = ce->entry_sp();
      if (bottom != nullptr) {
        values.describe(-1, bottom, "continuation entry");
      }
    }
  }
}
#endif

#ifdef ASSERT
void Continuation::debug_verify_continuation(oop contOop) {
  if (!VerifyContinuations) {
    return;
  }
  assert(contOop != nullptr, "");
  assert(oopDesc::is_oop(contOop), "");
  ContinuationWrapper cont(contOop);

  assert(oopDesc::is_oop_or_null(cont.tail()), "");
  assert(cont.chunk_invariant(), "");

  bool nonempty_chunk = false;
  size_t max_size = 0;
  int num_chunks = 0;
  int num_frames = 0;
  int num_interpreted_frames = 0;
  int num_oops = 0;

  for (stackChunkOop chunk = cont.tail(); chunk != nullptr; chunk = chunk->parent()) {
    log_develop_trace(continuations)("debug_verify_continuation chunk %d", num_chunks);
    chunk->verify(&max_size, &num_oops, &num_frames, &num_interpreted_frames);
    if (!chunk->is_empty()) {
      nonempty_chunk = true;
    }
    num_chunks++;
  }

  const bool is_empty = cont.is_empty();
  assert(!nonempty_chunk || !is_empty, "");
  assert(is_empty == (!nonempty_chunk && cont.last_frame().is_empty()), "");
}

void Continuation::print(oop continuation) { print_on(tty, continuation); }

void Continuation::print_on(outputStream* st, oop continuation) {
  ContinuationWrapper cont(continuation);

  st->print_cr("CONTINUATION: " PTR_FORMAT " done: %d",
    continuation->identity_hash(), jdk_internal_vm_Continuation::done(continuation));
  st->print_cr("CHUNKS:");
  for (stackChunkOop chunk = cont.tail(); chunk != nullptr; chunk = chunk->parent()) {
    st->print("* ");
    chunk->print_on(true, st);
  }
}
#endif // ASSERT


void continuations_init() { Continuations::init(); }

void Continuations::init() {
  Continuation::init();
}

bool Continuations::enabled() {
  return VMContinuations;
}

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)

static JNINativeMethod CONT_methods[] = {
    {CC"pin",              CC"()V",                                    FN_PTR(CONT_pin)},
    {CC"unpin",            CC"()V",                                    FN_PTR(CONT_unpin)},
    {CC"isPinned0",        CC"(Ljdk/internal/vm/ContinuationScope;)I", FN_PTR(CONT_isPinned0)},
};

void CONT_RegisterNativeMethods(JNIEnv *env, jclass cls) {
    JavaThread* thread = JavaThread::current();
    ThreadToNativeFromVM trans(thread);
    int status = env->RegisterNatives(cls, CONT_methods, sizeof(CONT_methods)/sizeof(JNINativeMethod));
    guarantee(status == JNI_OK, "register jdk.internal.vm.Continuation natives");
    guarantee(!env->ExceptionCheck(), "register jdk.internal.vm.Continuation natives");
}
