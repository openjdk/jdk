/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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


#include "libnuma_wrapper.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"
#include "utilities/globalDefinitions.hpp"

LibNuma LibNuma::_the_interface;

// Handle request to load libnuma symbol version 1.1 (API v1). If it fails
// load symbol from base version instead.
static void* libnuma_dlsym(void* handle, const char *name) {
  void* f = ::dlvsym(handle, name, "libnuma_1.1");
  if (f == nullptr) {
    f = ::dlsym(handle, name);
  }
  return f;
}

// Handle request to load libnuma symbol version 1.2 (API v2) only.
// Return null if the symbol is not defined in this particular version.
static void* libnuma_v2_dlsym(void* handle, const char* name) {
  return dlvsym(handle, name, "libnuma_1.2");
}

template <typename FNPTR>
FNPTR libnuma_resolve_function(void* handle, const char* name, bool v1) {
  void* const f =
      v1 ? libnuma_dlsym(handle, name) : libnuma_v2_dlsym(handle, name);
  FNPTR p = CAST_TO_FN_PTR(FNPTR, f);
  return p;
}

static struct LibNuma::bitmask* resolve_external_bitmask_pointer(void* libhandle, const char* name) {
  struct LibNuma::bitmask** p = (struct LibNuma::bitmask**) libnuma_dlsym(libhandle, name);
  if (p != nullptr) {
    return *p;
  }
  return nullptr;
}

LibNuma::LibNuma() : _state(State::unknown),
#define XX(name) _ ## name ## _func(nullptr),
ALL_FUNCTIONS_DO(XX)
#undef XX
  _numa_all_nodes_ptr(nullptr),
  _numa_nodes_ptr(nullptr),
  _numa_all_nodes(nullptr)
{}

// Initialize from the real libnuma
void LibNuma::initialize_real() {

  assert(_state == State::unknown, "Only once");
  _state = State::off;

  const char* const libname = "libnuma.so.1";
  void* const libhandle = dlopen(libname, RTLD_LAZY);
  if (libhandle == nullptr) {
    log_info(os, numa)("%s could not be loaded", libname);
    return;
  }

#define RESOLVE_FUNCTION(name, is_v1) \
  _ ## name ## _func = libnuma_resolve_function<name ## _func_t>(libhandle, #name, is_v1);
#define RESOLVE_V1_FUNCTION(name) RESOLVE_FUNCTION(name, true)
#define RESOLVE_V2_FUNCTION(name) RESOLVE_FUNCTION(name, false)

  // Call numa_available() right away. No need to proceed if that fails.
  RESOLVE_V1_FUNCTION(numa_available);
  if (_numa_available_func == nullptr) {
    log_info(os, numa)("numa_available() not found in %s ?", libname);
    return;
  }
  if (_numa_available_func() == -1) {
    log_info(os, numa)("NUMA not available");
    return;
  }

  // Proceed with the rest of the functions
  ALL_V1_FUNCTIONS_DO(RESOLVE_V1_FUNCTION)
  ALL_V2_FUNCTIONS_DO(RESOLVE_V2_FUNCTION)

  // Resolve data structures
  _numa_all_nodes = (unsigned long*)libnuma_dlsym(libhandle, "numa_all_nodes");
  _numa_all_nodes_ptr = resolve_external_bitmask_pointer(libhandle, "numa_all_nodes_ptr");
  _numa_nodes_ptr = resolve_external_bitmask_pointer(libhandle, "numa_nodes_ptr");

#undef RESOLVE_FUNCTION
#undef RESOLVE_V1_FUNCTION
#undef RESOLVE_V2_FUNCTION

  LogTarget(Info, os, numa) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    ls.print_cr("libnuma wrapper initialized.");
    print_on(&ls);
  }

  _state = State::on;

}

// Initialize in fake mode
void LibNuma::initialize_fake() {
  assert(_state == State::unknown, "Only once");
  _state = State::off;
}

