/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

// AOT-linked classes are loaded during VM bootstrap by the C++ class AOTLinkedClassBulkLoader.
// Make sure that the Module, Package, CodeSource and ProtectionDomain of these classes are
// set up properly.

/*
 * @test id=static
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/jdk/lib/testlibrary /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build InitiatingLoaderTester BadOldClassA BadOldClassB
 * @build jdk.test.whitebox.WhiteBox BulkLoaderTest SimpleCusty
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar BulkLoaderTestApp.jar BulkLoaderTestApp MyUtil InitiatingLoaderTester
 *                 BadOldClassA BadOldClassB
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar cust.jar
 *                 SimpleCusty
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar jdk.test.whitebox.WhiteBox
 * @run driver BulkLoaderTest STATIC
 */

/*
 * @test id=dynamic
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/jdk/lib/testlibrary /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build InitiatingLoaderTester BadOldClassA BadOldClassB
 * @build jdk.test.whitebox.WhiteBox BulkLoaderTest SimpleCusty
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar BulkLoaderTestApp.jar BulkLoaderTestApp MyUtil InitiatingLoaderTester
 *                 BadOldClassA BadOldClassB
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar cust.jar
 *                 SimpleCusty
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:WhiteBox.jar BulkLoaderTest DYNAMIC
 */

/*
 * @test id=aot
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/jdk/lib/testlibrary /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build jdk.test.whitebox.WhiteBox InitiatingLoaderTester BadOldClassA BadOldClassB
 * @build BulkLoaderTest SimpleCusty
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar BulkLoaderTestApp.jar BulkLoaderTestApp MyUtil InitiatingLoaderTester
 *                 BadOldClassA BadOldClassB
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar cust.jar
 *                 SimpleCusty
 * @run driver BulkLoaderTest AOT
 */

import java.io.File;
import java.lang.StackWalker.StackFrame;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Set;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.whitebox.WhiteBox;

public class BulkLoaderTest {
    static final String appJar = ClassFileInstaller.getJarPath("BulkLoaderTestApp.jar");
    static final String mainClass = "BulkLoaderTestApp";

    public static void main(String[] args) throws Exception {
        Tester t = new Tester();

        // Run with archived FMG loaded
        t.run(args);

        // Run with an extra classpath -- archived FMG can still load.
        {
            String extraVmArgs[] = {
                "-cp",
                appJar + File.pathSeparator + "foobar.jar"
            };
            OutputAnalyzer out = t.productionRun(extraVmArgs);
            out.shouldHaveExitValue(0);
        }

        // Run without archived FMG -- fail to load
        {
            final String archiveType = (args[0].equals("AOT")) ? "AOT cache" : "shared archive file";
            String extraVmArgs[] = {
                "-Xlog:cds",
                "-Djdk.module.showModuleResolution=true"
            };
            t.setCheckExitValue(false);
            OutputAnalyzer out = t.productionRun(extraVmArgs);
            out.shouldHaveExitValue(1);
            out.shouldContain(archiveType + " has aot-linked classes. It cannot be used when archived full module graph is not used.");
            t.setCheckExitValue(true);
        }
    }

    static class Tester extends CDSAppTester {
        public Tester() {
            super(mainClass);
            useWhiteBox(ClassFileInstaller.getJarPath("WhiteBox.jar"));
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            return new String[] {
                "-Xlog:cds,aot+load,cds+class=debug,aot+class=debug",
                "-XX:+AOTClassLinking",
            };
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (isAOTWorkflow() && runMode == RunMode.TRAINING) {
                out.shouldContain("Skipping BadOldClassA: Unlinked class not supported by AOTConfiguration");
                out.shouldContain("Skipping SimpleCusty: Duplicated unregistered class");
            }

            if (isDumping(runMode)) {
                // Check that we are archiving classes for custom class loaders.
                out.shouldMatch(",class.* SimpleCusty");
            }
        }
    }
}

class BulkLoaderTestApp {
    static String allPerms = "null.*<no principals>.*java.security.Permissions.*,*java.security.AllPermission.*<all permissions>.*<all actions>";

    public static void main(String args[]) throws Exception {
        checkClasses();
        checkInitiatingLoader();
        checkOldClasses();
        checkCustomLoader();
    }

    // Check the ClassLoader/Module/Package/ProtectionDomain/CodeSource of classes that are aot-linked
    static void checkClasses() throws Exception {
        check(String.class,
              "null",  // loader
              "module java.base",
              "package java.lang",
              "null",
              allPerms);

        check(Class.forName("sun.util.logging.internal.LoggingProviderImpl"),
              "null",
              "module java.logging",
              "package sun.util.logging.internal",
              "null",
              allPerms);


        check(javax.tools.FileObject.class,
              "^jdk.internal.loader.ClassLoaders[$]PlatformClassLoader@",
              "module java.compiler",
              "package javax.tools",
              "jrt:/java.compiler <no signer certificates>",
              "jdk.internal.loader.ClassLoaders[$]PlatformClassLoader.*<no principals>.*java.security.Permissions");

        check(BulkLoaderTestApp.class,
              "jdk.internal.loader.ClassLoaders[$]AppClassLoader@",
              "^unnamed module @",
              "package ",
              "file:.*BulkLoaderTestApp.jar <no signer certificates>",
              "jdk.internal.loader.ClassLoaders[$]AppClassLoader.*<no principals>.*java.security.Permissions");

        check(Class.forName("com.sun.tools.javac.Main"),
              "jdk.internal.loader.ClassLoaders[$]AppClassLoader@",
              "module jdk.compiler",
              "package com.sun.tools.javac",
              "jrt:/jdk.compiler <no signer certificates>",
              "jdk.internal.loader.ClassLoaders[$]AppClassLoader.*<no principals>.*java.security.Permissions");

        doit(() -> {
            Class<?> lambdaClass = MyUtil.getCallerClass(1);
            check(lambdaClass,
              "jdk.internal.loader.ClassLoaders[$]AppClassLoader@",
              "unnamed module",
              "package ",
              "file:.*BulkLoaderTestApp.jar <no signer certificates>",
              "jdk.internal.loader.ClassLoaders[$]AppClassLoader.*<no principals>.*java.security.Permissions");

          });
    }

