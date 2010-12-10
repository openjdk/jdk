/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <string.h>
#include "jli_util.h"

#ifdef GAMMA
#ifdef _WINDOWS
#define strdup _strdup
#endif
#endif

/*
 * Returns a pointer to a block of at least 'size' bytes of memory.
 * Prints error message and exits if the memory could not be allocated.
 */
void *
JLI_MemAlloc(size_t size)
{
    void *p = malloc(size);
    if (p == 0) {
        perror("malloc");
        exit(1);
    }
    return p;
}

/*
 * Equivalent to realloc(size).
 * Prints error message and exits if the memory could not be reallocated.
 */
void *
JLI_MemRealloc(void *ptr, size_t size)
{
    void *p = realloc(ptr, size);
    if (p == 0) {
        perror("realloc");
        exit(1);
    }
    return p;
}

/*
 * Wrapper over strdup(3C) which prints an error message and exits if memory
 * could not be allocated.
 */
char *
JLI_StringDup(const char *s1)
{
    char *s = strdup(s1);
    if (s == NULL) {
        perror("strdup");
        exit(1);
    }
    return s;
}

/*
 * Very equivalent to free(ptr).
 * Here to maintain pairing with the above routines.
 */
void
JLI_MemFree(void *ptr)
{
    free(ptr);
}
