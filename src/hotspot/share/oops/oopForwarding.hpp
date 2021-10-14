/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_MARKWORDDECODER_HPP
#define SHARE_OOPS_MARKWORDDECODER_HPP

#include "gc/shared/collectedHeap.hpp"
#include "memory/universe.hpp"
#include "oops/markWord.hpp"

/**
 * Decodes pointers that are encoded in object's mark-words. Specifically, it structures common sequences like:
 * if (obj->is_forwarded()) { // Load mark-word and test lowest 2 bits for 0b11
 *   fwd = obj->forwardee();  // Load mark-word and mask-out lowest 3 bits
 * } else
 *   fwd = ... // Something else
 * }
 *
 * to be most efficient.
 * The above structure has a number of problems:
 * - It loads the mark-word twice. The compiler may choose to coalesce the loads, though.
 * - Assuming the compiler can coalesce the loads, or it has been written better to begin with,
 *   the generated assembly code would typically look like this:
 *
 *   mov r, (robj)     ; Load mark-word into r
 *   mov rtmp, r       ; Preserve r for decoding path, requires allocation of temp register
 *   and rtmp, 0b11    ; Mask lowest two bits
 *   cmp rtmp, 0b11    ; Check if both bits set
 *   jne false-branch  ; Do 'Something else'
 *   mov rtmp, ~0b11   ; Load large immediate constant, requires several instructions on x86 other arches
 *   ...               ; More instructions (shifts, moves) to get immediate into rtmp
 *   and r, rtmp       ; Mask upper bits
 *   ...               ; false-branch returns here, too
 *
 * We can improve this by:
 * - Not loading the mark-word twice
 * - Not requiring a tmp register
 * - Avoiding large immediate constant
 * - Indeed, making the true-branch a no-op
 *
 * We can do so by inverting the bits that we want to test for using XOR, and test for 0. By this, we readily
 * get the decoded pointer in the target register:
 *
 *   mov r, (robj)     ; Load mark-word into r
 *   xor r, 0b11       ; Invert lowest two bits
 *   test r, 0b11      ; Test (mask) lowest two bits
 *   jne false-branch  ; Do 'Something else'
 *   ...               ; false-branch returns here, true-branch has result in r already
 */
class OopForwarding {
private:
  const oop _obj;
  const uintptr_t _value;

  static void verify_forwardee(oop obj, oop forwardee) {
#ifdef ASSERT
#if INCLUDE_CDS_JAVA_HEAP
    assert(!Universe::heap()->is_archived_object(forwardee) && !Universe::heap()->is_archived_object(obj),
           "forwarding archive object");
#endif
#endif
  }

public:
  explicit OopForwarding(oop obj) :
  _obj(obj), _value(obj->mark().value() ^ markWord::marked_value) {}

  inline bool is_forwarded() const {
    return (_value & markWord::lock_mask_in_place) == 0;
  }

  template <typename T>
  inline T forwardee() const {
    assert(is_forwarded(), "only decode when encoded");
    return reinterpret_cast<T>(_value);
  }

  inline oop forwardee() const {
    return cast_to_oop(forwardee<HeapWord*>());
  }

  static void forward_to(oop obj, oop fwd) {
    verify_forwardee(obj, fwd);
    markWord m = markWord::encode_pointer_as_mark(fwd);
    assert(m.decode_pointer() == fwd, "encoding must be reversable");
    obj->set_mark(m);
  }

  // Like "forward_to", but inserts the forwarding pointer atomically.
  // Exactly one thread succeeds in inserting the forwarding pointer, and
  // this call returns "NULL" for that thread; any other thread has the
  // value of the forwarding pointer returned and does not modify "this".
  oop forward_to_atomic(oop p, atomic_memory_order order = memory_order_conservative) const {
    verify_forwardee(_obj, p);
    markWord compare = mark();
    markWord m = markWord::encode_pointer_as_mark(p);
    assert(m.decode_pointer() == p, "encoding must be reversable");
    markWord old_mark = _obj->cas_set_mark(m, compare, order);
    if (old_mark == compare) {
      return NULL;
    } else {
      return cast_to_oop(old_mark.decode_pointer());
    }
  }

  markWord mark() const {
    return markWord(_value ^ markWord::marked_value);
  }
};

#endif // SHARE_OOPS_MARKWORDDECODER_HPP
