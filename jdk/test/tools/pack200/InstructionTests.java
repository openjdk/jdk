/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import static java.nio.file.StandardOpenOption.*;
import java.util.regex.Pattern;

/*
 * @test
 * @bug 8003549
 * @summary tests class files instruction formats introduced in JSR-335
 * @compile -XDignore.symbol.file Utils.java InstructionTests.java
 * @run main InstructionTests
 * @author ksrini
 */
public class InstructionTests {
    public static void main(String... args) throws Exception {
        testInvokeOpCodes();
    }
    /*
     * the following should produce invokestatic and invokespecial
     * on InterfaceMethodRefs vs. MethodRefs, packer/unpacker should work
     */
    static void testInvokeOpCodes() throws Exception {
        List<String> scratch = new ArrayList<>();
        final String fname = "A";
        String javaFileName = fname + Utils.JAVA_FILE_EXT;
        scratch.add("interface IntIterator {");
        scratch.add("    default void forEach(){}");
        scratch.add("    static void next() {}");
        scratch.add("}");
        scratch.add("class A implements IntIterator {");
        scratch.add("public void forEach(Object o){");
        scratch.add("IntIterator.super.forEach();");
        scratch.add("IntIterator.next();");
        scratch.add("}");
        scratch.add("}");
        File cwd = new File(".");
        File javaFile = new File(cwd, javaFileName);
        Files.write(javaFile.toPath(), scratch, Charset.defaultCharset(),
                CREATE, TRUNCATE_EXISTING);

        // make sure we have -g so that we  compare LVT and LNT entries
        Utils.compiler("-g", javaFile.getName());

        // jar the file up
        File testjarFile = new File(cwd, "test" + Utils.JAR_FILE_EXT);
        Utils.jar("cvf", testjarFile.getName(), ".");

        // pack using --repack
        File outjarFile = new File(cwd, "out" + Utils.JAR_FILE_EXT);
        scratch.clear();
        scratch.add(Utils.getPack200Cmd());
        scratch.add("-J-ea");
        scratch.add("-J-esa");
        scratch.add("--repack");
        scratch.add(outjarFile.getName());
        scratch.add(testjarFile.getName());
        List<String> output = Utils.runExec(scratch);
        // TODO remove this when we get bc escapes working correctly
        // this test anyhow would  fail  at that time
        findString("WARNING: Passing.*" + fname + Utils.CLASS_FILE_EXT,
                        output);

        Utils.doCompareVerify(testjarFile, outjarFile);
    }

    static boolean findString(String str, List<String> list) {
        Pattern p = Pattern.compile(str);
        for (String x : list) {
            if (p.matcher(x).matches())
                return true;
        }
        throw new RuntimeException("Error: " + str + " not found in output");
    }
}
