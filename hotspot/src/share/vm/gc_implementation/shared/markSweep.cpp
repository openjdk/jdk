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
#include "gc_implementation/shared/gcTimer.hpp"
#include "gc_implementation/shared/gcTrace.hpp"
#include "gc_implementation/shared/markSweep.inline.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/instanceMirrorKlass.inline.hpp"
#include "oops/methodData.hpp"
#include "oops/objArrayKlass.inline.hpp"
#include "oops/oop.inline.hpp"

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

void MarkSweep::FollowRootClosure::do_oop(oop* p)       { follow_root(p); }
void MarkSweep::FollowRootClosure::do_oop(narrowOop* p) { follow_root(p); }

MarkSweep::MarkAndPushClosure MarkSweep::mark_and_push_closure;
CLDToOopClosure               MarkSweep::follow_cld_closure(&mark_and_push_closure);
CLDToOopClosure               MarkSweep::adjust_cld_closure(&adjust_pointer_closure);

template <typename T>
void MarkSweep::MarkAndPushClosure::do_oop_nv(T* p)       { mark_and_push(p); }
void MarkSweep::MarkAndPushClosure::do_oop(oop* p)        { do_oop_nv(p); }
void MarkSweep::MarkAndPushClosure::do_oop(narrowOop* p)  { do_oop_nv(p); }

void MarkSweep::follow_class_loader(ClassLoaderData* cld) {
  MarkSweep::follow_cld_closure.do_cld(cld);
}

void InstanceKlass::oop_ms_follow_contents(oop obj) {
  assert(obj != NULL, "can't follow the content of NULL object");
  MarkSweep::follow_klass(obj->klass());

  oop_oop_iterate_oop_maps<true>(obj, &MarkSweep::mark_and_push_closure);
}

void InstanceMirrorKlass::oop_ms_follow_contents(oop obj) {
  InstanceKlass::oop_ms_follow_contents(obj);

  // Follow the klass field in the mirror
  Klass* klass = java_lang_Class::as_Klass(obj);
  if (klass != NULL) {
    // An anonymous class doesn't have its own class loader, so the call
    // to follow_klass will mark and push its java mirror instead of the
    // class loader. When handling the java mirror for an anonymous class
    // we need to make sure its class loader data is claimed, this is done
    // by calling follow_class_loader explicitly. For non-anonymous classes
    // the call to follow_class_loader is made when the class loader itself
    // is handled.
    if (klass->oop_is_instance() && InstanceKlass::cast(klass)->is_anonymous()) {
      MarkSweep::follow_class_loader(klass->class_loader_data());
    } else {
      MarkSweep::follow_klass(klass);
    }
  } else {
    // If klass is NULL then this a mirror for a primitive type.
    // We don't have to follow them, since they are handled as strong
    // roots in Universe::oops_do.
    assert(java_lang_Class::is_primitive(obj), "Sanity check");
  }

  oop_oop_iterate_statics<true>(obj, &MarkSweep::mark_and_push_closure);
}

void InstanceClassLoaderKlass::oop_ms_follow_contents(oop obj) {
  InstanceKlass::oop_ms_follow_contents(obj);

  ClassLoaderData * const loader_data = java_lang_ClassLoader::loader_data(obj);

  // We must NULL check here, since the class loader
  // can be found before the loader data has been set up.
  if(loader_data != NULL) {
    MarkSweep::follow_class_loader(loader_data);
  }
}

template <class T>
static void oop_ms_follow_contents_specialized(InstanceRefKlass* klass, oop obj) {
  T* referent_addr = (T*)java_lang_ref_Reference::referent_addr(obj);
  T heap_oop = oopDesc::load_heap_oop(referent_addr);
  debug_only(
    if(TraceReferenceGC && PrintGCDetails) {
      gclog_or_tty->print_cr("InstanceRefKlass::oop_ms_follow_contents_specialized " PTR_FORMAT, p2i(obj));
    }
  )
  if (!oopDesc::is_null(heap_oop)) {
    oop referent = oopDesc::decode_heap_oop_not_null(heap_oop);
    if (!referent->is_gc_marked() &&
        MarkSweep::ref_processor()->discover_reference(obj, klass->reference_type())) {
      // reference was discovered, referent will be traversed later
      klass->InstanceKlass::oop_ms_follow_contents(obj);
      debug_only(
        if(TraceReferenceGC && PrintGCDetails) {
          gclog_or_tty->print_cr("       Non NULL enqueued " PTR_FORMAT, p2i(obj));
        }
      )
      return;
    } else {
      // treat referent as normal oop
      debug_only(
        if(TraceReferenceGC && PrintGCDetails) {
          gclog_or_tty->print_cr("       Non NULL normal " PTR_FORMAT, p2i(obj));
        }
      )
      MarkSweep::mark_and_push(referent_addr);
    }
  }
  T* next_addr = (T*)java_lang_ref_Reference::next_addr(obj);
  if (ReferenceProcessor::pending_list_uses_discovered_field()) {
    // Treat discovered as normal oop, if ref is not "active",
    // i.e. if next is non-NULL.
    T  next_oop = oopDesc::load_heap_oop(next_addr);
    if (!oopDesc::is_null(next_oop)) { // i.e. ref is not "active"
      T* discovered_addr = (T*)java_lang_ref_Reference::discovered_addr(obj);
      debug_only(
        if(TraceReferenceGC && PrintGCDetails) {
          gclog_or_tty->print_cr("   Process discovered as normal "
                                 PTR_FORMAT, p2i(discovered_addr));
        }
      )
      MarkSweep::mark_and_push(discovered_addr);
    }
  } else {
#ifdef ASSERT
    // In the case of older JDKs which do not use the discovered
    // field for the pending list, an inactive ref (next != NULL)
    // must always have a NULL discovered field.
    oop next = oopDesc::load_decode_heap_oop(next_addr);
    oop discovered = java_lang_ref_Reference::discovered(obj);
    assert(oopDesc::is_null(next) || oopDesc::is_null(discovered),
        err_msg("Found an inactive reference " PTR_FORMAT " with a non-NULL discovered field",
            p2i(obj)));
#endif
  }
  // treat next as normal oop.  next is a link in the reference queue.
  debug_only(
    if(TraceReferenceGC && PrintGCDetails) {
      gclog_or_tty->print_cr("   Process next as normal " PTR_FORMAT, p2i(next_addr));
    }
  )
  MarkSweep::mark_and_push(next_addr);
  klass->InstanceKlass::oop_ms_follow_contents(obj);
}

