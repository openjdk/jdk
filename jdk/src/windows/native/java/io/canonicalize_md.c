/*
 * Copyright (c) 1998, 2005, Oracle and/or its affiliates. All rights reserved.
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
 * Pathname canonicalization for Win32 file systems
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <assert.h>
#include <sys/stat.h>

#include <windows.h>
#include <winbase.h>
#include <errno.h>
#include "io_util_md.h"

#undef DEBUG_PATH        /* Define this to debug path code */

#define isfilesep(c) ((c) == '/' || (c) == '\\')
#define wisfilesep(c) ((c) == L'/' || (c) == L'\\')
#define islb(c)      (IsDBCSLeadByte((BYTE)(c)))


/* Copy bytes to dst, not going past dend; return dst + number of bytes copied,
   or NULL if dend would have been exceeded.  If first != '\0', copy that byte
   before copying bytes from src to send - 1. */

static char *
cp(char *dst, char *dend, char first, char *src, char *send)
{
    char *p = src, *q = dst;
    if (first != '\0') {
        if (q < dend) {
            *q++ = first;
        } else {
            errno = ENAMETOOLONG;
            return NULL;
        }
    }
    if (send - p > dend - q) {
        errno = ENAMETOOLONG;
        return NULL;
    }
    while (p < send) {
        *q++ = *p++;
    }
    return q;
}

/* Wide character version of cp */

static WCHAR*
wcp(WCHAR *dst, WCHAR *dend, WCHAR first, WCHAR *src, WCHAR *send)
{
    WCHAR *p = src, *q = dst;
    if (first != L'\0') {
        if (q < dend) {
            *q++ = first;
        } else {
            errno = ENAMETOOLONG;
            return NULL;
        }
    }
    if (send - p > dend - q) {
        errno = ENAMETOOLONG;
        return NULL;
    }
    while (p < send)
        *q++ = *p++;
    return q;
}


/* Find first instance of '\\' at or following start.  Return the address of
   that byte or the address of the null terminator if '\\' is not found. */

static char *
nextsep(char *start)
{
    char *p = start;
    int c;
    while ((c = *p) && (c != '\\')) {
        p += ((islb(c) && p[1]) ? 2 : 1);
    }
    return p;
}

/* Wide character version of nextsep */

static WCHAR *
wnextsep(WCHAR *start)
{
    WCHAR *p = start;
    int c;
    while ((c = *p) && (c != L'\\'))
        p++;
    return p;
}

/* Tell whether the given string contains any wildcard characters */

static int
wild(char *start)
{
    char *p = start;
    int c;
    while (c = *p) {
        if ((c == '*') || (c == '?')) return 1;
        p += ((islb(c) && p[1]) ? 2 : 1);
    }
    return 0;
}

/* Wide character version of wild */

static int
wwild(WCHAR *start)
{
    WCHAR *p = start;
    int c;
    while (c = *p) {
        if ((c == L'*') || (c == L'?'))
            return 1;
        p++;
    }
    return 0;
}

/* Tell whether the given string contains prohibited combinations of dots.
   In the canonicalized form no path element may have dots at its end.
   Allowed canonical paths: c:\xa...dksd\..ksa\.lk    c:\...a\.b\cd..x.x
   Prohibited canonical paths: c:\..\x  c:\x.\d c:\...
*/
static int
dots(char *start)
{
    char *p = start;
    while (*p) {
        if ((p = strchr(p, '.')) == NULL) // find next occurence of '.'
            return 0; // no more dots
        p++; // next char
        while ((*p) == '.') // go to the end of dots
            p++;
        if (*p && (*p != '\\')) // path element does not end with a dot
            p++; // go to the next char
        else
            return 1; // path element does end with a dot - prohibited
    }
    return 0; // no prohibited combinations of dots found
}

/* Wide character version of dots */
static int
wdots(WCHAR *start)
{
    WCHAR *p = start;
    while (*p) {
        if ((p = wcschr(p, L'.')) == NULL) // find next occurence of '.'
            return 0; // no more dots
        p++; // next char
        while ((*p) == L'.') // go to the end of dots
            p++;
        if (*p && (*p != L'\\')) // path element does not end with a dot
            p++; // go to the next char
        else
            return 1; // path element does end with a dot - prohibited
    }
    return 0; // no prohibited combinations of dots found
}

/* If the lookup of a particular prefix fails because the file does not exist,
   because it is of the wrong type, because access is denied, or because the
   network is unreachable then canonicalization does not fail, it terminates
   successfully after copying the rest of the original path to the result path.
   Other I/O errors cause an error return.
*/

