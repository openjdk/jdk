/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package java.util.logging;

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

/**
 * A trivial UTF-8 stream handler subclass class to capture whether the
 * (package protected) post-write callback method is synchronized.
 */
public class TestStreamHandler extends StreamHandler {

    public int callbackCount = 0;

    public TestStreamHandler(OutputStream out) {
        setOutputStream(out);
        try {
            setEncoding("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    void synchronousPostWriteHook() {
        if (!Thread.holdsLock(this)) {
            throw new AssertionError(
                    String.format("Post write callback [index=%d] was invoked without handler locked.", callbackCount));
        }
        callbackCount++;
    }
}
