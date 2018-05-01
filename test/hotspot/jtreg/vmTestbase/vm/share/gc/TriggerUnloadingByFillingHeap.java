/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package vm.share.gc;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import nsk.share.TestFailure;
import nsk.share.test.ExecutionController;

public class TriggerUnloadingByFillingHeap implements TriggerUnloadingHelper {

    public void triggerUnloading(ExecutionController stresser) {
        List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        if (jvmArgs.contains("-XX:+ExplicitGCInvokesConcurrent")) {
                throw new TestFailure("Test bug! Found -XX:+ExplicitGCInvokesConcurrent in jvm args. TriggerUnloadingByFillingHeap.triggerUnloading will not work!.");
        }

        System.out.println("collections invoked: " + provokeGC(stresser));
        System.out.println("collections invoked: " + provokeGC(stresser));
        System.out.println("collections invoked: " + provokeGC(stresser));
    }

    private static long getGCCounter() {
        return ManagementFactory.getGarbageCollectorMXBeans().get(1).getCollectionCount();
    }

    private static Random random = new Random();

    public static byte[] garbage; //make it reference public to avoid compiler optimizations

    private static long provokeGC(ExecutionController stresser) {
        long initCounter = getGCCounter();
        ArrayList<byte[]> list = new ArrayList<byte[]>();
        while (getGCCounter() == initCounter && stresser.continueExecution()) {
            list.add(new byte[1024]);

            garbage = new byte[1024];
            if (random.nextInt(10) % 10 < 3 && !list.isEmpty()) {
                list.remove(0);
            }
            System.gc();
        }
        return getGCCounter() - initCounter;
    }

}
