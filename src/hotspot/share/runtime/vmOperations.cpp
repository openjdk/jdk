/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "compiler/compileBroker.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "logging/log.hpp"
#include "logging/logConfiguration.hpp"
#include "logging/logStream.hpp"
#include "memory/heapInspection.hpp"
#include "memory/metaspace/metaspaceReporter.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/symbol.hpp"
#include "runtime/arguments.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/objectMonitor.inline.hpp"
#include "runtime/stackFrameStream.inline.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/threads.hpp"
#include "runtime/threadSMR.inline.hpp"
#include "runtime/vmOperations.hpp"
#include "services/threadService.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/ticks.hpp"

#define VM_OP_NAME_INITIALIZE(name) #name,

const char* VM_Operation::_names[VM_Operation::VMOp_Terminating] = \
  { VM_OPS_DO(VM_OP_NAME_INITIALIZE) };

void VM_Operation::set_calling_thread(Thread* thread) {
  _calling_thread = thread;
}

void VM_Operation::evaluate() {
  ResourceMark rm;
  LogTarget(Debug, vmoperation) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    ls.print("begin ");
    print_on_error(&ls);
    ls.cr();
  }
  doit();
  if (lt.is_enabled()) {
    LogStream ls(lt);
    ls.print("end ");
    print_on_error(&ls);
    ls.cr();
  }
}

// Called by fatal error handler.
void VM_Operation::print_on_error(outputStream* st) const {
  st->print("VM_Operation (" PTR_FORMAT "): ", p2i(this));
  st->print("%s", name());

  st->print(", mode: %s", evaluate_at_safepoint() ? "safepoint" : "no safepoint");

  if (calling_thread()) {
    st->print(", requested by thread " PTR_FORMAT, p2i(calling_thread()));
  }
}

void VM_ClearICs::doit() {
  if (_preserve_static_stubs) {
    CodeCache::cleanup_inline_caches_whitebox();
  } else {
    CodeCache::clear_inline_caches();
  }
}

void VM_CleanClassLoaderDataMetaspaces::doit() {
  ClassLoaderDataGraph::walk_metadata_and_clean_metaspaces();
}

void VM_RehashStringTable::doit() {
  StringTable::rehash_table();
}

void VM_RehashSymbolTable::doit() {
  SymbolTable::rehash_table();
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
  JavaThreadIteratorWithHandle jtiwh;
  // deoptimize all java threads in the system
  if (DeoptimizeALot) {
    for (; JavaThread *thread = jtiwh.next(); ) {
      if (thread->has_last_Java_frame()) {
        thread->deoptimize();
      }
    }
  } else if (DeoptimizeRandom) {

    // Deoptimize some selected threads and frames
    int tnum = os::random() & 0x3;
    int fnum =  os::random() & 0x3;
    int tcount = 0;
    for (; JavaThread *thread = jtiwh.next(); ) {
      if (thread->has_last_Java_frame()) {
        if (tcount++ == tnum)  {
        tcount = 0;
          int fcount = 0;
          // Deoptimize some selected frames.
          for(StackFrameStream fst(thread, false /* update */, true /* process_frames */); !fst.is_done(); fst.next()) {
            if (fst.current()->can_be_deoptimized()) {
              if (fcount++ == fnum) {
                fcount = 0;
                Deoptimization::deoptimize(thread, *fst.current());
              }
            }
          }
        }
      }
    }
  }
}


void VM_ZombieAll::doit() {
  JavaThread::cast(calling_thread())->make_zombies();
}

#endif // !PRODUCT

bool VM_PrintThreads::doit_prologue() {
  // Get Heap_lock if concurrent locks will be dumped
  if (_print_concurrent_locks) {
    Heap_lock->lock();
  }
  return true;
}

void VM_PrintThreads::doit() {
  Threads::print_on(_out, true, false, _print_concurrent_locks, _print_extended_info);
  if (_print_jni_handle_info) {
    JNIHandles::print_on(_out);
  }
}

void VM_PrintThreads::doit_epilogue() {
  if (_print_concurrent_locks) {
    // Release Heap_lock
    Heap_lock->unlock();
  }
}

void VM_PrintMetadata::doit() {
  metaspace::MetaspaceReporter::print_report(_out, _scale, _flags);
}

