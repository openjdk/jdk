/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 SAP SE. All rights reserved.
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
#include "jvm.h"
#include "runtime/os.hpp"
#include "services/heapDumperCompression.hpp"
#include "utilities/zipLibrary.hpp"


char const* FileWriter::open_writer() {
  assert(_fd < 0, "Must not already be open");

  _fd = os::create_binary_file(_path, _overwrite);

  if (_fd < 0) {
    return os::strerror(errno);
  }

  return nullptr;
}

FileWriter::~FileWriter() {
  if (_fd >= 0) {
    ::close(_fd);
    _fd = -1;
  }
}

char const* FileWriter::write_buf(char* buf, ssize_t size) {
  assert(_fd >= 0, "Must be open");
  assert(size > 0, "Must write at least one byte");

  if (!os::write(_fd, buf, (size_t)size)) {
    return os::strerror(errno);
  }

  return nullptr;
}

char const* GZipCompressor::init(size_t block_size, size_t* needed_out_size,
                                 size_t* needed_tmp_size) {
  _block_size = block_size;
  _is_first = true;
  char const* result = ZipLibrary::init_params(block_size, needed_out_size,
                                               needed_tmp_size, _level);
  *needed_out_size += 1024; // Add extra space for the comment in the first chunk.
  return result;
}

char const* GZipCompressor::compress(char* in, size_t in_size, char* out, size_t out_size,
                                     char* tmp, size_t tmp_size, size_t* compressed_size) {
  char const* msg = nullptr;
  if (_is_first) {
    char buf[128];
    // Write the block size used as a comment in the first gzip chunk, so the
    // code used to read it later can make a good choice of the buffer sizes it uses.
    jio_snprintf(buf, sizeof(buf), "HPROF BLOCKSIZE=" SIZE_FORMAT, _block_size);
    *compressed_size = ZipLibrary::compress(in, in_size, out, out_size, tmp, tmp_size, _level, buf, &msg);
    _is_first = false;
  } else {
    *compressed_size = ZipLibrary::compress(in, in_size, out, out_size, tmp, tmp_size, _level, nullptr, &msg);
  }

  return msg;
}
