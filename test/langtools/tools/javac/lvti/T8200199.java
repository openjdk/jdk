/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test /nodynamiccopyright/
 * @bug 8200199
 * @summary javac suggests to use var even when var is used
 * @compile/fail/ref=T8200199.out -Werror -XDfind=local -XDrawDiagnostics T8200199.java
 */

class T8200199 {

    class Resource implements AutoCloseable {
        public void close() {};
    }

    public void implicit() {
        var i = 33;
        for (var x = 0 ; x < 10 ; x++) { }
        try (var r = new Resource()) { }
    }

    public void explicit() {
        int i = 33;
        for (int x = 0 ; x < 10 ; x++) { }
        try (Resource r = new Resource()) { }
    }
}
