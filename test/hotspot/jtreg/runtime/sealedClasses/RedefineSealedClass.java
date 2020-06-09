/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8225056
 * @library /test/lib
 * @summary Test that a class that is a sealed class can be redefined.
 * @modules java.base/jdk.internal.misc
 * @modules java.instrument
 *          jdk.jartool/sun.tools.jar
 * @compile --enable-preview -source ${jdk.version} RedefineSealedClass.java
 * @run main/othervm --enable-preview RedefineSealedClass buildagent
 * @run main/othervm/timeout=6000 --enable-preview RedefineSealedClass runtest
 */

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.RuntimeException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.lang.instrument.IllegalClassFormatException;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class RedefineSealedClass {

    final class A extends Tester { }
    final class B extends Tester { }

    sealed static class Tester permits A, B {}

    static class LoggingTransformer implements ClassFileTransformer {

        public LoggingTransformer() {}

        public byte[] transform(ClassLoader loader, String className,
                                Class classBeingRedefined, ProtectionDomain protectionDomain,
                                byte[] classfileBuffer) throws IllegalClassFormatException {
            return null;
        }
    }

    public static void premain(String agentArgs, Instrumentation inst) throws Exception {
        LoggingTransformer t = new LoggingTransformer();
        inst.addTransformer(t, true);
        {
            Class demoClass = Class.forName("RedefineSealedClass$Tester");
            inst.retransformClasses(demoClass);
        }
    }

    private static void buildAgent() {
        try {
            ClassFileInstaller.main("RedefineSealedClass");
        } catch (Exception e) {
            throw new RuntimeException("Could not write agent classfile", e);
        }

        try {
            PrintWriter pw = new PrintWriter("MANIFEST.MF");
            pw.println("Premain-Class: RedefineSealedClass");
            pw.println("Agent-Class: RedefineSealedClass");
            pw.println("Can-Redefine-Classes: true");
            pw.println("Can-Retransform-Classes: true");
            pw.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Could not write manifest file for the agent", e);
        }

        sun.tools.jar.Main jarTool = new sun.tools.jar.Main(System.out, System.err, "jar");
        if (!jarTool.run(new String[] { "-cmf", "MANIFEST.MF", "redefineagent.jar", "RedefineSealedClass.class" })) {
            throw new RuntimeException("Could not write the agent jar file");
        }
    }

    public static void main(String argv[]) throws Exception {
        if (argv.length == 1 && argv[0].equals("buildagent")) {
            buildAgent();
            return;
        }
        if (argv.length == 1 && argv[0].equals("runtest")) {
            String[] javaArgs1 = { "-XX:MetaspaceSize=12m", "-XX:MaxMetaspaceSize=12m",
                                   "-javaagent:redefineagent.jar", "--enable-preview",
                                   "RedefineSealedClass"};
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(javaArgs1);
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldNotContain("processing of -javaagent failed");
        }
    }
}
