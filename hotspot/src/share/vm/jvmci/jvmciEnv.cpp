/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "jvmci/jvmciEnv.hpp"
#include "classfile/javaAssertions.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/scopeDesc.hpp"
#include "runtime/sweeper.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compileLog.hpp"
#include "compiler/compilerOracle.hpp"
#include "interpreter/linkResolver.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/oopFactory.hpp"
#include "memory/universe.inline.hpp"
#include "oops/methodData.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/init.hpp"
#include "runtime/reflection.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/dtrace.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "jvmci/jvmciJavaClasses.hpp"

JVMCIEnv::JVMCIEnv(CompileTask* task, int system_dictionary_modification_counter) {
  _task = task;
  _system_dictionary_modification_counter = system_dictionary_modification_counter;
  {
    // Get Jvmti capabilities under lock to get consistent values.
    MutexLocker mu(JvmtiThreadState_lock);
    _jvmti_can_hotswap_or_post_breakpoint = JvmtiExport::can_hotswap_or_post_breakpoint();
    _jvmti_can_access_local_variables     = JvmtiExport::can_access_local_variables();
    _jvmti_can_post_on_exceptions         = JvmtiExport::can_post_on_exceptions();
  }
}

// ------------------------------------------------------------------
// Note: the logic of this method should mirror the logic of
// constantPoolOopDesc::verify_constant_pool_resolve.
bool JVMCIEnv::check_klass_accessibility(KlassHandle accessing_klass, KlassHandle resolved_klass) {
  if (accessing_klass->is_objArray_klass()) {
    accessing_klass = ObjArrayKlass::cast(accessing_klass())->bottom_klass();
  }
  if (!accessing_klass->is_instance_klass()) {
    return true;
  }

  if (resolved_klass->is_objArray_klass()) {
    // Find the element klass, if this is an array.
    resolved_klass = ObjArrayKlass::cast(resolved_klass())->bottom_klass();
  }
  if (resolved_klass->is_instance_klass()) {
    return Reflection::verify_class_access(accessing_klass(), resolved_klass(), true);
  }
  return true;
}

// ------------------------------------------------------------------
KlassHandle JVMCIEnv::get_klass_by_name_impl(KlassHandle& accessing_klass,
                                          const constantPoolHandle& cpool,
                                          Symbol* sym,
                                          bool require_local) {
  JVMCI_EXCEPTION_CONTEXT;

  // Now we need to check the SystemDictionary
  if (sym->byte_at(0) == 'L' &&
    sym->byte_at(sym->utf8_length()-1) == ';') {
    // This is a name from a signature.  Strip off the trimmings.
    // Call recursive to keep scope of strippedsym.
    TempNewSymbol strippedsym = SymbolTable::new_symbol(sym->as_utf8()+1,
                    sym->utf8_length()-2,
                    CHECK_(KlassHandle()));
    return get_klass_by_name_impl(accessing_klass, cpool, strippedsym, require_local);
  }

  Handle loader(THREAD, (oop)NULL);
  Handle domain(THREAD, (oop)NULL);
  if (!accessing_klass.is_null()) {
    loader = Handle(THREAD, accessing_klass->class_loader());
    domain = Handle(THREAD, accessing_klass->protection_domain());
  }

  KlassHandle found_klass;
  {
    ttyUnlocker ttyul;  // release tty lock to avoid ordering problems
    MutexLocker ml(Compile_lock);
    Klass*  kls;
    if (!require_local) {
      kls = SystemDictionary::find_constrained_instance_or_array_klass(sym, loader, CHECK_(KlassHandle()));
    } else {
      kls = SystemDictionary::find_instance_or_array_klass(sym, loader, domain, CHECK_(KlassHandle()));
    }
    found_klass = KlassHandle(THREAD, kls);
  }

  // If we fail to find an array klass, look again for its element type.
  // The element type may be available either locally or via constraints.
  // In either case, if we can find the element type in the system dictionary,
  // we must build an array type around it.  The CI requires array klasses
  // to be loaded if their element klasses are loaded, except when memory
  // is exhausted.
  if (sym->byte_at(0) == '[' &&
      (sym->byte_at(1) == '[' || sym->byte_at(1) == 'L')) {
    // We have an unloaded array.
    // Build it on the fly if the element class exists.
    TempNewSymbol elem_sym = SymbolTable::new_symbol(sym->as_utf8()+1,
                                                 sym->utf8_length()-1,
                                                 CHECK_(KlassHandle()));

    // Get element Klass recursively.
    KlassHandle elem_klass =
      get_klass_by_name_impl(accessing_klass,
                             cpool,
                             elem_sym,
                             require_local);
    if (!elem_klass.is_null()) {
      // Now make an array for it
      return elem_klass->array_klass(CHECK_(KlassHandle()));
    }
  }

  if (found_klass.is_null() && !cpool.is_null() && cpool->has_preresolution()) {
    // Look inside the constant pool for pre-resolved class entries.
    for (int i = cpool->length() - 1; i >= 1; i--) {
      if (cpool->tag_at(i).is_klass()) {
        Klass*  kls = cpool->resolved_klass_at(i);
        if (kls->name() == sym) {
          return kls;
        }
      }
    }
  }

  return found_klass();
}

