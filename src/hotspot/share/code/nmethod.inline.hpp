/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CODE_NMETHOD_INLINE_HPP
#define SHARE_CODE_NMETHOD_INLINE_HPP

#include "code/nmethod.hpp"

#include "code/nativeInst.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/frame.hpp"

inline bool nmethod::is_deopt_pc(address pc) const {
  return pc == deopt_handler_entry();
}

inline int nmethod::orig_pc_offset() const {
  assert(_orig_pc_offset == align_down(_orig_pc_offset, BytesPerWord),
         "Required to be word-aligned.");
  return _orig_pc_offset;
}

inline address nmethod::deopt_handler_entry() const {
  int frame_size_in_words = frame_size();

#ifdef INCLUDE_JVMCI
  if (compiler_type() == compiler_jvmci) {
    assert(_deopt_handler_entry_offset >= 0, "JVMCI must provide deoptimization handler");
  }
#endif

  if (_deopt_handler_entry_offset >= 0) {
    if (compiler_type() != compiler_jvmci && !is_native_method()) {
      assert(AlwaysEmitDeoptStubCode
             || frame_size_in_words >= DeoptimizationBlob::UNPACK_SUBENTRY_COUNT,
             "This does not need nmethod-specific deoptimization handler entry point");
    }
    return header_begin() + _deopt_handler_entry_offset;
  } else {
    assert(!AlwaysEmitDeoptStubCode
           && frame_size_in_words < DeoptimizationBlob::UNPACK_SUBENTRY_COUNT,
           "This requires nmethod-specific deoptimization handler entry point");
    assert(_orig_pc_offset >= 0, "The original PC slot must exist");

    int orig_pc_offset_in_words = orig_pc_offset() >> LogBytesPerWord;
    assert(orig_pc_offset_in_words < frame_size_in_words, "Original PC slot is outside the frame");

    return SharedRuntime::deopt_blob()->unpack_subentry(orig_pc_offset_in_words);
  }
}

// class ExceptionCache methods

inline int ExceptionCache::count() { return AtomicAccess::load_acquire(&_count); }

address ExceptionCache::pc_at(int index) {
  assert(index >= 0 && index < count(),"");
  return _pc[index];
}

address ExceptionCache::handler_at(int index) {
  assert(index >= 0 && index < count(),"");
  return _handler[index];
}

// increment_count is only called under lock, but there may be concurrent readers.
inline void ExceptionCache::increment_count() { AtomicAccess::release_store(&_count, _count + 1); }


#endif // SHARE_CODE_NMETHOD_INLINE_HPP
