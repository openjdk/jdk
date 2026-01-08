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
 */

#include "spin_wait_aarch64.hpp"
#include "utilities/debug.hpp"

#include <string.h>

bool SpinWait::supports(const char *name) {
  return name != nullptr &&
         (strcmp(name, "nop")   == 0 ||
          strcmp(name, "isb")   == 0 ||
          strcmp(name, "yield") == 0 ||
          strcmp(name, "sb")    == 0 ||
          strcmp(name, "none")  == 0);
}

SpinWait::Inst SpinWait::from_name(const char* name) {
  assert(supports(name), "checked by OnSpinWaitInstNameConstraintFunc");

  if (strcmp(name, "nop") == 0) {
    return SpinWait::NOP;
  } else if (strcmp(name, "isb") == 0) {
    return SpinWait::ISB;
  } else if (strcmp(name, "yield") == 0) {
    return SpinWait::YIELD;
  } else if (strcmp(name, "sb") == 0) {
    return SpinWait::SB;
  }

  return SpinWait::NONE;
}
