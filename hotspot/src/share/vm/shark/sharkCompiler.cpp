/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2008, 2009, 2010 Red Hat, Inc.
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
#include "incls/_sharkCompiler.cpp.incl"

#include <fnmatch.h>

using namespace llvm;

#if SHARK_LLVM_VERSION >= 27
namespace {
  cl::opt<std::string>
  MCPU("mcpu");

  cl::list<std::string>
  MAttrs("mattr",
         cl::CommaSeparated);
}
#endif

SharkCompiler::SharkCompiler()
  : AbstractCompiler() {
  // Create the lock to protect the memory manager and execution engine
  _execution_engine_lock = new Monitor(Mutex::leaf, "SharkExecutionEngineLock");
  MutexLocker locker(execution_engine_lock());

  // Make LLVM safe for multithreading
  if (!llvm_start_multithreaded())
    fatal("llvm_start_multithreaded() failed");

  // Initialize the native target
  InitializeNativeTarget();

  // Create the two contexts which we'll use
  _normal_context = new SharkContext("normal");
  _native_context = new SharkContext("native");

  // Create the memory manager
  _memory_manager = new SharkMemoryManager();

#if SHARK_LLVM_VERSION >= 27
  // Finetune LLVM for the current host CPU.
  StringMap<bool> Features;
  bool gotCpuFeatures = llvm::sys::getHostCPUFeatures(Features);
  std::string cpu("-mcpu=" + llvm::sys::getHostCPUName());

  std::vector<const char*> args;
  args.push_back(""); // program name
  args.push_back(cpu.c_str());

  std::string mattr("-mattr=");
  if(gotCpuFeatures){
    for(StringMap<bool>::iterator I = Features.begin(),
      E = Features.end(); I != E; ++I){
      if(I->second){
        std::string attr(I->first());
        mattr+="+"+attr+",";
      }
    }
    args.push_back(mattr.c_str());
  }

  args.push_back(0);  // terminator
  cl::ParseCommandLineOptions(args.size() - 1, (char **) &args[0]);

  // Create the JIT
  std::string ErrorMsg;

  EngineBuilder builder(_normal_context->module());
  builder.setMCPU(MCPU);
  builder.setMAttrs(MAttrs);
  builder.setJITMemoryManager(memory_manager());
  builder.setEngineKind(EngineKind::JIT);
  builder.setErrorStr(&ErrorMsg);
  _execution_engine = builder.create();

  if (!execution_engine()) {
    if (!ErrorMsg.empty())
      printf("Error while creating Shark JIT: %s\n",ErrorMsg.c_str());
    else
      printf("Unknown error while creating Shark JIT\n");
    exit(1);
  }

  execution_engine()->addModule(
    _native_context->module());
#else
  _execution_engine = ExecutionEngine::createJIT(
    _normal_context->module_provider(),
    NULL, memory_manager(), CodeGenOpt::Default);
  execution_engine()->addModuleProvider(
    _native_context->module_provider());
#endif

  // All done
  mark_initialized();
}

void SharkCompiler::initialize() {
  ShouldNotCallThis();
}

void SharkCompiler::compile_method(ciEnv*    env,
                                   ciMethod* target,
                                   int       entry_bci) {
  assert(is_initialized(), "should be");
  ResourceMark rm;
  const char *name = methodname(
    target->holder()->name()->as_utf8(), target->name()->as_utf8());

  // Do the typeflow analysis
  ciTypeFlow *flow;
  if (entry_bci == InvocationEntryBci)
    flow = target->get_flow_analysis();
  else
    flow = target->get_osr_flow_analysis(entry_bci);
  if (flow->failing())
    return;
  if (SharkPrintTypeflowOf != NULL) {
    if (!fnmatch(SharkPrintTypeflowOf, name, 0))
      flow->print_on(tty);
  }

  // Create the recorders
  Arena arena;
  env->set_oop_recorder(new OopRecorder(&arena));
  OopMapSet oopmaps;
  env->set_debug_info(new DebugInformationRecorder(env->oop_recorder()));
  env->debug_info()->set_oopmaps(&oopmaps);
  env->set_dependencies(new Dependencies(env));

  // Create the code buffer and builder
  CodeBuffer hscb("Shark", 256 * K, 64 * K);
  hscb.initialize_oop_recorder(env->oop_recorder());
  MacroAssembler *masm = new MacroAssembler(&hscb);
  SharkCodeBuffer cb(masm);
  SharkBuilder builder(&cb);

  // Emit the entry point
  SharkEntry *entry = (SharkEntry *) cb.malloc(sizeof(SharkEntry));

  // Build the LLVM IR for the method
  Function *function = SharkFunction::build(env, &builder, flow, name);

  // Generate native code.  It's unpleasant that we have to drop into
  // the VM to do this -- it blocks safepoints -- but I can't see any
  // other way to handle the locking.
  {
    ThreadInVMfromNative tiv(JavaThread::current());
    generate_native_code(entry, function, name);
  }

  // Install the method into the VM
  CodeOffsets offsets;
  offsets.set_value(CodeOffsets::Deopt, 0);
  offsets.set_value(CodeOffsets::Exceptions, 0);
  offsets.set_value(CodeOffsets::Verified_Entry,
                    target->is_static() ? 0 : wordSize);

  ExceptionHandlerTable handler_table;
  ImplicitExceptionTable inc_table;

  env->register_method(target,
                       entry_bci,
                       &offsets,
                       0,
                       &hscb,
                       0,
                       &oopmaps,
                       &handler_table,
                       &inc_table,
                       this,
                       env->comp_level(),
                       false,
                       false);
}

