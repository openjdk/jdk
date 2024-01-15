/*
* Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/moduleEntry.hpp"
#include "jfrfiles/jfrEventIds.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/recorder/jfrEventSetting.inline.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/stacktrace/jfrStackTraceRepository.hpp"
#include "jfr/support/jfrDeprecationEventWriter.hpp"
#include "jfr/support/jfrDeprecationManager.hpp"
#include "jfr/support/jfrKlassUnloading.hpp"
#include "jfr/support/jfrMethodData.hpp"
#include "jfr/support/jfrMethodLookup.hpp"
#include "jfr/utilities/jfrBlob.hpp"
#include "jfr/utilities/jfrLinkedList.inline.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.inline.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/method.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/thread.inline.hpp"

// for strstr
#include <string.h>

static bool _enqueue_klasses = false;

void JfrDeprecationManager::on_recorder_stop() {
  _enqueue_klasses = false;
}

static inline traceid load_traceid(const Method* method) {
  assert(method != nullptr, "invariant");
  // If the Jfr system is not yet running we only tag the artifacts, not enqueuing klasses.
  return _enqueue_klasses ? JfrTraceId::load(method) : JfrTraceId::load_no_enqueue(method);
}

JfrDeprecatedEdge::JfrDeprecatedEdge(const Method* method, Method* sender, int bci, u1 frame_type, JavaThread* jt) :
  _invocation_time(JfrTicks::now()),
  _stacktrace(),
  _next(nullptr),
  _deprecated_ik(method->method_holder()),
  _deprecated_methodid(load_traceid(method)),
  _sender_ik(sender->method_holder()),
  _sender_methodid(load_traceid(sender)),
  // Our stacktrace will be hand-rolled into a blob.
  // We don't need anything from the stacktrace
  // subsystem except for a unique id.
  _stack_trace_id(JfrStackTraceRepository::next_id()),
  _bci(bci),
  _linenumber(sender->line_number_from_bci(bci)),
  _frame_type(frame_type),
  _for_removal(method->deprecated_for_removal()) {}

bool JfrDeprecatedEdge::has_stacktrace() const {
  return _stacktrace.valid();
}

void JfrDeprecatedEdge::set_stacktrace(const JfrBlobHandle& blob) {
  assert(!has_stacktrace(), "invariant");
  _stacktrace = blob;
}

const JfrBlobHandle& JfrDeprecatedEdge::stacktrace() const {
  assert(has_stacktrace(), "invariant");
  return _stacktrace;
}

typedef JfrLinkedList<JfrDeprecatedEdge> DeprecatedEdgeList;

static DeprecatedEdgeList _list; // Newly constructed edges are concurrently added to this list.
static DeprecatedEdgeList _pending_list; // During epoch rotation (safepoint) entries in _list are moved onto _pending_list
static DeprecatedEdgeList _resolved_list; // Fully resolved edges (event and stacktrace blobs).

static JfrDeprecatedEdge* allocate_edge(const Method* method, Method* sender, int bci, u1 frame_type, JavaThread* jt) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(jt);)
  assert(method != nullptr, "invariant");
  assert(method->deprecated(), "invariant");
  assert(sender != nullptr, "invariant");
  assert(jt != nullptr, "invariant");
  return new JfrDeprecatedEdge(method, sender, bci, frame_type, jt);
}

static void create_edge(const Method* method, Method* sender, int bci, u1 frame_type, JavaThread* jt) {
  JfrDeprecatedEdge* edge;
  // We need the JavaThread to be in _thread_in_vm when creating the edge.
  // This is because the method artifacts needs to be tagged in the correct epoch.
  if (jt->thread_state() != _thread_in_vm) {
    assert(jt->is_Compiler_thread(), "invariant");
    // Can safepoint here.
    ThreadInVMfromNative transition(jt);
    edge = allocate_edge(method, sender, bci, frame_type, jt);
  } else {
    edge = allocate_edge(method, sender, bci, frame_type, jt);
  }
  _list.add(edge);
}

static constexpr const size_t max_num_edges = 10000;

static void log_max_num_edges_reached() {
  log_info(jfr)("The number of deprecated method invocations recorded has reached a maximum limit of %zu.", max_num_edges);
  log_info(jfr)("Deprecated method invocations will not be recorded from now on.");
  log_info(jfr)("Reduce the number of deprecated method invocations and try again.");
}

static bool max_limit_not_reached() {
  static size_t num_edges = 0;
  size_t compare_value;
  do {
    compare_value = Atomic::load(&num_edges);
    if (compare_value == max_num_edges) {
      return false;
    }
  } while (compare_value != Atomic::cmpxchg(&num_edges, compare_value, compare_value + 1));
  if (compare_value + 1 == max_num_edges) {
    log_max_num_edges_reached();
  }
  return true;
}

/*
 * Two cases for JDK modules as outlined by JEP 200: The Modular JDK.
 *
 * The modular structure of the JDK implements the following principles:
 * 1. Standard modules, whose specifications are governed by the JCP, have names starting with the string "java.".
 * 2. All other modules are merely part of the JDK, and have names starting with the string "jdk.".
 * */
