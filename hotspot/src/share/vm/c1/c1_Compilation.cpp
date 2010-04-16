/*
 * Copyright 1999-2010 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_c1_Compilation.cpp.incl"


typedef enum {
  _t_compile,
  _t_setup,
  _t_optimizeIR,
  _t_buildIR,
  _t_emit_lir,
  _t_linearScan,
  _t_lirGeneration,
  _t_lir_schedule,
  _t_codeemit,
  _t_codeinstall,
  max_phase_timers
} TimerName;

static const char * timer_name[] = {
  "compile",
  "setup",
  "optimizeIR",
  "buildIR",
  "emit_lir",
  "linearScan",
  "lirGeneration",
  "lir_schedule",
  "codeemit",
  "codeinstall"
};

static elapsedTimer timers[max_phase_timers];
static int totalInstructionNodes = 0;

class PhaseTraceTime: public TraceTime {
 private:
  JavaThread* _thread;

 public:
  PhaseTraceTime(TimerName timer):
    TraceTime("", &timers[timer], CITime || CITimeEach, Verbose) {
  }
};

Arena* Compilation::_arena = NULL;
Compilation* Compilation::_compilation = NULL;

// Implementation of Compilation


#ifndef PRODUCT

void Compilation::maybe_print_current_instruction() {
  if (_current_instruction != NULL && _last_instruction_printed != _current_instruction) {
    _last_instruction_printed = _current_instruction;
    _current_instruction->print_line();
  }
}
#endif // PRODUCT


DebugInformationRecorder* Compilation::debug_info_recorder() const {
  return _env->debug_info();
}


Dependencies* Compilation::dependency_recorder() const {
  return _env->dependencies();
}


void Compilation::initialize() {
  // Use an oop recorder bound to the CI environment.
  // (The default oop recorder is ignorant of the CI.)
  OopRecorder* ooprec = new OopRecorder(_env->arena());
  _env->set_oop_recorder(ooprec);
  _env->set_debug_info(new DebugInformationRecorder(ooprec));
  debug_info_recorder()->set_oopmaps(new OopMapSet());
  _env->set_dependencies(new Dependencies(_env));
}


void Compilation::build_hir() {
  CHECK_BAILOUT();

  // setup ir
  _hir = new IR(this, method(), osr_bci());
  if (!_hir->is_valid()) {
    bailout("invalid parsing");
    return;
  }

#ifndef PRODUCT
  if (PrintCFGToFile) {
    CFGPrinter::print_cfg(_hir, "After Generation of HIR", true, false);
  }
#endif

#ifndef PRODUCT
  if (PrintCFG || PrintCFG0) { tty->print_cr("CFG after parsing"); _hir->print(true); }
  if (PrintIR  || PrintIR0 ) { tty->print_cr("IR after parsing"); _hir->print(false); }
#endif

  _hir->verify();

  if (UseC1Optimizations) {
    NEEDS_CLEANUP
    // optimization
    PhaseTraceTime timeit(_t_optimizeIR);

    _hir->optimize();
  }

  _hir->verify();

  _hir->split_critical_edges();

#ifndef PRODUCT
  if (PrintCFG || PrintCFG1) { tty->print_cr("CFG after optimizations"); _hir->print(true); }
  if (PrintIR  || PrintIR1 ) { tty->print_cr("IR after optimizations"); _hir->print(false); }
#endif

  _hir->verify();

  // compute block ordering for code generation
  // the control flow must not be changed from here on
  _hir->compute_code();

  if (UseGlobalValueNumbering) {
    ResourceMark rm;
    int instructions = Instruction::number_of_instructions();
    GlobalValueNumbering gvn(_hir);
    assert(instructions == Instruction::number_of_instructions(),
           "shouldn't have created an instructions");
  }

  // compute use counts after global value numbering
  _hir->compute_use_counts();

#ifndef PRODUCT
  if (PrintCFG || PrintCFG2) { tty->print_cr("CFG before code generation"); _hir->code()->print(true); }
  if (PrintIR  || PrintIR2 ) { tty->print_cr("IR before code generation"); _hir->code()->print(false, true); }
#endif

  _hir->verify();
}


void Compilation::emit_lir() {
  CHECK_BAILOUT();

  LIRGenerator gen(this, method());
  {
    PhaseTraceTime timeit(_t_lirGeneration);
    hir()->iterate_linear_scan_order(&gen);
  }

  CHECK_BAILOUT();

  {
    PhaseTraceTime timeit(_t_linearScan);

    LinearScan* allocator = new LinearScan(hir(), &gen, frame_map());
    set_allocator(allocator);
    // Assign physical registers to LIR operands using a linear scan algorithm.
    allocator->do_linear_scan();
    CHECK_BAILOUT();

    _max_spills = allocator->max_spills();
  }

  if (BailoutAfterLIR) {
    if (PrintLIR && !bailed_out()) {
      print_LIR(hir()->code());
    }
    bailout("Bailing out because of -XX:+BailoutAfterLIR");
  }
}


void Compilation::emit_code_epilog(LIR_Assembler* assembler) {
  CHECK_BAILOUT();

  CodeOffsets* code_offsets = assembler->offsets();

  // generate code or slow cases
  assembler->emit_slow_case_stubs();
  CHECK_BAILOUT();

  // generate exception adapters
  assembler->emit_exception_entries(exception_info_list());
  CHECK_BAILOUT();

  // Generate code for exception handler.
  code_offsets->set_value(CodeOffsets::Exceptions, assembler->emit_exception_handler());
  CHECK_BAILOUT();

  // Generate code for deopt handler.
  code_offsets->set_value(CodeOffsets::Deopt, assembler->emit_deopt_handler());
  CHECK_BAILOUT();

  // Generate code for MethodHandle deopt handler.  We can use the
  // same code as for the normal deopt handler, we just need a
  // different entry point address.
  code_offsets->set_value(CodeOffsets::DeoptMH, assembler->emit_deopt_handler());
  CHECK_BAILOUT();

  // Emit the handler to remove the activation from the stack and
  // dispatch to the caller.
  offsets()->set_value(CodeOffsets::UnwindHandler, assembler->emit_unwind_handler());

  // done
  masm()->flush();
}


int Compilation::emit_code_body() {
  // emit code
  Runtime1::setup_code_buffer(code(), allocator()->num_calls());
  code()->initialize_oop_recorder(env()->oop_recorder());

  _masm = new C1_MacroAssembler(code());
  _masm->set_oop_recorder(env()->oop_recorder());

  LIR_Assembler lir_asm(this);

  lir_asm.emit_code(hir()->code());
  CHECK_BAILOUT_(0);

  emit_code_epilog(&lir_asm);
  CHECK_BAILOUT_(0);

  generate_exception_handler_table();

#ifndef PRODUCT
  if (PrintExceptionHandlers && Verbose) {
    exception_handler_table()->print();
  }
#endif /* PRODUCT */

  return frame_map()->framesize();
}


