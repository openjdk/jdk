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

#ifndef SHARE_VM_RUNTIME_VM_OPERATIONS_HPP
#define SHARE_VM_RUNTIME_VM_OPERATIONS_HPP

#include "classfile/javaClasses.hpp"
#include "memory/allocation.hpp"
#include "oops/oop.hpp"
#include "runtime/thread.hpp"
#include "utilities/top.hpp"

// The following classes are used for operations
// initiated by a Java thread but that must
// take place in the VMThread.

#define VM_OP_ENUM(type)   VMOp_##type,

// Note: When new VM_XXX comes up, add 'XXX' to the template table.
#define VM_OPS_DO(template)                       \
  template(Dummy)                                 \
  template(ThreadStop)                            \
  template(ThreadDump)                            \
  template(PrintThreads)                          \
  template(FindDeadlocks)                         \
  template(ForceSafepoint)                        \
  template(ForceAsyncSafepoint)                   \
  template(Deoptimize)                            \
  template(DeoptimizeFrame)                       \
  template(DeoptimizeAll)                         \
  template(ZombieAll)                             \
  template(UnlinkSymbols)                         \
  template(Verify)                                \
  template(PrintJNI)                              \
  template(HeapDumper)                            \
  template(DeoptimizeTheWorld)                    \
  template(CollectForMetadataAllocation)          \
  template(GC_HeapInspection)                     \
  template(GenCollectFull)                        \
  template(GenCollectFullConcurrent)              \
  template(GenCollectForAllocation)               \
  template(ParallelGCFailedAllocation)            \
  template(ParallelGCSystemGC)                    \
  template(CGC_Operation)                         \
  template(CMS_Initial_Mark)                      \
  template(CMS_Final_Remark)                      \
  template(G1CollectFull)                         \
  template(G1CollectForAllocation)                \
  template(G1IncCollectionPause)                  \
  template(EnableBiasedLocking)                   \
  template(RevokeBias)                            \
  template(BulkRevokeBias)                        \
  template(PopulateDumpSharedSpace)               \
  template(JNIFunctionTableCopier)                \
  template(RedefineClasses)                       \
  template(GetOwnedMonitorInfo)                   \
  template(GetObjectMonitorUsage)                 \
  template(GetCurrentContendedMonitor)            \
  template(GetStackTrace)                         \
  template(GetMultipleStackTraces)                \
  template(GetAllStackTraces)                     \
  template(GetThreadListStackTraces)              \
  template(GetFrameCount)                         \
  template(GetFrameLocation)                      \
  template(ChangeBreakpoints)                     \
  template(GetOrSetLocal)                         \
  template(GetCurrentLocation)                    \
  template(EnterInterpOnlyMode)                   \
  template(ChangeSingleStep)                      \
  template(HeapWalkOperation)                     \
  template(HeapIterateOperation)                  \
  template(ReportJavaOutOfMemory)                 \
  template(JFRCheckpoint)                         \
  template(Exit)                                  \
  template(LinuxDllLoad)                          \

class VM_Operation: public CHeapObj<mtInternal> {
 public:
  enum Mode {
    _safepoint,       // blocking,        safepoint, vm_op C-heap allocated
    _no_safepoint,    // blocking,     no safepoint, vm_op C-Heap allocated
    _concurrent,      // non-blocking, no safepoint, vm_op C-Heap allocated
    _async_safepoint  // non-blocking,    safepoint, vm_op C-Heap allocated
  };

  enum VMOp_Type {
    VM_OPS_DO(VM_OP_ENUM)
    VMOp_Terminating
  };

 private:
  Thread*         _calling_thread;
  ThreadPriority  _priority;
  long            _timestamp;
  VM_Operation*   _next;
  VM_Operation*   _prev;

  // The VM operation name array
  static const char* _names[];

 public:
  VM_Operation()  { _calling_thread = NULL; _next = NULL; _prev = NULL; }
  virtual ~VM_Operation() {}

  // VM operation support (used by VM thread)
  Thread* calling_thread() const                 { return _calling_thread; }
  ThreadPriority priority()                      { return _priority; }
  void set_calling_thread(Thread* thread, ThreadPriority priority);

  long timestamp() const              { return _timestamp; }
  void set_timestamp(long timestamp)  { _timestamp = timestamp; }

  // Called by VM thread - does in turn invoke doit(). Do not override this
  void evaluate();

