/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.salver.handler;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;

import org.graalvm.compiler.salver.dumper.Dumper;
import org.graalvm.compiler.salver.writer.ChannelDumpWriter;
import org.graalvm.compiler.salver.writer.DumpWriter;

public abstract class AbstractDumpHandler<D extends Dumper> implements DumpHandler {

    protected String label;

    protected DumpWriter writer;
    protected D dumper;

    public AbstractDumpHandler() {
        setLabel(getClass().getSimpleName() + ":" + Thread.currentThread().getName());
    }

    public String getLabel() {
        return label;
    }

    protected void setLabel(String label) {
        this.label = label;
    }

    public DumpWriter getWriter() {
        return writer;
    }

    protected void setWriter(DumpWriter writer) {
        this.writer = writer;
    }

    protected void setWriter(WritableByteChannel channel) {
        setWriter(new ChannelDumpWriter(channel));
    }

    protected void setWriter(SocketAddress remote) throws IOException {
        setWriter(SocketChannel.open(remote));
    }

    protected void setWriter(Path path) throws IOException {
        setWriter(path, WRITE, TRUNCATE_EXISTING, CREATE);
    }

    protected void setWriter(Path path, OpenOption... options) throws IOException {
        setWriter(FileChannel.open(path, options));
    }

    public D getDumper() {
        return dumper;
    }

    protected void setDumper(D dumper) {
        this.dumper = dumper;
    }

    @Override
    public void close() throws IOException {
        if (dumper != null) {
            try {
                dumper.close();
            } finally {
                dumper = null;
            }
        }
        if (writer != null) {
            try {
                writer.close();
            } finally {
                writer = null;
            }
        }
    }
}
