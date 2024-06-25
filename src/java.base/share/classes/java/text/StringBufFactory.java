/*
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

package java.text;

/**
 * StringBufFactory create {code Format.StringBuf}'s implements that
 * backend with {code StringBuffer} and {code StringBuilder}.
 * It used by {code Format}'s implements to replace inner string
 * buffer from {code StringBuffer} to {code StringBuilder} to gain
 * a better performance.
 */
final class StringBufFactory {

    static Format.StringBuf of(StringBuffer sb) {
        return new StringBufferImpl(sb);
    }

    static Format.StringBuf of(StringBuilder sb) {
        return new StringBuilderImpl(sb);
    }

    private static class StringBufferImpl implements Format.StringBuf {
        private final StringBuffer sb;

        StringBufferImpl(StringBuffer sb) {
            this.sb = sb;
        }

        @Override
        public int length() {
            return sb.length();
        }

        @Override
        public String substring(int start, int end) {
            return sb.substring(start, end);
        }

        @Override
        public Format.StringBuf append(char c) {
            sb.append(c);
            return this;
        }

        @Override
        public Format.StringBuf append(String str) {
            sb.append(str);
            return this;
        }

        @Override
        public Format.StringBuf append(int i) {
            sb.append(i);
            return this;
        }

        @Override
        public Format.StringBuf append(char[] str, int offset, int len) {
            sb.append(str, offset, len);
            return this;
        }

        @Override
        public StringBuffer asStringBuffer() {
            return sb;
        }

        @Override
        public StringBuilder asStringBuilder() {
            throw new AssertionError("Can't cast StringBuffer to StringBuilder");
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }

    private static class StringBuilderImpl implements Format.StringBuf {
        private final StringBuilder sb;

        StringBuilderImpl(StringBuilder sb) {
            this.sb = sb;
        }

        @Override
        public int length() {
            return sb.length();
        }

        @Override
        public String substring(int start, int end) {
            return sb.substring(start, end);
        }

        @Override
        public Format.StringBuf append(char c) {
            sb.append(c);
            return this;
        }

        @Override
        public Format.StringBuf append(String str) {
            sb.append(str);
            return this;
        }

        @Override
        public Format.StringBuf append(int i) {
            sb.append(i);
            return this;
        }

        @Override
        public Format.StringBuf append(char[] str, int offset, int len) {
            sb.append(str, offset, len);
            return this;
        }

        @Override
        public StringBuffer asStringBuffer() {
            throw new AssertionError("Can't cast StringBuilder to StringBuffer");
        }

        @Override
        public StringBuilder asStringBuilder() {
            return sb;
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }
}
