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

import java.text.Format.StringBuf;

/**
 * {@code StringBufFactory} creates implementations of {@code Format.StringBuf},
 * which is an interface with the minimum overlap required to support {@code StringBuffer}
 * and {@code StringBuilder} in {@code Format}. This allows for {@code StringBuilder} to be used
 * in place of {@code StringBuffer} to provide performance benefits for JDK internal
 * {@code Format} subclasses.
 */
final class StringBufFactory {

    private StringBufFactory() {
    }

    static StringBuf of(StringBuffer sb) {
        return new StringBufferImpl(sb);
    }

    static StringBuf of(StringBuilder sb) {
        return new StringBuilderImpl(sb);
    }

    static StringBuf of() {
        return new StringBuilderImpl();
    }

    final static class StringBufferImpl implements StringBuf {
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
        public String substring(int start) {
            return sb.substring(start);
        }

        @Override
        public StringBuf append(char c) {
            sb.append(c);
            return this;
        }

        @Override
        public StringBuf append(String str) {
            sb.append(str);
            return this;
        }

        @Override
        public StringBuf append(int i) {
            sb.append(i);
            return this;
        }

        @Override
        public StringBuf append(char[] str, int offset, int len) {
            sb.append(str, offset, len);
            return this;
        }

        @Override
        public StringBuf append(CharSequence s, int start, int end) {
            sb.append(s, start, end);
            return this;
        }

        @Override
        public StringBuf append(StringBuffer asb) {
            sb.append(asb);
            return this;
        }

        @Override
        public boolean isProxyStringBuilder() {
            return false;
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

    final static class StringBuilderImpl implements StringBuf {
        private final StringBuilder sb;

        StringBuilderImpl(StringBuilder sb) {
            this.sb = sb;
        }

        StringBuilderImpl() {
            this.sb = new StringBuilder();
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
        public String substring(int start) {
            return sb.substring(start);
        }

        @Override
        public StringBuf append(char c) {
            sb.append(c);
            return this;
        }

        @Override
        public StringBuf append(String str) {
            sb.append(str);
            return this;
        }

        @Override
        public StringBuf append(int i) {
            sb.append(i);
            return this;
        }

        @Override
        public StringBuf append(char[] str, int offset, int len) {
            sb.append(str, offset, len);
            return this;
        }

        @Override
        public StringBuf append(CharSequence s, int start, int end) {
            sb.append(s, start, end);
            return this;
        }

        @Override
        public StringBuf append(StringBuffer asb) {
            sb.append(asb);
            return this;
        }


        @Override
        public boolean isProxyStringBuilder() {
            return true;
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
