/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "classfile/systemDictionary.hpp"
#include "compiler/abstractCompiler.hpp"
#include "compiler/compileTask.hpp"
#include "compiler/compilerThread.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "jvmci/jvmci.hpp"
#include "jvmci/jvmciJavaClasses.hpp"
#include "jvmci/jvmciEnv.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "jvmci/metadataHandles.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomic.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/os.hpp"
#include "utilities/events.hpp"

JVMCIRuntime* JVMCI::_compiler_runtimes = nullptr;
JVMCIRuntime* JVMCI::_java_runtime = nullptr;
JVMCIRuntime* JVMCI::_shutdown_compiler_runtime = nullptr;
volatile bool JVMCI::_is_initialized = false;
bool JVMCI::_box_caches_initialized = false;
void* JVMCI::_shared_library_handle = nullptr;
char* JVMCI::_shared_library_path = nullptr;
volatile bool JVMCI::_in_shutdown = false;
StringEventLog* JVMCI::_events = nullptr;
StringEventLog* JVMCI::_verbose_events = nullptr;
volatile intx JVMCI::_fatal_log_init_thread = -1;
volatile int JVMCI::_fatal_log_fd = -1;
const char* JVMCI::_fatal_log_filename = nullptr;

CompilerThreadCanCallJava::CompilerThreadCanCallJava(JavaThread* current, bool new_state) {
  _current = nullptr;
  if (current->is_Compiler_thread()) {
    CompilerThread* ct = CompilerThread::cast(current);
    if (ct->_can_call_java != new_state &&
        ct->_compiler != nullptr &&
        ct->_compiler->is_jvmci())
    {
      // Only enter a new context if the ability of the
      // current thread to call Java actually changes
      _reset_state = ct->_can_call_java;
      ct->_can_call_java = new_state;
      _current = ct;
    }
  }
}

CompilerThreadCanCallJava::~CompilerThreadCanCallJava() {
  if (_current != nullptr) {
    _current->_can_call_java = _reset_state;
  }
}

void jvmci_vmStructs_init() NOT_DEBUG_RETURN;

bool JVMCI::can_initialize_JVMCI() {
  if (UseJVMCINativeLibrary) {
    // Initializing libjvmci does not execute Java code so
    // can be done any time.
    return true;
  }
  // Initializing JVMCI requires the module system to be initialized past phase 3.
  // The JVMCI API itself isn't available until phase 2 and ServiceLoader (which
  // JVMCI initialization requires) isn't usable until after phase 3. Testing
  // whether the system loader is initialized satisfies all these invariants.
  if (SystemDictionary::java_system_loader() == nullptr) {
    return false;
  }
  assert(Universe::is_module_initialized(), "must be");
  return true;
}

bool JVMCI::get_shared_library_path(char* pathbuf, size_t pathlen, bool fail_is_fatal) {
  if (JVMCILibPath != nullptr) {
    if (!os::dll_locate_lib(pathbuf, pathlen, JVMCILibPath, JVMCI_SHARED_LIBRARY_NAME)) {
      if (!fail_is_fatal) {
        return false;
      }
      fatal("Unable to create path to JVMCI shared library based on value of JVMCILibPath (%s)", JVMCILibPath);
    }
  } else {
    if (!os::dll_locate_lib(pathbuf, pathlen, Arguments::get_dll_dir(), JVMCI_SHARED_LIBRARY_NAME)) {
      if (!fail_is_fatal) {
        return false;
      }
      fatal("Unable to create path to JVMCI shared library");
    }
  }
  return true;
}

bool JVMCI::shared_library_exists() {
  if (_shared_library_handle != nullptr) {
    return true;
  }
  char path[JVM_MAXPATHLEN];
  return get_shared_library_path(path, sizeof(path), false);
}

void* JVMCI::get_shared_library(char*& path, bool load) {
  void* sl_handle = _shared_library_handle;
  if (sl_handle != nullptr || !load) {
    path = _shared_library_path;
    return sl_handle;
  }
  MutexLocker locker(JVMCI_lock);
  path = nullptr;
  if (_shared_library_handle == nullptr) {
    char path[JVM_MAXPATHLEN];
    char ebuf[1024];
    get_shared_library_path(path, sizeof(path), true);

    void* handle = os::dll_load(path, ebuf, sizeof ebuf);
    if (handle == nullptr) {
      fatal("Unable to load JVMCI shared library from %s: %s", path, ebuf);
    }
    _shared_library_handle = handle;
    _shared_library_path = os::strdup(path);

    JVMCI_event_1("loaded JVMCI shared library from %s", path);
  }
  path = _shared_library_path;
  return _shared_library_handle;
}

