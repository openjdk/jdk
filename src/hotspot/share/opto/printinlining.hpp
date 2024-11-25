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

#ifndef PRINTINLINING_HPP
#define PRINTINLINING_HPP

#include "memory/allocation.hpp"
#include "utilities/ostream.hpp"
#include "utilities/growableArray.hpp"

class JVMState;
class ciMethod;
class Compile;
enum class InliningResult;

// If not enabled, all method calls are no-ops.
class InlinePrinter {
private:
  struct IPInlineAttempt : public ArenaObj {
    IPInlineAttempt(InliningResult result);
    const InliningResult result;
    stringStream msg;
    int bci;
  };

  class IPInlineSite : public ArenaObj {
  private:
    Arena* const _arena;
    ciMethod* const _method;
    int const _bci;
    GrowableArray<IPInlineAttempt*> _attempts;
    GrowableArray<IPInlineSite*> _children;

  public:
    /**
     * Method may be null iff this is the root of the tree.
     */
    IPInlineSite(ciMethod* method, Arena* arena, int bci) : _arena(arena), _method(method), _bci(bci),
                                                            _attempts(arena, 2, 0, nullptr),
                                                            _children(arena, 2, 0, nullptr) {}
    /**
     * Finds the node for an inline attempt that occurred inside this inline.
     * If this is a new site, provide the callee otherwise null.
     */
    IPInlineSite* at_bci(int bci, ciMethod* callee);
    InlinePrinter::IPInlineAttempt* add(InliningResult result);

    void dump(outputStream* tty, int level);
  };

  bool is_enabled() const;

  Compile *C;

  /**
   * In case print inline is disabled, this null stream is returned from ::record()
   */
  nullStream _nullStream;

  /**
   * Locates the IPInlineSite node that corresponds to this JVM state.
   * state may be null. In this case, the root node is returned.
   * If this is a new site, provide the callee otherwise null.
   */
  IPInlineSite* locate(JVMState* state, ciMethod* callee);

  IPInlineSite* const _root;

public:
  InlinePrinter(Arena* arena, Compile* compile);

  /**
   * Saves the result of an inline attempt of method at state.
   * An optional string message with more details that is copied to the stream for this attempt. Pointer is not captured.
   * Returns an output stream which stores the message associated with this attempt. The buffer stays valid until InlinePrinter is deallocated.
   * You can print arbitrary information to this stream but do not add line breaks, as this will break formatting.
   */
  outputStream* record(ciMethod* callee, JVMState* state, InliningResult result, const char* msg = nullptr);

  /**
   * Prints all collected inlining information to the given output stream.
   */
  void print_on(outputStream* tty);
};

#endif // PRINTINLINING_HPP
