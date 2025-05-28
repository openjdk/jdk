/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * Defines native structures for errno APIs.
 * Generated with the following jextract command:
 * {@snippet lang = "Shell Script":
 *
 * HEADER_NAME=errno.h
 * echo "#include <errno.h>" > $HEADER_NAME
 * echo "#include <string.h>" >> $HEADER_NAME
 *
 * jextract --target-package jdk.internal.ffi.generated.errno \
 *       --include-constant EINTR \
 *       --include-function strerror_r \
 *  $HEADER_NAME
 * }
 *
 * After generation of native bindings, the layouts for the C builtin layouts and other
 * variables/methods not specific to a component area are moved to the {@code BindingUtils} class
 * for future reusability.
 * If the {@code BindingUtils} class already exists, any usage of the components mentioned above is
 * replaced with matching replacement from the {@code BindingUtils} where possible.
 *
 */

package jdk.internal.ffi.generated.errno;