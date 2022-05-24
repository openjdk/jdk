/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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
#include "runtime/deoptimization.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/globalDefinitions.hpp"

// Implementation of the platform-specific part of StubRoutines - for
// a description of how to extend it, see the stubRoutines.hpp file.

address StubRoutines::riscv::_get_previous_sp_entry = NULL;

address StubRoutines::riscv::_f2i_fixup = NULL;
address StubRoutines::riscv::_f2l_fixup = NULL;
address StubRoutines::riscv::_d2i_fixup = NULL;
address StubRoutines::riscv::_d2l_fixup = NULL;
address StubRoutines::riscv::_float_sign_mask = NULL;
address StubRoutines::riscv::_float_sign_flip = NULL;
address StubRoutines::riscv::_double_sign_mask = NULL;
address StubRoutines::riscv::_double_sign_flip = NULL;
address StubRoutines::riscv::_zero_blocks = NULL;
address StubRoutines::riscv::_compare_long_string_LL = NULL;
address StubRoutines::riscv::_compare_long_string_UU = NULL;
address StubRoutines::riscv::_compare_long_string_LU = NULL;
address StubRoutines::riscv::_compare_long_string_UL = NULL;
address StubRoutines::riscv::_string_indexof_linear_ll = NULL;
address StubRoutines::riscv::_string_indexof_linear_uu = NULL;
address StubRoutines::riscv::_string_indexof_linear_ul = NULL;
address StubRoutines::riscv::_large_byte_array_inflate = NULL;
address StubRoutines::riscv::_method_entry_barrier = NULL;

bool StubRoutines::riscv::_completed = false;
