/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "gc/shared/gcLogPrecious.hpp"
#include "gc/z/zAddress.hpp"
#include "gc/z/zBarrierSet.hpp"
#include "gc/z/zCPU.hpp"
#include "gc/z/zDriver.hpp"
#include "gc/z/zGCIdPrinter.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zInitialize.hpp"
#include "gc/z/zJNICritical.hpp"
#include "gc/z/zLargePages.hpp"
#include "gc/z/zNMT.hpp"
#include "gc/z/zNUMA.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zThreadLocalAllocBuffer.hpp"
#include "gc/z/zTracer.hpp"
#include "logging/log.hpp"
#include "nmt/memTag.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/formatBuffer.hpp"

char ZInitialize::_error_message[ErrorMessageLength] = {};
bool ZInitialize::_had_error                         = false;
bool ZInitialize::_finished                          = false;

ZInitializer::ZInitializer(ZBarrierSet* barrier_set) {
  ZInitialize::initialize(barrier_set);
}

void ZInitialize::initialize(ZBarrierSet* barrier_set) {
  log_info(gc, init)("Initializing %s", ZName);
  log_info(gc, init)("Version: %s (%s)",
                     VM_Version::vm_release(),
                     VM_Version::jdk_debug_level());

  // Early initialization
  ZNMT::initialize();
  ZNUMA::initialize();
  ZGlobalsPointers::initialize();
  ZCPU::initialize();
  ZStatValue::initialize();
  ZThreadLocalAllocBuffer::initialize();
  ZTracer::initialize();
  ZLargePages::initialize();
  ZBarrierSet::set_barrier_set(barrier_set);
  ZJNICritical::initialize();
  ZDriver::initialize();
  ZGCIdPrinter::initialize();

  pd_initialize();
}

void ZInitialize::register_error(bool debug, const char *error_msg) {
  guarantee(!_finished, "Only register errors during initialization");

  if (!_had_error) {
    strncpy(_error_message, error_msg, ErrorMessageLength - 1);
    _had_error = true;
  }

  if (debug) {
    log_error_pd(gc)("%s", error_msg);
  } else {
    log_error_p(gc)("%s", error_msg);
  }
}

void ZInitialize::error(const char* msg_format, ...) {
  va_list argp;
  va_start(argp, msg_format);
  const FormatBuffer<ErrorMessageLength> error_msg(FormatBufferDummy(), msg_format, argp);
  va_end(argp);
  register_error(false /* debug */, error_msg);
}

void ZInitialize::error_d(const char* msg_format, ...) {
  va_list argp;
  va_start(argp, msg_format);
  const FormatBuffer<ErrorMessageLength> error_msg(FormatBufferDummy(), msg_format, argp);
  va_end(argp);
  register_error(true /* debug */, error_msg);
}

bool ZInitialize::had_error() {
  return _had_error;
}

const char* ZInitialize::error_message() {
  assert(had_error(), "Should have registered an error");
  if (had_error()) {
    return _error_message;
  }
  return "Unknown error, check error GC logs";
}

void ZInitialize::finish() {
  guarantee(!_finished, "Only finish initialization once");
  _finished = true;
}
