/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmClasses.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "prims/jvmtiAgentList.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/arguments.hpp"
#include "runtime/flags/jvmFlag.hpp"
#include "runtime/globals.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/os.hpp"
#include "runtime/vmOperations.hpp"
#include "services/attachListener.hpp"
#include "services/diagnosticCommand.hpp"
#include "services/heapDumper.hpp"
#include "services/writeableFlags.hpp"
#include "utilities/debug.hpp"
#include "utilities/formatBuffer.hpp"

volatile AttachListenerState AttachListener::_state = AL_NOT_INITIALIZED;

// Implementation of "properties" command.
//
// Invokes VMSupport.serializePropertiesToByteArray to serialize
// the system properties into a byte array.

static InstanceKlass* load_and_initialize_klass(Symbol* sh, TRAPS) {
  Klass* k = SystemDictionary::resolve_or_fail(sh, true, CHECK_NULL);
  InstanceKlass* ik = InstanceKlass::cast(k);
  if (ik->should_be_initialized()) {
    ik->initialize(CHECK_NULL);
  }
  return ik;
}

static jint get_properties(AttachOperation* op, outputStream* out, Symbol* serializePropertiesMethod) {
  JavaThread* THREAD = JavaThread::current(); // For exception macros.
  HandleMark hm(THREAD);

  // load VMSupport
  Symbol* klass = vmSymbols::jdk_internal_vm_VMSupport();
  InstanceKlass* k = load_and_initialize_klass(klass, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    java_lang_Throwable::print(PENDING_EXCEPTION, out);
    CLEAR_PENDING_EXCEPTION;
    return JNI_ERR;
  }

  // invoke the serializePropertiesToByteArray method
  JavaValue result(T_OBJECT);
  JavaCallArguments args;


  Symbol* signature = vmSymbols::void_byte_array_signature();
  JavaCalls::call_static(&result,
                         k,
                         serializePropertiesMethod,
                         signature,
                         &args,
                         THREAD);
  if (HAS_PENDING_EXCEPTION) {
    java_lang_Throwable::print(PENDING_EXCEPTION, out);
    CLEAR_PENDING_EXCEPTION;
    return JNI_ERR;
  }

  // The result should be a [B
  oop res = result.get_oop();
  assert(res->is_typeArray(), "just checking");
  assert(TypeArrayKlass::cast(res->klass())->element_type() == T_BYTE, "just checking");

  // copy the bytes to the output stream
  typeArrayOop ba = typeArrayOop(res);
  jbyte* addr = typeArrayOop(res)->byte_at_addr(0);
  out->print_raw((const char*)addr, ba->length());

  return JNI_OK;
}

// Implementation of "load" command.
static jint load_agent(AttachOperation* op, outputStream* out) {
  // get agent name and options
  const char* agent = op->arg(0);
  const char* absParam = op->arg(1);
  const char* options = op->arg(2);

  // If loading a java agent then need to ensure that the java.instrument module is loaded
  if (strcmp(agent, "instrument") == 0) {
    JavaThread* THREAD = JavaThread::current(); // For exception macros.
    ResourceMark rm(THREAD);
    HandleMark hm(THREAD);
    JavaValue result(T_OBJECT);
    Handle h_module_name = java_lang_String::create_from_str("java.instrument", THREAD);
    JavaCalls::call_static(&result,
                           vmClasses::module_Modules_klass(),
                           vmSymbols::loadModule_name(),
                           vmSymbols::loadModule_signature(),
                           h_module_name,
                           THREAD);
    if (HAS_PENDING_EXCEPTION) {
      java_lang_Throwable::print(PENDING_EXCEPTION, out);
      CLEAR_PENDING_EXCEPTION;
      return JNI_ERR;
    }
  }

  // The abs parameter should be "true" or "false".
  const bool is_absolute_path = (absParam != nullptr) && (strcmp(absParam, "true") == 0);
  JvmtiAgentList::load_agent(agent, is_absolute_path, options, out);

  // Agent_OnAttach result or error message is written to 'out'.
  return JNI_OK;
}

// Implementation of "properties" command.
// See also: PrintSystemPropertiesDCmd class
static jint get_system_properties(AttachOperation* op, outputStream* out) {
  return get_properties(op, out, vmSymbols::serializePropertiesToByteArray_name());
}

// Implementation of "agent_properties" command.
static jint get_agent_properties(AttachOperation* op, outputStream* out) {
  return get_properties(op, out, vmSymbols::serializeAgentPropertiesToByteArray_name());
}

// Implementation of "datadump" command.
//
// Raises a SIGBREAK signal so that VM dump threads, does deadlock detection,
// etc. In theory this command should only post a DataDumpRequest to any
// JVMTI environment that has enabled this event. However it's useful to
// trigger the SIGBREAK handler.

static jint data_dump(AttachOperation* op, outputStream* out) {
  if (!ReduceSignalUsage) {
    AttachListener::pd_data_dump();
  } else {
    if (JvmtiExport::should_post_data_dump()) {
      JvmtiExport::post_data_dump();
    }
  }
  return JNI_OK;
}

