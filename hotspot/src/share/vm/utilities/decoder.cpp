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
#include "runtime/mutexLocker.hpp"
#include "utilities/decoder.hpp"

#if defined(_WINDOWS)
  #include "decoder_windows.hpp"
#elif defined(__APPLE__)
  #include "decoder_machO.hpp"
#else
  #include "decoder_elf.hpp"
#endif

NullDecoder*  Decoder::_decoder = NULL;
NullDecoder   Decoder::_do_nothing_decoder;
Mutex*           Decoder::_decoder_lock = new Mutex(Mutex::safepoint,
                                "DecoderLock");

// _decoder_lock should already acquired before enter this method
NullDecoder* Decoder::get_decoder() {
  assert(_decoder_lock != NULL && _decoder_lock->owned_by_self(),
    "Require DecoderLock to enter");

  if (_decoder != NULL) {
    return _decoder;
  }

  // Decoder is a secondary service. Although, it is good to have,
  // but we can live without it.
#if defined(_WINDOWS)
  _decoder = new (std::nothrow) WindowsDecoder();
#elif defined (__APPLE__)
    _decoder = new (std::nothrow)MachODecoder();
#else
    _decoder = new (std::nothrow)ElfDecoder();
#endif

  if (_decoder == NULL || _decoder->has_error()) {
    if (_decoder != NULL) {
      delete _decoder;
    }
    _decoder = &_do_nothing_decoder;
  }
  return _decoder;
}

bool Decoder::decode(address addr, char* buf, int buflen, int* offset, const char* modulepath) {
  assert(_decoder_lock != NULL, "Just check");
  MutexLockerEx locker(_decoder_lock, true);
  NullDecoder* decoder = get_decoder();
  assert(decoder != NULL, "null decoder");

  return decoder->decode(addr, buf, buflen, offset, modulepath);
}

bool Decoder::demangle(const char* symbol, char* buf, int buflen) {
  assert(_decoder_lock != NULL, "Just check");
  MutexLockerEx locker(_decoder_lock, true);
  NullDecoder* decoder = get_decoder();
  assert(decoder != NULL, "null decoder");
  return decoder->demangle(symbol, buf, buflen);
}

bool Decoder::can_decode_C_frame_in_vm() {
  assert(_decoder_lock != NULL, "Just check");
  MutexLockerEx locker(_decoder_lock, true);
  NullDecoder* decoder = get_decoder();
  assert(decoder != NULL, "null decoder");
  return decoder->can_decode_C_frame_in_vm();
}

// shutdown real decoder and replace it with
// _do_nothing_decoder
void Decoder::shutdown() {
  assert(_decoder_lock != NULL, "Just check");
  MutexLockerEx locker(_decoder_lock, true);

  if (_decoder != NULL && _decoder != &_do_nothing_decoder) {
    delete _decoder;
  }

  _decoder = &_do_nothing_decoder;
}

