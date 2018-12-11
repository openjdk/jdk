/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core;

/**
 * A compiler thread is a daemon thread that runs at {@link Thread#MAX_PRIORITY}.
 */
public class CompilerThread extends Thread {

    public CompilerThread(Runnable r, String namePrefix) {
        super(r);
        this.setName(namePrefix + "-" + this.getId());
        this.setPriority(Thread.MAX_PRIORITY);
        this.setDaemon(true);
    }

    @Override
    public void run() {
        setContextClassLoader(getClass().getClassLoader());
        super.run();
    }
}