    static void check(Class c, String loader, String module, String pkg, String codeSource, String protectionDomain) {
        System.out.println("====================================================================");
        System.out.println(c.getName() + ", loader  = " + c.getClassLoader());
        System.out.println(c.getName() + ", module  = " + c.getModule());
        System.out.println(c.getName() + ", package = " + c.getPackage());
        System.out.println(c.getName() + ", CS      = " + c.getProtectionDomain().getCodeSource());
        System.out.println(c.getName() + ", PD      = " + c.getProtectionDomain());

        expectMatch("" + c.getClassLoader(), loader);
        expectMatch("" + c.getModule(), module);
        expectSame("" + c.getPackage(), pkg);
        expectMatch("" + c.getProtectionDomain().getCodeSource(), codeSource);
        expectMatch("" + c.getProtectionDomain(), protectionDomain);
    }

    static void expectSame(String a, String b) {
        if (!a.equals(b)) {
            throw new RuntimeException("Expected \"" + b + "\" but got \"" + a + "\"");
        }
    }
    static void expectMatch(String string, String pattern) {
        Matcher matcher = Pattern.compile(pattern, Pattern.DOTALL).matcher(string);
        if (!matcher.find()) {
            throw new RuntimeException("Expected pattern \"" + pattern + "\" but got \"" + string + "\"");
        }
    }

    static void doit(Runnable t) {
        t.run();
    }

    static void checkInitiatingLoader() throws Exception {
        try {
            InitiatingLoaderTester.tryAccess();
        } catch (IllegalAccessError t) {
            if (t.getMessage().contains("cannot access class jdk.internal.misc.Unsafe (in module java.base)")) {
                System.out.println("Expected exception:");
                t.printStackTrace(System.out);
                // Class.forName() should still work. We just can't resolve it in CP entries.
                Class<?> c = Class.forName("jdk.internal.misc.Unsafe");
                System.out.println("App loader can still resolve by name: " + c);
                return;
            }
            throw new RuntimeException("Unexpected exception", t);
        }

        throw new RuntimeException("Should not have succeeded");
    }

    static void checkOldClasses() throws Exception {
        // Resolve BadOldClassA from the constant pool without linking it.
        // implNote: BadOldClassA will be excluded, so any resolved refereces
        // to BadOldClassA should be removed from the archived constant pool.
        Class c = BadOldClassA.class;
        Object n = new Object();
        if (c.isInstance(n)) { // Note that type-testing BadOldClassA here neither links nor initializes it.
            throw new RuntimeException("Must not succeed");
        }

        try {
            // In dynamic dump, the VM loads BadOldClassB and then attempts to
            // link it. This will leave BadOldClassB in a "failed verification" state.
            // All refernces to BadOldClassB from the CP should be purged from the CDS
            // archive.
            c = BadOldClassB.class;
            c.newInstance();
            throw new RuntimeException("Must not succeed");
        } catch (VerifyError e) {
            System.out.println("Caught VerifyError for BadOldClassB: " + e);
        }
    }


    static void checkCustomLoader() throws Exception {
        WhiteBox wb = WhiteBox.getWhiteBox();
        for (int i = 0; i < 2; i++) {
            Object o = initFromCustomLoader();
            System.out.println(o);
            Class c = o.getClass();
            if (wb.isSharedClass(BulkLoaderTestApp.class)) {
                // We are running with BulkLoaderTestApp from the AOT cache (or CDS achive)
                if (i == 0) {
                    if (!wb.isSharedClass(c)) {
                        throw new RuntimeException("The first loader should load SimpleCusty from AOT cache (or CDS achive)");
                    }
                } else {
                    if (wb.isSharedClass(c)) {
                        throw new RuntimeException("The second loader should not load SimpleCusty from AOT cache (or CDS achive)");
                    }
                }
            }
        }
    }

    static ArrayList<ClassLoader> savedLoaders = new ArrayList<>();

    static Object initFromCustomLoader() throws Exception {
        String path = "cust.jar";
        URL url = new File(path).toURI().toURL();
        URL[] urls = new URL[] {url};
        URLClassLoader urlClassLoader =
            new URLClassLoader("MyLoader", urls, null);
        savedLoaders.add(urlClassLoader);
        Class c = Class.forName("SimpleCusty", true, urlClassLoader);
        return c.newInstance();
    }
}

class MyUtil {
    // depth is 0-based -- i.e., depth==0 returns the class of the immediate caller of getCallerClass
    static Class<?> getCallerClass(int depth) {
        // Need to add the frame of the getCallerClass -- so the immediate caller (depth==0) of this method
        // is at stack.get(1) == stack.get(depth+1);
        StackWalker walker = StackWalker.getInstance(
            Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,
                   StackWalker.Option.SHOW_HIDDEN_FRAMES));
        List<StackFrame> stack = walker.walk(s -> s.limit(depth+2).collect(Collectors.toList()));
        return stack.get(depth+1).getDeclaringClass();
    }
}