// Implementation of "threaddump" command - essentially a remote ctrl-break
// See also: ThreadDumpDCmd class
//
static jint thread_dump(AttachOperation* op, outputStream* out) {
  bool print_concurrent_locks = false;
  bool print_extended_info = false;
  if (op->arg(0) != nullptr) {
    for (int i = 0; op->arg(0)[i] != 0; ++i) {
      if (op->arg(0)[i] == 'l') {
        print_concurrent_locks = true;
      }
      if (op->arg(0)[i] == 'e') {
        print_extended_info = true;
      }
    }
  }

  // thread stacks and JNI global handles
  VM_PrintThreads op1(out, print_concurrent_locks, print_extended_info, true /* print JNI handle info */);
  VMThread::execute(&op1);

  // Deadlock detection
  VM_FindDeadlocks op2(out);
  VMThread::execute(&op2);

  return JNI_OK;
}

// A jcmd attach operation request was received, which will now
// dispatch to the diagnostic commands used for serviceability functions.
static jint jcmd(AttachOperation* op, outputStream* out) {
  JavaThread* THREAD = JavaThread::current(); // For exception macros.
  // All the supplied jcmd arguments are stored as a single
  // string (op->arg(0)). This is parsed by the Dcmd framework.
  DCmd::parse_and_execute(DCmd_Source_AttachAPI, out, op->arg(0), ' ', THREAD);
  if (HAS_PENDING_EXCEPTION) {
    java_lang_Throwable::print(PENDING_EXCEPTION, out);
    out->cr();
    CLEAR_PENDING_EXCEPTION;
    return JNI_ERR;
  }
  return JNI_OK;
}

// Implementation of "dumpheap" command.
// See also: HeapDumpDCmd class
//
// Input arguments :-
//   arg0: Name of the dump file
//   arg1: "-live" or "-all"
//   arg2: Compress level
static jint dump_heap(AttachOperation* op, outputStream* out) {
  const char* path = op->arg(0);
  if (path == nullptr || path[0] == '\0') {
    out->print_cr("No dump file specified");
  } else {
    bool live_objects_only = true;   // default is true to retain the behavior before this change is made
    const char* arg1 = op->arg(1);
    if (arg1 != nullptr && (strlen(arg1) > 0)) {
      if (strcmp(arg1, "-all") != 0 && strcmp(arg1, "-live") != 0) {
        out->print_cr("Invalid argument to dumpheap operation: %s", arg1);
        return JNI_ERR;
      }
      live_objects_only = strcmp(arg1, "-live") == 0;
    }

    const char* num_str = op->arg(2);
    uint level = 0;
    if (num_str != nullptr && num_str[0] != '\0') {
      if (!Arguments::parse_uint(num_str, &level, 0)) {
        out->print_cr("Invalid compress level: [%s]", num_str);
        return JNI_ERR;
      } else if (level < 1 || level > 9) {
        out->print_cr("Compression level out of range (1-9): %u", level);
        return JNI_ERR;
      }
    }

    // Request a full GC before heap dump if live_objects_only = true
    // This helps reduces the amount of unreachable objects in the dump
    // and makes it easier to browse.
    HeapDumper dumper(live_objects_only /* request GC */);
    dumper.dump(path, out, level);
  }
  return JNI_OK;
}

// Implementation of "inspectheap" command
// See also: ClassHistogramDCmd class
//
// Input arguments :-
//   arg0: "-live" or "-all"
//   arg1: Name of the dump file or null
//   arg2: parallel thread number
static jint heap_inspection(AttachOperation* op, outputStream* out) {
  bool live_objects_only = true;   // default is true to retain the behavior before this change is made
  outputStream* os = out;   // if path not specified or path is null, use out
  fileStream* fs = nullptr;
  const char* arg0 = op->arg(0);
  uint parallel_thread_num = MAX2<uint>(1, (uint)os::initial_active_processor_count() * 3 / 8);
  if (arg0 != nullptr && (strlen(arg0) > 0)) {
    if (strcmp(arg0, "-all") != 0 && strcmp(arg0, "-live") != 0) {
      out->print_cr("Invalid argument to inspectheap operation: %s", arg0);
      return JNI_ERR;
    }
    live_objects_only = strcmp(arg0, "-live") == 0;
  }

  const char* path = op->arg(1);
  if (path != nullptr && path[0] != '\0') {
    // create file
    fs = new (mtInternal) fileStream(path);
    if (fs == nullptr) {
      out->print_cr("Failed to allocate space for file: %s", path);
    }
    os = fs;
  }

  const char* num_str = op->arg(2);
  if (num_str != nullptr && num_str[0] != '\0') {
    uint num;
    if (!Arguments::parse_uint(num_str, &num, 0)) {
      out->print_cr("Invalid parallel thread number: [%s]", num_str);
      delete fs;
      return JNI_ERR;
    }
    parallel_thread_num = num == 0 ? parallel_thread_num : num;
  }

  VM_GC_HeapInspection heapop(os, live_objects_only /* request full gc */, parallel_thread_num);
  VMThread::execute(&heapop);
  if (os != nullptr && os != out) {
    out->print_cr("Heap inspection file created: %s", path);
    delete fs;
  }
  return JNI_OK;
}

