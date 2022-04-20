
/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARED_CDS_DUMPTIMESHAREDCLASSINFO_INLINE_HPP
#define SHARED_CDS_DUMPTIMESHAREDCLASSINFO_INLINE_HPP

#include "cds/dumpTimeClassInfo.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "runtime/safepoint.hpp"

#if INCLUDE_CDS

// For safety, only iterate over a class if it loader is alive.
// IterationHelper and DumpTimeSharedClassTable::iterate
// must be used only inside a safepoint, where the value of
// k->is_loader_alive() will not change.
template<class ITER>
class DumpTimeSharedClassTable::IterationHelper {
  ITER* _iter;
public:
  IterationHelper(ITER* iter) {
    _iter = iter;
  }
  bool do_entry(InstanceKlass* k, DumpTimeClassInfo& info) {
    assert(SafepointSynchronize::is_at_safepoint(), "invariant");
    assert_lock_strong(DumpTimeTable_lock);
    if (k->is_loader_alive()) {
      bool result = _iter->do_entry(k, info);
      assert(k->is_loader_alive(), "must not change");
      return result;
    } else {
      if (!SystemDictionaryShared::is_excluded_class(k)) {
        SystemDictionaryShared::warn_excluded(k, "Class loader not alive");
        SystemDictionaryShared::set_excluded_locked(k);
      }
      return true;
    }
  }
};

template<class ITER>
void DumpTimeSharedClassTable::iterate(ITER* iter) const {
  IterationHelper<ITER> helper(iter);
  DumpTimeSharedClassTableBaseType::iterate(&helper);
}

#endif // INCLUDE_CDS

#endif // SHARED_CDS_DUMPTIMESHAREDCLASSINFO_INLINE_HPP
