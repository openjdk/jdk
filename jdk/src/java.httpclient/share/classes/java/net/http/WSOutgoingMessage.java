/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package java.net.http;

import java.nio.ByteBuffer;

abstract class WSOutgoingMessage {

    interface Visitor {
        void visit(Text message);
        void visit(Binary message);
        void visit(Ping message);
        void visit(Pong message);
        void visit(Close message);
    }

    abstract void accept(Visitor visitor);

    private WSOutgoingMessage() { }

    static final class Text extends WSOutgoingMessage {

        public final boolean isLast;
        public final CharSequence characters;

        Text(boolean isLast, CharSequence characters) {
            this.isLast = isLast;
            this.characters = characters;
        }

        @Override
        void accept(Visitor visitor) {
            visitor.visit(this);
        }

        @Override
        public String toString() {
            return WSUtils.toStringSimple(this) + "[isLast=" + isLast
                    + ", characters=" + WSUtils.toString(characters) + "]";
        }
    }

    static final class Binary extends WSOutgoingMessage {

        public final boolean isLast;
        public final ByteBuffer bytes;

        Binary(boolean isLast, ByteBuffer bytes) {
            this.isLast = isLast;
            this.bytes = bytes;
        }

        @Override
        void accept(Visitor visitor) {
            visitor.visit(this);
        }

        @Override
        public String toString() {
            return WSUtils.toStringSimple(this) + "[isLast=" + isLast
                    + ", bytes=" + WSUtils.toString(bytes) + "]";
        }
    }

    static final class Ping extends WSOutgoingMessage {

        public final ByteBuffer bytes;

        Ping(ByteBuffer bytes) {
            this.bytes = bytes;
        }

        @Override
        void accept(Visitor visitor) {
            visitor.visit(this);
        }

        @Override
        public String toString() {
            return WSUtils.toStringSimple(this) + "[" + WSUtils.toString(bytes) + "]";
        }
    }

    static final class Pong extends WSOutgoingMessage {

        public final ByteBuffer bytes;

        Pong(ByteBuffer bytes) {
            this.bytes = bytes;
        }

        @Override
        void accept(Visitor visitor) {
            visitor.visit(this);
        }

        @Override
        public String toString() {
            return WSUtils.toStringSimple(this) + "[" + WSUtils.toString(bytes) + "]";
        }
    }

    static final class Close extends WSOutgoingMessage {

        public final ByteBuffer bytes;

        Close(ByteBuffer bytes) {
            this.bytes = bytes;
        }

        @Override
        void accept(Visitor visitor) {
            visitor.visit(this);
        }

        @Override
        public String toString() {
            return WSUtils.toStringSimple(this) + "[" + WSUtils.toString(bytes) + "]";
        }
    }
}
