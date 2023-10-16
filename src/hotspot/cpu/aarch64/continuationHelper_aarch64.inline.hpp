/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_AARCH64_CONTINUATIONHELPER_AARCH64_INLINE_HPP
#define CPU_AARCH64_CONTINUATIONHELPER_AARCH64_INLINE_HPP

#include "runtime/continuationHelper.hpp"

#include "runtime/continuationEntry.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/registerMap.hpp"
#include "utilities/macros.hpp"

template<typename FKind>
static inline intptr_t** link_address(const frame& f) {
  assert(FKind::is_instance(f), "");
  return FKind::interpreted
            ? (intptr_t**)(f.fp() + frame::link_offset)
            : (intptr_t**)(f.unextended_sp() + f.cb()->frame_size() - frame::sender_sp_offset);
}

inline int ContinuationHelper::frame_align_words(int size) {
#ifdef _LP64
  return size & 1;
#else
  return 0;
#endif
}

inline intptr_t* ContinuationHelper::frame_align_pointer(intptr_t* sp) {
#ifdef _LP64
  sp = align_down(sp, frame::frame_alignment);
#endif
  return sp;
}

template<typename FKind>
inline void ContinuationHelper::update_register_map(const frame& f, RegisterMap* map) {
  frame::update_map_with_saved_link(map, link_address<FKind>(f));
}

inline void ContinuationHelper::update_register_map_with_callee(const frame& f, RegisterMap* map) {
  frame::update_map_with_saved_link(map, ContinuationHelper::Frame::callee_link_address(f));
}

inline void ContinuationHelper::push_pd(const frame& f) {
  *(intptr_t**)(f.sp() - frame::sender_sp_offset) = f.fp();
}

#define CPU_OVERRIDES_RETURN_ADDRESS_ACCESSORS

inline address ContinuationHelper::return_address_at(intptr_t* sp) {
  return pauth_strip_verifiable(*(address*)sp);
}

inline void ContinuationHelper::patch_return_address_at(intptr_t* sp,
                                                        address pc) {
  *(address*)sp = pauth_sign_return_address(pc);
}

inline void ContinuationHelper::set_anchor_to_entry_pd(JavaFrameAnchor* anchor, ContinuationEntry* entry) {
  anchor->set_last_Java_fp(entry->entry_fp());
}

#ifdef ASSERT
inline void ContinuationHelper::set_anchor_pd(JavaFrameAnchor* anchor, intptr_t* sp) {
  intptr_t* fp = *(intptr_t**)(sp - frame::sender_sp_offset);
  anchor->set_last_Java_fp(fp);
}

inline bool ContinuationHelper::Frame::assert_frame_laid_out(frame f) {
  intptr_t* sp = f.sp();
  address pc = ContinuationHelper::return_address_at(
                 sp - frame::sender_sp_ret_address_offset());
  intptr_t* fp = *(intptr_t**)(sp - frame::sender_sp_offset);
  assert(f.raw_pc() == pc, "f.ra_pc: " INTPTR_FORMAT " actual: " INTPTR_FORMAT, p2i(f.raw_pc()), p2i(pc));
  assert(f.fp() == fp, "f.fp: " INTPTR_FORMAT " actual: " INTPTR_FORMAT, p2i(f.fp()), p2i(fp));
  return f.raw_pc() == pc && f.fp() == fp;
}
#endif

inline intptr_t** ContinuationHelper::Frame::callee_link_address(const frame& f) {
  return (intptr_t**)(f.sp() - frame::sender_sp_offset);
}

inline address* ContinuationHelper::Frame::return_pc_address(const frame& f) {
  return (address*)(f.real_fp() - 1);
}

inline address* ContinuationHelper::InterpretedFrame::return_pc_address(const frame& f) {
  return (address*)(f.fp() + frame::return_addr_offset);
}

inline void ContinuationHelper::InterpretedFrame::patch_sender_sp(frame& f, const frame& caller) {
  intptr_t* sp = caller.unextended_sp();
  assert(f.is_interpreted_frame(), "");
  intptr_t* la = f.addr_at(frame::interpreter_frame_sender_sp_offset);
  *la = f.is_heap_frame() ? (intptr_t)(sp - f.fp()) : (intptr_t)sp;
}

inline address ContinuationHelper::Frame::real_pc(const frame& f) {
  // Always used in assertions. Just strip it.
  address* pc_addr = &(((address*) f.sp())[-1]);
  return pauth_strip_pointer(*pc_addr);
}

inline void ContinuationHelper::Frame::patch_pc(const frame& f, address pc) {
  address* pc_addr = &(((address*) f.sp())[-1]);
  *pc_addr = pauth_sign_return_address(pc);
}

inline intptr_t* ContinuationHelper::InterpretedFrame::frame_top(const frame& f, InterpreterOopMap* mask) { // inclusive; this will be copied with the frame
  // interpreter_frame_last_sp_offset, points to unextended_sp includes arguments in the frame
  // interpreter_frame_initial_sp_offset excludes expression stack slots
  int expression_stack_sz = expression_stack_size(f, mask);
  intptr_t* res = (intptr_t*)f.at_relative(frame::interpreter_frame_initial_sp_offset) - expression_stack_sz;
  assert(res == (intptr_t*)f.interpreter_frame_monitor_end() - expression_stack_sz, "");
  assert(res >= f.unextended_sp(),
    "res: " INTPTR_FORMAT " initial_sp: " INTPTR_FORMAT " last_sp: " INTPTR_FORMAT " unextended_sp: " INTPTR_FORMAT " expression_stack_size: %d",
    p2i(res), p2i(f.addr_at(frame::interpreter_frame_initial_sp_offset)), f.at_relative_or_null(frame::interpreter_frame_last_sp_offset),
    p2i(f.unextended_sp()), expression_stack_sz);
  return res;
}

inline intptr_t* ContinuationHelper::InterpretedFrame::frame_bottom(const frame& f) { // exclusive; this will not be copied with the frame
  return (intptr_t*)f.at_relative(frame::interpreter_frame_locals_offset) + 1; // exclusive, so we add 1 word
}

inline intptr_t* ContinuationHelper::InterpretedFrame::frame_top(const frame& f, int callee_argsize, bool callee_interpreted) {
  return f.unextended_sp() + (callee_interpreted ? callee_argsize : 0);
}

inline intptr_t* ContinuationHelper::InterpretedFrame::callers_sp(const frame& f) {
  return f.fp() + frame::metadata_words;
}

#endif // CPU_AARCH64_CONTINUATIONHELPER_AARCH64_INLINE_HPP
