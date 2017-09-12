/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.testlibrary;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @deprecated This class is deprecated. Use the one from
 *             {@code <root>/test/lib/jdk/test/lib/process}
 */
@Deprecated
class OutputBuffer {
    private static class OutputBufferException extends RuntimeException {
        private static final long serialVersionUID = 8528687792643129571L;

        public OutputBufferException(Throwable cause) {
            super(cause);
        }
    }

    private final Process p;
    private final Future<Void> outTask;
    private final Future<Void> errTask;
    private final ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();

    /**
     * Create an OutputBuffer, a class for storing and managing stdout and
     * stderr results separately
     *
     * @param stdout
     *            stdout result
     * @param stderr
     *            stderr result
     */
    OutputBuffer(Process p) {
        this.p = p;
        StreamPumper outPumper = new StreamPumper(p.getInputStream(),
                stdoutBuffer);
        StreamPumper errPumper = new StreamPumper(p.getErrorStream(),
                stderrBuffer);

        outTask = outPumper.process();
        errTask = errPumper.process();
    }

    /**
     * Returns the stdout result
     *
     * @return stdout result
     */
    public String getStdout() {
        try {
            outTask.get();
            return stdoutBuffer.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutputBufferException(e);
        } catch (ExecutionException | CancellationException e) {
            throw new OutputBufferException(e);
        }
    }

    /**
     * Returns the stderr result
     *
     * @return stderr result
     */
    public String getStderr() {
        try {
            errTask.get();
            return stderrBuffer.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutputBufferException(e);
        } catch (ExecutionException | CancellationException e) {
            throw new OutputBufferException(e);
        }
    }

    public int getExitValue() {
        try {
            return p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutputBufferException(e);
        }
    }
}
