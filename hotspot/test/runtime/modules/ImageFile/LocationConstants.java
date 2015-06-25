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
 */

public interface LocationConstants {
    // keep this in sync with enum in ImageLocation C++ class in the
    // hotspot's C++ header file imageFile.hpp
    public static final int LOCATION_ATTRIBUTE_END = 0;          // End of attribute stream marker
    public static final int LOCATION_ATTRIBUTE_MODULE = 1;       // String table offset of module name
    public static final int LOCATION_ATTRIBUTE_PARENT = 2;       // String table offset of resource path parent
    public static final int LOCATION_ATTRIBUTE_BASE = 3;         // String table offset of resource path base
    public static final int LOCATION_ATTRIBUTE_EXTENSION = 4;    // String table offset of resource path extension
    public static final int LOCATION_ATTRIBUTE_OFFSET = 5;       // Container byte offset of resource
    public static final int LOCATION_ATTRIBUTE_COMPRESSED = 6;   // In image byte size of the compressed resource
    public static final int LOCATION_ATTRIBUTE_UNCOMPRESSED = 7; // In memory byte size of the uncompressed resource
    public static final int LOCATION_ATTRIBUTE_COUNT = 8;        // Number of attribute kinds
}
