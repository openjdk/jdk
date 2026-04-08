/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_WINDOWS_OS_WINDOWS_FILEINFO_HPP
#define OS_WINDOWS_OS_WINDOWS_FILEINFO_HPP

#include <windows.h>

// SDK-guarded definitions for GetFileInformationByName and related types.
// These are provided so that OpenJDK can be built with older Windows SDKs
// that do not include these declarations.
//
// Keep FILE_STAT_BASIC_INFORMATION and FILE_INFO_BY_NAME_CLASS in sync with
// WindowsNativeDispatcher.c.

#if !defined(NTDDI_WIN10_NI) || !(NTDDI_VERSION >= NTDDI_WIN10_NI)

typedef struct _FILE_STAT_BASIC_INFORMATION {
    LARGE_INTEGER FileId;
    LARGE_INTEGER CreationTime;
    LARGE_INTEGER LastAccessTime;
    LARGE_INTEGER LastWriteTime;
    LARGE_INTEGER ChangeTime;
    LARGE_INTEGER AllocationSize;
    LARGE_INTEGER EndOfFile;
    ULONG         FileAttributes;
    ULONG         ReparseTag;
    ULONG         NumberOfLinks;
    ULONG         DeviceType;
    ULONG         DeviceCharacteristics;
    ULONG         Reserved;
    LARGE_INTEGER VolumeSerialNumber;
    FILE_ID_128   FileId128;
} FILE_STAT_BASIC_INFORMATION;

typedef enum _FILE_INFO_BY_NAME_CLASS {
    FileStatByNameInfo,
    FileStatLxByNameInfo,
    FileCaseSensitiveByNameInfo,
    FileStatBasicByNameInfo,
    MaximumFileInfoByNameClass
} FILE_INFO_BY_NAME_CLASS;

#endif // !defined(NTDDI_WIN10_NI) || !(NTDDI_VERSION >= NTDDI_WIN10_NI)

typedef BOOL (WINAPI *PGetFileInformationByName)(
    PCWSTR                  FileName,
    FILE_INFO_BY_NAME_CLASS FileInformationClass,
    PVOID                   FileInfoBuffer,
    ULONG                   FileInfoBufferSize
);

#endif // OS_WINDOWS_OS_WINDOWS_FILEINFO_HPP
