/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "ByteBuffer.h"

#include <stdio.h>

ByteBuffer::ByteBuffer() {
    buffer.reserve(1024);
}

ByteBuffer::~ByteBuffer() {
}

LPBYTE ByteBuffer::getPtr() {
    return &buffer[0];
}

size_t ByteBuffer::getPos() {
    return buffer.size();
}

void ByteBuffer::AppendString(wstring str) {
    size_t len = (str.size() + 1) * sizeof (WCHAR);
    AppendBytes((BYTE*) str.c_str(), len);
}

void ByteBuffer::AppendWORD(WORD word) {
    AppendBytes((BYTE*) & word, sizeof (WORD));
}

void ByteBuffer::Align(size_t bytesNumber) {
    size_t pos = getPos();
    if (pos % bytesNumber) {
        DWORD dwNull = 0;
        size_t len = bytesNumber - pos % bytesNumber;
        AppendBytes((BYTE*) & dwNull, len);
    }
}

void ByteBuffer::AppendBytes(BYTE* ptr, size_t len) {
    buffer.insert(buffer.end(), ptr, ptr + len);
}

void ByteBuffer::ReplaceWORD(size_t offset, WORD word) {
    ReplaceBytes(offset, (BYTE*) & word, sizeof (WORD));
}

void ByteBuffer::ReplaceBytes(size_t offset, BYTE* ptr, size_t len) {
    for (size_t i = 0; i < len; i++) {
        buffer[offset + i] = *(ptr + i);
    }
}
