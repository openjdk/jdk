/*
 * Copyright 2008 Sun Microsystems, Inc.  All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 * @test
 * @bug 6659207
 * @summary access violation in CompilerThread0
 */

public class Test {
    static int[] array = new int[12];

    static int index(int i) {
        if (i == 0) return 0;
        for (int n = 0; n < array.length; n++)
            if (i < array[n]) return n;
        return -1;
    }

    static int test(int i) {
        int result = 0;
        i = index(i);
        if (i >= 0)
            if (array[i] != 0)
                result++;

        if (i != -1)
            array[i]++;

        return result;
    }

    public static void main(String[] args) {
        int total = 0;
        for (int i = 0; i < 100000; i++) {
            total += test(10);
        }
        System.out.println(total);
    }
}
