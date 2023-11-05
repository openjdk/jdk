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

// Intentionally no #include guard.  May be included multiple times for effect.

// The files vmassert_uninstall.hpp and vmassert_reinstall.hpp provide a
// workaround for the name collision between HotSpot's assert macro and the
// Standard Library's assert macro.  When including a 3rd-party header that
// uses (and so includes) the standard assert macro, wrap that inclusion with
// includes of these two files, e.g.
//
// #include "utilities/vmassert_uninstall.hpp"
// #include <header including standard assert macro>
// #include "utilities/vmassert_reinstall.hpp"
//
// This removes the HotSpot macro definition while pre-processing the
// 3rd-party header, then reinstates the HotSpot macro (if previously defined)
// for following code.

// Remove HotSpot's assert macro, if present.
#ifdef vmassert
#undef assert
#endif // vmassert

