/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _JLI_UTIL_H
#define _JLI_UTIL_H

#include <stdlib.h>
#include <jni.h>

void *JLI_MemAlloc(size_t size);
void *JLI_MemRealloc(void *ptr, size_t size);
char *JLI_StringDup(const char *s1);
void  JLI_MemFree(void *ptr);
char **JLI_CopyArgs(int argc, const char **iargv);
int   JLI_StrCCmp(const char *s1, const char* s2);


#define JLI_StrLen(p1)          strlen((p1))
#define JLI_StrChr(p1, p2)      strchr((p1), (p2))
#define JLI_StrRChr(p1, p2)     strrchr((p1), (p2))
#define JLI_StrCmp(p1, p2)      strcmp((p1), (p2))
#define JLI_StrNCmp(p1, p2, p3) strncmp((p1), (p2), (p3))
#define JLI_StrCat(p1, p2)      strcat((p1), (p2))
#define JLI_StrCpy(p1, p2)      strcpy((p1), (p2))
#define JLI_StrNCpy(p1, p2, p3) strncpy((p1), (p2), (p3))
#define JLI_StrStr(p1, p2)      strstr((p1), (p2))
#define JLI_StrSpn(p1, p2)      strspn((p1), (p2))
#define JLI_StrCSpn(p1, p2)     strcspn((p1), (p2))
#define JLI_StrPBrk(p1, p2)     strpbrk((p1), (p2))

/* On Windows lseek() is in io.h rather than the location dictated by POSIX. */
#ifdef _WIN32
#include <windows.h>
#include <io.h>
#define JLI_StrCaseCmp(p1, p2)          stricmp((p1), (p2))
#define JLI_StrNCaseCmp(p1, p2, p3)     strnicmp((p1), (p2), (p3))
#else
#include <unistd.h>
#define JLI_StrCaseCmp(p1, p2)          strcasecmp((p1), (p2))
#define JLI_StrNCaseCmp(p1, p2, p3)     strncasecmp((p1), (p2), (p3))
#endif /* _WIN32 */

/*
 * Make launcher spit debug output.
 */
void     JLI_TraceLauncher(const char* fmt, ...);
void     JLI_SetTraceLauncher();
jboolean JLI_IsTraceLauncher();

#endif  /* _JLI_UTIL_H */
