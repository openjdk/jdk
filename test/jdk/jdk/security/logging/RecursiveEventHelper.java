/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.logging.*;

import jdk.internal.event.EventHelper;

/*
 * @test
 * @bug 8329013
 * @summary StackOverflowError when starting Apache Tomcat with signed jar
 * @modules java.base/jdk.internal.event:+open
 * @run main/othervm -Xmx32m -Djava.util.logging.manager=RecursiveEventHelper RecursiveEventHelper
 */
public class RecursiveEventHelper extends LogManager {
    // an extra check to ensure the custom manager is in use
    static volatile boolean customMethodCalled;

    public static void main(String[] args) throws Exception {
        String classname = System.getProperty("java.util.logging.manager");
        if (!classname.equals("RecursiveEventHelper")) {
            throw new RuntimeException("java.util.logging.manager not set");
        }

        // this call will trigger initialization of logging framework
        // which will call into our custom LogManager and use the
        // custom getProperty method below. EventHelper.isLoggingSecurity()
        // is also on the code path of original report and triggers
        // similar recursion.
        System.getLogger("testLogger");
        if (!customMethodCalled) {
            throw new RuntimeException("Method not called");
        }
    }

    @Override
    public String getProperty(String p) {
        // this call mimics issue reported in initial bug report where
        // opening of a signed jar during System logger initialization triggered
        // a recursive call (via EventHelper.isLoggingSecurity) back into
        // logger API
        EventHelper.isLoggingSecurity();
        customMethodCalled = true;
        return super.getProperty(p);
    }
}
