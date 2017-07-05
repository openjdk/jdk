/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 *  NativeBuffer.h
 *  Copyright 2007 Apple Inc. All rights reserved.
 *
 */

#define BUFFER_AT(buffer, offset)                (((UInt8 *)buffer) + offset)
#define GET_VALUE(type, buffer, offset)            (*((type *)BUFFER_AT(buffer, offset)))
#define PUT_VALUE(type, buffer, offset, value)    (*((type *)BUFFER_AT(buffer, offset)) = value)

#define GET_INT_AT(buffer, offset)                GET_VALUE(jint, buffer, offset)
#define GET_LONG_AT(buffer, offset)                GET_VALUE(jlong, buffer, offset)

#define PUT_INT_AT(buffer, offset, value)        PUT_VALUE(jint, buffer, offset, value)
#define PUT_LONG_AT(buffer, offset, value)        PUT_VALUE(jlong, buffer, offset, value)
