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
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/support/jfrAnnotationIterator.hpp"
#include "jfr/support/jfrJdkJfrEvent.hpp"
#include "jfr/support/methodtracer/jfrFilter.hpp"
#include "logging/log.hpp"
#include "logging/logMessage.hpp"
#include "memory/resourceArea.hpp"
#include "oops/method.inline.hpp"
#include "oops/symbol.hpp"

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
    if (equal) {
      return true;
    }
  }
  return false;
}

int JfrFilter::combine_bits(int a, int b) {
  if (a == NONE) {
    return b;
  }
  if (b == NONE) {
    return a;
  }
  return a | b;
}

int JfrFilter::class_modifications(const InstanceKlass* ik, bool log) const {
  assert(ik != nullptr, "invariant");
  AnnotationArray* class_annotations = ik->class_annotations();
  if (class_annotations == nullptr) {
    return NONE;
  }
  int result = NONE;
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
  if (class_modifications(ik, false) != NONE) {
    return true;
  }
  const Array<Method*>* methods = ik->methods();
  const int method_count = methods->length();
  for (int i = 0; i < method_count; i++) {
    if (method_modifications(methods->at(i)) != NONE) {
      return true;
    }
  }
  return false;
}

int JfrFilter::method_modifications(const Method* method) const {
  assert(method != nullptr, "invariant");
  InstanceKlass* klass = method->method_holder();
  int result = NONE;
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
  if (!log_is_enabled(Debug, jfr, methodtrace))  {
    return;
  }
  LogMessage(jfr,methodtrace) msg;
  msg.debug("%s = {", caption);
  for (int i = 0; i < _count; i++) {
    const Symbol* m = _method_names[i];
    const Symbol* c = _class_names[i];
    const Symbol* a = _annotation_names[i];
    const char* modification = modification_to_text(_modifications[i]);

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
        c->as_klass_external_name(class_buffer, 100);
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

bool JfrFilter::is_timing(int modification) {
  return modification == NONE ? false : (modification & TIMING) != 0;
}

bool JfrFilter::is_tracing(int modification) {
  return modification == NONE ? false : (modification & TRACING) != 0;
}

const char* JfrFilter::modification_to_text(int modification) {
  switch (modification) {
  case 0:
    return "-timing -tracing";
  case TIMING:
    return "+timing";
  case TRACING:
    return "+tracing";
  case TIMING + TRACING:
    return "+timing +tracing";
  default:
    ShouldNotReachHere();
  };
  return "unknown modification";
}
