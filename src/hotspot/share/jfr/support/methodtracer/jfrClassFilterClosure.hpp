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
#include "jfr/support/methodtracer/jfrInstrumentedClass.hpp"
#include "jfr/utilities/jfrRelation.hpp"
#include "jfr/utilities/jfrTypes.hpp"

class JavaThread;
class JfrFilter;
class Klass;

template<typename T> class GrowableArray;

template<typename K, typename V,
         AnyObj::allocation_type, MemTag,
         unsigned (*HASH)  (K const&),
         bool (*EQUALS)(K const&, K const&)> class ResizeableResourceHashtable;

// Knuth multiplicative hashing.
inline uint32_t knuth_hash(const traceid& id) {
  const uint32_t v = static_cast<uint32_t>(id);
  return v * UINT32_C(2654435761);
}

typedef ResizeableResourceHashtable<traceid, jclass,
                                    AnyObj::RESOURCE_AREA,
                                    mtTracing,
                                    knuth_hash,
                                    equals_traceid> ClosureSet;

//
// Class that collects classes that should be retransformed,
// either for adding instrumentation by matching the current
// filter or for removing old instrumentation.
//
class JfrFilterClassClosure : public KlassClosure {
 private:
  const JfrFilter* const _new_filter;
  ClosureSet* const _classes_to_modify;
  JavaThread* const _thread;

  bool match(const InstanceKlass* klass) const;
  void do_klass(Klass* k);

 public:
  JfrFilterClassClosure(JavaThread* thread);
  void iterate_all_classes(GrowableArray<JfrInstrumentedClass>* instrumented_klasses);
  // Returned set is Resource allocated.
  ClosureSet* to_modify() const;
  int number_of_classes() const;
};

#endif // SHARE_JFR_SUPPORT_METHODTRACER_JFRFILTERCLASSCLOSURE_HPP
