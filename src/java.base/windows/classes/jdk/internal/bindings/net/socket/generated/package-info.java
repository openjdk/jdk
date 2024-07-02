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
 * Defines native structures for socket APIs.
 * Generated with the following jextract command:
 * {@snippet lang = "Shell Script":
 *
 * HEADER_NAME=socket_address.h
 * echo "#include <winsock2.h>" > $HEADER_NAME
 * echo "#include <in6addr.h>" >> $HEADER_NAME
 * echo "#include <Ws2ipdef.h>" >> $HEADER_NAME
 *
 *
 * $jextract --target-package jdk.internal.bindings.net.socket \
 *           --include-struct sockaddr \
 *           --include-constant AF_INET \
 *           --include-constant AF_INET6 \
 *           --include-struct sockaddr_in \
 *           --include-struct sockaddr_in6 \
 *           --include-struct in_addr \
 *           --include-struct in6_addr \
 *           --include-typedef SCOPE_ID \
 *           $HEADER_NAME
 * }
 *
 */

package jdk.internal.bindings.net.socket.generated;
