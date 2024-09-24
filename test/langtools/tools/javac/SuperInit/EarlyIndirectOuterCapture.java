/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 9999999
 * @summary Allow capture of outer instance by skipping over inaccessible instance(s)
 */

public class EarlyIndirectOuterCapture {

    InnerSuperclass inner;

    EarlyIndirectOuterCapture() {
        this(null);
    }

    EarlyIndirectOuterCapture(InnerSuperclass inner) {
        this.inner = inner;
    }

    class InnerSuperclass {
        public EarlyIndirectOuterCapture getOuter() {
            return EarlyIndirectOuterCapture.this;
        }
    }

    static class InnerOuter extends EarlyIndirectOuterCapture {     // accessible
        class InnerInnerOuter extends EarlyIndirectOuterCapture {   // not accessible
            InnerInnerOuter() {
                super(new InnerSuperclass() { /* who's my outer instance? */ });
            }
        }
    }

    public static void main(String[] args) {
        InnerSuperclass inner = new InnerOuter().new InnerInnerOuter().inner;
        EarlyIndirectOuterCapture outer = inner.getOuter();
        String actual = outer.getClass().getName();
        String expected = "EarlyIndirectOuterCapture$InnerOuter";
        if (!actual.equals(expected))
            throw new AssertionError(String.format("\"%s\" != \"%s\"", actual, expected));
    }
}
