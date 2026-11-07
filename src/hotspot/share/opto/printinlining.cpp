/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.hpp"
#include "memory/resourceArea.hpp"
#include "opto/callnode.hpp"
#include "opto/printinlining.hpp"

bool InlinePrinter::is_enabled() const {
  return C->print_intrinsics() || C->print_inlining();
}

outputStream* InlinePrinter::record(ciMethod* callee, JVMState* state, InliningResult result, const char* msg) {
  if (!is_enabled()) {
    return &_nullStream;
  }
  if (is_suspended()) {
    return &_nullStream;
  }
  outputStream* stream = locate(state, callee)->add(callee, result);
  if (msg != nullptr) {
    stream->print("%s", msg);
  }
  return stream; // Pointer stays valid, see IPInlineSite::add()
}

outputStream* InlinePrinter::record_type_profile(ciMethod* callee, JVMState* state) {
  if (!is_enabled() || is_suspended()) {
    return &_nullStream;
  }
  return locate(state, callee)->add(nullptr, InliningResult::SUCCESS);
}

void InlinePrinter::print_on(outputStream* tty) const {
  if (!is_enabled()) {
    return;
  }
  // using stringStream prevents interleaving with multiple compile threads
  stringStream ss;
  _root.dump(&ss, -1);
  tty->print_raw(ss.freeze());
}

InlinePrinter::IPInlineSite* InlinePrinter::locate(JVMState* state, ciMethod* callee) {
  auto growableArray = new GrowableArrayCHeap<JVMState*, mtCompiler>(2);

  while (state != nullptr) {
    growableArray->push(state);
    state = state->caller();
  }

  IPInlineSite* site = &_root;
  for (int i = growableArray->length() - 1; i >= 0; i--) {
    site = &site->at_bci(growableArray->at(i)->bci(), i == 0 ? callee : nullptr);
  }

  delete growableArray;

  return site;
}

InlinePrinter::IPInlineSite& InlinePrinter::IPInlineSite::at_bci(int bci, ciMethod* callee) {
  RBTreeCHeap<int, IPInlineSite, Cmp, mtCompiler>::Cursor cursor = _children.cursor(bci);

  if (cursor.found()) { // We already saw a call at this site before
    return cursor.node()->val();
  }

  assert(callee != nullptr, "an inline call is missing in the chain up to the root");

  RBNode<int, IPInlineSite>* node = _children.allocate_node(bci);
  _children.insert_at_cursor(node, cursor);
  node->val().set_source(callee, bci);

  return node->val();
}

outputStream* InlinePrinter::IPInlineSite::add(ciMethod* callee, InliningResult result) {
  _attempts.push(IPInlineAttempt(callee, result));
  return _attempts.last().make_stream();
}

void InlinePrinter::IPInlineSite::dump(outputStream* tty, int level) const {
  assert(_bci != -999, "trying to dump site without source");

  ciMethod* last_callee = nullptr;
  bool line_open = false;
  for (int i = 0; i < _attempts.length(); i++) {
    const IPInlineAttempt& attempt = _attempts.at(i);
    if (attempt.callee() == nullptr) {
      if (line_open) {
        tty->cr();
      } else if (last_callee == nullptr) {
        CompileTask::print_inlining_header(tty, _method, level, _bci);
        tty->cr();
        last_callee = _method;
      }
      CompileTask::print_inline_indent(level, tty);
      tty->print_raw(attempt.stream()->base());
      tty->cr();
      line_open = false;
      continue;
    }
    if (!line_open || attempt.callee() != last_callee) {
      if (line_open) {
        tty->cr();
      }
      CompileTask::print_inlining_header(tty, attempt.callee(), level, _bci);
      line_open = true;
    }
    CompileTask::print_inlining_inner_message(tty, attempt.result(), attempt.stream()->base());
    last_callee = attempt.callee();
  }
  if (line_open) {
    tty->cr();
  }

  _children.visit_in_order([=](auto* node) {
    node->val().dump(tty, level + 1);
    return true;
  });
}
