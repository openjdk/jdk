/*
 * Copyright 2001-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
# include "incls/_taskqueue.cpp.incl"

bool TaskQueueSuper::peek() {
  return _bottom != _age.top();
}

int TaskQueueSetSuper::randomParkAndMiller(int *seed0) {
  const int a =      16807;
  const int m = 2147483647;
  const int q =     127773;  /* m div a */
  const int r =       2836;  /* m mod a */
  assert(sizeof(int) == 4, "I think this relies on that");
  int seed = *seed0;
  int hi   = seed / q;
  int lo   = seed % q;
  int test = a * lo - r * hi;
  if (test > 0)
    seed = test;
  else
    seed = test + m;
  *seed0 = seed;
  return seed;
}

ParallelTaskTerminator::
ParallelTaskTerminator(int n_threads, TaskQueueSetSuper* queue_set) :
  _n_threads(n_threads),
  _queue_set(queue_set),
  _offered_termination(0) {}

bool ParallelTaskTerminator::peek_in_queue_set() {
  return _queue_set->peek();
}

void ParallelTaskTerminator::yield() {
  os::yield();
}

void ParallelTaskTerminator::sleep(uint millis) {
  os::sleep(Thread::current(), millis, false);
}

bool
ParallelTaskTerminator::offer_termination(TerminatorTerminator* terminator) {
  Atomic::inc(&_offered_termination);

  juint yield_count = 0;
  while (true) {
    if (_offered_termination == _n_threads) {
      //inner_termination_loop();
      return true;
    } else {
      if (yield_count <= WorkStealingYieldsBeforeSleep) {
        yield_count++;
        yield();
      } else {
        if (PrintGCDetails && Verbose) {
         gclog_or_tty->print_cr("ParallelTaskTerminator::offer_termination() "
           "thread %d sleeps after %d yields",
           Thread::current(), yield_count);
        }
        yield_count = 0;
        // A sleep will cause this processor to seek work on another processor's
        // runqueue, if it has nothing else to run (as opposed to the yield
        // which may only move the thread to the end of the this processor's
        // runqueue).
        sleep(WorkStealingSleepMillis);
      }

      if (peek_in_queue_set() ||
          (terminator != NULL && terminator->should_exit_termination())) {
        Atomic::dec(&_offered_termination);
        return false;
      }
    }
  }
}

void ParallelTaskTerminator::reset_for_reuse() {
  if (_offered_termination != 0) {
    assert(_offered_termination == _n_threads,
           "Terminator may still be in use");
    _offered_termination = 0;
  }
}

bool RegionTaskQueueWithOverflow::is_empty() {
  return (_region_queue.size() == 0) &&
         (_overflow_stack->length() == 0);
}

bool RegionTaskQueueWithOverflow::stealable_is_empty() {
  return _region_queue.size() == 0;
}

bool RegionTaskQueueWithOverflow::overflow_is_empty() {
  return _overflow_stack->length() == 0;
}

void RegionTaskQueueWithOverflow::initialize() {
  _region_queue.initialize();
  assert(_overflow_stack == 0, "Creating memory leak");
  _overflow_stack =
    new (ResourceObj::C_HEAP) GrowableArray<RegionTask>(10, true);
}

void RegionTaskQueueWithOverflow::save(RegionTask t) {
  if (TraceRegionTasksQueuing && Verbose) {
    gclog_or_tty->print_cr("CTQ: save " PTR_FORMAT, t);
  }
  if(!_region_queue.push(t)) {
    _overflow_stack->push(t);
  }
}

// Note that using this method will retrieve all regions
// that have been saved but that it will always check
// the overflow stack.  It may be more efficient to
// check the stealable queue and the overflow stack
// separately.
bool RegionTaskQueueWithOverflow::retrieve(RegionTask& region_task) {
  bool result = retrieve_from_overflow(region_task);
  if (!result) {
    result = retrieve_from_stealable_queue(region_task);
  }
  if (TraceRegionTasksQueuing && Verbose && result) {
    gclog_or_tty->print_cr("  CTQ: retrieve " PTR_FORMAT, result);
  }
  return result;
}

bool RegionTaskQueueWithOverflow::retrieve_from_stealable_queue(
                                   RegionTask& region_task) {
  bool result = _region_queue.pop_local(region_task);
  if (TraceRegionTasksQueuing && Verbose) {
    gclog_or_tty->print_cr("CTQ: retrieve_stealable " PTR_FORMAT, region_task);
  }
  return result;
}

bool
RegionTaskQueueWithOverflow::retrieve_from_overflow(RegionTask& region_task) {
  bool result;
  if (!_overflow_stack->is_empty()) {
    region_task = _overflow_stack->pop();
    result = true;
  } else {
    region_task = (RegionTask) NULL;
    result = false;
  }
  if (TraceRegionTasksQueuing && Verbose) {
    gclog_or_tty->print_cr("CTQ: retrieve_stealable " PTR_FORMAT, region_task);
  }
  return result;
}
