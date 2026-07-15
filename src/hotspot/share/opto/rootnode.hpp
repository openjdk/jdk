/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_ROOTNODE_HPP
#define SHARE_OPTO_ROOTNODE_HPP

#include "opto/loopnode.hpp"

//------------------------------RootNode---------------------------------------
// The one-and-only before-all-else and after-all-else RootNode.  The RootNode
// represents what happens if the user runs the whole program repeatedly.  The
// RootNode produces the initial values of I/O and memory for the program or
// procedure start.
class RootNode : public LoopNode {
public:
  RootNode( ) : LoopNode(nullptr, nullptr) {
    init_class_id(Class_Root);
    del_req(2);
    del_req(1);
  }
  virtual int   Opcode() const;
  virtual const Node *is_block_proj() const { return this; }
  virtual const Type *bottom_type() const { return Type::BOTTOM; }
  virtual Node* Identity(PhaseGVN* phase) { return this; }
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type* Value(PhaseGVN* phase) const { return Type::BOTTOM; }
};

//------------------------------HaltNode---------------------------------------
// Throw an exception & die
class HaltNode : public Node {
protected:
  virtual uint size_of() const;
public:
  const char* _halt_reason;
  bool        _reachable;
  HaltNode(Node* ctrl, Node* frameptr, const char* halt_reason, bool reachable = true);
  virtual int Opcode() const;
  virtual bool  pinned() const { return true; };
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual const Type *bottom_type() const;
  virtual bool  is_CFG() const { return true; }
  virtual uint hash() const { return NO_HASH; }  // CFG nodes do not hash
  virtual const Node *is_block_proj() const { return this; }
  virtual const RegMask &out_RegMask() const;
  virtual uint ideal_reg() const { return NotAMachineReg; }
  virtual uint match_edge(uint idx) const { return 0; }
};


// This node collects paths that are found dead by PhaseIterGVN::make_dependent_paths_dead_if_top()

// There is a single DeadPath node for the lifetime of optimizations. It's initially not active (i.e. unreachable from
// the IR graph). When a cfg path becomes dead it's added as an input to the unique DeadPath node. If after some
// optimizations run, the DeadPath node gets disconnected, it's not destroyed. It becomes inactive and can possibly be
// activated again on a subsequent igvn. When optimizations are over, the DeadPath node, if it is active, is expanded to
// a Region and Halt node in Compile::final_graph_reshaping().

// Rather than having this dedicated node, igvn could add a Halt node everytime it finds a dead cfg path from a data
// node. What's likely, however, is that as igvn progresses, that same cfg path is found dead by following cfg edges.
// The Halt node then becomes dead. To avoid this unnecessary cycle of creation of a Halt node only to have it be found
// dead shortly after, dead cfg paths are added to the unique DeadPath node.
class DeadPathNode : public RegionNode {
public:
  DeadPathNode() : RegionNode(1) {
    deactivate();
    assert(Compile::current()->dead_path() == nullptr, "only one");
  }
  virtual int   Opcode() const;
  virtual const Type* bottom_type() const { return Type::BOTTOM; }
  virtual Node* Identity(PhaseGVN* phase) { return this; }
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
  virtual const Type* Value(PhaseGVN* phase) const;
  bool is_active() const {
    return in(0) == this;
  }
  void activate(PhaseIterGVN* igvn);
  void deactivate();
};

#endif // SHARE_OPTO_ROOTNODE_HPP