// ------------------------------------------------------------------
KlassHandle JVMCIEnv::get_klass_by_name(KlassHandle& accessing_klass,
                                  Symbol* klass_name,
                                  bool require_local) {
  ResourceMark rm;
  constantPoolHandle cpool;
  return get_klass_by_name_impl(accessing_klass,
                                                 cpool,
                                                 klass_name,
                                                 require_local);
}

// ------------------------------------------------------------------
// Implementation of get_klass_by_index.
KlassHandle JVMCIEnv::get_klass_by_index_impl(const constantPoolHandle& cpool,
                                        int index,
                                        bool& is_accessible,
                                        KlassHandle& accessor) {
  JVMCI_EXCEPTION_CONTEXT;
  KlassHandle klass (THREAD, ConstantPool::klass_at_if_loaded(cpool, index));
  Symbol* klass_name = NULL;
  if (klass.is_null()) {
    klass_name = cpool->klass_name_at(index);
  }

  if (klass.is_null()) {
    // Not found in constant pool.  Use the name to do the lookup.
    KlassHandle k = get_klass_by_name_impl(accessor,
                                        cpool,
                                        klass_name,
                                        false);
    // Calculate accessibility the hard way.
    if (k.is_null()) {
      is_accessible = false;
    } else if (k->class_loader() != accessor->class_loader() &&
               get_klass_by_name_impl(accessor, cpool, k->name(), true).is_null()) {
      // Loaded only remotely.  Not linked yet.
      is_accessible = false;
    } else {
      // Linked locally, and we must also check public/private, etc.
      is_accessible = check_klass_accessibility(accessor, k);
    }
    if (!is_accessible) {
      return KlassHandle();
    }
    return k;
  }

  // It is known to be accessible, since it was found in the constant pool.
  is_accessible = true;
  return klass;
}

// ------------------------------------------------------------------
// Get a klass from the constant pool.
KlassHandle JVMCIEnv::get_klass_by_index(const constantPoolHandle& cpool,
                                   int index,
                                   bool& is_accessible,
                                   KlassHandle& accessor) {
  ResourceMark rm;
  KlassHandle result = get_klass_by_index_impl(cpool, index, is_accessible, accessor);
  return result;
}

// ------------------------------------------------------------------
// Implementation of get_field_by_index.
//
// Implementation note: the results of field lookups are cached
// in the accessor klass.
void JVMCIEnv::get_field_by_index_impl(instanceKlassHandle& klass, fieldDescriptor& field_desc,
                                        int index) {
  JVMCI_EXCEPTION_CONTEXT;

  assert(klass->is_linked(), "must be linked before using its constant-pool");

  constantPoolHandle cpool(thread, klass->constants());

  // Get the field's name, signature, and type.
  Symbol* name  = cpool->name_ref_at(index);

  int nt_index = cpool->name_and_type_ref_index_at(index);
  int sig_index = cpool->signature_ref_index_at(nt_index);
  Symbol* signature = cpool->symbol_at(sig_index);

  // Get the field's declared holder.
  int holder_index = cpool->klass_ref_index_at(index);
  bool holder_is_accessible;
  KlassHandle declared_holder = get_klass_by_index(cpool, holder_index,
                                               holder_is_accessible,
                                               klass);

  // The declared holder of this field may not have been loaded.
  // Bail out with partial field information.
  if (!holder_is_accessible) {
    return;
  }


  // Perform the field lookup.
  Klass*  canonical_holder =
    InstanceKlass::cast(declared_holder())->find_field(name, signature, &field_desc);
  if (canonical_holder == NULL) {
    return;
  }

  assert(canonical_holder == field_desc.field_holder(), "just checking");
}

