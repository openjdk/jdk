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

class JVMState;
class ciMethod;
enum class InliningResult;

class InlinePrinter {
public:
  /**
   * @param If enabled is false, all method calls are no-ops.
   */
  InlinePrinter(Arena* arena, bool enabled);

  /**
   * Saves the result of an inline attempt of `method` at `state`.
   * @param method The method that was attempted to inline
   * @param state Where the attempt was made.
   * @param result Whether the inline was successful.
   * @param msg An optional string message with more details that is copied to the stream for this attempt. Pointer is not captured.
   * @returns An output stream which stores the message associated with this attempt. The buffer stays valid until InlinePrinter is deallocated.
   *          You can print arbitrary information to this stream but do not add line breaks, as this will break formatting.
   */
  outputStream* record(ciMethod* method, JVMState* state, InliningResult result, const char* msg = nullptr);

  /**
   * Prints all collected inlining information to the given output stream.
   */
  void dump(outputStream* tty);

  /**
   * Whether inline printing is enabled. If not enabled, all method calls are no-ops.
   */
  bool is_enabled() const { return _enabled; }

private:
  struct IPInlineAttempt : public ArenaObj {
    IPInlineAttempt(InliningResult result);
    const InliningResult result;
    stringStream msg;
  };

  class IPInlineSite : public ArenaObj {
  public:
    /**
     * @param The method being called. May be null iff this is the root of the tree.
     */
    IPInlineSite(ciMethod* method, Arena* arena) : _arena(arena), _method(method),
                                                   _attempts(arena, 2, 0, nullptr),
                                                   _children(arena, 2, 0, nullptr) {}
    /**
     * Finds the node for an inline attempt that occurred inside this inline.
     * @param If the method is allowed to create a missing inline site inside this inline, provide
     *        the method which is being inline. If no new inline site should be created, provide
     *        null.
     * @param arena
     */
    IPInlineSite* at_bci(int bci, ciMethod* create_for);
    InlinePrinter::IPInlineAttempt* add(InliningResult result);

    void dump(outputStream* tty, int level, int bci);

  private:
    Arena* const _arena;
    ciMethod* const _method;
    GrowableArray<IPInlineAttempt*> _attempts;
    GrowableArray<IPInlineSite*> _children;
  };

  bool _enabled;

  /**
   * In case print inline is disabled, this null stream is returned from ::record()
   */
  nullStream _nullStream;

  /**
   * Locates the IPCall node that corresponds to this JVM state.
   * state may be null. In this case, the root node is returned.
   * @param Set is_leaf to true if you call this method to add an new inline attempt.
   *        Must be false for recursive calls.
   * @param create_for
   */
  IPInlineSite* locate_call(JVMState* state, ciMethod* create_for);

  IPInlineSite* const _root;
};

#endif // PRINTINLINING_HPP
