/*
 * Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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

// a simple flat profiler for Java


// Forward declaration of classes defined in this header file
class ThreadProfiler;
class ThreadProfilerMark;
class FlatProfiler;
class IntervalData;

// Declarations of classes defined only in the implementation.
class ProfilerNode;
class FlatProfilerTask;

enum TickPosition {
  tp_code,
  tp_native
};

// One of these guys is constructed as we enter interesting regions
// and destructed as we exit the region.  While we are in the region
// ticks are allotted to the region.
class ThreadProfilerMark: public StackObj {
public:
  // For now, the only thread-specific region is the class loader.
  enum Region { noRegion, classLoaderRegion, extraRegion, maxRegion };

  ThreadProfilerMark(Region)  KERNEL_RETURN;
  ~ThreadProfilerMark()       KERNEL_RETURN;

private:
  ThreadProfiler* _pp;
  Region _r;
};

#ifndef FPROF_KERNEL

class IntervalData VALUE_OBJ_CLASS_SPEC {
  // Just to keep these things all together
private:
  int _interpreted;
  int _compiled;
  int _native;
  int _compiling;
public:
  int interpreted() {
    return _interpreted;
  }
  int compiled() {
    return _compiled;
  }
  int native() {
    return _native;
  }
  int compiling() {
    return _compiling;
  }
  int total() {
    return (interpreted() + compiled() + native() + compiling());
  }
  void inc_interpreted() {
    _interpreted += 1;
  }
  void inc_compiled() {
    _compiled += 1;
  }
  void inc_native() {
    _native += 1;
  }
  void inc_compiling() {
    _compiling += 1;
  }
  void reset() {
    _interpreted = 0;
    _compiled = 0;
    _native = 0;
    _compiling = 0;
  }
  static void print_header(outputStream* st);
  void print_data(outputStream* st);
};
#endif // FPROF_KERNEL

class ThreadProfiler: public CHeapObj {
public:
  ThreadProfiler()    KERNEL_RETURN;
  ~ThreadProfiler()   KERNEL_RETURN;

  // Resets the profiler
  void reset()        KERNEL_RETURN;

  // Activates the profiler for a certain thread
  void engage()       KERNEL_RETURN;

  // Deactivates the profiler
  void disengage()    KERNEL_RETURN;

  // Prints the collected profiling information
  void print(const char* thread_name) KERNEL_RETURN;

  // Garbage Collection Support
  void oops_do(OopClosure* f)         KERNEL_RETURN;

#ifndef FPROF_KERNEL
private:
  // for recording ticks.
  friend class ProfilerNode;
  char* area_bottom; // preallocated area for pnodes
  char* area_top;
  char* area_limit;
  static int            table_size;
  ProfilerNode** table;

private:
  void record_interpreted_tick(JavaThread* thread, frame fr, TickPosition where, int* ticks);
  void record_compiled_tick   (JavaThread* thread, frame fr, TickPosition where);
  void interpreted_update(methodOop method, TickPosition where);
  void compiled_update   (methodOop method, TickPosition where);
  void stub_update       (methodOop method, const char* name, TickPosition where);
  void adapter_update    (TickPosition where);

  void runtime_stub_update(const CodeBlob* stub, const char* name, TickPosition where);
  void unknown_compiled_update    (const CodeBlob* cb, TickPosition where);

  void vm_update    (TickPosition where);
  void vm_update    (const char* name, TickPosition where);

  void record_tick_for_running_frame(JavaThread* thread, frame fr);
  void record_tick_for_calling_frame(JavaThread* thread, frame fr);

  void initialize();

  static int  entry(int value);


private:
  friend class FlatProfiler;
  void record_tick(JavaThread* thread);
  bool engaged;
  // so we can do percentages for this thread, and quick checks for activity
  int thread_ticks;
  int compiler_ticks;
  int interpreter_ticks;

public:
  void inc_thread_ticks() { thread_ticks += 1; }

private:
  friend class ThreadProfilerMark;
  // counters for thread-specific regions
  bool region_flag[ThreadProfilerMark::maxRegion];
  int class_loader_ticks;
  int extra_ticks;

private:
  // other thread-specific regions
  int blocked_ticks;
  enum UnknownTickSites {
      ut_null_method,
      ut_vtable_stubs,
      ut_running_frame,
      ut_calling_frame,
      ut_no_pc,
      ut_no_last_Java_frame,
      ut_unknown_thread_state,
      ut_end
  };
  int unknown_ticks_array[ut_end];
  int unknown_ticks() {
    int result = 0;
    for (int ut = 0; ut < ut_end; ut += 1) {
      result += unknown_ticks_array[ut];
    }
    return result;
  }

  elapsedTimer timer;

  // For interval timing
private:
  IntervalData _interval_data;
  IntervalData interval_data() {
    return _interval_data;
  }
  IntervalData* interval_data_ref() {
    return &_interval_data;
  }
#endif // FPROF_KERNEL
};

class FlatProfiler: AllStatic {
public:
  static void reset() KERNEL_RETURN ;
  static void engage(JavaThread* mainThread, bool fullProfile) KERNEL_RETURN ;
  static void disengage() KERNEL_RETURN ;
  static void print(int unused) KERNEL_RETURN ;
  static bool is_active() KERNEL_RETURN_(return false;) ;

  // This is NULL if each thread has its own thread profiler,
  // else this is the single thread profiler used by all threads.
  // In particular it makes a difference during garbage collection,
  // where you only want to traverse each thread profiler once.
  static ThreadProfiler* get_thread_profiler() KERNEL_RETURN_(return NULL;);

  // Garbage Collection Support
  static void oops_do(OopClosure* f) KERNEL_RETURN ;

  // Support for disassembler to inspect the PCRecorder

  // Returns the start address for a given pc
  // NULL is returned if the PCRecorder is inactive
  static address bucket_start_for(address pc) KERNEL_RETURN_(return NULL;);

  enum { MillisecsPerTick = 10 };   // ms per profiling ticks

  // Returns the number of ticks recorded for the bucket
  // pc belongs to.
  static int bucket_count_for(address pc) KERNEL_RETURN_(return 0;);

#ifndef FPROF_KERNEL

 private:
  static bool full_profile() {
    return full_profile_flag;
  }

  friend class ThreadProfiler;
  // the following group of ticks cover everything that's not attributed to individual Java methods
  static int  received_gc_ticks;      // ticks during which gc was active
  static int vm_operation_ticks;      // total ticks in vm_operations other than GC
  static int threads_lock_ticks;      // the number of times we couldn't get the Threads_lock without blocking
  static int      blocked_ticks;      // ticks when the thread was blocked.
  static int class_loader_ticks;      // total ticks in class loader
  static int        extra_ticks;      // total ticks an extra temporary measuring
  static int     compiler_ticks;      // total ticks in compilation
  static int  interpreter_ticks;      // ticks in unknown interpreted method
  static int        deopt_ticks;      // ticks in deoptimization
  static int      unknown_ticks;      // ticks that cannot be categorized
  static int     received_ticks;      // ticks that were received by task
  static int    delivered_ticks;      // ticks that were delivered by task
  static int non_method_ticks() {
    return
      ( received_gc_ticks
      + vm_operation_ticks
      + deopt_ticks
      + threads_lock_ticks
      + blocked_ticks
      + compiler_ticks
      + interpreter_ticks
      + unknown_ticks );
  }
  static elapsedTimer timer;

  // Counts of each of the byte codes
  static int*           bytecode_ticks;
  static int*           bytecode_ticks_stub;
  static void print_byte_code_statistics();

  // the ticks below are for continuous profiling (to adjust recompilation, etc.)
  static int          all_ticks;      // total count of ticks received so far
  static int      all_int_ticks;      // ticks in interpreter
  static int     all_comp_ticks;      // ticks in compiled code (+ native)
  static bool full_profile_flag;      // collecting full profile?

  // to accumulate thread-specific data
  // if we aren't profiling individual threads.
  static ThreadProfiler* thread_profiler;
  static ThreadProfiler* vm_thread_profiler;

  static void allocate_table();

  // The task that periodically interrupts things.
  friend class FlatProfilerTask;
  static FlatProfilerTask* task;
  static void record_vm_operation();
  static void record_vm_tick();
  static void record_thread_ticks();

  // For interval analysis
 private:
  static int interval_ticks_previous;  // delivered_ticks from the last interval
  static void interval_record_thread(ThreadProfiler* tp); // extract ticks from ThreadProfiler.
  static void interval_print();       // print interval data.
  static void interval_reset();       // reset interval data.
  enum {interval_print_size = 10};
  static IntervalData* interval_data;
#endif // FPROF_KERNEL
};
