/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8375548
 * @enablePreview
 * @library / /test/lib
 * @summary Testing the additions and fixes of Replay file v4
 * @requires vm.flagless & vm.flightRecorder != true & vm.compMode != "Xint" & vm.compMode != "Xcomp" &
 *           vm.debug == true & vm.compiler2.enabled
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+TieredCompilation
 *                   ${test.main.class}
 */

package compiler.ciReplay;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.test.lib.Asserts;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import compiler.ciReplay.ReplayFile.ParsedReplayFile;
import compiler.ciReplay.ReplayFile.ParsedReplayFile.*;

public class TestReplayV4 extends DumpReplayBase {
    private final String[] defaultReplayRunFlags;

    public static void main(String[] args) {
        new TestReplayV4().runTest("-XX:CompileCommand=dontinline,*::*",
                                           "--enable-preview",
                                           "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                                           "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
                                           TIERED_DISABLED_VM_OPTION);
    }

    private TestReplayV4() {
        defaultReplayRunFlags = defaultReplayRunFlags();
    }


    private String[] defaultReplayRunFlags() {
        List<String> vmFlags = new ArrayList<>();
        Collections.addAll(vmFlags,
                           "-XX:+ReplayIgnoreInitErrors",
                           "-XX:CompileCommand=dontinline,*::*",
                           "--enable-preview",
                           "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                           "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
                           TIERED_DISABLED_VM_OPTION
        );
        return vmFlags.toArray(new String[0]);
    }

    @Override
    public void testAction() {
        reDumpAndCompare();
    }

    private String makeMessageFromList(String header, List<String> lines) {
        var message = new StringBuilder(header);
        message.append(":\n");
        for (String diff : lines) {
            message.append("  - ").append(diff).append("\n");
        }
        return message.toString();
    }

    private void reDumpAndCompare() {
        ParsedReplayFile firstParsedReplay;
        ParsedReplayFile secondParsedReplay;
        try {
            String[] reDumpingFlags = Arrays.copyOf(defaultReplayRunFlags, defaultReplayRunFlags.length + 2);
            reDumpingFlags[defaultReplayRunFlags.length] = "-XX:CompileCommand=option," + "*::*" + ",bool,DumpReplay,true";
            reDumpingFlags[defaultReplayRunFlags.length+1] = "-XX:CompileCommand=PrintCompilation,*::*";
            Asserts.assertEQ(getReplayFiles().size(), 1);
            File firstReplay = getReplayFiles().getFirst();
            positiveTest(reDumpingFlags);
            List<File> replayFilesPostRun;
            try (Stream<Path> files = Files.list(Paths.get("."))) {
                replayFilesPostRun = files.map(Path::toFile).filter(f -> f.getName().startsWith(DUMP_REPLAY_PATTERN)).toList();
            }
            Asserts.assertEQ(replayFilesPostRun.size(), 2);
            Asserts.assertTrue(replayFilesPostRun.contains(firstReplay));
            var secondReplayOpt = replayFilesPostRun.stream().filter(file -> !file.equals(firstReplay)).findAny();
            Asserts.assertTrue(secondReplayOpt.isPresent());
            var secondReplay = secondReplayOpt.get();
            System.out.println("Replay read by the second run: "+firstReplay+"; replay produced: "+secondReplay);

            firstParsedReplay = ParsedReplayFile.parse(firstReplay);
            secondParsedReplay = ParsedReplayFile.parse(secondReplay);
        } catch (Throwable t) {
            System.out.println(t);
            System.out.println(t.getMessage());
            throw new Error("Can't find replay: " + t, t);
        }

        // First, let's make sure replay files are not crazy.
        var firstInsanities = firstParsedReplay.checkSanity();
        Asserts.assertTrue(firstInsanities.isEmpty(), makeMessageFromList("Insane first replay file", firstInsanities));
        var secondInsanities = secondParsedReplay.checkSanity();
        Asserts.assertTrue(secondInsanities.isEmpty(), makeMessageFromList("Insane second replay file", secondInsanities));

        // For lookup later. This is allowed only after sanity checking.
        firstParsedReplay.index();
        secondParsedReplay.index();

        // Now, we make sure they have equivalent enough content.
        var differences = ParsedReplayFile.findDifferences(firstParsedReplay, secondParsedReplay);
        Asserts.assertTrue(differences.isEmpty(), makeMessageFromList("Differences", differences));

        // Finally, we check a few facts about the second replay file.
        var oArrNullCmdOpt = secondParsedReplay.findStaticFieldCommand("compiler/ciReplay/TestReplayV4$Test", "oArrNull");
        Asserts.assertTrue(oArrNullCmdOpt.isPresent());
        var oArrNullCmd = oArrNullCmdOpt.get();
        Asserts.assertTrue(oArrNullCmd instanceof StaticFieldCommandNullArray);

        var oArrRefArrayCmdOpt = secondParsedReplay.findStaticFieldCommand("compiler/ciReplay/TestReplayV4$Test", "oArrRefArray");
        Asserts.assertTrue(oArrRefArrayCmdOpt.isPresent());
        var oArrRefArrayCmdUntyped = oArrRefArrayCmdOpt.get();
        Asserts.assertTrue(oArrRefArrayCmdUntyped instanceof StaticFieldCommandRefArray);
        var oArrRefArrayCmd = (StaticFieldCommandRefArray)oArrRefArrayCmdUntyped;
        Asserts.assertFalse(oArrRefArrayCmd.nullFree());
        Asserts.assertEquals(oArrRefArrayCmd.length(), 2);

        var oArrNullableAtomicArrayCmdOpt = secondParsedReplay.findStaticFieldCommand("compiler/ciReplay/TestReplayV4$Test", "oArrNullableAtomicArray");
        Asserts.assertTrue(oArrNullableAtomicArrayCmdOpt.isPresent());
        var oArrNullableAtomicArrayCmdUntyped = oArrNullableAtomicArrayCmdOpt.get();
        Asserts.assertTrue(oArrNullableAtomicArrayCmdUntyped instanceof StaticFieldCommandFlatArray);
        var oArrNullableAtomicArrayCmd = (StaticFieldCommandFlatArray)oArrNullableAtomicArrayCmdUntyped;
        Asserts.assertFalse(oArrNullableAtomicArrayCmd.nullFree());
        Asserts.assertFalse(oArrNullableAtomicArrayCmd.nonAtomic());
        Asserts.assertEquals(oArrNullableAtomicArrayCmd.length(), 2);

        var oArrNullRestrictedAtomicArrayCmdOpt = secondParsedReplay.findStaticFieldCommand("compiler/ciReplay/TestReplayV4$Test", "oArrNullRestrictedAtomicArray");
        Asserts.assertTrue(oArrNullRestrictedAtomicArrayCmdOpt.isPresent());
        var oArrNullRestrictedAtomicArrayCmdUntyped = oArrNullRestrictedAtomicArrayCmdOpt.get();
        Asserts.assertTrue(oArrNullRestrictedAtomicArrayCmdUntyped instanceof StaticFieldCommandFlatArray);
        var oArrNullRestrictedAtomicArrayCmd = (StaticFieldCommandFlatArray)oArrNullRestrictedAtomicArrayCmdUntyped;
        Asserts.assertTrue(oArrNullRestrictedAtomicArrayCmd.nullFree());
        Asserts.assertFalse(oArrNullRestrictedAtomicArrayCmd.nonAtomic());
        Asserts.assertEquals(oArrNullRestrictedAtomicArrayCmd.length(), 2);
    }

