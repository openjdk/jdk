/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "compiler/compileBroker.hpp"
#include "gc/serial/markSweep.inline.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/specialized_oop_closures.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/instanceClassLoaderKlass.inline.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/instanceMirrorKlass.inline.hpp"
#include "oops/instanceRefKlass.inline.hpp"
#include "oops/methodData.hpp"
#include "oops/objArrayKlass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/macros.hpp"
#include "utilities/stack.inline.hpp"
#if INCLUDE_ALL_GCS
#include "gc/g1/g1StringDedup.hpp"
#endif // INCLUDE_ALL_GCS

uint                    MarkSweep::_total_invocations = 0;

Stack<oop, mtGC>              MarkSweep::_marking_stack;
Stack<ObjArrayTask, mtGC>     MarkSweep::_objarray_stack;

Stack<oop, mtGC>              MarkSweep::_preserved_oop_stack;
Stack<markOop, mtGC>          MarkSweep::_preserved_mark_stack;
size_t                  MarkSweep::_preserved_count = 0;
size_t                  MarkSweep::_preserved_count_max = 0;
PreservedMark*          MarkSweep::_preserved_marks = NULL;
ReferenceProcessor*     MarkSweep::_ref_processor   = NULL;
STWGCTimer*             MarkSweep::_gc_timer        = NULL;
SerialOldTracer*        MarkSweep::_gc_tracer       = NULL;

MarkSweep::FollowRootClosure  MarkSweep::follow_root_closure;

MarkAndPushClosure            MarkSweep::mark_and_push_closure;
CLDToOopClosure               MarkSweep::follow_cld_closure(&mark_and_push_closure);
CLDToOopClosure               MarkSweep::adjust_cld_closure(&adjust_pointer_closure);

inline void MarkSweep::mark_object(oop obj) {
#if INCLUDE_ALL_GCS
  if (G1StringDedup::is_enabled()) {
    // We must enqueue the object before it is marked
    // as we otherwise can't read the object's age.
    G1StringDedup::enqueue_from_mark(obj);
  }
#endif
  // some marks may contain information we need to preserve so we store them away
  // and overwrite the mark.  We'll restore it at the end of markSweep.
  markOop mark = obj->mark();
  obj->set_mark(markOopDesc::prototype()->set_marked());

  if (mark->must_be_preserved(obj)) {
    preserve_mark(obj, mark);
  }
}

template <class T> inline void MarkSweep::mark_and_push(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
    if (!obj->mark()->is_marked() &&
        !is_archive_object(obj)) {
      mark_object(obj);
      _marking_stack.push(obj);
    }
  }
}

inline void MarkSweep::follow_klass(Klass* klass) {
  oop op = klass->klass_holder();
  MarkSweep::mark_and_push(&op);
}

inline void MarkSweep::follow_cld(ClassLoaderData* cld) {
  MarkSweep::follow_cld_closure.do_cld(cld);
}

template <typename T>
inline void MarkAndPushClosure::do_oop_nv(T* p)                 { MarkSweep::mark_and_push(p); }
void MarkAndPushClosure::do_oop(oop* p)                         { do_oop_nv(p); }
void MarkAndPushClosure::do_oop(narrowOop* p)                   { do_oop_nv(p); }
inline bool MarkAndPushClosure::do_metadata_nv()                { return true; }
bool MarkAndPushClosure::do_metadata()                          { return do_metadata_nv(); }
inline void MarkAndPushClosure::do_klass_nv(Klass* k)           { MarkSweep::follow_klass(k); }
void MarkAndPushClosure::do_klass(Klass* k)                     { do_klass_nv(k); }
inline void MarkAndPushClosure::do_cld_nv(ClassLoaderData* cld) { MarkSweep::follow_cld(cld); }
void MarkAndPushClosure::do_cld(ClassLoaderData* cld)           { do_cld_nv(cld); }

template <class T> inline void MarkSweep::KeepAliveClosure::do_oop_work(T* p) {
  mark_and_push(p);
}

void MarkSweep::push_objarray(oop obj, size_t index) {
  ObjArrayTask task(obj, index);
  assert(task.is_valid(), "bad ObjArrayTask");
  _objarray_stack.push(task);
}

inline void MarkSweep::follow_array(objArrayOop array) {
  MarkSweep::follow_klass(array->klass());
  // Don't push empty arrays to avoid unnecessary work.
  if (array->length() > 0) {
    MarkSweep::push_objarray(array, 0);
  }
}

