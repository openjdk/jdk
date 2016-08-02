/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_SEMAPHORE_HPP
#define SHARE_VM_RUNTIME_SEMAPHORE_HPP

#include "memory/allocation.hpp"

#if defined(LINUX) || defined(SOLARIS) || defined(AIX)
# include "semaphore_posix.hpp"
#elif defined(BSD)
# include "semaphore_bsd.hpp"
#elif defined(_WINDOWS)
# include "semaphore_windows.hpp"
#else
# error "No semaphore implementation provided for this OS"
#endif

// Implements the limited, platform independent Semaphore API.
class Semaphore : public CHeapObj<mtInternal> {
  SemaphoreImpl _impl;

  // Prevent copying and assignment of Semaphore instances.
  Semaphore(const Semaphore&);
  Semaphore& operator=(const Semaphore&);

 public:
  Semaphore(uint value = 0) : _impl(value) {}
  ~Semaphore() {}

  void signal(uint count = 1) { _impl.signal(count); }

  void wait()                 { _impl.wait(); }
};


#endif // SHARE_VM_RUNTIME_SEMAPHORE_HPP
