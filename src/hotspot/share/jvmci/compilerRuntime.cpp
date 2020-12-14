/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "aot/aotLoader.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/vmSymbols.hpp"
#include "compiler/compilationPolicy.hpp"
#include "interpreter/linkResolver.hpp"
#include "jvmci/compilerRuntime.hpp"
#include "oops/cpCache.inline.hpp"
#include "oops/klass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/vframe.inline.hpp"
#include "utilities/sizes.hpp"

// Resolve and allocate String
JRT_BLOCK_ENTRY(void, CompilerRuntime::resolve_string_by_symbol(JavaThread *thread, void* string_result, const char* name))
  JRT_BLOCK
    oop str = *(oop*)string_result; // Is it resolved already?
    if (str == NULL) { // Do resolution
      // First 2 bytes of name contains length (number of bytes).
      int len = Bytes::get_Java_u2((address)name);
      name += 2;
      TempNewSymbol sym = SymbolTable::new_symbol(name, len);
      str = StringTable::intern(sym, CHECK);
      assert(java_lang_String::is_instance(str), "must be string");
      *(oop*)string_result = str; // Store result
    }
    assert(str != NULL, "Should be allocated!");
    thread->set_vm_result(str);
  JRT_BLOCK_END
JRT_END



Klass* CompilerRuntime::resolve_klass_helper(JavaThread *thread, const char* name, int len, TRAPS) {
  ResourceMark rm(THREAD);
  // last java frame on stack (which includes native call frames)
  RegisterMap cbl_map(thread, false);
  // Skip stub
  frame caller_frame = thread->last_frame().sender(&cbl_map);
  CodeBlob* caller_cb = caller_frame.cb();
  guarantee(caller_cb != NULL && caller_cb->is_compiled(), "must be called from compiled method");
  CompiledMethod* caller_nm = caller_cb->as_compiled_method_or_null();
  methodHandle caller(THREAD, caller_nm->method());

  // Use class loader of aot method.
  Handle loader(THREAD, caller->method_holder()->class_loader());
  Handle protection_domain(THREAD, caller->method_holder()->protection_domain());

  TempNewSymbol sym = SymbolTable::new_symbol(name, len);
  if (sym != NULL && Signature::has_envelope(sym)) {
    // Ignore wrapping L and ;
    sym = Signature::strip_envelope(sym);
  }
  if (sym == NULL) {
    return NULL;
  }
  Klass* k = SystemDictionary::resolve_or_fail(sym, loader, protection_domain, true, CHECK_NULL);

  return k;
}

// Resolve Klass
JRT_BLOCK_ENTRY(Klass*, CompilerRuntime::resolve_klass_by_symbol(JavaThread *thread, Klass** klass_result, const char* name))
  Klass* k = NULL;
  JRT_BLOCK
    k = *klass_result; // Is it resolved already?
    if (k == NULL) { // Do resolution
      // First 2 bytes of name contains length (number of bytes).
      int len = Bytes::get_Java_u2((address)name);
      name += 2;
      k = CompilerRuntime::resolve_klass_helper(thread, name, len, CHECK_NULL);
      *klass_result = k; // Store result
    }
  JRT_BLOCK_END
  assert(k != NULL, " Should be loaded!");
  return k;
JRT_END


Method* CompilerRuntime::resolve_method_helper(Klass* klass, const char* method_name, int method_name_len,
                                                               const char* signature_name, int signature_name_len) {
  Method* m = NULL;
  TempNewSymbol name_symbol = SymbolTable::probe(method_name, method_name_len);
  TempNewSymbol signature_symbol = SymbolTable::probe(signature_name, signature_name_len);
  if (name_symbol != NULL && signature_symbol != NULL) {
    if (name_symbol == vmSymbols::object_initializer_name() ||
        name_symbol == vmSymbols::class_initializer_name()) {
      // Never search superclasses for constructors
      if (klass->is_instance_klass()) {
        m = InstanceKlass::cast(klass)->find_method(name_symbol, signature_symbol);
      }
    } else {
      m = klass->lookup_method(name_symbol, signature_symbol);
      if (m == NULL && klass->is_instance_klass()) {
        m = InstanceKlass::cast(klass)->lookup_method_in_ordered_interfaces(name_symbol, signature_symbol);
      }
    }
  }
  return m;
}

JRT_BLOCK_ENTRY(void, CompilerRuntime::resolve_dynamic_invoke(JavaThread *thread, oop* appendix_result))
  JRT_BLOCK
  {
    ResourceMark rm(THREAD);
    vframeStream vfst(thread, true);  // Do not skip and javaCalls
    assert(!vfst.at_end(), "Java frame must exist");
    methodHandle caller(THREAD, vfst.method());
    InstanceKlass* holder = caller->method_holder();
    int bci = vfst.bci();
    Bytecode_invoke bytecode(caller, bci);
    int index = bytecode.index();

    // Make sure it's resolved first
    CallInfo callInfo;
    constantPoolHandle cp(THREAD, holder->constants());
    ConstantPoolCacheEntry* cp_cache_entry = cp->cache()->entry_at(cp->decode_cpcache_index(index, true));
    Bytecodes::Code invoke_code = bytecode.invoke_code();
    if (!cp_cache_entry->is_resolved(invoke_code)) {
        LinkResolver::resolve_invoke(callInfo, Handle(), cp, index, invoke_code, CHECK);
        if (bytecode.is_invokedynamic()) {
            cp_cache_entry->set_dynamic_call(cp, callInfo);
        } else {
            cp_cache_entry->set_method_handle(cp, callInfo);
        }
        vmassert(cp_cache_entry->is_resolved(invoke_code), "sanity");
    }

    Handle appendix(THREAD, cp_cache_entry->appendix_if_resolved(cp));
    Klass *appendix_klass = appendix.is_null() ? NULL : appendix->klass();

    methodHandle adapter_method(THREAD, cp_cache_entry->f1_as_method());
    InstanceKlass *adapter_klass = adapter_method->method_holder();

    if (appendix_klass != NULL && appendix_klass->is_instance_klass()) {
        vmassert(InstanceKlass::cast(appendix_klass)->is_initialized(), "sanity");
    }
    if (!adapter_klass->is_initialized()) {
        // Force initialization of adapter class
        adapter_klass->initialize(CHECK);
        // Double-check that it was really initialized,
        // because we could be doing a recursive call
        // from inside <clinit>.
    }

    int cpi = cp_cache_entry->constant_pool_index();
    if (!AOTLoader::reconcile_dynamic_invoke(holder, cpi, adapter_method(),
      appendix_klass)) {
      return;
    }

    *appendix_result = appendix();
    thread->set_vm_result(appendix());
  }
  JRT_BLOCK_END
