/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.io;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;

/**
 * @test
 * @bug 8310994
 * @summary test selection events
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.event.io.TestSelectorSelectEvent
 */
public class TestSelectorSelectEvent {

    private static String COUNT_FIELD = "selectionKeyCount";

    public static void main(String[] args) throws Throwable {
        new TestSelectorSelectEvent().test();
    }

    public void test() throws Throwable {
        try (Recording recording = new Recording()) {
            try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
                recording.enable(EventNames.SelectorSelect).withoutThreshold();
                recording.start();

                InetAddress lb = InetAddress.getLoopbackAddress();
                ssc.bind(new InetSocketAddress(lb, 0));

                try (SocketChannel sc1 = SocketChannel.open(ssc.getLocalAddress());
                    SocketChannel sc2 = ssc.accept();
                    Selector sel = Selector.open()) {

                    // register for read events, channel should not be selected
                    sc1.configureBlocking(false);
                    SelectionKey key = sc1.register(sel, SelectionKey.OP_READ);
                    int n = sel.selectNow();
                    Asserts.assertTrue(n == 0);

                    // write bytes to other end of connection
                    ByteBuffer msg = ByteBuffer.wrap("hello".getBytes("UTF-8"));
                    int nwrote = sc2.write(msg);
                    Asserts.assertTrue(nwrote >= 0);

                    // channel should be selected
                    n = sel.select();
                    Asserts.assertTrue(n == 1);
                }
                recording.stop();

                List<RecordedEvent> events = Events.fromRecording(recording);
                Asserts.assertEquals(events.size(), 2);
                Asserts.assertTrue(events.get(0).getInt(COUNT_FIELD) == 0);
                Asserts.assertTrue(events.get(1).getInt(COUNT_FIELD) == 1);
            }
        }
    }

}
