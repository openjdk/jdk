/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, 2024 SAP SE. All rights reserved.
 * Copyright (c) 2023, 2025, Red Hat, Inc. and/or its affiliates.
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

#include "cds/aotMetaspace.hpp"
#include "code/codeCache.hpp"
#include "compiler/compilationFailureInfo.hpp"
#include "compiler/compilationMemoryStatistic.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/disassembler.hpp"
#include "gc/shared/gcConfig.hpp"
#include "gc/shared/gcLogPrecious.hpp"
#include "jvm.h"
#include "logging/logConfiguration.hpp"
#include "memory/allocation.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/resourceArea.inline.hpp"
#include "memory/universe.hpp"
#include "nmt/memTracker.hpp"
#include "oops/compressedOops.hpp"
#include "prims/whitebox.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/flags/jvmFlag.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/osThread.hpp"
#include "runtime/safefetch.hpp"
#include "runtime/safepointMechanism.hpp"
#include "runtime/stackFrameStream.inline.hpp"
#include "runtime/stackOverflow.hpp"
#include "runtime/threads.hpp"
#include "runtime/threadSMR.hpp"
#include "runtime/trimNativeHeap.hpp"
#include "runtime/vm_version.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vmThread.hpp"
#include "sanitizers/address.hpp"
#include "sanitizers/ub.hpp"
#include "utilities/debug.hpp"
#include "utilities/decoder.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/events.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/nativeStackPrinter.hpp"
#include "utilities/ostream.hpp"
#include "utilities/vmError.hpp"
#if INCLUDE_JFR
#include "jfr/jfr.hpp"
#endif
#if INCLUDE_JVMCI
#include "jvmci/jvmci.hpp"
#endif

#ifndef PRODUCT
#include <signal.h>
#endif // PRODUCT

bool              VMError::coredump_status;
char              VMError::coredump_message[O_BUFLEN];
int               VMError::_current_step;
const char*       VMError::_current_step_info;
volatile jlong    VMError::_reporting_start_time = -1;
volatile bool     VMError::_reporting_did_timeout = false;
volatile jlong    VMError::_step_start_time = -1;
volatile bool     VMError::_step_did_timeout = false;
volatile bool     VMError::_step_did_succeed = false;
volatile intptr_t VMError::_first_error_tid = -1;
int               VMError::_id;
const char*       VMError::_message;
char              VMError::_detail_msg[1024];
Thread*           VMError::_thread;
address           VMError::_pc;
const void*       VMError::_siginfo;
const void*       VMError::_context;
bool              VMError::_print_stack_from_frame_used = false;
const char*       VMError::_filename;
int               VMError::_lineno;
size_t            VMError::_size;
const size_t      VMError::_reattempt_required_stack_headroom = 64 * K;
const intptr_t    VMError::segfault_address = pd_segfault_address;
Thread* volatile VMError::_handshake_timed_out_thread = nullptr;
Thread* volatile VMError::_safepoint_timed_out_thread = nullptr;

// List of environment variables that should be reported in error log file.
static const char* env_list[] = {
  // All platforms
  "JAVA_HOME", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH",
  "JDK_AOT_VM_OPTIONS",
  "JAVA_OPTS", "PATH", "USERNAME",

  "XDG_CACHE_HOME", "XDG_CONFIG_HOME", "FC_LANG", "FONTCONFIG_USE_MMAP",

  // Env variables that are defined on Linux/BSD
  "LD_LIBRARY_PATH", "LD_PRELOAD", "SHELL", "DISPLAY", "WAYLAND_DISPLAY",
  "HOSTTYPE", "OSTYPE", "ARCH", "MACHTYPE",
  "LANG", "LC_ALL", "LC_CTYPE", "LC_NUMERIC", "LC_TIME",
  "TERM", "TMPDIR", "TZ",

  // defined on AIX
  "LIBPATH", "LDR_PRELOAD", "LDR_PRELOAD64",

  // defined on Linux/AIX/BSD
  "_JAVA_SR_SIGNUM",

  // defined on Darwin
  "DYLD_LIBRARY_PATH", "DYLD_FALLBACK_LIBRARY_PATH",
  "DYLD_FRAMEWORK_PATH", "DYLD_FALLBACK_FRAMEWORK_PATH",
  "DYLD_INSERT_LIBRARIES",

  // defined on Windows
  "OS", "PROCESSOR_IDENTIFIER", "_ALT_JAVA_HOME_DIR", "TMP", "TEMP",

  nullptr                       // End marker.
};

// A simple parser for lists of commands such as -XX:OnError and -XX:OnOutOfMemoryError
// Command list (ptr) is expected to be a sequence of commands delineated by semicolons and/or newlines.
// Usage:
//  ptr = OnError;
//  while ((cmd = next_OnError_command(buffer, sizeof(buffer), &ptr) != nullptr)
//     ... ...
static char* next_OnError_command(char* buf, int buflen, const char** ptr) {
  if (ptr == nullptr || *ptr == nullptr) return nullptr;

  const char* cmd = *ptr;

  // skip leading blanks, ';' or newlines
  while (*cmd == ' ' || *cmd == ';' || *cmd == '\n') cmd++;

  if (*cmd == '\0') return nullptr;

  const char * cmdend = cmd;
  while (*cmdend != '\0' && *cmdend != ';' && *cmdend != '\n') cmdend++;

  Arguments::copy_expand_pid(cmd, cmdend - cmd, buf, buflen);

  *ptr = (*cmdend == '\0' ? cmdend : cmdend + 1);
  return buf;
}

static void print_bug_submit_message(outputStream *out, Thread *thread) {
  if (out == nullptr) return;
  const char *url = Arguments::java_vendor_url_bug();
  if (url == nullptr || *url == '\0')
    url = JDK_Version::runtime_vendor_vm_bug_url();
  if (url != nullptr && *url != '\0') {
    out->print_raw_cr("# If you would like to submit a bug report, please visit:");
    out->print_raw   ("#   ");
    out->print_raw_cr(url);
  }
  // If the crash is in native code, encourage user to submit a bug to the
  // provider of that code.
  if (thread && thread->is_Java_thread() &&
      !thread->is_hidden_from_external_view()) {
    if (JavaThread::cast(thread)->thread_state() == _thread_in_native) {
      out->print_cr("# The crash happened outside the Java Virtual Machine in native code.\n# See problematic frame for where to report the bug.");
    }
  }
  out->print_raw_cr("#");
}

static bool stack_has_headroom(size_t headroom) {
  size_t stack_size = 0;
  address stack_base = nullptr;
  os::current_stack_base_and_size(&stack_base, &stack_size);

  const size_t guard_size = StackOverflow::stack_guard_zone_size();
  const size_t unguarded_stack_size = stack_size - guard_size;

  if (unguarded_stack_size < headroom) {
    return false;
  }

  const address unguarded_stack_end = stack_base - unguarded_stack_size;
  const address stack_pointer       = os::current_stack_pointer();

  return stack_pointer >= unguarded_stack_end + headroom;
}

#ifdef ASSERT
PRAGMA_DIAG_PUSH
PRAGMA_INFINITE_RECURSION_IGNORED
void VMError::reattempt_test_hit_stack_limit(outputStream* st) {
  if (stack_has_headroom(_reattempt_required_stack_headroom)) {
    // Use all but (_reattempt_required_stack_headroom - K) unguarded stack space.
    size_t stack_size = 0;
    address stack_base = nullptr;
    os::current_stack_base_and_size(&stack_base, &stack_size);

    const size_t guard_size     = StackOverflow::stack_guard_zone_size();
    const address stack_pointer = os::current_stack_pointer();

    const size_t unguarded_stack_size = stack_size - guard_size;
    const address unguarded_stack_end = stack_base - unguarded_stack_size;
    const size_t available_headroom   = stack_pointer - unguarded_stack_end;
    const size_t allocation_size      = available_headroom - _reattempt_required_stack_headroom + K;

    st->print_cr("Current Stack Pointer: " PTR_FORMAT " alloca %zu"
                 " of %zu bytes available unguarded stack space",
                 p2i(stack_pointer), allocation_size, available_headroom);

    // Allocate byte blob on the stack. Make pointer volatile to avoid having
    // the compiler removing later reads.
    volatile char* stack_buffer = static_cast<char*>(alloca(allocation_size));
    // Initialize the last byte.
    stack_buffer[allocation_size - 1] = '\0';
    // Recursive call should hit the stack limit.
    reattempt_test_hit_stack_limit(st);
    // Perform a volatile read of the last byte to avoid having the complier
    // remove the allocation.
    static_cast<void>(stack_buffer[allocation_size - 1] == '\0');
  }
  controlled_crash(14);
}
PRAGMA_DIAG_POP
#endif // ASSERT

bool VMError::can_reattempt_step(const char* &stop_reason) {
  if (!stack_has_headroom(_reattempt_required_stack_headroom)) {
    stop_reason = "Stack headroom limit reached";
    return false;
  }

  if (_step_did_timeout) {
    stop_reason = "Step time limit reached";
    return false;
  }

  return true;
}

void VMError::record_coredump_status(const char* message, bool status) {
  coredump_status = status;
  strncpy(coredump_message, message, sizeof(coredump_message));
  coredump_message[sizeof(coredump_message)-1] = 0;
}

// Return a string to describe the error
char* VMError::error_string(char* buf, int buflen) {
  char signame_buf[64];
  const char *signame = os::exception_name(_id, signame_buf, sizeof(signame_buf));

  if (signame) {
    jio_snprintf(buf, buflen,
                 "%s (0x%x) at pc=" PTR_FORMAT ", pid=%d, tid=%zu",
                 signame, _id, p2i(_pc),
                 os::current_process_id(), os::current_thread_id());
  } else if (_filename != nullptr && _lineno > 0) {
    // skip directory names
    int n = jio_snprintf(buf, buflen,
                         "Internal Error at %s:%d, pid=%d, tid=%zu",
                         get_filename_only(), _lineno,
                         os::current_process_id(), os::current_thread_id());
    if (n >= 0 && n < buflen && _message) {
      if (strlen(_detail_msg) > 0) {
        jio_snprintf(buf + n, buflen - n, "%s%s: %s",
        os::line_separator(), _message, _detail_msg);
      } else {
        jio_snprintf(buf + n, buflen - n, "%sError: %s",
                     os::line_separator(), _message);
      }
    }
  } else {
    jio_snprintf(buf, buflen,
                 "Internal Error (0x%x), pid=%d, tid=%zu",
                 _id, os::current_process_id(), os::current_thread_id());
  }

  return buf;
}

