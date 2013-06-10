/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_COMPILER_COMPILEBROKER_HPP
#define SHARE_VM_COMPILER_COMPILEBROKER_HPP

#include "ci/compilerInterface.hpp"
#include "compiler/abstractCompiler.hpp"
#include "runtime/perfData.hpp"

class nmethod;
class nmethodLocker;

// CompileTask
//
// An entry in the compile queue.  It represents a pending or current
// compilation.
class CompileTask : public CHeapObj<mtCompiler> {
  friend class VMStructs;

 private:
  Monitor*     _lock;
  uint         _compile_id;
  Method*      _method;
  jobject      _method_holder;
  int          _osr_bci;
  bool         _is_complete;
  bool         _is_success;
  bool         _is_blocking;
  int          _comp_level;
  int          _num_inlined_bytecodes;
  nmethodLocker* _code_handle;  // holder of eventual result
  CompileTask* _next, *_prev;

  // Fields used for logging why the compilation was initiated:
  jlong        _time_queued;  // in units of os::elapsed_counter()
  Method*      _hot_method;   // which method actually triggered this task
  jobject      _hot_method_holder;
  int          _hot_count;    // information about its invocation counter
  const char*  _comment;      // more info about the task

 public:
  CompileTask() {
    _lock = new Monitor(Mutex::nonleaf+2, "CompileTaskLock");
  }

  void initialize(int compile_id, methodHandle method, int osr_bci, int comp_level,
                  methodHandle hot_method, int hot_count, const char* comment,
                  bool is_blocking);

  void free();

  int          compile_id() const                { return _compile_id; }
  Method*      method() const                    { return _method; }
  int          osr_bci() const                   { return _osr_bci; }
  bool         is_complete() const               { return _is_complete; }
  bool         is_blocking() const               { return _is_blocking; }
  bool         is_success() const                { return _is_success; }

  nmethodLocker* code_handle() const             { return _code_handle; }
  void         set_code_handle(nmethodLocker* l) { _code_handle = l; }
  nmethod*     code() const;                     // _code_handle->code()
  void         set_code(nmethod* nm);            // _code_handle->set_code(nm)

  Monitor*     lock() const                      { return _lock; }

  void         mark_complete()                   { _is_complete = true; }
  void         mark_success()                    { _is_success = true; }

  int          comp_level()                      { return _comp_level;}
  void         set_comp_level(int comp_level)    { _comp_level = comp_level;}

  int          num_inlined_bytecodes() const     { return _num_inlined_bytecodes; }
  void         set_num_inlined_bytecodes(int n)  { _num_inlined_bytecodes = n; }

  CompileTask* next() const                      { return _next; }
  void         set_next(CompileTask* next)       { _next = next; }
  CompileTask* prev() const                      { return _prev; }
  void         set_prev(CompileTask* prev)       { _prev = prev; }

private:
  static void  print_compilation_impl(outputStream* st, Method* method, int compile_id, int comp_level,
                                      bool is_osr_method = false, int osr_bci = -1, bool is_blocking = false,
                                      const char* msg = NULL, bool short_form = false);

public:
  void         print_compilation(outputStream* st = tty, const char* msg = NULL, bool short_form = false);
  static void  print_compilation(outputStream* st, const nmethod* nm, const char* msg = NULL, bool short_form = false) {
    print_compilation_impl(st, nm->method(), nm->compile_id(), nm->comp_level(),
                           nm->is_osr_method(), nm->is_osr_method() ? nm->osr_entry_bci() : -1, /*is_blocking*/ false,
                           msg, short_form);
  }

  static void  print_inlining(outputStream* st, ciMethod* method, int inline_level, int bci, const char* msg = NULL);
  static void  print_inlining(ciMethod* method, int inline_level, int bci, const char* msg = NULL) {
    print_inlining(tty, method, inline_level, bci, msg);
  }

  // Redefine Classes support
  void mark_on_stack();

  static void  print_inline_indent(int inline_level, outputStream* st = tty);

  void         print();
  void         print_line();
  void         print_line_on_error(outputStream* st, char* buf, int buflen);

  void         log_task(xmlStream* log);
  void         log_task_queued();
  void         log_task_start(CompileLog* log);
  void         log_task_done(CompileLog* log);
};

// CompilerCounters
//
// Per Compiler Performance Counters.
//
class CompilerCounters : public CHeapObj<mtCompiler> {

  public:
    enum {
      cmname_buffer_length = 160
    };

  private:

    char _current_method[cmname_buffer_length];
    PerfStringVariable* _perf_current_method;

    int  _compile_type;
    PerfVariable* _perf_compile_type;

    PerfCounter* _perf_time;
    PerfCounter* _perf_compiles;

  public:
    CompilerCounters(const char* name, int instance, TRAPS);

    // these methods should be called in a thread safe context

