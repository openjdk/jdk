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
#include "classfile/moduleEntry.hpp"
#include "classfile/modules.hpp"
#include "classfile/symbolTable.hpp"
#include "jfr/instrumentation/jfrClassTransformer.hpp"
#include "jfr/instrumentation/jfrJvmtiAgent.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/jni/jfrUpcalls.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/support/jfrKlassUnloading.hpp"
#include "jfr/support/methodtracer/jfrClassFilterClosure.hpp"
#include "jfr/support/methodtracer/jfrFilter.hpp"
#include "jfr/support/methodtracer/jfrFilterManager.hpp"
#include "jfr/support/methodtracer/jfrInstrumentedClass.hpp"
#include "jfr/support/methodtracer/jfrMethodProcessor.hpp"
#include "jfr/support/methodtracer/jfrMethodTracer.hpp"
#include "jfr/support/methodtracer/jfrTracedMethod.hpp"
#include "jfr/support/methodtracer/jfrTraceTagging.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/method.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/resizeableResourceHash.hpp"

ModuleEntry*                         JfrMethodTracer::_jdk_jfr_module = nullptr;
GrowableArray<JfrInstrumentedClass>* JfrMethodTracer::_instrumented_classes = nullptr;
GrowableArray<jlong>*                JfrMethodTracer::_timing_entries = nullptr;

// Quick and unlocked check to see if the Method Tracer has been activated.
// This is flipped to not null the first time a filter is set and will stay non-null forever.
bool JfrMethodTracer::in_use() {
  return JfrFilterManager::current() != nullptr;
}

static void clear(GrowableArray<JfrInstrumentedClass>* instrumented_classes) {
  assert(instrumented_classes != nullptr, "invariant");
  if (instrumented_classes->is_nonempty()) {
    instrumented_classes->clear();
    JfrTraceIdEpoch::reset_method_tracer_tag_state();
  }
}

jlongArray JfrMethodTracer::set_filters(JNIEnv* env, jobjectArray classes, jobjectArray methods, jobjectArray annotations, jintArray modifications, TRAPS) {
  assert(env != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));

  // This operation, if successful, atomically installs a JfrFilter object to represent all passed in filters.
  if (!JfrFilterManager::install(classes, methods, annotations, modifications, THREAD)) {
    return nullptr;
  }

  ResourceMark rm(THREAD);
  JfrFilterClassClosure filter_class_closure(THREAD);
  {
    MutexLocker lock(ClassLoaderDataGraph_lock);
    filter_class_closure.iterate_all_classes(instrumented_classes());
    ::clear(instrumented_classes());
  }
  retransform(env, filter_class_closure, THREAD);
  MutexLocker lock(ClassLoaderDataGraph_lock);
  if (_timing_entries->is_empty()) {
    return nullptr;
  }
  jlongArray array = JfrJavaSupport::create_long_array(_timing_entries, THREAD);
  _timing_entries->clear();
  return array;
}

class MirrorClosure {
 private:
  jclass* const _classes;
  int _size;
  int _idx;
 public:
  MirrorClosure(int size) : _classes(NEW_RESOURCE_ARRAY(jclass, size)), _size(size), _idx(0) {}

  jclass* classes() const {
    return _classes;
  }

  bool operator()(const traceid& key, const jclass& mirror) {
    assert(_classes != nullptr, "invariant");
    assert(_idx < _size, "invariant");
    _classes[_idx++] = mirror;
    return true;
  }
};

void JfrMethodTracer::retransform(JNIEnv* env, const JfrFilterClassClosure& classes, TRAPS) {
  log_debug(jfr, methodtrace)("Issuing Retransform Classes");
  const int class_count = classes.number_of_classes();
  if (class_count > 0) {
    ThreadToNativeFromVM transition(THREAD);
    const MirrorClosure closure(class_count);
    classes.to_modify()->iterate_all(closure);
    JfrJvmtiAgent::retransform_classes(env, closure.classes(), class_count, THREAD);
  }
}

static void handle_no_bytecode_result(const InstanceKlass* ik) {
  assert(ik != nullptr, "invariant");
  if (JfrTraceId::has_sticky_bit(ik)) {
    MutexLocker lock(ClassLoaderDataGraph_lock);
    JfrTraceTagging::clear_sticky(ik);
  }
}