void VMError::print_stack_trace(outputStream* st, JavaThread* jt,
                                char* buf, int buflen, bool verbose) {
#ifdef ZERO
  if (jt->zero_stack()->sp() && jt->top_zero_frame()) {
    // StackFrameStream uses the frame anchor, which may not have
    // been set up.  This can be done at any time in Zero, however,
    // so if it hasn't been set up then we just set it up now and
    // clear it again when we're done.
    bool has_last_Java_frame = jt->has_last_Java_frame();
    if (!has_last_Java_frame)
      jt->set_last_Java_frame();
    st->print("Java frames:");
    st->cr();

    // Print the frames
    StackFrameStream sfs(jt, true /* update */, true /* process_frames */);
    for(int i = 0; !sfs.is_done(); sfs.next(), i++) {
      sfs.current()->zero_print_on_error(i, st, buf, buflen);
      st->cr();
    }

    // Reset the frame anchor if necessary
    if (!has_last_Java_frame)
      jt->reset_last_Java_frame();
  }
#else
  if (jt->has_last_Java_frame()) {
    st->print_cr("Java frames: (J=compiled Java code, j=interpreted, Vv=VM code)");
    for (StackFrameStream sfs(jt, true /* update */, true /* process_frames */); !sfs.is_done(); sfs.next()) {
      sfs.current()->print_on_error(st, buf, buflen, verbose);
      st->cr();
    }
  }
#endif // ZERO
}

const char* VMError::get_filename_only() {
  char separator = os::file_separator()[0];
  const char* p = strrchr(_filename, separator);
  return p ? p + 1 : _filename;
}

/**
 * Adds `value` to `list` iff it's not already present and there is sufficient
 * capacity (i.e. length(list) < `list_capacity`). The length of the list
 * is the index of the first nullptr entry or `list_capacity` if there are
 * no nullptr entries.
 *
 * @ return true if the value was added, false otherwise
 */
static bool add_if_absent(address value, address* list, int list_capacity) {
  for (int i = 0; i < list_capacity; i++) {
    if (list[i] == value) {
      return false;
    }
    if (list[i] == nullptr) {
      list[i] = value;
      if (i + 1 < list_capacity) {
        list[i + 1] = nullptr;
      }
      return true;
    }
  }
  return false;
}

/**
 * Prints the VM generated code unit, if any, containing `pc` if it has not already
 * been printed. If the code unit is an InterpreterCodelet or StubCodeDesc, it is
 * only printed if `is_crash_pc` is true.
 *
 * @param printed array of code units that have already been printed (delimited by nullptr entry)
 * @param printed_capacity the capacity of `printed`
 * @return true if the code unit was printed, false otherwise
 */
static bool print_code(outputStream* st, Thread* thread, address pc, bool is_crash_pc,
                       address* printed, int printed_capacity) {
  if (Interpreter::contains(pc)) {
    if (is_crash_pc) {
      // The interpreter CodeBlob is very large so try to print the codelet instead.
      InterpreterCodelet* codelet = Interpreter::codelet_containing(pc);
      if (codelet != nullptr) {
        if (add_if_absent((address) codelet, printed, printed_capacity)) {
          codelet->print_on(st);
          Disassembler::decode(codelet->code_begin(), codelet->code_end(), st);
          return true;
        }
      }
    }
  } else {
    StubCodeDesc* desc = StubCodeDesc::desc_for(pc);
    if (desc != nullptr) {
      if (is_crash_pc) {
        if (add_if_absent((address) desc, printed, printed_capacity)) {
          desc->print_on(st);
          Disassembler::decode(desc->begin(), desc->end(), st);
          return true;
        }
      }
    } else if (thread != nullptr) {
      CodeBlob* cb = CodeCache::find_blob(pc);
      if (cb != nullptr && add_if_absent((address) cb, printed, printed_capacity)) {
        // Disassembling nmethod will incur resource memory allocation,
        // only do so when thread is valid.
        ResourceMark rm(thread);
        Disassembler::decode(cb, st);
        st->cr();
        return true;
      }
    }
  }
  return false;
}

// Like above, but only try to figure out a short name. Return nullptr if not found.
static const char* find_code_name(address pc) {
  if (Interpreter::contains(pc)) {
    InterpreterCodelet* codelet = Interpreter::codelet_containing(pc);
    if (codelet != nullptr) {
      return codelet->description();
    }
  } else {
    StubCodeDesc* desc = StubCodeDesc::desc_for(pc);
    if (desc != nullptr) {
      return desc->name();
    } else {
      CodeBlob* cb = CodeCache::find_blob(pc);
      if (cb != nullptr) {
        return cb->name();
      }
    }
  }
  return nullptr;
}

static void print_oom_reasons(outputStream* st) {
  st->print_cr("# Possible reasons:");
  st->print_cr("#   The system is out of physical RAM or swap space");
#ifdef LINUX
  st->print_cr("#   This process has exceeded the maximum number of memory mappings (check below");
  st->print_cr("#     for `/proc/sys/vm/max_map_count` and `Total number of mappings`)");
#endif
  if (UseCompressedOops) {
    st->print_cr("#   This process is running with CompressedOops enabled, and the Java Heap may be blocking the growth of the native heap");
  }
  if (LogBytesPerWord == 2) {
    st->print_cr("#   In 32 bit mode, the process size limit was hit");
  }
  st->print_cr("# Possible solutions:");
  st->print_cr("#   Reduce memory load on the system");
  st->print_cr("#   Increase physical memory or swap space");
  st->print_cr("#   Check if swap backing store is full");
  if (LogBytesPerWord == 2) {
    st->print_cr("#   Use 64 bit Java on a 64 bit OS");
  }
  st->print_cr("#   Decrease Java heap size (-Xmx/-Xms)");
  st->print_cr("#   Decrease number of Java threads");
  st->print_cr("#   Decrease Java thread stack sizes (-Xss)");
  st->print_cr("#   Set larger code cache with -XX:ReservedCodeCacheSize=");
  if (UseCompressedOops) {
    switch (CompressedOops::mode()) {
      case CompressedOops::UnscaledNarrowOop:
        st->print_cr("#   JVM is running with Unscaled Compressed Oops mode in which the Java heap is");
        st->print_cr("#     placed in the first 4GB address space. The Java Heap base address is the");
        st->print_cr("#     maximum limit for the native heap growth. Please use -XX:HeapBaseMinAddress");
        st->print_cr("#     to set the Java Heap base and to place the Java Heap above 4GB virtual address.");
        break;
      case CompressedOops::ZeroBasedNarrowOop:
        st->print_cr("#   JVM is running with Zero Based Compressed Oops mode in which the Java heap is");
        st->print_cr("#     placed in the first 32GB address space. The Java Heap base address is the");
        st->print_cr("#     maximum limit for the native heap growth. Please use -XX:HeapBaseMinAddress");
        st->print_cr("#     to set the Java Heap base and to place the Java Heap above 32GB virtual address.");
        break;
      default:
        break;
    }
  }
  st->print_cr("# This output file may be truncated or incomplete.");
}

static void print_stack_location(outputStream* st, const void* context, int& continuation) {
  const int number_of_stack_slots = 8;

  int i = continuation;
  // Update continuation with next index before fetching frame
  continuation = i + 1;
  const frame fr = os::fetch_frame_from_context(context);
  while (i < number_of_stack_slots) {
    // Update continuation with next index before printing location
    continuation = i + 1;
    // decode stack contents if possible
    const intptr_t *sp = fr.sp();
    const intptr_t *slot = sp + i;
    if (!is_aligned(slot, sizeof(intptr_t))) {
      st->print_cr("Misaligned sp: " PTR_FORMAT, p2i(sp));
      break;
    } else if (os::is_readable_pointer(slot)) {
      st->print("stack at sp + %d slots: ", i);
      os::print_location(st, *(slot));
    } else {
      st->print_cr("unreadable stack slot at sp + %d", i);
    }
    ++i;
  }
}

static void report_vm_version(outputStream* st, char* buf, int buflen) {
   // VM version
   st->print_cr("#");
   JDK_Version::current().to_string(buf, buflen);
   const char* runtime_name = JDK_Version::runtime_name() != nullptr ?
                                JDK_Version::runtime_name() : "";
   const char* runtime_version = JDK_Version::runtime_version() != nullptr ?
                                   JDK_Version::runtime_version() : "";
   const char* vendor_version = JDK_Version::runtime_vendor_version() != nullptr ?
                                  JDK_Version::runtime_vendor_version() : "";
   const char* jdk_debug_level = VM_Version::printable_jdk_debug_level() != nullptr ?
                                   VM_Version::printable_jdk_debug_level() : "";

   st->print_cr("# JRE version: %s%s%s (%s) (%sbuild %s)", runtime_name,
                (*vendor_version != '\0') ? " " : "", vendor_version,
                buf, jdk_debug_level, runtime_version);

   // This is the long version with some default settings added
   st->print_cr("# Java VM: %s%s%s (%s%s, %s%s%s%s%s%s, %s, %s)",
                 VM_Version::vm_name(),
                (*vendor_version != '\0') ? " " : "", vendor_version,
                 jdk_debug_level,
                 VM_Version::vm_release(),
                 VM_Version::vm_info_string(),
                 TieredCompilation ? ", tiered" : "",
#if INCLUDE_JVMCI
                 EnableJVMCI ? ", jvmci" : "",
                 UseJVMCICompiler ? ", jvmci compiler" : "",
#else
                 "", "",
#endif
                 UseCompressedOops ? ", compressed oops" : "",
                 UseCompactObjectHeaders ? ", compact obj headers"
                                         : (UseCompressedClassPointers ? ", compressed class ptrs" : ""),
                 GCConfig::hs_err_name(),
                 VM_Version::vm_platform_string()
               );
}

