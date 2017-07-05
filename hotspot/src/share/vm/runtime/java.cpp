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
#include "classfile/classLoader.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compilerOracle.hpp"
#include "interpreter/bytecodeHistogram.hpp"
#include "memory/genCollectedHeap.hpp"
#include "memory/oopFactory.hpp"
#include "memory/universe.hpp"
#include "oops/constantPool.hpp"
#include "oops/generateOopMap.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceOop.hpp"
#include "oops/method.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/arguments.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/compilationPolicy.hpp"
#include "runtime/fprofiler.hpp"
#include "runtime/init.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/java.hpp"
#include "runtime/memprofiler.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/statSampler.hpp"
#include "runtime/sweeper.hpp"
#include "runtime/task.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/timer.hpp"
#include "runtime/vm_operations.hpp"
#include "services/memReporter.hpp"
#include "services/memTracker.hpp"
#include "trace/tracing.hpp"
#include "utilities/dtrace.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/histogram.hpp"
#include "utilities/macros.hpp"
#include "utilities/vmError.hpp"
#ifdef TARGET_ARCH_x86
# include "vm_version_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "vm_version_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "vm_version_zero.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "vm_version_arm.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "vm_version_ppc.hpp"
#endif
#if INCLUDE_ALL_GCS
#include "gc_implementation/concurrentMarkSweep/concurrentMarkSweepThread.hpp"
#include "gc_implementation/parallelScavenge/psScavenge.hpp"
#include "gc_implementation/parallelScavenge/psScavenge.inline.hpp"
#endif // INCLUDE_ALL_GCS
#ifdef COMPILER1
#include "c1/c1_Compiler.hpp"
#include "c1/c1_Runtime1.hpp"
#endif
#ifdef COMPILER2
#include "code/compiledIC.hpp"
#include "compiler/methodLiveness.hpp"
#include "opto/compile.hpp"
#include "opto/indexSet.hpp"
#include "opto/runtime.hpp"
#endif

#ifndef USDT2
HS_DTRACE_PROBE_DECL(hotspot, vm__shutdown);
#endif /* !USDT2 */

#ifndef PRODUCT

// Statistics printing (method invocation histogram)

GrowableArray<Method*>* collected_invoked_methods;

void collect_invoked_methods(Method* m) {
  if (m->invocation_count() + m->compiled_invocation_count() >= 1 ) {
    collected_invoked_methods->push(m);
  }
}


GrowableArray<Method*>* collected_profiled_methods;

void collect_profiled_methods(Method* m) {
  Thread* thread = Thread::current();
  // This HandleMark prevents a huge amount of handles from being added
  // to the metadata_handles() array on the thread.
  HandleMark hm(thread);
  methodHandle mh(thread, m);
  if ((m->method_data() != NULL) &&
      (PrintMethodData || CompilerOracle::should_print(mh))) {
    collected_profiled_methods->push(m);
  }
}


int compare_methods(Method** a, Method** b) {
  // %%% there can be 32-bit overflow here
  return ((*b)->invocation_count() + (*b)->compiled_invocation_count())
       - ((*a)->invocation_count() + (*a)->compiled_invocation_count());
}


void print_method_invocation_histogram() {
  ResourceMark rm;
  HandleMark hm;
  collected_invoked_methods = new GrowableArray<Method*>(1024);
  SystemDictionary::methods_do(collect_invoked_methods);
  collected_invoked_methods->sort(&compare_methods);
  //
  tty->cr();
  tty->print_cr("Histogram Over MethodOop Invocation Counters (cutoff = %d):", MethodHistogramCutoff);
  tty->cr();
  tty->print_cr("____Count_(I+C)____Method________________________Module_________________");
  unsigned total = 0, int_total = 0, comp_total = 0, static_total = 0, final_total = 0,
      synch_total = 0, nativ_total = 0, acces_total = 0;
  for (int index = 0; index < collected_invoked_methods->length(); index++) {
    Method* m = collected_invoked_methods->at(index);
    int c = m->invocation_count() + m->compiled_invocation_count();
    if (c >= MethodHistogramCutoff) m->print_invocation_count();
    int_total  += m->invocation_count();
    comp_total += m->compiled_invocation_count();
    if (m->is_final())        final_total  += c;
    if (m->is_static())       static_total += c;
    if (m->is_synchronized()) synch_total  += c;
    if (m->is_native())       nativ_total  += c;
    if (m->is_accessor())     acces_total  += c;
  }
  tty->cr();
  total = int_total + comp_total;
  tty->print_cr("Invocations summary:");
  tty->print_cr("\t%9d (%4.1f%%) interpreted",  int_total,    100.0 * int_total    / total);
  tty->print_cr("\t%9d (%4.1f%%) compiled",     comp_total,   100.0 * comp_total   / total);
  tty->print_cr("\t%9d (100%%)  total",         total);
  tty->print_cr("\t%9d (%4.1f%%) synchronized", synch_total,  100.0 * synch_total  / total);
  tty->print_cr("\t%9d (%4.1f%%) final",        final_total,  100.0 * final_total  / total);
  tty->print_cr("\t%9d (%4.1f%%) static",       static_total, 100.0 * static_total / total);
  tty->print_cr("\t%9d (%4.1f%%) native",       nativ_total,  100.0 * nativ_total  / total);
  tty->print_cr("\t%9d (%4.1f%%) accessor",     acces_total,  100.0 * acces_total  / total);
  tty->cr();
  SharedRuntime::print_call_statistics(comp_total);
}

