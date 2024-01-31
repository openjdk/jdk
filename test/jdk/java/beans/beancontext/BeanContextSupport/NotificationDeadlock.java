/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.beancontext.BeanContextSupport;
import java.util.concurrent.TimeUnit;

/**
 * @test
 * @bug 8238170
 * @summary Test for a possible deadlock in the BeanContextSupport
 */
public final class NotificationDeadlock {

    private static volatile long endtime;

    public static void main(String[] args) throws Exception {
        // Will run the test no more than 5 seconds
        endtime = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        BeanContextSupport bcs = new BeanContextSupport();
        Thread add = new Thread(() -> {
            while (!isComplete()) {
                bcs.add(bcs);
            }
        });
        Thread remove = new Thread(() -> {
            while (!isComplete()) {
                Object[] objects = bcs.toArray();
                for (Object object : objects) {
                    bcs.remove(object);
                }
            }
        });
        Thread props = new Thread(() -> {
            while (!isComplete()) {
                Object[] objects = bcs.toArray();
                for (Object object : objects) {
                    PropertyChangeEvent beanContext = new PropertyChangeEvent(
                            object, "beanContext", object, null);
                    try {
                        bcs.vetoableChange(beanContext);
                    } catch (PropertyVetoException ignore) {
                    }
                    bcs.propertyChange(beanContext);
                }
            }
        });
        add.start();
        remove.start();
        props.start();
        add.join();
        remove.join();
        props.join();
    }

    private static boolean isComplete() {
        return endtime - System.nanoTime() < 0;
    }
}
