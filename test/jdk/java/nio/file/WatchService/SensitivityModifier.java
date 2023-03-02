/*
 * Copyright (c) 2008, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4313887
 * @summary Sanity test for JDK-specific sensitivity level watch event modifier
 * @modules jdk.unsupported
 * @library .. /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run main/timeout=240 SensitivityModifier
 * @key randomness
 */

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import static java.nio.file.StandardWatchEventKinds.*;
import java.io.IOException;
import java.util.Random;
import com.sun.nio.file.SensitivityWatchEventModifier;
import jdk.test.lib.RandomFactory;

public class SensitivityModifier {

    static final Random RAND = RandomFactory.getRandom();

    static WatchKey register(Path dir, WatchService watcher)
        throws IOException {
        SensitivityWatchEventModifier[] sensitivities =
            SensitivityWatchEventModifier.values();
        SensitivityWatchEventModifier sensitivity =
            sensitivities[RAND.nextInt(sensitivities.length)];
        return dir.register(watcher, new WatchEvent.Kind<?>[]{ ENTRY_MODIFY },
                            sensitivity);
    }

    @SuppressWarnings("unchecked")
    static void doTest(Path dir) throws Exception {
        FileSystem fs = dir.getFileSystem();
        try (WatchService watcher = fs.newWatchService()) {
            // register the directory (random sensitivity)
            WatchKey key = register(dir, watcher);

            // check validity
            if (!key.isValid())
                throw new RuntimeException("Registration is invalid");

            // cancel the registration
            key.cancel();
        }
    }

    public static void main(String[] args) throws Exception {
        Path dir = TestUtil.createTemporaryDirectory();
        try {
            doTest(dir);
        } finally {
            TestUtil.removeAll(dir);
        }
    }
}
