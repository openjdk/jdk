/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_JFR_RECORDER_STACKTRACE_JFRNATIVEFUNCTIONREPOSITORY_HPP
#define SHARE_JFR_RECORDER_STACKTRACE_JFRNATIVEFUNCTIONREPOSITORY_HPP

#include "jfr/utilities/jfrTypes.hpp"
#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/resizableHashTable.hpp"

class JfrChunkWriter;
class JfrNativeAddressRange;
class JfrNativeLibrary;

typedef ResizeableHashTable<uintptr_t, JfrNativeAddressRange, AnyObj::C_HEAP, mtTracing> JfrNativeAddressTable;
typedef GrowableArrayCHeap<uintptr_t, mtTracing> JfrPcList;

class JfrNativeFunctionRepository : AllStatic {
 private:
  static JfrNativeAddressTable* _addresses;
  static JfrPcList* _unwritten_pcs;
  static JfrNativeLibrary* _libraries;
  static traceid _next_range_id;
  static traceid _next_library_id;

  static void update_libraries();
  static JfrNativeLibrary* find_library(uintptr_t pc);
  static void resolve_and_write_function(JfrChunkWriter& cw, uintptr_t pc, JfrNativeLibrary* lib);

  static void clear_functions();
  static void clear_libraries();

 public:
  static bool create();
  static void destroy();

  static traceid id_for(uintptr_t pc);

  static size_t write_functions(JfrChunkWriter& cw, bool clear);
  static size_t write_libraries(JfrChunkWriter& cw, bool clear);
};

#endif // SHARE_JFR_RECORDER_STACKTRACE_JFRNATIVEFUNCTIONREPOSITORY_HPP