void print_method_profiling_data() {
  ResourceMark rm;
  HandleMark hm;
  collected_profiled_methods = new GrowableArray<Method*>(1024);
  SystemDictionary::methods_do(collect_profiled_methods);
  collected_profiled_methods->sort(&compare_methods);

  int count = collected_profiled_methods->length();
  int total_size = 0;
  if (count > 0) {
    for (int index = 0; index < count; index++) {
      Method* m = collected_profiled_methods->at(index);
      ttyLocker ttyl;
      tty->print_cr("------------------------------------------------------------------------");
      //m->print_name(tty);
      m->print_invocation_count();
      tty->print_cr("  mdo size: %d bytes", m->method_data()->size_in_bytes());
      tty->cr();
      // Dump data on parameters if any
      if (m->method_data() != NULL && m->method_data()->parameters_type_data() != NULL) {
        tty->fill_to(2);
        m->method_data()->parameters_type_data()->print_data_on(tty);
      }
      m->print_codes();
      total_size += m->method_data()->size_in_bytes();
    }
    tty->print_cr("------------------------------------------------------------------------");
    tty->print_cr("Total MDO size: %d bytes", total_size);
  }
}

void print_bytecode_count() {
  if (CountBytecodes || TraceBytecodes || StopInterpreterAt) {
    tty->print_cr("[BytecodeCounter::counter_value = %d]", BytecodeCounter::counter_value());
  }
}

AllocStats alloc_stats;



