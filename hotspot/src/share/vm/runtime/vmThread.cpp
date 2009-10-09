/*
 * Copyright 1998-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# include "incls/_vmThread.cpp.incl"

// Dummy VM operation to act as first element in our circular double-linked list
class VM_Dummy: public VM_Operation {
  VMOp_Type type() const { return VMOp_Dummy; }
  void  doit() {};
};

VMOperationQueue::VMOperationQueue() {
  // The queue is a circular doubled-linked list, which always contains
  // one element (i.e., one element means empty).
  for(int i = 0; i < nof_priorities; i++) {
    _queue_length[i] = 0;
    _queue_counter = 0;
    _queue[i] = new VM_Dummy();
    _queue[i]->set_next(_queue[i]);
    _queue[i]->set_prev(_queue[i]);
  }
  _drain_list = NULL;
}


bool VMOperationQueue::queue_empty(int prio) {
  // It is empty if there is exactly one element
  bool empty = (_queue[prio] == _queue[prio]->next());
  assert( (_queue_length[prio] == 0 && empty) ||
          (_queue_length[prio] > 0  && !empty), "sanity check");
  return _queue_length[prio] == 0;
}

// Inserts an element to the right of the q element
void VMOperationQueue::insert(VM_Operation* q, VM_Operation* n) {
  assert(q->next()->prev() == q && q->prev()->next() == q, "sanity check");
  n->set_prev(q);
  n->set_next(q->next());
  q->next()->set_prev(n);
  q->set_next(n);
}

void VMOperationQueue::queue_add_front(int prio, VM_Operation *op) {
  _queue_length[prio]++;
  insert(_queue[prio]->next(), op);
}

void VMOperationQueue::queue_add_back(int prio, VM_Operation *op) {
  _queue_length[prio]++;
  insert(_queue[prio]->prev(), op);
}


void VMOperationQueue::unlink(VM_Operation* q) {
  assert(q->next()->prev() == q && q->prev()->next() == q, "sanity check");
  q->prev()->set_next(q->next());
  q->next()->set_prev(q->prev());
}

VM_Operation* VMOperationQueue::queue_remove_front(int prio) {
  if (queue_empty(prio)) return NULL;
  assert(_queue_length[prio] >= 0, "sanity check");
  _queue_length[prio]--;
  VM_Operation* r = _queue[prio]->next();
  assert(r != _queue[prio], "cannot remove base element");
  unlink(r);
  return r;
}

VM_Operation* VMOperationQueue::queue_drain(int prio) {
  if (queue_empty(prio)) return NULL;
  DEBUG_ONLY(int length = _queue_length[prio];);
  assert(length >= 0, "sanity check");
  _queue_length[prio] = 0;
  VM_Operation* r = _queue[prio]->next();
  assert(r != _queue[prio], "cannot remove base element");
  // remove links to base element from head and tail
  r->set_prev(NULL);
  _queue[prio]->prev()->set_next(NULL);
  // restore queue to empty state
  _queue[prio]->set_next(_queue[prio]);
  _queue[prio]->set_prev(_queue[prio]);
  assert(queue_empty(prio), "drain corrupted queue")
#ifdef DEBUG
  int len = 0;
  VM_Operation* cur;
  for(cur = r; cur != NULL; cur=cur->next()) len++;
  assert(len == length, "drain lost some ops");
#endif
  return r;
}

void VMOperationQueue::queue_oops_do(int queue, OopClosure* f) {
  VM_Operation* cur = _queue[queue];
  cur = cur->next();
  while (cur != _queue[queue]) {
    cur->oops_do(f);
    cur = cur->next();
  }
}

void VMOperationQueue::drain_list_oops_do(OopClosure* f) {
  VM_Operation* cur = _drain_list;
  while (cur != NULL) {
    cur->oops_do(f);
    cur = cur->next();
  }
}

//-----------------------------------------------------------------
// High-level interface
bool VMOperationQueue::add(VM_Operation *op) {
  // Encapsulates VM queue policy. Currently, that
  // only involves putting them on the right list
  if (op->evaluate_at_safepoint()) {
    queue_add_back(SafepointPriority, op);
    return true;
  }

  queue_add_back(MediumPriority, op);
  return true;
}

VM_Operation* VMOperationQueue::remove_next() {
  // Assuming VMOperation queue is two-level priority queue. If there are
  // more than two priorities, we need a different scheduling algorithm.
  assert(SafepointPriority == 0 && MediumPriority == 1 && nof_priorities == 2,
         "current algorithm does not work");

  // simple counter based scheduling to prevent starvation of lower priority
  // queue. -- see 4390175
  int high_prio, low_prio;
  if (_queue_counter++ < 10) {
      high_prio = SafepointPriority;
      low_prio  = MediumPriority;
  } else {
      _queue_counter = 0;
      high_prio = MediumPriority;
      low_prio  = SafepointPriority;
  }

  return queue_remove_front(queue_empty(high_prio) ? low_prio : high_prio);
}

void VMOperationQueue::oops_do(OopClosure* f) {
  for(int i = 0; i < nof_priorities; i++) {
    queue_oops_do(i, f);
  }
  drain_list_oops_do(f);
}


//------------------------------------------------------------------------------------------------------------------
// Implementation of VMThread stuff

bool                VMThread::_should_terminate   = false;
bool              VMThread::_terminated         = false;
Monitor*          VMThread::_terminate_lock     = NULL;
VMThread*         VMThread::_vm_thread          = NULL;
VM_Operation*     VMThread::_cur_vm_operation   = NULL;
VMOperationQueue* VMThread::_vm_queue           = NULL;
PerfCounter*      VMThread::_perf_accumulated_vm_operation_time = NULL;


void VMThread::create() {
  assert(vm_thread() == NULL, "we can only allocate one VMThread");
  _vm_thread = new VMThread();

  // Create VM operation queue
  _vm_queue = new VMOperationQueue();
  guarantee(_vm_queue != NULL, "just checking");

  _terminate_lock = new Monitor(Mutex::safepoint, "VMThread::_terminate_lock", true);

  if (UsePerfData) {
    // jvmstat performance counters
    Thread* THREAD = Thread::current();
    _perf_accumulated_vm_operation_time =
                 PerfDataManager::create_counter(SUN_THREADS, "vmOperationTime",
                                                 PerfData::U_Ticks, CHECK);
  }
}


VMThread::VMThread() : Thread() {
  // nothing to do
}

void VMThread::destroy() {
  if (_vm_thread != NULL) {
    delete _vm_thread;
    _vm_thread = NULL;      // VM thread is gone
  }
}

void VMThread::run() {
  assert(this == vm_thread(), "check");

  this->initialize_thread_local_storage();
  this->record_stack_base_and_size();
  // Notify_lock wait checks on active_handles() to rewait in
  // case of spurious wakeup, it should wait on the last
  // value set prior to the notify
  this->set_active_handles(JNIHandleBlock::allocate_block());

  {
    MutexLocker ml(Notify_lock);
    Notify_lock->notify();
  }
  // Notify_lock is destroyed by Threads::create_vm()

  int prio = (VMThreadPriority == -1)
    ? os::java_to_os_priority[NearMaxPriority]
    : VMThreadPriority;
  // Note that I cannot call os::set_priority because it expects Java
  // priorities and I am *explicitly* using OS priorities so that it's
  // possible to set the VM thread priority higher than any Java thread.
  os::set_native_priority( this, prio );

  // Wait for VM_Operations until termination
  this->loop();

  // Note the intention to exit before safepointing.
  // 6295565  This has the effect of waiting for any large tty
  // outputs to finish.
  if (xtty != NULL) {
    ttyLocker ttyl;
    xtty->begin_elem("destroy_vm");
    xtty->stamp();
    xtty->end_elem();
    assert(should_terminate(), "termination flag must be set");
  }

  // 4526887 let VM thread exit at Safepoint
  SafepointSynchronize::begin();

  if (VerifyBeforeExit) {
    HandleMark hm(VMThread::vm_thread());
    // Among other things, this ensures that Eden top is correct.
    Universe::heap()->prepare_for_verify();
    os::check_heap();
    Universe::verify(true, true); // Silent verification to not polute normal output
  }

  CompileBroker::set_should_block();

  // wait for threads (compiler threads or daemon threads) in the
  // _thread_in_native state to block.
  VM_Exit::wait_for_threads_in_native_to_block();

  // signal other threads that VM process is gone
  {
    // Note: we must have the _no_safepoint_check_flag. Mutex::lock() allows
    // VM thread to enter any lock at Safepoint as long as its _owner is NULL.
    // If that happens after _terminate_lock->wait() has unset _owner
    // but before it actually drops the lock and waits, the notification below
    // may get lost and we will have a hang. To avoid this, we need to use
    // Mutex::lock_without_safepoint_check().
    MutexLockerEx ml(_terminate_lock, Mutex::_no_safepoint_check_flag);
    _terminated = true;
    _terminate_lock->notify();
  }

  // Deletion must be done synchronously by the JNI DestroyJavaVM thread
  // so that the VMThread deletion completes before the main thread frees
  // up the CodeHeap.

}


// Notify the VMThread that the last non-daemon JavaThread has terminated,
// and wait until operation is performed.
void VMThread::wait_for_vm_thread_exit() {
  { MutexLocker mu(VMOperationQueue_lock);
    _should_terminate = true;
    VMOperationQueue_lock->notify();
  }

  // Note: VM thread leaves at Safepoint. We are not stopped by Safepoint
  // because this thread has been removed from the threads list. But anything
  // that could get blocked by Safepoint should not be used after this point,
  // otherwise we will hang, since there is no one can end the safepoint.

  // Wait until VM thread is terminated
  // Note: it should be OK to use Terminator_lock here. But this is called
  // at a very delicate time (VM shutdown) and we are operating in non- VM
  // thread at Safepoint. It's safer to not share lock with other threads.
  { MutexLockerEx ml(_terminate_lock, Mutex::_no_safepoint_check_flag);
    while(!VMThread::is_terminated()) {
        _terminate_lock->wait(Mutex::_no_safepoint_check_flag);
    }
  }
}

void VMThread::print_on(outputStream* st) const {
  st->print("\"%s\" ", name());
  Thread::print_on(st);
  st->cr();
}

void VMThread::evaluate_operation(VM_Operation* op) {
  ResourceMark rm;

  {
    PerfTraceTime vm_op_timer(perf_accumulated_vm_operation_time());
    op->evaluate();
  }

  // Last access of info in _cur_vm_operation!
  bool c_heap_allocated = op->is_cheap_allocated();

  // Mark as completed
  if (!op->evaluate_concurrently()) {
    op->calling_thread()->increment_vm_operation_completed_count();
  }
  // It is unsafe to access the _cur_vm_operation after the 'increment_vm_operation_completed_count' call,
  // since if it is stack allocated the calling thread might have deallocated
  if (c_heap_allocated) {
    delete _cur_vm_operation;
  }
}


void VMThread::loop() {
  assert(_cur_vm_operation == NULL, "no current one should be executing");

  while(true) {
    VM_Operation* safepoint_ops = NULL;
    //
    // Wait for VM operation
    //
    // use no_safepoint_check to get lock without attempting to "sneak"
    { MutexLockerEx mu_queue(VMOperationQueue_lock,
                             Mutex::_no_safepoint_check_flag);

      // Look for new operation
      assert(_cur_vm_operation == NULL, "no current one should be executing");
      _cur_vm_operation = _vm_queue->remove_next();

      // Stall time tracking code
      if (PrintVMQWaitTime && _cur_vm_operation != NULL &&
          !_cur_vm_operation->evaluate_concurrently()) {
        long stall = os::javaTimeMillis() - _cur_vm_operation->timestamp();
        if (stall > 0)
          tty->print_cr("%s stall: %Ld",  _cur_vm_operation->name(), stall);
      }

      while (!should_terminate() && _cur_vm_operation == NULL) {
        // wait with a timeout to guarantee safepoints at regular intervals
        bool timedout =
          VMOperationQueue_lock->wait(Mutex::_no_safepoint_check_flag,
                                      GuaranteedSafepointInterval);

        // Support for self destruction
        if ((SelfDestructTimer != 0) && !is_error_reported() &&
            (os::elapsedTime() > SelfDestructTimer * 60)) {
          tty->print_cr("VM self-destructed");
          exit(-1);
        }

        if (timedout && (SafepointALot ||
                         SafepointSynchronize::is_cleanup_needed())) {
          MutexUnlockerEx mul(VMOperationQueue_lock,
                              Mutex::_no_safepoint_check_flag);
          // Force a safepoint since we have not had one for at least
          // 'GuaranteedSafepointInterval' milliseconds.  This will run all
          // the clean-up processing that needs to be done regularly at a
          // safepoint
          SafepointSynchronize::begin();
          #ifdef ASSERT
            if (GCALotAtAllSafepoints) InterfaceSupport::check_gc_alot();
          #endif
          SafepointSynchronize::end();
        }
        _cur_vm_operation = _vm_queue->remove_next();

        // If we are at a safepoint we will evaluate all the operations that
        // follow that also require a safepoint
        if (_cur_vm_operation != NULL &&
            _cur_vm_operation->evaluate_at_safepoint()) {
          safepoint_ops = _vm_queue->drain_at_safepoint_priority();
        }
      }

      if (should_terminate()) break;
    } // Release mu_queue_lock

    //
    // Execute VM operation
    //
    { HandleMark hm(VMThread::vm_thread());

      EventMark em("Executing VM operation: %s", vm_operation()->name());
      assert(_cur_vm_operation != NULL, "we should have found an operation to execute");

      // Give the VM thread an extra quantum.  Jobs tend to be bursty and this
      // helps the VM thread to finish up the job.
      // FIXME: When this is enabled and there are many threads, this can degrade
      // performance significantly.
      if( VMThreadHintNoPreempt )
        os::hint_no_preempt();

      // If we are at a safepoint we will evaluate all the operations that
      // follow that also require a safepoint
      if (_cur_vm_operation->evaluate_at_safepoint()) {

        if (PrintGCApplicationConcurrentTime) {
           gclog_or_tty->print_cr("Application time: %3.7f seconds",
                                  RuntimeService::last_application_time_sec());
        }

        _vm_queue->set_drain_list(safepoint_ops); // ensure ops can be scanned

        SafepointSynchronize::begin();
        evaluate_operation(_cur_vm_operation);
        // now process all queued safepoint ops, iteratively draining
        // the queue until there are none left
        do {
          _cur_vm_operation = safepoint_ops;
          if (_cur_vm_operation != NULL) {
            do {
              // evaluate_operation deletes the op object so we have
              // to grab the next op now
              VM_Operation* next = _cur_vm_operation->next();
              _vm_queue->set_drain_list(next);
              evaluate_operation(_cur_vm_operation);
              _cur_vm_operation = next;
              if (PrintSafepointStatistics) {
                SafepointSynchronize::inc_vmop_coalesced_count();
              }
            } while (_cur_vm_operation != NULL);
          }
          // There is a chance that a thread enqueued a safepoint op
          // since we released the op-queue lock and initiated the safepoint.
          // So we drain the queue again if there is anything there, as an
          // optimization to try and reduce the number of safepoints.
          // As the safepoint synchronizes us with JavaThreads we will see
          // any enqueue made by a JavaThread, but the peek will not
          // necessarily detect a concurrent enqueue by a GC thread, but
          // that simply means the op will wait for the next major cycle of the
          // VMThread - just as it would if the GC thread lost the race for
          // the lock.
          if (_vm_queue->peek_at_safepoint_priority()) {
            // must hold lock while draining queue
            MutexLockerEx mu_queue(VMOperationQueue_lock,
                                     Mutex::_no_safepoint_check_flag);
            safepoint_ops = _vm_queue->drain_at_safepoint_priority();
          } else {
            safepoint_ops = NULL;
          }
        } while(safepoint_ops != NULL);

        _vm_queue->set_drain_list(NULL);

        // Complete safepoint synchronization
        SafepointSynchronize::end();

        if (PrintGCApplicationStoppedTime) {
          gclog_or_tty->print_cr("Total time for which application threads "
                                 "were stopped: %3.7f seconds",
                                 RuntimeService::last_safepoint_time_sec());
        }

      } else {  // not a safepoint operation
        if (TraceLongCompiles) {
          elapsedTimer t;
          t.start();
          evaluate_operation(_cur_vm_operation);
          t.stop();
          double secs = t.seconds();
          if (secs * 1e3 > LongCompileThreshold) {
            // XXX - _cur_vm_operation should not be accessed after
            // the completed count has been incremented; the waiting
            // thread may have already freed this memory.
            tty->print_cr("vm %s: %3.7f secs]", _cur_vm_operation->name(), secs);
          }
        } else {
          evaluate_operation(_cur_vm_operation);
        }

        _cur_vm_operation = NULL;
      }
    }

    //
    //  Notify (potential) waiting Java thread(s) - lock without safepoint
    //  check so that sneaking is not possible
    { MutexLockerEx mu(VMOperationRequest_lock,
                       Mutex::_no_safepoint_check_flag);
      VMOperationRequest_lock->notify_all();
    }

    //
    // We want to make sure that we get to a safepoint regularly.
    //
    if (SafepointALot || SafepointSynchronize::is_cleanup_needed()) {
      long interval          = SafepointSynchronize::last_non_safepoint_interval();
      bool max_time_exceeded = GuaranteedSafepointInterval != 0 && (interval > GuaranteedSafepointInterval);
      if (SafepointALot || max_time_exceeded) {
        HandleMark hm(VMThread::vm_thread());
        SafepointSynchronize::begin();
        SafepointSynchronize::end();
      }
    }
  }
}

void VMThread::execute(VM_Operation* op) {
  Thread* t = Thread::current();

  if (!t->is_VM_thread()) {
    SkipGCALot sgcalot(t);    // avoid re-entrant attempts to gc-a-lot
    // JavaThread or WatcherThread
    t->check_for_valid_safepoint_state(true);

    // New request from Java thread, evaluate prologue
    if (!op->doit_prologue()) {
      return;   // op was cancelled
    }

    // Setup VM_operations for execution
    op->set_calling_thread(t, Thread::get_priority(t));

    // It does not make sense to execute the epilogue, if the VM operation object is getting
    // deallocated by the VM thread.
    bool concurrent     = op->evaluate_concurrently();
    bool execute_epilog = !op->is_cheap_allocated();
    assert(!concurrent || op->is_cheap_allocated(), "concurrent => cheap_allocated");

    // Get ticket number for non-concurrent VM operations
    int ticket = 0;
    if (!concurrent) {
      ticket = t->vm_operation_ticket();
    }

    // Add VM operation to list of waiting threads. We are guaranteed not to block while holding the
    // VMOperationQueue_lock, so we can block without a safepoint check. This allows vm operation requests
    // to be queued up during a safepoint synchronization.
    {
      VMOperationQueue_lock->lock_without_safepoint_check();
      bool ok = _vm_queue->add(op);
      op->set_timestamp(os::javaTimeMillis());
      VMOperationQueue_lock->notify();
      VMOperationQueue_lock->unlock();
      // VM_Operation got skipped
      if (!ok) {
        assert(concurrent, "can only skip concurrent tasks");
        if (op->is_cheap_allocated()) delete op;
        return;
      }
    }

    if (!concurrent) {
      // Wait for completion of request (non-concurrent)
      // Note: only a JavaThread triggers the safepoint check when locking
      MutexLocker mu(VMOperationRequest_lock);
      while(t->vm_operation_completed_count() < ticket) {
        VMOperationRequest_lock->wait(!t->is_Java_thread());
      }
    }

    if (execute_epilog) {
      op->doit_epilogue();
    }
  } else {
    // invoked by VM thread; usually nested VM operation
    assert(t->is_VM_thread(), "must be a VM thread");
    VM_Operation* prev_vm_operation = vm_operation();
    if (prev_vm_operation != NULL) {
      // Check the VM operation allows nested VM operation. This normally not the case, e.g., the compiler
      // does not allow nested scavenges or compiles.
      if (!prev_vm_operation->allow_nested_vm_operations()) {
        fatal2("Nested VM operation %s requested by operation %s", op->name(), vm_operation()->name());
      }
      op->set_calling_thread(prev_vm_operation->calling_thread(), prev_vm_operation->priority());
    }

    EventMark em("Executing %s VM operation: %s", prev_vm_operation ? "nested" : "", op->name());

    // Release all internal handles after operation is evaluated
    HandleMark hm(t);
    _cur_vm_operation = op;

    if (op->evaluate_at_safepoint() && !SafepointSynchronize::is_at_safepoint()) {
      SafepointSynchronize::begin();
      op->evaluate();
      SafepointSynchronize::end();
    } else {
      op->evaluate();
    }

    // Free memory if needed
    if (op->is_cheap_allocated()) delete op;

    _cur_vm_operation = prev_vm_operation;
  }
}


void VMThread::oops_do(OopClosure* f, CodeBlobClosure* cf) {
  Thread::oops_do(f, cf);
  _vm_queue->oops_do(f);
}

//------------------------------------------------------------------------------------------------------------------
#ifndef PRODUCT

void VMOperationQueue::verify_queue(int prio) {
  // Check that list is correctly linked
  int length = _queue_length[prio];
  VM_Operation *cur = _queue[prio];
  int i;

  // Check forward links
  for(i = 0; i < length; i++) {
    cur = cur->next();
    assert(cur != _queue[prio], "list to short (forward)");
  }
  assert(cur->next() == _queue[prio], "list to long (forward)");

  // Check backwards links
  cur = _queue[prio];
  for(i = 0; i < length; i++) {
    cur = cur->prev();
    assert(cur != _queue[prio], "list to short (backwards)");
  }
  assert(cur->prev() == _queue[prio], "list to long (backwards)");
}

#endif

void VMThread::verify() {
  oops_do(&VerifyOopClosure::verify_oop, NULL);
}
