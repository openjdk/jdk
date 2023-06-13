/*
 * Copyright (c) 2002, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2013 SAP SE. All rights reserved.
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

#ifndef OS_CPU_AIX_PPC_OS_AIX_PPC_INLINE_HPP
#define OS_CPU_AIX_PPC_OS_AIX_PPC_INLINE_HPP

#include "os_aix.hpp"

#define HAVE_PLATFORM_PRINT_NATIVE_STACK 1
inline bool os::platform_print_native_stack(outputStream* st, const void* context,
                                            char *buf, int buf_size) {
  return os::Aix::platform_print_native_stack(st, context, buf, buf_size);
}

#define HAVE_FUNCTION_DESCRIPTORS 1
inline void* os::resolve_function_descriptor(void* p) {
  return os::Aix::resolve_function_descriptor(p);
}
#endif // OS_CPU_AIX_PPC_OS_AIX_PPC_INLINE_HPP
