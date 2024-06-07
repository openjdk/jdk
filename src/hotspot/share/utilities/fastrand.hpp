/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_UTILITIES_FASTRAND_HPP
#define SHARE_UTILITIES_FASTRAND_HPP

#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"

// Simple utility class to generate random numbers for use in a single-threaded
// context. Since os::random() needs to update the global seed, this is faster
// when used on within a single thread.
// Seed initialization happens, similar to os::init_random(), via os::javaTimeNanos());

class FastRandom {
  unsigned _seed;
  public:
  FastRandom () : _seed((unsigned) os::javaTimeNanos()) {}
  unsigned next() {
    _seed = os::next_random(_seed);
    return _seed;
  }
};

#endif // SHARE_UTILITIES_FASTRAND_HPP
