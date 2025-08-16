/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.management.jfr.internal;

import jdk.jfr.RecordingState;
import jdk.jfr.internal.management.EventSource;
import jdk.management.jfr.FlightRecorderMXBean;
import jdk.management.jfr.RecordingInfo;

import java.util.Optional;

public class RemoteRecordingEventSource implements EventSource {

    private final FlightRecorderMXBean mbean;
    private final long recordingId;

    public RemoteRecordingEventSource(FlightRecorderMXBean mbean, long recordingId) {
        this.mbean = mbean;
        this.recordingId = recordingId;
    }

    @Override
    public long getStopTime() {
        Optional<RecordingInfo> recordingInfo = mbean.getRecordings().stream().filter(r -> r.getId() == recordingId).findFirst();
        if(recordingInfo.isEmpty()){
            return Long.MAX_VALUE;
        }
        return recordingInfo.get().getStopTime();
    }

    @Override
    public RecordingState getState()  {
        Optional<RecordingInfo> recordingInfo = mbean.getRecordings().stream().filter(r -> r.getId() == recordingId).findFirst();
        if(recordingInfo.isEmpty()){
            return  RecordingState.CLOSED;
        }
        return RecordingState.valueOf(recordingInfo.get().getState());
    }

    @Override
    public boolean stop() {
        return mbean.stopRecording(recordingId);
    }
}
