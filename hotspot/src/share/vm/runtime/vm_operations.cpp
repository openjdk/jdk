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
#include "classfile/symbolTable.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/codeCacheExtensions.hpp"
#include "compiler/compileBroker.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "logging/log.hpp"
#include "memory/heapInspection.hpp"
#include "memory/resourceArea.hpp"
#include "oops/symbol.hpp"
#include "runtime/arguments.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/sweeper.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vm_operations.hpp"
#include "services/threadService.hpp"
#include "trace/tracing.hpp"

#define VM_OP_NAME_INITIALIZE(name) #name,

const char* VM_Operation::_names[VM_Operation::VMOp_Terminating] = \
  { VM_OPS_DO(VM_OP_NAME_INITIALIZE) };

void VM_Operation::set_calling_thread(Thread* thread, ThreadPriority priority) {
  _calling_thread = thread;
  assert(MinPriority <= priority && priority <= MaxPriority, "sanity check");
  _priority = priority;
}


void VM_Operation::evaluate() {
  ResourceMark rm;
  outputStream* debugstream;
  bool enabled = log_is_enabled(Debug, vmoperation);
  if (enabled) {
    debugstream = LogHandle(vmoperation)::debug_stream();
    debugstream->print("begin ");
    print_on_error(debugstream);
    debugstream->cr();
  }
  doit();
  if (enabled) {
    debugstream->print("end ");
    print_on_error(debugstream);
    debugstream->cr();
  }
}

const char* VM_Operation::mode_to_string(Mode mode) {
  switch(mode) {
    case _safepoint      : return "safepoint";
    case _no_safepoint   : return "no safepoint";
    case _concurrent     : return "concurrent";
    case _async_safepoint: return "async safepoint";
    default              : return "unknown";
  }
}
// Called by fatal error handler.
void VM_Operation::print_on_error(outputStream* st) const {
  st->print("VM_Operation (" PTR_FORMAT "): ", p2i(this));
  st->print("%s", name());

  const char* mode = mode_to_string(evaluation_mode());
  st->print(", mode: %s", mode);

  if (calling_thread()) {
    st->print(", requested by thread " PTR_FORMAT, p2i(calling_thread()));
  }
}

void VM_ThreadStop::doit() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at a safepoint");
  JavaThread* target = java_lang_Thread::thread(target_thread());
  // Note that this now allows multiple ThreadDeath exceptions to be
  // thrown at a thread.
  if (target != NULL) {
    // the thread has run and is not already in the process of exiting
    target->send_thread_stop(throwable());
  }
}

void VM_Deoptimize::doit() {
  // We do not want any GCs to happen while we are in the middle of this VM operation
  ResourceMark rm;
  DeoptimizationMarker dm;

  // Deoptimize all activations depending on marked nmethods
  Deoptimization::deoptimize_dependents();

  // Make the dependent methods not entrant
  CodeCache::make_marked_nmethods_not_entrant();
}

void VM_MarkActiveNMethods::doit() {
  NMethodSweeper::mark_active_nmethods();
}

VM_DeoptimizeFrame::VM_DeoptimizeFrame(JavaThread* thread, intptr_t* id, int reason) {
  _thread = thread;
  _id     = id;
  _reason = reason;
}


void VM_DeoptimizeFrame::doit() {
  assert(_reason > Deoptimization::Reason_none && _reason < Deoptimization::Reason_LIMIT, "invalid deopt reason");
  Deoptimization::deoptimize_frame_internal(_thread, _id, (Deoptimization::DeoptReason)_reason);
}


#ifndef PRODUCT

void VM_DeoptimizeAll::doit() {
  DeoptimizationMarker dm;
  // deoptimize all java threads in the system
  if (DeoptimizeALot) {
    for (JavaThread* thread = Threads::first(); thread != NULL; thread = thread->next()) {
      if (thread->has_last_Java_frame()) {
        thread->deoptimize();
      }
    }
  } else if (DeoptimizeRandom) {

    // Deoptimize some selected threads and frames
    int tnum = os::random() & 0x3;
    int fnum =  os::random() & 0x3;
    int tcount = 0;
    for (JavaThread* thread = Threads::first(); thread != NULL; thread = thread->next()) {
      if (thread->has_last_Java_frame()) {
        if (tcount++ == tnum)  {
        tcount = 0;
          int fcount = 0;
          // Deoptimize some selected frames.
          // Biased llocking wants a updated register map
          for(StackFrameStream fst(thread, UseBiasedLocking); !fst.is_done(); fst.next()) {
            if (fst.current()->can_be_deoptimized()) {
              if (fcount++ == fnum) {
                fcount = 0;
                Deoptimization::deoptimize(thread, *fst.current(), fst.register_map());
              }
            }
          }
        }
      }
    }
  }
}


void VM_ZombieAll::doit() {
  JavaThread *thread = (JavaThread *)calling_thread();
  assert(thread->is_Java_thread(), "must be a Java thread");
  thread->make_zombies();
}

#endif // !PRODUCT

void VM_UnlinkSymbols::doit() {
  JavaThread *thread = (JavaThread *)calling_thread();
  assert(thread->is_Java_thread(), "must be a Java thread");
  SymbolTable::unlink();
}

