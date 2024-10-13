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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.sun.tools.javac.launcher.SourceLauncher;
import com.sun.tools.javac.launcher.Result;

record Run(String stdOut, String stdErr, Throwable exception, Result result) {
    static Run of(Path file) {
        return Run.of(file, List.of(), List.of("1", "2", "3"));
    }

    static Run of(Path file, List<String> runtimeArgs, List<String> appArgs) {
        List<String> args = new ArrayList<>();
        args.add(file.toString());
        args.addAll(appArgs);

        PrintStream prev = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(baos, true)) {
            System.setOut(out);
            StringWriter sw = new StringWriter();
            try (PrintWriter err = new PrintWriter(sw, true)) {
                var launcher = new SourceLauncher(err);
                var result = launcher.run(runtimeArgs.toArray(String[]::new), args.toArray(String[]::new));
                return new Run(baos.toString(), sw.toString(), null, result);
            } catch (Throwable throwable) {
                return new Run(baos.toString(), sw.toString(), throwable, null);
            }
        } finally {
            System.setOut(prev);
        }
    }
}
