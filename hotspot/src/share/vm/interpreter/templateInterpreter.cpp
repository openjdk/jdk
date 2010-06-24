/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_templateInterpreter.cpp.incl"

#ifndef CC_INTERP

# define __ _masm->

void TemplateInterpreter::initialize() {
  if (_code != NULL) return;
  // assertions
  assert((int)Bytecodes::number_of_codes <= (int)DispatchTable::length,
         "dispatch table too small");

  AbstractInterpreter::initialize();

  TemplateTable::initialize();

  // generate interpreter
  { ResourceMark rm;
    TraceTime timer("Interpreter generation", TraceStartupTime);
    int code_size = InterpreterCodeSize;
    NOT_PRODUCT(code_size *= 4;)  // debug uses extra interpreter code space
    _code = new StubQueue(new InterpreterCodeletInterface, code_size, NULL,
                          "Interpreter");
    InterpreterGenerator g(_code);
    if (PrintInterpreter) print();
  }

  // initialize dispatch table
  _active_table = _normal_table;
}

//------------------------------------------------------------------------------------------------------------------------
// Implementation of EntryPoint

EntryPoint::EntryPoint() {
  assert(number_of_states == 9, "check the code below");
  _entry[btos] = NULL;
  _entry[ctos] = NULL;
  _entry[stos] = NULL;
  _entry[atos] = NULL;
  _entry[itos] = NULL;
  _entry[ltos] = NULL;
  _entry[ftos] = NULL;
  _entry[dtos] = NULL;
  _entry[vtos] = NULL;
}


EntryPoint::EntryPoint(address bentry, address centry, address sentry, address aentry, address ientry, address lentry, address fentry, address dentry, address ventry) {
  assert(number_of_states == 9, "check the code below");
  _entry[btos] = bentry;
  _entry[ctos] = centry;
  _entry[stos] = sentry;
  _entry[atos] = aentry;
  _entry[itos] = ientry;
  _entry[ltos] = lentry;
  _entry[ftos] = fentry;
  _entry[dtos] = dentry;
  _entry[vtos] = ventry;
}


void EntryPoint::set_entry(TosState state, address entry) {
  assert(0 <= state && state < number_of_states, "state out of bounds");
  _entry[state] = entry;
}


address EntryPoint::entry(TosState state) const {
  assert(0 <= state && state < number_of_states, "state out of bounds");
  return _entry[state];
}


void EntryPoint::print() {
  tty->print("[");
  for (int i = 0; i < number_of_states; i++) {
    if (i > 0) tty->print(", ");
    tty->print(INTPTR_FORMAT, _entry[i]);
  }
  tty->print("]");
}


bool EntryPoint::operator == (const EntryPoint& y) {
  int i = number_of_states;
  while (i-- > 0) {
    if (_entry[i] != y._entry[i]) return false;
  }
  return true;
}


//------------------------------------------------------------------------------------------------------------------------
// Implementation of DispatchTable

EntryPoint DispatchTable::entry(int i) const {
  assert(0 <= i && i < length, "index out of bounds");
  return
    EntryPoint(
      _table[btos][i],
      _table[ctos][i],
      _table[stos][i],
      _table[atos][i],
      _table[itos][i],
      _table[ltos][i],
      _table[ftos][i],
      _table[dtos][i],
      _table[vtos][i]
    );
}


void DispatchTable::set_entry(int i, EntryPoint& entry) {
  assert(0 <= i && i < length, "index out of bounds");
  assert(number_of_states == 9, "check the code below");
  _table[btos][i] = entry.entry(btos);
  _table[ctos][i] = entry.entry(ctos);
  _table[stos][i] = entry.entry(stos);
  _table[atos][i] = entry.entry(atos);
  _table[itos][i] = entry.entry(itos);
  _table[ltos][i] = entry.entry(ltos);
  _table[ftos][i] = entry.entry(ftos);
  _table[dtos][i] = entry.entry(dtos);
  _table[vtos][i] = entry.entry(vtos);
}


