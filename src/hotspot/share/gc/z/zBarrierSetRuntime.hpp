/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZBARRIERSETRUNTIME_HPP
#define SHARE_GC_Z_ZBARRIERSETRUNTIME_HPP

#include "gc/z/zAddress.hpp"
#include "memory/allStatic.hpp"
#include "oops/accessDecorators.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/globalDefinitions.hpp"

class ZBarrierSetRuntime : public AllStatic {
private:
  static oopDesc* load_barrier_on_oop_field_preloaded(oopDesc* o, oop* p);
  static zpointer load_barrier_on_oop_field_preloaded_store_good(oopDesc* o, oop* p);
  static oopDesc* load_barrier_on_weak_oop_field_preloaded(oopDesc* o, oop* p);
  static oopDesc* load_barrier_on_phantom_oop_field_preloaded(oopDesc* o, oop* p);
  static oopDesc* no_keepalive_load_barrier_on_weak_oop_field_preloaded(oopDesc* o, oop* p);
  static oopDesc* no_keepalive_load_barrier_on_phantom_oop_field_preloaded(oopDesc* o, oop* p);
  static void store_barrier_on_oop_field_with_healing(oop* p);
  static void store_barrier_on_oop_field_without_healing(oop* p);
  static void store_barrier_on_native_oop_field_without_healing(oop* p);
  static void load_barrier_on_oop_array(oop* p, size_t length);
  static void clone(oopDesc* src, oopDesc* dst, size_t size);

public:
  static address load_barrier_on_oop_field_preloaded_addr(DecoratorSet decorators);
  static address load_barrier_on_oop_field_preloaded_addr();
  static address load_barrier_on_oop_field_preloaded_store_good_addr();
  static address load_barrier_on_weak_oop_field_preloaded_addr();
  static address load_barrier_on_phantom_oop_field_preloaded_addr();
  static address no_keepalive_load_barrier_on_weak_oop_field_preloaded_addr();
  static address no_keepalive_load_barrier_on_phantom_oop_field_preloaded_addr();
  static address store_barrier_on_oop_field_with_healing_addr();
  static address store_barrier_on_oop_field_without_healing_addr();
  static address store_barrier_on_native_oop_field_without_healing_addr();
  static address load_barrier_on_oop_array_addr();
  static address clone_addr();
};

#endif // SHARE_GC_Z_ZBARRIERSETRUNTIME_HPP
