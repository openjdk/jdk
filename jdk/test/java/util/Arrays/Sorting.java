/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6880672 6896573
 * @summary Exercise Arrays.sort
 * @build Sorting
 * @run main Sorting -shortrun
 * @author Vladimir Yaroslavskiy, Josh Bloch, Jon Bentley
 */

import java.util.Arrays;
import java.util.Random;
import java.io.PrintStream;

public class Sorting {
    static final PrintStream out = System.out;
    static final PrintStream err = System.err;

    // array lengths used in a long run (default)
    static final int[] LONG_RUN = {
        0, 1, 2, 3, 5, 8, 13, 21, 34, 55, 100, 1000, 10000, 100000, 1000000};

    // array lengths used in a short run
    static final int[] SHORT_RUN = {0, 1, 2, 3, 21, 55, 1000, 10000, 500000};

    public static void main(String[] args) {
        boolean shortRun = false;
        if (args.length > 0 && args[0].equals("-shortrun"))
            shortRun = true;

        long start = System.nanoTime();

        testAndCheck((shortRun) ? SHORT_RUN : LONG_RUN);

        long end = System.nanoTime();

        out.println();
        out.format("PASS in %ds%n", Math.round((end - start) / 1e9));
    }

    static void testAndCheck(int[] lengths) {
        for (int len : lengths) {
            out.println();
            ArrayBuilder.reset();
            int[] golden = new int[len];

            for (int m = 1; m < 2 * len; m *= 2) {
                for (ArrayBuilder builder : ArrayBuilder.values()) {
                    builder.build(golden, m);
                    int[] test = golden.clone();

                    for (Converter converter : Converter.values()) {
                        out.println("Test: " + converter + " " + builder +
                            "len = " + len + ", m = " + m);
                        Object convertedGolden = converter.convert(golden);
                        Object convertedTest = converter.convert(test);
                        sort(convertedTest);
                        checkWithCheckSum(convertedTest, convertedGolden);
                    }
                }
            }
        }
    }

    static enum Converter {
        INT {
            Object convert(int[] a) {
                return a;
            }
        },
        LONG {
            Object convert(int[] a) {
                long[] b = new long[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = (int) a[i];
                }
                return b;
            }
        },
        BYTE {
            Object convert(int[] a) {
                byte[] b = new byte[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = (byte) a[i];
                }
                return b;
            }
        },
        SHORT {
            Object convert(int[] a) {
                short[] b = new short[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = (short) a[i];
                }
                return b;
            }
        },
        CHAR {
            Object convert(int[] a) {
                char[] b = new char[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = (char) a[i];
                }
                return b;
            }
        },
        FLOAT {
            Object convert(int[] a) {
                float[] b = new float[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = (float) a[i];
                }
                return b;
            }
        },
        DOUBLE {
            Object convert(int[] a) {
                double[] b = new double[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = (double) a[i];
                }
                return b;
            }
        };

        abstract Object convert(int[] a);

        @Override public String toString() {
            String name = name();

            for (int i = name.length(); i < 9; i++) {
                name += " ";
            }
            return name;
        }
    }

