/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jvm_io.h"
#include "runtime/arguments.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/semaphore.inline.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/zipLibrary.hpp"

 // Entry points in zip.dll for loading zip/jar file entries
typedef void**(*ZIP_Open_t)(const char* name, char** pmsg);
typedef void(*ZIP_Close_t)(jzfile* zip);
typedef jzentry* (*ZIP_FindEntry_t)(jzfile* zip, const char* name, jint* sizeP, jint* nameLen);
typedef jboolean(*ZIP_ReadEntry_t)(jzfile* zip, jzentry* entry, unsigned char* buf, char* namebuf);
typedef void(*ZIP_FreeEntry_t)(jzfile *zip, jzentry *entry);
typedef jint(*ZIP_CRC32_t)(jint crc, const jbyte* buf, jint len);
typedef const char* (*ZIP_GZip_InitParams_t)(size_t, size_t*, size_t*, int);
typedef size_t(*ZIP_GZip_Fully_t)(char*, size_t, char*, size_t, char*, size_t, int, char*, char const**);

static ZIP_Open_t ZIP_Open = nullptr;
static ZIP_Close_t ZIP_Close = nullptr;
static ZIP_FindEntry_t ZIP_FindEntry = nullptr;
static ZIP_ReadEntry_t ZIP_ReadEntry = nullptr;
static ZIP_FreeEntry_t ZIP_FreeEntry = nullptr;
static ZIP_CRC32_t ZIP_CRC32 = nullptr;
static ZIP_GZip_InitParams_t ZIP_GZip_InitParams = nullptr;
static ZIP_GZip_Fully_t ZIP_GZip_Fully = nullptr;

static void* _zip_handle = nullptr;
static bool _loaded = false;

static inline bool is_loaded() {
  return Atomic::load_acquire(&_loaded);
}

static inline bool not_loaded() {
  return !is_loaded();
}

static void* dll_lookup(const char* name, const char* path, bool vm_exit_on_failure) {
  if (is_vm_statically_linked()) {
    return os::lookup_function(name);
  }

  assert(_zip_handle != nullptr, "invariant");
  void* func = os::dll_lookup(_zip_handle, name);
  if (func == nullptr && vm_exit_on_failure) {
    char msg[256] = "";
    jio_snprintf(&msg[0], sizeof msg, "Could not resolve \"%s\"", name);
    vm_exit_during_initialization(&msg[0], path);
  }
  return func;
}

static void store_function_pointers(const char* path, bool vm_exit_on_failure) {
  assert(_zip_handle != nullptr, "invariant");
  ZIP_Open = CAST_TO_FN_PTR(ZIP_Open_t, dll_lookup("ZIP_Open", path, vm_exit_on_failure));
  ZIP_Close = CAST_TO_FN_PTR(ZIP_Close_t, dll_lookup("ZIP_Close", path, vm_exit_on_failure));
  ZIP_FindEntry = CAST_TO_FN_PTR(ZIP_FindEntry_t, dll_lookup("ZIP_FindEntry", path, vm_exit_on_failure));
  ZIP_ReadEntry = CAST_TO_FN_PTR(ZIP_ReadEntry_t, dll_lookup("ZIP_ReadEntry", path, vm_exit_on_failure));
  ZIP_FreeEntry = CAST_TO_FN_PTR(ZIP_FreeEntry_t, dll_lookup("ZIP_FreeEntry", path, vm_exit_on_failure));
  ZIP_CRC32 = CAST_TO_FN_PTR(ZIP_CRC32_t, dll_lookup("ZIP_CRC32", path, vm_exit_on_failure));
  // The following entry points are most likely optional from a zip library implementation perspective.
  // Hence no vm_exit on a resolution failure. Further refactorings should investigate this,
  // and if possible, streamline setting all entry points consistently.
  ZIP_GZip_InitParams = CAST_TO_FN_PTR(ZIP_GZip_InitParams_t, dll_lookup("ZIP_GZip_InitParams", path, false));
  ZIP_GZip_Fully = CAST_TO_FN_PTR(ZIP_GZip_Fully_t, dll_lookup("ZIP_GZip_Fully", path, false));
}