// General statistics printing (profiling ...)
void print_statistics() {
#ifdef ASSERT

  if (CountRuntimeCalls) {
    extern Histogram *RuntimeHistogram;
    RuntimeHistogram->print();
  }

  if (CountJNICalls) {
    extern Histogram *JNIHistogram;
    JNIHistogram->print();
  }

  if (CountJVMCalls) {
    extern Histogram *JVMHistogram;
    JVMHistogram->print();
  }

#endif

  if (MemProfiling) {
    MemProfiler::disengage();
  }

  if (CITime) {
    CompileBroker::print_times();
  }

#ifdef COMPILER1
  if ((PrintC1Statistics || LogVMOutput || LogCompilation) && UseCompiler) {
    FlagSetting fs(DisplayVMOutput, DisplayVMOutput && PrintC1Statistics);
    Runtime1::print_statistics();
    Deoptimization::print_statistics();
    SharedRuntime::print_statistics();
    nmethod::print_statistics();
  }
#endif /* COMPILER1 */

#ifdef COMPILER2
  if ((PrintOptoStatistics || LogVMOutput || LogCompilation) && UseCompiler) {
    FlagSetting fs(DisplayVMOutput, DisplayVMOutput && PrintOptoStatistics);
    Compile::print_statistics();
#ifndef COMPILER1
    Deoptimization::print_statistics();
    nmethod::print_statistics();
    SharedRuntime::print_statistics();
#endif //COMPILER1
    os::print_statistics();
  }

  if (PrintLockStatistics || PrintPreciseBiasedLockingStatistics) {
    OptoRuntime::print_named_counters();
  }

  if (TimeLivenessAnalysis) {
    MethodLiveness::print_times();
  }
#ifdef ASSERT
  if (CollectIndexSetStatistics) {
    IndexSet::print_statistics();
  }
#endif // ASSERT
#endif // COMPILER2
  if (CountCompiledCalls) {
    print_method_invocation_histogram();
  }
  if (ProfileInterpreter COMPILER1_PRESENT(|| C1UpdateMethodData)) {
    print_method_profiling_data();
  }
  if (TimeCompiler) {
    COMPILER2_PRESENT(Compile::print_timers();)
  }
  if (TimeCompilationPolicy) {
    CompilationPolicy::policy()->print_time();
  }
  if (TimeOopMap) {
    GenerateOopMap::print_time();
  }
  if (ProfilerCheckIntervals) {
    PeriodicTask::print_intervals();
  }
  if (PrintSymbolTableSizeHistogram) {
    SymbolTable::print_histogram();
  }
  if (CountBytecodes || TraceBytecodes || StopInterpreterAt) {
    BytecodeCounter::print();
  }
  if (PrintBytecodePairHistogram) {
    BytecodePairHistogram::print();
  }

  if (PrintCodeCache) {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    CodeCache::print();
  }

  if (PrintMethodFlushingStatistics) {
    NMethodSweeper::print();
  }

  if (PrintCodeCache2) {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    CodeCache::print_internals();
  }

  if (PrintClassStatistics) {
    SystemDictionary::print_class_statistics();
  }
  if (PrintMethodStatistics) {
    SystemDictionary::print_method_statistics();
  }

  if (PrintVtableStats) {
    klassVtable::print_statistics();
    klassItable::print_statistics();
  }
  if (VerifyOops) {
    tty->print_cr("+VerifyOops count: %d", StubRoutines::verify_oop_count());
  }

  print_bytecode_count();
  if (PrintMallocStatistics) {
    tty->print("allocation stats: ");
    alloc_stats.print();
    tty->cr();
  }

  if (PrintSystemDictionaryAtExit) {
    SystemDictionary::print();
  }

  if (PrintBiasedLockingStatistics) {
    BiasedLocking::print_counters();
  }

#ifdef ENABLE_ZAP_DEAD_LOCALS
#ifdef COMPILER2
  if (ZapDeadCompiledLocals) {
    tty->print_cr("Compile::CompiledZap_count = %d", Compile::CompiledZap_count);
    tty->print_cr("OptoRuntime::ZapDeadCompiledLocals_count = %d", OptoRuntime::ZapDeadCompiledLocals_count);
  }
#endif // COMPILER2
#endif // ENABLE_ZAP_DEAD_LOCALS
  // Native memory tracking data
  if (PrintNMTStatistics) {
    if (MemTracker::is_on()) {
      BaselineTTYOutputer outputer(tty);
      MemTracker::print_memory_usage(outputer, K, false);
    } else {
      tty->print_cr(MemTracker::reason());
    }
  }
}

#else // PRODUCT MODE STATISTICS

void print_statistics() {

  if (CITime) {
    CompileBroker::print_times();
  }

  if (PrintCodeCache) {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    CodeCache::print();
  }

  if (PrintMethodFlushingStatistics) {
    NMethodSweeper::print();
  }

#ifdef COMPILER2
  if (PrintPreciseBiasedLockingStatistics) {
    OptoRuntime::print_named_counters();
  }
#endif
  if (PrintBiasedLockingStatistics) {
    BiasedLocking::print_counters();
  }

  // Native memory tracking data
  if (PrintNMTStatistics) {
    if (MemTracker::is_on()) {
      BaselineTTYOutputer outputer(tty);
      MemTracker::print_memory_usage(outputer, K, false);
    } else {
      tty->print_cr(MemTracker::reason());
    }
  }
}

#endif


// Helper class for registering on_exit calls through JVM_OnExit

extern "C" {
    typedef void (*__exit_proc)(void);
}

class ExitProc : public CHeapObj<mtInternal> {
 private:
  __exit_proc _proc;
  // void (*_proc)(void);
  ExitProc* _next;
 public:
  // ExitProc(void (*proc)(void)) {
  ExitProc(__exit_proc proc) {
    _proc = proc;
    _next = NULL;
  }
  void evaluate()               { _proc(); }
  ExitProc* next() const        { return _next; }
  void set_next(ExitProc* next) { _next = next; }
};


// Linked list of registered on_exit procedures

static ExitProc* exit_procs = NULL;


