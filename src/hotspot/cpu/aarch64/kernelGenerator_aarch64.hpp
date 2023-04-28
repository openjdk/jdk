/*
 * Copyright (c) 2023, Red Hat Inc. All rights reserved.
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

#ifndef CPU_AARCH64_KERNELGENERATOR_AARCH64_HPP
#define CPU_AARCH64_KERNELGENERATOR_AARCH64_HPP

#include "macroAssembler_aarch64.hpp"
#include "memory/resourceArea.hpp"

// KernelGenerator
//
// The abstract base class of an unrolled function generator.
// Subclasses override generate(), length(), and next() to generate
// unrolled and interleaved functions.
//
// The core idea is that a subclass defines a method which generates
// the base case of a function and a method to generate a clone of it,
// shifted to a different set of registers. KernelGenerator will then
// generate several interleaved copies of the function, with each one
// using a different set of registers.

// The subclass must implement three methods: length(), which is the
// number of instruction bundles in the intrinsic, generate(int n)
// which emits the nth instruction bundle in the intrinsic, and next()
// which takes an instance of the generator and returns a version of it,
// shifted to a new set of registers.

class KernelGenerator: public MacroAssembler {
protected:
  const int _unrolls;
public:
  KernelGenerator(Assembler *as, int unrolls)
    : MacroAssembler(as->code()), _unrolls(unrolls) { }
  virtual void generate(int index) = 0;
  virtual int length() = 0;
  virtual KernelGenerator *next() = 0;
  int unrolls() { return _unrolls; }
  void unroll();
};

inline void KernelGenerator::unroll() {
  ResourceMark rm;
  KernelGenerator **generators
    = NEW_RESOURCE_ARRAY(KernelGenerator *, unrolls());

  generators[0] = this;
  for (int i = 1; i < unrolls(); i++) {
    generators[i] = generators[i-1]->next();
  }

  for (int j = 0; j < length(); j++) {
    for (int i = 0; i < unrolls(); i++) {
      generators[i]->generate(j);
    }
  }
}

#endif // CPU_AARCH64_KERNELGENERATOR_AARCH64_HPP
