/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @key stress randomness
 *
 * @summary converted from VM Testbase gc/gctests/WeakReference/weak006.
 * VM Testbase keywords: [gc, stress, stressopt, nonconcurrent]
 *
 * @library /vmTestbase
 *          /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+IgnoreUnrecognizedVMOptions gc.gctests.WeakReference.weak006.weak006 -t 1
 */

package gc.gctests.WeakReference.weak006;

import jdk.test.whitebox.WhiteBox;
import nsk.share.test.*;
import nsk.share.gc.*;
import nsk.share.TestFailure;
import java.lang.ref.WeakReference;
import java.lang.ref.SoftReference;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;

/**
 * Test that GC correctly clears references.
 *
 * This test randomly creates a number of weak, soft,
 * phantom and strong references,  each of which points
 * to the next, then provokes GC with WB.fullGC().
 * The test succedes if last reference has been cleared.
 */

class MySoftReference<T> extends SoftReference<T> {
    public MySoftReference(T obj) {
        super(obj);
    }
}


class MyWeakReference<T> extends WeakReference<T> {
    public MyWeakReference(T obj) {
        super(obj);
    }
}

public class weak006 extends ThreadedGCTest {

    class Worker implements Runnable {

        private int length;
        private int objectSize = 100;
        private Object[] references;
        private Reference lastReference;

        private Reference<Object> makeReference(int n, Object o) {
            switch (n) {
                case 0:
                    return new MyWeakReference(o);
                case 1:
                    return new MySoftReference(o);
                default:
                    throw new RuntimeException("Incorrect reference type");
            }
        }

        private void clear() {
            lastReference = null;
            references[length - 1] = null;
        }

        private void makeReferences(int n) {
            clear();
            MemoryObject obj = new MemoryObject(objectSize);
            references[0] = new MyWeakReference(obj);
            for (int i = 1; i < length; ++i) {
                if (i != length - 1) {
                    references[i] = makeReference(LocalRandom.nextInt(2), references[i - 1]);
                } else {
                    lastReference = makeReference(n, references[i - 1]);
                    references[i] = lastReference;
                }
            }
            for (int i = 0; i < length; ++i) {
                references[i] = null;
            }
        }

        public void run() {
            makeReferences(0);
            ExecutionController stresser = getExecutionController();
            WhiteBox.getWhiteBox().fullGC();
            if (!stresser.continueExecution()) {
                return;
            }
            if (lastReference.get() != null) {
                references = null;
                throw new TestFailure("Last weak reference has not been cleared");
            }
            makeReferences(1);
            WhiteBox.getWhiteBox().fullGC();
            if (!stresser.continueExecution()) {
                return;
            }
            if (lastReference.get() != null) {
                references = null;
                throw new TestFailure("Last soft reference has not been cleared");
            }
        }

        public Worker() {
            length = Memory.getArrayLength(runParams.getTestMemory() - objectSize, Memory.getReferenceSize() + objectSize);
            System.out.println("Array size: " + length);
            references = new Object[length];
        }
    }

    @Override
    protected Runnable createRunnable(int i) {
        return new Worker();
    }

    public static void main(String[] args) {
        GC.runTest(new weak006(), args);
    }
}