// Implementation of "setflag" command
static jint set_flag(AttachOperation* op, outputStream* out) {

  const char* name = nullptr;
  if ((name = op->arg(0)) == nullptr) {
    out->print_cr("flag name is missing");
    return JNI_ERR;
  }

  FormatBuffer<80> err_msg("%s", "");

  int ret = WriteableFlags::set_flag(op->arg(0), op->arg(1), JVMFlagOrigin::ATTACH_ON_DEMAND, err_msg);
  if (ret != JVMFlag::SUCCESS) {
    if (ret == JVMFlag::NON_WRITABLE) {
      out->print_cr("flag '%s' cannot be changed", op->arg(0));
    } else {
      out->print_cr("%s", err_msg.buffer());
    }
    return JNI_ERR;
  }
  return JNI_OK;
}

// Implementation of "printflag" command
// See also: PrintVMFlagsDCmd class
static jint print_flag(AttachOperation* op, outputStream* out) {
  const char* name = nullptr;
  if ((name = op->arg(0)) == nullptr) {
    out->print_cr("flag name is missing");
    return JNI_ERR;
  }
  JVMFlag* f = JVMFlag::find_flag(name);
  if (f) {
    f->print_as_flag(out);
    out->cr();
  } else {
    out->print_cr("no such flag '%s'", name);
  }
  return JNI_OK;
}

// Table to map operation names to functions.

// names must be of length <= AttachOperation::name_length_max
static AttachOperationFunctionInfo funcs[] = {
  { "agentProperties",  get_agent_properties },
  { "datadump",         data_dump },
  { "dumpheap",         dump_heap },
  { "load",             load_agent },
  { "properties",       get_system_properties },
  { "threaddump",       thread_dump },
  { "inspectheap",      heap_inspection },
  { "setflag",          set_flag },
  { "printflag",        print_flag },
  { "jcmd",             jcmd },
  { nullptr,            nullptr }
};



// The Attach Listener threads services a queue. It dequeues an operation
// from the queue, examines the operation name (command), and dispatches
// to the corresponding function to perform the operation.

void AttachListenerThread::thread_entry(JavaThread* thread, TRAPS) {
  os::set_priority(thread, NearMaxPriority);

  assert(thread == Thread::current(), "Must be");
  assert(thread->stack_base() != nullptr && thread->stack_size() > 0,
         "Should already be setup");

  if (AttachListener::pd_init() != 0) {
    AttachListener::set_state(AL_NOT_INITIALIZED);
    return;
  }
  AttachListener::set_initialized();

  for (;;) {
    AttachOperation* op = AttachListener::dequeue();
    if (op == nullptr) {
      AttachListener::set_state(AL_NOT_INITIALIZED);
      return;   // dequeue failed or shutdown
    }

    ResourceMark rm;
    bufferedStream st;
    jint res = JNI_OK;

    // handle special detachall operation
    if (strcmp(op->name(), AttachOperation::detachall_operation_name()) == 0) {
      AttachListener::detachall();
    } else {
      // find the function to dispatch too
      AttachOperationFunctionInfo* info = nullptr;
      for (int i=0; funcs[i].name != nullptr; i++) {
        const char* name = funcs[i].name;
        assert(strlen(name) <= AttachOperation::name_length_max, "operation <= name_length_max");
        if (strcmp(op->name(), name) == 0) {
          info = &(funcs[i]);
          break;
        }
      }

      if (info != nullptr) {
        // dispatch to the function that implements this operation
        res = (info->func)(op, &st);
      } else {
        st.print("Operation %s not recognized!", op->name());
        res = JNI_ERR;
      }
    }

    // operation complete - send result and output to client
    op->complete(res, &st);
  }

  ShouldNotReachHere();
}

bool AttachListener::has_init_error(TRAPS) {
  if (HAS_PENDING_EXCEPTION) {
    tty->print_cr("Exception in VM (AttachListener::init) : ");
    java_lang_Throwable::print(PENDING_EXCEPTION, tty);
    tty->cr();

    CLEAR_PENDING_EXCEPTION;

    return true;
  } else {
    return false;
  }
}

// Starts the Attach Listener thread
void AttachListener::init() {
  EXCEPTION_MARK;

  const char* name = "Attach Listener";
  Handle thread_oop = JavaThread::create_system_thread_object(name, THREAD);
  if (has_init_error(THREAD)) {
    set_state(AL_NOT_INITIALIZED);
    return;
  }

  JavaThread* thread = new AttachListenerThread();
  JavaThread::vm_exit_on_osthread_failure(thread);

  JavaThread::start_internal_daemon(THREAD, thread, thread_oop, NoPriority);
}

// Performs clean-up tasks on platforms where we can detect that the last
// client has detached
void AttachListener::detachall() {
  // call the platform dependent clean-up
  pd_detachall();
}
