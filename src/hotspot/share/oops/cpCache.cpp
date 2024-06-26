/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/archiveBuilder.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/classPrelinker.hpp"
#include "cds/heapShared.hpp"
#include "classfile/resolutionErrors.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmClasses.hpp"
#include "code/codeCache.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/bytecodes.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/linkResolver.hpp"
#include "interpreter/rewriter.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/resourceArea.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.hpp"
#include "oops/constantPool.inline.hpp"
#include "oops/cpCache.inline.hpp"
#include "oops/method.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/resolvedFieldEntry.hpp"
#include "oops/resolvedIndyEntry.hpp"
#include "oops/resolvedMethodEntry.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/macros.hpp"

// Implementation of ConstantPoolCache

template <class T>
static Array<T>* initialize_resolved_entries_array(ClassLoaderData* loader_data, GrowableArray<T> entries, TRAPS) {
  Array<T>* resolved_entries;
  if (entries.length() != 0) {
    resolved_entries = MetadataFactory::new_array<T>(loader_data, entries.length(), CHECK_NULL);
    for (int i = 0; i < entries.length(); i++) {
      resolved_entries->at_put(i, entries.at(i));
    }
    return resolved_entries;
  }
  return nullptr;
}

void ConstantPoolCache::set_direct_or_vtable_call(Bytecodes::Code invoke_code,
                                                       int method_index,
                                                       const methodHandle& method,
                                                       int vtable_index,
                                                       bool sender_is_interface) {
  bool is_vtable_call = (vtable_index >= 0);  // FIXME: split this method on this boolean
  assert(method->interpreter_entry() != nullptr, "should have been set at this point");
  assert(!method->is_obsolete(),  "attempt to write obsolete method to cpCache");

  int byte_no = -1;
  bool change_to_virtual = false;
  InstanceKlass* holder = nullptr;  // have to declare this outside the switch
  ResolvedMethodEntry* method_entry = resolved_method_entry_at(method_index);
  switch (invoke_code) {
    case Bytecodes::_invokeinterface:
      holder = method->method_holder();
      // check for private interface method invocations
      if (vtable_index == Method::nonvirtual_vtable_index && holder->is_interface() ) {
        assert(method->is_private(), "unexpected non-private method");
        assert(method->can_be_statically_bound(), "unexpected non-statically-bound method");

        method_entry->set_flags((                             1      << ResolvedMethodEntry::is_vfinal_shift) |
                                ((method->is_final_method() ? 1 : 0) << ResolvedMethodEntry::is_final_shift));
        method_entry->fill_in((u1)as_TosState(method->result_type()), (u2)method()->size_of_parameters());
        assert(method_entry->is_vfinal(), "flags must be set");
        method_entry->set_method(method());
        byte_no = 2;
        method_entry->set_klass(holder);
        break;
      }
      else {
        // We get here from InterpreterRuntime::resolve_invoke when an invokeinterface
        // instruction links to a non-interface method (in Object). This can happen when
        // an interface redeclares an Object method (like CharSequence declaring toString())
        // or when invokeinterface is used explicitly.
        // In that case, the method has no itable index and must be invoked as a virtual.
        // Set a flag to keep track of this corner case.
        assert(holder->is_interface() || holder == vmClasses::Object_klass(), "unexpected holder class");
        assert(method->is_public(), "Calling non-public method in Object with invokeinterface");
        change_to_virtual = true;

        // ...and fall through as if we were handling invokevirtual:
      }
    case Bytecodes::_invokevirtual:
      {
        if (!is_vtable_call) {
          assert(method->can_be_statically_bound(), "");
          method_entry->set_flags((                             1      << ResolvedMethodEntry::is_vfinal_shift) |
                                  ((method->is_final_method() ? 1 : 0) << ResolvedMethodEntry::is_final_shift)  |
                                  ((change_to_virtual         ? 1 : 0) << ResolvedMethodEntry::is_forced_virtual_shift));
          method_entry->fill_in((u1)as_TosState(method->result_type()), (u2)method()->size_of_parameters());
          assert(method_entry->is_vfinal(), "flags must be set");
          method_entry->set_method(method());
        } else {
          assert(!method->can_be_statically_bound(), "");
          assert(vtable_index >= 0, "valid index");
          assert(!method->is_final_method(), "sanity");
          method_entry->set_flags((change_to_virtual ? 1 : 0) << ResolvedMethodEntry::is_forced_virtual_shift);
          method_entry->fill_in((u1)as_TosState(method->result_type()), (u2)method()->size_of_parameters());
          assert(!method_entry->is_vfinal(), "flags must not be set");
          method_entry->set_table_index(vtable_index);
        }
        byte_no = 2;
        break;
      }

    case Bytecodes::_invokespecial:
    case Bytecodes::_invokestatic: {
      assert(!is_vtable_call, "");
      // Note:  Read and preserve the value of the is_vfinal flag on any
      // invokevirtual bytecode shared with this constant pool cache entry.
      // It is cheap and safe to consult is_vfinal() at all times.
      // Once is_vfinal is set, it must stay that way, lest we get a dangling oop.
      bool vfinal = method_entry->is_vfinal();
      method_entry->set_flags(((method->is_final_method() ? 1 : 0) << ResolvedMethodEntry::is_final_shift));
      assert(vfinal == method_entry->is_vfinal(), "Vfinal flag must be preserved");
      method_entry->fill_in((u1)as_TosState(method->result_type()), (u2)method()->size_of_parameters());
      method_entry->set_method(method());
      byte_no = 1;
      break;
    }
    default:
      ShouldNotReachHere();
      break;
  }

  // Note:  byte_no also appears in TemplateTable::resolve.
  if (byte_no == 1) {
    assert(invoke_code != Bytecodes::_invokevirtual &&
           invoke_code != Bytecodes::_invokeinterface, "");
    bool do_resolve = true;
    // Don't mark invokespecial to method as resolved if sender is an interface.  The receiver
    // has to be checked that it is a subclass of the current class every time this bytecode
    // is executed.
    if (invoke_code == Bytecodes::_invokespecial && sender_is_interface &&
        method->name() != vmSymbols::object_initializer_name()) {
      do_resolve = false;
    }
    if (invoke_code == Bytecodes::_invokestatic) {
      assert(method->method_holder()->is_initialized() ||
             method->method_holder()->is_reentrant_initialization(JavaThread::current()),
             "invalid class initialization state for invoke_static");

      if (!VM_Version::supports_fast_class_init_checks() && method->needs_clinit_barrier()) {
        // Don't mark invokestatic to method as resolved if the holder class has not yet completed
        // initialization. An invokestatic must only proceed if the class is initialized, but if
        // we resolve it before then that class initialization check is skipped.
        //
        // When fast class initialization checks are supported (VM_Version::supports_fast_class_init_checks() == true),
        // template interpreter supports fast class initialization check for
        // invokestatic which doesn't require call site re-resolution to
        // enforce class initialization barrier.
        do_resolve = false;
      }
    }
    if (do_resolve) {
      method_entry->set_bytecode1(invoke_code);
    }
  } else if (byte_no == 2)  {
    if (change_to_virtual) {
      assert(invoke_code == Bytecodes::_invokeinterface, "");
      // NOTE: THIS IS A HACK - BE VERY CAREFUL!!!
      //
      // Workaround for the case where we encounter an invokeinterface, but we
      // should really have an _invokevirtual since the resolved method is a
      // virtual method in java.lang.Object. This is a corner case in the spec
      // but is presumably legal. javac does not generate this code.
      //
      // We do not set bytecode_1() to _invokeinterface, because that is the
      // bytecode # used by the interpreter to see if it is resolved.  In this
      // case, the method gets reresolved with caller for each interface call
      // because the actual selected method may not be public.
      //
      // We set bytecode_2() to _invokevirtual.
      // See also interpreterRuntime.cpp. (8/25/2000)
    } else {
      assert(invoke_code == Bytecodes::_invokevirtual ||
             (invoke_code == Bytecodes::_invokeinterface &&
              ((method->is_private() ||
                (method->is_final() && method->method_holder() == vmClasses::Object_klass())))),
             "unexpected invocation mode");
      if (invoke_code == Bytecodes::_invokeinterface &&
          (method->is_private() || method->is_final())) {
        // We set bytecode_1() to _invokeinterface, because that is the
        // bytecode # used by the interpreter to see if it is resolved.
        // We set bytecode_2() to _invokevirtual.
        method_entry->set_bytecode1(invoke_code);
      }
    }
    // set up for invokevirtual, even if linking for invokeinterface also:
    method_entry->set_bytecode2(Bytecodes::_invokevirtual);
  } else {
    ShouldNotReachHere();
  }
}

