/*
 * Copyright (c) 2013, 2018, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHBROOKSPOINTER_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHBROOKSPOINTER_HPP

#include "oops/oop.hpp"
#include "utilities/globalDefinitions.hpp"

class ShenandoahBrooksPointer {
  /*
   * Notes:
   *
   *  a. It is important to have byte_offset and word_offset return constant
   *     expressions, because that will allow to constant-fold forwarding ptr
   *     accesses. This is not a problem in JIT compilers that would generate
   *     the code once, but it is problematic in GC hotpath code.
   *
   *  b. With filler object mechanics, we may need to allocate more space for
   *     the forwarding ptr to meet alignment requirements for objects. This
   *     means *_offset and *_size calls are NOT interchangeable. The accesses
   *     to forwarding ptrs should always be via *_offset. Storage size
   *     calculations should always be via *_size.
   */

public:
  /* Offset from the object start, in HeapWords. */
  static inline int word_offset() {
    return -1; // exactly one HeapWord
  }

  /* Offset from the object start, in bytes. */
  static inline int byte_offset() {
    return -HeapWordSize; // exactly one HeapWord
  }

  /* Allocated size, in HeapWords. */
  static inline uint word_size() {
    return (uint) MinObjAlignment;
  }

  /* Allocated size, in bytes */
  static inline uint byte_size() {
    return (uint) MinObjAlignmentInBytes;
  }

  /* Assert basic stuff once at startup. */
  static void initial_checks() {
    guarantee (MinObjAlignment > 0, "sanity, word_size is correct");
    guarantee (MinObjAlignmentInBytes > 0, "sanity, byte_size is correct");
  }

  /* Initializes Brooks pointer (to self).
   */
  static inline void initialize(oop obj);

  /* Gets forwardee from the given object.
   */
  static inline oop forwardee(oop obj);

  /* Tries to atomically update forwardee in $holder object to $update.
   * Assumes $holder points at itself.
   * Asserts $holder is in from-space.
   * Asserts $update is in to-space.
   */
  static inline oop try_update_forwardee(oop obj, oop update);

  /* Sets raw value for forwardee slot.
   * THIS IS DANGEROUS: USERS HAVE TO INITIALIZE/SET FORWARDEE BACK AFTER THEY ARE DONE.
   */
  static inline void set_raw(oop obj, HeapWord* update);

  /* Returns the raw value from forwardee slot.
   */
  static inline HeapWord* get_raw(oop obj);

  /* Returns the raw value from forwardee slot without any checks.
   * Used for quick verification.
   */
  static inline HeapWord* get_raw_unchecked(oop obj);

private:
  static inline HeapWord** brooks_ptr_addr(oop obj);
};

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHBROOKSPOINTER_HPP
