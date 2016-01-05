/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_JVMCI_JVMCI_RUNTIME_HPP
#define SHARE_VM_JVMCI_JVMCI_RUNTIME_HPP

#include "interpreter/interpreter.hpp"
#include "memory/allocation.hpp"
#include "runtime/arguments.hpp"
#include "runtime/deoptimization.hpp"

#define JVMCI_ERROR(...)       \
  { Exceptions::fthrow(THREAD_AND_LOCATION, vmSymbols::jdk_vm_ci_common_JVMCIError(), __VA_ARGS__); return; }

#define JVMCI_ERROR_(ret, ...) \
  { Exceptions::fthrow(THREAD_AND_LOCATION, vmSymbols::jdk_vm_ci_common_JVMCIError(), __VA_ARGS__); return ret; }

#define JVMCI_ERROR_0(...)    JVMCI_ERROR_(0, __VA_ARGS__)
#define JVMCI_ERROR_NULL(...) JVMCI_ERROR_(NULL, __VA_ARGS__)
#define JVMCI_ERROR_OK(...)   JVMCI_ERROR_(JVMCIEnv::ok, __VA_ARGS__)
#define CHECK_OK              CHECK_(JVMCIEnv::ok)

class ParseClosure : public StackObj {
  int _lineNo;
  char* _filename;
  bool _abort;
protected:
  void abort() { _abort = true; }
  void warn_and_abort(const char* message) {
    warn(message);
    abort();
  }
  void warn(const char* message) {
    warning("Error at line %d while parsing %s: %s", _lineNo, _filename == NULL ? "?" : _filename, message);
  }
 public:
  ParseClosure() : _lineNo(0), _filename(NULL), _abort(false) {}
  void parse_line(char* line) {
    _lineNo++;
    do_line(line);
  }
  virtual void do_line(char* line) = 0;
  int lineNo() { return _lineNo; }
  bool is_aborted() { return _abort; }
  void set_filename(char* path) {_filename = path; _lineNo = 0;}
};

class JVMCIRuntime: public AllStatic {
 private:
  static jobject _HotSpotJVMCIRuntime_instance;
  static bool _HotSpotJVMCIRuntime_initialized;
  static bool _well_known_classes_initialized;
  static const char* _compiler;

  static int _trivial_prefixes_count;
  static char** _trivial_prefixes;

  static bool _shutdown_called;

  /**
   * Instantiates a service object, calls its default constructor and returns it.
   *
   * @param name the name of a class implementing jdk.vm.ci.service.Service
   */
  static Handle create_Service(const char* name, TRAPS);

 public:

  /**
   * Parses *.properties files in jre/lib/jvmci/ and adds the properties to plist.
   */
  static void init_system_properties(SystemProperty** plist);

  /**
   * Saves the value of the "jvmci.compiler" system property for processing
   * when JVMCI is initialized.
   */
  static void save_compiler(const char* compiler);

  static bool is_HotSpotJVMCIRuntime_initialized() { return _HotSpotJVMCIRuntime_initialized; }

  /**
   * Gets the singleton HotSpotJVMCIRuntime instance, initializing it if necessary
   */
  static Handle get_HotSpotJVMCIRuntime(TRAPS) {
    initialize_JVMCI(CHECK_(Handle()));
    return Handle(JNIHandles::resolve_non_null(_HotSpotJVMCIRuntime_instance));
  }

  static jobject get_HotSpotJVMCIRuntime_jobject(TRAPS) {
    initialize_JVMCI(CHECK_NULL);
    assert(_HotSpotJVMCIRuntime_initialized, "must be");
    return _HotSpotJVMCIRuntime_instance;
  }

  static Handle callStatic(const char* className, const char* methodName, const char* returnType, JavaCallArguments* args, TRAPS);

  /**
   * Trigger initialization of HotSpotJVMCIRuntime through JVMCI.getRuntime()
   */
  static void initialize_JVMCI(TRAPS);

  /**
   * Explicitly initialize HotSpotJVMCIRuntime itself
   */
  static void initialize_HotSpotJVMCIRuntime(TRAPS);

