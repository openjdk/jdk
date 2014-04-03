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

#ifndef SHARE_VM_OPTO_OPAQUENODE_HPP
#define SHARE_VM_OPTO_OPAQUENODE_HPP

#include "opto/node.hpp"
#include "opto/opcodes.hpp"

//------------------------------Opaque1Node------------------------------------
// A node to prevent unwanted optimizations.  Allows constant folding.
// Stops value-numbering, Ideal calls or Identity functions.
class Opaque1Node : public Node {
  virtual uint hash() const ;                  // { return NO_HASH; }
  virtual uint cmp( const Node &n ) const;
  public:
  Opaque1Node( Compile* C, Node *n ) : Node(0,n) {
    // Put it on the Macro nodes list to removed during macro nodes expansion.
    init_flags(Flag_is_macro);
    C->add_macro_node(this);
  }
  // Special version for the pre-loop to hold the original loop limit
  // which is consumed by range check elimination.
  Opaque1Node( Compile* C, Node *n, Node* orig_limit ) : Node(0,n,orig_limit) {
    // Put it on the Macro nodes list to removed during macro nodes expansion.
    init_flags(Flag_is_macro);
    C->add_macro_node(this);
  }
  Node* original_loop_limit() { return req()==3 ? in(2) : NULL; }
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual Node *Identity( PhaseTransform *phase );
};

//------------------------------Opaque2Node------------------------------------
// A node to prevent unwanted optimizations.  Allows constant folding.  Stops
// value-numbering, most Ideal calls or Identity functions.  This Node is
// specifically designed to prevent the pre-increment value of a loop trip
// counter from being live out of the bottom of the loop (hence causing the
// pre- and post-increment values both being live and thus requiring an extra
// temp register and an extra move).  If we "accidentally" optimize through
// this kind of a Node, we'll get slightly pessimal, but correct, code.  Thus
// it's OK to be slightly sloppy on optimizations here.
class Opaque2Node : public Node {
  virtual uint hash() const ;                  // { return NO_HASH; }
  virtual uint cmp( const Node &n ) const;
  public:
  Opaque2Node( Compile* C, Node *n ) : Node(0,n) {
    // Put it on the Macro nodes list to removed during macro nodes expansion.
    init_flags(Flag_is_macro);
    C->add_macro_node(this);
  }
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
};

//------------------------------Opaque3Node------------------------------------
// A node to prevent unwanted optimizations. Will be optimized only during
// macro nodes expansion.
class Opaque3Node : public Opaque2Node {
  int _opt; // what optimization it was used for
  public:
  enum { RTM_OPT };
  Opaque3Node(Compile* C, Node *n, int opt) : Opaque2Node(C, n), _opt(opt) {}
  virtual int Opcode() const;
  bool rtm_opt() const { return (_opt == RTM_OPT); }
};

#endif // SHARE_VM_OPTO_OPAQUENODE_HPP

