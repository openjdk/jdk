/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_NMT_MEM_TAG_HPP
#define SHARE_NMT_MEM_TAG_HPP

#include "utilities/globalDefinitions.hpp"

#define MEMORY_TAG_DO(f)                                                             \
  /* Memory tag by sub systems. It occupies lower byte. */                           \
  f(mtJavaHeap,       "Java Heap")   /* Java heap                                 */ \
  f(mtClass,          "Class")       /* Java classes                              */ \
  f(mtThread,         "Thread")      /* thread objects                            */ \
  f(mtThreadStack,    "Thread Stack")                                                \
  f(mtCode,           "Code")        /* generated code                            */ \
  f(mtGC,             "GC")                                                          \
  f(mtGCCardSet,      "GCCardSet")   /* G1 card set remembered set                */ \
  f(mtCompiler,       "Compiler")                                                    \
  f(mtJVMCI,          "JVMCI")                                                       \
  f(mtInternal,       "Internal")    /* memory used by VM, but does not belong to */ \
                                     /* any of above categories, and not used by  */ \
                                     /* NMT                                       */ \
  f(mtOther,          "Other")       /* memory not used by VM                     */ \
  f(mtSymbol,         "Symbol")                                                      \
  f(mtNMT,            "Native Memory Tracking")  /* memory used by NMT            */ \
  f(mtClassShared,    "Shared class space")      /* class data sharing            */ \
  f(mtChunk,          "Arena Chunk") /* chunk that holds content of arenas        */ \
  f(mtTest,           "Test")        /* Test type for verifying NMT               */ \
  f(mtTracing,        "Tracing")                                                     \
  f(mtLogging,        "Logging")                                                     \
  f(mtStatistics,     "Statistics")                                                  \
  f(mtArguments,      "Arguments")                                                   \
  f(mtModule,         "Module")                                                      \
  f(mtSafepoint,      "Safepoint")                                                   \
  f(mtSynchronizer,   "Synchronization")                                             \
  f(mtServiceability, "Serviceability")                                              \
  f(mtMetaspace,      "Metaspace")                                                   \
  f(mtStringDedup,    "String Deduplication")                                        \
  f(mtObjectMonitor,  "Object Monitors")                                             \
  f(mtJNI,            "JNI")                                                         \
  f(mtNone,           "Unknown")                                                     \
  //end

#define MEMORY_TAG_DECLARE_ENUM(mem_tag, human_readable) \
mem_tag,

enum class MemTag : uint8_t  {
  MEMORY_TAG_DO(MEMORY_TAG_DECLARE_ENUM)
  mt_number_of_tags    // number of memory tags (mtDontTrack
                       // is not included as validate tag)
};

#define MEMORY_TAG_SHORTNAME(mem_tag, human_readable) \
  constexpr MemTag mem_tag = MemTag::mem_tag;

// Generate short aliases for the enum values. E.g. mtGC instead of MemTag::mtGC.
MEMORY_TAG_DO(MEMORY_TAG_SHORTNAME)

// Make an int version of the sentinel end value.
constexpr int mt_number_of_tags = static_cast<int>(MemTag::mt_number_of_tags);

#endif // SHARE_NMT_MEM_TAG_HPP
