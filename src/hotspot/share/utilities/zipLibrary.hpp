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

#ifndef SHARE_UTILITIES_ZIPLIBRARY_HPP
#define SHARE_UTILITIES_ZIPLIBRARY_HPP

#include "memory/allocation.hpp"

 // Type definitions for zip file and zip file entry
typedef void* jzfile;
typedef struct {
  char* name;                   /* entry name */
  jlong time;                   /* modification time */
  jlong size;                   /* size of uncompressed data */
  jlong csize;                  /* size of compressed data (zero if uncompressed) */
  jint crc;                     /* crc of uncompressed data */
  char* comment;                /* optional zip file comment */
  jbyte* extra;                 /* optional extra data */
  jlong pos;                    /* position of LOC header (if negative) or data */
} jzentry;

class ZipLibrary : AllStatic {
 public:
  static void** open(const char* name, char** pmsg);
  static void close(jzfile* zip);
  static jzentry* find_entry(jzfile* zip, const char* name, jint* sizeP, jint* nameLen);
  static jboolean read_entry(jzfile* zip, jzentry* entry, unsigned char* buf, char* namebuf);
  static void free_entry(jzfile* zip, jzentry* entry);
  static jint crc32(jint crc, const jbyte* buf, jint len);
  static const char* init_params(size_t block_size, size_t* needed_out_size, size_t* needed_tmp_size, int level);
  static size_t compress(char* in, size_t in_size, char* out, size_t out_size, char* tmp, size_t tmp_size, int level, char* buf, const char** pmsg);
  static void* handle();
};

#endif // SHARE_UTILITIES_ZIPLIBRARY_HPP
