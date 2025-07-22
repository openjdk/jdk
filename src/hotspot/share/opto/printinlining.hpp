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

#ifndef PRINTINLINING_HPP
#define PRINTINLINING_HPP

#include "memory/allocation.hpp"
#include "nmt/nmtTreap.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/ostream.hpp"

class JVMState;
class ciMethod;
class Compile;
enum class InliningResult;

// If not enabled, all method calls are no-ops.
class InlinePrinter {
private:
  class IPInlineAttempt {
    InliningResult _result;
    stringStream* _stream;

  public:
    IPInlineAttempt() : _stream(nullptr) {}

    IPInlineAttempt(InliningResult result) : _result(result), _stream(nullptr) {}

    InliningResult result() const { return _result; }

    stringStream* make_stream() {
      assert(_stream == nullptr, "stream already exists");
      _stream = new (mtCompiler) stringStream;
      return _stream;
    }

    stringStream* stream() const {
      assert(_stream != nullptr, "stream was not created yet!");
      return _stream;
    }

    void deallocate_stream() {
      delete _stream;
      _stream = nullptr;
    }
  };

  struct Cmp {
    static int cmp(int a, int b) {
      return a - b;
    }
  };

  class IPInlineSite : public CHeapObj<mtCompiler> {
  private:
    ciMethod* _method;
    int _bci;
    GrowableArrayCHeap<IPInlineAttempt, mtCompiler> _attempts;
    TreapCHeap<int, IPInlineSite, Cmp> _children;

  public:
    IPInlineSite(ciMethod* method, int bci) : _method(method), _bci(bci) {}

    IPInlineSite() : _method(nullptr), _bci(-999) {}

    ~IPInlineSite() {
      // Since GrowableArrayCHeap uses copy semantics to resize itself we
      // cannot free the stream inside IPInlineAttempt's destructor unfortunately
      // and have to take care of this here instead.
      for (int i = 0; i < _attempts.length(); i++) {
        _attempts.at(i).deallocate_stream();
      }
    }

    void set_source(ciMethod* method, int bci) {
      _method = method;
      _bci = bci;
    }

    // Finds the node for an inline attempt that occurred inside this inline.
    // If this is a new site, provide the callee otherwise null.
    // Returned reference is valid until any at_bci is called with non-null callee.
    IPInlineSite& at_bci(int bci, ciMethod* callee);
    // The returned pointer stays valid until InlinePrinter is destructed.
    outputStream* add(InliningResult result);

    void dump(outputStream* tty, int level) const;
  };

  bool is_enabled() const;

  Compile* C;

  // In case print inline is disabled, this null stream is returned from ::record()
  nullStream _nullStream;

  // Locates the IPInlineSite node that corresponds to this JVM state.
  // state may be null. In this case, the root node is returned.
  // If this is a new site, provide the callee otherwise null.
  // Returned pointer is valid until InlinePrinter is destructed.
  IPInlineSite* locate(JVMState* state, ciMethod* callee);

  IPInlineSite _root{nullptr, 0};

public:
  InlinePrinter(Compile* compile) : C(compile) {}

  // Saves the result of an inline attempt of method at state.
  // An optional string message with more details that is copied to the stream for this attempt. Pointer is not captured.
  // Returns an output stream which stores the message associated with this attempt. The buffer stays valid until InlinePrinter is destructed.
  // You can print arbitrary information to this stream but do not add line breaks, as this will break formatting.
  outputStream* record(ciMethod* callee, JVMState* state, InliningResult result, const char* msg = nullptr);

  // Prints all collected inlining information to the given output stream.
  void print_on(outputStream* tty) const;
};

#endif // PRINTINLINING_HPP
