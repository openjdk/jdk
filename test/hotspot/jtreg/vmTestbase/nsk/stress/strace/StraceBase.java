/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package nsk.stress.strace;

import nsk.share.Log;

public class StraceBase {

    private static final String[] EXPECTED_SYSTEM_CLASSES = {
            "java.lang.ClassLoader",
            "java.lang.System",
            "java.lang.Object",
            "java.lang.Thread",
            "jdk.internal.event.Event",
            "jdk.internal.event.ThreadSleepEvent",
            "jdk.internal.misc.Blocker",
            "jdk.internal.misc.VM",
            "jdk.internal.vm.StackableScope",
    };

    /**
     *   Method verifies that StackTraceElement is sane and might be expected in the current stack.
     */
    final static boolean checkElement(StackTraceElement element) {
        String className = element.getClassName();
        String methodName = element.getMethodName();
        if (className.matches("nsk.stress.strace.strace\\d\\d\\dThread")) {
            if (methodName.matches("recursiveMethod\\d?")
                    || methodName.equals("run")) {
                return true;
            }
            return false;
        }
        for (var systemClassName : EXPECTED_SYSTEM_CLASSES) {
            if (className.equals(systemClassName))
                return true;
        }
        return false;
    }

    static final long waitTime = 2 * 60000;

    private static final Log log = new Log(System.out);

    static void display(String message) {
        log.display(message);
    }

    static void complain(String message) {
        log.complain(message);
    }

}
