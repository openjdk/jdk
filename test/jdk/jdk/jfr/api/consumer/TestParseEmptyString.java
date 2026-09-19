/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
package jdk.jfr.api.consumer;

import java.nio.file.Path;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.internal.JVM;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8392325
 * @summary Tests that an empty string is not parsed as null
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules jdk.jfr/jdk.jfr.internal
 * @run main/othervm jdk.jfr.api.consumer.TestParseEmptyString
 */
public class TestParseEmptyString {

    public static void main(String[] args) throws Exception {
        Path file = Utils.createTempFile("empty-string", ".jfr");

        try (Recording r = new Recording()) {
            r.enable("jdk.ThreadStart");
            r.start();

            Thread t1 = new Thread(new ThreadGroup(""), "");
            t1.start();
            t1.join();

            // Flush is needed to make the empty string appear first in the parse order
            JVM.flush();

            Thread t2 = new Thread(new ThreadGroup(""), "");
            t2.start();
            t2.join();

            r.dump(file);
        }

        boolean found = false;
        for (RecordedEvent e : RecordingFile.readAllEvents(file)) {
            if (e.getEventType().getName().equals("jdk.ThreadStart")) {
                RecordedThread t = e.getThread("thread");
                if (t.getJavaName().isEmpty()) {
                    Asserts.assertEquals("", t.getThreadGroup().getName());
                    found = true;
                }
            }
        }
        Asserts.assertTrue(found, "There should be a thread with empty name");
    }
}
