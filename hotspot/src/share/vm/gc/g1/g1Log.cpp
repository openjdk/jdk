/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1Log.hpp"
#include "gc/g1/g1_globals.hpp"
#include "runtime/globals_extension.hpp"

G1Log::LogLevel G1Log::_level = G1Log::LevelNone;


// Updates _level based on PrintGC and PrintGCDetails values (unless
// G1LogLevel is set explicitly)
// - PrintGC maps to "fine".
// - PrintGCDetails maps to "finer".
void G1Log::update_level() {
  if (FLAG_IS_DEFAULT(G1LogLevel)) {
    _level = LevelNone;
    if (PrintGCDetails) {
      _level = LevelFiner;
    } else if (PrintGC) {
      _level = LevelFine;
    }
  }
}


// If G1LogLevel has not been set up we will use the values of PrintGC
// and PrintGCDetails for the logging level.
void G1Log::init() {
  if (!FLAG_IS_DEFAULT(G1LogLevel)) {
    // PrintGC flags change won't have any affect, because G1LogLevel
    // is set explicitly
    if (G1LogLevel[0] == '\0' || strncmp("none", G1LogLevel, 4) == 0 && G1LogLevel[4] == '\0') {
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
    update_level();
  }
}

