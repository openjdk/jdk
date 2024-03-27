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

#ifndef CPU_PPC_CONTINUATION_PPC_INLINE_HPP
#define CPU_PPC_CONTINUATION_PPC_INLINE_HPP

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
  // Nothing to do. The backchain is reconstructed when thawing (see Thaw<ConfigT>::patch_caller_links())
}

// Slow path

template<typename FKind>
inline frame FreezeBase::sender(const frame& f) {
  assert(FKind::is_instance(f), "");
  if (FKind::interpreted) {
    return frame(f.sender_sp(), f.sender_pc(), f.interpreter_frame_sender_sp());
  }

  intptr_t* sender_sp = f.sender_sp();
  address sender_pc = f.sender_pc();
  assert(sender_sp != f.sp(), "must have changed");

  int slot = 0;
  CodeBlob* sender_cb = CodeCache::find_blob_and_oopmap(sender_pc, slot);
  return sender_cb != nullptr
    ? frame(sender_sp, sender_sp, nullptr, sender_pc, sender_cb, slot == -1 ? nullptr : sender_cb->oop_map_for_slot(slot, sender_pc))
    : frame(sender_sp, sender_pc, sender_sp);
}

void FreezeBase::adjust_interpreted_frame_unextended_sp(frame& f) {
  // nothing to do
}

inline void FreezeBase::relativize_interpreted_frame_metadata(const frame& f, const frame& hf) {
  intptr_t* vfp = f.fp();
  intptr_t* hfp = hf.fp();
  assert(f.fp() > (intptr_t*)f.interpreter_frame_esp(), "");

  // There is alignment padding between vfp and f's locals array in the original
  // frame, because we freeze the padding (see recurse_freeze_interpreted_frame)
  // in order to keep the same relativized locals pointer, we don't need to change it here.

  // Make sure that monitors is already relativized.
  assert(hf.at_absolute(ijava_idx(monitors)) <= -(frame::ijava_state_size / wordSize), "");

  // Make sure that esp is already relativized.
  assert(hf.at_absolute(ijava_idx(esp)) <= hf.at_absolute(ijava_idx(monitors)), "");

  // top_frame_sp is already relativized

  // hfp == hf.sp() + (f.fp() - f.sp()) is not true on ppc because the stack frame has room for
  // the maximal expression stack and the expression stack in the heap frame is trimmed.
  assert(hf.fp() == hf.interpreter_frame_esp() + (f.fp() - f.interpreter_frame_esp()), "");
  assert(hf.fp()                 <= (intptr_t*)hf.at(ijava_idx(locals)), "");
}

inline void FreezeBase::set_top_frame_metadata_pd(const frame& hf) {
  stackChunkOop chunk = _cont.tail();
  assert(chunk->is_in_chunk(hf.sp()), "hf.sp()=" PTR_FORMAT, p2i(hf.sp()));

  hf.own_abi()->lr = (uint64_t)hf.pc();
  if (hf.is_interpreted_frame()) {
    patch_callee_link_relative(hf, hf.fp());
  }
#ifdef ASSERT
  else {
    // See also FreezeBase::patch_pd()
    patch_callee_link(hf, (intptr_t*)badAddress);
  }
#endif
}

