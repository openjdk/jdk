/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderData.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/metadataOnStackMark.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "interpreter/linkResolver.hpp"
#include "memory/heapInspection.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/oopFactory.hpp"
#include "oops/constantPool.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/fieldType.hpp"
#include "runtime/init.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/signature.hpp"
#include "runtime/vframe.hpp"
#include "utilities/copy.hpp"

ConstantPool* ConstantPool::allocate(ClassLoaderData* loader_data, int length, TRAPS) {
  // Tags are RW but comment below applies to tags also.
  Array<u1>* tags = MetadataFactory::new_writeable_array<u1>(loader_data, length, 0, CHECK_NULL);

  int size = ConstantPool::size(length);

  // CDS considerations:
  // Allocate read-write but may be able to move to read-only at dumping time
  // if all the klasses are resolved.  The only other field that is writable is
  // the resolved_references array, which is recreated at startup time.
  // But that could be moved to InstanceKlass (although a pain to access from
  // assembly code).  Maybe it could be moved to the cpCache which is RW.
  return new (loader_data, size, false, MetaspaceObj::ConstantPoolType, THREAD) ConstantPool(tags);
}

#ifdef ASSERT

// MetaspaceObj allocation invariant is calloc equivalent memory
// simple verification of this here (JVM_CONSTANT_Invalid == 0 )
static bool tag_array_is_zero_initialized(Array<u1>* tags) {
  assert(tags != NULL, "invariant");
  const int length = tags->length();
  for (int index = 0; index < length; ++index) {
    if (JVM_CONSTANT_Invalid != tags->at(index)) {
      return false;
    }
  }
  return true;
}

#endif

ConstantPool::ConstantPool(Array<u1>* tags) :
  _tags(tags),
  _length(tags->length()) {

    assert(_tags != NULL, "invariant");
    assert(tags->length() == _length, "invariant");
    assert(tag_array_is_zero_initialized(tags), "invariant");
    assert(0 == _flags, "invariant");
    assert(0 == version(), "invariant");
    assert(NULL == _pool_holder, "invariant");
}

void ConstantPool::deallocate_contents(ClassLoaderData* loader_data) {
  MetadataFactory::free_metadata(loader_data, cache());
  set_cache(NULL);
  MetadataFactory::free_array<u2>(loader_data, reference_map());
  set_reference_map(NULL);

  MetadataFactory::free_array<jushort>(loader_data, operands());
  set_operands(NULL);

  release_C_heap_structures();

  // free tag array
  MetadataFactory::free_array<u1>(loader_data, tags());
  set_tags(NULL);
}

void ConstantPool::release_C_heap_structures() {
  // walk constant pool and decrement symbol reference counts
  unreference_symbols();
}

objArrayOop ConstantPool::resolved_references() const {
  return (objArrayOop)JNIHandles::resolve(_resolved_references);
}

// Create resolved_references array and mapping array for original cp indexes
// The ldc bytecode was rewritten to have the resolved reference array index so need a way
// to map it back for resolving and some unlikely miscellaneous uses.
// The objects created by invokedynamic are appended to this list.
void ConstantPool::initialize_resolved_references(ClassLoaderData* loader_data,
                                                  const intStack& reference_map,
                                                  int constant_pool_map_length,
                                                  TRAPS) {
  // Initialized the resolved object cache.
  int map_length = reference_map.length();
  if (map_length > 0) {
    // Only need mapping back to constant pool entries.  The map isn't used for
    // invokedynamic resolved_reference entries.  For invokedynamic entries,
    // the constant pool cache index has the mapping back to both the constant
    // pool and to the resolved reference index.
    if (constant_pool_map_length > 0) {
      Array<u2>* om = MetadataFactory::new_array<u2>(loader_data, constant_pool_map_length, CHECK);

      for (int i = 0; i < constant_pool_map_length; i++) {
        int x = reference_map.at(i);
        assert(x == (int)(jushort) x, "klass index is too big");
        om->at_put(i, (jushort)x);
      }
      set_reference_map(om);
    }

    // Create Java array for holding resolved strings, methodHandles,
    // methodTypes, invokedynamic and invokehandle appendix objects, etc.
    objArrayOop stom = oopFactory::new_objArray(SystemDictionary::Object_klass(), map_length, CHECK);
    Handle refs_handle (THREAD, (oop)stom);  // must handleize.
    set_resolved_references(loader_data->add_handle(refs_handle));
  }
}

// CDS support. Create a new resolved_references array.
void ConstantPool::restore_unshareable_info(TRAPS) {

  // Only create the new resolved references array if it hasn't been attempted before
  if (resolved_references() != NULL) return;

  // restore the C++ vtable from the shared archive
  restore_vtable();

  if (SystemDictionary::Object_klass_loaded()) {
    // Recreate the object array and add to ClassLoaderData.
    int map_length = resolved_reference_length();
    if (map_length > 0) {
      objArrayOop stom = oopFactory::new_objArray(SystemDictionary::Object_klass(), map_length, CHECK);
      Handle refs_handle (THREAD, (oop)stom);  // must handleize.

      ClassLoaderData* loader_data = pool_holder()->class_loader_data();
      set_resolved_references(loader_data->add_handle(refs_handle));
    }
  }
}

void ConstantPool::remove_unshareable_info() {
  // Resolved references are not in the shared archive.
  // Save the length for restoration.  It is not necessarily the same length
  // as reference_map.length() if invokedynamic is saved.
  set_resolved_reference_length(
    resolved_references() != NULL ? resolved_references()->length() : 0);
  set_resolved_references(NULL);
}

int ConstantPool::cp_to_object_index(int cp_index) {
  // this is harder don't do this so much.
  int i = reference_map()->find(cp_index);
  // We might not find the index for jsr292 call.
  return (i < 0) ? _no_index_sentinel : i;
}

void ConstantPool::trace_class_resolution(const constantPoolHandle& this_cp, KlassHandle k) {
  ResourceMark rm;
  int line_number = -1;
  const char * source_file = NULL;
  if (JavaThread::current()->has_last_Java_frame()) {
    // try to identify the method which called this function.
    vframeStream vfst(JavaThread::current());
    if (!vfst.at_end()) {
      line_number = vfst.method()->line_number_from_bci(vfst.bci());
      Symbol* s = vfst.method()->method_holder()->source_file_name();
      if (s != NULL) {
        source_file = s->as_C_string();
      }
    }
  }
  if (k() != this_cp->pool_holder()) {
    // only print something if the classes are different
    if (source_file != NULL) {
      log_info(classresolve)("%s %s %s:%d",
                 this_cp->pool_holder()->external_name(),
                 k->external_name(), source_file, line_number);
    } else {
      log_info(classresolve)("%s %s",
                 this_cp->pool_holder()->external_name(),
                 k->external_name());
    }
  }
}

Klass* ConstantPool::klass_at_impl(const constantPoolHandle& this_cp, int which,
                                   bool save_resolution_error, TRAPS) {
  assert(THREAD->is_Java_thread(), "must be a Java thread");

  // A resolved constantPool entry will contain a Klass*, otherwise a Symbol*.
  // It is not safe to rely on the tag bit's here, since we don't have a lock, and
  // the entry and tag is not updated atomicly.
  CPSlot entry = this_cp->slot_at(which);
  if (entry.is_resolved()) {
    assert(entry.get_klass()->is_klass(), "must be");
    // Already resolved - return entry.
    return entry.get_klass();
  }

  // This tag doesn't change back to unresolved class unless at a safepoint.
  if (this_cp->tag_at(which).is_unresolved_klass_in_error()) {
    // The original attempt to resolve this constant pool entry failed so find the
    // class of the original error and throw another error of the same class
    // (JVMS 5.4.3).
    // If there is a detail message, pass that detail message to the error.
    // The JVMS does not strictly require us to duplicate the same detail message,
    // or any internal exception fields such as cause or stacktrace.  But since the
    // detail message is often a class name or other literal string, we will repeat it
    // if we can find it in the symbol table.
    throw_resolution_error(this_cp, which, CHECK_0);
    ShouldNotReachHere();
  }

  Handle mirror_handle;
  Symbol* name = entry.get_symbol();
  Handle loader (THREAD, this_cp->pool_holder()->class_loader());
  Handle protection_domain (THREAD, this_cp->pool_holder()->protection_domain());
  Klass* kk = SystemDictionary::resolve_or_fail(name, loader, protection_domain, true, THREAD);
  KlassHandle k (THREAD, kk);
  if (!HAS_PENDING_EXCEPTION) {
    // preserve the resolved klass from unloading
    mirror_handle = Handle(THREAD, kk->java_mirror());
    // Do access check for klasses
    verify_constant_pool_resolve(this_cp, k, THREAD);
  }

  // Failed to resolve class. We must record the errors so that subsequent attempts
  // to resolve this constant pool entry fail with the same error (JVMS 5.4.3).
  if (HAS_PENDING_EXCEPTION) {
    if (save_resolution_error) {
      save_and_throw_exception(this_cp, which, constantTag(JVM_CONSTANT_UnresolvedClass), CHECK_NULL);
      // If CHECK_NULL above doesn't return the exception, that means that
      // some other thread has beaten us and has resolved the class.
      // To preserve old behavior, we return the resolved class.
      entry = this_cp->resolved_klass_at(which);
      assert(entry.is_resolved(), "must be resolved if exception was cleared");
      assert(entry.get_klass()->is_klass(), "must be resolved to a klass");
      return entry.get_klass();
    } else {
      return NULL;  // return the pending exception
    }
  }

  // Make this class loader depend upon the class loader owning the class reference
  ClassLoaderData* this_key = this_cp->pool_holder()->class_loader_data();
  this_key->record_dependency(k(), CHECK_NULL); // Can throw OOM

  if (log_is_enabled(Info, classresolve) && !k->is_array_klass()) {
    // skip resolving the constant pool so that this code gets
    // called the next time some bytecodes refer to this class.
    trace_class_resolution(this_cp, k);
    return k();
  } else {
    this_cp->klass_at_put(which, k());
  }

  entry = this_cp->resolved_klass_at(which);
  assert(entry.is_resolved() && entry.get_klass()->is_klass(), "must be resolved at this point");
  return entry.get_klass();
}


