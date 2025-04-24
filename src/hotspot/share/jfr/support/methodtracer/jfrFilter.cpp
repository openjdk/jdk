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

#include "classfile/moduleEntry.hpp"
#include "classfile/vmClasses.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/support/jfrAnnotationIterator.hpp"
#include "jfr/support/jfrJdkJfrEvent.hpp"
#include "jfr/support/methodtracer/jfrFilter.hpp"
#include "logging/log.hpp"
#include "logging/logMessage.hpp"
#include "memory/resourceArea.hpp"
#include "oops/method.inline.hpp"
#include "oops/symbol.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "runtime/handles.inline.hpp"

JfrFilter::JfrFilter(Symbol** class_names,
                     Symbol** method_names,
                     Symbol** annotation_names,
                     int* modifications,
                     int count) :
  _class_names(class_names),
  _method_names(method_names),
  _annotation_names(annotation_names),
  _modifications(modifications),
  _count(count) {}

JfrFilter::~JfrFilter() {
  for (int i = 0; i < _count; i++) {
    Symbol::maybe_decrement_refcount(_class_names[i]);
    Symbol::maybe_decrement_refcount(_method_names[i]);
    Symbol::maybe_decrement_refcount(_annotation_names[i]);
  }
  FREE_C_HEAP_ARRAY(Symbol*, _class_names);
  FREE_C_HEAP_ARRAY(Symbol*, _method_names);
  FREE_C_HEAP_ARRAY(Symbol*, _annotation_names);
  FREE_C_HEAP_ARRAY(int, _modifications);
}

bool JfrFilter::can_instrument_module(const ModuleEntry* module) const {
  if (module == nullptr) {
    return true;
  }
  const Symbol* name = module->name();
  if (name == nullptr) {
    return true;
  }
  if (name->equals("jdk.jfr", 7)) {
    return false;
  }
  if (name->equals("jdk.proxy1", 10)) {
    return false;
  }
  return true;
}

bool JfrFilter::can_instrument_class(const InstanceKlass* ik) const {
  assert(ik != nullptr, "invariant");
  if (JfrTraceId::has_sticky_bit(ik)) {
    return true;
  }
  if (ik->is_hidden()) {
    return false;
  }
  if (JdkJfrEvent::is_a(ik)) {
    return false;
  }
  if (ik == vmClasses::Continuation_klass()) {
    return false;
  }
  return can_instrument_module(ik->module());
}

// can_intrument(InstanceKlass*) is not called in this method
// to avoid executing the same code for every method in a class
bool JfrFilter::can_instrument_method(const Method* method) const {
  assert(method != nullptr, "invariant");
  if (JfrTraceId::has_sticky_bit(method)) {
    return true;
  }
  if (method->is_abstract()) {
    return false;
  }
  if (method->is_synthetic()) {
    return false;
  }
  if (method->is_native()) {
    return false;
  }
  if (method->is_compiled_lambda_form()) {
    return false;
  }
  return true;
}

bool JfrFilter::match_annotations(const InstanceKlass* ik, AnnotationArray* annotations, const Symbol* symbol, bool log) const {
  assert(ik != nullptr, "invariant");
  assert(symbol != nullptr, "invariant");
  if (annotations == nullptr) {
    return false;
  }
  JfrAnnotationIterator it(ik, annotations);
  while (it.has_next()) {
    it.move_to_next();
    const Symbol* current = it.type();
    bool equal = current == symbol;
    if (log && log_is_enabled(Trace, methodtrace)) {
      ResourceMark rm;
      log_trace(jfr, methodtrace)(
        "match_annotations: Class %s has annotation %s %s",
        ik->external_name(),
        current->as_C_string(),
        (equal ? "true" : "false")
        );
    }
    if (current == symbol) {
      return true;
    }
  }
  return false;
}

int JfrFilter::combine_bits(int a, int b) {
  if (a == -1) {
    return b;
  }
  if (b == -1) {
    return a;
  }
  return a | b;
}