//
// Heap frames differ from stack frames in the following aspects
//
// - they are just word aligned
// - the unextended sp of interpreted frames is set such that
//   unextended sp + frame::metadata_words_at_top + 1 points to the last call parameter
//   (the comment at the file end explains the unextended sp for interpreted frames on the stack)
//
// The difference in respect to the unextended sp is required to comply with shared code.
// Furthermore fast frozen and compiled frames have invalid back links (see
// Thaw<ConfigT>::patch_caller_links() and FreezeBase::patch_pd())
//
// === New Interpreted Frame ==========================================================================================
//
// ### Interpreted Caller: Overlap new frame with Caller
//
//     Caller on entry                                     New frame with resized Caller
//
//     | frame::java_abi        |                          |                        |
//     |                        |<- FP of caller           | Caller's SP            |<- FP of caller
//     ==========================                          ==========================
//     | ijava_state            |                          | ijava_state            |
//     |                        |                          |                        |
//     |------------------------|                  -----   |------------------------|
//     | P0                     |                    ^     | L0 aka P0              |
//     | :                      |                    |     | :      :               |
//     | Pn                     |<- unext. SP        |     | :      Pn              |<- unext. SP
//     |------------------------|   + metadata     overlap | :                      |   + metadata
//     | frame::java_abi        |                    |     | Lm                     |
//     | (metadata_words_at_top)|<- SP == unext. SP  v     |------------------------|<- unextended SP of caller (1)
//     ==========================   of caller      -----   | frame::java_abi        |
//                                                         | (metadata_words_at_top)|<- new SP of caller / FP of new frame
//      overlap = stack_argsize(f)                         ==========================       ^
//                + frame::metadata_words_at_top           | ijava_state            |       |
//                                                         |                        |       |
//      Where f is the frame to be relocated on the heap.  |------------------------|       |
//      See also StackChunkFrameStream::frame_size().      | Expressions            |   FP - esp of f
//                                                         | P0                     |       |
//                                                         | :                      |       |
//                            |  Growth  |                 | Pi                     |       v
//                            v          v                 |------------------------|      ---
//                                                         | frame::java_abi        |
//                                                         | (metadata_words_at_top)|<- unextended SP /
//                                                         ==========================   SP of new frame
// ### Compiled Caller: No Overlap
//
//     The caller is resized to accomodate the callee's locals and abi but there is _no_ overlap with
//     the original caller frame.
//
//     Caller on entry                                     New frame with resized Caller
//
//     | frame::java_abi        |                          |                        |
//     | (metadata_words_at_top)|<- FP of caller           | Caller's SP            |<- FP of caller
//     ==========================                          ==========================
//     |                        |                          |                        |
//     |                        |                          |                        |
//     |------------------------|                          |------------------------|
//     | frame::java_abi        |                          | frame::java_abi        |
//     | (metadata_words_at_top)|<- SP == unext. SP        | (metadata_words_at_top)|<- unext. SP of caller
//     ==========================   of caller              |------------------------|
//                                                         | L0 aka P0              |
//                                                         | :      :               |
//                                                         | :      Pn              |
//      overlap = 0                                        | Lm                     |
//                                                         |------------------------|
//      f is the frame to be relocated on the heap         | frame::java_abi        |
//                                                         | (metadata_words_at_top)|<- new SP of caller / FP of new frame
//                                                         ==========================       ^
//                                                         | ijava_state            |       |
//                             |  Growth  |                |                        |       |
//                             v          v                |------------------------|       |
//                                                         | Expressions            |   FP - esp of f
//                                                         | P0                     |       |
//                                                         | :                      |       |
//                                                         | Pi                     |       v
//                                                         |------------------------|      ---
//                                                         | frame::java_abi        |
//                                                         | (metadata_words_at_top)|<- unextended SP /
//                                                         ==========================   SP of new frame
//
// (1) Caller's unextended SP is preserved in callee's frame::ijava_state::sender_sp
//     (See ContinuationHelper::InterpretedFrame::patch_sender_sp). This is required
//     by StackChunkFrameStream<frame_kind>::next_for_interpreter_frame().
//
// === New Compiled Frame =============================================================================================
//
// ### Interpreted Caller: No Overlap
//
//     The caller is resized to accomodate the callee's stack arguments and abi but there is _no_ overlap with
//     the original caller frame.
//
//     Note: a new ABI is added to the caller even if there are no stackargs.
//     This is necessary to comply with shared code.
//
//     Caller on entry                                     New frame with resized Caller
//
//     | frame::java_abi        |                          | frame::java_abi        |
//     | (metadata_words_at_top)|<- FP of caller           | (metadata_words_at_top)|<- FP of caller
//     ==========================                          ==========================
//     | ijava_state            |                          | ijava_state            |
//     |                        |                          |                        |
//     |------------------------|                          |------------------------|
//     | P0                     |                          | P0                     |
//     | :                      |                          | :                      |
//     | Pn                     |<- unext. SP              | Pn                     |<- unext. SP
//     |------------------------|   + metadata             |------------------------|   + metadata
//     | frame::java_abi        |                          | frame::java_abi        |
//     | (metadata_words_at_top)|<- SP == unext. SP        | (metadata_words_at_top)|<- unextended SP of caller (1)
//     ==========================   of caller              |------------------------|
//                                                         | Stack Args             |
//      overlap = 0                                        | (if any)               |
//                                                         |------------------------|
//      f is the frame to be relocated on the heap         | frame::java_abi        |
//                                                         | (metadata_words_at_top)|<- new SP of caller / FP of new frame
//                                                         ==========================
//                                                         |                        |
//                             |  Growth  |                |                        |
//                             v          v                |------------------------|
//                                                         | frame::java_abi        |
//                                                         | (metadata_words_at_top)|<- SP == unext. SP of new frame
//                                                         ==========================
//
// ### Compiled Caller: Stackargs + ABI Overlap
//
//     Caller on entry                                     New frame with resized Caller
//
//     | frame::java_abi        |                          | frame::java_abi        |
//     | (metadata_words_at_top)|<- FP of caller           | (metadata_words_at_top)|<- FP of caller
//     ==========================                          ==========================
//     |                        |                          |                        |
//     |                        |                          |                        |
//     |------------------------|                   -----  |------------------------|
//     | Stack Args             |                     ^    | Stack Args             |
//     | (if any)               |                     |    | (if any)               |
//     |------------------------|                  overlap |------------------------|
//     | frame::java_abi        |                     |    | frame::java_abi        |
//     | (metadata_words_at_top)|<- SP == unext. SP   v    | (metadata_words_at_top)|<- SP == unext. SP of caller
//     ==========================   of caller       -----  ==========================   / FP of new frame
//                                                         |                        |
//      overlap = stack_argsize(f)                         |                        |
//                + frame::metadata_words_at_top           |------------------------|
//                                                         | frame::java_abi        |
//      Where f is the frame to be relocated on the heap.  | (metadata_words_at_top)|<- SP == unext. SP of new frame
//      See also StackChunkFrameStream::frame_size().      ==========================
//
template<typename FKind>
frame FreezeBase::new_heap_frame(frame& f, frame& caller) {
  assert(FKind::is_instance(f), "");

  intptr_t *sp, *fp;
  if (FKind::interpreted) {
    intptr_t locals_offset = *f.addr_at(ijava_idx(locals));
    // If the caller.is_empty(), i.e. we're freezing into an empty chunk, then we set
    // the chunk's argsize in finalize_freeze and make room for it above the unextended_sp
    // See also comment on StackChunkFrameStream<frame_kind>::interpreter_frame_size()
    int overlap =
        (caller.is_interpreted_frame() || caller.is_empty())
        ? ContinuationHelper::InterpretedFrame::stack_argsize(f) + frame::metadata_words_at_top
        : 0;
    fp = caller.unextended_sp() - 1 - locals_offset + overlap;
    // esp points one slot below the last argument
    intptr_t* x86_64_like_unextended_sp = f.interpreter_frame_esp() + 1 - frame::metadata_words_at_top;
    sp = fp - (f.fp() - x86_64_like_unextended_sp);

    assert (sp <= fp && (fp <= caller.unextended_sp() || caller.is_interpreted_frame()),
            "sp=" PTR_FORMAT " fp=" PTR_FORMAT " caller.unextended_sp()=" PTR_FORMAT " caller.is_interpreted_frame()=%d",
            p2i(sp), p2i(fp), p2i(caller.unextended_sp()), caller.is_interpreted_frame());
    caller.set_sp(fp);

    assert(_cont.tail()->is_in_chunk(sp), "");

    frame hf(sp, sp, fp, f.pc(), nullptr, nullptr, true /* on_heap */);
    // frame_top() and frame_bottom() read these before relativize_interpreted_frame_metadata() is called
    *hf.addr_at(ijava_idx(locals)) = locals_offset;
    *hf.addr_at(ijava_idx(esp))    = f.interpreter_frame_esp() - f.fp();
    return hf;
  } else {
    int fsize = FKind::size(f);
    sp = caller.unextended_sp() - fsize;
    if (caller.is_interpreted_frame()) {
      // If the caller is interpreted, our stackargs are not supposed to overlap with it
      // so we make more room by moving sp down by argsize
      int argsize = FKind::stack_argsize(f);
      sp -= argsize + frame::metadata_words_at_top;
    }
    fp = sp + fsize;
    caller.set_sp(fp);

    assert(_cont.tail()->is_in_chunk(sp), "");

    return frame(sp, sp, fp, f.pc(), nullptr, nullptr, true /* on_heap */);
  }
}

