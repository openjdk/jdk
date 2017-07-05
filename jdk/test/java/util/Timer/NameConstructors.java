/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4279061
 * @summary Basic test for constructors with thread name
 */

import java.util.*;

public class NameConstructors {
    private static final String NAME1 = "Norm D. Plume";
    private static final String NAME2 = "Ann Onymous";

    public static void main (String[] args) throws Exception  {
        Random rnd = new Random();
        test(new Timer(NAME1), NAME1);
        test(new Timer(NAME2, true), NAME2);
    }

    private static boolean done, passed;

    public static void test(Timer timer, final String name) throws Exception {
        done = passed = false;

        TimerTask task = new TimerTask() {
            public void run() {
                passed = Thread.currentThread().getName().equals(name);
                done = true;
            }
        };
        timer.schedule(task, 0); // Immediate
        Thread.sleep(500);
        if (!(done && passed))
            throw new RuntimeException(done + " : " + passed);
        timer.cancel();
    }
}
