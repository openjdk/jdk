/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "aot/aotLoader.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "jfr/leakprofiler/chains/bfsClosure.hpp"
#include "jfr/leakprofiler/chains/dfsClosure.hpp"
#include "jfr/leakprofiler/chains/edgeQueue.hpp"
#include "jfr/leakprofiler/chains/rootSetClosure.hpp"
#include "jfr/leakprofiler/utilities/unifiedOop.hpp"
#include "memory/universe.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/thread.hpp"
#include "services/management.hpp"
#include "utilities/align.hpp"

template <typename Delegate>
RootSetClosure<Delegate>::RootSetClosure(Delegate* delegate) : _delegate(delegate) {}

template <typename Delegate>
void RootSetClosure<Delegate>::do_oop(oop* ref) {
  assert(ref != NULL, "invariant");
  // We discard unaligned root references because
  // our reference tagging scheme will use
  // the lowest bit in a represented reference
  // to indicate the reference is narrow.
  // It is mainly roots delivered via nmethods::do_oops()
  // that come in unaligned. It should be ok to duck these
  // since they are supposedly weak.
  if (!is_aligned(ref, HeapWordSize)) {
    return;
  }

  assert(is_aligned(ref, HeapWordSize), "invariant");
  if (*ref != NULL) {
    _delegate->do_root(ref);
  }
}

template <typename Delegate>
void RootSetClosure<Delegate>::do_oop(narrowOop* ref) {
  assert(ref != NULL, "invariant");
  assert(is_aligned(ref, sizeof(narrowOop)), "invariant");
  const oop pointee = RawAccess<>::oop_load(ref);
  if (pointee != NULL) {
    _delegate->do_root(UnifiedOop::encode(ref));
  }
}

class RootSetClosureMarkScope : public MarkScope {};

template <typename Delegate>
void RootSetClosure<Delegate>::process() {
  RootSetClosureMarkScope mark_scope;
  CLDToOopClosure cldt_closure(this, ClassLoaderData::_claim_none);
  ClassLoaderDataGraph::always_strong_cld_do(&cldt_closure);
  CodeBlobToOopClosure blobs(this, false);
  Threads::oops_do(this, &blobs);
  ObjectSynchronizer::oops_do(this);
  Universe::oops_do(this);
  JNIHandles::oops_do(this);
  JvmtiExport::oops_do(this);
  SystemDictionary::oops_do(this);
  Management::oops_do(this);
  StringTable::oops_do(this);
  AOTLoader::oops_do(this);
}

template class RootSetClosure<BFSClosure>;
template class RootSetClosure<DFSClosure>;
