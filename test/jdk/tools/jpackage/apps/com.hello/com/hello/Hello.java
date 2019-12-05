/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.hello;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class Hello {

    private static final String MSG = "jpackage test application";
    private static final int EXPECTED_NUM_OF_PARAMS = 3; // Starts at 1

    public static void main(String[] args) {
        String outputFile = "appOutput.txt";
        File file = new File(outputFile);

        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
            System.out.println(MSG);
            out.println(MSG);

            System.out.println("args.length: " + args.length);
            out.println("args.length: " + args.length);

            for (String arg : args) {
                System.out.println(arg);
                out.println(arg);
            }

            for (int index = 1; index <= EXPECTED_NUM_OF_PARAMS; index++) {
                String value = System.getProperty("param" + index);
                if (value != null) {
                    System.out.println("-Dparam" + index + "=" + value);
                    out.println("-Dparam" + index + "=" + value);
                }
            }
        } catch (Exception ex) {
            System.err.println(ex.toString());
        }
    }

}
