/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
MarkSweep::FollowKlassClosure MarkSweep::follow_klass_closure;
MarkSweep::AdjustKlassClosure MarkSweep::adjust_klass_closure;

void MarkSweep::MarkAndPushClosure::do_oop(oop* p)       { mark_and_push(p); }
void MarkSweep::MarkAndPushClosure::do_oop(narrowOop* p) { mark_and_push(p); }

void MarkSweep::FollowKlassClosure::do_klass(Klass* klass) {
  klass->oops_do(&MarkSweep::mark_and_push_closure);
}
void MarkSweep::AdjustKlassClosure::do_klass(Klass* klass) {
  klass->oops_do(&MarkSweep::adjust_pointer_closure);
}

void MarkSweep::follow_class_loader(ClassLoaderData* cld) {
  cld->oops_do(&MarkSweep::mark_and_push_closure, &MarkSweep::follow_klass_closure, true);
}

void MarkSweep::follow_stack() {
  do {
    while (!_marking_stack.is_empty()) {
      oop obj = _marking_stack.pop();
      assert (obj->is_gc_marked(), "p must be marked");
      obj->follow_contents();
    }
    // Process ObjArrays one at a time to avoid marking stack bloat.
    if (!_objarray_stack.is_empty()) {
      ObjArrayTask task = _objarray_stack.pop();
      ObjArrayKlass* k = (ObjArrayKlass*)task.obj()->klass();
      k->oop_follow_contents(task.obj(), task.index());
    }
  } while (!_marking_stack.is_empty() || !_objarray_stack.is_empty());
}

MarkSweep::FollowStackClosure MarkSweep::follow_stack_closure;

void MarkSweep::FollowStackClosure::do_void() { follow_stack(); }

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

void MarkSweep::AdjustPointerClosure::do_oop(oop* p)       { adjust_pointer(p); }
void MarkSweep::AdjustPointerClosure::do_oop(narrowOop* p) { adjust_pointer(p); }

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
    gclog_or_tty->print_cr("Restoring %d marks",
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
