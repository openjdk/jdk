/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SERIAL_SERIALHEAP_INLINE_HPP
#define SHARE_GC_SERIAL_SERIALHEAP_INLINE_HPP

#include "gc/serial/serialHeap.hpp"

#include "gc/serial/tenuredGeneration.inline.hpp"
#include "oops/oop.inline.hpp"

class ScavengeHelper {
  DefNewGeneration* _young_gen;
  HeapWord*         _young_gen_end;
public:
  ScavengeHelper(DefNewGeneration* young_gen) :
    _young_gen(young_gen),
    _young_gen_end(young_gen->reserved().end()) {}

  bool is_in_young_gen(void* p) const {
    return p < _young_gen_end;
  }

  template <typename T, typename Func>
  void try_scavenge(T* p, Func&& f) {
    T heap_oop = RawAccess<>::oop_load(p);
    // Should we copy the obj?
    if (!CompressedOops::is_null(heap_oop)) {
      oop obj = CompressedOops::decode_not_null(heap_oop);
      if (is_in_young_gen(obj)) {
        assert(!_young_gen->to()->is_in_reserved(obj), "Scanning field twice?");
        oop new_obj = obj->is_forwarded() ? obj->forwardee()
                                          : _young_gen->copy_to_survivor_space(obj);
        RawAccess<IS_NOT_NULL>::oop_store(p, new_obj);

        // callback
        f(new_obj);
      }
    }
  }
};

class InHeapScanClosure : public BasicOopIterateClosure {
  ScavengeHelper _helper;
protected:
  bool is_in_young_gen(void* p) const {
    return _helper.is_in_young_gen(p);
  }

  template <typename T, typename Func>
  void try_scavenge(T* p, Func&& f) {
    _helper.try_scavenge(p, f);
  }

  InHeapScanClosure(DefNewGeneration* young_gen) :
    BasicOopIterateClosure(young_gen->ref_processor()),
    _helper(young_gen) {}
};

class OffHeapScanClosure : public OopClosure {
  ScavengeHelper _helper;
protected:
  bool is_in_young_gen(void* p) const {
    return _helper.is_in_young_gen(p);
  }

  template <typename T, typename Func>
  void try_scavenge(T* p, Func&& f) {
    _helper.try_scavenge(p, f);
  }

  OffHeapScanClosure(DefNewGeneration* young_gen) :  _helper(young_gen) {}
};

class YoungGenScanClosure : public InHeapScanClosure {
  template <typename T>
  void do_oop_work(T* p) {
    assert(SerialHeap::heap()->young_gen()->to()->is_in_reserved(p), "precondition");

    try_scavenge(p, [] (auto) {});
  }
public:
  YoungGenScanClosure(DefNewGeneration* g) : InHeapScanClosure(g) {}

  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
};

class OldGenScanClosure : public InHeapScanClosure {
  CardTableRS* _rs;

  template <typename T>
  void do_oop_work(T* p) {
    assert(!is_in_young_gen(p), "precondition");

    try_scavenge(p, [&] (oop new_obj) {
      // If p points to a younger generation, mark the card.
      if (is_in_young_gen(new_obj)) {
        _rs->inline_write_ref_field_gc(p);
      }
    });
  }
public:
  OldGenScanClosure(DefNewGeneration* g) : InHeapScanClosure(g),
                                           _rs(SerialHeap::heap()->rem_set()) {}

  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
};

#endif // SHARE_GC_SERIAL_SERIALHEAP_INLINE_HPP
