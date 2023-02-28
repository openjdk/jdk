/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/bytecodes.hpp"
#include "memory/resourceArea.hpp"
#include "oops/method.hpp"
#include "utilities/align.hpp"
#include "utilities/bytes.hpp"

bool            Bytecodes::_is_initialized = false;
const char*     Bytecodes::_name          [Bytecodes::number_of_codes];
BasicType       Bytecodes::_result_type   [Bytecodes::number_of_codes];
s_char          Bytecodes::_depth         [Bytecodes::number_of_codes];
u_char          Bytecodes::_lengths       [Bytecodes::number_of_codes];
Bytecodes::Code Bytecodes::_java_code     [Bytecodes::number_of_codes];
unsigned short  Bytecodes::_flags         [(1<<BitsPerByte)*2];

#ifdef ASSERT
bool Bytecodes::check_method(const Method* method, address bcp) {
  return method->contains(bcp);
}
#endif

bool Bytecodes::check_must_rewrite(Bytecodes::Code code) {
  assert(can_rewrite(code), "post-check only");

  // Some codes are conditionally rewriting.  Look closely at them.
  switch (code) {
  case Bytecodes::_aload_0:
    // Even if RewriteFrequentPairs is turned on,
    // the _aload_0 code might delay its rewrite until
    // a following _getfield rewrites itself.
    return false;

  case Bytecodes::_lookupswitch:
    return false;  // the rewrite is not done by the interpreter

  case Bytecodes::_new:
    // (Could actually look at the class here, but the profit would be small.)
    return false;  // the rewrite is not always done

  default:
    // No other special cases.
    return true;
  }
}

Bytecodes::Code Bytecodes::code_at(Method* method, int bci) {
  return code_at(method, method->bcp_from(bci));
}

Bytecodes::Code Bytecodes::non_breakpoint_code_at(const Method* method, address bcp) {
  assert(method != nullptr, "must have the method for breakpoint conversion");
  assert(method->contains(bcp), "must be valid bcp in method");
  return method->orig_bytecode_at(method->bci_from(bcp));
}

int Bytecodes::special_length_at(Bytecodes::Code code, address bcp, address end) {
  switch (code) {
  case _wide:
    if (end != nullptr && bcp + 1 >= end) {
      return -1; // don't read past end of code buffer
    }
    return wide_length_for(cast(*(bcp + 1)));
  case _tableswitch:
    { address aligned_bcp = align_up(bcp + 1, jintSize);
      if (end != nullptr && aligned_bcp + 3*jintSize >= end) {
        return -1; // don't read past end of code buffer
      }
      jlong lo = (jint)Bytes::get_Java_u4(aligned_bcp + 1*jintSize);
      jlong hi = (jint)Bytes::get_Java_u4(aligned_bcp + 2*jintSize);
      jlong len = (aligned_bcp - bcp) + (3 + hi - lo + 1)*jintSize;
      // only return len if it can be represented as a positive int;
      // return -1 otherwise
      return (len > 0 && len == (int)len) ? len : -1;
    }

  case _lookupswitch:      // fall through
  case _fast_binaryswitch: // fall through
  case _fast_linearswitch:
    { address aligned_bcp = align_up(bcp + 1, jintSize);
      if (end != nullptr && aligned_bcp + 2*jintSize >= end) {
        return -1; // don't read past end of code buffer
      }
      jlong npairs = (jint)Bytes::get_Java_u4(aligned_bcp + jintSize);
      jlong len = (aligned_bcp - bcp) + (2 + 2*npairs)*jintSize;
      // only return len if it can be represented as a positive int;
      // return -1 otherwise
      return (len > 0 && len == (int)len) ? len : -1;
    }
  default:
    // Note: Length functions must return <=0 for invalid bytecodes.
    return 0;
  }
}

// At a breakpoint instruction, this returns the breakpoint's length,
// otherwise, it's the same as special_length_at().  This is used by
// the RawByteCodeStream, which wants to see the actual bytecode
// values (including breakpoint).  RawByteCodeStream is used by the
// verifier when reading in bytecode to verify.  Other mechanisms that
// run at runtime (such as generateOopMaps) need to iterate over the code
// and don't expect to see breakpoints: they want to see the instruction
// which was replaced so that they can get the correct length and find
// the next bytecode.
//
// 'end' indicates the end of the code buffer, which we should not try to read
// past.
int Bytecodes::raw_special_length_at(address bcp, address end) {
  Code code = code_or_bp_at(bcp);
  if (code == _breakpoint) {
    return 1;
  } else {
    return special_length_at(code, bcp, end);
  }
}



void Bytecodes::def(Code code, const char* name, const char* format, const char* wide_format, BasicType result_type, int depth, bool can_trap) {
  def(code, name, format, wide_format, result_type, depth, can_trap, code);
}


