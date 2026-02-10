/*
 * Copyright (c) 2025 IBM Corporation. All rights reserved.
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

import java.io.*;
import jdk.jshell.tool.*;

public class WXHealing {

    // There's nothing special about jshell here: we just need an
    // application that does a lot of compilation and class loading.
    public static void main(String[] args) throws Throwable {
        JavaShellToolBuilder
            .builder()
            .in(new ByteArrayInputStream
                ("""
                 void main() {
                     System.out.println("Hello, World!");
                 }
                 main()
                 2+2
                 Math.sqrt(2)
                 4 * Math.atan(1)
                 Math.exp(1)
                 """
                 .getBytes()), null)
            .start();
    }
}
