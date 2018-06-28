/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.java;

public class JsrScope {

    public static final JsrScope EMPTY_SCOPE = new JsrScope();

    private final long scope;

    private JsrScope(long scope) {
        this.scope = scope;
    }

    public JsrScope() {
        this.scope = 0;
    }

    public int nextReturnAddress() {
        return (int) (scope & 0xffff);
    }

    public JsrScope push(int jsrReturnBci) {
        if ((scope & 0xffff000000000000L) != 0) {
            throw new JsrNotSupportedBailout("only four jsr nesting levels are supported");
        }
        return new JsrScope((scope << 16) | jsrReturnBci);
    }

    public boolean isEmpty() {
        return scope == 0;
    }

    public boolean isPrefixOf(JsrScope other) {
        return (scope & other.scope) == scope;
    }

    public JsrScope pop() {
        return new JsrScope(scope >>> 16);
    }

    @Override
    public int hashCode() {
        return (int) (scope ^ (scope >>> 32));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj != null && getClass() == obj.getClass() && scope == ((JsrScope) obj).scope;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        long tmp = scope;
        sb.append(" [");
        while (tmp != 0) {
            sb.append(", ").append(tmp & 0xffff);
            tmp = tmp >>> 16;
        }
        sb.append(']');
        return sb.toString();
    }
}