void JVMCI::initialize_compiler(TRAPS) {
  if (JVMCILibDumpJNIConfig) {
    JNIJVMCI::initialize_ids(nullptr);
    ShouldNotReachHere();
  }
  JVMCIRuntime* runtime;
  if (UseJVMCINativeLibrary) {
      runtime = JVMCI::compiler_runtime(THREAD);
  } else {
      runtime = JVMCI::java_runtime();
  }
  runtime->call_getCompiler(CHECK);
}

void JVMCI::initialize_globals() {
  jvmci_vmStructs_init();
  if (LogEvents) {
    if (JVMCIEventLogLevel > 0) {
      _events = new StringEventLog("JVMCI Events", "jvmci");
      if (JVMCIEventLogLevel > 1) {
        int count = LogEventsBufferEntries;
        for (int i = 1; i < JVMCIEventLogLevel && i < max_EventLog_level; i++) {
          // Expand event buffer by 10x for each level above 1
          count = count * 10;
        }
        _verbose_events = new StringEventLog("Verbose JVMCI Events", "verbose-jvmci", count);
      }
    }
  }
  _java_runtime = new JVMCIRuntime(nullptr, -1, false);
  if (using_singleton_shared_library_runtime()) {
    JVMCI::_compiler_runtimes = new JVMCIRuntime(nullptr, 0, true);
  }
}

void JVMCI::ensure_box_caches_initialized(TRAPS) {
  if (_box_caches_initialized) {
    return;
  }

  // While multiple threads may reach here, that's fine
  // since class initialization is synchronized.
  Symbol* box_classes[] = {
    java_lang_Boolean::symbol(),
    java_lang_Byte_ByteCache::symbol(),
    java_lang_Short_ShortCache::symbol(),
    java_lang_Character_CharacterCache::symbol(),
    java_lang_Integer_IntegerCache::symbol(),
    java_lang_Long_LongCache::symbol()
  };

  // Class resolution and initialization below
  // requires calling into Java
  CompilerThreadCanCallJava ccj(THREAD, true);

  for (unsigned i = 0; i < sizeof(box_classes) / sizeof(Symbol*); i++) {
    Klass* k = SystemDictionary::resolve_or_fail(box_classes[i], true, CHECK);
    InstanceKlass* ik = InstanceKlass::cast(k);
    if (ik->is_not_initialized()) {
      ik->initialize(CHECK);
    }
  }
  _box_caches_initialized = true;
}

JVMCIRuntime* JVMCI::compiler_runtime(JavaThread* thread, bool create) {
  assert(thread->is_Java_thread(), "must be") ;
  assert(UseJVMCINativeLibrary, "must be");
  JVMCIRuntime* runtime = thread->libjvmci_runtime();
  if (runtime == nullptr && create) {
    runtime = JVMCIRuntime::for_thread(thread);
  }
  return runtime;
}

JavaThread* JVMCI::compilation_tick(JavaThread* thread) {
  if (thread->is_Compiler_thread()) {
    CompileTask *task = CompilerThread::cast(thread)->task();
    if (task != nullptr) {
      JVMCICompileState *state = task->blocking_jvmci_compile_state();
      if (state != nullptr) {
        state->inc_compilation_ticks();
      }
    }
  }
  return thread;
}

void JVMCI::metadata_do(void f(Metadata*)) {
  if (_java_runtime != nullptr) {
    _java_runtime->_metadata_handles->metadata_do(f);
  }
  for (JVMCIRuntime* runtime = _compiler_runtimes; runtime != nullptr; runtime = runtime->_next) {
    runtime->_metadata_handles->metadata_do(f);
  }
  if (_shutdown_compiler_runtime != nullptr) {
    _shutdown_compiler_runtime->_metadata_handles->metadata_do(f);
  }
}

void JVMCI::do_unloading(bool unloading_occurred) {
  if (unloading_occurred) {
    if (_java_runtime != nullptr) {
      _java_runtime->_metadata_handles->do_unloading();
    }
    for (JVMCIRuntime* runtime = _compiler_runtimes; runtime != nullptr; runtime = runtime->_next) {
      runtime->_metadata_handles->do_unloading();
    }
    if (_shutdown_compiler_runtime != nullptr) {
      _shutdown_compiler_runtime->_metadata_handles->do_unloading();
    }
  }
}

bool JVMCI::is_compiler_initialized() {
  return _is_initialized;
}

void JVMCI::vlog(int level, const char* format, va_list ap) {
  if (LogEvents && JVMCIEventLogLevel >= level) {
    StringEventLog* events = level == 1 ? _events : _verbose_events;
    guarantee(events != nullptr, "JVMCI event log not yet initialized");
    Thread* thread = Thread::current_or_null_safe();
    if (thread != nullptr) {
      events->logv(thread, format, ap);
    }
  }
}

