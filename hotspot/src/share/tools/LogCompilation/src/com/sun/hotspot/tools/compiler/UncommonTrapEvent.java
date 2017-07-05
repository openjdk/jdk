/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package com.sun.hotspot.tools.compiler;

import java.io.PrintStream;

class UncommonTrapEvent extends BasicLogEvent {

    private final String reason;
    private final String action;
    private int count;
    private String jvms = "";

    UncommonTrapEvent(double s, String i, String r, String a, int c) {
        super(s, i);
        reason = r;
        action = a;
        count = c;
    }


    public void addJVMS(String method, int bci) {
        setJvms(getJvms() + "  @" + bci + " " + method + "\n");
    }

    public void updateCount(UncommonTrapEvent trap) {
        setCount(Math.max(getCount(), trap.getCount()));
    }

    public void print(PrintStream stream) {
        stream.printf("%s uncommon trap %s %s\n", getId(), getReason(), getAction());
        stream.print(getJvms());
    }

    public String getReason() {
        return reason;
    }

    public String getAction() {
        return action;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getJvms() {
        return jvms;
    }

    public void setJvms(String jvms) {
        this.jvms = jvms;
    }

    public void setCompilation(Compilation compilation) {
        this.compilation = compilation;
    }
}
