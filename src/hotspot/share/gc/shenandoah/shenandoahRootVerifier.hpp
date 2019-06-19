/*
 * Copyright (c) 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHROOTVERIFIER_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHROOTVERIFIER_HPP

#include "memory/allocation.hpp"
#include "memory/iterator.hpp"

class ShenandoahRootVerifier : public StackObj {
public:
  enum RootTypes {
    SerialRoots         = 1 << 0,
    ThreadRoots         = 1 << 1,
    CodeRoots           = 1 << 2,
    CLDGRoots           = 1 << 3,
    WeakRoots           = 1 << 4,
    StringDedupRoots    = 1 << 5,
    JNIHandleRoots      = 1 << 6,
    AllRoots            = (SerialRoots | ThreadRoots | CodeRoots | CLDGRoots | WeakRoots | StringDedupRoots | JNIHandleRoots)
  };

private:
  RootTypes _types;

public:
  ShenandoahRootVerifier();

  void excludes(RootTypes types);
  void oops_do(OopClosure* cl);

  // Used to seed ShenandoahVerifier, do not honor root type filter
  void roots_do(OopClosure* cl);
  void strong_roots_do(OopClosure* cl);
private:
  bool verify(RootTypes type) const;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHROOTVERIFIER_HPP
