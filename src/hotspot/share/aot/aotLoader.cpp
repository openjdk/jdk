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
#include "aot/aotCodeHeap.hpp"
#include "aot/aotLoader.inline.hpp"
#include "classfile/javaClasses.hpp"
#include "jvm.h"
#include "jvmci/jvmci.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/compressedOops.hpp"
#include "oops/method.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/timerTrace.hpp"

GrowableArray<AOTCodeHeap*>* AOTLoader::_heaps = new(ResourceObj::C_HEAP, mtCode) GrowableArray<AOTCodeHeap*> (2, mtCode);
GrowableArray<AOTLib*>* AOTLoader::_libraries = new(ResourceObj::C_HEAP, mtCode) GrowableArray<AOTLib*> (2, mtCode);

// Iterate over all AOT CodeHeaps
#define FOR_ALL_AOT_HEAPS(heap) for (GrowableArrayIterator<AOTCodeHeap*> heap = heaps()->begin(); heap != heaps()->end(); ++heap)
// Iterate over all AOT Libraries
#define FOR_ALL_AOT_LIBRARIES(lib) for (GrowableArrayIterator<AOTLib*> lib = libraries()->begin(); lib != libraries()->end(); ++lib)

void AOTLoader::load_for_klass(InstanceKlass* ik, Thread* thread) {
  if (ik->is_hidden() || ik->is_unsafe_anonymous()) {
    // don't even bother
    return;
  }
  if (UseAOT) {
    // We allow hotswap to be enabled after the onload phase, but not breakpoints
    assert(!JvmtiExport::can_post_breakpoint(), "AOT should have been disabled.");
    FOR_ALL_AOT_HEAPS(heap) {
      (*heap)->load_klass_data(ik, thread);
    }
  }
}

uint64_t AOTLoader::get_saved_fingerprint(InstanceKlass* ik) {
  assert(UseAOT, "called only when AOT is enabled");
  if (ik->is_hidden() || ik->is_unsafe_anonymous()) {
    // don't even bother
    return 0;
  }
  FOR_ALL_AOT_HEAPS(heap) {
    AOTKlassData* klass_data = (*heap)->find_klass(ik);
    if (klass_data != NULL) {
      return klass_data->_fingerprint;
    }
  }
  return 0;
}

void AOTLoader::oops_do(OopClosure* f) {
  if (UseAOT) {
    FOR_ALL_AOT_HEAPS(heap) {
      (*heap)->oops_do(f);
    }
  }
}

void AOTLoader::metadata_do(MetadataClosure* f) {
  if (UseAOT) {
    FOR_ALL_AOT_HEAPS(heap) {
      (*heap)->metadata_do(f);
    }
  }
}

void AOTLoader::mark_evol_dependent_methods(InstanceKlass* dependee) {
  if (UseAOT) {
    FOR_ALL_AOT_HEAPS(heap) {
      (*heap)->mark_evol_dependent_methods(dependee);
    }
  }
}

/**
 * List of core modules for which we search for shared libraries.
 */
static const char* modules[] = {
  "java.base",
  "java.logging",
  "jdk.compiler",
  "jdk.internal.vm.ci",
  "jdk.internal.vm.compiler"
};

