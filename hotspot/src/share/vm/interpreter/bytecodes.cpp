/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_bytecodes.cpp.incl"


#if defined(WIN32) && (defined(_MSC_VER) && (_MSC_VER < 1600))
// Windows AMD64 Compiler Hangs compiling this file
// unless optimization is off
#ifdef _M_AMD64
#pragma optimize ("", off)
#endif
#endif


bool            Bytecodes::_is_initialized = false;
const char*     Bytecodes::_name          [Bytecodes::number_of_codes];
const char*     Bytecodes::_format        [Bytecodes::number_of_codes];
const char*     Bytecodes::_wide_format   [Bytecodes::number_of_codes];
BasicType       Bytecodes::_result_type   [Bytecodes::number_of_codes];
s_char          Bytecodes::_depth         [Bytecodes::number_of_codes];
u_char          Bytecodes::_length        [Bytecodes::number_of_codes];
bool            Bytecodes::_can_trap      [Bytecodes::number_of_codes];
Bytecodes::Code Bytecodes::_java_code     [Bytecodes::number_of_codes];
bool            Bytecodes::_can_rewrite   [Bytecodes::number_of_codes];


Bytecodes::Code Bytecodes::code_at(methodOop method, int bci) {
  return code_at(method->bcp_from(bci), method);
}

Bytecodes::Code Bytecodes::non_breakpoint_code_at(address bcp, methodOop method) {
  if (method == NULL)  method = methodOopDesc::method_from_bcp(bcp);
  return method->orig_bytecode_at(method->bci_from(bcp));
}