int Compilation::compile_java_method() {
  assert(!method()->is_native(), "should not reach here");

  if (BailoutOnExceptionHandlers) {
    if (method()->has_exception_handlers()) {
      bailout("linear scan can't handle exception handlers");
    }
  }

  CHECK_BAILOUT_(no_frame_size);

  {
    PhaseTraceTime timeit(_t_buildIR);
  build_hir();
  }
  if (BailoutAfterHIR) {
    BAILOUT_("Bailing out because of -XX:+BailoutAfterHIR", no_frame_size);
  }


  {
    PhaseTraceTime timeit(_t_emit_lir);

    _frame_map = new FrameMap(method(), hir()->number_of_locks(), MAX2(4, hir()->max_stack()));
    emit_lir();
  }
  CHECK_BAILOUT_(no_frame_size);

  {
    PhaseTraceTime timeit(_t_codeemit);
    return emit_code_body();
  }
}

void Compilation::install_code(int frame_size) {
  // frame_size is in 32-bit words so adjust it intptr_t words
  assert(frame_size == frame_map()->framesize(), "must match");
  assert(in_bytes(frame_map()->framesize_in_bytes()) % sizeof(intptr_t) == 0, "must be at least pointer aligned");
  _env->register_method(
    method(),
    osr_bci(),
    &_offsets,
    in_bytes(_frame_map->sp_offset_for_orig_pc()),
    code(),
    in_bytes(frame_map()->framesize_in_bytes()) / sizeof(intptr_t),
    debug_info_recorder()->_oopmaps,
    exception_handler_table(),
    implicit_exception_table(),
    compiler(),
    _env->comp_level(),
    needs_debug_information(),
    has_unsafe_access()
  );
}


