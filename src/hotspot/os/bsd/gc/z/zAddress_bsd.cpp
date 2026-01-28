/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zAddress.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"
#ifdef __APPLE__
#include <mach/vm_param.h>
#endif

#if defined (__APPLE__) && defined(MACH_VM_MAX_ADDRESS)
// Use the system define if available
#define Z_PLATFORM_MAX_HEAP_ADDRESS ((size_t)(MACH_VM_MAX_ADDRESS))
#else
// Try using up to 46 bits for the address
#define Z_PLATFORM_MAX_HEAP_ADDRESS (size_t(1) << 45)
#endif

size_t ZPlatformHeapBaseMaxShift() {
  return clamp((size_t)log2i(Z_PLATFORM_MAX_HEAP_ADDRESS), ZAddressHeapBaseMinShift,ZAddressHeapBaseMaxShift);
}
