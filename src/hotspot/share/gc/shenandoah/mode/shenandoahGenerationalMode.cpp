/*
 * Copyright (c) 2019, 2020, Red Hat, Inc. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/shenandoah/mode/shenandoahGenerationalMode.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"
#include "runtime/globals_extension.hpp"

void ShenandoahGenerationalMode::initialize_flags() const {
  // When we fill in dead objects during update refs, we use oop::size,
  // which depends on the klass being loaded. However, if these dead objects
  // were the last referrers to the klass, it will be unloaded and we'll
  // crash. Class unloading is disabled until we're able to sort this out.
  FLAG_SET_ERGO(ClassUnloading, false);
  FLAG_SET_ERGO(ClassUnloadingWithConcurrentMark, false);
  FLAG_SET_ERGO(ShenandoahUnloadClassesFrequency, 0);

  if (ClassUnloading) {
    // Leaving this here for the day we re-enable class unloading
    FLAG_SET_DEFAULT(ShenandoahSuspendibleWorkers, true);
    FLAG_SET_DEFAULT(VerifyBeforeExit, false);
  }

  SHENANDOAH_ERGO_ENABLE_FLAG(ExplicitGCInvokesConcurrent);
  SHENANDOAH_ERGO_ENABLE_FLAG(ShenandoahImplicitGCInvokesConcurrent);

  // This helps most multi-core hardware hosts, enable by default
  SHENANDOAH_ERGO_ENABLE_FLAG(UseCondCardMark);

  // Final configuration checks
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahLoadRefBarrier);
  SHENANDOAH_CHECK_FLAG_UNSET(ShenandoahIUBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahSATBBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahCASBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahCloneBarrier);
  SHENANDOAH_CHECK_FLAG_UNSET(ClassUnloading);
}

const char *affiliation_name(ShenandoahRegionAffiliation type) {
  switch (type) {
    case ShenandoahRegionAffiliation::FREE:
      return "FREE";
    case ShenandoahRegionAffiliation::YOUNG_GENERATION:
      return "YOUNG";
    case ShenandoahRegionAffiliation::OLD_GENERATION:
      return "OLD";
    default:
      return "UnrecognizedAffiliation";
  }
}
