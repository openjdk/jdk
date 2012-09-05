/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7185778
 * @summary javah error "Not a valid class name" on class names with dollar signs
 *   The first two tests are on an inner class name whose name does not contain $.
 *   The second two tests are on an inner class name whose name does contain $.
 *   The last test is on an outer class whose name contains $.
 * @run main T7185778 T7185778$inner
 * @run main T7185778 T7185778.inner
 * @run main T7185778 T7185778$inner$
 * @run main T7185778 T7185778.inner$
 * @run main T7185778 xx$yy
 */

public class T7185778 {
    class inner {
        native byte[] xxxxx(String name);
    }
    class inner$ {
        native byte[] xxxxx(String name);
    }

    static public void main(String[] args) {
        int rc = com.sun.tools.javah.Main.run(args, null);
        if ( rc != 0) {
            throw new Error("javah returned non zero: " + rc);
        }
    }
}

class xx$yy {
    native byte[] xxxxx(String name);
}
