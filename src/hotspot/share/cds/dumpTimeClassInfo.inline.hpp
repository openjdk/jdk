
/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_DUMPTIMECLASSINFO_INLINE_HPP
#define SHARE_CDS_DUMPTIMECLASSINFO_INLINE_HPP

#include "cds/dumpTimeClassInfo.hpp"

#include "cds/cdsConfig.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "runtime/safepoint.hpp"

#if INCLUDE_CDS

// For safety, only iterate over a class if its loader is alive.
// This function must be called only inside a safepoint, where the value of
// k->is_loader_alive() will not change.
template<typename Function>
void DumpTimeSharedClassTable::iterate_all_live_classes(Function function) const {
  auto wrapper = [&] (InstanceKlass* k, DumpTimeClassInfo& info) {
    assert(SafepointSynchronize::is_at_safepoint(), "invariant");
    assert_lock_strong(DumpTimeTable_lock);
    if (CDSConfig::is_dumping_final_static_archive() && !k->is_loaded()) {
      assert(k->defined_by_other_loaders(), "must be");
      function(k, info);
    } else if (k->is_loader_alive()) {
      function(k, info);
      assert(k->is_loader_alive(), "must not change");
    } else {
      if (!SystemDictionaryShared::is_excluded_class(k)) {
        SystemDictionaryShared::warn_excluded(k, "Class loader not alive");
        SystemDictionaryShared::set_excluded_locked(k);
      }
    }
  };
  DumpTimeSharedClassTableBaseType::iterate_all(wrapper);
}


template<class ITER>
void DumpTimeSharedClassTable::iterate_all_live_classes(ITER* iter) const {
  auto function = [&] (InstanceKlass* k, DumpTimeClassInfo& v) {
    iter->do_entry(k, v);
  };
  iterate_all_live_classes(function);
}

#endif // INCLUDE_CDS

#endif // SHARE_CDS_DUMPTIMECLASSINFO_INLINE_HPP
