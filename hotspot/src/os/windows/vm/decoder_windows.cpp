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

#include "precompiled.hpp"
#include "prims/jvm.h"
#include "runtime/os.hpp"
#include "utilities/decoder.hpp"

HMODULE                   Decoder::_dbghelp_handle = NULL;
bool                      Decoder::_can_decode_in_vm = false;
pfn_SymGetSymFromAddr64   Decoder::_pfnSymGetSymFromAddr64 = NULL;
pfn_UndecorateSymbolName  Decoder::_pfnUndecorateSymbolName = NULL;

void Decoder::initialize() {
  if (!_initialized) {
    _initialized = true;

    HINSTANCE handle = os::win32::load_Windows_dll("dbghelp.dll", NULL, 0);
    if (!handle) {
      _decoder_status = helper_not_found;
        return;
    }

    _dbghelp_handle = handle;

    pfn_SymSetOptions _pfnSymSetOptions = (pfn_SymSetOptions)::GetProcAddress(handle, "SymSetOptions");
    pfn_SymInitialize _pfnSymInitialize = (pfn_SymInitialize)::GetProcAddress(handle, "SymInitialize");
    _pfnSymGetSymFromAddr64 = (pfn_SymGetSymFromAddr64)::GetProcAddress(handle, "SymGetSymFromAddr64");
    _pfnUndecorateSymbolName = (pfn_UndecorateSymbolName)GetProcAddress(handle, "UnDecorateSymbolName");

    if (_pfnSymSetOptions == NULL || _pfnSymInitialize == NULL || _pfnSymGetSymFromAddr64 == NULL) {
      _pfnSymGetSymFromAddr64 = NULL;
      _pfnUndecorateSymbolName = NULL;
      ::FreeLibrary(handle);
      _dbghelp_handle = NULL;
      _decoder_status = helper_func_error;
      return;
    }

    _pfnSymSetOptions(SYMOPT_UNDNAME | SYMOPT_DEFERRED_LOADS);
    if (!_pfnSymInitialize(GetCurrentProcess(), NULL, TRUE)) {
      _pfnSymGetSymFromAddr64 = NULL;
      _pfnUndecorateSymbolName = NULL;
      ::FreeLibrary(handle);
      _dbghelp_handle = NULL;
      _decoder_status = helper_init_error;
      return;
    }

     // find out if jvm.dll contains private symbols, by decoding
     // current function and comparing the result
     address addr = (address)Decoder::initialize;
     char buf[MAX_PATH];
     if (decode(addr, buf, sizeof(buf), NULL) == no_error) {
       _can_decode_in_vm = !strcmp(buf, "Decoder::initialize");
     }
  }
}

void Decoder::uninitialize() {
  assert(_initialized, "Decoder not yet initialized");
  _pfnSymGetSymFromAddr64 = NULL;
  _pfnUndecorateSymbolName = NULL;
  if (_dbghelp_handle != NULL) {
    ::FreeLibrary(_dbghelp_handle);
  }
  _initialized = false;
}

bool Decoder::can_decode_C_frame_in_vm() {
  initialize();
  return  _can_decode_in_vm;
}


Decoder::decoder_status Decoder::decode(address addr, char *buf, int buflen, int *offset) {
  assert(_initialized, "Decoder not yet initialized");
  if (_pfnSymGetSymFromAddr64 != NULL) {
    PIMAGEHLP_SYMBOL64 pSymbol;
    char symbolInfo[MAX_PATH + sizeof(IMAGEHLP_SYMBOL64)];
    pSymbol = (PIMAGEHLP_SYMBOL64)symbolInfo;
    pSymbol->MaxNameLength = MAX_PATH;
    pSymbol->SizeOfStruct = sizeof(IMAGEHLP_SYMBOL64);
    DWORD64 displacement;
    if (_pfnSymGetSymFromAddr64(::GetCurrentProcess(), (DWORD64)addr, &displacement, pSymbol)) {
      if (buf != NULL) {
        if (!demangle(pSymbol->Name, buf, buflen)) {
          jio_snprintf(buf, buflen, "%s", pSymbol->Name);
        }
      }
      if (offset != NULL) *offset = (int)displacement;
      return no_error;
    }
  }
  return helper_not_found;
}

bool Decoder::demangle(const char* symbol, char *buf, int buflen) {
  assert(_initialized, "Decoder not yet initialized");
  return _pfnUndecorateSymbolName != NULL &&
         _pfnUndecorateSymbolName(symbol, buf, buflen, UNDNAME_COMPLETE);
}

