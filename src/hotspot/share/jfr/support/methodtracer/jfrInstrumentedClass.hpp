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

#ifndef SHARE_JFR_SUPPORT_METHODTRACER_JFRINSTRUMENTEDCLASS_HPP
#define SHARE_JFR_SUPPORT_METHODTRACER_JFRINSTRUMENTEDCLASS_HPP

#include "jfr/utilities/jfrTypes.hpp"

class InstanceKlass;

//
// Class that holds classes that are currently being instrumented and
// if the have been unloaded.
//
class JfrInstrumentedClass {
 private:
  traceid              _trace_id;
  const InstanceKlass* _instance_klass;
  bool                 _unloaded;

 public:
  JfrInstrumentedClass(traceid trace_id = 0, const InstanceKlass* instance_klass = nullptr, bool unloaded = false) :
    _trace_id(trace_id), _instance_klass(instance_klass), _unloaded(unloaded) {
  }

  const InstanceKlass* instance_klass() const {
    return _instance_klass;
  }

  traceid trace_id() const {
    return _trace_id;
  }

  jlong trace_id_as_jlong() const {
    return static_cast<jlong>(_trace_id);
  }

  void set_unloaded(bool unloaded) {
    _unloaded = unloaded;
  }

  bool unloaded() const {
    return _unloaded;
  }

  bool operator==(const JfrInstrumentedClass& rhs) {
    return _trace_id == rhs._trace_id;
  }
};

#endif // SHARE_JFR_SUPPORT_METHODTRACER_JFRINSTRUMENTEDCLASS_HPP
