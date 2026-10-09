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

#include "oops/markWord.hpp"
#include "oops/oop.hpp"
#include "utilities/globalDefinitions.hpp"

class ShenandoahForwarding {
public:
  /* Returns the forwardee.
   */
  static inline oop forwardee(oop obj);

  /* Returns the forwardee, or null if not forwarded.
   */
  static inline oop forwardee_or_null(oop obj);

  /* Returns the raw forwardee without extra checks.
   */
  static inline oop forwardee_raw(oop obj);

  /* Returns true iff the object is forwarded:
   * either real-forwarded or self-forwarded.
   */
  static inline bool is_forwarded(oop obj);

  /* Returns true iff the object is real-forwarded:
   * there is a forwardee that is not the object itself.
   */
  static inline bool is_real_forwarded(oop obj);

  /* Returns true iff the object is self-forwarded:
   * there is a forwardee, it is the object itself.
   */
  static inline bool is_self_forwarded(oop obj);

  /* Tries to atomically update forwardee in $obj to $update.
   *
   * Returns the actual forwardee, whether installed by this call
   * or discovered during the conflict.
   */
  static inline oop try_forward_to(oop obj, oop update);

  /* Unsets self-forwarding bit on the object.
   * WARNING: This is only safe to do when no evacuations happen.
   */
  static inline void unset_self_forwarded(oop obj);

  /* Gets the size of the object, taking care of any forwardings.
   */
  static inline size_t size(oop obj);

  /* Gets the klass of the object, taking care of any forwardings.
   */
  static inline Klass* klass(oop obj);

  /* Gets the age of the object, taking care of any forwardings.
   */
  static inline uint age(oop obj);

  /* Bumps the age of the object.
   * WARNING: This method is expected to operate on a copy that is
   * not accessible to normal use.
   */
  static inline void increase_age(oop obj, uint add);

private:
  static inline oop forwardee_raw(oop obj, markWord mark);
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFORWARDING_HPP
