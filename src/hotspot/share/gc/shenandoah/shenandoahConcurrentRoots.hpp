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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHCONCURRENTROOTS_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHCONCURRENTROOTS_HPP

#include "memory/allocation.hpp"

class ShenandoahConcurrentRoots : public AllStatic {
public:
  // Can GC settings allow concurrent root processing
  static bool can_do_concurrent_roots();
  // If current GC cycle can process roots concurrently
  static bool should_do_concurrent_roots();

  // If GC settings allow concurrent class unloading
  static bool can_do_concurrent_class_unloading();
  // If current GC cycle can unload classes concurrently
  static bool should_do_concurrent_class_unloading();
};


#endif // SHARE_GC_SHENANDOAH_SHENANDOAHCONCURRENTROOTS_HPP
