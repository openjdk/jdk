/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classPrinter.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "interpreter/bytecodeHistogram.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/bytecodeTracer.hpp"
#include "interpreter/bytecodes.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "memory/resourceArea.hpp"
#include "oops/constantPool.inline.hpp"
#include "oops/methodData.hpp"
#include "oops/method.hpp"
#include "oops/resolvedFieldEntry.hpp"
#include "oops/resolvedIndyEntry.hpp"
#include "oops/resolvedMethodEntry.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/osThread.hpp"
#include "runtime/timer.hpp"
#include "utilities/align.hpp"

// Prints the current bytecode and its attributes using bytecode-specific information.

class BytecodePrinter {
 private:
  // %%% This field is not GC-ed, and so can contain garbage
  // between critical sections.  Use only pointer-comparison
  // operations on the pointer, except within a critical section.
  // (Also, ensure that occasional false positives are benign.)
  Method* _current_method;
  bool      _is_wide;
  Bytecodes::Code _code;
  address   _next_pc;                // current decoding position
  int       _flags;
  bool      _is_linked;

  bool      is_linked() const        { return _is_linked; }
  void      align()                  { _next_pc = align_up(_next_pc, sizeof(jint)); }
  int       get_byte()               { return *(jbyte*) _next_pc++; }  // signed
  int       get_index_u1()           { return *(address)_next_pc++; }  // returns 0x00 - 0xff as an int
  short     get_short()              { short i = Bytes::get_Java_u2  (_next_pc); _next_pc += 2; return i; }
  int       get_int()                { int   i = Bytes::get_Java_u4  (_next_pc); _next_pc += 4; return i; }
  int       get_native_index_u2()    { int   i = Bytes::get_native_u2(_next_pc); _next_pc += 2; return i; }
  int       get_native_index_u4()    { int   i = Bytes::get_native_u4(_next_pc); _next_pc += 4; return i; }
  int       get_Java_index_u2()      { int   i = Bytes::get_Java_u2  (_next_pc); _next_pc += 2; return i; }
  int       get_Java_index_u4()      { int   i = Bytes::get_Java_u4  (_next_pc); _next_pc += 4; return i; }
  int       get_index_special()      { return (is_wide()) ? get_Java_index_u2() : get_index_u1(); }
  Method*   method() const           { return _current_method; }
  bool      is_wide() const          { return _is_wide; }
  Bytecodes::Code raw_code() const   { return Bytecodes::Code(_code); }
  ConstantPool* constants() const    { return method()->constants(); }
  ConstantPoolCache* cpcache() const { assert(is_linked(), "must be"); return constants()->cache(); }

  void      print_constant(int i, outputStream* st);
  void      print_cpcache_entry(int cpc_index, outputStream* st);
  void      print_invokedynamic(int indy_index, int cp_index, outputStream* st);
  void      print_bsm(int cp_index, outputStream* st);
  void      print_field_or_method(int cp_index, outputStream* st);
  void      print_dynamic(int cp_index, outputStream* st);
  void      print_attributes(int bci, outputStream* st);
  void      bytecode_epilog(int bci, outputStream* st);

 public:
  BytecodePrinter(int flags = 0) {
    _is_wide = false;
    _code = Bytecodes::_illegal;
    _flags = flags;
  }