void JfrMethodTracer::on_klass_creation(InstanceKlass*& ik, ClassFileParser& parser, TRAPS) {
  assert(ik != nullptr, "invariant");
  assert(in_use(), "invariant");

  ResourceMark rm(THREAD);

  // 1. Is the ik the initial load, i.e.the first InstanceKlass, or a scratch klass, denoting a redefine / retransform?
  const InstanceKlass* const existing_ik = JfrClassTransformer::find_existing_klass(ik, THREAD);
  const bool is_retransform = existing_ik != nullptr;

  // 2. Test the ik and its methods against the currently installed filter object.
  JfrMethodProcessor mp(is_retransform ? existing_ik : ik, THREAD);
  if (!mp.has_methods()) {
    return;
  }

  // 3. We matched one or serveral filters. Now construct a new bytecode representation with instrumented methods in accordance with matched instructions.
  const ClassFileStream* clone = parser.clone_stream();
  ClassFileStream* const result = JfrUpcalls::on_method_trace(ik, clone, mp.methods(), THREAD);
  if (result == nullptr) {
    // If no bytecode is returned, either an error occurred during transformation, but more
    // likely the matched instructions were negative, i.e. instructions to remove existing instrumentation
    // and so Java added no new instrumentation. By not returning a bytecode result, the klass is restored to its original, non-instrumented, version.
    handle_no_bytecode_result(is_retransform ? existing_ik : ik);
    return;
  }
  // 4. Now create a new InstanceKlass representation from the modified bytecode.
  InstanceKlass* const new_ik = JfrClassTransformer::create_instance_klass(ik, result, !is_retransform, THREAD);
  if (new_ik == nullptr) {
    return;
  }
  // 5. Replace the passed in ik with the newly constructed, new_ik.
  JfrClassTransformer::copy_traceid(ik, new_ik); // copy existing traceid
  if (is_retransform) {
    // Keep the original cached class file data from the existing class.
    JfrClassTransformer::transfer_cached_class_file_data(ik, new_ik, parser, THREAD);
    JfrClassTransformer::rewrite_klass_pointer(ik, new_ik, parser, THREAD); // The ik is modified to point to new_ik here.
    mp.update_methods(existing_ik);
    existing_ik->module()->add_read(jdk_jfr_module());
    // By setting the sticky bit on the existng klass, we receive a callback into on_klass_redefinition (see below)
    // when our new methods are installed into the existing klass as part of retransformation / redefinition.
    // Only when we know our new methods have been installed can we add the klass to the instrumented list (done as part of callback).
    JfrTraceTagging::tag_sticky_for_retransform_klass(existing_ik, ik, mp.methods(), mp.has_timing());
    return;
  }
  // Initial class load.
  JfrClassTransformer::cache_class_file_data(new_ik, clone, THREAD); // save the initial class file bytes (clone stream)
  JfrClassTransformer::rewrite_klass_pointer(ik, new_ik, parser, THREAD); // The ik is modified to point to new_ik here.
  mp.update_methods(ik);
  // On initial class load the newly created klass can be installed into the instrumented class list directly.
  add_instrumented_class(ik, mp.methods());
  if (mp.has_timing()) {
    // After having installed the newly created klass into the list, perform an upcall to publish the associated TimedClass.
    JfrUpcalls::publish_method_timers_for_klass(JfrTraceId::load_raw(ik), THREAD);
  }
}

static inline void log_add(const InstanceKlass* ik) {
  assert(ik != nullptr, "invariant");
  if (log_is_enabled(Debug, jfr, methodtrace)) {
    ResourceMark rm;
    const traceid klass_id = JfrTraceId::load_raw(ik);
    log_debug(jfr, methodtrace)("Adding class %s to instrumented list (Klass ID: " UINT64_FORMAT_X ")", ik->external_name(), klass_id);
  }
}

void JfrMethodTracer::add_timing_entry(traceid klass_id) {
  assert(_timing_entries != nullptr, "invariant");
  _timing_entries->append(klass_id);
}

