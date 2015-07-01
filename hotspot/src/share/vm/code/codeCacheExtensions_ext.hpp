/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CODE_CODE_CACHE_EXTENSIONS_EXT_HPP
#define SHARE_VM_CODE_CODE_CACHE_EXTENSIONS_EXT_HPP

#include "utilities/macros.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"
#include "interpreter/bytecodes.hpp"

class AdapterHandlerEntry;
class CodeBlob;
class CodeBuffer;
class InterpreterMacroAssembler;
class Template;

// All the methods defined here are placeholders for possible extensions.

class CodeCacheExtensions: AllStatic {
  friend class CodeCacheDumper;

public:
  // init both code saving and loading
  // Must be called very early, before any code is generated.
  static void initialize() {}

  // Check whether the generated interpreter will be saved.
  static bool saving_generated_interpreter() { return false; }

  // Check whether a pregenerated interpreter is used.
  static bool use_pregenerated_interpreter() { return false; }

  // Placeholder for additional VM initialization code
  static void complete_step(CodeCacheExtensionsSteps::Step phase) {}

  // Return false for newly generated code, on systems where it is not
  // executable.
  static bool is_executable(void *pc) { return true; }

  // Return whether dynamically generated code can be executable
  static bool support_dynamic_code() { return true; }

  // Skip new code generation when known to be useless.
  static bool skip_code_generation() { return false; }

  // Skip stubs used only for compiled code support.
  static bool skip_compiler_support() { return false; }

  // Ignore UseFastSignatureHandlers when returning false
  static bool support_fast_signature_handlers() { return true; }

  /////////////////////////
  // Handle generated code:
  // - allow newly generated code to be shared
  // - allow pregenerated code to be used in place of the newly generated one
  //   (modifying pc).
  // - support remapping when doing both save and load
  // 'remap' can be set to false if the addresses handled are not referenced
  // from code generated later.

  // Associate a name to a generated codelet and possibly modify the pc
  // Note: use instead the specialized versions when they exist:
  // - handle_generated_blob for CodeBlob
  // - handle_generated_handler for SignatureHandlers
  // See also the optimized calls below that handle several PCs at once.
  static void handle_generated_pc(address &pc, const char *name) {}

  // Adds a safe definition of the codelet, for codelets used right after
  // generation (else we would need to immediately stop the JVM and convert
  // the generated code to executable format before being able to go further).
  static void handle_generated_pc(address &pc, const char *name, address default_entry) {}

  // Special cases

  // Special case for CodeBlobs, which may require blob specific actions.
  static CodeBlob* handle_generated_blob(CodeBlob* blob, const char *name = NULL) { return blob; }

  // Special case for Signature Handlers.
  static void handle_generated_handler(address &handler_start, const char *name, address handler_end) {}

  // Support for generating different variants of the interpreter
  // that can be dynamically selected after reload.
  //
  // - init_interpreter_assembler allows to configure the assembler for
  //   the current variant
  //
  // - needs_other_interpreter_variant returns true as long as other
  //   variants are needed.
  //
  // - skip_template_interpreter_entries returns true if new entries
  //   need not be generated for this masm setup and this bytecode
  //
  // - completed_template_interpreter_entries is called after new
  //   entries have been generated and installed, for any non skipped
  //   bytecode.
  static void init_interpreter_assembler(InterpreterMacroAssembler* masm, CodeBuffer* code) {}
  static bool needs_other_interpreter_variant() { return false; }
  static bool skip_template_interpreter_entries(Bytecodes::Code code) { return false; }
  static void completed_template_interpreter_entries(InterpreterMacroAssembler* masm, Bytecodes::Code code) {}

  // Code size optimization. May optimize the requested size.
  static void size_blob(const char* name, int *updatable_size) {}

  // ergonomics
  static void set_ergonomics_flags() {}
};

#endif // SHARE_VM_CODE_CODE_CACHE_EXTENSIONS_EXT_HPP
