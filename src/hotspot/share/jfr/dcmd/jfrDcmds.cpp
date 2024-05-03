/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "classfile/vmSymbols.hpp"
#include "jfr/jfr.hpp"
#include "jfr/dcmd/jfrDcmds.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "logging/log.hpp"
#include "logging/logConfiguration.hpp"
#include "logging/logMessage.hpp"
#include "memory/arena.hpp"
#include "memory/resourceArea.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/jniHandles.hpp"
#include "services/diagnosticArgument.hpp"
#include "services/diagnosticFramework.hpp"
#include "utilities/globalDefinitions.hpp"


bool register_jfr_dcmds() {
  uint32_t full_export = DCmd_Source_Internal | DCmd_Source_AttachAPI | DCmd_Source_MBean;
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JfrCheckFlightRecordingDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JfrDumpFlightRecordingDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JfrStartFlightRecordingDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JfrStopFlightRecordingDCmd>(full_export, true, false));
  // JFR.query Uncomment when developing new queries for the JFR.view command
  // DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JfrQueryFlightRecordingDCmd>(full_export, true, true));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JfrViewFlightRecordingDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JfrConfigureFlightRecorderDCmd>(full_export, true, false));
  return true;
}

static bool is_disabled(outputStream* output) {
  if (Jfr::is_disabled()) {
    if (output != nullptr) {
      output->print_cr("Flight Recorder is disabled.\n");
    }
    return true;
  }
  return false;
}

static bool invalid_state(outputStream* out, TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  if (is_disabled(out)) {
    return true;
  }
  if (!JfrJavaSupport::is_jdk_jfr_module_available()) {
    JfrJavaSupport::load_jdk_jfr_module(THREAD);
    if (HAS_PENDING_EXCEPTION) {
      // Log exception here, but let is_jdk_jfr_module_available(out, THREAD)
      // handle output to the user.
      ResourceMark rm(THREAD);
      oop throwable = PENDING_EXCEPTION;
      assert(throwable != nullptr, "invariant");
      oop msg = java_lang_Throwable::message(throwable);
      if (msg != nullptr) {
        char* text = java_lang_String::as_utf8_string(msg);
        if (text != nullptr) {
          log_debug(jfr, startup)("Flight Recorder can not be enabled. %s", text);
        }
      }
      CLEAR_PENDING_EXCEPTION;
    }
  }
  return !JfrJavaSupport::is_jdk_jfr_module_available(out, THREAD);
}

static void handle_pending_exception(outputStream* output, bool startup, oop throwable) {
  assert(throwable != nullptr, "invariant");

  oop msg = java_lang_Throwable::message(throwable);
  if (msg == nullptr) {
    return;
  }
  char* text = java_lang_String::as_utf8_string(msg);
  if (text != nullptr) {
    if (startup) {
      log_error(jfr,startup)("%s", text);
    } else {
      output->print_cr("%s", text);
    }
  }
}

static void print_message(outputStream* output, oop content, TRAPS) {
  objArrayOop lines = objArrayOop(content);
  assert(lines != nullptr, "invariant");
  assert(lines->is_array(), "must be array");
  const int length = lines->length();
  for (int i = 0; i < length; ++i) {
    const char* text = JfrJavaSupport::c_str(lines->obj_at(i), THREAD);
    if (text == nullptr) {
      // An oome has been thrown and is pending.
      break;
    }
    output->print_cr("%s", text);
  }
}

static void log(oop content, TRAPS) {
  LogMessage(jfr,startup) msg;
  objArrayOop lines = objArrayOop(content);
  assert(lines != nullptr, "invariant");
  assert(lines->is_array(), "must be array");
  const int length = lines->length();
  for (int i = 0; i < length; ++i) {
    const char* text = JfrJavaSupport::c_str(lines->obj_at(i), THREAD);
    if (text == nullptr) {
      // An oome has been thrown and is pending.
      break;
    }
    msg.info("%s", text);
  }
}

