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

#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahYoungHeuristics.hpp"
#include "gc/shenandoah/mode/shenandoahGenerationalMode.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"
#include "runtime/globals_extension.hpp"

void ShenandoahGenerationalMode::initialize_flags() const {

#if !(defined AARCH64 || defined AMD64 || defined IA32 || defined PPC64 || defined RISCV64)
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
  SHENANDOAH_ERGO_ENABLE_FLAG(ShenandoahImplicitGCInvokesConcurrent);

  // This helps most multi-core hardware hosts, enable by default
  SHENANDOAH_ERGO_ENABLE_FLAG(UseCondCardMark);

  // Final configuration checks
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahLoadRefBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahSATBBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahCASBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahCloneBarrier);
  SHENANDOAH_CHECK_FLAG_SET(ShenandoahCardBarrier);
}