bool DispatchTable::operator == (DispatchTable& y) {
  int i = length;
  while (i-- > 0) {
    EntryPoint t = y.entry(i); // for compiler compatibility (BugId 4150096)
    if (!(entry(i) == t)) return false;
  }
  return true;
}

address    TemplateInterpreter::_remove_activation_entry                    = NULL;
address    TemplateInterpreter::_remove_activation_preserving_args_entry    = NULL;


address    TemplateInterpreter::_throw_ArrayIndexOutOfBoundsException_entry = NULL;
address    TemplateInterpreter::_throw_ArrayStoreException_entry            = NULL;
address    TemplateInterpreter::_throw_ArithmeticException_entry            = NULL;
address    TemplateInterpreter::_throw_ClassCastException_entry             = NULL;
address    TemplateInterpreter::_throw_WrongMethodType_entry                = NULL;
address    TemplateInterpreter::_throw_NullPointerException_entry           = NULL;
address    TemplateInterpreter::_throw_StackOverflowError_entry             = NULL;
address    TemplateInterpreter::_throw_exception_entry                      = NULL;

#ifndef PRODUCT
EntryPoint TemplateInterpreter::_trace_code;
#endif // !PRODUCT
EntryPoint TemplateInterpreter::_return_entry[TemplateInterpreter::number_of_return_entries];
EntryPoint TemplateInterpreter::_earlyret_entry;
EntryPoint TemplateInterpreter::_deopt_entry [TemplateInterpreter::number_of_deopt_entries ];
EntryPoint TemplateInterpreter::_continuation_entry;
EntryPoint TemplateInterpreter::_safept_entry;

address    TemplateInterpreter::_return_3_addrs_by_index[TemplateInterpreter::number_of_return_addrs];
address    TemplateInterpreter::_return_5_addrs_by_index[TemplateInterpreter::number_of_return_addrs];

DispatchTable TemplateInterpreter::_active_table;
DispatchTable TemplateInterpreter::_normal_table;
DispatchTable TemplateInterpreter::_safept_table;
address    TemplateInterpreter::_wentry_point[DispatchTable::length];

TemplateInterpreterGenerator::TemplateInterpreterGenerator(StubQueue* _code): AbstractInterpreterGenerator(_code) {
  _unimplemented_bytecode    = NULL;
  _illegal_bytecode_sequence = NULL;
}

static const BasicType types[Interpreter::number_of_result_handlers] = {
  T_BOOLEAN,
  T_CHAR   ,
  T_BYTE   ,
  T_SHORT  ,
  T_INT    ,
  T_LONG   ,
  T_VOID   ,
  T_FLOAT  ,
  T_DOUBLE ,
  T_OBJECT
};