  // This method is called while executing the raw bytecodes, so none of
  // the adjustments that BytecodeStream performs applies.
  void trace(const methodHandle& method, address bcp, uintptr_t tos, uintptr_t tos2, outputStream* st) {
    ResourceMark rm;
    bool method_changed = _current_method != method();
    if (method_changed) {
      // Note 1: This code will not work as expected with true MT/MP.
      //         Need an explicit lock or a different solution.
      // It is possible for this block to be skipped, if a garbage
      // _current_method pointer happens to have the same bits as
      // the incoming method.  We could lose a line of trace output.
      // This is acceptable in a debug-only feature.
      st->cr();
      st->print("[%ld] ", (long) Thread::current()->osthread()->thread_id());
      method->print_name(st);
      st->cr();
      _current_method = method();
      _is_linked = method->method_holder()->is_linked();
      assert(_is_linked, "this function must be called on methods that are already executing");
    }
    Bytecodes::Code code;
    if (is_wide()) {
      // bcp wasn't advanced if previous bytecode was _wide.
      code = Bytecodes::code_at(method(), bcp+1);
    } else {
      code = Bytecodes::code_at(method(), bcp);
    }
    _code = code;
    _next_pc = is_wide() ? bcp+2 : bcp+1;
    // Trace each bytecode unless we're truncating the tracing output, then only print the first
    // bytecode in every method as well as returns/throws that pop control flow
    if (!TraceBytecodesTruncated || method_changed ||
        code == Bytecodes::_athrow ||
        code == Bytecodes::_return_register_finalizer ||
        (code >= Bytecodes::_ireturn && code <= Bytecodes::_return)) {
      int bci = (int)(bcp - method->code_base());
      st->print("[%ld] ", (long) Thread::current()->osthread()->thread_id());
      if (Verbose) {
        st->print("%8d  %4d  " INTPTR_FORMAT " " INTPTR_FORMAT " %s",
            BytecodeCounter::counter_value(), bci, tos, tos2, Bytecodes::name(code));
      } else {
        st->print("%8d  %4d  %s",
            BytecodeCounter::counter_value(), bci, Bytecodes::name(code));
      }
      print_attributes(bci, st);
    }
    // Set is_wide for the next one, since the caller of this doesn't skip
    // the next bytecode.
    _is_wide = (code == Bytecodes::_wide);
    _code = Bytecodes::_illegal;

#ifndef PRODUCT
    if (TraceBytecodesStopAt != 0 && BytecodeCounter::counter_value() >= TraceBytecodesStopAt) {
      TraceBytecodes = false;
    }
#endif
  }

  // Used for Method*::print_codes().  The input bcp comes from
  // BytecodeStream, which will skip wide bytecodes.
  void trace(const methodHandle& method, address bcp, outputStream* st) {
    _current_method = method();
    _is_linked = method->method_holder()->is_linked();
    ResourceMark rm;
    Bytecodes::Code code = Bytecodes::code_at(method(), bcp);
    // Set is_wide
    _is_wide = (code == Bytecodes::_wide);
    if (is_wide()) {
      code = Bytecodes::code_at(method(), bcp+1);
    }
    _code = code;
    int bci = (int)(bcp - method->code_base());
    // Print bytecode index and name
    if (ClassPrinter::has_mode(_flags, ClassPrinter::PRINT_BYTECODE_ADDR)) {
      st->print(INTPTR_FORMAT " ", p2i(bcp));
    }
    if (is_wide()) {
      st->print("%4d %s_w", bci, Bytecodes::name(code));
    } else {
      st->print("%4d %s", bci, Bytecodes::name(code));
    }
    _next_pc = is_wide() ? bcp+2 : bcp+1;
    print_attributes(bci, st);
    bytecode_epilog(bci, st);
  }
};

// We need a global instance to keep track of the states when the bytecodes
// are executed. Access by multiple threads are controlled by ttyLocker.
static BytecodePrinter _interpreter_printer;

void BytecodeTracer::trace_interpreter(const methodHandle& method, address bcp, uintptr_t tos, uintptr_t tos2, outputStream* st) {
  if (TraceBytecodes && BytecodeCounter::counter_value() >= TraceBytecodesAt) {
    ttyLocker ttyl;  // 5065316: keep the following output coherent
    // The ttyLocker also prevents races between two threads
    // trying to use the single instance of BytecodePrinter.
    //
    // There used to be a leaf mutex here, but the ttyLocker will
    // work just as well, as long as the printing operations never block.
    _interpreter_printer.trace(method, bcp, tos, tos2, st);
  }
}

