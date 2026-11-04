/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include "gc/shenandoah/heuristics/shenandoahAggressiveHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahCompactHeuristics.hpp"
#include "gc/shenandoah/mode/shenandoahGenerationalMode.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"
#include "runtime/globals_extension.hpp"

void ShenandoahGenerationalMode::initialize_flags() const {

#if !(defined AARCH64 || defined AMD64 || defined PPC64 || defined RISCV64)
  vm_exit_during_initialization("Shenandoah Generational GC is not supported on this platform.");
#endif

  // Exit if the user has asked ShenandoahCardBarrier to be disabled
  if (!FLAG_IS_DEFAULT(ShenandoahCardBarrier)) {
    SHENANDOAH_CHECK_FLAG_SET(ShenandoahCardBarrier);
  }

  // Enable card-marking post-write barrier for tracking old to young pointers
  FLAG_SET_DEFAULT(ShenandoahCardBarrier, true);

  if (ClassUnloading) {
    FLAG_SET_DEFAULT(VerifyBeforeExit, false);
  }

  SHENANDOAH_ERGO_OVERRIDE_DEFAULT(GCTimeRatio, 70);
  SHENANDOAH_ERGO_ENABLE_FLAG(ExplicitGCInvokesConcurrent);

  // This helps most multi-core hardware hosts, enable by default
  SHENANDOAH_ERGO_ENABLE_FLAG(UseCondCardMark);

  // Final configuration checks
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahLoadRefBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahSATBBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahCloneBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahCardBarrier);

  switch (ShenandoahHeuristicsFlag::value()) {
    case ShenandoahHeuristicsFlag::Heuristic::ADAPTIVE:
      // Nothing to do, this is the default
      break;
    case ShenandoahHeuristicsFlag::Heuristic::PASSIVE:
      // This is also acceptable, but will be handled in the regulator thread
      break;
    case ShenandoahHeuristicsFlag::Heuristic::COMPACT:
      // Configure flags in accordance with the heuristic
      ShenandoahCompactHeuristics::configure();
      break;
    case ShenandoahHeuristicsFlag::Heuristic::AGGRESSIVE:
      // This will configure the periodic gc interval to 1ms, effectively running
      // continuous collections as the aggressive heuristic would do in the single
      // generation mode. Note that global cycles will still only run when there is
      // a need to unload classes. The young collector will still be subject to the
      // rule which guarantees old cycles a 100ms time slice.
      SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahGuaranteedGCInterval, 1);
      SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahGuaranteedYoungGCInterval, 1);
      SHENANDOAH_ERGO_OVERRIDE_DEFAULT(ShenandoahGuaranteedOldGCInterval, 1);

      // Configure other flags in accordance with the heuristic
      ShenandoahAggressiveHeuristics::configure();
      break;
    default:
      log_warning(gc)("Ignoring -XX:ShenandoahGCHeuristics input: %s, "
                      "because generational mode does not support it", ShenandoahGCHeuristics);
      // This changes nothing downstream, but is here so that PrintFlagsFinal shows the effective value
      FLAG_SET_ERGO(ShenandoahGCHeuristics, "adaptive");
  }
}
