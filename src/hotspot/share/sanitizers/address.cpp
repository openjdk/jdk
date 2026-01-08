/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/globals_extension.hpp"
#include "sanitizers/address.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/vmError.hpp"

#include <dlfcn.h>
#include <stdio.h>

typedef void (*callback_setter_t) (void (*callback)(const char *));
static callback_setter_t g_callback_setter = nullptr;
static const char* g_report = nullptr;

extern "C" void asan_error_callback(const char* report_text) {
  // Please keep things very short and simple here and use as little
  // as possible of any hotspot infrastructure. However shaky the JVM,
  // we should always at least get the ASAN report on stderr.

  // Note: this is threadsafe since ASAN synchronizes error reports
  g_report = report_text;

  // First, print off the bare error to stderr
  fprintf(stderr, "JVM caught ASAN Error\n");
  fprintf(stderr, "%s\n", report_text);

  // Then, let normal JVM error handling run its due course.
  fatal("ASAN Error");
}

void Asan::initialize() {

  // For documentation of __asan_set_error_report_callback() see asan_interface.h .
  g_callback_setter = (callback_setter_t) dlsym(RTLD_DEFAULT, "__asan_set_error_report_callback");
  if (g_callback_setter == nullptr) {
    log_info(asan)("*** Failed to install JVM callback for ASAN. ASAN errors will not generate hs-err files. ***");
    return;
  }

  g_callback_setter(asan_error_callback);
  log_info(asan)("JVM callback for ASAN errors successfully installed");

  // Controlling core dump behavior:
  //
  // In hotspot, CreateCoredumpOnCrash decides whether to create a core dump (on Posix, whether to
  // end the process with abort(3) or exit(3)).
  //
  // Core generation in the default ASAN reporter is controlled by two options:
  // - "abort_on_error=0" (default) - end with exit(3), "abort_on_error=1" end with abort(3)
  // - "disable_coredump=1" (default) disables cores by imposing a near-zero core soft limit.
  // By default both options are set to prevent cores. That default makes sense since ASAN cores
  // can get very large (due to the shadow map) and very numerous (ASAN is typically ran for
  // large-scale integration tests, not targeted micro-tests).
  //
  // In hotspot ASAN builds, we replace the default ASAN reporter. The soft limit imposed by
  // "disable_coredump=1" is still in effect. But "abort_on_error" is not honored. Since we'd
  // like to exhibit exactly the same behavior as the standard ASAN error reporter, we disable
  // core files if ASAN would inhibit them (we just switch off CreateCoredumpOnCrash).
  //
  // Thus:
  //     abort_on_error      disable_coredump       core file?
  //         0                   0                  No  (enforced by ergo-setting CreateCoredumpOnCrash=0)
  // (*)     0                   1                  No  (enforced by ASAN-imposed soft limit)
  //         1                   0                  Yes, unless -XX:-CreateCoredumpOnCrash set on command line
  //         1                   1                  No  (enforced by ASAN-imposed soft limit)
  // (*) is the default if no ASAN options are specified.

  const char* const asan_options = getenv("ASAN_OPTIONS");
  const bool asan_inhibits_cores = (asan_options == nullptr) ||
                                   (::strstr(asan_options, "abort_on_error=1") == nullptr) ||
                                   (::strstr(asan_options, "disable_coredump=0") == nullptr);
  if (asan_inhibits_cores) {
    if (CreateCoredumpOnCrash) {
      log_info(asan)("CreateCoredumpOnCrash overruled by%s asan options. Core generation disabled.",
                        asan_options != nullptr ? "" : " default setting for");
      log_info(asan)("Use 'ASAN_OPTIONS=abort_on_error=1:disable_coredump=0:unmap_shadow_on_exit=1' "
                     "to enable core generation.");
    }
    FLAG_SET_ERGO(CreateCoredumpOnCrash, false);
  }
}

bool Asan::had_error() {
  return g_report != nullptr;
}

void Asan::report(outputStream* st) {
  if (had_error()) {
    // Use raw print here to avoid truncation.
    st->print_raw(g_report);
    st->cr();
    st->cr();
  }
}

#endif // ADDRESS_SANITIZER
