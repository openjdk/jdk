/*
 * Copyright (c) 1996, 2005, Oracle and/or its affiliates. All rights reserved.
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
        A simple Java class definition that helps self-test the runtime
        interpreter.  Used for getfield/putfield, invoke* opcodes and
        their _quick variants.

        See src/share/java/runtime/selftest.c for details of the test
        environment.
*/

/* Used to be sun/misc/SelfTest.java */

interface UseAllBytecodesInterface
{
    public void test_an_interface(int p1);
}

public class UseAllBytecodes implements UseAllBytecodesInterface
{
        public int i1, i2;
        public float f1, f2;
        public double d1, d2;
        public long l1, l2;

        public static int si1, si2;
        public static float sf1, sf2;
        public static double sd1, sd2;
        public static long sl1, sl2;

        public UseAllBytecodesInterface interfaceObject;

        public int multi[][][];

        public UseAllBytecodes()
        {
                /*      This constructor is not intended to ever be run.  It is here
                        to force CONSTANT_Methodref constants into the CP */
                set_i1(11);
                set_i2(22);
                set_f1(1.1f);
                set_f2(2.2f);
                set_d1(1.0);
                set_d2(2.0);
                set_l1(3);
                set_l2(4);

                set_si1(33);
                set_si2(44);
                set_sf1(3.3f);
                set_sf2(4.4f);
                set_sd1(3.0);
                set_sd2(4.0);
                set_sl1(5);
                set_sl2(6);

                test_areturn();
                test_athrow1();
                test_athrow2();
                test_athrow3();
                test_athrow4();

                /*      This puts a CONSTANT_InterfaceMethodref into the CP */
                interfaceObject.test_an_interface(1234);

                /*      This creates an array and puts it into the CP */
                multi = new int[2][3][4];
        }

        public UseAllBytecodes(int p1)
        {
                i1 = p1;
                i2 = 12345678;  /* This puts a CONSTANT_Integer into the CP */
                d1 = (double) p1;
                d2 = 1.2e234;   /* This puts a CONSTANT_Double into the CP */
        }

        public UseAllBytecodes(int p1, int p2)
        {
                i1 = p1;
                i2 = p2;
        }

        /*      These methods should return something other than their
                arguments, so the self test can easily determine that
                the correct value was returned. */
        public int set_i1(int p1)
        {
                i1 = p1;
                return i1 + 1;
        }

        public int set_i2(int p2)
        {
                i2 = p2;
                return i2 + 1;
        }

        public float set_f1(float p1)
        {
                f1 = p1;
                return f1 + 1.0e34f;
        }

        public float set_f2(float p2)
        {
                f2 = p2;
                return f2 + 1.0e34f;
        }

        public double set_d1(double p1)
        {
                d1 = p1;
                return d1 + 1.0e234;
        }

        public double set_d2(double p2)
        {
                d2 = p2;
                return d2 + 1.0e234;
        }

        public long set_l1(long p1)
        {
                l1 = p1;
                return l1 + 1;
        }

        public long set_l2(long p2)
        {
                l2 = p2;
                return l2 + 1;
        }

        public static void set_si1(int p1)
        {
                si1 = p1;
        }

        public static void set_si2(int p2)
        {
                si2 = p2;
        }

        public static void set_sf1(float p1)
        {
                sf1 = p1;
        }

        public static void set_sf2(float p2)
        {
                sf2 = p2;
        }

        public static void set_sd1(double p1)
        {
                sd1 = p1;
        }

        public static void set_sd2(double p2)
        {
                sd2 = p2;
        }

        public static void set_sl1(long p1)
        {
                sl1 = p1;
        }

        public static void set_sl2(long p2)
        {
                sl2 = p2;
        }

        public UseAllBytecodes test_areturn()
        {
                return this;
        }

        /*      This method does the same thing as set_i1.
                It is here to test the invokeinterface opcode. */
        public void test_an_interface(int p1)
        {
                i1 = p1;
        }

        /*      The following 10 methods test various permutations of
                try-and-catch. */
        public static void test_athrow1() throws NullPointerException
        {
                try
                {
                        si1 = -1;
                        throw new NullPointerException();
                }
                catch (Exception e)
                {
                        si1 = 1;
                }
        }

        public static void test_athrow2()
        {
                int i = 1;
                try
                {
                        si1 = -1;
                        test_athrow1();
                }
                catch (Exception e)
                {
                        // This should *not* catch the exception;
                        // should be caught in test_athrow1.
                        si1 = i + 1;
                }
        }

        public static void test_athrow3()
        {
                int i = 1;
                try
                {
                        // Ultimately throws NullPointerException
                        si1 = -1;
                        si2 = -1;
                        test_athrow5();
                }
                catch (NullPointerException np)
                {
                        si1 = i + 1;
                }
                catch (NoSuchMethodException e)
                {
                        si2 = i + 1;
                }
                si1++;  // total is 3
        }

        public static void test_athrow4()
        {
                int i = 2;
                try
                {
                        // Ultimately throws NoSuchMethodException
                        si1 = -1;
                        si2 = -1;
                        test_athrow7();
                }
                catch (NullPointerException e)
                {
                        si1 = i + 1;
                }
                catch (NoSuchMethodException nsm)
                {
                        si2 = i + 1;
                }
                si2++;  // total is 4
        }

        public static void test_throw_nosuchmethod() throws NoSuchMethodException
        {
                throw new NoSuchMethodException();
        }

        public static void test_throw_nullpointer() throws NullPointerException
        {
                throw new NullPointerException();
        }

        public static void test_athrow5() throws NullPointerException, NoSuchMethodException
        {
                test_athrow6();
        }

        public static void test_athrow6() throws NullPointerException, NoSuchMethodException
        {
                test_throw_nullpointer();
        }

        public static void test_athrow7() throws NullPointerException, NoSuchMethodException
        {
                test_athrow8();
        }

        public static void test_athrow8() throws NullPointerException, NoSuchMethodException
        {
                test_throw_nosuchmethod();
        }
}
