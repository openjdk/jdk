/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "jfr/jfrEvents.hpp"
#include "runtime/continuation.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/thread.hpp"

#define CONT_JFR false // emit low-level JFR events that count slow/fast path for continuation peformance debugging only
#if CONT_JFR
  #define CONT_JFR_ONLY(code) code
#else
  #define CONT_JFR_ONLY(code)
#endif

// Intermediary to the jdk.internal.vm.Continuation objects and ContinuationEntry
// This object is created when we begin a operation for a continuation, and is destroyed when the operation completes.
// Contents are read from the Java object at the entry points of this module, and written at exit or calls into Java
// It also serves as a custom NoSafepointVerifier
class ContinuationWrapper : public StackObj {
private:
  JavaThread* const  _thread;   // Thread being frozen/thawed
  ContinuationEntry* _entry;
  // These oops are managed by SafepointOp
  oop                _continuation;  // jdk.internal.vm.Continuation instance
  stackChunkOop      _tail;

#if CONT_JFR // Profiling data for the JFR event
  short _e_size;
  short _e_num_interpreted_frames;
#endif

  ContinuationWrapper(const ContinuationWrapper& cont); // no copy constructor

private:
  DEBUG_ONLY(Thread* _current_thread;)
  friend class SafepointOp;

  void disallow_safepoint() {
    #ifdef ASSERT
      assert(_continuation != nullptr, "");
      _current_thread = Thread::current();
      if (_current_thread->is_Java_thread()) {
        JavaThread::cast(_current_thread)->inc_no_safepoint_count();
      }
    #endif
  }

  void allow_safepoint() {
    #ifdef ASSERT
      // we could have already allowed safepoints in done
      if (_continuation != nullptr && _current_thread->is_Java_thread()) {
        JavaThread::cast(_current_thread)->dec_no_safepoint_count();
      }
    #endif
  }

public:
  void done() {
    allow_safepoint(); // must be done first
    _continuation = nullptr;
    _tail = (stackChunkOop)badOop;
  }

  class SafepointOp : public StackObj {
    ContinuationWrapper& _cont;
    Handle _conth;
  public:
    SafepointOp(Thread* current, ContinuationWrapper& cont)
      : _cont(cont), _conth(current, cont._continuation) {
      _cont.allow_safepoint();
    }
    ~SafepointOp() { // reload oops
      _cont._continuation = _conth();
      if (_cont._tail != nullptr) {
        _cont._tail = jdk_internal_vm_Continuation::tail(_cont._continuation);
      }
      _cont.disallow_safepoint();
    }
  };

public:
  ~ContinuationWrapper() { allow_safepoint(); }

  inline ContinuationWrapper(JavaThread* thread, oop continuation);
  inline ContinuationWrapper(oop continuation);
  inline ContinuationWrapper(const RegisterMap* map);

  JavaThread* thread() const         { return _thread; }
  oop continuation()                 { return _continuation; }
  stackChunkOop tail() const         { return _tail; }
  void set_tail(stackChunkOop chunk) { _tail = chunk; }

  oop parent()                   { return jdk_internal_vm_Continuation::parent(_continuation); }
  bool is_preempted()            { return jdk_internal_vm_Continuation::is_preempted(_continuation); }
  void set_preempted(bool value) { jdk_internal_vm_Continuation::set_preempted(_continuation, value); }
  void read()                    { _tail  = jdk_internal_vm_Continuation::tail(_continuation); }
  void write() {
    assert(oopDesc::is_oop(_continuation), "bad oop");
    assert(oopDesc::is_oop_or_null(_tail), "bad oop");
    jdk_internal_vm_Continuation::set_tail(_continuation, _tail);
  }

  NOT_PRODUCT(intptr_t hash()    { return Thread::current()->is_Java_thread() ? _continuation->identity_hash() : -1; })

  ContinuationEntry* entry() const { return _entry; }
  bool is_mounted()   const { return _entry != nullptr; }
  intptr_t* entrySP() const { return _entry->entry_sp(); }
  intptr_t* entryFP() const { return _entry->entry_fp(); }
  address   entryPC() const { return _entry->entry_pc(); }
  int argsize()       const { assert(_entry->argsize() >= 0, ""); return _entry->argsize(); }
  void set_argsize(int value) { _entry->set_argsize(value); }

  bool is_empty() const { return last_nonempty_chunk() == nullptr; }
  inline const frame last_frame();