void AOTLoader::initialize() {
  TraceTime timer("AOT initialization", TRACETIME_LOG(Info, aot, startuptime));

  if (FLAG_IS_DEFAULT(UseAOT) && AOTLibrary != NULL) {
    // Don't need to set UseAOT on command line when AOTLibrary is specified
    FLAG_SET_DEFAULT(UseAOT, true);
  }
  if (UseAOT) {
    // EagerInitialization is not compatible with AOT
    if (EagerInitialization) {
      if (PrintAOT) {
        warning("EagerInitialization is not compatible with AOT (switching AOT off)");
      }
      FLAG_SET_DEFAULT(UseAOT, false);
      return;
    }

    if (JvmtiExport::can_post_breakpoint()) {
      if (PrintAOT) {
        warning("JVMTI capability to post breakpoint is not compatible with AOT (switching AOT off)");
      }
      FLAG_SET_DEFAULT(UseAOT, false);
      return;
    }

    // -Xint is not compatible with AOT
    if (Arguments::is_interpreter_only()) {
      if (PrintAOT) {
        warning("-Xint is not compatible with AOT (switching AOT off)");
      }
      FLAG_SET_DEFAULT(UseAOT, false);
      return;
    }

#ifdef _WINDOWS
    const char pathSep = ';';
#else
    const char pathSep = ':';
#endif

    // Scan the AOTLibrary option.
    if (AOTLibrary != NULL) {
      const int len = (int)strlen(AOTLibrary);
      char* cp  = NEW_C_HEAP_ARRAY(char, len+1, mtCode);
      memcpy(cp, AOTLibrary, len);
      cp[len] = '\0';
      char* end = cp + len;
      while (cp < end) {
        const char* name = cp;
        while ((*cp) != '\0' && (*cp) != '\n' && (*cp) != ',' && (*cp) != pathSep) cp++;
        cp[0] = '\0';  // Terminate name
        cp++;
        load_library(name, true);
      }
    }

    // Load well-know AOT libraries from Java installation directory.
    const char* home = Arguments::get_java_home();
    const char* file_separator = os::file_separator();

    for (int i = 0; i < (int) (sizeof(modules) / sizeof(const char*)); i++) {
      char library[JVM_MAXPATHLEN];
      jio_snprintf(library, sizeof(library), "%s%slib%slib%s%s%s%s", home, file_separator, file_separator, modules[i], UseCompressedOops ? "-coop" : "", UseG1GC ? "" : "-nong1", os::dll_file_extension());
      load_library(library, false);
    }
  }
}

void AOTLoader::universe_init() {
  if (UseAOT && libraries_count() > 0) {
    // Shifts are static values which initialized by 0 until java heap initialization.
    // AOT libs are loaded before heap initialized so shift values are not set.
    // It is okay since ObjectAlignmentInBytes flag which defines shifts value is set before AOT libs are loaded.
    // AOT sets shift values during heap and metaspace initialization.
    // Check shifts value to make sure thay did not change.
    if (UseCompressedOops && AOTLib::narrow_oop_shift_initialized()) {
      int oop_shift = CompressedOops::shift();
      FOR_ALL_AOT_LIBRARIES(lib) {
        (*lib)->verify_flag((*lib)->config()->_narrowOopShift, oop_shift, "CompressedOops::shift");
      }
      if (UseCompressedClassPointers) { // It is set only if UseCompressedOops is set
        int klass_shift = CompressedKlassPointers::shift();
        FOR_ALL_AOT_LIBRARIES(lib) {
          (*lib)->verify_flag((*lib)->config()->_narrowKlassShift, klass_shift, "CompressedKlassPointers::shift");
        }
      }
    }
    // Create heaps for all valid libraries
    FOR_ALL_AOT_LIBRARIES(lib) {
      if ((*lib)->is_valid()) {
        AOTCodeHeap* heap = new AOTCodeHeap(*lib);
        {
          MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
          add_heap(heap);
          CodeCache::add_heap(heap);
        }
      } else {
        // Unload invalid libraries
        os::dll_unload((*lib)->dl_handle());
      }
    }
  }
  if (heaps_count() == 0) {
    if (FLAG_IS_DEFAULT(UseAOT)) {
      FLAG_SET_DEFAULT(UseAOT, false);
    }
  }
}

// Set shift value for compressed oops and classes based on first AOT library config.
// AOTLoader::universe_init(), which is called later, will check the shift value again to make sure nobody change it.
// This code is not executed during CDS dump because it runs in Interpreter mode and AOT is disabled in this mode.

void AOTLoader::set_narrow_oop_shift() {
  // This method is called from Universe::initialize_heap().
  if (UseAOT && libraries_count() > 0 &&
      UseCompressedOops && AOTLib::narrow_oop_shift_initialized()) {
    if (CompressedOops::shift() == 0) {
      // 0 is valid shift value for small heap but we can safely increase it
      // at this point when nobody used it yet.
      CompressedOops::set_shift(AOTLib::narrow_oop_shift());
    }
  }
}

