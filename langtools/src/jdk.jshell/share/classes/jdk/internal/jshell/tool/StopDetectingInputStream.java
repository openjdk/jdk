/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jshell.tool;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

public final class StopDetectingInputStream extends InputStream {
    public static final int INITIAL_SIZE = 128;

    private final Runnable stop;
    private final Consumer<Exception> errorHandler;

    private boolean initialized;
    private int[] buffer = new int[INITIAL_SIZE];
    private int start;
    private int end;
    private State state = State.WAIT;

    public StopDetectingInputStream(Runnable stop, Consumer<Exception> errorHandler) {
        this.stop = stop;
        this.errorHandler = errorHandler;
    }

    public synchronized InputStream setInputStream(InputStream input) {
        if (initialized)
            throw new IllegalStateException("Already initialized.");
        initialized = true;

        Thread reader = new Thread() {
            @Override
            public void run() {
                try {
                    int read;
                    while (true) {
                        //to support external terminal editors, the "cmdin.read" must not run when
                        //an external editor is running. At the same time, it needs to run while the
                        //user's code is running (so Ctrl-C is detected). Hence waiting here until
                        //there is a confirmed need for input.
                        waitInputNeeded();
                        if ((read = input.read()) == (-1)) {
                            break;
                        }
                        if (read == 3 && state == StopDetectingInputStream.State.BUFFER) {
                            stop.run();
                        } else {
                            write(read);
                        }
                    }
                } catch (IOException ex) {
                    errorHandler.accept(ex);
                } finally {
                    state = StopDetectingInputStream.State.CLOSED;
                }
            }
        };
        reader.setDaemon(true);
        reader.start();

        return this;
    }

    @Override
    public synchronized int read() {
        while (start == end) {
            if (state == State.CLOSED) {
                return -1;
            }
            if (state == State.WAIT) {
                state = State.READ;
            }
            notifyAll();
            try {
                wait();
            } catch (InterruptedException ex) {
                //ignore
            }
        }
        try {
            return buffer[start];
        } finally {
            start = (start + 1) % buffer.length;
        }
    }

    public synchronized void write(int b) {
        if (state != State.BUFFER) {
            state = State.WAIT;
        }
        int newEnd = (end + 1) % buffer.length;
        if (newEnd == start) {
            //overflow:
            int[] newBuffer = new int[buffer.length * 2];
            int rightPart = (end > start ? end : buffer.length) - start;
            int leftPart = end > start ? 0 : start - 1;
            System.arraycopy(buffer, start, newBuffer, 0, rightPart);
            System.arraycopy(buffer, 0, newBuffer, rightPart, leftPart);
            buffer = newBuffer;
            start = 0;
            end = rightPart + leftPart;
            newEnd = end + 1;
        }
        buffer[end] = b;
        end = newEnd;
        notifyAll();
    }

    public synchronized void setState(State state) {
        this.state = state;
        notifyAll();
    }

    private synchronized void waitInputNeeded() {
        while (state == State.WAIT) {
            try {
                wait();
            } catch (InterruptedException ex) {
                //ignore
            }
        }
    }

    public enum State {
        /* No reading from the input should happen. This is the default state. The StopDetectingInput
         * must be in this state when an external editor is being run, so that the external editor
         * can read from the input.
         */
        WAIT,
        /* A single input character should be read. Reading from the StopDetectingInput will move it
         * into this state, and the state will be automatically changed back to WAIT when a single
         * input character is obtained. This state is typically used while the user is editing the
         * input line.
         */
        READ,
        /* Continuously read from the input. Forward Ctrl-C ((int) 3) to the "stop" Runnable, buffer
         * all other input. This state is typically used while the user's code is running, to provide
         * the ability to detect Ctrl-C in order to stop the execution.
         */
        BUFFER,
        /* The input is closed.
        */
        CLOSED
    }

}