    static enum ArrayBuilder {
        RANDOM {
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = ourRandom.nextInt();
                }
            }
        },
        ASCENDING {
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = m + i;
                }
            }
        },
        DESCENDING {
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = a.length - m - i;
                }
            }
        },
        ALL_EQUAL {
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = m;
                }
            }
        },
        SAW {
            void build(int[] a, int m) {
                int incCount = 1;
                int decCount = a.length;
                int i = 0;
                int period = m;
                m--;
                while (true) {
                    for (int k = 1; k <= period; k++) {
                        if (i >= a.length) {
                            return;
                        }
                        a[i++] = incCount++;
                    }
                    period += m;

                    for (int k = 1; k <= period; k++) {
                        if (i >= a.length) {
                            return;
                        }
                        a[i++] = decCount--;
                    }
                    period += m;
                }
            }
        },
        REPEATED {
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = i % m;
                }
            }
        },
        DUPLICATED {
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = ourRandom.nextInt(m);
                }
            }
        },
        ORGAN_PIPES {
            void build(int[] a, int m) {
                int middle = a.length / (m + 1);

                for (int i = 0; i < middle; i++) {
                    a[i] = i;
                }
                for (int i = middle; i < a.length; i++) {
                    a[i] = a.length - i - 1;
                }
            }
        },
        STAGGER {
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = (i * m + i) % a.length;
                }
            }
        },
        PLATEAU {
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = Math.min(i, m);
                }
            }
        },
        SHUFFLE {
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = ourRandom.nextBoolean() ? (ourFirst += 2) : (ourSecond += 2);
                }
            }
        };

        abstract void build(int[] a, int m);

        static void reset() {
            ourRandom = new Random(666);
            ourFirst = 0;
            ourSecond = 0;
        }

        @Override public String toString() {
            String name = name();
            for (int i = name.length(); i < 12; i++) {
                name += " ";
            }
            return name;
        }

        private static int ourFirst;
        private static int ourSecond;
        private static Random ourRandom = new Random(666);
    }

    static void checkWithCheckSum(Object test, Object golden) {
        checkSorted(test);
        checkCheckSum(test, golden);
    }

    static void failed(String message) {
        err.format("***FAILED: %s%%n", message);
        throw new RuntimeException("Test failed - see log file for details");
    }

    static void failed(int index, String value1, String value2) {
        failed("Array is not sorted at " + index + "-th position: " + value1 +
               " and " + value2);
    }

    static void checkSorted(Object object) {
        if (object instanceof int[]) {
            checkSorted((int[]) object);
        } else if (object instanceof long[]) {
            checkSorted((long[]) object);
        } else if (object instanceof short[]) {
            checkSorted((short[]) object);
        } else if (object instanceof byte[]) {
            checkSorted((byte[]) object);
        } else if (object instanceof char[]) {
            checkSorted((char[]) object);
        } else if (object instanceof float[]) {
            checkSorted((float[]) object);
        } else if (object instanceof double[]) {
            checkSorted((double[]) object);
        } else {
            failed("Unknow type of array: " + object + " of class " +
                object.getClass().getName());
        }
    }

    static void checkSorted(int[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    static void checkSorted(long[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    static void checkSorted(short[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    static void checkSorted(byte[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    static void checkSorted(char[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    static void checkSorted(float[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    static void checkSorted(double[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    static void checkCheckSum(Object test, Object golden) {
        if (checkSum(test) != checkSum(golden)) {
            failed("Original and sorted arrays seems not identical");
        }
    }

    static int checkSum(Object object) {
        if (object instanceof int[]) {
            return checkSum((int[]) object);
        } else if (object instanceof long[]) {
            return checkSum((long[]) object);
        } else if (object instanceof short[]) {
            return checkSum((short[]) object);
        } else if (object instanceof byte[]) {
            return checkSum((byte[]) object);
        } else if (object instanceof char[]) {
            return checkSum((char[]) object);
        } else if (object instanceof float[]) {
            return checkSum((float[]) object);
        } else if (object instanceof double[]) {
            return checkSum((double[]) object);
        } else {
            failed("Unknow type of array: " + object + " of class " +
                object.getClass().getName());
            return -1;
        }
    }

    static int checkSum(int[] a) {
        int checkSum = 0;

        for (int e : a) {
            checkSum ^= e; // xor
        }
        return checkSum;
    }

    static int checkSum(long[] a) {
        long checkSum = 0;

        for (long e : a) {
            checkSum ^= e; // xor
        }
        return (int) checkSum;
    }

    static int checkSum(short[] a) {
        short checkSum = 0;

        for (short e : a) {
            checkSum ^= e; // xor
        }
        return (int) checkSum;
    }

    static int checkSum(byte[] a) {
        byte checkSum = 0;

        for (byte e : a) {
            checkSum ^= e; // xor
        }
        return (int) checkSum;
    }

    static int checkSum(char[] a) {
        char checkSum = 0;

        for (char e : a) {
            checkSum ^= e; // xor
        }
        return (int) checkSum;
    }

    static int checkSum(float[] a) {
        int checkSum = 0;

        for (float e : a) {
            checkSum ^= (int) e; // xor
        }
        return checkSum;
    }

    static int checkSum(double[] a) {
        int checkSum = 0;

        for (double e : a) {
            checkSum ^= (int) e; // xor
        }
        return checkSum;
    }

    static void sort(Object object) {
        if (object instanceof int[]) {
            Arrays.sort((int[]) object);
        } else if (object instanceof long[]) {
            Arrays.sort((long[]) object);
        } else if (object instanceof short[]) {
            Arrays.sort((short[]) object);
        } else if (object instanceof byte[]) {
            Arrays.sort((byte[]) object);
        } else if (object instanceof char[]) {
            Arrays.sort((char[]) object);
        } else if (object instanceof float[]) {
            Arrays.sort((float[]) object);
        } else if (object instanceof double[]) {
            Arrays.sort((double[]) object);
        } else {
            failed("Unknow type of array: " + object + " of class " +
                object.getClass().getName());
        }
    }
}
