/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package testdata;
import java.util.*;

public class Lvt {
    public void m(String a) {
        int b;
        Object c;
        char[] d = new char[27];

        for (int j = 0; j < d.length; j++) {
            char x = d[j];
        }
    }

    public <U> List<String> n(U u, Class <? extends List<?>> z) {
        var v = new ArrayList<Integer>();

        Set<? super Set<?>> s = new TreeSet<>();

        for (List<?> f : new ArrayList<List<Object>>()) {
            System.out.println(f);
        }

        return null;
    }
}
