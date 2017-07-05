/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.test.models;

import java.io.PrintWriter;
import java.io.StringWriter;

@SuppressWarnings("javadoc")
public class InternalRunnable implements Runnable, RestrictedRunnable {

    // This is a public field in a restricted class; scripts should not see it.
    public final int canNotSeeThisField = 42;

    private boolean runExecuted = false;

    @Override
    public void run() {
        runExecuted = true;
    }

    @Override
    public void restrictedRun() {
        // This is a public method on a restricted interface; scripts should not see it.
        throw new AssertionError();
    }

    @Override
    public String toString() {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        if(runExecuted) {
            pw.println("InternalRunnable.run() executed!");
        }
        pw.println("InternalRunnable.toString() executed!");
        pw.flush();
        return sw.toString();
    }

    public void canNotInvokeThis() {
        // This is a public method in a restricted class; scripts should not see it.
        throw new AssertionError();
    }

    public void getInvisibleProperty() {
        // This is a public method in a restricted class; scripts should not see it.
        throw new AssertionError();
    }
}

