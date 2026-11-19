/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/heuristics/shenandoahAdaptiveHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahAggressiveHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahCompactHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahStaticHeuristics.hpp"
#include "gc/shenandoah/mode/shenandoahMode.hpp"

ShenandoahModeFlag::Mode ShenandoahModeFlag::_value = Mode::GENERATIONAL;
void ShenandoahModeFlag::initialize() {
  if (ShenandoahGCMode == nullptr) {
    vm_exit_during_initialization("Unknown -XX:ShenandoahGCMode option (null)");
  }

  if (strcmp(ShenandoahGCMode, "satb") == 0) {
    _value = Mode::SATB;
  } else if (strcmp(ShenandoahGCMode, "passive") == 0) {
    _value = Mode::PASSIVE;
  } else if (strcmp(ShenandoahGCMode, "generational") == 0) {
    _value = Mode::GENERATIONAL;
  } else {
    vm_exit_during_initialization("Unknown -XX:ShenandoahGCMode option");
  }
}

ShenandoahHeuristics* ShenandoahMode::initialize_heuristics(ShenandoahSpaceInfo* space_info) const {
  switch (ShenandoahHeuristicsFlag::value()) {
    case ShenandoahHeuristicsFlag::Heuristic::AGGRESSIVE:
      return new ShenandoahAggressiveHeuristics(space_info);
    case ShenandoahHeuristicsFlag::Heuristic::STATIC:
      return new ShenandoahStaticHeuristics(space_info);
    case ShenandoahHeuristicsFlag::Heuristic::ADAPTIVE:
    case ShenandoahHeuristicsFlag::Heuristic::PASSIVE:
      // Passive flag gets regular heuristics for collection set construction, but
      // adaptive heuristic triggers will be suppressed.
      return new ShenandoahAdaptiveHeuristics(space_info);
    case ShenandoahHeuristicsFlag::Heuristic::COMPACT:
      return new ShenandoahCompactHeuristics(space_info);
    default:
      ShouldNotReachHere();
  }
}