    @Override
    public String getTestClass() {
        return Test.class.getName();
    }


    private static class Test {
        static final Base[] oArrDefault = new Base[2];
        static final Base[] oArrNullableAtomicArray = (Base[]) ValueClass.newNullableAtomicArray(Derived.class, 2);
        static final Base[] oArrNullRestrictedAtomicArray = (Base[]) ValueClass.newNullRestrictedAtomicArray(Derived.class, 2, new Derived(1, 0));
        static final Base[] oArrNullRestrictedNonAtomicArray = (Base[]) ValueClass.newNullRestrictedNonAtomicArray(Derived.class, 2, new Derived(2, 0));
        static final Base[] oArrRefArray = (Base[]) ValueClass.newReferenceArray(Derived.class, 2);
        static final Base[] oArrNull = null;

        static Base o1, o2, o3, o4;
        static final Base a = new Derived(10, 15);
        static final Base a_base_null = null;
        static final Derived a_derived_null = null;
        @NullRestricted
        static final Base a_base_null_free = new Derived(10, 15);
        @NullRestricted
        static final Derived a_derived_null_free = new Derived(10, 15);

        public static void main(String[] args) {
            oArrDefault[0] = new Derived(3, 5);
            oArrNullableAtomicArray[0] = new Derived(4, 6);
            oArrNullRestrictedAtomicArray[0] = new Derived(5, 7);
            oArrNullRestrictedNonAtomicArray[0] = new Derived(6, 8);
            oArrRefArray[0] = new Derived(7, 9);
            for (int i = 0; i < 10000; i++) {
                test();
            }
        }

        static void test() {
            o1 = oArrDefault[0];
            oArrDefault[1] = a;
            o2 = oArrNullableAtomicArray[0];
            oArrNullableAtomicArray[1] = a;
            o3 = oArrNullRestrictedAtomicArray[0];
            oArrNullRestrictedAtomicArray[1] = a;
            o4 = oArrNullRestrictedNonAtomicArray[0];
            oArrNullRestrictedNonAtomicArray[1] = a;
        }

        static abstract value class Base {
            short x;
            byte y;
            public Base(int x, int y) {
                this.x = (short)x;
                this.y = (byte)y;
            }
        }

        static value class Derived extends Base {
            public Derived(int x, int y) {
                super(x, y);
            }
        }
    }
}