// Returns true if at least one thread reported a fatal error and fatal error handling is in process.
bool VMError::is_error_reported() {
  return _first_error_tid != -1;
}

// Returns true if the current thread reported a fatal error.
bool VMError::is_error_reported_in_current_thread() {
  return _first_error_tid == os::current_thread_id();
}

// Helper, return current timestamp for timeout handling.
jlong VMError::get_current_timestamp() {
  return os::javaTimeNanos();
}
// Factor to translate the timestamp to seconds.
#define TIMESTAMP_TO_SECONDS_FACTOR (1000 * 1000 * 1000)

void VMError::record_reporting_start_time() {
  const jlong now = get_current_timestamp();
  AtomicAccess::store(&_reporting_start_time, now);
}

jlong VMError::get_reporting_start_time() {
  return AtomicAccess::load(&_reporting_start_time);
}

void VMError::record_step_start_time() {
  const jlong now = get_current_timestamp();
  AtomicAccess::store(&_step_start_time, now);
}

jlong VMError::get_step_start_time() {
  return AtomicAccess::load(&_step_start_time);
}

void VMError::clear_step_start_time() {
  return AtomicAccess::store(&_step_start_time, (jlong)0);
}

// This is the main function to report a fatal error. Only one thread can
// call this function, so we don't need to worry about MT-safety. But it's
// possible that the error handler itself may crash or die on an internal
// error, for example, when the stack/heap is badly damaged. We must be
// able to handle recursive errors that happen inside error handler.
//
// Error reporting is done in several steps. If a crash or internal error
// occurred when reporting an error, the nested signal/exception handler
// can skip steps that are already (or partially) done. Error reporting will
// continue from the next step. This allows us to retrieve and print
// information that may be unsafe to get after a fatal error. If it happens,
// you may find nested report_and_die() frames when you look at the stack
// in a debugger.
//
// In general, a hang in error handler is much worse than a crash or internal
// error, as it's harder to recover from a hang. Deadlock can happen if we
// try to grab a lock that is already owned by current thread, or if the
// owner is blocked forever (e.g. in os::infinite_sleep()). If possible, the
// error handler and all the functions it called should avoid grabbing any
// lock. An important thing to notice is that memory allocation needs a lock.
//
// We should avoid using large stack allocated buffers. Many errors happen
// when stack space is already low. Making things even worse is that there
// could be nested report_and_die() calls on stack (see above). Only one
// thread can report error, so large buffers are statically allocated in data
// segment.
void VMError::report(outputStream* st, bool _verbose) {
  // Used by reattempt step logic
  static int continuation = 0;
  const char* stop_reattempt_reason = nullptr;
# define BEGIN                                             \
  if (_current_step == 0) {                                \
    _step_did_succeed = false;                             \
    _current_step = __LINE__;                              \
    {
      // [Begin logic]

# define STEP_IF(s, cond)                                  \
    }                                                      \
    _step_did_succeed = true;                              \
  }                                                        \
  if (_current_step < __LINE__) {                          \
    _step_did_succeed = false;                             \
    _current_step = __LINE__;                              \
    _current_step_info = s;                                \
    if ((cond)) {                                          \
      record_step_start_time();                            \
      _step_did_timeout = false;
      // [Step logic]

# define STEP(s) STEP_IF(s, true)

# define REATTEMPT_STEP_IF(s, cond)                        \
    }                                                      \
    _step_did_succeed = true;                              \
  }                                                        \
  if (_current_step < __LINE__ && !_step_did_succeed) {    \
    _current_step = __LINE__;                              \
    _current_step_info = s;                                \
    const bool cond_value = (cond);                        \
    if (cond_value && !can_reattempt_step(                 \
                          stop_reattempt_reason)) {        \
      st->print_cr("[stop reattempt (%s) reason: %s]",     \
                   _current_step_info,                     \
                   stop_reattempt_reason);                 \
    } else if (cond_value) {
      // [Continue Step logic]

# define END                                               \
    }                                                      \
    _step_did_succeed = true;                              \
    clear_step_start_time();                               \
  }

  // don't allocate large buffer on stack
  static char buf[O_BUFLEN];

  // Native stack trace may get stuck. We try to handle the last pc if it
  // belongs to VM generated code.
  address lastpc = nullptr;

  BEGIN
  if (MemTracker::enabled() &&
      NmtVirtualMemory_lock != nullptr &&
      _thread != nullptr &&
      NmtVirtualMemory_lock->owned_by_self()) {
    // Manually unlock to avoid reentrancy due to mallocs in detailed mode.
    NmtVirtualMemory_lock->unlock();
  }

  STEP("printing fatal error message")
    st->print_cr("#");
    if (should_report_bug(_id)) {
      st->print_cr("# A fatal error has been detected by the Java Runtime Environment:");
    } else {
      st->print_cr("# There is insufficient memory for the Java "
                   "Runtime Environment to continue.");
    }

  // avoid the cache update for malloc/mmap errors
  if (should_report_bug(_id)) {
    os::prepare_native_symbols();
  }

#ifdef ASSERT
  // Error handler self tests
  // Meaning of codes passed through in the tests.
#define TEST_SECONDARY_CRASH 14
#define TEST_REATTEMPT_SECONDARY_CRASH 15
#define TEST_RESOURCE_MARK_CRASH 2

  // test secondary error handling. Test it twice, to test that resetting
  // error handler after a secondary crash works.
  STEP_IF("test secondary crash 1", _verbose && TestCrashInErrorHandler == TEST_SECONDARY_CRASH)
    st->print_cr("Will crash now (TestCrashInErrorHandler=%u)...",
      TestCrashInErrorHandler);
    controlled_crash(TestCrashInErrorHandler);

  STEP_IF("test secondary crash 2", _verbose && TestCrashInErrorHandler == TEST_SECONDARY_CRASH)
    st->print_cr("Will crash now (TestCrashInErrorHandler=%u)...",
      TestCrashInErrorHandler);
    controlled_crash(TestCrashInErrorHandler);

  // See corresponding test in test/runtime/ErrorHandling/ReattemptErrorTest.java
  STEP_IF("test reattempt secondary crash",
      _verbose && TestCrashInErrorHandler == TEST_REATTEMPT_SECONDARY_CRASH)
    st->print_cr("Will crash now (TestCrashInErrorHandler=%u)...",
      TestCrashInErrorHandler);
    controlled_crash(14);

  REATTEMPT_STEP_IF("test reattempt secondary crash, attempt 2",
      _verbose && TestCrashInErrorHandler == TEST_REATTEMPT_SECONDARY_CRASH)
    st->print_cr("test reattempt secondary crash. attempt 2");

  REATTEMPT_STEP_IF("test reattempt secondary crash, attempt 3",
      _verbose && TestCrashInErrorHandler == TEST_REATTEMPT_SECONDARY_CRASH)
    st->print_cr("test reattempt secondary crash. attempt 3");

  STEP_IF("test reattempt timeout",
      _verbose && TestCrashInErrorHandler == TEST_REATTEMPT_SECONDARY_CRASH)
    st->print_cr("test reattempt timeout");
    os::infinite_sleep();

  REATTEMPT_STEP_IF("test reattempt timeout, attempt 2",
      _verbose && TestCrashInErrorHandler == TEST_REATTEMPT_SECONDARY_CRASH)
    st->print_cr("test reattempt timeout, attempt 2");

  STEP_IF("test reattempt stack headroom",
      _verbose && TestCrashInErrorHandler == TEST_REATTEMPT_SECONDARY_CRASH)
    st->print_cr("test reattempt stack headroom");
    reattempt_test_hit_stack_limit(st);

  REATTEMPT_STEP_IF("test reattempt stack headroom, attempt 2",
      _verbose && TestCrashInErrorHandler == TEST_REATTEMPT_SECONDARY_CRASH)
    st->print_cr("test reattempt stack headroom, attempt 2");

  STEP_IF("test missing ResourceMark does not crash",
      _verbose && TestCrashInErrorHandler == TEST_RESOURCE_MARK_CRASH)
    stringStream message;
    message.print("This is a message with no ResourceMark");
    tty->print_cr("%s", message.as_string());

  // TestUnresponsiveErrorHandler: We want to test both step timeouts and global timeout.
  // Step to global timeout ratio is 4:1, so in order to be absolutely sure we hit the
  // global timeout, let's execute the timeout step five times.
  // See corresponding test in test/runtime/ErrorHandling/TimeoutInErrorHandlingTest.java
  STEP_IF("setup for test unresponsive error reporting step",
      _verbose && TestUnresponsiveErrorHandler)
    // We record reporting_start_time for this test here because we
    // care about the time spent executing TIMEOUT_TEST_STEP and not
    // about the time it took us to get here.
    tty->print_cr("Recording reporting_start_time for TestUnresponsiveErrorHandler.");
    record_reporting_start_time();

  #define TIMEOUT_TEST_STEP STEP_IF("test unresponsive error reporting step", \
      _verbose && TestUnresponsiveErrorHandler) \
    os::infinite_sleep();
  TIMEOUT_TEST_STEP
  TIMEOUT_TEST_STEP
  TIMEOUT_TEST_STEP
  TIMEOUT_TEST_STEP
  TIMEOUT_TEST_STEP

  STEP_IF("test safefetch in error handler", _verbose && TestSafeFetchInErrorHandler)
    // test whether it is safe to use SafeFetch32 in Crash Handler. Test twice
    // to test that resetting the signal handler works correctly.
    st->print_cr("Will test SafeFetch...");
    int* const invalid_pointer = (int*)segfault_address;
    const int x = 0x76543210;
    int i1 = SafeFetch32(invalid_pointer, x);
    int i2 = SafeFetch32(invalid_pointer, x);
    if (i1 == x && i2 == x) {
      st->print_cr("SafeFetch OK."); // Correctly deflected and returned default pattern
    } else {
      st->print_cr("??");
    }
