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

package compiler.lib.test_generator;

import java.util.Objects;

public class ProcessOutput {
    private final int exitCode;
    private final String output;

    public ProcessOutput(int exitCode, String output) {
        this.exitCode = exitCode;
        this.output = output;
    }

    /* TODO:
     * improve error handling:
     * - What kind of error is it? error in Template and cannot parse with javac
     * - or runtime crash in Java (e.g. Null-pointer Exception) nor JVM (compiler bug?)
     **/
    public void checkExecutionOutput() {
        if (exitCode == 0 && Objects.requireNonNull(output).contains("Passed")) {
            System.out.println("Test passed successfully.");
        } else {
            System.err.println("Test failed:");
            System.err.println(output);
        }
    }
}
