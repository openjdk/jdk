/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_CONTINUATIONFREEZETHAW_X86_INLINE_HPP
#define CPU_X86_CONTINUATIONFREEZETHAW_X86_INLINE_HPP

#include "code/codeBlob.inline.hpp"
#include "oops/stackChunkOop.inline.hpp"
#include "runtime/frame.hpp"
#include "runtime/frame.inline.hpp"

inline void patch_callee_link(const frame& f, intptr_t* fp) {
  *ContinuationHelper::Frame::callee_link_address(f) = fp;
}

inline void patch_callee_link_relative(const frame& f, intptr_t* fp) {
  intptr_t* la = (intptr_t*)ContinuationHelper::Frame::callee_link_address(f);
  intptr_t new_value = fp - la;
  *la = new_value;
}

////// Freeze

// Fast path

inline void FreezeBase::patch_stack_pd(intptr_t* frame_sp, intptr_t* heap_sp) {
  // copy the spilled rbp from the heap to the stack
  *(frame_sp - frame::sender_sp_offset) = *(heap_sp - frame::sender_sp_offset);
}

// Slow path

template<typename FKind>
inline frame FreezeBase::sender(const frame& f) {
  assert(FKind::is_instance(f), "");
  if (FKind::interpreted) {
    return frame(f.sender_sp(), f.interpreter_frame_sender_sp(), f.link(), f.sender_pc());
  }
  intptr_t** link_addr = link_address<FKind>(f);

  intptr_t* sender_sp = (intptr_t*)(link_addr + frame::sender_sp_offset); //  f.unextended_sp() + (fsize/wordSize); //
  address sender_pc = (address) *(sender_sp-1);
  assert(sender_sp != f.sp(), "must have changed");

  int slot = 0;
  CodeBlob* sender_cb = CodeCache::find_blob_and_oopmap(sender_pc, slot);
  return sender_cb != nullptr
    ? frame(sender_sp, sender_sp, *link_addr, sender_pc, sender_cb,
            slot == -1 ? nullptr : sender_cb->oop_map_for_slot(slot, sender_pc), false)
    : frame(sender_sp, sender_sp, *link_addr, sender_pc);
}

template<typename FKind>
frame FreezeBase::new_heap_frame(frame& f, frame& caller) {
  assert(FKind::is_instance(f), "");
  assert(!caller.is_interpreted_frame()
    || caller.unextended_sp() == (intptr_t*)caller.at(frame::interpreter_frame_last_sp_offset), "");

  intptr_t *sp, *fp; // sp is really our unextended_sp
  if (FKind::interpreted) {
    assert((intptr_t*)f.at_relative_or_null(frame::interpreter_frame_last_sp_offset) == nullptr
      || f.unextended_sp() == (intptr_t*)f.at_relative(frame::interpreter_frame_last_sp_offset), "");
    intptr_t locals_offset = *f.addr_at(frame::interpreter_frame_locals_offset);
    // If the caller.is_empty(), i.e. we're freezing into an empty chunk, then we set
    // the chunk's argsize in finalize_freeze and make room for it above the unextended_sp
    bool overlap_caller = caller.is_interpreted_frame() || caller.is_empty();
    fp = caller.unextended_sp() - 1 - locals_offset + (overlap_caller ? ContinuationHelper::InterpretedFrame::stack_argsize(f) : 0);
    sp = fp - (f.fp() - f.unextended_sp());
    assert(sp <= fp, "");
    assert(fp <= caller.unextended_sp(), "");
    caller.set_sp(fp + frame::sender_sp_offset);

    assert(_cont.tail()->is_in_chunk(sp), "");

    frame hf(sp, sp, fp, f.pc(), nullptr, nullptr, true /* on_heap */);
    // copy relativized locals from the stack frame
    *hf.addr_at(frame::interpreter_frame_locals_offset) = locals_offset;
    return hf;
  } else {
    // We need to re-read fp out of the frame because it may be an oop and we might have
    // had a safepoint in finalize_freeze, after constructing f.
    fp = *(intptr_t**)(f.sp() - frame::sender_sp_offset);

    int fsize = FKind::size(f);
    sp = caller.unextended_sp() - fsize;
    if (caller.is_interpreted_frame()) {
      // If the caller is interpreted, our stackargs are not supposed to overlap with it
      // so we make more room by moving sp down by argsize
      int argsize = FKind::stack_argsize(f);
      sp -= argsize;
    }
    caller.set_sp(sp + fsize);

    assert(_cont.tail()->is_in_chunk(sp), "");

    return frame(sp, sp, fp, f.pc(), nullptr, nullptr, true /* on_heap */);
  }
}

