/*
 * Copyright 2012, 2013 SAP AG. All rights reserved.
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

#include "runtime/arguments.hpp"
#include "libperfstat_aix.hpp"

// For dlopen and friends
#include <fcntl.h>

// handle to the libperfstat
static void* g_libhandle = NULL;

// whether initialization worked
static bool g_initialized = false;


typedef int (*fun_perfstat_cpu_total_t) (perfstat_id_t *name, perfstat_cpu_total_t* userbuff,
                                         int sizeof_userbuff, int desired_number);

typedef int (*fun_perfstat_memory_total_t) (perfstat_id_t *name, perfstat_memory_total_t* userbuff,
                                            int sizeof_userbuff, int desired_number);

typedef void (*fun_perfstat_reset_t) ();

static fun_perfstat_cpu_total_t     g_fun_perfstat_cpu_total = NULL;
static fun_perfstat_memory_total_t  g_fun_perfstat_memory_total = NULL;
static fun_perfstat_reset_t         g_fun_perfstat_reset = NULL;

bool libperfstat::init() {

  if (g_initialized) {
    return true;
  }

  g_initialized = false;

  // dynamically load the libperfstat porting library.
  g_libhandle = dlopen("/usr/lib/libperfstat.a(shr_64.o)", RTLD_MEMBER | RTLD_NOW);
  if (!g_libhandle) {
    if (Verbose) {
      fprintf(stderr, "Cannot load libperfstat.a (dlerror: %s)", dlerror());
    }
    return false;
  }

  // resolve function pointers

#define RESOLVE_FUN_NO_ERROR(name) \
  g_fun_##name = (fun_##name##_t) dlsym(g_libhandle, #name);

#define RESOLVE_FUN(name) \
  RESOLVE_FUN_NO_ERROR(name) \
  if (!g_fun_##name) { \
    if (Verbose) { \
      fprintf(stderr, "Cannot resolve " #name "() from libperfstat.a\n" \
                      "   (dlerror: %s)", dlerror()); \
      } \
    return false; \
  }

  RESOLVE_FUN(perfstat_cpu_total);
  RESOLVE_FUN(perfstat_memory_total);
  RESOLVE_FUN(perfstat_reset);

  g_initialized = true;

  return true;
}

void libperfstat::cleanup() {

  g_initialized = false;

  if (g_libhandle) {
    dlclose(g_libhandle);
    g_libhandle = NULL;
  }

  g_fun_perfstat_cpu_total = NULL;
  g_fun_perfstat_memory_total = NULL;
  g_fun_perfstat_reset = NULL;
}

int libperfstat::perfstat_memory_total(perfstat_id_t *name,
                                       perfstat_memory_total_t* userbuff,
                                       int sizeof_userbuff, int desired_number) {
  assert(g_initialized, "libperfstat not initialized");
  assert(g_fun_perfstat_memory_total, "");
  return g_fun_perfstat_memory_total(name, userbuff, sizeof_userbuff, desired_number);
}

int libperfstat::perfstat_cpu_total(perfstat_id_t *name, perfstat_cpu_total_t* userbuff,
                                    int sizeof_userbuff, int desired_number) {
  assert(g_initialized, "libperfstat not initialized");
  assert(g_fun_perfstat_cpu_total, "");
  return g_fun_perfstat_cpu_total(name, userbuff, sizeof_userbuff, desired_number);
}

void libperfstat::perfstat_reset() {
  assert(g_initialized, "libperfstat not initialized");
  assert(g_fun_perfstat_reset, "");
  g_fun_perfstat_reset();
}
