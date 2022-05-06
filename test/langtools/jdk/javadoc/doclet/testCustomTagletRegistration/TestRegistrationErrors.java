/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8206181
 * @library  ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.* *
 * @run main TestRegistrationErrors
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javadoc.tester.JavadocTester;

public class TestRegistrationErrors extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestRegistrationErrors tester = new TestRegistrationErrors();
        tester.runTests();
    }

    @Test
    public void test() throws Exception {
        try (Stream<Path> tagletClasses = findTagletClasses()) {
            tagletClasses.forEach(p -> {
                String tagletName = getTagletName(p);
                javadoc("-d", "out-" + tagletName, // a directory per taglet
                        "-tagletpath", System.getProperty("test.classes"),
                        "-taglet", tagletName,
                        testSrc("TestRegistrationErrors.java")); // specify this file
                checkExit(Exit.ERROR);
                new OutputChecker(Output.OUT).checkUnique(Pattern.compile("thrown while trying to register Taglet"));
                checkNoCrashes();
            });
        }
    }

    private static Stream<Path> findTagletClasses() throws IOException {
        var path = Path.of(System.getProperty("test.classes"));
        return Files.find(path, Integer.MAX_VALUE,
                (p, a) -> a.isRegularFile() && p.toString().endsWith("Taglet.class"));
    }

    private static String getTagletName(Path tagletClass) {
        Path fileName = tagletClass.getFileName();
        return fileName.toString().substring(0, fileName.toString().lastIndexOf('.'));
    }

    protected void checkNoCrashes() {
        checking("check crashes");
        Matcher matcher = Pattern.compile("\\s*at.*\\(.*\\.java:\\d+\\)")
                .matcher(getOutput(Output.STDERR));
        if (!matcher.find()) {
            passed("");
        } else {
            failed("Looks like a stacktrace: " + matcher.group());
        }
    }
}
