/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.foreign.abi.fallback;

/**
 * See doc: <a href="https://github.com/libffi/libffi/blob/7611bb4cfe90884b55ad225e0166136a1d2cf22b/doc/libffi.texi#L159"></a>
 * <p>
 * typedef enum {
 *   FFI_OK = 0,
 *   FFI_BAD_TYPEDEF,
 *   FFI_BAD_ABI,
 *   FFI_BAD_ARGTYPE
 * } ffi_status;
 */
enum FFIStatus {
    FFI_OK,
    FFI_BAD_TYPEDEF,
    FFI_BAD_ABI,
    FFI_BAD_ARGTYPE;

    static FFIStatus of(int code) {
        return switch (code) {
            case 0 -> FFI_OK;
            case 1 -> FFI_BAD_TYPEDEF;
            case 2 -> FFI_BAD_ABI;
            case 3 -> FFI_BAD_ARGTYPE;
            default -> throw new IllegalArgumentException("Unknown status code: " + code);
        };
    }
}
