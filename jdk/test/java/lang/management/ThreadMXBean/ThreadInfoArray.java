/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug     5058327
 * @summary Test if getThreadInfo(long[]) returns a ThreadInfo[]
 *          with null elements with no exception.
 *
 * @author  Mandy Chung
 *
 * @build ThreadInfoArray
 * @run main ThreadInfoArray
 */

import java.lang.management.*;
import javax.management.*;
import java.util.*;
import static java.lang.management.ManagementFactory.*;

public class ThreadInfoArray {
    public static void main(String[] argv) throws Exception {
        ThreadMXBean mbean = ManagementFactory.getThreadMXBean();

        // ID for a new thread
        long [] ids = {new Thread().getId()};
        ThreadInfo[] tinfos = mbean.getThreadInfo(ids);

        if (tinfos[0] != null) {
            throw new RuntimeException("TEST FAILED: " +
                "Expected to have a null element");
        }

        // call getThreadInfo through MBeanServer
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName on = new ObjectName(THREAD_MXBEAN_NAME);
        Object[] params = {ids};
        String[] sigs = {"[J"};
        Object[] result = (Object[]) mbs.invoke(on, "getThreadInfo", params, sigs);

        if (result[0] != null) {
            throw new RuntimeException("TEST FAILED: " +
                "Expected to have a null element via MBeanServer");
        }

        // call getThreadInfo through proxy
        ThreadMXBean proxy = newPlatformMXBeanProxy(mbs,
                                 on.toString(),
                                 ThreadMXBean.class);
        tinfos = proxy.getThreadInfo(ids);
        if (tinfos[0] != null) {
            throw new RuntimeException("TEST FAILED: " +
                "Expected to have a null element");
        }
        System.out.println("Test passed");
    }
}