void FreezeBase::adjust_interpreted_frame_unextended_sp(frame& f) {
  assert((f.at(frame::interpreter_frame_last_sp_offset) != 0) || (f.unextended_sp() == f.sp()), "");
  intptr_t* real_unextended_sp = (intptr_t*)f.at_relative_or_null(frame::interpreter_frame_last_sp_offset);
  if (real_unextended_sp != nullptr) {
    f.set_unextended_sp(real_unextended_sp); // can be null at a safepoint
  }
}

static inline void relativize_one(intptr_t* const vfp, intptr_t* const hfp, int offset) {
  assert(*(hfp + offset) == *(vfp + offset), "");
  intptr_t* addr = hfp + offset;
  intptr_t value = *(intptr_t**)addr - vfp;
  *addr = value;
}

inline void FreezeBase::relativize_interpreted_frame_metadata(const frame& f, const frame& hf) {
  intptr_t* vfp = f.fp();
  intptr_t* hfp = hf.fp();
  assert(hfp == hf.unextended_sp() + (f.fp() - f.unextended_sp()), "");
  assert((f.at(frame::interpreter_frame_last_sp_offset) != 0)
    || (f.unextended_sp() == f.sp()), "");
  assert(f.fp() > (intptr_t*)f.at(frame::interpreter_frame_initial_sp_offset), "");

  // Make sure that last_sp is already relativized.
  assert((intptr_t*)hf.at_relative(frame::interpreter_frame_last_sp_offset) == hf.unextended_sp(), "");

  // Make sure that locals is already relativized.
  assert((*hf.addr_at(frame::interpreter_frame_locals_offset) == frame::sender_sp_offset + f.interpreter_frame_method()->max_locals() - 1), "");

  relativize_one(vfp, hfp, frame::interpreter_frame_initial_sp_offset); // == block_top == block_bottom

  assert((hf.fp() - hf.unextended_sp()) == (f.fp() - f.unextended_sp()), "");
  assert(hf.unextended_sp() == (intptr_t*)hf.at(frame::interpreter_frame_last_sp_offset), "");
  assert(hf.unextended_sp() <= (intptr_t*)hf.at(frame::interpreter_frame_initial_sp_offset), "");
  assert(hf.fp()            >  (intptr_t*)hf.at(frame::interpreter_frame_initial_sp_offset), "");
  assert(hf.fp()            <= (intptr_t*)hf.at(frame::interpreter_frame_locals_offset), "");
}

inline void FreezeBase::set_top_frame_metadata_pd(const frame& hf) {
  stackChunkOop chunk = _cont.tail();
  assert(chunk->is_in_chunk(hf.sp() - 1), "");
  assert(chunk->is_in_chunk(hf.sp() - frame::sender_sp_offset), "");

  address frame_pc = hf.pc();

  *(hf.sp() - 1) = (intptr_t)hf.pc();

  intptr_t* fp_addr = hf.sp() - frame::sender_sp_offset;
  *fp_addr = hf.is_interpreted_frame() ? (intptr_t)(hf.fp() - fp_addr)
                                       : (intptr_t)hf.fp();
  assert(frame_pc == ContinuationHelper::Frame::real_pc(hf), "");
}

inline void FreezeBase::patch_pd(frame& hf, const frame& caller) {
  if (caller.is_interpreted_frame()) {
    assert(!caller.is_empty(), "");
    patch_callee_link_relative(caller, caller.fp());
  } else {
    // If we're the bottom-most frame frozen in this freeze, the caller might have stayed frozen in the chunk,
    // and its oop-containing fp fixed. We've now just overwritten it, so we must patch it back to its value
    // as read from the chunk.
    patch_callee_link(caller, caller.fp());
  }
}

//////// Thaw

// Fast path

inline void ThawBase::prefetch_chunk_pd(void* start, int size) {
  size <<= LogBytesPerWord;
  Prefetch::read(start, size);
  Prefetch::read(start, size - 64);
}

template <typename ConfigT>
inline void Thaw<ConfigT>::patch_caller_links(intptr_t* sp, intptr_t* bottom) {
  // Fast path depends on !PreserveFramePointer. See can_thaw_fast().
  assert(!PreserveFramePointer, "Frame pointers need to be fixed");
}

