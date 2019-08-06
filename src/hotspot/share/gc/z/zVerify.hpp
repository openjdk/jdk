/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZVERIFY_HPP
#define SHARE_GC_Z_ZVERIFY_HPP

#include "memory/allocation.hpp"

class ZVerify : public AllStatic {
private:
  template <typename RootsIterator>
  static void roots_impl();
  static void roots(bool verify_weaks);

  static void roots_strong();
  static void roots_weak();
  static void roots_concurrent();
  static void roots_concurrent_weak();

  static void objects(bool verify_weaks);

  static void roots_and_objects(bool visit_weaks);

public:
  // Verify strong (non-concurrent) roots. Should always be good.
  static void before_zoperation();

  // Verify all strong roots and references after marking.
  static void after_mark();

  // Verify strong and weak roots and references.
  static void after_weak_processing();
};

class VM_ZVerifyOperation : public VM_Operation {
public:
  virtual bool needs_inactive_gc_locker() const {
    // An inactive GC locker is needed in operations where we change the bad
    // mask or move objects. Changing the bad mask will invalidate all oops,
    // which makes it conceptually the same thing as moving all objects.
    return false;
  }

  virtual void doit() {
    ZVerify::after_weak_processing();
  }

  bool success() const {
    return true;
  }

  virtual VMOp_Type type() const { return VMOp_ZVerify; }
};

#endif // SHARE_GC_Z_ZVERIFY_HPP