void ConstantPoolCache::set_direct_call(Bytecodes::Code invoke_code, int method_index, const methodHandle& method,
                                        bool sender_is_interface) {
  int index = Method::nonvirtual_vtable_index;
  // index < 0; FIXME: inline and customize set_direct_or_vtable_call
  set_direct_or_vtable_call(invoke_code, method_index, method, index, sender_is_interface);
}

void ConstantPoolCache::set_vtable_call(Bytecodes::Code invoke_code, int method_index, const methodHandle& method, int index) {
  // either the method is a miranda or its holder should accept the given index
  assert(method->method_holder()->is_interface() || method->method_holder()->verify_vtable_index(index), "");
  // index >= 0; FIXME: inline and customize set_direct_or_vtable_call
  set_direct_or_vtable_call(invoke_code, method_index, method, index, false);
}

void ConstantPoolCache::set_itable_call(Bytecodes::Code invoke_code,
                                        int method_index,
                                        Klass* referenced_klass,
                                        const methodHandle& method, int index) {
  assert(method->method_holder()->verify_itable_index(index), "");
  assert(invoke_code == Bytecodes::_invokeinterface, "");
  InstanceKlass* interf = method->method_holder();
  assert(interf->is_interface(), "must be an interface");
  assert(!method->is_final_method(), "interfaces do not have final methods; cannot link to one here");
  ResolvedMethodEntry* method_entry = resolved_method_entry_at(method_index);
  method_entry->set_klass(static_cast<InstanceKlass*>(referenced_klass));
  method_entry->set_method(method());
  method_entry->fill_in((u1)as_TosState(method->result_type()), (u2)method()->size_of_parameters());
  method_entry->set_bytecode1(Bytecodes::_invokeinterface);
}