void Compilation::compile_method() {
  // setup compilation
  initialize();

  if (!method()->can_be_compiled()) {
    // Prevent race condition 6328518.
    // This can happen if the method is obsolete or breakpointed.
    bailout("Bailing out because method is not compilable");
    return;
  }

  if (_env->jvmti_can_hotswap_or_post_breakpoint()) {
    // We can assert evol_method because method->can_be_compiled is true.
    dependency_recorder()->assert_evol_method(method());
  }

  if (method()->break_at_execute()) {
    BREAKPOINT;
  }

#ifndef PRODUCT
  if (PrintCFGToFile) {
    CFGPrinter::print_compilation(this);
  }
#endif

  // compile method
  int frame_size = compile_java_method();

  // bailout if method couldn't be compiled
  // Note: make sure we mark the method as not compilable!
  CHECK_BAILOUT();

  if (InstallMethods) {
    // install code
    PhaseTraceTime timeit(_t_codeinstall);
    install_code(frame_size);
  }
  totalInstructionNodes += Instruction::number_of_instructions();
}


void Compilation::generate_exception_handler_table() {
  // Generate an ExceptionHandlerTable from the exception handler
  // information accumulated during the compilation.
  ExceptionInfoList* info_list = exception_info_list();

  if (info_list->length() == 0) {
    return;
  }

  // allocate some arrays for use by the collection code.
  const int num_handlers = 5;
  GrowableArray<intptr_t>* bcis = new GrowableArray<intptr_t>(num_handlers);
  GrowableArray<intptr_t>* scope_depths = new GrowableArray<intptr_t>(num_handlers);
  GrowableArray<intptr_t>* pcos = new GrowableArray<intptr_t>(num_handlers);

  for (int i = 0; i < info_list->length(); i++) {
    ExceptionInfo* info = info_list->at(i);
    XHandlers* handlers = info->exception_handlers();

    // empty the arrays
    bcis->trunc_to(0);
    scope_depths->trunc_to(0);
    pcos->trunc_to(0);

    for (int i = 0; i < handlers->length(); i++) {
      XHandler* handler = handlers->handler_at(i);
      assert(handler->entry_pco() != -1, "must have been generated");

      int e = bcis->find(handler->handler_bci());
      if (e >= 0 && scope_depths->at(e) == handler->scope_count()) {
        // two different handlers are declared to dispatch to the same
        // catch bci.  During parsing we created edges for each
        // handler but we really only need one.  The exception handler
        // table will also get unhappy if we try to declare both since
        // it's nonsensical.  Just skip this handler.
        continue;
      }

      bcis->append(handler->handler_bci());
      if (handler->handler_bci() == -1) {
        // insert a wildcard handler at scope depth 0 so that the
        // exception lookup logic with find it.
        scope_depths->append(0);
      } else {
        scope_depths->append(handler->scope_count());
    }
      pcos->append(handler->entry_pco());

      // stop processing once we hit a catch any
      if (handler->is_catch_all()) {
        assert(i == handlers->length() - 1, "catch all must be last handler");
  }
    }
    exception_handler_table()->add_subtable(info->pco(), bcis, scope_depths, pcos);
  }
}


Compilation::Compilation(AbstractCompiler* compiler, ciEnv* env, ciMethod* method, int osr_bci)
: _compiler(compiler)
, _env(env)
, _method(method)
, _osr_bci(osr_bci)
, _hir(NULL)
, _max_spills(-1)
, _frame_map(NULL)
, _masm(NULL)
, _has_exception_handlers(false)
, _has_fpu_code(true)   // pessimistic assumption
, _has_unsafe_access(false)
, _bailout_msg(NULL)
, _exception_info_list(NULL)
, _allocator(NULL)
, _code(Runtime1::get_buffer_blob()->instructions_begin(),
        Runtime1::get_buffer_blob()->instructions_size())
, _current_instruction(NULL)
#ifndef PRODUCT
, _last_instruction_printed(NULL)
#endif // PRODUCT
{
  PhaseTraceTime timeit(_t_compile);

  assert(_arena == NULL, "shouldn't only one instance of Compilation in existence at a time");
  _arena = Thread::current()->resource_area();
  _compilation = this;
  _needs_debug_information = _env->jvmti_can_examine_or_deopt_anywhere() ||
                               JavaMonitorsInStackTrace || AlwaysEmitDebugInfo || DeoptimizeALot;
  _exception_info_list = new ExceptionInfoList();
  _implicit_exception_table.set_size(0);
  compile_method();
}

