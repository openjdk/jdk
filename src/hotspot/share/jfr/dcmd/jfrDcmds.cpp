/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/vmSymbols.hpp"
#include "jfr/jfr.hpp"
#include "jfr/dcmd/jfrDcmds.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "runtime/handles.inline.hpp"
#include "services/diagnosticArgument.hpp"
#include "services/diagnosticFramework.hpp"
#include "utilities/globalDefinitions.hpp"

#ifdef _WINDOWS
#define JFR_FILENAME_EXAMPLE "C:\\Users\\user\\My Recording.jfr"
#endif

#ifdef __APPLE__
#define JFR_FILENAME_EXAMPLE  "/Users/user/My Recording.jfr"
#endif

#ifndef JFR_FILENAME_EXAMPLE
#define JFR_FILENAME_EXAMPLE "/home/user/My Recording.jfr"
#endif

// JNIHandle management

// ------------------------------------------------------------------
// push_jni_handle_block
//
// Push on a new block of JNI handles.
static void push_jni_handle_block(Thread* const thread) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(thread));

  // Allocate a new block for JNI handles.
  // Inlined code from jni_PushLocalFrame()
  JNIHandleBlock* prev_handles = thread->active_handles();
  JNIHandleBlock* entry_handles = JNIHandleBlock::allocate_block(thread);
  assert(entry_handles != NULL && prev_handles != NULL, "should not be NULL");
  entry_handles->set_pop_frame_link(prev_handles);  // make sure prev handles get gc'd.
  thread->set_active_handles(entry_handles);
}

// ------------------------------------------------------------------
// pop_jni_handle_block
//
// Pop off the current block of JNI handles.
static void pop_jni_handle_block(Thread* const thread) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(thread));

  // Release our JNI handle block
  JNIHandleBlock* entry_handles = thread->active_handles();
  JNIHandleBlock* prev_handles = entry_handles->pop_frame_link();
  // restore
  thread->set_active_handles(prev_handles);
  entry_handles->set_pop_frame_link(NULL);
  JNIHandleBlock::release_block(entry_handles, thread); // may block
}

class JNIHandleBlockManager : public StackObj {
 private:
  Thread* const _thread;
 public:
  JNIHandleBlockManager(Thread* thread) : _thread(thread) {
    push_jni_handle_block(_thread);
  }

  ~JNIHandleBlockManager() {
    pop_jni_handle_block(_thread);
  }
};

static bool is_module_available(outputStream* output, TRAPS) {
  return JfrJavaSupport::is_jdk_jfr_module_available(output, THREAD);
}

static bool is_disabled(outputStream* output) {
  if (Jfr::is_disabled()) {
    if (output != NULL) {
      output->print_cr("Flight Recorder is disabled.\n");
    }
    return true;
  }
  return false;
}

static bool is_recorder_instance_created(outputStream* output) {
  if (!JfrRecorder::is_created()) {
    if (output != NULL) {
      output->print_cr("No available recordings.\n");
      output->print_cr("Use JFR.start to start a recording.\n");
    }
    return false;
  }
  return true;
}

static bool invalid_state(outputStream* out, TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  return is_disabled(out) || !is_module_available(out, THREAD);
}

static void print_pending_exception(outputStream* output, oop throwable) {
  assert(throwable != NULL, "invariant");

  oop msg = java_lang_Throwable::message(throwable);

  if (msg != NULL) {
    char* text = java_lang_String::as_utf8_string(msg);
    output->print_raw_cr(text);
  }
}

static void print_message(outputStream* output, const char* message) {
  if (message != NULL) {
    output->print_raw(message);
  }
}

static void handle_dcmd_result(outputStream* output,
                               const oop result,
                               const DCmdSource source,
                               TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  assert(output != NULL, "invariant");
  if (HAS_PENDING_EXCEPTION) {
    print_pending_exception(output, PENDING_EXCEPTION);
    // Don't clear excption on startup, JVM should fail initialization.
    if (DCmd_Source_Internal != source) {
      CLEAR_PENDING_EXCEPTION;
    }
    return;
  }

  assert(!HAS_PENDING_EXCEPTION, "invariant");

  if (result != NULL) {
    const char* result_chars = java_lang_String::as_utf8_string(result);
    print_message(output, result_chars);
  }
}