  stackChunkOop last_nonempty_chunk() const { return nonempty_chunk(_tail); }
  inline stackChunkOop nonempty_chunk(stackChunkOop chunk) const;
  inline stackChunkOop find_chunk_by_address(void* p) const;

#if CONT_JFR
  inline void record_interpreted_frame() { _e_num_interpreted_frames++; }
  inline void record_size_copied(int size) { _e_size += size << LogBytesPerWord; }
  template<typename Event> void post_jfr_event(Event *e, JavaThread* jt);
#endif

#ifdef ASSERT
  inline bool is_entry_frame(const frame& f);
  bool chunk_invariant(outputStream* st);
#endif
};

ContinuationWrapper::ContinuationWrapper(JavaThread* thread, oop continuation)
  : _thread(thread), _entry(thread->last_continuation()), _continuation(continuation)
#if CONT_JFR
  , _e_size(0), _e_num_interpreted_frames(0)
#endif
  {
  assert(oopDesc::is_oop(_continuation),
         "Invalid continuation object: " INTPTR_FORMAT, p2i((void*)_continuation));
  assert(_continuation == _entry->cont_oop(), "cont: " INTPTR_FORMAT " entry: " INTPTR_FORMAT " entry_sp: "
         INTPTR_FORMAT, p2i((oopDesc*)_continuation), p2i((oopDesc*)_entry->cont_oop()), p2i(entrySP()));
  disallow_safepoint();
  read();
}

ContinuationWrapper::ContinuationWrapper(oop continuation)
  : _thread(nullptr), _entry(nullptr), _continuation(continuation)
#if CONT_JFR
  , _e_size(0), _e_num_interpreted_frames(0)
#endif
  {
  assert(oopDesc::is_oop(_continuation),
         "Invalid continuation object: " INTPTR_FORMAT, p2i((void*)_continuation));
  disallow_safepoint();
  read();
}

ContinuationWrapper::ContinuationWrapper(const RegisterMap* map)
  : _thread(map->thread()),
    _entry(Continuation::get_continuation_entry_for_continuation(_thread, map->stack_chunk()->cont())),
    _continuation(map->stack_chunk()->cont())
#if CONT_JFR
  , _e_size(0), _e_num_interpreted_frames(0)
#endif
  {
  assert(oopDesc::is_oop(_continuation),"Invalid cont: " INTPTR_FORMAT, p2i((void*)_continuation));
  assert(_entry == nullptr || _continuation == _entry->cont_oop(),
    "cont: " INTPTR_FORMAT " entry: " INTPTR_FORMAT " entry_sp: " INTPTR_FORMAT,
    p2i( (oopDesc*)_continuation), p2i((oopDesc*)_entry->cont_oop()), p2i(entrySP()));
  disallow_safepoint();
  read();
}

const frame ContinuationWrapper::last_frame() {
  stackChunkOop chunk = last_nonempty_chunk();
  if (chunk == nullptr) {
    return frame();
  }
  return StackChunkFrameStream<ChunkFrames::Mixed>(chunk).to_frame();
}

inline stackChunkOop ContinuationWrapper::nonempty_chunk(stackChunkOop chunk) const {
  while (chunk != nullptr && chunk->is_empty()) {
    chunk = chunk->parent();
  }
  return chunk;
}

stackChunkOop ContinuationWrapper::find_chunk_by_address(void* p) const {
  for (stackChunkOop chunk = tail(); chunk != nullptr; chunk = chunk->parent()) {
    if (chunk->is_in_chunk(p)) {
      assert(chunk->is_usable_in_chunk(p), "");
      return chunk;
    }
  }
  return nullptr;
}

#if CONT_JFR
template<typename Event> void ContinuationWrapper::post_jfr_event(Event* e, JavaThread* jt) {
  if (e->should_commit()) {
    log_develop_trace(continuations)("JFR event: iframes: %d size: %d", _e_num_interpreted_frames, _e_size);
    e->set_carrierThread(JFR_JVM_THREAD_ID(jt));
    e->set_contClass(_continuation->klass());
    e->set_numIFrames(_e_num_interpreted_frames);
    e->set_size(_e_size);
    e->commit();
  }
}
#endif

#ifdef ASSERT
inline bool ContinuationWrapper::is_entry_frame(const frame& f) {
  return f.sp() == entrySP();
}
#endif // ASSERT