// At this point we have installed our new retransformed methods into the original klass, which is ik.
// jvmtiRedefineClassses::redefine_single_class() is about to finish so we are still at a safepoint.
// If the original klass is not already in the list, add it. If the klass has an associated TimedClass,
// add also the klass_id to the list of _timing_entries for publication.
void JfrMethodTracer::on_klass_redefinition(const InstanceKlass* ik, bool has_timing) {
  assert(ik != nullptr, "invariant");
  assert(!ik->is_scratch_class(), "invarint");
  assert(ik->has_been_redefined(), "invariant");
  assert(JfrTraceId::has_sticky_bit(ik), "invariant");
  assert(in_use(), "invariant");
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");

  const traceid klass_id = JfrTraceId::load_raw(ik);
  const JfrInstrumentedClass jic(klass_id, ik, false);

  if (instrumented_classes()->find(jic) == -1) { // not already existing
    const int idx = instrumented_classes()->append(jic);
    if (idx == 0) {
      assert(!JfrTraceIdEpoch::has_method_tracer_changed_tag_state(), "invariant");
      JfrTraceIdEpoch::set_method_tracer_tag_state();
    }
    if (has_timing) {
      add_timing_entry(klass_id);
    }
    log_add(ik);
  }
}

#ifdef ASSERT
static bool in_instrumented_list(const InstanceKlass* ik, const GrowableArray<JfrInstrumentedClass>* list) {
  assert(ik != nullptr, "invariant");
  assert(list != nullptr, "invariant");
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  const JfrInstrumentedClass jic(JfrTraceId::load_raw(ik), ik, false);
  return list->find(jic) != -1;
}
#endif

void JfrMethodTracer::add_instrumented_class(InstanceKlass* ik, GrowableArray<JfrTracedMethod>* methods) {
  assert(ik != nullptr, "invariant");
  assert(!ik->is_scratch_class(), "invariant");
  assert(methods->is_nonempty(), "invariant");
  ik->module()->add_read(jdk_jfr_module());
  MutexLocker lock(ClassLoaderDataGraph_lock);
  assert(!in_instrumented_list(ik, instrumented_classes()), "invariant");
  JfrTraceTagging::tag_sticky(ik, methods);
  const JfrInstrumentedClass jik(JfrTraceId::load_raw(ik), ik, false);
  const int idx = instrumented_classes()->append(jik);
  if (idx == 0) {
    JfrTraceIdEpoch::set_method_tracer_tag_state();
  }
  assert(in_instrumented_list(ik, instrumented_classes()), "invariant");
  log_add(ik);
}

ModuleEntry* JfrMethodTracer::jdk_jfr_module() {
  if (_jdk_jfr_module == nullptr) {
    MutexLocker ml(Module_lock);
    ModuleEntryTable* const table = Modules::get_module_entry_table(Handle());
    Symbol* jfr_module_name = SymbolTable::probe("jdk.jfr", 7);
    assert(jfr_module_name != nullptr, "jdk.jfr name could not be found");
    _jdk_jfr_module = table->lookup_only(jfr_module_name);
    assert(_jdk_jfr_module != nullptr, "jdk.jfr module could not be found");
  }
  return _jdk_jfr_module;
}

// Track the set of unloaded class ids during a chunk / epoch.
static GrowableArray<jlong>* _unloaded_class_ids_0 = nullptr;
static GrowableArray<jlong>* _unloaded_class_ids_1 = nullptr;
static GrowableArray<jlong>* _current_unloaded_class_ids = nullptr;
static GrowableArray<jlong>* _stale_class_ids = nullptr;
static GrowableArray<jlong>* _empty_class_ids = nullptr;

jlongArray JfrMethodTracer::drain_stale_class_ids(TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD);)
  if (!in_use()) {
    return nullptr;
  }
  MutexLocker lock(ClassLoaderDataGraph_lock);
  assert(_stale_class_ids != nullptr, "invariant");
  if (_stale_class_ids == _empty_class_ids) {
    return nullptr;
  }
  assert(_stale_class_ids != _empty_class_ids, "invariant");
  assert(_stale_class_ids->is_nonempty(), "invariant");
  jlongArray array = JfrJavaSupport::create_long_array(_stale_class_ids, THREAD);
  _stale_class_ids->clear();
  assert(_stale_class_ids->is_empty(), "invariant");
  _stale_class_ids = _empty_class_ids;
  return array;
}

static constexpr const int initial_array_size = 256;

static GrowableArray<jlong>* c_heap_allocate_array(int size = initial_array_size) {
  return new (mtTracing) GrowableArray<jlong>(size, mtTracing);
}

static GrowableArray<jlong>* unloaded_class_ids_0() {
  if (_unloaded_class_ids_0 == nullptr) {
    _unloaded_class_ids_0 = c_heap_allocate_array(initial_array_size);
  }
  return _unloaded_class_ids_0;
}