Compilation::~Compilation() {
  _arena = NULL;
  _compilation = NULL;
}


void Compilation::add_exception_handlers_for_pco(int pco, XHandlers* exception_handlers) {
#ifndef PRODUCT
  if (PrintExceptionHandlers && Verbose) {
    tty->print_cr("  added exception scope for pco %d", pco);
  }
#endif
  // Note: we do not have program counters for these exception handlers yet
  exception_info_list()->push(new ExceptionInfo(pco, exception_handlers));
}


void Compilation::notice_inlined_method(ciMethod* method) {
  _env->notice_inlined_method(method);
}


void Compilation::bailout(const char* msg) {
  assert(msg != NULL, "bailout message must exist");
  if (!bailed_out()) {
    // keep first bailout message
    if (PrintBailouts) tty->print_cr("compilation bailout: %s", msg);
    _bailout_msg = msg;
  }
}


void Compilation::print_timers() {
  // tty->print_cr("    Native methods         : %6.3f s, Average : %2.3f", CompileBroker::_t_native_compilation.seconds(), CompileBroker::_t_native_compilation.seconds() / CompileBroker::_total_native_compile_count);
  float total = timers[_t_setup].seconds() + timers[_t_buildIR].seconds() + timers[_t_emit_lir].seconds() + timers[_t_lir_schedule].seconds() + timers[_t_codeemit].seconds() + timers[_t_codeinstall].seconds();


  tty->print_cr("    Detailed C1 Timings");
  tty->print_cr("       Setup time:        %6.3f s (%4.1f%%)",    timers[_t_setup].seconds(),           (timers[_t_setup].seconds() / total) * 100.0);
  tty->print_cr("       Build IR:          %6.3f s (%4.1f%%)",    timers[_t_buildIR].seconds(),         (timers[_t_buildIR].seconds() / total) * 100.0);
  tty->print_cr("         Optimize:           %6.3f s (%4.1f%%)", timers[_t_optimizeIR].seconds(),      (timers[_t_optimizeIR].seconds() / total) * 100.0);
  tty->print_cr("       Emit LIR:          %6.3f s (%4.1f%%)",    timers[_t_emit_lir].seconds(),        (timers[_t_emit_lir].seconds() / total) * 100.0);
  tty->print_cr("         LIR Gen:          %6.3f s (%4.1f%%)",   timers[_t_lirGeneration].seconds(), (timers[_t_lirGeneration].seconds() / total) * 100.0);
  tty->print_cr("         Linear Scan:      %6.3f s (%4.1f%%)",   timers[_t_linearScan].seconds(),    (timers[_t_linearScan].seconds() / total) * 100.0);
  NOT_PRODUCT(LinearScan::print_timers(timers[_t_linearScan].seconds()));
  tty->print_cr("       LIR Schedule:      %6.3f s (%4.1f%%)",    timers[_t_lir_schedule].seconds(),  (timers[_t_lir_schedule].seconds() / total) * 100.0);
  tty->print_cr("       Code Emission:     %6.3f s (%4.1f%%)",    timers[_t_codeemit].seconds(),        (timers[_t_codeemit].seconds() / total) * 100.0);
  tty->print_cr("       Code Installation: %6.3f s (%4.1f%%)",    timers[_t_codeinstall].seconds(),     (timers[_t_codeinstall].seconds() / total) * 100.0);
  tty->print_cr("       Instruction Nodes: %6d nodes",    totalInstructionNodes);

  NOT_PRODUCT(LinearScan::print_statistics());
}


#ifndef PRODUCT
void Compilation::compile_only_this_method() {
  ResourceMark rm;
  fileStream stream(fopen("c1_compile_only", "wt"));
  stream.print_cr("# c1 compile only directives");
  compile_only_this_scope(&stream, hir()->top_scope());
}


void Compilation::compile_only_this_scope(outputStream* st, IRScope* scope) {
  st->print("CompileOnly=");
  scope->method()->holder()->name()->print_symbol_on(st);
  st->print(".");
  scope->method()->name()->print_symbol_on(st);
  st->cr();
}


void Compilation::exclude_this_method() {
  fileStream stream(fopen(".hotspot_compiler", "at"));
  stream.print("exclude ");
  method()->holder()->name()->print_symbol_on(&stream);
  stream.print(" ");
  method()->name()->print_symbol_on(&stream);
  stream.cr();
  stream.cr();
}
#endif
