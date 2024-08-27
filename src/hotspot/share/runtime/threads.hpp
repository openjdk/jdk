/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Azul Systems, Inc. All rights reserved.
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

#ifndef SHARE_RUNTIME_THREADS_HPP
#define SHARE_RUNTIME_THREADS_HPP

#include "jni.h"
#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class JavaThread;
class Metadata;
class MetadataClosure;
class OopClosure;
class Thread;
class ThreadClosure;
class ThreadsList;
class outputStream;

// The active thread queue. It also keeps track of the current used
// thread priorities.
class Threads: AllStatic {
  friend class VMStructs;
 private:
  static int         _number_of_threads;
  static int         _number_of_non_daemon_threads;
  static int         _return_code;
  static uintx       _thread_claim_token;
#ifdef ASSERT
  static bool        _vm_complete;
#endif

  static void initialize_java_lang_classes(JavaThread* main_thread, TRAPS);
  static void initialize_jsr292_core_classes(TRAPS);

 public:
  // Thread management
  // force_daemon is a concession to JNI, where we may need to add a
  // thread to the thread list before allocating its thread object
  static void add(JavaThread* p, bool force_daemon = false);
  static void remove(JavaThread* p, bool is_daemon);
  static void non_java_threads_do(ThreadClosure* tc);
  static void java_threads_do(ThreadClosure* tc);
  static void threads_do(ThreadClosure* tc);
  static void possibly_parallel_threads_do(bool is_par, ThreadClosure* tc);

  // Initializes the vm and creates the vm thread
  static jint create_vm(JavaVMInitArgs* args, bool* canTryAgain);
  static void convert_vm_init_libraries_to_agents();
  static void create_vm_init_libraries();
  static void create_vm_init_agents();
  static void shutdown_vm_agents();
  static void destroy_vm();
  // Supported VM versions via JNI
  // Includes JNI_VERSION_1_1
  static jboolean is_supported_jni_version_including_1_1(jint version);
  // Does not include JNI_VERSION_1_1
  static jboolean is_supported_jni_version(jint version);

private:
  // The "thread claim token" provides a way for threads to be claimed
  // by parallel worker tasks.
  //
  // Each thread contains a "token" field. A task will claim the
  // thread only if its token is different from the global token,
  // which is updated by calling change_thread_claim_token().  When
  // a thread is claimed, it's token is set to the global token value
  // so other threads in the same iteration pass won't claim it.
  //
  // For this to work change_thread_claim_token() needs to be called
  // exactly once in sequential code before starting parallel tasks
  // that should claim threads.
  //
  // New threads get their token set to 0 and change_thread_claim_token()
  // never sets the global token to 0.
  static uintx thread_claim_token() { return _thread_claim_token; }

public:
  static void change_thread_claim_token();
  static void assert_all_threads_claimed() NOT_DEBUG_RETURN;

  // Apply "f->do_oop" to all root oops in all threads.
  // This version may only be called by sequential code.
  static void oops_do(OopClosure* f, NMethodClosure* cf);
  // This version may be called by sequential or parallel code.
  static void possibly_parallel_oops_do(bool is_par, OopClosure* f, NMethodClosure* cf);

  // RedefineClasses support
  static void metadata_do(MetadataClosure* f);
  static void metadata_handles_do(void f(Metadata*));

#ifdef ASSERT
  static bool is_vm_complete() { return _vm_complete; }
#endif // ASSERT

  // Verification
  static void verify();
  static void print_on(outputStream* st, bool print_stacks, bool internal_format, bool print_concurrent_locks, bool print_extended_info);
  static void print(bool print_stacks, bool internal_format) {
    // this function is only used by debug.cpp
    print_on(tty, print_stacks, internal_format, false /* no concurrent lock printed */, false /* simple format */);
  }
  static void print_on_error(outputStream* st, Thread* current, char* buf, int buflen);
  static void print_on_error(Thread* this_thread, outputStream* st, Thread* current, char* buf,
                             int buflen, bool* found_current);
  // Print threads busy compiling, and returns the number of printed threads.
  static unsigned print_threads_compiling(outputStream* st, char* buf, int buflen, bool short_form = false);

  // Get Java threads that are waiting to enter or re-enter the specified monitor.
  // Java threads that are executing mounted virtual threads are not included.
  static GrowableArray<JavaThread*>* get_pending_threads(ThreadsList * t_list,
                                                         int count, address monitor);

  // Get owning Java thread from the monitor's owner field.
  static JavaThread *owning_thread_from_monitor_owner(ThreadsList * t_list,
                                                      address owner);

  static JavaThread* owning_thread_from_object(ThreadsList* t_list, oop obj);
  static JavaThread* owning_thread_from_monitor(ThreadsList* t_list, ObjectMonitor* owner);

  // Number of threads on the active threads list
  static int number_of_threads()                 { return _number_of_threads; }
  // Number of non-daemon threads on the active threads list
  static int number_of_non_daemon_threads()      { return _number_of_non_daemon_threads; }

  struct Test;                  // For private gtest access.
};

#endif // SHARE_RUNTIME_THREADS_HPP
