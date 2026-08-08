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

#include "opto/int128tnode.hpp"
#include "opto/machnode.hpp"
#include "opto/matcher.hpp"
#include "opto/node.hpp"
#include "opto/type.hpp"

Int128TBinaryNode::Int128TBinaryNode(Node* lo1, Node* hi1, Node* lo2, Node* hi2) : MultiNode(5) {
  init_flags(Flag_is_macro);
  init_req(1, lo1);
  init_req(2, hi1);
  init_req(3, lo2);
  init_req(4, hi2);
  Compile* C = Compile::current();
  assert(C->allow_macro_nodes(), "must before macro expansion");
  C->add_macro_node(this);
}

const Type* Int128TBinaryNode::bottom_type() const {
  const Type* proj_types[2];
  proj_types[0] = TypeLong::LONG;
  proj_types[1] = TypeLong::LONG;
  return TypeTuple::make(2, proj_types);
}

Node* Int128TBinaryNode::match(const ProjNode* proj, const Matcher* matcher) {
  return new MachProjNode(this, proj->_con, *matcher->idealreg2regmask[Op_RegL], Op_RegL);
}
