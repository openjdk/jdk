/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package sun.security.provider;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;

/// Accumulate messages in a [NamedSignature]. May be pure or pre-hashed.
public interface SignatureMessageAccumulator {

    void add(byte b);
    void add(byte[] data, int off, int len);
    byte[] getMessage();
    void reset();
    boolean isOn();

    public static class DigestAccumulator implements SignatureMessageAccumulator {
        private final MessageDigest digester;
        private boolean isOn = false;

        DigestAccumulator(MessageDigest digester) {
            this.digester = digester;
        }

        @Override
        public void add(byte b) {
            isOn = true;
            digester.update(b);
        }
        @Override
        public void add(byte[] data, int off, int len) {
            if (len > 0) isOn = true;
            digester.update(data, off, len);
        }
        @Override
        public byte[] getMessage() {
            isOn = false;
            return digester.digest();
        }

        @Override
        public void reset() {
            digester.reset();
            isOn = false;
        }

        @Override
        public boolean isOn() {
            return isOn;
        }
    }

    public static class MemoryAccumulator implements SignatureMessageAccumulator {
        ByteArrayOutputStream message = new ByteArrayOutputStream();

        @Override
        public void add(byte b) {
            message.write(b);
        }
        @Override
        public void add(byte[] data, int off, int len) {
            message.write(data, off, len);
        }
        @Override
        public byte[] getMessage() {
            var result = message.toByteArray();
            message.reset();
            return result;
        }

        @Override
        public void reset() {
            message.reset();
        }

        @Override
        public boolean isOn() {
            return message.size() != 0;
        }
    }
}