static oop construct_dcmd_instance(JfrJavaArguments* args, TRAPS) {
  assert(args != NULL, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  assert(args->klass() != NULL, "invariant");
  args->set_name("<init>", CHECK_NULL);
  args->set_signature("()V", CHECK_NULL);
  JfrJavaSupport::new_object(args, CHECK_NULL);
  return (oop)args->result()->get_jobject();
}

JfrDumpFlightRecordingDCmd::JfrDumpFlightRecordingDCmd(outputStream* output,
                                                       bool heap) : DCmdWithParser(output, heap),
  _name("name", "Recording name, e.g. \\\"My Recording\\\"", "STRING", false, NULL),
  _filename("filename", "Copy recording data to file, e.g. \\\"" JFR_FILENAME_EXAMPLE "\\\"", "STRING", false),
  _maxage("maxage", "Maximum duration to dump, in (s)econds, (m)inutes, (h)ours, or (d)ays, e.g. 60m, or 0 for no limit", "NANOTIME", false, "0"),
  _maxsize("maxsize", "Maximum amount of bytes to dump, in (M)B or (G)B, e.g. 500M, or 0 for no limit", "MEMORY SIZE", false, "0"),
  _begin("begin", "Point in time to dump data from, e.g. 09:00, 21:35:00, 2018-06-03T18:12:56.827Z, 2018-06-03T20:13:46.832, -10m, -3h, or -1d", "STRING", false),
  _end("end", "Point in time to dump data to, e.g. 09:00, 21:35:00, 2018-06-03T18:12:56.827Z, 2018-06-03T20:13:46.832, -10m, -3h, or -1d", "STRING", false),
  _path_to_gc_roots("path-to-gc-roots", "Collect path to GC roots", "BOOLEAN", false, "false") {
  _dcmdparser.add_dcmd_option(&_name);
  _dcmdparser.add_dcmd_option(&_filename);
  _dcmdparser.add_dcmd_option(&_maxage);
  _dcmdparser.add_dcmd_option(&_maxsize);
  _dcmdparser.add_dcmd_option(&_begin);
  _dcmdparser.add_dcmd_option(&_end);
  _dcmdparser.add_dcmd_option(&_path_to_gc_roots);
};

int JfrDumpFlightRecordingDCmd::num_arguments() {
  ResourceMark rm;
  JfrDumpFlightRecordingDCmd* dcmd = new JfrDumpFlightRecordingDCmd(NULL, false);
  if (dcmd != NULL) {
    DCmdMark mark(dcmd);
    return dcmd->_dcmdparser.num_arguments();
  }
  return 0;
}

void JfrDumpFlightRecordingDCmd::execute(DCmdSource source, TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));

  if (invalid_state(output(), THREAD) || !is_recorder_instance_created(output())) {
    return;
  }

  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);
  JNIHandleBlockManager jni_handle_management(THREAD);

  JavaValue result(T_OBJECT);
  JfrJavaArguments constructor_args(&result);
  constructor_args.set_klass("jdk/jfr/internal/dcmd/DCmdDump", CHECK);
  const oop dcmd = construct_dcmd_instance(&constructor_args, CHECK);
  Handle h_dcmd_instance(THREAD, dcmd);
  assert(h_dcmd_instance.not_null(), "invariant");

  jstring name = NULL;
  if (_name.is_set() && _name.value()  != NULL) {
    name = JfrJavaSupport::new_string(_name.value(), CHECK);
  }

  jstring filepath = NULL;
  if (_filename.is_set() && _filename.value() != NULL) {
    filepath = JfrJavaSupport::new_string(_filename.value(), CHECK);
  }

  jobject maxage = NULL;
  if (_maxage.is_set()) {
    maxage = JfrJavaSupport::new_java_lang_Long(_maxage.value()._nanotime, CHECK);
  }

  jobject maxsize = NULL;
  if (_maxsize.is_set()) {
    maxsize = JfrJavaSupport::new_java_lang_Long(_maxsize.value()._size, CHECK);
  }

  jstring begin = NULL;
  if (_begin.is_set() && _begin.value() != NULL) {
    begin = JfrJavaSupport::new_string(_begin.value(), CHECK);
  }

  jstring end = NULL;
  if (_end.is_set() && _end.value() != NULL) {
    end = JfrJavaSupport::new_string(_end.value(), CHECK);
  }

  jobject path_to_gc_roots = NULL;
  if (_path_to_gc_roots.is_set()) {
    path_to_gc_roots = JfrJavaSupport::new_java_lang_Boolean(_path_to_gc_roots.value(), CHECK);
  }

  static const char klass[] = "jdk/jfr/internal/dcmd/DCmdDump";
  static const char method[] = "execute";
  static const char signature[] = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Long;Ljava/lang/Long;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Boolean;)Ljava/lang/String;";

  JfrJavaArguments execute_args(&result, klass, method, signature, CHECK);
  execute_args.set_receiver(h_dcmd_instance);

  // arguments
  execute_args.push_jobject(name);
  execute_args.push_jobject(filepath);
  execute_args.push_jobject(maxage);
  execute_args.push_jobject(maxsize);
  execute_args.push_jobject(begin);
  execute_args.push_jobject(end);
  execute_args.push_jobject(path_to_gc_roots);

  JfrJavaSupport::call_virtual(&execute_args, THREAD);
  handle_dcmd_result(output(), (oop)result.get_jobject(), source, THREAD);
}

