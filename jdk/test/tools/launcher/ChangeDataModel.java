/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4894330 4810347 6277269 8029388
 * @compile -XDignore.symbol.file ChangeDataModel.java
 * @run main ChangeDataModel
 * @summary Verify -d32 and -d64 options are accepted(rejected) on all platforms
 * @author Joseph D. Darcy, ksrini
 */
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ChangeDataModel extends TestHelper {
    private static final File TestJar      = new File("test" + JAR_FILE_EXT);
    private static final String OptionName = "Args";
    private static final File TestOptionJar  = new File(OptionName + JAR_FILE_EXT);
    private static final String OPT_PREFIX = "ARCH_OPT:";

    static void createTestJar() throws Exception {
        String[] code = {
            "   public static void main(String argv[]) {",
            "      System.out.println(\"" + OPT_PREFIX + "-d\" + System.getProperty(\"sun.arch.data.model\", \"none\"));",
            "   }",};
        createJar(TestJar, code);
    }
    public static void main(String... args) throws Exception {
        createTestJar();
        createOptionsJar();

        // verify if data model flag for default data model is accepted, also
        // verify if the complimentary data model is rejected.
        if (is32Bit) {
            checkAcceptance(javaCmd, "-d32");
            checkRejection(javaCmd, "-d64");
            checkOption(javaCmd, "-d64");
        } else if (is64Bit) {
            checkAcceptance(javaCmd, "-d64");
            checkRejection(javaCmd, "-d32");
            checkOption(javaCmd, "-d32");
        } else {
            throw new Error("unsupported data model");
        }
    }

    static void checkAcceptance(String cmd, String dmodel) {
        TestResult tr = doExec(cmd, dmodel, "-jar", TestJar.getAbsolutePath());
        if (!tr.contains(OPT_PREFIX + dmodel)) {
            System.out.println(tr);
            String message = "Data model flag " + dmodel +
                    " not accepted or had improper effect.";
            throw new RuntimeException(message);
        }
    }

    static void checkRejection(String cmd, String dmodel) {
        TestResult tr = doExec(cmd, dmodel, "-jar", TestJar.getAbsolutePath());
        if (tr.contains(OPT_PREFIX + dmodel)) {
            System.out.println(tr);
            String message = "Data model flag " + dmodel + " was accepted.";
            throw new RuntimeException(message);
        }
    }

    static void checkOption(String cmd, String dmodel) throws Exception {
        TestResult tr = doExec(cmd, "-jar", TestOptionJar.getAbsolutePath(), dmodel);
        verifyOption(tr, dmodel);

        tr = doExec(cmd, "-cp", ".", OptionName, dmodel);
        verifyOption(tr, dmodel);
    }

    static void verifyOption(TestResult tr, String dmodel) {
        if (!tr.contains(OPT_PREFIX + dmodel)) {
            System.out.println(tr);
            String message = "app argument: " + dmodel + " not found.";
            throw new RuntimeException(message);
        }
        if (!tr.isOK()) {
            System.out.println(tr);
            String message = "app argument: " + dmodel + " interpreted ?";
            throw new RuntimeException(message);
        }
    }

    static void createOptionsJar() throws Exception {
        List<String> code = new ArrayList<>();
        code.add("public class Args {");
        code.add("   public static void main(String argv[]) {");
        code.add("       for (String x : argv)");
        code.add("           System.out.println(\"" + OPT_PREFIX + "\" + x);");
        code.add("   }");
        code.add("}");
        File optionsJava  = new File(OptionName + JAVA_FILE_EXT);
        createFile(optionsJava, code);
        File optionsClass = new File(OptionName + CLASS_FILE_EXT);

        compile(optionsJava.getName());
        createJar("cvfe",
                  TestOptionJar.getName(),
                  OptionName,
                  optionsClass.getName());
    }
}
