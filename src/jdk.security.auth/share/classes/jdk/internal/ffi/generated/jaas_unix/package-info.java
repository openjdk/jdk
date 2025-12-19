/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Defines native structures for JAAS {@code UnixLoginModule}.
 * Generated with the following jextract command:
 * {@snippet lang = "Shell Script":
 *
 *  HEADER_NAME=jaas_unix.h
 *   echo "#include <unistd.h> " > $HEADER_NAME
 *   echo "#include <spwd.h>" >> $HEADER_NAME
 *
 *
 * jextract --target-package jdk.internal.ffi.generated.jaas_unix \
 *    --include-function getgroups \
 * 	  --include-function getpwuid_r \
 * 	  --include-function getuid \
 * 	  --include-function getgid \
 * 	  --include-typedef gid_t \
 * 	  --include-struct passwd \
 *    $HEADER_NAME
 * }
 * The classes in this package were generated on macOS. The result is mostly
 * the same when generated on Linux. The biggest difference is that Linux has
 * fewer fields in the middle of the `passwd` struct. This difference does not
 * affect usage, as only fields common to both platforms at the beginning are
 * accessed in the {@link com.sun.security.auth.module.UnixSystem} class.
 */
package jdk.internal.ffi.generated.jaas_unix;
