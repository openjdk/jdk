/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5020931
 * @summary Unit test for Collections.checkedQueue
 */

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class CheckedQueue {
    static int status = 0;

    public static void main(String[] args) throws Exception {
        new CheckedQueue();
    }

    public CheckedQueue() throws Exception {
        run();
    }

    private void run() throws Exception {
        Method[] methods = this.getClass().getDeclaredMethods();

        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            String methodName = method.getName();

            if (methodName.startsWith("test")) {
                try {
                    Object obj = method.invoke(this, new Object[0]);
                } catch(Exception e) {
                    throw new Exception(this.getClass().getName() + "." +
                            methodName + " test failed, test exception "
                            + "follows\n" + e.getCause());
                }
            }
        }
    }

    /**
     * This test adds items to a queue.
     */
    private void test00() {
        int arrayLength = 10;
        ArrayBlockingQueue<String> abq = new ArrayBlockingQueue(arrayLength);

        for (int i = 0; i < arrayLength; i++) {
            abq.add(new String(Integer.toString(i)));
        }
    }

    /**
     * This test tests the CheckedQueue.add method.  It creates a queue of
     * {@code String}s gets the checked queue, and attempt to add an Integer to
     * the checked queue.
     */
    private void test01() throws Exception {
        int arrayLength = 10;
        ArrayBlockingQueue<String> abq = new ArrayBlockingQueue(arrayLength + 1);

        for (int i = 0; i < arrayLength; i++) {
            abq.add(new String(Integer.toString(i)));
        }

        Queue q = Collections.checkedQueue(abq, String.class);

        try {
            q.add(new Integer(0));
            throw new Exception(this.getClass().getName() + "." + "test01 test"
                    + " failed, should throw ClassCastException.");
        } catch(ClassCastException cce) {
            // Do nothing.
        }
    }

    /**
     * This test tests the CheckedQueue.add method.  It creates a queue of one
     * {@code String}, gets the checked queue, and attempt to add an Integer to
     * the checked queue.
     */
    private void test02() throws Exception {
        ArrayBlockingQueue<String> abq = new ArrayBlockingQueue(1);
        Queue q = Collections.checkedQueue(abq, String.class);

        try {
            q.add(new Integer(0));
            throw new Exception(this.getClass().getName() + "." + "test02 test"
                    + " failed, should throw ClassCastException.");
        } catch(ClassCastException e) {
            // Do nothing.
        }
    }

    /**
     * This test tests the Collections.checkedQueue method call for nulls in
     * each and both of the parameters.
     */
    private void test03() throws Exception {
        ArrayBlockingQueue<String> abq = new ArrayBlockingQueue(1);
        Queue q;

        try {
            q = Collections.checkedQueue(null, String.class);
            throw new Exception(this.getClass().getName() + "." + "test03 test"
                    + " failed, should throw NullPointerException.");
        } catch(NullPointerException npe) {
            // Do nothing
        }

        try {
            q = Collections.checkedQueue(abq, null);
            throw new Exception(this.getClass().getName() + "." + "test03 test"
                    + " failed, should throw NullPointerException.");
        } catch(Exception e) {
            // Do nothing
        }

        try {
            q = Collections.checkedQueue(null, null);
            throw new Exception(this.getClass().getName() + "." + "test03 test"
                    + " failed, should throw NullPointerException.");
        } catch(Exception e) {
            // Do nothing
        }
    }

    /**
     * This test tests the CheckedQueue.offer method.
     */
    private void test04() throws Exception {
        ArrayBlockingQueue<String> abq = new ArrayBlockingQueue(1);
        Queue q = Collections.checkedQueue(abq, String.class);

        try {
            q.offer(null);
            throw new Exception(this.getClass().getName() + "." + "test04 test"
                    + " failed, should throw NullPointerException.");
        } catch (NullPointerException npe) {
            // Do nothing
        }

        try {
            q.offer(new Integer(0));
            throw new Exception(this.getClass().getName() + "." + "test04 test"
                    + " failed, should throw ClassCastException.");
        } catch (ClassCastException cce) {
            // Do nothing
        }

        q.offer(new String("0"));

        try {
            q.offer(new String("1"));
            throw new Exception(this.getClass().getName() + "." + "test04 test"
                    + " failed, should throw IllegalStateException.");
        } catch(IllegalStateException ise) {
            // Do nothing
        }
    }

    private void test05() {

    }
}