void JVMCI::vtrace(int level, const char* format, va_list ap) {
  if (JVMCITraceLevel >= level) {
    Thread* thread = Thread::current_or_null_safe();
    if (thread != nullptr && thread->is_Java_thread()) {
      ResourceMark rm(thread);
      JavaThreadState state = JavaThread::cast(thread)->thread_state();
      if (state == _thread_in_vm || state == _thread_in_Java || state == _thread_new) {
        tty->print("JVMCITrace-%d[" PTR_FORMAT " \"%s\"]:%*c", level, p2i(thread), thread->name(), level, ' ');
      } else {
        // According to check_access_thread_state, it's unsafe to
        // resolve the j.l.Thread object unless the thread is in
        // one of the states above.
        tty->print("JVMCITrace-%d[" PTR_FORMAT " <%s>]:%*c", level, p2i(thread), thread->type_name(), level, ' ');
      }
    } else {
      tty->print("JVMCITrace-%d[?]:%*c", level, level, ' ');
    }
    tty->vprint_cr(format, ap);
  }
}

#define LOG_TRACE(level) { va_list ap; \
  va_start(ap, format); vlog(level, format, ap); va_end(ap); \
  va_start(ap, format); vtrace(level, format, ap); va_end(ap); \
}

void JVMCI::event(int level, const char* format, ...) LOG_TRACE(level)
void JVMCI::event1(const char* format, ...) LOG_TRACE(1)
void JVMCI::event2(const char* format, ...) LOG_TRACE(2)
void JVMCI::event3(const char* format, ...) LOG_TRACE(3)
void JVMCI::event4(const char* format, ...) LOG_TRACE(4)

#undef LOG_TRACE

void JVMCI::shutdown(JavaThread* thread) {
  ResourceMark rm;
  {
    MutexLocker locker(JVMCI_lock);
    _in_shutdown = true;
    JVMCI_event_1("shutting down JVMCI");
  }
  JVMCIRuntime* java_runtime = _java_runtime;
  if (java_runtime != nullptr) {
    java_runtime->shutdown();
  }
  JVMCIRuntime* runtime = thread->libjvmci_runtime();
  if (runtime != nullptr) {
    runtime->detach_thread(thread, "JVMCI shutdown");
  }
  {
    // Attach to JVMCI initialized runtimes that are not already shutting down
    // and shut them down. This ensures HotSpotJVMCIRuntime.shutdown() is called
    // for each JVMCI runtime.
    MutexLocker locker(JVMCI_lock);
    for (JVMCIRuntime* rt = JVMCI::_compiler_runtimes; rt != nullptr; rt = rt->_next) {
      if (rt->is_HotSpotJVMCIRuntime_initialized() && rt->_num_attached_threads != JVMCIRuntime::cannot_be_attached) {
        rt->_num_attached_threads++;
        {
          MutexUnlocker unlocker(JVMCI_lock);
          rt->attach_thread(thread);
          rt->shutdown();
          rt->detach_thread(thread, "JVMCI shutdown");
        }
      }
    }
  }
}

bool JVMCI::in_shutdown() {
  return _in_shutdown;
}

void JVMCI::fatal_log(const char* buf, size_t count) {
  intx current_thread_id = os::current_thread_id();
  intx invalid_id = -1;
  int log_fd;
  if (_fatal_log_init_thread == invalid_id && Atomic::cmpxchg(&_fatal_log_init_thread, invalid_id, current_thread_id) == invalid_id) {
    if (ErrorFileToStdout) {
      log_fd = 1;
    } else if (ErrorFileToStderr) {
      log_fd = 2;
    } else {
      static char name_buffer[O_BUFLEN];
      log_fd = VMError::prepare_log_file(JVMCINativeLibraryErrorFile, LIBJVMCI_ERR_FILE, true, name_buffer, sizeof(name_buffer));
      if (log_fd != -1) {
        _fatal_log_filename = name_buffer;
      } else {
        int e = errno;
        tty->print("Can't open JVMCI shared library error report file. Error: ");
        tty->print_raw_cr(os::strerror(e));
        tty->print_cr("JVMCI shared library error report will be written to console.");

        // See notes in VMError::report_and_die about hard coding tty to 1
        log_fd = 1;
      }
    }
    _fatal_log_fd = log_fd;
  } else {
    // Another thread won the race to initialize the stream. Give it time
    // to complete initialization. VM locks cannot be used as the current
    // thread might not be attached to the VM (e.g. a native thread started
    // within libjvmci).
    while (_fatal_log_fd == -1) {
      os::naked_short_sleep(50);
    }
  }
  fdStream log(_fatal_log_fd);
  log.write(buf, count);
  log.flush();
}