extern "C" {
  void register_on_exit_function(void (*func)(void)) {
    ExitProc *entry = new ExitProc(func);
    // Classic vm does not throw an exception in case the allocation failed,
    if (entry != NULL) {
      entry->set_next(exit_procs);
      exit_procs = entry;
    }
  }
}

// Note: before_exit() can be executed only once, if more than one threads
//       are trying to shutdown the VM at the same time, only one thread
//       can run before_exit() and all other threads must wait.
void before_exit(JavaThread * thread) {
  #define BEFORE_EXIT_NOT_RUN 0
  #define BEFORE_EXIT_RUNNING 1
  #define BEFORE_EXIT_DONE    2
  static jint volatile _before_exit_status = BEFORE_EXIT_NOT_RUN;

  // Note: don't use a Mutex to guard the entire before_exit(), as
  // JVMTI post_thread_end_event and post_vm_death_event will run native code.
  // A CAS or OSMutex would work just fine but then we need to manipulate
  // thread state for Safepoint. Here we use Monitor wait() and notify_all()
  // for synchronization.
  { MutexLocker ml(BeforeExit_lock);
    switch (_before_exit_status) {
    case BEFORE_EXIT_NOT_RUN:
      _before_exit_status = BEFORE_EXIT_RUNNING;
      break;
    case BEFORE_EXIT_RUNNING:
      while (_before_exit_status == BEFORE_EXIT_RUNNING) {
        BeforeExit_lock->wait();
      }
      assert(_before_exit_status == BEFORE_EXIT_DONE, "invalid state");
      return;
    case BEFORE_EXIT_DONE:
      return;
    }
  }

  // The only difference between this and Win32's _onexit procs is that
  // this version is invoked before any threads get killed.
  ExitProc* current = exit_procs;
  while (current != NULL) {
    ExitProc* next = current->next();
    current->evaluate();
    delete current;
    current = next;
  }

  // Hang forever on exit if we're reporting an error.
  if (ShowMessageBoxOnError && is_error_reported()) {
    os::infinite_sleep();
  }

  // Terminate watcher thread - must before disenrolling any periodic task
  if (PeriodicTask::num_tasks() > 0)
    WatcherThread::stop();

  // Print statistics gathered (profiling ...)
  if (Arguments::has_profile()) {
    FlatProfiler::disengage();
    FlatProfiler::print(10);
  }

  // shut down the StatSampler task
  StatSampler::disengage();
  StatSampler::destroy();

  // We do not need to explicitly stop concurrent GC threads because the
  // JVM will be taken down at a safepoint when such threads are inactive --
  // except for some concurrent G1 threads, see (comment in)
  // Threads::destroy_vm().

  // Print GC/heap related information.
  if (PrintGCDetails) {
    Universe::print();
    AdaptiveSizePolicyOutput(0);
    if (Verbose) {
      ClassLoaderDataGraph::dump_on(gclog_or_tty);
    }
  }

  if (PrintBytecodeHistogram) {
    BytecodeHistogram::print();
  }

  if (JvmtiExport::should_post_thread_life()) {
    JvmtiExport::post_thread_end(thread);
  }


  EventThreadEnd event;
  if (event.should_commit()) {
      event.set_javalangthread(java_lang_Thread::thread_id(thread->threadObj()));
      event.commit();
  }

  // Always call even when there are not JVMTI environments yet, since environments
  // may be attached late and JVMTI must track phases of VM execution
  JvmtiExport::post_vm_death();
  Threads::shutdown_vm_agents();

  // Terminate the signal thread
  // Note: we don't wait until it actually dies.
  os::terminate_signal_thread();

  print_statistics();
  Universe::heap()->print_tracing_info();

  { MutexLocker ml(BeforeExit_lock);
    _before_exit_status = BEFORE_EXIT_DONE;
    BeforeExit_lock->notify_all();
  }

  // Shutdown NMT before exit. Otherwise,
  // it will run into trouble when system destroys static variables.
  MemTracker::shutdown(MemTracker::NMT_normal);

  if (VerifyStringTableAtExit) {
    int fail_cnt = 0;
    {
      MutexLocker ml(StringTable_lock);
      fail_cnt = StringTable::verify_and_compare_entries();
    }

    if (fail_cnt != 0) {
      tty->print_cr("ERROR: fail_cnt=%d", fail_cnt);
      guarantee(fail_cnt == 0, "unexpected StringTable verification failures");
    }
  }

  #undef BEFORE_EXIT_NOT_RUN
  #undef BEFORE_EXIT_RUNNING
  #undef BEFORE_EXIT_DONE
}

