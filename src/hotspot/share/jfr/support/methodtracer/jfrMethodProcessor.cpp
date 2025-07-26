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

#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/support/methodtracer/jfrFilter.hpp"
#include "jfr/support/methodtracer/jfrFilterManager.hpp"
#include "jfr/support/methodtracer/jfrMethodProcessor.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "logging/log.hpp"
#include "oops/instanceKlass.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/growableArray.hpp"

JfrMethodProcessor::JfrMethodProcessor(const InstanceKlass* ik, Thread* thread) :
  _klass(ik),
  _methods(nullptr),
  _thread(thread),
  _has_timing(false),
  _log(log_is_enabled(Debug, jfr, methodtrace)) {
    assert(ik != nullptr, "invariant");
    assert(Thread::current() == thread, "invariant");
    process();
}

JfrMethodProcessor::~JfrMethodProcessor() {
  assert(_thread != nullptr, "invariant");
  if (_methods != nullptr) {
    // Removal of pushed metadata keep-alive entries.
    for (int i = 0; i < _methods->length(); ++i) {
      Method* const method = const_cast<Method*>(_methods->at(i).method());
      if (method != nullptr) {
        const int idx = _thread->metadata_handles()->find_from_end(method);
        assert(idx >= 0, "invariant");
        _thread->metadata_handles()->remove_at(idx);
      }
    }
  }
}

void JfrMethodProcessor::update_methods(const InstanceKlass* ik) {
  assert(ik != nullptr, "invariant");
  assert(_methods != nullptr, "invariant");
  const Array<Method*>* const ik_methods = ik->methods();
  assert(ik_methods != nullptr, "invariant");
  for (int i = 0; i < _methods->length(); ++i) {
    const uint32_t idx = _methods->at(i).methods_array_index();
    Method* const method = ik_methods->at(idx);
    assert(method != nullptr, "invariant");
    assert(method->name() == _methods->at(i).name(), "invariant");
    assert(method->signature() == _methods->at(i).signature(), "invariant");
    _methods->at(i).set_method(method);
    // This is to keep the method from being unloaded during redefine / retransform.
    // Equivalent functionality to that provided by the methodHandle. Unfortunately,
    // we cannot use that directly because our handles would reside not on the stack
    // but in an Arena managed by a thread-local ResourceArea, which is not allowed.
    // Removal of pushed metadata entries happens in the destructor.
    _thread->metadata_handles()->push(method);
  }
}

void JfrMethodProcessor::set_timing(int modification) {
  if (_has_timing) {
    return;
  }
  if (modification > 0 && (modification & 1)) {
    _has_timing = true;
  }
}


static void log(const Method* method, traceid id, int new_modification) {
  assert(method != nullptr, "invariant");
  const char* timing = JfrFilter::is_timing(new_modification) ? "+timing" : "-timing";
  const char* tracing = JfrFilter::is_tracing(new_modification) ? "+tracing" : "-tracing";
  stringStream param_stream;
  method->signature()->print_as_signature_external_parameters(&param_stream);
  const char* param_string = param_stream.as_string();

  stringStream ss;
  ss.print("%s", method->method_holder()->external_name());
  ss.print("::");
  ss.print("%s", method->name()->as_C_string());
  ss.print("(");
  if (strlen(param_string) < 30) {
    ss.print("%s", param_string);
  } else {
    ss.print("...");
  }
  ss.print(")");
  log_debug(jfr, methodtrace)("Modify bytecode for %s %s %s (Method ID: " UINT64_FORMAT_X ")", ss.as_string(), timing, tracing, id);
}

void JfrMethodProcessor::process() {
  const JfrFilter* const filter = JfrFilterManager::current();
  assert(filter != nullptr, "invariant");
  if (!filter->can_instrument_class(_klass)) {
    return;
  }
  const int class_modifications = filter->class_modifications(_klass, false);
  const Array<Method*>* const methods = _klass->methods();
  const int method_count = methods->length();
  for (int i = 0; i < method_count; i++) {
    const Method* const m = methods->at(i);
    assert(m != nullptr, "invariant");
    if (filter->can_instrument_method(m)) {
      const int new_modification = JfrFilter::combine_bits(class_modifications, filter->method_modifications(m));
      if (new_modification != JfrFilter::NONE || JfrTraceId::has_sticky_bit(m)) {
        // Allocate lazy, most classes will not match a filter
        if (_methods == nullptr) {
          _methods = new GrowableArray<JfrTracedMethod>();
        }
        set_timing(new_modification);
        const int modification = new_modification == JfrFilter::NONE ? 0 : new_modification;
        JfrTracedMethod traced_method(_klass, m, modification, i);
        _methods->append(traced_method);
        if (_log) {
          log(m, traced_method.id(), modification);
        }
      }
    }
  }
}
