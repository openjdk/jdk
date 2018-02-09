/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

/*
 * This header file is used to hijack the include of "zlib.h" from libpng on
 * Macos. We do that to be able to build on macos 10.13 or later, but still
 * keep binary compatibility with older versions (as specified to configure).
 *
 * The problem is that in 10.13, Macos shipped with a newer version of zlib,
 * which exports the function inflateValidate. There is a call to this
 * function in pngrutil.c, guarded by a preprocessor check of ZLIB_VERNUM being
 * high enough. If we compile this call in and link to the newer version of
 * zlib, we will get link errors if the code is executed on an older Mac with
 * an older version of zlib.
 *
 * The zlib.h header in Macos has been annotated with Macos specific macros that
 * guard these kinds of version specific APIs, but libpng is not using those
 * checks in its conditionals, just ZLIB_VERNUM. To fix this, we check for the
 * MAC_OS_X_VERSION_MIN_REQUIRED macro here and adjust the ZLIB_VERNUM to the
 # known version bundled with that release. This solution is certainly a hack,
 * but it seems the affected versions of zlib.h are compatible enough for this
 * to work.
 */

#include <zlib.h>
#include <AvailabilityMacros.h>

#if MAC_OS_X_VERSION_MIN_REQUIRED < MAC_OS_X_VERSION_10_12
#  undef ZLIB_VERNUM
#  define ZLIB_VERNUM 0x1250
#elif MAC_OS_X_VERSION_MIN_REQUIRED < MAC_OS_X_VERSION_10_13
#  undef ZLIB_VERNUM
#  define ZLIB_VERNUM 0x1280
#endif