ResolvedMethodEntry* ConstantPoolCache::set_method_handle(int method_index, const CallInfo &call_info) {
  // NOTE: This method entry can be the subject of data races.
  // There are three words to update: flags, refs[appendix_index], method (in that order).
  // Writers must store all other values before method.
  // Readers must test the method first for non-null before reading other fields.
  // Competing writers must acquire exclusive access via a lock.
  // A losing writer waits on the lock until the winner writes the method and leaves
  // the lock, so that when the losing writer returns, he can use the linked
  // cache entry.

  // Lock fields to write
  Bytecodes::Code invoke_code = Bytecodes::_invokehandle;

  JavaThread* current = JavaThread::current();
  objArrayHandle resolved_references(current, constant_pool()->resolved_references());
  // Use the resolved_references() lock for this cpCache entry.
  // resolved_references are created for all classes with Invokedynamic, MethodHandle
  // or MethodType constant pool cache entries.
  assert(resolved_references() != nullptr,
         "a resolved_references array should have been created for this class");
  ObjectLocker ol(resolved_references, current);

  ResolvedMethodEntry* method_entry = resolved_method_entry_at(method_index);
  if (method_entry->is_resolved(invoke_code)) {
    return method_entry;
  }

  Method* adapter            = call_info.resolved_method();
  const Handle appendix      = call_info.resolved_appendix();
  const bool has_appendix    = appendix.not_null();

  // Write the flags.
  // MHs are always sig-poly and have a local signature.
  method_entry->fill_in((u1)as_TosState(adapter->result_type()), (u2)adapter->size_of_parameters());
  method_entry->set_flags(((has_appendix    ? 1 : 0) << ResolvedMethodEntry::has_appendix_shift        ) |
                          (                   1      << ResolvedMethodEntry::has_local_signature_shift ) |
                          (                   1      << ResolvedMethodEntry::is_final_shift            ));

  // Method handle invokes use both a method and a resolved references index.
  // refs[appendix_index], if not null, contains a value passed as a trailing argument to the adapter.
  // In the general case, this could be the call site's MethodType,
  // for use with java.lang.Invokers.checkExactType, or else a CallSite object.
  // method_entry->method() contains the adapter method which manages the actual call.
  // In the general case, this is a compiled LambdaForm.
  // (The Java code is free to optimize these calls by binding other
  // sorts of methods and appendices to call sites.)
  // JVM-level linking is via the method, as if for invokespecial, and signatures are erased.
  // The appendix argument (if any) is added to the signature, and is counted in the parameter_size bits.
  // Even with the appendix, the method will never take more than 255 parameter slots.
  //
  // This means that given a call site like (List)mh.invoke("foo"),
  // the method has signature '(Ljl/Object;Ljl/invoke/MethodType;)Ljl/Object;',
  // not '(Ljava/lang/String;)Ljava/util/List;'.
  // The fact that String and List are involved is encoded in the MethodType in refs[appendix_index].
  // This allows us to create fewer Methods, while keeping type safety.
  //

  // Store appendix, if any.
  if (has_appendix) {
    const int appendix_index = method_entry->resolved_references_index();
    assert(appendix_index >= 0 && appendix_index < resolved_references->length(), "oob");
    assert(resolved_references->obj_at(appendix_index) == nullptr, "init just once");
    resolved_references->obj_at_put(appendix_index, appendix());
  }

  method_entry->set_method(adapter); // This must be the last one to set (see NOTE above)!

  // The interpreter assembly code does not check byte_2,
  // but it is used by is_resolved, method_if_resolved, etc.
  method_entry->set_bytecode1(invoke_code);

  assert(has_appendix == method_entry->has_appendix(), "proper storage of appendix flag");
  assert(method_entry->has_local_signature(), "proper storage of signature flag");
  return method_entry;
}

