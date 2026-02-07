/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#ifdef COMPILER2
#include "opto/node.hpp"
#include "opto/convertnode.hpp"
#include "opto/vectornode.hpp"
#include "opto/phaseX.hpp"

static Node* LowerVectorCastFloatingPointToLong(PhaseIterGVN* phase, Node* n) {
  if (VM_Version::supports_avx512dq()) {
    return nullptr;
  }

  Node* invec = n->in(1);
  const TypeVect* invecTy = invec->bottom_type()->is_vect();
  const TypeVect* outvecTy = n->bottom_type()->is_vect();

  auto extract_lane = [&](Node* in, int cnt) -> Node* {
    Node* lcnt = phase->intcon(cnt);
    BasicType bt = in->bottom_type()->is_vect()->element_basic_type();
    if (bt == T_FLOAT) {
      return new ExtractFNode(in, lcnt);
    } else {
      assert(bt == T_DOUBLE, "");
      return new ExtractDNode(in, lcnt);
    }
  };

  auto scalar_conv = [&](Node* in) -> Node* {
    BasicType bt = in->bottom_type()->basic_type();
    if (bt == T_FLOAT) {
      return new ConvF2LNode(in);
    } else {
      assert(bt == T_DOUBLE, "");
      return new ConvD2LNode(in);
    }
  };

  Node* res = phase->transform(new ReplicateNode(phase->longcon(0), outvecTy));
  for (uint i = 0; i < invecTy->length(); i++) {
    Node* elem = phase->transform(extract_lane(invec, i));
    Node* conv_elem =  phase->transform(scalar_conv(elem));
    res = phase->transform(new VectorInsertNode(res, conv_elem, phase->intcon(i), outvecTy));
  }
  return res;
}

static Node* LowerVectorCastIntegralToFloatingPoint(PhaseIterGVN* phase, Node* n) {
  if (VM_Version::supports_avx512dq()) {
    return nullptr;
  }

  Node* invec = n->in(1);
  const TypeVect* invecTy = invec->bottom_type()->is_vect();
  const TypeVect* outvecTy = n->bottom_type()->is_vect();
  BasicType ibt = invecTy->element_basic_type();
  BasicType obt = outvecTy->element_basic_type();

  auto extract_lane = [&](Node* in, int cnt) -> Node* {
    Node* lcnt = phase->intcon(cnt);
    BasicType bt = in->bottom_type()->is_vect()->element_basic_type();
    if (bt == T_BYTE) {
      return new ExtractBNode(in, lcnt);
    } else if (bt == T_SHORT) {
      return new ExtractSNode(in, lcnt);
    } else if (bt == T_INT) {
      return new ExtractINode(in, lcnt);
    } else {
      assert(bt == T_LONG, "");
      return new ExtractLNode(in, lcnt);
    }
  };

  auto scalar_conv = [&](Node* in) -> Node* {
    if (obt == T_FLOAT) {
      switch(ibt) {
        case T_BYTE:
        case T_SHORT:
        case T_INT:
          return new ConvI2FNode(in);
        case T_LONG:
          return new ConvL2FNode(in);
        default:
          ShouldNotReachHere();
      }
    } else {
      assert(obt == T_DOUBLE, "");
      switch(ibt) {
        case T_BYTE:
        case T_SHORT:
        case T_INT:
          return new ConvI2DNode(in);
        case T_LONG:
          return new ConvL2DNode(in);
        default:
          ShouldNotReachHere();
      }
    }
  };

  Node* con = phase->transform(phase->makecon(obt == T_FLOAT ?
                 static_cast<const Type*>(TypeF::make(0.0f)) :
                 static_cast<const Type*>(TypeD::make(0.0))));
  Node* res = phase->transform(new ReplicateNode(con, outvecTy));
  for (uint i = 0; i < invecTy->length(); i++) {
    Node* elem = phase->transform(extract_lane(invec, i));
    Node* conv_elem =  phase->transform(scalar_conv(elem));
    res = phase->transform(new VectorInsertNode(res, conv_elem, phase->intcon(i), outvecTy));
  }
  return res;
}


Node* PhaseLowering::lower_node_platform(Node* n) {
  switch(n->Opcode()) {
     case Op_VectorCastF2X:
     case Op_VectorCastD2X:
       if (n->bottom_type()->is_vect()->element_basic_type() == T_LONG) {
         return LowerVectorCastFloatingPointToLong(this, n);
       }
       break;
     case Op_VectorCastL2X:
       if (is_floating_point_type(n->bottom_type()->is_vect()->element_basic_type())) {
         return LowerVectorCastIntegralToFloatingPoint(this, n);
       }
       break;
     case Op_VectorCastI2X:
     case Op_VectorCastB2X:
     case Op_VectorCastS2X:
       if (n->bottom_type()->is_vect()->element_basic_type() != T_DOUBLE) {
         return LowerVectorCastIntegralToFloatingPoint(this, n);
       }
       break;
     default:
       break;
  }
  return nullptr;
}

bool PhaseLowering::should_lower() {
  return true;
}
#endif // COMPILER2
