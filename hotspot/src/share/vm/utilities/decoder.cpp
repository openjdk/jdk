/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/os.hpp"
#include "utilities/decoder.hpp"
#include "utilities/vmError.hpp"

#if defined(_WINDOWS)
  #include "decoder_windows.hpp"
#elif defined(__APPLE__)
  #include "decoder_machO.hpp"
#elif defined(AIX)
  #include "decoder_aix.hpp"
#else
  #include "decoder_elf.hpp"
#endif

AbstractDecoder*  Decoder::_shared_decoder = NULL;
AbstractDecoder*  Decoder::_error_handler_decoder = NULL;
NullDecoder       Decoder::_do_nothing_decoder;
Mutex*            Decoder::_shared_decoder_lock = new Mutex(Mutex::native,
                                "SharedDecoderLock");

AbstractDecoder* Decoder::get_shared_instance() {
  assert(_shared_decoder_lock != NULL && _shared_decoder_lock->owned_by_self(),
    "Require DecoderLock to enter");

  if (_shared_decoder == NULL) {
    _shared_decoder = create_decoder();
  }
  return _shared_decoder;
}

AbstractDecoder* Decoder::get_error_handler_instance() {
  if (_error_handler_decoder == NULL) {
    _error_handler_decoder = create_decoder();
  }
  return _error_handler_decoder;
}


AbstractDecoder* Decoder::create_decoder() {
  AbstractDecoder* decoder;
#if defined(_WINDOWS)
  decoder = new (std::nothrow) WindowsDecoder();
#elif defined (__APPLE__)
  decoder = new (std::nothrow)MachODecoder();
#elif defined(AIX)
  decoder = new (std::nothrow)AIXDecoder();
#else
  decoder = new (std::nothrow)ElfDecoder();
#endif

  if (decoder == NULL || decoder->has_error()) {
    if (decoder != NULL) {
      delete decoder;
    }
    decoder = &_do_nothing_decoder;
  }
  return decoder;
}

bool Decoder::decode(address addr, char* buf, int buflen, int* offset, const char* modulepath) {
  assert(_shared_decoder_lock != NULL, "Just check");
  bool error_handling_thread = os::current_thread_id() == VMError::first_error_tid;
  MutexLockerEx locker(error_handling_thread ? NULL : _shared_decoder_lock, true);
  AbstractDecoder* decoder = error_handling_thread ?
    get_error_handler_instance(): get_shared_instance();
  assert(decoder != NULL, "null decoder");

  return decoder->decode(addr, buf, buflen, offset, modulepath);
}

bool Decoder::decode(address addr, char* buf, int buflen, int* offset, const void* base) {
  assert(_shared_decoder_lock != NULL, "Just check");
  bool error_handling_thread = os::current_thread_id() == VMError::first_error_tid;
  MutexLockerEx locker(error_handling_thread ? NULL : _shared_decoder_lock, true);
  AbstractDecoder* decoder = error_handling_thread ?
    get_error_handler_instance(): get_shared_instance();
  assert(decoder != NULL, "null decoder");

  return decoder->decode(addr, buf, buflen, offset, base);
}


bool Decoder::demangle(const char* symbol, char* buf, int buflen) {
  assert(_shared_decoder_lock != NULL, "Just check");
  bool error_handling_thread = os::current_thread_id() == VMError::first_error_tid;
  MutexLockerEx locker(error_handling_thread ? NULL : _shared_decoder_lock, true);
  AbstractDecoder* decoder = error_handling_thread ?
    get_error_handler_instance(): get_shared_instance();
  assert(decoder != NULL, "null decoder");
  return decoder->demangle(symbol, buf, buflen);
}

bool Decoder::can_decode_C_frame_in_vm() {
  assert(_shared_decoder_lock != NULL, "Just check");
  bool error_handling_thread = os::current_thread_id() == VMError::first_error_tid;
  MutexLockerEx locker(error_handling_thread ? NULL : _shared_decoder_lock, true);
  AbstractDecoder* decoder = error_handling_thread ?
    get_error_handler_instance(): get_shared_instance();
  assert(decoder != NULL, "null decoder");
  return decoder->can_decode_C_frame_in_vm();
}

/*
 * Shutdown shared decoder and replace it with
 * _do_nothing_decoder. Do nothing with error handler
 * instance, since the JVM is going down.
 */
void Decoder::shutdown() {
  assert(_shared_decoder_lock != NULL, "Just check");
  MutexLockerEx locker(_shared_decoder_lock, true);

  if (_shared_decoder != NULL &&
    _shared_decoder != &_do_nothing_decoder) {
    delete _shared_decoder;
  }

  _shared_decoder = &_do_nothing_decoder;
}