void BytecodeTracer::print_method_codes(const methodHandle& method, int from, int to, outputStream* st, int flags) {
  BytecodePrinter method_printer(flags);
  BytecodeStream s(method);
  s.set_interval(from, to);

  // Keep output to st coherent: collect all lines and print at once.
  ResourceMark rm;
  stringStream ss;
  while (s.next() >= 0) {
    method_printer.trace(method, s.bcp(), &ss);
  }
  st->print("%s", ss.as_string());
}

void BytecodePrinter::print_constant(int cp_index, outputStream* st) {
  ConstantPool* constants = method()->constants();
  constantTag tag = constants->tag_at(cp_index);

  if (tag.is_int()) {
    st->print_cr(" " INT32_FORMAT, constants->int_at(cp_index));
  } else if (tag.is_long()) {
    st->print_cr(" " INT64_FORMAT, (int64_t)(constants->long_at(cp_index)));
  } else if (tag.is_float()) {
    st->print_cr(" %f", constants->float_at(cp_index));
  } else if (tag.is_double()) {
    st->print_cr(" %f", constants->double_at(cp_index));
  } else if (tag.is_string()) {
    const char* string = constants->unresolved_string_at(cp_index)->as_quoted_ascii();
    st->print_cr(" \"%s\"", string);
  } else if (tag.is_klass()) {
    st->print_cr(" %s", constants->resolved_klass_at(cp_index)->external_name());
  } else if (tag.is_unresolved_klass()) {
    st->print_cr(" %s", constants->klass_at_noresolve(cp_index)->as_quoted_ascii());
  } else if (tag.is_method_type()) {
    int i2 = constants->method_type_index_at(cp_index);
    st->print(" <MethodType> %d", i2);
    st->print_cr(" %s", constants->symbol_at(i2)->as_quoted_ascii());
  } else if (tag.is_method_handle()) {
    int kind = constants->method_handle_ref_kind_at(cp_index);
    int i2 = constants->method_handle_index_at(cp_index);
    st->print(" <MethodHandle of kind %d index at %d>", kind, i2);
    print_field_or_method(i2, st);
  } else if (tag.is_dynamic_constant()) {
    print_dynamic(cp_index, st);
    if (ClassPrinter::has_mode(_flags, ClassPrinter::PRINT_DYNAMIC)) {
      print_bsm(cp_index, st);
    }
  } else {
    st->print_cr(" bad tag=%d at %d", tag.value(), cp_index);
  }
}

// Fieldref, Methodref, or InterfaceMethodref
void BytecodePrinter::print_field_or_method(int cp_index, outputStream* st) {
  ConstantPool* constants = method()->constants();
  constantTag tag = constants->tag_at(cp_index);

  switch (tag.value()) {
  case JVM_CONSTANT_Fieldref:
  case JVM_CONSTANT_Methodref:
  case JVM_CONSTANT_InterfaceMethodref:
    break;
  default:
    st->print_cr(" bad tag=%d at %d", tag.value(), cp_index);
    return;
  }

  Symbol* name = constants->uncached_name_ref_at(cp_index);
  Symbol* signature = constants->uncached_signature_ref_at(cp_index);
  Symbol* klass = constants->klass_name_at(constants->uncached_klass_ref_index_at(cp_index));
  const char* sep = (tag.is_field() ? ":" : "");
  st->print_cr(" %d <%s.%s%s%s> ", cp_index, klass->as_C_string(), name->as_C_string(), sep, signature->as_C_string());
}

// JVM_CONSTANT_Dynamic or JVM_CONSTANT_InvokeDynamic
void BytecodePrinter::print_dynamic(int cp_index, outputStream* st) {
  ConstantPool* constants = method()->constants();
  constantTag tag = constants->tag_at(cp_index);

  switch (tag.value()) {
  case JVM_CONSTANT_Dynamic:
  case JVM_CONSTANT_InvokeDynamic:
    break;
  default:
    st->print_cr(" bad tag=%d at %d", tag.value(), cp_index);
    return;
  }

  int bsm = constants->bootstrap_method_ref_index_at(cp_index);
  st->print(" bsm=%d", bsm);

  Symbol* name = constants->uncached_name_ref_at(cp_index);
  Symbol* signature = constants->uncached_signature_ref_at(cp_index);
  const char* sep = tag.is_dynamic_constant() ? ":" : "";
  st->print_cr(" %d <%s%s%s>", cp_index, name->as_C_string(), sep, signature->as_C_string());
}