// ------------------------------------------------------------------
// Get a field by index from a klass's constant pool.
void JVMCIEnv::get_field_by_index(instanceKlassHandle& accessor, fieldDescriptor& fd, int index) {
  ResourceMark rm;
  return get_field_by_index_impl(accessor, fd, index);
}

// ------------------------------------------------------------------
// Perform an appropriate method lookup based on accessor, holder,
// name, signature, and bytecode.
methodHandle JVMCIEnv::lookup_method(instanceKlassHandle& h_accessor,
                               instanceKlassHandle& h_holder,
                               Symbol*       name,
                               Symbol*       sig,
                               Bytecodes::Code bc) {
  JVMCI_EXCEPTION_CONTEXT;
  LinkResolver::check_klass_accessability(h_accessor, h_holder, KILL_COMPILE_ON_FATAL_(NULL));
  methodHandle dest_method;
  LinkInfo link_info(h_holder, name, sig, h_accessor, /*check_access*/true);
  switch (bc) {
  case Bytecodes::_invokestatic:
    dest_method =
      LinkResolver::resolve_static_call_or_null(link_info);
    break;
  case Bytecodes::_invokespecial:
    dest_method =
      LinkResolver::resolve_special_call_or_null(link_info);
    break;
  case Bytecodes::_invokeinterface:
    dest_method =
      LinkResolver::linktime_resolve_interface_method_or_null(link_info);
    break;
  case Bytecodes::_invokevirtual:
    dest_method =
      LinkResolver::linktime_resolve_virtual_method_or_null(link_info);
    break;
  default: ShouldNotReachHere();
  }

  return dest_method;
}


// ------------------------------------------------------------------
methodHandle JVMCIEnv::get_method_by_index_impl(const constantPoolHandle& cpool,
                                          int index, Bytecodes::Code bc,
                                          instanceKlassHandle& accessor) {
  if (bc == Bytecodes::_invokedynamic) {
    ConstantPoolCacheEntry* cpce = cpool->invokedynamic_cp_cache_entry_at(index);
    bool is_resolved = !cpce->is_f1_null();
    if (is_resolved) {
      // Get the invoker Method* from the constant pool.
      // (The appendix argument, if any, will be noted in the method's signature.)
      Method* adapter = cpce->f1_as_method();
      return methodHandle(adapter);
    }

    return NULL;
  }

  int holder_index = cpool->klass_ref_index_at(index);
  bool holder_is_accessible;
  KlassHandle holder = get_klass_by_index_impl(cpool, holder_index, holder_is_accessible, accessor);

  // Get the method's name and signature.
  Symbol* name_sym = cpool->name_ref_at(index);
  Symbol* sig_sym  = cpool->signature_ref_at(index);

  if (cpool->has_preresolution()
      || (holder() == SystemDictionary::MethodHandle_klass() &&
          MethodHandles::is_signature_polymorphic_name(holder(), name_sym))) {
    // Short-circuit lookups for JSR 292-related call sites.
    // That is, do not rely only on name-based lookups, because they may fail
    // if the names are not resolvable in the boot class loader (7056328).
    switch (bc) {
    case Bytecodes::_invokevirtual:
    case Bytecodes::_invokeinterface:
    case Bytecodes::_invokespecial:
    case Bytecodes::_invokestatic:
      {
        Method* m = ConstantPool::method_at_if_loaded(cpool, index);
        if (m != NULL) {
          return m;
        }
      }
      break;
    }
  }

  if (holder_is_accessible) { // Our declared holder is loaded.
    instanceKlassHandle lookup = get_instance_klass_for_declared_method_holder(holder);
    methodHandle m = lookup_method(accessor, lookup, name_sym, sig_sym, bc);
    if (!m.is_null() &&
        (bc == Bytecodes::_invokestatic
         ?  InstanceKlass::cast(m->method_holder())->is_not_initialized()
         : !InstanceKlass::cast(m->method_holder())->is_loaded())) {
      m = NULL;
    }
    if (!m.is_null()) {
      // We found the method.
      return m;
    }
  }

  // Either the declared holder was not loaded, or the method could
  // not be found.

  return NULL;
}

