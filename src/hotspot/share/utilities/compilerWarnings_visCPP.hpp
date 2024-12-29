/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_COMPILERWARNINGS_VISCPP_HPP
#define SHARE_UTILITIES_COMPILERWARNINGS_VISCPP_HPP

#define PRAGMA_DISABLE_MSVC_WARNING(num) _Pragma(STR(warning(disable : num)))

#define PRAGMA_DIAG_PUSH _Pragma("warning(push)")
#define PRAGMA_DIAG_POP  _Pragma("warning(pop)")

#define PRAGMA_DEPRECATED_IGNORED PRAGMA_DISABLE_MSVC_WARNING(4996)

// These variants of FORBID_C_FUNCTION override the default definitions.  They
// add `__declspec(dllimport)` to the signature.  Failure to do so where
// needed leads to "redefinition; different linkage" errors for the forbidding
// declaration. Including a dllimport specifier here if not present in the
// compiler's header leads to the same errors.  It seems one just must know
// which are imported and which are not, and use the specifier accordingly.

#define FORBID_IMPORTED_C_FUNCTION(Signature, Alternative) \
  FORBID_C_FUNCTION(__declspec(dllimport) Signature, Alternative)

#define FORBID_IMPORTED_NORETURN_C_FUNCTION(Signature, Alternative) \
  FORBID_NORETURN_C_FUNCTION(__declspec(dllimport) Signature, Alternative)

#endif // SHARE_UTILITIES_COMPILERWARNINGS_VISCPP_HPP