void BytecodePrinter::print_invokedynamic(int indy_index, int cp_index, outputStream* st) {
  print_dynamic(cp_index, st);

  if (ClassPrinter::has_mode(_flags, ClassPrinter::PRINT_DYNAMIC)) {
    print_bsm(cp_index, st);

    if (is_linked()) {
      ResolvedIndyEntry* indy_entry = constants()->resolved_indy_entry_at(indy_index);
      st->print("  ResolvedIndyEntry: ");
      indy_entry->print_on(st);
    }
  }
}

// cp_index: must be the cp_index of a JVM_CONSTANT_{Dynamic, DynamicInError, InvokeDynamic}
void BytecodePrinter::print_bsm(int cp_index, outputStream* st) {
  assert(constants()->tag_at(cp_index).has_bootstrap(), "must be");
  int bsm = constants()->bootstrap_method_ref_index_at(cp_index);
  const char* ref_kind = "";
  switch (constants()->method_handle_ref_kind_at(bsm)) {
  case JVM_REF_getField         : ref_kind = "REF_getField"; break;
  case JVM_REF_getStatic        : ref_kind = "REF_getStatic"; break;
  case JVM_REF_putField         : ref_kind = "REF_putField"; break;
  case JVM_REF_putStatic        : ref_kind = "REF_putStatic"; break;
  case JVM_REF_invokeVirtual    : ref_kind = "REF_invokeVirtual"; break;
  case JVM_REF_invokeStatic     : ref_kind = "REF_invokeStatic"; break;
  case JVM_REF_invokeSpecial    : ref_kind = "REF_invokeSpecial"; break;
  case JVM_REF_newInvokeSpecial : ref_kind = "REF_newInvokeSpecial"; break;
  case JVM_REF_invokeInterface  : ref_kind = "REF_invokeInterface"; break;
  default                       : ShouldNotReachHere();
  }
  st->print("  BSM: %s", ref_kind);
  print_field_or_method(constants()->method_handle_index_at(bsm), st);
  int argc = constants()->bootstrap_argument_count_at(cp_index);
  st->print("  arguments[%d] = {", argc);
  if (argc > 0) {
    st->cr();
    for (int arg_i = 0; arg_i < argc; arg_i++) {
      int arg = constants()->bootstrap_argument_index_at(cp_index, arg_i);
      st->print("    ");
      print_constant(arg, st);
    }
  }
  st->print_cr("  }");
}

