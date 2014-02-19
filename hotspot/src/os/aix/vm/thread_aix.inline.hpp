/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2013 SAP AG. All rights reserved.
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

#ifndef OS_AIX_VM_THREAD_AIX_INLINE_HPP
#define OS_AIX_VM_THREAD_AIX_INLINE_HPP

#include "runtime/atomic.hpp"
#include "runtime/prefetch.hpp"
#include "runtime/thread.hpp"
#include "runtime/threadLocalStorage.hpp"

#include "atomic_aix_ppc.inline.hpp"
#include "orderAccess_aix_ppc.inline.hpp"
#include "prefetch_aix_ppc.inline.hpp"

// Contains inlined functions for class Thread and ThreadLocalStorage

inline void ThreadLocalStorage::pd_invalidate_all() {} // nothing to do

#endif // OS_AIX_VM_THREAD_AIX_INLINE_HPP