int Bytecodes::special_length_at(address bcp, address end) {
  Code code = code_at(bcp);
  switch (code) {
  case _wide:
    if (end != NULL && bcp + 1 >= end) {
      return -1; // don't read past end of code buffer
    }
    return wide_length_for(cast(*(bcp + 1)));
  case _tableswitch:
    { address aligned_bcp = (address)round_to((intptr_t)bcp + 1, jintSize);
      if (end != NULL && aligned_bcp + 3*jintSize >= end) {
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
    { address aligned_bcp = (address)round_to((intptr_t)bcp + 1, jintSize);
      if (end != NULL && aligned_bcp + 2*jintSize >= end) {
        return -1; // don't read past end of code buffer
      }
      jlong npairs = (jint)Bytes::get_Java_u4(aligned_bcp + jintSize);
      jlong len = (aligned_bcp - bcp) + (2 + 2*npairs)*jintSize;
      // only return len if it can be represented as a positive int;
      // return -1 otherwise
      return (len > 0 && len == (int)len) ? len : -1;
    }
  }
  return 0;
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
    return special_length_at(bcp, end);
  }
}



void Bytecodes::def(Code code, const char* name, const char* format, const char* wide_format, BasicType result_type, int depth, bool can_trap) {
  def(code, name, format, wide_format, result_type, depth, can_trap, code);
}


void Bytecodes::def(Code code, const char* name, const char* format, const char* wide_format, BasicType result_type, int depth, bool can_trap, Code java_code) {
  assert(wide_format == NULL || format != NULL, "short form must exist if there's a wide form");
  _name          [code] = name;
  _format        [code] = format;
  _wide_format   [code] = wide_format;
  _result_type   [code] = result_type;
  _depth         [code] = depth;
  _can_trap      [code] = can_trap;
  _length        [code] = format != NULL ? (u_char)strlen(format) : 0;
  _java_code     [code] = java_code;
  if (java_code != code)  _can_rewrite[java_code] = true;
}


// Format strings interpretation:
//
// b: bytecode
// c: signed constant, Java byte-ordering
// i: unsigned index , Java byte-ordering
// j: unsigned index , native byte-ordering
// o: branch offset  , Java byte-ordering
// _: unused/ignored
// w: wide bytecode
//
// Note: Right now the format strings are used for 2 purposes:
//       1. to specify the length of the bytecode
//          (= number of characters in format string)
//       2. to specify the bytecode attributes
//
//       The bytecode attributes are currently used only for bytecode tracing
//       (see BytecodeTracer); thus if more specific format information is
//       used, one would also have to adjust the bytecode tracer.
//
// Note: For bytecodes with variable length, the format string is the empty string.

void Bytecodes::initialize() {
  if (_is_initialized) return;
  assert(number_of_codes <= 256, "too many bytecodes");

  // initialize bytecode tables - didn't use static array initializers
  // (such as {}) so we can do additional consistency checks and init-
  // code is independent of actual bytecode numbering.
  //
  // Note 1: NULL for the format string means the bytecode doesn't exist
  //         in that form.
  //
  // Note 2: The result type is T_ILLEGAL for bytecodes where the top of stack
  //         type after execution is not only determined by the bytecode itself.

  //  Java bytecodes
  //  bytecode               bytecode name           format   wide f.   result tp  stk traps
  def(_nop                 , "nop"                 , "b"    , NULL    , T_VOID   ,  0, false);
  def(_aconst_null         , "aconst_null"         , "b"    , NULL    , T_OBJECT ,  1, false);
  def(_iconst_m1           , "iconst_m1"           , "b"    , NULL    , T_INT    ,  1, false);
  def(_iconst_0            , "iconst_0"            , "b"    , NULL    , T_INT    ,  1, false);
  def(_iconst_1            , "iconst_1"            , "b"    , NULL    , T_INT    ,  1, false);
  def(_iconst_2            , "iconst_2"            , "b"    , NULL    , T_INT    ,  1, false);
  def(_iconst_3            , "iconst_3"            , "b"    , NULL    , T_INT    ,  1, false);
  def(_iconst_4            , "iconst_4"            , "b"    , NULL    , T_INT    ,  1, false);
  def(_iconst_5            , "iconst_5"            , "b"    , NULL    , T_INT    ,  1, false);
  def(_lconst_0            , "lconst_0"            , "b"    , NULL    , T_LONG   ,  2, false);
  def(_lconst_1            , "lconst_1"            , "b"    , NULL    , T_LONG   ,  2, false);
  def(_fconst_0            , "fconst_0"            , "b"    , NULL    , T_FLOAT  ,  1, false);
  def(_fconst_1            , "fconst_1"            , "b"    , NULL    , T_FLOAT  ,  1, false);
  def(_fconst_2            , "fconst_2"            , "b"    , NULL    , T_FLOAT  ,  1, false);
  def(_dconst_0            , "dconst_0"            , "b"    , NULL    , T_DOUBLE ,  2, false);
  def(_dconst_1            , "dconst_1"            , "b"    , NULL    , T_DOUBLE ,  2, false);
  def(_bipush              , "bipush"              , "bc"   , NULL    , T_INT    ,  1, false);
  def(_sipush              , "sipush"              , "bcc"  , NULL    , T_INT    ,  1, false);
  def(_ldc                 , "ldc"                 , "bi"   , NULL    , T_ILLEGAL,  1, true );
  def(_ldc_w               , "ldc_w"               , "bii"  , NULL    , T_ILLEGAL,  1, true );
  def(_ldc2_w              , "ldc2_w"              , "bii"  , NULL    , T_ILLEGAL,  2, true );
  def(_iload               , "iload"               , "bi"   , "wbii"  , T_INT    ,  1, false);
  def(_lload               , "lload"               , "bi"   , "wbii"  , T_LONG   ,  2, false);
  def(_fload               , "fload"               , "bi"   , "wbii"  , T_FLOAT  ,  1, false);
  def(_dload               , "dload"               , "bi"   , "wbii"  , T_DOUBLE ,  2, false);
  def(_aload               , "aload"               , "bi"   , "wbii"  , T_OBJECT ,  1, false);
  def(_iload_0             , "iload_0"             , "b"    , NULL    , T_INT    ,  1, false);
  def(_iload_1             , "iload_1"             , "b"    , NULL    , T_INT    ,  1, false);
  def(_iload_2             , "iload_2"             , "b"    , NULL    , T_INT    ,  1, false);
  def(_iload_3             , "iload_3"             , "b"    , NULL    , T_INT    ,  1, false);
  def(_lload_0             , "lload_0"             , "b"    , NULL    , T_LONG   ,  2, false);
  def(_lload_1             , "lload_1"             , "b"    , NULL    , T_LONG   ,  2, false);
  def(_lload_2             , "lload_2"             , "b"    , NULL    , T_LONG   ,  2, false);
  def(_lload_3             , "lload_3"             , "b"    , NULL    , T_LONG   ,  2, false);
  def(_fload_0             , "fload_0"             , "b"    , NULL    , T_FLOAT  ,  1, false);
  def(_fload_1             , "fload_1"             , "b"    , NULL    , T_FLOAT  ,  1, false);
  def(_fload_2             , "fload_2"             , "b"    , NULL    , T_FLOAT  ,  1, false);
  def(_fload_3             , "fload_3"             , "b"    , NULL    , T_FLOAT  ,  1, false);
  def(_dload_0             , "dload_0"             , "b"    , NULL    , T_DOUBLE ,  2, false);
  def(_dload_1             , "dload_1"             , "b"    , NULL    , T_DOUBLE ,  2, false);
  def(_dload_2             , "dload_2"             , "b"    , NULL    , T_DOUBLE ,  2, false);
  def(_dload_3             , "dload_3"             , "b"    , NULL    , T_DOUBLE ,  2, false);
  def(_aload_0             , "aload_0"             , "b"    , NULL    , T_OBJECT ,  1, true ); // rewriting in interpreter
  def(_aload_1             , "aload_1"             , "b"    , NULL    , T_OBJECT ,  1, false);
  def(_aload_2             , "aload_2"             , "b"    , NULL    , T_OBJECT ,  1, false);
  def(_aload_3             , "aload_3"             , "b"    , NULL    , T_OBJECT ,  1, false);
  def(_iaload              , "iaload"              , "b"    , NULL    , T_INT    , -1, true );
  def(_laload              , "laload"              , "b"    , NULL    , T_LONG   ,  0, true );
  def(_faload              , "faload"              , "b"    , NULL    , T_FLOAT  , -1, true );
  def(_daload              , "daload"              , "b"    , NULL    , T_DOUBLE ,  0, true );
  def(_aaload              , "aaload"              , "b"    , NULL    , T_OBJECT , -1, true );
  def(_baload              , "baload"              , "b"    , NULL    , T_INT    , -1, true );
  def(_caload              , "caload"              , "b"    , NULL    , T_INT    , -1, true );
  def(_saload              , "saload"              , "b"    , NULL    , T_INT    , -1, true );
  def(_istore              , "istore"              , "bi"   , "wbii"  , T_VOID   , -1, false);
  def(_lstore              , "lstore"              , "bi"   , "wbii"  , T_VOID   , -2, false);
  def(_fstore              , "fstore"              , "bi"   , "wbii"  , T_VOID   , -1, false);
  def(_dstore              , "dstore"              , "bi"   , "wbii"  , T_VOID   , -2, false);
  def(_astore              , "astore"              , "bi"   , "wbii"  , T_VOID   , -1, false);
  def(_istore_0            , "istore_0"            , "b"    , NULL    , T_VOID   , -1, false);
  def(_istore_1            , "istore_1"            , "b"    , NULL    , T_VOID   , -1, false);
  def(_istore_2            , "istore_2"            , "b"    , NULL    , T_VOID   , -1, false);
  def(_istore_3            , "istore_3"            , "b"    , NULL    , T_VOID   , -1, false);
  def(_lstore_0            , "lstore_0"            , "b"    , NULL    , T_VOID   , -2, false);
  def(_lstore_1            , "lstore_1"            , "b"    , NULL    , T_VOID   , -2, false);
  def(_lstore_2            , "lstore_2"            , "b"    , NULL    , T_VOID   , -2, false);
  def(_lstore_3            , "lstore_3"            , "b"    , NULL    , T_VOID   , -2, false);
  def(_fstore_0            , "fstore_0"            , "b"    , NULL    , T_VOID   , -1, false);
  def(_fstore_1            , "fstore_1"            , "b"    , NULL    , T_VOID   , -1, false);
  def(_fstore_2            , "fstore_2"            , "b"    , NULL    , T_VOID   , -1, false);
  def(_fstore_3            , "fstore_3"            , "b"    , NULL    , T_VOID   , -1, false);
  def(_dstore_0            , "dstore_0"            , "b"    , NULL    , T_VOID   , -2, false);
  def(_dstore_1            , "dstore_1"            , "b"    , NULL    , T_VOID   , -2, false);
  def(_dstore_2            , "dstore_2"            , "b"    , NULL    , T_VOID   , -2, false);
  def(_dstore_3            , "dstore_3"            , "b"    , NULL    , T_VOID   , -2, false);
  def(_astore_0            , "astore_0"            , "b"    , NULL    , T_VOID   , -1, false);
  def(_astore_1            , "astore_1"            , "b"    , NULL    , T_VOID   , -1, false);
  def(_astore_2            , "astore_2"            , "b"    , NULL    , T_VOID   , -1, false);
  def(_astore_3            , "astore_3"            , "b"    , NULL    , T_VOID   , -1, false);
  def(_iastore             , "iastore"             , "b"    , NULL    , T_VOID   , -3, true );
  def(_lastore             , "lastore"             , "b"    , NULL    , T_VOID   , -4, true );
  def(_fastore             , "fastore"             , "b"    , NULL    , T_VOID   , -3, true );
  def(_dastore             , "dastore"             , "b"    , NULL    , T_VOID   , -4, true );
  def(_aastore             , "aastore"             , "b"    , NULL    , T_VOID   , -3, true );
  def(_bastore             , "bastore"             , "b"    , NULL    , T_VOID   , -3, true );
  def(_castore             , "castore"             , "b"    , NULL    , T_VOID   , -3, true );
  def(_sastore             , "sastore"             , "b"    , NULL    , T_VOID   , -3, true );
  def(_pop                 , "pop"                 , "b"    , NULL    , T_VOID   , -1, false);
  def(_pop2                , "pop2"                , "b"    , NULL    , T_VOID   , -2, false);
  def(_dup                 , "dup"                 , "b"    , NULL    , T_VOID   ,  1, false);
  def(_dup_x1              , "dup_x1"              , "b"    , NULL    , T_VOID   ,  1, false);
  def(_dup_x2              , "dup_x2"              , "b"    , NULL    , T_VOID   ,  1, false);
  def(_dup2                , "dup2"                , "b"    , NULL    , T_VOID   ,  2, false);
  def(_dup2_x1             , "dup2_x1"             , "b"    , NULL    , T_VOID   ,  2, false);
  def(_dup2_x2             , "dup2_x2"             , "b"    , NULL    , T_VOID   ,  2, false);
  def(_swap                , "swap"                , "b"    , NULL    , T_VOID   ,  0, false);
  def(_iadd                , "iadd"                , "b"    , NULL    , T_INT    , -1, false);
  def(_ladd                , "ladd"                , "b"    , NULL    , T_LONG   , -2, false);
  def(_fadd                , "fadd"                , "b"    , NULL    , T_FLOAT  , -1, false);
  def(_dadd                , "dadd"                , "b"    , NULL    , T_DOUBLE , -2, false);
  def(_isub                , "isub"                , "b"    , NULL    , T_INT    , -1, false);
  def(_lsub                , "lsub"                , "b"    , NULL    , T_LONG   , -2, false);
  def(_fsub                , "fsub"                , "b"    , NULL    , T_FLOAT  , -1, false);
  def(_dsub                , "dsub"                , "b"    , NULL    , T_DOUBLE , -2, false);
  def(_imul                , "imul"                , "b"    , NULL    , T_INT    , -1, false);
  def(_lmul                , "lmul"                , "b"    , NULL    , T_LONG   , -2, false);
  def(_fmul                , "fmul"                , "b"    , NULL    , T_FLOAT  , -1, false);
  def(_dmul                , "dmul"                , "b"    , NULL    , T_DOUBLE , -2, false);
  def(_idiv                , "idiv"                , "b"    , NULL    , T_INT    , -1, true );
  def(_ldiv                , "ldiv"                , "b"    , NULL    , T_LONG   , -2, true );
  def(_fdiv                , "fdiv"                , "b"    , NULL    , T_FLOAT  , -1, false);
  def(_ddiv                , "ddiv"                , "b"    , NULL    , T_DOUBLE , -2, false);
  def(_irem                , "irem"                , "b"    , NULL    , T_INT    , -1, true );
  def(_lrem                , "lrem"                , "b"    , NULL    , T_LONG   , -2, true );
  def(_frem                , "frem"                , "b"    , NULL    , T_FLOAT  , -1, false);
  def(_drem                , "drem"                , "b"    , NULL    , T_DOUBLE , -2, false);
  def(_ineg                , "ineg"                , "b"    , NULL    , T_INT    ,  0, false);
  def(_lneg                , "lneg"                , "b"    , NULL    , T_LONG   ,  0, false);
  def(_fneg                , "fneg"                , "b"    , NULL    , T_FLOAT  ,  0, false);
  def(_dneg                , "dneg"                , "b"    , NULL    , T_DOUBLE ,  0, false);
  def(_ishl                , "ishl"                , "b"    , NULL    , T_INT    , -1, false);
  def(_lshl                , "lshl"                , "b"    , NULL    , T_LONG   , -1, false);
  def(_ishr                , "ishr"                , "b"    , NULL    , T_INT    , -1, false);
  def(_lshr                , "lshr"                , "b"    , NULL    , T_LONG   , -1, false);
  def(_iushr               , "iushr"               , "b"    , NULL    , T_INT    , -1, false);
  def(_lushr               , "lushr"               , "b"    , NULL    , T_LONG   , -1, false);
  def(_iand                , "iand"                , "b"    , NULL    , T_INT    , -1, false);
  def(_land                , "land"                , "b"    , NULL    , T_LONG   , -2, false);
  def(_ior                 , "ior"                 , "b"    , NULL    , T_INT    , -1, false);
  def(_lor                 , "lor"                 , "b"    , NULL    , T_LONG   , -2, false);
  def(_ixor                , "ixor"                , "b"    , NULL    , T_INT    , -1, false);
  def(_lxor                , "lxor"                , "b"    , NULL    , T_LONG   , -2, false);
  def(_iinc                , "iinc"                , "bic"  , "wbiicc", T_VOID   ,  0, false);
  def(_i2l                 , "i2l"                 , "b"    , NULL    , T_LONG   ,  1, false);
  def(_i2f                 , "i2f"                 , "b"    , NULL    , T_FLOAT  ,  0, false);
  def(_i2d                 , "i2d"                 , "b"    , NULL    , T_DOUBLE ,  1, false);
  def(_l2i                 , "l2i"                 , "b"    , NULL    , T_INT    , -1, false);
  def(_l2f                 , "l2f"                 , "b"    , NULL    , T_FLOAT  , -1, false);
  def(_l2d                 , "l2d"                 , "b"    , NULL    , T_DOUBLE ,  0, false);
  def(_f2i                 , "f2i"                 , "b"    , NULL    , T_INT    ,  0, false);
  def(_f2l                 , "f2l"                 , "b"    , NULL    , T_LONG   ,  1, false);
  def(_f2d                 , "f2d"                 , "b"    , NULL    , T_DOUBLE ,  1, false);
  def(_d2i                 , "d2i"                 , "b"    , NULL    , T_INT    , -1, false);
  def(_d2l                 , "d2l"                 , "b"    , NULL    , T_LONG   ,  0, false);
  def(_d2f                 , "d2f"                 , "b"    , NULL    , T_FLOAT  , -1, false);
  def(_i2b                 , "i2b"                 , "b"    , NULL    , T_BYTE   ,  0, false);
  def(_i2c                 , "i2c"                 , "b"    , NULL    , T_CHAR   ,  0, false);
  def(_i2s                 , "i2s"                 , "b"    , NULL    , T_SHORT  ,  0, false);
  def(_lcmp                , "lcmp"                , "b"    , NULL    , T_VOID   , -3, false);
  def(_fcmpl               , "fcmpl"               , "b"    , NULL    , T_VOID   , -1, false);
  def(_fcmpg               , "fcmpg"               , "b"    , NULL    , T_VOID   , -1, false);
  def(_dcmpl               , "dcmpl"               , "b"    , NULL    , T_VOID   , -3, false);
  def(_dcmpg               , "dcmpg"               , "b"    , NULL    , T_VOID   , -3, false);
  def(_ifeq                , "ifeq"                , "boo"  , NULL    , T_VOID   , -1, false);
  def(_ifne                , "ifne"                , "boo"  , NULL    , T_VOID   , -1, false);
  def(_iflt                , "iflt"                , "boo"  , NULL    , T_VOID   , -1, false);
  def(_ifge                , "ifge"                , "boo"  , NULL    , T_VOID   , -1, false);
  def(_ifgt                , "ifgt"                , "boo"  , NULL    , T_VOID   , -1, false);
  def(_ifle                , "ifle"                , "boo"  , NULL    , T_VOID   , -1, false);
  def(_if_icmpeq           , "if_icmpeq"           , "boo"  , NULL    , T_VOID   , -2, false);
  def(_if_icmpne           , "if_icmpne"           , "boo"  , NULL    , T_VOID   , -2, false);
  def(_if_icmplt           , "if_icmplt"           , "boo"  , NULL    , T_VOID   , -2, false);
  def(_if_icmpge           , "if_icmpge"           , "boo"  , NULL    , T_VOID   , -2, false);
  def(_if_icmpgt           , "if_icmpgt"           , "boo"  , NULL    , T_VOID   , -2, false);
  def(_if_icmple           , "if_icmple"           , "boo"  , NULL    , T_VOID   , -2, false);
  def(_if_acmpeq           , "if_acmpeq"           , "boo"  , NULL    , T_VOID   , -2, false);
  def(_if_acmpne           , "if_acmpne"           , "boo"  , NULL    , T_VOID   , -2, false);
  def(_goto                , "goto"                , "boo"  , NULL    , T_VOID   ,  0, false);
  def(_jsr                 , "jsr"                 , "boo"  , NULL    , T_INT    ,  0, false);
  def(_ret                 , "ret"                 , "bi"   , "wbii"  , T_VOID   ,  0, false);
  def(_tableswitch         , "tableswitch"         , ""     , NULL    , T_VOID   , -1, false); // may have backward branches
  def(_lookupswitch        , "lookupswitch"        , ""     , NULL    , T_VOID   , -1, false); // rewriting in interpreter
  def(_ireturn             , "ireturn"             , "b"    , NULL    , T_INT    , -1, true);
  def(_lreturn             , "lreturn"             , "b"    , NULL    , T_LONG   , -2, true);
  def(_freturn             , "freturn"             , "b"    , NULL    , T_FLOAT  , -1, true);
  def(_dreturn             , "dreturn"             , "b"    , NULL    , T_DOUBLE , -2, true);
  def(_areturn             , "areturn"             , "b"    , NULL    , T_OBJECT , -1, true);
  def(_return              , "return"              , "b"    , NULL    , T_VOID   ,  0, true);
  def(_getstatic           , "getstatic"           , "bjj"  , NULL    , T_ILLEGAL,  1, true );
  def(_putstatic           , "putstatic"           , "bjj"  , NULL    , T_ILLEGAL, -1, true );
  def(_getfield            , "getfield"            , "bjj"  , NULL    , T_ILLEGAL,  0, true );
  def(_putfield            , "putfield"            , "bjj"  , NULL    , T_ILLEGAL, -2, true );
  def(_invokevirtual       , "invokevirtual"       , "bjj"  , NULL    , T_ILLEGAL, -1, true);
  def(_invokespecial       , "invokespecial"       , "bjj"  , NULL    , T_ILLEGAL, -1, true);
  def(_invokestatic        , "invokestatic"        , "bjj"  , NULL    , T_ILLEGAL,  0, true);
  def(_invokeinterface     , "invokeinterface"     , "bjj__", NULL    , T_ILLEGAL, -1, true);
  def(_invokedynamic       , "invokedynamic"       , "bjjjj", NULL    , T_ILLEGAL,  0, true );
  def(_new                 , "new"                 , "bii"  , NULL    , T_OBJECT ,  1, true );
  def(_newarray            , "newarray"            , "bc"   , NULL    , T_OBJECT ,  0, true );
  def(_anewarray           , "anewarray"           , "bii"  , NULL    , T_OBJECT ,  0, true );
  def(_arraylength         , "arraylength"         , "b"    , NULL    , T_VOID   ,  0, true );
  def(_athrow              , "athrow"              , "b"    , NULL    , T_VOID   , -1, true );
  def(_checkcast           , "checkcast"           , "bii"  , NULL    , T_OBJECT ,  0, true );
  def(_instanceof          , "instanceof"          , "bii"  , NULL    , T_INT    ,  0, true );
  def(_monitorenter        , "monitorenter"        , "b"    , NULL    , T_VOID   , -1, true );
  def(_monitorexit         , "monitorexit"         , "b"    , NULL    , T_VOID   , -1, true );
  def(_wide                , "wide"                , ""     , NULL    , T_VOID   ,  0, false);
  def(_multianewarray      , "multianewarray"      , "biic" , NULL    , T_OBJECT ,  1, true );
  def(_ifnull              , "ifnull"              , "boo"  , NULL    , T_VOID   , -1, false);
  def(_ifnonnull           , "ifnonnull"           , "boo"  , NULL    , T_VOID   , -1, false);
  def(_goto_w              , "goto_w"              , "boooo", NULL    , T_VOID   ,  0, false);
  def(_jsr_w               , "jsr_w"               , "boooo", NULL    , T_INT    ,  0, false);
  def(_breakpoint          , "breakpoint"          , ""     , NULL    , T_VOID   ,  0, true);

  //  JVM bytecodes
  //  bytecode               bytecode name           format   wide f.   result tp  stk traps  std code

  def(_fast_agetfield      , "fast_agetfield"      , "bjj"  , NULL    , T_OBJECT ,  0, true , _getfield       );
  def(_fast_bgetfield      , "fast_bgetfield"      , "bjj"  , NULL    , T_INT    ,  0, true , _getfield       );
  def(_fast_cgetfield      , "fast_cgetfield"      , "bjj"  , NULL    , T_CHAR   ,  0, true , _getfield       );
  def(_fast_dgetfield      , "fast_dgetfield"      , "bjj"  , NULL    , T_DOUBLE ,  0, true , _getfield       );
  def(_fast_fgetfield      , "fast_fgetfield"      , "bjj"  , NULL    , T_FLOAT  ,  0, true , _getfield       );
  def(_fast_igetfield      , "fast_igetfield"      , "bjj"  , NULL    , T_INT    ,  0, true , _getfield       );
  def(_fast_lgetfield      , "fast_lgetfield"      , "bjj"  , NULL    , T_LONG   ,  0, true , _getfield       );
  def(_fast_sgetfield      , "fast_sgetfield"      , "bjj"  , NULL    , T_SHORT  ,  0, true , _getfield       );

  def(_fast_aputfield      , "fast_aputfield"      , "bjj"  , NULL    , T_OBJECT ,  0, true , _putfield       );
  def(_fast_bputfield      , "fast_bputfield"      , "bjj"  , NULL    , T_INT    ,  0, true , _putfield       );
  def(_fast_cputfield      , "fast_cputfield"      , "bjj"  , NULL    , T_CHAR   ,  0, true , _putfield       );
  def(_fast_dputfield      , "fast_dputfield"      , "bjj"  , NULL    , T_DOUBLE ,  0, true , _putfield       );
  def(_fast_fputfield      , "fast_fputfield"      , "bjj"  , NULL    , T_FLOAT  ,  0, true , _putfield       );
  def(_fast_iputfield      , "fast_iputfield"      , "bjj"  , NULL    , T_INT    ,  0, true , _putfield       );
  def(_fast_lputfield      , "fast_lputfield"      , "bjj"  , NULL    , T_LONG   ,  0, true , _putfield       );
  def(_fast_sputfield      , "fast_sputfield"      , "bjj"  , NULL    , T_SHORT  ,  0, true , _putfield       );

  def(_fast_aload_0        , "fast_aload_0"        , "b"    , NULL    , T_OBJECT ,  1, true , _aload_0        );
  def(_fast_iaccess_0      , "fast_iaccess_0"      , "b_jj" , NULL    , T_INT    ,  1, true , _aload_0        );
  def(_fast_aaccess_0      , "fast_aaccess_0"      , "b_jj" , NULL    , T_OBJECT ,  1, true , _aload_0        );
  def(_fast_faccess_0      , "fast_faccess_0"      , "b_jj" , NULL    , T_OBJECT ,  1, true , _aload_0        );

  def(_fast_iload          , "fast_iload"          , "bi"   , NULL    , T_INT    ,  1, false, _iload);
  def(_fast_iload2         , "fast_iload2"         , "bi_i" , NULL    , T_INT    ,  2, false, _iload);
  def(_fast_icaload        , "fast_icaload"        , "bi_"  , NULL    , T_INT    ,  0, false, _iload);

  // Faster method invocation.
  def(_fast_invokevfinal   , "fast_invokevfinal"   , "bjj"  , NULL    , T_ILLEGAL, -1, true, _invokevirtual   );

  def(_fast_linearswitch   , "fast_linearswitch"   , ""     , NULL    , T_VOID   , -1, false, _lookupswitch   );
  def(_fast_binaryswitch   , "fast_binaryswitch"   , ""     , NULL    , T_VOID   , -1, false, _lookupswitch   );

  def(_return_register_finalizer , "return_register_finalizer" , "b"    , NULL    , T_VOID   ,  0, true, _return);

  def(_shouldnotreachhere  , "_shouldnotreachhere" , "b"    , NULL    , T_VOID   ,  0, false);

  // platform specific JVM bytecodes
  pd_initialize();

  // compare can_trap information for each bytecode with the
  // can_trap information for the corresponding base bytecode
  // (if a rewritten bytecode can trap, so must the base bytecode)
  #ifdef ASSERT
    { for (int i = 0; i < number_of_codes; i++) {
        if (is_defined(i)) {
          Code code = cast(i);
          Code java = java_code(code);
          if (can_trap(code) && !can_trap(java)) fatal2("%s can trap => %s can trap, too", name(code), name(java));
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
