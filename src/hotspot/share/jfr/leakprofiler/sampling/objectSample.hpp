/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_JFR_LEAKPROFILER_SAMPLING_OBJECTSAMPLE_HPP
#define SHARE_VM_JFR_LEAKPROFILER_SAMPLING_OBJECTSAMPLE_HPP

#include "jfr/recorder/checkpoint/jfrCheckpointBlob.hpp"
#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "memory/allocation.hpp"
#include "oops/oop.hpp"
#include "utilities/ticks.hpp"
/*
 * Handle for diagnosing Java memory leaks.
 *
 * The class tracks the time the object was
 * allocated, the thread and the stack trace.
 */
class ObjectSample : public JfrCHeapObj {
  friend class ObjectSampler;
  friend class SampleList;
 private:
  ObjectSample* _next;
  ObjectSample* _previous;
  JfrCheckpointBlobHandle _thread_cp;
  JfrCheckpointBlobHandle _klass_cp;
  oop _object;
  Ticks _allocation_time;
  traceid _stack_trace_id;
  traceid _thread_id;
  int _index;
  size_t _span;
  size_t _allocated;
  size_t _heap_used_at_last_gc;
  unsigned int _stack_trace_hash;
  bool _dead;

  void set_dead() {
    _dead = true;
  }

  void release_references() {
    if (_thread_cp.valid()) {
      _thread_cp.~JfrCheckpointBlobHandle();
    }
    if (_klass_cp.valid()) {
      _klass_cp.~JfrCheckpointBlobHandle();
    }
  }

  void reset() {
    set_stack_trace_id(0);
    set_stack_trace_hash(0),
    release_references();
    _dead = false;
  }

 public:
  ObjectSample() : _next(NULL),
                   _previous(NULL),
                   _thread_cp(),
                   _klass_cp(),
                   _object(NULL),
                   _allocation_time(),
                   _stack_trace_id(0),
                   _thread_id(0),
                   _index(0),
                   _span(0),
                   _allocated(0),
                   _heap_used_at_last_gc(0),
                   _stack_trace_hash(0),
                   _dead(false) {}

  ObjectSample* next() const {
    return _next;
  }

  void set_next(ObjectSample* next) {
    _next = next;
  }

  ObjectSample* prev() const {
    return _previous;
  }

  void set_prev(ObjectSample* prev) {
    _previous = prev;
  }

  bool is_dead() const {
    return _dead;
  }

  const oop object() const {
    return _object;
  }

  const oop* object_addr() const {
    return &_object;
  }

  void set_object(oop object) {
    _object = object;
  }

  const Klass* klass() const {
    assert(_object != NULL, "invariant");
    return _object->klass();
  }

  int index() const {
    return _index;
  }

  void set_index(int index) {
    _index = index;
  }

  size_t span() const {
    return _span;
  }

  void set_span(size_t span) {
    _span = span;
  }

  void add_span(size_t span) {
    _span += span;
  }

  size_t allocated() const {
    return _allocated;
  }

  void set_allocated(size_t size) {
    _allocated = size;
  }

  const Ticks& allocation_time() const {
    return _allocation_time;
  }

  const void set_allocation_time(const JfrTicks& time) {
    _allocation_time = Ticks(time.value());
  }

  void set_heap_used_at_last_gc(size_t heap_used) {
    _heap_used_at_last_gc = heap_used;
  }

  size_t heap_used_at_last_gc() const {
    return _heap_used_at_last_gc;
  }

  bool has_stack_trace() const {
    return stack_trace_id() != 0;
  }

  traceid stack_trace_id() const {
    return _stack_trace_id;
  }

  void set_stack_trace_id(traceid id) {
    _stack_trace_id = id;
  }

  unsigned int stack_trace_hash() const {
    return _stack_trace_hash;
  }

  void set_stack_trace_hash(unsigned int hash) {
    _stack_trace_hash = hash;
  }

  bool has_thread() const {
    return _thread_id != 0;
  }

  traceid thread_id() const {
    return _thread_id;
  }

  void set_thread_id(traceid id) {
    _thread_id = id;
  }

  bool is_alive_and_older_than(jlong time_stamp) const {
    return !is_dead() && (JfrTime::is_ft_enabled() ?
      _allocation_time.ft_value() : _allocation_time.value()) < time_stamp;
  }

  const JfrCheckpointBlobHandle& thread_checkpoint() const {
    return _thread_cp;
  }

  bool has_thread_checkpoint() const {
    return _thread_cp.valid();
  }

  // JfrCheckpointBlobHandle assignment operator
  // maintains proper reference counting
  void set_thread_checkpoint(const JfrCheckpointBlobHandle& ref) {
    if (_thread_cp != ref) {
      _thread_cp = ref;
    }
  }

  const JfrCheckpointBlobHandle& klass_checkpoint() const {
    return _klass_cp;
  }

  bool has_klass_checkpoint() const {
    return _klass_cp.valid();
  }

  void set_klass_checkpoint(const JfrCheckpointBlobHandle& ref) {
    if (_klass_cp != ref) {
      if (_klass_cp.valid()) {
        _klass_cp->set_next(ref);
        return;
      }
      _klass_cp = ref;
    }
  }
};

#endif // SHARE_VM_JFR_LEAKPROFILER_SAMPLING_OBJECTSAMPLE_HPP
