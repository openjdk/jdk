/*
 * Copyright (c) 2021, Amazon.com Inc. or its affiliates. All rights reserved.
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

#ifndef CPU_AARCH64_SPIN_WAIT_AARCH64_HPP
#define CPU_AARCH64_SPIN_WAIT_AARCH64_HPP

// SpinWait provides a description for implementations of spin wait/pause.
// The description includes:
// - what an instruction should be used by an implementation.
// - how many of the instructions.
// - a runner which can execute the requested number of instructions.
//
// Creation of SpinWait is controlled by VM_Version.
class SpinWait final {
public:
  enum Inst {
    NONE = -1,
    NOP,
    ISB,
    YIELD
  };
  using InstRunner = void (*)(int count);

private:
  Inst _inst;
  int _count;
  InstRunner _inst_runner;

  static void run_nop(int count) {
    while (count-- > 0) {
      __asm volatile("nop");
    }
  }

  static void run_isb(int count) {
    while (count-- > 0) {
      __asm volatile("isb");
    }
  }

  static void run_yield(int count) {
    while (count-- > 0) {
      __asm volatile("yield");
    }
  }

  static void run_none(int) {}

  SpinWait(Inst inst = NONE, int count = 0, InstRunner inst_runner = run_none) :
      _inst(inst), _count(count), _inst_runner(inst_runner)  {}

public:
  Inst inst() const { return _inst; }
  int inst_count() const { return _count; }
  InstRunner inst_runner() const { return _inst_runner; }

  friend class VM_Version;
};

#endif // CPU_AARCH64_SPIN_WAIT_AARCH64_HPP
