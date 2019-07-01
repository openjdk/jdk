/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.bcel.internal.classfile;

import com.sun.org.apache.bcel.internal.Const;
import java.io.DataInput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class is derived from the abstract {@link Constant}
 * and represents a reference to a Utf8 encoded string.
 *
 * @version $Id$
 * @see     Constant
 * @LastModified: Jun 2019
 */
public final class ConstantUtf8 extends Constant {

    private final String bytes;

    // TODO these should perhaps be AtomicInt?
    private static volatile int considered = 0;
    private static volatile int hits = 0;
    private static volatile int skipped = 0;
    private static volatile int created = 0;

    // Set the size to 0 or below to skip caching entirely
    private static final int MAX_CACHED_SIZE = 200;
    private static final boolean BCEL_STATISTICS = false;


    private static class CACHE_HOLDER {

        private static final int MAX_CACHE_ENTRIES = 20000;
        private static final int INITIAL_CACHE_CAPACITY = (int)(MAX_CACHE_ENTRIES/0.75);

        private static final HashMap<String, ConstantUtf8> CACHE =
                new LinkedHashMap<String, ConstantUtf8>(INITIAL_CACHE_CAPACITY, 0.75f, true) {
            private static final long serialVersionUID = -8506975356158971766L;

            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, ConstantUtf8> eldest) {
                 return size() > MAX_CACHE_ENTRIES;
            }
        };

    }

    // for accesss by test code
    static void printStats() {
        System.err.println("Cache hit " + hits + "/" + considered +", " + skipped + " skipped");
        System.err.println("Total of " + created + " ConstantUtf8 objects created");
    }

    // for accesss by test code
    static void clearStats() {
        hits = considered = skipped = created = 0;
    }

    static {
        if (BCEL_STATISTICS) {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    printStats();
                }
            });
        }
    }

    /**
     * @since 6.0
     */
    public static ConstantUtf8 getCachedInstance(final String s) {
        if (s.length() > MAX_CACHED_SIZE) {
            skipped++;
            return  new ConstantUtf8(s);
        }
        considered++;
        synchronized (ConstantUtf8.class) { // might be better with a specific lock object
            ConstantUtf8 result = CACHE_HOLDER.CACHE.get(s);
            if (result != null) {
                    hits++;
                    return result;
                }
            result = new ConstantUtf8(s);
            CACHE_HOLDER.CACHE.put(s, result);
            return result;
        }
    }

    /**
     * @since 6.0
     */
    public static ConstantUtf8 getInstance(final String s) {
        return new ConstantUtf8(s);
    }

    /**
     * @since 6.0
     */
    public static ConstantUtf8 getInstance (final DataInput input)  throws IOException {
        return getInstance(input.readUTF());
    }

    /**
     * Initialize from another object.
     */
    public ConstantUtf8(final ConstantUtf8 c) {
        this(c.getBytes());
    }


    /**
     * Initialize instance from file data.
     *
     * @param file Input stream
     * @throws IOException
     */
    ConstantUtf8(final DataInput file) throws IOException {
        super(Const.CONSTANT_Utf8);
        bytes = file.readUTF();
        created++;
    }


    /**
     * @param bytes Data
     */
    public ConstantUtf8(final String bytes) {
        super(Const.CONSTANT_Utf8);
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must not be null!");
        }
        this.bytes = bytes;
        created++;
    }


    /**
     * Called by objects that are traversing the nodes of the tree implicitely
     * defined by the contents of a Java class. I.e., the hierarchy of methods,
     * fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept( final Visitor v ) {
        v.visitConstantUtf8(this);
    }


    /**
     * Dump String in Utf8 format to file stream.
     *
     * @param file Output file stream
     * @throws IOException
     */
    @Override
    public final void dump( final DataOutputStream file ) throws IOException {
        file.writeByte(super.getTag());
        file.writeUTF(bytes);
    }


    /**
     * @return Data converted to string.
     */
    public final String getBytes() {
        return bytes;
    }


    /**
     * @param bytes the raw bytes of this Utf-8
     * @deprecated (since 6.0)
     */
    @java.lang.Deprecated
    public final void setBytes( final String bytes ) {
        throw new UnsupportedOperationException();
    }


    /**
     * @return String representation
     */
    @Override
    public final String toString() {
        return super.toString() + "(\"" + Utility.replace(bytes, "\n", "\\n") + "\")";
    }
}
