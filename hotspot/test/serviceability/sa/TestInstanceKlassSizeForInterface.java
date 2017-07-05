/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import sun.jvm.hotspot.HotSpotAgent;
import sun.jvm.hotspot.utilities.SystemDictionaryHelper;
import sun.jvm.hotspot.oops.InstanceKlass;
import sun.jvm.hotspot.debugger.*;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Utils;
import jdk.test.lib.Asserts;

/*
 * @test
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @compile -XDignore.symbol.file=true
 *          --add-modules=jdk.hotspot.agent
 *          --add-exports=jdk.hotspot.agent/sun.jvm.hotspot=ALL-UNNAMED
 *          --add-exports=jdk.hotspot.agent/sun.jvm.hotspot.utilities=ALL-UNNAMED
 *          --add-exports=jdk.hotspot.agent/sun.jvm.hotspot.oops=ALL-UNNAMED
 *          --add-exports=jdk.hotspot.agent/sun.jvm.hotspot.debugger=ALL-UNNAMED
 *          TestInstanceKlassSizeForInterface.java
 * @run main/othervm
 *          --add-modules=jdk.hotspot.agent
 *          --add-exports=jdk.hotspot.agent/sun.jvm.hotspot=ALL-UNNAMED
 *          --add-exports=jdk.hotspot.agent/sun.jvm.hotspot.utilities=ALL-UNNAMED
 *          --add-exports=jdk.hotspot.agent/sun.jvm.hotspot.oops=ALL-UNNAMED
 *          --add-exports=jdk.hotspot.agent/sun.jvm.hotspot.debugger=ALL-UNNAMED
 *          TestInstanceKlassSizeForInterface
 */

interface Language {
    static final long nbrOfWords = 99999;
    public abstract long getNbrOfWords();
}

class ParselTongue implements Language {
    public long getNbrOfWords() {
      return nbrOfWords * 4;
    }
}

public class TestInstanceKlassSizeForInterface {

    private static void SAInstanceKlassSize(int pid,
                                            String[] instanceKlassNames) {

        HotSpotAgent agent = new HotSpotAgent();
        try {
            agent.attach((int)pid);
        }
        catch (DebuggerException e) {
            System.out.println(e.getMessage());
            System.err.println("Unable to connect to process ID: " + pid);

            agent.detach();
            e.printStackTrace();
        }

        for (String instanceKlassName : instanceKlassNames) {
            InstanceKlass iKlass = SystemDictionaryHelper.findInstanceKlass(
                                       instanceKlassName);
            Asserts.assertNotNull(iKlass,
                String.format("Unable to find instance klass for %s", instanceKlassName));
            System.out.println("SA: The size of " + instanceKlassName +
                               " is " + iKlass.getSize());
        }
        agent.detach();
    }

    private static String getJcmdInstanceKlassSize(OutputAnalyzer output,
                                                   String instanceKlassName) {
        for (String s : output.asLines()) {
            if (s.contains(instanceKlassName)) {
                String tokens[];
                System.out.println(s);
                tokens = s.split("\\s+");
                return tokens[3];
            }
        }
        return null;
    }

    private static void createAnotherToAttach(
                            String[] instanceKlassNames) throws Exception {

        ProcessBuilder pb = new ProcessBuilder();

        // Grab the pid from the current java process and pass it
        String[] toolArgs = {
            "--add-modules=jdk.hotspot.agent",
            "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot=ALL-UNNAMED",
            "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.utilities=ALL-UNNAMED",
            "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.oops=ALL-UNNAMED",
            "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.debugger=ALL-UNNAMED",
            "TestInstanceKlassSizeForInterface",
            Long.toString(ProcessTools.getProcessId())
        };

        pb.command(new String[] {
                          JDKToolFinder.getJDKTool("jcmd"),
                          Long.toString(ProcessTools.getProcessId()),
                          "GC.class_stats",
                          "VTab,ITab,OopMap,KlassBytes"
                      }
                  );

        // Start a new process to attach to the current process
        ProcessBuilder processBuilder = ProcessTools
                  .createJavaProcessBuilder(toolArgs);
        OutputAnalyzer SAOutput = ProcessTools.executeProcess(processBuilder);
        System.out.println(SAOutput.getOutput());

        OutputAnalyzer jcmdOutput = new OutputAnalyzer(pb.start());
        System.out.println(jcmdOutput.getOutput());

        // Match the sizes from both the output streams
        for (String instanceKlassName : instanceKlassNames) {
            System.out.println ("Trying to match for " + instanceKlassName);
            String jcmdInstanceKlassSize = getJcmdInstanceKlassSize(
                                                      jcmdOutput,
                                                      instanceKlassName);
            Asserts.assertNotNull(jcmdInstanceKlassSize,
                "Could not get the instance klass size from the jcmd output");
            for (String s : SAOutput.asLines()) {
                if (s.contains(instanceKlassName)) {
                   Asserts.assertTrue(
                      s.contains(jcmdInstanceKlassSize),
                      "The size computed by SA for " +
                      instanceKlassName + " does not match.");
                }
            }
        }
    }

    public static void main (String... args) throws Exception {
        String[] instanceKlassNames = new String[] {
                                          "Language",
                                          "ParselTongue",
                                          "TestInstanceKlassSizeForInterface$1"
                                      };

        if (!Platform.shouldSAAttach()) {
            System.out.println(
               "SA attach not expected to work - test skipped.");
            return;
        }

        if (args == null || args.length == 0) {
            ParselTongue lang = new ParselTongue();

            Language ventro = new Language() {
                public long getNbrOfWords() {
                    return nbrOfWords * 8;
                }
            };

            // Not tested at this point. The test needs to be enhanced
            // later to test for the sizes of the Lambda MetaFactory
            // generated anonymous classes too. (After JDK-8160228 gets
            // fixed.)
            Runnable r2 = () -> System.out.println("Hello world!");
            r2.run();

            createAnotherToAttach(instanceKlassNames);
        } else {
            SAInstanceKlassSize(Integer.parseInt(args[0]), instanceKlassNames);
        }
    }
}
