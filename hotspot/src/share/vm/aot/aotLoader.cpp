/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "jvmci/jvmciRuntime.hpp"
#include "oops/method.hpp"

GrowableArray<AOTCodeHeap*>* AOTLoader::_heaps = new(ResourceObj::C_HEAP, mtCode) GrowableArray<AOTCodeHeap*> (2, true);
GrowableArray<AOTLib*>* AOTLoader::_libraries = new(ResourceObj::C_HEAP, mtCode) GrowableArray<AOTLib*> (2, true);

// Iterate over all AOT CodeHeaps
#define FOR_ALL_AOT_HEAPS(heap) for (GrowableArrayIterator<AOTCodeHeap*> heap = heaps()->begin(); heap != heaps()->end(); ++heap)
// Iterate over all AOT Libraries
#define FOR_ALL_AOT_LIBRARIES(lib) for (GrowableArrayIterator<AOTLib*> lib = libraries()->begin(); lib != libraries()->end(); ++lib)

void AOTLoader::load_for_klass(instanceKlassHandle kh, Thread* thread) {
  if (UseAOT) {
    FOR_ALL_AOT_HEAPS(heap) {
      (*heap)->load_klass_data(kh, thread);
    }
  }
}

uint64_t AOTLoader::get_saved_fingerprint(InstanceKlass* ik) {
  FOR_ALL_AOT_HEAPS(heap) {
    AOTKlassData* klass_data = (*heap)->find_klass(ik);
    if (klass_data != NULL) {
      return klass_data->_fingerprint;
    }
  }
  return 0;
}

bool AOTLoader::find_klass(InstanceKlass* ik) {
  FOR_ALL_AOT_HEAPS(heap) {
    if ((*heap)->find_klass(ik) != NULL) {
      return true;
    }
  }
  return false;
}

bool AOTLoader::contains(address p) {
  FOR_ALL_AOT_HEAPS(heap) {
    if ((*heap)->contains(p)) {
      return true;
    }
  }
  return false;
}

void AOTLoader::oops_do(OopClosure* f) {
  if (UseAOT) {
    FOR_ALL_AOT_HEAPS(heap) {
      (*heap)->oops_do(f);
    }
  }
}

void AOTLoader::metadata_do(void f(Metadata*)) {
  if (UseAOT) {
    FOR_ALL_AOT_HEAPS(heap) {
      (*heap)->metadata_do(f);
    }
  }
}

address AOTLoader::exception_begin(JavaThread* thread, CodeBlob* blob, address return_address) {
  assert(blob->is_aot(), "sanity");
  AOTCompiledMethod* aotm = (AOTCompiledMethod*)blob;
  // Set flag if return address is a method handle call site.
  thread->set_is_method_handle_return(aotm->is_method_handle_return(return_address));
  return aotm->exception_begin();
}

// Flushing and deoptimization in case of evolution
void AOTLoader::flush_evol_dependents_on(instanceKlassHandle dependee) {
  // make non entrant and mark for deoptimization
  FOR_ALL_AOT_HEAPS(heap) {
    (*heap)->flush_evol_dependents_on(dependee);
  }
  Deoptimization::deoptimize_dependents();
}

/**
 * List of core modules for which we search for shared libraries.
 */
static const char* modules[] = {
  "java.base",
  "java.logging",
  "jdk.compiler",
  "jdk.scripting.nashorn",
  "jdk.internal.vm.ci",
  "jdk.internal.vm.compiler"
};

