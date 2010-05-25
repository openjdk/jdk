/*
 * Copyright (c) 1994, 2004, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Machine dependent path name and file name manipulation code
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <sys/stat.h>

#include <windows.h>
#include <errno.h>

#include "hpi_impl.h"

#undef DEBUG_PATH               /* Define this to debug path code */

#define isfilesep(c) ((c) == '/' || (c) == '\\')
#define islb(c)      (IsDBCSLeadByte((BYTE)(c)))


/* Convert a pathname to native format.  On win32, this involves forcing all
   separators to be '\\' rather than '/' (both are legal inputs, but Win95
   sometimes rejects '/') and removing redundant separators.  The input path is
   assumed to have been converted into the character encoding used by the local
   system.  Because this might be a double-byte encoding, care is taken to
   treat double-byte lead characters correctly.

   This procedure modifies the given path in place, as the result is never
   longer than the original.  There is no error return; this operation always
   succeeds. */

char *
sysNativePath(char *path)
{
    char *src = path, *dst = path, *end = path;
    char *colon = NULL;         /* If a drive specifier is found, this will
                                   point to the colon following the drive
                                   letter */

    /* Assumption: '/', '\\', ':', and drive letters are never lead bytes */
    sysAssert(!islb('/') && !islb('\\') && !islb(':'));

    /* Check for leading separators */
    while (isfilesep(*src)) src++;
    if (isalpha(*src) && !islb(*src) && src[1] == ':') {
        /* Remove leading separators if followed by drive specifier.  This
           hack is necessary to support file URLs containing drive
           specifiers (e.g., "file://c:/path").  As a side effect,
           "/c:/path" can be used as an alternative to "c:/path". */
        *dst++ = *src++;
        colon = dst;
        *dst++ = ':'; src++;
    } else {
        src = path;
        if (isfilesep(src[0]) && isfilesep(src[1])) {
            /* UNC pathname: Retain first separator; leave src pointed at
               second separator so that further separators will be collapsed
               into the second separator.  The result will be a pathname
               beginning with "\\\\" followed (most likely) by a host name. */
            src = dst = path + 1;
            path[0] = '\\';     /* Force first separator to '\\' */
        }
    }

    end = dst;

    /* Remove redundant separators from remainder of path, forcing all
       separators to be '\\' rather than '/'. Also, single byte space
       characters are removed from the end of the path because those
       are not legal ending characters on this operating system.
    */
    while (*src != '\0') {
        if (isfilesep(*src)) {
            *dst++ = '\\'; src++;
            while (isfilesep(*src)) src++;
            if (*src == '\0') { /* Check for trailing separator */
                end = dst;
                if (colon == dst - 2) break;                      /* "z:\\" */
                if (dst == path + 1) break;                       /* "\\" */
                if (dst == path + 2 && isfilesep(path[0])) {
                    /* "\\\\" is not collapsed to "\\" because "\\\\" marks the
                       beginning of a UNC pathname.  Even though it is not, by
                       itself, a valid UNC pathname, we leave it as is in order
                       to be consistent with the path canonicalizer as well
                       as the win32 APIs, which treat this case as an invalid
                       UNC pathname rather than as an alias for the root
                       directory of the current drive. */
                    break;
                }
                end = --dst;    /* Path does not denote a root directory, so
                                   remove trailing separator */
                break;
            }
            end = dst;
        } else {
            if (islb(*src)) {   /* Copy a double-byte character */
                *dst++ = *src++;
                if (*src) {
                    *dst++ = *src++;
                }
                end = dst;
            } else {            /* Copy a single-byte character */
                char c = *src++;
                *dst++ = c;
                /* Space is not a legal ending character */
                if (c != ' ')
                    end = dst;
            }
        }
    }

    *end = '\0';

    /* For "z:", add "." to work around a bug in the C runtime library */
    if (colon == dst - 1) {
        path[2] = '.';
        path[3] = '\0';
    }

#ifdef DEBUG_PATH
    jio_fprintf(stderr, "sysNativePath: %s\n", path);
#endif DEBUG_PATH
    return path;
}