template <typename FNPTR>
static void print_fnptr(outputStream* st, const char* name, FNPTR p) {
  st->print("_%s_func: ", name);
  st->fill_to(40);
  st->print(PTR_FORMAT " ", (intptr_t)p);
  os::print_function_and_library_name(st, (address)p, nullptr, 0, true, false, false);
  st->cr();
}

void LibNuma::print_on(outputStream* st) const {
  char tmp[256];
  stringStream ss(tmp, sizeof(tmp));
#define PRINTFUNC(name) { \
		print_fnptr(st, #name, _ ## name ## _func); \
  }
  ALL_V1_FUNCTIONS_DO(PRINTFUNC)
  ALL_V2_FUNCTIONS_DO(PRINTFUNC)
#undef PRINTFUNC
  st->print_cr("_numa_all_nodes: " PTR_FORMAT, p2i(_numa_all_nodes));
  st->print_cr("_numa_all_nodes_ptr: " PTR_FORMAT, p2i(_numa_all_nodes_ptr));
  st->print_cr("_numa_nodes_ptr: " PTR_FORMAT, p2i(_numa_nodes_ptr));
}

// Initialize
void LibNuma::initialize(bool fakemode) {
  if (fakemode) {
    _the_interface.initialize_fake();
  } else {
    _the_interface.initialize_real();
  }
}

void LibNuma::print_state(outputStream* st) {
  _the_interface.print_on(st);
}

// V1.1
int LibNuma::numa_node_to_cpus(int node, unsigned long *buffer, int bufferlen) {
  return _the_interface._numa_node_to_cpus_func(node, buffer, bufferlen);
}

int LibNuma::numa_max_node(void) {
  return _the_interface._numa_max_node_func();
}

int LibNuma::numa_num_configured_nodes(void) {
  return _the_interface._numa_num_configured_nodes_func();
}

int LibNuma::numa_tonode_memory(void *start, size_t size, int node) {
  return _the_interface._numa_tonode_memory_func(start, size, node);
}

void LibNuma::numa_interleave_memory(void *start, size_t size, unsigned long *nodemask) {
  return _the_interface._numa_interleave_memory_func(start, size, nodemask);
}

struct LibNuma::bitmask* LibNuma::numa_get_membind(void) {
  return _the_interface._numa_get_membind_func();
}

struct LibNuma::bitmask* LibNuma::numa_get_interleave_mask(void) {
  return _the_interface._numa_get_interleave_mask_func();
}

struct LibNuma::bitmask* LibNuma::numa_get_run_node_mask(void) {
  return _the_interface._numa_get_run_node_mask_func();
}

long LibNuma::numa_move_pages(int pid, unsigned long count, void **pages, const int *nodes, int *status, int flags) {
  return _the_interface._numa_move_pages_func(pid, count, pages, nodes, status, flags);
}

void LibNuma::numa_set_preferred(int node) {
  return _the_interface._numa_set_preferred_func(node);
}

void LibNuma::numa_set_bind_policy(int policy) {
  return _the_interface._numa_set_bind_policy_func(policy);
}

int LibNuma::numa_bitmask_isbitset(struct bitmask *bmp, unsigned int n) {
  return _the_interface._numa_bitmask_isbitset_func(bmp, n);
}

int LibNuma::numa_bitmask_equal(struct bitmask *bmp1, struct bitmask *bmp2) {
  return _the_interface._numa_bitmask_equal_func(bmp1, bmp2);
}

int LibNuma::numa_distance(int node1, int node2) {
  return _the_interface._numa_distance_func(node1, node2);
}

// V1.2
int LibNuma::numa_node_to_cpus_v2(int node, void *mask) {
  return _the_interface._numa_node_to_cpus_v2_func(node, mask);
}

void LibNuma::numa_interleave_memory_v2(void *start, size_t size, struct bitmask* mask) {
  return _the_interface._numa_interleave_memory_v2_func(start, size, mask);
}

