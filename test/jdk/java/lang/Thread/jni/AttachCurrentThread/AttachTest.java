/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8287982
 * @summary Test native threads attaching to the VM with JNI AttachCurrentThread
 * @requires (os.family == "linux" | os.family == "mac")
 * @library /test/lib
 * @compile ExplicitAttach.java
 * @run main AttachTest ExplicitAttach 1
 * @run main AttachTest ExplicitAttach 2
 * @run main AttachTest ExplicitAttach 4
 */

/**
 * @test
 * @summary Test native threads attaching implicitly to the VM by means of an upcall
 * @requires (os.family == "linux" | os.family == "mac") & (sun.arch.data.model == "64")
 * @library /test/lib
 * @compile ImplicitAttach.java
 * @run main AttachTest --enable-native-access=ALL-UNNAMED ImplicitAttach 1
 * @run main AttachTest --enable-native-access=ALL-UNNAMED ImplicitAttach 2
 * @run main AttachTest --enable-native-access=ALL-UNNAMED ImplicitAttach 4
 */

import java.util.stream.Stream;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class AttachTest {
    static final String TEST_CLASSES = System.getProperty("test.classes");
    static final String JAVA_LIBRARY_PATH = System.getProperty("java.library.path");

    public static void main(String[] args) throws Exception {
        // prepend -cp ${test.classes} -Djava.library.path=${java.library.path}
        String[] opts = Stream.concat(Stream.of(
                        "-cp", TEST_CLASSES,
                        "-Djava.library.path=" + JAVA_LIBRARY_PATH),
                        Stream.of(args))
                .toArray(String[]::new);
        OutputAnalyzer outputAnalyzer = ProcessTools
                .executeTestJava(opts)
                .outputTo(System.out)
                .errorTo(System.out);
        outputAnalyzer.shouldHaveExitValue(0);
    }
}
