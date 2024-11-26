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

#include "precompiled.hpp"
#include "printinlining.hpp"
#include "opto/callnode.hpp"
#include "memory/allocation.hpp"
#include "memory/resourceArea.hpp"

InlinePrinter::InlinePrinter(Arena* arena, Compile* compile) : C(compile), _root(new(arena) IPInlineSite(nullptr, arena, 0)) {}

bool InlinePrinter::is_enabled() const {
  return C->print_intrinsics() || C->print_inlining();
}


InlinePrinter::IPInlineAttempt::IPInlineAttempt(InliningResult result) : result(result) {}

outputStream* InlinePrinter::record(ciMethod* callee, JVMState* state, InliningResult result, const char* msg) {
  if (!is_enabled()) {
    return &_nullStream;
  }
  IPInlineAttempt* attempt = locate(state, callee)->add(result);
  if (msg != nullptr) {
    attempt->msg.print("%s", msg);
  }
  return &attempt->msg; // IPInlineAttempts are heap allocated so this address is safe
}

void InlinePrinter::print_on(outputStream* tty) {
  if (!is_enabled()) {
    return;
  }
  _root->dump(tty, -1);
}

InlinePrinter::IPInlineSite* InlinePrinter::locate(JVMState* state, ciMethod* callee) {
  auto growableArray = new GrowableArrayCHeap<JVMState*, mtCompiler>(2);

  while (state != nullptr) {
    growableArray->push(state);
    state = state->caller();
  }

  IPInlineSite* site = _root;
  for (int i = growableArray->length() - 1; i >= 0; i--) {
    site = site->at_bci(growableArray->at(i)->bci(), i == 0 ? callee : nullptr);
  }

  delete growableArray;

  return site;
}

InlinePrinter::IPInlineSite* InlinePrinter::IPInlineSite::at_bci(int bci, ciMethod* callee) {
  int index = (bci == -1) ? 0 : bci; // -1 is a special case for some intrinsics (e.g. java.lang.ref.reference.get)
  assert(index >= 0, "index cannot be negative");
  if (_children.length() <= index) {
    assert(callee != nullptr, "an inline call is missing in the chain up to the root");
    auto child = new (_arena) IPInlineSite(callee, _arena, bci);
    _children.at_put_grow(index, child, nullptr);
    return child;
  }
  if (IPInlineSite* child = _children.at(index)) {
    if (callee != nullptr && callee != child->_method) {
      IPInlineAttempt* attempt = child->add(InliningResult::SUCCESS);
      attempt->msg.print("callee changed to ");
      CompileTask::print_inline_inner_method_info(&attempt->msg, callee);
    }
    return child;
  }
  auto child = new (_arena) IPInlineSite(callee, _arena, bci);
  _children.at_put(index, child);
  return child;
}

InlinePrinter::IPInlineAttempt* InlinePrinter::IPInlineSite::add(InliningResult result) {
  auto attempt = new (_arena) IPInlineAttempt(result);
  _attempts.push(attempt);
  return attempt;
}

void InlinePrinter::IPInlineSite::dump(outputStream* tty, int level) {
  if (_attempts.is_nonempty()) {
    CompileTask::print_inlining_header(tty, _method, level, _bci);
  }
  for (IPInlineAttempt* attempt : _attempts) {
    CompileTask::print_inlining_inner_message(tty, attempt->result, attempt->msg.base());
  }
  if (_attempts.is_nonempty()) {
    tty->cr();
  }

  for (IPInlineSite* child : _children) {
    if (child != nullptr) {
      child->dump(tty, level + 1);
    }
  }
}
