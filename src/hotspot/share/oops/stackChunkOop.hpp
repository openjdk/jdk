/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_STACKCHUNKOOP_HPP
#define SHARE_OOPS_STACKCHUNKOOP_HPP


#include "oops/instanceOop.hpp"
#include "runtime/handles.hpp"
#include "runtime/stackChunkFrameStream.hpp"
#include "utilities/bitMap.hpp"
#include "utilities/macros.hpp"

class frame;
class MemRegion;
class RegisterMap;
class VMRegImpl;
typedef VMRegImpl* VMReg;

// A continuation stack-chunk oop.
// See InstanceStackChunkKlass for a description of continuation stack-chunks.
//
// size and sp are in machine words
// max_size is the maximum space a thawed chunk would take up on the stack, *not* including top-most frame's metadata
class stackChunkOopDesc : public instanceOopDesc {
public:
  enum class BarrierType { Load, Store };

private:
  template <BarrierType barrier> friend class DoBarriersStackClosure;

  // Chunk flags.
  // FLAG_HAS_INTERPRETED_FRAMES actually means "thaw slow because of some content-based chunk condition"
  // It is set whenever we freeze slow, but generally signifies there might be interpreted/deoptimized/stub frames
  static const uint8_t FLAG_HAS_INTERPRETED_FRAMES = 1 << 0;
  static const uint8_t FLAG_CLAIM_RELATIVIZE = 1 << 1; // Only one thread claims relativization of derived pointers
  static const uint8_t FLAG_NOTIFY_RELATIVIZE = 1 << 2; // Someone is waiting for relativization to complete
  static const uint8_t FLAG_GC_MODE = 1 << 3; // Once true it and FLAG_HAS_INTERPRETED_FRAMES can't change
  static const uint8_t FLAG_HAS_BITMAP = 1 << 4; // Can only be true if FLAG_GC_MODE is true

  bool try_acquire_relativization();
  void release_relativization();

public:
  static inline stackChunkOop cast(oop obj);

  inline stackChunkOop parent() const;
  inline void set_parent(stackChunkOop value);
  template<typename P>
  inline void set_parent_raw(oop value);
  template<DecoratorSet decorators>
  inline void set_parent_access(oop value);

  inline int stack_size() const;

  inline int sp() const;
  inline void set_sp(int value);

  inline address pc() const;
  inline void set_pc(address value);

  inline int argsize() const;
  inline void set_argsize(int value);

  inline uint8_t flags() const;
  inline uint8_t flags_acquire() const;
  inline void set_flags(uint8_t value);
  inline void release_set_flags(uint8_t value);

  inline int max_thawing_size() const;
  inline void set_max_thawing_size(int value);

  inline oop cont() const;
  template<typename P>
  inline oop cont() const;
  inline void set_cont(oop value);
  template<typename P>
  inline void set_cont_raw(oop value);
  template<DecoratorSet decorators>
  inline void set_cont_access(oop value);

  inline int bottom() const;

  inline HeapWord* start_of_stack() const;

  inline intptr_t* start_address() const;
  inline intptr_t* end_address() const;
  inline intptr_t* bottom_address() const; // = end_address - argsize
  inline intptr_t* sp_address() const;


  inline int to_offset(intptr_t* p) const;
  inline intptr_t* from_offset(int offset) const;

  inline bool is_empty() const;
  inline bool is_in_chunk(void* p) const;
  inline bool is_usable_in_chunk(void* p) const;

  inline bool is_flag(uint8_t flag) const;
  inline bool is_flag_acquire(uint8_t flag) const;
  inline void set_flag(uint8_t flag, bool value);
  inline bool try_set_flags(uint8_t prev_flags, uint8_t new_flags);
  inline void clear_flags();

  inline bool has_mixed_frames() const;
  inline void set_has_mixed_frames(bool value);

  inline bool is_gc_mode() const;
  inline bool is_gc_mode_acquire() const;
  inline void set_gc_mode(bool value);

  inline bool has_bitmap() const;
  inline void set_has_bitmap(bool value);

  inline bool has_thaw_slowpath_condition() const;

  inline bool requires_barriers();

  template <BarrierType>
  void do_barriers();

  template <BarrierType, ChunkFrames frames, typename RegisterMapT>
  inline void do_barriers(const StackChunkFrameStream<frames>& f, const RegisterMapT* map);

  template <typename RegisterMapT>
  void fix_thawed_frame(const frame& f, const RegisterMapT* map);

  template <class StackChunkFrameClosureType>
  inline void iterate_stack(StackChunkFrameClosureType* closure);

  // Derived pointers are relativized, with respect to their oop base.
  void relativize_derived_pointers_concurrently();
  void transform();

  inline void* gc_data() const;
  inline BitMapView bitmap() const;
  inline BitMap::idx_t bit_index_for(address p) const;
  inline intptr_t* address_for_bit(BitMap::idx_t index) const;
  template <typename OopT> inline BitMap::idx_t bit_index_for(OopT* p) const;
  template <typename OopT> inline OopT* address_for_bit(BitMap::idx_t index) const;

  MemRegion range();

  // Returns a relative frame (with offset_sp, offset_unextended_sp, and offset_fp) that can be held
  // during safepoints.  This is orthogonal to the relativizing of the actual content of interpreted frames.
  // To be used, frame objects need to be derelativized with `derelativize`.
  inline frame relativize(frame fr) const;
  inline frame derelativize(frame fr) const;

  frame top_frame(RegisterMap* map);
  frame sender(const frame& fr, RegisterMap* map);

  inline int relativize_usp_offset(const frame& fr, const int usp_offset_in_bytes) const;
  inline address usp_offset_to_location(const frame& fr, const int usp_offset_in_bytes) const;
  inline address reg_to_location(const frame& fr, const RegisterMap* map, VMReg reg) const;

  // Access to relativized interpreter frames (both the frame object and frame content are relativized)
  inline Method* interpreter_frame_method(const frame& fr);
  inline address interpreter_frame_bcp(const frame& fr);
  inline intptr_t* interpreter_frame_expression_stack_at(const frame& fr, int index) const;
  inline intptr_t* interpreter_frame_local_at(const frame& fr, int index) const;

  int num_java_frames() const;

  inline void copy_from_stack_to_chunk(intptr_t* from, intptr_t* to, int size);
  inline void copy_from_chunk_to_stack(intptr_t* from, intptr_t* to, int size);

  template <typename OopT>
  inline oop load_oop(OopT* addr);

  using oopDesc::print_on;
  void print_on(bool verbose, outputStream* st) const;

  // Verifies the consistency of the chunk's data
  bool verify(size_t* out_size = nullptr, int* out_oops = nullptr,
              int* out_frames = nullptr, int* out_interpreted_frames = nullptr) NOT_DEBUG({ return true; });

private:
  template <BarrierType barrier, ChunkFrames frames = ChunkFrames::Mixed, typename RegisterMapT>
  void do_barriers0(const StackChunkFrameStream<frames>& f, const RegisterMapT* map);

  template <ChunkFrames frames, class StackChunkFrameClosureType>
  inline void iterate_stack(StackChunkFrameClosureType* closure);

  inline intptr_t* relative_base() const;

  inline intptr_t* derelativize_address(int offset) const;
  int relativize_address(intptr_t* p) const;

  inline void relativize_frame(frame& fr) const;
  inline void derelativize_frame(frame& fr) const;

  inline void relativize_frame_pd(frame& fr) const;
  inline void derelativize_frame_pd(frame& fr) const;
};

#endif // SHARE_OOPS_STACKCHUNKOOP_HPP