inline void MarkSweep::follow_object(oop obj) {
  assert(obj->is_gc_marked(), "should be marked");
  if (obj->is_objArray()) {
    // Handle object arrays explicitly to allow them to
    // be split into chunks if needed.
    MarkSweep::follow_array((objArrayOop)obj);
  } else {
    obj->oop_iterate(&mark_and_push_closure);
  }
}

void MarkSweep::follow_array_chunk(objArrayOop array, int index) {
  const int len = array->length();
  const int beg_index = index;
  assert(beg_index < len || len == 0, "index too large");

  const int stride = MIN2(len - beg_index, (int) ObjArrayMarkingStride);
  const int end_index = beg_index + stride;

  array->oop_iterate_range(&mark_and_push_closure, beg_index, end_index);

  if (end_index < len) {
    MarkSweep::push_objarray(array, end_index); // Push the continuation.
  }
}

void MarkSweep::follow_stack() {
  do {
    while (!_marking_stack.is_empty()) {
      oop obj = _marking_stack.pop();
      assert (obj->is_gc_marked(), "p must be marked");
      follow_object(obj);
    }
    // Process ObjArrays one at a time to avoid marking stack bloat.
    if (!_objarray_stack.is_empty()) {
      ObjArrayTask task = _objarray_stack.pop();
      follow_array_chunk(objArrayOop(task.obj()), task.index());
    }
  } while (!_marking_stack.is_empty() || !_objarray_stack.is_empty());
}

MarkSweep::FollowStackClosure MarkSweep::follow_stack_closure;

void MarkSweep::FollowStackClosure::do_void() { follow_stack(); }

template <class T> inline void MarkSweep::follow_root(T* p) {
  assert(!Universe::heap()->is_in_reserved(p),
         "roots shouldn't be things within the heap");
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
    if (!obj->mark()->is_marked() &&
        !is_archive_object(obj)) {
      mark_object(obj);
      follow_object(obj);
    }
  }
  follow_stack();
}

void MarkSweep::FollowRootClosure::do_oop(oop* p)       { follow_root(p); }
void MarkSweep::FollowRootClosure::do_oop(narrowOop* p) { follow_root(p); }

void PreservedMark::adjust_pointer() {
  MarkSweep::adjust_pointer(&_obj);
}

void PreservedMark::restore() {
  _obj->set_mark(_mark);
}

// We preserve the mark which should be replaced at the end and the location
// that it will go.  Note that the object that this markOop belongs to isn't
// currently at that address but it will be after phase4
void MarkSweep::preserve_mark(oop obj, markOop mark) {
  // We try to store preserved marks in the to space of the new generation since
  // this is storage which should be available.  Most of the time this should be
  // sufficient space for the marks we need to preserve but if it isn't we fall
  // back to using Stacks to keep track of the overflow.
  if (_preserved_count < _preserved_count_max) {
    _preserved_marks[_preserved_count++].init(obj, mark);
  } else {
    _preserved_mark_stack.push(mark);
    _preserved_oop_stack.push(obj);
  }
}

void MarkSweep::set_ref_processor(ReferenceProcessor* rp) {
  _ref_processor = rp;
  mark_and_push_closure.set_ref_processor(_ref_processor);
}

MarkSweep::AdjustPointerClosure MarkSweep::adjust_pointer_closure;

template <typename T>
void MarkSweep::AdjustPointerClosure::do_oop_nv(T* p)      { adjust_pointer(p); }
void MarkSweep::AdjustPointerClosure::do_oop(oop* p)       { do_oop_nv(p); }
void MarkSweep::AdjustPointerClosure::do_oop(narrowOop* p) { do_oop_nv(p); }

void MarkSweep::adjust_marks() {
  assert( _preserved_oop_stack.size() == _preserved_mark_stack.size(),
         "inconsistent preserved oop stacks");

  // adjust the oops we saved earlier
  for (size_t i = 0; i < _preserved_count; i++) {
    _preserved_marks[i].adjust_pointer();
  }

  // deal with the overflow stack
  StackIterator<oop, mtGC> iter(_preserved_oop_stack);
  while (!iter.is_empty()) {
    oop* p = iter.next_addr();
    adjust_pointer(p);
  }
}

void MarkSweep::restore_marks() {
  assert(_preserved_oop_stack.size() == _preserved_mark_stack.size(),
         "inconsistent preserved oop stacks");
  log_trace(gc)("Restoring " SIZE_FORMAT " marks", _preserved_count + _preserved_oop_stack.size());

  // restore the marks we saved earlier
  for (size_t i = 0; i < _preserved_count; i++) {
    _preserved_marks[i].restore();
  }

  // deal with the overflow
  while (!_preserved_oop_stack.is_empty()) {
    oop obj       = _preserved_oop_stack.pop();
    markOop mark  = _preserved_mark_stack.pop();
    obj->set_mark(mark);
  }
}