// ------------------------------------------------------------------
instanceKlassHandle JVMCIEnv::get_instance_klass_for_declared_method_holder(KlassHandle& method_holder) {
  // For the case of <array>.clone(), the method holder can be an ArrayKlass*
  // instead of an InstanceKlass*.  For that case simply pretend that the
  // declared holder is Object.clone since that's where the call will bottom out.
  if (method_holder->is_instance_klass()) {
    return instanceKlassHandle(method_holder());
  } else if (method_holder->is_array_klass()) {
    return instanceKlassHandle(SystemDictionary::Object_klass());
  } else {
    ShouldNotReachHere();
  }
  return NULL;
}


// ------------------------------------------------------------------
methodHandle JVMCIEnv::get_method_by_index(const constantPoolHandle& cpool,
                                     int index, Bytecodes::Code bc,
                                     instanceKlassHandle& accessor) {
  ResourceMark rm;
  return get_method_by_index_impl(cpool, index, bc, accessor);
}

// ------------------------------------------------------------------
// Check for changes to the system dictionary during compilation
// class loads, evolution, breakpoints
JVMCIEnv::CodeInstallResult JVMCIEnv::check_for_system_dictionary_modification(Dependencies* dependencies, Handle compiled_code,
                                                                               JVMCIEnv* env, char** failure_detail) {
  // If JVMTI capabilities were enabled during compile, the compilation is invalidated.
  if (env != NULL) {
    if (!env->_jvmti_can_hotswap_or_post_breakpoint && JvmtiExport::can_hotswap_or_post_breakpoint()) {
      *failure_detail = (char*) "Hotswapping or breakpointing was enabled during compilation";
      return JVMCIEnv::dependencies_failed;
    }
  }

  // Dependencies must be checked when the system dictionary changes
  // or if we don't know whether it has changed (i.e., env == NULL).
  // In debug mode, always check dependencies.
  bool counter_changed = env != NULL && env->_system_dictionary_modification_counter != SystemDictionary::number_of_modifications();
  bool verify_deps = env == NULL || trueInDebug || JavaAssertions::enabled(SystemDictionary::HotSpotInstalledCode_klass()->name()->as_C_string(), true);
  if (!counter_changed && !verify_deps) {
    return JVMCIEnv::ok;
  }

  for (Dependencies::DepStream deps(dependencies); deps.next(); ) {
    Klass* witness = deps.check_dependency();
    if (witness != NULL) {
      // Use a fixed size buffer to prevent the string stream from
      // resizing in the context of an inner resource mark.
      char* buffer = NEW_RESOURCE_ARRAY(char, O_BUFLEN);
      stringStream st(buffer, O_BUFLEN);
      deps.print_dependency(witness, true, &st);
      *failure_detail = st.as_string();
      if (env == NULL || counter_changed) {
        return JVMCIEnv::dependencies_failed;
      } else {
        // The dependencies were invalid at the time of installation
        // without any intervening modification of the system
        // dictionary.  That means they were invalidly constructed.
        return JVMCIEnv::dependencies_invalid;
      }
    }
    if (LogCompilation) {
      deps.log_dependency();
    }
  }

  return JVMCIEnv::ok;
}