JfrCheckFlightRecordingDCmd::JfrCheckFlightRecordingDCmd(outputStream* output, bool heap) : DCmdWithParser(output, heap),
  _name("name","Recording name, e.g. \\\"My Recording\\\" or omit to see all recordings","STRING",false, NULL),
  _verbose("verbose","Print event settings for the recording(s)","BOOLEAN",
           false, "false") {
  _dcmdparser.add_dcmd_option(&_name);
  _dcmdparser.add_dcmd_option(&_verbose);
};

int JfrCheckFlightRecordingDCmd::num_arguments() {
  ResourceMark rm;
  JfrCheckFlightRecordingDCmd* dcmd = new JfrCheckFlightRecordingDCmd(NULL, false);
  if (dcmd != NULL) {
    DCmdMark mark(dcmd);
    return dcmd->_dcmdparser.num_arguments();
  }
  return 0;
}

void JfrCheckFlightRecordingDCmd::execute(DCmdSource source, TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));

  if (invalid_state(output(), THREAD) || !is_recorder_instance_created(output())) {
    return;
  }

  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);
  JNIHandleBlockManager jni_handle_management(THREAD);

  JavaValue result(T_OBJECT);
  JfrJavaArguments constructor_args(&result);
  constructor_args.set_klass("jdk/jfr/internal/dcmd/DCmdCheck", CHECK);
  const oop dcmd = construct_dcmd_instance(&constructor_args, CHECK);
  Handle h_dcmd_instance(THREAD, dcmd);
  assert(h_dcmd_instance.not_null(), "invariant");

  jstring name = NULL;
  if (_name.is_set() && _name.value() != NULL) {
    name = JfrJavaSupport::new_string(_name.value(), CHECK);
  }

  jobject verbose = NULL;
  if (_verbose.is_set()) {
    verbose = JfrJavaSupport::new_java_lang_Boolean(_verbose.value(), CHECK);
  }

  static const char klass[] = "jdk/jfr/internal/dcmd/DCmdCheck";
  static const char method[] = "execute";
  static const char signature[] = "(Ljava/lang/String;Ljava/lang/Boolean;)Ljava/lang/String;";

  JfrJavaArguments execute_args(&result, klass, method, signature, CHECK);
  execute_args.set_receiver(h_dcmd_instance);

  // arguments
  execute_args.push_jobject(name);
  execute_args.push_jobject(verbose);

  JfrJavaSupport::call_virtual(&execute_args, THREAD);
  handle_dcmd_result(output(), (oop)result.get_jobject(), source, THREAD);
}

