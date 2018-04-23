/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

//Definitions of our util functions

void* must_malloc(size_t size);
#ifndef USE_MTRACE
#define mtrace(c, ptr, size)
#else
void mtrace(char c, void* ptr, size_t size);
#endif

// overflow management
#define POVERFLOW ((uint)-1)
#define PSIZE_MAX (POVERFLOW/2)  /* normal size limit */

inline size_t scale_size(size_t size, size_t scale) {
  return (size > PSIZE_MAX / scale) ? POVERFLOW : size * scale;
}

inline size_t add_size(size_t size1, size_t size2) {
  return ((size1 | size2 | (size1 + size2)) > PSIZE_MAX)
    ? POVERFLOW
    : size1 + size2;
}

inline size_t add_size(size_t size1, size_t size2, int size3) {
  return add_size(add_size(size1, size2), size3);
}

// These may be expensive, because they have to go via Java TSD,
// if the optional u argument is missing.
struct unpacker;
extern void unpack_abort(const char* msg, unpacker* u = null);
extern bool unpack_aborting(unpacker* u = null);

#ifndef PRODUCT
inline bool endsWith(const char* str, const char* suf) {
  size_t len1 = strlen(str);
  size_t len2 = strlen(suf);
  return (len1 > len2 && 0 == strcmp(str + (len1-len2), suf));
}
#endif

void mkdirs(int oklen, char* path);