// ------------------------------------------------------------------
JVMCIEnv::CodeInstallResult JVMCIEnv::register_method(
                                methodHandle& method,
                                nmethod*& nm,
                                int entry_bci,
                                CodeOffsets* offsets,
                                int orig_pc_offset,
                                CodeBuffer* code_buffer,
                                int frame_words,
                                OopMapSet* oop_map_set,
                                ExceptionHandlerTable* handler_table,
                                AbstractCompiler* compiler,
                                DebugInformationRecorder* debug_info,
                                Dependencies* dependencies,
                                JVMCIEnv* env,
                                int compile_id,
                                bool has_unsafe_access,
                                bool has_wide_vector,
                                Handle installed_code,
                                Handle compiled_code,
                                Handle speculation_log) {
  JVMCI_EXCEPTION_CONTEXT;
  nm = NULL;
  int comp_level = CompLevel_full_optimization;
  char* failure_detail = NULL;
  JVMCIEnv::CodeInstallResult result;
  {
    // To prevent compile queue updates.
    MutexLocker locker(MethodCompileQueue_lock, THREAD);

    // Prevent SystemDictionary::add_to_hierarchy from running
    // and invalidating our dependencies until we install this method.
    MutexLocker ml(Compile_lock);

    // Encode the dependencies now, so we can check them right away.
    dependencies->encode_content_bytes();

    // Check for {class loads, evolution, breakpoints} during compilation
    result = check_for_system_dictionary_modification(dependencies, compiled_code, env, &failure_detail);
    if (result != JVMCIEnv::ok) {
      // While not a true deoptimization, it is a preemptive decompile.
      MethodData* mdp = method()->method_data();
      if (mdp != NULL) {
        mdp->inc_decompile_count();
        if (mdp->decompile_count() > (uint)PerMethodRecompilationCutoff) {
          // TODO (chaeubl) enable this in the fastdebug build only once we are more stable
          ResourceMark m;
          tty->print_cr("WARN: endless recompilation of %s. Method was set to not compilable.", method()->name_and_sig_as_C_string());
          //ShouldNotReachHere();
        }
      }

      // All buffers in the CodeBuffer are allocated in the CodeCache.
      // If the code buffer is created on each compile attempt
      // as in C2, then it must be freed.
      //code_buffer->free_blob();
    } else {
      ImplicitExceptionTable implicit_tbl;
      nm =  nmethod::new_nmethod(method,
                                 compile_id,
                                 entry_bci,
                                 offsets,
                                 orig_pc_offset,
                                 debug_info, dependencies, code_buffer,
                                 frame_words, oop_map_set,
                                 handler_table, &implicit_tbl,
                                 compiler, comp_level, installed_code, speculation_log);

      // Free codeBlobs
      //code_buffer->free_blob();
      if (nm == NULL) {
        // The CodeCache is full.  Print out warning and disable compilation.
        {
          MutexUnlocker ml(Compile_lock);
          MutexUnlocker locker(MethodCompileQueue_lock);
          CompileBroker::handle_full_code_cache(CodeCache::get_code_blob_type(comp_level));
        }
      } else {
        nm->set_has_unsafe_access(has_unsafe_access);
        nm->set_has_wide_vectors(has_wide_vector);

        // Record successful registration.
        // (Put nm into the task handle *before* publishing to the Java heap.)
        CompileTask* task = env == NULL ? NULL : env->task();
        if (task != NULL)  task->set_code(nm);

        if (installed_code->is_a(HotSpotNmethod::klass()) && HotSpotNmethod::isDefault(installed_code())) {
          if (entry_bci == InvocationEntryBci) {
            if (TieredCompilation) {
              // If there is an old version we're done with it
              nmethod* old = method->code();
              if (TraceMethodReplacement && old != NULL) {
                ResourceMark rm;
                char *method_name = method->name_and_sig_as_C_string();
                tty->print_cr("Replacing method %s", method_name);
              }
              if (old != NULL ) {
                old->make_not_entrant();
              }
            }
            if (TraceNMethodInstalls) {
              ResourceMark rm;
              char *method_name = method->name_and_sig_as_C_string();
              ttyLocker ttyl;
              tty->print_cr("Installing method (%d) %s [entry point: %p]",
                            comp_level,
                            method_name, nm->entry_point());
            }
            // Allow the code to be executed
            method->set_code(method, nm);
          } else {
            if (TraceNMethodInstalls ) {
              ResourceMark rm;
              char *method_name = method->name_and_sig_as_C_string();
              ttyLocker ttyl;
              tty->print_cr("Installing osr method (%d) %s @ %d",
                            comp_level,
                            method_name,
                            entry_bci);
            }
            InstanceKlass::cast(method->method_holder())->add_osr_nmethod(nm);
          }
        }
      }
      result = nm != NULL ? JVMCIEnv::ok :JVMCIEnv::cache_full;
    }
  }

  // String creation must be done outside lock
  if (failure_detail != NULL) {
    // A failure to allocate the string is silently ignored.
    Handle message = java_lang_String::create_from_str(failure_detail, THREAD);
    HotSpotCompiledNmethod::set_installationFailureMessage(compiled_code, message());
  }

  // JVMTI -- compiled method notification (must be done outside lock)
  if (nm != NULL) {
    nm->post_compiled_method_load_event();
  }

  return result;
}