static void load_zip_library(bool vm_exit_on_failure) {
  assert(!is_loaded(), "should not load zip library twice");
  char path[JVM_MAXPATHLEN];

  if (is_vm_statically_linked()) {
    _zip_handle = os::get_default_process_handle();
  } else {
    // Load the libzip shared library and lookup the needed functions.
    if (os::dll_locate_lib(&path[0], sizeof path, Arguments::get_dll_dir(), "zip")) {
      char ebuf[1024];
      _zip_handle = os::dll_load(&path[0], &ebuf[0], sizeof ebuf);
    }
    if (_zip_handle == nullptr) {
      if (vm_exit_on_failure) {
        vm_exit_during_initialization("Unable to load zip library", &path[0]);
      }
      return;
    }
  }

  store_function_pointers(&path[0], vm_exit_on_failure);
  Atomic::release_store(&_loaded, true);
  assert(is_loaded(), "invariant");
}

//
// Helper mutex class that also ensures that java threads
// are in _thread_in_native when loading the zip library.
//
class ZipLibraryLoaderLock : public StackObj {
 private:
  static Semaphore _lock;
  JavaThread* _jt;
 public:
   ZipLibraryLoaderLock() : _jt(nullptr) {
    Thread* thread = Thread::current_or_null();
    if (thread != nullptr && thread->is_Java_thread()) {
      JavaThread* const jt = JavaThread::cast(thread);
      if (jt->thread_state() != _thread_in_native) {
        _jt = jt;
        ThreadStateTransition::transition_from_vm(jt, _thread_in_native, false);
      }
    }
    _lock.wait();
  }
  ~ZipLibraryLoaderLock() {
    _lock.signal();
    if (_jt != nullptr) {
      ThreadStateTransition::transition_from_native(_jt, _thread_in_vm, false);
    }
  }
};

Semaphore ZipLibraryLoaderLock::_lock(1);

static void initialize(bool vm_exit_on_failure = true) {
  if (is_loaded()) {
    return;
  }
  ZipLibraryLoaderLock lock;
  if (not_loaded()) {
    load_zip_library(vm_exit_on_failure);
  }
}

void** ZipLibrary::open(const char* name, char** pmsg) {
  initialize();
  assert(ZIP_Open != nullptr, "invariant");
  return ZIP_Open(name, pmsg);
}

void ZipLibrary::close(jzfile* zip) {
  assert(is_loaded(), "invariant");
  assert(ZIP_Close != nullptr, "invariant");
  ZIP_Close(zip);
}

jzentry* ZipLibrary::find_entry(jzfile* zip, const char* name, jint* sizeP, jint* nameLen) {
  initialize();
  assert(ZIP_FindEntry != nullptr, "invariant");
  return ZIP_FindEntry(zip, name, sizeP, nameLen);
}

jboolean ZipLibrary::read_entry(jzfile* zip, jzentry* entry, unsigned char* buf, char* namebuf) {
  initialize();
  assert(ZIP_ReadEntry != nullptr, "invariant");
  return ZIP_ReadEntry(zip, entry, buf, namebuf);
}

void ZipLibrary::free_entry(jzfile* zip, jzentry* entry) {
  initialize();
  assert(ZIP_FreeEntry != nullptr, "invariant");
  ZIP_FreeEntry(zip, entry);
}

jint ZipLibrary::crc32(jint crc, const jbyte* buf, jint len) {
  initialize();
  assert(ZIP_CRC32 != nullptr, "invariant");
  return ZIP_CRC32(crc, buf, len);
}

const char* ZipLibrary::init_params(size_t block_size, size_t* needed_out_size, size_t* needed_tmp_size, int level) {
  initialize(false);
  if (ZIP_GZip_InitParams == nullptr) {
    return "Cannot get ZIP_GZip_InitParams function";
  }
  return ZIP_GZip_InitParams(block_size, needed_out_size, needed_tmp_size, level);
}

size_t ZipLibrary::compress(char* in, size_t in_size, char* out, size_t out_size, char* tmp, size_t tmp_size, int level, char* buf, const char** pmsg) {
  initialize(false);
  if (ZIP_GZip_Fully == nullptr) {
    *pmsg = "Cannot get ZIP_GZip_Fully function";
    return 0;
  }
  return ZIP_GZip_Fully(in, in_size, out, out_size, tmp, tmp_size, level, buf, pmsg);
}

void* ZipLibrary::handle() {
  initialize();
  assert(is_loaded(), "invariant");
  assert(_zip_handle != nullptr, "invariant");
  return _zip_handle;
}
