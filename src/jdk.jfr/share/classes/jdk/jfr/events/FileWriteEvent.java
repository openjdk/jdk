/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.DataAmount;
import jdk.jfr.Name;
import jdk.jfr.Throttle;
import jdk.jfr.internal.Type;
import jdk.jfr.internal.MirrorEvent;

@Name(Type.EVENT_NAME_PREFIX + "FileWrite")
@Label("File Write")
@Category("Java Application")
@Description("Writing data to a file")
@StackFilter({"java.nio.channels.FileChannel",
              "java.io.DataOutputStream",
              "java.io.FileOutputStream",
              "java.io.OutputStream",
              "java.io.RandomAccessFile",
              "sun.nio.ch.ChannelOutputStream",
              "sun.nio.ch.FileChannelImpl"})
@Throttle
public final class FileWriteEvent extends MirrorEvent {

    @Label("Path")
    @Description("Full path of the file, or N/A if a file descriptor was used to create the stream, for example System.out and System.err")
    public String path;

    @Label("Bytes Written")
    @Description("Number of bytes written to the file")
    @DataAmount
    public long bytesWritten;
}
