/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc_implementation/g1/g1_globals.hpp"
#include "gc_implementation/g1/g1Log.hpp"
#include "runtime/globals.hpp"

G1Log::LogLevel G1Log::_level = G1Log::LevelNone;

// If G1LogLevel has not been set up we will use the values of PrintGC
// and PrintGCDetails for the logging level.
// - PrintGC maps to "fine".
// - PrintGCDetails maps to "finer".
void G1Log::init() {
  if (G1LogLevel != NULL && G1LogLevel[0] != '\0') {
    if (strncmp("none", G1LogLevel, 4) == 0 && G1LogLevel[4] == '\0') {
      _level = LevelNone;
    } else if (strncmp("fine", G1LogLevel, 4) == 0 && G1LogLevel[4] == '\0') {
      _level = LevelFine;
    } else if (strncmp("finer", G1LogLevel, 5) == 0 && G1LogLevel[5] == '\0') {
      _level = LevelFiner;
    } else if (strncmp("finest", G1LogLevel, 6) == 0 && G1LogLevel[6] == '\0') {
      _level = LevelFinest;
    } else {
      warning("Unknown logging level '%s', should be one of 'fine', 'finer' or 'finest'.", G1LogLevel);
    }
  } else {
    if (PrintGCDetails) {
      _level = LevelFiner;
    } else if (PrintGC) {
      _level = LevelFine;
    }
  }
}