static inline bool is_jdk_module(const char* module_name) {
  assert(module_name != nullptr, "invariant");
  return strstr(module_name, "java.") == module_name || strstr(module_name, "jdk.") == module_name;
}

static inline bool is_unnamed_module(const ModuleEntry* module) {
  return module == nullptr || !module->is_named();
}

static inline bool is_jdk_module(const ModuleEntry* module, JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  if (is_unnamed_module(module)) {
    return false;
  }
  ResourceMark rm(jt);
  const Symbol* const module_symbol = module->name();
  assert(module_symbol != nullptr, "invariant");
  const char* const module_name = module_symbol->as_C_string();
  return is_jdk_module(module_name);
}

static inline bool is_not_jdk_module(const ModuleEntry* module, JavaThread* jt) {
  return !is_jdk_module(module, jt);
}

static inline bool jfr_is_started_on_command_line() {
  return JfrRecorder::is_started_on_commandline();
}

static bool should_record(const Method* method, const Method* sender, JavaThread* jt) {
  assert(method != nullptr, "invariant");
  assert(method->deprecated(), "invariant");
  assert(sender != nullptr, "invariant");
  assert(!sender->is_native(), "invariant");
  assert(jt != nullptr, "invariant");
  assert(jfr_is_started_on_command_line(), "invariant");
  const ModuleEntry* const deprecated_module = method->method_holder()->module();
  // Only record invoked deprecated methods in the JDK.
  if (is_not_jdk_module(deprecated_module, jt)) {
    return false;
  }
  const ModuleEntry* const sender_module = sender->method_holder()->module();
  // Only record senders not in the JDK and if we are still within budget.
  return is_not_jdk_module(sender_module, jt) && max_limit_not_reached();
}

// This is the entry point for newly discovered edges in JfrResolution.cpp.
void JfrDeprecationManager::on_link(const Method* method, Method* sender, int bci, u1 frame_type, JavaThread* jt) {
  assert(method != nullptr, "invariant");
  assert(method->deprecated(), "invariant");
  assert(sender != nullptr, "invariant");
  assert(!sender->is_native(), "invariant");
  assert(jt != nullptr, "invariant");
  assert(JfrRecorder::is_started_on_commandline(), "invariant");
  if (JfrMethodData::mark_deprecated_call_site(sender, bci, jt)) {
    if (should_record(method, sender, jt)) {
      create_edge(method, sender, bci, frame_type, jt);
    }
  }
}

static void transfer_list() {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(_pending_list.is_empty(), "invariant");
  DeprecatedEdgeList::NodePtr head = _list.cut();
  assert(_list.is_empty(), "invariant");
  if (head != nullptr) {
    _pending_list.add_list(head);
  }
}

void JfrDeprecationManager::on_level_setting_update(int64_t new_level) {
  JfrDeprecatedEventWriterState::on_level_setting_update(new_level);
}

void JfrDeprecationManager::on_safepoint_clear() {
  assert(!_enqueue_klasses, "invariant");
  // We are now starting JFR, so begin enqueuing tagged klasses.
  _enqueue_klasses = true;
  JfrDeprecatedEventWriterState::on_initialization();
  transfer_list();
}

void JfrDeprecationManager::on_safepoint_write() {
  assert(_enqueue_klasses, "invariant");
  transfer_list();
}

static bool is_klass_unloaded(traceid klass_id) {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  return JfrKlassUnloading::is_unloaded(klass_id, true);
}

static void add_to_leakp_set(const InstanceKlass* ik, traceid method_id) {
  // The lock is needed to ensure the klass unloading lists do not grow in the middle of inspection.
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  assert(ik != nullptr, "invariant");
  if (is_klass_unloaded(JfrMethodLookup::klass_id(method_id))) {
    return;
  }
  const Method* const method = JfrMethodLookup::lookup(ik, method_id);
  assert(method != nullptr, "invariant");
  assert(method->method_holder() == ik, "invariant");
  JfrTraceId::load_leakp_previous_epoch(ik, method); // now has the leakp marker
}

