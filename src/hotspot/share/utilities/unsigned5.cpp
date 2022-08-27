/*
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

#include "precompiled.hpp"
#include "memory/allocation.hpp"
#include "utilities/unsigned5.hpp"

// Explicit instantiation for supported types.

template u4 UNSIGNED5::read_uint(u_char* array, int& offset_rw, int limit);
template void UNSIGNED5::write_uint(uint32_t value, u_char* array, int& offset_rw, int limit);
template int UNSIGNED5::check_length(u_char* array, int offset, int limit);

//template uint32_t UNSIGNED5::read_uint(address array, size_t& offset_rw, size_t limit);
//template void UNSIGNED5::write_uint(uint32_t value, address array, size_t& offset_rw, size_t limit);
//template int UNSIGNED5::check_length(address array, size_t offset, size_t limit);