// Does not update ConstantPool* - to avoid any exception throwing. Used
// by compiler and exception handling.  Also used to avoid classloads for
// instanceof operations. Returns NULL if the class has not been loaded or
// if the verification of constant pool failed
Klass* ConstantPool::klass_at_if_loaded(const constantPoolHandle& this_cp, int which) {
  CPSlot entry = this_cp->slot_at(which);
  if (entry.is_resolved()) {
    assert(entry.get_klass()->is_klass(), "must be");
    return entry.get_klass();
  } else {
    assert(entry.is_unresolved(), "must be either symbol or klass");
    Thread *thread = Thread::current();
    Symbol* name = entry.get_symbol();
    oop loader = this_cp->pool_holder()->class_loader();
    oop protection_domain = this_cp->pool_holder()->protection_domain();
    Handle h_prot (thread, protection_domain);
    Handle h_loader (thread, loader);
    Klass* k = SystemDictionary::find(name, h_loader, h_prot, thread);

    if (k != NULL) {
      // Make sure that resolving is legal
      EXCEPTION_MARK;
      KlassHandle klass(THREAD, k);
      // return NULL if verification fails
      verify_constant_pool_resolve(this_cp, klass, THREAD);
      if (HAS_PENDING_EXCEPTION) {
        CLEAR_PENDING_EXCEPTION;
        return NULL;
      }
      return klass();
    } else {
      return k;
    }
  }
}


Klass* ConstantPool::klass_ref_at_if_loaded(const constantPoolHandle& this_cp, int which) {
  return klass_at_if_loaded(this_cp, this_cp->klass_ref_index_at(which));
}


Method* ConstantPool::method_at_if_loaded(const constantPoolHandle& cpool,
                                                   int which) {
  if (cpool->cache() == NULL)  return NULL;  // nothing to load yet
  int cache_index = decode_cpcache_index(which, true);
  if (!(cache_index >= 0 && cache_index < cpool->cache()->length())) {
    // FIXME: should be an assert
    if (PrintMiscellaneous && (Verbose||WizardMode)) {
      tty->print_cr("bad operand %d in:", which); cpool->print();
    }
    return NULL;
  }
  ConstantPoolCacheEntry* e = cpool->cache()->entry_at(cache_index);
  return e->method_if_resolved(cpool);
}


bool ConstantPool::has_appendix_at_if_loaded(const constantPoolHandle& cpool, int which) {
  if (cpool->cache() == NULL)  return false;  // nothing to load yet
  int cache_index = decode_cpcache_index(which, true);
  ConstantPoolCacheEntry* e = cpool->cache()->entry_at(cache_index);
  return e->has_appendix();
}

oop ConstantPool::appendix_at_if_loaded(const constantPoolHandle& cpool, int which) {
  if (cpool->cache() == NULL)  return NULL;  // nothing to load yet
  int cache_index = decode_cpcache_index(which, true);
  ConstantPoolCacheEntry* e = cpool->cache()->entry_at(cache_index);
  return e->appendix_if_resolved(cpool);
}


bool ConstantPool::has_method_type_at_if_loaded(const constantPoolHandle& cpool, int which) {
  if (cpool->cache() == NULL)  return false;  // nothing to load yet
  int cache_index = decode_cpcache_index(which, true);
  ConstantPoolCacheEntry* e = cpool->cache()->entry_at(cache_index);
  return e->has_method_type();
}

oop ConstantPool::method_type_at_if_loaded(const constantPoolHandle& cpool, int which) {
  if (cpool->cache() == NULL)  return NULL;  // nothing to load yet
  int cache_index = decode_cpcache_index(which, true);
  ConstantPoolCacheEntry* e = cpool->cache()->entry_at(cache_index);
  return e->method_type_if_resolved(cpool);
}


Symbol* ConstantPool::impl_name_ref_at(int which, bool uncached) {
  int name_index = name_ref_index_at(impl_name_and_type_ref_index_at(which, uncached));
  return symbol_at(name_index);
}


Symbol* ConstantPool::impl_signature_ref_at(int which, bool uncached) {
  int signature_index = signature_ref_index_at(impl_name_and_type_ref_index_at(which, uncached));
  return symbol_at(signature_index);
}


int ConstantPool::impl_name_and_type_ref_index_at(int which, bool uncached) {
  int i = which;
  if (!uncached && cache() != NULL) {
    if (ConstantPool::is_invokedynamic_index(which)) {
      // Invokedynamic index is index into resolved_references
      int pool_index = invokedynamic_cp_cache_entry_at(which)->constant_pool_index();
      pool_index = invoke_dynamic_name_and_type_ref_index_at(pool_index);
      assert(tag_at(pool_index).is_name_and_type(), "");
      return pool_index;
    }
    // change byte-ordering and go via cache
    i = remap_instruction_operand_from_cache(which);
  } else {
    if (tag_at(which).is_invoke_dynamic()) {
      int pool_index = invoke_dynamic_name_and_type_ref_index_at(which);
      assert(tag_at(pool_index).is_name_and_type(), "");
      return pool_index;
    }
  }
  assert(tag_at(i).is_field_or_method(), "Corrupted constant pool");
  assert(!tag_at(i).is_invoke_dynamic(), "Must be handled above");
  jint ref_index = *int_at_addr(i);
  return extract_high_short_from_int(ref_index);
}


int ConstantPool::impl_klass_ref_index_at(int which, bool uncached) {
  guarantee(!ConstantPool::is_invokedynamic_index(which),
            "an invokedynamic instruction does not have a klass");
  int i = which;
  if (!uncached && cache() != NULL) {
    // change byte-ordering and go via cache
    i = remap_instruction_operand_from_cache(which);
  }
  assert(tag_at(i).is_field_or_method(), "Corrupted constant pool");
  jint ref_index = *int_at_addr(i);
  return extract_low_short_from_int(ref_index);
}



int ConstantPool::remap_instruction_operand_from_cache(int operand) {
  int cpc_index = operand;
  DEBUG_ONLY(cpc_index -= CPCACHE_INDEX_TAG);
  assert((int)(u2)cpc_index == cpc_index, "clean u2");
  int member_index = cache()->entry_at(cpc_index)->constant_pool_index();
  return member_index;
}


void ConstantPool::verify_constant_pool_resolve(const constantPoolHandle& this_cp, KlassHandle k, TRAPS) {
 if (k->is_instance_klass() || k->is_objArray_klass()) {
    instanceKlassHandle holder (THREAD, this_cp->pool_holder());
    Klass* elem = k->is_instance_klass() ? k() : ObjArrayKlass::cast(k())->bottom_klass();
    KlassHandle element (THREAD, elem);

    // The element type could be a typeArray - we only need the access check if it is
    // an reference to another class
    if (element->is_instance_klass()) {
      LinkResolver::check_klass_accessability(holder, element, CHECK);
    }
  }
}


int ConstantPool::name_ref_index_at(int which_nt) {
  jint ref_index = name_and_type_at(which_nt);
  return extract_low_short_from_int(ref_index);
}


int ConstantPool::signature_ref_index_at(int which_nt) {
  jint ref_index = name_and_type_at(which_nt);
  return extract_high_short_from_int(ref_index);
}


Klass* ConstantPool::klass_ref_at(int which, TRAPS) {
  return klass_at(klass_ref_index_at(which), THREAD);
}


Symbol* ConstantPool::klass_name_at(int which) const {
  assert(tag_at(which).is_unresolved_klass() || tag_at(which).is_klass(),
         "Corrupted constant pool");
  // A resolved constantPool entry will contain a Klass*, otherwise a Symbol*.
  // It is not safe to rely on the tag bit's here, since we don't have a lock, and the entry and
  // tag is not updated atomicly.
  CPSlot entry = slot_at(which);
  if (entry.is_resolved()) {
    // Already resolved - return entry's name.
    assert(entry.get_klass()->is_klass(), "must be");
    return entry.get_klass()->name();
  } else {
    assert(entry.is_unresolved(), "must be either symbol or klass");
    return entry.get_symbol();
  }
}

Symbol* ConstantPool::klass_ref_at_noresolve(int which) {
  jint ref_index = klass_ref_index_at(which);
  return klass_at_noresolve(ref_index);
}

Symbol* ConstantPool::uncached_klass_ref_at_noresolve(int which) {
  jint ref_index = uncached_klass_ref_index_at(which);
  return klass_at_noresolve(ref_index);
}

char* ConstantPool::string_at_noresolve(int which) {
  return unresolved_string_at(which)->as_C_string();
}

BasicType ConstantPool::basic_type_for_signature_at(int which) const {
  return FieldType::basic_type(symbol_at(which));
}


void ConstantPool::resolve_string_constants_impl(const constantPoolHandle& this_cp, TRAPS) {
  for (int index = 1; index < this_cp->length(); index++) { // Index 0 is unused
    if (this_cp->tag_at(index).is_string()) {
      this_cp->string_at(index, CHECK);
    }
  }
}