Method* ConstantPoolCache::method_if_resolved(int method_index) const {
  // Decode the action of set_method and set_interface_call
  ResolvedMethodEntry* method_entry = resolved_method_entry_at(method_index);

  Bytecodes::Code invoke_code = (Bytecodes::Code)method_entry->bytecode1();
  switch (invoke_code) {
    case Bytecodes::_invokeinterface:
    case Bytecodes::_invokestatic:
    case Bytecodes::_invokespecial:
      assert(!method_entry->has_appendix(), "");
      // fall through
    case Bytecodes::_invokehandle:
      return method_entry->method();
    case Bytecodes::_invokedynamic:
      ShouldNotReachHere();
    default:
      assert(invoke_code == (Bytecodes::Code)0, "unexpected bytecode");
      break;
  }

  invoke_code = (Bytecodes::Code)method_entry->bytecode2();
  if (invoke_code == Bytecodes::_invokevirtual) {
    if (method_entry->is_vfinal()) {
      return method_entry->method();
    } else {
      int holder_index = constant_pool()->uncached_klass_ref_index_at(method_entry->constant_pool_index());
      if (constant_pool()->tag_at(holder_index).is_klass()) {
        Klass* klass = constant_pool()->resolved_klass_at(holder_index);
        return klass->method_at_vtable(method_entry->table_index());
      }
    }
  }
  return nullptr;
}

ConstantPoolCache* ConstantPoolCache::allocate(ClassLoaderData* loader_data,
                                     const intStack& invokedynamic_map,
                                     const GrowableArray<ResolvedIndyEntry> indy_entries,
                                     const GrowableArray<ResolvedFieldEntry> field_entries,
                                     const GrowableArray<ResolvedMethodEntry> method_entries,
                                     TRAPS) {

  int size = ConstantPoolCache::size();

  // Initialize resolved entry arrays with available data
  Array<ResolvedFieldEntry>* resolved_field_entries = initialize_resolved_entries_array(loader_data, field_entries, CHECK_NULL);
  Array<ResolvedIndyEntry>* resolved_indy_entries = initialize_resolved_entries_array(loader_data, indy_entries, CHECK_NULL);
  Array<ResolvedMethodEntry>* resolved_method_entries = initialize_resolved_entries_array(loader_data, method_entries, CHECK_NULL);

  return new (loader_data, size, MetaspaceObj::ConstantPoolCacheType, THREAD)
              ConstantPoolCache(invokedynamic_map, resolved_indy_entries, resolved_field_entries, resolved_method_entries);
}

// Record the GC marking cycle when redefined vs. when found in the loom stack chunks.
void ConstantPoolCache::record_gc_epoch() {
  _gc_epoch = CodeCache::gc_epoch();
}

#if INCLUDE_CDS
void ConstantPoolCache::remove_unshareable_info() {
  assert(CDSConfig::is_dumping_archive(), "sanity");

  if (_resolved_indy_entries != nullptr) {
    for (int i = 0; i < _resolved_indy_entries->length(); i++) {
      resolved_indy_entry_at(i)->remove_unshareable_info();
    }
  }
  if (_resolved_field_entries != nullptr) {
    remove_resolved_field_entries_if_non_deterministic();
  }
  if (_resolved_method_entries != nullptr) {
    remove_resolved_method_entries_if_non_deterministic();
  }
}

