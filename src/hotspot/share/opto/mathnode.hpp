/*
* Copyright (c) 2026, IBM and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_MATHNODE_HPP
#define SHARE_OPTO_MATHNODE_HPP

#include "callnode.hpp"

// TODO: move to subnode.hpp/cpp
class PowDNode : public CallLeafPureNode {
    TupleNode* make_tuple_of_input_state_and_result(PhaseIterGVN* phase, Node* result) const;

public:
    PowDNode(Compile* C, Node* base, Node* exp);
    virtual int Opcode() const;
    virtual const Type *Value(PhaseGVN *phase) const;
    virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);

    Node* base() const { return in(TypeFunc::Parms + 0); }
    Node* exp() const  { return in(TypeFunc::Parms + 2); }
};

#endif //SHARE_OPTO_MATHNODE_HPP

