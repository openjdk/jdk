/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.inline.hpp"
#include "code/compiledMethod.inline.hpp"
#include "code/vmreg.inline.hpp"
#include "compiler/oopMap.inline.hpp"
#include "gc/shared/continuationGCSupport.inline.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/memAllocator.hpp"
#include "gc/shared/threadLocalAllocBuffer.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "jfr/jfrEvents.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "oops/access.inline.hpp"
#include "oops/method.inline.hpp"
#include "oops/oopsHierarchy.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/stackChunkOop.inline.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "runtime/arguments.hpp"
#include "runtime/continuation.hpp"
#include "runtime/continuationEntry.inline.hpp"
#include "runtime/continuationHelper.inline.hpp"
#include "runtime/continuationJavaClasses.inline.hpp"
#include "runtime/continuationWrapper.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/keepStackGCProcessed.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/prefetch.inline.hpp"
#include "runtime/smallRegisterMap.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stackChunkFrameStream.inline.hpp"
#include "runtime/stackFrameStream.inline.hpp"
#include "runtime/stackOverflow.hpp"
#include "runtime/stackWatermarkSet.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ZGC
#include "gc/z/zStackChunkGCData.inline.hpp"
#endif

#include <type_traits>

/*
 * This file contains the implementation of continuation freezing (yield) and thawing (run).
 *
 * This code is very latency-critical and very hot. An ordinary and well-behaved server application
 * would likely call these operations many thousands of times per second second, on every core.
 *
 * Freeze might be called every time the application performs any I/O operation, every time it
 * acquires a j.u.c. lock, every time it takes a message from a queue, and thaw can be called
 * multiple times in each of those cases, as it is called by the return barrier, which may be
 * invoked on method return.
 *
 * The amortized budget for each of those two operations is ~100-150ns. That is why, for
 * example, every effort is made to avoid Java-VM transitions as much as possible.
 *
 * On the fast path, all frames are known to be compiled, and the chunk requires no barriers
 * and so frames simply copied, and the bottom-most one is patched.
 * On the slow path, internal pointers in interpreted frames are de/relativized to/from offsets
 * and absolute pointers, and barriers invoked.
 */

/************************************************

Thread-stack layout on freeze/thaw.
See corresponding stack-chunk layout in instanceStackChunkKlass.hpp

            +----------------------------+
            |      .                     |
            |      .                     |
            |      .                     |
            |   carrier frames           |
            |                            |
            |----------------------------|
            |                            |
            |    Continuation.run        |
            |                            |
            |============================|
            |    enterSpecial frame      |
            |  pc                        |
            |  rbp                       |
            |  -----                     |
        ^   |  int argsize               | = ContinuationEntry
        |   |  oopDesc* cont             |
        |   |  oopDesc* chunk            |
        |   |  ContinuationEntry* parent |
        |   |  ...                       |
        |   |============================| <------ JavaThread::_cont_entry = entry->sp()
        |   |  ? alignment word ?        |
        |   |----------------------------| <--\
        |   |                            |    |
        |   |  ? caller stack args ?     |    |   argsize (might not be 2-word aligned) words
Address |   |                            |    |   Caller is still in the chunk.
        |   |----------------------------|    |
        |   |  pc (? return barrier ?)   |    |  This pc contains the return barrier when the bottom-most frame
        |   |  rbp                       |    |  isn't the last one in the continuation.
        |   |                            |    |
        |   |    frame                   |    |
        |   |                            |    |
            +----------------------------|     \__ Continuation frames to be frozen/thawed
            |                            |     /
            |    frame                   |    |
            |                            |    |
            |----------------------------|    |
            |                            |    |
            |    frame                   |    |
            |                            |    |
            |----------------------------| <--/
            |                            |
            |    doYield/safepoint stub  | When preempting forcefully, we could have a safepoint stub
            |                            | instead of a doYield stub
            |============================| <- the sp passed to freeze
            |                            |
            |  Native freeze/thaw frames |
            |      .                     |
            |      .                     |
            |      .                     |
            +----------------------------+

************************************************/

static const bool TEST_THAW_ONE_CHUNK_FRAME = false; // force thawing frames one-at-a-time for testing

#define CONT_JFR false // emit low-level JFR events that count slow/fast path for continuation performance debugging only
#if CONT_JFR
  #define CONT_JFR_ONLY(code) code
#else
  #define CONT_JFR_ONLY(code)
#endif

// TODO: See AbstractAssembler::generate_stack_overflow_check,
// Compile::bang_size_in_bytes(), m->as_SafePoint()->jvms()->interpreter_frame_size()
// when we stack-bang, we need to update a thread field with the lowest (farthest) bang point.

// Data invariants are defined by Continuation::debug_verify_continuation and Continuation::debug_verify_stack_chunk

// Used to just annotatate cold/hot branches
#define LIKELY(condition)   (condition)
#define UNLIKELY(condition) (condition)

// debugging functions
#ifdef ASSERT
extern "C" bool dbg_is_safe(const void* p, intptr_t errvalue); // address p is readable and *(intptr_t*)p != errvalue

static void verify_continuation(oop continuation) { Continuation::debug_verify_continuation(continuation); }

static void do_deopt_after_thaw(JavaThread* thread);
static bool do_verify_after_thaw(JavaThread* thread, stackChunkOop chunk, outputStream* st);
static void log_frames(JavaThread* thread);
static void print_frame_layout(const frame& f, bool callee_complete, outputStream* st = tty);

