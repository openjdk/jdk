/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classFileParser.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/modules.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "jfr/instrumentation/jfrClassTransformer.hpp"
#include "jfr/instrumentation/jfrJvmtiAgent.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/jni/jfrUpcalls.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/support/jfrKlassUnloading.hpp"
#include "jfr/support/methodtracer//jfrTracedMethod.hpp"
#include "jfr/support/methodtracer/jfrClassFilterClosure.hpp"
#include "jfr/support/methodtracer/jfrFilter.hpp"
#include "jfr/support/methodtracer/jfrInstrumentedClass.hpp"
#include "jfr/support/methodtracer/jfrMethodProcessor.hpp"
#include "jfr/support/methodtracer/jfrMethodTracer.hpp"
#include "jfr/support/methodtracer/jfrTraceTagging.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "oops/access.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/method.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/growableArray.hpp"

const JfrFilter*                     JfrMethodTracer::_filter = nullptr;
const JfrFilter*                     JfrMethodTracer::_previous_filter = nullptr;
ModuleEntry*                         JfrMethodTracer::_jdk_jfr_module = nullptr;
GrowableArray<JfrInstrumentedClass>* JfrMethodTracer::_instrumented_classes = nullptr;
GrowableArray<jlong>*                JfrMethodTracer::_unloaded_class_ids = nullptr;

static inline void log_add(const InstanceKlass* ik) {
  assert(ik != nullptr, "invariant");
  if (log_is_enabled(Debug, jfr, methodtrace)) {
    ResourceMark rm;
    log_debug(jfr, methodtrace)("Adding class %s to instrumented list", ik->external_name());
  }
}

// Quick and unlocked check to see if the Method Tracer has been activated.
// This is flipped to not null the first time a filter is set and will stay non-null forever.
bool JfrMethodTracer::in_use() {
  return _filter != nullptr;
}

jlongArray JfrMethodTracer::set_filters(JNIEnv* env, jobjectArray classes, jobjectArray methods, jobjectArray annotations, jintArray modifications, TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));

  const JfrFilter* const new_filter = JfrFilter::from(classes, methods, annotations, modifications, THREAD);
  if (new_filter == nullptr) {
    return nullptr;
  }
  const JfrFilter* const previous_filter = current_filter();
  JfrFilterClassClosure filter_class_closure(previous_filter, new_filter, THREAD);
  {
    MutexLocker lock(ClassLoaderDataGraph_lock);
    filter_class_closure.iterate_all_classes();
    JfrTraceTagging::clear(instrumented_classes());
  }

  set_filters(previous_filter, new_filter);
  retransform(env, filter_class_closure.classes_to_modify(), THREAD);
  set_filters(nullptr, new_filter);
  delete previous_filter;

  ResourceMark rm(THREAD);
  GrowableArray<jlong>* to_be_published = collect_new_timing_entries();
  if (to_be_published->is_nonempty()) {
    return JfrJavaSupport::create_long_array(to_be_published, THREAD);
  }
  return nullptr;
}

GrowableArray<jlong>* JfrMethodTracer::collect_new_timing_entries() {
  GrowableArray<jlong>* result = new GrowableArray<jlong>(256);
  MutexLocker lock(ClassLoaderDataGraph_lock);
  GrowableArray<JfrInstrumentedClass>* instrumented = instrumented_classes();
  assert(instrumented != nullptr, "invariant");
  const int length = instrumented->length();
  if (length == 0) {
    return result;
  }
  JfrKlassUnloading::sort(true);
  for (int i = 0; i < length; i++) {
    const JfrInstrumentedClass& jic = instrumented->at(i);
    traceid trace_id = jic.trace_id();
    if (!JfrKlassUnloading::is_unloaded(trace_id)) {
      const InstanceKlass* ik = jic.instance_klass();
      assert(HAS_STICKY_BIT(ik), "invariant");
      if (HAS_TIMING_BIT(ik)) {
        CLEAR_TIMING_BIT(ik);
        assert(HAS_NOT_TIMING_BIT(ik), "invariant");
        result->append(trace_id);
      }
    }
  }
  return result;
}

void JfrMethodTracer::retransform(JNIEnv* env, GrowableArray<jclass>* class_array, TRAPS) {
  assert(class_array != nullptr, "invariant");
  int class_count = class_array->length();
  if (class_count > 0) {
    ResourceMark rm(THREAD);
    ThreadToNativeFromVM state(THREAD);
    jclass* classes = NEW_C_HEAP_ARRAY(jclass, class_count, mtTracing);
    for (int i = 0; i < class_count; i++) {
      classes[i] = class_array->at(i);
    }
    JfrJvmtiAgent::retransform_classes(env, classes, class_count, THREAD);
    FREE_C_HEAP_ARRAY(jclass, classes);
  }
}