JfrStartFlightRecordingDCmd::JfrStartFlightRecordingDCmd(outputStream* output,
                                                         bool heap) : DCmdWithParser(output, heap),
  _name("name", "Name that can be used to identify recording, e.g. \\\"My Recording\\\"", "STRING", false, NULL),
  _settings("settings", "Settings file(s), e.g. profile or default. See JRE_HOME/lib/jfr", "STRING SET", false),
  _delay("delay", "Delay recording start with (s)econds, (m)inutes), (h)ours), or (d)ays, e.g. 5h.", "NANOTIME", false, "0"),
  _duration("duration", "Duration of recording in (s)econds, (m)inutes, (h)ours, or (d)ays, e.g. 300s.", "NANOTIME", false, "0"),
  _filename("filename", "Resulting recording filename, e.g. \\\"" JFR_FILENAME_EXAMPLE "\\\"", "STRING", false),
  _disk("disk", "Recording should be persisted to disk", "BOOLEAN", false),
  _maxage("maxage", "Maximum time to keep recorded data (on disk) in (s)econds, (m)inutes, (h)ours, or (d)ays, e.g. 60m, or 0 for no limit", "NANOTIME", false, "0"),
  _maxsize("maxsize", "Maximum amount of bytes to keep (on disk) in (k)B, (M)B or (G)B, e.g. 500M, or 0 for no limit", "MEMORY SIZE", false, "0"),
  _dump_on_exit("dumponexit", "Dump running recording when JVM shuts down", "BOOLEAN", false),
  _path_to_gc_roots("path-to-gc-roots", "Collect path to GC roots", "BOOLEAN", false, "false") {
  _dcmdparser.add_dcmd_option(&_name);
  _dcmdparser.add_dcmd_option(&_settings);
  _dcmdparser.add_dcmd_option(&_delay);
  _dcmdparser.add_dcmd_option(&_duration);
  _dcmdparser.add_dcmd_option(&_disk);
  _dcmdparser.add_dcmd_option(&_filename);
  _dcmdparser.add_dcmd_option(&_maxage);
  _dcmdparser.add_dcmd_option(&_maxsize);
  _dcmdparser.add_dcmd_option(&_dump_on_exit);
  _dcmdparser.add_dcmd_option(&_path_to_gc_roots);
};

int JfrStartFlightRecordingDCmd::num_arguments() {
  ResourceMark rm;
  JfrStartFlightRecordingDCmd* dcmd = new JfrStartFlightRecordingDCmd(NULL, false);
  if (dcmd != NULL) {
    DCmdMark mark(dcmd);
    return dcmd->_dcmdparser.num_arguments();
  }
  return 0;
}

