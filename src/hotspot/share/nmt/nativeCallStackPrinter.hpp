/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_NMT_NATIVECALLSTACKPRINTER_HPP
#define SHARE_NMT_NATIVECALLSTACKPRINTER_HPP

#include "memory/arena.hpp"
#include "nmt/memflags.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/resourceHash.hpp"

class outputStream;
class NativeCallStack;

// This is a text cache for NativeCallStack frames by PC. When printing tons of
// NativeCallStack instances (e.g. during NMT detail reports), printing through
// this printer speeds up frame description resolution by quite a bit.
class NativeCallStackPrinter {
  // Cache-related data are mutable to be able to use NativeCallStackPrinter as
  // inline member in classes with const printing methods.
  mutable Arena _text_storage;
  mutable ResourceHashtable<address, const char*, 293, AnyObj::C_HEAP, mtNMT> _cache;
  outputStream* const _out;
public:
  NativeCallStackPrinter(outputStream* out);
  void print_stack(const NativeCallStack* stack) const;
};

#endif // SHARE_NMT_NATIVECALLSTACKPRINTER_HPP
