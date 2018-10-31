/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
        if (args.length == 0) {
            launchChildProcesses();
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

    static void launchChildProcesses() throws Throwable {
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

            // Try to replace classes that are used by the archived subgraph graphs.
            "-subgraph java/util/ArrayList",
            "-subgraph java/lang/module/ResolvedModule",

            // Replace classes that are loaded after JVMTI_PHASE_PRIMORDIAL. It's OK to replace such
            // classes even when CDS is enabled. Nothing bad should happen.
            "-notshared jdk/internal/vm/PostVMInitHook",
            "-notshared java/util/Locale",
            "-notshared sun/util/locale/BaseLocale",
            "-notshared java/lang/Readable",
        };

        int n = 0;
        for (String s : tests) {
            System.out.println("Test case[" + (n++) + "] = \"" + s + "\"");
            String args[] = s.split("\\s+"); // split by space character
            launchChild(args);
        }
    }

    static void launchChild(String args[]) throws Throwable {
        if (args.length < 1) {
            throw new RuntimeException("Invalid test case. Should be <-early> <-subgraph> <-notshared> klassName");
        }
        String klassName = null;
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
                throw new RuntimeException("Unknown option: " + opt);
            }
        }
        klassName = args[args.length-1];
        Class.forName(klassName.replace("/", ".")); // make sure it's a valid class

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
            .setUseSystemArchive(true)
            .setUseVersion(false)
            .addSuffix("-showversion",
                       "-Xlog:cds",
                       "-XX:+UnlockDiagnosticVMOptions",
                       agent,
                       "-XX:+WhiteBoxAPI",
                       "-Xbootclasspath/a:" + ClassFileInstaller.getJarPath("whitebox.jar"));

        if (subgraph) {
            opts.addSuffix("-Xlog:cds+heap",
                           "-Xlog:class+load");
        }

        opts.addSuffix("ReplaceCriticalClasses",
                       "child",
                       shared,
                       klassName);

        final boolean expectDisable = !early.equals("");
        final boolean checkSubgraph = subgraph;
        CDSTestUtils.run(opts).assertNormalExit(out -> {
                if (expectDisable) {
                    out.shouldContain("UseSharedSpaces: CDS is disabled because early JVMTI ClassFileLoadHook is in use.");
                    System.out.println("CDS disabled as expected");
                }
                if (checkSubgraph) {
                    // As of 2018/10/21 the classes in the archived subgraphs won't be
                    // replaced because all archived subgraphs were loaded in JVMTI_PHASE_PRIMORDIAL.
                    //
                    // This is the first class to be loaded after JVMTI has exited JVMTI_PHASE_PRIMORDIAL.
                    // Make sure no subgraphs are loaded afterwards.
                    //
                    // Can't use out.shouldNotMatch() because that doesn't match across multiple lines.
                    String firstNonPrimordialClass = "jdk.jfr.internal.EventWriter";
                    String regexp = firstNonPrimordialClass + ".*initialize_from_archived_subgraph";
                    Pattern regex = Pattern.compile(regexp, Pattern.DOTALL);
                    Matcher matcher = regex.matcher(out.getStdout());
                    if (matcher.find()) {
                        out.reportDiagnosticSummary();
                        throw new RuntimeException("'" + regexp
                                                   + "' found in stdout: '" + matcher.group() + "' \n");
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