  // evaluate() is called by the VMThread and in turn calls doit().
  // If the thread invoking VMThread::execute((VM_Operation*) is a JavaThread,
  // doit_prologue() is called in that thread before transferring control to
  // the VMThread.
  // If doit_prologue() returns true the VM operation will proceed, and
  // doit_epilogue() will be called by the JavaThread once the VM operation
  // completes. If doit_prologue() returns false the VM operation is cancelled.
  virtual void doit()                            = 0;
  virtual bool doit_prologue()                   { return true; };
  virtual void doit_epilogue()                   {}; // Note: Not called if mode is: _concurrent

  // Type test
  virtual bool is_methodCompiler() const         { return false; }

  // Linking
  VM_Operation *next() const                     { return _next; }
  VM_Operation *prev() const                     { return _prev; }
  void set_next(VM_Operation *next)              { _next = next; }
  void set_prev(VM_Operation *prev)              { _prev = prev; }

  // Configuration. Override these appropriatly in subclasses.
  virtual VMOp_Type type() const = 0;
  virtual Mode evaluation_mode() const            { return _safepoint; }
  virtual bool allow_nested_vm_operations() const { return false; }
  virtual bool is_cheap_allocated() const         { return false; }
  virtual void oops_do(OopClosure* f)              { /* do nothing */ };

  // CAUTION: <don't hang yourself with following rope>
  // If you override these methods, make sure that the evaluation
  // of these methods is race-free and non-blocking, since these
  // methods may be evaluated either by the mutators or by the
  // vm thread, either concurrently with mutators or with the mutators
  // stopped. In other words, taking locks is verboten, and if there
  // are any races in evaluating the conditions, they'd better be benign.
  virtual bool evaluate_at_safepoint() const {
    return evaluation_mode() == _safepoint  ||
           evaluation_mode() == _async_safepoint;
  }
  virtual bool evaluate_concurrently() const {
    return evaluation_mode() == _concurrent ||
           evaluation_mode() == _async_safepoint;
  }

  static const char* mode_to_string(Mode mode);

  // Debugging
  void print_on_error(outputStream* st) const;
  const char* name() const { return _names[type()]; }
  static const char* name(int type) {
    assert(type >= 0 && type < VMOp_Terminating, "invalid VM operation type");
    return _names[type];
  }
#ifndef PRODUCT
  void print_on(outputStream* st) const { print_on_error(st); }
#endif
};

class VM_ThreadStop: public VM_Operation {
 private:
  oop     _thread;        // The Thread that the Throwable is thrown against
  oop     _throwable;     // The Throwable thrown at the target Thread
 public:
  // All oops are passed as JNI handles, since there is no guarantee that a GC might happen before the
  // VM operation is executed.
  VM_ThreadStop(oop thread, oop throwable) {
    _thread    = thread;
    _throwable = throwable;
  }
  VMOp_Type type() const                         { return VMOp_ThreadStop; }
  oop target_thread() const                      { return _thread; }
  oop throwable() const                          { return _throwable;}
  void doit();
  // We deoptimize if top-most frame is compiled - this might require a C2I adapter to be generated
  bool allow_nested_vm_operations() const        { return true; }
  Mode evaluation_mode() const                   { return _async_safepoint; }
  bool is_cheap_allocated() const                { return true; }

  // GC support
  void oops_do(OopClosure* f) {
    f->do_oop(&_thread); f->do_oop(&_throwable);
  }
};

// dummy vm op, evaluated just to force a safepoint
class VM_ForceSafepoint: public VM_Operation {
 public:
  VM_ForceSafepoint() {}
  void doit()         {}
  VMOp_Type type() const { return VMOp_ForceSafepoint; }
};

// dummy vm op, evaluated just to force a safepoint
class VM_ForceAsyncSafepoint: public VM_Operation {
 public:
  VM_ForceAsyncSafepoint() {}
  void doit()              {}
  VMOp_Type type() const                         { return VMOp_ForceAsyncSafepoint; }
  Mode evaluation_mode() const                   { return _async_safepoint; }
  bool is_cheap_allocated() const                { return true; }
};

class VM_Deoptimize: public VM_Operation {
 public:
  VM_Deoptimize() {}
  VMOp_Type type() const                        { return VMOp_Deoptimize; }
  void doit();
  bool allow_nested_vm_operations() const        { return true; }
};


// Deopt helper that can deoptimize frames in threads other than the
// current thread.  Only used through Deoptimization::deoptimize_frame.
class VM_DeoptimizeFrame: public VM_Operation {
  friend class Deoptimization;

 private:
  JavaThread* _thread;
  intptr_t*   _id;
  VM_DeoptimizeFrame(JavaThread* thread, intptr_t* id);

 public:
  VMOp_Type type() const                         { return VMOp_DeoptimizeFrame; }
  void doit();
  bool allow_nested_vm_operations() const        { return true;  }
};