  static void initialize_well_known_classes(TRAPS);

  static void metadata_do(void f(Metadata*));

  static void shutdown(TRAPS);

  static bool shutdown_called() {
    return _shutdown_called;
  }

  static bool treat_as_trivial(Method* method);
  static void parse_lines(char* path, ParseClosure* closure, bool warnStatFailure);

  static BasicType kindToBasicType(Handle kind, TRAPS);

  // The following routines are all called from compiled JVMCI code

  static void new_instance(JavaThread* thread, Klass* klass);
  static void new_array(JavaThread* thread, Klass* klass, jint length);
  static void new_multi_array(JavaThread* thread, Klass* klass, int rank, jint* dims);
  static void dynamic_new_array(JavaThread* thread, oopDesc* element_mirror, jint length);
  static void dynamic_new_instance(JavaThread* thread, oopDesc* type_mirror);
  static jboolean thread_is_interrupted(JavaThread* thread, oopDesc* obj, jboolean clear_interrupted);
  static void vm_message(jboolean vmError, jlong format, jlong v1, jlong v2, jlong v3);
  static jint identity_hash_code(JavaThread* thread, oopDesc* obj);
  static address exception_handler_for_pc(JavaThread* thread);
  static void monitorenter(JavaThread* thread, oopDesc* obj, BasicLock* lock);
  static void monitorexit (JavaThread* thread, oopDesc* obj, BasicLock* lock);
  static void create_null_exception(JavaThread* thread);
  static void create_out_of_bounds_exception(JavaThread* thread, jint index);
  static void vm_error(JavaThread* thread, jlong where, jlong format, jlong value);
  static oopDesc* load_and_clear_exception(JavaThread* thread);
  static void log_printf(JavaThread* thread, oopDesc* format, jlong v1, jlong v2, jlong v3);
  static void log_primitive(JavaThread* thread, jchar typeChar, jlong value, jboolean newline);
  // Print the passed in object, optionally followed by a newline.  If
  // as_string is true and the object is a java.lang.String then it
  // printed as a string, otherwise the type of the object is printed
  // followed by its address.
  static void log_object(JavaThread* thread, oopDesc* object, bool as_string, bool newline);
  static void write_barrier_pre(JavaThread* thread, oopDesc* obj);
  static void write_barrier_post(JavaThread* thread, void* card);
  static jboolean validate_object(JavaThread* thread, oopDesc* parent, oopDesc* child);
  static void new_store_pre_barrier(JavaThread* thread);

  // Test only function
  static int test_deoptimize_call_int(JavaThread* thread, int value);
};

// Tracing macros.

#define IF_TRACE_jvmci_1 if (!(JVMCITraceLevel >= 1)) ; else
#define IF_TRACE_jvmci_2 if (!(JVMCITraceLevel >= 2)) ; else
#define IF_TRACE_jvmci_3 if (!(JVMCITraceLevel >= 3)) ; else
#define IF_TRACE_jvmci_4 if (!(JVMCITraceLevel >= 4)) ; else
#define IF_TRACE_jvmci_5 if (!(JVMCITraceLevel >= 5)) ; else

#define TRACE_jvmci_1 if (!(JVMCITraceLevel >= 1 && (tty->print("JVMCITrace-1: "), true))) ; else tty->print_cr
#define TRACE_jvmci_2 if (!(JVMCITraceLevel >= 2 && (tty->print("   JVMCITrace-2: "), true))) ; else tty->print_cr
#define TRACE_jvmci_3 if (!(JVMCITraceLevel >= 3 && (tty->print("      JVMCITrace-3: "), true))) ; else tty->print_cr
#define TRACE_jvmci_4 if (!(JVMCITraceLevel >= 4 && (tty->print("         JVMCITrace-4: "), true))) ; else tty->print_cr
#define TRACE_jvmci_5 if (!(JVMCITraceLevel >= 5 && (tty->print("            JVMCITrace-5: "), true))) ; else tty->print_cr

#endif // SHARE_VM_JVMCI_JVMCI_RUNTIME_HPP
