/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/systemDictionary.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/oopStorage.hpp"
#include "gc/shared/oopStorageSet.hpp"
#include "jvmci/jvmci.hpp"
#include "jvmci/jvmciJavaClasses.hpp"
#include "jvmci/jvmciRuntime.hpp"
#include "jvmci/metadataHandleBlock.hpp"
#include "memory/universe.hpp"

MetadataHandleBlock* JVMCI::_metadata_handles = NULL;
JVMCIRuntime* JVMCI::_compiler_runtime = NULL;
JVMCIRuntime* JVMCI::_java_runtime = NULL;

bool JVMCI::can_initialize_JVMCI() {
  // Initializing JVMCI requires the module system to be initialized past phase 3.
  // The JVMCI API itself isn't available until phase 2 and ServiceLoader (which
  // JVMCI initialization requires) isn't usable until after phase 3. Testing
  // whether the system loader is initialized satisfies all these invariants.
  if (SystemDictionary::java_system_loader() == NULL) {
    return false;
  }
  assert(Universe::is_module_initialized(), "must be");
  return true;
}

void JVMCI::initialize_compiler(TRAPS) {
  if (JVMCILibDumpJNIConfig) {
    JNIJVMCI::initialize_ids(NULL);
    ShouldNotReachHere();
  }

  JVMCI::compiler_runtime()->call_getCompiler(CHECK);
}

void JVMCI::initialize_globals() {
  _metadata_handles = MetadataHandleBlock::allocate_block();
  if (UseJVMCINativeLibrary) {
    // There are two runtimes.
    _compiler_runtime = new JVMCIRuntime();
    _java_runtime = new JVMCIRuntime();
  } else {
    // There is only a single runtime
    _java_runtime = _compiler_runtime = new JVMCIRuntime();
  }
}

// Handles to objects in the Hotspot heap.
static OopStorage* object_handles() {
  return OopStorageSet::vm_global();
}

jobject JVMCI::make_global(const Handle& obj) {
  assert(!Universe::heap()->is_gc_active(), "can't extend the root set during GC");
  assert(oopDesc::is_oop(obj()), "not an oop");
  oop* ptr = object_handles()->allocate();
  jobject res = NULL;
  if (ptr != NULL) {
    assert(*ptr == NULL, "invariant");
    NativeAccess<>::oop_store(ptr, obj());
    res = reinterpret_cast<jobject>(ptr);
  } else {
    vm_exit_out_of_memory(sizeof(oop), OOM_MALLOC_ERROR,
                          "Cannot create JVMCI oop handle");
  }
  return res;
}

void JVMCI::destroy_global(jobject handle) {
  // Assert before nulling out, for better debugging.
  assert(is_global_handle(handle), "precondition");
  oop* oop_ptr = reinterpret_cast<oop*>(handle);
  NativeAccess<>::oop_store(oop_ptr, (oop)NULL);
  object_handles()->release(oop_ptr);
}

bool JVMCI::is_global_handle(jobject handle) {
  const oop* ptr = reinterpret_cast<oop*>(handle);
  return object_handles()->allocation_status(ptr) == OopStorage::ALLOCATED_ENTRY;
}

jmetadata JVMCI::allocate_handle(const methodHandle& handle) {
  assert(_metadata_handles != NULL, "uninitialized");
  MutexLocker ml(JVMCI_lock);
  return _metadata_handles->allocate_handle(handle);
}

jmetadata JVMCI::allocate_handle(const constantPoolHandle& handle) {
  assert(_metadata_handles != NULL, "uninitialized");
  MutexLocker ml(JVMCI_lock);
  return _metadata_handles->allocate_handle(handle);
}

void JVMCI::release_handle(jmetadata handle) {
  MutexLocker ml(JVMCI_lock);
  _metadata_handles->chain_free_list(handle);
}

void JVMCI::metadata_do(void f(Metadata*)) {
  if (_metadata_handles != NULL) {
    _metadata_handles->metadata_do(f);
  }
}

void JVMCI::do_unloading(bool unloading_occurred) {
  if (_metadata_handles != NULL && unloading_occurred) {
    _metadata_handles->do_unloading();
  }
}

bool JVMCI::is_compiler_initialized() {
  return compiler_runtime()->is_HotSpotJVMCIRuntime_initialized();
}

void JVMCI::shutdown() {
  if (compiler_runtime() != NULL) {
    compiler_runtime()->shutdown();
  }
}

bool JVMCI::shutdown_called() {
  if (compiler_runtime() != NULL) {
    return compiler_runtime()->shutdown_called();
  }
  return false;
}
