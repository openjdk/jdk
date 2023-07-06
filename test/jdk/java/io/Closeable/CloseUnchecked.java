/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8066869
 * @summary Verify expected behavior of default method Closeable::closeUnchecked
 */

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;

public class CloseUnchecked {
    private static final String MESSAGE = "Gratuitous exception";

    private static class CloseableImpl implements Closeable {
        private boolean closeThrows;

        private CloseableImpl(boolean closeThrows) {
            this.closeThrows = closeThrows;
        }

        public void close() throws IOException {
            if (closeThrows)
                throw new IOException(MESSAGE);
        }
    }

    public static void main(String[] args) {
        Closeable c = new CloseableImpl(false);
        try {
            c.closeUnchecked();
        } catch (UncheckedIOException unexpected) {
            throw new RuntimeException("Unexpected exception", unexpected);
        }

        c = new CloseableImpl(true);
        try {
            c.closeUnchecked();
            throw new RuntimeException("UncheckedIOException not thrown");
        } catch (UncheckedIOException expected) {
            System.out.println("Caught expected UncheckedIOException");
            IOException cause = expected.getCause();
            String message = cause.getMessage();
            if (!message.equals(MESSAGE))
                throw new RuntimeException("Unexpected message " + message);
        }
    }
}
