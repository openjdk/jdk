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
 *
 */

#ifndef SHARE_GC_PARALLEL_PSMARKSWEEPPROXY_HPP
#define SHARE_GC_PARALLEL_PSMARKSWEEPPROXY_HPP

#include "utilities/macros.hpp"
#if INCLUDE_SERIALGC
#include "gc/parallel/psMarkSweep.hpp"
#endif

#if INCLUDE_SERIALGC
namespace PSMarkSweepProxy {
  inline void initialize()                              { PSMarkSweep::initialize(); }
  inline void invoke(bool maximum_heap_compaction)      { PSMarkSweep::invoke(maximum_heap_compaction); }
  inline bool invoke_no_policy(bool clear_all_softrefs) { return PSMarkSweep::invoke_no_policy(clear_all_softrefs); }
  inline jlong millis_since_last_gc()                   { return PSMarkSweep::millis_since_last_gc(); }
  inline elapsedTimer* accumulated_time()               { return PSMarkSweep::accumulated_time(); }
  inline uint total_invocations()                       { return PSMarkSweep::total_invocations(); }
};
#else
namespace PSMarkSweepProxy {
  inline void initialize()                { fatal("Serial GC excluded from build"); }
  inline void invoke(bool)                { fatal("Serial GC excluded from build"); }
  inline bool invoke_no_policy(bool)      { fatal("Serial GC excluded from build"); return false;}
  inline jlong millis_since_last_gc()     { fatal("Serial GC excluded from build"); return 0L; }
  inline elapsedTimer* accumulated_time() { fatal("Serial GC excluded from build"); return NULL; }
  inline uint total_invocations()         { fatal("Serial GC excluded from build"); return 0u; }
};
#endif

#endif // SHARE_GC_PARALLEL_PSMARKSWEEPPROXY_HPP
