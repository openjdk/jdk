/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4916607
 * @summary Test casts (legal, warning, and errors)
 * @author gafter
 *
 * @compile/fail  -Werror -Xlint:unchecked CastWarn4.java
 */

import java.util.*;

class CastTest {

    // --- Disjoint ---

    private interface DA<T> { }
    private interface DB<T> extends DA<T> { }
    private interface DC<T> extends DA<Integer> { }

    private <N extends Number, I extends Integer, R extends Runnable, S extends String> void disjointness() {
        Object o;

        o = (DA<? super String>) (DA<? super Number>) null; // <<warn 4>>
    }

}
