/*
 * Copyright (c) 2004, 2005, Oracle and/or its affiliates. All rights reserved.
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

/* Native Platform Toolkit */

#ifndef  _NPT_MD_H
#define _NPT_MD_H

#include <windows.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>

#define NPT_LIBNAME "npt.dll"

#define NPT_INITIALIZE(pnpt,version,options)                            \
    {                                                                   \
        HINSTANCE jvm;                                                  \
        void   *_handle;                                                \
        void   *_sym;                                                   \
        char    buf[FILENAME_MAX+32];                                   \
        char   *lastSlash;                                              \
                                                                        \
        if ( (pnpt) == NULL ) NPT_ERROR("NptEnv* is NULL");             \
        _handle =  NULL;                                                \
        *(pnpt) = NULL;                                                 \
        buf[0] = 0;                                                     \
        jvm = LoadLibrary("jvm.dll");                                   \
        if ( jvm == NULL ) NPT_ERROR("Cannot find jvm.dll");            \
        GetModuleFileName(jvm, buf, FILENAME_MAX);                      \
        lastSlash = strrchr(buf, '\\');                                 \
        if ( lastSlash != NULL ) {                                      \
            *lastSlash = '\0';                                          \
            (void)strcat(buf, "\\..\\");                                \
            (void)strcat(buf, NPT_LIBNAME);                             \
            _handle =  LoadLibrary(buf);                                \
        }                                                               \
        if ( _handle == NULL ) NPT_ERROR("Cannot open library");        \
        _sym = GetProcAddress(_handle, "nptInitialize");                \
        if ( _sym == NULL ) NPT_ERROR("Cannot find nptInitialize");     \
        ((NptInitialize)_sym)((pnpt), version, (options));              \
        if ( *(pnpt) == NULL ) NPT_ERROR("Cannot initialize NptEnv");   \
        (*(pnpt))->libhandle = _handle;                                 \
    }

#define NPT_TERMINATE(npt,options)                                      \
    {                                                                   \
        void *_handle;                                                  \
        void *_sym;                                                     \
                                                                        \
        if ( (npt) == NULL ) NPT_ERROR("NptEnv* is NULL");              \
        _handle = (npt)->libhandle;                                     \
        if ( _handle == NULL ) NPT_ERROR("npt->libhandle is NULL");     \
        _sym = GetProcAddress(_handle, "nptTerminate");                 \
        if ( _sym == NULL ) NPT_ERROR("Cannot find nptTerminate");      \
        ((NptTerminate)_sym)((npt), (options));                         \
        (void)FreeLibrary(_handle);                                     \
    }

#endif
