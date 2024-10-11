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

#ifndef SHARE_RUNTIME_CONTINUATIONJAVACLASSES_HPP
#define SHARE_RUNTIME_CONTINUATIONJAVACLASSES_HPP

#include "memory/allStatic.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class SerializeClosure;

// Interface to jdk.internal.vm.ContinuationScope objects
class jdk_internal_vm_ContinuationScope: AllStatic {
  friend class JavaClasses;
 private:
  static int _name_offset;

  static void compute_offsets();
 public:
  static void serialize_offsets(SerializeClosure* f) NOT_CDS_RETURN;
};

// Interface to jdk.internal.vm.Continuation objects
class jdk_internal_vm_Continuation: AllStatic {
  friend class JavaClasses;
 private:
  static int _scope_offset;
  static int _target_offset;
  static int _parent_offset;
  static int _yieldInfo_offset;
  static int _tail_offset;
  static int _mounted_offset;
  static int _done_offset;
  static int _preempted_offset;

  static void compute_offsets();
 public:
  static void serialize_offsets(SerializeClosure* f) NOT_CDS_RETURN;
  // Accessors
  static inline oop scope(oop continuation);
  static inline oop parent(oop continuation);
  static inline stackChunkOop tail(oop continuation);
  static inline void set_tail(oop continuation, stackChunkOop value);
  static inline bool done(oop continuation);
  static inline bool is_preempted(oop continuation);
  static inline void set_preempted(oop continuation, bool value);
};

// Interface to jdk.internal.vm.StackChunk objects
#define STACKCHUNK_INJECTED_FIELDS(macro)                                          \
  macro(jdk_internal_vm_StackChunk, cont,           continuation_signature, false) \
  macro(jdk_internal_vm_StackChunk, flags,          byte_signature,         false) \
  macro(jdk_internal_vm_StackChunk, pc,             intptr_signature,       false) \
  macro(jdk_internal_vm_StackChunk, maxThawingSize, int_signature,          false) \

class jdk_internal_vm_StackChunk: AllStatic {
  friend class JavaClasses;
 private:
  static int _parent_offset;
  static int _size_offset;
  static int _sp_offset;
  static int _pc_offset;
  static int _bottom_offset;
  static int _flags_offset;
  static int _maxThawingSize_offset;
  static int _cont_offset;


  static void compute_offsets();
 public:
  static void serialize_offsets(SerializeClosure* f) NOT_CDS_RETURN;

  static inline int parent_offset() { return _parent_offset; }
  static inline int cont_offset()   { return _cont_offset; }

  // Accessors
  static inline oop parent(oop chunk);
  static inline void set_parent(oop chunk, oop value);
  template<typename P>
  static inline void set_parent_raw(oop chunk, oop value);
  template<DecoratorSet decorators>
  static inline void set_parent_access(oop chunk, oop value);

  static inline int size(oop chunk);
  static inline void set_size(HeapWord* chunk, int value);

  static inline int sp(oop chunk);
  static inline void set_sp(oop chunk, int value);
  static inline void set_sp(HeapWord* chunk, int value); // used while allocating
  static inline address pc(oop chunk);
  static inline void set_pc(oop chunk, address value);
  static inline int bottom(oop chunk);
  static inline void set_bottom(oop chunk, int value);
  static inline void set_bottom(HeapWord* chunk, int value);
  static inline uint8_t flags(oop chunk);
  static inline void set_flags(oop chunk, uint8_t value);
  static inline uint8_t flags_acquire(oop chunk);
  static inline void release_set_flags(oop chunk, uint8_t value);
  static inline bool try_set_flags(oop chunk, uint8_t expected_value, uint8_t new_value);

  static inline int maxThawingSize(oop chunk);
  static inline void set_maxThawingSize(oop chunk, int value);

  // cont oop's processing is essential for the chunk's GC protocol
  static inline oop cont(oop chunk);
  template<typename P>
  static inline oop cont_raw(oop chunk);
  static inline void set_cont(oop chunk, oop value);
  template<typename P>
  static inline void set_cont_raw(oop chunk, oop value);
  template<DecoratorSet decorators>
  static inline void set_cont_access(oop chunk, oop value);
};

#endif // SHARE_RUNTIME_CONTINUATIONJAVACLASSES_HPP