void vm_exit(int code) {
  Thread* thread = ThreadLocalStorage::is_initialized() ?
    ThreadLocalStorage::get_thread_slow() : NULL;
  if (thread == NULL) {
    // we have serious problems -- just exit
    vm_direct_exit(code);
  }

  if (VMThread::vm_thread() != NULL) {
    // Fire off a VM_Exit operation to bring VM to a safepoint and exit
    VM_Exit op(code);
    if (thread->is_Java_thread())
      ((JavaThread*)thread)->set_thread_state(_thread_in_vm);
    VMThread::execute(&op);
    // should never reach here; but in case something wrong with VM Thread.
    vm_direct_exit(code);
  } else {
    // VM thread is gone, just exit
    vm_direct_exit(code);
  }
  ShouldNotReachHere();
}

void notify_vm_shutdown() {
  // For now, just a dtrace probe.
#ifndef USDT2
  HS_DTRACE_PROBE(hotspot, vm__shutdown);
  HS_DTRACE_WORKAROUND_TAIL_CALL_BUG();
#else /* USDT2 */
  HOTSPOT_VM_SHUTDOWN();
#endif /* USDT2 */
}

void vm_direct_exit(int code) {
  notify_vm_shutdown();
  os::wait_for_keypress_at_exit();
  ::exit(code);
}

void vm_perform_shutdown_actions() {
  // Warning: do not call 'exit_globals()' here. All threads are still running.
  // Calling 'exit_globals()' will disable thread-local-storage and cause all
  // kinds of assertions to trigger in debug mode.
  if (is_init_completed()) {
    Thread* thread = ThreadLocalStorage::is_initialized() ?
                     ThreadLocalStorage::get_thread_slow() : NULL;
    if (thread != NULL && thread->is_Java_thread()) {
      // We are leaving the VM, set state to native (in case any OS exit
      // handlers call back to the VM)
      JavaThread* jt = (JavaThread*)thread;
      // Must always be walkable or have no last_Java_frame when in
      // thread_in_native
      jt->frame_anchor()->make_walkable(jt);
      jt->set_thread_state(_thread_in_native);
    }
  }
  notify_vm_shutdown();
}

void vm_shutdown()
{
  vm_perform_shutdown_actions();
  os::wait_for_keypress_at_exit();
  os::shutdown();
}

void vm_abort(bool dump_core) {
  vm_perform_shutdown_actions();
  os::wait_for_keypress_at_exit();
  os::abort(dump_core);
  ShouldNotReachHere();
}

void vm_notify_during_shutdown(const char* error, const char* message) {
  if (error != NULL) {
    tty->print_cr("Error occurred during initialization of VM");
    tty->print("%s", error);
    if (message != NULL) {
      tty->print_cr(": %s", message);
    }
    else {
      tty->cr();
    }
  }
  if (ShowMessageBoxOnError && WizardMode) {
    fatal("Error occurred during initialization of VM");
  }
}

void vm_exit_during_initialization(Handle exception) {
  tty->print_cr("Error occurred during initialization of VM");
  // If there are exceptions on this thread it must be cleared
  // first and here. Any future calls to EXCEPTION_MARK requires
  // that no pending exceptions exist.
  Thread *THREAD = Thread::current();
  if (HAS_PENDING_EXCEPTION) {
    CLEAR_PENDING_EXCEPTION;
  }
  java_lang_Throwable::print(exception, tty);
  tty->cr();
  java_lang_Throwable::print_stack_trace(exception(), tty);
  tty->cr();
  vm_notify_during_shutdown(NULL, NULL);

  // Failure during initialization, we don't want to dump core
  vm_abort(false);
}

void vm_exit_during_initialization(Symbol* ex, const char* message) {
  ResourceMark rm;
  vm_notify_during_shutdown(ex->as_C_string(), message);

  // Failure during initialization, we don't want to dump core
  vm_abort(false);
}

void vm_exit_during_initialization(const char* error, const char* message) {
  vm_notify_during_shutdown(error, message);

  // Failure during initialization, we don't want to dump core
  vm_abort(false);
}