#endif // ASSERT

  STEP("printing type of error")
    switch(static_cast<unsigned int>(_id)) {
      case OOM_MALLOC_ERROR:
      case OOM_MMAP_ERROR:
      case OOM_MPROTECT_ERROR:
        if (_size) {
          st->print("# Native memory allocation ");
          st->print((_id == (int)OOM_MALLOC_ERROR) ? "(malloc) failed to allocate " :
                    (_id == (int)OOM_MMAP_ERROR)   ? "(mmap) failed to map " :
                                                    "(mprotect) failed to protect ");
          jio_snprintf(buf, sizeof(buf), "%zu", _size);
          st->print("%s", buf);
          st->print(" bytes.");
          if (strlen(_detail_msg) > 0) {
            st->print(" Error detail: ");
            st->print("%s", _detail_msg);
          }
          st->cr();
        } else {
          if (strlen(_detail_msg) > 0) {
            st->print("# ");
            st->print_cr("%s", _detail_msg);
          }
        }
        // In error file give some solutions
        if (_verbose) {
          print_oom_reasons(st);
        } else {
          return;  // that's enough for the screen
        }
        break;
      case INTERNAL_ERROR:
      default:
        break;
    }

  STEP("printing exception/signal name")
    st->print_cr("#");
    st->print("#  ");
    // Is it an OS exception/signal?
    if (os::exception_name(_id, buf, sizeof(buf))) {
      st->print("%s", buf);
      st->print(" (0x%x)", _id);                // signal number
      st->print(" at pc=" PTR_FORMAT, p2i(_pc));
      if (_siginfo != nullptr && os::signal_sent_by_kill(_siginfo)) {
        if (get_handshake_timed_out_thread() == _thread) {
          st->print(" (sent by handshake timeout handler)");
        } else if (get_safepoint_timed_out_thread() == _thread) {
          st->print(" (sent by safepoint timeout handler)");
        } else {
          st->print(" (sent by kill)");
        }
      }
    } else {
      if (should_report_bug(_id)) {
        st->print("Internal Error");
      } else {
        st->print("Out of Memory Error");
      }
      if (_filename != nullptr && _lineno > 0) {
#ifdef PRODUCT
        // In product mode chop off pathname
        const char *file = get_filename_only();
#else
        const char *file = _filename;
#endif
        st->print(" (%s:%d)", file, _lineno);
      } else {
        st->print(" (0x%x)", _id);
      }
    }

  STEP("printing current thread and pid")
    // process id, thread id
    st->print(", pid=%d", os::current_process_id());
    st->print(", tid=%zu", os::current_thread_id());
    st->cr();

  STEP_IF("printing error message", should_report_bug(_id)) // already printed the message.
    // error message
    if (strlen(_detail_msg) > 0) {
      st->print_cr("#  %s: %s", _message ? _message : "Error", _detail_msg);
    } else if (_message) {
      st->print_cr("#  Error: %s", _message);
    }

  STEP("printing Java version string")
    report_vm_version(st, buf, sizeof(buf));

  STEP_IF("printing problematic frame", _context != nullptr)
    // Print current frame if we have a context (i.e. it's a crash)
    st->print_cr("# Problematic frame:");
    st->print("# ");
    frame fr = os::fetch_frame_from_context(_context);
    fr.print_on_error(st, buf, sizeof(buf));
    st->cr();
    st->print_cr("#");

  STEP("printing core file information")
    st->print("# ");
    if (CreateCoredumpOnCrash) {
      if (coredump_status) {
        st->print("Core dump will be written. Default location: %s", coredump_message);
      } else {
        st->print("No core dump will be written. %s", coredump_message);
      }
    } else {
      st->print("CreateCoredumpOnCrash turned off, no core file dumped");
    }
    st->cr();
    st->print_cr("#");

  JFR_ONLY(STEP("printing jfr information"))
  JFR_ONLY(Jfr::on_vm_error_report(st);)

  STEP_IF("printing bug submit message", should_submit_bug_report(_id) && _verbose)
    print_bug_submit_message(st, _thread);

  STEP_IF("printing summary", _verbose)
    st->cr();
    st->print_cr("---------------  S U M M A R Y ------------");
    st->cr();

  STEP_IF("printing VM option summary", _verbose)
    // VM options
    Arguments::print_summary_on(st);
    st->cr();

  STEP_IF("printing summary machine and OS info", _verbose)
    os::print_summary_info(st, buf, sizeof(buf));

  STEP_IF("printing date and time", _verbose)
    os::print_date_and_time(st, buf, sizeof(buf));

#ifdef ADDRESS_SANITIZER
  STEP_IF("printing ASAN error information", _verbose && Asan::had_error())
    st->cr();
    st->print_cr("------------------  A S A N ----------------");
    st->cr();
    Asan::report(st);
    st->cr();
#endif // ADDRESS_SANITIZER

    STEP_IF("printing thread", _verbose)
    st->cr();
    st->print_cr("---------------  T H R E A D  ---------------");
    st->cr();

  STEP_IF("printing current thread", _verbose)
    // current thread
    if (_thread) {
      st->print("Current thread (" PTR_FORMAT "):  ", p2i(_thread));
      _thread->print_on_error(st, buf, sizeof(buf));
      st->cr();
    } else {
      st->print_cr("Current thread is native thread");
    }
    st->cr();

  STEP_IF("printing current compile task",
      _verbose && _thread != nullptr && _thread->is_Compiler_thread())
    CompilerThread* t = (CompilerThread*)_thread;
    if (t->task()) {
        st->cr();
        st->print_cr("Current CompileTask:");
        t->task()->print_line_on_error(st, buf, sizeof(buf));
        st->cr();
    }

  STEP_IF("printing stack bounds", _verbose)
    st->print("Stack: ");

    address stack_top;
    size_t stack_size;

    if (_thread) {
      stack_top = _thread->stack_base();
      stack_size = _thread->stack_size();
    } else {
      os::current_stack_base_and_size(&stack_top, &stack_size);
    }

    address stack_bottom = stack_top - stack_size;
    st->print("[" PTR_FORMAT "," PTR_FORMAT "]", p2i(stack_bottom), p2i(stack_top));

    frame fr = _context ? os::fetch_frame_from_context(_context)
                        : os::current_frame();

    address sp = (address)fr.sp();
    if (sp != nullptr) {
      st->print(",  sp=" PTR_FORMAT, p2i(sp));
      if (sp >= stack_bottom && sp < stack_top) {
        size_t free_stack_size = pointer_delta(sp, stack_bottom, 1024);
        st->print(",  free space=%zuk", free_stack_size);
      } else {
        st->print(" **OUTSIDE STACK**.");
      }
    }

    st->cr();

  STEP_IF("printing native stack (with source info)", _verbose)

    NativeStackPrinter nsp(_thread, _context, _filename != nullptr ? get_filename_only() : nullptr, _lineno);
    if (nsp.print_stack(st, buf, sizeof(buf), lastpc,
                        true /*print_source_info */, -1 /* max stack */)) {
      // We have printed the native stack in platform-specific code
      // Windows/x64 needs special handling.
      // Stack walking may get stuck. Try to find the calling code.
      if (lastpc != nullptr) {
        const char* name = find_code_name(lastpc);
        if (name != nullptr) {
          st->print_cr("The last pc belongs to %s (printed below).", name);
        }
      }
    } else {
      _print_stack_from_frame_used = true; // frame-based native stack walk done
    }

  REATTEMPT_STEP_IF("retry printing native stack (no source info)", _verbose)
    st->cr();
    st->print_cr("Retrying call stack printing without source information...");
    NativeStackPrinter nsp(_thread, _context, get_filename_only(), _lineno);
    nsp.print_stack_from_frame(st, buf, sizeof(buf),
                               false /*print_source_info */, -1 /* max stack */);
    _print_stack_from_frame_used = true;

  STEP_IF("printing Java stack", _verbose && _thread && _thread->is_Java_thread())
    if (_verbose && _thread && _thread->is_Java_thread()) {
      print_stack_trace(st, JavaThread::cast(_thread), buf, sizeof(buf));
    }

  STEP_IF("printing target Java thread stack",
      _verbose && _thread != nullptr && (_thread->is_Named_thread()))
    // printing Java thread stack trace if it is involved in GC crash
    Thread* thread = ((NamedThread *)_thread)->processed_thread();
    if (thread != nullptr && thread->is_Java_thread()) {
      JavaThread* jt = JavaThread::cast(thread);
      st->print_cr("JavaThread " PTR_FORMAT " (nid = %d) was being processed", p2i(jt), jt->osthread()->thread_id());
      print_stack_trace(st, jt, buf, sizeof(buf), true);
    }

  STEP_IF("printing siginfo", _verbose && _siginfo != nullptr)
    // signal no, signal code, address that caused the fault
    st->cr();
    os::print_siginfo(st, _siginfo);
    st->cr();

  STEP_IF("CDS archive access warning", _verbose && _siginfo != nullptr)
    // Print an explicit hint if we crashed on access to the CDS archive.
    check_failing_cds_access(st, _siginfo);
    st->cr();

#if defined(COMPILER1) || defined(COMPILER2)
  STEP_IF("printing pending compilation failure",
          _verbose && _thread != nullptr && _thread->is_Compiler_thread())
    CompilationFailureInfo::print_pending_compilation_failure(st);
  if (CompilationMemoryStatistic::enabled() && CompilationMemoryStatistic::in_oom_crash()) {
    st->cr();
    st->print_cr(">> Please see below for a detailed breakdown of compiler memory usage.");
    st->cr();
  }