void JfrStartFlightRecordingDCmd::execute(DCmdSource source, TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));

  if (invalid_state(output(), THREAD)) {
    return;
  }

  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);
  JNIHandleBlockManager jni_handle_management(THREAD);

  JavaValue result(T_OBJECT);
  JfrJavaArguments constructor_args(&result);
  constructor_args.set_klass("jdk/jfr/internal/dcmd/DCmdStart", THREAD);
  const oop dcmd = construct_dcmd_instance(&constructor_args, CHECK);
  Handle h_dcmd_instance(THREAD, dcmd);
  assert(h_dcmd_instance.not_null(), "invariant");

  jstring name = NULL;
  if (_name.is_set() && _name.value() != NULL) {
    name = JfrJavaSupport::new_string(_name.value(), CHECK);
  }

  jstring filename = NULL;
  if (_filename.is_set() && _filename.value() != NULL) {
    filename = JfrJavaSupport::new_string(_filename.value(), CHECK);
  }

  jobject maxage = NULL;
  if (_maxage.is_set()) {
    maxage = JfrJavaSupport::new_java_lang_Long(_maxage.value()._nanotime, CHECK);
  }

  jobject maxsize = NULL;
  if (_maxsize.is_set()) {
    maxsize = JfrJavaSupport::new_java_lang_Long(_maxsize.value()._size, CHECK);
  }

  jobject duration = NULL;
  if (_duration.is_set()) {
    duration = JfrJavaSupport::new_java_lang_Long(_duration.value()._nanotime, CHECK);
  }

  jobject delay = NULL;
  if (_delay.is_set()) {
    delay = JfrJavaSupport::new_java_lang_Long(_delay.value()._nanotime, CHECK);
  }

  jobject disk = NULL;
  if (_disk.is_set()) {
    disk = JfrJavaSupport::new_java_lang_Boolean(_disk.value(), CHECK);
  }

  jobject dump_on_exit = NULL;
  if (_dump_on_exit.is_set()) {
    dump_on_exit = JfrJavaSupport::new_java_lang_Boolean(_dump_on_exit.value(), CHECK);
  }

  jobject path_to_gc_roots = NULL;
  if (_path_to_gc_roots.is_set()) {
    path_to_gc_roots = JfrJavaSupport::new_java_lang_Boolean(_path_to_gc_roots.value(), CHECK);
  }

  jobjectArray settings = NULL;
  if (_settings.is_set()) {
    const int length = _settings.value()->array()->length();
    settings = JfrJavaSupport::new_string_array(length, CHECK);
    assert(settings != NULL, "invariant");
    for (int i = 0; i < length; ++i) {
      jobject element = JfrJavaSupport::new_string(_settings.value()->array()->at(i), CHECK);
      assert(element != NULL, "invariant");
      JfrJavaSupport::set_array_element(settings, element, i, CHECK);
    }
  }

  static const char klass[] = "jdk/jfr/internal/dcmd/DCmdStart";
  static const char method[] = "execute";
  static const char signature[] = "(Ljava/lang/String;[Ljava/lang/String;Ljava/lang/Long;"
    "Ljava/lang/Long;Ljava/lang/Boolean;Ljava/lang/String;"
    "Ljava/lang/Long;Ljava/lang/Long;Ljava/lang/Boolean;Ljava/lang/Boolean;)Ljava/lang/String;";

  JfrJavaArguments execute_args(&result, klass, method, signature, CHECK);
  execute_args.set_receiver(h_dcmd_instance);

  // arguments
  execute_args.push_jobject(name);
  execute_args.push_jobject(settings);
  execute_args.push_jobject(delay);
  execute_args.push_jobject(duration);
  execute_args.push_jobject(disk);
  execute_args.push_jobject(filename);
  execute_args.push_jobject(maxage);
  execute_args.push_jobject(maxsize);
  execute_args.push_jobject(dump_on_exit);
  execute_args.push_jobject(path_to_gc_roots);

  JfrJavaSupport::call_virtual(&execute_args, THREAD);
  handle_dcmd_result(output(), (oop)result.get_jobject(), source, THREAD);
}

JfrStopFlightRecordingDCmd::JfrStopFlightRecordingDCmd(outputStream* output,
                                                       bool heap) : DCmdWithParser(output, heap),
  _name("name", "Recording text,.e.g \\\"My Recording\\\"", "STRING", true, NULL),
  _filename("filename", "Copy recording data to file, e.g. \\\"" JFR_FILENAME_EXAMPLE "\\\"", "STRING", false, NULL) {
  _dcmdparser.add_dcmd_option(&_name);
  _dcmdparser.add_dcmd_option(&_filename);
};

int JfrStopFlightRecordingDCmd::num_arguments() {
  ResourceMark rm;
  JfrStopFlightRecordingDCmd* dcmd = new JfrStopFlightRecordingDCmd(NULL, false);
  if (dcmd != NULL) {
    DCmdMark mark(dcmd);
    return dcmd->_dcmdparser.num_arguments();
  }
  return 0;
}