void VM_Verify::doit() {
  Universe::heap()->prepare_for_verify();
  Universe::verify();
}

bool VM_PrintThreads::doit_prologue() {
  assert(Thread::current()->is_Java_thread(), "just checking");

  // Make sure AbstractOwnableSynchronizer is loaded
  java_util_concurrent_locks_AbstractOwnableSynchronizer::initialize(JavaThread::current());

  // Get Heap_lock if concurrent locks will be dumped
  if (_print_concurrent_locks) {
    Heap_lock->lock();
  }
  return true;
}

void VM_PrintThreads::doit() {
  Threads::print_on(_out, true, false, _print_concurrent_locks);
}

void VM_PrintThreads::doit_epilogue() {
  if (_print_concurrent_locks) {
    // Release Heap_lock
    Heap_lock->unlock();
  }
}

void VM_PrintJNI::doit() {
  JNIHandles::print_on(_out);
}

VM_FindDeadlocks::~VM_FindDeadlocks() {
  if (_deadlocks != NULL) {
    DeadlockCycle* cycle = _deadlocks;
    while (cycle != NULL) {
      DeadlockCycle* d = cycle;
      cycle = cycle->next();
      delete d;
    }
  }
}

bool VM_FindDeadlocks::doit_prologue() {
  assert(Thread::current()->is_Java_thread(), "just checking");

  // Load AbstractOwnableSynchronizer class
  if (_concurrent_locks) {
    java_util_concurrent_locks_AbstractOwnableSynchronizer::initialize(JavaThread::current());
  }

  return true;
}

void VM_FindDeadlocks::doit() {
  _deadlocks = ThreadService::find_deadlocks_at_safepoint(_concurrent_locks);
  if (_out != NULL) {
    int num_deadlocks = 0;
    for (DeadlockCycle* cycle = _deadlocks; cycle != NULL; cycle = cycle->next()) {
      num_deadlocks++;
      cycle->print_on(_out);
    }

    if (num_deadlocks == 1) {
      _out->print_cr("\nFound 1 deadlock.\n");
      _out->flush();
    } else if (num_deadlocks > 1) {
      _out->print_cr("\nFound %d deadlocks.\n", num_deadlocks);
      _out->flush();
    }
  }
}

VM_ThreadDump::VM_ThreadDump(ThreadDumpResult* result,
                             int max_depth,
                             bool with_locked_monitors,
                             bool with_locked_synchronizers) {
  _result = result;
  _num_threads = 0; // 0 indicates all threads
  _threads = NULL;
  _result = result;
  _max_depth = max_depth;
  _with_locked_monitors = with_locked_monitors;
  _with_locked_synchronizers = with_locked_synchronizers;
}

VM_ThreadDump::VM_ThreadDump(ThreadDumpResult* result,
                             GrowableArray<instanceHandle>* threads,
                             int num_threads,
                             int max_depth,
                             bool with_locked_monitors,
                             bool with_locked_synchronizers) {
  _result = result;
  _num_threads = num_threads;
  _threads = threads;
  _result = result;
  _max_depth = max_depth;
  _with_locked_monitors = with_locked_monitors;
  _with_locked_synchronizers = with_locked_synchronizers;
}

bool VM_ThreadDump::doit_prologue() {
  assert(Thread::current()->is_Java_thread(), "just checking");

  // Load AbstractOwnableSynchronizer class before taking thread snapshots
  java_util_concurrent_locks_AbstractOwnableSynchronizer::initialize(JavaThread::current());

  if (_with_locked_synchronizers) {
    // Acquire Heap_lock to dump concurrent locks
    Heap_lock->lock();
  }

  return true;
}

void VM_ThreadDump::doit_epilogue() {
  if (_with_locked_synchronizers) {
    // Release Heap_lock
    Heap_lock->unlock();
  }
}

void VM_ThreadDump::doit() {
  ResourceMark rm;

  ConcurrentLocksDump concurrent_locks(true);
  if (_with_locked_synchronizers) {
    concurrent_locks.dump_at_safepoint();
  }

  if (_num_threads == 0) {
    // Snapshot all live threads
    for (JavaThread* jt = Threads::first(); jt != NULL; jt = jt->next()) {
      if (jt->is_exiting() ||
          jt->is_hidden_from_external_view())  {
        // skip terminating threads and hidden threads
        continue;
      }
      ThreadConcurrentLocks* tcl = NULL;
      if (_with_locked_synchronizers) {
        tcl = concurrent_locks.thread_concurrent_locks(jt);
      }
      ThreadSnapshot* ts = snapshot_thread(jt, tcl);
      _result->add_thread_snapshot(ts);
    }
  } else {
    // Snapshot threads in the given _threads array
    // A dummy snapshot is created if a thread doesn't exist
    for (int i = 0; i < _num_threads; i++) {
      instanceHandle th = _threads->at(i);
      if (th() == NULL) {
        // skip if the thread doesn't exist
        // Add a dummy snapshot
        _result->add_thread_snapshot(new ThreadSnapshot());
        continue;
      }

      // Dump thread stack only if the thread is alive and not exiting
      // and not VM internal thread.
      JavaThread* jt = java_lang_Thread::thread(th());
      if (jt == NULL || /* thread not alive */
          jt->is_exiting() ||
          jt->is_hidden_from_external_view())  {
        // add a NULL snapshot if skipped
        _result->add_thread_snapshot(new ThreadSnapshot());
        continue;
      }
      ThreadConcurrentLocks* tcl = NULL;
      if (_with_locked_synchronizers) {
        tcl = concurrent_locks.thread_concurrent_locks(jt);
      }
      ThreadSnapshot* ts = snapshot_thread(jt, tcl);
      _result->add_thread_snapshot(ts);
    }
  }
}

