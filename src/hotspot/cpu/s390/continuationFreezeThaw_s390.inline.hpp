/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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

#ifndef CPU_S390_CONTINUATION_S390_INLINE_HPP
#define CPU_S390_CONTINUATION_S390_INLINE_HPP

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

inline void FreezeBase::set_top_frame_metadata_pd(const frame& hf) {
  stackChunkOop chunk = _cont.tail();
  assert(chunk->is_in_chunk(hf.sp()), "hf.sp()=" PTR_FORMAT, p2i(hf.sp()));

  hf.own_abi()->return_pc = (uint64_t)hf.pc();
  if (hf.is_interpreted_frame()) {
    patch_callee_link_relative(hf, hf.fp());
  } else {
#ifdef ASSERT
    // See also FreezeBase::patch_pd()
    patch_callee_link(hf, (intptr_t*)badAddress);
#endif
  }
}

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

template<typename FKind> frame FreezeBase::new_heap_frame(frame& f, frame& caller) {
  assert(FKind::is_instance(f), "");
  intptr_t *sp, *fp;
  if (FKind::interpreted) {
    intptr_t locals_offset = *f.addr_at(_z_ijava_idx(locals));

    // If the caller.is_empty(), i.e. we're freezing into an empty chunk, then we set
    // the chunk's argsize in finalize_freeze and make room for it above the unextended_sp
    // See also comment on StackChunkFrameStream<frame_kind>::interpreter_frame_size()

    int overlap =
      (caller.is_interpreted_frame() || caller.is_empty())
      ? ContinuationHelper::InterpretedFrame::stack_argsize(f) + frame::metadata_words_at_top
      : 0;

    // Calculate the new frame's FP in the heap chunk.
    // Starting from caller's unextended_sp, we:
    // - subtract 1 for the z_parent_ijava_frame_abi (which sits just below the locals)
    // - subtract locals_offset (distance from FP to locals in the original frame)
    // - add overlap (to account for shared stack args when caller is interpreted or empty)
    // This positions FP such that locals are correctly placed relative to the caller's frame.
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
    *hf.addr_at(_z_ijava_idx(locals)) = locals_offset;
    *hf.addr_at(_z_ijava_idx(esp))    = f.interpreter_frame_esp() - f.fp();
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

void FreezeBase::adjust_interpreted_frame_unextended_sp(frame& f) {
  // Nothing to do on s390 and ppc. On x86/aarch64/riscv, the unextended_sp is stored
  // in interpreter_frame_last_sp and needs to be restored from there. On s390/ppc,
  // the frame structure doesn't have interpreter_frame_last_sp; instead, the unextended_sp
  // is directly maintained in the frame and doesn't need adjustment.
}

inline void FreezeBase::prepare_freeze_interpreted_top_frame(frame& f) {
  // Nothing to do. We don't save a last sp because we cannot use sp as esp.
  // Instead the top frame is trimmed when making an i2i call. The original
  // top_frame_sp is set when the frame is pushed (see generate_fixed_frame()).
  // An interpreter top frame that was just thawed is resized to top_frame_sp by the
  // resume adapter (see generate_cont_resume_interpreter_adapter()). So the assertion is
  // false, if we freeze again right after thawing as we do when redoing a vm call wasn't
  // successful.
  assert(_thread->interp_redoing_vm_call() ||
         ((intptr_t*)f.at_relative(_z_ijava_idx(top_frame_sp)) == f.unextended_sp()),
         "top_frame_sp:" PTR_FORMAT " usp:" PTR_FORMAT, f.at_relative(_z_ijava_idx(top_frame_sp)), p2i(f.unextended_sp()));
}

inline void FreezeBase::relativize_interpreted_frame_metadata(const frame& f, const frame& hf) {
  intptr_t* vfp = f.fp();
  intptr_t* hfp = hf.fp();
  assert(f.fp() > (intptr_t*)f.interpreter_frame_esp(), "");

  // There is alignment padding between vfp and f's locals array in the original
  // frame, because we freeze the padding (see recurse_freeze_interpreted_frame)
  // in order to keep the same relativized locals pointer, we don't need to change it here.

  // Make sure that monitors is already relativized.
  assert(hf.at_absolute(_z_ijava_idx(monitors)) <= -(frame::z_ijava_state_size / wordSize), "");
  // Make sure that esp is already relativized.
  assert(hf.at_absolute(_z_ijava_idx(esp)) <= hf.at_absolute(_z_ijava_idx(monitors)), "");
  // top_frame_sp is already relativized

  // hfp == hf.sp() + (f.fp() - f.sp()) is not true on ppc because the stack frame has room for
  // the maximal expression stack and the expression stack in the heap frame is trimmed.
  assert(hf.fp() == hf.interpreter_frame_esp() + (f.fp() - f.interpreter_frame_esp()), "");
  assert(hf.fp()                 <= (intptr_t*)hf.at(_z_ijava_idx(locals)), "");
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

    // Note a difference from x86_64: the link is not made relative if the caller
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

inline void FreezeBase::patch_pd_unused(intptr_t* sp) {
}

inline void FreezeBase::patch_stack_pd(intptr_t* frame_sp, intptr_t* heap_sp) {
  // Nothing to do. The backchain is reconstructed when thawing (see Thaw<ConfigT>::patch_caller_links())
}

inline intptr_t* AnchorMark::anchor_mark_set_pd() {
  // Nothing to do on s390 because the interpreter does not use SP as expression stack pointer.
  // Instead there is a dedicated register Z_esp which is not affected by VM calls.
  return _top_frame.sp();
}

inline void AnchorMark::anchor_mark_clear_pd() {
  // Nothing to do. See anchor_mark_set_pd().
}

inline frame ThawBase::new_entry_frame() {
  intptr_t* sp = _cont.entrySP();
  return frame(sp, _cont.entryPC(), sp, _cont.entryFP());
}

template<typename FKind> frame ThawBase::new_stack_frame(const frame& hf, frame& caller, bool bottom) {
  assert(FKind::is_instance(hf), "");

  assert(is_aligned(caller.fp(), frame::frame_alignment), PTR_FORMAT, p2i(caller.fp()));
  // caller.sp() can be unaligned. This is fixed below.
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

    // On s390 esp points to the first free slot on the expression stack (see frame_s390.hpp).
    // The assertion verifies that frame_sp + metadata_words_at_top points to the slot above esp,
    // which corresponds to the last parameter position.
    DEBUG_ONLY(intptr_t* esp = fp + *hf.addr_at(_z_ijava_idx(esp));)
    assert(frame_sp + frame::metadata_words_at_top == esp+1, " frame_sp=" PTR_FORMAT " esp=" PTR_FORMAT, p2i(frame_sp), p2i(esp));
    caller.set_sp(fp);
    frame f(frame_sp, hf.pc(), frame_sp, fp);
    // we need to set the locals so that the caller of new_stack_frame() can call
    // ContinuationHelper::InterpretedFrame::frame_bottom
    // copy relativized locals from the heap frame
    *f.addr_at(_z_ijava_idx(locals)) = *hf.addr_at(_z_ijava_idx(locals));

    return f;
  } else {
    int fsize = FKind::size(hf);
    int argsize = FKind::stack_argsize(hf);
    intptr_t* frame_sp = caller.sp() - fsize;

    if ((bottom && argsize > 0) || caller.is_interpreted_frame()) {
      assert(!_should_patch_caller_pc, "what??");
      _should_patch_caller_pc = caller.is_interpreted_frame();
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

inline void ThawBase::derelativize_interpreted_frame_metadata(const frame& hf, const frame& f) {
  // Make sure that monitors is still relativized.
  assert(f.at_absolute(_z_ijava_idx(monitors)) <= -(frame::z_ijava_state_size / wordSize), "");
  // Make sure that esp is still relativized.
  assert(f.at_absolute(_z_ijava_idx(esp)) <= f.at_absolute(_z_ijava_idx(monitors)), "");
  // Keep top_frame_sp relativized.
}

inline intptr_t* ThawBase::align(const frame& hf, intptr_t* frame_sp, frame& caller, bool bottom) {
  // Unused. Alignment is done directly in new_stack_frame() / finish_thaw().
  return nullptr;
}

inline void ThawBase::patch_pd(frame& f, const frame& caller) {
  patch_callee_link(caller, caller.fp());
  // Prevent assertion if f gets deoptimized right away before it's fully initialized
  f.mark_not_fully_initialized();
}

inline void ThawBase::patch_pd(frame& f, intptr_t* caller_sp) {
  assert(f.own_abi()->callers_sp == (uint64_t)caller_sp, "should have been fixed by patch_caller_links");
}

inline intptr_t* ThawBase::push_cleanup_continuation() {
  frame enterSpecial = new_entry_frame();
  frame::z_common_abi* enterSpecial_abi = (frame::z_common_abi*)enterSpecial.sp();

  enterSpecial_abi->return_pc = (intptr_t)ContinuationEntry::cleanup_pc();

  log_develop_trace(continuations, preempt)("push_cleanup_continuation enterSpecial sp: " INTPTR_FORMAT " cleanup pc: " INTPTR_FORMAT,
                                            p2i(enterSpecial_abi),
                                            p2i(ContinuationEntry::cleanup_pc()));

  return enterSpecial.sp();
}

inline intptr_t* ThawBase::push_preempt_adapter() {
  frame enterSpecial = new_entry_frame();
  frame::z_common_abi* enterSpecial_abi = (frame::z_common_abi*)enterSpecial.sp();

  enterSpecial_abi->return_pc = (intptr_t)StubRoutines::cont_preempt_stub();

  log_develop_trace(continuations, preempt)("push_preempt_adapter enterSpecial sp: " INTPTR_FORMAT " adapter pc: " INTPTR_FORMAT,
                                            p2i(enterSpecial_abi),
                                            p2i(StubRoutines::cont_preempt_stub()));

  return enterSpecial.sp();
}

template <typename ConfigT>
inline void Thaw<ConfigT>::patch_caller_links(intptr_t* sp, intptr_t* bottom) {
  for (intptr_t* callers_sp; sp < bottom; sp = callers_sp) {
    address pc = (address)((frame::z_java_abi*) sp)->return_pc;
    assert(pc != nullptr, "");
    // see ThawBase::patch_return() which gets called just before
    bool is_entry_frame = pc == StubRoutines::cont_returnBarrier() || pc == _cont.entryPC();
    if (is_entry_frame) {
      callers_sp = _cont.entryFP();
    } else {
      assert(!Interpreter::contains(pc), "sp:" PTR_FORMAT " pc:" PTR_FORMAT, p2i(sp), p2i(pc));
      CodeBlob* cb = CodeCache::find_blob(pc);
      callers_sp = sp + cb->frame_size();
    }
    // set the back link
    ((frame::z_java_abi*) sp)->callers_sp = (intptr_t) callers_sp;
  }
}

inline void ThawBase::prefetch_chunk_pd(void* start, int size) {
  // TODO: implement in future;
}

#endif // CPU_S390_CONTINUATION_S390_INLINE_HPP
