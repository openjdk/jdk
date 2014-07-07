/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/metadataOnStackMark.hpp"
#include "code/codeCache.hpp"
#include "compiler/compileBroker.hpp"
#include "oops/metadata.hpp"
#include "prims/jvmtiImpl.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/thread.hpp"
#include "services/threadService.hpp"
#include "utilities/growableArray.hpp"


// Keep track of marked on-stack metadata so it can be cleared.
GrowableArray<Metadata*>* _marked_objects = NULL;
NOT_PRODUCT(bool MetadataOnStackMark::_is_active = false;)

// Walk metadata on the stack and mark it so that redefinition doesn't delete
// it.  Class unloading also walks the previous versions and might try to
// delete it, so this class is used by class unloading also.
MetadataOnStackMark::MetadataOnStackMark() {
  assert(SafepointSynchronize::is_at_safepoint(), "sanity check");
  NOT_PRODUCT(_is_active = true;)
  if (_marked_objects == NULL) {
    _marked_objects = new (ResourceObj::C_HEAP, mtClass) GrowableArray<Metadata*>(1000, true);
  }
  Threads::metadata_do(Metadata::mark_on_stack);
  CodeCache::alive_nmethods_do(nmethod::mark_on_stack);
  CompileBroker::mark_on_stack();
  JvmtiCurrentBreakpoints::metadata_do(Metadata::mark_on_stack);
  ThreadService::metadata_do(Metadata::mark_on_stack);
}

MetadataOnStackMark::~MetadataOnStackMark() {
  assert(SafepointSynchronize::is_at_safepoint(), "sanity check");
  // Unmark everything that was marked.   Can't do the same walk because
  // redefine classes messes up the code cache so the set of methods
  // might not be the same.
  for (int i = 0; i< _marked_objects->length(); i++) {
    _marked_objects->at(i)->set_on_stack(false);
  }
  _marked_objects->clear();   // reuse growable array for next time.
  NOT_PRODUCT(_is_active = false;)
}

// Record which objects are marked so we can unmark the same objects.
void MetadataOnStackMark::record(Metadata* m) {
  assert(_is_active, "metadata on stack marking is active");
  _marked_objects->push(m);
}
