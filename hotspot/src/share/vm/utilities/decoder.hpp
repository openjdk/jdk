/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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


#ifndef __DECODER_HPP
#define __DECODER_HPP

#include "memory/allocation.hpp"

#ifdef _WINDOWS
#include <windows.h>
#include <imagehlp.h>

// functions needed for decoding symbols
typedef DWORD (WINAPI *pfn_SymSetOptions)(DWORD);
typedef BOOL  (WINAPI *pfn_SymInitialize)(HANDLE, PCTSTR, BOOL);
typedef BOOL  (WINAPI *pfn_SymGetSymFromAddr64)(HANDLE, DWORD64, PDWORD64, PIMAGEHLP_SYMBOL64);
typedef DWORD (WINAPI *pfn_UndecorateSymbolName)(const char*, char*, DWORD, DWORD);

#elif defined(__APPLE__)

#else

class ElfFile;

#endif // _WINDOWS


class Decoder: public StackObj {

 public:
  // status code for decoding native C frame
  enum decoder_status {
         no_error,             // successfully decoded frames
         out_of_memory,        // out of memory
         file_invalid,         // invalid elf file
         file_not_found,       // could not found symbol file (on windows), such as jvm.pdb or jvm.map
         helper_not_found,     // could not load dbghelp.dll (Windows only)
         helper_func_error,    // decoding functions not found (Windows only)
         helper_init_error,    // SymInitialize failed (Windows only)
         symbol_not_found      // could not find the symbol
  };

 public:
  Decoder() { initialize(); };
  ~Decoder() { uninitialize(); };

  static bool can_decode_C_frame_in_vm();

  static void initialize();
  static void uninitialize();

#ifdef _WINDOWS
  static decoder_status    decode(address addr, char *buf, int buflen, int *offset);
#else
  static decoder_status    decode(address addr, const char* filepath, char *buf, int buflen, int *offset);
#endif

  static bool              demangle(const char* symbol, char *buf, int buflen);

  static decoder_status    get_status() { return _decoder_status; };

#if !defined(_WINDOWS) && !defined(__APPLE__)
 private:
  static ElfFile*         get_elf_file(const char* filepath);
#endif // _WINDOWS


 private:
  static decoder_status     _decoder_status;
  static bool               _initialized;

#ifdef _WINDOWS
  static HMODULE                   _dbghelp_handle;
  static bool                      _can_decode_in_vm;
  static pfn_SymGetSymFromAddr64   _pfnSymGetSymFromAddr64;
  static pfn_UndecorateSymbolName  _pfnUndecorateSymbolName;
#elif __APPLE__
#else
  static ElfFile*                  _opened_elf_files;
#endif // _WINDOWS
};

#endif // __DECODER_HPP
