/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allStatic.hpp"

class frame;
class ZForwarding;
class ZPageAllocator;

NOT_DEBUG(inline) void z_verify_safepoints_are_blocked() NOT_DEBUG_RETURN;

class ZVerify : public AllStatic {
private:
  static void roots_strong(bool verify_after_old_mark);
  static void roots_weak();

  static void objects(bool verify_weaks);
  static void threads_start_processing();

  static void after_relocation_internal(ZForwarding* forwarding);

public:
  static void before_zoperation();
  static void after_mark();
  static void after_weak_processing();

  static void before_relocation(ZForwarding* forwarding);
  static void after_relocation(ZForwarding* forwarding);
  static void after_scan(ZForwarding* forwarding);

  static void on_color_flip();
};

#endif // SHARE_GC_Z_ZVERIFY_HPP