// Resolve all the classes in the constant pool.  If they are all resolved,
// the constant pool is read-only.  Enhancement: allocate cp entries to
// another metaspace, and copy to read-only or read-write space if this
// bit is set.
bool ConstantPool::resolve_class_constants(TRAPS) {
  constantPoolHandle cp(THREAD, this);
  for (int index = 1; index < length(); index++) { // Index 0 is unused
    if (tag_at(index).is_unresolved_klass() &&
        klass_at_if_loaded(cp, index) == NULL) {
      return false;
  }
  }
  // set_preresolution(); or some bit for future use
  return true;
}

Symbol* ConstantPool::exception_message(const constantPoolHandle& this_cp, int which, constantTag tag, oop pending_exception) {
  // Dig out the detailed message to reuse if possible
  Symbol* message = java_lang_Throwable::detail_message(pending_exception);
  if (message != NULL) {
    return message;
  }

  // Return specific message for the tag
  switch (tag.value()) {
  case JVM_CONSTANT_UnresolvedClass:
    // return the class name in the error message
    message = this_cp->klass_name_at(which);
    break;
  case JVM_CONSTANT_MethodHandle:
    // return the method handle name in the error message
    message = this_cp->method_handle_name_ref_at(which);
    break;
  case JVM_CONSTANT_MethodType:
    // return the method type signature in the error message
    message = this_cp->method_type_signature_at(which);
    break;
  default:
    ShouldNotReachHere();
  }

  return message;
}

void ConstantPool::throw_resolution_error(const constantPoolHandle& this_cp, int which, TRAPS) {
  Symbol* message = NULL;
  Symbol* error = SystemDictionary::find_resolution_error(this_cp, which, &message);
  assert(error != NULL && message != NULL, "checking");
  CLEAR_PENDING_EXCEPTION;
  ResourceMark rm;
  THROW_MSG(error, message->as_C_string());
}

// If resolution for Class, MethodHandle or MethodType fails, save the exception
// in the resolution error table, so that the same exception is thrown again.
void ConstantPool::save_and_throw_exception(const constantPoolHandle& this_cp, int which,
                                            constantTag tag, TRAPS) {
  Symbol* error = PENDING_EXCEPTION->klass()->name();

  int error_tag = tag.error_value();

  if (!PENDING_EXCEPTION->
    is_a(SystemDictionary::LinkageError_klass())) {
    // Just throw the exception and don't prevent these classes from
    // being loaded due to virtual machine errors like StackOverflow
    // and OutOfMemoryError, etc, or if the thread was hit by stop()
    // Needs clarification to section 5.4.3 of the VM spec (see 6308271)
  } else if (this_cp->tag_at(which).value() != error_tag) {
    Symbol* message = exception_message(this_cp, which, tag, PENDING_EXCEPTION);
    SystemDictionary::add_resolution_error(this_cp, which, error, message);
    // CAS in the tag.  If a thread beat us to registering this error that's fine.
    // If another thread resolved the reference, this is a race condition. This
    // thread may have had a security manager or something temporary.
    // This doesn't deterministically get an error.   So why do we save this?
    // We save this because jvmti can add classes to the bootclass path after
    // this error, so it needs to get the same error if the error is first.
    jbyte old_tag = Atomic::cmpxchg((jbyte)error_tag,
                            (jbyte*)this_cp->tag_addr_at(which), (jbyte)tag.value());
    if (old_tag != error_tag && old_tag != tag.value()) {
      // MethodHandles and MethodType doesn't change to resolved version.
      assert(this_cp->tag_at(which).is_klass(), "Wrong tag value");
      // Forget the exception and use the resolved class.
      CLEAR_PENDING_EXCEPTION;
    }
  } else {
    // some other thread put this in error state
    throw_resolution_error(this_cp, which, CHECK);
  }
}

// Called to resolve constants in the constant pool and return an oop.
// Some constant pool entries cache their resolved oop. This is also
// called to create oops from constants to use in arguments for invokedynamic
oop ConstantPool::resolve_constant_at_impl(const constantPoolHandle& this_cp, int index, int cache_index, TRAPS) {
  oop result_oop = NULL;
  Handle throw_exception;

  if (cache_index == _possible_index_sentinel) {
    // It is possible that this constant is one which is cached in the objects.
    // We'll do a linear search.  This should be OK because this usage is rare.
    assert(index > 0, "valid index");
    cache_index = this_cp->cp_to_object_index(index);
  }
  assert(cache_index == _no_index_sentinel || cache_index >= 0, "");
  assert(index == _no_index_sentinel || index >= 0, "");

  if (cache_index >= 0) {
    result_oop = this_cp->resolved_references()->obj_at(cache_index);
    if (result_oop != NULL) {
      return result_oop;
      // That was easy...
    }
    index = this_cp->object_to_cp_index(cache_index);
  }

  jvalue prim_value;  // temp used only in a few cases below

  constantTag tag = this_cp->tag_at(index);

  switch (tag.value()) {

  case JVM_CONSTANT_UnresolvedClass:
  case JVM_CONSTANT_UnresolvedClassInError:
  case JVM_CONSTANT_Class:
    {
      assert(cache_index == _no_index_sentinel, "should not have been set");
      Klass* resolved = klass_at_impl(this_cp, index, true, CHECK_NULL);
      // ldc wants the java mirror.
      result_oop = resolved->java_mirror();
      break;
    }

  case JVM_CONSTANT_String:
    assert(cache_index != _no_index_sentinel, "should have been set");
    if (this_cp->is_pseudo_string_at(index)) {
      result_oop = this_cp->pseudo_string_at(index, cache_index);
      break;
    }
    result_oop = string_at_impl(this_cp, index, cache_index, CHECK_NULL);
    break;

  case JVM_CONSTANT_MethodHandleInError:
  case JVM_CONSTANT_MethodTypeInError:
    {
      throw_resolution_error(this_cp, index, CHECK_NULL);
      break;
    }

  case JVM_CONSTANT_MethodHandle:
    {
      int ref_kind                 = this_cp->method_handle_ref_kind_at(index);
      int callee_index             = this_cp->method_handle_klass_index_at(index);
      Symbol*  name =      this_cp->method_handle_name_ref_at(index);
      Symbol*  signature = this_cp->method_handle_signature_ref_at(index);
      if (PrintMiscellaneous)
        tty->print_cr("resolve JVM_CONSTANT_MethodHandle:%d [%d/%d/%d] %s.%s",
                      ref_kind, index, this_cp->method_handle_index_at(index),
                      callee_index, name->as_C_string(), signature->as_C_string());
      KlassHandle callee;
      { Klass* k = klass_at_impl(this_cp, callee_index, true, CHECK_NULL);
        callee = KlassHandle(THREAD, k);
      }
      KlassHandle klass(THREAD, this_cp->pool_holder());
      Handle value = SystemDictionary::link_method_handle_constant(klass, ref_kind,
                                                                   callee, name, signature,
                                                                   THREAD);
      result_oop = value();
      if (HAS_PENDING_EXCEPTION) {
        save_and_throw_exception(this_cp, index, tag, CHECK_NULL);
      }
      break;
    }

  case JVM_CONSTANT_MethodType:
    {
      Symbol*  signature = this_cp->method_type_signature_at(index);
      if (PrintMiscellaneous)
        tty->print_cr("resolve JVM_CONSTANT_MethodType [%d/%d] %s",
                      index, this_cp->method_type_index_at(index),
                      signature->as_C_string());
      KlassHandle klass(THREAD, this_cp->pool_holder());
      Handle value = SystemDictionary::find_method_handle_type(signature, klass, THREAD);
      result_oop = value();
      if (HAS_PENDING_EXCEPTION) {
        save_and_throw_exception(this_cp, index, tag, CHECK_NULL);
      }
      break;
    }

  case JVM_CONSTANT_Integer:
    assert(cache_index == _no_index_sentinel, "should not have been set");
    prim_value.i = this_cp->int_at(index);
    result_oop = java_lang_boxing_object::create(T_INT, &prim_value, CHECK_NULL);
    break;

  case JVM_CONSTANT_Float:
    assert(cache_index == _no_index_sentinel, "should not have been set");
    prim_value.f = this_cp->float_at(index);
    result_oop = java_lang_boxing_object::create(T_FLOAT, &prim_value, CHECK_NULL);
    break;

  case JVM_CONSTANT_Long:
    assert(cache_index == _no_index_sentinel, "should not have been set");
    prim_value.j = this_cp->long_at(index);
    result_oop = java_lang_boxing_object::create(T_LONG, &prim_value, CHECK_NULL);
    break;

  case JVM_CONSTANT_Double:
    assert(cache_index == _no_index_sentinel, "should not have been set");
    prim_value.d = this_cp->double_at(index);
    result_oop = java_lang_boxing_object::create(T_DOUBLE, &prim_value, CHECK_NULL);
    break;

  default:
    DEBUG_ONLY( tty->print_cr("*** %p: tag at CP[%d/%d] = %d",
                              this_cp(), index, cache_index, tag.value()));
    assert(false, "unexpected constant tag");
    break;
  }

  if (cache_index >= 0) {
    // Benign race condition:  resolved_references may already be filled in.
    // The important thing here is that all threads pick up the same result.
    // It doesn't matter which racing thread wins, as long as only one
    // result is used by all threads, and all future queries.
    oop old_result = this_cp->resolved_references()->atomic_compare_exchange_oop(cache_index, result_oop, NULL);
    if (old_result == NULL) {
      return result_oop;  // was installed
    } else {
      // Return the winning thread's result.  This can be different than
      // the result here for MethodHandles.
      return old_result;
    }
  } else {
    return result_oop;
  }
}

oop ConstantPool::uncached_string_at(int which, TRAPS) {
  Symbol* sym = unresolved_string_at(which);
  oop str = StringTable::intern(sym, CHECK_(NULL));
  assert(java_lang_String::is_instance(str), "must be string");
  return str;
}