void TemplateInterpreterGenerator::generate_all() {
  AbstractInterpreterGenerator::generate_all();

  { CodeletMark cm(_masm, "error exits");
    _unimplemented_bytecode    = generate_error_exit("unimplemented bytecode");
    _illegal_bytecode_sequence = generate_error_exit("illegal bytecode sequence - method not verified");
  }

#ifndef PRODUCT
  if (TraceBytecodes) {
    CodeletMark cm(_masm, "bytecode tracing support");
    Interpreter::_trace_code =
      EntryPoint(
        generate_trace_code(btos),
        generate_trace_code(ctos),
        generate_trace_code(stos),
        generate_trace_code(atos),
        generate_trace_code(itos),
        generate_trace_code(ltos),
        generate_trace_code(ftos),
        generate_trace_code(dtos),
        generate_trace_code(vtos)
      );
  }
#endif // !PRODUCT

  { CodeletMark cm(_masm, "return entry points");
    for (int i = 0; i < Interpreter::number_of_return_entries; i++) {
      Interpreter::_return_entry[i] =
        EntryPoint(
          generate_return_entry_for(itos, i),
          generate_return_entry_for(itos, i),
          generate_return_entry_for(itos, i),
          generate_return_entry_for(atos, i),
          generate_return_entry_for(itos, i),
          generate_return_entry_for(ltos, i),
          generate_return_entry_for(ftos, i),
          generate_return_entry_for(dtos, i),
          generate_return_entry_for(vtos, i)
        );
    }
  }

  { CodeletMark cm(_masm, "earlyret entry points");
    Interpreter::_earlyret_entry =
      EntryPoint(
        generate_earlyret_entry_for(btos),
        generate_earlyret_entry_for(ctos),
        generate_earlyret_entry_for(stos),
        generate_earlyret_entry_for(atos),
        generate_earlyret_entry_for(itos),
        generate_earlyret_entry_for(ltos),
        generate_earlyret_entry_for(ftos),
        generate_earlyret_entry_for(dtos),
        generate_earlyret_entry_for(vtos)
      );
  }

  { CodeletMark cm(_masm, "deoptimization entry points");
    for (int i = 0; i < Interpreter::number_of_deopt_entries; i++) {
      Interpreter::_deopt_entry[i] =
        EntryPoint(
          generate_deopt_entry_for(itos, i),
          generate_deopt_entry_for(itos, i),
          generate_deopt_entry_for(itos, i),
          generate_deopt_entry_for(atos, i),
          generate_deopt_entry_for(itos, i),
          generate_deopt_entry_for(ltos, i),
          generate_deopt_entry_for(ftos, i),
          generate_deopt_entry_for(dtos, i),
          generate_deopt_entry_for(vtos, i)
        );
    }
  }

  { CodeletMark cm(_masm, "result handlers for native calls");
    // The various result converter stublets.
    int is_generated[Interpreter::number_of_result_handlers];
    memset(is_generated, 0, sizeof(is_generated));

    for (int i = 0; i < Interpreter::number_of_result_handlers; i++) {
      BasicType type = types[i];
      if (!is_generated[Interpreter::BasicType_as_index(type)]++) {
        Interpreter::_native_abi_to_tosca[Interpreter::BasicType_as_index(type)] = generate_result_handler_for(type);
      }
    }
  }

  for (int j = 0; j < number_of_states; j++) {
    const TosState states[] = {btos, ctos, stos, itos, ltos, ftos, dtos, atos, vtos};
    int index = Interpreter::TosState_as_index(states[j]);
    Interpreter::_return_3_addrs_by_index[index] = Interpreter::return_entry(states[j], 3);
    Interpreter::_return_5_addrs_by_index[index] = Interpreter::return_entry(states[j], 5);
  }

  { CodeletMark cm(_masm, "continuation entry points");
    Interpreter::_continuation_entry =
      EntryPoint(
        generate_continuation_for(btos),
        generate_continuation_for(ctos),
        generate_continuation_for(stos),
        generate_continuation_for(atos),
        generate_continuation_for(itos),
        generate_continuation_for(ltos),
        generate_continuation_for(ftos),
        generate_continuation_for(dtos),
        generate_continuation_for(vtos)
      );
  }

  { CodeletMark cm(_masm, "safepoint entry points");
    Interpreter::_safept_entry =
      EntryPoint(
        generate_safept_entry_for(btos, CAST_FROM_FN_PTR(address, InterpreterRuntime::at_safepoint)),
        generate_safept_entry_for(ctos, CAST_FROM_FN_PTR(address, InterpreterRuntime::at_safepoint)),
        generate_safept_entry_for(stos, CAST_FROM_FN_PTR(address, InterpreterRuntime::at_safepoint)),
        generate_safept_entry_for(atos, CAST_FROM_FN_PTR(address, InterpreterRuntime::at_safepoint)),
        generate_safept_entry_for(itos, CAST_FROM_FN_PTR(address, InterpreterRuntime::at_safepoint)),
        generate_safept_entry_for(ltos, CAST_FROM_FN_PTR(address, InterpreterRuntime::at_safepoint)),
        generate_safept_entry_for(ftos, CAST_FROM_FN_PTR(address, InterpreterRuntime::at_safepoint)),
        generate_safept_entry_for(dtos, CAST_FROM_FN_PTR(address, InterpreterRuntime::at_safepoint)),
        generate_safept_entry_for(vtos, CAST_FROM_FN_PTR(address, InterpreterRuntime::at_safepoint))
      );
  }

  { CodeletMark cm(_masm, "exception handling");
    // (Note: this is not safepoint safe because thread may return to compiled code)
    generate_throw_exception();
  }

  { CodeletMark cm(_masm, "throw exception entrypoints");
    Interpreter::_throw_ArrayIndexOutOfBoundsException_entry = generate_ArrayIndexOutOfBounds_handler("java/lang/ArrayIndexOutOfBoundsException");
    Interpreter::_throw_ArrayStoreException_entry            = generate_klass_exception_handler("java/lang/ArrayStoreException"                 );
    Interpreter::_throw_ArithmeticException_entry            = generate_exception_handler("java/lang/ArithmeticException"           , "/ by zero");
    Interpreter::_throw_ClassCastException_entry             = generate_ClassCastException_handler();
    Interpreter::_throw_WrongMethodType_entry                = generate_WrongMethodType_handler();
    Interpreter::_throw_NullPointerException_entry           = generate_exception_handler("java/lang/NullPointerException"          , NULL       );
    Interpreter::_throw_StackOverflowError_entry             = generate_StackOverflowError_handler();
  }



#define method_entry(kind)                                                                    \
  { CodeletMark cm(_masm, "method entry point (kind = " #kind ")");                    \
    Interpreter::_entry_table[Interpreter::kind] = generate_method_entry(Interpreter::kind);  \
  }

  // all non-native method kinds
  method_entry(zerolocals)
  method_entry(zerolocals_synchronized)
  method_entry(empty)
  method_entry(accessor)
  method_entry(abstract)
  method_entry(method_handle)
  method_entry(java_lang_math_sin  )
  method_entry(java_lang_math_cos  )
  method_entry(java_lang_math_tan  )
  method_entry(java_lang_math_abs  )
  method_entry(java_lang_math_sqrt )
  method_entry(java_lang_math_log  )
  method_entry(java_lang_math_log10)

  // all native method kinds (must be one contiguous block)
  Interpreter::_native_entry_begin = Interpreter::code()->code_end();
  method_entry(native)
  method_entry(native_synchronized)
  Interpreter::_native_entry_end = Interpreter::code()->code_end();

#undef method_entry

  // Bytecodes
  set_entry_points_for_all_bytes();
  set_safepoints_for_all_bytes();
}

//------------------------------------------------------------------------------------------------------------------------

address TemplateInterpreterGenerator::generate_error_exit(const char* msg) {
  address entry = __ pc();
  __ stop(msg);
  return entry;
}


//------------------------------------------------------------------------------------------------------------------------

void TemplateInterpreterGenerator::set_entry_points_for_all_bytes() {
  for (int i = 0; i < DispatchTable::length; i++) {
    Bytecodes::Code code = (Bytecodes::Code)i;
    if (Bytecodes::is_defined(code)) {
      set_entry_points(code);
    } else {
      set_unimplemented(i);
    }
  }
}


void TemplateInterpreterGenerator::set_safepoints_for_all_bytes() {
  for (int i = 0; i < DispatchTable::length; i++) {
    Bytecodes::Code code = (Bytecodes::Code)i;
    if (Bytecodes::is_defined(code)) Interpreter::_safept_table.set_entry(code, Interpreter::_safept_entry);
  }
}


void TemplateInterpreterGenerator::set_unimplemented(int i) {
  address e = _unimplemented_bytecode;
  EntryPoint entry(e, e, e, e, e, e, e, e, e);
  Interpreter::_normal_table.set_entry(i, entry);
  Interpreter::_wentry_point[i] = _unimplemented_bytecode;
}


void TemplateInterpreterGenerator::set_entry_points(Bytecodes::Code code) {
  CodeletMark cm(_masm, Bytecodes::name(code), code);
  // initialize entry points
  assert(_unimplemented_bytecode    != NULL, "should have been generated before");
  assert(_illegal_bytecode_sequence != NULL, "should have been generated before");
  address bep = _illegal_bytecode_sequence;
  address cep = _illegal_bytecode_sequence;
  address sep = _illegal_bytecode_sequence;
  address aep = _illegal_bytecode_sequence;
  address iep = _illegal_bytecode_sequence;
  address lep = _illegal_bytecode_sequence;
  address fep = _illegal_bytecode_sequence;
  address dep = _illegal_bytecode_sequence;
  address vep = _unimplemented_bytecode;
  address wep = _unimplemented_bytecode;
  // code for short & wide version of bytecode
  if (Bytecodes::is_defined(code)) {
    Template* t = TemplateTable::template_for(code);
    assert(t->is_valid(), "just checking");
    set_short_entry_points(t, bep, cep, sep, aep, iep, lep, fep, dep, vep);
  }
  if (Bytecodes::wide_is_defined(code)) {
    Template* t = TemplateTable::template_for_wide(code);
    assert(t->is_valid(), "just checking");
    set_wide_entry_point(t, wep);
  }
  // set entry points
  EntryPoint entry(bep, cep, sep, aep, iep, lep, fep, dep, vep);
  Interpreter::_normal_table.set_entry(code, entry);
  Interpreter::_wentry_point[code] = wep;
}


void TemplateInterpreterGenerator::set_wide_entry_point(Template* t, address& wep) {
  assert(t->is_valid(), "template must exist");
  assert(t->tos_in() == vtos, "only vtos tos_in supported for wide instructions");
  wep = __ pc(); generate_and_dispatch(t);
}


void TemplateInterpreterGenerator::set_short_entry_points(Template* t, address& bep, address& cep, address& sep, address& aep, address& iep, address& lep, address& fep, address& dep, address& vep) {
  assert(t->is_valid(), "template must exist");
  switch (t->tos_in()) {
    case btos:
    case ctos:
    case stos:
      ShouldNotReachHere();  // btos/ctos/stos should use itos.
      break;
    case atos: vep = __ pc(); __ pop(atos); aep = __ pc(); generate_and_dispatch(t); break;
    case itos: vep = __ pc(); __ pop(itos); iep = __ pc(); generate_and_dispatch(t); break;
    case ltos: vep = __ pc(); __ pop(ltos); lep = __ pc(); generate_and_dispatch(t); break;
    case ftos: vep = __ pc(); __ pop(ftos); fep = __ pc(); generate_and_dispatch(t); break;
    case dtos: vep = __ pc(); __ pop(dtos); dep = __ pc(); generate_and_dispatch(t); break;
    case vtos: set_vtos_entry_points(t, bep, cep, sep, aep, iep, lep, fep, dep, vep);     break;
    default  : ShouldNotReachHere();                                                 break;
  }
}


//------------------------------------------------------------------------------------------------------------------------

void TemplateInterpreterGenerator::generate_and_dispatch(Template* t, TosState tos_out) {
  if (PrintBytecodeHistogram)                                    histogram_bytecode(t);
#ifndef PRODUCT
  // debugging code
  if (CountBytecodes || TraceBytecodes || StopInterpreterAt > 0) count_bytecode();
  if (PrintBytecodePairHistogram)                                histogram_bytecode_pair(t);
  if (TraceBytecodes)                                            trace_bytecode(t);
  if (StopInterpreterAt > 0)                                     stop_interpreter_at();
  __ verify_FPU(1, t->tos_in());
#endif // !PRODUCT
  int step;
  if (!t->does_dispatch()) {
    step = t->is_wide() ? Bytecodes::wide_length_for(t->bytecode()) : Bytecodes::length_for(t->bytecode());
    if (tos_out == ilgl) tos_out = t->tos_out();
    // compute bytecode size
    assert(step > 0, "just checkin'");
    // setup stuff for dispatching next bytecode
    if (ProfileInterpreter && VerifyDataPointer
        && methodDataOopDesc::bytecode_has_profile(t->bytecode())) {
      __ verify_method_data_pointer();
    }
    __ dispatch_prolog(tos_out, step);
  }
  // generate template
  t->generate(_masm);
  // advance
  if (t->does_dispatch()) {
#ifdef ASSERT
    // make sure execution doesn't go beyond this point if code is broken
    __ should_not_reach_here();
#endif // ASSERT
  } else {
    // dispatch to next bytecode
    __ dispatch_epilog(tos_out, step);
  }
}

//------------------------------------------------------------------------------------------------------------------------
// Entry points

address TemplateInterpreter::return_entry(TosState state, int length) {
  guarantee(0 <= length && length < Interpreter::number_of_return_entries, "illegal length");
  return _return_entry[length].entry(state);
}


address TemplateInterpreter::deopt_entry(TosState state, int length) {
  guarantee(0 <= length && length < Interpreter::number_of_deopt_entries, "illegal length");
  return _deopt_entry[length].entry(state);
}

//------------------------------------------------------------------------------------------------------------------------
// Suport for invokes

int TemplateInterpreter::TosState_as_index(TosState state) {
  assert( state < number_of_states , "Invalid state in TosState_as_index");
  assert(0 <= (int)state && (int)state < TemplateInterpreter::number_of_return_addrs, "index out of bounds");
  return (int)state;
}


//------------------------------------------------------------------------------------------------------------------------
// Safepoint suppport

static inline void copy_table(address* from, address* to, int size) {
  // Copy non-overlapping tables. The copy has to occur word wise for MT safety.
  while (size-- > 0) *to++ = *from++;
}

void TemplateInterpreter::notice_safepoints() {
  if (!_notice_safepoints) {
    // switch to safepoint dispatch table
    _notice_safepoints = true;
    copy_table((address*)&_safept_table, (address*)&_active_table, sizeof(_active_table) / sizeof(address));
  }
}

// switch from the dispatch table which notices safepoints back to the
// normal dispatch table.  So that we can notice single stepping points,
// keep the safepoint dispatch table if we are single stepping in JVMTI.
// Note that the should_post_single_step test is exactly as fast as the
// JvmtiExport::_enabled test and covers both cases.
void TemplateInterpreter::ignore_safepoints() {
  if (_notice_safepoints) {
    if (!JvmtiExport::should_post_single_step()) {
      // switch to normal dispatch table
      _notice_safepoints = false;
      copy_table((address*)&_normal_table, (address*)&_active_table, sizeof(_active_table) / sizeof(address));
    }
  }
}

//------------------------------------------------------------------------------------------------------------------------
// Deoptimization support

// If deoptimization happens, this function returns the point of next bytecode to continue execution
address TemplateInterpreter::deopt_continue_after_entry(methodOop method, address bcp, int callee_parameters, bool is_top_frame) {
  return AbstractInterpreter::deopt_continue_after_entry(method, bcp, callee_parameters, is_top_frame);
}

// If deoptimization happens, this function returns the point where the interpreter reexecutes
// the bytecode.
// Note: Bytecodes::_athrow (C1 only) and Bytecodes::_return are the special cases
//       that do not return "Interpreter::deopt_entry(vtos, 0)"
address TemplateInterpreter::deopt_reexecute_entry(methodOop method, address bcp) {
  assert(method->contains(bcp), "just checkin'");
  Bytecodes::Code code   = Bytecodes::java_code_at(bcp);
  if (code == Bytecodes::_return) {
    // This is used for deopt during registration of finalizers
    // during Object.<init>.  We simply need to resume execution at
    // the standard return vtos bytecode to pop the frame normally.
    // reexecuting the real bytecode would cause double registration
    // of the finalizable object.
    return _normal_table.entry(Bytecodes::_return).entry(vtos);
  } else {
    return AbstractInterpreter::deopt_reexecute_entry(method, bcp);
  }
}

// If deoptimization happens, the interpreter should reexecute this bytecode.
// This function mainly helps the compilers to set up the reexecute bit.
bool TemplateInterpreter::bytecode_should_reexecute(Bytecodes::Code code) {
  if (code == Bytecodes::_return) {
    //Yes, we consider Bytecodes::_return as a special case of reexecution
    return true;
  } else {
    return AbstractInterpreter::bytecode_should_reexecute(code);
  }
}

#endif // !CC_INTERP
