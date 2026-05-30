/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_INT128NODE_HPP
#define SHARE_OPTO_INT128NODE_HPP

#include "opto/multnode.hpp"

class Matcher;
class ProjNode;
class Type;

// An operation producing an int128 consisting of 2 longs, a lo and a hi Projs
class Int128TBinaryNode : public MultiNode {
public:
  Int128TBinaryNode(Node* lo1, Node* hi1, Node* lo2, Node* hi2);

  Node* result_lo_or_null() const { return proj_out_or_null(lo_proj_num); }
  Node* result_hi_or_null() const { return proj_out_or_null(hi_proj_num); }
  Node* lo1() const { return in(1); }
  Node* hi1() const { return in(2); }
  Node* lo2() const { return in(3); }
  Node* hi2() const { return in(4); }

  virtual const Type* bottom_type() const override final;
  virtual bool is_CFG() const override final { return false; }
  virtual uint ideal_reg() const override final { return NotAMachineReg; }
  virtual Node* match(const ProjNode* proj, const Matcher* m) override;

  static constexpr uint lo_proj_num = 0;
  static constexpr uint hi_proj_num = 1;
};

class AddI128TNode : public Int128TBinaryNode {
public:
  AddI128TNode(Node* lo1, Node* hi1, Node* lo2, Node* hi2) : Int128TBinaryNode(lo1, hi1, lo2, hi2) {}

  virtual int Opcode() const override;
};

class SubI128TNode : public Int128TBinaryNode {
public:
  SubI128TNode(Node* lo1, Node* hi1, Node* lo2, Node* hi2) : Int128TBinaryNode(lo1, hi1, lo2, hi2) {}

  virtual int Opcode() const override;
};

#endif // SHARE_OPTO_INT128NODE_HPP
