/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
  outputStream* stream = locate(state, callee)->add(result);
  if (msg != nullptr) {
    stream->print("%s", msg);
  }
  return stream; // Pointer stays valid, see IPInlineSite::add()
}

void InlinePrinter::print_on(outputStream* tty) const {
  if (!is_enabled()) {
    return;
  }
  _root.dump(tty, -1);
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
  auto find_result = _children.find(bci);
  IPInlineSite& child = find_result.node->val();

  if (find_result.new_node) {
    assert(callee != nullptr, "an inline call is missing in the chain up to the root");
    child.set_source(callee, bci);
  } else { // We already saw a call at this site before
    if (callee != nullptr && callee != child._method) {
      outputStream* stream = child.add(InliningResult::SUCCESS);
      stream->print("callee changed to ");
      CompileTask::print_inline_inner_method_info(stream, callee);
    }
  }

  return child;
}

outputStream* InlinePrinter::IPInlineSite::add(InliningResult result) {
  _attempts.push(IPInlineAttempt(result));
  return _attempts.last().make_stream();
}

void InlinePrinter::IPInlineSite::dump(outputStream* tty, int level) const {
  assert(_bci != -999, "trying to dump site without source");

  if (_attempts.is_nonempty()) {
    CompileTask::print_inlining_header(tty, _method, level, _bci);
  }
  for (int i = 0; i < _attempts.length(); i++) {
    CompileTask::print_inlining_inner_message(tty, _attempts.at(i).result(), _attempts.at(i).stream()->base());
  }
  if (_attempts.is_nonempty()) {
    tty->cr();
  }

  _children.visit_in_order([=](auto* node) {
    node->val().dump(tty, level + 1);
    return true;
  });
}
