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

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;

import jdk.jshell.tool.JavaShellToolBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/*
 * @test
 * @bug 8347418
 * @summary Verify that loading of JShell history doesn't lead to a
 *          NullPointerException when the Preferences are modified concurrently.
 * @run junit ConcurrentHistoryLoadingTest
 */
public class ConcurrentHistoryLoadingTest {

    private static final String HISTORY_LINE_PREFIX = "HISTORY_LINE_";

    @Test
    public void testConcurrentHistoryLoading() throws Throwable {
        AtomicBoolean removeOnAccess = new AtomicBoolean();
        Preferences testPrefs = new ReplToolTesting.MemoryPreferences() {
            @Override
            protected String getSpi(String key) {
                String result = super.getSpi(key);
                if (key.startsWith(HISTORY_LINE_PREFIX) && removeOnAccess.getAndSet(false)) {
                    for (String key2Remote : keysSpi()) {
                        remove(key2Remote);
                    }
                }
                return result;
            }
        };
        StringBuilder input = new StringBuilder();
        int max = 10;
        for (int j = 0; j < max; j++) {
            input.append("int x").append(j).append(" = 42\n");
        }
        JavaShellToolBuilder
                .builder()
                .persistence(testPrefs)
                .in(new ByteArrayInputStream(input.toString().getBytes()), null)
                .start();
        Assertions.assertEquals(10, Arrays.stream(testPrefs.keys())
                .filter(key -> key.startsWith(HISTORY_LINE_PREFIX))
                .count());
        removeOnAccess.set(true);
        JavaShellToolBuilder
                .builder()
                .persistence(testPrefs)
                .in(new ByteArrayInputStream(input.toString().getBytes()), null)
                .start();

    }
}
