/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_TYPES_HPP
#define SHARE_MEMORY_TYPES_HPP

#include "memory/allStatic.hpp"
#include "utilities/debug.hpp"

#include <cstdint>

#define MEMORY_TYPES_DO(f)                                                         \
  /* Memory type by sub systems. It occupies lower byte. */                        \
  f(JavaHeap,       "Java Heap")   /* Java heap                                 */ \
  f(Class,          "Class")       /* Java classes                              */ \
  f(Thread,         "Thread")      /* thread objects                            */ \
  f(ThreadStack,    "Thread Stack")                                                \
  f(Code,           "Code")        /* generated code                            */ \
  f(GC,             "GC")                                                          \
  f(GCCardSet,      "GCCardSet")   /* G1 card set remembered set                */ \
  f(Compiler,       "Compiler")                                                    \
  f(JVMCI,          "JVMCI")                                                       \
  f(Internal,       "Internal")    /* memory used by VM, but does not belong to */ \
                                   /* any of above categories, and not used by  */ \
                                   /* NMT                                       */ \
  f(Other,          "Other")       /* memory not used by VM                     */ \
  f(Symbol,         "Symbol")                                                      \
  f(NMT,            "Native Memory Tracking")  /* memory used by NMT            */ \
  f(ClassShared,    "Shared class space")      /* class data sharing            */ \
  f(Chunk,          "Arena Chunk") /* chunk that holds content of arenas        */ \
  f(Test,           "Test")        /* Test type for verifying NMT               */ \
  f(Tracing,        "Tracing")                                                     \
  f(Logging,        "Logging")                                                     \
  f(Statistics,     "Statistics")                                                  \
  f(Arguments,      "Arguments")                                                   \
  f(Module,         "Module")                                                      \
  f(Safepoint,      "Safepoint")                                                   \
  f(Synchronizer,   "Synchronization")                                             \
  f(Serviceability, "Serviceability")                                              \
  f(Metaspace,      "Metaspace")                                                   \
  f(StringDedup,    "String Deduplication")                                        \
  f(ObjectMonitor,  "Object Monitors")                                             \
  f(None,           "Unknown")

/*
 * Memory types.
 */
enum class MemoryType : uint8_t {
#define MEMORY_TYPE_DECLARE_ENUM(type, human_readable) type,
  MEMORY_TYPES_DO(MEMORY_TYPE_DECLARE_ENUM)
#undef MEMORY_TYPE_DECLARE_ENUM
};

// Extra insurance that MemoryType truly has the same size as uint8_t.
STATIC_ASSERT(sizeof(MemoryType) == sizeof(uint8_t));

// Generate short aliases for the enum values. E.g. mtGC instead of MemoryType::GC.
#define MEMORY_TYPE_SHORTNAME(type, human_readable) \
  constexpr MemoryType mt##type = MemoryType::type;
MEMORY_TYPES_DO(MEMORY_TYPE_SHORTNAME)
#undef MEMORY_TYPE_SHORTNAME

class MemoryTypes final : public AllStatic {
 public:
  static constexpr int count() {
#define MEMORY_TYPE_COUNT(type, human_readable) + 1
    return 0 MEMORY_TYPES_DO(MEMORY_TYPE_COUNT);
#undef MEMORY_TYPE_COUNT
  }

  static const char* name(MemoryType mt);

  static constexpr bool is_index_valid(int index) {
    return index >= 0 && index < count();
  }

  static constexpr bool is_valid(MemoryType mt) {
    return is_index_valid(static_cast<int>(mt));
  }

  static inline MemoryType from_index(int index) {
    assert(is_index_valid(index), "invalid memory type index (%d)", index);
    return static_cast<MemoryType>(index);
  }

  static inline int to_index(MemoryType mt) {
    assert(is_valid(mt), "invalid memory type (%d)", static_cast<int>(mt));
    return static_cast<int>(mt);
  }

  static MemoryType from_string(const char* s);
};

// Legacy constant.
constexpr int mt_number_of_types = MemoryTypes::count();

#endif // SHARE_MEMORY_TYPES_HPP
