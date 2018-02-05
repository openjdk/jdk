/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4169183
 * @summary Check for correct inlining by the interpreter (widefp and strictfp).
 *          The default is widefp.  A strictfp method was getting inlined
 *          into a widefp method.
 */

import java.io.PrintStream;

public class WideStrictInline {
    static PrintStream out;
    static float halfUlp;

    static {
        halfUlp = 1;
        for ( int i = 127 - 24; i > 0; i-- )
            halfUlp *= 2;
    }

    public static void main(String argv[]) throws Exception {
        out = System.err;
        pr(-1,"halfUlp",halfUlp);
        WideStrictInline obj = new WideStrictInline();
        for( int i=0; i<48; i++ )
            obj.instanceMethod( i );
    }

    private static void pr(int i, String desc, float r) {
        out.print(" i=("+i+") "+desc+" ; == "+r);
        out.println(" , 0x"+Integer.toHexString(Float.floatToIntBits(r)));
    }

    private static strictfp float WideStrictInline(float par) {
        return par;
    }

    public static strictfp float strictValue(int i) {
        float r;
        switch (i%4) {
        case 0: r = -Float.MAX_VALUE;  break;
        case 1: r =  Float.MAX_VALUE;  break;
        case 2: r =  Float.MIN_VALUE;  break;
        default : r = 1L << 24;
        }
        return r;
    }

    void instanceMethod (int i) throws Exception {
        float r;
        switch (i%4) {
        case 0:
            if (!Float.isInfinite( WideStrictInline(strictValue(i)*2) +
                                   Float.MAX_VALUE ))
                {
                    pr(i,
                       "WideStrictInline(-Float.MAX_VALUE * 2) " +
                       "!= Float.NEGATIVE_INFINITY"
                       ,WideStrictInline(strictValue(i)*2) + Float.MAX_VALUE);
                }
            r = WideStrictInline(strictValue(i)*2) + Float.MAX_VALUE;
            if ( !Float.isInfinite( r ) ) {
                pr(i,"r != Float.NEGATIVE_INFINITY",r);
                throw new RuntimeException();
            }
            break;
        case 1:
            if (!Float.isInfinite(WideStrictInline(strictValue(i)+halfUlp) -
                                  Float.MAX_VALUE )) {
                pr(i,"WideStrictInline(Float.MAX_VALUE+halfUlp) " +
                   "!= Float.POSITIVE_INFINITY"
                   ,WideStrictInline(strictValue(i)+halfUlp) - Float.MAX_VALUE);
            }
            r = WideStrictInline(strictValue(i)+halfUlp) - Float.MAX_VALUE;
            if ( !Float.isInfinite( r ) ) {
                pr(i,"r != Float.POSITIVE_INFINITY",r);
                throw new RuntimeException();
            }
            break;
        case 2:
            if (WideStrictInline(strictValue(i)/2) != 0) {
                pr(i,"WideStrictInline(Float.MIN_VALUE/2) != 0",
                   WideStrictInline(strictValue(i)/2));
            }
            r = WideStrictInline(strictValue(i)/2);
            if ( r != 0 ) {
                pr(i,"r != 0",r);
                throw new RuntimeException();
            }
            break;
        default:
            if (WideStrictInline(strictValue(i)-0.5f) - strictValue(i) != 0) {
                pr(i,"WideStrictInline(2^24-0.5) != 2^24",
                   WideStrictInline(strictValue(i)-0.5f));
            }
            r = WideStrictInline(strictValue(i)-0.5f);
            if ( r - strictValue(i) != 0 ) {
                pr(i,"r != 2^24",r);
                throw new RuntimeException();
            }
        }
    }

}
