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
 */

package jdk.jfr.event.security;

import jdk.internal.access.SharedSecrets;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

import java.security.Security;
import java.util.List;
import java.util.Properties;

/*
 * @test
 * @bug 8292177
 * @summary InitialSecurityProperty JFR event
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules java.base/jdk.internal.access
 * @run main/othervm jdk.jfr.event.security.TestInitialSecurityPropertyEvent
 */
public class TestInitialSecurityPropertyEvent {

    private static final String SEC_KEY = "security.overridePropertiesFile";
    public static void main(String[] args) throws Exception {
        try (Recording recording = new Recording()) {
            recording.enable(EventNames.InitialSecurityProperty)
                    .with("period", "beginChunk");
            recording.start();
            // this property edit should not be recorded
            Security.setProperty(SEC_KEY, "false");
            recording.stop();

            Properties p = SharedSecrets.getJavaSecurityPropertiesAccess().getInitialProperties();
            List<RecordedEvent> events = Events.fromRecording(recording);
            if (events.size() == 0) {
                throw new Exception("No security properties - Security class may not have loaded ?");
            }
            Asserts.assertEquals(events.size(), p.size(), "Incorrect number of events");
            assertEvent(events, SEC_KEY, "true");
        }
    }

    private static void assertEvent(List<RecordedEvent> events, String key, String origValue) throws Exception {
        for (RecordedEvent e : events) {
            if (e.getString("key").equals(key)) {
                Events.assertField(e, "value").equal(origValue);
                return;
            }
        }
        System.out.println(events);
        throw new Exception("Incorrect value for " + key + " property recorded");
    }
}
