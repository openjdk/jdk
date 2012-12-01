/*
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
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

/* decode_instructions -- dump a range of addresses as native instructions
   This implements the protocol required by the HotSpot PrintAssembly option.

   The start_va, end_va is the virtual address the region of memory to
   disasemble and buffer contains the instructions to decode,
   Disassembling instructions in the current address space is done by
   having start_va == buffer.

   The option string, if not empty, is interpreted by the disassembler implementation.

   The printf callback is 'fprintf' or any other workalike.
   It is called as (*printf_callback)(printf_stream, "some format...", some, format, args).

   The event callback receives an event tag (a string) and an argument (a void*).
   It is called as (*event_callback)(event_stream, "tag", arg).

   Events:
     <insn pc='%p'>             begin an instruction, at a given location
     </insn pc='%d'>            end an instruction, at a given location
     <addr value='%p'/>         emit the symbolic value of an address

   A tag format is one of three basic forms: "tag", "/tag", "tag/",
   where tag is a simple identifier, signifying (as in XML) a element start,
   element end, and standalone element.  (To render as XML, add angle brackets.)
*/
#ifndef SHARED_TOOLS_HSDIS_H
#define SHARED_TOOLS_HSDIS_H

extern
#ifdef DLL_EXPORT
  DLL_EXPORT
#endif
void* decode_instructions_virtual(uintptr_t start_va, uintptr_t end_va,
                                  unsigned char* buffer, uintptr_t length,
                                  void* (*event_callback)(void*, const char*, void*),
                                  void* event_stream,
                                  int (*printf_callback)(void*, const char*, ...),
                                  void* printf_stream,
                                  const char* options,
                                  int newline /* bool value for nice new line */);

/* This is the compatability interface for older versions of hotspot */
extern
#ifdef DLL_ENTRY
  DLL_ENTRY
#endif
void* decode_instructions(void* start_pv, void* end_pv,
                    void* (*event_callback)(void*, const char*, void*),
                    void* event_stream,
                    int   (*printf_callback)(void*, const char*, ...),
                    void* printf_stream,
                    const char* options);

/* convenience typedefs */

typedef void* (*decode_instructions_event_callback_ftype)  (void*, const char*, void*);
typedef int   (*decode_instructions_printf_callback_ftype) (void*, const char*, ...);
typedef void* (*decode_func_vtype) (uintptr_t start_va, uintptr_t end_va,
                                    unsigned char* buffer, uintptr_t length,
                                    decode_instructions_event_callback_ftype event_callback,
                                    void* event_stream,
                                    decode_instructions_printf_callback_ftype printf_callback,
                                    void* printf_stream,
                                    const char* options,
                                    int newline);
typedef void* (*decode_func_stype) (void* start_pv, void* end_pv,
                                    decode_instructions_event_callback_ftype event_callback,
                                    void* event_stream,
                                    decode_instructions_printf_callback_ftype printf_callback,
                                    void* printf_stream,
                                    const char* options);
#endif /* SHARED_TOOLS_HSDIS_H */
