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

#ifndef SHARE_VM_RUNTIME_CONTINUATIONWRAPPER_INLINE_HPP
#define SHARE_VM_RUNTIME_CONTINUATIONWRAPPER_INLINE_HPP

// There is no continuationWrapper.hpp file

#include "classfile/javaClasses.inline.hpp"
#include "oops/oop.inline.hpp"
#include "memory/allocation.hpp"
#include "oops/oopsHierarchy.hpp"
#include "oops/stackChunkOop.hpp"
#include "runtime/continuationEntry.inline.hpp"
#include "runtime/continuationJavaClasses.inline.hpp"
#include "runtime/javaThread.hpp"

/////////////////////////////////////////////////////////////////////

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
  bool               _done;

  ContinuationWrapper(const ContinuationWrapper& cont); // no copy constructor

private:
  DEBUG_ONLY(Thread* _current_thread;)
  friend class SafepointOp;

  void disallow_safepoint() {
    #ifdef ASSERT
      assert(!_done, "");
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
      if (!_done && _current_thread->is_Java_thread()) {
        JavaThread::cast(_current_thread)->dec_no_safepoint_count();
      }
    #endif
  }

  ContinuationWrapper(JavaThread* thread, ContinuationEntry* entry, oop continuation);

public:
  void done() {
    allow_safepoint(); // must be done first
    _done = true;
    *reinterpret_cast<intptr_t*>(&_continuation) = badHeapOopVal;
    *reinterpret_cast<intptr_t*>(&_tail) = badHeapOopVal;
  }

  class SafepointOp : public StackObj {
    ContinuationWrapper& _cont;
    Handle _conth;
  public:
    SafepointOp(Thread* current, ContinuationWrapper& cont)
      : _cont(cont), _conth(current, cont._continuation) {
      _cont.allow_safepoint();
    }
    inline ~SafepointOp() { // reload oops
      _cont._continuation = _conth();
      _cont._tail = jdk_internal_vm_Continuation::tail(_cont._continuation);
      _cont.disallow_safepoint();
    }
  };

public:
  ~ContinuationWrapper() { allow_safepoint(); }

  ContinuationWrapper(JavaThread* thread, oop continuation);
  ContinuationWrapper(oop continuation);
  ContinuationWrapper(const RegisterMap* map);

  JavaThread* thread() const         { return _thread; }
  oop continuation()                 { return _continuation; }
  stackChunkOop tail() const         { return _tail; }
  void set_tail(stackChunkOop chunk) { _tail = chunk; }

  inline bool is_preempted();
  inline void read();
  inline void write();

  NOT_PRODUCT(intptr_t hash();)

  ContinuationEntry* entry() const { return _entry; }
  bool is_mounted()   const { return _entry != nullptr; }
  intptr_t* entrySP() const { return _entry->entry_sp(); }
  intptr_t* entryFP() const { return _entry->entry_fp(); }
  address   entryPC() const { return _entry->entry_pc(); }
  int argsize()       const { assert(_entry->argsize() >= 0, ""); return _entry->argsize(); }
  int entry_frame_extension() const {
    // the entry frame is extended if the bottom frame has stack arguments
    assert(_entry->argsize() >= 0, "");
    return _entry->argsize() == 0 ? _entry->argsize() : _entry->argsize() + frame::metadata_words_at_top;
  }
  void set_argsize(int value) { _entry->set_argsize(value); }

  bool is_empty() const { return last_nonempty_chunk() == nullptr; }
  const frame last_frame();

  inline stackChunkOop last_nonempty_chunk() const;
  stackChunkOop find_chunk_by_address(void* p) const;

#ifdef ASSERT
  bool is_entry_frame(const frame& f);
  bool chunk_invariant() const;
#endif
};

inline ContinuationWrapper::ContinuationWrapper(JavaThread* thread, ContinuationEntry* entry, oop continuation)
  : _thread(thread), _entry(entry), _continuation(continuation), _done(false) {
  assert(oopDesc::is_oop(_continuation),
         "Invalid continuation object: " INTPTR_FORMAT, p2i((void*)_continuation));
  disallow_safepoint();
  read();
}

inline ContinuationWrapper::ContinuationWrapper(JavaThread* thread, oop continuation)
  : ContinuationWrapper(thread, thread->last_continuation(), continuation) {}

inline ContinuationWrapper::ContinuationWrapper(oop continuation)
  : ContinuationWrapper(nullptr, nullptr, continuation) {}

inline bool ContinuationWrapper::is_preempted() {
  return jdk_internal_vm_Continuation::is_preempted(_continuation);
}

inline void ContinuationWrapper::read() {
  _tail  = jdk_internal_vm_Continuation::tail(_continuation);
}

inline void ContinuationWrapper::write() {
  assert(oopDesc::is_oop(_continuation), "bad oop");
  assert(oopDesc::is_oop_or_null(_tail), "bad oop");
  jdk_internal_vm_Continuation::set_tail(_continuation, _tail);
}

inline stackChunkOop ContinuationWrapper::last_nonempty_chunk() const {
  assert(chunk_invariant(), "");
  stackChunkOop chunk = _tail;
  if (chunk != nullptr && chunk->is_empty()) {
    chunk = chunk->parent();
  }
  assert(chunk == nullptr || !chunk->is_empty(), "");
  return chunk;
}

#endif // SHARE_VM_RUNTIME_CONTINUATIONWRAPPER_INLINE_HPP
