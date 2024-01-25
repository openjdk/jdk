/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_POSIX_SAFEFETCH_SIGJMP_HPP
#define OS_POSIX_SAFEFETCH_SIGJMP_HPP

#include "utilities/globalDefinitions.hpp"

// On Posix platforms that don't do anything better - or cannot, like Zero -
// SafeFetch is implemented using setjmp/longjmp. That is reliable and portable,
// but slower than other methods, and needs more thread stack (the sigjmp buffer
// lives on the thread stack).

int SafeFetch32_impl(int* adr, int errValue);
intptr_t SafeFetchN_impl(intptr_t* adr, intptr_t errValue);

// Handle safefetch, sigsetjmp style. Only call from signal handler.
// If a safefetch jump had been established and the sig qualifies, we
// jump back to the established jump point (and hence out of signal handling).
bool handle_safefetch(int sig, address pc, void* context);

#endif // OS_POSIX_SAFEFETCH_SIGJMP_HPP
