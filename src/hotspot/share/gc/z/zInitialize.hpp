/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZINITIALIZE_HPP
#define SHARE_GC_Z_ZINITIALIZE_HPP

#include "memory/allStatic.hpp"
#include "utilities/compilerWarnings.hpp"

#include <cstddef>

class ZBarrierSet;

class ZInitializer {
public:
  ZInitializer(ZBarrierSet* barrier_set);
};

class ZInitialize : public AllStatic {
  friend class ZTest;

private:
  static constexpr size_t ErrorMessageLength = 256;

  static char _error_message[ErrorMessageLength];
  static bool _had_error;
  static bool _finished;

  static void register_error(bool debug, const char *error_msg);

  static void pd_initialize();

public:
  static void error(const char* msg_format, ...) ATTRIBUTE_PRINTF(1, 2);
  static void error_d(const char* msg_format, ...) ATTRIBUTE_PRINTF(1, 2);

  static bool had_error();
  static const char* error_message();

  static void initialize(ZBarrierSet* barrier_set);
  static void finish();
};

#endif // SHARE_GC_Z_ZINITIALIZE_HPP