ThreadSnapshot* VM_ThreadDump::snapshot_thread(JavaThread* java_thread, ThreadConcurrentLocks* tcl) {
  ThreadSnapshot* snapshot = new ThreadSnapshot(java_thread);
  snapshot->dump_stack_at_safepoint(_max_depth, _with_locked_monitors);
  snapshot->set_concurrent_locks(tcl);
  return snapshot;
}

volatile bool VM_Exit::_vm_exited = false;
Thread * VM_Exit::_shutdown_thread = NULL;

int VM_Exit::set_vm_exited() {
  CodeCacheExtensions::complete_step(CodeCacheExtensionsSteps::LastStep);

  Thread * thr_cur = Thread::current();

  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint already");

  int num_active = 0;

  _shutdown_thread = thr_cur;
  _vm_exited = true;                                // global flag
  for(JavaThread *thr = Threads::first(); thr != NULL; thr = thr->next())
    if (thr!=thr_cur && thr->thread_state() == _thread_in_native) {
      ++num_active;
      thr->set_terminated(JavaThread::_vm_exited);  // per-thread flag
    }

  return num_active;
}

int VM_Exit::wait_for_threads_in_native_to_block() {
  // VM exits at safepoint. This function must be called at the final safepoint
  // to wait for threads in _thread_in_native state to be quiescent.
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint already");

  Thread * thr_cur = Thread::current();
  Monitor timer(Mutex::leaf, "VM_Exit timer", true,
                Monitor::_safepoint_check_never);

  // Compiler threads need longer wait because they can access VM data directly
  // while in native. If they are active and some structures being used are
  // deleted by the shutdown sequence, they will crash. On the other hand, user
  // threads must go through native=>Java/VM transitions first to access VM
  // data, and they will be stopped during state transition. In theory, we
  // don't have to wait for user threads to be quiescent, but it's always
  // better to terminate VM when current thread is the only active thread, so
  // wait for user threads too. Numbers are in 10 milliseconds.
  int max_wait_user_thread = 30;                  // at least 300 milliseconds
  int max_wait_compiler_thread = 1000;            // at least 10 seconds

  int max_wait = max_wait_compiler_thread;

  int attempts = 0;
  while (true) {
    int num_active = 0;
    int num_active_compiler_thread = 0;

    for(JavaThread *thr = Threads::first(); thr != NULL; thr = thr->next()) {
      if (thr!=thr_cur && thr->thread_state() == _thread_in_native) {
        num_active++;
        if (thr->is_Compiler_thread()) {
          num_active_compiler_thread++;
        }
      }
    }

    if (num_active == 0) {
       return 0;
    } else if (attempts > max_wait) {
       return num_active;
    } else if (num_active_compiler_thread == 0 && attempts > max_wait_user_thread) {
       return num_active;
    }

    attempts++;

    MutexLockerEx ml(&timer, Mutex::_no_safepoint_check_flag);
    timer.wait(Mutex::_no_safepoint_check_flag, 10);
  }
}

void VM_Exit::doit() {
  CompileBroker::set_should_block();

  // Wait for a short period for threads in native to block. Any thread
  // still executing native code after the wait will be stopped at
  // native==>Java/VM barriers.
  // Among 16276 JCK tests, 94% of them come here without any threads still
  // running in native; the other 6% are quiescent within 250ms (Ultra 80).
  wait_for_threads_in_native_to_block();

  set_vm_exited();

  // cleanup globals resources before exiting. exit_globals() currently
  // cleans up outputStream resources and PerfMemory resources.
  exit_globals();

  // Check for exit hook
  exit_hook_t exit_hook = Arguments::exit_hook();
  if (exit_hook != NULL) {
    // exit hook should exit.
    exit_hook(_exit_code);
    // ... but if it didn't, we must do it here
    vm_direct_exit(_exit_code);
  } else {
    vm_direct_exit(_exit_code);
  }
}


void VM_Exit::wait_if_vm_exited() {
  if (_vm_exited &&
      Thread::current_or_null() != _shutdown_thread) {
    // _vm_exited is set at safepoint, and the Threads_lock is never released
    // we will block here until the process dies
    Threads_lock->lock_without_safepoint_check();
    ShouldNotReachHere();
  }
}

#if INCLUDE_SERVICES
void VM_PrintClassHierarchy::doit() {
  KlassHierarchy::print_class_hierarchy(_out, _print_interfaces, _print_subclasses, _classname);
}
#endif
