/*
 * Copyright 1995-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * Posix-compatible directory access routines
 */

#include <windows.h>
#include <direct.h>                    /* For _getdrive() */
#include <errno.h>
#include <assert.h>

#include "dirent_md.h"


/* Caller must have already run dirname through JVM_NativePath, which removes
   duplicate slashes and converts all instances of '/' into '\\'. */

DIR *
opendir(const char *dirname)
{
    DIR *dirp = (DIR *)malloc(sizeof(DIR));
    DWORD fattr;
    char alt_dirname[4] = { 0, 0, 0, 0 };

    if (dirp == 0) {
        errno = ENOMEM;
        return 0;
    }

    /*
     * Win32 accepts "\" in its POSIX stat(), but refuses to treat it
     * as a directory in FindFirstFile().  We detect this case here and
     * prepend the current drive name.
     */
    if (dirname[1] == '\0' && dirname[0] == '\\') {
        alt_dirname[0] = _getdrive() + 'A' - 1;
        alt_dirname[1] = ':';
        alt_dirname[2] = '\\';
        alt_dirname[3] = '\0';
        dirname = alt_dirname;
    }

    dirp->path = (char *)malloc(strlen(dirname) + 5);
    if (dirp->path == 0) {
        free(dirp);
        errno = ENOMEM;
        return 0;
    }
    strcpy(dirp->path, dirname);

    fattr = GetFileAttributes(dirp->path);
    if (fattr == ((DWORD)-1)) {
        free(dirp->path);
        free(dirp);
        errno = ENOENT;
        return 0;
    } else if ((fattr & FILE_ATTRIBUTE_DIRECTORY) == 0) {
        free(dirp->path);
        free(dirp);
        errno = ENOTDIR;
        return 0;
    }

    /* Append "*.*", or possibly "\\*.*", to path */
    if (dirp->path[1] == ':'
        && (dirp->path[2] == '\0'
            || (dirp->path[2] == '\\' && dirp->path[3] == '\0'))) {
        /* No '\\' needed for cases like "Z:" or "Z:\" */
        strcat(dirp->path, "*.*");
    } else {
        strcat(dirp->path, "\\*.*");
    }

    dirp->handle = FindFirstFile(dirp->path, &dirp->find_data);
    if (dirp->handle == INVALID_HANDLE_VALUE) {
        if (GetLastError() != ERROR_FILE_NOT_FOUND) {
            free(dirp->path);
            free(dirp);
            errno = EACCES;
            return 0;
        }
    }
    return dirp;
}

struct dirent *
readdir(DIR *dirp)
{
    if (dirp->handle == INVALID_HANDLE_VALUE) {
        return 0;
    }

    strcpy(dirp->dirent.d_name, dirp->find_data.cFileName);

    if (!FindNextFile(dirp->handle, &dirp->find_data)) {
        if (GetLastError() == ERROR_INVALID_HANDLE) {
            errno = EBADF;
            return 0;
        }
        FindClose(dirp->handle);
        dirp->handle = INVALID_HANDLE_VALUE;
    }

    return &dirp->dirent;
}

int
closedir(DIR *dirp)
{
    if (dirp->handle != INVALID_HANDLE_VALUE) {
        if (!FindClose(dirp->handle)) {
            errno = EBADF;
            return -1;
        }
        dirp->handle = INVALID_HANDLE_VALUE;
    }
    free(dirp->path);
    free(dirp);
    return 0;
}

void
rewinddir(DIR *dirp)
{
    if (dirp->handle != INVALID_HANDLE_VALUE) {
        FindClose(dirp->handle);
    }
    dirp->handle = FindFirstFile(dirp->path, &dirp->find_data);
}
