/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_PPC_CONTINUATIONENTRY_PPC_INLINE_HPP
#define CPU_PPC_CONTINUATIONENTRY_PPC_INLINE_HPP

#include "runtime/continuationEntry.hpp"

#include "oops/method.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/registerMap.hpp"
#include "utilities/macros.hpp"

inline frame ContinuationEntry::to_frame() const {
  static CodeBlob* cb = CodeCache::find_blob_fast(entry_pc());
  assert(cb != nullptr, "");
  assert(cb->as_nmethod()->method()->is_continuation_enter_intrinsic(), "");
  return frame(entry_sp(), entry_pc(), entry_sp(), entry_fp(), cb);
}

inline intptr_t* ContinuationEntry::entry_fp() const {
  return (intptr_t*)((address)this + size());
}

inline void ContinuationEntry::update_register_map(RegisterMap* map) const {
// Nothing to do (no non-volatile registers in java calling convention)
}

#endif // CPU_PPC_CONTINUATIONENTRY_PPC_INLINE_HPP
