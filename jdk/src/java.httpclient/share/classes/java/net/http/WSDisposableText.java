/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General  License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General  License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General  License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package java.net.http;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

final class WSDisposableText implements WebSocket.Text, WSDisposable {

    private final WSShared<CharBuffer> text;

    WSDisposableText(WSShared<CharBuffer> text) {
        this.text = text;
    }

    @Override
    public int length() {
        return text.buffer().length();
    }

    @Override
    public char charAt(int index) {
        return text.buffer().charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return text.buffer().subSequence(start, end);
    }

    @Override
    public ByteBuffer asByteBuffer() {
        throw new UnsupportedOperationException("To be removed from the API");
    }

    @Override
    public String toString() {
        return text.buffer().toString();
    }

    @Override
    public void dispose() {
        text.dispose();
    }
}