static GrowableArray<jlong>* unloaded_class_ids_1() {
  if (_unloaded_class_ids_1 == nullptr) {
    _unloaded_class_ids_1 = c_heap_allocate_array(initial_array_size);
  }
  return _unloaded_class_ids_1;
}

GrowableArray<JfrInstrumentedClass>* JfrMethodTracer::instrumented_classes() {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  if (_instrumented_classes == nullptr) {
    _instrumented_classes = new (mtTracing) GrowableArray<JfrInstrumentedClass>(256, mtTracing);
    _empty_class_ids = new (mtTracing) GrowableArray<jlong>(0, mtTracing);
    _stale_class_ids = _empty_class_ids;
    _current_unloaded_class_ids = unloaded_class_ids_0();
    _timing_entries = new (mtTracing) GrowableArray<jlong>(256, mtTracing);
  }
  return _instrumented_classes;
}

// Invoked from JfrTypeSet on class unloading of sticky bit-tagged classes.
void JfrMethodTracer::add_to_unloaded_set(const Klass* k) {
  assert(k != nullptr, "invariant");
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  assert(JfrTraceId::has_sticky_bit(k), "invariant");
  assert(_current_unloaded_class_ids != nullptr, "invariant");
  assert(_current_unloaded_class_ids->find(JfrTraceId::load_raw(k)) == -1, "invariant");
  _current_unloaded_class_ids->append(static_cast<jlong>(JfrTraceId::load_raw(k)));
}

// Invoked from JfrTypeSet after having finalized rotation.
void JfrMethodTracer::trim_instrumented_classes(bool trim) {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  if (trim) {
    GrowableArray<JfrInstrumentedClass>* trimmed_classes = new (mtTracing) GrowableArray<JfrInstrumentedClass>(256, mtTracing);
    for (int i = 0; i < _instrumented_classes->length(); i++) {
      const JfrInstrumentedClass& jic = _instrumented_classes->at(i);
      if (jic.unloaded()) {
        assert(JfrKlassUnloading::is_unloaded(jic.trace_id(), true), "invariant");
        assert(_stale_class_ids->find(jic.trace_id()) != -1 || _current_unloaded_class_ids->find(jic.trace_id()) != -1, "invariant");
        continue;
      }
      trimmed_classes->append(jic);
    }
    delete _instrumented_classes;
    _instrumented_classes = trimmed_classes;
  }

  if (instrumented_classes()->is_nonempty()) {
    if (!JfrTraceIdEpoch::has_method_tracer_changed_tag_state()) {
      // Turn the tag state back on for next chunk.
      JfrTraceIdEpoch::set_method_tracer_tag_state();
    }
  }

  // Clearing out filters that were used during the previous epoch.
  JfrFilterManager::clear_previous_filters();

  // Tracking unloading of sticky bit-tagged classes.
  //
  // We want to delay publishing an unloaded class until the very last moment.
  // Because of our tagging scheme, writing events for classes that have unloaded
  // is okay under the invariant that events are written in the same epoch during
  // which the class unloaded. We save classes that unloaded during an epoch and
  // publish them upon epoch rotation.
  //
  // Precautions are necessary because of complexities involving physical recording start/stop,
  // where we must be careful not to rotate away saved unloaded class IDs before they have been drained.
  if (_stale_class_ids == _empty_class_ids) {
    if (_current_unloaded_class_ids->is_nonempty()) {
      // Since we have rotated, we publicize the list of classes unloaded during the previous epoch.
      log_debug(jfr, methodtrace)("Since we have rotated, we publicize the list of classes unloaded during the previous epoch.");
      _stale_class_ids = _current_unloaded_class_ids;
      // Rotating the sets for tracking the unloaded class ids.
      _current_unloaded_class_ids = _current_unloaded_class_ids == unloaded_class_ids_0() ? unloaded_class_ids_1() : unloaded_class_ids_0();
    }
    return;
  }

  // The previously published unloaded classes are yet to be drained.
  // Most likely because we are now starting a new physical recording.
  // Move over all newly unloaded class IDs to make them available for drainage.
  assert(_stale_class_ids != _current_unloaded_class_ids, "invariant");
  if (_current_unloaded_class_ids->is_nonempty()) {
    log_debug(jfr, methodtrace)("Appending unloaded classes during the previous epoch.");
    _stale_class_ids->appendAll(_current_unloaded_class_ids);
    _current_unloaded_class_ids->clear();
  }
  assert(_current_unloaded_class_ids->is_empty(), "invariant");
}
