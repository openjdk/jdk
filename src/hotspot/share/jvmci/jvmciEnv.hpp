/*
 * Copyright (c) 1999, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JVMCI_JVMCIENV_HPP
#define SHARE_JVMCI_JVMCIENV_HPP

#include "classfile/systemDictionary.hpp"
#include "code/debugInfoRec.hpp"
#include "code/dependencies.hpp"
#include "code/exceptionHandlerTable.hpp"
#include "compiler/oopMap.hpp"
#include "runtime/thread.hpp"

class CompileTask;

// Bring the JVMCI compiler thread into the VM state.
#define JVMCI_VM_ENTRY_MARK                       \
  JavaThread* thread = JavaThread::current(); \
  ThreadInVMfromNative __tiv(thread);       \
  ResetNoHandleMark rnhm;                   \
  HandleMarkCleaner __hm(thread);           \
  Thread* THREAD = thread;                  \
  debug_only(VMNativeEntryWrapper __vew;)

#define JVMCI_EXCEPTION_CONTEXT \
  JavaThread* thread=JavaThread::current(); \
  Thread* THREAD = thread;

//
// This class is the top level broker for requests from the compiler
// to the VM.
class JVMCIEnv : StackObj {
  CI_PACKAGE_ACCESS_TO

  friend class JVMCIVMStructs;
  friend class CompileBroker;
  friend class Dependencies;  // for get_object, during logging

public:

  enum CodeInstallResult {
     ok,
     dependencies_failed,
     dependencies_invalid,
     cache_full,
     code_too_large
  };

  // Look up a klass by name from a particular class loader (the accessor's).
  // If require_local, result must be defined in that class loader, or NULL.
  // If !require_local, a result from remote class loader may be reported,
  // if sufficient class loader constraints exist such that initiating
  // a class loading request from the given loader is bound to return
  // the class defined in the remote loader (or throw an error).
  //
  // Return an unloaded klass if !require_local and no class at all is found.
  //
  // The CI treats a klass as loaded if it is consistently defined in
  // another loader, even if it hasn't yet been loaded in all loaders
  // that could potentially see it via delegation.
  static Klass* get_klass_by_name(Klass* accessing_klass, Symbol* klass_name, bool require_local);

  // Constant pool access.
  static Klass* get_klass_by_index(const constantPoolHandle& cpool,
                                   int klass_index,
                                   bool& is_accessible,
                                   Klass* loading_klass);
  static void   get_field_by_index(InstanceKlass* loading_klass, fieldDescriptor& fd,
                                   int field_index);
  static methodHandle  get_method_by_index(const constantPoolHandle& cpool,
                                    int method_index, Bytecodes::Code bc,
                                    InstanceKlass* loading_klass);

  JVMCIEnv(CompileTask* task, int system_dictionary_modification_counter);

private:
  CompileTask*     _task;
  int              _system_dictionary_modification_counter;

  // Compilation result values
  bool             _retryable;
  const char*      _failure_reason;

  // Specifies if _failure_reason is on the C heap.
  bool             _failure_reason_on_C_heap;

  // Cache JVMTI state. Defined as bytes so that reading them from Java
  // via Unsafe is well defined (the C++ type for bool is implementation
  // defined and may not be the same as a Java boolean).
  jbyte  _jvmti_can_hotswap_or_post_breakpoint;
  jbyte  _jvmti_can_access_local_variables;
  jbyte  _jvmti_can_post_on_exceptions;
  jbyte  _jvmti_can_pop_frame;

  // Implementation methods for loading and constant pool access.
  static Klass* get_klass_by_name_impl(Klass* accessing_klass,
                                  const constantPoolHandle& cpool,
                                  Symbol* klass_name,
                                  bool require_local);
  static Klass* get_klass_by_index_impl(const constantPoolHandle& cpool,
                                     int klass_index,
                                     bool& is_accessible,
                                     Klass* loading_klass);
  static void   get_field_by_index_impl(InstanceKlass* loading_klass, fieldDescriptor& fd,
                                     int field_index);
  static methodHandle  get_method_by_index_impl(const constantPoolHandle& cpool,
                                      int method_index, Bytecodes::Code bc,
                                      InstanceKlass* loading_klass);

  // Helper methods
  static bool       check_klass_accessibility(Klass* accessing_klass, Klass* resolved_klass);
  static methodHandle  lookup_method(InstanceKlass*  accessor,
                           Klass*         holder,
                           Symbol*        name,
                           Symbol*        sig,
                           Bytecodes::Code bc,
                           constantTag     tag);

  private:

  // Is this thread currently in the VM state?
  static bool is_in_vm();

  // Helper routine for determining the validity of a compilation
  // with respect to concurrent class loading.
  static JVMCIEnv::CodeInstallResult validate_compile_task_dependencies(Dependencies* target, Handle compiled_code,
                                                                        JVMCIEnv* env, char** failure_detail);

public:
  CompileTask* task() { return _task; }

  bool  jvmti_state_changed() const;
  bool  jvmti_can_hotswap_or_post_breakpoint() const { return  _jvmti_can_hotswap_or_post_breakpoint != 0; }
  bool  jvmti_can_access_local_variables() const     { return  _jvmti_can_access_local_variables != 0; }
  bool  jvmti_can_post_on_exceptions() const         { return  _jvmti_can_post_on_exceptions != 0; }
  bool  jvmti_can_pop_frame() const                  { return  _jvmti_can_pop_frame != 0; }

  const char* failure_reason() { return _failure_reason; }
  bool failure_reason_on_C_heap() { return _failure_reason_on_C_heap; }
  bool retryable() { return _retryable; }

  void set_failure(bool retryable, const char* reason, bool reason_on_C_heap = false) {
    _failure_reason = reason;
    _failure_reason_on_C_heap = reason_on_C_heap;
    _retryable = retryable;
  }

  // Register the result of a compilation.
  static JVMCIEnv::CodeInstallResult register_method(
                       const methodHandle&       target,
                       nmethod*&                 nm,
                       int                       entry_bci,
                       CodeOffsets*              offsets,
                       int                       orig_pc_offset,
                       CodeBuffer*               code_buffer,
                       int                       frame_words,
                       OopMapSet*                oop_map_set,
                       ExceptionHandlerTable*    handler_table,
                       AbstractCompiler*         compiler,
                       DebugInformationRecorder* debug_info,
                       Dependencies*             dependencies,
                       JVMCIEnv*                 env,
                       int                       compile_id,
                       bool                      has_unsafe_access,
                       bool                      has_wide_vector,
                       Handle                    installed_code,
                       Handle                    compiled_code,
                       Handle                    speculation_log);

  // converts the Klass* representing the holder of a method into a
  // InstanceKlass*.  This is needed since the holder of a method in
  // the bytecodes could be an array type.  Basically this converts
  // array types into java/lang/Object and other types stay as they are.
  static InstanceKlass* get_instance_klass_for_declared_method_holder(Klass* klass);
};

#endif // SHARE_JVMCI_JVMCIENV_HPP