void Bytecodes::def(Code code, const char* name, const char* format, const char* wide_format, BasicType result_type, int depth, bool can_trap, Code java_code) {
  assert(wide_format == nullptr || format != nullptr, "short form must exist if there's a wide form");
  int len  = (format      != nullptr ? (int) strlen(format)      : 0);
  int wlen = (wide_format != nullptr ? (int) strlen(wide_format) : 0);
  _name          [code] = name;
  _result_type   [code] = result_type;
  _depth         [code] = depth;
  _lengths       [code] = (wlen << 4) | (len & 0xF);
  _java_code     [code] = java_code;
  int bc_flags = 0;
  if (can_trap)           bc_flags |= _bc_can_trap;
  if (java_code != code)  bc_flags |= _bc_can_rewrite;
  _flags[(u1)code+0*(1<<BitsPerByte)] = compute_flags(format,      bc_flags);
  _flags[(u1)code+1*(1<<BitsPerByte)] = compute_flags(wide_format, bc_flags);
  assert(is_defined(code)      == (format != nullptr),      "");
  assert(wide_is_defined(code) == (wide_format != nullptr), "");
  assert(length_for(code)      == len, "");
  assert(wide_length_for(code) == wlen, "");
}


// Format strings interpretation:
//
// b: bytecode
// c: signed constant, Java byte-ordering
// i: unsigned local index, Java byte-ordering (I = native byte ordering)
// j: unsigned CP cache index, Java byte-ordering (J = native byte ordering)
// k: unsigned CP index, Java byte-ordering
// o: branch offset, Java byte-ordering
// _: unused/ignored
// w: wide bytecode
//
// Note: The format strings are used for 2 purposes:
//       1. to specify the length of the bytecode
//          (= number of characters in format string)
//       2. to derive bytecode format flags (_fmt_has_k, etc.)
//
// Note: For bytecodes with variable length, the format string is the empty string.

int Bytecodes::compute_flags(const char* format, int more_flags) {
  if (format == nullptr)  return 0;  // not even more_flags
  int flags = more_flags;
  const char* fp = format;
  switch (*fp) {
  case '\0':
    flags |= _fmt_not_simple; // but variable
    break;
  case 'b':
    flags |= _fmt_not_variable;  // but simple
    ++fp;  // skip 'b'
    break;
  case 'w':
    flags |= _fmt_not_variable | _fmt_not_simple;
    ++fp;  // skip 'w'
    guarantee(*fp == 'b', "wide format must start with 'wb'");
    ++fp;  // skip 'b'
    break;
  }

  int has_nbo = 0, has_jbo = 0, has_size = 0;
  for (;;) {
    int this_flag = 0;
    char fc = *fp++;
    switch (fc) {
    case '\0':  // end of string
      assert(flags == (jchar)flags, "change _format_flags");
      return flags;

    case '_': continue;         // ignore these

    case 'j': this_flag = _fmt_has_j; has_jbo = 1; break;
    case 'k': this_flag = _fmt_has_k; has_jbo = 1; break;
    case 'i': this_flag = _fmt_has_i; has_jbo = 1; break;
    case 'c': this_flag = _fmt_has_c; has_jbo = 1; break;
    case 'o': this_flag = _fmt_has_o; has_jbo = 1; break;

    // uppercase versions mark native byte order (from Rewriter)
    // actually, only the 'J' case happens currently
    case 'J': this_flag = _fmt_has_j; has_nbo = 1; break;
    case 'K': this_flag = _fmt_has_k; has_nbo = 1; break;
    case 'I': this_flag = _fmt_has_i; has_nbo = 1; break;
    case 'C': this_flag = _fmt_has_c; has_nbo = 1; break;
    case 'O': this_flag = _fmt_has_o; has_nbo = 1; break;
    default:  guarantee(false, "bad char in format");
    }

    flags |= this_flag;

    guarantee(!(has_jbo && has_nbo), "mixed byte orders in format");
    if (has_nbo)
      flags |= _fmt_has_nbo;

    int this_size = 1;
    if (*fp == fc) {
      // advance beyond run of the same characters
      this_size = 2;
      while (*++fp == fc)  this_size++;
      switch (this_size) {
      case 2: flags |= _fmt_has_u2; break;
      case 4: flags |= _fmt_has_u4; break;
      default: guarantee(false, "bad rep count in format");
      }
    }
    guarantee(has_size == 0 ||                     // no field yet
              this_size == has_size ||             // same size
              this_size < has_size && *fp == '\0', // last field can be short
              "mixed field sizes in format");
    has_size = this_size;
  }
}