int
lastErrorReportable()
{
    DWORD errval = GetLastError();
    if ((errval == ERROR_FILE_NOT_FOUND)
        || (errval == ERROR_DIRECTORY)
        || (errval == ERROR_PATH_NOT_FOUND)
        || (errval == ERROR_BAD_NETPATH)
        || (errval == ERROR_BAD_NET_NAME)
        || (errval == ERROR_ACCESS_DENIED)
        || (errval == ERROR_NETWORK_UNREACHABLE)
        || (errval == ERROR_NETWORK_ACCESS_DENIED)) {
        return 0;
    }

#ifdef DEBUG_PATH
    jio_fprintf(stderr, "canonicalize: errval %d\n", errval);
#endif
    return 1;
}

/* Convert a pathname to canonical form.  The input orig_path is assumed to
   have been converted to native form already, via JVM_NativePath().  This is
   necessary because _fullpath() rejects duplicate separator characters on
   Win95, though it accepts them on NT. */

int
canonicalize(char *orig_path, char *result, int size)
{
    WIN32_FIND_DATA fd;
    HANDLE h;
    char path[1024];    /* Working copy of path */
    char *src, *dst, *dend;

    /* Reject paths that contain wildcards */
    if (wild(orig_path)) {
        errno = EINVAL;
        return -1;
    }

    /* Collapse instances of "foo\.." and ensure absoluteness.  Note that
       contrary to the documentation, the _fullpath procedure does not require
       the drive to be available.  It also does not reliably change all
       occurrences of '/' to '\\' on Win95, so now JVM_NativePath does that. */
    if(!_fullpath(path, orig_path, sizeof(path))) {
        return -1;
    }

    /* Correction for Win95: _fullpath may leave a trailing "\\"
       on a UNC pathname */
    if ((path[0] == '\\') && (path[1] == '\\')) {
        char *p = path + strlen(path);
        if ((p[-1] == '\\') && !islb(p[-2])) {
            p[-1] = '\0';
        }
    }

    if (dots(path)) /* Check for prohibited combinations of dots */
        return -1;

    src = path;            /* Start scanning here */
    dst = result;        /* Place results here */
    dend = dst + size;        /* Don't go to or past here */

    /* Copy prefix, assuming path is absolute */
    if (isalpha(src[0]) && (src[1] == ':') && (src[2] == '\\')) {
        /* Drive specifier */
        *src = toupper(*src);    /* Canonicalize drive letter */
        if (!(dst = cp(dst, dend, '\0', src, src + 2))) {
            return -1;
        }
        src += 2;
    } else if ((src[0] == '\\') && (src[1] == '\\')) {
        /* UNC pathname */
        char *p;
        p = nextsep(src + 2);    /* Skip past host name */
        if (!*p) {
        /* A UNC pathname must begin with "\\\\host\\share",
           so reject this path as invalid if there is no share name */
            errno = EINVAL;
            return -1;
    }
    p = nextsep(p + 1);    /* Skip past share name */
    if (!(dst = cp(dst, dend, '\0', src, p))) {
        return -1;
    }
    src = p;
    } else {
        /* Invalid path */
        errno = EINVAL;
        return -1;
    }

    /* Windows 95/98/Me bug - FindFirstFile fails on network mounted drives */
    /* for root pathes like "E:\" . If the path has this form, we should  */
    /* simply return it, it is already canonicalized. */
    if (strlen(path) == 3 && path[1] == ':' && path[2] == '\\') {
        /* At this point we have already copied the drive specifier ("z:")*/
        /* so we need to copy "\" and the null character. */
        result[2] = '\\';
        result[3] = '\0';
        return 0;
    }

    /* At this point we have copied either a drive specifier ("z:") or a UNC
       prefix ("\\\\host\\share") to the result buffer, and src points to the
       first byte of the remainder of the path.  We now scan through the rest
       of the path, looking up each prefix in order to find the true name of
       the last element of each prefix, thereby computing the full true name of
       the original path. */
    while (*src) {
        char *p = nextsep(src + 1);    /* Find next separator */
        char c = *p;
        assert(*src == '\\');        /* Invariant */
        *p = '\0';            /* Temporarily clear separator */
        h = FindFirstFile(path, &fd);    /* Look up prefix */
        *p = c;                /* Restore separator */
        if (h != INVALID_HANDLE_VALUE) {
            /* Lookup succeeded; append true name to result and continue */
            FindClose(h);
            if (!(dst = cp(dst, dend, '\\',
                           fd.cFileName,
                           fd.cFileName + strlen(fd.cFileName)))) {
                return -1;
            }
            src = p;
            continue;
        } else {
            if (!lastErrorReportable()) {
                if (!(dst = cp(dst, dend, '\0', src, src + strlen(src)))) {
                    return -1;
                }
                break;
            } else {
                return -1;
            }
        }
    }

    if (dst >= dend) {
    errno = ENAMETOOLONG;
    return -1;
    }
    *dst = '\0';
    return 0;

}