VM_FindDeadlocks::~VM_FindDeadlocks() {
  if (_deadlocks != nullptr) {
    DeadlockCycle* cycle = _deadlocks;
    while (cycle != nullptr) {
      DeadlockCycle* d = cycle;
      cycle = cycle->next();
      delete d;
    }
  }
}

void VM_FindDeadlocks::doit() {
  // Update the hazard ptr in the originating thread to the current
  // list of threads. This VM operation needs the current list of
  // threads for proper deadlock detection and those are the
  // JavaThreads we need to be protected when we return info to the
  // originating thread.
  _setter.set();

  _deadlocks = ThreadService::find_deadlocks_at_safepoint(_setter.list(), _concurrent_locks);
  if (_out != nullptr) {
    int num_deadlocks = 0;
    for (DeadlockCycle* cycle = _deadlocks; cycle != nullptr; cycle = cycle->next()) {
      num_deadlocks++;
      cycle->print_on_with(_setter.list(), _out);
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
  _threads = nullptr;
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
  _max_depth = max_depth;
  _with_locked_monitors = with_locked_monitors;
  _with_locked_synchronizers = with_locked_synchronizers;
}

bool VM_ThreadDump::doit_prologue() {
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

// Hash table of int64_t to a list of ObjectMonitor* owned by the JavaThread.
// The JavaThread's owner key is either a JavaThread* or a stack lock
// address in the JavaThread so we use "int64_t".
//
class ObjectMonitorsDump : public MonitorClosure, public ObjectMonitorsView {
 private:
  static unsigned int ptr_hash(int64_t const& s1) {
    // 2654435761 = 2^32 * Phi (golden ratio)
    return (unsigned int)(((uint32_t)(uintptr_t)s1) * 2654435761u);
  }

 private:
  using ObjectMonitorList = GrowableArrayCHeap<ObjectMonitor*, mtThread>;

  // HashTable SIZE is specified at compile time so we
  // use 1031 which is the first prime after 1024.
  typedef HashTable<int64_t, ObjectMonitorList, 1031, AnyObj::C_HEAP, mtThread,
                            &ObjectMonitorsDump::ptr_hash> PtrTable;
  PtrTable* _ptrs;
  size_t _key_count;
  size_t _om_count;

  void add(ObjectMonitor* monitor) {
    int64_t key = monitor->owner();

    bool created = false;
    ObjectMonitorList* list = _ptrs->put_if_absent(key, &created);
    if (created) {
      _key_count++;
    }

    assert(list->find(monitor) == -1, "Should not contain duplicates");
    list->push(monitor);  // Add the ObjectMonitor to the list.
    _om_count++;
  }

 public:
  // HashTable is passed to various functions and populated in
  // different places so we allocate it using C_HEAP to make it immune
  // from any ResourceMarks that happen to be in the code paths.
  ObjectMonitorsDump() : _ptrs(new (mtThread) PtrTable), _key_count(0), _om_count(0) {}

  ~ObjectMonitorsDump() {
    delete _ptrs;
  }

  // Implements MonitorClosure used to collect all owned monitors in the system
  void do_monitor(ObjectMonitor* monitor) override {
    assert(monitor->has_owner(), "Expects only owned monitors");

    if (monitor->has_anonymous_owner()) {
      // There's no need to collect anonymous owned monitors
      // because the caller of this code is only interested
      // in JNI owned monitors.
      return;
    }

    if (monitor->object_peek() == nullptr) {
      // JNI code doesn't necessarily keep the monitor object
      // alive. Filter out monitors with dead objects.
      return;
    }

    add(monitor);
  }

  // Implements the ObjectMonitorsView interface
  void visit(MonitorClosure* closure, JavaThread* thread) override {
    int64_t key = ObjectMonitor::owner_id_from(thread);
    ObjectMonitorList* list = _ptrs->get(key);
    if (list == nullptr) {
      return;
    }
    for (int i = 0; i < list->length(); i++) {
      closure->do_monitor(list->at(i));
    }
  }

  size_t key_count() { return _key_count; }
  size_t om_count() { return _om_count; }
};

void VM_ThreadDump::doit() {
  ResourceMark rm;

  // Set the hazard ptr in the originating thread to protect the
  // current list of threads. This VM operation needs the current list
  // of threads for a proper dump and those are the JavaThreads we need
  // to be protected when we return info to the originating thread.
  _result->set_t_list();

  ConcurrentLocksDump concurrent_locks(true);
  if (_with_locked_synchronizers) {
    concurrent_locks.dump_at_safepoint();
  }

  ObjectMonitorsDump object_monitors;
  if (_with_locked_monitors) {
    // Gather information about owned monitors.
    ObjectSynchronizer::owned_monitors_iterate(&object_monitors);

    // If there are many object monitors in the system then the above iteration
    // can start to take time. Be friendly to following thread dumps by telling
    // the MonitorDeflationThread to deflate monitors.
    //
    // This is trying to be somewhat backwards compatible with the previous
    // implementation, which performed monitor deflation right here. We might
    // want to reconsider the need to trigger monitor deflation from the thread
    // dumping and instead maybe tweak the deflation heuristics.
    ObjectSynchronizer::request_deflate_idle_monitors();
  }

  if (_num_threads == 0) {
    // Snapshot all live threads

    for (uint i = 0; i < _result->t_list()->length(); i++) {
      JavaThread* jt = _result->t_list()->thread_at(i);
      if (jt->is_exiting() ||
          jt->is_hidden_from_external_view())  {
        // skip terminating threads and hidden threads
        continue;
      }
      ThreadConcurrentLocks* tcl = nullptr;
      if (_with_locked_synchronizers) {
        tcl = concurrent_locks.thread_concurrent_locks(jt);
      }
      snapshot_thread(jt, tcl, &object_monitors);
    }
  } else {
    // Snapshot threads in the given _threads array
    // A dummy snapshot is created if a thread doesn't exist

    for (int i = 0; i < _num_threads; i++) {
      instanceHandle th = _threads->at(i);
      if (th() == nullptr) {
        // skip if the thread doesn't exist
        // Add a dummy snapshot
        _result->add_thread_snapshot();
        continue;
      }

      // Dump thread stack only if the thread is alive and not exiting
      // and not VM internal thread.
      JavaThread* jt = java_lang_Thread::thread(th());
      if (jt != nullptr && !_result->t_list()->includes(jt)) {
        // _threads[i] doesn't refer to a valid JavaThread; this check
        // is primarily for JVM_DumpThreads() which doesn't have a good
        // way to validate the _threads array.
        jt = nullptr;
      }
      if (jt == nullptr || /* thread not alive */
          jt->is_exiting() ||
          jt->is_hidden_from_external_view())  {
        // add a null snapshot if skipped
        _result->add_thread_snapshot();
        continue;
      }
      ThreadConcurrentLocks* tcl = nullptr;
      if (_with_locked_synchronizers) {
        tcl = concurrent_locks.thread_concurrent_locks(jt);
      }
      snapshot_thread(jt, tcl, &object_monitors);
    }
  }
}

void VM_ThreadDump::snapshot_thread(JavaThread* java_thread, ThreadConcurrentLocks* tcl,
                                    ObjectMonitorsView* monitors) {
  ThreadSnapshot* snapshot = _result->add_thread_snapshot(java_thread);
  snapshot->dump_stack_at_safepoint(_max_depth, _with_locked_monitors, monitors, false);
  snapshot->set_concurrent_locks(tcl);
}

volatile bool VM_Exit::_vm_exited = false;
Thread * volatile VM_Exit::_shutdown_thread = nullptr;

int VM_Exit::set_vm_exited() {

  Thread * thr_cur = Thread::current();

  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint already");

  int num_active = 0;

  _shutdown_thread = thr_cur;
  _vm_exited = true;                                // global flag
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *thr = jtiwh.next(); ) {
    if (thr != thr_cur && thr->thread_state() == _thread_in_native) {
      ++num_active;
      thr->set_terminated(JavaThread::_vm_exited);  // per-thread flag
    }
  }

  return num_active;
}

int VM_Exit::wait_for_threads_in_native_to_block() {
  // VM exits at safepoint. This function must be called at the final safepoint
  // to wait for threads in _thread_in_native state to be quiescent.
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint already");

  Thread * thr_cur = Thread::current();

  // Compiler threads need longer wait because they can access VM data directly
  // while in native. If they are active and some structures being used are
  // deleted by the shutdown sequence, they will crash. On the other hand, user
  // threads must go through native=>Java/VM transitions first to access VM
  // data, and they will be stopped during state transition. In theory, we
  // don't have to wait for user threads to be quiescent, but it's always
  // better to terminate VM when current thread is the only active thread, so
  // wait for user threads too.

  // Time per attempt. It is practical to start waiting with 10us delays
  // (around scheduling delay / timer slack), and exponentially ramp up
  // to 10ms if compiler threads are not responding.
  jlong max_wait_time = millis_to_nanos(10);
  jlong wait_time = 10000;

  jlong start_time = os::javaTimeNanos();

  // Deadline for user threads in native code.
  // User-settable flag counts "attempts" in 10ms units, to a maximum of 10s.
  jlong user_threads_deadline = start_time + (UserThreadWaitAttemptsAtExit * millis_to_nanos(10));

  // Deadline for compiler threads: at least 10 seconds.
  jlong compiler_threads_deadline = start_time + millis_to_nanos(10000);

  JavaThreadIteratorWithHandle jtiwh;
  while (true) {
    int num_active = 0;
    int num_active_compiler_thread = 0;

    jtiwh.rewind();
    for (; JavaThread *thr = jtiwh.next(); ) {
      if (thr!=thr_cur && thr->thread_state() == _thread_in_native) {
        num_active++;
        if (thr->is_Compiler_thread()) {
#if INCLUDE_JVMCI
          CompilerThread* ct = (CompilerThread*) thr;
          if (ct->compiler() == nullptr || !ct->compiler()->is_jvmci()) {
            num_active_compiler_thread++;
          } else {
            // A JVMCI compiler thread never accesses VM data structures
            // while in _thread_in_native state so there's no need to wait
            // for it and potentially add a 300 millisecond delay to VM
            // shutdown.
            num_active--;
          }
#else
          num_active_compiler_thread++;
#endif
        }
      }
    }

    jlong time = os::javaTimeNanos();

    if (num_active == 0) {
      return 0;
    }
    if (time >= compiler_threads_deadline) {
      return num_active;
    }
    if ((num_active_compiler_thread == 0) && (time >= user_threads_deadline)) {
      return num_active;
    }

    os::naked_short_nanosleep(wait_time);
    wait_time = MIN2(max_wait_time, wait_time * 2);
  }
}

void VM_Exit::doit() {

  if (VerifyBeforeExit) {
    HandleMark hm(VMThread::vm_thread());
    // Among other things, this ensures that Eden top is correct.
    Universe::heap()->prepare_for_verify();
    // Silent verification so as not to pollute normal output,
    // unless we really asked for it.
    Universe::verify();
  }

  CompileBroker::set_should_block();

  // Wait for a short period for threads in native to block. Any thread
  // still executing native code after the wait will be stopped at
  // native==>Java/VM barriers.
  // Among 16276 JCK tests, 94% of them come here without any threads still
  // running in native; the other 6% are quiescent within 250ms (Ultra 80).
  wait_for_threads_in_native_to_block();

  set_vm_exited();

  // The ObjectMonitor subsystem uses perf counters so do this before
  // we call exit_globals() so we don't run afoul of perfMemory_exit().
  ObjectSynchronizer::do_final_audit_and_print_stats();

  // We'd like to call IdealGraphPrinter::clean_up() to finalize the
  // XML logging, but we can't safely do that here. The logic to make
  // XML termination logging safe is tied to the termination of the
  // VMThread, and it doesn't terminate on this exit path. See 8222534.

  // cleanup globals resources before exiting. exit_globals() currently
  // cleans up outputStream resources and PerfMemory resources.
  exit_globals();

  LogConfiguration::finalize();

  // Check for exit hook
  exit_hook_t exit_hook = Arguments::exit_hook();
  if (exit_hook != nullptr) {
    // exit hook should exit.
    exit_hook(_exit_code);
    // ... but if it didn't, we must do it here
    vm_direct_exit(_exit_code);
  } else {
    vm_direct_exit(_exit_code);
  }
}


void VM_Exit::wait_if_vm_exited() {
  if (_vm_exited) {
    // Need to check for an unattached thread as only attached threads
    // can acquire the lock.
    Thread* current = Thread::current_or_null();
    if (current != nullptr && current != _shutdown_thread) {
      // _vm_exited is set at safepoint, and the Threads_lock is never released
      // so we will block here until the process dies.
      Threads_lock->lock();
      ShouldNotReachHere();
    }
  }
}

void VM_PrintCompileQueue::doit() {
  CompileBroker::print_compile_queues(_out);
}

#if INCLUDE_SERVICES
void VM_PrintClassHierarchy::doit() {
  KlassHierarchy::print_class_hierarchy(_out, _print_interfaces, _print_subclasses, _classname);
}
#endif