void ConstantPoolCache::remove_resolved_field_entries_if_non_deterministic() {
  ConstantPool* cp = constant_pool();
  ConstantPool* src_cp =  ArchiveBuilder::current()->get_source_addr(cp);
  for (int i = 0; i < _resolved_field_entries->length(); i++) {
    ResolvedFieldEntry* rfi = _resolved_field_entries->adr_at(i);
    int cp_index = rfi->constant_pool_index();
    bool archived = false;
    bool resolved = rfi->is_resolved(Bytecodes::_getfield)  ||
                    rfi->is_resolved(Bytecodes::_putfield);
    if (resolved && ClassPrelinker::is_resolution_deterministic(src_cp, cp_index)) {
      rfi->mark_and_relocate();
      archived = true;
    } else {
      rfi->remove_unshareable_info();
    }
    if (resolved) {
      LogStreamHandle(Trace, cds, resolve) log;
      if (log.is_enabled()) {
        ResourceMark rm;
        int klass_cp_index = cp->uncached_klass_ref_index_at(cp_index);
        Symbol* klass_name = cp->klass_name_at(klass_cp_index);
        Symbol* name = cp->uncached_name_ref_at(cp_index);
        Symbol* signature = cp->uncached_signature_ref_at(cp_index);
        log.print("%s field  CP entry [%3d]: %s %s %s.%s:%s",
                  (archived ? "archived" : "reverted"),
                  cp_index,
                  cp->pool_holder()->name()->as_C_string(),
                  (archived ? "=>" : "  "),
                  klass_name->as_C_string(), name->as_C_string(), signature->as_C_string());
      }
    }
    ArchiveBuilder::alloc_stats()->record_field_cp_entry(archived, resolved && !archived);
  }
}

void ConstantPoolCache::remove_resolved_method_entries_if_non_deterministic() {
  ConstantPool* cp = constant_pool();
  ConstantPool* src_cp =  ArchiveBuilder::current()->get_source_addr(cp);
  for (int i = 0; i < _resolved_method_entries->length(); i++) {
    ResolvedMethodEntry* rme = _resolved_method_entries->adr_at(i);
    int cp_index = rme->constant_pool_index();
    bool archived = false;
    bool resolved = rme->is_resolved(Bytecodes::_invokevirtual)   ||
                    rme->is_resolved(Bytecodes::_invokespecial)   ||
                    rme->is_resolved(Bytecodes::_invokeinterface);

    // Just for safety -- this should not happen, but do not archive if we ever see this.
    resolved &= !(rme->is_resolved(Bytecodes::_invokehandle) ||
                  rme->is_resolved(Bytecodes::_invokestatic));

    if (resolved && can_archive_resolved_method(rme)) {
      rme->mark_and_relocate(src_cp);
      archived = true;
    } else {
      rme->remove_unshareable_info();
    }
    if (resolved) {
      LogStreamHandle(Trace, cds, resolve) log;
      if (log.is_enabled()) {
        ResourceMark rm;
        int klass_cp_index = cp->uncached_klass_ref_index_at(cp_index);
        Symbol* klass_name = cp->klass_name_at(klass_cp_index);
        Symbol* name = cp->uncached_name_ref_at(cp_index);
        Symbol* signature = cp->uncached_signature_ref_at(cp_index);
        log.print("%s%s method CP entry [%3d]: %s %s.%s:%s",
                  (archived ? "archived" : "reverted"),
                  (rme->is_resolved(Bytecodes::_invokeinterface) ? " interface" : ""),
                  cp_index,
                  cp->pool_holder()->name()->as_C_string(),
                  klass_name->as_C_string(), name->as_C_string(), signature->as_C_string());
        if (archived) {
          Klass* resolved_klass = cp->resolved_klass_at(klass_cp_index);
          log.print(" => %s%s",
                    resolved_klass->name()->as_C_string(),
                    (rme->is_resolved(Bytecodes::_invokestatic) ? " *** static" : ""));
        }
      }
      ArchiveBuilder::alloc_stats()->record_method_cp_entry(archived, resolved && !archived);
    }
  }
}

