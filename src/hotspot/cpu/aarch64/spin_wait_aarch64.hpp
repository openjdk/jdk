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

#ifndef CPU_AARCH64_SPIN_WAIT_AARCH64_HPP
#define CPU_AARCH64_SPIN_WAIT_AARCH64_HPP

#define SPIN_WAIT_INST_OPTIONS "nop, isb, yield, sb, none"

class SpinWait {
public:
  // Non-zero values are chosen to have only one bit set.
  // This simplifies testing values in assembly code.
  // This limits us to 64 possible implementation.
  // Value 1 is used for the default implementation.
  enum Inst {
    NONE  = 0,
    YIELD = (1 << 0),
    ISB   = (1 << 1),
    SB    = (1 << 2),
    NOP   = (1 << 3)
  };

private:
  Inst _inst;
  int _count;

  Inst from_name(const char *name);

public:
  SpinWait(Inst inst = NONE, int count = 0) : _inst(inst), _count(inst == NONE ? 0 : count) {}
  SpinWait(const char *name, int count) : SpinWait(from_name(name), count) {}

  Inst inst() const { return _inst; }
  int inst_count() const { return _count; }

  static bool supports(const char *name);
};

#endif // CPU_AARCH64_SPIN_WAIT_AARCH64_HPP
