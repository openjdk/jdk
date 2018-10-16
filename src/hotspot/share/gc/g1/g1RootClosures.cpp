/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/g1/g1OopClosures.inline.hpp"
#include "gc/g1/g1RootClosures.hpp"
#include "gc/g1/g1SharedClosures.hpp"

// Closures used for standard G1 evacuation.
class G1EvacuationClosures : public G1EvacuationRootClosures {
  G1SharedClosures<G1MarkNone> _closures;

public:
  G1EvacuationClosures(G1CollectedHeap* g1h,
                       G1ParScanThreadState* pss,
                       bool in_young_gc) :
      _closures(g1h, pss, in_young_gc, /* cld_claim */ ClassLoaderData::_claim_none) {}

  OopClosure* weak_oops()   { return &_closures._oops; }
  OopClosure* strong_oops() { return &_closures._oops; }

  CLDClosure* weak_clds()             { return &_closures._clds; }
  CLDClosure* strong_clds()           { return &_closures._clds; }
  CLDClosure* second_pass_weak_clds() { return NULL; }

  CodeBlobClosure* strong_codeblobs()      { return &_closures._codeblobs; }
  CodeBlobClosure* weak_codeblobs()        { return &_closures._codeblobs; }

  OopClosure* raw_strong_oops() { return &_closures._oops; }

  bool trace_metadata()         { return false; }
};

// Closures used during initial mark.
// The treatment of "weak" roots is selectable through the template parameter,
// this is usually used to control unloading of classes and interned strings.
template <G1Mark MarkWeak>
class G1InitialMarkClosures : public G1EvacuationRootClosures {
  G1SharedClosures<G1MarkFromRoot> _strong;
  G1SharedClosures<MarkWeak>       _weak;

  // Filter method to help with returning the appropriate closures
  // depending on the class template parameter.
  template <G1Mark Mark, typename T>
  T* null_if(T* t) {
    if (Mark == MarkWeak) {
      return NULL;
    }
    return t;
  }

public:
  G1InitialMarkClosures(G1CollectedHeap* g1h,
                        G1ParScanThreadState* pss) :
      _strong(g1h, pss, /* process_only_dirty_klasses */ false, /* cld_claim */ ClassLoaderData::_claim_strong),
      _weak(g1h, pss,   /* process_only_dirty_klasses */ false, /* cld_claim */ ClassLoaderData::_claim_strong) {}

  OopClosure* weak_oops()   { return &_weak._oops; }
  OopClosure* strong_oops() { return &_strong._oops; }

  // If MarkWeak is G1MarkPromotedFromRoot then the weak CLDs must be processed in a second pass.
  CLDClosure* weak_clds()             { return null_if<G1MarkPromotedFromRoot>(&_weak._clds); }
  CLDClosure* strong_clds()           { return &_strong._clds; }

  // If MarkWeak is G1MarkFromRoot then all CLDs are processed by the weak and strong variants
  // return a NULL closure for the following specialized versions in that case.
  CLDClosure* second_pass_weak_clds() { return null_if<G1MarkFromRoot>(&_weak._clds); }

  CodeBlobClosure* strong_codeblobs()      { return &_strong._codeblobs; }
  CodeBlobClosure* weak_codeblobs()        { return &_weak._codeblobs; }

  OopClosure* raw_strong_oops() { return &_strong._oops; }

  // If we are not marking all weak roots then we are tracing
  // which metadata is alive.
  bool trace_metadata()         { return MarkWeak == G1MarkPromotedFromRoot; }
};

G1EvacuationRootClosures* G1EvacuationRootClosures::create_root_closures(G1ParScanThreadState* pss, G1CollectedHeap* g1h) {
  G1EvacuationRootClosures* res = NULL;
  if (g1h->collector_state()->in_initial_mark_gc()) {
    if (ClassUnloadingWithConcurrentMark) {
      res = new G1InitialMarkClosures<G1MarkPromotedFromRoot>(g1h, pss);
    } else {
      res = new G1InitialMarkClosures<G1MarkFromRoot>(g1h, pss);
    }
  } else {
    res = new G1EvacuationClosures(g1h, pss, g1h->collector_state()->in_young_only_phase());
  }
  return res;
}
