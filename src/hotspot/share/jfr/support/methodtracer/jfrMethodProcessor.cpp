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
#include "jfr/support/methodtracer/jfrMethodProcessor.hpp"
#include "jfr/support/methodtracer/jfrTracedMethod.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "logging/log.hpp"
#include "oops/instanceKlass.hpp"
#include "utilities/growableArray.hpp"

JfrMethodProcessor::JfrMethodProcessor(const InstanceKlass* ik) :
  _klass(ik),
  _methods(nullptr),
  _has_timing(false),
  _log(log_is_enabled(Debug, jfr, methodtrace)) {
  assert(ik != nullptr, "invariant");
}

static inline bool is_timing(int modification) {
  return modification == -1 ? false : (modification & 1) != 0;
}

static inline bool is_tracing(int modification) {
  return modification == -1 ? false : (modification & 2) != 0;
}

static void log(const Method* method, traceid id, int previous_modification, int new_modification) {
  assert(method != nullptr, "invariant");
  const char* tracing = "";
  const char* timing = "";
  if (is_tracing(previous_modification) != is_tracing(new_modification)) {
    tracing = is_tracing(new_modification) ? "+tracing " : "-tracing";
  }
  if (is_timing(previous_modification) != is_timing(new_modification)) {
    tracing = is_timing(new_modification) ? "+timing " : "-timing";
  }
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
  log_debug(jfr, methodtrace)("Modify bytecode: %s %s%s (Method ID:" UINT64_FORMAT ")", ss.as_string(), tracing, timing, id);
}

void JfrMethodProcessor::process(const JfrFilter* previous_filter, const JfrFilter* filter) {
  assert(filter != nullptr, "invariant");
  if (!filter->can_instrument_class(_klass)) {
    return;
  }
  const bool has_previous_filter = previous_filter != nullptr;
  const int class_modifications = filter->class_modifications(_klass, false);
  const int previous_class_modifications = has_previous_filter ? previous_filter->class_modifications(_klass, false) : -1;
  const Array<Method*>* const methods = _klass->methods();
  const int method_count = methods->length();
  for (int i = 0; i < method_count; i++) {
    const Method* const m = methods->at(i);
    assert(m != nullptr, "invariant");
    if (filter->can_instrument_method(m)) {
      int new_modification = filter->method_modifications(m);
      new_modification = JfrFilter::combine_bits(class_modifications, new_modification);
      int previous_modification = class_modifications;
      if (has_previous_filter) {
        previous_modification = JfrFilter::combine_bits(previous_modification, previous_filter->method_modifications(m));
      }
      const int previous_and_new = JfrFilter::combine_bits(previous_modification, new_modification);
      if (previous_and_new != -1) {
        // Allocate lazy, most classes will not match a filter
        if (_methods == nullptr) {
          _methods = new GrowableArray<JfrTracedMethod>();
        }
        set_timing(new_modification);
        int modification = new_modification == -1 ? 0 : new_modification;
        JfrTracedMethod traced_method(_klass, m, modification, i);
        _methods->append(traced_method);
        if (_log) {
          log(m, traced_method.id(), previous_modification, new_modification);
        }
      }
    }
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

void JfrMethodProcessor::update_methods(const InstanceKlass* ik) {
 assert(ik != nullptr, "invariant");
 assert(_methods != nullptr, "invariant");
 for (int i = 0; i < _methods->length(); ++i) {
     _methods->at(i).set_method_from_klass(ik);
   }
}
