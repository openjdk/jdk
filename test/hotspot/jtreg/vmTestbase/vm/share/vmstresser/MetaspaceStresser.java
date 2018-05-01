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
package vm.share.vmstresser;

import java.util.*;
import java.util.concurrent.locks.*;

import nsk.share.*;
import nsk.share.classload.*;
import nsk.share.test.*;

/**
 * Stresser that load classes until OOME, then unload some of them and continue loading.
 */
public class MetaspaceStresser extends Thread {

    /**
     * Capacity of class containers.
     * This amount of classes will be unloaded on reset call.
     */
    public static final int DEFAULT_BUCKET_SIZE = 4000;

    public static final int DEFAULT_PAUSE_TIME = 0;

    /*
     * Loaded classes stored in ClassContainer instances.
     * Such instances organized in array-based stack as it is
     * one of the simplest way to minimize possibility
     * to get OOME and guarntee that after replacing
     * reference to class container by null there will be
     * no cached refereces and container will be reclaimed by
     * GC and classes will become unloadable.
     */
    // Maximum available amount of arrays with class containers.
    private static final int CONTAINERS_ARRAY_LENGTH = 1000;
    // Maximum length array with class containers.
    private static final int CONTAINER_ARRAYS_COUNT = 100;

    private ClassContainersStack containersStack = new ClassContainersStack(CONTAINER_ARRAYS_COUNT * CONTAINERS_ARRAY_LENGTH,
            CONTAINERS_ARRAY_LENGTH);
    private ClassContainer newContainer = null;

    private ExecutionController controller = null;
    private int bucketSize = DEFAULT_BUCKET_SIZE;
    private int pauseTime = DEFAULT_PAUSE_TIME;

    private ReentrantLock lock = new ReentrantLock();

    /**
     * Construct MetaspaceStrresser with default bucket size
     * and pause time.
     * @param c controller to control execution time.
     */
    public MetaspaceStresser(ExecutionController c) {
        controller = c;
    }

    /**
     * Construct MetaspaceStrresser with custom bucket size
     * and pause time.
     * @param c controller to control execution time.
     * @param bucketSize classes to be unloaded on reset.
     * @param pauseTime pause after reset.
     */
    public MetaspaceStresser(ExecutionController c, int bucketSize, int pauseTime) {
        this(c);
        this.bucketSize = bucketSize;
        this.pauseTime = pauseTime;
    }

    /**
     *  Fill Metaspace with classes.
     *  Classes will be loaded until OOME, then some of them will be unloaded.
     */
    public synchronized void prepare() {
        while (controller.continueExecution()) {
            try {
                fillContainerStack();
            } catch (OutOfMemoryError oome) {
                unloadLastClassBucket();
                return;
            } catch (ClassNotFoundException cnfe) {
                throw new TestBug("Unexpected exception in stresser.", cnfe);
            }
        }
    }

    /**
     * Load new class to container, fill containerStack.
     * Classes will be loaded until OOME
     * @throws ClassNotFoundException
     */
    private void fillContainerStack() throws ClassNotFoundException {
        newContainer = new ClassContainer();
        while (newContainer.size() < bucketSize && controller.continueExecution()) {
            newContainer.loadClass();
        }
        containersStack.push(newContainer);
        newContainer = null;
    }

    /**
     * Run stresser.
     * Stresser will load classes until OOME, then bucketSize classes
     * will be unloaded and stresser will wait pauseTime millisiconds
     * before continuing class loading.
     */
    public void run() {
        try {
            while (controller.continueExecution()) {
                try {
                    fillContainerStack();
                } catch (OutOfMemoryError oome) {
                    unloadLastClassBucket();
                    try {
                        Thread.sleep(pauseTime);
                    } catch (InterruptedException ie) {
                    }
                }
            }
        } catch (Throwable e) {
            throw new TestBug("Unexpected exception in stresser.", e);
        } finally {
            containersStack.free();
        }
    }

    /**
     * Unload most recently loaded bucket of classes.
     */
    public void unloadLastClassBucket() {
        while (controller.continueExecution()) {
            try {
                containersStack.pop();
                System.gc();
                break;
            } catch (OutOfMemoryError oome) {
                oome.printStackTrace();
                continue;
            }
        }
    }

    /**
     * Array-based stack for ClassContainer's.
     */
    private class ClassContainersStack {

        private int arrayLength = 0;
        private int arraysCount = 0;
        private int arrayIndex = 0;
        private int elemIndex = 0;

        private ClassContainer data[][];

        /**
         * Create ClassContainersStack that will be able
         * to store size classes in arrays of segmentSize length.
         */
        public ClassContainersStack(int size, int segementSize) {
            arrayLength = segementSize;
            arraysCount = size / arrayLength;
            data = new ClassContainer[arraysCount][];
            data[0] = new ClassContainer[arrayLength];
        }

        /**
         * Push ClassContainer c into stack.
         */
        public synchronized void push(ClassContainer c) {
            data[arrayIndex][elemIndex] = c;
            elemIndex++;
            if (elemIndex == arrayLength) {
                if (arrayIndex == arraysCount) {
                    throw new TestBug("ClassContainersStack ran out of available slots");
                }
                data[arrayIndex + 1] = new ClassContainer[arrayLength];
                arrayIndex++;
                elemIndex = 0;
            }
        }

        /**
         * Remove reference to top ClassContainer.
         */
        public synchronized void pop() {
            data[arrayIndex][elemIndex] = null;
            if (elemIndex > 0) {
                elemIndex--;
            } else if (arrayIndex > 0) {
                data[arrayIndex] = null;
                arrayIndex--;
                elemIndex = arrayLength - 1;
            }
        }

        /**
         * Remove all stored ClassContainers.
         */
        public synchronized void free() {
            data = null;
            System.gc();
            data = new ClassContainer[arraysCount][];
            data[0] = new ClassContainer[arrayLength];
            arrayIndex = 0;
            elemIndex = 0;
        }

    }

    /// Variable used to create uniqe name for generated classes.
    private static long lastClass = 0;

    /**
     * Class container consists of classes and their ClassLoader, so
     * if there will be no references to container and classes inside it then
     * it could be easely collected by GC.
     */
    private class ClassContainer {

        private List<Class> classes = new LinkedList<Class>();
        private GeneratingClassLoader loader = new GeneratingClassLoader();
        private String prefix = loader.getPrefix();
        private int length = loader.getNameLength();

        public void loadClass() throws ClassNotFoundException {
            String newName = prefix + "c" + lastClass;
            lastClass++;
            while (newName.length() < length) {
                newName = newName + "c";
            }
            classes.add(loader.loadClass(newName));
        }

        public int size() {
            return classes.size();
        }
    }

}
