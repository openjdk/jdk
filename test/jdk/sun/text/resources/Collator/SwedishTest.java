/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8306927
 * @modules jdk.localedata
 * @summary Tests Swedish collation involving 'v' and 'w'.
 */

import java.text.Collator;
import java.util.Arrays;
import java.util.Locale;

public class SwedishTest {
    private static final String[] src = {"wb", "va", "vc"};
    private static final String[] expected = {"va", "vc", "wb"};

    public static void main (String[] args) {
        Arrays.sort(src, Collator.getInstance(Locale.of("sv")));
        if (!Arrays.equals(src, expected)) {
            throw new RuntimeException("Swedish collation failed");
        }
    }
}
