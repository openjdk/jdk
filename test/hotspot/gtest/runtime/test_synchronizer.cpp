/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "memory/allocation.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/vm_version.hpp"
#include "unittest.hpp"

class SynchronizerTest : public ::testing::Test {
  public:
    static u_char* get_gvars_addr() { return ObjectSynchronizer::get_gvars_addr(); }
    static u_char* get_gvars_hcSequence_addr() { return ObjectSynchronizer::get_gvars_hcSequence_addr(); }
    static size_t get_gvars_size() { return ObjectSynchronizer::get_gvars_size(); }
    static u_char* get_gvars_stwRandom_addr() { return ObjectSynchronizer::get_gvars_stwRandom_addr(); }
};

TEST_VM(SynchronizerTest, sanity) {
  uint cache_line_size = VM_Version::L1_data_cache_line_size();
  if (cache_line_size != 0) {
    // We were able to determine the L1 data cache line size so
    // do some cache line specific sanity checks

    u_char *addr_begin = SynchronizerTest::get_gvars_addr();
    u_char *addr_stwRandom = SynchronizerTest::get_gvars_stwRandom_addr();
    u_char *addr_hcSequence = SynchronizerTest::get_gvars_hcSequence_addr();
    size_t gvars_size = SynchronizerTest::get_gvars_size();

    uint offset_stwRandom = (uint) (addr_stwRandom - addr_begin);
    uint offset_hcSequence = (uint) (addr_hcSequence - addr_begin);
    uint offset_hcSequence_stwRandom = offset_hcSequence - offset_stwRandom;
    uint offset_hcSequence_struct_end = (uint) gvars_size - offset_hcSequence;

    EXPECT_GE(offset_stwRandom, cache_line_size)
            << "the SharedGlobals.stwRandom field is closer "
            << "to the struct beginning than a cache line which permits "
            << "false sharing.";

    EXPECT_GE(offset_hcSequence_stwRandom, cache_line_size)
            << "the SharedGlobals.stwRandom and "
            << "SharedGlobals.hcSequence fields are closer than a cache "
            << "line which permits false sharing.";

    EXPECT_GE(offset_hcSequence_struct_end, cache_line_size)
            << "the SharedGlobals.hcSequence field is closer "
            << "to the struct end than a cache line which permits false "
            << "sharing.";
  }
}
