/*
 * Copyright 1997-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// A BytecodeStream is used for fast iteration over the bytecodes
// of a methodOop.
//
// Usage:
//
// BytecodeStream s(method);
// Bytecodes::Code c;
// while ((c = s.next()) >= 0) {
//   ...
// }
//
// A RawBytecodeStream is a simple version of BytecodeStream.
// It is used ONLY when we know the bytecodes haven't been rewritten
// yet, such as in the rewriter or the verifier. Currently only the
// verifier uses this class.

class RawBytecodeStream: StackObj {
 protected:
  // stream buffer
  methodHandle    _method;                       // read from method directly

  // reading position
  int             _bci;                          // bci if current bytecode
  int             _next_bci;                     // bci of next bytecode
  int             _end_bci;                      // bci after the current iteration interval

  // last bytecode read
  Bytecodes::Code _code;
  bool            _is_wide;

 public:
  // Construction
  RawBytecodeStream(methodHandle method) : _method(method) {
    set_interval(0, _method->code_size());
  }

  // Iteration control
  void set_interval(int beg_bci, int end_bci) {
    // iterate over the interval [beg_bci, end_bci)
    assert(0 <= beg_bci && beg_bci <= method()->code_size(), "illegal beg_bci");
    assert(0 <= end_bci && end_bci <= method()->code_size(), "illegal end_bci");
    // setup of iteration pointers
    _bci      = beg_bci;
    _next_bci = beg_bci;
    _end_bci  = end_bci;
  }
  void set_start   (int beg_bci) {
    set_interval(beg_bci, _method->code_size());
  }

  // Iteration
  // Use raw_next() rather than next() for faster method reference
  Bytecodes::Code raw_next() {
    Bytecodes::Code code;
    // set reading position
    _bci = _next_bci;
    assert(!is_last_bytecode(), "caller should check is_last_bytecode()");

    address bcp = RawBytecodeStream::bcp();
    code        = Bytecodes::code_or_bp_at(bcp);

    // set next bytecode position
    int l = Bytecodes::length_for(code);
    if (l > 0 && (_bci + l) <= _end_bci) {
      assert(code != Bytecodes::_wide && code != Bytecodes::_tableswitch
             && code != Bytecodes::_lookupswitch, "can't be special bytecode");
      _is_wide = false;
      _next_bci += l;
      _code = code;
      return code;
    } else if (code == Bytecodes::_wide && _bci + 1 >= _end_bci) {
      return Bytecodes::_illegal;
    } else {
      return raw_next_special(code);
    }
  }
  Bytecodes::Code raw_next_special(Bytecodes::Code code);

  // Stream attributes
  methodHandle    method() const                 { return _method; }

  int             bci() const                    { return _bci; }
  int             next_bci() const               { return _next_bci; }
  int             end_bci() const                { return _end_bci; }

  Bytecodes::Code code() const                   { return _code; }
  bool            is_wide() const                { return _is_wide; }
  int             instruction_size() const       { return (_next_bci - _bci); }
  bool            is_last_bytecode() const       { return _next_bci >= _end_bci; }

  address         bcp() const                    { return method()->code_base() + _bci; }
  address         next_bcp()                     { return method()->code_base() + _next_bci; }

  // State changes
  void            set_next_bci(int bci)          { assert(0 <= bci && bci <= method()->code_size(), "illegal bci"); _next_bci = bci; }

  // Bytecode-specific attributes
  int             dest() const                   { return bci() + (short)Bytes::get_Java_u2(bcp() + 1); }
  int             dest_w() const                 { return bci() + (int  )Bytes::get_Java_u4(bcp() + 1); }

  // Unsigned indices, widening
  int             get_index() const              { assert_index_size(is_wide() ? 2 : 1);
                                                   return (is_wide()) ? Bytes::get_Java_u2(bcp() + 2) : bcp()[1]; }
  int             get_index_big() const          { assert_index_size(2);
                                                   return (int)Bytes::get_Java_u2(bcp() + 1);  }
  int             get_index_int() const          { return has_giant_index() ? get_index_giant() : get_index_big(); }
  int             get_index_giant() const        { assert_index_size(4); return Bytes::get_native_u4(bcp() + 1); }
  int             has_giant_index() const        { return (code() == Bytecodes::_invokedynamic); }

 private:
  void assert_index_size(int required_size) const {
#ifdef ASSERT
    int isize = instruction_size() - (int)_is_wide - 1;
    if (isize == 2 && code() == Bytecodes::_iinc)
      isize = 1;
    else if (isize <= 2)
      ;                         // no change
    else if (has_giant_index())
      isize = 4;
    else
      isize = 2;
    assert(isize = required_size, "wrong index size");
#endif
  }
};

// In BytecodeStream, non-java bytecodes will be translated into the
// corresponding java bytecodes.

class BytecodeStream: public RawBytecodeStream {
 public:
  // Construction
  BytecodeStream(methodHandle method) : RawBytecodeStream(method) { }

  // Iteration
  Bytecodes::Code next() {
    Bytecodes::Code code;
    // set reading position
    _bci = _next_bci;
    if (is_last_bytecode()) {
      // indicate end of bytecode stream
      code = Bytecodes::_illegal;
    } else {
      // get bytecode
      address bcp = BytecodeStream::bcp();
      code        = Bytecodes::java_code_at(bcp);
      // set next bytecode position
      //
      // note that we cannot advance before having the
      // tty bytecode otherwise the stepping is wrong!
      // (carefull: length_for(...) must be used first!)
      int l = Bytecodes::length_for(code);
      if (l == 0) l = Bytecodes::length_at(bcp);
      _next_bci  += l;
      assert(_bci < _next_bci, "length must be > 0");
      // set attributes
      _is_wide      = false;
      // check for special (uncommon) cases
      if (code == Bytecodes::_wide) {
        code = (Bytecodes::Code)bcp[1];
        _is_wide = true;
      }
      assert(Bytecodes::is_java_code(code), "sanity check");
    }
    _code = code;
    return _code;
  }

  bool            is_active_breakpoint() const   { return Bytecodes::is_active_breakpoint_at(bcp()); }
};
