/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Tests how CDS works when critical library classes are replaced with JVMTI ClassFileLoadHook
 * @library /test/lib
 * @requires vm.cds
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller -jar whitebox.jar sun.hotspot.WhiteBox
 * @run main/othervm/native ReplaceCriticalClasses
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.process.OutputAnalyzer;
import sun.hotspot.WhiteBox;

public class ReplaceCriticalClasses {
    public static void main(String args[]) throws Throwable {
        ReplaceCriticalClasses rcc = new ReplaceCriticalClasses();
        rcc.process(args);
    }

    public void process(String args[]) throws Throwable {
        if (args.length == 0) {
            // Dump the shared archive in case it was not generated during the JDK build.
            // Put the archive at separate file to avoid clashes with concurrent tests.
            CDSOptions opts = new CDSOptions()
                .setXShareMode("dump")
                .setArchiveName(ReplaceCriticalClasses.class.getName() + ".jsa")
                .setUseVersion(false)
                .addSuffix("-showversion");
            CDSTestUtils.run(opts).assertNormalExit("");

            launchChildProcesses(getTests());
        } else if (args.length == 3 && args[0].equals("child")) {
            Class klass = Class.forName(args[2].replace("/", "."));
            if (args[1].equals("-shared")) {
                testInChild(true, klass);
            } else if (args[1].equals("-notshared")) {
                testInChild(false, klass);
            } else {
                throw new RuntimeException("Unknown child exec option " + args[1]);
            }
            return;
        } else {
            throw new RuntimeException("Usage: @run main/othervm/native ReplaceCriticalClasses");
        }
    }

    public String[] getTests() {
        String tests[] = {
            // CDS should be disabled -- these critical classes will be replaced
            // because JvmtiExport::early_class_hook_env() is true.
            "-early -notshared java/lang/Object",
            "-early -notshared java/lang/String",
            "-early -notshared java/lang/Cloneable",
            "-early -notshared java/io/Serializable",

            // CDS should not be disabled -- these critical classes cannot be replaced because
            // JvmtiExport::early_class_hook_env() is false.
            "java/lang/Object",
            "java/lang/String",
            "java/lang/Cloneable",
            "java/io/Serializable",

            /* Try to replace classes that are used by the archived subgraph graphs.
               The following test cases are in ReplaceCriticalClassesForSubgraphs.java.
            "-early -notshared -subgraph java/lang/module/ResolvedModule jdk.internal.module.ArchivedModuleGraph",
            "-early -notshared -subgraph java/lang/Long java.lang.Long$LongCache",
            "-subgraph java/lang/Long java.lang.Long$LongCache",
            */

            // Replace classes that are loaded after JVMTI_PHASE_PRIMORDIAL. It's OK to replace
            // such
            // classes even when CDS is enabled. Nothing bad should happen.
            "-notshared java/util/Locale",
            "-notshared sun/util/locale/BaseLocale",
            "-notshared java/lang/Readable",
        };
        return tests;
    }

    static void launchChildProcesses(String tests[]) throws Throwable {
        int n = 0;
        for (String s : tests) {
            System.out.println("Test case[" + (n++) + "] = \"" + s + "\"");
            String args[] = s.split("\\s+"); // split by space character
            launchChild(args);
        }
    }

