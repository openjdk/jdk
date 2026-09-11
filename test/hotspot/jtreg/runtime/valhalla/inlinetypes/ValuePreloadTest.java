/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test ValuePreloadTest
 * @library /test/lib
 * @requires vm.flagless
 * @enablePreview
 * @compile ValuePreloadClient0.java PreloadValue0.java ValuePreloadClient1.jcod PreloadShared.java
 * @run main runtime.valhalla.inlinetypes.ValuePreloadTest
 */

package runtime.valhalla.inlinetypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class ValuePreloadTest {

    static ProcessBuilder exec(String... args) throws Exception {
        List<String> argsList = new ArrayList<>();
        Collections.addAll(argsList, "--enable-preview");
        Collections.addAll(argsList, "-Dtest.class.path=" + System.getProperty("test.class.path", "."));
        Collections.addAll(argsList, args);
        return ProcessTools.createTestJavaProcessBuilder(argsList);
    }

    static void checkFor(ProcessBuilder pb, String expected) throws Exception {
        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        out.shouldHaveExitValue(0);
        out.shouldContain(expected);
    }

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = exec("-Xlog:class+preload","ValuePreloadClient0");
        checkFor(pb, "[info][class,preload] Preloading of class PreloadValue0 during loading of class ValuePreloadClient0. Cause: field type in LoadableDescriptors attribute");

        pb = exec("-Xlog:class+preload","ValuePreloadClient1");
        checkFor(pb, "[info][class,preload] Preloading of class PreloadValue1 during linking of class ValuePreloadClient1 (cause: LoadableDescriptors attribute) failed");

        pb = exec("-Xlog:class+preload", "PreloadShared");
        // Class java/time/LocalDate is already preloaded, so the LoadableDescriptor attribute does not trigger it
        // to be loaded from the shared archive.
        // checkFor(pb, "[info][class,preload] Preloading of class java/time/LocalDate during loading of shared class java/time/LocalDateTime (cause: field type in LoadableDescriptors attribute) succeeded");
        checkFor(pb, "[info][class,preload] Preloading of class java/time/LocalDateTime during linking of class PreloadShared (cause: LoadableDescriptors attribute) succeeded");
    }
}
