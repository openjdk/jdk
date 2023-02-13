/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.DataInput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.sun.org.apache.bcel.internal.Const;

/**
 * Extends the abstract {@link Constant} to represent a reference to a UTF-8 encoded string.
 * <p>
 * The following system properties govern caching this class performs.
 * </p>
 * <ul>
 * <li>{@value #SYS_PROP_CACHE_MAX_ENTRIES} (since 6.4): The size of the cache, by default 0, meaning caching is
 * disabled.</li>
 * <li>{@value #SYS_PROP_CACHE_MAX_ENTRY_SIZE} (since 6.0): The maximum size of the values to cache, by default 200, 0
 * disables caching. Values larger than this are <em>not</em> cached.</li>
 * <li>{@value #SYS_PROP_STATISTICS} (since 6.0): Prints statistics on the console when the JVM exits.</li>
 * </ul>
 * <p>
 * Here is a sample Maven invocation with caching disabled:
 * </p>
 *
 * <pre>
 * mvn test -Dbcel.statistics=true -Dbcel.maxcached.size=0 -Dbcel.maxcached=0
 * </pre>
 * <p>
 * Here is a sample Maven invocation with caching enabled:
 * </p>
 *
 * <pre>
 * mvn test -Dbcel.statistics=true -Dbcel.maxcached.size=100000 -Dbcel.maxcached=5000000
 * </pre>
 *
 * @see Constant
 * @LastModified: Feb 2023
 */
public final class ConstantUtf8 extends Constant {

    private static class Cache {

        private static final boolean BCEL_STATISTICS = false;
        private static final int MAX_ENTRIES = 20000;
        private static final int INITIAL_CAPACITY = (int) (MAX_ENTRIES / 0.75);

        private static final HashMap<String, ConstantUtf8> CACHE = new LinkedHashMap<String, ConstantUtf8>(INITIAL_CAPACITY, 0.75f, true) {

            private static final long serialVersionUID = -8506975356158971766L;

            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, ConstantUtf8> eldest) {
                return size() > MAX_ENTRIES;
            }
        };

        // Set the size to 0 or below to skip caching entirely
        private static final int MAX_ENTRY_SIZE = 200;

        static boolean isEnabled() {
            return Cache.MAX_ENTRIES > 0 && MAX_ENTRY_SIZE > 0;
        }

    }

    // TODO these should perhaps be AtomicInt?
    private static volatile int considered;
    private static volatile int created;
    private static volatile int hits;
    private static volatile int skipped;

    private static final String SYS_PROP_CACHE_MAX_ENTRIES = "bcel.maxcached";
    private static final String SYS_PROP_CACHE_MAX_ENTRY_SIZE = "bcel.maxcached.size";
    private static final String SYS_PROP_STATISTICS = "bcel.statistics";

    static {
        if (Cache.BCEL_STATISTICS) {
            Runtime.getRuntime().addShutdownHook(new Thread(ConstantUtf8::printStats));
        }
    }

    /**
     * Clears the cache.
     *
     * @since 6.4.0
     */
    public static synchronized void clearCache() {
        Cache.CACHE.clear();
    }

    // for access by test code
    static synchronized void clearStats() {
        hits = considered = skipped = created = 0;
    }

    /**
     * Gets a new or cached instance of the given value.
     * <p>
     * See {@link ConstantUtf8} class Javadoc for details.
     * </p>
     *
     * @param value the value.
     * @return a new or cached instance of the given value.
     * @since 6.0
     */
    public static ConstantUtf8 getCachedInstance(final String value) {
        if (value.length() > Cache.MAX_ENTRY_SIZE) {
            skipped++;
            return new ConstantUtf8(value);
        }
        considered++;
        synchronized (ConstantUtf8.class) { // might be better with a specific lock object
            ConstantUtf8 result = Cache.CACHE.get(value);
            if (result != null) {
                hits++;
                return result;
            }
            result = new ConstantUtf8(value);
            Cache.CACHE.put(value, result);
            return result;
        }
    }

    /**
     * Gets a new or cached instance of the given value.
     * <p>
     * See {@link ConstantUtf8} class Javadoc for details.
     * </p>
     *
     * @param dataInput the value.
     * @return a new or cached instance of the given value.
     * @throws IOException if an I/O error occurs.
     * @since 6.0
     */
    public static ConstantUtf8 getInstance(final DataInput dataInput) throws IOException {
        return getInstance(dataInput.readUTF());
    }

    /**
     * Gets a new or cached instance of the given value.
     * <p>
     * See {@link ConstantUtf8} class Javadoc for details.
     * </p>
     *
     * @param value the value.
     * @return a new or cached instance of the given value.
     * @since 6.0
     */
    public static ConstantUtf8 getInstance(final String value) {
        return Cache.isEnabled() ? getCachedInstance(value) : new ConstantUtf8(value);
    }

    // for access by test code
    static void printStats() {
        final String prefix = "[Apache Commons BCEL]";
        System.err.printf("%s Cache hit %,d/%,d, %d skipped.%n", prefix, hits, considered, skipped);
        System.err.printf("%s Total of %,d ConstantUtf8 objects created.%n", prefix, created);
        System.err.printf("%s Configuration: %s=%,d, %s=%,d.%n", prefix, SYS_PROP_CACHE_MAX_ENTRIES, Cache.MAX_ENTRIES, SYS_PROP_CACHE_MAX_ENTRY_SIZE,
            Cache.MAX_ENTRY_SIZE);
    }

    private final String value;

    /**
     * Initializes from another object.
     *
     * @param constantUtf8 the value.
     */
    public ConstantUtf8(final ConstantUtf8 constantUtf8) {
        this(constantUtf8.getBytes());
    }

    /**
     * Initializes instance from file data.
     *
     * @param dataInput Input stream
     * @throws IOException if an I/O error occurs.
     */
    ConstantUtf8(final DataInput dataInput) throws IOException {
        super(Const.CONSTANT_Utf8);
        value = dataInput.readUTF();
        created++;
    }

    /**
     * @param value Data
     */
    public ConstantUtf8(final String value) {
        super(Const.CONSTANT_Utf8);
        this.value = Objects.requireNonNull(value, "value");
        created++;
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * I.e., the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        v.visitConstantUtf8(this);
    }

    /**
     * Dumps String in Utf8 format to file stream.
     *
     * @param file Output file stream
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void dump(final DataOutputStream file) throws IOException {
        file.writeByte(super.getTag());
        file.writeUTF(value);
    }

    /**
     * @return Data converted to string.
     */
    public String getBytes() {
        return value;
    }

    /**
     * @param bytes the raw bytes of this UTF-8
     * @deprecated (since 6.0)
     */
    @java.lang.Deprecated
    public void setBytes(final String bytes) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return String representation
     */
    @Override
    public String toString() {
        return super.toString() + "(\"" + Utility.replace(value, "\n", "\\n") + "\")";
    }
}