#endif

  STEP_IF("printing registers", _verbose && _context != nullptr)
    // printing registers
    os::print_context(st, _context);
    st->cr();

  STEP_IF("printing register info",
      _verbose && _context != nullptr && _thread != nullptr && Universe::is_fully_initialized())
    continuation = 0;
    ResourceMark rm(_thread);
    st->print_cr("Register to memory mapping:");
    st->cr();
    os::print_register_info(st, _context, continuation);
    st->cr();

  REATTEMPT_STEP_IF("printing register info, attempt 2",
      _verbose && _context != nullptr && _thread != nullptr && Universe::is_fully_initialized())
    ResourceMark rm(_thread);
    os::print_register_info(st, _context, continuation);
    st->cr();

  REATTEMPT_STEP_IF("printing register info, attempt 3",
      _verbose && _context != nullptr && _thread != nullptr && Universe::is_fully_initialized())
    ResourceMark rm(_thread);
    os::print_register_info(st, _context, continuation);
    st->cr();

  STEP_IF("printing top of stack, instructions near pc", _verbose && _context != nullptr)
    // printing top of stack, instructions near pc
    os::print_tos_pc(st, _context);
    st->cr();

  STEP_IF("inspecting top of stack",
      _verbose && _context != nullptr && _thread != nullptr && Universe::is_fully_initialized())
    continuation = 0;
    ResourceMark rm(_thread);
    st->print_cr("Stack slot to memory mapping:");
    st->cr();
    print_stack_location(st, _context, continuation);
    st->cr();

  REATTEMPT_STEP_IF("inspecting top of stack, attempt 2",
      _verbose && _context != nullptr && _thread != nullptr && Universe::is_fully_initialized())
    ResourceMark rm(_thread);
    print_stack_location(st, _context, continuation);
    st->cr();

  REATTEMPT_STEP_IF("inspecting top of stack, attempt 3",
      _verbose && _context != nullptr && _thread != nullptr && Universe::is_fully_initialized())
    ResourceMark rm(_thread);
    print_stack_location(st, _context, continuation);
    st->cr();

  STEP_IF("printing lock stack", _verbose && _thread != nullptr && _thread->is_Java_thread());
    st->print_cr("Lock stack of current Java thread (top to bottom):");
    JavaThread::cast(_thread)->lock_stack().print_on(st);
    st->cr();

  STEP_IF("printing code blobs if possible", _verbose)
    const int printed_capacity = max_error_log_print_code;
    address printed[printed_capacity];
    printed[0] = nullptr;
    int printed_len = 0;
    // Even though ErrorLogPrintCodeLimit is ranged checked
    // during argument parsing, there's no way to prevent it
    // subsequently (i.e., after parsing) being set to a
    // value outside the range.
    int limit = MIN2(ErrorLogPrintCodeLimit, printed_capacity);
    if (limit > 0) {
      // Check if a pc was found by native stack trace above.
      if (lastpc != nullptr) {
        if (print_code(st, _thread, lastpc, true, printed, printed_capacity)) {
          printed_len++;
        }
      }

      // Scan the native stack
      if (!_print_stack_from_frame_used) {
        // Only try to print code of the crashing frame since
        // the native stack cannot be walked with next_frame.
        if (print_code(st, _thread, _pc, true, printed, printed_capacity)) {
          printed_len++;
        }
      } else {
        frame fr = _context ? os::fetch_frame_from_context(_context)
                            : os::current_frame();
        while (printed_len < limit && fr.pc() != nullptr) {
          if (print_code(st, _thread, fr.pc(), fr.pc() == _pc, printed, printed_capacity)) {
            printed_len++;
          }
          fr = frame::next_frame(fr, _thread);
        }
      }

      // Scan the Java stack
      if (_thread != nullptr && _thread->is_Java_thread()) {
        JavaThread* jt = JavaThread::cast(_thread);
        if (jt->has_last_Java_frame()) {
          for (StackFrameStream sfs(jt, true /* update */, true /* process_frames */); printed_len < limit && !sfs.is_done(); sfs.next()) {
            address pc = sfs.current()->pc();
            if (print_code(st, _thread, pc, pc == _pc, printed, printed_capacity)) {
              printed_len++;
            }
          }
        }
      }
    }

  STEP_IF("printing VM operation", _verbose && _thread != nullptr && _thread->is_VM_thread())
    VMThread* t = (VMThread*)_thread;
    VM_Operation* op = t->vm_operation();
    if (op) {
      op->print_on_error(st);
      st->cr();
      st->cr();
    }

  STEP_IF("printing registered callbacks", _verbose && _thread != nullptr);
    size_t count = 0;
    for (VMErrorCallback* callback = _thread->_vm_error_callbacks;
        callback != nullptr;
        callback = callback->_next) {
      st->print_cr("VMErrorCallback %zu:", ++count);
      callback->call(st);
      st->cr();
    }

  STEP_IF("printing process", _verbose)
    st->cr();
    st->print_cr("---------------  P R O C E S S  ---------------");
    st->cr();

  STEP_IF("printing user info", ExtensiveErrorReports && _verbose)
    os::print_user_info(st);

  STEP_IF("printing all threads", _verbose && _thread != nullptr)
    // all threads
    Threads::print_on_error(st, _thread, buf, sizeof(buf));
    st->cr();

  STEP_IF("printing VM state", _verbose)
    // Safepoint state
    st->print("VM state: ");

    if (SafepointSynchronize::is_synchronizing()) st->print("synchronizing");
    else if (SafepointSynchronize::is_at_safepoint()) st->print("at safepoint");
    else st->print("not at safepoint");

    // Also see if error occurred during initialization or shutdown
    if (!Universe::is_fully_initialized()) {
      st->print(" (not fully initialized)");
    } else if (VM_Exit::vm_exited()) {
      st->print(" (shutting down)");
    } else {
      st->print(" (normal execution)");
    }
    st->cr();
    st->cr();

  STEP_IF("printing owned locks on error", _verbose)
    // mutexes/monitors that currently have an owner
    Mutex::print_owned_locks_on_error(st);
    st->cr();

  STEP_IF("printing number of OutOfMemoryError and StackOverflow exceptions",
      _verbose && Exceptions::has_exception_counts())
    st->print_cr("OutOfMemory and StackOverflow Exception counts:");
    Exceptions::print_exception_counts_on_error(st);
    st->cr();

#ifdef _LP64
  STEP_IF("printing compressed oops mode", _verbose && UseCompressedOops)
    CompressedOops::print_mode(st);
    st->cr();

  STEP_IF("printing compressed klass pointers mode", _verbose && UseCompressedClassPointers)
    CDS_ONLY(AOTMetaspace::print_on(st);)
    Metaspace::print_compressed_class_space(st);
    CompressedKlassPointers::print_mode(st);
    st->cr();
#endif

  STEP_IF("printing heap information", _verbose)
    GCLogPrecious::print_on_error(st);

    if (Universe::heap() != nullptr) {
      st->print_cr("Heap:");
      StreamIndentor si(st, 1);
      Universe::heap()->print_heap_on(st);
      st->cr();
    }

  STEP_IF("printing GC information", _verbose)
    if (Universe::heap() != nullptr) {
      Universe::heap()->print_gc_on(st);
      st->cr();
    }

    if (Universe::is_fully_initialized()) {
      st->print_cr("Polling page: " PTR_FORMAT, p2i(SafepointMechanism::get_polling_page()));
      st->cr();
    }

  STEP_IF("printing metaspace information", _verbose && Universe::is_fully_initialized())
    st->print_cr("Metaspace:");
    MetaspaceUtils::print_on(st);
    MetaspaceUtils::print_basic_report(st, 0);

  STEP_IF("printing code cache information", _verbose && Universe::is_fully_initialized())
    // print code cache information before vm abort
    CodeCache::print_summary(st);
    st->cr();

  STEP_IF("printing ring buffers", _verbose)
    Events::print_all(st);
    st->cr();

  STEP_IF("printing dynamic libraries", _verbose)
    // dynamic libraries, or memory map
    os::print_dll_info(st);
    st->cr();

#if INCLUDE_JVMTI
  STEP_IF("printing jvmti agent info", _verbose)
    os::print_jvmti_agent_info(st);
    st->cr();
#endif

  STEP_IF("printing native decoder state", _verbose)
    Decoder::print_state_on(st);
    st->cr();

  STEP_IF("printing VM options", _verbose)
    // VM options
    Arguments::print_on(st);
    st->cr();

  STEP_IF("printing flags", _verbose)
    JVMFlag::printFlags(
      st,
      true, // with comments
      false, // no ranges
      true); // skip defaults
    st->cr();

  STEP_IF("printing warning if internal testing API used", WhiteBox::used())
    st->print_cr("Unsupported internal testing APIs have been used.");
    st->cr();

  STEP_IF("printing log configuration", _verbose)
    st->print_cr("Logging:");
    LogConfiguration::describe_current_configuration(st);
    st->cr();

  STEP_IF("printing release file content", _verbose)
    st->print_cr("Release file:");
    os::print_image_release_file(st);

  STEP_IF("printing all environment variables", _verbose)
    os::print_environment_variables(st, env_list);
    st->cr();

  STEP_IF("printing locale settings", _verbose)
    os::print_active_locale(st);
    st->cr();

  STEP_IF("printing signal handlers", _verbose)
    os::print_signal_handlers(st, buf, sizeof(buf));
    st->cr();

  STEP_IF("Native Memory Tracking", _verbose && _thread != nullptr)
    MemTracker::error_report(st);
    st->cr();

  STEP_IF("printing compiler memory info, if any", _verbose)
    CompilationMemoryStatistic::print_error_report(st);
    st->cr();

  STEP_IF("printing periodic trim state", _verbose)
    NativeHeapTrimmer::print_state(st);
    st->cr();

  STEP_IF("printing system", _verbose)
    st->print_cr("---------------  S Y S T E M  ---------------");
    st->cr();

  STEP_IF("printing OS information", _verbose)
    os::print_os_info(st);
    st->cr();

  STEP_IF("printing CPU info", _verbose)
    os::print_cpu_info(st, buf, sizeof(buf));
    st->cr();

  STEP_IF("printing memory info", _verbose)
    os::print_memory_info(st);
    st->cr();

  STEP_IF("printing internal vm info", _verbose)
    st->print_cr("vm_info: %s", VM_Version::internal_vm_info_string());
    st->cr();

  // print a defined marker to show that error handling finished correctly.
  STEP_IF("printing end marker", _verbose)
    st->print_cr("END.");

  END

# undef BEGIN
# undef STEP_IF
# undef STEP
# undef REATTEMPT_STEP_IF
# undef END
}