bool ConstantPoolCache::can_archive_resolved_method(ResolvedMethodEntry* method_entry) {
  InstanceKlass* pool_holder = constant_pool()->pool_holder();
  if (!(pool_holder->is_shared_boot_class() || pool_holder->is_shared_platform_class() ||
        pool_holder->is_shared_app_class())) {
    // Archiving resolved cp entries for classes from non-builtin loaders
    // is not yet supported.
    return false;
  }

  if (CDSConfig::is_dumping_dynamic_archive()) {
    // InstanceKlass::methods() has been resorted. We need to
    // update the vtable_index in method_entry (not implemented)
    return false;
  }

  if (!method_entry->is_resolved(Bytecodes::_invokevirtual)) {
    if (method_entry->method() == nullptr) {
      return false;
    }
    if (method_entry->method()->is_continuation_native_intrinsic()) {
      return false; // FIXME: corresponding stub is generated on demand during method resolution (see LinkResolver::resolve_static_call).
    }
  }

  int cp_index = method_entry->constant_pool_index();
  ConstantPool* src_cp = ArchiveBuilder::current()->get_source_addr(constant_pool());
  assert(src_cp->tag_at(cp_index).is_method() || src_cp->tag_at(cp_index).is_interface_method(), "sanity");

  if (!ClassPrelinker::is_resolution_deterministic(src_cp, cp_index)) {
    return false;
  }

  if (method_entry->is_resolved(Bytecodes::_invokeinterface) ||
      method_entry->is_resolved(Bytecodes::_invokevirtual) ||
      method_entry->is_resolved(Bytecodes::_invokespecial)) {
    return true;
  } else {
    // invokestatic and invokehandle are not supported yet.
    return false;
  }

}
#endif // INCLUDE_CDS

void ConstantPoolCache::deallocate_contents(ClassLoaderData* data) {
  assert(!is_shared(), "shared caches are not deallocated");
  data->remove_handle(_resolved_references);
  set_resolved_references(OopHandle());
  MetadataFactory::free_array<u2>(data, _reference_map);
  set_reference_map(nullptr);
#if INCLUDE_CDS
  if (_resolved_indy_entries != nullptr) {
    MetadataFactory::free_array<ResolvedIndyEntry>(data, _resolved_indy_entries);
    _resolved_indy_entries = nullptr;
  }
  if (_resolved_field_entries != nullptr) {
    MetadataFactory::free_array<ResolvedFieldEntry>(data, _resolved_field_entries);
    _resolved_field_entries = nullptr;
  }
  if (_resolved_method_entries != nullptr) {
    MetadataFactory::free_array<ResolvedMethodEntry>(data, _resolved_method_entries);
    _resolved_method_entries = nullptr;
  }
#endif
}

#if INCLUDE_CDS_JAVA_HEAP
oop ConstantPoolCache::archived_references() {
  if (_archived_references_index < 0) {
    return nullptr;
  }
  return HeapShared::get_root(_archived_references_index);
}

void ConstantPoolCache::clear_archived_references() {
  if (_archived_references_index >= 0) {
    HeapShared::clear_root(_archived_references_index);
    _archived_references_index = -1;
  }
}

void ConstantPoolCache::set_archived_references(int root_index) {
  assert(CDSConfig::is_dumping_heap(), "sanity");
  _archived_references_index = root_index;
}
#endif

#if INCLUDE_JVMTI
static void log_adjust(const char* entry_type, Method* old_method, Method* new_method, bool* trace_name_printed) {
  ResourceMark rm;

  if (!(*trace_name_printed)) {
    log_info(redefine, class, update)("adjust: name=%s", old_method->method_holder()->external_name());
    *trace_name_printed = true;
  }
  log_trace(redefine, class, update, constantpool)
    ("cpc %s entry update: %s", entry_type, new_method->external_name());
}

// RedefineClasses() API support:
// If any entry of this ConstantPoolCache points to any of
// old_methods, replace it with the corresponding new_method.
void ConstantPoolCache::adjust_method_entries(bool * trace_name_printed) {
  if (_resolved_indy_entries != nullptr) {
    for (int j = 0; j < _resolved_indy_entries->length(); j++) {
      Method* old_method = resolved_indy_entry_at(j)->method();
      if (old_method == nullptr || !old_method->is_old()) {
        continue;
      }
      Method* new_method = old_method->get_new_method();
      resolved_indy_entry_at(j)->adjust_method_entry(new_method);
      log_adjust("indy", old_method, new_method, trace_name_printed);
    }
  }
  if (_resolved_method_entries != nullptr) {
    for (int i = 0; i < _resolved_method_entries->length(); i++) {
      ResolvedMethodEntry* method_entry = resolved_method_entry_at(i);
      // get interesting method entry
      Method* old_method = method_entry->method();
      if (old_method == nullptr || !old_method->is_old()) {
        continue; // skip uninteresting entries
      }
      if (old_method->is_deleted()) {
        // clean up entries with deleted methods
        method_entry->reset_entry();
        continue;
      }
      Method* new_method = old_method->get_new_method();
      method_entry->adjust_method_entry(new_method);
      log_adjust("non-indy", old_method, new_method, trace_name_printed);
    }
  }
}

