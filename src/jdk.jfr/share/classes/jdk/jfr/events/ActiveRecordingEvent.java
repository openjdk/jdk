/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jfr.events;

import jdk.jfr.Category;
import jdk.jfr.Label;
import jdk.jfr.DataAmount;
import jdk.jfr.Name;
import jdk.jfr.Timespan;
import jdk.jfr.Timestamp;
import jdk.jfr.internal.RemoveFields;
import jdk.jfr.internal.Type;

@Name(Type.EVENT_NAME_PREFIX + "ActiveRecording")
@Label("Flight Recording")
@Category("Flight Recorder")
@RemoveFields({"duration", "eventThread", "stackTrace"})
public final class ActiveRecordingEvent extends AbstractJDKEvent {

    // The order of these fields must be the same as the parameters in
    // commit(... , long, String, String, long, long, long, long, long)

    @Label("Id")
    public long id;

    @Label("Name")
    public String name;

    @Label("Destination")
    public String destination;

    @Label("To Disk")
    public boolean disk;

    @Label("Max Age")
    @Timespan(Timespan.MILLISECONDS)
    public long maxAge;

    @Label("Flush Interval")
    @Timespan(Timespan.MILLISECONDS)
    public long flushInterval;

    @Label("Max Size")
    @DataAmount
    public long maxSize;

    @Label("Start Time")
    @Timestamp(Timestamp.MILLISECONDS_SINCE_EPOCH)
    public long recordingStart;

    @Label("Recording Duration")
    @Timespan(Timespan.MILLISECONDS)
    public long recordingDuration;

    public static boolean enabled() {
        return false; // Generated
    }

    public static void commit(long timestamp, long id, String name,
                              String destination, boolean disk, long maxAge, long flushInterval,
                              long maxSize, long recordingStart, long recordingDuration) {
        // Generated
    }
}
