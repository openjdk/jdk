/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZJNICRITICAL_HPP
#define SHARE_GC_Z_ZJNICRITICAL_HPP

#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"

class JavaThread;
class ZConditionLock;

class ZJNICritical : public AllStatic {
private:
  static Atomic<int64_t> _count;
  static ZConditionLock* _lock;

  static void enter_inner(JavaThread* thread);
  static void exit_inner();

public:
  // For use by GC
  static void initialize();
  static void block();
  static void unblock();

  // For use by Java threads
  static void enter(JavaThread* thread);
  static void exit(JavaThread* thread);
};

#endif // SHARE_GC_Z_ZJNICRITICAL_HPP
