
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

// For safety, do not iterate over a class if it loader
// was not marked as eligble for dumpimg.
template<class ITER>
class EligibleClassIterationHelper {
  ITER* _iter;
public:
  EligibleClassIterationHelper(ITER* iter) {
    _iter = iter;
  }
  bool do_entry(InstanceKlass* k, DumpTimeClassInfo& info) {
    assert_lock_strong(DumpTimeTable_lock);
    if (k->class_loader_data()->eligible_for_dumping()) {
      assert(k->is_loader_alive(), "must be");
      return _iter->do_entry(k, info);
    } else {
      if (!SystemDictionaryShared::is_excluded_class(k)) {
        SystemDictionaryShared::warn_excluded(k, "Class loader not eligible");
        SystemDictionaryShared::set_excluded_locked(k);
      }
      return true;
    }
  }
};

template<class ITER>
void DumpTimeSharedClassTable::iterate(ITER* iter) const {
  EligibleClassIterationHelper<ITER> helper(iter);
  DumpTimeSharedClassTableBaseType::iterate(&helper);
}

#endif // SHARED_CDS_DUMPTIMESHAREDCLASSINFO_INLINE_HPP
