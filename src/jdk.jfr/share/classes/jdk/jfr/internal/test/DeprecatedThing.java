/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.test;

@Deprecated(since = "0")
public class DeprecatedThing {
    public int counter;

    public void foo() {
        bar();
    }

    public void zoo() {
        System.out.println("Zoo invoked");
        for (int i = 0; i < 1_000_000; i++) {
            bar();
        }
    }

    private void bar() {
        baz();
    }

    public void baz() {
        inc();
    }

    private void inc() {
        counter++;
    }

    @Deprecated(forRemoval = true)
    public void instanceDeprecatedForRemoval() {
        for (int i = 0; i < 1_000_000; i++) {
           inc();
        }
    }

    @Deprecated(since = "0", forRemoval = true)
    public void instanceDeprecatedSinceForRemoval() {
        counter++;
    }
}
