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

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedByInterruptException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.salver.Salver;
import org.graalvm.compiler.salver.SalverOptions;
import org.graalvm.compiler.salver.dumper.AbstractGraalDumper;
import org.graalvm.compiler.salver.serialize.JSONSerializer;
import org.graalvm.compiler.salver.serialize.Serializer;

public abstract class AbstractGraalDumpHandler<D extends AbstractGraalDumper> extends AbstractDumpHandler<D> implements DebugDumpHandler {

    private Serializer serializer;

    private static final int MAX_FAILURES = 7;
    private int failures;

    public static final class NotInitializedException extends IOException {

        private static final long serialVersionUID = 1L;
    }

    protected void ensureInitialized() throws IOException {
        if (writer == null) {
            if (failures < MAX_FAILURES) {
                if (SalverOptions.SalverToFile.getValue()) {
                    initializeFileChannelWriter();
                } else {
                    initializeSocketChannelWriter();
                }
            }
            if (writer == null) {
                throw new NotInitializedException();
            }
        }
        if (dumper == null) {
            dumper = createDumper();
            if (dumper == null) {
                throw new NotInitializedException();
            }
            if (serializer == null) {
                serializer = createSerializer();
            }
            if (serializer.getWriter() != writer) {
                serializer.setWriter(writer);
            }
            dumper.setSerializer(serializer);
            dumper.beginDump();
        }
    }

    protected abstract D createDumper();

    protected Serializer createSerializer() {
        return new JSONSerializer();
    }

    protected abstract void handle(Object obj, String msg) throws IOException;

    protected void initializeSocketChannelWriter() {
        InetSocketAddress socketAddress = Salver.getSocketAddress();
        try {
            setWriter(socketAddress);
            printlnTTY("Connected to %s:%d (ECID = %s)", socketAddress.getHostName(), socketAddress.getPort(), Salver.ECID);
        } catch (ClosedByInterruptException e) {
            // May be caused by a cancelled Graal compilation
        } catch (IOException e) {
            printlnTTY("Couldn't connect to %s:%d (%s)", socketAddress.getHostName(), socketAddress.getPort(), e);
            failures++;
        }
    }

    private static final ThreadLocal<SimpleDateFormat> sdf = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("YYYY-MM-dd_HH-mm");
        }
    };

    protected void initializeFileChannelWriter() {
        String filename = sdf.get().format(new Date());
        if (label != null) {
            filename += "_" + Salver.ECID + "_" + label.replaceAll("(?i)[^a-z0-9-]", "-");
        }
        String fileExt = JSONSerializer.getFileExtension();
        File file = new File(filename + "." + fileExt);
        try {
            for (int i = 1; file.exists(); i++) {
                if (i < 1 << 7) {
                    file = new File(filename + "_" + i + "." + fileExt);
                } else {
                    throw new IOException();
                }
            }
            setWriter(file.toPath());
            printlnTTY("Dumping to \"%s\"", file.getName());
        } catch (ClosedByInterruptException e) {
            // May be caused by a cancelled Graal compilation
        } catch (IOException e) {
            printlnTTY("Failed to open %s for dumping (%s)", file.getName(), e);
            failures++;
        }
    }

    public void dump(Object obj) {
        dump(obj, null);
    }

    @Override
    public void dump(Object obj, String msg) {
        try {
            handle(obj, msg);
        } catch (NotInitializedException e) {
            // Ignore
        } catch (IOException e) {
            printlnTTY("%s", e);
            if (failures < MAX_FAILURES) {
                failures++;
            } else {
                close();
            }
        }
    }

    @Override
    public void close() {
        try {
            super.close();
        } catch (IOException e) {
            printlnTTY("%s", e);
        } finally {
            failures = 0;
        }
    }

    protected void printlnTTY(String format, Object... args) {
        if (label != null) {
            TTY.println("[" + label + "] " + format, args);
        } else {
            TTY.println(format, args);
        }
    }
}