    static void launchChild(String args[]) throws Throwable {
        if (args.length < 1) {
            throw new RuntimeException("Invalid test case. Should be <-early> <-subgraph> <-notshared> klassName subgraphKlass");
        }
        String klassName = null;
        String subgraphKlass = null;
        String early = "";
        boolean subgraph = false;
        String shared = "-shared";

        for (int i=0; i<args.length-1; i++) {
            String opt = args[i];
            if (opt.equals("-early")) {
                early = "-early,";
            } else if (opt.equals("-subgraph")) {
                subgraph = true;
            } else if (opt.equals("-notshared")) {
                shared = opt;
            } else {
              if (!subgraph) {
                throw new RuntimeException("Unknown option: " + opt);
              }
            }
        }
        if (subgraph) {
          klassName = args[args.length-2];
          subgraphKlass = args[args.length-1];
        } else {
          klassName = args[args.length-1];
        }
        Class.forName(klassName.replace("/", ".")); // make sure it's a valid class
        final String subgraphInit = "initialize_from_archived_subgraph " + subgraphKlass;

        // We will pass an option like "-agentlib:SimpleClassFileLoadHook=java/util/Locale,XXX,XXX".
        // The SimpleClassFileLoadHook agent would attempt to hook the java/util/Locale class
        // but leave the class file bytes unchanged (it replaces all bytes "XXX" with "XXX", i.e.,
        // a no-op). JVMTI doesn't check the class file bytes returned by the agent, so as long
        // as the agent returns a buffer, it will not load the class from CDS, and will instead
        // load the class by parsing the buffer.
        //
        // Note that for safety we don't change the contents of the class file bytes. If in the
        // future JVMTI starts checking the contents of the class file bytes, this test would need
        // to be updated. (You'd see the test case with java/util/Locale staring to fail).
        String agent = "-agentlib:SimpleClassFileLoadHook=" + early + klassName + ",XXX,XXX";

        CDSOptions opts = (new CDSOptions())
            .setXShareMode("auto")
            .setArchiveName(ReplaceCriticalClasses.class.getName() + ".jsa")
            .setUseVersion(false)
            .addSuffix("-showversion",
                       "-Xlog:cds",
                       "-XX:+UnlockDiagnosticVMOptions",
                       agent,
                       "-XX:+WhiteBoxAPI",
                       "-Xbootclasspath/a:" + ClassFileInstaller.getJarPath("whitebox.jar"));

        if (subgraph) {
            opts.addSuffix("-Xlog:cds,cds+heap");
        }

        opts.addSuffix("ReplaceCriticalClasses",
                       "child",
                       shared,
                       klassName);

        final boolean expectDisable = !early.equals("");
        final boolean checkSubgraph = subgraph;
        final boolean expectShared = shared.equals("-shared");
        CDSTestUtils.run(opts).assertNormalExit(out -> {
                if (expectDisable) {
                    out.shouldContain("UseSharedSpaces: CDS is disabled because early JVMTI ClassFileLoadHook is in use.");
                    System.out.println("CDS disabled as expected");
                }
                if (checkSubgraph) {
                    if (expectShared) {
                        if (!out.getOutput().contains("UseSharedSpaces: Unable to map at required address in java heap")) {
                            out.shouldContain(subgraphInit);
                        }
                    } else {
                      out.shouldNotContain(subgraphInit);
                    }
                }
            });
    }

    static void testInChild(boolean shouldBeShared, Class klass) {
        WhiteBox wb = WhiteBox.getWhiteBox();

        if (shouldBeShared && !wb.isSharedClass(klass)) {
            throw new RuntimeException(klass + " should be shared but but actually is not.");
        }
        if (!shouldBeShared && wb.isSharedClass(klass)) {
            throw new RuntimeException(klass + " should not be shared but actually is.");
        }
        System.out.println("wb.isSharedClass(klass): " + wb.isSharedClass(klass) + " == " + shouldBeShared);

        String strings[] = {
            // interned strings from j.l.Object
            "@",
            "nanosecond timeout value out of range",
            "timeoutMillis value is negative",

            // interned strings from j.l.Integer
            "0",
            "0X",
            "0x",
            "int"
        };

        // Make sure the interned string table is same
        for (String s : strings) {
            String i = s.intern();
            if (s != i) {
                throw new RuntimeException("Interned string mismatch: \"" + s + "\" @ " + System.identityHashCode(s) +
                                           " vs \"" + i + "\" @ " + System.identityHashCode(i));
            }
        }
        // We have tried to use ClassFileLoadHook to replace critical library classes (which may
        // may not have succeeded, depending on whether the agent has requested
        // can_generate_all_class_hook_events/can_generate_early_class_hook_events capabilities).
        //
        // In any case, the JVM should have started properly (perhaps with CDS disabled) and
        // the above operations should succeed.
        System.out.println("If I can come to here without crashing, things should be OK");
    }
}
