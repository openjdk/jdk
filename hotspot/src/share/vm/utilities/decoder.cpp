/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "prims/jvm.h"
#include "utilities/decoder.hpp"

Decoder::decoder_status  Decoder::_decoder_status = Decoder::no_error;
bool                     Decoder::_initialized = false;

#ifndef _WINDOWS

// Implementation of common functionalities among Solaris and Linux
#include "utilities/elfFile.hpp"

ElfFile* Decoder::_opened_elf_files = NULL;

bool Decoder::can_decode_C_frame_in_vm() {
  return true;
}

void Decoder::initialize() {
  _initialized = true;
}

void Decoder::uninitialize() {
  if (_opened_elf_files != NULL) {
    delete _opened_elf_files;
    _opened_elf_files = NULL;
  }
  _initialized = false;
}

Decoder::decoder_status Decoder::decode(address addr, const char* filepath, char *buf, int buflen, int *offset) {
  if (_decoder_status != no_error) {
    return _decoder_status;
  }

  ElfFile* file = get_elf_file(filepath);
  if (_decoder_status != no_error) {
    return _decoder_status;
  }

  const char* symbol = file->decode(addr, offset);
  if (file->get_status() == out_of_memory) {
    _decoder_status = out_of_memory;
    return _decoder_status;
  } else if (symbol != NULL) {
    if (!demangle(symbol, buf, buflen)) {
      jio_snprintf(buf, buflen, "%s", symbol);
    }
    return no_error;
  } else {
    return symbol_not_found;
  }
}

ElfFile* Decoder::get_elf_file(const char* filepath) {
  if (_decoder_status != no_error) {
    return NULL;
  }
  ElfFile* file = _opened_elf_files;
  while (file != NULL) {
    if (file->same_elf_file(filepath)) {
      return file;
    }
    file = file->m_next;
  }

  file = new ElfFile(filepath);
  if (file == NULL) {
    _decoder_status = out_of_memory;
  }
  if (_opened_elf_files != NULL) {
    file->m_next = _opened_elf_files;
  }

  _opened_elf_files = file;
  return file;
}

#endif

