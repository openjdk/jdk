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

AttachAPIVersion AttachListener::_supported_version = ATTACH_API_V1;

static bool get_bool_sys_prop(const char* name, bool default_value, TRAPS) {
  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);

  // setup the arguments to getProperty
  Handle key_str = java_lang_String::create_from_str(name, CHECK_(default_value));
  // return value
  JavaValue result(T_OBJECT);
  // public static String getProperty(String key, String def);
  JavaCalls::call_static(&result,
                         vmClasses::System_klass(),
                         vmSymbols::getProperty_name(),
                         vmSymbols::string_string_signature(),
                         key_str,
                         CHECK_(default_value));
  oop value_oop = result.get_oop();
  if (value_oop != nullptr) {
    // convert Java String to utf8 string
    char* value = java_lang_String::as_utf8_string(value_oop);
    if (strcasecmp(value, "true") == 0) {
        return true;
    }
    if (strcasecmp(value, "false") == 0) {
        return false;
    }
  }
  return default_value;
}


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

// Implementation of "getversion" command
static jint get_version(AttachOperation* op, outputStream* out) {
  out->print("%d", (int)AttachListener::get_supported_version());
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
  { "getversion",       get_version },
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
    // jcmd output can get lengthy. As long as we miss jcmd continuous streaming output
    // and instead just send the output in bulk, make sure large command output does not
    // cause asserts. We still retain a max cap, but dimensioned in a way that makes it
    // highly unlikely we should ever hit it under normal conditions.
    constexpr size_t initial_size = 1 * M;
    constexpr size_t max_size = 3 * G;
    bufferedStream st(initial_size, max_size);
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

void AttachListener::set_supported_version(AttachAPIVersion version) {
//  _supported_version = version;
  const char* prop_name = "jdk.attach.compat";
  if (!get_bool_sys_prop(prop_name, false, JavaThread::current())) {
    _supported_version = version;
  }
}

AttachAPIVersion AttachListener::get_supported_version() {
  return _supported_version;
}


int AttachOperation::RequestReader::read_uint(bool may_be_empty) {
  const int MAX_VALUE = INT_MAX / 20;
  char ch;
  int value = 0;
  while (true) {
    int n = read(&ch, 1);
    if (n != 1) {
      // IO errors (n < 0) are logged by read().
      if (n == 0) { // EOF
        if (!may_be_empty || value != 0) { // value != 0 means this is not the 1st read
          log_error(attach)("Failed to read int value: EOF");
        }
      }
      return -1;
    }
    if (ch == '\0') {
      return value;
    }
    if (ch < '0' || ch > '9') {
      log_error(attach)("Failed to read int value: unexpected symbol: %c", ch);
      return -1;
    }
    // Ensure there is no integer overflow.
    if (value >= MAX_VALUE) {
      log_error(attach)("Failed to read int value: too big");
      return -1;
    }
    value = value * 10 + (ch - '0');
  }
}

// Reads operation name and arguments.
// buffer_size: maximum data size;
// min_str_count: minimum number of strings in the request (name + arguments);
// min_read_size: minimum data size.
bool AttachOperation::read_request_data(AttachOperation::RequestReader* reader,
                                        int buffer_size, int min_str_count, int min_read_size) {
  char* buffer = (char*)os::malloc(buffer_size, mtServiceability);
  int str_count = 0;
  int off = 0;
  int left = buffer_size;

  // Read until all (expected) strings or expected bytes have been read, the buffer is full, or EOF.
  do {
    int n = reader->read(buffer + off, left);
    if (n < 0) {
      os::free(buffer);
      return false;
    }
    if (n == 0) { // EOF
      break;
    }
    if (min_str_count > 0) { // need to count arguments
      for (int i = 0; i < n; i++) {
        if (buffer[off + i] == '\0') {
          str_count++;
        }
      }
    }
    off += n;
    left -= n;
  } while (left > 0 && (off < min_read_size || str_count < min_str_count));

  if (off < min_read_size || str_count < min_str_count) { // unexpected EOF
    log_error(attach)("Failed to read request: incomplete request");
    os::free(buffer);
    return false;
  }
  // Request must ends with '\0'.
  if (buffer[off - 1] != '\0') {
    log_error(attach)("Failed to read request: not terminated");
    os::free(buffer);
    return false;
  }

  // Parse request.
  // Command name is the 1st string.
  set_name(buffer);
  log_debug(attach)("read request: cmd = %s", buffer);

  // Arguments.
  char* end = buffer + off;
  for (char* cur = strchr(buffer, '\0') + 1; cur < end; cur = strchr(cur, '\0') + 1) {
    log_debug(attach)("read request: arg = %s", cur);
    append_arg(cur);
  }

  os::free(buffer);

  return true;
}

bool AttachOperation::read_request(RequestReader* reader, ReplyWriter* error_writer) {
  int ver = reader->read_uint(true); // do not log error if this is "empty" connection
  if (ver < 0) {
    return false;
  }
  int buffer_size = 0;
  // Read conditions:
  int min_str_count = 0; // expected number of strings in the request
  int min_read_size = 1; // expected size of the request data (by default 1 symbol for terminating '\0')
  switch (ver) {
  case ATTACH_API_V1: // <ver>0<cmd>0<arg>0<arg>0<arg>0
    // Always contain a command (up to name_length_max chars)
    // and arg_count_max(3) arguments (each up to arg_length_max chars).
    buffer_size = (name_length_max + 1) + arg_count_max * (arg_length_max + 1);
    min_str_count = 1 /*name*/ + arg_count_max;
    break;
  case ATTACH_API_V2: // <ver>0<size>0<cmd>0(<arg>0)* (any number of arguments)
    if (AttachListener::get_supported_version() < 2) {
        log_error(attach)("Failed to read request: v2 is unsupported or disabled");
        write_reply(error_writer, ATTACH_ERROR_BADVERSION, "v2 is unsupported or disabled");
        return false;
    }

    // read size of the data
    buffer_size = reader->read_uint();
    if (buffer_size < 0) {
      log_error(attach)("Failed to read request: negative request size (%d)", buffer_size);
      return false;
    }
    log_debug(attach)("v2 request, data size = %d", buffer_size);

    // Sanity check: max request size is 256K.
    if (buffer_size > 256 * 1024) {
      log_error(attach)("Failed to read request: too big");
      return false;
    }
    // Must contain exactly 'buffer_size' bytes.
    min_read_size = buffer_size;
    break;
  default:
    log_error(attach)("Failed to read request: unknown version (%d)", ver);
    write_reply(error_writer, ATTACH_ERROR_BADVERSION, "unknown version");
    return false;
  }

  bool result = read_request_data(reader, buffer_size, min_str_count, min_read_size);
  if (result && ver == ATTACH_API_V1) {
    // We know the whole request does not exceed buffer_size,
    // for v1 also name/arguments should not exceed name_length_max/arg_length_max.
    if (strlen(name()) > AttachOperation::name_length_max) {
      log_error(attach)("Failed to read request: operation name is too long");
      return false;
    }
    for (int i = 0; i < arg_count(); i++) {
      if (strlen(arg(i)) > AttachOperation::arg_length_max) {
        log_error(attach)("Failed to read request: operation argument is too long");
        return false;
      }
    }
  }
  return result;
}

bool AttachOperation::ReplyWriter::write_fully(const void* buffer, int size) {
  const char* buf = (const char*)buffer;
  do {
    int n = write(buf, size);
    if (n < 0) {
      return false;
    }
    buf += n;
    size -= n;
  } while (size > 0);
  return true;
}

bool AttachOperation::write_reply(ReplyWriter * writer, jint result, const char* message, int message_len) {
  if (message_len < 0) {
    message_len = (int)strlen(message);
  }
  char buf[32];
  os::snprintf_checked(buf, sizeof(buf), "%d\n", result);
  if (!writer->write_fully(buf, (int)strlen(buf))) {
    return false;
  }
  if (!writer->write_fully(message, message_len)) {
    return false;
  }
  writer->flush();
  return true;
}

bool AttachOperation::write_reply(ReplyWriter* writer, jint result, bufferedStream* result_stream) {
  return write_reply(writer, result, result_stream->base(), (int)result_stream->size());
}

