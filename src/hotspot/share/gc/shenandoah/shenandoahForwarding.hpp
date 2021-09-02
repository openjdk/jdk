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
private:
  static inline oop decode_forwardee(oop obj, markWord mark);
  static inline oop decode_forwardee_mutator(oop obj, markWord mark);

public:
  /* Gets forwardee from the given object.
   * Stable versions are only safe when no evacs happen.
   */
  static inline oop get_forwardee(oop obj);
  static inline oop get_forwardee_maybe_null(oop obj);
  static inline oop get_forwardee_stable(oop obj);

  /* Gets forwardee from the given object. Only from mutator thread.
   */
  static inline oop get_forwardee_mutator(oop obj);

  /* Returns the raw value from forwardee slot without any checks.
   * Used for quick verification.
   * Stable versions are only safe when no evacs happen.
   */
  static inline oop get_forwardee_raw(oop obj);
  static inline oop get_forwardee_stable_raw(oop obj);

  /*
   * Returns true if the object is forwarded, false otherwise.
   */
  static inline bool is_forwarded(oop obj);

  /* Tries to atomically update forwardee in $holder object to $update.
   * Assumes $holder points at itself.
   * Asserts $holder is in from-space.
   * Asserts $update is in to-space.
   *
   * Returns the new object 'update' upon success, or
   * the new forwardee that a competing thread installed.
   */
  static inline oop try_update_forwardee(oop obj, oop update);

  /*
   * Tries to atomically update the heap address to object's forwardee.
   * Stable versions are only safe when no evacs happen.
   */
  static inline void update_with_forwarded(      oop obj,       oop* addr, oop update);
  static inline void update_with_forwarded(      oop obj, narrowOop* addr, oop update);
  static inline void update_with_forwarded(narrowOop obj, narrowOop* addr, oop update);
  static inline void update_with_forwarded_stable(      oop obj,       oop* addr, oop update);
  static inline void update_with_forwarded_stable(      oop obj, narrowOop* addr, oop update);
  static inline void update_with_forwarded_stable(narrowOop obj, narrowOop* addr, oop update);
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_HPP