void InstanceRefKlass::oop_ms_follow_contents(oop obj) {
  if (UseCompressedOops) {
    oop_ms_follow_contents_specialized<narrowOop>(this, obj);
  } else {
    oop_ms_follow_contents_specialized<oop>(this, obj);
  }
}

template <class T>
static void oop_ms_follow_contents_specialized(oop obj, int index) {
  objArrayOop a = objArrayOop(obj);
  const size_t len = size_t(a->length());
  const size_t beg_index = size_t(index);
  assert(beg_index < len || len == 0, "index too large");

  const size_t stride = MIN2(len - beg_index, ObjArrayMarkingStride);
  const size_t end_index = beg_index + stride;
  T* const base = (T*)a->base();
  T* const beg = base + beg_index;
  T* const end = base + end_index;

  // Push the non-NULL elements of the next stride on the marking stack.
  for (T* e = beg; e < end; e++) {
    MarkSweep::mark_and_push<T>(e);
  }

  if (end_index < len) {
    MarkSweep::push_objarray(a, end_index); // Push the continuation.
  }
}

void ObjArrayKlass::oop_ms_follow_contents(oop obj) {
  assert (obj->is_array(), "obj must be array");
  MarkSweep::follow_klass(this);
  if (UseCompressedOops) {
    oop_ms_follow_contents_specialized<narrowOop>(obj, 0);
  } else {
    oop_ms_follow_contents_specialized<oop>(obj, 0);
  }
}

void TypeArrayKlass::oop_ms_follow_contents(oop obj) {
  assert(obj->is_typeArray(),"must be a type array");
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::TypeArrayKlass never moves.
}

void MarkSweep::follow_array(objArrayOop array, int index) {
  if (UseCompressedOops) {
    oop_ms_follow_contents_specialized<narrowOop>(array, index);
  } else {
    oop_ms_follow_contents_specialized<oop>(array, index);
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
      follow_array(objArrayOop(task.obj()), task.index());
    }
  } while (!_marking_stack.is_empty() || !_objarray_stack.is_empty());
}

MarkSweep::FollowStackClosure MarkSweep::follow_stack_closure;

void MarkSweep::FollowStackClosure::do_void() { follow_stack(); }

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
  if (PrintGC && Verbose) {
    gclog_or_tty->print_cr("Restoring " SIZE_FORMAT " marks",
                           _preserved_count + _preserved_oop_stack.size());
  }

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

bool MarkSweep::IsAliveClosure::do_object_b(oop p) { return p->is_gc_marked(); }

MarkSweep::KeepAliveClosure MarkSweep::keep_alive;

void MarkSweep::KeepAliveClosure::do_oop(oop* p)       { MarkSweep::KeepAliveClosure::do_oop_work(p); }
void MarkSweep::KeepAliveClosure::do_oop(narrowOop* p) { MarkSweep::KeepAliveClosure::do_oop_work(p); }

void marksweep_init() {
  MarkSweep::_gc_timer = new (ResourceObj::C_HEAP, mtGC) STWGCTimer();
  MarkSweep::_gc_tracer = new (ResourceObj::C_HEAP, mtGC) SerialOldTracer();
}

#ifndef PRODUCT

void MarkSweep::trace(const char* msg) {
  if (TraceMarkSweep)
    gclog_or_tty->print("%s", msg);
}

#endif

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
  if(TraceReferenceGC && PrintGCDetails) {
    gclog_or_tty->print_cr("%s obj " PTR_FORMAT, s, p2i(obj));
    gclog_or_tty->print_cr("     referent_addr/* " PTR_FORMAT " / "
                           PTR_FORMAT, p2i(referent_addr),
                           p2i(referent_addr ?
                               (address)oopDesc::load_decode_heap_oop(referent_addr) : NULL));
    gclog_or_tty->print_cr("     next_addr/* " PTR_FORMAT " / "
                           PTR_FORMAT, p2i(next_addr),
                           p2i(next_addr ? (address)oopDesc::load_decode_heap_oop(next_addr) : NULL));
    gclog_or_tty->print_cr("     discovered_addr/* " PTR_FORMAT " / "
                           PTR_FORMAT, p2i(discovered_addr),
                           p2i(discovered_addr ?
                               (address)oopDesc::load_decode_heap_oop(discovered_addr) : NULL));
  }
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
