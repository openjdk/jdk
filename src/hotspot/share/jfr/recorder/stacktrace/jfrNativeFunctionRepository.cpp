/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/stacktrace/jfrNativeFunctionRepository.hpp"
#include "jfr/utilities/jfrAllocation.hpp"
#include "jvm_io.h"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"

#ifdef LINUX
#include <elf.h>
#include <link.h>
#include <unistd.h>
#endif

// A native library referenced by at least one sampled pc.
class JfrNativeLibrary : public JfrCHeapObj {
 public:
  static constexpr size_t BUILD_ID_LEN = 20;

  JfrNativeLibrary* _next;
  char* _path;             // file path
  uintptr_t _base;         // where the library's virtual address 0 is mapped to
  uintptr_t _min_addr;     // min address of a loaded segment
  uintptr_t _max_addr;     // max address of a loaded segment
  traceid _id;             // assigned lazily
  bool _written;
  char _build_id[BUILD_ID_LEN * 2 + 1]; // 2 hex digits per byte + \0 terminator

  JfrNativeLibrary(const char* path, uintptr_t base, uintptr_t min_addr, uintptr_t max_addr, JfrNativeLibrary* next) :
    _next(next), _path(os::strdup(path, mtTracing)), _base(base), _min_addr(min_addr), _max_addr(max_addr),
    _id(0), _written(false) {
    _build_id[0] = '\0';
  }

  ~JfrNativeLibrary() {
    os::free(_path);
  }

  bool contains(uintptr_t pc) const {
    return pc >= _min_addr && pc < _max_addr;
  }
};

// Sampled pcs are stored in bitmaps, each covering 256-byte address range.
// This allows storing only one range object per 256 pcs in a map.
// NativeFunction ID consists of the range ID and an offset within the range.
class JfrNativeAddressRange {
 private:
  static constexpr int SHIFT = 8;
  static constexpr uintptr_t SIZE = 1 << SHIFT;
  static constexpr uintptr_t MASK = SIZE - 1;

  // TODO: use denser bitmap on AArch64, where pcs are 4-byte aligned
  uintptr_t _bitmap[SIZE / BitsPerWord]; // referenced pcs within the range

 public:
  traceid _id;

  static traceid key(uintptr_t pc) {
    return pc >> SHIFT;
  }

  JfrNativeAddressRange() : _id(0) {
    memset(_bitmap, 0, sizeof(_bitmap));
  }

  traceid function_id_for(uintptr_t pc) const {
    return (_id << SHIFT) | (pc & MASK);
  }

  // Returns true if the pc was not referenced before.
  bool set(uintptr_t pc) {
    const uintptr_t bit = pc & MASK;
    uintptr_t& word = _bitmap[bit / BitsPerWord];
    const uintptr_t mask = static_cast<uintptr_t>(1) << (bit % BitsPerWord);
    if ((word & mask) != 0) {
      return false;
    }
    word |= mask;
    return true;
  }
};

static constexpr unsigned MIN_ADDRESS_TABLE_SIZE = 1013;
static constexpr unsigned MAX_ADDRESS_TABLE_SIZE = 100003;

JfrNativeAddressTable* JfrNativeFunctionRepository::_addresses = nullptr;
JfrPcList* JfrNativeFunctionRepository::_unwritten_pcs = nullptr;
JfrNativeLibrary* JfrNativeFunctionRepository::_libraries = nullptr;
traceid JfrNativeFunctionRepository::_next_range_id = 1;
traceid JfrNativeFunctionRepository::_next_library_id = 1;

bool JfrNativeFunctionRepository::create() {
  assert(_addresses == nullptr, "invariant");
  assert(_unwritten_pcs == nullptr, "invariant");
  _addresses = new (mtTracing) JfrNativeAddressTable(MIN_ADDRESS_TABLE_SIZE, MAX_ADDRESS_TABLE_SIZE);
  _unwritten_pcs = new JfrPcList(64);
  return true;
}

void JfrNativeFunctionRepository::destroy() {
  delete _addresses;
  _addresses = nullptr;
  delete _unwritten_pcs;
  _unwritten_pcs = nullptr;
  clear_libraries();
}

traceid JfrNativeFunctionRepository::id_for(uintptr_t pc) {
  assert(JfrStacktrace_lock->owned_by_self(), "invariant");

  bool created;
  JfrNativeAddressRange* range = _addresses->put_if_absent(JfrNativeAddressRange::key(pc), &created);
  if (created) {
    range->_id = _next_range_id++;
    _addresses->maybe_grow();
  }
  if (range->set(pc)) {
    _unwritten_pcs->append(pc);
  }
  return range->function_id_for(pc);
}

#ifdef LINUX

static constexpr char HEX[] = "0123456789abcdef";

// Searches for .note.gnu.build-id in PT_NOTE segment.
// If found, stores build-id in the provided array and returns true.
static bool parse_build_id(const dl_phdr_info* info, const ElfW(Phdr)* phdr, char* build_id) {
  const char* pos = reinterpret_cast<const char*>(info->dlpi_addr + phdr->p_vaddr);
  const char* end = pos + phdr->p_memsz;

  while (pos + sizeof(ElfW(Nhdr)) <= end) {
    const ElfW(Nhdr)* nhdr = reinterpret_cast<const ElfW(Nhdr)*>(pos);
    const char* name = pos + sizeof(ElfW(Nhdr));
    const char* desc = name + align_up(nhdr->n_namesz, 4);
    pos = desc + align_up(nhdr->n_descsz, 4);
    if (pos > end) {
      return false;
    }

    if (nhdr->n_type == NT_GNU_BUILD_ID && nhdr->n_namesz == 4 && memcmp(name, "GNU", 4) == 0) {
      size_t bytes = MIN2(static_cast<size_t>(nhdr->n_descsz), JfrNativeLibrary::BUILD_ID_LEN);
      for (size_t i = 0; i < bytes; i++) {
        unsigned char b = static_cast<unsigned char>(desc[i]);
        build_id[i * 2] = HEX[b >> 4];
        build_id[i * 2 + 1] = HEX[b & 0xf];
      }
      build_id[bytes * 2] = '\0';
      return true;
    }
  }
  return false;
}

