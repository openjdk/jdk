/*
 * Copyright (c) 2026, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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

#ifndef SHARE_OPTO_GENERICOPTS_HPP
#define SHARE_OPTO_GENERICOPTS_HPP

#include "memory/allStatic.hpp"
#include "opto/phaseX.hpp"

// GVN/IGVN optimizations that apply to different types of nodes, so the same
// logic is not duplicated for each node type. For example, `A + B - B` -> `A`
// can apply to INT, LONG, FLOAT, DOUBLE, VECTOR, and so on.
//
// The framework assumes all supported optimizations have the same prototype:
// every optimization takes a root IR node, matches an IR pattern rooted at it,
// and returns a node. What is returned when the pattern does not match follows
// the caller's convention, e.g. nullptr for Ideal and the root for Identity.
// So each optimization is declared with this macro.
#define DECLARE_GENERIC_OPT(opt_name) template<typename NodeClass> \
  static Node* opt_name(NodeClass* root, PhaseGVN* phase);

class GenericOpts : public AllStatic {
 public:
  DECLARE_GENERIC_OPT(Ideal_XorAnd_to_AndNot)
};

#undef DECLARE_GENERIC_OPT

#endif // SHARE_OPTO_GENERICOPTS_HPP
