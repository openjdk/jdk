/*
 * Copyright (c) 1994, 2005, Oracle and/or its affiliates. All rights reserved.
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
 * Maintains a list of currently loaded DLLs (Dynamic Link Libraries)
 * and their associated handles. Library names are case-insensitive.
 */

#include <windows.h>
#include <stdio.h>
#include <string.h>

#include "hpi_impl.h"

#include "path_md.h"

/*
 * create a string for the JNI native function name by adding the
 * appropriate decorations.
 *
 * On Win32, "__stdcall" functions are exported differently, depending
 * on the compiler. In MSVC 4.0, they are decorated with a "_" in the
 * beginning, and @nnn in the end, where nnn is the number of bytes in
 * the arguments (in decimal). Borland C++ exports undecorated names.
 *
 * sysBuildFunName handles different encodings depending on the value
 * of encodingIndex. It returns 0 when handed an out-of-range
 * encodingIndex.
 */
int
sysBuildFunName(char *name, int nameMax, int args_size, int encodingIndex)
{
  if (encodingIndex == 0) {
    /* For Microsoft MSVC 4.0 */
    char suffix[6];    /* This is enough since Java never has more than
                           256 words of arguments. */
    int nameLen;
    int i;

    sprintf(suffix, "@%d", args_size * 4);

    nameLen = strlen(name);
    if (nameLen >= nameMax - 7)
        return 1;
    for(i = nameLen; i > 0; i--)
        name[i] = name[i-1];
    name[0] = '_';

    sprintf(name + nameLen + 1, "%s", suffix);
    return 1;
  } else if (encodingIndex == 1)
    /* For Borland, etc. */
    return 1;
  else
    return 0;
}

/*
 * Build a machine dependent library name out of a path and file name.
 */
void
sysBuildLibName(char *holder, int holderlen, char *pname, char *fname)
{
    const int pnamelen = pname ? strlen(pname) : 0;
    const char c = (pnamelen > 0) ? pname[pnamelen-1] : 0;

    /* Quietly truncates on buffer overflow. Should be an error. */
    if (pnamelen + strlen(fname) + 10 > holderlen) {
        *holder = '\0';
        return;
    }

    if (pnamelen == 0) {
        sprintf(holder, "%s.dll", fname);
    } else if (c == ':' || c == '\\') {
        sprintf(holder, "%s%s.dll", pname, fname);
    } else {
        sprintf(holder, "%s\\%s.dll", pname, fname);
    }
}

void *
sysLoadLibrary(const char * name, char *err_buf, int err_buflen)
{
    void *result = LoadLibrary(name);
    if (result == NULL) {
        /* Error message is pretty lame, try to make a better guess. */
        long errcode = GetLastError();
        if (errcode == ERROR_MOD_NOT_FOUND) {
            strncpy(err_buf, "Can't find dependent libraries", err_buflen-2);
            err_buf[err_buflen-1] = '\0';
        } else {
            sysGetLastErrorString(err_buf, err_buflen);
        }
    }
    return result;
}

void sysUnloadLibrary(void *handle)
{
    FreeLibrary(handle);
}

void * sysFindLibraryEntry(void *handle, const char *name)
{
    return GetProcAddress(handle, name);
}