void VMError::set_handshake_timed_out_thread(Thread* thread) {
  // Only preserve the first thread to time-out this way. The atomic operation ensures
  // visibility to the target thread.
  AtomicAccess::replace_if_null(&_handshake_timed_out_thread, thread);
}

void VMError::set_safepoint_timed_out_thread(Thread* thread) {
  // Only preserve the first thread to time-out this way. The atomic operation ensures
  // visibility to the target thread.
  AtomicAccess::replace_if_null(&_safepoint_timed_out_thread, thread);
}

Thread* VMError::get_handshake_timed_out_thread() {
  return AtomicAccess::load(&_handshake_timed_out_thread);
}

Thread* VMError::get_safepoint_timed_out_thread() {
  return AtomicAccess::load(&_safepoint_timed_out_thread);
}

// Report for the vm_info_cmd. This prints out the information above omitting
// crash and thread specific information.  If output is added above, it should be added
// here also, if it is safe to call during a running process.
void VMError::print_vm_info(outputStream* st) {

  char buf[O_BUFLEN];
  os::prepare_native_symbols();

  report_vm_version(st, buf, sizeof(buf));

  // STEP("printing summary")

  st->cr();
  st->print_cr("---------------  S U M M A R Y ------------");
  st->cr();

  // STEP("printing VM option summary")

  // VM options
  Arguments::print_summary_on(st);
  st->cr();

  // STEP("printing summary machine and OS info")

  os::print_summary_info(st, buf, sizeof(buf));

  // STEP("printing date and time")

  os::print_date_and_time(st, buf, sizeof(buf));

  // Skip: STEP("printing thread")

  // STEP("printing process")

  st->cr();
  st->print_cr("---------------  P R O C E S S  ---------------");
  st->cr();

  // STEP("printing number of OutOfMemoryError and StackOverflow exceptions")

  if (Exceptions::has_exception_counts()) {
    st->print_cr("OutOfMemory and StackOverflow Exception counts:");
    Exceptions::print_exception_counts_on_error(st);
    st->cr();
  }

#ifdef _LP64
  // STEP("printing compressed oops mode")
  if (UseCompressedOops) {
    CompressedOops::print_mode(st);
    st->cr();
  }
#endif

  // STEP("printing compressed class ptrs mode")
  if (UseCompressedClassPointers) {
    CDS_ONLY(AOTMetaspace::print_on(st);)
    Metaspace::print_compressed_class_space(st);
    CompressedKlassPointers::print_mode(st);
    st->cr();
  }

  // Take heap lock over heap, GC and metaspace printing so that information
  // is consistent.
  if (Universe::is_fully_initialized()) {
    MutexLocker ml(Heap_lock);

    // STEP("printing heap information")

    GCLogPrecious::print_on_error(st);

    {
      st->print_cr("Heap:");
      StreamIndentor si(st, 1);
      Universe::heap()->print_heap_on(st);
      st->cr();
    }

    // STEP("printing GC information")

    Universe::heap()->print_gc_on(st);
    st->cr();

    st->print_cr("Polling page: " PTR_FORMAT, p2i(SafepointMechanism::get_polling_page()));
    st->cr();

    // STEP("printing metaspace information")

    st->print_cr("Metaspace:");
    MetaspaceUtils::print_on(st);
    MetaspaceUtils::print_basic_report(st, 0);
  }

  // STEP("printing code cache information")

  if (Universe::is_fully_initialized()) {
    // print code cache information before vm abort
    CodeCache::print_summary(st);
    st->cr();
  }

  // STEP("printing ring buffers")

  Events::print_all(st);
  st->cr();

  // STEP("printing dynamic libraries")

  // dynamic libraries, or memory map
  os::print_dll_info(st);
  st->cr();

#if INCLUDE_JVMTI
  os::print_jvmti_agent_info(st);
  st->cr();
#endif

  // STEP("printing VM options")

  // VM options
  Arguments::print_on(st);
  st->cr();

  // STEP("printing warning if internal testing API used")

  if (WhiteBox::used()) {
    st->print_cr("Unsupported internal testing APIs have been used.");
    st->cr();
  }

  // STEP("printing log configuration")
  st->print_cr("Logging:");
  LogConfiguration::describe(st);
  st->cr();

  // STEP("printing release file content")
  st->print_cr("Release file:");
  os::print_image_release_file(st);

  // STEP("printing all environment variables")

  os::print_environment_variables(st, env_list);
  st->cr();

  // STEP("printing locale settings")

  os::print_active_locale(st);
  st->cr();


  // STEP("printing signal handlers")

  os::print_signal_handlers(st, buf, sizeof(buf));
  st->cr();

  // STEP("Native Memory Tracking")
  MemTracker::error_report(st);
  st->cr();

  // STEP("Compiler Memory Statistic")
  CompilationMemoryStatistic::print_final_report(st);

  // STEP("printing periodic trim state")
  NativeHeapTrimmer::print_state(st);
  st->cr();


  // STEP("printing system")
  st->print_cr("---------------  S Y S T E M  ---------------");
  st->cr();

  // STEP("printing OS information")

  os::print_os_info(st);
  st->cr();

  // STEP("printing CPU info")

  os::print_cpu_info(st, buf, sizeof(buf));
  st->cr();

  // STEP("printing memory info")

  os::print_memory_info(st);
  st->cr();

  // STEP("printing internal vm info")

  st->print_cr("vm_info: %s", VM_Version::internal_vm_info_string());
  st->cr();

  // print a defined marker to show that error handling finished correctly.
  // STEP("printing end marker")

  st->print_cr("END.");
}

/** Expand a pattern into a buffer starting at pos and open a file using constructed path */
static int expand_and_open(const char* pattern, bool overwrite_existing, char* buf, size_t buflen, size_t pos) {
  int fd = -1;
  int mode = O_RDWR | O_CREAT;
  if (overwrite_existing) {
    mode |= O_TRUNC;
  } else {
    mode |= O_EXCL;
  }
  if (Arguments::copy_expand_pid(pattern, strlen(pattern), &buf[pos], buflen - pos)) {
    fd = open(buf, mode, 0666);
  }
  return fd;
}

/**
 * Construct file name for a log file and return it's file descriptor.
 * Name and location depends on pattern, default_pattern params and access
 * permissions.
 */
int VMError::prepare_log_file(const char* pattern, const char* default_pattern, bool overwrite_existing, char* buf, size_t buflen) {
  int fd = -1;

  // If possible, use specified pattern to construct log file name
  if (pattern != nullptr) {
    fd = expand_and_open(pattern, overwrite_existing, buf, buflen, 0);
  }

  // Either user didn't specify, or the user's location failed,
  // so use the default name in the current directory
  if (fd == -1) {
    const char* cwd = os::get_current_directory(buf, buflen);
    if (cwd != nullptr) {
      size_t pos = strlen(cwd);
      int fsep_len = jio_snprintf(&buf[pos], buflen-pos, "%s", os::file_separator());
      pos += fsep_len;
      if (fsep_len > 0) {
        fd = expand_and_open(default_pattern, overwrite_existing, buf, buflen, pos);
      }
    }
  }

   // try temp directory if it exists.
   if (fd == -1) {
     const char* tmpdir = os::get_temp_directory();
     if (tmpdir != nullptr && strlen(tmpdir) > 0) {
       int pos = jio_snprintf(buf, buflen, "%s%s", tmpdir, os::file_separator());
       if (pos > 0) {
         fd = expand_and_open(default_pattern, overwrite_existing, buf, buflen, pos);
       }
     }
   }

  return fd;
}

void VMError::report_and_die(Thread* thread, unsigned int sig, address pc, const void* siginfo,
                             const void* context, const char* detail_fmt, ...)
{
  va_list detail_args;
  va_start(detail_args, detail_fmt);
  report_and_die(sig, nullptr, detail_fmt, detail_args, thread, pc, siginfo, context, nullptr, 0, 0);
  va_end(detail_args);
}

void VMError::report_and_die(Thread* thread, const void* context, const char* filename, int lineno, const char* message,
                             const char* detail_fmt, ...) {
  va_list detail_args;
  va_start(detail_args, detail_fmt);
  report_and_die(thread, context, filename, lineno, message, detail_fmt, detail_args);
  va_end(detail_args);
}

void VMError::report_and_die(Thread* thread, unsigned int sig, address pc, const void* siginfo, const void* context)
{
  if (ExecutingUnitTests) {
    // See TEST_VM_CRASH_SIGNAL gtest macro
    char tmp[64];
    fprintf(stderr, "signaled: %s", os::exception_name(sig, tmp, sizeof(tmp)));
  }

  report_and_die(thread, sig, pc, siginfo, context, "%s", "");
}

void VMError::report_and_die(Thread* thread, const void* context, const char* filename, int lineno, const char* message,
                             const char* detail_fmt, va_list detail_args)
{
  report_and_die(INTERNAL_ERROR, message, detail_fmt, detail_args, thread, nullptr, nullptr, context, filename, lineno, 0);
}

void VMError::report_and_die(Thread* thread, const char* filename, int lineno, size_t size,
                             VMErrorType vm_err_type, const char* detail_fmt, va_list detail_args) {
  report_and_die(vm_err_type, nullptr, detail_fmt, detail_args, thread, nullptr, nullptr, nullptr, filename, lineno, size);
}

