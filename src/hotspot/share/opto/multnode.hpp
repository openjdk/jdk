/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_MULTNODE_HPP
#define SHARE_OPTO_MULTNODE_HPP

#include "opto/node.hpp"

class Matcher;
class ProjNode;

//------------------------------MultiNode--------------------------------------
// This class defines a MultiNode, a Node which produces many values.  The
// values are wrapped up in a tuple Type, i.e. a TypeTuple.
class MultiNode : public Node {
public:
  MultiNode( uint required ) : Node(required) {
    init_class_id(Class_Multi);
  }
  virtual int Opcode() const;
  virtual const Type *bottom_type() const = 0;
  virtual bool       is_CFG() const { return true; }
  virtual uint hash() const { return NO_HASH; }  // CFG nodes do not hash
  virtual bool depends_only_on_test() const { return false; }
  virtual const RegMask &out_RegMask() const;
  virtual Node *match( const ProjNode *proj, const Matcher *m );
  virtual uint ideal_reg() const { return NotAMachineReg; }
  ProjNode* proj_out(uint which_proj) const; // Get a named projection
  ProjNode* proj_out_or_null(uint which_proj) const;
  ProjNode* proj_out_or_null(uint which_proj, bool is_io_use) const;
};

//------------------------------ProjNode---------------------------------------
// This class defines a Projection node.  Projections project a single element
// out of a tuple (or Signature) type.  Only MultiNodes produce TypeTuple
// results.
class ProjNode : public Node {
protected:
  virtual uint hash() const;
  virtual bool cmp( const Node &n ) const;
  virtual uint size_of() const;
  void check_con() const;       // Called from constructor.
  const Type* proj_type(const Type* t) const;

public:
  ProjNode( Node *src, uint con, bool io_use = false )
    : Node( src ), _con(con), _is_io_use(io_use)
  {
    init_class_id(Class_Proj);
    // Optimistic setting. Need additional checks in Node::is_dead_loop_safe().
    if (con != TypeFunc::Memory || src->is_Start())
      init_flags(Flag_is_dead_loop_safe);
    DEBUG_ONLY(check_con());
  }
  const uint _con;              // The field in the tuple we are projecting
  const bool _is_io_use;        // Used to distinguish between the projections
                                // used on the control and io paths from a macro node
  virtual int Opcode() const;
  virtual bool      is_CFG() const;
  virtual bool depends_only_on_test() const { return false; }
  virtual const Type *bottom_type() const;
  virtual const TypePtr *adr_type() const;
  virtual bool pinned() const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual uint ideal_reg() const;
  virtual const RegMask &out_RegMask() const;

#ifndef PRODUCT
  virtual void dump_spec(outputStream *st) const;
  virtual void dump_compact_spec(outputStream *st) const;
#endif

  // Return uncommon trap call node if proj is for "proj->[region->..]call_uct"
  // null otherwise
  CallStaticJavaNode* is_uncommon_trap_proj(Deoptimization::DeoptReason reason = Deoptimization::Reason_none) const;
  // Return uncommon trap call node for    "if(test)-> proj -> ...
  //                                                 |
  //                                                 V
  //                                             other_proj->[region->..]call_uct"
  // null otherwise
  CallStaticJavaNode* is_uncommon_trap_if_pattern(Deoptimization::DeoptReason reason = Deoptimization::Reason_none) const;

  // Return other proj node when this is a If proj node
  ProjNode* other_if_proj() const;
};

/* Tuples are used to avoid manual graph surgery. When a node with Proj outputs (such as a call)
 * must be removed and its ouputs replaced by its input, or some other value, we can make its
 * ::Ideal return a tuple of what we want for each output: the ::Identity of output Proj will
 * take care to jump over the Tuple and directly pick up the right input of the Tuple.
 *
 * For instance, if a function call is proven to have no side effect and return the constant 0,
 * we can replace it with the 6-tuple:
 * (control input, IO input, memory input, frame ptr input, return addr input, Con:0)
 * all the output projections will pick up the input of the now gone call, except for the result
 * projection that is replaced by 0.
 *
 * Using TupleNode avoid manual graph surgery and leave that to our expert surgeon: IGVN.
 * Since the user of a Tuple are expected to be Proj, when creating a tuple during idealization,
 * the output Proj should be enqueued for IGVN immediately after, and the tuple should not survive
 * after the current IGVN.
 */
class TupleNode : public MultiNode {
  const TypeTuple* _tf;

  template <typename... NN>
  static void make_helper(TupleNode* tn, uint i, Node* node, NN... nn) {
    tn->set_req(i, node);
    make_helper(tn, i + 1, nn...);
  }

  static void make_helper(TupleNode*, uint) {}

public:
  TupleNode(const TypeTuple* tf) : MultiNode(tf->cnt()), _tf(tf) {}

  int Opcode() const override;
  const Type* bottom_type() const override { return _tf; }

  /* Give as many `Node*` as you want in the `nn` pack:
   * TupleNode::make(tf, input1)
   * TupleNode::make(tf, input1, input2, input3, input4)
   */
  template <typename... NN>
  static TupleNode* make(const TypeTuple* tf, NN... nn) {
    TupleNode* tn = new TupleNode(tf);
    make_helper(tn, 0, nn...);
    return tn;
  }
};

#endif // SHARE_OPTO_MULTNODE_HPP