static void add_to_leakp_set(const JfrDeprecatedEdge* edge) {
  assert(edge != nullptr, "invariant");
  add_to_leakp_set(edge->deprecated_ik(), edge->deprecated_methodid());
  add_to_leakp_set(edge->sender_ik(), edge->sender_methodid());
}

// Keeps track of nodes processed from the _pending_list.
static DeprecatedEdgeList::NodePtr _pending_head = nullptr;
static DeprecatedEdgeList::NodePtr _pending_tail = nullptr;

class PendingListProcessor {
 private:
  JfrCheckpointWriter& _writer;
  JavaThread* _jt;
 public:
  PendingListProcessor(JfrCheckpointWriter& writer, JavaThread* jt) : _writer(writer), _jt(jt) {}
  bool process(DeprecatedEdgeList::NodePtr edge) {
    assert(edge != nullptr, "invariant");
    JfrDeprecatedStackTraceWriter::install_stacktrace_blob(edge, _writer, _jt);
    assert(edge->has_stacktrace(), "invariant");
    add_to_leakp_set(edge);
    if (_pending_head == nullptr) {
      _pending_head = edge;
    }
    _pending_tail = edge;
    return true;
  }
};

void JfrDeprecationManager::prepare_type_set(JavaThread* jt) {
  _pending_head = nullptr;
  _pending_tail = nullptr;
  if (_pending_list.is_nonempty()) {
    JfrKlassUnloading::sort(true);
    JfrCheckpointWriter writer(true /* prev epoch */, jt,  false /* header */);
    PendingListProcessor plp(writer, jt);
    _pending_list.iterate(plp);
    assert(_pending_head != nullptr, "invariant");
    assert(_pending_tail != nullptr, "invariant");
    assert(_pending_tail->next() == nullptr, "invariant");
    // Excise already resolved edges to link them.
    _pending_tail->set_next(_resolved_list.cut());
    // Re-insertion.
    _resolved_list.add_list(_pending_head);
    _pending_list.clear();
  }
  assert(_pending_list.is_empty(), "invariant");
}

// A linked-list of blob handles.
static JfrBlobHandle type_set_blobs;

static inline void write_type_set_blobs(JfrCheckpointWriter& writer) {
  type_set_blobs->write(writer);
}

static void save_type_set_blob(JfrCheckpointWriter& writer, bool copy = false) {
  assert(writer.has_data(), "invariant");
  const JfrBlobHandle blob = copy ? writer.copy() : writer.move();
  if (type_set_blobs.valid()) {
    type_set_blobs->set_next(blob);
  } else {
    type_set_blobs = blob;
  }
}

void JfrDeprecationManager::on_type_set_unload(JfrCheckpointWriter& writer) {
  if (writer.has_data()) {
    save_type_set_blob(writer, true);
  }
}

static inline bool has_stacktrace() {
  return JfrEventSetting::has_stacktrace(JfrDeprecatedInvocationEvent);
}

static inline bool write_events(JfrChunkWriter& cw) {
  assert(_resolved_list.is_nonempty(), "invariant");
  JfrDeprecatedEventWriter ebw(cw, has_stacktrace());
  _resolved_list.iterate(ebw);
  return ebw.did_write();
}

static inline void write_stacktraces(JfrChunkWriter& cw) {
  assert(has_stacktrace(), "invariant");
  JfrDeprecatedStackTraceWriter scw(cw);
  _resolved_list.iterate(scw);
}

static inline void write_type_sets(Thread* thread, bool on_error) {
  JfrCheckpointWriter writer(!on_error, thread, false);
  write_type_set_blobs(writer);
}

// First, we consolidate all stacktrace blobs into a single TYPE_STACKTRACE checkpoint and serialize it to the chunk.
// Secondly, we serialize all events to the chunk.
// Thirdly, the type set blobs are written into the JfrCheckpoint system, to be serialized to the chunk
// just after we return from here.
void JfrDeprecationManager::write_edges(JfrChunkWriter& cw, Thread* thread, bool on_error /* false */) {
  if (_resolved_list.is_nonempty() && JfrEventSetting::is_enabled(JfrDeprecatedInvocationEvent)) {
    if (has_stacktrace()) {
      write_stacktraces(cw);
    }
    if (write_events(cw)) {
      write_type_sets(thread, on_error);
    }
  }
}

void JfrDeprecationManager::on_type_set(JfrCheckpointWriter& writer, JfrChunkWriter* cw, Thread* thread) {
  assert(_pending_list.is_empty(), "invariant");
  if (writer.has_data() && _pending_head != nullptr) {
    save_type_set_blob(writer);
  }
  if (cw != nullptr) {
    write_edges(*cw, thread);
  }
}
