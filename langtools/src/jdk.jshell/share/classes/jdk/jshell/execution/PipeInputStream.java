/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jshell.execution;

import java.io.InputStream;

/**
 *
 * @author Jan Lahoda
 */
class PipeInputStream extends InputStream {

    private static final int INITIAL_SIZE = 128;
    private int[] buffer = new int[INITIAL_SIZE];
    private int start;
    private int end;
    private boolean closed;

    @Override
    public synchronized int read() {
        while (start == end) {
            if (closed) {
                return -1;
            }
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
        if (closed) {
            throw new IllegalStateException("Already closed.");
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

    @Override
    public synchronized void close() {
        closed = true;
        notifyAll();
    }

}
