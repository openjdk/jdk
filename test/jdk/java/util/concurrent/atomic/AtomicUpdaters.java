/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7103570 8189291
 * @author David Holmes
 * @run main/othervm AtomicUpdaters
 * @summary Checks the (in)ability to create field updaters for differently
 *          accessible fields in different locations
 */

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class  AtomicUpdaters {
    enum TYPE { INT, LONG, REF }

    static class Config {
        final Class<?> clazz;
        final String field;
        final String access;
        final boolean updaterOk;
        final String desc;
        final TYPE type;

        Config(Class<?> clazz, String field, String access,
               boolean updaterOk, String desc, TYPE type) {
            this.clazz = clazz;
            this.field = field;
            this.access = access;
            this.updaterOk = updaterOk;
            this.desc = desc;
            this.type =type;
        }

        public String toString() {
            return desc + ": " + access + " " + clazz.getName() + "." + field;
        }
    }

    static Config[] tests;

    static void initTests() {
        tests = new Config[] {
            new Config(AtomicUpdaters.class, "pub_int", "public", true, "public int field of current class", TYPE.INT),
            new Config(AtomicUpdaters.class, "priv_int", "private", true, "private int field of current class", TYPE.INT),
            new Config(AtomicUpdaters.class, "pub_long", "public", true, "public long field of current class", TYPE.LONG),
            new Config(AtomicUpdaters.class, "priv_long", "private", true, "private long field of current class", TYPE.LONG),
            new Config(AtomicUpdaters.class, "pub_ref", "public", true, "public ref field of current class", TYPE.REF),
            new Config(AtomicUpdaters.class, "priv_ref", "private", true, "private ref field of current class", TYPE.REF),

            // Would like to test a public volatile in a class in another
            // package - but of course there aren't any
            new Config(AtomicInteger.class, "value", "private", false, "private int field of class in different package", TYPE.INT),
            new Config(AtomicLong.class, "value", "private", false, "private long field of class in different package", TYPE.LONG),
            new Config(AtomicReference.class, "value", "private", false, "private reference field of class in different package", TYPE.REF),
        };
    }

    public volatile int pub_int;
    private volatile int priv_int;
    public volatile long pub_long;
    private volatile long priv_long;
    public volatile Object pub_ref;
    private volatile Object priv_ref;


    // run with -v
    static boolean verbose;

    public static void main(String[] args) throws Throwable {
        for (String arg : args) {
            if ("-v".equals(arg)) {
                verbose = true;
            }
            else {
                throw new IllegalArgumentException("Unexpected option: " + arg);
            }
        }
        initTests();

        int failures = 0;


        for (Config c : tests) {
            System.out.println("Testing: " + c);
            Error updaterFailure = null;
            Class<?> clazz = c.clazz;
            // See if we can reflectively access the field
            System.out.println(" - testing getDeclaredField");

            Field f = clazz.getDeclaredField(c.field);

            // see if we can create an atomic updater for the field
            Object u = null;
            try {
                switch (c.type) {
                case INT:
                    System.out.println(" - testing AtomicIntegerFieldUpdater");
                    u = AtomicIntegerFieldUpdater.newUpdater(clazz, c.field);
                    break;
                case LONG:
                    System.out.println(" - testing AtomicLongFieldUpdater");
                    u = AtomicLongFieldUpdater.newUpdater(clazz, c.field);
                    break;
                case REF:
                    System.out.println(" - testing AtomicReferenceFieldUpdater");
                    u = AtomicReferenceFieldUpdater.newUpdater(clazz, Object.class, c.field);
                    break;
                }

                if (!c.updaterOk)
                    updaterFailure =  new Error("Unexpected updater access: " + c);
            }
            catch (Exception e) {
                if (c.updaterOk)
                    updaterFailure = new Error("Unexpected updater access failure: " + c, e);
                else if (verbose) {
                    System.out.println("Got expected updater exception: " + e);
                    e.printStackTrace(System.out);
                }
            }

            if (updaterFailure != null) {
                updaterFailure.printStackTrace(System.out);
                failures++;
            }
        }

        if (failures > 0) {
            throw new Error("Some tests failed - see previous stacktraces");
        }
    }
}