void Bytecodes::initialize() {
  if (_is_initialized) return;
  assert(number_of_codes <= 256, "too many bytecodes");

  // initialize bytecode tables - didn't use static array initializers
  // (such as {}) so we can do additional consistency checks and init-
  // code is independent of actual bytecode numbering.
  //
  // Note 1: nullptr for the format string means the bytecode doesn't exist
  //         in that form.
  //
  // Note 2: The result type is T_ILLEGAL for bytecodes where the top of stack
  //         type after execution is not only determined by the bytecode itself.

  //  Java bytecodes
  //  bytecode               bytecode name           format   wide f.   result tp  stk traps
  def(_nop                 , "nop"                 , "b"    , nullptr    , T_VOID   ,  0, false);
  def(_aconst_null         , "aconst_null"         , "b"    , nullptr    , T_OBJECT ,  1, false);
  def(_iconst_m1           , "iconst_m1"           , "b"    , nullptr    , T_INT    ,  1, false);
  def(_iconst_0            , "iconst_0"            , "b"    , nullptr    , T_INT    ,  1, false);
  def(_iconst_1            , "iconst_1"            , "b"    , nullptr    , T_INT    ,  1, false);
  def(_iconst_2            , "iconst_2"            , "b"    , nullptr    , T_INT    ,  1, false);
  def(_iconst_3            , "iconst_3"            , "b"    , nullptr    , T_INT    ,  1, false);
  def(_iconst_4            , "iconst_4"            , "b"    , nullptr    , T_INT    ,  1, false);
  def(_iconst_5            , "iconst_5"            , "b"    , nullptr    , T_INT    ,  1, false);
  def(_lconst_0            , "lconst_0"            , "b"    , nullptr    , T_LONG   ,  2, false);
  def(_lconst_1            , "lconst_1"            , "b"    , nullptr    , T_LONG   ,  2, false);
  def(_fconst_0            , "fconst_0"            , "b"    , nullptr    , T_FLOAT  ,  1, false);
  def(_fconst_1            , "fconst_1"            , "b"    , nullptr    , T_FLOAT  ,  1, false);
  def(_fconst_2            , "fconst_2"            , "b"    , nullptr    , T_FLOAT  ,  1, false);
  def(_dconst_0            , "dconst_0"            , "b"    , nullptr    , T_DOUBLE ,  2, false);
  def(_dconst_1            , "dconst_1"            , "b"    , nullptr    , T_DOUBLE ,  2, false);
  def(_bipush              , "bipush"              , "bc"   , nullptr    , T_INT    ,  1, false);
  def(_sipush              , "sipush"              , "bcc"  , nullptr    , T_INT    ,  1, false);
  def(_ldc                 , "ldc"                 , "bk"   , nullptr    , T_ILLEGAL,  1, true );
  def(_ldc_w               , "ldc_w"               , "bkk"  , nullptr    , T_ILLEGAL,  1, true );
  def(_ldc2_w              , "ldc2_w"              , "bkk"  , nullptr    , T_ILLEGAL,  2, true );
  def(_iload               , "iload"               , "bi"   , "wbii"     , T_INT    ,  1, false);
  def(_lload               , "lload"               , "bi"   , "wbii"     , T_LONG   ,  2, false);
  def(_fload               , "fload"               , "bi"   , "wbii"     , T_FLOAT  ,  1, false);
  def(_dload               , "dload"               , "bi"   , "wbii"     , T_DOUBLE ,  2, false);
  def(_aload               , "aload"               , "bi"   , "wbii"     , T_OBJECT ,  1, false);
  def(_iload_0             , "iload_0"             , "b"    , nullptr    , T_INT    ,  1, false);
  def(_iload_1             , "iload_1"             , "b"    , nullptr    , T_INT    ,  1, false);
  def(_iload_2             , "iload_2"             , "b"    , nullptr    , T_INT    ,  1, false);
  def(_iload_3             , "iload_3"             , "b"    , nullptr    , T_INT    ,  1, false);
  def(_lload_0             , "lload_0"             , "b"    , nullptr    , T_LONG   ,  2, false);
  def(_lload_1             , "lload_1"             , "b"    , nullptr    , T_LONG   ,  2, false);
  def(_lload_2             , "lload_2"             , "b"    , nullptr    , T_LONG   ,  2, false);
  def(_lload_3             , "lload_3"             , "b"    , nullptr    , T_LONG   ,  2, false);
  def(_fload_0             , "fload_0"             , "b"    , nullptr    , T_FLOAT  ,  1, false);
  def(_fload_1             , "fload_1"             , "b"    , nullptr    , T_FLOAT  ,  1, false);
  def(_fload_2             , "fload_2"             , "b"    , nullptr    , T_FLOAT  ,  1, false);
  def(_fload_3             , "fload_3"             , "b"    , nullptr    , T_FLOAT  ,  1, false);
  def(_dload_0             , "dload_0"             , "b"    , nullptr    , T_DOUBLE ,  2, false);
  def(_dload_1             , "dload_1"             , "b"    , nullptr    , T_DOUBLE ,  2, false);
  def(_dload_2             , "dload_2"             , "b"    , nullptr    , T_DOUBLE ,  2, false);
  def(_dload_3             , "dload_3"             , "b"    , nullptr    , T_DOUBLE ,  2, false);
  def(_aload_0             , "aload_0"             , "b"    , nullptr    , T_OBJECT ,  1, true ); // rewriting in interpreter
  def(_aload_1             , "aload_1"             , "b"    , nullptr    , T_OBJECT ,  1, false);
  def(_aload_2             , "aload_2"             , "b"    , nullptr    , T_OBJECT ,  1, false);
  def(_aload_3             , "aload_3"             , "b"    , nullptr    , T_OBJECT ,  1, false);
  def(_iaload              , "iaload"              , "b"    , nullptr    , T_INT    , -1, true );
  def(_laload              , "laload"              , "b"    , nullptr    , T_LONG   ,  0, true );
  def(_faload              , "faload"              , "b"    , nullptr    , T_FLOAT  , -1, true );
  def(_daload              , "daload"              , "b"    , nullptr    , T_DOUBLE ,  0, true );
  def(_aaload              , "aaload"              , "b"    , nullptr    , T_OBJECT , -1, true );
  def(_baload              , "baload"              , "b"    , nullptr    , T_INT    , -1, true );
  def(_caload              , "caload"              , "b"    , nullptr    , T_INT    , -1, true );
  def(_saload              , "saload"              , "b"    , nullptr    , T_INT    , -1, true );
  def(_istore              , "istore"              , "bi"   , "wbii"     , T_VOID   , -1, false);
  def(_lstore              , "lstore"              , "bi"   , "wbii"     , T_VOID   , -2, false);
  def(_fstore              , "fstore"              , "bi"   , "wbii"     , T_VOID   , -1, false);
  def(_dstore              , "dstore"              , "bi"   , "wbii"     , T_VOID   , -2, false);
  def(_astore              , "astore"              , "bi"   , "wbii"     , T_VOID   , -1, false);
  def(_istore_0            , "istore_0"            , "b"    , nullptr    , T_VOID   , -1, false);
  def(_istore_1            , "istore_1"            , "b"    , nullptr    , T_VOID   , -1, false);
  def(_istore_2            , "istore_2"            , "b"    , nullptr    , T_VOID   , -1, false);
  def(_istore_3            , "istore_3"            , "b"    , nullptr    , T_VOID   , -1, false);
  def(_lstore_0            , "lstore_0"            , "b"    , nullptr    , T_VOID   , -2, false);
  def(_lstore_1            , "lstore_1"            , "b"    , nullptr    , T_VOID   , -2, false);
  def(_lstore_2            , "lstore_2"            , "b"    , nullptr    , T_VOID   , -2, false);
  def(_lstore_3            , "lstore_3"            , "b"    , nullptr    , T_VOID   , -2, false);
  def(_fstore_0            , "fstore_0"            , "b"    , nullptr    , T_VOID   , -1, false);
  def(_fstore_1            , "fstore_1"            , "b"    , nullptr    , T_VOID   , -1, false);
  def(_fstore_2            , "fstore_2"            , "b"    , nullptr    , T_VOID   , -1, false);
  def(_fstore_3            , "fstore_3"            , "b"    , nullptr    , T_VOID   , -1, false);
  def(_dstore_0            , "dstore_0"            , "b"    , nullptr    , T_VOID   , -2, false);
  def(_dstore_1            , "dstore_1"            , "b"    , nullptr    , T_VOID   , -2, false);
  def(_dstore_2            , "dstore_2"            , "b"    , nullptr    , T_VOID   , -2, false);
  def(_dstore_3            , "dstore_3"            , "b"    , nullptr    , T_VOID   , -2, false);
  def(_astore_0            , "astore_0"            , "b"    , nullptr    , T_VOID   , -1, false);
  def(_astore_1            , "astore_1"            , "b"    , nullptr    , T_VOID   , -1, false);
  def(_astore_2            , "astore_2"            , "b"    , nullptr    , T_VOID   , -1, false);
  def(_astore_3            , "astore_3"            , "b"    , nullptr    , T_VOID   , -1, false);
  def(_iastore             , "iastore"             , "b"    , nullptr    , T_VOID   , -3, true );
  def(_lastore             , "lastore"             , "b"    , nullptr    , T_VOID   , -4, true );
  def(_fastore             , "fastore"             , "b"    , nullptr    , T_VOID   , -3, true );
  def(_dastore             , "dastore"             , "b"    , nullptr    , T_VOID   , -4, true );
  def(_aastore             , "aastore"             , "b"    , nullptr    , T_VOID   , -3, true );
  def(_bastore             , "bastore"             , "b"    , nullptr    , T_VOID   , -3, true );
  def(_castore             , "castore"             , "b"    , nullptr    , T_VOID   , -3, true );
  def(_sastore             , "sastore"             , "b"    , nullptr    , T_VOID   , -3, true );
  def(_pop                 , "pop"                 , "b"    , nullptr    , T_VOID   , -1, false);
  def(_pop2                , "pop2"                , "b"    , nullptr    , T_VOID   , -2, false);
  def(_dup                 , "dup"                 , "b"    , nullptr    , T_VOID   ,  1, false);
  def(_dup_x1              , "dup_x1"              , "b"    , nullptr    , T_VOID   ,  1, false);
  def(_dup_x2              , "dup_x2"              , "b"    , nullptr    , T_VOID   ,  1, false);
  def(_dup2                , "dup2"                , "b"    , nullptr    , T_VOID   ,  2, false);
  def(_dup2_x1             , "dup2_x1"             , "b"    , nullptr    , T_VOID   ,  2, false);
  def(_dup2_x2             , "dup2_x2"             , "b"    , nullptr    , T_VOID   ,  2, false);
  def(_swap                , "swap"                , "b"    , nullptr    , T_VOID   ,  0, false);
  def(_iadd                , "iadd"                , "b"    , nullptr    , T_INT    , -1, false);
  def(_ladd                , "ladd"                , "b"    , nullptr    , T_LONG   , -2, false);
  def(_fadd                , "fadd"                , "b"    , nullptr    , T_FLOAT  , -1, false);
  def(_dadd                , "dadd"                , "b"    , nullptr    , T_DOUBLE , -2, false);
  def(_isub                , "isub"                , "b"    , nullptr    , T_INT    , -1, false);
  def(_lsub                , "lsub"                , "b"    , nullptr    , T_LONG   , -2, false);
  def(_fsub                , "fsub"                , "b"    , nullptr    , T_FLOAT  , -1, false);
  def(_dsub                , "dsub"                , "b"    , nullptr    , T_DOUBLE , -2, false);
  def(_imul                , "imul"                , "b"    , nullptr    , T_INT    , -1, false);
  def(_lmul                , "lmul"                , "b"    , nullptr    , T_LONG   , -2, false);
  def(_fmul                , "fmul"                , "b"    , nullptr    , T_FLOAT  , -1, false);
  def(_dmul                , "dmul"                , "b"    , nullptr    , T_DOUBLE , -2, false);
  def(_idiv                , "idiv"                , "b"    , nullptr    , T_INT    , -1, true );
  def(_ldiv                , "ldiv"                , "b"    , nullptr    , T_LONG   , -2, true );
  def(_fdiv                , "fdiv"                , "b"    , nullptr    , T_FLOAT  , -1, false);
  def(_ddiv                , "ddiv"                , "b"    , nullptr    , T_DOUBLE , -2, false);
  def(_irem                , "irem"                , "b"    , nullptr    , T_INT    , -1, true );
  def(_lrem                , "lrem"                , "b"    , nullptr    , T_LONG   , -2, true );
  def(_frem                , "frem"                , "b"    , nullptr    , T_FLOAT  , -1, false);
  def(_drem                , "drem"                , "b"    , nullptr    , T_DOUBLE , -2, false);
  def(_ineg                , "ineg"                , "b"    , nullptr    , T_INT    ,  0, false);
  def(_lneg                , "lneg"                , "b"    , nullptr    , T_LONG   ,  0, false);
  def(_fneg                , "fneg"                , "b"    , nullptr    , T_FLOAT  ,  0, false);
  def(_dneg                , "dneg"                , "b"    , nullptr    , T_DOUBLE ,  0, false);
  def(_ishl                , "ishl"                , "b"    , nullptr    , T_INT    , -1, false);
  def(_lshl                , "lshl"                , "b"    , nullptr    , T_LONG   , -1, false);
  def(_ishr                , "ishr"                , "b"    , nullptr    , T_INT    , -1, false);
  def(_lshr                , "lshr"                , "b"    , nullptr    , T_LONG   , -1, false);
  def(_iushr               , "iushr"               , "b"    , nullptr    , T_INT    , -1, false);
  def(_lushr               , "lushr"               , "b"    , nullptr    , T_LONG   , -1, false);
  def(_iand                , "iand"                , "b"    , nullptr    , T_INT    , -1, false);
  def(_land                , "land"                , "b"    , nullptr    , T_LONG   , -2, false);
  def(_ior                 , "ior"                 , "b"    , nullptr    , T_INT    , -1, false);
  def(_lor                 , "lor"                 , "b"    , nullptr    , T_LONG   , -2, false);
  def(_ixor                , "ixor"                , "b"    , nullptr    , T_INT    , -1, false);
  def(_lxor                , "lxor"                , "b"    , nullptr    , T_LONG   , -2, false);
  def(_iinc                , "iinc"                , "bic"  , "wbiicc",    T_VOID   ,  0, false);
  def(_i2l                 , "i2l"                 , "b"    , nullptr    , T_LONG   ,  1, false);
  def(_i2f                 , "i2f"                 , "b"    , nullptr    , T_FLOAT  ,  0, false);
  def(_i2d                 , "i2d"                 , "b"    , nullptr    , T_DOUBLE ,  1, false);
  def(_l2i                 , "l2i"                 , "b"    , nullptr    , T_INT    , -1, false);
  def(_l2f                 , "l2f"                 , "b"    , nullptr    , T_FLOAT  , -1, false);
  def(_l2d                 , "l2d"                 , "b"    , nullptr    , T_DOUBLE ,  0, false);
  def(_f2i                 , "f2i"                 , "b"    , nullptr    , T_INT    ,  0, false);
  def(_f2l                 , "f2l"                 , "b"    , nullptr    , T_LONG   ,  1, false);
  def(_f2d                 , "f2d"                 , "b"    , nullptr    , T_DOUBLE ,  1, false);
  def(_d2i                 , "d2i"                 , "b"    , nullptr    , T_INT    , -1, false);
  def(_d2l                 , "d2l"                 , "b"    , nullptr    , T_LONG   ,  0, false);
  def(_d2f                 , "d2f"                 , "b"    , nullptr    , T_FLOAT  , -1, false);
  def(_i2b                 , "i2b"                 , "b"    , nullptr    , T_BYTE   ,  0, false);
  def(_i2c                 , "i2c"                 , "b"    , nullptr    , T_CHAR   ,  0, false);
  def(_i2s                 , "i2s"                 , "b"    , nullptr    , T_SHORT  ,  0, false);
  def(_lcmp                , "lcmp"                , "b"    , nullptr    , T_VOID   , -3, false);
  def(_fcmpl               , "fcmpl"               , "b"    , nullptr    , T_VOID   , -1, false);
  def(_fcmpg               , "fcmpg"               , "b"    , nullptr    , T_VOID   , -1, false);
  def(_dcmpl               , "dcmpl"               , "b"    , nullptr    , T_VOID   , -3, false);
  def(_dcmpg               , "dcmpg"               , "b"    , nullptr    , T_VOID   , -3, false);
  def(_ifeq                , "ifeq"                , "boo"  , nullptr    , T_VOID   , -1, false);
  def(_ifne                , "ifne"                , "boo"  , nullptr    , T_VOID   , -1, false);
  def(_iflt                , "iflt"                , "boo"  , nullptr    , T_VOID   , -1, false);
  def(_ifge                , "ifge"                , "boo"  , nullptr    , T_VOID   , -1, false);
  def(_ifgt                , "ifgt"                , "boo"  , nullptr    , T_VOID   , -1, false);
  def(_ifle                , "ifle"                , "boo"  , nullptr    , T_VOID   , -1, false);
  def(_if_icmpeq           , "if_icmpeq"           , "boo"  , nullptr    , T_VOID   , -2, false);
  def(_if_icmpne           , "if_icmpne"           , "boo"  , nullptr    , T_VOID   , -2, false);
  def(_if_icmplt           , "if_icmplt"           , "boo"  , nullptr    , T_VOID   , -2, false);
  def(_if_icmpge           , "if_icmpge"           , "boo"  , nullptr    , T_VOID   , -2, false);
  def(_if_icmpgt           , "if_icmpgt"           , "boo"  , nullptr    , T_VOID   , -2, false);
  def(_if_icmple           , "if_icmple"           , "boo"  , nullptr    , T_VOID   , -2, false);
  def(_if_acmpeq           , "if_acmpeq"           , "boo"  , nullptr    , T_VOID   , -2, false);
  def(_if_acmpne           , "if_acmpne"           , "boo"  , nullptr    , T_VOID   , -2, false);
  def(_goto                , "goto"                , "boo"  , nullptr    , T_VOID   ,  0, false);
  def(_jsr                 , "jsr"                 , "boo"  , nullptr    , T_INT    ,  0, false);
  def(_ret                 , "ret"                 , "bi"   , "wbii"     , T_VOID   ,  0, false);
  def(_tableswitch         , "tableswitch"         , ""     , nullptr    , T_VOID   , -1, false); // may have backward branches
  def(_lookupswitch        , "lookupswitch"        , ""     , nullptr    , T_VOID   , -1, false); // rewriting in interpreter
  def(_ireturn             , "ireturn"             , "b"    , nullptr    , T_INT    , -1, true);
  def(_lreturn             , "lreturn"             , "b"    , nullptr    , T_LONG   , -2, true);
  def(_freturn             , "freturn"             , "b"    , nullptr    , T_FLOAT  , -1, true);
  def(_dreturn             , "dreturn"             , "b"    , nullptr    , T_DOUBLE , -2, true);
  def(_areturn             , "areturn"             , "b"    , nullptr    , T_OBJECT , -1, true);
  def(_return              , "return"              , "b"    , nullptr    , T_VOID   ,  0, true);
  def(_getstatic           , "getstatic"           , "bJJ"  , nullptr    , T_ILLEGAL,  1, true );
  def(_putstatic           , "putstatic"           , "bJJ"  , nullptr    , T_ILLEGAL, -1, true );
  def(_getfield            , "getfield"            , "bJJ"  , nullptr    , T_ILLEGAL,  0, true );
  def(_putfield            , "putfield"            , "bJJ"  , nullptr    , T_ILLEGAL, -2, true );
  def(_invokevirtual       , "invokevirtual"       , "bJJ"  , nullptr    , T_ILLEGAL, -1, true);
  def(_invokespecial       , "invokespecial"       , "bJJ"  , nullptr    , T_ILLEGAL, -1, true);
  def(_invokestatic        , "invokestatic"        , "bJJ"  , nullptr    , T_ILLEGAL,  0, true);
  def(_invokeinterface     , "invokeinterface"     , "bJJ__", nullptr    , T_ILLEGAL, -1, true);
  def(_invokedynamic       , "invokedynamic"       , "bJJJJ", nullptr    , T_ILLEGAL,  0, true );
  def(_new                 , "new"                 , "bkk"  , nullptr    , T_OBJECT ,  1, true );
  def(_newarray            , "newarray"            , "bc"   , nullptr    , T_OBJECT ,  0, true );
  def(_anewarray           , "anewarray"           , "bkk"  , nullptr    , T_OBJECT ,  0, true );
  def(_arraylength         , "arraylength"         , "b"    , nullptr    , T_INT    ,  0, true );
  def(_athrow              , "athrow"              , "b"    , nullptr    , T_VOID   , -1, true );
  def(_checkcast           , "checkcast"           , "bkk"  , nullptr    , T_OBJECT ,  0, true );
  def(_instanceof          , "instanceof"          , "bkk"  , nullptr    , T_INT    ,  0, true );
  def(_monitorenter        , "monitorenter"        , "b"    , nullptr    , T_VOID   , -1, true );
  def(_monitorexit         , "monitorexit"         , "b"    , nullptr    , T_VOID   , -1, true );
  def(_wide                , "wide"                , ""     , nullptr    , T_VOID   ,  0, false);
  def(_multianewarray      , "multianewarray"      , "bkkc" , nullptr    , T_OBJECT ,  1, true );
  def(_ifnull              , "ifnull"              , "boo"  , nullptr    , T_VOID   , -1, false);
  def(_ifnonnull           , "ifnonnull"           , "boo"  , nullptr    , T_VOID   , -1, false);
  def(_goto_w              , "goto_w"              , "boooo", nullptr    , T_VOID   ,  0, false);
  def(_jsr_w               , "jsr_w"               , "boooo", nullptr    , T_INT    ,  0, false);
  def(_breakpoint          , "breakpoint"          , ""     , nullptr    , T_VOID   ,  0, true);

  //  JVM bytecodes
  //  bytecode               bytecode name           format   wide f.   result tp  stk traps  std code

  def(_fast_agetfield      , "fast_agetfield"      , "bJJ"  , nullptr    , T_OBJECT ,  0, true , _getfield       );
  def(_fast_bgetfield      , "fast_bgetfield"      , "bJJ"  , nullptr    , T_INT    ,  0, true , _getfield       );
  def(_fast_cgetfield      , "fast_cgetfield"      , "bJJ"  , nullptr    , T_CHAR   ,  0, true , _getfield       );
  def(_fast_dgetfield      , "fast_dgetfield"      , "bJJ"  , nullptr    , T_DOUBLE ,  0, true , _getfield       );
  def(_fast_fgetfield      , "fast_fgetfield"      , "bJJ"  , nullptr    , T_FLOAT  ,  0, true , _getfield       );
  def(_fast_igetfield      , "fast_igetfield"      , "bJJ"  , nullptr    , T_INT    ,  0, true , _getfield       );
  def(_fast_lgetfield      , "fast_lgetfield"      , "bJJ"  , nullptr    , T_LONG   ,  0, true , _getfield       );
  def(_fast_sgetfield      , "fast_sgetfield"      , "bJJ"  , nullptr    , T_SHORT  ,  0, true , _getfield       );

  def(_fast_aputfield      , "fast_aputfield"      , "bJJ"  , nullptr    , T_OBJECT ,  0, true , _putfield       );
  def(_fast_bputfield      , "fast_bputfield"      , "bJJ"  , nullptr    , T_INT    ,  0, true , _putfield       );
  def(_fast_zputfield      , "fast_zputfield"      , "bJJ"  , nullptr    , T_INT    ,  0, true , _putfield       );
  def(_fast_cputfield      , "fast_cputfield"      , "bJJ"  , nullptr    , T_CHAR   ,  0, true , _putfield       );
  def(_fast_dputfield      , "fast_dputfield"      , "bJJ"  , nullptr    , T_DOUBLE ,  0, true , _putfield       );
  def(_fast_fputfield      , "fast_fputfield"      , "bJJ"  , nullptr    , T_FLOAT  ,  0, true , _putfield       );
  def(_fast_iputfield      , "fast_iputfield"      , "bJJ"  , nullptr    , T_INT    ,  0, true , _putfield       );
  def(_fast_lputfield      , "fast_lputfield"      , "bJJ"  , nullptr    , T_LONG   ,  0, true , _putfield       );
  def(_fast_sputfield      , "fast_sputfield"      , "bJJ"  , nullptr    , T_SHORT  ,  0, true , _putfield       );

  def(_fast_aload_0        , "fast_aload_0"        , "b"    , nullptr    , T_OBJECT ,  1, true , _aload_0        );
  def(_fast_iaccess_0      , "fast_iaccess_0"      , "b_JJ" , nullptr    , T_INT    ,  1, true , _aload_0        );
  def(_fast_aaccess_0      , "fast_aaccess_0"      , "b_JJ" , nullptr    , T_OBJECT ,  1, true , _aload_0        );
  def(_fast_faccess_0      , "fast_faccess_0"      , "b_JJ" , nullptr    , T_OBJECT ,  1, true , _aload_0        );

  def(_fast_iload          , "fast_iload"          , "bi"   , nullptr    , T_INT    ,  1, false, _iload);
  def(_fast_iload2         , "fast_iload2"         , "bi_i" , nullptr    , T_INT    ,  2, false, _iload);
  def(_fast_icaload        , "fast_icaload"        , "bi_"  , nullptr    , T_INT    ,  0, false, _iload);

  // Faster method invocation.
  def(_fast_invokevfinal   , "fast_invokevfinal"   , "bJJ"  , nullptr    , T_ILLEGAL, -1, true, _invokevirtual   );

  def(_fast_linearswitch   , "fast_linearswitch"   , ""     , nullptr    , T_VOID   , -1, false, _lookupswitch   );
  def(_fast_binaryswitch   , "fast_binaryswitch"   , ""     , nullptr    , T_VOID   , -1, false, _lookupswitch   );

  def(_return_register_finalizer , "return_register_finalizer" , "b"     , nullptr    , T_VOID   ,  0, true, _return);

  def(_invokehandle        , "invokehandle"        , "bJJ"  , nullptr    , T_ILLEGAL, -1, true, _invokevirtual   );

  def(_fast_aldc           , "fast_aldc"           , "bj"   , nullptr    , T_OBJECT,   1, true,  _ldc   );
  def(_fast_aldc_w         , "fast_aldc_w"         , "bJJ"  , nullptr    , T_OBJECT,   1, true,  _ldc_w );

  def(_nofast_getfield     , "nofast_getfield"     , "bJJ"  , nullptr    , T_ILLEGAL,  0, true,  _getfield       );
  def(_nofast_putfield     , "nofast_putfield"     , "bJJ"  , nullptr    , T_ILLEGAL, -2, true , _putfield       );

  def(_nofast_aload_0      , "nofast_aload_0"      , "b"    , nullptr    , T_OBJECT,   1, true , _aload_0        );
  def(_nofast_iload        , "nofast_iload"        , "bi"   , nullptr    , T_INT,      1, false, _iload          );

  def(_shouldnotreachhere  , "_shouldnotreachhere" , "b"    , nullptr    , T_VOID   ,  0, false);

  // compare can_trap information for each bytecode with the
  // can_trap information for the corresponding base bytecode
  // (if a rewritten bytecode can trap, so must the base bytecode)
  #ifdef ASSERT
    { for (int i = 0; i < number_of_codes; i++) {
        if (is_defined(i)) {
          Code code = cast(i);
          Code java = java_code(code);
          if (can_trap(code) && !can_trap(java))
            fatal("%s can trap => %s can trap, too", name(code), name(java));
        }
      }
    }
  #endif

  // initialization successful
  _is_initialized = true;
}


void bytecodes_init() {
  Bytecodes::initialize();
}

// Restore optimization
#ifdef _M_AMD64
#pragma optimize ("", on)
#endif