void JfrStopFlightRecordingDCmd::execute(DCmdSource source, TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));

  if (invalid_state(output(), THREAD) || !is_recorder_instance_created(output())) {
    return;
  }

  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);
  JNIHandleBlockManager jni_handle_management(THREAD);

  JavaValue result(T_OBJECT);
  JfrJavaArguments constructor_args(&result);
  constructor_args.set_klass("jdk/jfr/internal/dcmd/DCmdStop", CHECK);
  const oop dcmd = construct_dcmd_instance(&constructor_args, CHECK);
  Handle h_dcmd_instance(THREAD, dcmd);
  assert(h_dcmd_instance.not_null(), "invariant");

  jstring name = NULL;
  if (_name.is_set() && _name.value()  != NULL) {
    name = JfrJavaSupport::new_string(_name.value(), CHECK);
  }

  jstring filepath = NULL;
  if (_filename.is_set() && _filename.value() != NULL) {
    filepath = JfrJavaSupport::new_string(_filename.value(), CHECK);
  }

  static const char klass[] = "jdk/jfr/internal/dcmd/DCmdStop";
  static const char method[] = "execute";
  static const char signature[] = "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";

  JfrJavaArguments execute_args(&result, klass, method, signature, CHECK);
  execute_args.set_receiver(h_dcmd_instance);

  // arguments
  execute_args.push_jobject(name);
  execute_args.push_jobject(filepath);

  JfrJavaSupport::call_virtual(&execute_args, THREAD);
  handle_dcmd_result(output(), (oop)result.get_jobject(), source, THREAD);
}

JfrConfigureFlightRecorderDCmd::JfrConfigureFlightRecorderDCmd(outputStream* output,
                                                               bool heap) : DCmdWithParser(output, heap),
  _repository_path("repositorypath", "Path to repository,.e.g \\\"My Repository\\\"", "STRING", false, NULL),
  _dump_path("dumppath", "Path to dump,.e.g \\\"My Dump path\\\"", "STRING", false, NULL),
  _stack_depth("stackdepth", "Stack Depth", "JLONG", false, "64"),
  _global_buffer_count("globalbuffercount", "Number of global buffers,", "JLONG", false, "32"),
  _global_buffer_size("globalbuffersize", "Size of a global buffers,", "JLONG", false, "524288"),
  _thread_buffer_size("thread_buffer_size", "Size of a thread buffer", "JLONG", false, "8192"),
  _memory_size("memorysize", "Overall memory size, ", "JLONG", false, "16777216"),
  _max_chunk_size("maxchunksize", "Size of an individual disk chunk", "JLONG", false, "12582912"),
  _sample_threads("samplethreads", "Activate Thread sampling", "BOOLEAN", false, "true") {
  _dcmdparser.add_dcmd_option(&_repository_path);
  _dcmdparser.add_dcmd_option(&_dump_path);
  _dcmdparser.add_dcmd_option(&_stack_depth);
  _dcmdparser.add_dcmd_option(&_global_buffer_count);
  _dcmdparser.add_dcmd_option(&_global_buffer_size);
  _dcmdparser.add_dcmd_option(&_thread_buffer_size);
  _dcmdparser.add_dcmd_option(&_memory_size);
  _dcmdparser.add_dcmd_option(&_max_chunk_size);
  _dcmdparser.add_dcmd_option(&_sample_threads);
};

int JfrConfigureFlightRecorderDCmd::num_arguments() {
  ResourceMark rm;
  JfrConfigureFlightRecorderDCmd* dcmd = new JfrConfigureFlightRecorderDCmd(NULL, false);
  if (dcmd != NULL) {
    DCmdMark mark(dcmd);
    return dcmd->_dcmdparser.num_arguments();
  }
  return 0;
}