void AOTLoader::initialize() {
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

    // -Xint is not compatible with AOT
    if (Arguments::is_interpreter_only()) {
      if (PrintAOT) {
        warning("-Xint is not compatible with AOT (switching AOT off)");
      }
      FLAG_SET_DEFAULT(UseAOT, false);
      return;
    }

    const char* home = Arguments::get_java_home();
    const char* file_separator = os::file_separator();

    for (int i = 0; i < (int) (sizeof(modules) / sizeof(const char*)); i++) {
      char library[JVM_MAXPATHLEN];
      jio_snprintf(library, sizeof(library), "%s%slib%slib%s%s%s.so", home, file_separator, file_separator, modules[i], UseCompressedOops ? "-coop" : "", UseG1GC ? "" : "-nong1");
      load_library(library, false);
    }

    // Scan the AOTLibrary option.
    if (AOTLibrary != NULL) {
      const int len = strlen(AOTLibrary);
      char* cp  = NEW_C_HEAP_ARRAY(char, len+1, mtCode);
      if (cp != NULL) { // No memory?
        memcpy(cp, AOTLibrary, len);
        cp[len] = '\0';
        char* end = cp + len;
        while (cp < end) {
          const char* name = cp;
          while ((*cp) != '\0' && (*cp) != '\n' && (*cp) != ',' && (*cp) != ':' && (*cp) != ';')  cp++;
          cp[0] = '\0';  // Terminate name
          cp++;
          load_library(name, true);
        }
      }
    }
  }
}

void AOTLoader::universe_init() {
  if (UseAOT && libraries_count() > 0) {
    // Shifts are static values which initialized by 0 until java heap initialization.
    // AOT libs are loaded before heap initialized so shift values are not set.
    // It is okay since ObjectAlignmentInBytes flag which defines shifts value is set before AOT libs are loaded.
    // Set shifts value based on first AOT library config.
    if (UseCompressedOops && AOTLib::narrow_oop_shift_initialized()) {
      int oop_shift = Universe::narrow_oop_shift();
      if (oop_shift == 0) {
        Universe::set_narrow_oop_shift(AOTLib::narrow_oop_shift());
      } else {
        FOR_ALL_AOT_LIBRARIES(lib) {
          (*lib)->verify_flag(AOTLib::narrow_oop_shift(), oop_shift, "Universe::narrow_oop_shift");
        }
      }
      if (UseCompressedClassPointers) { // It is set only if UseCompressedOops is set
        int klass_shift = Universe::narrow_klass_shift();
        if (klass_shift == 0) {
          Universe::set_narrow_klass_shift(AOTLib::narrow_klass_shift());
        } else {
          FOR_ALL_AOT_LIBRARIES(lib) {
            (*lib)->verify_flag(AOTLib::narrow_klass_shift(), klass_shift, "Universe::narrow_klass_shift");
          }
        }
      }
    }
    // Create heaps for all the libraries
    FOR_ALL_AOT_LIBRARIES(lib) {
      if ((*lib)->is_valid()) {
        AOTCodeHeap* heap = new AOTCodeHeap(*lib);
        {
          MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
          add_heap(heap);
          CodeCache::add_heap(heap);
        }
      }
    }
  }
  if (heaps_count() == 0) {
    if (FLAG_IS_DEFAULT(UseAOT)) {
      FLAG_SET_DEFAULT(UseAOT, false);
    }
  }
}

void AOTLoader::set_narrow_klass_shift() {
  // This method could be called from Metaspace::set_narrow_klass_base_and_shift().
  // In case it is not called (during dump CDS, for example) the corresponding code in
  // AOTLoader::universe_init(), which is called later, will set the shift value.
  if (UseAOT && libraries_count() > 0 &&
      UseCompressedOops && AOTLib::narrow_oop_shift_initialized() &&
      UseCompressedClassPointers) {
    int klass_shift = Universe::narrow_klass_shift();
    if (klass_shift == 0) {
      Universe::set_narrow_klass_shift(AOTLib::narrow_klass_shift());
    } else {
      FOR_ALL_AOT_LIBRARIES(lib) {
        (*lib)->verify_flag(AOTLib::narrow_klass_shift(), klass_shift, "Universe::narrow_klass_shift");
      }
    }
  }
}

void AOTLoader::load_library(const char* name, bool exit_on_error) {
  void* handle = dlopen(name, RTLD_LAZY);
  if (handle == NULL) {
    if (exit_on_error) {
      tty->print_cr("error opening file: %s", dlerror());
      vm_exit(1);
    }
    return;
  }
  const int dso_id = libraries_count() + 1;
  AOTLib* lib = new AOTLib(handle, name, dso_id);
  if (!lib->is_valid()) {
    delete lib;
    dlclose(handle);
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
