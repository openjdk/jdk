/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_SIGNATURE_CC_HPP
#define SHARE_RUNTIME_SIGNATURE_CC_HPP

#include "runtime/signature.hpp"

// Stream that iterates over a scalarized signature
class ScalarizedInlineArgsStream : public StackObj {
  const GrowableArray<SigEntry>* _sig;
  int _sig_idx;
  const VMRegPair* _regs;
  int _regs_count;
  int _regs_idx;
  int _depth;
  const int _step;
  DEBUG_ONLY(bool _finished);

public:
  ScalarizedInlineArgsStream(const GrowableArray<SigEntry>* sig, int sig_idx, VMRegPair* regs, int regs_count, int regs_idx, bool reverse = false)
    : _sig(sig), _sig_idx(sig_idx), _regs(regs), _regs_count(regs_count), _regs_idx(regs_idx), _step(reverse ? -1 : 1) {
    reset(sig_idx, regs_idx);
  }

  bool next(VMReg& reg, BasicType& bt) {
    assert(!_finished, "sanity");
    do {
      _sig_idx += _step;
      bt = _sig->at(_sig_idx)._bt;
      if (bt == T_METADATA) {
        _depth += _step;
      } else if (bt == T_VOID &&
                 _sig->at(_sig_idx-1)._bt != T_LONG &&
                 _sig->at(_sig_idx-1)._bt != T_DOUBLE) {
        _depth -= _step;
      } else {
        assert(_regs_idx >= 0 && _regs_idx < _regs_count, "out of bounds");
        const VMRegPair pair = _regs[_regs_idx];
        _regs_idx += _step;
        reg = pair.first();
        if (!reg->is_valid()) {
          assert(!pair.second()->is_valid(), "must be invalid");
        } else {
          return true;
        }
      }
    } while (_depth != 0);

    DEBUG_ONLY(_finished = true);
    return false;
  }

  void reset(int sig_idx, int regs_idx) {
    _sig_idx = sig_idx;
    _regs_idx = regs_idx;
    assert(_sig->at(_sig_idx)._bt == (_step > 0) ? T_METADATA : T_VOID, "should be at inline type delimiter");
    _depth = 1;
    DEBUG_ONLY(_finished = false);
  }

  int sig_index()  { return _sig_idx;  }
  int regs_index() { return _regs_idx; }
};

#endif // SHARE_RUNTIME_SIGNATURE_CC_HPP