void JfrConfigureFlightRecorderDCmd::execute(DCmdSource source, TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));

  if (invalid_state(output(), THREAD)) {
    return;
  }

  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);
  JNIHandleBlockManager jni_handle_management(THREAD);

  JavaValue result(T_OBJECT);
  JfrJavaArguments constructor_args(&result);
  constructor_args.set_klass("jdk/jfr/internal/dcmd/DCmdConfigure", CHECK);
  const oop dcmd = construct_dcmd_instance(&constructor_args, CHECK);
  Handle h_dcmd_instance(THREAD, dcmd);
  assert(h_dcmd_instance.not_null(), "invariant");

  jstring repository_path = NULL;
  if (_repository_path.is_set() && _repository_path.value() != NULL) {
    repository_path = JfrJavaSupport::new_string(_repository_path.value(), CHECK);
  }

  jstring dump_path = NULL;
  if (_dump_path.is_set() && _dump_path.value() != NULL) {
    dump_path = JfrJavaSupport::new_string(_dump_path.value(), CHECK);
  }

  jobject stack_depth = NULL;
  if (_stack_depth.is_set()) {
    stack_depth = JfrJavaSupport::new_java_lang_Integer((jint)_stack_depth.value(), CHECK);
  }

  jobject global_buffer_count = NULL;
  if (_global_buffer_count.is_set()) {
    global_buffer_count = JfrJavaSupport::new_java_lang_Long(_global_buffer_count.value(), CHECK);
  }

  jobject global_buffer_size = NULL;
  if (_global_buffer_size.is_set()) {
    global_buffer_size = JfrJavaSupport::new_java_lang_Long(_global_buffer_size.value(), CHECK);
  }

  jobject thread_buffer_size = NULL;
  if (_thread_buffer_size.is_set()) {
    thread_buffer_size = JfrJavaSupport::new_java_lang_Long(_thread_buffer_size.value(), CHECK);
  }

  jobject max_chunk_size = NULL;
  if (_max_chunk_size.is_set()) {
    max_chunk_size = JfrJavaSupport::new_java_lang_Long(_max_chunk_size.value(), CHECK);
  }

  jobject memory_size = NULL;
  if (_memory_size.is_set()) {
    memory_size = JfrJavaSupport::new_java_lang_Long(_memory_size.value(), CHECK);
  }

  jobject sample_threads = NULL;
  if (_sample_threads.is_set()) {
    sample_threads = JfrJavaSupport::new_java_lang_Boolean(_sample_threads.value(), CHECK);
  }

  static const char klass[] = "jdk/jfr/internal/dcmd/DCmdConfigure";
  static const char method[] = "execute";
  static const char signature[] = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;"
    "Ljava/lang/Long;Ljava/lang/Long;Ljava/lang/Long;Ljava/lang/Long;"
    "Ljava/lang/Long;Ljava/lang/Boolean;)Ljava/lang/String;";

  JfrJavaArguments execute_args(&result, klass, method, signature, CHECK);
  execute_args.set_receiver(h_dcmd_instance);

  // params
  execute_args.push_jobject(repository_path);
  execute_args.push_jobject(dump_path);
  execute_args.push_jobject(stack_depth);
  execute_args.push_jobject(global_buffer_count);
  execute_args.push_jobject(global_buffer_size);
  execute_args.push_jobject(thread_buffer_size);
  execute_args.push_jobject(memory_size);
  execute_args.push_jobject(max_chunk_size);
  execute_args.push_jobject(sample_threads);

  JfrJavaSupport::call_virtual(&execute_args, THREAD);
  handle_dcmd_result(output(), (oop)result.get_jobject(), source, THREAD);
}

bool register_jfr_dcmds() {
  uint32_t full_export = DCmd_Source_Internal | DCmd_Source_AttachAPI | DCmd_Source_MBean;
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JfrCheckFlightRecordingDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JfrDumpFlightRecordingDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JfrStartFlightRecordingDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JfrStopFlightRecordingDCmd>(full_export, true, false));
  DCmdFactory::register_DCmdFactory(new DCmdFactoryImpl<JfrConfigureFlightRecorderDCmd>(full_export, true, false));
  return true;
}