void vm_shutdown_during_initialization(const char* error, const char* message) {
  vm_notify_during_shutdown(error, message);
  vm_shutdown();
}

JDK_Version JDK_Version::_current;
const char* JDK_Version::_runtime_name;
const char* JDK_Version::_runtime_version;

void JDK_Version::initialize() {
  jdk_version_info info;
  assert(!_current.is_valid(), "Don't initialize twice");

  void *lib_handle = os::native_java_library();
  jdk_version_info_fn_t func = CAST_TO_FN_PTR(jdk_version_info_fn_t,
     os::dll_lookup(lib_handle, "JDK_GetVersionInfo0"));

  if (func == NULL) {
    // JDK older than 1.6
    _current._partially_initialized = true;
  } else {
    (*func)(&info, sizeof(info));

    int major = JDK_VERSION_MAJOR(info.jdk_version);
    int minor = JDK_VERSION_MINOR(info.jdk_version);
    int micro = JDK_VERSION_MICRO(info.jdk_version);
    int build = JDK_VERSION_BUILD(info.jdk_version);
    if (major == 1 && minor > 4) {
      // We represent "1.5.0" as "5.0", but 1.4.2 as itself.
      major = minor;
      minor = micro;
      micro = 0;
    }
    _current = JDK_Version(major, minor, micro, info.update_version,
                           info.special_update_version, build,
                           info.thread_park_blocker == 1,
                           info.post_vm_init_hook_enabled == 1,
                           info.pending_list_uses_discovered_field == 1);
  }
}

void JDK_Version::fully_initialize(
    uint8_t major, uint8_t minor, uint8_t micro, uint8_t update) {
  // This is only called when current is less than 1.6 and we've gotten
  // far enough in the initialization to determine the exact version.
  assert(major < 6, "not needed for JDK version >= 6");
  assert(is_partially_initialized(), "must not initialize");
  if (major < 5) {
    // JDK verison sequence: 1.2.x, 1.3.x, 1.4.x, 5.0.x, 6.0.x, etc.
    micro = minor;
    minor = major;
    major = 1;
  }
  _current = JDK_Version(major, minor, micro, update);
}

void JDK_Version_init() {
  JDK_Version::initialize();
}

static int64_t encode_jdk_version(const JDK_Version& v) {
  return
    ((int64_t)v.major_version()          << (BitsPerByte * 5)) |
    ((int64_t)v.minor_version()          << (BitsPerByte * 4)) |
    ((int64_t)v.micro_version()          << (BitsPerByte * 3)) |
    ((int64_t)v.update_version()         << (BitsPerByte * 2)) |
    ((int64_t)v.special_update_version() << (BitsPerByte * 1)) |
    ((int64_t)v.build_number()           << (BitsPerByte * 0));
}

int JDK_Version::compare(const JDK_Version& other) const {
  assert(is_valid() && other.is_valid(), "Invalid version (uninitialized?)");
  if (!is_partially_initialized() && other.is_partially_initialized()) {
    return -(other.compare(*this)); // flip the comparators
  }
  assert(!other.is_partially_initialized(), "Not initialized yet");
  if (is_partially_initialized()) {
    assert(other.major_version() >= 6,
           "Invalid JDK version comparison during initialization");
    return -1;
  } else {
    uint64_t e = encode_jdk_version(*this);
    uint64_t o = encode_jdk_version(other);
    return (e > o) ? 1 : ((e == o) ? 0 : -1);
  }
}

void JDK_Version::to_string(char* buffer, size_t buflen) const {
  size_t index = 0;
  if (!is_valid()) {
    jio_snprintf(buffer, buflen, "%s", "(uninitialized)");
  } else if (is_partially_initialized()) {
    jio_snprintf(buffer, buflen, "%s", "(uninitialized) pre-1.6.0");
  } else {
    index += jio_snprintf(
        &buffer[index], buflen - index, "%d.%d", _major, _minor);
    if (_micro > 0) {
      index += jio_snprintf(&buffer[index], buflen - index, ".%d", _micro);
    }
    if (_update > 0) {
      index += jio_snprintf(&buffer[index], buflen - index, "_%02d", _update);
    }
    if (_special > 0) {
      index += jio_snprintf(&buffer[index], buflen - index, "%c", _special);
    }
    if (_build > 0) {
      index += jio_snprintf(&buffer[index], buflen - index, "-b%02d", _build);
    }
  }
}
