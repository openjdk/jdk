/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/arguments.hpp"
#include "decoder_windows.hpp"

WindowsDecoder::WindowsDecoder() {
  _dbghelp_handle = NULL;
  _can_decode_in_vm = false;
  _pfnSymGetSymFromAddr64 = NULL;
  _pfnUndecorateSymbolName = NULL;
#ifdef AMD64
  _pfnStackWalk64 = NULL;
  _pfnSymFunctionTableAccess64 = NULL;
  _pfnSymGetModuleBase64 = NULL;
#endif
  _decoder_status = no_error;
  initialize();
}

void WindowsDecoder::initialize() {
  if (!has_error() && _dbghelp_handle == NULL) {
    HMODULE handle = ::LoadLibrary("dbghelp.dll");
    if (!handle) {
      _decoder_status = helper_not_found;
      return;
    }

    _dbghelp_handle = handle;

    pfn_SymSetOptions _pfnSymSetOptions = (pfn_SymSetOptions)::GetProcAddress(handle, "SymSetOptions");
    pfn_SymInitialize _pfnSymInitialize = (pfn_SymInitialize)::GetProcAddress(handle, "SymInitialize");
    _pfnSymGetSymFromAddr64 = (pfn_SymGetSymFromAddr64)::GetProcAddress(handle, "SymGetSymFromAddr64");
    _pfnUndecorateSymbolName = (pfn_UndecorateSymbolName)::GetProcAddress(handle, "UnDecorateSymbolName");

    if (_pfnSymSetOptions == NULL || _pfnSymInitialize == NULL || _pfnSymGetSymFromAddr64 == NULL) {
      uninitialize();
      _decoder_status = helper_func_error;
      return;
    }

#ifdef AMD64
    _pfnStackWalk64 = (pfn_StackWalk64)::GetProcAddress(handle, "StackWalk64");
    _pfnSymFunctionTableAccess64 = (pfn_SymFunctionTableAccess64)::GetProcAddress(handle, "SymFunctionTableAccess64");
    _pfnSymGetModuleBase64 = (pfn_SymGetModuleBase64)::GetProcAddress(handle, "SymGetModuleBase64");
    if (_pfnStackWalk64 == NULL || _pfnSymFunctionTableAccess64 == NULL || _pfnSymGetModuleBase64 == NULL) {
      // We can't call StackWalk64 to walk the stack, but we are still
      // able to decode the symbols. Let's limp on.
      _pfnStackWalk64 = NULL;
      _pfnSymFunctionTableAccess64 = NULL;
      _pfnSymGetModuleBase64 = NULL;
    }
#endif

    HANDLE hProcess = ::GetCurrentProcess();
    _pfnSymSetOptions(SYMOPT_UNDNAME | SYMOPT_DEFERRED_LOADS | SYMOPT_EXACT_SYMBOLS);
    if (!_pfnSymInitialize(hProcess, NULL, TRUE)) {
      _pfnSymGetSymFromAddr64 = NULL;
      _pfnUndecorateSymbolName = NULL;
      ::FreeLibrary(handle);
      _dbghelp_handle = NULL;
      _decoder_status = helper_init_error;
      return;
    }

    // set pdb search paths
    pfn_SymSetSearchPath  _pfn_SymSetSearchPath =
      (pfn_SymSetSearchPath)::GetProcAddress(handle, "SymSetSearchPath");
    pfn_SymGetSearchPath  _pfn_SymGetSearchPath =
      (pfn_SymGetSearchPath)::GetProcAddress(handle, "SymGetSearchPath");
    if (_pfn_SymSetSearchPath != NULL && _pfn_SymGetSearchPath != NULL) {
      char paths[MAX_PATH];
      int  len = sizeof(paths);
      if (!_pfn_SymGetSearchPath(hProcess, paths, len)) {
        paths[0] = '\0';
      } else {
        // available spaces in path buffer
        len -= (int)strlen(paths);
      }

      char tmp_path[MAX_PATH];
      DWORD dwSize;
      HMODULE hJVM = ::GetModuleHandle("jvm.dll");
      tmp_path[0] = '\0';
      // append the path where jvm.dll is located
      if (hJVM != NULL && (dwSize = ::GetModuleFileName(hJVM, tmp_path, sizeof(tmp_path))) > 0) {
        while (dwSize > 0 && tmp_path[dwSize] != '\\') {
          dwSize --;
        }

        tmp_path[dwSize] = '\0';

        if (dwSize > 0 && len > (int)dwSize + 1) {
          strncat(paths, os::path_separator(), 1);
          strncat(paths, tmp_path, dwSize);
          len -= dwSize + 1;
        }
      }

      // append $JRE/bin. Arguments::get_java_home actually returns $JRE
      // path
      char *p = Arguments::get_java_home();
      assert(p != NULL, "empty java home");
      size_t java_home_len = strlen(p);
      if (len > (int)java_home_len + 5) {
        strncat(paths, os::path_separator(), 1);
        strncat(paths, p, java_home_len);
        strncat(paths, "\\bin", 4);
        len -= (int)(java_home_len + 5);
      }

      // append $JDK/bin path if it exists
      assert(java_home_len < MAX_PATH, "Invalid path length");
      // assume $JRE is under $JDK, construct $JDK/bin path and
      // see if it exists or not
      if (strncmp(&p[java_home_len - 3], "jre", 3) == 0) {
        strncpy(tmp_path, p, java_home_len - 3);
        tmp_path[java_home_len - 3] = '\0';
        strncat(tmp_path, "bin", 3);

        // if the directory exists
        DWORD dwAttrib = GetFileAttributes(tmp_path);
        if (dwAttrib != INVALID_FILE_ATTRIBUTES &&
            (dwAttrib & FILE_ATTRIBUTE_DIRECTORY)) {
          // tmp_path should have the same length as java_home_len, since we only
          // replaced 'jre' with 'bin'
          if (len > (int)java_home_len + 1) {
            strncat(paths, os::path_separator(), 1);
            strncat(paths, tmp_path, java_home_len);
          }
        }
      }

      _pfn_SymSetSearchPath(hProcess, paths);
    }

     // find out if jvm.dll contains private symbols, by decoding
     // current function and comparing the result
     address addr = (address)Decoder::demangle;
     char buf[MAX_PATH];
     if (decode(addr, buf, sizeof(buf), NULL)) {
       _can_decode_in_vm = !strcmp(buf, "Decoder::demangle");
     }
  }
}

