/*
 * Copyright (c) 1994, 1998, Oracle and/or its affiliates. All rights reserved.
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

/*-
 * Win32 dependent machine byte ordering (actually intel ordering)
 */

#ifndef _JAVASOFT_WIN32_BYTEORDER_MD_H_
#define _JAVASOFT_WIN32_BYTEORDER_MD_H_

#ifdef  x86
#define ntohl(x)        ((x << 24) |                            \
                          ((x & 0x0000ff00) << 8) |             \
                          ((x & 0x00ff0000) >> 8) |             \
                          (((unsigned long)(x & 0xff000000)) >> 24))
#define ntohs(x)        (((x & 0xff) << 8) | ((x >> 8) & (0xff)))
#define htonl(x)        ntohl(x)
#define htons(x)        ntohs(x)
#else   /* x86 */
#define ntohl(x)        (x)
#define ntohs(x)        (x)
#define htonl(x)        (x)
#define htons(x)        (x)
#endif  /* x86 */

#endif /* !_JAVASOFT_WIN32_BYTEORDER_MD_H_ */
