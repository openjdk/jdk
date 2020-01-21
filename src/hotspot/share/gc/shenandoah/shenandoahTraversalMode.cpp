/*
 * Copyright (c) 2019, Red Hat, Inc. All rights reserved.
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
#include "gc/shenandoah/shenandoahTraversalMode.hpp"
#include "gc/shenandoah/heuristics/shenandoahTraversalAggressiveHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahTraversalHeuristics.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"

void ShenandoahTraversalMode::initialize_flags() const {
  FLAG_SET_DEFAULT(ShenandoahSATBBarrier,            false);
  FLAG_SET_DEFAULT(ShenandoahStoreValEnqueueBarrier, true);
  FLAG_SET_DEFAULT(ShenandoahKeepAliveBarrier,       false);
  FLAG_SET_DEFAULT(ShenandoahAllowMixedAllocs,       false);

  SHENANDOAH_ERGO_ENABLE_FLAG(ExplicitGCInvokesConcurrent);
  SHENANDOAH_ERGO_ENABLE_FLAG(ShenandoahImplicitGCInvokesConcurrent);

  // Final configuration checks
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahLoadRefBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahStoreValEnqueueBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahCASBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahCloneBarrier);
}

ShenandoahHeuristics* ShenandoahTraversalMode::initialize_heuristics() const {
  if (ShenandoahGCHeuristics != NULL) {
    if (strcmp(ShenandoahGCHeuristics, "adaptive") == 0) {
      return new ShenandoahTraversalHeuristics();
    } else if (strcmp(ShenandoahGCHeuristics, "aggressive") == 0) {
      return new ShenandoahTraversalAggressiveHeuristics();
    } else {
      vm_exit_during_initialization("Unknown -XX:ShenandoahGCHeuristics option");
    }
  }
  ShouldNotReachHere();
  return NULL;
}
