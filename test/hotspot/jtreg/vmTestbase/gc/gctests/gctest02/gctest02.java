/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
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
//gctest02.java


/*
 * @test
 * @key gc
 *
 * @summary converted from VM Testbase gc/gctests/gctest02.
 * VM Testbase keywords: [gc]
 *
 * @library /vmTestbase
 *          /test/lib
 * @run driver jdk.test.lib.FileInstaller . .
 * @run main/othervm gc.gctests.gctest02.gctest02 100
 */

package gc.gctests.gctest02;

import nsk.share.TestFailure;
import nsk.share.TestBug;
/*  stress testing
 create 16 memory evil threads requesting to allocate
 the object of sizes from 8 to ( 2 ^ 19).
 The live time of objects is random (0 ~ 1000).
 Here we let the threads that reference the objects
 to simulate the object life time.
*/

import java.util.Random;

class PopulationException extends Exception {
    //this exception is used to signal that we've
    //reached the end of the test
}

//the LocalRandom class is used to isolate the pseudo-random
//number generator from other parts of the system which might
//silently be using it.
//This is to make sure the tests are repeatable

class LocalRandom {
    public static Random rGen = null;

    public static double random() {
        //should fail if rGen is not initialized
        return rGen.nextDouble();
    }
}

class ThreadCount {
    static int count= 0;
    static synchronized void inc() { count++; }
    static synchronized void dec() { count --; }
    static synchronized int get() { return count; }
}

class Person {
        String name;
        int     ssid;
        int     age;
        int     buf[];
        int     bufsz;
        static int populationCount = 0;
        static int populationLimit = 0;

        Person(String n, int ssid, int age, int bufsz)
        throws PopulationException {
                name = n;
                this.ssid = ssid;
                this.age = age;
                if ( bufsz > 0 ) {
                        this.bufsz = bufsz;
                        this.buf = new int[bufsz];
                }
                incPopulation();
                if (getPopulation() > getPopulationLimit()) {
                        throw new PopulationException();
                }
        }
        public static synchronized int getPopulationLimit() {
                return populationLimit;
        }
        public static synchronized void setPopulationLimit(int newLimit) {
                populationLimit = newLimit;
        }
        public static synchronized int getPopulation() {
                return populationCount;
        }
        public static synchronized void incPopulation() {
                populationCount ++;
        }

}

// hr (humane resource) dept is using objects.
// Put the hr thread to sleep to keep the reference to objects
class hr extends Thread {
        Person pp;
        int lifetime;

        hr(Person p, int l) {
                pp = p;
                lifetime = l;
        }

        public void run() {
                // just sleep to emulate the life time of object referenced by p
                try { sleep(lifetime); }
                catch (InterruptedException e) {}
        }
}

class Memevil extends Thread {
        int sum;
        int bufsz = 64;
        boolean debug = false;

        Memevil(int bufsz) {
                sum = 0;
                this.bufsz = bufsz;
        }
        /*      Person object is live short, it will be garbage after
         *      control returns
         */
        private boolean doit() {
                try {
                        Person p = new Person("Duke", 100, 100, bufsz);
                        hr useit = new hr(p, (int)(100*LocalRandom.random()));
                        useit.start();
                        return true;
                }
                catch (PopulationException e) {
                        return false;
                }
                catch (OutOfMemoryError e ) {
                        System.err.println(getName() + ": Out of Memory");
                        return false;
                }
        }
        public void run() {
                while ( doit() ) {
                        if ( LocalRandom.random() > 0.6668) {
                                try {
                                        sleep(10);   // to be nice
                                }
                                catch (InterruptedException e) {
                                }
                        }
                        Thread.yield();
                }
                //we've reached the population limit, so we're exiting the thread
                ThreadCount.dec();
        }
}

class Escaper extends Thread {
        public void run() {
                while ( ThreadCount.get() > 0 ) {
                        int buf[] = new int[32];
                        {
                                                yield();
                        }
                }
        }
}

public class gctest02 {
        public static void main(String args[] ) {
                int bufsz = 8;
                int peopleLimit = 1000;
                long randomSeed = System.currentTimeMillis();
                Memevil me=null;
                if (args.length > 0)
                {
                        try
                        {
                                peopleLimit = new Integer(args[0]).intValue();
                        }
                        catch (NumberFormatException e)
                        {
                                throw new TestBug(
                                        "Bad input to gctest02. Expected integer, got: ->"
                                        + args[0] + "<-", e);
                        }
                }

                if (args.length == 2)
                {
                        try
                        {
                                randomSeed = new Long(args[1]).longValue();
                        }
                        catch (NumberFormatException e)
                        {
                                throw new TestBug(
                                        "Bad input to gctest02. Expected long, got: ->"
                                        + args[0] + "<-", e);
                        }
                }
                Person.setPopulationLimit(peopleLimit);
                System.out.println("Seed value: " + randomSeed);
                for (int ii=0; ii<40; ii++) {
                        bufsz = 8;
                        LocalRandom.rGen = new Random(randomSeed);
                        Person.populationCount = 0;
                        Escaper you = new Escaper();
                        you.setName("Escaper");
                        ThreadCount.inc();
                        you.start();
                        me = new Memevil(bufsz);
                        me.setName("Memevil" + bufsz);
                        bufsz = 2*bufsz;
                        me.start();
                        Thread.yield();
                        for (int i=1; i<11; i++) {
                                ThreadCount.inc();
                                me = new Memevil(bufsz);
                                me.setName("Memevil" + bufsz);
                                bufsz = 2*bufsz;
                                me.start();
                                Thread.yield();
                        }
                        try {
                                you.join();
                        }
                        catch (InterruptedException e) {
                                throw new TestFailure("InterruptedException in gctest2.main()");
                        }
                        for (int i=1; i<11; i++) {
                                try { me.join(); }
                                catch (InterruptedException e) {
                                        throw new TestFailure("InterruptedException in gctest2.main()");
                                }
                        }
                }
                System.out.println("Test passed.");
        }
}