JRT_END

JRT_BLOCK_ENTRY(MethodCounters*, CompilerRuntime::resolve_method_by_symbol_and_load_counters(JavaThread *thread, MethodCounters** counters_result, Klass* klass, const char* data))
  MethodCounters* c = *counters_result; // Is it resolved already?
  JRT_BLOCK
     if (c == NULL) { // Do resolution
       // Get method name and its length
       int method_name_len = Bytes::get_Java_u2((address)data);
       data += sizeof(u2);
       const char* method_name = data;
       data += method_name_len;

       // Get signature and its length
       int signature_name_len = Bytes::get_Java_u2((address)data);
       data += sizeof(u2);
       const char* signature_name = data;

       assert(klass != NULL, "Klass parameter must not be null");
       Method* m = resolve_method_helper(klass, method_name, method_name_len, signature_name, signature_name_len);
       assert(m != NULL, "Method must resolve successfully");

       // Create method counters immediately to avoid check at runtime.
       c = m->get_method_counters(thread);
       if (c == NULL) {
         THROW_MSG_NULL(vmSymbols::java_lang_OutOfMemoryError(), "Cannot allocate method counters");
       }

       *counters_result = c;
     }
  JRT_BLOCK_END
  return c;
JRT_END

// Resolve and initialize Klass
JRT_BLOCK_ENTRY(Klass*, CompilerRuntime::initialize_klass_by_symbol(JavaThread *thread, Klass** klass_result, const char* name))
  Klass* k = NULL;
  JRT_BLOCK
    k = klass_result[0]; // Is it initialized already?
    if (k == NULL) { // Do initialized
      k = klass_result[1]; // Is it resolved already?
      if (k == NULL) { // Do resolution
        // First 2 bytes of name contains length (number of bytes).
        int len = Bytes::get_Java_u2((address)name);
        const char *cname = name + 2;
        k = CompilerRuntime::resolve_klass_helper(thread,  cname, len, CHECK_NULL);
        klass_result[1] = k; // Store resolved result
      }
      Klass* k0 = klass_result[0]; // Is it initialized already?
      if (k0 == NULL && k != NULL && k->is_instance_klass()) {
        // Force initialization of instance class
        InstanceKlass::cast(k)->initialize(CHECK_NULL);
        // Double-check that it was really initialized,
        // because we could be doing a recursive call
        // from inside <clinit>.
        if (InstanceKlass::cast(k)->is_initialized()) {
          klass_result[0] = k; // Store initialized result
        }
      }
    }
  JRT_BLOCK_END
  assert(k != NULL, " Should be loaded!");
  return k;
JRT_END


JRT_BLOCK_ENTRY(void, CompilerRuntime::invocation_event(JavaThread *thread, MethodCounters* counters))
  if (!TieredCompilation) {
    // Ignore the event if tiered is off
    return;
  }
  JRT_BLOCK
    methodHandle mh(THREAD, counters->method());
    RegisterMap map(thread, false);
    // Compute the enclosing method
    frame fr = thread->last_frame().sender(&map);
    CompiledMethod* cm = fr.cb()->as_compiled_method_or_null();
    assert(cm != NULL && cm->is_compiled(), "Sanity check");
    methodHandle emh(THREAD, cm->method());
    CompilationPolicy::policy()->event(emh, mh, InvocationEntryBci, InvocationEntryBci, CompLevel_aot, cm, THREAD);
  JRT_BLOCK_END
JRT_END

JRT_BLOCK_ENTRY(void, CompilerRuntime::backedge_event(JavaThread *thread, MethodCounters* counters, int branch_bci, int target_bci))
  if (!TieredCompilation) {
    // Ignore the event if tiered is off
    return;
  }
  assert(branch_bci != InvocationEntryBci && target_bci != InvocationEntryBci, "Wrong bci");
  assert(target_bci <= branch_bci, "Expected a back edge");
  JRT_BLOCK
    methodHandle mh(THREAD, counters->method());
    RegisterMap map(thread, false);

    // Compute the enclosing method
    frame fr = thread->last_frame().sender(&map);
    CompiledMethod* cm = fr.cb()->as_compiled_method_or_null();
    assert(cm != NULL && cm->is_compiled(), "Sanity check");
    methodHandle emh(THREAD, cm->method());
    nmethod* osr_nm = CompilationPolicy::policy()->event(emh, mh, branch_bci, target_bci, CompLevel_aot, cm, THREAD);
    if (osr_nm != NULL) {
      Deoptimization::deoptimize_frame(thread, fr.id());
    }
  JRT_BLOCK_END
JRT_END