static int library_callback(dl_phdr_info* info, size_t size, void* data) {
  // Compute library boundaries from the loadable segments
  uintptr_t lo = UINTPTR_MAX;
  uintptr_t hi = 0;
  for (int i = 0; i < info->dlpi_phnum; i++) {
    const ElfW(Phdr)* phdr = &info->dlpi_phdr[i];
    if (phdr->p_type == PT_LOAD) {
      lo = MIN2(lo, info->dlpi_addr + phdr->p_vaddr);
      hi = MAX2(hi, info->dlpi_addr + phdr->p_vaddr + phdr->p_memsz);
    }
  }
  if (lo >= hi) {
    return 0;
  }

  const char* path = info->dlpi_name;
  if (path == nullptr || path[0] == '\0') {
    path = "java"; // TODO: Get main executable path from /proc/self/exe
  }

  JfrNativeLibrary** libraries = static_cast<JfrNativeLibrary**>(data);
  for (JfrNativeLibrary* lib = *libraries; lib != nullptr; lib = lib->_next) {
    if (lib->_base == info->dlpi_addr && strcmp(lib->_path, path) == 0) {
      return 0;
    }
  }

  JfrNativeLibrary* lib = new JfrNativeLibrary(path, info->dlpi_addr, lo, hi, *libraries);
  for (int i = 0; i < info->dlpi_phnum; i++) {
    const ElfW(Phdr)* phdr = &info->dlpi_phdr[i];
    if (phdr->p_type == PT_NOTE && parse_build_id(info, phdr, lib->_build_id)) {
      break;
    }
  }

  *libraries = lib;
  return 0;
}

void JfrNativeFunctionRepository::update_libraries() {
  dl_iterate_phdr(&library_callback, &_libraries);
}

#else

void JfrNativeFunctionRepository::update_libraries() {}

#endif // LINUX

JfrNativeLibrary* JfrNativeFunctionRepository::find_library(uintptr_t pc) {
  for (JfrNativeLibrary* lib = _libraries; lib != nullptr; lib = lib->_next) {
    if (lib->contains(pc)) {
      return lib;
    }
  }
  return nullptr;
}

void JfrNativeFunctionRepository::resolve_and_write_function(JfrChunkWriter& cw, uintptr_t pc, JfrNativeLibrary* lib) {
  char name[1024];
  int offset;
  if (!os::dll_address_to_function_name(reinterpret_cast<address>(pc), name, sizeof(name), &offset, true)) {
    name[0] = '\0';
  }

  const JfrNativeAddressRange* range = _addresses->get(JfrNativeAddressRange::key(pc));
  assert(range != nullptr, "invariant");

  cw.write(range->function_id_for(pc));
  if (lib != nullptr) {
    if (lib->_id == 0) {
      lib->_id = _next_library_id++;
    }
    cw.write(lib->_id);
    cw.write(pc - lib->_base);
  } else {
    cw.write(static_cast<u8>(0));
    cw.write(pc);
  }
  cw.write(name);
}

void JfrNativeFunctionRepository::clear_functions() {
  MutexLocker lock(JfrStacktrace_lock, Mutex::_no_safepoint_check_flag);
  delete _addresses;
  _addresses = new (mtTracing) JfrNativeAddressTable(MIN_ADDRESS_TABLE_SIZE, MAX_ADDRESS_TABLE_SIZE);
  _next_range_id = 1;
}

void JfrNativeFunctionRepository::clear_libraries() {
  JfrNativeLibrary* lib = _libraries;
  while (lib != nullptr) {
    JfrNativeLibrary* next = lib->_next;
    delete lib;
    lib = next;
  }
  _libraries = nullptr;
  _next_library_id = 1;
}

size_t JfrNativeFunctionRepository::write_functions(JfrChunkWriter& cw, bool clear) {
  assert(_addresses != nullptr, "invariant");

  // Atomically drain the list of unwritten PC addresses under the lock
  JfrPcList pcs;
  {
    MutexLocker lock(JfrStacktrace_lock, Mutex::_no_safepoint_check_flag);
    pcs.swap(_unwritten_pcs);
  }

  if (pcs.is_nonempty()) {
    // Sorting helps to optimize library lookup for neighbouring addresses
    pcs.sort([](uintptr_t* a, uintptr_t* b) -> int {
      return *a < *b ? -1 : (*a > *b ? 1 : 0);
    });

    update_libraries();

    JfrNativeLibrary* lib = nullptr;
    for (int i = 0; i < pcs.length(); i++) {
      uintptr_t pc = pcs.at(i);
      if (lib == nullptr || !lib->contains(pc)) {
        lib = find_library(pc);
      }
      resolve_and_write_function(cw, pc, lib);
    }
  }

  if (clear) {
    clear_functions();
  }

  return pcs.length();
}

size_t JfrNativeFunctionRepository::write_libraries(JfrChunkWriter& cw, bool clear) {
  size_t count = 0;
  for (JfrNativeLibrary* lib = _libraries; lib != nullptr; lib = lib->_next) {
    if (lib->_id != 0 && !lib->_written) {
      cw.write(lib->_id);
      cw.write(lib->_path);
      cw.write(static_cast<u8>(lib->_base));
      cw.write(lib->_build_id);
      lib->_written = true;
      count++;
    }
  }

  if (clear) {
    clear_libraries();
  }

  return count;
}
