/*
 * Copyright (c) 2018, Google and/or its affiliates. All rights reserved.
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

package MyPackage;

import java.util.List;

/**
 * @test
 * @summary Verifies if turning off the event notification stops events.
 * @build Frame HeapMonitor
 * @compile HeapMonitorEventOnOffTest.java
 * @run main/othervm/native -agentlib:HeapMonitorTest MyPackage.HeapMonitorEventOnOffTest
 */
public class HeapMonitorEventOnOffTest {
  private static void checkNoEventsAreBeingSent() {
    HeapMonitor.resetEventStorage();
    HeapMonitor.repeatAllocate(5);

    // Check that the data is not available while heap sampling is disabled.
    boolean status = HeapMonitor.eventStorageIsEmpty();
    if (!status) {
      throw new RuntimeException("Storage is not empty after allocating with disabled events.");
    }
  }

  private static void checkEventsAreBeingSent() {
    List<Frame> frameList = HeapMonitor.repeatAllocate(5);

    frameList.add(new Frame("checkEventsAreBeingSent", "()V", "HeapMonitorEventOnOffTest.java", 48));
    Frame[] frames = frameList.toArray(new Frame[0]);

    // Check that the data is available while heap sampling is enabled.
    boolean status = HeapMonitor.obtainedEvents(frames);
    if (!status) {
      throw new RuntimeException("Failed to find the traces after allocating with enabled events.");
    }
  }

  public static void main(String[] args) {
    HeapMonitor.enableSamplingEvents();
    checkEventsAreBeingSent();

    // Disabling the notification system should stop events.
    HeapMonitor.disableSamplingEvents();
    checkNoEventsAreBeingSent();

    // Enabling the notification system should start events again.
    HeapMonitor.enableSamplingEvents();
    checkEventsAreBeingSent();
  }
}
