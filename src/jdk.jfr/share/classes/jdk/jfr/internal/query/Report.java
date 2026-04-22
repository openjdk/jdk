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
package jdk.jfr.internal.query;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.consumer.AbstractEventStream;
import jdk.jfr.internal.dcmd.QueryRecording;
import jdk.jfr.internal.util.Output.BufferedPrinter;
import jdk.jfr.internal.util.UserDataException;
import jdk.jfr.internal.util.UserSyntaxException;

public record Report(String name) {

    public static List<Report> getReports() {
        return ViewFile.getDefault().getViewConfigurations() .stream().map(view -> new Report(view.name())).toList();
    }

    public void print(Instant startTime, Instant endTime) {
        Logger.log(LogTag.JFR, LogLevel.DEBUG, "Writing report " + name);
        BufferedPrinter output = new BufferedPrinter(System.out);
        try (QueryRecording qr = new QueryRecording(startTime, endTime)) {
            AbstractEventStream stream = (AbstractEventStream) qr.getStream();
            stream.setWaitForChunks(false);
            Configuration configuration = new Configuration();
            configuration.startTime = startTime;
            configuration.endTime = endTime;
            configuration.output = output;
            ViewPrinter printer = new ViewPrinter(configuration, stream);
            printer.execute(name);
            Logger.log(LogTag.JFR, LogLevel.DEBUG, "Report " + name + " written successfully.");
        } catch (IOException | UserDataException | UserSyntaxException e) {
            Logger.log(LogTag.JFR, LogLevel.WARN, "Error writing report " + name + " on exit: " + e.getMessage());
        }
        output.flush();
    }
}