inline void FreezeBase::patch_pd(frame& hf, const frame& caller) {
  if (caller.is_interpreted_frame()) {
    assert(!caller.is_empty(), "");
    patch_callee_link_relative(caller, caller.fp());
  }
#ifdef ASSERT
  else {
    // For compiled frames the back link is actually redundant. It gets computed
    // as unextended_sp + frame_size.

    // Note the difference on x86_64: the link is not made relative if the caller
    // is a compiled frame because there rbp is used as a non-volatile register by
    // c1/c2 so it could be a computed value local to the caller.

    // See also:
    // - FreezeBase::set_top_frame_metadata_pd
    // - StackChunkFrameStream<frame_kind>::fp()
    // - UseContinuationFastPath: compiled frames are copied in a batch w/o patching the back link.
    //   The backlinks are restored when thawing (see Thaw<ConfigT>::patch_caller_links())
    patch_callee_link(hf, (intptr_t*)badAddress);
  }
#endif
}

//////// Thaw

// Fast path

inline void ThawBase::prefetch_chunk_pd(void* start, int size) {
  size <<= LogBytesPerWord;
  Prefetch::read(start, size);
  Prefetch::read(start, size - 64);
}

// Set back chain links of fast thawed frames such that *sp == callers_sp.
// See https://refspecs.linuxfoundation.org/ELF/ppc64/PPC-elf64abi.html#STACK
template <typename ConfigT>
inline void Thaw<ConfigT>::patch_caller_links(intptr_t* sp, intptr_t* bottom) {
  for (intptr_t* callers_sp; sp < bottom; sp = callers_sp) {
    address pc = (address)((frame::java_abi*) sp)->lr;
    assert(pc != nullptr, "");
    // see ThawBase::patch_return() which gets called just before
    bool is_entry_frame = pc == StubRoutines::cont_returnBarrier() || pc == _cont.entryPC();
    if (is_entry_frame) {
      callers_sp = _cont.entryFP();
    } else {
      CodeBlob* cb = CodeCache::find_blob_fast(pc);
      callers_sp = sp + cb->frame_size();
    }
    // set the back link
    ((frame::java_abi*) sp)->callers_sp = (intptr_t) callers_sp;
  }
}

