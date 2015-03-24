/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2007, 2008, 2009 Red Hat, Inc.
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

#ifndef OS_CPU_BSD_ZERO_VM_ORDERACCESS_BSD_ZERO_INLINE_HPP
#define OS_CPU_BSD_ZERO_VM_ORDERACCESS_BSD_ZERO_INLINE_HPP

#include "runtime/orderAccess.hpp"

#ifdef ARM

/*
 * ARM Kernel helper for memory barrier.
 * Using __asm __volatile ("":::"memory") does not work reliable on ARM
 * and gcc __sync_synchronize(); implementation does not use the kernel
 * helper for all gcc versions so it is unreliable to use as well.
 */
typedef void (__kernel_dmb_t) (void);
#define __kernel_dmb (*(__kernel_dmb_t *) 0xffff0fa0)

#define FULL_MEM_BARRIER __kernel_dmb()
#define LIGHT_MEM_BARRIER __kernel_dmb()

#else // ARM

#define FULL_MEM_BARRIER __sync_synchronize()

#ifdef PPC

#ifdef __NO_LWSYNC__
#define LIGHT_MEM_BARRIER __asm __volatile ("sync":::"memory")
#else
#define LIGHT_MEM_BARRIER __asm __volatile ("lwsync":::"memory")
#endif

#else // PPC

#define LIGHT_MEM_BARRIER __asm __volatile ("":::"memory")

#endif // PPC

#endif // ARM

// Note: What is meant by LIGHT_MEM_BARRIER is a barrier which is sufficient
// to provide TSO semantics, i.e. StoreStore | LoadLoad | LoadStore.

inline void OrderAccess::loadload()   { LIGHT_MEM_BARRIER; }
inline void OrderAccess::storestore() { LIGHT_MEM_BARRIER; }
inline void OrderAccess::loadstore()  { LIGHT_MEM_BARRIER; }
inline void OrderAccess::storeload()  { FULL_MEM_BARRIER;  }

inline void OrderAccess::acquire()    { LIGHT_MEM_BARRIER; }
inline void OrderAccess::release()    { LIGHT_MEM_BARRIER; }
inline void OrderAccess::fence()      { FULL_MEM_BARRIER;  }

#define VM_HAS_GENERALIZED_ORDER_ACCESS 1

#endif // OS_CPU_BSD_ZERO_VM_ORDERACCESS_BSD_ZERO_INLINE_HPP
