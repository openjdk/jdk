/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHRUNTIME_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHRUNTIME_HPP

#include "memory/allocation.hpp"
#include "oops/oopsHierarchy.hpp"

class JavaThread;
class oopDesc;

class ShenandoahRuntime : public AllStatic {
public:
  static void write_ref_array_pre_oop_entry(oop* dst, size_t length);
  static void write_ref_array_pre_narrow_oop_entry(narrowOop* dst, size_t length);
  static void write_ref_array_post_entry(HeapWord* dst, size_t length);
  static void write_ref_field_pre_entry(oopDesc* orig, JavaThread* thread);

  static oopDesc* load_reference_barrier_JRT(oopDesc* src);

  static void shenandoah_clone_barrier(oopDesc* obj);
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHRUNTIME_HPP
