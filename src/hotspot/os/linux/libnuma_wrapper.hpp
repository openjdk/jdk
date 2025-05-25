/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_LINUX_LIBNUMA_HPP
#define OS_LINUX_LIBNUMA_HPP

#include "utilities/globalDefinitions.hpp"

class outputStream;

class LibNuma {
public:

  struct bitmask {
    unsigned long size; /* number of bits in the map */
    unsigned long *maskp;
  };

private:

  // V1.1
  typedef int (*numa_available_func_t)(void);
  typedef int (*numa_node_to_cpus_func_t)(int node, unsigned long *buffer, int bufferlen);
  typedef int (*numa_max_node_func_t)(void);
  typedef int (*numa_num_configured_nodes_func_t)(void);
  typedef int (*numa_tonode_memory_func_t)(void *start, size_t size, int node);
  typedef void (*numa_interleave_memory_func_t)(void *start, size_t size, unsigned long *nodemask);
  typedef struct bitmask* (*numa_get_membind_func_t)(void);
  typedef struct bitmask* (*numa_get_interleave_mask_func_t)(void);
  typedef struct bitmask* (*numa_get_run_node_mask_func_t)(void);
  typedef long (*numa_move_pages_func_t)(int pid, unsigned long count, void **pages, const int *nodes, int *status, int flags);
  typedef void (*numa_set_preferred_func_t)(int node);
  typedef void (*numa_set_bind_policy_func_t)(int policy);
  typedef int (*numa_bitmask_isbitset_func_t)(struct bitmask *bmp, unsigned int n);
  typedef int (*numa_bitmask_equal_func_t)(struct bitmask *bmp1, struct bitmask *bmp2);
  typedef int (*numa_distance_func_t)(int node1, int node2);

  // V1.2
  typedef int (*numa_node_to_cpus_v2_func_t)(int node, void *mask);
  typedef void (*numa_interleave_memory_v2_func_t)(void *start, size_t size, struct bitmask* mask);

  enum class State { unknown, on, off };
  State _state;

#define ALL_V1_FUNCTIONS_DO(XX) \
	  XX(numa_available) \
	  XX(numa_node_to_cpus) \
	  XX(numa_max_node) \
	  XX(numa_num_configured_nodes) \
	  XX(numa_tonode_memory) \
	  XX(numa_interleave_memory) \
	  XX(numa_get_membind) \
	  XX(numa_get_interleave_mask) \
	  XX(numa_get_run_node_mask) \
	  XX(numa_move_pages) \
	  XX(numa_set_preferred) \
	  XX(numa_set_bind_policy) \
	  XX(numa_bitmask_isbitset) \
	  XX(numa_bitmask_equal) \
	  XX(numa_distance)

#define ALL_V2_FUNCTIONS_DO(XX) \
  XX(numa_node_to_cpus_v2) \
  XX(numa_interleave_memory_v2)

#define ALL_FUNCTIONS_DO(XX) \
  ALL_V1_FUNCTIONS_DO(XX) \
  ALL_V2_FUNCTIONS_DO(XX)

  // Define function pointers
#define XX(name)  name ## _func_t _ ## name ## _func;
  ALL_FUNCTIONS_DO(XX)
#undef XX

  // Define external data in libnuma
  struct bitmask* _numa_all_nodes_ptr;
  struct bitmask* _numa_nodes_ptr;
  unsigned long* _numa_all_nodes;

  // Initialize from the real libnuma
  void initialize_real();

  // Initialize in fake mode
  void initialize_fake();

  void print_on(outputStream* st) const;

  static LibNuma _the_interface;

public:

  LibNuma();

  // have_xxx
#define XX(name) static bool has_ ## name() const { return _the_interface._ ## name ## _func != nullptr; }
  ALL_FUNCTIONS_DO(XX)
#undef XX

  // V1.1
  static int numa_node_to_cpus(int node, unsigned long *buffer, int bufferlen);
  static int numa_max_node(void);
  static int numa_num_configured_nodes(void);
  static int numa_tonode_memory(void *start, size_t size, int node);
  static void numa_interleave_memory(void *start, size_t size, unsigned long *nodemask);
  static struct bitmask* numa_get_membind(void);
  static struct bitmask* numa_get_interleave_mask(void);
  static struct bitmask* numa_get_run_node_mask(void);
  static long numa_move_pages(int pid, unsigned long count, void **pages, const int *nodes, int *status, int flags);
  static void numa_set_preferred(int node);
  static void numa_set_bind_policy(int policy);
  static int numa_bitmask_isbitset(struct bitmask *bmp, unsigned int n);
  static int numa_bitmask_equal(struct bitmask *bmp1, struct bitmask *bmp2);
  static int numa_distance(int node1, int node2);

  // V1.2
  static int numa_node_to_cpus_v2(int node, void *mask);
  static void numa_interleave_memory_v2(void *start, size_t size, struct bitmask* mask);

  // Get pointers to external data
  static struct bitmask* numa_all_nodes_ptr() const { return _the_interface._numa_all_nodes_ptr; }
  static struct bitmask* numa_nodes_ptr() const     { return _the_interface._numa_nodes_ptr; }
  static unsigned long* numa_all_nodes() const            { return _the_interface._numa_all_nodes; }

  // Initialize
  static void initialize(bool fakemode);
  static bool enabled() { return _the_interface._state == State::on; }

  static void print_state(outputStream* st) const;

  static int numa_node_to_cpus(int node, unsigned long *buffer, int bufferlen);



};

#endif // OS_LINUX_LIBNUMA_HPP