nmethod* SharkCompiler::generate_native_wrapper(MacroAssembler* masm,
                                                methodHandle    target,
                                                BasicType*      arg_types,
                                                BasicType       return_type) {
  assert(is_initialized(), "should be");
  ResourceMark rm;
  const char *name = methodname(
    target->klass_name()->as_utf8(), target->name()->as_utf8());

  // Create the code buffer and builder
  SharkCodeBuffer cb(masm);
  SharkBuilder builder(&cb);

  // Emit the entry point
  SharkEntry *entry = (SharkEntry *) cb.malloc(sizeof(SharkEntry));

  // Build the LLVM IR for the method
  SharkNativeWrapper *wrapper = SharkNativeWrapper::build(
    &builder, target, name, arg_types, return_type);

  // Generate native code
  generate_native_code(entry, wrapper->function(), name);

  // Return the nmethod for installation in the VM
  return nmethod::new_native_nmethod(target,
                                     masm->code(),
                                     0,
                                     0,
                                     wrapper->frame_size(),
                                     wrapper->receiver_offset(),
                                     wrapper->lock_offset(),
                                     wrapper->oop_maps());
}

void SharkCompiler::generate_native_code(SharkEntry* entry,
                                         Function*   function,
                                         const char* name) {
  // Print the LLVM bitcode, if requested
  if (SharkPrintBitcodeOf != NULL) {
    if (!fnmatch(SharkPrintBitcodeOf, name, 0))
      function->dump();
  }

  // Compile to native code
  address code = NULL;
  context()->add_function(function);
  {
    MutexLocker locker(execution_engine_lock());
    free_queued_methods();

    if (SharkPrintAsmOf != NULL) {
#if SHARK_LLVM_VERSION >= 27
#ifndef NDEBUG
      if (!fnmatch(SharkPrintAsmOf, name, 0)) {
        llvm::SetCurrentDebugType(X86_ONLY("x86-emitter") NOT_X86("jit"));
        llvm::DebugFlag = true;
      }
      else {
        llvm::SetCurrentDebugType("");
        llvm::DebugFlag = false;
      }
#endif // !NDEBUG
#else
      // NB you need to patch LLVM with http://tinyurl.com/yf3baln for this
      std::vector<const char*> args;
      args.push_back(""); // program name
      if (!fnmatch(SharkPrintAsmOf, name, 0))
        args.push_back("-debug-only=x86-emitter");
      else
        args.push_back("-debug-only=none");
      args.push_back(0);  // terminator
      cl::ParseCommandLineOptions(args.size() - 1, (char **) &args[0]);
#endif // SHARK_LLVM_VERSION
    }
    memory_manager()->set_entry_for_function(function, entry);
    code = (address) execution_engine()->getPointerToFunction(function);
  }
  entry->set_entry_point(code);
  entry->set_function(function);
  entry->set_context(context());
  address code_start = entry->code_start();
  address code_limit = entry->code_limit();

  // Register generated code for profiling, etc
  if (JvmtiExport::should_post_dynamic_code_generated())
    JvmtiExport::post_dynamic_code_generated(name, code_start, code_limit);

  // Print debug information, if requested
  if (SharkTraceInstalls) {
    tty->print_cr(
      " [%p-%p): %s (%d bytes code)",
      code_start, code_limit, name, code_limit - code_start);
  }
}

void SharkCompiler::free_compiled_method(address code) {
  // This method may only be called when the VM is at a safepoint.
  // All _thread_in_vm threads will be waiting for the safepoint to
  // finish with the exception of the VM thread, so we can consider
  // ourself the owner of the execution engine lock even though we
  // can't actually acquire it at this time.
  assert(Thread::current()->is_VM_thread(), "must be called by VM thread");
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");

  SharkEntry *entry = (SharkEntry *) code;
  entry->context()->push_to_free_queue(entry->function());
}

void SharkCompiler::free_queued_methods() {
  // The free queue is protected by the execution engine lock
  assert(execution_engine_lock()->owned_by_self(), "should be");

  while (true) {
    Function *function = context()->pop_from_free_queue();
    if (function == NULL)
      break;

    execution_engine()->freeMachineCodeForFunction(function);
    function->eraseFromParent();
  }
}

const char* SharkCompiler::methodname(const char* klass, const char* method) {
  char *buf = NEW_RESOURCE_ARRAY(char, strlen(klass) + 2 + strlen(method) + 1);

  char *dst = buf;
  for (const char *c = klass; *c; c++) {
    if (*c == '/')
      *(dst++) = '.';
    else
      *(dst++) = *c;
  }
  *(dst++) = ':';
  *(dst++) = ':';
  for (const char *c = method; *c; c++) {
    *(dst++) = *c;
  }
  *(dst++) = '\0';
  return buf;
}
