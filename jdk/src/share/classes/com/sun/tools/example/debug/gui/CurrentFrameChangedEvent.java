/*
 * Copyright (c) 1998, 1999, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.example.debug.gui;

import com.sun.jdi.*;
import com.sun.tools.example.debug.bdi.*;
import java.util.EventObject;

public class CurrentFrameChangedEvent extends EventObject {

    private static final long serialVersionUID = 4214479486546762179L;
    private ThreadInfo tinfo;
    private int index;
    private boolean invalidate;

    public CurrentFrameChangedEvent(Object source, ThreadInfo tinfo,
                                    int index, boolean invalidate) {
        super(source);
        this.tinfo = tinfo;
        this.index = index;
        this.invalidate = invalidate;
    }

    public ThreadReference getThread() {
        return tinfo == null? null : tinfo.thread();
    }

    public ThreadInfo getThreadInfo() {
        return tinfo;
    }

    public int getIndex() {
        return index;
    }

    public boolean getInvalidate() {
        return invalidate;
    }
}
