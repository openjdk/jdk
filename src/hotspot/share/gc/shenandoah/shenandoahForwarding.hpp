/*
 * Copyright (c) 2013, 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_HPP

#include "oops/oop.hpp"
#include "utilities/globalDefinitions.hpp"

class ShenandoahForwarding {
public:
  static const int self_forwarded_bits                = 1;
  static const int self_forwarded_shift               = markWord::lock_shift + markWord::lock_bits;
  static const uintptr_t self_forwarded_mask          = right_n_bits(self_forwarded_bits);
  static const uintptr_t self_forwarded_mask_in_place = self_forwarded_mask << self_forwarded_shift;

  /* Gets forwardee from the given object.
   */
  static inline oop get_forwardee(oop obj);

  /* Gets forwardee from the given object. Only from mutator thread.
   */
  static inline oop get_forwardee_mutator(oop obj);

  /* Returns the raw value from forwardee slot.
   */
  static inline oop get_forwardee_raw(oop obj);

  /* Returns the raw value from forwardee slot without any checks.
   * Used for quick verification.
   */
  static inline oop get_forwardee_raw_unchecked(oop obj);

  /**
   * Returns true if the object is forwarded, false otherwise.
   */
  static inline bool is_forwarded(oop obj);
};

// Encapsulate relevant forwarding state during evacuation.
// In particular, it avoids to re-load and re-test the mark word
// several times.
class ShenandoahForwardingScope : public StackObj {
private:
  oop const _obj;
  markWord _mark;

  inline oop forwardee(markWord mark) const;
  inline oop forward_to_impl(markWord new_mark);
public:
  inline ShenandoahForwardingScope(oop obj);

  inline markWord mark() const { return _mark; }

  // When the object has been forwarded, returns the forwardee.
  // When the object has been self-forwarded (evacuation failure) returns object itself.
  // Otherwise returns null.
  inline oop forwardee() const;

  // Atomically installs forwarding pointer to fwd.
  // Only one thread can succeed to install the forwarding pointer.
  // When this thread succeeds, returns null.
  // When another thread succeeds, returns the forwarding pointer that has been
  // installed by the other thread.
  inline oop forward_to(oop fwd);

  // Atomically installs forwarding to self (in case of evacuation failure).
  // Only one thread can succeed to install the forwarding pointer.
  // When this thread succeeds, returns null.
  // When another thread succeeds, returns the forwarding pointer that has been
  // installed by the other thread.
  inline oop forward_to_self();
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_HPP
