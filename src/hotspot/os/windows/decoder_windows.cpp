/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/os.hpp"
#include "decoder_windows.hpp"
#include "windbghelp.hpp"

WindowsDecoder::WindowsDecoder() {
  _can_decode_in_vm = true;
  _decoder_status = no_error;
  initialize();
}

void WindowsDecoder::initialize() {
  if (!has_error()) {
    HANDLE hProcess = ::GetCurrentProcess();
    WindowsDbgHelp::symSetOptions(SYMOPT_UNDNAME | SYMOPT_DEFERRED_LOADS | SYMOPT_EXACT_SYMBOLS);
    if (!WindowsDbgHelp::symInitialize(hProcess, NULL, TRUE)) {
      _decoder_status = helper_init_error;
      return;
    }

    // set pdb search paths
    char paths[MAX_PATH];
    int  len = sizeof(paths);
    if (!WindowsDbgHelp::symGetSearchPath(hProcess, paths, len)) {
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

    WindowsDbgHelp::symSetSearchPath(hProcess, paths);

    // find out if jvm.dll contains private symbols, by decoding
    // current function and comparing the result
    address addr = (address)Decoder::demangle;
    char buf[MAX_PATH];
    if (decode(addr, buf, sizeof(buf), NULL, NULL, true /* demangle */)) {
      _can_decode_in_vm = !strcmp(buf, "Decoder::demangle");
    }
  }
}

void WindowsDecoder::uninitialize() {}

bool WindowsDecoder::can_decode_C_frame_in_vm() const {
  return  (!has_error() && _can_decode_in_vm);
}


bool WindowsDecoder::decode(address addr, char *buf, int buflen, int* offset, const char* modulepath, bool demangle_name)  {
  if (!has_error()) {
    PIMAGEHLP_SYMBOL64 pSymbol;
    char symbolInfo[MAX_PATH + sizeof(IMAGEHLP_SYMBOL64)];
    pSymbol = (PIMAGEHLP_SYMBOL64)symbolInfo;
    pSymbol->MaxNameLength = MAX_PATH;
    pSymbol->SizeOfStruct = sizeof(IMAGEHLP_SYMBOL64);
    DWORD64 displacement;
    if (WindowsDbgHelp::symGetSymFromAddr64(::GetCurrentProcess(), (DWORD64)addr, &displacement, pSymbol)) {
      if (buf != NULL) {
        if (!(demangle_name && demangle(pSymbol->Name, buf, buflen))) {
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
  if (!has_error()) {
    return WindowsDbgHelp::unDecorateSymbolName(symbol, buf, buflen, UNDNAME_COMPLETE) > 0;
  }
  return false;
}

