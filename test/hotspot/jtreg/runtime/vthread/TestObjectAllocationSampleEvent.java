/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import jdk.jfr.consumer.RecordingStream;

/**
 * @test Re-written from jfr test ObjectAllocationSampleEvent
 * @summary Tests ObjectAllocationSampleEvent
 * @requires vm.hasJFR
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} TestObjectAllocationSampleEvent.java
 * @run main/othervm --enable-preview TestObjectAllocationSampleEvent
 */
public class TestObjectAllocationSampleEvent {

    public static void main(String... args) throws Exception {
        Thread thread = Thread.ofVirtual().start(new Task());
        thread.join();
    }
}

class Task implements Runnable {

    public void run() {

        // Needs to wait for crash...
        try {
            RecordingStream rs = new RecordingStream();
            Thread.sleep(1_000);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
}
