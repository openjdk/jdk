/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_COMPILER_CODECACHE_INLINE_HPP
#define SHARE_VM_COMPILER_CODECACHE_INLINE_HPP

#include "code/codeCache.hpp"

#include "code/nativeInst.hpp"

inline CodeBlob* CodeCache::find_blob_fast(void* pc) {
  int slot = 0;
  return find_blob_and_oopmap(pc, slot);
}

inline CodeBlob* CodeCache::find_blob_and_oopmap(void* pc, int& slot) {
  NativePostCallNop* nop = nativePostCallNop_at((address) pc);
  CodeBlob* cb;
  if (nop != nullptr && nop->displacement() != 0) {
    int offset = (nop->displacement() & 0xffffff);
    cb = (CodeBlob*) ((address) pc - offset);
    slot = ((nop->displacement() >> 24) & 0xff);
    assert(cb == CodeCache::find_blob(pc), "must be");
  } else {
    cb = CodeCache::find_blob(pc);
    slot = -1;
  }
  assert(cb != nullptr, "must be");
  return cb;
}

inline int CodeCache::find_oopmap_slot_fast(void* pc) {
  NativePostCallNop* nop = nativePostCallNop_at((address) pc);
  return (nop != nullptr && nop->displacement() != 0)
      ? ((nop->displacement() >> 24) & 0xff)
      : -1;
}

#endif // SHARE_VM_COMPILER_CODECACHE_INLINE_HPP
