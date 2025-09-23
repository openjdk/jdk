/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifdef ADDRESS_SANITIZER

#include "logging/log.hpp"
#include "sanitizers/address.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/vmError.hpp"
#include <dlfcn.h>
#include <stdio.h>

static const char* g_asan_report = nullptr;
typedef void (*callback_setter_t) (void (*callback)(const char *));
static callback_setter_t callback_setter = nullptr;

extern "C" void asan_error_callback(const char* report_text) {
  // Keep things very short and simple here;
  // Do use as little as possible of any hotspot infrastructure.
  // We will print out the report text on stderr; then, we will
  // end the JVM with a fatal error, resulting in hs-err file
  // and core dump. VMError will also print the error report
  // to the hs-err file.
  g_asan_report = report_text;
  fprintf(stderr, "JVM caught ASAN Error\n");
  fprintf(stderr, "%s\n", report_text);
  fatal("ASAN error caught");
}

const char* Asan::report() {
  return g_asan_report;
}

void Asan::initialize() {
  callback_setter = (callback_setter_t) dlsym(RTLD_DEFAULT, "__asan_set_error_report_callback");
  if (callback_setter) {
    callback_setter(asan_error_callback);
    log_info(asan)("JVM callback for ASAN errors successfully installed");
  } else {
    log_info(asan)("*** Failed to install JVM callback for ASAN. ASAN errors will not generate hs-err files. ***");
  }
  //__asan_set_error_report_callback(asan_error_callback);
}

#endif // ADDRESS_SANITIZER