    void set_current_method(const char* method) {
      strncpy(_current_method, method, (size_t)cmname_buffer_length);
      if (UsePerfData) _perf_current_method->set_value(method);
    }

    char* current_method()                  { return _current_method; }

    void set_compile_type(int compile_type) {
      _compile_type = compile_type;
      if (UsePerfData) _perf_compile_type->set_value((jlong)compile_type);
    }

    int compile_type()                       { return _compile_type; }

    PerfCounter* time_counter()              { return _perf_time; }
    PerfCounter* compile_counter()           { return _perf_compiles; }
};

// CompileQueue
//
// A list of CompileTasks.
class CompileQueue : public CHeapObj<mtCompiler> {
 private:
  const char* _name;
  Monitor*    _lock;

  CompileTask* _first;
  CompileTask* _last;

  int _size;
 public:
  CompileQueue(const char* name, Monitor* lock) {
    _name = name;
    _lock = lock;
    _first = NULL;
    _last = NULL;
    _size = 0;
  }

  const char*  name() const                      { return _name; }
  Monitor*     lock() const                      { return _lock; }

  void         add(CompileTask* task);
  void         remove(CompileTask* task);
  CompileTask* first()                           { return _first; }
  CompileTask* last()                            { return _last;  }

  CompileTask* get();

  bool         is_empty() const                  { return _first == NULL; }
  int          size()     const                  { return _size;          }

  // Redefine Classes support
  void mark_on_stack();

  void         print();
};

// CompileTaskWrapper
//
// Assign this task to the current thread.  Deallocate the task
// when the compilation is complete.
class CompileTaskWrapper : StackObj {
public:
  CompileTaskWrapper(CompileTask* task);
  ~CompileTaskWrapper();
};


// Compilation
//
// The broker for all compilation requests.
class CompileBroker: AllStatic {
 friend class Threads;
  friend class CompileTaskWrapper;

 public:
  enum {
    name_buffer_length = 100
  };

  // Compile type Information for print_last_compile() and CompilerCounters
  enum { no_compile, normal_compile, osr_compile, native_compile };

 private:
  static bool _initialized;
  static volatile bool _should_block;

  // This flag can be used to stop compilation or turn it back on
  static volatile jint _should_compile_new_jobs;

  // The installed compiler(s)
  static AbstractCompiler* _compilers[2];

  // These counters are used for assigning id's to each compilation
  static uint _compilation_id;
  static uint _osr_compilation_id;
  static uint _native_compilation_id;

  static int  _last_compile_type;
  static int  _last_compile_level;
  static char _last_method_compiled[name_buffer_length];

  static CompileQueue* _c2_method_queue;
  static CompileQueue* _c1_method_queue;
  static CompileTask* _task_free_list;

  static GrowableArray<CompilerThread*>* _method_threads;

  // performance counters
  static PerfCounter* _perf_total_compilation;
  static PerfCounter* _perf_native_compilation;
  static PerfCounter* _perf_osr_compilation;
  static PerfCounter* _perf_standard_compilation;

  static PerfCounter* _perf_total_bailout_count;
  static PerfCounter* _perf_total_invalidated_count;
  static PerfCounter* _perf_total_compile_count;
  static PerfCounter* _perf_total_native_compile_count;
  static PerfCounter* _perf_total_osr_compile_count;
  static PerfCounter* _perf_total_standard_compile_count;

  static PerfCounter* _perf_sum_osr_bytes_compiled;
  static PerfCounter* _perf_sum_standard_bytes_compiled;
  static PerfCounter* _perf_sum_nmethod_size;
  static PerfCounter* _perf_sum_nmethod_code_size;

  static PerfStringVariable* _perf_last_method;
  static PerfStringVariable* _perf_last_failed_method;
  static PerfStringVariable* _perf_last_invalidated_method;
  static PerfVariable*       _perf_last_compile_type;
  static PerfVariable*       _perf_last_compile_size;
  static PerfVariable*       _perf_last_failed_type;
  static PerfVariable*       _perf_last_invalidated_type;

  // Timers and counters for generating statistics
  static elapsedTimer _t_total_compilation;
  static elapsedTimer _t_osr_compilation;
  static elapsedTimer _t_standard_compilation;

  static int _total_compile_count;
  static int _total_bailout_count;
  static int _total_invalidated_count;
  static int _total_native_compile_count;
  static int _total_osr_compile_count;
  static int _total_standard_compile_count;
  static int _sum_osr_bytes_compiled;
  static int _sum_standard_bytes_compiled;
  static int _sum_nmethod_size;
  static int _sum_nmethod_code_size;
  static long _peak_compilation_time;