oop ConstantPool::resolve_bootstrap_specifier_at_impl(const constantPoolHandle& this_cp, int index, TRAPS) {
  assert(this_cp->tag_at(index).is_invoke_dynamic(), "Corrupted constant pool");

  Handle bsm;
  int argc;
  {
    // JVM_CONSTANT_InvokeDynamic is an ordered pair of [bootm, name&type], plus optional arguments
    // The bootm, being a JVM_CONSTANT_MethodHandle, has its own cache entry.
    // It is accompanied by the optional arguments.
    int bsm_index = this_cp->invoke_dynamic_bootstrap_method_ref_index_at(index);
    oop bsm_oop = this_cp->resolve_possibly_cached_constant_at(bsm_index, CHECK_NULL);
    if (!java_lang_invoke_MethodHandle::is_instance(bsm_oop)) {
      THROW_MSG_NULL(vmSymbols::java_lang_LinkageError(), "BSM not an MethodHandle");
    }

    // Extract the optional static arguments.
    argc = this_cp->invoke_dynamic_argument_count_at(index);
    if (argc == 0)  return bsm_oop;

    bsm = Handle(THREAD, bsm_oop);
  }

  objArrayHandle info;
  {
    objArrayOop info_oop = oopFactory::new_objArray(SystemDictionary::Object_klass(), 1+argc, CHECK_NULL);
    info = objArrayHandle(THREAD, info_oop);
  }

  info->obj_at_put(0, bsm());
  for (int i = 0; i < argc; i++) {
    int arg_index = this_cp->invoke_dynamic_argument_index_at(index, i);
    oop arg_oop = this_cp->resolve_possibly_cached_constant_at(arg_index, CHECK_NULL);
    info->obj_at_put(1+i, arg_oop);
  }

  return info();
}

oop ConstantPool::string_at_impl(const constantPoolHandle& this_cp, int which, int obj_index, TRAPS) {
  // If the string has already been interned, this entry will be non-null
  oop str = this_cp->resolved_references()->obj_at(obj_index);
  if (str != NULL) return str;
  Symbol* sym = this_cp->unresolved_string_at(which);
  str = StringTable::intern(sym, CHECK_(NULL));
  this_cp->string_at_put(which, obj_index, str);
  assert(java_lang_String::is_instance(str), "must be string");
  return str;
}


bool ConstantPool::klass_name_at_matches(instanceKlassHandle k,
                                                int which) {
  // Names are interned, so we can compare Symbol*s directly
  Symbol* cp_name = klass_name_at(which);
  return (cp_name == k->name());
}


// Iterate over symbols and decrement ones which are Symbol*s
// This is done during GC.
// Only decrement the UTF8 symbols. Unresolved classes and strings point to
// these symbols but didn't increment the reference count.
void ConstantPool::unreference_symbols() {
  for (int index = 1; index < length(); index++) { // Index 0 is unused
    constantTag tag = tag_at(index);
    if (tag.is_symbol()) {
      symbol_at(index)->decrement_refcount();
    }
  }
}


