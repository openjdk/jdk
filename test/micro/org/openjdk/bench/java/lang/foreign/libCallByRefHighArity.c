/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "export.h"

EXPORT void noop_params0() {}
EXPORT void noop_params1(void *param0) {}
EXPORT void noop_params2(void *param0, void *param1) {}
EXPORT void noop_params3(void *param0, void *param1, void *param2) {}
EXPORT void noop_params4(void *param0, void *param1, void *param2, void *param3) {}
EXPORT void noop_params5(void *param0, void *param1, void *param2, void *param3, void *param4) {}
EXPORT void noop_params10(void *param0, void *param1, void *param2, void *param3, void *param4,
                          void *param5, void *param6, void *param7, void *param8, void *param9) {}