// Slow path

inline frame ThawBase::new_entry_frame() {
  intptr_t* sp = _cont.entrySP();
  return frame(sp, _cont.entryPC(), sp, _cont.entryFP());
}

// === New Interpreted Frame ================================================================================================================
//
// ### Non-Interpreted Caller (compiled, enterSpecial): No Overlap
//
//     Heap Frame `hf`                                   `hf` gets copied to stack _without_ overlapping the caller
//
//     |                      |                            Non-Interpreted |                      |
//     |                      |<- bottom                   Caller          |----------------------|
//     |----------------------|    ^                                       | frame::java_abi      |<- unextended SP
//     | L0 aka P0            |    |                                   --- ========================
//     | :      :             |    |                                    ^  | L0 aka P0            |
//     | :      Pn            |    |                                    |  | :      :             | Parameters do
//     | :                    |    |                                    |  | :      Pn            | not overlap with
//     | Lm                   |    |                                    |  | :                    | caller!
//     |----------------------| `fsize`                                 |  | :                    |
//     | frame::java_abi      |    |                                       | :                    |
//     ========================    |                     `fsize` + padding | Lm                   |
//     |                      |    |                                       |----------------------|
//     | ijava_state          |    |                                    |  | Opt. Align. Padding  |
//     |                      |    |                                    |  |----------------------|
//     |----------------------|    |                                    |  | frame::java_abi      |<- new SP of caller
//     | L0 aka P0            |    |                                    |  ========================   / FP of new frame
//     | :      :             |    |                                    |  |                      |   (aligned)
//     | :      Pn            |<- unext. SP + metadata                  |  | ijava_state          |
//     | :                    |    |                                    |  |                      |
//     | Lm                   |    |                                    |  |----------------------|
//     |----------------------|    v                                    |  | P0                   |
//     | frame::java_abi      |<- SP / unextended SP                    |  | :                    |
//     ========================                                         |  | Pi                   |<- unextended SP + metadata
//                                                                      |  |----------------------|
//                                           | Growth |                 v  | frame::java_abi      |<- unextended SP / SP of new frame
//                                           v        v                --- ========================   (not yet aligned(1))
//
//
// ### Interpreted Caller: Overlap with Caller
//
//     Caller                                                              New frame with resized/aligned Caller
//
//     |                      |                                            |                      |
//     | ijava_state          |                                            | ijava_state          |
//     |----------------------|                                            |----------------------|
//     | non param. expr.     |                                     bottom | non param. expr.     |
//     | - - - - - - - - - -  |                           ---           ^  | - - - - - - - - - -  |
//     | P0                   |                            ^            |  | L0 aka P0            |
//     | :                    |                            |            |  | :      :             |
//     | Pn                   |<- unextended SP           overlap       |  | :      Pn            |<- unextended SP
//     |----------------------|   + metadata_words_at_top  |            |  | :                    |   + metadata_words_at_top
//     | frame::java_abi      |<- unextended SP            v            |  | :                    |   (unaligned)
//     ========================   / SP of new frame       ---           |  | :                    |   of caller
//                                (not yet aligned(1))                  |  | Lm                   |
//                                                                `fsize`  |----------------------|
//       overlap = stack_argsize(hf)                              + padding| Opt. Align. Padding  |
//                 + frame::metadata_words_at_top                       |  |----------------------|
//                                                                      |  | frame::java_abi      |<- new SP of caller
//                                                                      |  ========================   / FP of new frame
//                                                                      |  |                      |   (aligned)
//                                  | Growth |                          |  | ijava_state          |
//                                  v        v                          |  |                      |
//                                                                      |  |----------------------|
//                                                                      |  | P0                   |
//                                                                      |  | :                    |
//                                                                      |  | Pi                   |<- unextended SP
//                                                                      |  |----------------------|    + metadata_words_at_top
//                                                                      v  | frame::java_abi      |<- unextended SP / SP of new frame
//                                                                     --- ========================   (not yet aligned(1))
//
//
//  (1) The SP / unextended SP of the new interpreted frame is not aligned. It
//      gets aligned when its callee is pushed on stack or in finish_thaw() if
//      it is the top frame. This allows addressing parameters: unextended SP + metadata_words_at_top
//
//  (2) If caller is interpreted then its ijava_state::top_frame_sp will be used as sender sp
//      of the new frame (see ContinuationHelper::InterpretedFrame::patch_sender_sp() and diagram at the end of this file)
//
//  (3) The size of alignment padding required when thawing frames is accounted for
//      in FreezeBase::_align_size.
//
// === New Compiled Frame ===================================================================================================================
//
//        Compiled Caller                              Interpreted Caller
//
//        - stackargs+abi overlap with caller          - gets resized for stackargs
//        - no alignment padding                       - SP gets aligned
//                                                     - no overlap with orig.
//                                                       caller
//   O C
//   r a  |                      |                     |                      |
//   i l  |                      |                     |                      |
//   g l  |----------------------|                     |                      |
//   i e  | Stack Args           |                     |                      |
//   n r  | (if any)             |                     |----------------------|
//   a    |----------------------|                     | frame::java_abi      |
//   l    | frame::java_abi      |<- unext. SP / SP    | (unused)             |<- unal.unext.SP
//  - - - ======================== - - - - - - - - - - |----------------------|- - - - - - - - - - - - - - - - - - - - - - - - - - - -
//    N   |                      |                     | Opt. Align. Padding  |
//    e   |                      |                     |----------------------|
//    w   |----------------------|                     | Stack Args           |
//        | frame::java_abi      |<- unext. SP / SP    | (if any)             |
//    F   ========================                     |----------------------|
//    r                                                | frame::java_abi      |<- caller's SP
//    a                                                ======================== / new frame's FP
//    m                                                |                      |   (aligned)
//    e                                                |                      |
//                                                     |----------------------|
//                                                     | frame::java_abi      |<- unext. SP / SP
//                                                     ========================
//
//  If the new frame is at the bottom just above the ContinuationEntry frame then the stackargs
//  don't overlap the caller either even though it is compiled because the size is not
//  limited/known. In contrast to the interpreted caller case the abi overlaps with the caller
//  if there are no stackargs. This is to comply with shared code (see e.g. StackChunkFrameStream::frame_size())
//
template<typename FKind> frame ThawBase::new_stack_frame(const frame& hf, frame& caller, bool bottom) {
  assert(FKind::is_instance(hf), "");

  assert(is_aligned(caller.fp(), frame::frame_alignment), "");
  assert(is_aligned(caller.sp(), frame::frame_alignment), "");
  if (FKind::interpreted) {
    // Note: we have to overlap with the caller, at least if it is interpreted, to match the
    // max_thawing_size calculation during freeze. See also comment above.
    intptr_t* heap_sp = hf.unextended_sp();
    const int fsize = ContinuationHelper::InterpretedFrame::frame_bottom(hf) - hf.unextended_sp();
    const int overlap = !caller.is_interpreted_frame() ? 0
                        : ContinuationHelper::InterpretedFrame::stack_argsize(hf) + frame::metadata_words_at_top;
    intptr_t* frame_sp = caller.unextended_sp() + overlap - fsize;
    intptr_t* fp = frame_sp + (hf.fp() - heap_sp);
    // align fp
    int padding = fp - align_down(fp, frame::frame_alignment);
    fp -= padding;
    // alignment of sp is done by callee or in finish_thaw()
    frame_sp -= padding;

    // On ppc esp points to the next free slot on the expression stack and sp + metadata points to the last parameter
    DEBUG_ONLY(intptr_t* esp = fp + *hf.addr_at(ijava_idx(esp));)
    assert(frame_sp + frame::metadata_words_at_top == esp+1, " frame_sp=" PTR_FORMAT " esp=" PTR_FORMAT, p2i(frame_sp), p2i(esp));
    caller.set_sp(fp);
    frame f(frame_sp, hf.pc(), frame_sp, fp);
    // we need to set the locals so that the caller of new_stack_frame() can call
    // ContinuationHelper::InterpretedFrame::frame_bottom
    // copy relativized locals from the heap frame
    *f.addr_at(ijava_idx(locals)) = *hf.addr_at(ijava_idx(locals));

    return f;
  } else {
    int fsize = FKind::size(hf);
    int argsize = hf.compiled_frame_stack_argsize();
    intptr_t* frame_sp = caller.sp() - fsize;

    if ((bottom && argsize > 0) || caller.is_interpreted_frame()) {
      frame_sp -= argsize + frame::metadata_words_at_top;
      frame_sp = align_down(frame_sp, frame::alignment_in_bytes);
      caller.set_sp(frame_sp + fsize);
    }

    assert(hf.cb() != nullptr, "");
    assert(hf.oop_map() != nullptr, "");
    intptr_t* fp = frame_sp + fsize;
    return frame(frame_sp, frame_sp, fp, hf.pc(), hf.cb(), hf.oop_map(), false);
  }
}