void JfrMethodTracer::set_filters(const JfrFilter* previous_filter, const JfrFilter* new_filter) {
  MutexLocker lock(JfrMethodTracer_lock, Mutex::_no_safepoint_check_flag);
  _previous_filter = previous_filter;
  if (_filter != new_filter) {
    _filter = new_filter;
    _filter->log("Current filter");
  }
}

const JfrFilter* JfrMethodTracer::current_filter() {
  MutexLocker lock(JfrMethodTracer_lock, Mutex::_no_safepoint_check_flag);
  return _filter;
}

void JfrMethodTracer::on_klass_creation(InstanceKlass*& ik, ClassFileParser& parser, TRAPS) {
  assert(ik != nullptr, "invariant");
  assert(in_use(), "invariant");
  // 1. Is the ik the initial load, i.e.the first InstanceKlass, or a scratch klass, denoting a redefine / retransform?
  const Klass* const existing_klass = JfrClassTransformer::find_existing_klass(ik, THREAD);
  const bool is_redefine = existing_klass != nullptr;

  // 2. Test the ik and its methods against the installed filters.
  JfrMethodProcessor mp(is_redefine ? InstanceKlass::cast(existing_klass) : ik);
  {
    MutexLocker lock(JfrMethodTracer_lock, Mutex::_no_safepoint_check_flag);
    mp.process(_previous_filter, _filter);
  }
  if (!mp.has_methods()) {
    return;
  }

  // 3. We matched one or more filters. Now construct a new InstanceKlass with instrumented methods in accordance with matches.
  ResourceMark rm(THREAD);
  const ClassFileStream* clone = parser.clone_stream();
  ClassFileStream* const result = JfrUpcalls::on_method_trace(ik, clone, mp.methods(), THREAD);
  if (result != nullptr) {
    InstanceKlass* const new_ik = JfrClassTransformer::create_instance_klass(ik, result, !is_redefine, THREAD);
    if (new_ik != nullptr) {
      JfrClassTransformer::copy_traceid(ik, new_ik); // copy existing traceid
      if (is_redefine) {
        // Keep the original cached class file data from the existing class.
        JfrClassTransformer::transfer_cached_class_file_data(ik, new_ik, parser, THREAD);
        JfrClassTransformer::rewrite_klass_pointer(ik, new_ik, parser, THREAD); // The ik is modified to point to new_ik here.
        // By setting the sticky bit on the existng klass, we receive a callback into on_klass_redefinition (see below)
        // when our new methods are installed into the existing klass during retransformation / redefinition.
        const InstanceKlass* existing_ik = InstanceKlass::cast(existing_klass);
        mp.update_methods(existing_ik);
        existing_ik->module()->add_read(jdk_jfr_module());
        JfrTraceTagging::install_sticky_bit_for_retransform_klass(existing_ik, mp.methods(), mp.has_timing());
      } else {
        // Initial class load.
        JfrClassTransformer::cache_class_file_data(new_ik, clone, THREAD); // save the initial class file bytes (clone stream)
        JfrClassTransformer::rewrite_klass_pointer(ik, new_ik, parser, THREAD); // The ik is modified to point to new_ik here.
        // The initial class load can be installed into the instrumented class list directly.
        mp.update_methods(ik);
        add_instrumented_class(ik, mp.methods());
        if (mp.has_timing()) {
          JfrUpcalls::publish_method_timers_for_klass(JfrTraceId::load_raw(ik), THREAD);
        }
      }
    }
  }
}

// At this point we have installed our retransformed methods into the original klass, which is ik.
// jvmtiRedefineClassses::redefine_single_class() has finished so we are still at a safepoint.
// If the original klass is not already in the list, add it and also dynamically tag all
// artifacts that have the sticky bit set.
void JfrMethodTracer::on_klass_redefinition(const InstanceKlass* ik, Thread* thread) {
  assert(ik != nullptr, "invariant");
  assert(!ik->is_scratch_class(), "invarint");
  assert(ik->has_been_redefined(), "invariant");
  assert(HAS_STICKY_BIT(ik), "invariant");
  assert(in_use(), "invariant");
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  if (!JfrRecorder::is_recording()) {
    JfrTraceTagging::clear_sticky_bit(ik, false);
    return;
  }
  GrowableArray<JfrInstrumentedClass>* const instrumented = instrumented_classes();
  JfrInstrumentedClass jic(JfrTraceId::load_raw(ik), ik, false);

  assert(instrumented != nullptr, "invariant");
  if (instrumented->find(jic) == -1) { // not already existing
    const int idx = instrumented_classes()->append(jic);
    if (idx == 0) {
      assert(!JfrTraceIdEpoch::has_method_tracer_changed_tag_state(), "invariant");
      JfrTraceIdEpoch::set_method_tracer_tag_state();
    }
    JfrTraceTagging::set_dynamic_tag_for_sticky_bit(ik);
    log_add(ik);
  }
}

