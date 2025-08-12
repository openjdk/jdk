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
 * @bug 8321053
 * @summary Verify ByteArrayInputStream.buf is used directly by
 *          ByteArrayInputStream.transferTo only when its OutputStream
 *          parameter is trusted
 * @key randomness
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

public class TransferToTrusted {
    private static final Random RND = new Random(System.nanoTime());

    private static class UntrustedOutputStream extends OutputStream {
        UntrustedOutputStream() {
            super();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            Objects.checkFromIndexSize(off, len, b.length);
            byte[] tmp = new byte[len];
            RND.nextBytes(tmp);
            System.arraycopy(tmp, 0, b, off, len);
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[] {(byte)b});
        }
    }

    public static void main(String[] args) throws IOException {
        byte[] buf = new byte[RND.nextInt(1025)];
        System.out.println("buf.length: " + buf.length);
        RND.nextBytes(buf);
        byte[] dup = Arrays.copyOf(buf, buf.length);

        ByteArrayInputStream bais = new ByteArrayInputStream(dup);
        bais.mark(dup.length);

        OutputStream[] outputStreams = new OutputStream[] {
            new ByteArrayOutputStream(),
            new UntrustedOutputStream()
        };

        for (OutputStream out : outputStreams) {
            System.err.println("out: " + out.getClass().getName());

            bais.transferTo(out);
            bais.reset();
            try {
                if (!Arrays.equals(buf, bais.readAllBytes()))
                    throw new RuntimeException("Internal buffer was modified");
            } finally {
                out.close();
            }
            bais.reset();
        }
    }
}