// Compare this constant pool's entry at index1 to the constant pool
// cp2's entry at index2.
bool ConstantPool::compare_entry_to(int index1, const constantPoolHandle& cp2,
       int index2, TRAPS) {

  // The error tags are equivalent to non-error tags when comparing
  jbyte t1 = tag_at(index1).non_error_value();
  jbyte t2 = cp2->tag_at(index2).non_error_value();

  if (t1 != t2) {
    // Not the same entry type so there is nothing else to check. Note
    // that this style of checking will consider resolved/unresolved
    // class pairs as different.
    // From the ConstantPool* API point of view, this is correct
    // behavior. See VM_RedefineClasses::merge_constant_pools() to see how this
    // plays out in the context of ConstantPool* merging.
    return false;
  }

  switch (t1) {
  case JVM_CONSTANT_Class:
  {
    Klass* k1 = klass_at(index1, CHECK_false);
    Klass* k2 = cp2->klass_at(index2, CHECK_false);
    if (k1 == k2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_ClassIndex:
  {
    int recur1 = klass_index_at(index1);
    int recur2 = cp2->klass_index_at(index2);
    bool match = compare_entry_to(recur1, cp2, recur2, CHECK_false);
    if (match) {
      return true;
    }
  } break;

  case JVM_CONSTANT_Double:
  {
    jdouble d1 = double_at(index1);
    jdouble d2 = cp2->double_at(index2);
    if (d1 == d2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_Fieldref:
  case JVM_CONSTANT_InterfaceMethodref:
  case JVM_CONSTANT_Methodref:
  {
    int recur1 = uncached_klass_ref_index_at(index1);
    int recur2 = cp2->uncached_klass_ref_index_at(index2);
    bool match = compare_entry_to(recur1, cp2, recur2, CHECK_false);
    if (match) {
      recur1 = uncached_name_and_type_ref_index_at(index1);
      recur2 = cp2->uncached_name_and_type_ref_index_at(index2);
      match = compare_entry_to(recur1, cp2, recur2, CHECK_false);
      if (match) {
        return true;
      }
    }
  } break;

  case JVM_CONSTANT_Float:
  {
    jfloat f1 = float_at(index1);
    jfloat f2 = cp2->float_at(index2);
    if (f1 == f2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_Integer:
  {
    jint i1 = int_at(index1);
    jint i2 = cp2->int_at(index2);
    if (i1 == i2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_Long:
  {
    jlong l1 = long_at(index1);
    jlong l2 = cp2->long_at(index2);
    if (l1 == l2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_NameAndType:
  {
    int recur1 = name_ref_index_at(index1);
    int recur2 = cp2->name_ref_index_at(index2);
    bool match = compare_entry_to(recur1, cp2, recur2, CHECK_false);
    if (match) {
      recur1 = signature_ref_index_at(index1);
      recur2 = cp2->signature_ref_index_at(index2);
      match = compare_entry_to(recur1, cp2, recur2, CHECK_false);
      if (match) {
        return true;
      }
    }
  } break;

  case JVM_CONSTANT_StringIndex:
  {
    int recur1 = string_index_at(index1);
    int recur2 = cp2->string_index_at(index2);
    bool match = compare_entry_to(recur1, cp2, recur2, CHECK_false);
    if (match) {
      return true;
    }
  } break;

  case JVM_CONSTANT_UnresolvedClass:
  {
    Symbol* k1 = klass_name_at(index1);
    Symbol* k2 = cp2->klass_name_at(index2);
    if (k1 == k2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_MethodType:
  {
    int k1 = method_type_index_at_error_ok(index1);
    int k2 = cp2->method_type_index_at_error_ok(index2);
    bool match = compare_entry_to(k1, cp2, k2, CHECK_false);
    if (match) {
      return true;
    }
  } break;

  case JVM_CONSTANT_MethodHandle:
  {
    int k1 = method_handle_ref_kind_at_error_ok(index1);
    int k2 = cp2->method_handle_ref_kind_at_error_ok(index2);
    if (k1 == k2) {
      int i1 = method_handle_index_at_error_ok(index1);
      int i2 = cp2->method_handle_index_at_error_ok(index2);
      bool match = compare_entry_to(i1, cp2, i2, CHECK_false);
      if (match) {
        return true;
      }
    }
  } break;

  case JVM_CONSTANT_InvokeDynamic:
  {
    int k1 = invoke_dynamic_name_and_type_ref_index_at(index1);
    int k2 = cp2->invoke_dynamic_name_and_type_ref_index_at(index2);
    int i1 = invoke_dynamic_bootstrap_specifier_index(index1);
    int i2 = cp2->invoke_dynamic_bootstrap_specifier_index(index2);
    // separate statements and variables because CHECK_false is used
    bool match_entry = compare_entry_to(k1, cp2, k2, CHECK_false);
    bool match_operand = compare_operand_to(i1, cp2, i2, CHECK_false);
    return (match_entry && match_operand);
  } break;

  case JVM_CONSTANT_String:
  {
    Symbol* s1 = unresolved_string_at(index1);
    Symbol* s2 = cp2->unresolved_string_at(index2);
    if (s1 == s2) {
      return true;
    }
  } break;

  case JVM_CONSTANT_Utf8:
  {
    Symbol* s1 = symbol_at(index1);
    Symbol* s2 = cp2->symbol_at(index2);
    if (s1 == s2) {
      return true;
    }
  } break;

  // Invalid is used as the tag for the second constant pool entry
  // occupied by JVM_CONSTANT_Double or JVM_CONSTANT_Long. It should
  // not be seen by itself.
  case JVM_CONSTANT_Invalid: // fall through

  default:
    ShouldNotReachHere();
    break;
  }

  return false;
} // end compare_entry_to()


// Resize the operands array with delta_len and delta_size.
// Used in RedefineClasses for CP merge.
void ConstantPool::resize_operands(int delta_len, int delta_size, TRAPS) {
  int old_len  = operand_array_length(operands());
  int new_len  = old_len + delta_len;
  int min_len  = (delta_len > 0) ? old_len : new_len;

  int old_size = operands()->length();
  int new_size = old_size + delta_size;
  int min_size = (delta_size > 0) ? old_size : new_size;

  ClassLoaderData* loader_data = pool_holder()->class_loader_data();
  Array<u2>* new_ops = MetadataFactory::new_array<u2>(loader_data, new_size, CHECK);

  // Set index in the resized array for existing elements only
  for (int idx = 0; idx < min_len; idx++) {
    int offset = operand_offset_at(idx);                       // offset in original array
    operand_offset_at_put(new_ops, idx, offset + 2*delta_len); // offset in resized array
  }
  // Copy the bootstrap specifiers only
  Copy::conjoint_memory_atomic(operands()->adr_at(2*old_len),
                               new_ops->adr_at(2*new_len),
                               (min_size - 2*min_len) * sizeof(u2));
  // Explicitly deallocate old operands array.
  // Note, it is not needed for 7u backport.
  if ( operands() != NULL) { // the safety check
    MetadataFactory::free_array<u2>(loader_data, operands());
  }
  set_operands(new_ops);
} // end resize_operands()


// Extend the operands array with the length and size of the ext_cp operands.
// Used in RedefineClasses for CP merge.
void ConstantPool::extend_operands(const constantPoolHandle& ext_cp, TRAPS) {
  int delta_len = operand_array_length(ext_cp->operands());
  if (delta_len == 0) {
    return; // nothing to do
  }
  int delta_size = ext_cp->operands()->length();

  assert(delta_len  > 0 && delta_size > 0, "extended operands array must be bigger");

  if (operand_array_length(operands()) == 0) {
    ClassLoaderData* loader_data = pool_holder()->class_loader_data();
    Array<u2>* new_ops = MetadataFactory::new_array<u2>(loader_data, delta_size, CHECK);
    // The first element index defines the offset of second part
    operand_offset_at_put(new_ops, 0, 2*delta_len); // offset in new array
    set_operands(new_ops);
  } else {
    resize_operands(delta_len, delta_size, CHECK);
  }

} // end extend_operands()


// Shrink the operands array to a smaller array with new_len length.
// Used in RedefineClasses for CP merge.
void ConstantPool::shrink_operands(int new_len, TRAPS) {
  int old_len = operand_array_length(operands());
  if (new_len == old_len) {
    return; // nothing to do
  }
  assert(new_len < old_len, "shrunken operands array must be smaller");

  int free_base  = operand_next_offset_at(new_len - 1);
  int delta_len  = new_len - old_len;
  int delta_size = 2*delta_len + free_base - operands()->length();

  resize_operands(delta_len, delta_size, CHECK);

} // end shrink_operands()


void ConstantPool::copy_operands(const constantPoolHandle& from_cp,
                                 const constantPoolHandle& to_cp,
                                 TRAPS) {

  int from_oplen = operand_array_length(from_cp->operands());
  int old_oplen  = operand_array_length(to_cp->operands());
  if (from_oplen != 0) {
    ClassLoaderData* loader_data = to_cp->pool_holder()->class_loader_data();
    // append my operands to the target's operands array
    if (old_oplen == 0) {
      // Can't just reuse from_cp's operand list because of deallocation issues
      int len = from_cp->operands()->length();
      Array<u2>* new_ops = MetadataFactory::new_array<u2>(loader_data, len, CHECK);
      Copy::conjoint_memory_atomic(
          from_cp->operands()->adr_at(0), new_ops->adr_at(0), len * sizeof(u2));
      to_cp->set_operands(new_ops);
    } else {
      int old_len  = to_cp->operands()->length();
      int from_len = from_cp->operands()->length();
      int old_off  = old_oplen * sizeof(u2);
      int from_off = from_oplen * sizeof(u2);
      // Use the metaspace for the destination constant pool
      Array<u2>* new_operands = MetadataFactory::new_array<u2>(loader_data, old_len + from_len, CHECK);
      int fillp = 0, len = 0;
      // first part of dest
      Copy::conjoint_memory_atomic(to_cp->operands()->adr_at(0),
                                   new_operands->adr_at(fillp),
                                   (len = old_off) * sizeof(u2));
      fillp += len;
      // first part of src
      Copy::conjoint_memory_atomic(from_cp->operands()->adr_at(0),
                                   new_operands->adr_at(fillp),
                                   (len = from_off) * sizeof(u2));
      fillp += len;
      // second part of dest
      Copy::conjoint_memory_atomic(to_cp->operands()->adr_at(old_off),
                                   new_operands->adr_at(fillp),
                                   (len = old_len - old_off) * sizeof(u2));
      fillp += len;
      // second part of src
      Copy::conjoint_memory_atomic(from_cp->operands()->adr_at(from_off),
                                   new_operands->adr_at(fillp),
                                   (len = from_len - from_off) * sizeof(u2));
      fillp += len;
      assert(fillp == new_operands->length(), "");

      // Adjust indexes in the first part of the copied operands array.
      for (int j = 0; j < from_oplen; j++) {
        int offset = operand_offset_at(new_operands, old_oplen + j);
        assert(offset == operand_offset_at(from_cp->operands(), j), "correct copy");
        offset += old_len;  // every new tuple is preceded by old_len extra u2's
        operand_offset_at_put(new_operands, old_oplen + j, offset);
      }

      // replace target operands array with combined array
      to_cp->set_operands(new_operands);
    }
  }
} // end copy_operands()


// Copy this constant pool's entries at start_i to end_i (inclusive)
// to the constant pool to_cp's entries starting at to_i. A total of
// (end_i - start_i) + 1 entries are copied.
void ConstantPool::copy_cp_to_impl(const constantPoolHandle& from_cp, int start_i, int end_i,
       const constantPoolHandle& to_cp, int to_i, TRAPS) {


  int dest_i = to_i;  // leave original alone for debug purposes

  for (int src_i = start_i; src_i <= end_i; /* see loop bottom */ ) {
    copy_entry_to(from_cp, src_i, to_cp, dest_i, CHECK);

    switch (from_cp->tag_at(src_i).value()) {
    case JVM_CONSTANT_Double:
    case JVM_CONSTANT_Long:
      // double and long take two constant pool entries
      src_i += 2;
      dest_i += 2;
      break;

    default:
      // all others take one constant pool entry
      src_i++;
      dest_i++;
      break;
    }
  }
  copy_operands(from_cp, to_cp, CHECK);

} // end copy_cp_to_impl()


// Copy this constant pool's entry at from_i to the constant pool
// to_cp's entry at to_i.
void ConstantPool::copy_entry_to(const constantPoolHandle& from_cp, int from_i,
                                        const constantPoolHandle& to_cp, int to_i,
                                        TRAPS) {

  int tag = from_cp->tag_at(from_i).value();
  switch (tag) {
  case JVM_CONSTANT_Class:
  {
    Klass* k = from_cp->klass_at(from_i, CHECK);
    to_cp->klass_at_put(to_i, k);
  } break;

  case JVM_CONSTANT_ClassIndex:
  {
    jint ki = from_cp->klass_index_at(from_i);
    to_cp->klass_index_at_put(to_i, ki);
  } break;

  case JVM_CONSTANT_Double:
  {
    jdouble d = from_cp->double_at(from_i);
    to_cp->double_at_put(to_i, d);
    // double takes two constant pool entries so init second entry's tag
    to_cp->tag_at_put(to_i + 1, JVM_CONSTANT_Invalid);
  } break;

  case JVM_CONSTANT_Fieldref:
  {
    int class_index = from_cp->uncached_klass_ref_index_at(from_i);
    int name_and_type_index = from_cp->uncached_name_and_type_ref_index_at(from_i);
    to_cp->field_at_put(to_i, class_index, name_and_type_index);
  } break;

  case JVM_CONSTANT_Float:
  {
    jfloat f = from_cp->float_at(from_i);
    to_cp->float_at_put(to_i, f);
  } break;

  case JVM_CONSTANT_Integer:
  {
    jint i = from_cp->int_at(from_i);
    to_cp->int_at_put(to_i, i);
  } break;

  case JVM_CONSTANT_InterfaceMethodref:
  {
    int class_index = from_cp->uncached_klass_ref_index_at(from_i);
    int name_and_type_index = from_cp->uncached_name_and_type_ref_index_at(from_i);
    to_cp->interface_method_at_put(to_i, class_index, name_and_type_index);
  } break;

  case JVM_CONSTANT_Long:
  {
    jlong l = from_cp->long_at(from_i);
    to_cp->long_at_put(to_i, l);
    // long takes two constant pool entries so init second entry's tag
    to_cp->tag_at_put(to_i + 1, JVM_CONSTANT_Invalid);
  } break;

  case JVM_CONSTANT_Methodref:
  {
    int class_index = from_cp->uncached_klass_ref_index_at(from_i);
    int name_and_type_index = from_cp->uncached_name_and_type_ref_index_at(from_i);
    to_cp->method_at_put(to_i, class_index, name_and_type_index);
  } break;

  case JVM_CONSTANT_NameAndType:
  {
    int name_ref_index = from_cp->name_ref_index_at(from_i);
    int signature_ref_index = from_cp->signature_ref_index_at(from_i);
    to_cp->name_and_type_at_put(to_i, name_ref_index, signature_ref_index);
  } break;

  case JVM_CONSTANT_StringIndex:
  {
    jint si = from_cp->string_index_at(from_i);
    to_cp->string_index_at_put(to_i, si);
  } break;

  case JVM_CONSTANT_UnresolvedClass:
  case JVM_CONSTANT_UnresolvedClassInError:
  {
    // Can be resolved after checking tag, so check the slot first.
    CPSlot entry = from_cp->slot_at(from_i);
    if (entry.is_resolved()) {
      assert(entry.get_klass()->is_klass(), "must be");
      // Already resolved
      to_cp->klass_at_put(to_i, entry.get_klass());
    } else {
      to_cp->unresolved_klass_at_put(to_i, entry.get_symbol());
    }
  } break;

  case JVM_CONSTANT_String:
  {
    Symbol* s = from_cp->unresolved_string_at(from_i);
    to_cp->unresolved_string_at_put(to_i, s);
  } break;

  case JVM_CONSTANT_Utf8:
  {
    Symbol* s = from_cp->symbol_at(from_i);
    // Need to increase refcount, the old one will be thrown away and deferenced
    s->increment_refcount();
    to_cp->symbol_at_put(to_i, s);
  } break;

  case JVM_CONSTANT_MethodType:
  case JVM_CONSTANT_MethodTypeInError:
  {
    jint k = from_cp->method_type_index_at_error_ok(from_i);
    to_cp->method_type_index_at_put(to_i, k);
  } break;

  case JVM_CONSTANT_MethodHandle:
  case JVM_CONSTANT_MethodHandleInError:
  {
    int k1 = from_cp->method_handle_ref_kind_at_error_ok(from_i);
    int k2 = from_cp->method_handle_index_at_error_ok(from_i);
    to_cp->method_handle_index_at_put(to_i, k1, k2);
  } break;

  case JVM_CONSTANT_InvokeDynamic:
  {
    int k1 = from_cp->invoke_dynamic_bootstrap_specifier_index(from_i);
    int k2 = from_cp->invoke_dynamic_name_and_type_ref_index_at(from_i);
    k1 += operand_array_length(to_cp->operands());  // to_cp might already have operands
    to_cp->invoke_dynamic_at_put(to_i, k1, k2);
  } break;

  // Invalid is used as the tag for the second constant pool entry
  // occupied by JVM_CONSTANT_Double or JVM_CONSTANT_Long. It should
  // not be seen by itself.
  case JVM_CONSTANT_Invalid: // fall through

  default:
  {
    ShouldNotReachHere();
  } break;
  }
} // end copy_entry_to()


// Search constant pool search_cp for an entry that matches this
// constant pool's entry at pattern_i. Returns the index of a
// matching entry or zero (0) if there is no matching entry.
int ConstantPool::find_matching_entry(int pattern_i,
      const constantPoolHandle& search_cp, TRAPS) {

  // index zero (0) is not used
  for (int i = 1; i < search_cp->length(); i++) {
    bool found = compare_entry_to(pattern_i, search_cp, i, CHECK_0);
    if (found) {
      return i;
    }
  }

  return 0;  // entry not found; return unused index zero (0)
} // end find_matching_entry()


// Compare this constant pool's bootstrap specifier at idx1 to the constant pool
// cp2's bootstrap specifier at idx2.
bool ConstantPool::compare_operand_to(int idx1, const constantPoolHandle& cp2, int idx2, TRAPS) {
  int k1 = operand_bootstrap_method_ref_index_at(idx1);
  int k2 = cp2->operand_bootstrap_method_ref_index_at(idx2);
  bool match = compare_entry_to(k1, cp2, k2, CHECK_false);

  if (!match) {
    return false;
  }
  int argc = operand_argument_count_at(idx1);
  if (argc == cp2->operand_argument_count_at(idx2)) {
    for (int j = 0; j < argc; j++) {
      k1 = operand_argument_index_at(idx1, j);
      k2 = cp2->operand_argument_index_at(idx2, j);
      match = compare_entry_to(k1, cp2, k2, CHECK_false);
      if (!match) {
        return false;
      }
    }
    return true;           // got through loop; all elements equal
  }
  return false;
} // end compare_operand_to()

// Search constant pool search_cp for a bootstrap specifier that matches
// this constant pool's bootstrap specifier at pattern_i index.
// Return the index of a matching bootstrap specifier or (-1) if there is no match.
int ConstantPool::find_matching_operand(int pattern_i,
                    const constantPoolHandle& search_cp, int search_len, TRAPS) {
  for (int i = 0; i < search_len; i++) {
    bool found = compare_operand_to(pattern_i, search_cp, i, CHECK_(-1));
    if (found) {
      return i;
    }
  }
  return -1;  // bootstrap specifier not found; return unused index (-1)
} // end find_matching_operand()


#ifndef PRODUCT

const char* ConstantPool::printable_name_at(int which) {

  constantTag tag = tag_at(which);

  if (tag.is_string()) {
    return string_at_noresolve(which);
  } else if (tag.is_klass() || tag.is_unresolved_klass()) {
    return klass_name_at(which)->as_C_string();
  } else if (tag.is_symbol()) {
    return symbol_at(which)->as_C_string();
  }
  return "";
}

#endif // PRODUCT


// JVMTI GetConstantPool support

// For debugging of constant pool
const bool debug_cpool = false;

#define DBG(code) do { if (debug_cpool) { (code); } } while(0)

static void print_cpool_bytes(jint cnt, u1 *bytes) {
  const char* WARN_MSG = "Must not be such entry!";
  jint size = 0;
  u2   idx1, idx2;

  for (jint idx = 1; idx < cnt; idx++) {
    jint ent_size = 0;
    u1   tag  = *bytes++;
    size++;                       // count tag

    printf("const #%03d, tag: %02d ", idx, tag);
    switch(tag) {
      case JVM_CONSTANT_Invalid: {
        printf("Invalid");
        break;
      }
      case JVM_CONSTANT_Unicode: {
        printf("Unicode      %s", WARN_MSG);
        break;
      }
      case JVM_CONSTANT_Utf8: {
        u2 len = Bytes::get_Java_u2(bytes);
        char str[128];
        if (len > 127) {
           len = 127;
        }
        strncpy(str, (char *) (bytes+2), len);
        str[len] = '\0';
        printf("Utf8          \"%s\"", str);
        ent_size = 2 + len;
        break;
      }
      case JVM_CONSTANT_Integer: {
        u4 val = Bytes::get_Java_u4(bytes);
        printf("int          %d", *(int *) &val);
        ent_size = 4;
        break;
      }
      case JVM_CONSTANT_Float: {
        u4 val = Bytes::get_Java_u4(bytes);
        printf("float        %5.3ff", *(float *) &val);
        ent_size = 4;
        break;
      }
      case JVM_CONSTANT_Long: {
        u8 val = Bytes::get_Java_u8(bytes);
        printf("long         " INT64_FORMAT, (int64_t) *(jlong *) &val);
        ent_size = 8;
        idx++; // Long takes two cpool slots
        break;
      }
      case JVM_CONSTANT_Double: {
        u8 val = Bytes::get_Java_u8(bytes);
        printf("double       %5.3fd", *(jdouble *)&val);
        ent_size = 8;
        idx++; // Double takes two cpool slots
        break;
      }
      case JVM_CONSTANT_Class: {
        idx1 = Bytes::get_Java_u2(bytes);
        printf("class        #%03d", idx1);
        ent_size = 2;
        break;
      }
      case JVM_CONSTANT_String: {
        idx1 = Bytes::get_Java_u2(bytes);
        printf("String       #%03d", idx1);
        ent_size = 2;
        break;
      }
      case JVM_CONSTANT_Fieldref: {
        idx1 = Bytes::get_Java_u2(bytes);
        idx2 = Bytes::get_Java_u2(bytes+2);
        printf("Field        #%03d, #%03d", (int) idx1, (int) idx2);
        ent_size = 4;
        break;
      }
      case JVM_CONSTANT_Methodref: {
        idx1 = Bytes::get_Java_u2(bytes);
        idx2 = Bytes::get_Java_u2(bytes+2);
        printf("Method       #%03d, #%03d", idx1, idx2);
        ent_size = 4;
        break;
      }
      case JVM_CONSTANT_InterfaceMethodref: {
        idx1 = Bytes::get_Java_u2(bytes);
        idx2 = Bytes::get_Java_u2(bytes+2);
        printf("InterfMethod #%03d, #%03d", idx1, idx2);
        ent_size = 4;
        break;
      }
      case JVM_CONSTANT_NameAndType: {
        idx1 = Bytes::get_Java_u2(bytes);
        idx2 = Bytes::get_Java_u2(bytes+2);
        printf("NameAndType  #%03d, #%03d", idx1, idx2);
        ent_size = 4;
        break;
      }
      case JVM_CONSTANT_ClassIndex: {
        printf("ClassIndex  %s", WARN_MSG);
        break;
      }
      case JVM_CONSTANT_UnresolvedClass: {
        printf("UnresolvedClass: %s", WARN_MSG);
        break;
      }
      case JVM_CONSTANT_UnresolvedClassInError: {
        printf("UnresolvedClassInErr: %s", WARN_MSG);
        break;
      }
      case JVM_CONSTANT_StringIndex: {
        printf("StringIndex: %s", WARN_MSG);
        break;
      }
    }
    printf(";\n");
    bytes += ent_size;
    size  += ent_size;
  }
  printf("Cpool size: %d\n", size);
  fflush(0);
  return;
} /* end print_cpool_bytes */


// Returns size of constant pool entry.
jint ConstantPool::cpool_entry_size(jint idx) {
  switch(tag_at(idx).value()) {
    case JVM_CONSTANT_Invalid:
    case JVM_CONSTANT_Unicode:
      return 1;

    case JVM_CONSTANT_Utf8:
      return 3 + symbol_at(idx)->utf8_length();

    case JVM_CONSTANT_Class:
    case JVM_CONSTANT_String:
    case JVM_CONSTANT_ClassIndex:
    case JVM_CONSTANT_UnresolvedClass:
    case JVM_CONSTANT_UnresolvedClassInError:
    case JVM_CONSTANT_StringIndex:
    case JVM_CONSTANT_MethodType:
    case JVM_CONSTANT_MethodTypeInError:
      return 3;

    case JVM_CONSTANT_MethodHandle:
    case JVM_CONSTANT_MethodHandleInError:
      return 4; //tag, ref_kind, ref_index

    case JVM_CONSTANT_Integer:
    case JVM_CONSTANT_Float:
    case JVM_CONSTANT_Fieldref:
    case JVM_CONSTANT_Methodref:
    case JVM_CONSTANT_InterfaceMethodref:
    case JVM_CONSTANT_NameAndType:
      return 5;

    case JVM_CONSTANT_InvokeDynamic:
      // u1 tag, u2 bsm, u2 nt
      return 5;

    case JVM_CONSTANT_Long:
    case JVM_CONSTANT_Double:
      return 9;
  }
  assert(false, "cpool_entry_size: Invalid constant pool entry tag");
  return 1;
} /* end cpool_entry_size */


// SymbolHashMap is used to find a constant pool index from a string.
// This function fills in SymbolHashMaps, one for utf8s and one for
// class names, returns size of the cpool raw bytes.
jint ConstantPool::hash_entries_to(SymbolHashMap *symmap,
                                          SymbolHashMap *classmap) {
  jint size = 0;

  for (u2 idx = 1; idx < length(); idx++) {
    u2 tag = tag_at(idx).value();
    size += cpool_entry_size(idx);

    switch(tag) {
      case JVM_CONSTANT_Utf8: {
        Symbol* sym = symbol_at(idx);
        symmap->add_entry(sym, idx);
        DBG(printf("adding symbol entry %s = %d\n", sym->as_utf8(), idx));
        break;
      }
      case JVM_CONSTANT_Class:
      case JVM_CONSTANT_UnresolvedClass:
      case JVM_CONSTANT_UnresolvedClassInError: {
        Symbol* sym = klass_name_at(idx);
        classmap->add_entry(sym, idx);
        DBG(printf("adding class entry %s = %d\n", sym->as_utf8(), idx));
        break;
      }
      case JVM_CONSTANT_Long:
      case JVM_CONSTANT_Double: {
        idx++; // Both Long and Double take two cpool slots
        break;
      }
    }
  }
  return size;
} /* end hash_utf8_entries_to */


// Copy cpool bytes.
// Returns:
//    0, in case of OutOfMemoryError
//   -1, in case of internal error
//  > 0, count of the raw cpool bytes that have been copied
int ConstantPool::copy_cpool_bytes(int cpool_size,
                                          SymbolHashMap* tbl,
                                          unsigned char *bytes) {
  u2   idx1, idx2;
  jint size  = 0;
  jint cnt   = length();
  unsigned char *start_bytes = bytes;

  for (jint idx = 1; idx < cnt; idx++) {
    u1   tag      = tag_at(idx).value();
    jint ent_size = cpool_entry_size(idx);

    assert(size + ent_size <= cpool_size, "Size mismatch");

    *bytes = tag;
    DBG(printf("#%03hd tag=%03hd, ", idx, tag));
    switch(tag) {
      case JVM_CONSTANT_Invalid: {
        DBG(printf("JVM_CONSTANT_Invalid"));
        break;
      }
      case JVM_CONSTANT_Unicode: {
        assert(false, "Wrong constant pool tag: JVM_CONSTANT_Unicode");
        DBG(printf("JVM_CONSTANT_Unicode"));
        break;
      }
      case JVM_CONSTANT_Utf8: {
        Symbol* sym = symbol_at(idx);
        char*     str = sym->as_utf8();
        // Warning! It's crashing on x86 with len = sym->utf8_length()
        int       len = (int) strlen(str);
        Bytes::put_Java_u2((address) (bytes+1), (u2) len);
        for (int i = 0; i < len; i++) {
            bytes[3+i] = (u1) str[i];
        }
        DBG(printf("JVM_CONSTANT_Utf8: %s ", str));
        break;
      }
      case JVM_CONSTANT_Integer: {
        jint val = int_at(idx);
        Bytes::put_Java_u4((address) (bytes+1), *(u4*)&val);
        break;
      }
      case JVM_CONSTANT_Float: {
        jfloat val = float_at(idx);
        Bytes::put_Java_u4((address) (bytes+1), *(u4*)&val);
        break;
      }
      case JVM_CONSTANT_Long: {
        jlong val = long_at(idx);
        Bytes::put_Java_u8((address) (bytes+1), *(u8*)&val);
        idx++;             // Long takes two cpool slots
        break;
      }
      case JVM_CONSTANT_Double: {
        jdouble val = double_at(idx);
        Bytes::put_Java_u8((address) (bytes+1), *(u8*)&val);
        idx++;             // Double takes two cpool slots
        break;
      }
      case JVM_CONSTANT_Class:
      case JVM_CONSTANT_UnresolvedClass:
      case JVM_CONSTANT_UnresolvedClassInError: {
        *bytes = JVM_CONSTANT_Class;
        Symbol* sym = klass_name_at(idx);
        idx1 = tbl->symbol_to_value(sym);
        assert(idx1 != 0, "Have not found a hashtable entry");
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        DBG(printf("JVM_CONSTANT_Class: idx=#%03hd, %s", idx1, sym->as_utf8()));
        break;
      }
      case JVM_CONSTANT_String: {
        *bytes = JVM_CONSTANT_String;
        Symbol* sym = unresolved_string_at(idx);
        idx1 = tbl->symbol_to_value(sym);
        assert(idx1 != 0, "Have not found a hashtable entry");
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        DBG(printf("JVM_CONSTANT_String: idx=#%03hd, %s", idx1, sym->as_utf8()));
        break;
      }
      case JVM_CONSTANT_Fieldref:
      case JVM_CONSTANT_Methodref:
      case JVM_CONSTANT_InterfaceMethodref: {
        idx1 = uncached_klass_ref_index_at(idx);
        idx2 = uncached_name_and_type_ref_index_at(idx);
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        Bytes::put_Java_u2((address) (bytes+3), idx2);
        DBG(printf("JVM_CONSTANT_Methodref: %hd %hd", idx1, idx2));
        break;
      }
      case JVM_CONSTANT_NameAndType: {
        idx1 = name_ref_index_at(idx);
        idx2 = signature_ref_index_at(idx);
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        Bytes::put_Java_u2((address) (bytes+3), idx2);
        DBG(printf("JVM_CONSTANT_NameAndType: %hd %hd", idx1, idx2));
        break;
      }
      case JVM_CONSTANT_ClassIndex: {
        *bytes = JVM_CONSTANT_Class;
        idx1 = klass_index_at(idx);
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        DBG(printf("JVM_CONSTANT_ClassIndex: %hd", idx1));
        break;
      }
      case JVM_CONSTANT_StringIndex: {
        *bytes = JVM_CONSTANT_String;
        idx1 = string_index_at(idx);
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        DBG(printf("JVM_CONSTANT_StringIndex: %hd", idx1));
        break;
      }
      case JVM_CONSTANT_MethodHandle:
      case JVM_CONSTANT_MethodHandleInError: {
        *bytes = JVM_CONSTANT_MethodHandle;
        int kind = method_handle_ref_kind_at_error_ok(idx);
        idx1 = method_handle_index_at_error_ok(idx);
        *(bytes+1) = (unsigned char) kind;
        Bytes::put_Java_u2((address) (bytes+2), idx1);
        DBG(printf("JVM_CONSTANT_MethodHandle: %d %hd", kind, idx1));
        break;
      }
      case JVM_CONSTANT_MethodType:
      case JVM_CONSTANT_MethodTypeInError: {
        *bytes = JVM_CONSTANT_MethodType;
        idx1 = method_type_index_at_error_ok(idx);
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        DBG(printf("JVM_CONSTANT_MethodType: %hd", idx1));
        break;
      }
      case JVM_CONSTANT_InvokeDynamic: {
        *bytes = tag;
        idx1 = extract_low_short_from_int(*int_at_addr(idx));
        idx2 = extract_high_short_from_int(*int_at_addr(idx));
        assert(idx2 == invoke_dynamic_name_and_type_ref_index_at(idx), "correct half of u4");
        Bytes::put_Java_u2((address) (bytes+1), idx1);
        Bytes::put_Java_u2((address) (bytes+3), idx2);
        DBG(printf("JVM_CONSTANT_InvokeDynamic: %hd %hd", idx1, idx2));
        break;
      }
    }
    DBG(printf("\n"));
    bytes += ent_size;
    size  += ent_size;
  }
  assert(size == cpool_size, "Size mismatch");

  // Keep temorarily for debugging until it's stable.
  DBG(print_cpool_bytes(cnt, start_bytes));
  return (int)(bytes - start_bytes);
} /* end copy_cpool_bytes */

#undef DBG


void ConstantPool::set_on_stack(const bool value) {
  if (value) {
    // Only record if it's not already set.
    if (!on_stack()) {
      _flags |= _on_stack;
      MetadataOnStackMark::record(this);
    }
  } else {
    // Clearing is done single-threadedly.
    _flags &= ~_on_stack;
  }
}

// JSR 292 support for patching constant pool oops after the class is linked and
// the oop array for resolved references are created.
// We can't do this during classfile parsing, which is how the other indexes are
// patched.  The other patches are applied early for some error checking
// so only defer the pseudo_strings.
void ConstantPool::patch_resolved_references(GrowableArray<Handle>* cp_patches) {
  for (int index = 1; index < cp_patches->length(); index++) { // Index 0 is unused
    Handle patch = cp_patches->at(index);
    if (patch.not_null()) {
      assert (tag_at(index).is_string(), "should only be string left");
      // Patching a string means pre-resolving it.
      // The spelling in the constant pool is ignored.
      // The constant reference may be any object whatever.
      // If it is not a real interned string, the constant is referred
      // to as a "pseudo-string", and must be presented to the CP
      // explicitly, because it may require scavenging.
      int obj_index = cp_to_object_index(index);
      pseudo_string_at_put(index, obj_index, patch());
     DEBUG_ONLY(cp_patches->at_put(index, Handle());)
    }
  }
#ifdef ASSERT
  // Ensure that all the patches have been used.
  for (int index = 0; index < cp_patches->length(); index++) {
    assert(cp_patches->at(index).is_null(),
           "Unused constant pool patch at %d in class file %s",
           index,
           pool_holder()->external_name());
  }
#endif // ASSERT
}

#ifndef PRODUCT

// CompileTheWorld support. Preload all classes loaded references in the passed in constantpool
void ConstantPool::preload_and_initialize_all_classes(ConstantPool* obj, TRAPS) {
  guarantee(obj->is_constantPool(), "object must be constant pool");
  constantPoolHandle cp(THREAD, (ConstantPool*)obj);
  guarantee(cp->pool_holder() != NULL, "must be fully loaded");

  for (int i = 0; i< cp->length();  i++) {
    if (cp->tag_at(i).is_unresolved_klass()) {
      // This will force loading of the class
      Klass* klass = cp->klass_at(i, CHECK);
      if (klass->is_instance_klass()) {
        // Force initialization of class
        InstanceKlass::cast(klass)->initialize(CHECK);
      }
    }
  }
}

#endif


// Printing

void ConstantPool::print_on(outputStream* st) const {
  assert(is_constantPool(), "must be constantPool");
  st->print_cr("%s", internal_name());
  if (flags() != 0) {
    st->print(" - flags: 0x%x", flags());
    if (has_preresolution()) st->print(" has_preresolution");
    if (on_stack()) st->print(" on_stack");
    st->cr();
  }
  if (pool_holder() != NULL) {
    st->print_cr(" - holder: " INTPTR_FORMAT, p2i(pool_holder()));
  }
  st->print_cr(" - cache: " INTPTR_FORMAT, p2i(cache()));
  st->print_cr(" - resolved_references: " INTPTR_FORMAT, p2i(resolved_references()));
  st->print_cr(" - reference_map: " INTPTR_FORMAT, p2i(reference_map()));

  for (int index = 1; index < length(); index++) {      // Index 0 is unused
    ((ConstantPool*)this)->print_entry_on(index, st);
    switch (tag_at(index).value()) {
      case JVM_CONSTANT_Long :
      case JVM_CONSTANT_Double :
        index++;   // Skip entry following eigth-byte constant
    }

  }
  st->cr();
}

// Print one constant pool entry
void ConstantPool::print_entry_on(const int index, outputStream* st) {
  EXCEPTION_MARK;
  st->print(" - %3d : ", index);
  tag_at(index).print_on(st);
  st->print(" : ");
  switch (tag_at(index).value()) {
    case JVM_CONSTANT_Class :
      { Klass* k = klass_at(index, CATCH);
        guarantee(k != NULL, "need klass");
        k->print_value_on(st);
        st->print(" {" PTR_FORMAT "}", p2i(k));
      }
      break;
    case JVM_CONSTANT_Fieldref :
    case JVM_CONSTANT_Methodref :
    case JVM_CONSTANT_InterfaceMethodref :
      st->print("klass_index=%d", uncached_klass_ref_index_at(index));
      st->print(" name_and_type_index=%d", uncached_name_and_type_ref_index_at(index));
      break;
    case JVM_CONSTANT_String :
      if (is_pseudo_string_at(index)) {
        oop anObj = pseudo_string_at(index);
        anObj->print_value_on(st);
        st->print(" {" PTR_FORMAT "}", p2i(anObj));
      } else {
        unresolved_string_at(index)->print_value_on(st);
      }
      break;
    case JVM_CONSTANT_Integer :
      st->print("%d", int_at(index));
      break;
    case JVM_CONSTANT_Float :
      st->print("%f", float_at(index));
      break;
    case JVM_CONSTANT_Long :
      st->print_jlong(long_at(index));
      break;
    case JVM_CONSTANT_Double :
      st->print("%lf", double_at(index));
      break;
    case JVM_CONSTANT_NameAndType :
      st->print("name_index=%d", name_ref_index_at(index));
      st->print(" signature_index=%d", signature_ref_index_at(index));
      break;
    case JVM_CONSTANT_Utf8 :
      symbol_at(index)->print_value_on(st);
      break;
    case JVM_CONSTANT_UnresolvedClass :               // fall-through
    case JVM_CONSTANT_UnresolvedClassInError: {
      CPSlot entry = slot_at(index);
      if (entry.is_resolved()) {
        entry.get_klass()->print_value_on(st);
      } else {
        entry.get_symbol()->print_value_on(st);
      }
      }
      break;
    case JVM_CONSTANT_MethodHandle :
    case JVM_CONSTANT_MethodHandleInError :
      st->print("ref_kind=%d", method_handle_ref_kind_at_error_ok(index));
      st->print(" ref_index=%d", method_handle_index_at_error_ok(index));
      break;
    case JVM_CONSTANT_MethodType :
    case JVM_CONSTANT_MethodTypeInError :
      st->print("signature_index=%d", method_type_index_at_error_ok(index));
      break;
    case JVM_CONSTANT_InvokeDynamic :
      {
        st->print("bootstrap_method_index=%d", invoke_dynamic_bootstrap_method_ref_index_at(index));
        st->print(" name_and_type_index=%d", invoke_dynamic_name_and_type_ref_index_at(index));
        int argc = invoke_dynamic_argument_count_at(index);
        if (argc > 0) {
          for (int arg_i = 0; arg_i < argc; arg_i++) {
            int arg = invoke_dynamic_argument_index_at(index, arg_i);
            st->print((arg_i == 0 ? " arguments={%d" : ", %d"), arg);
          }
          st->print("}");
        }
      }
      break;
    default:
      ShouldNotReachHere();
      break;
  }
  st->cr();
}

void ConstantPool::print_value_on(outputStream* st) const {
  assert(is_constantPool(), "must be constantPool");
  st->print("constant pool [%d]", length());
  if (has_preresolution()) st->print("/preresolution");
  if (operands() != NULL)  st->print("/operands[%d]", operands()->length());
  print_address_on(st);
  st->print(" for ");
  pool_holder()->print_value_on(st);
  if (pool_holder() != NULL) {
    bool extra = (pool_holder()->constants() != this);
    if (extra)  st->print(" (extra)");
  }
  if (cache() != NULL) {
    st->print(" cache=" PTR_FORMAT, p2i(cache()));
  }
}

#if INCLUDE_SERVICES
// Size Statistics
void ConstantPool::collect_statistics(KlassSizeStats *sz) const {
  sz->_cp_all_bytes += (sz->_cp_bytes          = sz->count(this));
  sz->_cp_all_bytes += (sz->_cp_tags_bytes     = sz->count_array(tags()));
  sz->_cp_all_bytes += (sz->_cp_cache_bytes    = sz->count(cache()));
  sz->_cp_all_bytes += (sz->_cp_operands_bytes = sz->count_array(operands()));
  sz->_cp_all_bytes += (sz->_cp_refmap_bytes   = sz->count_array(reference_map()));

  sz->_ro_bytes += sz->_cp_operands_bytes + sz->_cp_tags_bytes +
                   sz->_cp_refmap_bytes;
  sz->_rw_bytes += sz->_cp_bytes + sz->_cp_cache_bytes;
}
#endif // INCLUDE_SERVICES

// Verification

void ConstantPool::verify_on(outputStream* st) {
  guarantee(is_constantPool(), "object must be constant pool");
  for (int i = 0; i< length();  i++) {
    constantTag tag = tag_at(i);
    CPSlot entry = slot_at(i);
    if (tag.is_klass()) {
      if (entry.is_resolved()) {
        guarantee(entry.get_klass()->is_klass(),    "should be klass");
      }
    } else if (tag.is_unresolved_klass()) {
      if (entry.is_resolved()) {
        guarantee(entry.get_klass()->is_klass(),    "should be klass");
      }
    } else if (tag.is_symbol()) {
      guarantee(entry.get_symbol()->refcount() != 0, "should have nonzero reference count");
    } else if (tag.is_string()) {
      guarantee(entry.get_symbol()->refcount() != 0, "should have nonzero reference count");
    }
  }
  if (cache() != NULL) {
    // Note: cache() can be NULL before a class is completely setup or
    // in temporary constant pools used during constant pool merging
    guarantee(cache()->is_constantPoolCache(), "should be constant pool cache");
  }
  if (pool_holder() != NULL) {
    // Note: pool_holder() can be NULL in temporary constant pools
    // used during constant pool merging
    guarantee(pool_holder()->is_klass(),    "should be klass");
  }
}


void SymbolHashMap::add_entry(Symbol* sym, u2 value) {
  char *str = sym->as_utf8();
  unsigned int hash = compute_hash(str, sym->utf8_length());
  unsigned int index = hash % table_size();

  // check if already in map
  // we prefer the first entry since it is more likely to be what was used in
  // the class file
  for (SymbolHashMapEntry *en = bucket(index); en != NULL; en = en->next()) {
    assert(en->symbol() != NULL, "SymbolHashMapEntry symbol is NULL");
    if (en->hash() == hash && en->symbol() == sym) {
        return;  // already there
    }
  }

  SymbolHashMapEntry* entry = new SymbolHashMapEntry(hash, sym, value);
  entry->set_next(bucket(index));
  _buckets[index].set_entry(entry);
  assert(entry->symbol() != NULL, "SymbolHashMapEntry symbol is NULL");
}

SymbolHashMapEntry* SymbolHashMap::find_entry(Symbol* sym) {
  assert(sym != NULL, "SymbolHashMap::find_entry - symbol is NULL");
  char *str = sym->as_utf8();
  int   len = sym->utf8_length();
  unsigned int hash = SymbolHashMap::compute_hash(str, len);
  unsigned int index = hash % table_size();
  for (SymbolHashMapEntry *en = bucket(index); en != NULL; en = en->next()) {
    assert(en->symbol() != NULL, "SymbolHashMapEntry symbol is NULL");
    if (en->hash() == hash && en->symbol() == sym) {
      return en;
    }
  }
  return NULL;
}
