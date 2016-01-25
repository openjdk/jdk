/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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

#include <fcntl.h>
#include "precompiled.hpp"
#include "code/codeCache.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/disassembler.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "prims/whitebox.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vm_operations.hpp"
#include "services/memTracker.hpp"
#include "utilities/debug.hpp"
#include "utilities/decoder.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/errorReporter.hpp"
#include "utilities/events.hpp"
#include "utilities/top.hpp"
#include "utilities/vmError.hpp"

// List of environment variables that should be reported in error log file.
const char *env_list[] = {
  // All platforms
  "JAVA_HOME", "JRE_HOME", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH",
  "JAVA_COMPILER", "PATH", "USERNAME",

  // Env variables that are defined on Solaris/Linux/BSD
  "LD_LIBRARY_PATH", "LD_PRELOAD", "SHELL", "DISPLAY",
  "HOSTTYPE", "OSTYPE", "ARCH", "MACHTYPE",

  // defined on Linux
  "LD_ASSUME_KERNEL", "_JAVA_SR_SIGNUM",

  // defined on Darwin
  "DYLD_LIBRARY_PATH", "DYLD_FALLBACK_LIBRARY_PATH",
  "DYLD_FRAMEWORK_PATH", "DYLD_FALLBACK_FRAMEWORK_PATH",
  "DYLD_INSERT_LIBRARIES",

  // defined on Windows
  "OS", "PROCESSOR_IDENTIFIER", "_ALT_JAVA_HOME_DIR",

  (const char *)0
};

// A simple parser for -XX:OnError, usage:
//  ptr = OnError;
//  while ((cmd = next_OnError_command(buffer, sizeof(buffer), &ptr) != NULL)
//     ... ...
static char* next_OnError_command(char* buf, int buflen, const char** ptr) {
  if (ptr == NULL || *ptr == NULL) return NULL;

  const char* cmd = *ptr;

  // skip leading blanks or ';'
  while (*cmd == ' ' || *cmd == ';') cmd++;

  if (*cmd == '\0') return NULL;

  const char * cmdend = cmd;
  while (*cmdend != '\0' && *cmdend != ';') cmdend++;

  Arguments::copy_expand_pid(cmd, cmdend - cmd, buf, buflen);

  *ptr = (*cmdend == '\0' ? cmdend : cmdend + 1);
  return buf;
}

static void print_bug_submit_message(outputStream *out, Thread *thread) {
  if (out == NULL) return;
  out->print_raw_cr("# If you would like to submit a bug report, please visit:");
  out->print_raw   ("#   ");
  out->print_raw_cr(Arguments::java_vendor_url_bug());
  // If the crash is in native code, encourage user to submit a bug to the
  // provider of that code.
  if (thread && thread->is_Java_thread() &&
      !thread->is_hidden_from_external_view()) {
    JavaThread* jt = (JavaThread*)thread;
    if (jt->thread_state() == _thread_in_native) {
      out->print_cr("# The crash happened outside the Java Virtual Machine in native code.\n# See problematic frame for where to report the bug.");
    }
  }
  out->print_raw_cr("#");
}

bool VMError::coredump_status;
char VMError::coredump_message[O_BUFLEN];

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
                 "%s (0x%x) at pc=" PTR_FORMAT ", pid=%d, tid=" UINTX_FORMAT,
                 signame, _id, _pc,
                 os::current_process_id(), os::current_thread_id());
  } else if (_filename != NULL && _lineno > 0) {
    // skip directory names
    char separator = os::file_separator()[0];
    const char *p = strrchr(_filename, separator);
    int n = jio_snprintf(buf, buflen,
                         "Internal Error at %s:%d, pid=%d, tid=" UINTX_FORMAT,
                         p ? p + 1 : _filename, _lineno,
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
                 "Internal Error (0x%x), pid=%d, tid=" UINTX_FORMAT,
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

    // If the top frame is a Shark frame and the frame anchor isn't
    // set up then it's possible that the information in the frame
    // is garbage: it could be from a previous decache, or it could
    // simply have never been written.  So we print a warning...
    StackFrameStream sfs(jt);
    if (!has_last_Java_frame && !sfs.is_done()) {
      if (sfs.current()->zeroframe()->is_shark_frame()) {
        st->print(" (TOP FRAME MAY BE JUNK)");
      }
    }
    st->cr();

    // Print the frames
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
    for(StackFrameStream sfs(jt); !sfs.is_done(); sfs.next()) {
      sfs.current()->print_on_error(st, buf, buflen, verbose);
      st->cr();
    }
  }
#endif // ZERO
}

