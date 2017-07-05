/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 *
 *
 * Indictly used by SolarisRunpath.sh; this file is used to generate
 * the shared objects:
 *
 * ./lib/sparc/lib32/liblibrary.so
 * ./lib/sparc/lib32/lib32/liblibrary.so
 *
 * ./lib/sparc/lib64/liblibrary.so
 * ./lib/sparc/lib64/lib64/liblibrary.so
 *
 * ./lib/i386/lib32/liblibrary.so
 * ./lib/i386/lib32/lib32/liblibrary.so
 *
 * The function defined below returns either 0 or the size of an
 * integer in the data model used to compile the file (32 for ILP; 64
 * for LP).  The libraries in ./lib/$ARCH/lib$DM return 0; those in
 * ./lib/$ARCH/lib$DM/lib$DM return 32 or 64.
 */


#include <jni.h>
#include "libraryCaller.h"

#ifndef RETURN_VALUE
#define RETURN_VALUE 0
#endif

JNIEXPORT jint JNICALL Java_libraryCaller_number
(JNIEnv *je, jclass jc) {
  return RETURN_VALUE;
}
