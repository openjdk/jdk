/*
 * Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_CPU_WINDOWS_X86_UNWIND_WINDOWS_X86_HPP
#define OS_CPU_WINDOWS_X86_UNWIND_WINDOWS_X86_HPP

typedef unsigned char UBYTE;

// This structure is used to define an UNWIND_INFO that
// only has an ExceptionHandler.  There are no UnwindCodes
// declared.
typedef struct _UNWIND_INFO_EH_ONLY {
    UBYTE Version       : 3;
    UBYTE Flags         : 5;
    UBYTE SizeOfProlog;
    UBYTE CountOfCodes;
    UBYTE FrameRegister : 4;
    UBYTE FrameOffset   : 4;
    union {
       OPTIONAL ULONG ExceptionHandler;
       OPTIONAL ULONG FunctionEntry;
    };
    OPTIONAL ULONG ExceptionData[1];
} UNWIND_INFO_EH_ONLY, *PUNWIND_INFO_EH_ONLY;

/*
typedef struct _RUNTIME_FUNCTION {
    ULONG BeginAddress;
    ULONG EndAddress;
    ULONG UnwindData;
} RUNTIME_FUNCTION, *PRUNTIME_FUNCTION;
*/

#endif // OS_CPU_WINDOWS_X86_UNWIND_WINDOWS_X86_HPP