MarkSweep::IsAliveClosure   MarkSweep::is_alive;

bool MarkSweep::IsAliveClosure::do_object_b(oop p) { return p->is_gc_marked() || is_archive_object(p); }

MarkSweep::KeepAliveClosure MarkSweep::keep_alive;

void MarkSweep::KeepAliveClosure::do_oop(oop* p)       { MarkSweep::KeepAliveClosure::do_oop_work(p); }
void MarkSweep::KeepAliveClosure::do_oop(narrowOop* p) { MarkSweep::KeepAliveClosure::do_oop_work(p); }

void marksweep_init() {
  MarkSweep::_gc_timer = new (ResourceObj::C_HEAP, mtGC) STWGCTimer();
  MarkSweep::_gc_tracer = new (ResourceObj::C_HEAP, mtGC) SerialOldTracer();
}

int InstanceKlass::oop_ms_adjust_pointers(oop obj) {
  int size = size_helper();
  oop_oop_iterate_oop_maps<true>(obj, &MarkSweep::adjust_pointer_closure);
  return size;
}

int InstanceMirrorKlass::oop_ms_adjust_pointers(oop obj) {
  int size = oop_size(obj);
  InstanceKlass::oop_ms_adjust_pointers(obj);

  oop_oop_iterate_statics<true>(obj, &MarkSweep::adjust_pointer_closure);
  return size;
}

int InstanceClassLoaderKlass::oop_ms_adjust_pointers(oop obj) {
  return InstanceKlass::oop_ms_adjust_pointers(obj);
}

#ifdef ASSERT
template <class T> static void trace_reference_gc(const char *s, oop obj,
                                                  T* referent_addr,
                                                  T* next_addr,
                                                  T* discovered_addr) {
  log_develop_trace(gc, ref)("%s obj " PTR_FORMAT, s, p2i(obj));
  log_develop_trace(gc, ref)("     referent_addr/* " PTR_FORMAT " / " PTR_FORMAT,
                             p2i(referent_addr), p2i(referent_addr ? (address)oopDesc::load_decode_heap_oop(referent_addr) : NULL));
  log_develop_trace(gc, ref)("     next_addr/* " PTR_FORMAT " / " PTR_FORMAT,
                             p2i(next_addr), p2i(next_addr ? (address)oopDesc::load_decode_heap_oop(next_addr) : NULL));
  log_develop_trace(gc, ref)("     discovered_addr/* " PTR_FORMAT " / " PTR_FORMAT,
                             p2i(discovered_addr), p2i(discovered_addr ? (address)oopDesc::load_decode_heap_oop(discovered_addr) : NULL));
}
#endif

template <class T> void static adjust_object_specialized(oop obj) {
  T* referent_addr = (T*)java_lang_ref_Reference::referent_addr(obj);
  MarkSweep::adjust_pointer(referent_addr);
  T* next_addr = (T*)java_lang_ref_Reference::next_addr(obj);
  MarkSweep::adjust_pointer(next_addr);
  T* discovered_addr = (T*)java_lang_ref_Reference::discovered_addr(obj);
  MarkSweep::adjust_pointer(discovered_addr);
  debug_only(trace_reference_gc("InstanceRefKlass::oop_ms_adjust_pointers", obj,
                                referent_addr, next_addr, discovered_addr);)
}

int InstanceRefKlass::oop_ms_adjust_pointers(oop obj) {
  int size = size_helper();
  InstanceKlass::oop_ms_adjust_pointers(obj);

  if (UseCompressedOops) {
    adjust_object_specialized<narrowOop>(obj);
  } else {
    adjust_object_specialized<oop>(obj);
  }
  return size;
}

int ObjArrayKlass::oop_ms_adjust_pointers(oop obj) {
  assert(obj->is_objArray(), "obj must be obj array");
  objArrayOop a = objArrayOop(obj);
  // Get size before changing pointers.
  // Don't call size() or oop_size() since that is a virtual call.
  int size = a->object_size();
  oop_oop_iterate_elements<true>(a, &MarkSweep::adjust_pointer_closure);
  return size;
}

int TypeArrayKlass::oop_ms_adjust_pointers(oop obj) {
  assert(obj->is_typeArray(), "must be a type array");
  typeArrayOop t = typeArrayOop(obj);
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::TypeArrayKlass never moves.
  return t->object_size();
}

// Generate MS specialized oop_oop_iterate functions.
SPECIALIZED_OOP_OOP_ITERATE_CLOSURES_MS(ALL_KLASS_OOP_OOP_ITERATE_DEFN)