static void print_oom_reasons(outputStream* st) {
  st->print_cr("# Possible reasons:");
  st->print_cr("#   The system is out of physical RAM or swap space");
  st->print_cr("#   In 32 bit mode, the process size limit was hit");
  st->print_cr("# Possible solutions:");
  st->print_cr("#   Reduce memory load on the system");
  st->print_cr("#   Increase physical memory or swap space");
  st->print_cr("#   Check if swap backing store is full");
  st->print_cr("#   Use 64 bit Java on a 64 bit OS");
  st->print_cr("#   Decrease Java heap size (-Xmx/-Xms)");
  st->print_cr("#   Decrease number of Java threads");
  st->print_cr("#   Decrease Java thread stack sizes (-Xss)");
  st->print_cr("#   Set larger code cache with -XX:ReservedCodeCacheSize=");
  st->print_cr("# This output file may be truncated or incomplete.");
}

static const char* gc_mode() {
  if (UseG1GC)            return "g1 gc";
  if (UseParallelGC)      return "parallel gc";
  if (UseConcMarkSweepGC) return "concurrent mark sweep gc";
  if (UseSerialGC)        return "serial gc";
  return "ERROR in GC mode";
}

static void report_vm_version(outputStream* st, char* buf, int buflen) {
   // VM version
   st->print_cr("#");
   JDK_Version::current().to_string(buf, buflen);
   const char* runtime_name = JDK_Version::runtime_name() != NULL ?
                                JDK_Version::runtime_name() : "";
   const char* runtime_version = JDK_Version::runtime_version() != NULL ?
                                   JDK_Version::runtime_version() : "";
   const char* jdk_debug_level = Abstract_VM_Version::printable_jdk_debug_level() != NULL ?
                                   Abstract_VM_Version::printable_jdk_debug_level() : "";

   st->print_cr("# JRE version: %s (%s) (%sbuild %s)", runtime_name, buf,
                 jdk_debug_level, runtime_version);

   // This is the long version with some default settings added
   st->print_cr("# Java VM: %s (%s%s, %s%s%s%s%s, %s, %s)",
                 Abstract_VM_Version::vm_name(),
                 jdk_debug_level,
                 Abstract_VM_Version::vm_release(),
                 Abstract_VM_Version::vm_info_string(),
                 TieredCompilation ? ", tiered" : "",
#if INCLUDE_JVMCI
                 EnableJVMCI ? ", jvmci" : "",
                 UseJVMCICompiler ? ", jvmci compiler" : "",
#else
                 "", "",
#endif
                 UseCompressedOops ? ", compressed oops" : "",
                 gc_mode(),
                 Abstract_VM_Version::vm_platform_string()
               );
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

int          VMError::_current_step;
const char*  VMError::_current_step_info;

void VMError::report(outputStream* st, bool _verbose) {

# define BEGIN if (_current_step == 0) { _current_step = 1;
# define STEP(n, s) } if (_current_step < n) { _current_step = n; _current_step_info = s;
# define END }

  // don't allocate large buffer on stack
  static char buf[O_BUFLEN];

  BEGIN

  STEP(10, "(printing fatal error message)")

    st->print_cr("#");
    if (should_report_bug(_id)) {
      st->print_cr("# A fatal error has been detected by the Java Runtime Environment:");
    } else {
      st->print_cr("# There is insufficient memory for the Java "
                   "Runtime Environment to continue.");
    }

#ifndef PRODUCT
  // Error handler self tests

  // test secondary error handling. Test it twice, to test that resetting
  // error handler after a secondary crash works.
  STEP(20, "(test secondary crash 1)")
    if (_verbose && TestCrashInErrorHandler != 0) {
      st->print_cr("Will crash now (TestCrashInErrorHandler=" UINTX_FORMAT ")...",
        TestCrashInErrorHandler);
      controlled_crash(TestCrashInErrorHandler);
    }

  STEP(30, "(test secondary crash 2)")
    if (_verbose && TestCrashInErrorHandler != 0) {
      st->print_cr("Will crash now (TestCrashInErrorHandler=" UINTX_FORMAT ")...",
        TestCrashInErrorHandler);
      controlled_crash(TestCrashInErrorHandler);
    }

  STEP(40, "(test safefetch in error handler)")
    // test whether it is safe to use SafeFetch32 in Crash Handler. Test twice
    // to test that resetting the signal handler works correctly.
    if (_verbose && TestSafeFetchInErrorHandler) {
      st->print_cr("Will test SafeFetch...");
      if (CanUseSafeFetch32()) {
        int* const invalid_pointer = (int*) get_segfault_address();
        const int x = 0x76543210;
        int i1 = SafeFetch32(invalid_pointer, x);
        int i2 = SafeFetch32(invalid_pointer, x);
        if (i1 == x && i2 == x) {
          st->print_cr("SafeFetch OK."); // Correctly deflected and returned default pattern
        } else {
          st->print_cr("??");
        }
      } else {
        st->print_cr("not possible; skipped.");
      }
    }
#endif // PRODUCT

  STEP(50, "(printing type of error)")

     switch(_id) {
       case OOM_MALLOC_ERROR:
       case OOM_MMAP_ERROR:
         if (_size) {
           st->print("# Native memory allocation ");
           st->print((_id == (int)OOM_MALLOC_ERROR) ? "(malloc) failed to allocate " :
                                                 "(mmap) failed to map ");
           jio_snprintf(buf, sizeof(buf), SIZE_FORMAT, _size);
           st->print("%s", buf);
           st->print(" bytes");
           if (strlen(_detail_msg) > 0) {
             st->print(" for ");
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

  STEP(60, "(printing exception/signal name)")

     st->print_cr("#");
     st->print("#  ");
     // Is it an OS exception/signal?
     if (os::exception_name(_id, buf, sizeof(buf))) {
       st->print("%s", buf);
       st->print(" (0x%x)", _id);                // signal number
       st->print(" at pc=" PTR_FORMAT, p2i(_pc));
     } else {
       if (should_report_bug(_id)) {
         st->print("Internal Error");
       } else {
         st->print("Out of Memory Error");
       }
       if (_filename != NULL && _lineno > 0) {
#ifdef PRODUCT
         // In product mode chop off pathname?
         char separator = os::file_separator()[0];
         const char *p = strrchr(_filename, separator);
         const char *file = p ? p+1 : _filename;
#else
         const char *file = _filename;
#endif
         st->print(" (%s:%d)", file, _lineno);
       } else {
         st->print(" (0x%x)", _id);
       }
     }

  STEP(70, "(printing current thread and pid)")

     // process id, thread id
     st->print(", pid=%d", os::current_process_id());
     st->print(", tid=" UINTX_FORMAT, os::current_thread_id());
     st->cr();

  STEP(80, "(printing error message)")

     if (should_report_bug(_id)) {  // already printed the message.
       // error message
       if (strlen(_detail_msg) > 0) {
         st->print_cr("#  %s: %s", _message ? _message : "Error", _detail_msg);
       } else if (_message) {
         st->print_cr("#  Error: %s", _message);
       }
     }

  STEP(90, "(printing Java version string)")

     report_vm_version(st, buf, sizeof(buf));

  STEP(100, "(printing problematic frame)")

     // Print current frame if we have a context (i.e. it's a crash)
     if (_context) {
       st->print_cr("# Problematic frame:");
       st->print("# ");
       frame fr = os::fetch_frame_from_context(_context);
       fr.print_on_error(st, buf, sizeof(buf));
       st->cr();
       st->print_cr("#");
     }

  STEP(110, "(printing core file information)")
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

  STEP(120, "(printing bug submit message)")

     if (should_report_bug(_id) && _verbose) {
       print_bug_submit_message(st, _thread);
     }

  STEP(130, "(printing summary)" )

     if (_verbose) {
       st->cr();
       st->print_cr("---------------  S U M M A R Y ------------");
       st->cr();
     }

  STEP(140, "(printing VM option summary)" )

     if (_verbose) {
       // VM options
       Arguments::print_summary_on(st);
       st->cr();
     }

  STEP(150, "(printing summary machine and OS info)")

     if (_verbose) {
       os::print_summary_info(st, buf, sizeof(buf));
     }


  STEP(160, "(printing date and time)" )

     if (_verbose) {
       os::print_date_and_time(st, buf, sizeof(buf));
     }

  STEP(170, "(printing thread)" )

     if (_verbose) {
       st->cr();
       st->print_cr("---------------  T H R E A D  ---------------");
       st->cr();
     }

  STEP(180, "(printing current thread)" )

     // current thread
     if (_verbose) {
       if (_thread) {
         st->print("Current thread (" PTR_FORMAT "):  ", p2i(_thread));
         _thread->print_on_error(st, buf, sizeof(buf));
         st->cr();
       } else {
         st->print_cr("Current thread is native thread");
       }
       st->cr();
     }

  STEP(190, "(printing current compile task)" )

     if (_verbose && _thread && _thread->is_Compiler_thread()) {
        CompilerThread* t = (CompilerThread*)_thread;
        if (t->task()) {
           st->cr();
           st->print_cr("Current CompileTask:");
           t->task()->print_line_on_error(st, buf, sizeof(buf));
           st->cr();
        }
     }


  STEP(200, "(printing stack bounds)" )

     if (_verbose) {
       st->print("Stack: ");

       address stack_top;
       size_t stack_size;

       if (_thread) {
          stack_top = _thread->stack_base();
          stack_size = _thread->stack_size();
       } else {
          stack_top = os::current_stack_base();
          stack_size = os::current_stack_size();
       }

       address stack_bottom = stack_top - stack_size;
       st->print("[" PTR_FORMAT "," PTR_FORMAT "]", p2i(stack_bottom), p2i(stack_top));

       frame fr = _context ? os::fetch_frame_from_context(_context)
                           : os::current_frame();

       if (fr.sp()) {
         st->print(",  sp=" PTR_FORMAT, p2i(fr.sp()));
         size_t free_stack_size = pointer_delta(fr.sp(), stack_bottom, 1024);
         st->print(",  free space=" SIZE_FORMAT "k", free_stack_size);
       }

       st->cr();
     }

  STEP(210, "(printing native stack)" )

   if (_verbose) {
     if (os::platform_print_native_stack(st, _context, buf, sizeof(buf))) {
       // We have printed the native stack in platform-specific code
       // Windows/x64 needs special handling.
     } else {
       frame fr = _context ? os::fetch_frame_from_context(_context)
                           : os::current_frame();

       print_native_stack(st, fr, _thread, buf, sizeof(buf));
     }
   }

  STEP(220, "(printing Java stack)" )

     if (_verbose && _thread && _thread->is_Java_thread()) {
       print_stack_trace(st, (JavaThread*)_thread, buf, sizeof(buf));
     }

  STEP(230, "(printing target Java thread stack)" )

     // printing Java thread stack trace if it is involved in GC crash
     if (_verbose && _thread && (_thread->is_Named_thread())) {
       JavaThread*  jt = ((NamedThread *)_thread)->processed_thread();
       if (jt != NULL) {
         st->print_cr("JavaThread " PTR_FORMAT " (nid = %d) was being processed", p2i(jt), jt->osthread()->thread_id());
         print_stack_trace(st, jt, buf, sizeof(buf), true);
       }
     }

  STEP(240, "(printing siginfo)" )

     // signal no, signal code, address that caused the fault
     if (_verbose && _siginfo) {
       st->cr();
       os::print_siginfo(st, _siginfo);
       st->cr();
     }

  STEP(245, "(CDS archive access warning)" )

     // Print an explicit hint if we crashed on access to the CDS archive.
     if (_verbose && _siginfo) {
       check_failing_cds_access(st, _siginfo);
       st->cr();
     }

  STEP(250, "(printing register info)")

     // decode register contents if possible
     if (_verbose && _context && Universe::is_fully_initialized()) {
       os::print_register_info(st, _context);
       st->cr();
     }

  STEP(260, "(printing registers, top of stack, instructions near pc)")

     // registers, top of stack, instructions near pc
     if (_verbose && _context) {
       os::print_context(st, _context);
       st->cr();
     }

  STEP(265, "(printing code blob if possible)")

     if (_verbose && _context) {
       CodeBlob* cb = CodeCache::find_blob(_pc);
       if (cb != NULL) {
         if (Interpreter::contains(_pc)) {
           // The interpreter CodeBlob is very large so try to print the codelet instead.
           InterpreterCodelet* codelet = Interpreter::codelet_containing(_pc);
           if (codelet != NULL) {
             codelet->print_on(st);
             Disassembler::decode(codelet->code_begin(), codelet->code_end(), st);
           }
         } else {
           StubCodeDesc* desc = StubCodeDesc::desc_for(_pc);
           if (desc != NULL) {
             desc->print_on(st);
             Disassembler::decode(desc->begin(), desc->end(), st);
           } else {
             Disassembler::decode(cb, st);
             st->cr();
           }
         }
       }
     }

  STEP(270, "(printing VM operation)" )

     if (_verbose && _thread && _thread->is_VM_thread()) {
        VMThread* t = (VMThread*)_thread;
        VM_Operation* op = t->vm_operation();
        if (op) {
          op->print_on_error(st);
          st->cr();
          st->cr();
        }
     }

  STEP(280, "(printing process)" )

     if (_verbose) {
       st->cr();
       st->print_cr("---------------  P R O C E S S  ---------------");
       st->cr();
     }

  STEP(290, "(printing all threads)" )

     // all threads
     if (_verbose && _thread) {
       Threads::print_on_error(st, _thread, buf, sizeof(buf));
       st->cr();
     }

  STEP(300, "(printing VM state)" )

     if (_verbose) {
       // Safepoint state
       st->print("VM state:");

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
     }

  STEP(310, "(printing owned locks on error)" )

     // mutexes/monitors that currently have an owner
     if (_verbose) {
       print_owned_locks_on_error(st);
       st->cr();
     }

  STEP(320, "(printing number of OutOfMemoryError and StackOverflow exceptions)")

     if (_verbose && Exceptions::has_exception_counts()) {
       st->print_cr("OutOfMemory and StackOverflow Exception counts:");
       Exceptions::print_exception_counts_on_error(st);
       st->cr();
     }

  STEP(330, "(printing compressed oops mode")

     if (_verbose && UseCompressedOops) {
       Universe::print_compressed_oops_mode(st);
       if (UseCompressedClassPointers) {
         Metaspace::print_compressed_class_space(st);
       }
       st->cr();
     }

  STEP(340, "(printing heap information)" )

     if (_verbose && Universe::is_fully_initialized()) {
       Universe::heap()->print_on_error(st);
       st->cr();
       st->print_cr("Polling page: " INTPTR_FORMAT, p2i(os::get_polling_page()));
       st->cr();
     }

  STEP(350, "(printing code cache information)" )

     if (_verbose && Universe::is_fully_initialized()) {
       // print code cache information before vm abort
       CodeCache::print_summary(st);
       st->cr();
     }

  STEP(360, "(printing ring buffers)" )

     if (_verbose) {
       Events::print_all(st);
       st->cr();
     }

  STEP(370, "(printing dynamic libraries)" )

     if (_verbose) {
       // dynamic libraries, or memory map
       os::print_dll_info(st);
       st->cr();
     }

  STEP(380, "(printing VM options)" )

     if (_verbose) {
       // VM options
       Arguments::print_on(st);
       st->cr();
     }

  STEP(390, "(printing warning if internal testing API used)" )

     if (WhiteBox::used()) {
       st->print_cr("Unsupported internal testing APIs have been used.");
       st->cr();
     }

  STEP(400, "(printing all environment variables)" )

     if (_verbose) {
       os::print_environment_variables(st, env_list);
       st->cr();
     }

  STEP(410, "(printing signal handlers)" )

     if (_verbose) {
       os::print_signal_handlers(st, buf, sizeof(buf));
       st->cr();
     }

  STEP(420, "(Native Memory Tracking)" )
     if (_verbose) {
       MemTracker::error_report(st);
     }

  STEP(430, "(printing system)" )

     if (_verbose) {
       st->cr();
       st->print_cr("---------------  S Y S T E M  ---------------");
       st->cr();
     }

  STEP(440, "(printing OS information)" )

     if (_verbose) {
       os::print_os_info(st);
       st->cr();
     }

  STEP(450, "(printing CPU info)" )
     if (_verbose) {
       os::print_cpu_info(st, buf, sizeof(buf));
       st->cr();
     }

  STEP(460, "(printing memory info)" )

     if (_verbose) {
       os::print_memory_info(st);
       st->cr();
     }

  STEP(470, "(printing internal vm info)" )

     if (_verbose) {
       st->print_cr("vm_info: %s", Abstract_VM_Version::internal_vm_info_string());
       st->cr();
     }

  // print a defined marker to show that error handling finished correctly.
  STEP(480, "(printing end marker)" )

     if (_verbose) {
       st->print_cr("END.");
     }

  END

# undef BEGIN
# undef STEP
# undef END
}

// Report for the vm_info_cmd. This prints out the information above omitting
// crash and thread specific information.  If output is added above, it should be added
// here also, if it is safe to call during a running process.
void VMError::print_vm_info(outputStream* st) {

  char buf[O_BUFLEN];
  report_vm_version(st, buf, sizeof(buf));

  // STEP("(printing summary)")

  st->cr();
  st->print_cr("---------------  S U M M A R Y ------------");
  st->cr();

  // STEP("(printing VM option summary)")

  // VM options
  Arguments::print_summary_on(st);
  st->cr();

  // STEP("(printing summary machine and OS info)")

  os::print_summary_info(st, buf, sizeof(buf));

  // STEP("(printing date and time)")

  os::print_date_and_time(st, buf, sizeof(buf));

  // Skip: STEP("(printing thread)")

  // STEP("(printing process)")

  st->cr();
  st->print_cr("---------------  P R O C E S S  ---------------");
  st->cr();

  // STEP("(printing number of OutOfMemoryError and StackOverflow exceptions)")

  if (Exceptions::has_exception_counts()) {
    st->print_cr("OutOfMemory and StackOverflow Exception counts:");
    Exceptions::print_exception_counts_on_error(st);
    st->cr();
  }

  // STEP("(printing compressed oops mode")

  if (UseCompressedOops) {
    Universe::print_compressed_oops_mode(st);
    if (UseCompressedClassPointers) {
      Metaspace::print_compressed_class_space(st);
    }
    st->cr();
  }

  // STEP("(printing heap information)")

  if (Universe::is_fully_initialized()) {
    Universe::heap()->print_on_error(st);
    st->cr();
    st->print_cr("Polling page: " INTPTR_FORMAT, p2i(os::get_polling_page()));
    st->cr();
  }

  // STEP("(printing code cache information)")

  if (Universe::is_fully_initialized()) {
    // print code cache information before vm abort
    CodeCache::print_summary(st);
    st->cr();
  }

  // STEP("(printing ring buffers)")

  Events::print_all(st);
  st->cr();

  // STEP("(printing dynamic libraries)")

  // dynamic libraries, or memory map
  os::print_dll_info(st);
  st->cr();

  // STEP("(printing VM options)")

  // VM options
  Arguments::print_on(st);
  st->cr();

  // STEP("(printing warning if internal testing API used)")

  if (WhiteBox::used()) {
    st->print_cr("Unsupported internal testing APIs have been used.");
    st->cr();
  }

  // STEP("(printing all environment variables)")

  os::print_environment_variables(st, env_list);
  st->cr();

  // STEP("(printing signal handlers)")

  os::print_signal_handlers(st, buf, sizeof(buf));
  st->cr();

  // STEP("(Native Memory Tracking)")

  MemTracker::error_report(st);

  // STEP("(printing system)")

  st->cr();
  st->print_cr("---------------  S Y S T E M  ---------------");
  st->cr();

  // STEP("(printing OS information)")

  os::print_os_info(st);
  st->cr();

  // STEP("(printing CPU info)")

  os::print_cpu_info(st, buf, sizeof(buf));
  st->cr();

  // STEP("(printing memory info)")

  os::print_memory_info(st);
  st->cr();

  // STEP("(printing internal vm info)")

  st->print_cr("vm_info: %s", Abstract_VM_Version::internal_vm_info_string());
  st->cr();

  // print a defined marker to show that error handling finished correctly.
  // STEP("(printing end marker)")

  st->print_cr("END.");
}

volatile intptr_t VMError::first_error_tid = -1;

// An error could happen before tty is initialized or after it has been
// destroyed. Here we use a very simple unbuffered fdStream for printing.
// Only out.print_raw() and out.print_raw_cr() should be used, as other
// printing methods need to allocate large buffer on stack. To format a
// string, use jio_snprintf() with a static buffer or use staticBufferStream.
fdStream VMError::out(defaultStream::output_fd());
fdStream VMError::log; // error log used by VMError::report_and_die()

/** Expand a pattern into a buffer starting at pos and open a file using constructed path */
static int expand_and_open(const char* pattern, char* buf, size_t buflen, size_t pos) {
  int fd = -1;
  if (Arguments::copy_expand_pid(pattern, strlen(pattern), &buf[pos], buflen - pos)) {
    // the O_EXCL flag will cause the open to fail if the file exists
    fd = open(buf, O_RDWR | O_CREAT | O_EXCL, 0666);
  }
  return fd;
}

/**
 * Construct file name for a log file and return it's file descriptor.
 * Name and location depends on pattern, default_pattern params and access
 * permissions.
 */
static int prepare_log_file(const char* pattern, const char* default_pattern, char* buf, size_t buflen) {
  int fd = -1;

  // If possible, use specified pattern to construct log file name
  if (pattern != NULL) {
    fd = expand_and_open(pattern, buf, buflen, 0);
  }

  // Either user didn't specify, or the user's location failed,
  // so use the default name in the current directory
  if (fd == -1) {
    const char* cwd = os::get_current_directory(buf, buflen);
    if (cwd != NULL) {
      size_t pos = strlen(cwd);
      int fsep_len = jio_snprintf(&buf[pos], buflen-pos, "%s", os::file_separator());
      pos += fsep_len;
      if (fsep_len > 0) {
        fd = expand_and_open(default_pattern, buf, buflen, pos);
      }
    }
  }

   // try temp directory if it exists.
   if (fd == -1) {
     const char* tmpdir = os::get_temp_directory();
     if (tmpdir != NULL && strlen(tmpdir) > 0) {
       int pos = jio_snprintf(buf, buflen, "%s%s", tmpdir, os::file_separator());
       if (pos > 0) {
         fd = expand_and_open(default_pattern, buf, buflen, pos);
       }
     }
   }

  return fd;
}

int         VMError::_id;
const char* VMError::_message;
char        VMError::_detail_msg[1024];
Thread*     VMError::_thread;
address     VMError::_pc;
void*       VMError::_siginfo;
void*       VMError::_context;
const char* VMError::_filename;
int         VMError::_lineno;
size_t      VMError::_size;

void VMError::report_and_die(Thread* thread, unsigned int sig, address pc, void* siginfo,
                             void* context, const char* detail_fmt, ...)
{
  va_list detail_args;
  va_start(detail_args, detail_fmt);
  report_and_die(sig, NULL, detail_fmt, detail_args, thread, pc, siginfo, context, NULL, 0, 0);
  va_end(detail_args);
}

void VMError::report_and_die(Thread* thread, unsigned int sig, address pc, void* siginfo, void* context)
{
  report_and_die(thread, sig, pc, siginfo, context, "%s", "");
}

void VMError::report_and_die(const char* message, const char* detail_fmt, ...)
{
  va_list detail_args;
  va_start(detail_args, detail_fmt);
  report_and_die(INTERNAL_ERROR, message, detail_fmt, detail_args, NULL, NULL, NULL, NULL, NULL, 0, 0);
  va_end(detail_args);
}

void VMError::report_and_die(const char* message)
{
  report_and_die(message, "%s", "");
}

void VMError::report_and_die(Thread* thread, const char* filename, int lineno, const char* message,
                             const char* detail_fmt, va_list detail_args)
{
  report_and_die(INTERNAL_ERROR, message, detail_fmt, detail_args, thread, NULL, NULL, NULL, filename, lineno, 0);
}

void VMError::report_and_die(Thread* thread, const char* filename, int lineno, size_t size,
                             VMErrorType vm_err_type, const char* detail_fmt, va_list detail_args) {
  report_and_die(vm_err_type, NULL, detail_fmt, detail_args, thread, NULL, NULL, NULL, filename, lineno, size);
}

void VMError::report_and_die(int id, const char* message, const char* detail_fmt, va_list detail_args,
                             Thread* thread, address pc, void* siginfo, void* context, const char* filename,
                             int lineno, size_t size)
{
  // Don't allocate large buffer on stack
  static char buffer[O_BUFLEN];

  // How many errors occurred in error handler when reporting first_error.
  static int recursive_error_count;

  // We will first print a brief message to standard out (verbose = false),
  // then save detailed information in log file (verbose = true).
  static bool out_done = false;         // done printing to standard out
  static bool log_done = false;         // done saving error log
  static bool transmit_report_done = false; // done error reporting

  if (SuppressFatalErrorMessage) {
      os::abort(CreateCoredumpOnCrash);
  }
  intptr_t mytid = os::current_thread_id();
  if (first_error_tid == -1 &&
      Atomic::cmpxchg_ptr(mytid, &first_error_tid, -1) == -1) {

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

    // first time
    set_error_reported();

    if (ShowMessageBoxOnError || PauseAtExit) {
      show_message_box(buffer, sizeof(buffer));

      // User has asked JVM to abort. Reset ShowMessageBoxOnError so the
      // WatcherThread can kill JVM if the error handler hangs.
      ShowMessageBoxOnError = false;
    }

    os::check_dump_limit(buffer, sizeof(buffer));

    // reset signal handlers or exception filter; make sure recursive crashes
    // are handled properly.
    reset_signal_handlers();

  } else {
    // If UseOsErrorReporting we call this for each level of the call stack
    // while searching for the exception handler.  Only the first level needs
    // to be reported.
    if (UseOSErrorReporting && log_done) return;

    // This is not the first error, see if it happened in a different thread
    // or in the same thread during error reporting.
    if (first_error_tid != mytid) {
      char msgbuf[64];
      jio_snprintf(msgbuf, sizeof(msgbuf),
                   "[thread " INTX_FORMAT " also had an error]",
                   mytid);
      out.print_raw_cr(msgbuf);

      // error reporting is not MT-safe, block current thread
      os::infinite_sleep();

    } else {
      if (recursive_error_count++ > 30) {
        out.print_raw_cr("[Too many errors, abort]");
        os::die();
      }

      jio_snprintf(buffer, sizeof(buffer),
                   "[error occurred during error reporting %s, id 0x%x]",
                   _current_step_info, _id);
      if (log.is_open()) {
        log.cr();
        log.print_raw_cr(buffer);
        log.cr();
      } else {
        out.cr();
        out.print_raw_cr(buffer);
        out.cr();
      }
    }
  }

  // print to screen
  if (!out_done) {
    staticBufferStream sbs(buffer, sizeof(buffer), &out);
    report(&sbs, false);

    out_done = true;

    _current_step = 0;
    _current_step_info = "";
  }

  // print to error log file
  if (!log_done) {
    // see if log file is already open
    if (!log.is_open()) {
      // open log file
      int fd = prepare_log_file(ErrorFile, "hs_err_pid%p.log", buffer, sizeof(buffer));
      if (fd != -1) {
        out.print_raw("# An error report file with more information is saved as:\n# ");
        out.print_raw_cr(buffer);

        log.set_fd(fd);
      } else {
        out.print_raw_cr("# Can not save log file, dump to screen..");
        log.set_fd(defaultStream::output_fd());
        /* Error reporting currently needs dumpfile.
         * Maybe implement direct streaming in the future.*/
        transmit_report_done = true;
      }
    }

    staticBufferStream sbs(buffer, O_BUFLEN, &log);
    report(&sbs, true);
    _current_step = 0;
    _current_step_info = "";

    // Run error reporting to determine whether or not to report the crash.
    if (!transmit_report_done && should_report_bug(_id)) {
      transmit_report_done = true;
      const int fd2 = ::dup(log.fd());
      FILE* const hs_err = ::fdopen(fd2, "r");
      if (NULL != hs_err) {
        ErrorReporter er;
        er.call(hs_err, buffer, O_BUFLEN);
      }
      ::fclose(hs_err);
    }

    if (log.fd() != defaultStream::output_fd()) {
      close(log.fd());
    }

    log.set_fd(-1);
    log_done = true;
  }


  static bool skip_OnError = false;
  if (!skip_OnError && OnError && OnError[0]) {
    skip_OnError = true;

    out.print_raw_cr("#");
    out.print_raw   ("# -XX:OnError=\"");
    out.print_raw   (OnError);
    out.print_raw_cr("\"");

    char* cmd;
    const char* ptr = OnError;
    while ((cmd = next_OnError_command(buffer, sizeof(buffer), &ptr)) != NULL){
      out.print_raw   ("#   Executing ");
#if defined(LINUX) || defined(_ALLBSD_SOURCE)
      out.print_raw   ("/bin/sh -c ");
#elif defined(SOLARIS)
      out.print_raw   ("/usr/bin/sh -c ");
#endif
      out.print_raw   ("\"");
      out.print_raw   (cmd);
      out.print_raw_cr("\" ...");

      if (os::fork_and_exec(cmd) < 0) {
        out.print_cr("os::fork_and_exec failed: %s (%d)", strerror(errno), errno);
      }
    }

    // done with OnError
    OnError = NULL;
  }

  static bool skip_replay = ReplayCompiles; // Do not overwrite file during replay
  if (DumpReplayDataOnError && _thread && _thread->is_Compiler_thread() && !skip_replay) {
    skip_replay = true;
    ciEnv* env = ciEnv::current();
    if (env != NULL) {
      int fd = prepare_log_file(ReplayDataFile, "replay_pid%p.log", buffer, sizeof(buffer));
      if (fd != -1) {
        FILE* replay_data_file = os::open(fd, "w");
        if (replay_data_file != NULL) {
          fileStream replay_data_stream(replay_data_file, /*need_close=*/true);
          env->dump_replay_data_unsafe(&replay_data_stream);
          out.print_raw("#\n# Compiler replay data is saved as:\n# ");
          out.print_raw_cr(buffer);
        } else {
          out.print_raw("#\n# Can't open file to dump replay data. Error: ");
          out.print_raw_cr(strerror(os::get_last_error()));
        }
      }
    }
  }

  static bool skip_bug_url = !should_report_bug(_id);
  if (!skip_bug_url) {
    skip_bug_url = true;

    out.print_raw_cr("#");
    print_bug_submit_message(&out, _thread);
  }

  if (!UseOSErrorReporting) {
    // os::abort() will call abort hooks, try it first.
    static bool skip_os_abort = false;
    if (!skip_os_abort) {
      skip_os_abort = true;
      bool dump_core = should_report_bug(_id);
      os::abort(dump_core && CreateCoredumpOnCrash, _siginfo, _context);
    }

    // if os::abort() doesn't abort, try os::die();
    os::die();
  }
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
  while ((cmd = next_OnError_command(buffer, sizeof(buffer), &ptr)) != NULL){
    tty->print("#   Executing ");
#if defined(LINUX)
    tty->print  ("/bin/sh -c ");
#elif defined(SOLARIS)
    tty->print  ("/usr/bin/sh -c ");
#endif
    tty->print_cr("\"%s\"...", cmd);

    if (os::fork_and_exec(cmd) < 0) {
      tty->print_cr("os::fork_and_exec failed: %s (%d)", strerror(errno), errno);
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
