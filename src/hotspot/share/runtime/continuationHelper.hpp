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

#ifndef SHARE_VM_RUNTIME_CONTINUATIONHELPER_HPP
#define SHARE_VM_RUNTIME_CONTINUATIONHELPER_HPP

#include "code/scopeDesc.hpp"
#include "compiler/oopMap.hpp"
#include "memory/allStatic.hpp"
#include "runtime/frame.hpp"
#include "runtime/stackValue.hpp"

// Helper, all-static
class ContinuationEntry;

class ContinuationHelper {
public:
  static inline void set_anchor_pd(JavaFrameAnchor* anchor, intptr_t* sp);
  static inline void set_anchor_to_entry_pd(JavaFrameAnchor* anchor, ContinuationEntry* entry);

  template<typename FKind> static void update_register_map(const frame& f, RegisterMap* map);
  static inline void update_register_map_with_callee(const frame& f, RegisterMap* map);

  static inline void push_pd(const frame& f);

  static inline address return_address_at(intptr_t* sp);
  static inline void patch_return_address_at(intptr_t* sp, address pc);

  static inline int frame_align_words(int size);
  static inline intptr_t* frame_align_pointer(intptr_t* sp);

  // Abstract helpers for describing frames in general
  class Frame;
  class NonInterpretedFrame;

  // Concrete helpers for describing concrete types of frames
  class InterpretedFrame;
  class NonInterpretedUnknownFrame;
  class CompiledFrame;
  class StubFrame;
};

class ContinuationHelper::Frame : public AllStatic {
public:
  static const bool interpreted = false;
  static const bool stub = false;

  static inline intptr_t** callee_link_address(const frame& f);
  static Method* frame_method(const frame& f);
  static inline address real_pc(const frame& f);
  static inline void patch_pc(const frame& f, address pc);
  static address* return_pc_address(const frame& f);
  static address return_pc(const frame& f);
  static bool is_stub(CodeBlob* cb);

#ifdef ASSERT
  static inline intptr_t* frame_top(const frame &f);
  static inline bool is_deopt_return(address pc, const frame& sender);
  static bool assert_frame_laid_out(frame f);
#endif
};

class ContinuationHelper::InterpretedFrame : public ContinuationHelper::Frame {
public:
  static const bool interpreted = true;

  static inline intptr_t* frame_top(const frame& f, InterpreterOopMap* mask);
  static inline intptr_t* frame_top(const frame& f);
  static inline intptr_t* frame_top(const frame& f, int callee_argsize, bool callee_interpreted);
  static inline intptr_t* frame_bottom(const frame& f);
  static inline intptr_t* callers_sp(const frame& f);
  static inline int stack_argsize(const frame& f);

  static inline address* return_pc_address(const frame& f);
  static address return_pc(const frame& f);
  static void patch_sender_sp(frame& f, const frame& caller);

  static int size(const frame& f);
  static inline int expression_stack_size(const frame &f, InterpreterOopMap* mask);

#ifdef ASSERT
  static bool is_owning_locks(const frame& f);
#endif

  static bool is_instance(const frame& f);

  typedef InterpreterOopMap* ExtraT;
};

class ContinuationHelper::NonInterpretedFrame : public ContinuationHelper::Frame  {
public:
  static inline intptr_t* frame_top(const frame& f, int callee_argsize, bool callee_interpreted);
  static inline intptr_t* frame_top(const frame& f);
  static inline intptr_t* frame_bottom(const frame& f);

  static inline int size(const frame& f);
  static inline int stack_argsize(const frame& f);
};

class ContinuationHelper::NonInterpretedUnknownFrame : public ContinuationHelper::NonInterpretedFrame  {
public:
  static bool is_instance(const frame& f);
};

class ContinuationHelper::CompiledFrame : public ContinuationHelper::NonInterpretedFrame {
public:
  static bool is_instance(const frame& f);

#ifdef ASSERT
  template <typename RegisterMapT>
  static bool is_owning_locks(JavaThread* thread, RegisterMapT* map, const frame& f);
#endif
};

class ContinuationHelper::StubFrame : public ContinuationHelper::NonInterpretedFrame {
public:
  static const bool stub = true;

  static bool is_instance(const frame& f);
};

#endif // SHARE_VM_RUNTIME_CONTINUATIONHELPER_HPP