void AOTLoader::set_narrow_klass_shift() {
  // This method is called from Metaspace::set_narrow_klass_base_and_shift().
  if (UseAOT && libraries_count() > 0 &&
      UseCompressedOops && AOTLib::narrow_oop_shift_initialized() &&
      UseCompressedClassPointers) {
    if (CompressedKlassPointers::shift() == 0) {
      CompressedKlassPointers::set_shift(AOTLib::narrow_klass_shift());
    }
  }
}

void AOTLoader::load_library(const char* name, bool exit_on_error) {
  // Skip library if a library with the same name is already loaded.
  const int file_separator = *os::file_separator();
  const char* start = strrchr(name, file_separator);
  const char* new_name = (start == NULL) ? name : (start + 1);
  FOR_ALL_AOT_LIBRARIES(lib) {
    const char* lib_name = (*lib)->name();
    start = strrchr(lib_name, file_separator);
    const char* old_name = (start == NULL) ? lib_name : (start + 1);
    if (strcmp(old_name, new_name) == 0) {
      if (PrintAOT) {
        warning("AOT library %s is already loaded as %s.", name, lib_name);
      }
      return;
    }
  }
  char ebuf[1024];
  void* handle = os::dll_load(name, ebuf, sizeof ebuf);
  if (handle == NULL) {
    if (exit_on_error) {
      tty->print_cr("error opening file: %s", ebuf);
      vm_exit(1);
    }
    return;
  }
  const int dso_id = libraries_count() + 1;
  AOTLib* lib = new AOTLib(handle, name, dso_id);
  if (!lib->is_valid()) {
    delete lib;
    os::dll_unload(handle);
    return;
  }
  add_library(lib);
}

#ifndef PRODUCT
void AOTLoader::print_statistics() {
  { ttyLocker ttyl;
    tty->print_cr("--- AOT Statistics ---");
    tty->print_cr("AOT libraries loaded: %d", heaps_count());
    AOTCodeHeap::print_statistics();
  }
}
#endif


bool AOTLoader::reconcile_dynamic_invoke(InstanceKlass* holder, int index, Method* adapter_method, Klass* appendix_klass) {
  if (!UseAOT) {
    return true;
  }
  JavaThread* thread = JavaThread::current();
  ResourceMark rm(thread);
  RegisterMap map(thread, false);
  frame caller_frame = thread->last_frame().sender(&map); // Skip stub
  CodeBlob* caller_cb = caller_frame.cb();
  guarantee(caller_cb != NULL && caller_cb->is_compiled(), "must be called from compiled method");
  CompiledMethod* cm = caller_cb->as_compiled_method();

  if (!cm->is_aot()) {
    return true;
  }
  AOTCompiledMethod* aot = (AOTCompiledMethod*)cm;

  AOTCodeHeap* caller_heap = NULL;
  FOR_ALL_AOT_HEAPS(heap) {
    if ((*heap)->contains_blob(aot)) {
      caller_heap = *heap;
      break;
    }
  }
  guarantee(caller_heap != NULL, "CodeHeap not found");
  bool success = caller_heap->reconcile_dynamic_invoke(aot, holder, index, adapter_method, appendix_klass);
  vmassert(success || thread->last_frame().sender(&map).is_deoptimized_frame(), "caller not deoptimized on failure");
  return success;
}


// This should be called very early during startup before any of the AOTed methods that use boxes can deoptimize.
// Deoptimization machinery expects the caches to be present and populated.
void AOTLoader::initialize_box_caches(TRAPS) {
  if (!UseAOT || libraries_count() == 0) {
    return;
  }
  TraceTime timer("AOT initialization of box caches", TRACETIME_LOG(Info, aot, startuptime));
  JVMCI::ensure_box_caches_initialized(CHECK);
}