inline intptr_t* ThawBase::align(const frame& hf, intptr_t* frame_sp, frame& caller, bool bottom) {
  // Unused. Alignment is done directly in new_stack_frame() / finish_thaw().
  return nullptr;
}

inline void ThawBase::derelativize_interpreted_frame_metadata(const frame& hf, const frame& f) {
  intptr_t* vfp = f.fp();

  // Make sure that monitors is still relativized.
  assert(f.at_absolute(ijava_idx(monitors)) <= -(frame::ijava_state_size / wordSize), "");

  // Make sure that esp is still relativized.
  assert(f.at_absolute(ijava_idx(esp)) <= f.at_absolute(ijava_idx(monitors)), "");

  // Keep top_frame_sp relativized.
}

inline void ThawBase::patch_pd(frame& f, const frame& caller) {
  patch_callee_link(caller, caller.fp());
  // Prevent assertion if f gets deoptimized right away before it's fully initialized
  f.mark_not_fully_initialized();
}

//
// Interpreter Calling Procedure on PPC
//
// Caller                                   Resized Caller before the Call                New Callee Frame
//
//   - SP/FP are 16 byte aligned.           - The unused part of the expression stack     - The caller's original SP is passed as
//     Padding is added as necessary.         is removed                                    sender SP (in R21_sender_SP) also by
//   - SP is _not_ used as esp              - Slots for the callee's nonparameter locals    compiled callers. It is saved in the
//     (expression stack pointer)             are added.                                    ijava_state::sender_sp slot and
//   - Has reserved slots for the           - The large ABI is replaced with a minimal      restored when returning.
//     maximal expression stack               ABI.                                          This removes a c2i extension if there
//   - Has a larger ABI section on          - The original SP was saved in                  is one.
//     top that is required to call           ijava_state::top_frame_sp slot.             - ijava_state::sender_sp will be set
//     C++ code                               From there it is restored as SP _after_       as the caller's unextended sp when
//                                            returning from a call. This reverts the       iterating stack frames
//                                            resizing described above. It is also          (see frame::unextended_sp() and
//                                            required to undo potential i2c extensions     frame::sender_for_interpreter_frame())
//                                            if the calle should be compiled.
//                                          - Note that unextended SP < SP
//                                            is possible on ppc.
//
// |                      |                 |                      |                      |                      |
// | (frame::java_abi)    |                 | (frame::java_abi)    |                      | (frame::java_abi)    |
// | 4 words              |                 | 4 words              |                      | 4 words              |
// | Caller's SP          |<- FP of caller  | Caller's SP          |<- FP of caller       | Caller's SP          |<- FP of caller
// ========================   (aligned)     ========================                      ========================
// | frame::              |                 | frame::              |                      | frame::              |
// | ijava_state          |                 | ijava_state          |                      | ijava_state          |
// |                      |                 |                      |                      |                      |
// |----------------------|                 |----------------------|                      |----------------------|
// | P0                   |                 | L0 aka P0            |                      | L0 aka P0            |
// |                      |                 | :                    |                      | :                    |
// | Pn                   |                 | :      Pn            |                      | :      Pn            |
// |----------------------|                 | :                    |                      | :                    |
// |                      |                 | Lm                   |                      | Lm                   |
// | Reserved Expr. Stack |                 |----------------------|                      |----------------------|
// |                      |                 | Opt. Alignm. Padding |                      | Opt. Alignm. Padding |
// |                      |<- ConstMethod   |----------------------|                      |----------------------|
// |----------------------|   ::_max_stack  |                      |                      |                      |
// | Opt. Alignm. Padding |                 | (frame::java_abi)    |                      | (frame::java_abi)    |
// |----------------------|                 | 4 words              |                      | 4 words              |
// | Large ABI            |                 | Caller's SP          |<- new SP of caller   | Caller's SP          |<- SP of caller /
// | for C++ calls        |                 ========================   (aligned)          ========================   FP of callee
// | (frame::             |                                                               | frame::              |   (aligned)
// |  native_abi_reg_args)|                                                               | ijava_state          |
// |                      |                                                               |                      |
// |                      |                                                               |----------------------|
// |                      |                                                               |                      |
// | Caller's SP          |<- SP of caller                          <- unextended SP      | Reserved Expr. Stack |<- unextended SP
// ========================   (aligned)                                of caller          |                      |   of caller
//                                                                     (aligned)          |                      |
//                                                                                        |                      |
//                                                                                        |                      |
//                                                                                        |                      |
//                                                                                        |                      |<- ConstMethod
//                                                                                        |----------------------|   ::_max_stack
//                         Resize Caller                    Push new Callee Frame         | Opt. Alignm. Padding |
//                     -------------------->              ------------------------>       |----------------------|
//                     (ABI, expressions, locals)                                         | Large ABI            |
//                                                                                        | for C++ calls        |
//                                                                                        | (frame::             |
//                                                                                        |  native_abi_reg_args)|
//                                                |  Growth  |                            |                      |
//                                                v          v                            |                      |
//                                                                                        |                      |
//                                                                                        | Caller's SP          |<- SP of callee
//                                                                                        ========================   (aligned)
//
//
#endif // CPU_PPC_CONTINUATION_PPC_INLINE_HPP
