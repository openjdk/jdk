/*
 * Copyright (c) 1998, 2011, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package com.sun.tools.example.debug.bdi;

import com.sun.jdi.*;

//### Should handle target VM death or connection failure cleanly.

public class ThreadInfo {

    private ThreadReference thread;
    private int status;

    private int frameCount;

    Object userObject;  // User-supplied annotation.

    private boolean interrupted = false;

    private void assureInterrupted() throws VMNotInterruptedException {
        if (!interrupted) {
            throw new VMNotInterruptedException();
        }
    }

    ThreadInfo (ThreadReference thread) {
        this.thread = thread;
        this.frameCount = -1;
    }

    public ThreadReference thread() {
        return thread;
    }

    public int getStatus() throws VMNotInterruptedException {
        assureInterrupted();
        update();
        return status;
    }

    public int getFrameCount() throws VMNotInterruptedException {
        assureInterrupted();
        update();
        return frameCount;
    }

    public StackFrame getFrame(int index) throws VMNotInterruptedException {
        assureInterrupted();
        update();
        try {
            return thread.frame(index);
        } catch (IncompatibleThreadStateException e) {
            // Should not happen
            interrupted = false;
            throw new VMNotInterruptedException();
        }
    }

    public Object getUserObject() {
        return userObject;
    }

    public void setUserObject(Object obj) {
        userObject = obj;
    }

    // Refresh upon first access after cache is cleared.

    void update() throws VMNotInterruptedException {
        if (frameCount == -1) {
            try {
                status = thread.status();
                frameCount = thread.frameCount();
            } catch (IncompatibleThreadStateException e) {
                // Should not happen
                interrupted = false;
                throw new VMNotInterruptedException();
            }
        }
    }

    // Called from 'ExecutionManager'.

    void validate() {
        interrupted = true;
    }

    void invalidate() {
        interrupted = false;
        frameCount = -1;
        status = ThreadReference.THREAD_STATUS_UNKNOWN;
    }

}