void BytecodePrinter::print_attributes(int bci, outputStream* st) {
  // Show attributes of pre-rewritten codes
  Bytecodes::Code code = Bytecodes::java_code(raw_code());
  // If the code doesn't have any fields there's nothing to print.
  // note this is ==1 because the tableswitch and lookupswitch are
  // zero size (for some reason) and we want to print stuff out for them.
  // Also skip this if we're truncating bytecode output
  if (TraceBytecodesTruncated || Bytecodes::length_for(code) == 1) {
    st->cr();
    return;
  }

  switch(code) {
    // Java specific bytecodes only matter.
    case Bytecodes::_bipush:
      st->print_cr(" " INT32_FORMAT, get_byte());
      break;
    case Bytecodes::_sipush:
      st->print_cr(" " INT32_FORMAT, get_short());
      break;
    case Bytecodes::_ldc:
      {
        int cp_index;
        if (Bytecodes::uses_cp_cache(raw_code())) {
          assert(is_linked(), "fast ldc bytecode must be in linked classes");
          int obj_index = get_index_u1();
          cp_index = constants()->object_to_cp_index(obj_index);
        } else {
          cp_index = get_index_u1();
        }
        print_constant(cp_index, st);
      }
      break;

    case Bytecodes::_ldc_w:
    case Bytecodes::_ldc2_w:
      {
        int cp_index;
        if (Bytecodes::uses_cp_cache(raw_code())) {
          assert(is_linked(), "fast ldc bytecode must be in linked classes");
          int obj_index = get_native_index_u2();
          cp_index = constants()->object_to_cp_index(obj_index);
        } else {
          cp_index = get_Java_index_u2();
        }
        print_constant(cp_index, st);
      }
      break;

    case Bytecodes::_iload:
    case Bytecodes::_lload:
    case Bytecodes::_fload:
    case Bytecodes::_dload:
    case Bytecodes::_aload:
    case Bytecodes::_istore:
    case Bytecodes::_lstore:
    case Bytecodes::_fstore:
    case Bytecodes::_dstore:
    case Bytecodes::_astore:
      st->print_cr(" #%d", get_index_special());
      break;

    case Bytecodes::_iinc:
      { int index = get_index_special();
        jint offset = is_wide() ? get_short(): get_byte();
        st->print_cr(" #%d " INT32_FORMAT, index, offset);
      }
      break;

    case Bytecodes::_newarray: {
        BasicType atype = (BasicType)get_index_u1();
        const char* str = type2name(atype);
        if (str == nullptr || is_reference_type(atype)) {
          assert(false, "Unidentified basic type");
        }
        st->print_cr(" %s", str);
      }
      break;
    case Bytecodes::_anewarray: {
        int klass_index = get_Java_index_u2();
        ConstantPool* constants = method()->constants();
        Symbol* name = constants->klass_name_at(klass_index);
        st->print_cr(" %s ", name->as_C_string());
      }
      break;
    case Bytecodes::_multianewarray: {
        int klass_index = get_Java_index_u2();
        int nof_dims = get_index_u1();
        ConstantPool* constants = method()->constants();
        Symbol* name = constants->klass_name_at(klass_index);
        st->print_cr(" %s %d", name->as_C_string(), nof_dims);
      }
      break;

    case Bytecodes::_ifeq:
    case Bytecodes::_ifnull:
    case Bytecodes::_iflt:
    case Bytecodes::_ifle:
    case Bytecodes::_ifne:
    case Bytecodes::_ifnonnull:
    case Bytecodes::_ifgt:
    case Bytecodes::_ifge:
    case Bytecodes::_if_icmpeq:
    case Bytecodes::_if_icmpne:
    case Bytecodes::_if_icmplt:
    case Bytecodes::_if_icmpgt:
    case Bytecodes::_if_icmple:
    case Bytecodes::_if_icmpge:
    case Bytecodes::_if_acmpeq:
    case Bytecodes::_if_acmpne:
    case Bytecodes::_goto:
    case Bytecodes::_jsr:
      st->print_cr(" %d", bci + get_short());
      break;

    case Bytecodes::_goto_w:
    case Bytecodes::_jsr_w:
      st->print_cr(" %d", bci + get_int());
      break;

    case Bytecodes::_ret: st->print_cr(" %d", get_index_special()); break;

    case Bytecodes::_tableswitch:
      { align();
        int  default_dest = bci + get_int();
        int  lo           = get_int();
        int  hi           = get_int();
        int  len          = hi - lo + 1;
        jint* dest        = NEW_RESOURCE_ARRAY(jint, len);
        for (int i = 0; i < len; i++) {
          dest[i] = bci + get_int();
        }
        st->print(" %d " INT32_FORMAT " " INT32_FORMAT " ",
                      default_dest, lo, hi);
        const char *comma = "";
        for (int ll = lo; ll <= hi; ll++) {
          int idx = ll - lo;
          st->print("%s %d:" INT32_FORMAT " (delta: %d)", comma, ll, dest[idx], dest[idx]-bci);
          comma = ",";
        }
        st->cr();
      }
      break;
    case Bytecodes::_lookupswitch:
      { align();
        int  default_dest = bci + get_int();
        int  len          = get_int();
        jint* key         = NEW_RESOURCE_ARRAY(jint, len);
        jint* dest        = NEW_RESOURCE_ARRAY(jint, len);
        for (int i = 0; i < len; i++) {
          key [i] = get_int();
          dest[i] = bci + get_int();
        };
        st->print(" %d %d ", default_dest, len);
        const char *comma = "";
        for (int ll = 0; ll < len; ll++)  {
          st->print("%s " INT32_FORMAT ":" INT32_FORMAT, comma, key[ll], dest[ll]);
          comma = ",";
        }
        st->cr();
      }
      break;

    case Bytecodes::_putstatic:
    case Bytecodes::_getstatic:
    case Bytecodes::_putfield:
    case Bytecodes::_getfield:
      {
        int cp_index;
        if (is_linked()) {
          int field_index = get_native_index_u2();
          cp_index = cpcache()->resolved_field_entry_at(field_index)->constant_pool_index();
        } else {
          cp_index = get_Java_index_u2();
        }
        print_field_or_method(cp_index, st);
      }
      break;

    case Bytecodes::_invokevirtual:
    case Bytecodes::_invokespecial:
    case Bytecodes::_invokestatic:
      {
        int cp_index;
        if (is_linked()) {
          int method_index = get_native_index_u2();
          ResolvedMethodEntry* method_entry = cpcache()->resolved_method_entry_at(method_index);
          cp_index = method_entry->constant_pool_index();
          print_field_or_method(cp_index, st);

          if (raw_code() == Bytecodes::_invokehandle &&
              ClassPrinter::has_mode(_flags, ClassPrinter::PRINT_METHOD_HANDLE)) {
            assert(is_linked(), "invokehandle is only in rewritten methods");
            method_entry->print_on(st);
            if (method_entry->has_appendix()) {
              st->print("  appendix: ");
              constants()->resolved_reference_from_method(method_index)->print_on(st);
            }
          }
        } else {
          cp_index = get_Java_index_u2();
          print_field_or_method(cp_index, st);
        }
      }
      break;

    case Bytecodes::_invokeinterface:
      {
        int cp_index;
        if (is_linked()) {
          int method_index = get_native_index_u2();
          cp_index = cpcache()->resolved_method_entry_at(method_index)->constant_pool_index();
        } else {
          cp_index = get_Java_index_u2();
        }
        int count = get_index_u1(); // TODO: this is not printed.
        get_byte();                 // ignore zero byte
        print_field_or_method(cp_index, st);
      }
      break;

    case Bytecodes::_invokedynamic:
      {
        int indy_index;
        int cp_index;
        if (is_linked()) {
          indy_index = get_native_index_u4();
          cp_index = constants()->resolved_indy_entry_at(indy_index)->constant_pool_index();
        } else {
          indy_index = -1;
          cp_index = get_Java_index_u2();
          get_byte();            // ignore zero byte
          get_byte();            // ignore zero byte
        }
        print_invokedynamic(indy_index, cp_index, st);
      }
      break;

    case Bytecodes::_new:
    case Bytecodes::_checkcast:
    case Bytecodes::_instanceof:
      { int i = get_Java_index_u2();
        ConstantPool* constants = method()->constants();
        Symbol* name = constants->klass_name_at(i);
        st->print_cr(" %d <%s>", i, name->as_C_string());
      }
      break;

    case Bytecodes::_wide:
      // length is zero not one, but printed with no more info.
      break;

    default:
      ShouldNotReachHere();
      break;
  }
}


void BytecodePrinter::bytecode_epilog(int bci, outputStream* st) {
  MethodData* mdo = method()->method_data();
  if (mdo != nullptr) {

    // Lock to read ProfileData, and ensure lock is not broken by a safepoint
    MutexLocker ml(mdo->extra_data_lock(), Mutex::_no_safepoint_check_flag);

    ProfileData* data = mdo->bci_to_data(bci);
    if (data != nullptr) {
      st->print("  %d ", mdo->dp_to_di(data->dp()));
      st->fill_to(7);
      data->print_data_on(st, mdo);
    }
  }
}