// Slow path

inline frame ThawBase::new_entry_frame() {
  intptr_t* sp = _cont.entrySP();
  return frame(sp, sp, _cont.entryFP(), _cont.entryPC()); // TODO PERF: This finds code blob and computes deopt state
}

template<typename FKind> frame ThawBase::new_stack_frame(const frame& hf, frame& caller, bool bottom) {
  assert(FKind::is_instance(hf), "");
  // The values in the returned frame object will be written into the callee's stack in patch.

  if (FKind::interpreted) {
    intptr_t* heap_sp = hf.unextended_sp();
    // If caller is interpreted it already made room for the callee arguments
    int overlap = caller.is_interpreted_frame() ? ContinuationHelper::InterpretedFrame::stack_argsize(hf) : 0;
    const int fsize = (int)(ContinuationHelper::InterpretedFrame::frame_bottom(hf) - hf.unextended_sp() - overlap);
    const int locals = hf.interpreter_frame_method()->max_locals();
    intptr_t* frame_sp = caller.unextended_sp() - fsize;
    intptr_t* fp = frame_sp + (hf.fp() - heap_sp);
    DEBUG_ONLY(intptr_t* unextended_sp = fp + *hf.addr_at(frame::interpreter_frame_last_sp_offset);)
    assert(frame_sp == unextended_sp, "");
    caller.set_sp(fp + frame::sender_sp_offset);
    frame f(frame_sp, frame_sp, fp, hf.pc());
    // we need to set the locals so that the caller of new_stack_frame() can call
    // ContinuationHelper::InterpretedFrame::frame_bottom
    intptr_t locals_offset = *hf.addr_at(frame::interpreter_frame_locals_offset);
    assert((int)locals_offset == frame::sender_sp_offset + locals - 1, "");
    // copy relativized locals from the heap frame
    *f.addr_at(frame::interpreter_frame_locals_offset) = locals_offset;
    return f;
  } else {
    int fsize = FKind::size(hf);
    intptr_t* frame_sp = caller.unextended_sp() - fsize;
    if (bottom || caller.is_interpreted_frame()) {
      int argsize = hf.compiled_frame_stack_argsize();

      fsize += argsize;
      frame_sp   -= argsize;
      caller.set_sp(caller.sp() - argsize);
      assert(caller.sp() == frame_sp + (fsize-argsize), "");

      frame_sp = align(hf, frame_sp, caller, bottom);
    }

    assert(hf.cb() != nullptr, "");
    assert(hf.oop_map() != nullptr, "");
    intptr_t* fp;
    if (PreserveFramePointer) {
      // we need to recreate a "real" frame pointer, pointing into the stack
      fp = frame_sp + FKind::size(hf) - frame::sender_sp_offset;
    } else {
       // we need to re-read fp because it may be an oop and we might have fixed the frame.
      fp = *(intptr_t**)(hf.sp() - frame::sender_sp_offset);
    }
    return frame(frame_sp, frame_sp, fp, hf.pc(), hf.cb(), hf.oop_map(), false); // TODO PERF : this computes deopt state; is it necessary?
  }
}

inline intptr_t* ThawBase::align(const frame& hf, intptr_t* frame_sp, frame& caller, bool bottom) {
#ifdef _LP64
  if (((intptr_t)frame_sp & 0xf) != 0) {
    assert(caller.is_interpreted_frame() || (bottom && hf.compiled_frame_stack_argsize() % 2 != 0), "");
    frame_sp--;
    caller.set_sp(caller.sp() - 1);
  }
  assert(is_aligned(frame_sp, frame::frame_alignment), "");
#endif

  return frame_sp;
}

inline void ThawBase::patch_pd(frame& f, const frame& caller) {
  patch_callee_link(caller, caller.fp());
}

static inline void derelativize_one(intptr_t* const fp, int offset) {
  intptr_t* addr = fp + offset;
  *addr = (intptr_t)(fp + *addr);
}

inline void ThawBase::derelativize_interpreted_frame_metadata(const frame& hf, const frame& f) {
  intptr_t* vfp = f.fp();

  // Make sure that last_sp is kept relativized.
  assert((intptr_t*)f.at_relative(frame::interpreter_frame_last_sp_offset) == f.unextended_sp(), "");

  derelativize_one(vfp, frame::interpreter_frame_initial_sp_offset);
}

#endif // CPU_X86_CONTINUATIONFREEZE_THAW_X86_INLINE_HPP
