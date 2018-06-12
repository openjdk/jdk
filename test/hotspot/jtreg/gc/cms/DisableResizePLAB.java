/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @test DisableResizePLAB
 * @key gc
 * @bug 8060467
 * @author filipp.zhinkin@oracle.com, john.coomes@oracle.com
 * @requires vm.gc.ConcMarkSweep & !vm.graal.enabled
 * @summary Run CMS with PLAB resizing disabled and a small OldPLABSize
 * @run main/othervm -XX:+UseConcMarkSweepGC -XX:-ResizePLAB -XX:OldPLABSize=1k -Xmx256m -Xlog:gc=debug DisableResizePLAB
 */

public class DisableResizePLAB {
    public static void main(String args[]) throws Exception {
        Object garbage[] = new Object[1_000];
        for (int i = 0; i < garbage.length; i++) {
            garbage[i] = new byte[0];
        }
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 10_000) {
            Object o = new byte[1024];
        }
    }
}
