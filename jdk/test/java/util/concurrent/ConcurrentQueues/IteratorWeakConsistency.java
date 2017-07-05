/*
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

import java.util.*;
import java.util.concurrent.*;

/*
 * @test
 * @bug 6805775 6815766
 * @summary Check weak consistency of concurrent queue iterators
 */

@SuppressWarnings({"unchecked", "rawtypes"})
public class IteratorWeakConsistency {

    void test(String[] args) throws Throwable {
        test(new LinkedBlockingQueue());
        test(new LinkedBlockingQueue(20));
        test(new LinkedBlockingDeque());
        test(new LinkedBlockingDeque(20));
        test(new ConcurrentLinkedDeque());
        test(new ConcurrentLinkedQueue());
        test(new LinkedTransferQueue());
        // Other concurrent queues (e.g. ArrayBlockingQueue) do not
        // currently have weakly consistent iterators.
        // test(new ArrayBlockingQueue(20));
    }

    void test(Queue q) {
        // TODO: make this more general
        try {
            for (int i = 0; i < 10; i++)
                q.add(i);
            Iterator it = q.iterator();
            q.poll();
            q.poll();
            q.poll();
            q.remove(7);
            List list = new ArrayList();
            while (it.hasNext())
                list.add(it.next());
            equal(list, Arrays.asList(0, 3, 4, 5, 6, 8, 9));
            check(! list.contains(null));
            System.out.printf("%s: %s%n",
                              q.getClass().getSimpleName(),
                              list);
        } catch (Throwable t) { unexpected(t); }

        try {
            q.clear();
            q.add(1);
            q.add(2);
            q.add(3);
            q.add(4);
            Iterator it = q.iterator();
            it.next();
            q.remove(2);
            q.remove(1);
            q.remove(3);
            boolean found4 = false;
            while (it.hasNext()) {
                found4 |= it.next().equals(4);
            }
            check(found4);
        } catch (Throwable t) { unexpected(t); }

    }

    //--------------------- Infrastructure ---------------------------
    volatile int passed = 0, failed = 0;
    void pass() {passed++;}
    void fail() {failed++; Thread.dumpStack();}
    void fail(String msg) {System.err.println(msg); fail();}
    void unexpected(Throwable t) {failed++; t.printStackTrace();}
    void check(boolean cond) {if (cond) pass(); else fail();}
    void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        new IteratorWeakConsistency().instanceMain(args);}
    public void instanceMain(String[] args) throws Throwable {
        try {test(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
