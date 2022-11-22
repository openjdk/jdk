/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/cfgnode.hpp"
#include "opto/loopnode.hpp"
#include "opto/opaquenode.hpp"
#include "opto/phaseX.hpp"

//=============================================================================
// Do not allow value-numbering
uint Opaque1Node::hash() const { return NO_HASH; }
bool Opaque1Node::cmp( const Node &n ) const {
  return (&n == this);          // Always fail except on self
}

//------------------------------Identity---------------------------------------
// Do NOT remove the opaque node until no more loop opts can happen.
Node* Opaque1Node::Identity(PhaseGVN* phase) {
  if (phase->C->post_loop_opts_phase()) {
    return in(1);
  } else {
    phase->C->record_for_post_loop_opts_igvn(this);
  }
  return this;
}

CountedLoopNode* Opaque1Node::guarded_counted_loop() const {
  if (Opcode() != Op_Opaque1) {
    return NULL;
  }

  CountedLoopNode* loop = NULL;
  for (DUIterator_Fast imax, i = fast_outs(imax); i < imax; i++) {
    Node* u1 = fast_out(i);
    if (u1->Opcode() == Op_CmpI) {
      Node* cmp = u1;
      for (DUIterator_Fast jmax, j = cmp->fast_outs(jmax); j < jmax; j++) {
        Node* u2 = cmp->fast_out(j);
        if (u2->is_Bool()) {
          Node* bol = u2;
          for (DUIterator_Fast kmax, k = bol->fast_outs(kmax); k < kmax; k++) {
            Node* u3 = bol->fast_out(k);
            if (u3->is_If()) {
              IfNode* iff = u3->as_If();
              Node* ctrl_true = try_find_loop(iff, 1);
              Node* ctrl_false = try_find_loop(iff, 0);
              if (ctrl_true != NULL && ctrl_true->is_CountedLoop()) {
                CountedLoopNode* cl = ctrl_true->as_CountedLoop();
                if (cl->is_canonical_loop_entry(false) == this) {
                  assert(loop == NULL, "");
                  loop = cl;
                }
              }
              if (ctrl_false != NULL && ctrl_false->is_CountedLoop()) {
                CountedLoopNode* cl = ctrl_false->as_CountedLoop();
                if (cl->is_canonical_loop_entry(false) == this) {
                  assert(loop == NULL, "");
                  loop = cl;
                }
                assert(loop == NULL || (outcnt() == 1 && cmp->outcnt() == 1 && bol->outcnt() == 1), "opaq can't be shared");
              }
            }
          }
        }
      }
    }
  }
  return loop;
}

Node* Opaque1Node::try_find_loop(const IfNode* iff, uint proj) const {
  Node* ctrl = iff->proj_out_or_null(proj);
  if (ctrl != NULL) {
    ctrl = ctrl->unique_ctrl_out_or_null();
  }
  while (ctrl != NULL && ctrl->is_If()) {
    Node* ctrl_true = ctrl->as_If()->proj_out_or_null(0);
    if (ctrl_true != NULL) {
      ctrl_true = ctrl_true->unique_ctrl_out();
    }
    Node* ctrl_false = ctrl->as_If()->proj_out_or_null(1);
    if (ctrl_false != NULL) {
      ctrl_false = ctrl_false->unique_ctrl_out();
    }
    if (ctrl_true == NULL || ctrl_true->Opcode() == Op_Halt) {
      ctrl = ctrl_false;
    } else if (ctrl_false == NULL || ctrl_false->Opcode() == Op_Halt) {
      ctrl = ctrl_true;
    } else {
      ctrl = NULL;
    }
  }
  if (ctrl != NULL && ctrl->is_OuterStripMinedLoop()) {
    ctrl = ctrl->unique_ctrl_out();
  }
  return ctrl;
}


//=============================================================================
// A node to prevent unwanted optimizations.  Allows constant folding.  Stops
// value-numbering, most Ideal calls or Identity functions.  This Node is
// specifically designed to prevent the pre-increment value of a loop trip
// counter from being live out of the bottom of the loop (hence causing the
// pre- and post-increment values both being live and thus requiring an extra
// temp register and an extra move).  If we "accidentally" optimize through
// this kind of a Node, we'll get slightly pessimal, but correct, code.  Thus
// it's OK to be slightly sloppy on optimizations here.

// Do NOT remove the opaque node until no more loop opts can happen. Opaque1
// and Opaque2 nodes are removed together in order to optimize loops away
// before macro expansion.
Node* Opaque2Node::Identity(PhaseGVN* phase) {
  if (phase->C->post_loop_opts_phase()) {
    return in(1);
  } else {
    phase->C->record_for_post_loop_opts_igvn(this);
  }
  return this;
}

// Do not allow value-numbering
uint Opaque2Node::hash() const { return NO_HASH; }
bool Opaque2Node::cmp( const Node &n ) const {
  return (&n == this);          // Always fail except on self
}

const Type* Opaque4Node::Value(PhaseGVN* phase) const {
  return phase->type(in(1));
}

//=============================================================================

uint ProfileBooleanNode::hash() const { return NO_HASH; }
bool ProfileBooleanNode::cmp( const Node &n ) const {
  return (&n == this);
}

Node *ProfileBooleanNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if (can_reshape && _delay_removal) {
    _delay_removal = false;
    return this;
  } else {
    return NULL;
  }
}

Node* ProfileBooleanNode::Identity(PhaseGVN* phase) {
  if (_delay_removal) {
    return this;
  } else {
    assert(_consumed, "profile should be consumed before elimination");
    return in(1);
  }
}