void VMError::report_and_die(int id, const char* message, const char* detail_fmt, va_list detail_args,
                             Thread* thread, address pc, const void* siginfo, const void* context, const char* filename,
                             int lineno, size_t size)
{
  // A single scratch buffer to be used from here on.
  // Do not rely on it being preserved across function calls.
  static char buffer[O_BUFLEN];

  // File descriptor to tty to print an error summary to.
  // Hard wired to stdout; see JDK-8215004 (compatibility concerns).
  static const int fd_out = 1; // stdout

  // File descriptor to the error log file.
  static int fd_log = -1;

#ifdef CAN_SHOW_REGISTERS_ON_ASSERT
  // Disarm assertion poison page, since from this point on we do not need this mechanism anymore and it may
  // cause problems in error handling during native OOM, see JDK-8227275.
  disarm_assert_poison();
#endif

  // Use local fdStream objects only. Do not use global instances whose initialization
  // relies on dynamic initialization (see JDK-8214975). Do not rely on these instances
  // to carry over into recursions or invocations from other threads.
  fdStream out(fd_out);
  out.set_scratch_buffer(buffer, sizeof(buffer));

  // Depending on the re-entrance depth at this point, fd_log may be -1 or point to an open hs-err file.
  fdStream log(fd_log);
  log.set_scratch_buffer(buffer, sizeof(buffer));

  // How many errors occurred in error handler when reporting first_error.
  static int recursive_error_count;

  // We will first print a brief message to standard out (verbose = false),
  // then save detailed information in log file (verbose = true).
  static bool out_done = false;         // done printing to standard out
  static bool log_done = false;         // done saving error log

  intptr_t mytid = os::current_thread_id();
  if (_first_error_tid == -1 &&
      AtomicAccess::cmpxchg(&_first_error_tid, (intptr_t)-1, mytid) == -1) {

    if (SuppressFatalErrorMessage) {
      os::abort(CreateCoredumpOnCrash);
    }

    // Initialize time stamps to use the same base.
    out.time_stamp().update_to(1);
    log.time_stamp().update_to(1);

    _id = id;
    _message = message;
    _thread = thread;
    _pc = pc;
    _siginfo = siginfo;
    _context = context;
    _filename = filename;
    _lineno = lineno;
    _size = size;
    jio_vsnprintf(_detail_msg, sizeof(_detail_msg), detail_fmt, detail_args);

    reporting_started();
    if (!TestUnresponsiveErrorHandler) {
      // Record reporting_start_time unless we're running the
      // TestUnresponsiveErrorHandler test. For that test we record
      // reporting_start_time at the beginning of the test.
      record_reporting_start_time();
    } else {
      out.print_raw_cr("Delaying recording reporting_start_time for TestUnresponsiveErrorHandler.");
    }

    if (ShowMessageBoxOnError || PauseAtExit) {
      show_message_box(buffer, sizeof(buffer));

      // User has asked JVM to abort. Reset ShowMessageBoxOnError so the
      // WatcherThread can kill JVM if the error handler hangs.
      ShowMessageBoxOnError = false;
    }

    os::check_core_dump_prerequisites(buffer, sizeof(buffer));

    // reset signal handlers or exception filter; make sure recursive crashes
    // are handled properly.
    install_secondary_signal_handler();
  } else {
    // This is not the first error, see if it happened in a different thread
    // or in the same thread during error reporting.
    if (_first_error_tid != mytid) {
      if (!SuppressFatalErrorMessage) {
        char msgbuf[64];
        jio_snprintf(msgbuf, sizeof(msgbuf),
                     "[thread %zd also had an error]",
                     mytid);
        out.print_raw_cr(msgbuf);
      }

      // Error reporting is not MT-safe, nor can we let the current thread
      // proceed, so we block it.
      os::infinite_sleep();

    } else {
      if (recursive_error_count++ > 30) {
        if (!SuppressFatalErrorMessage) {
          out.print_raw_cr("[Too many errors, abort]");
        }
        os::die();
      }

      if (SuppressFatalErrorMessage) {
        // If we already hit a secondary error during abort, then calling
        // it again is likely to hit another one. But eventually, if we
        // don't deadlock somewhere, we will call os::die() above.
        os::abort(CreateCoredumpOnCrash);
      }

      outputStream* const st = log.is_open() ? &log : &out;
      st->cr();

      // Timeout handling.
      if (_step_did_timeout) {
        // The current step had a timeout. Lets continue reporting with the next step.
        st->print_raw("[timeout occurred during error reporting in step \"");
        st->print_raw(_current_step_info);
        st->print_cr("\"] after " INT64_FORMAT " s.",
                     (int64_t)
                     ((get_current_timestamp() - _step_start_time) / TIMESTAMP_TO_SECONDS_FACTOR));
      } else if (_reporting_did_timeout) {
        // We hit ErrorLogTimeout. Reporting will stop altogether. Let's wrap things
        // up, the process is about to be stopped by the WatcherThread.
        st->print_cr("------ Timeout during error reporting after " INT64_FORMAT " s. ------",
                     (int64_t)
                     ((get_current_timestamp() - _reporting_start_time) / TIMESTAMP_TO_SECONDS_FACTOR));
        st->flush();
        // Watcherthread is about to call os::die. Lets just wait.
        os::infinite_sleep();
      } else {
        // A secondary error happened. Print brief information, but take care, since crashing
        // here would just recurse endlessly.
        // Any information (signal, context, siginfo etc) printed here should use the function
        // arguments, not the information stored in *this, since those describe the primary crash.
        static char tmp[256]; // cannot use global scratch buffer
        // Note: this string does get parsed by a number of jtreg tests,
        // see hotspot/jtreg/runtime/ErrorHandling.
        st->print("[error occurred during error reporting (%s), id 0x%x",
                   _current_step_info, id);
        if (os::exception_name(id, tmp, sizeof(tmp))) {
          st->print(", %s (0x%x) at pc=" PTR_FORMAT, tmp, id, p2i(pc));
        } else {
          if (should_report_bug(id)) {
            st->print(", Internal Error (%s:%d)",
              filename == nullptr ? "??" : filename, lineno);
          } else {
            st->print(", Out of Memory Error (%s:%d)",
              filename == nullptr ? "??" : filename, lineno);
          }
        }
        st->print_cr("]");
        if (ErrorLogSecondaryErrorDetails) {
          static bool recursed = false;
          if (!recursed) {
            recursed = true;
            // Print even more information for secondary errors. This may generate a lot of output
            // and possibly disturb error reporting, therefore its optional and only available in debug builds.
            if (siginfo != nullptr) {
              st->print("[");
              os::print_siginfo(st, siginfo);
              st->print_cr("]");
            }
            st->print("[stack: ");
            NativeStackPrinter nsp(_thread, context, _filename != nullptr ? get_filename_only() : nullptr, _lineno);
            // Subsequent secondary errors build up stack; to avoid flooding the hs-err file with irrelevant
            // call stacks, limit the stack we print here (we are only interested in what happened before the
            // last assert/fault).
            const int max_stack_size = 15;
            nsp.print_stack_from_frame(st, tmp, sizeof(tmp), true /* print_source_info */, max_stack_size);
            st->print_cr("]");
          } // !recursed
          recursed = false; // Note: reset outside !recursed
        }
      }
    }
  }

  // Part 1: print an abbreviated version (the '#' section) to stdout.
  if (!out_done) {
    // Suppress this output if we plan to print Part 2 to stdout too.
    // No need to have the "#" section twice.
    if (!(ErrorFileToStdout && out.fd() == 1)) {
      report(&out, false);
    }

    out_done = true;

    _current_step = 0;
    _current_step_info = "";
  }

  // Part 2: print a full error log file (optionally to stdout or stderr).
  // print to error log file
  if (!log_done) {
    // see if log file is already open
    if (!log.is_open()) {
      // open log file
      if (ErrorFileToStdout) {
        fd_log = 1;
      } else if (ErrorFileToStderr) {
        fd_log = 2;
      } else {
        fd_log = prepare_log_file(ErrorFile, "hs_err_pid%p.log", true,
                 buffer, sizeof(buffer));
        if (fd_log != -1) {
          out.print_raw("# An error report file with more information is saved as:\n# ");
          out.print_raw_cr(buffer);
        } else {
          out.print_raw_cr("# Can not save log file, dump to screen..");
          fd_log = 1;
        }
      }
      log.set_fd(fd_log);
    }

    report(&log, true);
    log_done = true;
    _current_step = 0;
    _current_step_info = "";

    if (fd_log > 3) {
      ::close(fd_log);
      fd_log = -1;
    }

    log.set_fd(-1);
  }

  JFR_ONLY(Jfr::on_vm_shutdown(true, false, static_cast<VMErrorType>(_id) == OOM_JAVA_HEAP_FATAL);)

  if (PrintNMTStatistics) {
    fdStream fds(fd_out);
    MemTracker::final_report(&fds);
  }

  static bool skip_replay = ReplayCompiles && !ReplayReduce; // Do not overwrite file during replay
  if (DumpReplayDataOnError && _thread && _thread->is_Compiler_thread() && !skip_replay) {
    skip_replay = true;
    ciEnv* env = ciEnv::current();
    if (env != nullptr && env->task() != nullptr) {
      const bool overwrite = false; // We do not overwrite an existing replay file.
      int fd = prepare_log_file(ReplayDataFile, "replay_pid%p.log", overwrite, buffer, sizeof(buffer));
      if (fd != -1) {
        FILE* replay_data_file = os::fdopen(fd, "w");
        if (replay_data_file != nullptr) {
          fileStream replay_data_stream(replay_data_file, /*need_close=*/true);
          env->dump_replay_data_unsafe(&replay_data_stream);
          out.print_raw("#\n# Compiler replay data is saved as:\n# ");
          out.print_raw_cr(buffer);
        } else {
          int e = errno;
          out.print_raw("#\n# Can't open file to dump replay data. Error: ");
          out.print_raw_cr(os::strerror(e));
          close(fd);
        }
      }
    }
  }

#if INCLUDE_JVMCI
  if (JVMCI::fatal_log_filename() != nullptr) {
    out.print_raw("#\n# The JVMCI shared library error report file is saved as:\n# ");
    out.print_raw_cr(JVMCI::fatal_log_filename());
  }
#endif

  static bool skip_bug_url = !should_submit_bug_report(_id);
  if (!skip_bug_url) {
    skip_bug_url = true;

    out.print_raw_cr("#");
    print_bug_submit_message(&out, _thread);
  }

  static bool skip_OnError = false;
  if (!skip_OnError && OnError && OnError[0]) {
    skip_OnError = true;

    // Flush output and finish logs before running OnError commands.
    ostream_abort();

    out.print_raw_cr("#");
    out.print_raw   ("# -XX:OnError=\"");
    out.print_raw   (OnError);
    out.print_raw_cr("\"");

    char* cmd;
    const char* ptr = OnError;
    while ((cmd = next_OnError_command(buffer, sizeof(buffer), &ptr)) != nullptr){
      out.print_raw   ("#   Executing ");
#if defined(LINUX) || defined(_ALLBSD_SOURCE)
      out.print_raw   ("/bin/sh -c ");
#elif defined(_WINDOWS)
      out.print_raw   ("cmd /C ");
#endif
      out.print_raw   ("\"");
      out.print_raw   (cmd);
      out.print_raw_cr("\" ...");

      if (os::fork_and_exec(cmd) < 0) {
        out.print_cr("os::fork_and_exec failed: %s (%s=%d)",
                     os::strerror(errno), os::errno_name(errno), errno);
      }
    }

    // done with OnError
    OnError = nullptr;
  }

#if defined _WINDOWS
  if (UseOSErrorReporting) {
    raise_fail_fast(_siginfo, _context);
  }
#endif // _WINDOWS

  // os::abort() will call abort hooks, try it first.
  static bool skip_os_abort = false;
  if (!skip_os_abort) {
    skip_os_abort = true;
    bool dump_core = should_report_bug(_id);
    os::abort(dump_core && CreateCoredumpOnCrash, _siginfo, _context);
    // if os::abort() doesn't abort, try os::die();
  }
  os::die();
}