int JfrFilter::class_modifications(const InstanceKlass* ik, bool log) const {
  assert(ik != nullptr, "invariant");
  AnnotationArray* class_annotations = ik->class_annotations();
  if (class_annotations == nullptr) {
    return -1;
  }
  int result = -1;
  for (int i = 0; i < _count; i++) {
    const Symbol* annotation_filter = _annotation_names[i];
    if (annotation_filter != nullptr && match_annotations(ik, class_annotations, annotation_filter, log)) {
      result = combine_bits(result, _modifications[i]);
      if (log && log_is_enabled(Trace, methodtrace)) {
        ResourceMark rm;
        log_trace(jfr, methodtrace)("Class_modifications: %s bits = %d", ik->external_name(), result);
      }
    }
  }
  return result;
}

bool JfrFilter::match(const InstanceKlass* ik) const {
  assert(ik != nullptr, "invariant");
  if (class_modifications(ik, false) != -1) {
    return true;
  }
  const Array<Method*>* methods = ik->methods();
  const int method_count = methods->length();
  for (int i = 0; i < method_count; i++) {
    if (method_modifications(methods->at(i)) != -1) {
      return true;
    }
  }
  return false;
}

int JfrFilter::method_modifications(const Method* method) const {
  assert(method != nullptr, "invariant");
  InstanceKlass* klass = method->method_holder();
  int result = -1;
  for (int i = 0; i < _count; i++) {
    Symbol* annotation_name = _annotation_names[i];
    if (annotation_name != nullptr) {
      if (match_annotations(klass, method->annotations(), annotation_name, false)) {
        result = combine_bits(result, _modifications[i]);
      }
    } else {
      Symbol* class_name = _class_names[i];
      if (class_name == nullptr || klass->name() == class_name) {
        Symbol* method_name = _method_names[i];
        if (method_name == nullptr || (method->name() == method_name && can_instrument_method(method))) {
          result = combine_bits(result, _modifications[i]);
        }
      }
    }
  }
  return result;
}

void JfrFilter::log(const char* caption) const {
  assert(caption != nullptr, "invariant");
  LogMessage(jfr,methodtrace) msg;
  msg.debug("%s = {", caption);
  for (int i = 0; i < _count; i++) {
    const Symbol* m = _method_names[i];
    const Symbol* c = _class_names[i];
    const Symbol* a = _annotation_names[i];
    const int mod = _modifications[i];
    const char* modification = "-timing -tracing";

    if (mod == 1) {
      modification = "+timing";
    } else if (mod == 2) {
      modification = "+tracing";
    } else if (mod == 3) {
      modification = "+timing +tracing";
    }

    if (a != nullptr) {
      char annotation_buffer[100];
      a->as_klass_external_name(annotation_buffer, 100);
      size_t length = strlen(annotation_buffer);
      if (length > 2) {
        annotation_buffer[length - 1] = '\0'; // remove trailing ';'
        msg.debug(" @%s %s", annotation_buffer + 1, modification); // Skip 'L'
      }
    } else {
      char class_buffer[100];
      if (c != nullptr) {
        c->as_C_string(class_buffer, 100);
      } else {
        class_buffer[0] = '\0';
      }
      if (m != nullptr) {
        char method_buffer[100];
        m->as_klass_external_name(method_buffer, 100);
        msg.debug(" %s::%s %s", class_buffer, method_buffer, modification);
      } else {
        msg.debug(" %s %s", class_buffer, modification);
      }
    }
  }
  msg.debug("}");
}

static constexpr const int initial_array_size = 4;

static GrowableArray<const JfrFilter*>* c_heap_allocate_array(int size = initial_array_size) {
  return new (mtTracing) GrowableArray<const JfrFilter*>(size, mtTracing);
}

// Track the set of previous filters during a chunk / epoch.
static GrowableArray<const JfrFilter*>* _previous_filters_epoch_0 = nullptr;
static GrowableArray<const JfrFilter*>* _previous_filters_epoch_1 = nullptr;

