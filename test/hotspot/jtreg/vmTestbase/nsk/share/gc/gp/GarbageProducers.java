/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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

package nsk.share.gc.gp;

import java.util.List;
import java.util.ArrayList;

import nsk.share.gc.Memory;
import nsk.share.gc.gp.array.*;
import nsk.share.gc.gp.string.*;
import nsk.share.test.LocalRandom;

/**
 * Factory for garbage producers
 */
public class GarbageProducers {
        private List<GarbageProducer> primitiveArrayProducers;
        private List<GarbageProducer> valueArrayProducers;
        private List<GarbageProducer> arrayProducers;
        private List<GarbageProducer<String>> stringProducers;
        private List<GarbageProducer> allProducers;

        /**
         * Get all primitive array producers.
         */
        public List<GarbageProducer> getPrimitiveArrayProducers() {
                if (primitiveArrayProducers == null) {
                        primitiveArrayProducers = new ArrayList<GarbageProducer>();
                        primitiveArrayProducers.add(new ByteArrayProducer());
                        primitiveArrayProducers.add(new BooleanArrayProducer());
                        primitiveArrayProducers.add(new ShortArrayProducer());
                        primitiveArrayProducers.add(new CharArrayProducer());
                        primitiveArrayProducers.add(new IntArrayProducer());
                        primitiveArrayProducers.add(new LongArrayProducer());
                        primitiveArrayProducers.add(new FloatArrayProducer());
                        primitiveArrayProducers.add(new DoubleArrayProducer());
                }
                return primitiveArrayProducers;
        }

        /**
         * Get all primitive array producers.
         */
        public List<GarbageProducer> getValueArrayProducers() {
            if (valueArrayProducers == null) {
                valueArrayProducers = new ArrayList<GarbageProducer>();
                valueArrayProducers.add(new ByteObjArrayProducer());
                valueArrayProducers.add(new BooleanObjArrayProducer());
                valueArrayProducers.add(new IntegerObjArrayProducer());
            }
            return primitiveArrayProducers;
        }
        /**
         * Get all array producers.
         */
        public List<GarbageProducer> getArrayProducers() {
                if (arrayProducers == null) {
                        arrayProducers = new ArrayList<GarbageProducer>();
                        arrayProducers.addAll(getPrimitiveArrayProducers());
                        arrayProducers.add(new ObjectArrayProducer());
                        if (Memory.isValhallaEnabled()) {
                            arrayProducers.addAll(getValueArrayProducers());
                        }
                }
                return arrayProducers;
        }

        /**
         * Get all string producers.
         */
        public List<GarbageProducer<String>> getStringProducers() {
                if (stringProducers == null) {
                        stringProducers = new ArrayList<GarbageProducer<String>>();
                        stringProducers.add(new RandomStringProducer());
                        stringProducers.add(new InternedStringProducer());
                }
                return stringProducers;
        }

        public List<GarbageProducer> getAllProducers() {
                if (allProducers == null) {
                        allProducers = new ArrayList<GarbageProducer>();
                        allProducers.addAll(getArrayProducers());
                        allProducers.addAll(getStringProducers());
                }
                return allProducers;
        }
}

 class IntegerObjArrayProducer implements GarbageProducer<Integer[]> {
    public Integer[] create(long memory) {
        int size = Memory.getIntegerArrayElementSize();
        if (!Memory.isValhallaEnabled()) {
            // Let assume that every Integer is new object
            size += Memory.getObjectExtraSize();
        }
        Integer[] arr = new Integer[Memory.getArrayLength(memory, size)];
        LocalRandom.nextInts(arr);
        return arr;
    }

    public void validate(Integer[] arr) {
        LocalRandom.validate(arr);
    }
}


class ByteObjArrayProducer implements GarbageProducer<Byte[]> {
    public Byte[] create(long memory) {
        Byte[] arr = new Byte[Memory.getArrayLength(memory, Memory.getByteArrayElementSize())];
        LocalRandom.nextBytes(arr);
        return arr;
    }

    public void validate(Byte[] arr) {
        LocalRandom.validate(arr);
    }
}

class BooleanObjArrayProducer implements GarbageProducer<Boolean[]> {
    public Boolean[] create(long memory) {
        Boolean[] arr = new Boolean[Memory.getArrayLength(memory, Memory.getBooleanArrayElementSize())];
        LocalRandom.nextBooleans(arr);
        return arr;
    }

    public void validate(Boolean[] arr) {
        LocalRandom.validate(arr);
    }
}