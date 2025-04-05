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

#ifndef SHARE_JFR_SUPPORT_METHODTRACER_JFRFILTERCLASSCLOSURE_HPP
#define SHARE_JFR_SUPPORT_METHODTRACER_JFRFILTERCLASSCLOSURE_HPP

#include "jni.h"
#include "memory/iterator.hpp"

class JavaThread;
class JfrFilter;
class Klass;
template<typename T> class GrowableArray;

//
// Class that collects classes that should be retransformed,
// either for adding instrumentation (new_filter) or removing
// old instrumentation (previous_filter).
//
class JfrFilterClassClosure : public KlassClosure {
 private:
  const JfrFilter* const _previous_filter;
  const JfrFilter* const _new_filter;
  JavaThread*            _thread;
  GrowableArray<jclass>* _classes_to_modify;

  bool match(const InstanceKlass* klass) const;
 public:
  JfrFilterClassClosure(const JfrFilter* previous_filter, const JfrFilter* new_filter, JavaThread* thread);
  ~JfrFilterClassClosure();
  void do_klass(Klass* k);
  void iterate_all_classes();
  // Returned array is destroyed with the closure.
  GrowableArray<jclass>* classes_to_modify() const;
};

#endif // SHARE_JFR_SUPPORT_METHODTRACER_JFRFILTERCLASSCLOSURE_HPP
