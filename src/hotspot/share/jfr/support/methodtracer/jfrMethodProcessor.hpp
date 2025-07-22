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

#ifndef SHARE_JFR_SUPPORT_METHODTRACER_JFRMETHODPROCESSOR_HPP
#define SHARE_JFR_SUPPORT_METHODTRACER_JFRMETHODPROCESSOR_HPP

#include "jfr/support/methodtracer/jfrTracedMethod.hpp"
#include "memory/allocation.hpp"

class InstanceKlass;
class JfrFilter;
class Thread;

template <typename> class GrowableArray;

//
// Class responsible for determining which methods in an InstanceKlass
// that should be instrumented and tagged.
//
class JfrMethodProcessor: public StackObj {
 private:
  const InstanceKlass* const      _klass;
  GrowableArray<JfrTracedMethod>* _methods;
  Thread*                         _thread;
  bool                            _has_timing;
  bool                            _log;

  void set_timing(int modification);
  void process();

 public:
  JfrMethodProcessor(const InstanceKlass* klass, Thread* thread);
  ~JfrMethodProcessor();

  bool has_methods() const {
    return _methods != nullptr;
  }

  const GrowableArray<JfrTracedMethod>* methods() const {
    return _methods;
  }

  GrowableArray<JfrTracedMethod>* methods() {
    return _methods;
  }

  void update_methods(const InstanceKlass* ik);

  bool has_timing() const {
    return _has_timing;
  }
};

#endif // SHARE_JFR_SUPPORT_METHODTRACER_JFRMETHODPROCESSOR_HPP
