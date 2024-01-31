/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_INSTANCESTACKCHUNKKLASS_HPP
#define SHARE_OOPS_INSTANCESTACKCHUNKKLASS_HPP

#include "oops/instanceKlass.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"

class ClassFileParser;
class MemRegion;

// An InstanceStackChunkKlass is a specialization of the InstanceKlass.
//
// The stackChunkOops have a header containing metadata, and a blob containing a
// stack segment (some integral number of stack frames).
//
// A chunk is said to be "mixed" if it contains interpreter frames or stubs
// (which can only be a safepoint stub as the topmost frame). Otherwise, it
// must contain only compiled Java frames.
//
// Interpreter frames in chunks have their internal pointers converted to
// relative offsets from fp. Derived pointers in compiled frames might also
// be converted to relative offsets from their base.

/************************************************

Chunk layout:

                   +--------------------------------+
                   |                                |
                   |  oop bitmap                    |
                   |                                |
                   | ------------------------------ |
                   |                                |
                   |  [empty]                       |
                   |                                |
                  -|================================|
                /  |                                |
               |   | caller stack args              |   argsize
               |   | [metadata at frame top (1)]    | + frame::metadata_words_at_top
               |   | ------------------------------ |   words
               |   | [metadata at frame bottom (2)] |
         ^     |   | frame                          |
         |     |   |                                |
         |   size  | ------------------------------ |
         |   words |                                |
         |     |   | frame                          |
         |     |   |                                |
 Address |     |   | ------------------------------ |
         |     |   |                                |
         |     |   | frame                          |
         |     |   |                                |
         |     |   | callee stack args              |
         |     |   | [metadata at frame top (1)]    |<--\
         |     |   | ------------------------------ |   |
         |     |   | [metadata at frame bottom (2)  |   |
         |     |   |  i.e. rbp, pc]                 |   |
         |     |   |                                |   |
         |     |   | [empty]                        |   |
         |     \   |                                |   |
                 - |================================|   |
                   | int maxSize                    |   |
                   | long pc                        |   |
            header | byte flags                     |   |
                   | int argsize                    |   |
                   | int sp                         +---/
                   | int size                       |
                   +--------------------------------+

 (1) Metadata at frame top (see frame::metadata_words_at_top)
     Used on ppc64, empty on x86_64, aarch64
 (2) Metadata at the frame bottom (see frame::metadata_words_at_bottom)
     Used on x86_64 (saved rbp, ret.addr.), aarch64, empty on ppc64

************************************************/


class InstanceStackChunkKlass: public InstanceKlass {
  friend class VMStructs;
  friend class InstanceKlass;
  friend class Continuations;

public:
  static const KlassKind Kind = InstanceStackChunkKlassKind;

private:
  static int _offset_of_stack;

  InstanceStackChunkKlass(const ClassFileParser& parser);

public:
  InstanceStackChunkKlass();

  // Casting from Klass*
  static InstanceStackChunkKlass* cast(Klass* k) {
    assert(k->is_stack_chunk_instance_klass(), "cast to InstanceStackChunkKlass");
    return static_cast<InstanceStackChunkKlass*>(k);
  }

  inline size_t instance_size(size_t stack_size_in_words) const;

  static inline size_t bitmap_size_in_bits(size_t stack_size_in_words); // In bits
  static inline size_t bitmap_size(size_t stack_size_in_words); // In words
  static inline size_t gc_data_size(size_t stack_size_in_words); // In words

  // Returns the size of the instance including the stack data.
  virtual size_t oop_size(oop obj) const override;

  static void serialize_offsets(class SerializeClosure* f) NOT_CDS_RETURN;

  static void print_chunk(const stackChunkOop chunk, bool verbose, outputStream* st = tty);

#ifndef PRODUCT
  void oop_print_on(oop obj, outputStream* st) override;
#endif

  // Stack offset is an offset into the Heap
  static int offset_of_stack() { return _offset_of_stack; }
  static void init_offset_of_stack();

  // Oop fields (and metadata) iterators
  //
  // The InstanceClassLoaderKlass iterators also visit the CLD pointer (or mirror of anonymous klasses).

  // Forward iteration
  // Iterate over the oop fields and metadata.
  template <typename T, class OopClosureType>
  inline void oop_oop_iterate(oop obj, OopClosureType* closure);

  // Reverse iteration
  // Iterate over the oop fields and metadata.
  template <typename T, class OopClosureType>
  inline void oop_oop_iterate_reverse(oop obj, OopClosureType* closure);

  // Bounded range iteration
  // Iterate over the oop fields and metadata.
  template <typename T, class OopClosureType>
  inline void oop_oop_iterate_bounded(oop obj, OopClosureType* closure, MemRegion mr);

private:
  template <typename T, class OopClosureType>
  inline void oop_oop_iterate_header(stackChunkOop chunk, OopClosureType* closure);

  template <typename T, class OopClosureType>
  inline void oop_oop_iterate_header_bounded(stackChunkOop chunk, OopClosureType* closure, MemRegion mr);

  template <typename T, class OopClosureType>
  inline void oop_oop_iterate_stack(stackChunkOop chunk, OopClosureType* closure);

  template <typename T, class OopClosureType>
  inline void oop_oop_iterate_stack_bounded(stackChunkOop chunk, OopClosureType* closure, MemRegion mr);

  template <typename T, class OopClosureType>
  inline void oop_oop_iterate_stack_with_bitmap(stackChunkOop chunk, OopClosureType* closure, intptr_t* start, intptr_t* end);

  void do_methods(stackChunkOop chunk, OopIterateClosure* cl);

  void oop_oop_iterate_stack_slow(stackChunkOop chunk, OopIterateClosure* closure, MemRegion mr);
};

#endif // SHARE_OOPS_INSTANCESTACKCHUNKKLASS_HPP