/* Convert a pathname to canonical form.  The input prefix is assumed
   to be in canonical form already, and the trailing filename must not
   contain any wildcard, dot/double dot, or other "tricky" characters
   that are rejected by the canonicalize() routine above.  This
   routine is present to allow the canonicalization prefix cache to be
   used while still returning canonical names with the correct
   capitalization. */

int
canonicalizeWithPrefix(char* canonicalPrefix, char* pathWithCanonicalPrefix, char *result, int size)
{
    WIN32_FIND_DATA fd;
    HANDLE h;
    char *src, *dst, *dend;

    src = pathWithCanonicalPrefix;
    dst = result;        /* Place results here */
    dend = dst + size;   /* Don't go to or past here */

    h = FindFirstFile(pathWithCanonicalPrefix, &fd);    /* Look up file */
    if (h != INVALID_HANDLE_VALUE) {
        /* Lookup succeeded; concatenate true name to prefix */
        FindClose(h);
        if (!(dst = cp(dst, dend, '\0',
                       canonicalPrefix,
                       canonicalPrefix + strlen(canonicalPrefix)))) {
            return -1;
        }
        if (!(dst = cp(dst, dend, '\\',
                       fd.cFileName,
                       fd.cFileName + strlen(fd.cFileName)))) {
            return -1;
        }
    } else {
        if (!lastErrorReportable()) {
            if (!(dst = cp(dst, dend, '\0', src, src + strlen(src)))) {
                return -1;
            }
        } else {
            return -1;
        }
    }

    if (dst >= dend) {
        errno = ENAMETOOLONG;
        return -1;
    }
    *dst = '\0';
    return 0;
}


/* Wide character version of canonicalize. Size is a wide-character size. */

int
wcanonicalize(WCHAR *orig_path, WCHAR *result, int size)
{
    WIN32_FIND_DATAW fd;
    HANDLE h;
    WCHAR *path;    /* Working copy of path */
    WCHAR *src, *dst, *dend, c;

    /* Reject paths that contain wildcards */
    if (wwild(orig_path)) {
        errno = EINVAL;
        return -1;
    }

    if ((path = (WCHAR*)malloc(size * sizeof(WCHAR))) == NULL)
        return -1;

    /* Collapse instances of "foo\.." and ensure absoluteness.  Note that
       contrary to the documentation, the _fullpath procedure does not require
       the drive to be available.  */
    if(!_wfullpath(path, orig_path, size)) {
        goto err;
    }

    if (wdots(path)) /* Check for prohibited combinations of dots */
        goto err;

    src = path;            /* Start scanning here */
    dst = result;        /* Place results here */
    dend = dst + size;        /* Don't go to or past here */

    /* Copy prefix, assuming path is absolute */
    c = src[0];
    if (((c <= L'z' && c >= L'a') || (c <= L'Z' && c >= L'A'))
       && (src[1] == L':') && (src[2] == L'\\')) {
        /* Drive specifier */
        *src = towupper(*src);    /* Canonicalize drive letter */
        if (!(dst = wcp(dst, dend, L'\0', src, src + 2))) {
            goto err;
        }

        src += 2;
    } else if ((src[0] == L'\\') && (src[1] == L'\\')) {
        /* UNC pathname */
        WCHAR *p;
        p = wnextsep(src + 2);    /* Skip past host name */
        if (!*p) {
            /* A UNC pathname must begin with "\\\\host\\share",
               so reject this path as invalid if there is no share name */
            errno = EINVAL;
            goto err;
        }
        p = wnextsep(p + 1);    /* Skip past share name */
        if (!(dst = wcp(dst, dend, L'\0', src, p)))
            goto err;
        src = p;
    } else {
        /* Invalid path */
        errno = EINVAL;
        goto err;
    }
    /* At this point we have copied either a drive specifier ("z:") or a UNC
       prefix ("\\\\host\\share") to the result buffer, and src points to the
       first byte of the remainder of the path.  We now scan through the rest
       of the path, looking up each prefix in order to find the true name of
       the last element of each prefix, thereby computing the full true name of
       the original path. */
    while (*src) {
        WCHAR *p = wnextsep(src + 1);    /* Find next separator */
        WCHAR c = *p;
        WCHAR *pathbuf;
        int pathlen;

        assert(*src == L'\\');        /* Invariant */
        *p = L'\0';            /* Temporarily clear separator */

        if ((pathlen = wcslen(path)) > MAX_PATH - 1) {
            pathbuf = getPrefixed(path, pathlen);
            h = FindFirstFileW(pathbuf, &fd);    /* Look up prefix */
            free(pathbuf);
        } else
            h = FindFirstFileW(path, &fd);    /* Look up prefix */

        *p = c;                /* Restore separator */
        if (h != INVALID_HANDLE_VALUE) {
            /* Lookup succeeded; append true name to result and continue */
            FindClose(h);
            if (!(dst = wcp(dst, dend, L'\\', fd.cFileName,
                            fd.cFileName + wcslen(fd.cFileName)))){
                goto err;
            }
            src = p;
            continue;
        } else {
            if (!lastErrorReportable()) {
               if (!(dst = wcp(dst, dend, L'\0', src, src + wcslen(src)))){
                   goto err;
               }
                break;
            } else {
                goto err;
            }
        }
    }

    if (dst >= dend) {
    errno = ENAMETOOLONG;
        goto err;
    }
    *dst = L'\0';
    free(path);
    return 0;

 err:
    free(path);
    return -1;
}


