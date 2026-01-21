/*
 * Copyright 2026 Arm Limited and/or its affiliates.
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

#ifdef COMPILER2
#include "opto/vtransform.hpp"

float VTransformElementWiseVectorNode::node_weight() const {
  if (vector_opcode() == Op_MulVL && vector_length() == 2) {
    VTransformElementWiseVectorNode* in1 = in_req(1)->isa_ElementWiseVector();
    if (in1 != nullptr && in1->vector_opcode() == Op_MulVL) {
      return 4;
    }
    VTransformElementWiseVectorNode* in2 = in_req(2)->isa_ElementWiseVector();
    if (in2 != nullptr && in2->vector_opcode() == Op_MulVL) {
      return 4;
    }
  }
  return 1;
}
#endif // COMPILER2