// the constant pool cache should never contain old or obsolete methods
bool ConstantPoolCache::check_no_old_or_obsolete_entries() {
  ResourceMark rm;
  if (_resolved_indy_entries != nullptr) {
    for (int i = 0; i < _resolved_indy_entries->length(); i++) {
      Method* m = resolved_indy_entry_at(i)->method();
      if (m != nullptr && !resolved_indy_entry_at(i)->check_no_old_or_obsolete_entry()) {
        log_trace(redefine, class, update, constantpool)
          ("cpcache check found old method entry: class: %s, old: %d, obsolete: %d, method: %s",
           constant_pool()->pool_holder()->external_name(), m->is_old(), m->is_obsolete(), m->external_name());
        return false;
      }
    }
  }
  if (_resolved_method_entries != nullptr) {
    for (int i = 0; i < _resolved_method_entries->length(); i++) {
      ResolvedMethodEntry* method_entry = resolved_method_entry_at(i);
      Method* m = method_entry->method();
      if (m != nullptr && !method_entry->check_no_old_or_obsolete_entry()) {
        log_trace(redefine, class, update, constantpool)
          ("cpcache check found old method entry: class: %s, old: %d, obsolete: %d, method: %s",
           constant_pool()->pool_holder()->external_name(), m->is_old(), m->is_obsolete(), m->external_name());
        return false;
      }
    }
  }
  return true;
}

void ConstantPoolCache::dump_cache() {
  print_on(tty);
}
#endif // INCLUDE_JVMTI

void ConstantPoolCache::metaspace_pointers_do(MetaspaceClosure* it) {
  log_trace(cds)("Iter(ConstantPoolCache): %p", this);
  it->push(&_constant_pool);
  it->push(&_reference_map);
  if (_resolved_indy_entries != nullptr) {
    it->push(&_resolved_indy_entries, MetaspaceClosure::_writable);
  }
  if (_resolved_field_entries != nullptr) {
    it->push(&_resolved_field_entries, MetaspaceClosure::_writable);
  }
  if (_resolved_method_entries != nullptr) {
    it->push(&_resolved_method_entries, MetaspaceClosure::_writable);
  }
}

bool ConstantPoolCache::save_and_throw_indy_exc(
  const constantPoolHandle& cpool, int cpool_index, int index, constantTag tag, TRAPS) {

  assert(HAS_PENDING_EXCEPTION, "No exception got thrown!");
  assert(PENDING_EXCEPTION->is_a(vmClasses::LinkageError_klass()),
         "No LinkageError exception");

  // Use the resolved_references() lock for this cpCache entry.
  // resolved_references are created for all classes with Invokedynamic, MethodHandle
  // or MethodType constant pool cache entries.
  JavaThread* current = THREAD;
  objArrayHandle resolved_references(current, cpool->resolved_references());
  assert(resolved_references() != nullptr,
         "a resolved_references array should have been created for this class");
  ObjectLocker ol(resolved_references, current);

  // if the indy_info is resolved or the indy_resolution_failed flag is set then another
  // thread either succeeded in resolving the method or got a LinkageError
  // exception, before this thread was able to record its failure.  So, clear
  // this thread's exception and return false so caller can use the earlier
  // thread's result.
  if (resolved_indy_entry_at(index)->is_resolved() || resolved_indy_entry_at(index)->resolution_failed()) {
    CLEAR_PENDING_EXCEPTION;
    return false;
  }
  ResourceMark rm(THREAD);
  Symbol* error = PENDING_EXCEPTION->klass()->name();
  const char* message = java_lang_Throwable::message_as_utf8(PENDING_EXCEPTION);

  int encoded_index = ResolutionErrorTable::encode_indy_index(index);
  SystemDictionary::add_resolution_error(cpool, encoded_index, error, message);
  resolved_indy_entry_at(index)->set_resolution_failed();
  return true;
}

