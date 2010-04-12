/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_satbQueue.cpp.incl"

void ObjPtrQueue::apply_closure(ObjectClosure* cl) {
  if (_buf != NULL) {
    apply_closure_to_buffer(cl, _buf, _index, _sz);
    _index = _sz;
  }
}

void ObjPtrQueue::apply_closure_to_buffer(ObjectClosure* cl,
                                          void** buf, size_t index, size_t sz) {
  if (cl == NULL) return;
  for (size_t i = index; i < sz; i += oopSize) {
    oop obj = (oop)buf[byte_index_to_index((int)i)];
    // There can be NULL entries because of destructors.
    if (obj != NULL) {
      cl->do_object(obj);
    }
  }
}

#ifdef ASSERT
void ObjPtrQueue::verify_oops_in_buffer() {
  if (_buf == NULL) return;
  for (size_t i = _index; i < _sz; i += oopSize) {
    oop obj = (oop)_buf[byte_index_to_index((int)i)];
    assert(obj != NULL && obj->is_oop(true /* ignore mark word */),
           "Not an oop");
  }
}
#endif

#ifdef _MSC_VER // the use of 'this' below gets a warning, make it go away
#pragma warning( disable:4355 ) // 'this' : used in base member initializer list
#endif // _MSC_VER


SATBMarkQueueSet::SATBMarkQueueSet() :
  PtrQueueSet(),
  _closure(NULL), _par_closures(NULL),
  _shared_satb_queue(this, true /*perm*/)
{}

void SATBMarkQueueSet::initialize(Monitor* cbl_mon, Mutex* fl_lock,
                                  int process_completed_threshold,
                                  Mutex* lock) {
  PtrQueueSet::initialize(cbl_mon, fl_lock, process_completed_threshold, -1);
  _shared_satb_queue.set_lock(lock);
  if (ParallelGCThreads > 0) {
    _par_closures = NEW_C_HEAP_ARRAY(ObjectClosure*, ParallelGCThreads);
  }
}


void SATBMarkQueueSet::handle_zero_index_for_thread(JavaThread* t) {
  DEBUG_ONLY(t->satb_mark_queue().verify_oops_in_buffer();)
  t->satb_mark_queue().handle_zero_index();
}

#ifdef ASSERT
void SATBMarkQueueSet::dump_active_values(JavaThread* first,
                                          bool expected_active) {
  gclog_or_tty->print_cr("SATB queue active values for Java Threads");
  gclog_or_tty->print_cr(" SATB queue set: active is %s",
                         (is_active()) ? "TRUE" : "FALSE");
  gclog_or_tty->print_cr(" expected_active is %s",
                         (expected_active) ? "TRUE" : "FALSE");
  for (JavaThread* t = first; t; t = t->next()) {
    bool active = t->satb_mark_queue().is_active();
    gclog_or_tty->print_cr("  thread %s, active is %s",
                           t->name(), (active) ? "TRUE" : "FALSE");
  }
}
#endif // ASSERT

void SATBMarkQueueSet::set_active_all_threads(bool b,
                                              bool expected_active) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint.");
  JavaThread* first = Threads::first();

#ifdef ASSERT
  if (_all_active != expected_active) {
    dump_active_values(first, expected_active);

    // I leave this here as a guarantee, instead of an assert, so
    // that it will still be compiled in if we choose to uncomment
    // the #ifdef ASSERT in a product build. The whole block is
    // within an #ifdef ASSERT so the guarantee will not be compiled
    // in a product build anyway.
    guarantee(false,
              "SATB queue set has an unexpected active value");
  }
#endif // ASSERT
  _all_active = b;

  for (JavaThread* t = first; t; t = t->next()) {
#ifdef ASSERT
    bool active = t->satb_mark_queue().is_active();
    if (active != expected_active) {
      dump_active_values(first, expected_active);

      // I leave this here as a guarantee, instead of an assert, so
      // that it will still be compiled in if we choose to uncomment
      // the #ifdef ASSERT in a product build. The whole block is
      // within an #ifdef ASSERT so the guarantee will not be compiled
      // in a product build anyway.
      guarantee(false,
                "thread has an unexpected active value in its SATB queue");
    }
#endif // ASSERT
    t->satb_mark_queue().set_active(b);
  }
}

void SATBMarkQueueSet::set_closure(ObjectClosure* closure) {
  _closure = closure;
}

void SATBMarkQueueSet::set_par_closure(int i, ObjectClosure* par_closure) {
  assert(ParallelGCThreads > 0 && _par_closures != NULL, "Precondition");
  _par_closures[i] = par_closure;
}

void SATBMarkQueueSet::iterate_closure_all_threads() {
  for(JavaThread* t = Threads::first(); t; t = t->next()) {
    t->satb_mark_queue().apply_closure(_closure);
  }
  shared_satb_queue()->apply_closure(_closure);
}

void SATBMarkQueueSet::par_iterate_closure_all_threads(int worker) {
  SharedHeap* sh = SharedHeap::heap();
  int parity = sh->strong_roots_parity();

  for(JavaThread* t = Threads::first(); t; t = t->next()) {
    if (t->claim_oops_do(true, parity)) {
      t->satb_mark_queue().apply_closure(_par_closures[worker]);
    }
  }
  // We'll have worker 0 do this one.
  if (worker == 0) {
    shared_satb_queue()->apply_closure(_par_closures[0]);
  }
}

bool SATBMarkQueueSet::apply_closure_to_completed_buffer_work(bool par,
                                                              int worker) {
  BufferNode* nd = NULL;
  {
    MutexLockerEx x(_cbl_mon, Mutex::_no_safepoint_check_flag);
    if (_completed_buffers_head != NULL) {
      nd = _completed_buffers_head;
      _completed_buffers_head = nd->next();
      if (_completed_buffers_head == NULL) _completed_buffers_tail = NULL;
      _n_completed_buffers--;
      if (_n_completed_buffers == 0) _process_completed = false;
    }
  }
  ObjectClosure* cl = (par ? _par_closures[worker] : _closure);
  if (nd != NULL) {
    void **buf = BufferNode::make_buffer_from_node(nd);
    ObjPtrQueue::apply_closure_to_buffer(cl, buf, 0, _sz);
    deallocate_buffer(buf);
    return true;
  } else {
    return false;
  }
}

void SATBMarkQueueSet::abandon_partial_marking() {
  BufferNode* buffers_to_delete = NULL;
  {
    MutexLockerEx x(_cbl_mon, Mutex::_no_safepoint_check_flag);
    while (_completed_buffers_head != NULL) {
      BufferNode* nd = _completed_buffers_head;
      _completed_buffers_head = nd->next();
      nd->set_next(buffers_to_delete);
      buffers_to_delete = nd;
    }
    _completed_buffers_tail = NULL;
    _n_completed_buffers = 0;
    DEBUG_ONLY(assert_completed_buffer_list_len_correct_locked());
  }
  while (buffers_to_delete != NULL) {
    BufferNode* nd = buffers_to_delete;
    buffers_to_delete = nd->next();
    deallocate_buffer(BufferNode::make_buffer_from_node(nd));
  }
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint.");
  // So we can safely manipulate these queues.
  for (JavaThread* t = Threads::first(); t; t = t->next()) {
    t->satb_mark_queue().reset();
  }
  shared_satb_queue()->reset();
}
