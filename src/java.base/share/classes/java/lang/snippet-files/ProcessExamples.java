/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

public class ProcessExamples {
    // @start region=example
    void main() {
        try (Process p = new ProcessBuilder("cat").start();
             var writer = p.outputWriter();
             var reader = p.inputReader()) {
            writer.write(haiku);
            writer.close();
            // Read all lines and print each
            reader.readAllLines()
                    .forEach(IO::println);
            var status = p.waitFor();
            if (status != 0)
                throw new RuntimeException("unexpected process status: " + status);
        } catch (Exception e) {
            System.out.println("Process failed: " + e);
        }
    }

    static final String haiku = """
                Oh, the sunrise glow;
                Paddling with the river flow;
                Chilling still, go slow.
                """;
    // @end region=example
}
