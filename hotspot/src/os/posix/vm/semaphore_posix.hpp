/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_POSIX_VM_SEMAPHORE_POSIX_HPP
#define OS_POSIX_VM_SEMAPHORE_POSIX_HPP

#include "memory/allocation.hpp"

#include <semaphore.h>

class PosixSemaphore : public CHeapObj<mtInternal> {
  sem_t _semaphore;

  // Prevent copying and assignment.
  PosixSemaphore(const PosixSemaphore&);
  PosixSemaphore& operator=(const PosixSemaphore&);

 public:
  PosixSemaphore(uint value = 0);
  ~PosixSemaphore();

  void signal(uint count = 1);

  void wait();

  bool trywait();
  bool timedwait(unsigned int sec, int nsec) {
    return timedwait(create_timespec(sec, nsec));
  }

 private:
  bool timedwait(struct timespec ts);

  // OS specific implementation to create a timespec suitable for semaphores.
  struct timespec create_timespec(unsigned int set, int nsec);
};

typedef PosixSemaphore SemaphoreImpl;

#endif // OS_POSIX_VM_SEMAPHORE_POSIX_HPP