#ifndef PRODUCT
class VM_DeoptimizeAll: public VM_Operation {
 private:
  KlassHandle _dependee;
 public:
  VM_DeoptimizeAll() {}
  VMOp_Type type() const                         { return VMOp_DeoptimizeAll; }
  void doit();
  bool allow_nested_vm_operations() const        { return true; }
};


class VM_ZombieAll: public VM_Operation {
 public:
  VM_ZombieAll() {}
  VMOp_Type type() const                         { return VMOp_ZombieAll; }
  void doit();
  bool allow_nested_vm_operations() const        { return true; }
};
#endif // PRODUCT

class VM_UnlinkSymbols: public VM_Operation {
 public:
  VM_UnlinkSymbols() {}
  VMOp_Type type() const                         { return VMOp_UnlinkSymbols; }
  void doit();
  bool allow_nested_vm_operations() const        { return true; }
};

class VM_Verify: public VM_Operation {
 private:
  bool _silent;
 public:
  VM_Verify(bool silent = VerifySilently) : _silent(silent) {}
  VMOp_Type type() const { return VMOp_Verify; }
  void doit();
};


class VM_PrintThreads: public VM_Operation {
 private:
  outputStream* _out;
  bool _print_concurrent_locks;
 public:
  VM_PrintThreads()                                                { _out = tty; _print_concurrent_locks = PrintConcurrentLocks; }
  VM_PrintThreads(outputStream* out, bool print_concurrent_locks)  { _out = out; _print_concurrent_locks = print_concurrent_locks; }
  VMOp_Type type() const                                           {  return VMOp_PrintThreads; }
  void doit();
  bool doit_prologue();
  void doit_epilogue();
};

class VM_PrintJNI: public VM_Operation {
 private:
  outputStream* _out;
 public:
  VM_PrintJNI()                         { _out = tty; }
  VM_PrintJNI(outputStream* out)        { _out = out; }
  VMOp_Type type() const                { return VMOp_PrintJNI; }
  void doit();
};

class DeadlockCycle;
class VM_FindDeadlocks: public VM_Operation {
 private:
  bool           _concurrent_locks;
  DeadlockCycle* _deadlocks;
  outputStream*  _out;

 public:
  VM_FindDeadlocks(bool concurrent_locks) :  _concurrent_locks(concurrent_locks), _out(NULL), _deadlocks(NULL) {};
  VM_FindDeadlocks(outputStream* st) : _concurrent_locks(true), _out(st), _deadlocks(NULL) {};
  ~VM_FindDeadlocks();

  DeadlockCycle* result()      { return _deadlocks; };
  VMOp_Type type() const       { return VMOp_FindDeadlocks; }
  void doit();
  bool doit_prologue();
};

class ThreadDumpResult;
class ThreadSnapshot;
class ThreadConcurrentLocks;

class VM_ThreadDump : public VM_Operation {
 private:
  ThreadDumpResult*              _result;
  int                            _num_threads;
  GrowableArray<instanceHandle>* _threads;
  int                            _max_depth;
  bool                           _with_locked_monitors;
  bool                           _with_locked_synchronizers;

  ThreadSnapshot* snapshot_thread(JavaThread* java_thread, ThreadConcurrentLocks* tcl);

 public:
  VM_ThreadDump(ThreadDumpResult* result,
                int max_depth,  // -1 indicates entire stack
                bool with_locked_monitors,
                bool with_locked_synchronizers);

  VM_ThreadDump(ThreadDumpResult* result,
                GrowableArray<instanceHandle>* threads,
                int num_threads, // -1 indicates entire stack
                int max_depth,
                bool with_locked_monitors,
                bool with_locked_synchronizers);

  VMOp_Type type() const { return VMOp_ThreadDump; }
  void doit();
  bool doit_prologue();
  void doit_epilogue();
};


class VM_Exit: public VM_Operation {
 private:
  int  _exit_code;
  static volatile bool _vm_exited;
  static Thread * _shutdown_thread;
  static void wait_if_vm_exited();
 public:
  VM_Exit(int exit_code) {
    _exit_code = exit_code;
  }
  static int wait_for_threads_in_native_to_block();
  static int set_vm_exited();
  static bool vm_exited()                      { return _vm_exited; }
  static void block_if_vm_exited() {
    if (_vm_exited) {
      wait_if_vm_exited();
    }
  }
  VMOp_Type type() const { return VMOp_Exit; }
  void doit();
};

#endif // SHARE_VM_RUNTIME_VM_OPERATIONS_HPP
