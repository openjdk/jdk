/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.List;

public class CallSite {

    private int bci;
    private Method method;
    private int count;
    private String receiver;
    private int receiver_count;
    private String reason;
    private List<CallSite> calls;
    private int endNodes;
    private int endLiveNodes;
    private double timeStamp;

    CallSite() {
    }

    CallSite(int bci, Method m) {
        this.bci = bci;
        this.method = m;
    }

    void add(CallSite site) {
        if (getCalls() == null) {
            setCalls(new ArrayList<CallSite>());
        }
        getCalls().add(site);
    }

    CallSite last() {
        return last(-1);
    }

    CallSite last(int fromEnd) {
        return getCalls().get(getCalls().size() + fromEnd);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (getReason() == null) {
            sb.append("  @ " + getBci() + " " + getMethod());
        } else {
            sb.append("- @ " + getBci() + " " + getMethod() + " " + getReason());
        }
        sb.append("\n");
        if (getCalls() != null) {
            for (CallSite site : getCalls()) {
                sb.append(site);
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public void print(PrintStream stream) {
        print(stream, 0);
    }

    void emit(PrintStream stream, int indent) {
        for (int i = 0; i < indent; i++) {
            stream.print(' ');
        }
    }
    private static boolean compat = true;

    public void print(PrintStream stream, int indent) {
        emit(stream, indent);
        String m = getMethod().getHolder().replace('/', '.') + "::" + getMethod().getName();
        if (getReason() == null) {
            stream.print("  @ " + getBci() + " " + m + " (" + getMethod().getBytes() + " bytes)");

        } else {
            if (isCompat()) {
                stream.print("  @ " + getBci() + " " + m + " " + getReason());
            } else {
                stream.print("- @ " + getBci() + " " + m +
                        " (" + getMethod().getBytes() + " bytes) " + getReason());
            }
        }
        stream.printf(" (end time: %6.4f", getTimeStamp());
        if (getEndNodes() > 0) {
            stream.printf(" nodes: %d live: %d", getEndNodes(), getEndLiveNodes());
        }
        stream.println(")");

        if (getReceiver() != null) {
            emit(stream, indent + 4);
            //                 stream.println("type profile " + method.holder + " -> " + receiver + " (" +
            //                                receiver_count + "/" + count + "," + (receiver_count * 100 / count) + "%)");
            stream.println("type profile " + getMethod().getHolder() + " -> " + getReceiver() + " (" +
                    (getReceiverCount() * 100 / getCount()) + "%)");
        }
        if (getCalls() != null) {
            for (CallSite site : getCalls()) {
                site.print(stream, indent + 2);
            }
        }
    }

    public int getBci() {
        return bci;
    }

    public void setBci(int bci) {
        this.bci = bci;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public int getReceiverCount() {
        return receiver_count;
    }

    public void setReceiver_count(int receiver_count) {
        this.receiver_count = receiver_count;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<CallSite> getCalls() {
        return calls;
    }

    public void setCalls(List<CallSite> calls) {
        this.calls = calls;
    }

    public static boolean isCompat() {
        return compat;
    }

    public static void setCompat(boolean aCompat) {
        compat = aCompat;
    }

    void setEndNodes(int n) {
        endNodes = n;
    }

    public int getEndNodes() {
        return endNodes;
    }

    void setEndLiveNodes(int n) {
        endLiveNodes = n;
    }

    public int getEndLiveNodes() {
        return endLiveNodes;
    }

    void setTimeStamp(double time) {
        timeStamp = time;
    }

    public double getTimeStamp() {
        return timeStamp;
    }

}