#ifdef ASSERT
static bool in_instrumented_list(const InstanceKlass* ik) {
  assert(ik != nullptr, "invariant");
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  const GrowableArray<JfrInstrumentedClass>* const instrumented = JfrMethodTracer::instrumented_classes();
  assert(instrumented != nullptr, "invariant");
  const JfrInstrumentedClass jic(JfrTraceId::load_raw(ik), ik, false);
  return instrumented->find(jic) != -1;
}
#endif

void JfrMethodTracer::add_instrumented_class(InstanceKlass* ik, GrowableArray<JfrTracedMethod>* methods) {
  assert(ik != nullptr, "invariant");
  assert(!ik->is_scratch_class(), "invariant");
  assert(methods->is_nonempty(), "invariant");
  ik->module()->add_read(jdk_jfr_module());
  MutexLocker lock(ClassLoaderDataGraph_lock);
  assert(!in_instrumented_list(ik), "invariant");
  JfrTraceTagging::set_dynamic_tag(ik, methods);
  JfrTraceTagging::set_sticky_bit(ik, methods);
  JfrInstrumentedClass jik(JfrTraceId::load_raw(ik), ik, false);
  const int idx = instrumented_classes()->append(jik);
  if (idx == 0) {
    JfrTraceIdEpoch::set_method_tracer_tag_state();
  }
  assert(in_instrumented_list(ik), "invariant");
  log_add(ik);
}

GrowableArray<JfrInstrumentedClass>* JfrMethodTracer::instrumented_classes() {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  if (_instrumented_classes == nullptr) {
    _instrumented_classes = new (mtTracing) GrowableArray<JfrInstrumentedClass>(10, mtTracing);
    _unloaded_class_ids = new (mtTracing) GrowableArray<jlong>(10, mtTracing);
  }
  return _instrumented_classes;
}

jlongArray JfrMethodTracer::drain_stale_class_ids(TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD);)
  if (!in_use()) {
    return nullptr;
  }
  MutexLocker lock(ClassLoaderDataGraph_lock);
  if (_unloaded_class_ids == nullptr || _unloaded_class_ids->is_empty()) {
    return nullptr;
  }
  return JfrJavaSupport::create_long_array(_unloaded_class_ids, THREAD);
}

ModuleEntry* JfrMethodTracer::jdk_jfr_module() {
  if (_jdk_jfr_module == nullptr) {
    MutexLocker ml(Module_lock);
    ModuleEntryTable* table = Modules::get_module_entry_table(Handle());
    Symbol* jfr_module_name = SymbolTable::probe("jdk.jfr", 7);
    assert(jfr_module_name != nullptr, "jdk.jfr name could not be found");
    _jdk_jfr_module = table->lookup_only(jfr_module_name);
    assert(_jdk_jfr_module != nullptr, "jdk.jfr module could not be found");
  }
  return _jdk_jfr_module;
}

// Invoked from JfrTypeSet

void JfrMethodTracer::clear_instrumented_classes() {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  assert(!JfrRecorder::is_recording(), "invariant");
  if (_instrumented_classes != nullptr) {
    JfrTraceTagging::clear(_instrumented_classes, false);
  }
}

void JfrMethodTracer::trim_instrumented_classes() {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  assert(JfrTraceIdEpoch::has_method_tracer_changed_tag_state(), "invariant");
  GrowableArray<JfrInstrumentedClass>* trimmed_classes = new (mtTracing) GrowableArray<JfrInstrumentedClass>(10, mtTracing);
  for (int i = 0; i < _instrumented_classes->length(); i++) {
    const JfrInstrumentedClass& jic = _instrumented_classes->at(i);
    if (jic.unloaded()) {
      _unloaded_class_ids->append(jic.trace_id_as_jlong());
    } else {
      trimmed_classes->append(jic);
    }
  }
  delete _instrumented_classes;
  _instrumented_classes = trimmed_classes;
}
