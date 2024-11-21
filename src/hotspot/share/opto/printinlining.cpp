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

#include "printinlining.hpp"
#include "opto/callnode.hpp"
#include "memory/allocation.hpp"
#include "memory/resourceArea.hpp"

InlinePrinter::InlinePrinter(bool enabled) : _enabled(enabled), _root(new IPInlineSite(nullptr)) {
}

InlinePrinter::IPInlineAttempt::IPInlineAttempt(InliningResult result) : result(result) {
}

outputStream* InlinePrinter::record(ciMethod* method, JVMState* state, InliningResult result, const char* msg) {
  if (!_enabled) {
    return &_nullStream;
  }
  auto attempt = locate_call(state, method)->add(result);
  if (msg != nullptr) {
    attempt->msg.print("%s", msg);
  }
  return &attempt->msg; // IPInlineAttempts are heap allocated so this address is safe
}

void InlinePrinter::dump(outputStream* tty) {
  if (!_enabled) {
    return;
  }
  _root->dump(tty, -1, -1);
}

InlinePrinter::IPInlineSite* InlinePrinter::locate_call(JVMState* state, ciMethod* create_for) {
  if (state == nullptr) {
    return _root;
  }

  return locate_call(state->caller(), nullptr)->at_bci(state->bci(), create_for);
}

InlinePrinter::IPInlineSite* InlinePrinter::IPInlineSite::at_bci(int bci, ciMethod* create_for) {
  if (_children.length() <= bci) {
    assert(create_for != nullptr, "an inline call is missing in the chain up to the root");
    auto child = new IPInlineSite(create_for);
    _children.at_put_grow(bci, child, nullptr);
    return child;
  }
  if (auto child = _children.at(bci)) {
    return child;
  }
  auto child = new IPInlineSite(create_for);
  _children.at_put(bci, child);
  return child;
}

InlinePrinter::IPInlineAttempt* InlinePrinter::IPInlineSite::add(InliningResult result) {
  auto attempt = new IPInlineAttempt(result);
  _attempts.push(attempt);
  return attempt;
}

void InlinePrinter::IPInlineSite::dump(outputStream* tty, int level, int bci) {
  if (_attempts.is_nonempty()) {
    CompileTask::print_inlining_header(tty, _method, level, bci);
  }
  for (auto* attempt : _attempts) {
    assert(bci >= 0, "BCI cannot be negative. Is there an inline attempt for the root?");
    CompileTask::print_inlining_inner_message(tty, attempt->result, attempt->msg.base());
  }
  if (_attempts.is_nonempty()) {
    tty->cr();
  }

  for (int bci = 0; bci < _children.length(); bci++) {
    auto child = _children.at(bci);
    if (child != nullptr) {
      child->dump(tty, level + 1, bci);
    }
  }
}