static void handle_dcmd_result(outputStream* output,
                               const oop result,
                               const DCmdSource source,
                               TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  assert(output != nullptr, "invariant");
  ResourceMark rm(THREAD);
  const bool startup = DCmd_Source_Internal == source;
  if (HAS_PENDING_EXCEPTION) {
    handle_pending_exception(output, startup, PENDING_EXCEPTION);
    // Don't clear exception on startup, JVM should fail initialization.
    if (!startup) {
      CLEAR_PENDING_EXCEPTION;
    }
    return;
  }

  assert(!HAS_PENDING_EXCEPTION, "invariant");

  if (startup) {
    if (log_is_enabled(Warning, jfr, startup))  {
      // if warning is set, assume user hasn't configured log level
      // Log to Info and reset to Warning. This way user can disable
      // default output by setting -Xlog:jfr+startup=error/off
      LogConfiguration::configure_stdout(LogLevel::Info, true, LOG_TAGS(jfr, startup));
      log(result, THREAD);
      LogConfiguration::configure_stdout(LogLevel::Warning, true, LOG_TAGS(jfr, startup));
    } else {
      log(result, THREAD);
    }
  } else {
      // Print output for jcmd or MXBean
      print_message(output, result, THREAD);
  }
}

static oop construct_dcmd_instance(JfrJavaArguments* args, TRAPS) {
  assert(args != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  assert(args->klass() != nullptr, "invariant");
  args->set_name("<init>");
  args->set_signature("()V");
  JfrJavaSupport::new_object(args, CHECK_NULL);
  return args->result()->get_oop();
}

JfrDCmd::JfrDCmd(outputStream* output, bool heap, int num_arguments) : DCmd(output, heap), _args(nullptr), _num_arguments(num_arguments), _delimiter('\0') {}

void JfrDCmd::invoke(JfrJavaArguments& method, TRAPS) const {
  JavaValue constructor_result(T_OBJECT);
  JfrJavaArguments constructor_args(&constructor_result);
  constructor_args.set_klass(javaClass(), CHECK);

  HandleMark hm(THREAD);
  JNIHandleMark jni_handle_management(THREAD);

  const oop dcmd = construct_dcmd_instance(&constructor_args, CHECK);

  Handle h_dcmd_instance(THREAD, dcmd);
  assert(h_dcmd_instance.not_null(), "invariant");

  method.set_receiver(h_dcmd_instance);
  JfrJavaSupport::call_virtual(&method, THREAD);
}

void JfrDCmd::parse(CmdLine* line, char delim, TRAPS) {
  _args = line->args_addr();
  _delimiter = delim;
  // Error checking done in execute.
  // Will not matter from DCmdFactory perspective
  // where parse and execute are called consecutively.
}

void JfrDCmd::execute(DCmdSource source, TRAPS) {
  if (invalid_state(output(), THREAD)) {
    return;
  }
  if (source == DCmd_Source_Internal && _args != nullptr && strcmp(_args, "help") == 0) {
     print_java_help("printStartupHelp");
     vm_exit(0);
  }

  static const char signature[] = "(Ljava/lang/String;Ljava/lang/String;C)[Ljava/lang/String;";
  JavaValue result(T_OBJECT);
  JfrJavaArguments execute(&result, javaClass(), "execute", signature, CHECK);
  jstring argument = JfrJavaSupport::new_string(_args, CHECK);
  jstring s = nullptr;
  if (source == DCmd_Source_Internal) {
    s = JfrJavaSupport::new_string("internal", CHECK);
  }
  if (source == DCmd_Source_MBean) {
    s = JfrJavaSupport::new_string("mbean", CHECK);
  }
  if (source == DCmd_Source_AttachAPI) {
    s = JfrJavaSupport::new_string("attach", CHECK);
  }
  execute.push_jobject(s);
  execute.push_jobject(argument);
  execute.push_int(_delimiter);
  invoke(execute, THREAD);
  handle_dcmd_result(output(), result.get_oop(), source, THREAD);
}

void JfrDCmd::print_java_help(const char* help_method) const {
  static const char signature[] = "()[Ljava/lang/String;";
  JavaThread* thread = JavaThread::current();
  JavaValue result(T_OBJECT);
  JfrJavaArguments printHelp(&result, javaClass(), help_method, signature, thread);
  invoke(printHelp, thread);
  handle_dcmd_result(output(), result.get_oop(), DCmd_Source_MBean, thread);
}

void JfrDCmd::print_help(const char* name) const {
  print_java_help("printHelp");
}

static void initialize_dummy_descriptors(GrowableArray<DCmdArgumentInfo*>* array) {
  assert(array != nullptr, "invariant");
  DCmdArgumentInfo * const dummy = new DCmdArgumentInfo(nullptr,
                                                        nullptr,
                                                        nullptr,
                                                        nullptr,
                                                        false,
                                                        true, // a DcmdFramework "option"
                                                        false);
  for (int i = 0; i < array->capacity(); ++i) {
    array->append(dummy);
  }
}

// Since the DcmdFramework does not support dynamically allocated strings,
// we keep them in a thread local arena. The arena is reset between invocations.
static THREAD_LOCAL Arena* dcmd_arena = nullptr;

static void prepare_dcmd_string_arena(JavaThread* jt) {
  dcmd_arena = JfrThreadLocal::dcmd_arena(jt);
  assert(dcmd_arena != nullptr, "invariant");
  dcmd_arena->destruct_contents(); // will grow on next allocation
}

static char* dcmd_arena_allocate(size_t size) {
  assert(dcmd_arena != nullptr, "invariant");
  return (char*)dcmd_arena->Amalloc(size);
}

static const char* get_as_dcmd_arena_string(oop string) {
  char* str = nullptr;
  const typeArrayOop value = java_lang_String::value(string);
  if (value != nullptr) {
    const size_t length = static_cast<size_t>(java_lang_String::utf8_length(string, value)) + 1;
    str = dcmd_arena_allocate(length);
    assert(str != nullptr, "invariant");
    java_lang_String::as_utf8_string(string, value, str, static_cast<int>(length));
  }
  return str;
}

static const char* read_string_field(oop argument, const char* field_name, TRAPS) {
  JavaValue result(T_OBJECT);
  JfrJavaArguments args(&result);
  args.set_klass(argument->klass());
  args.set_name(field_name);
  args.set_signature("Ljava/lang/String;");
  args.set_receiver(argument);
  JfrJavaSupport::get_field(&args, THREAD);
  const oop string_oop = result.get_oop();
  return string_oop != nullptr ? get_as_dcmd_arena_string(string_oop) : nullptr;
}

static bool read_boolean_field(oop argument, const char* field_name, TRAPS) {
  JavaValue result(T_BOOLEAN);
  JfrJavaArguments args(&result);
  args.set_klass(argument->klass());
  args.set_name(field_name);
  args.set_signature("Z");
  args.set_receiver(argument);
  JfrJavaSupport::get_field(&args, THREAD);
  return (result.get_jint() & 1) == 1;
}

static DCmdArgumentInfo* create_info(oop argument, TRAPS) {
  return new DCmdArgumentInfo(
    read_string_field(argument, "name", THREAD),
    read_string_field(argument, "description", THREAD),
    read_string_field(argument, "type", THREAD),
    read_string_field(argument, "defaultValue", THREAD),
    read_boolean_field(argument, "mandatory", THREAD),
    read_boolean_field(argument, "option", THREAD),
    read_boolean_field(argument, "allowMultiple", THREAD));
}

GrowableArray<DCmdArgumentInfo*>* JfrDCmd::argument_info_array() const {
  static const char signature[] = "()[Ljdk/jfr/internal/dcmd/Argument;";
  JavaThread* thread = JavaThread::current();
  GrowableArray<DCmdArgumentInfo*>* const array = new GrowableArray<DCmdArgumentInfo*>(_num_arguments);
  JavaValue result(T_OBJECT);
  JfrJavaArguments getArgumentInfos(&result, javaClass(), "getArgumentInfos", signature, thread);
  invoke(getArgumentInfos, thread);
  if (thread->has_pending_exception()) {
    // Most likely an OOME, but the DCmdFramework is not the best place to handle it.
    // We handle it locally by clearing the exception and returning an array with dummy descriptors.
    // This lets the MBean server initialization routine complete successfully,
    // but this particular command will have no argument descriptors exposed.
    // Hence we postpone, or delegate, handling of OOME's to code that is better suited.
    log_debug(jfr, system)("Exception in DCmd getArgumentInfos");
    thread->clear_pending_exception();
    initialize_dummy_descriptors(array);
    assert(array->length() == _num_arguments, "invariant");
    return array;
  }
  objArrayOop arguments = objArrayOop(result.get_oop());
  assert(arguments != nullptr, "invariant");
  assert(arguments->is_array(), "must be array");
  const int num_arguments = arguments->length();
  assert(num_arguments == _num_arguments, "invariant");
  prepare_dcmd_string_arena(thread);
  for (int i = 0; i < num_arguments; ++i) {
    DCmdArgumentInfo* const dai = create_info(arguments->obj_at(i), thread);
    assert(dai != nullptr, "invariant");
    array->append(dai);
  }
  return array;
}

GrowableArray<const char*>* JfrDCmd::argument_name_array() const {
  GrowableArray<DCmdArgumentInfo*>* argument_infos = argument_info_array();
  GrowableArray<const char*>* array = new GrowableArray<const char*>(argument_infos->length());
  for (int i = 0; i < argument_infos->length(); i++) {
    array->append(argument_infos->at(i)->name());
  }
  return array;
}

JfrConfigureFlightRecorderDCmd::JfrConfigureFlightRecorderDCmd(outputStream* output,
                                                               bool heap) : DCmdWithParser(output, heap),
  _repository_path("repositorypath", "Path to repository,.e.g \\\"My Repository\\\"", "STRING", false, nullptr),
  _dump_path("dumppath", "Path to dump, e.g. \\\"My Dump path\\\"", "STRING", false, nullptr),
  _stack_depth("stackdepth", "Stack depth", "JULONG", false, "64"),
  _global_buffer_count("globalbuffercount", "Number of global buffers,", "JULONG", false, "20"),
  _global_buffer_size("globalbuffersize", "Size of a global buffers,", "MEMORY SIZE", false, "512k"),
  _thread_buffer_size("thread_buffer_size", "Size of a thread buffer", "MEMORY SIZE", false, "8k"),
  _memory_size("memorysize", "Overall memory size, ", "MEMORY SIZE", false, "10m"),
  _max_chunk_size("maxchunksize", "Size of an individual disk chunk", "MEMORY SIZE", false, "12m"),
  _sample_threads("samplethreads", "Activate thread sampling", "BOOLEAN", false, "true"),
  _preserve_repository("preserve-repository", "Preserve the disk repository after JVM exit", "BOOLEAN", false, "false"),
  _verbose(true) {
  _dcmdparser.add_dcmd_option(&_repository_path);
  _dcmdparser.add_dcmd_option(&_dump_path);
  _dcmdparser.add_dcmd_option(&_stack_depth);
  _dcmdparser.add_dcmd_option(&_global_buffer_count);
  _dcmdparser.add_dcmd_option(&_global_buffer_size);
  _dcmdparser.add_dcmd_option(&_thread_buffer_size);
  _dcmdparser.add_dcmd_option(&_memory_size);
  _dcmdparser.add_dcmd_option(&_max_chunk_size);
  _dcmdparser.add_dcmd_option(&_sample_threads);
  _dcmdparser.add_dcmd_option(&_preserve_repository);
};

void JfrConfigureFlightRecorderDCmd::print_help(const char* name) const {
  outputStream* out = output();
              // 0123456789001234567890012345678900123456789001234567890012345678900123456789001234567890
  out->print_cr("Options:");
  out->print_cr("");
  out->print_cr("  globalbuffercount   (Optional) Number of global buffers. This option is a legacy");
  out->print_cr("                      option: change the memorysize parameter to alter the number of");
  out->print_cr("                      global buffers. This value cannot be changed once JFR has been");
  out->print_cr("                      initialized. (STRING, default determined by the value for");
  out->print_cr("                      memorysize)");
  out->print_cr("");
  out->print_cr("  globalbuffersize    (Optional) Size of the global buffers, in bytes. This option is a");
  out->print_cr("                      legacy option: change the memorysize parameter to alter the size");
  out->print_cr("                      of the global buffers. This value cannot be changed once JFR has");
  out->print_cr("                      been initialized. (STRING, default determined by the value for");
  out->print_cr("                      memorysize)");
  out->print_cr("");
  out->print_cr("  maxchunksize        (Optional) Maximum size of an individual data chunk in bytes if");
  out->print_cr("                      one of the following suffixes is not used: 'm' or 'M' for");
  out->print_cr("                      megabytes OR 'g' or 'G' for gigabytes. This value cannot be");
  out->print_cr("                      changed once JFR has been initialized. (STRING, 12M)");
  out->print_cr("");
  out->print_cr("  memorysize          (Optional) Overall memory size, in bytes if one of the following");
  out->print_cr("                      suffixes is not used: 'm' or 'M' for megabytes OR 'g' or 'G' for");
  out->print_cr("                      gigabytes. This value cannot be changed once JFR has been");
  out->print_cr("                      initialized. (STRING, 10M)");
  out->print_cr("");
  out->print_cr("  repositorypath      (Optional) Path to the location where recordings are stored until");
  out->print_cr("                      they are written to a permanent file. (STRING, The default");
  out->print_cr("                      location is the temporary directory for the operating system. On");
  out->print_cr("                      Linux operating systems, the temporary directory is /tmp. On");
  out->print_cr("                      Windows, the temporary directory is specified by the TMP");
  out->print_cr("                      environment variable)");
  out->print_cr("");
  out->print_cr("  dumppath            (Optional) Path to the location where a recording file is written");
  out->print_cr("                      in case the VM runs into a critical error, such as a system");
  out->print_cr("                      crash. (STRING, The default location is the current directory)");
  out->print_cr("");
  out->print_cr("  stackdepth          (Optional) Stack depth for stack traces. Setting this value");
  out->print_cr("                      greater than the default of 64 may cause a performance");
  out->print_cr("                      degradation. This value cannot be changed once JFR has been");
  out->print_cr("                      initialized. (LONG, 64)");
  out->print_cr("");
  out->print_cr("  thread_buffer_size  (Optional) Local buffer size for each thread in bytes if one of");
  out->print_cr("                      the following suffixes is not used: 'k' or 'K' for kilobytes or");
  out->print_cr("                      'm' or 'M' for megabytes. Overriding this parameter could reduce");
  out->print_cr("                      performance and is not recommended. This value cannot be changed");
  out->print_cr("                      once JFR has been initialized. (STRING, 8k)");
  out->print_cr("");
  out->print_cr("  preserve-repository (Optional) Preserve files stored in the disk repository after the");
  out->print_cr("                      Java Virtual Machine has exited. (BOOLEAN, false)");
  out->print_cr("");
  out->print_cr("Options must be specified using the <key> or <key>=<value> syntax.");
  out->print_cr("");
  out->print_cr("Example usage:");
  out->print_cr("");
  out->print_cr(" $ jcmd <pid> JFR.configure");
  out->print_cr(" $ jcmd <pid> JFR.configure repositorypath=/temporary");
  out->print_cr(" $ jcmd <pid> JFR.configure stackdepth=256");
  out->print_cr(" $ jcmd <pid> JFR.configure memorysize=100M");
  out->print_cr("");
}

void JfrConfigureFlightRecorderDCmd::execute(DCmdSource source, TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));

  if (invalid_state(output(), THREAD)) {
    return;
  }

  HandleMark hm(THREAD);
  JNIHandleMark jni_handle_management(THREAD);

  JavaValue result(T_OBJECT);
  JfrJavaArguments constructor_args(&result);
  constructor_args.set_klass("jdk/jfr/internal/dcmd/DCmdConfigure", CHECK);
  const oop dcmd = construct_dcmd_instance(&constructor_args, CHECK);
  Handle h_dcmd_instance(THREAD, dcmd);
  assert(h_dcmd_instance.not_null(), "invariant");

  jstring repository_path = nullptr;
  if (_repository_path.is_set() && _repository_path.value() != nullptr) {
    repository_path = JfrJavaSupport::new_string(_repository_path.value(), CHECK);
  }

  jstring dump_path = nullptr;
  if (_dump_path.is_set() && _dump_path.value() != nullptr) {
    dump_path = JfrJavaSupport::new_string(_dump_path.value(), CHECK);
  }

  jobject stack_depth = nullptr;
  jobject global_buffer_count = nullptr;
  jobject global_buffer_size = nullptr;
  jobject thread_buffer_size = nullptr;
  jobject max_chunk_size = nullptr;
  jobject memory_size = nullptr;
  jobject preserve_repository = nullptr;

  if (!JfrRecorder::is_created()) {
    if (_stack_depth.is_set()) {
      stack_depth = JfrJavaSupport::new_java_lang_Integer((jint)_stack_depth.value(), CHECK);
    }
    if (_global_buffer_count.is_set()) {
      global_buffer_count = JfrJavaSupport::new_java_lang_Long(_global_buffer_count.value(), CHECK);
    }
    if (_global_buffer_size.is_set()) {
      global_buffer_size = JfrJavaSupport::new_java_lang_Long(_global_buffer_size.value()._size, CHECK);
    }
    if (_thread_buffer_size.is_set()) {
      thread_buffer_size = JfrJavaSupport::new_java_lang_Long(_thread_buffer_size.value()._size, CHECK);
    }
    if (_max_chunk_size.is_set()) {
      max_chunk_size = JfrJavaSupport::new_java_lang_Long(_max_chunk_size.value()._size, CHECK);
    }
    if (_memory_size.is_set()) {
      memory_size = JfrJavaSupport::new_java_lang_Long(_memory_size.value()._size, CHECK);
    }
    if (_sample_threads.is_set()) {
      bool startup = DCmd_Source_Internal == source;
      if (startup) {
        log_warning(jfr,startup)("%s", "Option samplethreads is deprecated. Use -XX:StartFlightRecording:method-profiling=<off|normal|high|max>");
      } else {
        output()->print_cr("%s", "Option samplethreads is deprecated. Use JFR.start method-profiling=<off|normal|high|max>");
        output()->print_cr("");
      }
    }
  }
  if (_preserve_repository.is_set()) {
    preserve_repository = JfrJavaSupport::new_java_lang_Boolean(_preserve_repository.value(), CHECK);
  }

  static const char klass[] = "jdk/jfr/internal/dcmd/DCmdConfigure";
  static const char method[] = "execute";
  static const char signature[] = "(ZLjava/lang/String;Ljava/lang/String;Ljava/lang/Integer;"
    "Ljava/lang/Long;Ljava/lang/Long;Ljava/lang/Long;Ljava/lang/Long;"
    "Ljava/lang/Long;Ljava/lang/Boolean;)[Ljava/lang/String;";

  JfrJavaArguments execute_args(&result, klass, method, signature, CHECK);
  execute_args.set_receiver(h_dcmd_instance);

  // params
  execute_args.push_int(_verbose ? 1 : 0);
  execute_args.push_jobject(repository_path);
  execute_args.push_jobject(dump_path);
  execute_args.push_jobject(stack_depth);
  execute_args.push_jobject(global_buffer_count);
  execute_args.push_jobject(global_buffer_size);
  execute_args.push_jobject(thread_buffer_size);
  execute_args.push_jobject(memory_size);
  execute_args.push_jobject(max_chunk_size);
  execute_args.push_jobject(preserve_repository);

  JfrJavaSupport::call_virtual(&execute_args, THREAD);
  handle_dcmd_result(output(), result.get_oop(), source, THREAD);
}
