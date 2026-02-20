/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHREFERENCEPROCESSOR_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHREFERENCEPROCESSOR_INLINE_HPP

#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"

template <typename T>
T* reference_discovered_addr(oop reference) {
  return reinterpret_cast<T*>(java_lang_ref_Reference::discovered_addr_raw(reference));
}

template <typename T>
oop reference_discovered(oop reference) {
  T heap_oop = *reference_discovered_addr<T>(reference);
  return lrb(CompressedOops::decode(heap_oop));
}

template <typename ClosureType>
class ShenandoahOldDiscoveredMarker : public OopIterateClosure {
  ClosureType* _closure;
public:
  explicit ShenandoahOldDiscoveredMarker(ClosureType* closure) : _closure(closure) {}

  void do_oop(oop* o) override               { _closure->do_oop(o); }
  void do_oop(narrowOop* o) override         { _closure->do_oop(o); }
  bool do_metadata() override                { return _closure->do_metadata(); }
  void do_klass(Klass* k) override           { _closure->do_klass(k); }
  void do_cld(ClassLoaderData* cld) override { _closure->do_cld(cld); }
  void do_method(Method* m) override         { _closure->do_method(m); }
  void do_nmethod(nmethod* nm) override      { _closure->do_nmethod(nm); }

  ReferenceIterationMode reference_iteration_mode() override {
    return DO_FIELDS_EXCEPT_REFERENT;
  }
};

template <typename ClosureType>
void ShenandoahRefProcThreadLocal::mark_discovered_list(ClosureType* cl) {
  // We may have young references with old referents on the discovered lists of the
  // old generation reference processor. Since these references were "discovered",
  // none of them were marked. However, they also cannot be "processed" until old
  // marking is complete. We therefore have the unappetizing duty to keep these young
  // references alive until old marking is done. This is a form of nepotism. Note that
  // here we are only marking the references, not the referents.
  if (UseCompressedOops) {
    do_mark_discovered_list<narrowOop>(cl);
  } else {
    do_mark_discovered_list<oop>(cl);
  }
}

template <typename OopType, typename ClosureType>
void ShenandoahRefProcThreadLocal::do_mark_discovered_list(ClosureType* cl) {
  if (_discovered_list == nullptr) {
    return;
  }

  ShenandoahOldDiscoveredMarker<ClosureType> marker(cl);
  OopType* list = reinterpret_cast<OopType*>(&_discovered_list);
  while (list != nullptr) {
    const oop discovered_ref = CompressedOops::decode(*list);
    if (discovered_ref == nullptr) {
      break;
    }

    // We have discovered this reference, and it has an old referent. We must keep this young
    // reference itself alive until old reference processing is complete. If we don't do this,
    // an unreachable young reference will simply disappear, leaving a dangling pointer
    // in the list. Note that we cannot also simply remove young references from the list at the
    // end of young marking even if they are unreachable. If the reference has a queue associated
    // with it, we _must_ wait until old marking is complete before enqueueing the reference.
    discovered_ref->oop_iterate(&marker);

    // Discovered list terminates with a self-loop
    const oop discovered = reference_discovered<OopType>(discovered_ref);
    if (discovered_ref == discovered) {
      break;
    }
    list = reference_discovered_addr<OopType>(discovered_ref);
  }
}

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHREFERENCEPROCESSOR_INLINE_HPP