  static CompilerThread* make_compiler_thread(const char* name, CompileQueue* queue, CompilerCounters* counters, TRAPS);
  static void init_compiler_threads(int c1_compiler_count, int c2_compiler_count);
  static bool compilation_is_complete  (methodHandle method, int osr_bci, int comp_level);
  static bool compilation_is_prohibited(methodHandle method, int osr_bci, int comp_level);
  static uint assign_compile_id        (methodHandle method, int osr_bci);
  static bool is_compile_blocking      (methodHandle method, int osr_bci);
  static void preload_classes          (methodHandle method, TRAPS);

  static CompileTask* create_compile_task(CompileQueue* queue,
                                          int           compile_id,
                                          methodHandle  method,
                                          int           osr_bci,
                                          int           comp_level,
                                          methodHandle  hot_method,
                                          int           hot_count,
                                          const char*   comment,
                                          bool          blocking);
  static CompileTask* allocate_task();
  static void free_task(CompileTask* task);
  static void wait_for_completion(CompileTask* task);

  static void invoke_compiler_on_method(CompileTask* task);
  static void set_last_compile(CompilerThread *thread, methodHandle method, bool is_osr, int comp_level);
  static void push_jni_handle_block();
  static void pop_jni_handle_block();
  static bool check_break_at(methodHandle method, int compile_id, bool is_osr);
  static void collect_statistics(CompilerThread* thread, elapsedTimer time, CompileTask* task);

  static void compile_method_base(methodHandle method,
                                  int osr_bci,
                                  int comp_level,
                                  methodHandle hot_method,
                                  int hot_count,
                                  const char* comment,
                                  Thread* thread);
  static CompileQueue* compile_queue(int comp_level) {
    if (is_c2_compile(comp_level)) return _c2_method_queue;
    if (is_c1_compile(comp_level)) return _c1_method_queue;
    return NULL;
  }
 public:
  enum {
    // The entry bci used for non-OSR compilations.
    standard_entry_bci = InvocationEntryBci
  };

  static AbstractCompiler* compiler(int comp_level) {
    if (is_c2_compile(comp_level)) return _compilers[1]; // C2
    if (is_c1_compile(comp_level)) return _compilers[0]; // C1
    return NULL;
  }

  static bool compilation_is_in_queue(methodHandle method, int osr_bci);
  static int queue_size(int comp_level) {
    CompileQueue *q = compile_queue(comp_level);
    return q != NULL ? q->size() : 0;
  }
  static void compilation_init();
  static void init_compiler_thread_log();
  static nmethod* compile_method(methodHandle method,
                                 int osr_bci,
                                 int comp_level,
                                 methodHandle hot_method,
                                 int hot_count,
                                 const char* comment, Thread* thread);

  static void compiler_thread_loop();

  static uint get_compilation_id() { return _compilation_id; }
  static bool is_idle();

  // Set _should_block.
  // Call this from the VM, with Threads_lock held and a safepoint requested.
  static void set_should_block();

  // Call this from the compiler at convenient points, to poll for _should_block.
  static void maybe_block();

  enum {
    // Flags for toggling compiler activity
    stop_compilation = 0,
    run_compilation  = 1
  };

  static bool should_compile_new_jobs() { return UseCompiler && (_should_compile_new_jobs == run_compilation); }
  static bool set_should_compile_new_jobs(jint new_state) {
    // Return success if the current caller set it
    jint old = Atomic::cmpxchg(new_state, &_should_compile_new_jobs, 1-new_state);
    return (old == (1-new_state));
  }
  static void handle_full_code_cache();

  // Return total compilation ticks
  static jlong total_compilation_ticks() {
    return _perf_total_compilation != NULL ? _perf_total_compilation->get_value() : 0;
  }

  // Redefine Classes support
  static void mark_on_stack();

  // Print a detailed accounting of compilation time
  static void print_times();

  // Debugging output for failure
  static void print_last_compile();

  static void print_compiler_threads_on(outputStream* st);

  // compiler name for debugging
  static const char* compiler_name(int comp_level);

  static int get_total_compile_count() {          return _total_compile_count; }
  static int get_total_bailout_count() {          return _total_bailout_count; }
  static int get_total_invalidated_count() {      return _total_invalidated_count; }
  static int get_total_native_compile_count() {   return _total_native_compile_count; }
  static int get_total_osr_compile_count() {      return _total_osr_compile_count; }
  static int get_total_standard_compile_count() { return _total_standard_compile_count; }
  static int get_sum_osr_bytes_compiled() {       return _sum_osr_bytes_compiled; }
  static int get_sum_standard_bytes_compiled() {  return _sum_standard_bytes_compiled; }
  static int get_sum_nmethod_size() {             return _sum_nmethod_size;}
  static int get_sum_nmethod_code_size() {        return _sum_nmethod_code_size; }
  static long get_peak_compilation_time() {       return _peak_compilation_time; }
  static long get_total_compilation_time() {      return _t_total_compilation.milliseconds(); }
};

#endif // SHARE_VM_COMPILER_COMPILEBROKER_HPP