/* Wide character version of canonicalizeWithPrefix. */

int
wcanonicalizeWithPrefix(WCHAR *canonicalPrefix, WCHAR *pathWithCanonicalPrefix, WCHAR *result, int size)
{
    WIN32_FIND_DATAW fd;
    HANDLE h;
    WCHAR *src, *dst, *dend;
    WCHAR *pathbuf;
    int pathlen;

    src = pathWithCanonicalPrefix;
    dst = result;        /* Place results here */
    dend = dst + size;   /* Don't go to or past here */


    if ((pathlen=wcslen(pathWithCanonicalPrefix)) > MAX_PATH - 1) {
        pathbuf = getPrefixed(pathWithCanonicalPrefix, pathlen);
        h = FindFirstFileW(pathbuf, &fd);    /* Look up prefix */
        free(pathbuf);
    } else
        h = FindFirstFileW(pathWithCanonicalPrefix, &fd);    /* Look up prefix */
    if (h != INVALID_HANDLE_VALUE) {
        /* Lookup succeeded; append true name to result and continue */
        FindClose(h);
        if (!(dst = wcp(dst, dend, L'\0',
                        canonicalPrefix,
                        canonicalPrefix + wcslen(canonicalPrefix)))) {
            return -1;
        }
        if (!(dst = wcp(dst, dend, L'\\',
                        fd.cFileName,
                        fd.cFileName + wcslen(fd.cFileName)))) {
            return -1;
        }
    } else {
        if (!lastErrorReportable()) {
            if (!(dst = wcp(dst, dend, L'\0', src, src + wcslen(src)))) {
                return -1;
            }
        } else {
            return -1;
        }
    }

    if (dst >= dend) {
        errno = ENAMETOOLONG;
        return -1;
    }
    *dst = L'\0';
    return 0;
}


/* The appropriate location of getPrefixed() should be io_util_md.c, but
   java.lang.instrument package has hardwired canonicalize_md.c into their
   dll, to avoid complicate solution such as including io_util_md.c into
   that package, as a workaround we put this method here.
 */

/* copy \\?\ or \\?\UNC\ to the front of path*/
WCHAR*
getPrefixed(const WCHAR* path, int pathlen) {
    WCHAR* pathbuf = (WCHAR*)malloc((pathlen + 10) * sizeof (WCHAR));
    if (pathbuf != 0) {
        if (path[0] == L'\\' && path[1] == L'\\') {
            if (path[2] == L'?' && path[3] == L'\\'){
                /* if it already has a \\?\ don't do the prefix */
                wcscpy(pathbuf, path );
            } else {
                /* only UNC pathname includes double slashes here */
                wcscpy(pathbuf, L"\\\\?\\UNC\0");
                wcscat(pathbuf, path + 1);
            }
        } else {
            wcscpy(pathbuf, L"\\\\?\\\0");
            wcscat(pathbuf, path );
        }
    }
    return pathbuf;
}
