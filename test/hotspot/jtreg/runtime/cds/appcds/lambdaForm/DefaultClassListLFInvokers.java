/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @bug 8295102
 * @summary Always load the lambda-form-invoker lines from default classlist
 * @requires vm.cds
 * @library /test/jdk/lib/testlibrary /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @build DefaultClassListLFInvokers
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *             DefaultClassListLFInvokersApp DefaultClassListLFInvokersApp$CompMethods
 * @run driver DefaultClassListLFInvokers
 */

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class DefaultClassListLFInvokers {
    static final String appClass = DefaultClassListLFInvokersApp.class.getName();
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");

    static final String[] classlist = {
        appClass,
        // If we have at least one line of @lambda-form-invoker in the classlist, it triggers
        // the regeneration of the 4 XXX$Holder during -Xshare:dump.
        "@lambda-form-invoker [LF_RESOLVE] java.lang.invoke.DirectMethodHandle$Holder invokeStatic L_V"
    };

    public static void main(String[] args) throws Exception {
        File classListFile = CDSTestUtils.makeClassList(classlist);

        OutputAnalyzer dump =
            CDSTestUtils.createArchive("-XX:SharedClassListFile=" + classListFile.getPath(),
                                       "-cp", appJar);
        CDSTestUtils.checkDump(dump);

        CDSOptions opts = (new CDSOptions())
            .addSuffix("-showversion", "-Xlog:cds:stderr", "-cp", appJar, appClass)
            .setUseVersion(false);

        opts.setXShareMode("auto");
        opts.setUseSystemArchive(false);
        OutputAnalyzer withcds = CDSTestUtils.run(opts).getOutput();
        withcds.shouldContain(DefaultClassListLFInvokersApp.FLAG);
        withcds.shouldHaveExitValue(0);

        opts.setXShareMode("off");
        opts.setUseSystemArchive(true);
        OutputAnalyzer nocds = CDSTestUtils.run(opts).getOutput();
        nocds.shouldContain(DefaultClassListLFInvokersApp.FLAG);
        withcds.shouldHaveExitValue(0);

        // Make sure we still have all the LF invoker methods as when CDS is disabled,
        // in which case the XXX$Holder classes are loaded from $JAVA_HOME/lib/modules

        System.out.println("\n\n============================== Checking output: withcds vs nocds");
        TestCommon.stdoutMustMatch(withcds, nocds);

        opts.setXShareMode("auto");
        opts.setUseSystemArchive(true);
        OutputAnalyzer defcds = CDSTestUtils.run(opts).getOutput();
        defcds.shouldContain(DefaultClassListLFInvokersApp.FLAG);
        withcds.shouldHaveExitValue(0);

        // We should also have all the LF invoker methods as when the default CDS archive is used
        // in which case the XXX$Holder classes are loaded from the default archive,
        // e.g., $JAVA_HOME/lib/server/classes.jsa

        System.out.println("\n\n============================== Checking output: withcds vs defcds");
        TestCommon.stdoutMustMatch(withcds, defcds);
    }
}

class DefaultClassListLFInvokersApp {
    public static final String FLAG = "Test Success!";

    static class CompMethods implements Comparator<Method> {
        public int compare(Method a, Method b) {
            return a.toString().compareTo(b.toString());
        }
    }
    static final CompMethods compMethods = new CompMethods();

    public static void main(String[] args) throws Exception {
        test("java.lang.invoke.Invokers$Holder");
        test("java.lang.invoke.DirectMethodHandle$Holder");
        test("java.lang.invoke.DelegatingMethodHandle$Holder");
        test("java.lang.invoke.LambdaForm$Holder");
        System.out.println(FLAG);
    }

    static void test(String className) throws Exception {
        Class c = Class.forName(className);
        Method[] methods = c.getDeclaredMethods();
        System.out.println("Dumping all methods in " + c);
        Arrays.sort(methods, 0, methods.length, compMethods);
        for (Method m : methods) {
            System.out.println(m);
        }
        System.out.println("Found " + methods.length + " methods\n\n");
    }
}
