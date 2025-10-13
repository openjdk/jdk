/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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


#if !defined(_WINDOWS) && !defined(__APPLE__)
#include "decoder_elf.hpp"
#include "logging/log.hpp"
#include "runtime/os.hpp"

ElfDecoder::~ElfDecoder() {
  if (_opened_elf_files != nullptr) {
    delete _opened_elf_files;
    _opened_elf_files = nullptr;
  }
}

bool ElfDecoder::decode(address addr, char *buf, int buflen, int* offset, const char* filepath, bool demangle_name) {
  assert(filepath, "null file path");
  assert(buf != nullptr && buflen > 0, "Invalid buffer");
  if (has_error()) return false;
  ElfFile* file = get_elf_file(filepath);
  if (file == nullptr) {
    return false;
  }

  if (!file->decode(addr, buf, buflen, offset)) {
    return false;
  }
  if (demangle_name && (buf[0] != '\0')) {
    demangle(buf, buf, buflen);
  }
  return true;
}

bool ElfDecoder::get_source_info(address pc, char* filename, size_t filename_len, int* line, bool is_pc_after_call) {
#if defined(__clang_major__) && (__clang_major__ < 5)
  DWARF_LOG_ERROR("The DWARF parser only supports Clang 5.0+.");
  return false;
#else
  assert(filename != nullptr && line != nullptr, "arguments should not be null");
  assert(filename_len > 1, "buffer must be able to at least hold 1 character with a null terminator");
  filename[0] = '\0';
  *line = -1;

  char filepath[JVM_MAXPATHLEN];
  filepath[JVM_MAXPATHLEN - 1] = '\0';
  int offset_in_library = -1;
  if (!os::dll_address_to_library_name(pc, filepath, sizeof(filepath), &offset_in_library)) {
    // Method not found. offset_in_library should not overflow.
    DWARF_LOG_ERROR("Did not find library for address " PTR_FORMAT, p2i(pc))
    return false;
  }

  if (filepath[JVM_MAXPATHLEN - 1] != '\0') {
    DWARF_LOG_ERROR("File path is too large to fit into buffer of size %d", JVM_MAXPATHLEN);
    return false;
  }

  const uint32_t unsigned_offset_in_library = (uint32_t)offset_in_library;

  ElfFile* file = get_elf_file(filepath);
  if (file == nullptr) {
    return false;
  }
  DWARF_LOG_INFO("##### Find filename and line number for offset " INT32_FORMAT_X_0 " in library %s #####",
                 unsigned_offset_in_library, filepath);

  if (!file->get_source_info(unsigned_offset_in_library, filename, filename_len, line, is_pc_after_call)) {
    // Return sane values.
    filename[0] = '\0';
    *line = -1;
    return false;
  }

  DWARF_LOG_SUMMARY("pc: " PTR_FORMAT ", offset: " INT32_FORMAT_X_0 ", filename: %s, line: %u",
                       p2i(pc), offset_in_library, filename, *line);
  DWARF_LOG_INFO("") // To structure the debug output better.
  return true;
#endif // clang
}


ElfFile* ElfDecoder::get_elf_file(const char* filepath) {
  ElfFile* file;

  file = _opened_elf_files;
  while (file != nullptr) {
    if (file->same_elf_file(filepath)) {
      return file;
    }
    file = file->next();
  }

  file = new (std::nothrow)ElfFile(filepath);
  if (file != nullptr) {
    if (_opened_elf_files != nullptr) {
      file->set_next(_opened_elf_files);
    }
    _opened_elf_files = file;
  }

  return file;
}
#endif // !_WINDOWS && !__APPLE__
