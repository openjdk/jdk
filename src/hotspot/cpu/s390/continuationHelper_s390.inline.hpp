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

#ifndef CPU_S390_CONTINUATIONHELPER_S390_INLINE_HPP
#define CPU_S390_CONTINUATIONHELPER_S390_INLINE_HPP

#include "runtime/continuationHelper.hpp"

// TODO: Implement

template<typename FKind>
static inline intptr_t** link_address(const frame& f) {
  Unimplemented();
  return nullptr;
}

static inline void patch_return_pc_with_preempt_stub(frame& f) {
  Unimplemented();
}

inline int ContinuationHelper::frame_align_words(int size) {
  Unimplemented();
  return 0;
}

inline intptr_t* ContinuationHelper::frame_align_pointer(intptr_t* sp) {
  Unimplemented();
  return nullptr;
}

template<typename FKind>
inline void ContinuationHelper::update_register_map(const frame& f, RegisterMap* map) {
  Unimplemented();
}

inline void ContinuationHelper::update_register_map_with_callee(const frame& f, RegisterMap* map) {
  Unimplemented();
}

inline void ContinuationHelper::push_pd(const frame& f) {
  Unimplemented();
}

inline void ContinuationHelper::set_anchor_to_entry_pd(JavaFrameAnchor* anchor, ContinuationEntry* cont) {
  Unimplemented();
}

inline void ContinuationHelper::set_anchor_pd(JavaFrameAnchor* anchor, intptr_t* sp) {
  Unimplemented();
}

#ifdef ASSERT
inline bool ContinuationHelper::Frame::assert_frame_laid_out(frame f) {
  Unimplemented();
  return false;
}
#endif

inline intptr_t** ContinuationHelper::Frame::callee_link_address(const frame& f) {
  Unimplemented();
  return nullptr;
}

template<typename FKind>
static inline intptr_t* real_fp(const frame& f) {
  Unimplemented();
  return nullptr;
}

inline address* ContinuationHelper::InterpretedFrame::return_pc_address(const frame& f) {
  Unimplemented();
  return nullptr;
}

inline void ContinuationHelper::InterpretedFrame::patch_sender_sp(frame& f, const frame& caller) {
  Unimplemented();
}

inline address* ContinuationHelper::Frame::return_pc_address(const frame& f) {
  Unimplemented();
  return nullptr;
}

inline address ContinuationHelper::Frame::real_pc(const frame& f) {
  Unimplemented();
  return nullptr;
}

inline void ContinuationHelper::Frame::patch_pc(const frame& f, address pc) {
  Unimplemented();
}

inline intptr_t* ContinuationHelper::InterpretedFrame::frame_top(const frame& f, InterpreterOopMap* mask) { // inclusive; this will be copied with the frame
  Unimplemented();
  return nullptr;
}

inline intptr_t* ContinuationHelper::InterpretedFrame::frame_bottom(const frame& f) { // exclusive; this will not be copied with the frame
  Unimplemented();
  return nullptr;
}

inline intptr_t* ContinuationHelper::InterpretedFrame::frame_top(const frame& f, int callee_argsize, bool callee_interpreted) {
  Unimplemented();
  return nullptr;
}

inline intptr_t* ContinuationHelper::InterpretedFrame::callers_sp(const frame& f) {
  Unimplemented();
  return nullptr;
}

#endif // CPU_S390_CONTINUATIONHELPER_S390_INLINE_HPP
