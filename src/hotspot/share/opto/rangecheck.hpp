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

#ifndef SHARE_OPTO_RANGECHECK_HPP
#define SHARE_OPTO_RANGECHECK_HPP

#include "opto/subnode.hpp"

// A typical range-check expression compares an affine transformation of the induction variable,
// with loop-invariant scale and offset, against a loop-invariant range.
class RangeCheckInfo {
public:
  BasicType _bt;
  int _scale;
  Node* _offset;
  BoolTest::mask _test;
  bool _is_unsigned;
  Node* _range;

  RangeCheckInfo(BasicType bt, int scale, Node* offset, BoolTest::mask test, bool is_unsigned, Node* range)
    : _bt(bt), _scale(scale), _offset(offset), _test(test), _is_unsigned(is_unsigned), _range(range) {
    assert(test == BoolTest::lt || test == BoolTest::le || test == BoolTest::gt || test == BoolTest::ge,
           "ordered comparison only %d", int(test));
  }
};

#endif // SHARE_OPTO_RANGECHECK_HPP
