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

#include "opto/mempointer.hpp"
#include "utilities/resourceHash.hpp"

MemPointerSimpleForm MemPointer::parse_simple_form(Node* pointer) {
  ResourceMark rm;
  ResourceHashtable<int, MemPointerSimpleForm> idx_to_simple_form;
  GrowableArray<Node*> stack;

  auto get_simple_form_of_input_or_push = [&](Node* n, int i) {
    Node* in = n->in(i);
    MemPointerSimpleForm* simple_form = idx_to_simple_form.get(in->_idx);
    if (simple_form == nullptr) {
      stack.push(in);
    }
    return simple_form;
  };

  stack.push(pointer);
  while (stack.is_nonempty()) {
    Node* n = stack.top();

    if (idx_to_simple_form.get(n->_idx) != nullptr) {
      stack.pop(); // already processed elsewhere
      continue;
    }

    n->dump();

    int opc = n->Opcode();
    switch (opc) {
      case Op_ConI:
      case Op_ConL:
      {
        jlong con = (opc == Op_ConI) ? n->get_int() : n->get_long();
        MemPointerSimpleForm f = MemPointerSimpleForm::make_from_ConIL(n, con);
        idx_to_simple_form.put_when_absent(n->_idx, f);
        stack.pop();
        continue;
      }
      case Op_AddP:
      case Op_AddL:
      case Op_AddI:
      case Op_SubL:
      case Op_SubI:
      {
        const MemPointerSimpleForm* a = get_simple_form_of_input_or_push(n, (opc == Op_AddP) ? 2 : 1);
        const MemPointerSimpleForm* b = get_simple_form_of_input_or_push(n, (opc == Op_AddP) ? 3 : 2);
        if (a == nullptr || b == nullptr) { continue; }
        MemPointerSimpleForm f = MemPointerSimpleForm::make_from_AddSubILP(n, a, b);
        idx_to_simple_form.put_when_absent(n->_idx, f);
        stack.pop();
        continue;
      }
      case Op_MulL:
      case Op_MulI:
      case Op_LShiftL:
      case Op_LShiftI:
      {
        // Form must be linear: only multiplication with constants is allowed.
        Node* in2 = n->in(2);
        if (!in2->is_Con()) { break; }
        jlong scale;
        switch (opc) {
          case Op_MulL: scale = in2->get_long(); break;
          case Op_MulI: scale = in2->get_int();  break;
          case Op_LShiftL:
          case Op_LShiftI:
            assert(false, "shift");
        }
        // Scale cannot be too large: TODO make this a special method, maybe better threshold?
        const jlong max_scale = 1 << 30;
        if (scale > max_scale || scale < -max_scale) { break; }

        const MemPointerSimpleForm* a = get_simple_form_of_input_or_push(n, 1);
        if (a == nullptr) { continue; }
        MemPointerSimpleForm f = MemPointerSimpleForm::make_from_Mul(n, a, scale);
        idx_to_simple_form.put_when_absent(n->_idx, f);
        stack.pop();
        continue;
      }
      case Op_CastII:
      case Op_CastLL:
      case Op_CastX2P:
      {
        assert(false, "unary");
        break;
      }
      case Op_ConvI2L:
      {
        const MemPointerSimpleForm* a = get_simple_form_of_input_or_push(n, 1);
        if (a == nullptr) { continue; }
        MemPointerSimpleForm f = MemPointerSimpleForm::make_from_ConvI2L(n, a);
        idx_to_simple_form.put_when_absent(n->_idx, f);
        stack.pop();
        continue;
      }
    }
    assert(false, "default");
  }

  return MemPointerSimpleForm();
}

MemPointerSimpleForm MemPointerSimpleForm::make_from_ConIL(Node* n, const jlong con) {
  return MemPointerSimpleForm(); // TODO
}

MemPointerSimpleForm MemPointerSimpleForm::make_from_AddSubILP(Node* n, const MemPointerSimpleForm* a, const MemPointerSimpleForm* b) {
  return MemPointerSimpleForm(); // TODO
}

MemPointerSimpleForm MemPointerSimpleForm::make_from_Mul(Node* n, const MemPointerSimpleForm* a, const jlong scale) {
  return MemPointerSimpleForm(); // TODO
}

MemPointerSimpleForm MemPointerSimpleForm::make_from_ConvI2L(Node* n, const MemPointerSimpleForm* a) {
  return MemPointerSimpleForm(); // TODO
}

bool MemPointer::is_adjacent_to_and_before(const MemPointer& other) const {
  return true; // TODO
}