static GrowableArray<const JfrFilter*>* previous_filters_epoch_0() {
  if (_previous_filters_epoch_0 == nullptr) {
    _previous_filters_epoch_0 = c_heap_allocate_array(initial_array_size);
  }
  return _previous_filters_epoch_0;
}

static GrowableArray<const JfrFilter*>* previous_filters_epoch_1() {
  if (_previous_filters_epoch_1 == nullptr) {
    _previous_filters_epoch_1 = c_heap_allocate_array(initial_array_size);
  }
  return _previous_filters_epoch_1;
}

static GrowableArray<const JfrFilter*>* get_previous_filters(u1 epoch) {
  return epoch == 0 ? previous_filters_epoch_0() : previous_filters_epoch_1();
}

static GrowableArray<const JfrFilter*>* get_previous_filters() {
  return get_previous_filters(JfrTraceIdEpoch::current());
}

static GrowableArray<const JfrFilter*>* get_previous_filters_previous_epoch() {
  return get_previous_filters(JfrTraceIdEpoch::previous());
}

static void add_previous_filter(const JfrFilter* previous_filter) {
  if (previous_filter != nullptr) {
    get_previous_filters()->append(previous_filter);
  }
}

const JfrFilter* JfrFilterManager::_current = nullptr;

const JfrFilter* JfrFilterManager::current() {
  return Atomic::load(&_current);
}

void JfrFilterManager::install(const JfrFilter* new_filter) {
  assert(new_filter != nullptr, "invarinat");
  add_previous_filter(Atomic::xchg(&_current, new_filter));
  new_filter->log("New filter installed");
}

static void delete_filters(GrowableArray<const JfrFilter*>* filters) {
  assert(filters != nullptr, "invariant");
  for (int i = 0; i < filters->length(); ++i) {
    delete filters->at(i);
  }
  filters->clear();
}

void JfrFilterManager::clear_previous_filters() {
  delete_filters(get_previous_filters_previous_epoch());
}

bool JfrFilterManager::install(jobjectArray classes, jobjectArray methods, jobjectArray annotations, jintArray modification_array, JavaThread* jt) {
  assert(classes != nullptr, "invariant");
  assert(methods != nullptr, "invariant");
  assert(annotations != nullptr, "invariant");
  assert(modification_array != nullptr, "invariant");

  intptr_t class_size = 0;
  Symbol** class_names = JfrJavaSupport::symbol_array(classes, jt, &class_size, true);
  assert(class_names != nullptr, "invariant");

  intptr_t method_size = 0;
  Symbol** method_names = JfrJavaSupport::symbol_array(methods, jt, &method_size, true);
  assert(method_names != nullptr, "invariant");

  intptr_t annotation_size = 0;
  Symbol** annotation_names = JfrJavaSupport::symbol_array(annotations, jt, &annotation_size, true);
  assert(annotation_names != nullptr, "invariant");

  typeArrayOop ta = typeArrayOop(JfrJavaSupport::resolve_non_null(modification_array));
  const typeArrayHandle modification_tah(jt, ta);
  const int modification_size = modification_tah->length();
  int* const modifications = NEW_C_HEAP_ARRAY(int, modification_size, mtTracing);
  for (int i = 0; i < modification_size; i++) {
    modifications[i] = modification_tah->int_at(i);
  }
  if (class_size != method_size || class_size != annotation_size || class_size != modification_size) {
    FREE_C_HEAP_ARRAY(Symbol*, class_names);
    FREE_C_HEAP_ARRAY(Symbol*, method_names);
    FREE_C_HEAP_ARRAY(Symbol*, annotation_names);
    FREE_C_HEAP_ARRAY(int, modifications);
    JfrJavaSupport::throw_internal_error("Method array sizes don't match", jt);
    return false;
  }
  const JfrFilter* const new_filter = new JfrFilter(class_names, method_names, annotation_names, modifications, modification_size);
  assert(new_filter != nullptr, "invariant");
  install(new_filter);
  return true;
}
