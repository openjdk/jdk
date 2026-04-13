/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2025 Arm Limited and/or its affiliates.
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
#include "runtime/sharedRuntime.hpp"

inline CodeBlob* CodeCache::find_blob_fast(void* pc) {
  int slot = 0;
  return find_blob_and_oopmap(pc, slot);
}

inline CodeBlob* CodeCache::find_blob_and_oopmap(void* pc, int& slot) {
  NativePostCallNop* nop = nativePostCallNop_at((address) pc);
  CodeBlob* cb;
  int offset;
  if (nop != nullptr && nop->decode(slot, offset)) {
    cb = (CodeBlob*) ((address) pc - offset);
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
  int oopmap_slot;
  int cb_offset;
  if (nop != nullptr && nop->decode(oopmap_slot, cb_offset)) {
    return oopmap_slot;
  }
  return -1;
}

inline bool CodeCache::is_deopt_pc(address pc, bool strictly_compiled,
                                   CodeBlob* input_cb) {
  const DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();
  int subentry_index = -1;
  if (deopt_blob->get_unpack_subentry(pc, subentry_index)) {
    return true;
  }

  assert(input_cb == nullptr || input_cb == CodeCache::find_blob(pc),
         "Inconsistent input_cb");
  CodeBlob* cb = (input_cb != nullptr) ? input_cb : CodeCache::find_blob(pc);
  nmethod* nm = nullptr;
  if (cb != nullptr) {
    nm = cb->as_nmethod_or_null();
  }

  if (nm != nullptr) {
    return nm->is_deopt_pc(pc);
  } else {
    assert(!strictly_compiled, "this is not an nmethod");
  }

  return false;
}

inline address CodeCache::get_deopt_original_pc_and_cb(intptr_t* unextended_sp,
                                                       address pc,
                                                       CodeBlob* input_cb,
                                                       CodeBlob*& out_cb) {
  const DeoptimizationBlob* deopt_blob = SharedRuntime::deopt_blob();

  out_cb = nullptr;

  address original_pc = nullptr;
  if (deopt_blob->get_original_pc(unextended_sp, pc, original_pc)) {
    if (input_cb != nullptr && input_cb->is_nmethod()) {
      nmethod* nm = input_cb->as_nmethod();
      assert(nm->is_deopt_pc(pc), "mismatched deopt PC");
      out_cb = input_cb;
    } else {
      assert(input_cb == nullptr || input_cb == deopt_blob,
             "pc is expected to be of the sub entry points");
      out_cb = CodeCache::find_blob_fast(original_pc);
    }
    assert(out_cb != nullptr, "corrupted stack frame");
  } else {
#ifdef ASSERT
    if (input_cb != nullptr) {
      assert(input_cb == CodeCache::find_blob_fast(pc),
             "unexpected input_cb");
    }
#endif

    CodeBlob *cb = (input_cb != nullptr) ?
      input_cb : CodeCache::find_blob_fast(pc);
    if (cb != nullptr) {
      out_cb = cb;

      nmethod* nm = cb->as_nmethod_or_null();
      if (nm != nullptr && nm->is_deopt_pc(pc)) {
        address orig_pc_slot = (address) unextended_sp + nm->orig_pc_offset();
        original_pc = *(address*)orig_pc_slot;
      }
    }
  }

#ifdef ASSERT
  if (original_pc != nullptr) {
    nmethod* nm = CodeCache::find_nmethod(original_pc);
    assert(nm == out_cb, "mismatched code blob");
    assert(nm != nullptr && nm->is_deopt_pc(pc), "mismatched deopt PC");
  }
#endif

  return original_pc;
}

#endif // SHARE_VM_COMPILER_CODECACHE_INLINE_HPP