void WindowsDecoder::uninitialize() {
  _pfnSymGetSymFromAddr64 = NULL;
  _pfnUndecorateSymbolName = NULL;
#ifdef AMD64
  _pfnStackWalk64 = NULL;
  _pfnSymFunctionTableAccess64 = NULL;
  _pfnSymGetModuleBase64 = NULL;
#endif
  if (_dbghelp_handle != NULL) {
    ::FreeLibrary(_dbghelp_handle);
  }
  _dbghelp_handle = NULL;
}

bool WindowsDecoder::can_decode_C_frame_in_vm() const {
  return  (!has_error() && _can_decode_in_vm);
}


bool WindowsDecoder::decode(address addr, char *buf, int buflen, int* offset, const char* modulepath)  {
  if (_pfnSymGetSymFromAddr64 != NULL) {
    PIMAGEHLP_SYMBOL64 pSymbol;
    char symbolInfo[MAX_PATH + sizeof(IMAGEHLP_SYMBOL64)];
    pSymbol = (PIMAGEHLP_SYMBOL64)symbolInfo;
    pSymbol->MaxNameLength = MAX_PATH;
    pSymbol->SizeOfStruct = sizeof(IMAGEHLP_SYMBOL64);
    DWORD64 displacement;
    if (_pfnSymGetSymFromAddr64(::GetCurrentProcess(), (DWORD64)addr, &displacement, pSymbol)) {
      if (buf != NULL) {
        if (demangle(pSymbol->Name, buf, buflen)) {
          jio_snprintf(buf, buflen, "%s", pSymbol->Name);
        }
      }
      if(offset != NULL) *offset = (int)displacement;
      return true;
    }
  }
  if (buf != NULL && buflen > 0) buf[0] = '\0';
  if (offset != NULL) *offset = -1;
  return false;
}

bool WindowsDecoder::demangle(const char* symbol, char *buf, int buflen) {
  return _pfnUndecorateSymbolName != NULL &&
         _pfnUndecorateSymbolName(symbol, buf, buflen, UNDNAME_COMPLETE);
}

#ifdef AMD64
BOOL WindowsDbgHelp::StackWalk64(DWORD MachineType,
                                 HANDLE hProcess,
                                 HANDLE hThread,
                                 LPSTACKFRAME64 StackFrame,
                                 PVOID ContextRecord,
                                 PREAD_PROCESS_MEMORY_ROUTINE64 ReadMemoryRoutine,
                                 PFUNCTION_TABLE_ACCESS_ROUTINE64 FunctionTableAccessRoutine,
                                 PGET_MODULE_BASE_ROUTINE64 GetModuleBaseRoutine,
                                 PTRANSLATE_ADDRESS_ROUTINE64 TranslateAddress) {
  DecoderLocker locker;
  WindowsDecoder* wd = (WindowsDecoder*)locker.decoder();

  if (!wd->has_error() && wd->_pfnStackWalk64) {
    return wd->_pfnStackWalk64(MachineType,
                               hProcess,
                               hThread,
                               StackFrame,
                               ContextRecord,
                               ReadMemoryRoutine,
                               FunctionTableAccessRoutine,
                               GetModuleBaseRoutine,
                               TranslateAddress);
  } else {
    return false;
  }
}

PVOID WindowsDbgHelp::SymFunctionTableAccess64(HANDLE hProcess, DWORD64 AddrBase) {
  DecoderLocker locker;
  WindowsDecoder* wd = (WindowsDecoder*)locker.decoder();

  if (!wd->has_error() && wd->_pfnSymFunctionTableAccess64) {
    return wd->_pfnSymFunctionTableAccess64(hProcess, AddrBase);
  } else {
    return NULL;
  }
}

pfn_SymFunctionTableAccess64 WindowsDbgHelp::pfnSymFunctionTableAccess64() {
  DecoderLocker locker;
  WindowsDecoder* wd = (WindowsDecoder*)locker.decoder();

  if (!wd->has_error()) {
    return wd->_pfnSymFunctionTableAccess64;
  } else {
    return NULL;
  }
}

pfn_SymGetModuleBase64 WindowsDbgHelp::pfnSymGetModuleBase64() {
  DecoderLocker locker;
  WindowsDecoder* wd = (WindowsDecoder*)locker.decoder();

  if (!wd->has_error()) {
    return wd->_pfnSymGetModuleBase64;
  } else {
    return NULL;
  }
}

#endif // AMD64
