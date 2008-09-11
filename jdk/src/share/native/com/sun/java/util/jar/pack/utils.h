/*
 * Copyright 2001-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

//Definitions of our util functions

void* must_malloc(int size);
#ifndef USE_MTRACE
#define mtrace(c, ptr, size)
#else
void mtrace(char c, void* ptr, size_t size);
#endif

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
