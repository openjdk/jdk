/*
 * Copyright (c) 2023, Google and/or its affiliates. All rights reserved.
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

#ifndef SHARE_SANITIZERS_LEAK_HPP
#define SHARE_SANITIZERS_LEAK_HPP

#ifdef LEAK_SANITIZER
#include <sanitizer/lsan_interface.h>
#endif

// LSAN_REGISTER_ROOT_REGION()/LSAN_UNREGISTER_ROOT_REGION()
//
// Register/unregister regions of memory with LSan. LSan scans these regions looking for
// pointers to malloc memory. This is only necessary when pointers to malloc memory are
// located in memory that is not returned by malloc, such as mapped memory. LSan will
// skip inaccessible parts of the region, such as those that are not readable.
#ifdef LEAK_SANITIZER
#define LSAN_REGISTER_ROOT_REGION(addr, size) __lsan_register_root_region((addr), (size))
#define LSAN_UNREGISTER_ROOT_REGION(addr, size) __lsan_unregister_root_region((addr), (size))
#else
#define LSAN_REGISTER_ROOT_REGION(addr, size) \
  do {                                        \
    if (false) {                              \
      ((void) (addr));                        \
      ((void) (size));                        \
    }                                         \
  } while (false)
#define LSAN_UNREGISTER_ROOT_REGION(addr, size) \
  do {                                          \
    if (false) {                                \
      ((void) (addr));                          \
      ((void) (size));                          \
    }                                           \
  } while (false)
#endif

// LSAN_IGNORE_OBJECT()
//
// Causes LSan to ignore any leaks related to the object. Should only be used
// in cases where leaks are intentional or where LSan will be unable to discover
// pointers to object, for example due to pointers being stored unaligned.
#ifdef LEAK_SANITIZER
#define LSAN_IGNORE_OBJECT(object) __lsan_ignore_object(object)
#else
#define LSAN_IGNORE_OBJECT(object) \
  do {                             \
    if (false) {                   \
      ((void) (object));           \
    }                              \
  } while (false)
#endif

// LSAN_DO_LEAK_CHECK()
//
// Perform a leak check, terminating the process if leaks are found. LSan will
// skip performing leak checks at process exit and further calls will be ignored.
#ifdef LEAK_SANITIZER
#define LSAN_DO_LEAK_CHECK() __lsan_do_leak_check()
#else
#define LSAN_DO_LEAK_CHECK() ((void) 0)
#endif

// LSAN_DO_RECOVERABLE_LEAK_CHECK()
//
// Perform a leak check without terminating if leaks are found.
#ifdef LEAK_SANITIZER
#define LSAN_DO_RECOVERABLE_LEAK_CHECK() __lsan_do_recoverable_leak_check()
#else
#define LSAN_DO_RECOVERABLE_LEAK_CHECK() ((int) 0)
#endif

#endif // SHARE_SANITIZERS_ADDRESS_HPP