/*
 * OnOutOfMemoryError scripts/commands executed while VM is a safepoint - this
 * ensures utilities such as jmap can observe the process is a consistent state.
 */
class VM_ReportJavaOutOfMemory : public VM_Operation {
 private:
  const char* _message;
 public:
  VM_ReportJavaOutOfMemory(const char* message) { _message = message; }
  VMOp_Type type() const                        { return VMOp_ReportJavaOutOfMemory; }
  void doit();
};

void VM_ReportJavaOutOfMemory::doit() {
  // Don't allocate large buffer on stack
  static char buffer[O_BUFLEN];

  tty->print_cr("#");
  tty->print_cr("# java.lang.OutOfMemoryError: %s", _message);
  tty->print_cr("# -XX:OnOutOfMemoryError=\"%s\"", OnOutOfMemoryError);

  // make heap parsability
  Universe::heap()->ensure_parsability(false);  // no need to retire TLABs

  char* cmd;
  const char* ptr = OnOutOfMemoryError;
  while ((cmd = next_OnError_command(buffer, sizeof(buffer), &ptr)) != nullptr){
    tty->print("#   Executing ");
#if defined(LINUX)
    tty->print  ("/bin/sh -c ");
#endif
    tty->print_cr("\"%s\"...", cmd);

    if (os::fork_and_exec(cmd) < 0) {
      tty->print_cr("os::fork_and_exec failed: %s (%s=%d)",
                     os::strerror(errno), os::errno_name(errno), errno);
    }
  }
}

void VMError::report_java_out_of_memory(const char* message) {
  if (OnOutOfMemoryError && OnOutOfMemoryError[0]) {
    MutexLocker ml(Heap_lock);
    VM_ReportJavaOutOfMemory op(message);
    VMThread::execute(&op);
  }
}

void VMError::show_message_box(char *buf, int buflen) {
  bool yes;
  do {
    error_string(buf, buflen);
    yes = os::start_debugging(buf,buflen);
  } while (yes);
}

// Fatal error handling is subject to several timeouts:
// - a global timeout (controlled via ErrorLogTimeout)
// - local error reporting step timeouts.
//
// The latter aims to "give the JVM a kick" if it gets stuck in one particular place during
// error reporting. This prevents one error reporting step from hogging all the time allotted
// to error reporting under ErrorLogTimeout.
//
// VMError::check_timeout() is called from the watcher thread and checks for either global
// or step timeout. If a timeout happened, we interrupt the reporting thread and set either
// _reporting_did_timeout or _step_did_timeout to signal which timeout fired. Function returns
// true if the *global* timeout fired, which will cause WatcherThread to shut down the JVM
// immediately.
bool VMError::check_timeout() {

  // This function is supposed to be called from watcher thread during fatal error handling only.
  assert(VMError::is_error_reported(), "Only call during error handling");
  assert(Thread::current()->is_Watcher_thread(), "Only call from watcher thread");

  if (ErrorLogTimeout == 0) {
    return false;
  }

  // There are three situations where we suppress the *global* error timeout:
  // - if the JVM is embedded and the launcher has its abort hook installed.
  //   That must be allowed to run.
  // - if the user specified one or more OnError commands to run, and these
  //   did not yet run. These must have finished.
  // - if the user (typically developer) specified ShowMessageBoxOnError,
  //   and the error box has not yet been shown
  const bool ignore_global_timeout =
      (ShowMessageBoxOnError
            || (OnError != nullptr && OnError[0] != '\0')
            || Arguments::abort_hook() != nullptr);

  const jlong now = get_current_timestamp();

  // Global timeout hit?
  if (!ignore_global_timeout) {
    const jlong reporting_start_time = get_reporting_start_time();
    // Timestamp is stored in nanos.
    if (reporting_start_time > 0) {
      const jlong end = reporting_start_time + (jlong)ErrorLogTimeout * TIMESTAMP_TO_SECONDS_FACTOR;
      if (end <= now && !_reporting_did_timeout) {
        // We hit ErrorLogTimeout and we haven't interrupted the reporting
        // thread yet.
        _reporting_did_timeout = true;
        interrupt_reporting_thread();
        return true; // global timeout
      }
    }
  }

  // Reporting step timeout?
  const jlong step_start_time = get_step_start_time();
  if (step_start_time > 0) {
    // A step times out after a quarter of the total timeout. Steps are mostly fast unless they
    // hang for some reason, so this simple rule allows for three hanging step and still
    // hopefully leaves time enough for the rest of the steps to finish.
    const int max_step_timeout_secs = 5;
    const jlong timeout_duration = MAX2((jlong)max_step_timeout_secs, (jlong)ErrorLogTimeout * TIMESTAMP_TO_SECONDS_FACTOR / 4);
    const jlong end = step_start_time + timeout_duration;
    if (end <= now && !_step_did_timeout) {
      // The step timed out and we haven't interrupted the reporting
      // thread yet.
      _step_did_timeout = true;
      interrupt_reporting_thread();
      return false; // (Not a global timeout)
    }
  }

  return false;

}

#ifdef ASSERT
typedef void (*voidfun_t)();

// Crash with an authentic sigfpe; behavior is subtly different from a real signal
// compared to one generated with raise (asynchronous vs synchronous). See JDK-8065895.
volatile int sigfpe_int = 0;

ATTRIBUTE_NO_UBSAN
static void ALWAYSINLINE crash_with_sigfpe() {

  // generate a native synchronous SIGFPE where possible;
  sigfpe_int = sigfpe_int/sigfpe_int;

  // if that did not cause a signal (e.g. on ppc), just
  // raise the signal.
#ifndef _WIN32
  // OSX implements raise(sig) incorrectly so we need to
  // explicitly target the current thread
  pthread_kill(pthread_self(), SIGFPE);
#endif

} // end: crash_with_sigfpe

// crash with sigsegv at non-null address.
static void ALWAYSINLINE crash_with_segfault() {

  int* crash_addr = reinterpret_cast<int*>(VMError::segfault_address);
  *crash_addr = 1;

} // end: crash_with_segfault

// crash in a controlled way:
// 1  - assert
// 2  - guarantee
// 14 - SIGSEGV
// 15 - SIGFPE
void VMError::controlled_crash(int how) {

  // Case 14 is tested by test/hotspot/jtreg/runtime/ErrorHandling/SafeFetchInErrorHandlingTest.java.
  // Case 15 is tested by test/hotspot/jtreg/runtime/ErrorHandling/SecondaryErrorTest.java.
  // Case 16 is tested by test/hotspot/jtreg/runtime/ErrorHandling/ThreadsListHandleInErrorHandlingTest.java.
  // Case 17 is tested by test/hotspot/jtreg/runtime/ErrorHandling/NestedThreadsListHandleInErrorHandlingTest.java.

  // We try to grab Threads_lock to keep ThreadsSMRSupport::print_info_on()
  // from racing with Threads::add() or Threads::remove() as we
  // generate the hs_err_pid file. This makes our ErrorHandling tests
  // more stable.
  if (!Threads_lock->owned_by_self()) {
    Threads_lock->try_lock();
    // The VM is going to die so no need to unlock Thread_lock.
  }

  switch (how) {
    case 1: assert(how == 0, "test assert"); break;
    case 2: guarantee(how == 0, "test guarantee"); break;

    // The other cases are unused.
    case 14: crash_with_segfault(); break;
    case 15: crash_with_sigfpe(); break;
    case 16: {
      ThreadsListHandle tlh;
      fatal("Force crash with an active ThreadsListHandle.");
    }
    case 17: {
      ThreadsListHandle tlh;
      {
        ThreadsListHandle tlh2;
        fatal("Force crash with a nested ThreadsListHandle.");
      }
    }
    case 18: {
      // Trigger an error that should cause ASAN to report a double free or use-after-free.
      // Please note that this is not 100% bullet-proof since it assumes that this block
      // is not immediately repurposed by some other thread after free.
      void* const p = os::malloc(4096, mtTest);
      os::free(p);
      os::free(p);
    }
    default:
      // If another number is given, give a generic crash.
      fatal("Crashing with number %d", how);
  }
  tty->print_cr("controlled_crash: survived intentional crash. Did you suppress the assert?");
  ShouldNotReachHere();
}
#endif // !ASSERT

VMErrorCallbackMark::VMErrorCallbackMark(VMErrorCallback* callback)
  : _thread(Thread::current()) {
  callback->_next = _thread->_vm_error_callbacks;
  _thread->_vm_error_callbacks = callback;
}

VMErrorCallbackMark::~VMErrorCallbackMark() {
  assert(_thread->_vm_error_callbacks != nullptr, "Popped too far");
  _thread->_vm_error_callbacks = _thread->_vm_error_callbacks->_next;
}