oop ConstantPoolCache::set_dynamic_call(const CallInfo &call_info, int index) {
  ResourceMark rm;

  // Use the resolved_references() lock for this cpCache entry.
  // resolved_references are created for all classes with Invokedynamic, MethodHandle
  // or MethodType constant pool cache entries.
  JavaThread* current = JavaThread::current();
  constantPoolHandle cp(current, constant_pool());

  objArrayHandle resolved_references(current, cp->resolved_references());
  assert(resolved_references() != nullptr,
         "a resolved_references array should have been created for this class");
  ObjectLocker ol(resolved_references, current);
  assert(index >= 0, "Indy index must be positive at this point");

  if (resolved_indy_entry_at(index)->method() != nullptr) {
    return cp->resolved_reference_from_indy(index);
  }

  if (resolved_indy_entry_at(index)->resolution_failed()) {
    // Before we got here, another thread got a LinkageError exception during
    // resolution.  Ignore our success and throw their exception.
    guarantee(index >= 0, "Invalid indy index");
    int encoded_index = ResolutionErrorTable::encode_indy_index(index);
    ConstantPool::throw_resolution_error(cp, encoded_index, current);
    return nullptr;
  }

  Method* adapter            = call_info.resolved_method();
  const Handle appendix      = call_info.resolved_appendix();
  const bool has_appendix    = appendix.not_null();

  LogStream* log_stream = nullptr;
  LogStreamHandle(Debug, methodhandles, indy) lsh_indy;
  if (lsh_indy.is_enabled()) {
    ResourceMark rm;
    log_stream = &lsh_indy;
    log_stream->print_cr("set_method_handle bc=%d appendix=" PTR_FORMAT "%s method=" PTR_FORMAT " (local signature) ",
                         0xba,
                         p2i(appendix()),
                         (has_appendix ? "" : " (unused)"),
                         p2i(adapter));
    adapter->print_on(log_stream);
    if (has_appendix)  appendix()->print_on(log_stream);
  }

  if (has_appendix) {
    const int appendix_index = resolved_indy_entry_at(index)->resolved_references_index();
    assert(appendix_index >= 0 && appendix_index < resolved_references->length(), "oob");
    assert(resolved_references->obj_at(appendix_index) == nullptr, "init just once");
    resolved_references->obj_at_put(appendix_index, appendix());
  }

  // Populate entry with resolved information
  assert(resolved_indy_entries() != nullptr, "Invokedynamic array is empty, cannot fill with resolved information");
  resolved_indy_entry_at(index)->fill_in(adapter, adapter->size_of_parameters(), as_TosState(adapter->result_type()), has_appendix);

  if (log_stream != nullptr) {
    resolved_indy_entry_at(index)->print_on(log_stream);
  }
  return appendix();
}

oop ConstantPoolCache::appendix_if_resolved(int method_index) const {
  ResolvedMethodEntry* method_entry = resolved_method_entry_at(method_index);
  return appendix_if_resolved(method_entry);
}

oop ConstantPoolCache::appendix_if_resolved(ResolvedMethodEntry* method_entry) const {
  if (!method_entry->has_appendix())
    return nullptr;
  const int ref_index = method_entry->resolved_references_index();
  return constant_pool()->resolved_reference_at(ref_index);
}

// Printing

void ConstantPoolCache::print_on(outputStream* st) const {
  st->print_cr("%s", internal_name());
  // print constant pool cache entries
  print_resolved_field_entries(st);
  print_resolved_method_entries(st);
  print_resolved_indy_entries(st);
}

void ConstantPoolCache::print_resolved_field_entries(outputStream* st) const {
  for (int field_index = 0; field_index < resolved_field_entries_length(); field_index++) {
    resolved_field_entry_at(field_index)->print_on(st);
  }
}

void ConstantPoolCache::print_resolved_method_entries(outputStream* st) const {
  for (int method_index = 0; method_index < resolved_method_entries_length(); method_index++) {
    ResolvedMethodEntry* method_entry = resolved_method_entry_at(method_index);
    method_entry->print_on(st);
    if (method_entry->has_appendix()) {
      st->print("  appendix: ");
      constant_pool()->resolved_reference_from_method(method_index)->print_on(st);
    }
  }
}

void ConstantPoolCache::print_resolved_indy_entries(outputStream* st) const {
  for (int indy_index = 0; indy_index < resolved_indy_entries_length(); indy_index++) {
    ResolvedIndyEntry* indy_entry = resolved_indy_entry_at(indy_index);
    indy_entry->print_on(st);
    if (indy_entry->has_appendix()) {
      st->print("  appendix: ");
      constant_pool()->resolved_reference_from_indy(indy_index)->print_on(st);
    }
  }
}