#define assert_pfl(p, ...) \
do {                                           \
  if (!(p)) {                                  \
    JavaThread* t = JavaThread::active();      \
    if (t->has_last_Java_frame()) {            \
      tty->print_cr("assert(" #p ") failed:"); \
      t->print_frame_layout();                 \
    }                                          \
  }                                            \
  vmassert(p, __VA_ARGS__);                    \
} while(0)

#else
static void verify_continuation(oop continuation) { }
#define assert_pfl(p, ...)
#endif

// should match Continuation.preemptStatus() in Continuation.java
enum freeze_result {
  freeze_ok = 0,
  freeze_ok_bottom = 1,
  freeze_pinned_cs = 2,
  freeze_pinned_native = 3,
  freeze_pinned_monitor = 4,
  freeze_exception = 5
};

const char* freeze_result_names[6] = {
  "freeze_ok",
  "freeze_ok_bottom",
  "freeze_pinned_cs",
  "freeze_pinned_native",
  "freeze_pinned_monitor",
  "freeze_exception"
};

static freeze_result is_pinned0(JavaThread* thread, oop cont_scope, bool safepoint);
template<typename ConfigT> static inline int freeze_internal(JavaThread* current, intptr_t* const sp);

static inline int prepare_thaw_internal(JavaThread* thread, bool return_barrier);
template<typename ConfigT> static inline intptr_t* thaw_internal(JavaThread* thread, const Continuation::thaw_kind kind);


// Entry point to freeze. Transitions are handled manually
// Called from gen_continuation_yield() in sharedRuntime_<cpu>.cpp through Continuation::freeze_entry();
template<typename ConfigT>
static JRT_BLOCK_ENTRY(int, freeze(JavaThread* current, intptr_t* sp))
  assert(sp == current->frame_anchor()->last_Java_sp(), "");

  if (current->raw_cont_fastpath() > current->last_continuation()->entry_sp() || current->raw_cont_fastpath() < sp) {
    current->set_cont_fastpath(nullptr);
  }

  return ConfigT::freeze(current, sp);
JRT_END

JRT_LEAF(int, Continuation::prepare_thaw(JavaThread* thread, bool return_barrier))
  return prepare_thaw_internal(thread, return_barrier);
JRT_END

template<typename ConfigT>
static JRT_LEAF(intptr_t*, thaw(JavaThread* thread, int kind))
  // TODO: JRT_LEAF and NoHandleMark is problematic for JFR events.
  // vFrameStreamCommon allocates Handles in RegisterMap for continuations.
  // JRT_ENTRY instead?
  ResetNoHandleMark rnhm;

  // we might modify the code cache via BarrierSetNMethod::nmethod_entry_barrier
  MACOS_AARCH64_ONLY(ThreadWXEnable __wx(WXWrite, thread));
  return ConfigT::thaw(thread, (Continuation::thaw_kind)kind);
JRT_END

JVM_ENTRY(jint, CONT_isPinned0(JNIEnv* env, jobject cont_scope)) {
  JavaThread* thread = JavaThread::thread_from_jni_environment(env);
  return is_pinned0(thread, JNIHandles::resolve(cont_scope), false);
}
JVM_END

///////////

enum class oop_kind { NARROW, WIDE };
template <oop_kind oops, typename BarrierSetT>
class Config {
public:
  typedef Config<oops, BarrierSetT> SelfT;
  using OopT = std::conditional_t<oops == oop_kind::NARROW, narrowOop, oop>;

  static int freeze(JavaThread* thread, intptr_t* const sp) {
    return freeze_internal<SelfT>(thread, sp);
  }

  static intptr_t* thaw(JavaThread* thread, Continuation::thaw_kind kind) {
    return thaw_internal<SelfT>(thread, kind);
  }
};

static bool stack_overflow_check(JavaThread* thread, size_t size, address sp) {
  const size_t page_size = os::vm_page_size();
  if (size > page_size) {
    if (sp - size < thread->stack_overflow_state()->shadow_zone_safe_limit()) {
      return false;
    }
  }
  return true;
}

#ifdef ASSERT
static oop get_continuation(JavaThread* thread) {
  assert(thread != nullptr, "");
  assert(thread->threadObj() != nullptr, "");
  return java_lang_Thread::continuation(thread->threadObj());
}

inline void clear_anchor(JavaThread* thread) {
  thread->frame_anchor()->clear();
}

static void set_anchor(JavaThread* thread, intptr_t* sp) {
  address pc = ContinuationHelper::return_address_at(
                 sp - frame::sender_sp_ret_address_offset());
  assert(pc != nullptr, "");

  JavaFrameAnchor* anchor = thread->frame_anchor();
  anchor->set_last_Java_sp(sp);
  anchor->set_last_Java_pc(pc);
  ContinuationHelper::set_anchor_pd(anchor, sp);

  assert(thread->has_last_Java_frame(), "");
  assert(thread->last_frame().cb() != nullptr, "");
}
#endif // ASSERT

static void set_anchor_to_entry(JavaThread* thread, ContinuationEntry* entry) {
  JavaFrameAnchor* anchor = thread->frame_anchor();
  anchor->set_last_Java_sp(entry->entry_sp());
  anchor->set_last_Java_pc(entry->entry_pc());
  ContinuationHelper::set_anchor_to_entry_pd(anchor, entry);

  assert(thread->has_last_Java_frame(), "");
  assert(thread->last_frame().cb() != nullptr, "");
}

#if CONT_JFR
class FreezeThawJfrInfo : public StackObj {
  short _e_size;
  short _e_num_interpreted_frames;
 public:

  FreezeThawJfrInfo() : _e_size(0), _e_num_interpreted_frames(0) {}
  inline void record_interpreted_frame() { _e_num_interpreted_frames++; }
  inline void record_size_copied(int size) { _e_size += size << LogBytesPerWord; }
  template<typename Event> void post_jfr_event(Event *e, oop continuation, JavaThread* jt);
};

template<typename Event> void FreezeThawJfrInfo::post_jfr_event(Event* e, oop continuation, JavaThread* jt) {
  if (e->should_commit()) {
    log_develop_trace(continuations)("JFR event: iframes: %d size: %d", _e_num_interpreted_frames, _e_size);
    e->set_carrierThread(JFR_JVM_THREAD_ID(jt));
    e->set_continuationClass(continuation->klass());
    e->set_interpretedFrames(_e_num_interpreted_frames);
    e->set_size(_e_size);
    e->commit();
  }
}
#endif // CONT_JFR

/////////////// FREEZE ////

class FreezeBase : public StackObj {
protected:
  JavaThread* const _thread;
  ContinuationWrapper& _cont;
  bool _barriers; // only set when we allocate a chunk
  const bool _preempt; // used only on the slow path
  const intptr_t * const _frame_sp; // Top frame sp for this freeze

  intptr_t* _bottom_address;

  int _freeze_size; // total size of all frames plus metadata in words.
  int _total_align_size;

  intptr_t* _cont_stack_top;
  intptr_t* _cont_stack_bottom;

  CONT_JFR_ONLY(FreezeThawJfrInfo _jfr_info;)

#ifdef ASSERT
  intptr_t* _orig_chunk_sp;
  int _fast_freeze_size;
  bool _empty;
#endif

  JvmtiSampledObjectAllocEventCollector* _jvmti_event_collector;

  NOT_PRODUCT(int _frames;)
  DEBUG_ONLY(intptr_t* _last_write;)

  inline FreezeBase(JavaThread* thread, ContinuationWrapper& cont, intptr_t* sp);

public:
  NOINLINE freeze_result freeze_slow();
  void freeze_fast_existing_chunk();

  CONT_JFR_ONLY(FreezeThawJfrInfo& jfr_info() { return _jfr_info; })
  void set_jvmti_event_collector(JvmtiSampledObjectAllocEventCollector* jsoaec) { _jvmti_event_collector = jsoaec; }

  inline int size_if_fast_freeze_available();

#ifdef ASSERT
  bool interpreted_native_or_deoptimized_on_stack();
#endif

protected:
  inline void init_rest();
  void throw_stack_overflow_on_humongous_chunk();

  // fast path
  inline void copy_to_chunk(intptr_t* from, intptr_t* to, int size);
  inline void unwind_frames();
  inline void patch_stack_pd(intptr_t* frame_sp, intptr_t* heap_sp);

  // slow path
  virtual stackChunkOop allocate_chunk_slow(size_t stack_size) = 0;

  int cont_size() { return pointer_delta_as_int(_cont_stack_bottom, _cont_stack_top); }

private:
  // slow path
  frame freeze_start_frame();
  frame freeze_start_frame_safepoint_stub(frame f);
  NOINLINE freeze_result recurse_freeze(frame& f, frame& caller, int callee_argsize, bool callee_interpreted, bool top);
  inline frame freeze_start_frame_yield_stub(frame f);
  template<typename FKind>
  inline freeze_result recurse_freeze_java_frame(const frame& f, frame& caller, int fsize, int argsize);
  inline void before_freeze_java_frame(const frame& f, const frame& caller, int fsize, int argsize, bool is_bottom_frame);
  inline void after_freeze_java_frame(const frame& hf, bool is_bottom_frame);
  freeze_result finalize_freeze(const frame& callee, frame& caller, int argsize);
  void patch(const frame& f, frame& hf, const frame& caller, bool is_bottom_frame);
  NOINLINE freeze_result recurse_freeze_interpreted_frame(frame& f, frame& caller, int callee_argsize, bool callee_interpreted);
  freeze_result recurse_freeze_compiled_frame(frame& f, frame& caller, int callee_argsize, bool callee_interpreted);
  NOINLINE freeze_result recurse_freeze_stub_frame(frame& f, frame& caller);
  NOINLINE void finish_freeze(const frame& f, const frame& top);

  inline bool stack_overflow();

  static frame sender(const frame& f) { return f.is_interpreted_frame() ? sender<ContinuationHelper::InterpretedFrame>(f)
                                                                        : sender<ContinuationHelper::NonInterpretedUnknownFrame>(f); }
  template<typename FKind> static inline frame sender(const frame& f);
  template<typename FKind> frame new_heap_frame(frame& f, frame& caller);
  inline void set_top_frame_metadata_pd(const frame& hf);
  inline void patch_pd(frame& callee, const frame& caller);
  void adjust_interpreted_frame_unextended_sp(frame& f);
  static inline void relativize_interpreted_frame_metadata(const frame& f, const frame& hf);

protected:
  void freeze_fast_copy(stackChunkOop chunk, int chunk_start_sp CONT_JFR_ONLY(COMMA bool chunk_is_allocated));
  bool freeze_fast_new_chunk(stackChunkOop chunk);

#ifdef ASSERT
  bool is_empty(stackChunkOop chunk) {
    // during freeze, the chunk is in an intermediate state (after setting the chunk's argsize but before setting its
    // ultimate sp) so we use this instead of stackChunkOopDesc::is_empty
    return chunk->sp() >= chunk->stack_size() - chunk->argsize() - frame::metadata_words_at_top;
  }
#endif
};

template <typename ConfigT>
class Freeze : public FreezeBase {
private:
  stackChunkOop allocate_chunk(size_t stack_size);

public:
  inline Freeze(JavaThread* thread, ContinuationWrapper& cont, intptr_t* frame_sp)
    : FreezeBase(thread, cont, frame_sp) {}

  freeze_result try_freeze_fast();

protected:
  virtual stackChunkOop allocate_chunk_slow(size_t stack_size) override { return allocate_chunk(stack_size); }
};

FreezeBase::FreezeBase(JavaThread* thread, ContinuationWrapper& cont, intptr_t* frame_sp) :
    _thread(thread), _cont(cont), _barriers(false), _preempt(false), _frame_sp(frame_sp) {
  DEBUG_ONLY(_jvmti_event_collector = nullptr;)

  assert(_thread != nullptr, "");
  assert(_thread->last_continuation()->entry_sp() == _cont.entrySP(), "");

  DEBUG_ONLY(_cont.entry()->verify_cookie();)

  assert(!Interpreter::contains(_cont.entryPC()), "");

  _bottom_address = _cont.entrySP() - _cont.entry_frame_extension();
#ifdef _LP64
  if (((intptr_t)_bottom_address & 0xf) != 0) {
    _bottom_address--;
  }
  assert(is_aligned(_bottom_address, frame::frame_alignment), "");
#endif

  log_develop_trace(continuations)("bottom_address: " INTPTR_FORMAT " entrySP: " INTPTR_FORMAT " argsize: " PTR_FORMAT,
                p2i(_bottom_address), p2i(_cont.entrySP()), (_cont.entrySP() - _bottom_address) << LogBytesPerWord);
  assert(_bottom_address != nullptr, "");
  assert(_bottom_address <= _cont.entrySP(), "");
  DEBUG_ONLY(_last_write = nullptr;)

  assert(_cont.chunk_invariant(), "");
  assert(!Interpreter::contains(_cont.entryPC()), "");
#if !defined(PPC64) || defined(ZERO)
  static const int doYield_stub_frame_size = frame::metadata_words;
#else
  static const int doYield_stub_frame_size = frame::native_abi_reg_args_size >> LogBytesPerWord;
#endif
  assert(SharedRuntime::cont_doYield_stub()->frame_size() == doYield_stub_frame_size, "");

  // properties of the continuation on the stack; all sizes are in words
  _cont_stack_top    = frame_sp + doYield_stub_frame_size; // we don't freeze the doYield stub frame
  _cont_stack_bottom = _cont.entrySP() + (_cont.argsize() == 0 ? frame::metadata_words_at_top : 0)
      - ContinuationHelper::frame_align_words(_cont.argsize()); // see alignment in thaw

  log_develop_trace(continuations)("freeze size: %d argsize: %d top: " INTPTR_FORMAT " bottom: " INTPTR_FORMAT,
    cont_size(), _cont.argsize(), p2i(_cont_stack_top), p2i(_cont_stack_bottom));
  assert(cont_size() > 0, "");
}

void FreezeBase::init_rest() { // we want to postpone some initialization after chunk handling
  _freeze_size = 0;
  _total_align_size = 0;
  NOT_PRODUCT(_frames = 0;)
}

void FreezeBase::copy_to_chunk(intptr_t* from, intptr_t* to, int size) {
  stackChunkOop chunk = _cont.tail();
  chunk->copy_from_stack_to_chunk(from, to, size);
  CONT_JFR_ONLY(_jfr_info.record_size_copied(size);)

#ifdef ASSERT
  if (_last_write != nullptr) {
    assert(_last_write == to + size, "Missed a spot: _last_write: " INTPTR_FORMAT " to+size: " INTPTR_FORMAT
        " stack_size: %d _last_write offset: " PTR_FORMAT " to+size: " PTR_FORMAT, p2i(_last_write), p2i(to+size),
        chunk->stack_size(), _last_write-chunk->start_address(), to+size-chunk->start_address());
    _last_write = to;
  }
#endif
}

// Called _after_ the last possible safepoint during the freeze operation (chunk allocation)
void FreezeBase::unwind_frames() {
  ContinuationEntry* entry = _cont.entry();
  entry->flush_stack_processing(_thread);
  set_anchor_to_entry(_thread, entry);
}

template <typename ConfigT>
freeze_result Freeze<ConfigT>::try_freeze_fast() {
  assert(_thread->thread_state() == _thread_in_vm, "");
  assert(_thread->cont_fastpath(), "");

  DEBUG_ONLY(_fast_freeze_size = size_if_fast_freeze_available();)
  assert(_fast_freeze_size == 0, "");

  stackChunkOop chunk = allocate_chunk(cont_size() + frame::metadata_words);
  if (freeze_fast_new_chunk(chunk)) {
    return freeze_ok;
  }
  if (_thread->has_pending_exception()) {
    return freeze_exception;
  }

  // TODO R REMOVE when deopt change is fixed
  assert(!_thread->cont_fastpath() || _barriers, "");
  log_develop_trace(continuations)("-- RETRYING SLOW --");
  return freeze_slow();
}

// Returns size needed if the continuation fits, otherwise 0.
int FreezeBase::size_if_fast_freeze_available() {
  stackChunkOop chunk = _cont.tail();
  if (chunk == nullptr || chunk->is_gc_mode() || chunk->requires_barriers() || chunk->has_mixed_frames()) {
    log_develop_trace(continuations)("chunk available %s", chunk == nullptr ? "no chunk" : "chunk requires barriers");
    return 0;
  }

  int total_size_needed = cont_size();
  const int chunk_sp = chunk->sp();

  // argsize can be nonzero if we have a caller, but the caller could be in a non-empty parent chunk,
  // so we subtract it only if we overlap with the caller, i.e. the current chunk isn't empty.
  // Consider leaving the chunk's argsize set when emptying it and removing the following branch,
  // although that would require changing stackChunkOopDesc::is_empty
  if (chunk_sp < chunk->stack_size()) {
    total_size_needed -= _cont.argsize() + frame::metadata_words_at_top;
  }

  int chunk_free_room = chunk_sp - frame::metadata_words_at_bottom;
  bool available = chunk_free_room >= total_size_needed;
  log_develop_trace(continuations)("chunk available: %s size: %d argsize: %d top: " INTPTR_FORMAT " bottom: " INTPTR_FORMAT,
    available ? "yes" : "no" , total_size_needed, _cont.argsize(), p2i(_cont_stack_top), p2i(_cont_stack_bottom));
  return available ? total_size_needed : 0;
}

void FreezeBase::freeze_fast_existing_chunk() {
  stackChunkOop chunk = _cont.tail();
  DEBUG_ONLY(_orig_chunk_sp = chunk->sp_address();)

  DEBUG_ONLY(_fast_freeze_size = size_if_fast_freeze_available();)
  assert(_fast_freeze_size > 0, "");

  if (chunk->sp() < chunk->stack_size()) { // we are copying into a non-empty chunk
    DEBUG_ONLY(_empty = false;)
    assert(chunk->sp() < (chunk->stack_size() - chunk->argsize()), "");
#ifdef ASSERT
    {
      intptr_t* retaddr_slot = (chunk->sp_address()
                                - frame::sender_sp_ret_address_offset());
      assert(ContinuationHelper::return_address_at(retaddr_slot) == chunk->pc(),
             "unexpected saved return address");
    }
#endif

    // the chunk's sp before the freeze, adjusted to point beyond the stack-passed arguments in the topmost frame
    // we overlap; we'll overwrite the chunk's top frame's callee arguments
    const int chunk_start_sp = chunk->sp() + _cont.argsize() + frame::metadata_words_at_top;
    assert(chunk_start_sp <= chunk->stack_size(), "sp not pointing into stack");

    // increase max_size by what we're freezing minus the overlap
    chunk->set_max_thawing_size(chunk->max_thawing_size() + cont_size() - _cont.argsize() - frame::metadata_words_at_top);

    intptr_t* const bottom_sp = _cont_stack_bottom - _cont.argsize() - frame::metadata_words_at_top;
    assert(bottom_sp == _bottom_address, "");
    // Because the chunk isn't empty, we know there's a caller in the chunk, therefore the bottom-most frame
    // should have a return barrier (installed back when we thawed it).
#ifdef ASSERT
    {
      intptr_t* retaddr_slot = (bottom_sp
                                - frame::sender_sp_ret_address_offset());
      assert(ContinuationHelper::return_address_at(retaddr_slot)
             == StubRoutines::cont_returnBarrier(),
             "should be the continuation return barrier");
    }
#endif
    // We copy the fp from the chunk back to the stack because it contains some caller data,
    // including, possibly, an oop that might have gone stale since we thawed.
    patch_stack_pd(bottom_sp, chunk->sp_address());
    // we don't patch the return pc at this time, so as not to make the stack unwalkable for async walks

    freeze_fast_copy(chunk, chunk_start_sp CONT_JFR_ONLY(COMMA false));
  } else { // the chunk is empty
    DEBUG_ONLY(_empty = true;)
    const int chunk_start_sp = chunk->sp();

    assert(chunk_start_sp == chunk->stack_size(), "");

    chunk->set_max_thawing_size(cont_size());
    chunk->set_argsize(_cont.argsize());

    freeze_fast_copy(chunk, chunk_start_sp CONT_JFR_ONLY(COMMA false));
  }
}

bool FreezeBase::freeze_fast_new_chunk(stackChunkOop chunk) {
  DEBUG_ONLY(_empty = true;)

  // Install new chunk
  _cont.set_tail(chunk);

  if (UNLIKELY(chunk == nullptr || !_thread->cont_fastpath() || _barriers)) { // OOME/probably humongous
    log_develop_trace(continuations)("Retrying slow. Barriers: %d", _barriers);
    return false;
  }

  chunk->set_max_thawing_size(cont_size());
  chunk->set_argsize(_cont.argsize());

  // in a fresh chunk, we freeze *with* the bottom-most frame's stack arguments.
  // They'll then be stored twice: in the chunk and in the parent chunk's top frame
  const int chunk_start_sp = cont_size() + frame::metadata_words;
  assert(chunk_start_sp == chunk->stack_size(), "");

  DEBUG_ONLY(_orig_chunk_sp = chunk->start_address() + chunk_start_sp;)

  freeze_fast_copy(chunk, chunk_start_sp CONT_JFR_ONLY(COMMA true));

  return true;
}

void FreezeBase::freeze_fast_copy(stackChunkOop chunk, int chunk_start_sp CONT_JFR_ONLY(COMMA bool chunk_is_allocated)) {
  assert(chunk != nullptr, "");
  assert(!chunk->has_mixed_frames(), "");
  assert(!chunk->is_gc_mode(), "");
  assert(!chunk->has_bitmap(), "");
  assert(!chunk->requires_barriers(), "");
  assert(chunk == _cont.tail(), "");

  // We unwind frames after the last safepoint so that the GC will have found the oops in the frames, but before
  // writing into the chunk. This is so that an asynchronous stack walk (not at a safepoint) that suspends us here
  // will either see no continuation on the stack, or a consistent chunk.
  unwind_frames();

  log_develop_trace(continuations)("freeze_fast start: chunk " INTPTR_FORMAT " size: %d orig sp: %d argsize: %d",
    p2i((oopDesc*)chunk), chunk->stack_size(), chunk_start_sp, _cont.argsize());
  assert(chunk_start_sp <= chunk->stack_size(), "");
  assert(chunk_start_sp >= cont_size(), "no room in the chunk");

  const int chunk_new_sp = chunk_start_sp - cont_size(); // the chunk's new sp, after freeze
  assert(!(_fast_freeze_size > 0) || _orig_chunk_sp - (chunk->start_address() + chunk_new_sp) == _fast_freeze_size, "");

  intptr_t* chunk_top = chunk->start_address() + chunk_new_sp;
#ifdef ASSERT
  if (!_empty) {
    intptr_t* retaddr_slot = (_orig_chunk_sp
                              - frame::sender_sp_ret_address_offset());
    assert(ContinuationHelper::return_address_at(retaddr_slot) == chunk->pc(),
           "unexpected saved return address");
  }
#endif

  log_develop_trace(continuations)("freeze_fast start: " INTPTR_FORMAT " sp: %d chunk_top: " INTPTR_FORMAT,
                              p2i(chunk->start_address()), chunk_new_sp, p2i(chunk_top));
  intptr_t* from = _cont_stack_top - frame::metadata_words_at_bottom;
  intptr_t* to   = chunk_top - frame::metadata_words_at_bottom;
  copy_to_chunk(from, to, cont_size() + frame::metadata_words_at_bottom);
  // Because we're not patched yet, the chunk is now in a bad state

  // patch return pc of the bottom-most frozen frame (now in the chunk)
  // with the actual caller's return address
  intptr_t* chunk_bottom_retaddr_slot = (chunk_top + cont_size()
                                         - _cont.argsize()
                                         - frame::metadata_words_at_top
                                         - frame::sender_sp_ret_address_offset());
#ifdef ASSERT
  if (!_empty) {
    assert(ContinuationHelper::return_address_at(chunk_bottom_retaddr_slot)
           == StubRoutines::cont_returnBarrier(),
           "should be the continuation return barrier");
  }
#endif
  ContinuationHelper::patch_return_address_at(chunk_bottom_retaddr_slot,
                                              chunk->pc());

  // We're always writing to a young chunk, so the GC can't see it until the next safepoint.
  chunk->set_sp(chunk_new_sp);
  // set chunk->pc to the return address of the topmost frame in the chunk
  chunk->set_pc(ContinuationHelper::return_address_at(
                  _cont_stack_top - frame::sender_sp_ret_address_offset()));

  _cont.write();

  log_develop_trace(continuations)("FREEZE CHUNK #" INTPTR_FORMAT " (young)", _cont.hash());
  LogTarget(Trace, continuations) lt;
  if (lt.develop_is_enabled()) {
    LogStream ls(lt);
    chunk->print_on(true, &ls);
  }

  // Verification
  assert(_cont.chunk_invariant(), "");
  chunk->verify();

#if CONT_JFR
  EventContinuationFreezeFast e;
  if (e.should_commit()) {
    e.set_id(cast_from_oop<u8>(chunk));
    DEBUG_ONLY(e.set_allocate(chunk_is_allocated);)
    e.set_size(cont_size() << LogBytesPerWord);
    e.commit();
  }
#endif
}

NOINLINE freeze_result FreezeBase::freeze_slow() {
#ifdef ASSERT
  ResourceMark rm;
#endif

  log_develop_trace(continuations)("freeze_slow  #" INTPTR_FORMAT, _cont.hash());
  assert(_thread->thread_state() == _thread_in_vm || _thread->thread_state() == _thread_blocked, "");

#if CONT_JFR
  EventContinuationFreezeSlow e;
  if (e.should_commit()) {
    e.set_id(cast_from_oop<u8>(_cont.continuation()));
    e.commit();
  }
#endif

  init_rest();

  HandleMark hm(Thread::current());

  frame f = freeze_start_frame();

  LogTarget(Debug, continuations) lt;
  if (lt.develop_is_enabled()) {
    LogStream ls(lt);
    f.print_on(&ls);
  }

  frame caller; // the frozen caller in the chunk
  freeze_result res = recurse_freeze(f, caller, 0, false, true);

  if (res == freeze_ok) {
    finish_freeze(f, caller);
    _cont.write();
  }

  return res;
}

frame FreezeBase::freeze_start_frame() {
  frame f = _thread->last_frame();
  if (LIKELY(!_preempt)) {
    return freeze_start_frame_yield_stub(f);
  } else {
    return freeze_start_frame_safepoint_stub(f);
  }
}

frame FreezeBase::freeze_start_frame_yield_stub(frame f) {
  assert(SharedRuntime::cont_doYield_stub()->contains(f.pc()), "must be");
  f = sender<ContinuationHelper::NonInterpretedUnknownFrame>(f);
  assert(Continuation::is_frame_in_continuation(_thread->last_continuation(), f), "");
  return f;
}

frame FreezeBase::freeze_start_frame_safepoint_stub(frame f) {
#if (defined(X86) || defined(AARCH64) || defined(RISCV64)) && !defined(ZERO)
  f.set_fp(f.real_fp()); // f.set_fp(*Frame::callee_link_address(f)); // ????
#else
  Unimplemented();
#endif
  if (!Interpreter::contains(f.pc())) {
    assert(ContinuationHelper::Frame::is_stub(f.cb()), "must be");
    assert(f.oop_map() != nullptr, "must be");

    if (Interpreter::contains(ContinuationHelper::StubFrame::return_pc(f))) {
      f = sender<ContinuationHelper::StubFrame>(f); // Safepoint stub in interpreter
    }
  }
  assert(Continuation::is_frame_in_continuation(_thread->last_continuation(), f), "");
  return f;
}

// The parameter callee_argsize includes metadata that has to be part of caller/callee overlap.
NOINLINE freeze_result FreezeBase::recurse_freeze(frame& f, frame& caller, int callee_argsize, bool callee_interpreted, bool top) {
  assert(f.unextended_sp() < _bottom_address, ""); // see recurse_freeze_java_frame
  assert(f.is_interpreted_frame() || ((top && _preempt) == ContinuationHelper::Frame::is_stub(f.cb())), "");

  if (stack_overflow()) {
    return freeze_exception;
  }

  if (f.is_compiled_frame()) {
    if (UNLIKELY(f.oop_map() == nullptr)) {
      // special native frame
      return freeze_pinned_native;
    }
    return recurse_freeze_compiled_frame(f, caller, callee_argsize, callee_interpreted);
  } else if (f.is_interpreted_frame()) {
    assert((_preempt && top) || !f.interpreter_frame_method()->is_native(), "");
    if (_preempt && top && f.interpreter_frame_method()->is_native()) {
      // int native entry
      return freeze_pinned_native;
    }

    return recurse_freeze_interpreted_frame(f, caller, callee_argsize, callee_interpreted);
  } else if (_preempt && top && ContinuationHelper::Frame::is_stub(f.cb())) {
    return recurse_freeze_stub_frame(f, caller);
  } else {
    return freeze_pinned_native;
  }
}

// The parameter callee_argsize includes metadata that has to be part of caller/callee overlap.
// See also StackChunkFrameStream<frame_kind>::frame_size()
template<typename FKind>
inline freeze_result FreezeBase::recurse_freeze_java_frame(const frame& f, frame& caller, int fsize, int argsize) {
  assert(FKind::is_instance(f), "");

  assert(fsize > 0, "");
  assert(argsize >= 0, "");
  _freeze_size += fsize;
  NOT_PRODUCT(_frames++;)

  assert(FKind::frame_bottom(f) <= _bottom_address, "");

  // We don't use FKind::frame_bottom(f) == _bottom_address because on x64 there's sometimes an extra word between
  // enterSpecial and an interpreted frame
  if (FKind::frame_bottom(f) >= _bottom_address - 1) {
    return finalize_freeze(f, caller, argsize); // recursion end
  } else {
    frame senderf = sender<FKind>(f);
    assert(FKind::interpreted || senderf.sp() == senderf.unextended_sp(), "");
    freeze_result result = recurse_freeze(senderf, caller, argsize, FKind::interpreted, false); // recursive call
    return result;
  }
}

inline void FreezeBase::before_freeze_java_frame(const frame& f, const frame& caller, int fsize, int argsize, bool is_bottom_frame) {
  LogTarget(Trace, continuations) lt;
  if (lt.develop_is_enabled()) {
    LogStream ls(lt);
    ls.print_cr("======== FREEZING FRAME interpreted: %d bottom: %d", f.is_interpreted_frame(), is_bottom_frame);
    ls.print_cr("fsize: %d argsize: %d", fsize, argsize);
    f.print_value_on(&ls, nullptr);
  }
  assert(caller.is_interpreted_frame() == Interpreter::contains(caller.pc()), "");
}

inline void FreezeBase::after_freeze_java_frame(const frame& hf, bool is_bottom_frame) {
  LogTarget(Trace, continuations) lt;
  if (lt.develop_is_enabled()) {
    LogStream ls(lt);
    DEBUG_ONLY(hf.print_value_on(&ls, nullptr);)
    assert(hf.is_heap_frame(), "should be");
    DEBUG_ONLY(print_frame_layout(hf, false, &ls);)
    if (is_bottom_frame) {
      ls.print_cr("bottom h-frame:");
      hf.print_on(&ls);
    }
  }
}

// The parameter argsize_md includes metadata that has to be part of caller/callee overlap.
// See also StackChunkFrameStream<frame_kind>::frame_size()
freeze_result FreezeBase::finalize_freeze(const frame& callee, frame& caller, int argsize_md) {
  int argsize = argsize_md - frame::metadata_words_at_top;
  assert(callee.is_interpreted_frame()
    || callee.cb()->as_nmethod()->is_osr_method()
    || argsize == _cont.argsize(), "argsize: %d cont.argsize: %d", argsize, _cont.argsize());
  log_develop_trace(continuations)("bottom: " INTPTR_FORMAT " count %d size: %d argsize: %d",
    p2i(_bottom_address), _frames, _freeze_size << LogBytesPerWord, argsize);

  LogTarget(Trace, continuations) lt;

#ifdef ASSERT
  bool empty = _cont.is_empty();
  log_develop_trace(continuations)("empty: %d", empty);
#endif

  stackChunkOop chunk = _cont.tail();

  assert(chunk == nullptr || (chunk->max_thawing_size() == 0) == chunk->is_empty(), "");

  _freeze_size += frame::metadata_words; // for top frame's metadata

  int overlap = 0; // the args overlap the caller -- if there is one in this chunk and is of the same kind
  int unextended_sp = -1;
  if (chunk != nullptr) {
    unextended_sp = chunk->sp();
    if (!chunk->is_empty()) {
      StackChunkFrameStream<ChunkFrames::Mixed> last(chunk);
      unextended_sp = chunk->to_offset(StackChunkFrameStream<ChunkFrames::Mixed>(chunk).unextended_sp());
      bool top_interpreted = Interpreter::contains(chunk->pc());
      if (callee.is_interpreted_frame() == top_interpreted) {
        overlap = argsize_md;
      }
    }
  }

  log_develop_trace(continuations)("finalize _size: %d overlap: %d unextended_sp: %d", _freeze_size, overlap, unextended_sp);

  _freeze_size -= overlap;
  assert(_freeze_size >= 0, "");

  assert(chunk == nullptr || chunk->is_empty()
          || unextended_sp == chunk->to_offset(StackChunkFrameStream<ChunkFrames::Mixed>(chunk).unextended_sp()), "");
  assert(chunk != nullptr || unextended_sp < _freeze_size, "");

  // _barriers can be set to true by an allocation in freeze_fast, in which case the chunk is available
  bool allocated_old_in_freeze_fast = _barriers;
  assert(!allocated_old_in_freeze_fast || (unextended_sp >= _freeze_size && chunk->is_empty()),
    "Chunk allocated in freeze_fast is of insufficient size "
    "unextended_sp: %d size: %d is_empty: %d", unextended_sp, _freeze_size, chunk->is_empty());
  assert(!allocated_old_in_freeze_fast || (!UseZGC && !UseG1GC), "Unexpected allocation");

  DEBUG_ONLY(bool empty_chunk = true);
  if (unextended_sp < _freeze_size || chunk->is_gc_mode() || (!allocated_old_in_freeze_fast && chunk->requires_barriers())) {
    // ALLOCATE NEW CHUNK

    if (lt.develop_is_enabled()) {
      LogStream ls(lt);
      if (chunk == nullptr) {
        ls.print_cr("no chunk");
      } else {
        ls.print_cr("chunk barriers: %d _size: %d free size: %d",
          chunk->requires_barriers(), _freeze_size, chunk->sp() - frame::metadata_words);
        chunk->print_on(&ls);
      }
    }

    _freeze_size += overlap; // we're allocating a new chunk, so no overlap
    // overlap = 0;

    chunk = allocate_chunk_slow(_freeze_size);
    if (chunk == nullptr) {
      return freeze_exception;
    }

    // Install new chunk
    _cont.set_tail(chunk);

    int sp = chunk->stack_size() - argsize_md;
    chunk->set_sp(sp);
    chunk->set_argsize(argsize);
    assert(is_empty(chunk), "");
  } else {
    // REUSE EXISTING CHUNK
    log_develop_trace(continuations)("Reusing chunk mixed: %d empty: %d", chunk->has_mixed_frames(), chunk->is_empty());
    if (chunk->is_empty()) {
      int sp = chunk->stack_size() - argsize_md;
      chunk->set_sp(sp);
      chunk->set_argsize(argsize);
      _freeze_size += overlap;
      assert(chunk->max_thawing_size() == 0, "");
    } DEBUG_ONLY(else empty_chunk = false;)
  }
  assert(!chunk->is_gc_mode(), "");
  assert(!chunk->has_bitmap(), "");
  chunk->set_has_mixed_frames(true);

  assert(chunk->requires_barriers() == _barriers, "");
  assert(!_barriers || is_empty(chunk), "");

  assert(!is_empty(chunk) || StackChunkFrameStream<ChunkFrames::Mixed>(chunk).is_done(), "");
  assert(!is_empty(chunk) || StackChunkFrameStream<ChunkFrames::Mixed>(chunk).to_frame().is_empty(), "");

  // We unwind frames after the last safepoint so that the GC will have found the oops in the frames, but before
  // writing into the chunk. This is so that an asynchronous stack walk (not at a safepoint) that suspends us here
  // will either see no continuation or a consistent chunk.
  unwind_frames();

  chunk->set_max_thawing_size(chunk->max_thawing_size() + _freeze_size - frame::metadata_words);

  if (lt.develop_is_enabled()) {
    LogStream ls(lt);
    ls.print_cr("top chunk:");
    chunk->print_on(&ls);
  }

  // The topmost existing frame in the chunk; or an empty frame if the chunk is empty
  caller = StackChunkFrameStream<ChunkFrames::Mixed>(chunk).to_frame();

  DEBUG_ONLY(_last_write = caller.unextended_sp() + (empty_chunk ? argsize_md : overlap);)

  assert(chunk->is_in_chunk(_last_write - _freeze_size),
    "last_write-size: " INTPTR_FORMAT " start: " INTPTR_FORMAT, p2i(_last_write-_freeze_size), p2i(chunk->start_address()));
#ifdef ASSERT
  if (lt.develop_is_enabled()) {
    LogStream ls(lt);
    ls.print_cr("top hframe before (freeze):");
    assert(caller.is_heap_frame(), "should be");
    caller.print_on(&ls);
  }

  assert(!empty || Continuation::is_continuation_entry_frame(callee, nullptr), "");

  frame entry = sender(callee);

  assert(Continuation::is_return_barrier_entry(entry.pc()) || Continuation::is_continuation_enterSpecial(entry), "");
  assert(callee.is_interpreted_frame() || entry.sp() == entry.unextended_sp(), "");
#endif

  return freeze_ok_bottom;
}

void FreezeBase::patch(const frame& f, frame& hf, const frame& caller, bool is_bottom_frame) {
  if (is_bottom_frame) {
    // If we're the bottom frame, we need to replace the return barrier with the real
    // caller's pc.
    address last_pc = caller.pc();
    assert((last_pc == nullptr) == is_empty(_cont.tail()), "");
    ContinuationHelper::Frame::patch_pc(caller, last_pc);
  } else {
    assert(!caller.is_empty(), "");
  }

  patch_pd(hf, caller);

  if (f.is_interpreted_frame()) {
    assert(hf.is_heap_frame(), "should be");
    ContinuationHelper::InterpretedFrame::patch_sender_sp(hf, caller);
  }

#ifdef ASSERT
  if (hf.is_compiled_frame()) {
    if (f.is_deoptimized_frame()) { // TODO DEOPT: long term solution: unroll on freeze and patch pc
      log_develop_trace(continuations)("Freezing deoptimized frame");
      assert(f.cb()->as_compiled_method()->is_deopt_pc(f.raw_pc()), "");
      assert(f.cb()->as_compiled_method()->is_deopt_pc(ContinuationHelper::Frame::real_pc(f)), "");
    }
  }
#endif
}

#ifdef ASSERT
static void verify_frame_top(const frame& f, intptr_t* top) {
  ResourceMark rm;
  InterpreterOopMap mask;
  f.interpreted_frame_oop_map(&mask);
  assert(top <= ContinuationHelper::InterpretedFrame::frame_top(f, &mask),
         "frame_top: " INTPTR_FORMAT " Interpreted::frame_top: " INTPTR_FORMAT,
           p2i(top), p2i(ContinuationHelper::InterpretedFrame::frame_top(f, &mask)));
}
#endif // ASSERT

// The parameter callee_argsize includes metadata that has to be part of caller/callee overlap.
// See also StackChunkFrameStream<frame_kind>::frame_size()
NOINLINE freeze_result FreezeBase::recurse_freeze_interpreted_frame(frame& f, frame& caller,
                                                                    int callee_argsize /* incl. metadata */,
                                                                    bool callee_interpreted) {
  adjust_interpreted_frame_unextended_sp(f);

  // The frame's top never includes the stack arguments to the callee
  intptr_t* const stack_frame_top = ContinuationHelper::InterpretedFrame::frame_top(f, callee_argsize, callee_interpreted);
  intptr_t* const stack_frame_bottom = ContinuationHelper::InterpretedFrame::frame_bottom(f);
  const int fsize = pointer_delta_as_int(stack_frame_bottom, stack_frame_top);

  DEBUG_ONLY(verify_frame_top(f, stack_frame_top));

  Method* frame_method = ContinuationHelper::Frame::frame_method(f);
  // including metadata between f and its args
  const int argsize = ContinuationHelper::InterpretedFrame::stack_argsize(f) + frame::metadata_words_at_top;

  log_develop_trace(continuations)("recurse_freeze_interpreted_frame %s _size: %d fsize: %d argsize: %d",
    frame_method->name_and_sig_as_C_string(), _freeze_size, fsize, argsize);
  // we'd rather not yield inside methods annotated with @JvmtiMountTransition
  assert(!ContinuationHelper::Frame::frame_method(f)->jvmti_mount_transition(), "");

  freeze_result result = recurse_freeze_java_frame<ContinuationHelper::InterpretedFrame>(f, caller, fsize, argsize);
  if (UNLIKELY(result > freeze_ok_bottom)) {
    return result;
  }

  bool is_bottom_frame = result == freeze_ok_bottom;
  assert(!caller.is_empty() || is_bottom_frame, "");

  DEBUG_ONLY(before_freeze_java_frame(f, caller, fsize, 0, is_bottom_frame);)

  frame hf = new_heap_frame<ContinuationHelper::InterpretedFrame>(f, caller);
  _total_align_size += frame::align_wiggle; // add alignment room for internal interpreted frame alignment on AArch64/PPC64

  intptr_t* heap_frame_top = ContinuationHelper::InterpretedFrame::frame_top(hf, callee_argsize, callee_interpreted);
  intptr_t* heap_frame_bottom = ContinuationHelper::InterpretedFrame::frame_bottom(hf);
  assert(heap_frame_bottom == heap_frame_top + fsize, "");

  // Some architectures (like AArch64/PPC64/RISC-V) add padding between the locals and the fixed_frame to keep the fp 16-byte-aligned.
  // On those architectures we freeze the padding in order to keep the same fp-relative offsets in the fixed_frame.
  copy_to_chunk(stack_frame_top, heap_frame_top, fsize);
  assert(!is_bottom_frame || !caller.is_interpreted_frame() || (heap_frame_top + fsize) == (caller.unextended_sp() + argsize), "");

  relativize_interpreted_frame_metadata(f, hf);

  patch(f, hf, caller, is_bottom_frame);

  CONT_JFR_ONLY(_jfr_info.record_interpreted_frame();)
  DEBUG_ONLY(after_freeze_java_frame(hf, is_bottom_frame);)
  caller = hf;

  // Mark frame_method's GC epoch for class redefinition on_stack calculation.
  frame_method->record_gc_epoch();

  return freeze_ok;
}

// The parameter callee_argsize includes metadata that has to be part of caller/callee overlap.
// See also StackChunkFrameStream<frame_kind>::frame_size()
freeze_result FreezeBase::recurse_freeze_compiled_frame(frame& f, frame& caller,
                                                        int callee_argsize /* incl. metadata */,
                                                        bool callee_interpreted) {
  // The frame's top never includes the stack arguments to the callee
  intptr_t* const stack_frame_top = ContinuationHelper::CompiledFrame::frame_top(f, callee_argsize, callee_interpreted);
  intptr_t* const stack_frame_bottom = ContinuationHelper::CompiledFrame::frame_bottom(f);
  // including metadata between f and its stackargs
  const int argsize = ContinuationHelper::CompiledFrame::stack_argsize(f) + frame::metadata_words_at_top;
  const int fsize = pointer_delta_as_int(stack_frame_bottom + argsize, stack_frame_top);

  log_develop_trace(continuations)("recurse_freeze_compiled_frame %s _size: %d fsize: %d argsize: %d",
                             ContinuationHelper::Frame::frame_method(f) != nullptr ?
                             ContinuationHelper::Frame::frame_method(f)->name_and_sig_as_C_string() : "",
                             _freeze_size, fsize, argsize);
  // we'd rather not yield inside methods annotated with @JvmtiMountTransition
  assert(!ContinuationHelper::Frame::frame_method(f)->jvmti_mount_transition(), "");

  freeze_result result = recurse_freeze_java_frame<ContinuationHelper::CompiledFrame>(f, caller, fsize, argsize);
  if (UNLIKELY(result > freeze_ok_bottom)) {
    return result;
  }

  bool is_bottom_frame = result == freeze_ok_bottom;
  assert(!caller.is_empty() || is_bottom_frame, "");

  DEBUG_ONLY(before_freeze_java_frame(f, caller, fsize, argsize, is_bottom_frame);)

  frame hf = new_heap_frame<ContinuationHelper::CompiledFrame>(f, caller);

  intptr_t* heap_frame_top = ContinuationHelper::CompiledFrame::frame_top(hf, callee_argsize, callee_interpreted);

  copy_to_chunk(stack_frame_top, heap_frame_top, fsize);
  assert(!is_bottom_frame || !caller.is_compiled_frame() || (heap_frame_top + fsize) == (caller.unextended_sp() + argsize), "");

  if (caller.is_interpreted_frame()) {
    _total_align_size += frame::align_wiggle; // See Thaw::align
  }

  patch(f, hf, caller, is_bottom_frame);

  assert(is_bottom_frame || Interpreter::contains(ContinuationHelper::CompiledFrame::real_pc(caller)) == caller.is_interpreted_frame(), "");

  DEBUG_ONLY(after_freeze_java_frame(hf, is_bottom_frame);)
  caller = hf;
  return freeze_ok;
}

NOINLINE freeze_result FreezeBase::recurse_freeze_stub_frame(frame& f, frame& caller) {
  intptr_t* const stack_frame_top = ContinuationHelper::StubFrame::frame_top(f, 0, 0);
  const int fsize = f.cb()->frame_size();

  log_develop_trace(continuations)("recurse_freeze_stub_frame %s _size: %d fsize: %d :: " INTPTR_FORMAT " - " INTPTR_FORMAT,
    f.cb()->name(), _freeze_size, fsize, p2i(stack_frame_top), p2i(stack_frame_top+fsize));

  // recurse_freeze_java_frame and freeze inlined here because we need to use a full RegisterMap for lock ownership
  NOT_PRODUCT(_frames++;)
  _freeze_size += fsize;

  RegisterMap map(_cont.thread(),
                  RegisterMap::UpdateMap::include,
                  RegisterMap::ProcessFrames::skip,
                  RegisterMap::WalkContinuation::skip);
  map.set_include_argument_oops(false);
  ContinuationHelper::update_register_map<ContinuationHelper::StubFrame>(f, &map);
  f.oop_map()->update_register_map(&f, &map); // we have callee-save registers in this case
  frame senderf = sender<ContinuationHelper::StubFrame>(f);
  assert(senderf.unextended_sp() < _bottom_address - 1, "");
  assert(senderf.is_compiled_frame(), "");

  if (UNLIKELY(senderf.oop_map() == nullptr)) {
    // native frame
    return freeze_pinned_native;
  }

  freeze_result result = recurse_freeze_compiled_frame(senderf, caller, 0, 0); // This might be deoptimized
  if (UNLIKELY(result > freeze_ok_bottom)) {
    return result;
  }
  assert(result != freeze_ok_bottom, "");
  assert(!caller.is_interpreted_frame(), "");

  DEBUG_ONLY(before_freeze_java_frame(f, caller, fsize, 0, false);)
  frame hf = new_heap_frame<ContinuationHelper::StubFrame>(f, caller);
  intptr_t* heap_frame_top = ContinuationHelper::StubFrame::frame_top(hf, 0, 0);
  copy_to_chunk(stack_frame_top, heap_frame_top, fsize);
  DEBUG_ONLY(after_freeze_java_frame(hf, false);)

  caller = hf;
  return freeze_ok;
}

NOINLINE void FreezeBase::finish_freeze(const frame& f, const frame& top) {
  stackChunkOop chunk = _cont.tail();
  assert(chunk->to_offset(top.sp()) <= chunk->sp(), "");

  LogTarget(Trace, continuations) lt;
  if (lt.develop_is_enabled()) {
    LogStream ls(lt);
    assert(top.is_heap_frame(), "should be");
    top.print_on(&ls);
  }

  set_top_frame_metadata_pd(top);

  chunk->set_sp(chunk->to_offset(top.sp()));
  chunk->set_pc(top.pc());

  chunk->set_max_thawing_size(chunk->max_thawing_size() + _total_align_size);

  // At this point the chunk is consistent

  if (UNLIKELY(_barriers)) {
    log_develop_trace(continuations)("do barriers on old chunk");
    // Serial and Parallel GC can allocate objects directly into the old generation.
    // Then we want to relativize the derived pointers eagerly so that
    // old chunks are all in GC mode.
    assert(!UseG1GC, "G1 can not deal with allocating outside of eden");
    assert(!UseZGC, "ZGC can not deal with allocating chunks visible to marking");
    if (UseShenandoahGC) {
      _cont.tail()->relativize_derived_pointers_concurrently();
    } else {
      ContinuationGCSupport::transform_stack_chunk(_cont.tail());
    }
    // For objects in the old generation we must maintain the remembered set
    _cont.tail()->do_barriers<stackChunkOopDesc::BarrierType::Store>();
  }

  log_develop_trace(continuations)("finish_freeze: has_mixed_frames: %d", chunk->has_mixed_frames());
  if (lt.develop_is_enabled()) {
    LogStream ls(lt);
    chunk->print_on(true, &ls);
  }

  if (lt.develop_is_enabled()) {
    LogStream ls(lt);
    ls.print_cr("top hframe after (freeze):");
    assert(_cont.last_frame().is_heap_frame(), "should be");
    _cont.last_frame().print_on(&ls);
  }

  assert(_cont.chunk_invariant(), "");
}

inline bool FreezeBase::stack_overflow() { // detect stack overflow in recursive native code
  JavaThread* t = !_preempt ? _thread : JavaThread::current();
  assert(t == JavaThread::current(), "");
  if (os::current_stack_pointer() < t->stack_overflow_state()->shadow_zone_safe_limit()) {
    if (!_preempt) {
      ContinuationWrapper::SafepointOp so(t, _cont); // could also call _cont.done() instead
      Exceptions::_throw_msg(t, __FILE__, __LINE__, vmSymbols::java_lang_StackOverflowError(), "Stack overflow while freezing");
    }
    return true;
  }
  return false;
}

class StackChunkAllocator : public MemAllocator {
  const size_t                                 _stack_size;
  ContinuationWrapper&                         _continuation_wrapper;
  JvmtiSampledObjectAllocEventCollector* const _jvmti_event_collector;
  mutable bool                                 _took_slow_path;

  // Does the minimal amount of initialization needed for a TLAB allocation.
  // We don't need to do a full initialization, as such an allocation need not be immediately walkable.
  virtual oop initialize(HeapWord* mem) const override {
    assert(_stack_size > 0, "");
    assert(_stack_size <= max_jint, "");
    assert(_word_size > _stack_size, "");

    // zero out fields (but not the stack)
    const size_t hs = oopDesc::header_size();
    Copy::fill_to_aligned_words(mem + hs, vmClasses::StackChunk_klass()->size_helper() - hs);

    jdk_internal_vm_StackChunk::set_size(mem, (int)_stack_size);
    jdk_internal_vm_StackChunk::set_sp(mem, (int)_stack_size);

    return finish(mem);
  }

  stackChunkOop allocate_fast() const {
    if (!UseTLAB) {
      return nullptr;
    }

    HeapWord* const mem = MemAllocator::mem_allocate_inside_tlab_fast();
    if (mem == nullptr) {
      return nullptr;
    }

    oop obj = initialize(mem);
    return stackChunkOopDesc::cast(obj);
  }

public:
  StackChunkAllocator(Klass* klass,
                      size_t word_size,
                      Thread* thread,
                      size_t stack_size,
                      ContinuationWrapper& continuation_wrapper,
                      JvmtiSampledObjectAllocEventCollector* jvmti_event_collector)
    : MemAllocator(klass, word_size, thread),
      _stack_size(stack_size),
      _continuation_wrapper(continuation_wrapper),
      _jvmti_event_collector(jvmti_event_collector),
      _took_slow_path(false) {}

  // Provides it's own, specialized allocation which skips instrumentation
  // if the memory can be allocated without going to a slow-path.
  stackChunkOop allocate() const {
    // First try to allocate without any slow-paths or instrumentation.
    stackChunkOop obj = allocate_fast();
    if (obj != nullptr) {
      return obj;
    }

    // Now try full-blown allocation with all expensive operations,
    // including potentially safepoint operations.
    _took_slow_path = true;

    // Protect unhandled Loom oops
    ContinuationWrapper::SafepointOp so(_thread, _continuation_wrapper);

    // Can safepoint
    _jvmti_event_collector->start();

    // Can safepoint
    return stackChunkOopDesc::cast(MemAllocator::allocate());
  }

  bool took_slow_path() const {
    return _took_slow_path;
  }
};

template <typename ConfigT>
stackChunkOop Freeze<ConfigT>::allocate_chunk(size_t stack_size) {
  log_develop_trace(continuations)("allocate_chunk allocating new chunk");

  InstanceStackChunkKlass* klass = InstanceStackChunkKlass::cast(vmClasses::StackChunk_klass());
  size_t size_in_words = klass->instance_size(stack_size);

  if (CollectedHeap::stack_chunk_max_size() > 0 && size_in_words >= CollectedHeap::stack_chunk_max_size()) {
    if (!_preempt) {
      throw_stack_overflow_on_humongous_chunk();
    }
    return nullptr;
  }

  JavaThread* current = _preempt ? JavaThread::current() : _thread;
  assert(current == JavaThread::current(), "should be current");

  // Allocate the chunk.
  //
  // This might safepoint while allocating, but all safepointing due to
  // instrumentation have been deferred. This property is important for
  // some GCs, as this ensures that the allocated object is in the young
  // generation / newly allocated memory.
  StackChunkAllocator allocator(klass, size_in_words, current, stack_size, _cont, _jvmti_event_collector);
  stackChunkOop chunk = allocator.allocate();

  if (chunk == nullptr) {
    return nullptr; // OOME
  }

  // assert that chunk is properly initialized
  assert(chunk->stack_size() == (int)stack_size, "");
  assert(chunk->size() >= stack_size, "chunk->size(): %zu size: %zu", chunk->size(), stack_size);
  assert(chunk->sp() == chunk->stack_size(), "");
  assert((intptr_t)chunk->start_address() % 8 == 0, "");
  assert(chunk->max_thawing_size() == 0, "");
  assert(chunk->pc() == nullptr, "");
  assert(chunk->argsize() == 0, "");
  assert(chunk->flags() == 0, "");
  assert(chunk->is_gc_mode() == false, "");

  // fields are uninitialized
  chunk->set_parent_access<IS_DEST_UNINITIALIZED>(_cont.last_nonempty_chunk());
  chunk->set_cont_access<IS_DEST_UNINITIALIZED>(_cont.continuation());

#if INCLUDE_ZGC
  if (UseZGC) {
    if (ZGenerational) {
      ZStackChunkGCData::initialize(chunk);
    }
    assert(!chunk->requires_barriers(), "ZGC always allocates in the young generation");
    _barriers = false;
  } else
#endif
#if INCLUDE_SHENANDOAHGC
  if (UseShenandoahGC) {
    _barriers = chunk->requires_barriers();
  } else
#endif
  {
    if (!allocator.took_slow_path()) {
      // Guaranteed to be in young gen / newly allocated memory
      assert(!chunk->requires_barriers(), "Unfamiliar GC requires barriers on TLAB allocation");
      _barriers = false;
    } else {
      // Some GCs could put direct allocations in old gen for slow-path
      // allocations; need to explicitly check if that was the case.
      _barriers = chunk->requires_barriers();
    }
  }

  if (_barriers) {
    log_develop_trace(continuations)("allocation requires barriers");
  }

  assert(chunk->parent() == nullptr || chunk->parent()->is_stackChunk(), "");

  return chunk;
}

void FreezeBase::throw_stack_overflow_on_humongous_chunk() {
  ContinuationWrapper::SafepointOp so(_thread, _cont); // could also call _cont.done() instead
  Exceptions::_throw_msg(_thread, __FILE__, __LINE__, vmSymbols::java_lang_StackOverflowError(), "Humongous stack chunk");
}

#if INCLUDE_JVMTI
static int num_java_frames(ContinuationWrapper& cont) {
  ResourceMark rm; // used for scope traversal in num_java_frames(CompiledMethod*, address)
  int count = 0;
  for (stackChunkOop chunk = cont.tail(); chunk != nullptr; chunk = chunk->parent()) {
    count += chunk->num_java_frames();
  }
  return count;
}

static void invalidate_jvmti_stack(JavaThread* thread) {
  if (thread->is_interp_only_mode()) {
    JvmtiThreadState *state = thread->jvmti_thread_state();
    if (state != nullptr)
      state->invalidate_cur_stack_depth();
  }
}

static void jvmti_yield_cleanup(JavaThread* thread, ContinuationWrapper& cont) {
  if (JvmtiExport::can_post_frame_pop()) {
    int num_frames = num_java_frames(cont);

    ContinuationWrapper::SafepointOp so(Thread::current(), cont);
    JvmtiExport::continuation_yield_cleanup(JavaThread::current(), num_frames);
  }
  invalidate_jvmti_stack(thread);
}
#endif // INCLUDE_JVMTI

#ifdef ASSERT
static bool monitors_on_stack(JavaThread* thread) {
  ContinuationEntry* ce = thread->last_continuation();
  RegisterMap map(thread,
                  RegisterMap::UpdateMap::include,
                  RegisterMap::ProcessFrames::include,
                  RegisterMap::WalkContinuation::skip);
  map.set_include_argument_oops(false);
  for (frame f = thread->last_frame(); Continuation::is_frame_in_continuation(ce, f); f = f.sender(&map)) {
    if ((f.is_interpreted_frame() && ContinuationHelper::InterpretedFrame::is_owning_locks(f)) ||
        (f.is_compiled_frame() && ContinuationHelper::CompiledFrame::is_owning_locks(map.thread(), &map, f))) {
      return true;
    }
  }
  return false;
}

bool FreezeBase::interpreted_native_or_deoptimized_on_stack() {
  ContinuationEntry* ce = _thread->last_continuation();
  RegisterMap map(_thread,
                  RegisterMap::UpdateMap::skip,
                  RegisterMap::ProcessFrames::skip,
                  RegisterMap::WalkContinuation::skip);
  map.set_include_argument_oops(false);
  for (frame f = freeze_start_frame(); Continuation::is_frame_in_continuation(ce, f); f = f.sender(&map)) {
    if (f.is_interpreted_frame() || f.is_native_frame() || f.is_deoptimized_frame()) {
      return true;
    }
  }
  return false;
}
#endif // ASSERT

static inline int freeze_epilog(JavaThread* thread, ContinuationWrapper& cont) {
  verify_continuation(cont.continuation());
  assert(!cont.is_empty(), "");
  // This is done for the sake of the enterSpecial frame
  StackWatermarkSet::after_unwind(thread);

  log_develop_debug(continuations)("=== End of freeze cont ### #" INTPTR_FORMAT, cont.hash());

  return 0;
}

static int freeze_epilog(JavaThread* thread, ContinuationWrapper& cont, freeze_result res) {
  if (UNLIKELY(res != freeze_ok)) {
    verify_continuation(cont.continuation());
    log_develop_trace(continuations)("=== end of freeze (fail %d)", res);
    return res;
  }

  JVMTI_ONLY(jvmti_yield_cleanup(thread, cont)); // can safepoint
  return freeze_epilog(thread, cont);
}

template<typename ConfigT>
static inline int freeze_internal(JavaThread* current, intptr_t* const sp) {
  assert(!current->has_pending_exception(), "");

#ifdef ASSERT
  log_trace(continuations)("~~~~ freeze sp: " INTPTR_FORMAT, p2i(current->last_continuation()->entry_sp()));
  log_frames(current);
#endif

  CONT_JFR_ONLY(EventContinuationFreeze event;)

  ContinuationEntry* entry = current->last_continuation();

  oop oopCont = entry->cont_oop(current);
  assert(oopCont == current->last_continuation()->cont_oop(current), "");
  assert(ContinuationEntry::assert_entry_frame_laid_out(current), "");

  verify_continuation(oopCont);
  ContinuationWrapper cont(current, oopCont);
  log_develop_debug(continuations)("FREEZE #" INTPTR_FORMAT " " INTPTR_FORMAT, cont.hash(), p2i((oopDesc*)oopCont));

  assert(entry->is_virtual_thread() == (entry->scope(current) == java_lang_VirtualThread::vthread_scope()), "");

  assert(monitors_on_stack(current) == ((current->held_monitor_count() - current->jni_monitor_count()) > 0),
         "Held monitor count and locks on stack invariant: " INT64_FORMAT " JNI: " INT64_FORMAT, (int64_t)current->held_monitor_count(), (int64_t)current->jni_monitor_count());

  if (entry->is_pinned() || current->held_monitor_count() > 0) {
    log_develop_debug(continuations)("PINNED due to critical section/hold monitor");
    verify_continuation(cont.continuation());
    freeze_result res = entry->is_pinned() ? freeze_pinned_cs : freeze_pinned_monitor;
    log_develop_trace(continuations)("=== end of freeze (fail %d)", res);
    return res;
  }

  Freeze<ConfigT> freeze(current, cont, sp);

  // There are no interpreted frames if we're not called from the interpreter and we haven't ancountered an i2c
  // adapter or called Deoptimization::unpack_frames. Calls from native frames also go through the interpreter
  // (see JavaCalls::call_helper).
  assert(!current->cont_fastpath()
         || (current->cont_fastpath_thread_state() && !freeze.interpreted_native_or_deoptimized_on_stack()), "");
  bool fast = UseContinuationFastPath && current->cont_fastpath();
  if (fast && freeze.size_if_fast_freeze_available() > 0) {
    freeze.freeze_fast_existing_chunk();
    CONT_JFR_ONLY(freeze.jfr_info().post_jfr_event(&event, oopCont, current);)
    freeze_epilog(current, cont);
    return 0;
  }

  log_develop_trace(continuations)("chunk unavailable; transitioning to VM");
  assert(current == JavaThread::current(), "must be current thread except for preempt");
  JRT_BLOCK
    // delays a possible JvmtiSampledObjectAllocEventCollector in alloc_chunk
    JvmtiSampledObjectAllocEventCollector jsoaec(false);
    freeze.set_jvmti_event_collector(&jsoaec);

    freeze_result res = fast ? freeze.try_freeze_fast() : freeze.freeze_slow();

    CONT_JFR_ONLY(freeze.jfr_info().post_jfr_event(&event, oopCont, current);)
    freeze_epilog(current, cont, res);
    cont.done(); // allow safepoint in the transition back to Java
    return res;
  JRT_BLOCK_END
}

static freeze_result is_pinned0(JavaThread* thread, oop cont_scope, bool safepoint) {
  ContinuationEntry* entry = thread->last_continuation();
  if (entry == nullptr) {
    return freeze_ok;
  }
  if (entry->is_pinned()) {
    return freeze_pinned_cs;
  } else if (thread->held_monitor_count() > 0) {
    return freeze_pinned_monitor;
  }

  RegisterMap map(thread,
                  RegisterMap::UpdateMap::include,
                  RegisterMap::ProcessFrames::skip,
                  RegisterMap::WalkContinuation::skip);
  map.set_include_argument_oops(false);
  frame f = thread->last_frame();

  if (!safepoint) {
    f = f.sender(&map); // this is the yield frame
  } else { // safepoint yield
#if (defined(X86) || defined(AARCH64) || defined(RISCV64)) && !defined(ZERO)
    f.set_fp(f.real_fp()); // Instead of this, maybe in ContinuationWrapper::set_last_frame always use the real_fp?
#else
    Unimplemented();
#endif
    if (!Interpreter::contains(f.pc())) {
      assert(ContinuationHelper::Frame::is_stub(f.cb()), "must be");
      assert(f.oop_map() != nullptr, "must be");
      f.oop_map()->update_register_map(&f, &map); // we have callee-save registers in this case
    }
  }

  while (true) {
    if ((f.is_interpreted_frame() && f.interpreter_frame_method()->is_native()) || f.is_native_frame()) {
      return freeze_pinned_native;
    }

    f = f.sender(&map);
    if (!Continuation::is_frame_in_continuation(entry, f)) {
      oop scope = jdk_internal_vm_Continuation::scope(entry->cont_oop(thread));
      if (scope == cont_scope) {
        break;
      }
      intx monitor_count = entry->parent_held_monitor_count();
      entry = entry->parent();
      if (entry == nullptr) {
        break;
      }
      if (entry->is_pinned()) {
        return freeze_pinned_cs;
      } else if (monitor_count > 0) {
        return freeze_pinned_monitor;
      }
    }
  }
  return freeze_ok;
}

/////////////// THAW ////

static int thaw_size(stackChunkOop chunk) {
  int size = chunk->max_thawing_size();
  size += frame::metadata_words; // For the top pc+fp in push_return_frame or top = stack_sp - frame::metadata_words in thaw_fast
  size += 2*frame::align_wiggle; // in case of alignments at the top and bottom
  return size;
}

// make room on the stack for thaw
// returns the size in bytes, or 0 on failure
static inline int prepare_thaw_internal(JavaThread* thread, bool return_barrier) {
  log_develop_trace(continuations)("~~~~ prepare_thaw return_barrier: %d", return_barrier);

  assert(thread == JavaThread::current(), "");

  ContinuationEntry* ce = thread->last_continuation();
  assert(ce != nullptr, "");
  oop continuation = ce->cont_oop(thread);
  assert(continuation == get_continuation(thread), "");
  verify_continuation(continuation);

  stackChunkOop chunk = jdk_internal_vm_Continuation::tail(continuation);
  assert(chunk != nullptr, "");

  // The tail can be empty because it might still be available for another freeze.
  // However, here we want to thaw, so we get rid of it (it will be GCed).
  if (UNLIKELY(chunk->is_empty())) {
    chunk = chunk->parent();
    assert(chunk != nullptr, "");
    assert(!chunk->is_empty(), "");
    jdk_internal_vm_Continuation::set_tail(continuation, chunk);
  }

  // Verification
  chunk->verify();
  assert(chunk->max_thawing_size() > 0, "chunk invariant violated; expected to not be empty");

  // Only make space for the last chunk because we only thaw from the last chunk
  int size = thaw_size(chunk) << LogBytesPerWord;

  const address bottom = (address)thread->last_continuation()->entry_sp();
  // 300 is an estimate for stack size taken for this native code, in addition to StackShadowPages
  // for the Java frames in the check below.
  if (!stack_overflow_check(thread, size + 300, bottom)) {
    return 0;
  }

  log_develop_trace(continuations)("prepare_thaw bottom: " INTPTR_FORMAT " top: " INTPTR_FORMAT " size: %d",
                              p2i(bottom), p2i(bottom - size), size);
  return size;
}

class ThawBase : public StackObj {
protected:
  JavaThread* _thread;
  ContinuationWrapper& _cont;
  CONT_JFR_ONLY(FreezeThawJfrInfo _jfr_info;)

  intptr_t* _fastpath;
  bool _barriers;
  intptr_t* _top_unextended_sp_before_thaw;
  int _align_size;
  DEBUG_ONLY(intptr_t* _top_stack_address);

  StackChunkFrameStream<ChunkFrames::Mixed> _stream;

  NOT_PRODUCT(int _frames;)

protected:
  ThawBase(JavaThread* thread, ContinuationWrapper& cont) :
      _thread(thread), _cont(cont),
      _fastpath(nullptr) {
    DEBUG_ONLY(_top_unextended_sp_before_thaw = nullptr;)
    assert (cont.tail() != nullptr, "no last chunk");
    DEBUG_ONLY(_top_stack_address = _cont.entrySP() - thaw_size(cont.tail());)
  }

  void clear_chunk(stackChunkOop chunk);
  int remove_top_compiled_frame_from_chunk(stackChunkOop chunk, int &argsize);
  void copy_from_chunk(intptr_t* from, intptr_t* to, int size);

  // fast path
  inline void prefetch_chunk_pd(void* start, int size_words);
  void patch_return(intptr_t* sp, bool is_last);

  // slow path
  NOINLINE intptr_t* thaw_slow(stackChunkOop chunk, bool return_barrier);

private:
  void recurse_thaw(const frame& heap_frame, frame& caller, int num_frames, bool top);
  template<typename FKind> bool recurse_thaw_java_frame(frame& caller, int num_frames);
  void finalize_thaw(frame& entry, int argsize);

  inline bool seen_by_gc();

  inline void before_thaw_java_frame(const frame& hf, const frame& caller, bool bottom, int num_frame);
  inline void after_thaw_java_frame(const frame& f, bool bottom);
  inline void patch(frame& f, const frame& caller, bool bottom);
  void clear_bitmap_bits(address start, address end);

  NOINLINE void recurse_thaw_interpreted_frame(const frame& hf, frame& caller, int num_frames);
  void recurse_thaw_compiled_frame(const frame& hf, frame& caller, int num_frames, bool stub_caller);
  void recurse_thaw_stub_frame(const frame& hf, frame& caller, int num_frames);
  void finish_thaw(frame& f);

  void push_return_frame(frame& f);
  inline frame new_entry_frame();
  template<typename FKind> frame new_stack_frame(const frame& hf, frame& caller, bool bottom);
  inline void patch_pd(frame& f, const frame& sender);
  inline intptr_t* align(const frame& hf, intptr_t* frame_sp, frame& caller, bool bottom);

  void maybe_set_fastpath(intptr_t* sp) { if (sp > _fastpath) _fastpath = sp; }

  static inline void derelativize_interpreted_frame_metadata(const frame& hf, const frame& f);

 public:
  CONT_JFR_ONLY(FreezeThawJfrInfo& jfr_info() { return _jfr_info; })
};

template <typename ConfigT>
class Thaw : public ThawBase {
public:
  Thaw(JavaThread* thread, ContinuationWrapper& cont) : ThawBase(thread, cont) {}

  inline bool can_thaw_fast(stackChunkOop chunk) {
    return    !_barriers
           &&  _thread->cont_fastpath_thread_state()
           && !chunk->has_thaw_slowpath_condition()
           && !PreserveFramePointer;
  }

  inline intptr_t* thaw(Continuation::thaw_kind kind);
  NOINLINE intptr_t* thaw_fast(stackChunkOop chunk);
  inline void patch_caller_links(intptr_t* sp, intptr_t* bottom);
};

template <typename ConfigT>
inline intptr_t* Thaw<ConfigT>::thaw(Continuation::thaw_kind kind) {
  verify_continuation(_cont.continuation());
  assert(!jdk_internal_vm_Continuation::done(_cont.continuation()), "");
  assert(!_cont.is_empty(), "");

  stackChunkOop chunk = _cont.tail();
  assert(chunk != nullptr, "guaranteed by prepare_thaw");
  assert(!chunk->is_empty(), "guaranteed by prepare_thaw");

  _barriers = chunk->requires_barriers();
  return (LIKELY(can_thaw_fast(chunk))) ? thaw_fast(chunk)
                                        : thaw_slow(chunk, kind != Continuation::thaw_top);
}

class ReconstructedStack : public StackObj {
  intptr_t* _base;  // _cont.entrySP(); // top of the entry frame
  int _thaw_size;
  int _argsize;
public:
  ReconstructedStack(intptr_t* base, int thaw_size, int argsize)
  : _base(base), _thaw_size(thaw_size - (argsize == 0 ? frame::metadata_words_at_top : 0)), _argsize(argsize) {
    // The only possible source of misalignment is stack-passed arguments b/c compiled frames are 16-byte aligned.
    assert(argsize != 0 || (_base - _thaw_size) == ContinuationHelper::frame_align_pointer(_base - _thaw_size), "");
    // We're at most one alignment word away from entrySP
    assert(_base - 1 <= top() + total_size() + frame::metadata_words_at_bottom, "missed entry frame");
  }

  int entry_frame_extension() const { return _argsize + (_argsize > 0 ? frame::metadata_words_at_top : 0); }

  // top and bottom stack pointers
  intptr_t* sp() const { return ContinuationHelper::frame_align_pointer(_base - _thaw_size); }
  intptr_t* bottom_sp() const { return ContinuationHelper::frame_align_pointer(_base - entry_frame_extension()); }

  // several operations operate on the totality of the stack being reconstructed,
  // including the metadata words
  intptr_t* top() const { return sp() - frame::metadata_words_at_bottom;  }
  int total_size() const { return _thaw_size + frame::metadata_words_at_bottom; }
};

inline void ThawBase::clear_chunk(stackChunkOop chunk) {
  chunk->set_sp(chunk->stack_size());
  chunk->set_argsize(0);
  chunk->set_max_thawing_size(0);
}

 int ThawBase::remove_top_compiled_frame_from_chunk(stackChunkOop chunk, int &argsize) {
  bool empty = false;
  StackChunkFrameStream<ChunkFrames::CompiledOnly> f(chunk);
  DEBUG_ONLY(intptr_t* const chunk_sp = chunk->start_address() + chunk->sp();)
  assert(chunk_sp == f.sp(), "");
  assert(chunk_sp == f.unextended_sp(), "");

  const int frame_size = f.cb()->frame_size();
  argsize = f.stack_argsize();

  f.next(SmallRegisterMap::instance, true /* stop */);
  empty = f.is_done();
  assert(!empty || argsize == chunk->argsize(), "");

  if (empty) {
    clear_chunk(chunk);
  } else {
    chunk->set_sp(chunk->sp() + frame_size);
    chunk->set_max_thawing_size(chunk->max_thawing_size() - frame_size);
    // We set chunk->pc to the return pc into the next frame
    chunk->set_pc(f.pc());
#ifdef ASSERT
    {
      intptr_t* retaddr_slot = (chunk_sp
                                + frame_size
                                - frame::sender_sp_ret_address_offset());
      assert(f.pc() == ContinuationHelper::return_address_at(retaddr_slot),
             "unexpected pc");
    }
#endif
  }
  assert(empty == chunk->is_empty(), "");
  // returns the size required to store the frame on stack, and because it is a
  // compiled frame, it must include a copy of the arguments passed by the caller
  return frame_size + argsize + frame::metadata_words_at_top;
}

void ThawBase::copy_from_chunk(intptr_t* from, intptr_t* to, int size) {
  assert(to >= _top_stack_address, "overwrote past thawing space"
    " to: " INTPTR_FORMAT " top_address: " INTPTR_FORMAT, p2i(to), p2i(_top_stack_address));
  assert(to + size <= _cont.entrySP(), "overwrote past thawing space");
  _cont.tail()->copy_from_chunk_to_stack(from, to, size);
  CONT_JFR_ONLY(_jfr_info.record_size_copied(size);)
}

void ThawBase::patch_return(intptr_t* sp, bool is_last) {
  log_develop_trace(continuations)("thaw_fast patching -- sp: " INTPTR_FORMAT, p2i(sp));

  address pc = !is_last ? StubRoutines::cont_returnBarrier() : _cont.entryPC();
  ContinuationHelper::patch_return_address_at(
    sp - frame::sender_sp_ret_address_offset(),
    pc);
}

template <typename ConfigT>
NOINLINE intptr_t* Thaw<ConfigT>::thaw_fast(stackChunkOop chunk) {
  assert(chunk == _cont.tail(), "");
  assert(!chunk->has_mixed_frames(), "");
  assert(!chunk->requires_barriers(), "");
  assert(!chunk->has_bitmap(), "");
  assert(!_thread->is_interp_only_mode(), "");

  LogTarget(Trace, continuations) lt;
  if (lt.develop_is_enabled()) {
    LogStream ls(lt);
    ls.print_cr("thaw_fast");
    chunk->print_on(true, &ls);
  }

  // Below this heuristic, we thaw the whole chunk, above it we thaw just one frame.
  static const int threshold = 500; // words

  const int full_chunk_size = chunk->stack_size() - chunk->sp(); // this initial size could be reduced if it's a partial thaw
  int argsize, thaw_size;

  intptr_t* const chunk_sp = chunk->start_address() + chunk->sp();

  bool partial, empty;
  if (LIKELY(!TEST_THAW_ONE_CHUNK_FRAME && (full_chunk_size < threshold))) {
    prefetch_chunk_pd(chunk->start_address(), full_chunk_size); // prefetch anticipating memcpy starting at highest address

    partial = false;
    argsize = chunk->argsize(); // must be called *before* clearing the chunk
    clear_chunk(chunk);
    thaw_size = full_chunk_size;
    empty = true;
  } else { // thaw a single frame
    partial = true;
    thaw_size = remove_top_compiled_frame_from_chunk(chunk, argsize);
    empty = chunk->is_empty();
  }

  // Are we thawing the last frame(s) in the continuation
  const bool is_last = empty && chunk->parent() == nullptr;
  assert(!is_last || argsize == 0, "");

  log_develop_trace(continuations)("thaw_fast partial: %d is_last: %d empty: %d size: %d argsize: %d entrySP: " PTR_FORMAT,
                              partial, is_last, empty, thaw_size, argsize, p2i(_cont.entrySP()));

  ReconstructedStack rs(_cont.entrySP(), thaw_size, argsize);

  // also copy metadata words at frame bottom
  copy_from_chunk(chunk_sp - frame::metadata_words_at_bottom, rs.top(), rs.total_size());

  // update the ContinuationEntry
  _cont.set_argsize(argsize);
  log_develop_trace(continuations)("setting entry argsize: %d", _cont.argsize());
  assert(rs.bottom_sp() == _cont.entry()->bottom_sender_sp(), "");

  // install the return barrier if not last frame, or the entry's pc if last
  patch_return(rs.bottom_sp(), is_last);

  // insert the back links from callee to caller frames
  patch_caller_links(rs.top(), rs.top() + rs.total_size());

  assert(is_last == _cont.is_empty(), "");
  assert(_cont.chunk_invariant(), "");

#if CONT_JFR
  EventContinuationThawFast e;
  if (e.should_commit()) {
    e.set_id(cast_from_oop<u8>(chunk));
    e.set_size(thaw_size << LogBytesPerWord);
    e.set_full(!partial);
    e.commit();
  }
#endif

#ifdef ASSERT
  set_anchor(_thread, rs.sp());
  log_frames(_thread);
  if (LoomDeoptAfterThaw) {
    do_deopt_after_thaw(_thread);
  }
  clear_anchor(_thread);
#endif

  return rs.sp();
}

inline bool ThawBase::seen_by_gc() {
  return _barriers || _cont.tail()->is_gc_mode();
}

NOINLINE intptr_t* ThawBase::thaw_slow(stackChunkOop chunk, bool return_barrier) {
  LogTarget(Trace, continuations) lt;
  if (lt.develop_is_enabled()) {
    LogStream ls(lt);
    ls.print_cr("thaw slow return_barrier: %d " INTPTR_FORMAT, return_barrier, p2i(chunk));
    chunk->print_on(true, &ls);
  }

#if CONT_JFR
  EventContinuationThawSlow e;
  if (e.should_commit()) {
    e.set_id(cast_from_oop<u8>(_cont.continuation()));
    e.commit();
  }
#endif

  DEBUG_ONLY(_frames = 0;)
  _align_size = 0;
  int num_frames = (return_barrier ? 1 : 2);

  _stream = StackChunkFrameStream<ChunkFrames::Mixed>(chunk);
  _top_unextended_sp_before_thaw = _stream.unextended_sp();

  frame heap_frame = _stream.to_frame();
  if (lt.develop_is_enabled()) {
    LogStream ls(lt);
    ls.print_cr("top hframe before (thaw):");
    assert(heap_frame.is_heap_frame(), "should have created a relative frame");
    heap_frame.print_value_on(&ls, nullptr);
  }

#if INCLUDE_ZGC || INCLUDE_SHENANDOAHGC
  if (UseZGC || UseShenandoahGC) {
    _cont.tail()->relativize_derived_pointers_concurrently();
  }
#endif

  frame caller; // the thawed caller on the stack
  recurse_thaw(heap_frame, caller, num_frames, true);
  finish_thaw(caller); // caller is now the topmost thawed frame
  _cont.write();

  assert(_cont.chunk_invariant(), "");

  JVMTI_ONLY(if (!return_barrier) invalidate_jvmti_stack(_thread));

  _thread->set_cont_fastpath(_fastpath);

  intptr_t* sp = caller.sp();
  return sp;
}

void ThawBase::recurse_thaw(const frame& heap_frame, frame& caller, int num_frames, bool top) {
  log_develop_debug(continuations)("thaw num_frames: %d", num_frames);
  assert(!_cont.is_empty(), "no more frames");
  assert(num_frames > 0, "");
  assert(!heap_frame.is_empty(), "");

  if (top && heap_frame.is_safepoint_blob_frame()) {
    assert(ContinuationHelper::Frame::is_stub(heap_frame.cb()), "cb: %s", heap_frame.cb()->name());
    recurse_thaw_stub_frame(heap_frame, caller, num_frames);
  } else if (!heap_frame.is_interpreted_frame()) {
    recurse_thaw_compiled_frame(heap_frame, caller, num_frames, false);
  } else {
    recurse_thaw_interpreted_frame(heap_frame, caller, num_frames);
  }
}

template<typename FKind>
bool ThawBase::recurse_thaw_java_frame(frame& caller, int num_frames) {
  assert(num_frames > 0, "");

  DEBUG_ONLY(_frames++;)

  int argsize = _stream.stack_argsize();

  _stream.next(SmallRegisterMap::instance);
  assert(_stream.to_frame().is_empty() == _stream.is_done(), "");

  // we never leave a compiled caller of an interpreted frame as the top frame in the chunk
  // as it makes detecting that situation and adjusting unextended_sp tricky
  if (num_frames == 1 && !_stream.is_done() && FKind::interpreted && _stream.is_compiled()) {
    log_develop_trace(continuations)("thawing extra compiled frame to not leave a compiled interpreted-caller at top");
    num_frames++;
  }

  if (num_frames == 1 || _stream.is_done()) { // end recursion
    finalize_thaw(caller, FKind::interpreted ? 0 : argsize);
    return true; // bottom
  } else { // recurse
    recurse_thaw(_stream.to_frame(), caller, num_frames - 1, false);
    return false;
  }
}

void ThawBase::finalize_thaw(frame& entry, int argsize) {
  stackChunkOop chunk = _cont.tail();

  if (!_stream.is_done()) {
    assert(_stream.sp() >= chunk->sp_address(), "");
    chunk->set_sp(chunk->to_offset(_stream.sp()));
    chunk->set_pc(_stream.pc());
  } else {
    chunk->set_argsize(0);
    chunk->set_sp(chunk->stack_size());
    chunk->set_pc(nullptr);
  }
  assert(_stream.is_done() == chunk->is_empty(), "");

  int total_thawed = pointer_delta_as_int(_stream.unextended_sp(), _top_unextended_sp_before_thaw);
  chunk->set_max_thawing_size(chunk->max_thawing_size() - total_thawed);

  _cont.set_argsize(argsize);
  entry = new_entry_frame();

  assert(entry.sp() == _cont.entrySP(), "");
  assert(Continuation::is_continuation_enterSpecial(entry), "");
  assert(_cont.is_entry_frame(entry), "");
}

inline void ThawBase::before_thaw_java_frame(const frame& hf, const frame& caller, bool bottom, int num_frame) {
  LogTarget(Trace, continuations) lt;
  if (lt.develop_is_enabled()) {
    LogStream ls(lt);
    ls.print_cr("======== THAWING FRAME: %d", num_frame);
    assert(hf.is_heap_frame(), "should be");
    hf.print_value_on(&ls, nullptr);
  }
  assert(bottom == _cont.is_entry_frame(caller), "bottom: %d is_entry_frame: %d", bottom, _cont.is_entry_frame(hf));
}

inline void ThawBase::after_thaw_java_frame(const frame& f, bool bottom) {
#ifdef ASSERT
  LogTarget(Trace, continuations) lt;
  if (lt.develop_is_enabled()) {
    LogStream ls(lt);
    ls.print_cr("thawed frame:");
    print_frame_layout(f, false, &ls); // f.print_on(&ls);
  }
#endif
}

inline void ThawBase::patch(frame& f, const frame& caller, bool bottom) {
  assert(!bottom || caller.fp() == _cont.entryFP(), "");
  if (bottom) {
    ContinuationHelper::Frame::patch_pc(caller, _cont.is_empty() ? caller.pc()
                                                                 : StubRoutines::cont_returnBarrier());
  } else {
    // caller might have been deoptimized during thaw but we've overwritten the return address when copying f from the heap.
    // If the caller is not deoptimized, pc is unchanged.
    ContinuationHelper::Frame::patch_pc(caller, caller.raw_pc());
  }

  patch_pd(f, caller);

  if (f.is_interpreted_frame()) {
    ContinuationHelper::InterpretedFrame::patch_sender_sp(f, caller);
  }

  assert(!bottom || !_cont.is_empty() || Continuation::is_continuation_entry_frame(f, nullptr), "");
  assert(!bottom || (_cont.is_empty() != Continuation::is_cont_barrier_frame(f)), "");
}

void ThawBase::clear_bitmap_bits(address start, address end) {
  assert(is_aligned(start, wordSize), "should be aligned: " PTR_FORMAT, p2i(start));
  assert(is_aligned(end, VMRegImpl::stack_slot_size), "should be aligned: " PTR_FORMAT, p2i(end));

  // we need to clear the bits that correspond to arguments as they reside in the caller frame
  // or they will keep objects that are otherwise unreachable alive.

  // Align `end` if UseCompressedOops is not set to avoid UB when calculating the bit index, since
  // `end` could be at an odd number of stack slots from `start`, i.e might not be oop aligned.
  // If that's the case the bit range corresponding to the last stack slot should not have bits set
  // anyways and we assert that before returning.
  address effective_end = UseCompressedOops ? end : align_down(end, wordSize);
  log_develop_trace(continuations)("clearing bitmap for " INTPTR_FORMAT " - " INTPTR_FORMAT, p2i(start), p2i(effective_end));
  stackChunkOop chunk = _cont.tail();
  chunk->bitmap().clear_range(chunk->bit_index_for(start), chunk->bit_index_for(effective_end));
  assert(chunk->bitmap().count_one_bits(chunk->bit_index_for(effective_end), chunk->bit_index_for(end)) == 0, "bits should not be set");
}

NOINLINE void ThawBase::recurse_thaw_interpreted_frame(const frame& hf, frame& caller, int num_frames) {
  assert(hf.is_interpreted_frame(), "");

  if (UNLIKELY(seen_by_gc())) {
    _cont.tail()->do_barriers<stackChunkOopDesc::BarrierType::Store>(_stream, SmallRegisterMap::instance);
  }

  const bool is_bottom_frame = recurse_thaw_java_frame<ContinuationHelper::InterpretedFrame>(caller, num_frames);

  DEBUG_ONLY(before_thaw_java_frame(hf, caller, is_bottom_frame, num_frames);)

  _align_size += frame::align_wiggle; // possible added alignment for internal interpreted frame alignment om AArch64

  frame f = new_stack_frame<ContinuationHelper::InterpretedFrame>(hf, caller, is_bottom_frame);

  intptr_t* const stack_frame_top = f.sp() + frame::metadata_words_at_top;
  intptr_t* const stack_frame_bottom = ContinuationHelper::InterpretedFrame::frame_bottom(f);
  intptr_t* const heap_frame_top = hf.unextended_sp() + frame::metadata_words_at_top;
  intptr_t* const heap_frame_bottom = ContinuationHelper::InterpretedFrame::frame_bottom(hf);

  assert(hf.is_heap_frame(), "should be");
  assert(!f.is_heap_frame(), "should not be");

  const int fsize = pointer_delta_as_int(heap_frame_bottom, heap_frame_top);
  assert((stack_frame_bottom == stack_frame_top + fsize), "");

  // Some architectures (like AArch64/PPC64/RISC-V) add padding between the locals and the fixed_frame to keep the fp 16-byte-aligned.
  // On those architectures we freeze the padding in order to keep the same fp-relative offsets in the fixed_frame.
  copy_from_chunk(heap_frame_top, stack_frame_top, fsize);

  // Make sure the relativized locals is already set.
  assert(f.interpreter_frame_local_at(0) == stack_frame_bottom - 1, "invalid frame bottom");

  derelativize_interpreted_frame_metadata(hf, f);
  patch(f, caller, is_bottom_frame);

  assert(f.is_interpreted_frame_valid(_cont.thread()), "invalid thawed frame");
  assert(stack_frame_bottom <= ContinuationHelper::Frame::frame_top(caller), "");

  CONT_JFR_ONLY(_jfr_info.record_interpreted_frame();)

  maybe_set_fastpath(f.sp());

  const int locals = hf.interpreter_frame_method()->max_locals();

  if (!is_bottom_frame) {
    // can only fix caller once this frame is thawed (due to callee saved regs)
    _cont.tail()->fix_thawed_frame(caller, SmallRegisterMap::instance);
  } else if (_cont.tail()->has_bitmap() && locals > 0) {
    assert(hf.is_heap_frame(), "should be");
    address start = (address)(heap_frame_bottom - locals);
    address end = (address)heap_frame_bottom;
    clear_bitmap_bits(start, end);
  }

  DEBUG_ONLY(after_thaw_java_frame(f, is_bottom_frame);)
  caller = f;
}

void ThawBase::recurse_thaw_compiled_frame(const frame& hf, frame& caller, int num_frames, bool stub_caller) {
  assert(!hf.is_interpreted_frame(), "");
  assert(_cont.is_preempted() || !stub_caller, "stub caller not at preemption");

  if (!stub_caller && UNLIKELY(seen_by_gc())) { // recurse_thaw_stub_frame already invoked our barriers with a full regmap
    _cont.tail()->do_barriers<stackChunkOopDesc::BarrierType::Store>(_stream, SmallRegisterMap::instance);
  }

  const bool is_bottom_frame = recurse_thaw_java_frame<ContinuationHelper::CompiledFrame>(caller, num_frames);

  DEBUG_ONLY(before_thaw_java_frame(hf, caller, is_bottom_frame, num_frames);)

  assert(caller.sp() == caller.unextended_sp(), "");

  if ((!is_bottom_frame && caller.is_interpreted_frame()) || (is_bottom_frame && Interpreter::contains(_cont.tail()->pc()))) {
    _align_size += frame::align_wiggle; // we add one whether or not we've aligned because we add it in freeze_interpreted_frame
  }

  // new_stack_frame must construct the resulting frame using hf.pc() rather than hf.raw_pc() because the frame is not
  // yet laid out in the stack, and so the original_pc is not stored in it.
  // As a result, f.is_deoptimized_frame() is always false and we must test hf to know if the frame is deoptimized.
  frame f = new_stack_frame<ContinuationHelper::CompiledFrame>(hf, caller, is_bottom_frame);
  intptr_t* const stack_frame_top = f.sp();
  intptr_t* const heap_frame_top = hf.unextended_sp();

  const int added_argsize = (is_bottom_frame || caller.is_interpreted_frame()) ? hf.compiled_frame_stack_argsize() : 0;
  int fsize = ContinuationHelper::CompiledFrame::size(hf) + added_argsize;
  assert(fsize <= (int)(caller.unextended_sp() - f.unextended_sp()), "");

  intptr_t* from = heap_frame_top - frame::metadata_words_at_bottom;
  intptr_t* to   = stack_frame_top - frame::metadata_words_at_bottom;
  // copy metadata, except the metadata at the top of the (unextended) entry frame
  int sz = fsize + frame::metadata_words_at_bottom + (is_bottom_frame && added_argsize == 0 ? 0 : frame::metadata_words_at_top);

  // If we're the bottom-most thawed frame, we're writing to within one word from entrySP
  // (we might have one padding word for alignment)
  assert(!is_bottom_frame || (_cont.entrySP() - 1 <= to + sz && to + sz <= _cont.entrySP()), "");
  assert(!is_bottom_frame || hf.compiled_frame_stack_argsize() != 0 || (to + sz && to + sz == _cont.entrySP()), "");

  copy_from_chunk(from, to, sz); // copying good oops because we invoked barriers above

  patch(f, caller, is_bottom_frame);

  // f.is_deoptimized_frame() is always false and we must test hf.is_deoptimized_frame() (see comment above)
  assert(!f.is_deoptimized_frame(), "");
  if (hf.is_deoptimized_frame()) {
    maybe_set_fastpath(f.sp());
  } else if (_thread->is_interp_only_mode()
              || (_cont.is_preempted() && f.cb()->as_compiled_method()->is_marked_for_deoptimization())) {
    // The caller of the safepoint stub when the continuation is preempted is not at a call instruction, and so
    // cannot rely on nmethod patching for deopt.
    assert(_thread->is_interp_only_mode() || stub_caller, "expected a stub-caller");

    log_develop_trace(continuations)("Deoptimizing thawed frame");
    DEBUG_ONLY(ContinuationHelper::Frame::patch_pc(f, nullptr));

    f.deoptimize(nullptr); // the null thread simply avoids the assertion in deoptimize which we're not set up for
    assert(f.is_deoptimized_frame(), "");
    assert(ContinuationHelper::Frame::is_deopt_return(f.raw_pc(), f), "");
    maybe_set_fastpath(f.sp());
  }

  if (!is_bottom_frame) {
    // can only fix caller once this frame is thawed (due to callee saved regs); this happens on the stack
    _cont.tail()->fix_thawed_frame(caller, SmallRegisterMap::instance);
  } else if (_cont.tail()->has_bitmap() && added_argsize > 0) {
    address start = (address)(heap_frame_top + ContinuationHelper::CompiledFrame::size(hf) + frame::metadata_words_at_top);
    int stack_args_slots = f.cb()->as_compiled_method()->method()->num_stack_arg_slots(false /* rounded */);
    int argsize_in_bytes = stack_args_slots * VMRegImpl::stack_slot_size;
    clear_bitmap_bits(start, start + argsize_in_bytes);
  }

  DEBUG_ONLY(after_thaw_java_frame(f, is_bottom_frame);)
  caller = f;
}

void ThawBase::recurse_thaw_stub_frame(const frame& hf, frame& caller, int num_frames) {
  DEBUG_ONLY(_frames++;)

  {
    RegisterMap map(nullptr,
                    RegisterMap::UpdateMap::include,
                    RegisterMap::ProcessFrames::skip,
                    RegisterMap::WalkContinuation::skip);
    map.set_include_argument_oops(false);
    _stream.next(&map);
    assert(!_stream.is_done(), "");
    if (UNLIKELY(seen_by_gc())) { // we're now doing this on the stub's caller
      _cont.tail()->do_barriers<stackChunkOopDesc::BarrierType::Store>(_stream, &map);
    }
    assert(!_stream.is_done(), "");
  }

  recurse_thaw_compiled_frame(_stream.to_frame(), caller, num_frames, true); // this could be deoptimized

  DEBUG_ONLY(before_thaw_java_frame(hf, caller, false, num_frames);)

  assert(ContinuationHelper::Frame::is_stub(hf.cb()), "");
  assert(caller.sp() == caller.unextended_sp(), "");
  assert(!caller.is_interpreted_frame(), "");

  int fsize = ContinuationHelper::StubFrame::size(hf);

  frame f = new_stack_frame<ContinuationHelper::StubFrame>(hf, caller, false);
  intptr_t* stack_frame_top = f.sp();
  intptr_t* heap_frame_top = hf.sp();

  copy_from_chunk(heap_frame_top - frame::metadata_words, stack_frame_top - frame::metadata_words,
                  fsize + frame::metadata_words);

  { // can only fix caller once this frame is thawed (due to callee saved regs)
    RegisterMap map(nullptr,
                    RegisterMap::UpdateMap::include,
                    RegisterMap::ProcessFrames::skip,
                    RegisterMap::WalkContinuation::skip); // map.clear();
    map.set_include_argument_oops(false);
    f.oop_map()->update_register_map(&f, &map);
    ContinuationHelper::update_register_map_with_callee(caller, &map);
    _cont.tail()->fix_thawed_frame(caller, &map);
  }

  DEBUG_ONLY(after_thaw_java_frame(f, false);)
  caller = f;
}

void ThawBase::finish_thaw(frame& f) {
  stackChunkOop chunk = _cont.tail();

  if (chunk->is_empty()) {
    // Only remove chunk from list if it can't be reused for another freeze
    if (seen_by_gc()) {
      _cont.set_tail(chunk->parent());
    } else {
      chunk->set_has_mixed_frames(false);
    }
    chunk->set_max_thawing_size(0);
    assert(chunk->argsize() == 0, "");
  } else {
    chunk->set_max_thawing_size(chunk->max_thawing_size() - _align_size);
  }
  assert(chunk->is_empty() == (chunk->max_thawing_size() == 0), "");

  if (!is_aligned(f.sp(), frame::frame_alignment)) {
    assert(f.is_interpreted_frame(), "");
    f.set_sp(align_down(f.sp(), frame::frame_alignment));
  }
  push_return_frame(f);
  chunk->fix_thawed_frame(f, SmallRegisterMap::instance); // can only fix caller after push_return_frame (due to callee saved regs)

  assert(_cont.is_empty() == _cont.last_frame().is_empty(), "");

  log_develop_trace(continuations)("thawed %d frames", _frames);

  LogTarget(Trace, continuations) lt;
  if (lt.develop_is_enabled()) {
    LogStream ls(lt);
    ls.print_cr("top hframe after (thaw):");
    _cont.last_frame().print_value_on(&ls, nullptr);
  }
}

void ThawBase::push_return_frame(frame& f) { // see generate_cont_thaw
  assert(!f.is_compiled_frame() || f.is_deoptimized_frame() == f.cb()->as_compiled_method()->is_deopt_pc(f.raw_pc()), "");
  assert(!f.is_compiled_frame() || f.is_deoptimized_frame() == (f.pc() != f.raw_pc()), "");

  LogTarget(Trace, continuations) lt;
  if (lt.develop_is_enabled()) {
    LogStream ls(lt);
    ls.print_cr("push_return_frame");
    f.print_value_on(&ls, nullptr);
  }

  assert(f.sp() - frame::metadata_words_at_bottom >= _top_stack_address, "overwrote past thawing space"
    " to: " INTPTR_FORMAT " top_address: " INTPTR_FORMAT, p2i(f.sp() - frame::metadata_words), p2i(_top_stack_address));
  ContinuationHelper::Frame::patch_pc(f, f.raw_pc()); // in case we want to deopt the frame in a full transition, this is checked.
  ContinuationHelper::push_pd(f);

  assert(ContinuationHelper::Frame::assert_frame_laid_out(f), "");
}

// returns new top sp
// called after preparations (stack overflow check and making room)
template<typename ConfigT>
static inline intptr_t* thaw_internal(JavaThread* thread, const Continuation::thaw_kind kind) {
  assert(thread == JavaThread::current(), "Must be current thread");

  CONT_JFR_ONLY(EventContinuationThaw event;)

  log_develop_trace(continuations)("~~~~ thaw kind: %d sp: " INTPTR_FORMAT, kind, p2i(thread->last_continuation()->entry_sp()));

  ContinuationEntry* entry = thread->last_continuation();
  assert(entry != nullptr, "");
  oop oopCont = entry->cont_oop(thread);

  assert(!jdk_internal_vm_Continuation::done(oopCont), "");
  assert(oopCont == get_continuation(thread), "");
  verify_continuation(oopCont);

  assert(entry->is_virtual_thread() == (entry->scope(thread) == java_lang_VirtualThread::vthread_scope()), "");

  ContinuationWrapper cont(thread, oopCont);
  log_develop_debug(continuations)("THAW #" INTPTR_FORMAT " " INTPTR_FORMAT, cont.hash(), p2i((oopDesc*)oopCont));

#ifdef ASSERT
  set_anchor_to_entry(thread, cont.entry());
  log_frames(thread);
  clear_anchor(thread);
#endif

  Thaw<ConfigT> thw(thread, cont);
  intptr_t* const sp = thw.thaw(kind);
  assert(is_aligned(sp, frame::frame_alignment), "");

  // All the frames have been thawed so we know they don't hold any monitors
  assert(thread->held_monitor_count() == 0, "Must be");

#ifdef ASSERT
  intptr_t* sp0 = sp;
  set_anchor(thread, sp0);
  log_frames(thread);
  if (LoomVerifyAfterThaw) {
    assert(do_verify_after_thaw(thread, cont.tail(), tty), "");
  }
  assert(ContinuationEntry::assert_entry_frame_laid_out(thread), "");
  clear_anchor(thread);

  LogTarget(Trace, continuations) lt;
  if (lt.develop_is_enabled()) {
    LogStream ls(lt);
    ls.print_cr("Jumping to frame (thaw):");
    frame(sp).print_value_on(&ls, nullptr);
  }
#endif

  CONT_JFR_ONLY(thw.jfr_info().post_jfr_event(&event, cont.continuation(), thread);)

  verify_continuation(cont.continuation());
  log_develop_debug(continuations)("=== End of thaw #" INTPTR_FORMAT, cont.hash());

  return sp;
}

#ifdef ASSERT
static void do_deopt_after_thaw(JavaThread* thread) {
  int i = 0;
  StackFrameStream fst(thread, true, false);
  fst.register_map()->set_include_argument_oops(false);
  ContinuationHelper::update_register_map_with_callee(*fst.current(), fst.register_map());
  for (; !fst.is_done(); fst.next()) {
    if (fst.current()->cb()->is_compiled()) {
      CompiledMethod* cm = fst.current()->cb()->as_compiled_method();
      if (!cm->method()->is_continuation_native_intrinsic()) {
        cm->make_deoptimized();
      }
    }
  }
}

class ThawVerifyOopsClosure: public OopClosure {
  intptr_t* _p;
  outputStream* _st;
  bool is_good_oop(oop o) {
    return dbg_is_safe(o, -1) && dbg_is_safe(o->klass(), -1) && oopDesc::is_oop(o) && o->klass()->is_klass();
  }
public:
  ThawVerifyOopsClosure(outputStream* st) : _p(nullptr), _st(st) {}
  intptr_t* p() { return _p; }
  void reset() { _p = nullptr; }

  virtual void do_oop(oop* p) {
    oop o = *p;
    if (o == nullptr || is_good_oop(o)) {
      return;
    }
    _p = (intptr_t*)p;
    _st->print_cr("*** non-oop " PTR_FORMAT " found at " PTR_FORMAT, p2i(*p), p2i(p));
  }
  virtual void do_oop(narrowOop* p) {
    oop o = RawAccess<>::oop_load(p);
    if (o == nullptr || is_good_oop(o)) {
      return;
    }
    _p = (intptr_t*)p;
    _st->print_cr("*** (narrow) non-oop %x found at " PTR_FORMAT, (int)(*p), p2i(p));
  }
};

static bool do_verify_after_thaw(JavaThread* thread, stackChunkOop chunk, outputStream* st) {
  assert(thread->has_last_Java_frame(), "");

  ResourceMark rm;
  ThawVerifyOopsClosure cl(st);
  CodeBlobToOopClosure cf(&cl, false);

  StackFrameStream fst(thread, true, false);
  fst.register_map()->set_include_argument_oops(false);
  ContinuationHelper::update_register_map_with_callee(*fst.current(), fst.register_map());
  for (; !fst.is_done() && !Continuation::is_continuation_enterSpecial(*fst.current()); fst.next()) {
    if (fst.current()->cb()->is_compiled() && fst.current()->cb()->as_compiled_method()->is_marked_for_deoptimization()) {
      st->print_cr(">>> do_verify_after_thaw deopt");
      fst.current()->deoptimize(nullptr);
      fst.current()->print_on(st);
    }

    fst.current()->oops_do(&cl, &cf, fst.register_map());
    if (cl.p() != nullptr) {
      frame fr = *fst.current();
      st->print_cr("Failed for frame barriers: %d",chunk->requires_barriers());
      fr.print_on(st);
      if (!fr.is_interpreted_frame()) {
        st->print_cr("size: %d argsize: %d",
                     ContinuationHelper::NonInterpretedUnknownFrame::size(fr),
                     ContinuationHelper::NonInterpretedUnknownFrame::stack_argsize(fr));
      }
      VMReg reg = fst.register_map()->find_register_spilled_here(cl.p(), fst.current()->sp());
      if (reg != nullptr) {
        st->print_cr("Reg %s %d", reg->name(), reg->is_stack() ? (int)reg->reg2stack() : -99);
      }
      cl.reset();
      DEBUG_ONLY(thread->print_frame_layout();)
      if (chunk != nullptr) {
        chunk->print_on(true, st);
      }
      return false;
    }
  }
  return true;
}

static void log_frames(JavaThread* thread) {
  const static int show_entry_callers = 3;
  LogTarget(Trace, continuations) lt;
  if (!lt.develop_is_enabled()) {
    return;
  }
  LogStream ls(lt);

  ls.print_cr("------- frames ---------");
  if (!thread->has_last_Java_frame()) {
    ls.print_cr("NO ANCHOR!");
  }

  RegisterMap map(thread,
                  RegisterMap::UpdateMap::include,
                  RegisterMap::ProcessFrames::include,
                  RegisterMap::WalkContinuation::skip);
  map.set_include_argument_oops(false);

  if (false) {
    for (frame f = thread->last_frame(); !f.is_entry_frame(); f = f.sender(&map)) {
      f.print_on(&ls);
    }
  } else {
    map.set_skip_missing(true);
    ResetNoHandleMark rnhm;
    ResourceMark rm;
    HandleMark hm(Thread::current());
    FrameValues values;

    int i = 0;
    int post_entry = -1;
    for (frame f = thread->last_frame(); !f.is_entry_frame(); f = f.sender(&map)) {
      f.describe(values, i++, &map);
      if (post_entry >= 0 || Continuation::is_continuation_enterSpecial(f))
        post_entry++;
      if (post_entry >= show_entry_callers)
        break;
    }
    values.print_on(thread, &ls);
  }

  ls.print_cr("======= end frames =========");
}
#endif // ASSERT

#include CPU_HEADER_INLINE(continuationFreezeThaw)

#ifdef ASSERT
static void print_frame_layout(const frame& f, bool callee_complete, outputStream* st) {
  ResourceMark rm;
  FrameValues values;
  assert(f.get_cb() != nullptr, "");
  RegisterMap map(f.is_heap_frame() ?
                    nullptr :
                    JavaThread::current(),
                  RegisterMap::UpdateMap::include,
                  RegisterMap::ProcessFrames::skip,
                  RegisterMap::WalkContinuation::skip);
  map.set_include_argument_oops(false);
  map.set_skip_missing(true);
  if (callee_complete) {
    frame::update_map_with_saved_link(&map, ContinuationHelper::Frame::callee_link_address(f));
  }
  const_cast<frame&>(f).describe(values, 0, &map);
  values.print_on(static_cast<JavaThread*>(nullptr), st);
}
#endif

static address thaw_entry   = nullptr;
static address freeze_entry = nullptr;

address Continuation::thaw_entry() {
  return ::thaw_entry;
}

address Continuation::freeze_entry() {
  return ::freeze_entry;
}

class ConfigResolve {
public:
  static void resolve() { resolve_compressed(); }

  static void resolve_compressed() {
    UseCompressedOops ? resolve_gc<true>()
                      : resolve_gc<false>();
  }

private:
  template <bool use_compressed>
  static void resolve_gc() {
    BarrierSet* bs = BarrierSet::barrier_set();
    assert(bs != nullptr, "freeze/thaw invoked before BarrierSet is set");
    switch (bs->kind()) {
#define BARRIER_SET_RESOLVE_BARRIER_CLOSURE(bs_name)                    \
      case BarrierSet::bs_name: {                                       \
        resolve<use_compressed, typename BarrierSet::GetType<BarrierSet::bs_name>::type>(); \
      }                                                                 \
        break;
      FOR_EACH_CONCRETE_BARRIER_SET_DO(BARRIER_SET_RESOLVE_BARRIER_CLOSURE)
#undef BARRIER_SET_RESOLVE_BARRIER_CLOSURE

    default:
      fatal("BarrierSet resolving not implemented");
    };
  }

  template <bool use_compressed, typename BarrierSetT>
  static void resolve() {
    typedef Config<use_compressed ? oop_kind::NARROW : oop_kind::WIDE, BarrierSetT> SelectedConfigT;

    freeze_entry = (address)freeze<SelectedConfigT>;

    // If we wanted, we could templatize by kind and have three different thaw entries
    thaw_entry   = (address)thaw<SelectedConfigT>;
  }
};

void Continuation::init() {
  ConfigResolve::resolve();
}
