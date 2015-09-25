/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef LIBJIMAGE_INTTYPES_HPP
#define LIBJIMAGE_INTTYPES_HPP

typedef unsigned char      u1;
typedef          char      s1;
typedef unsigned short     u2;
typedef          short     s2;
typedef unsigned int       u4;
typedef          int       s4;
#ifdef LP64
typedef unsigned long      u8;
typedef          long      s8;
#else
typedef unsigned long long u8;
typedef          long long s8;
#endif

#endif // LIBJIMAGE_INTTYPES_HPP

