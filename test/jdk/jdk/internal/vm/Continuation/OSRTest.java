/*
* Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
* @test
* @bug 8325469
* @summary Test freeze/thaw with OSR frames
* @requires vm.continuations
* @requires vm.compMode != "Xint" & vm.compMode != "Xcomp"
* @modules java.base/jdk.internal.vm
* @library /test/lib /test/hotspot/jtreg
* @build jdk.test.whitebox.WhiteBox
* @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
*
* @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:CompileCommand=dontinline,*::foo* OSRTest true true true
* @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:CompileCommand=dontinline,*::foo* -XX:CompileCommand=inline,*::yield0 OSRTest true true false
* @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:CompileCommand=dontinline,*::foo* -XX:CompileCommand=dontinline,*::yield* OSRTest true true false
* @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:CompileCommand=dontinline,*::foo* -XX:CompileCommand=exclude,*::bar() OSRTest true false false
* @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:CompileCommand=dontinline,*::foo* OSRTest false true true
* @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:CompileCommand=dontinline,*::foo* OSRTest false true false
* @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:CompileCommand=dontinline,*::foo* -XX:CompileCommand=exclude,*::bar() OSRTest false false false
*
*/

import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;

import java.lang.reflect.Method;
import jdk.test.whitebox.WhiteBox;

public class OSRTest {
    static final WhiteBox wb = WhiteBox.getWhiteBox();
    static final ContinuationScope FOO = new ContinuationScope() {};
    static final Method foo = getMethod("foo");
    static final Method fooBigFrame = getMethod("fooBigFrame");
    boolean osrAtBottom;
    boolean freezeFast;
    boolean thawFast;
    int fooCallCount;

    public static void main(String[] args) {
        if (args.length != 3) {
            throw new Error("Error: args.length must be 3");
        }
        boolean TEST_OSR_AT_BOTTOM = Boolean.parseBoolean(args[0]);
        boolean FREEZE_FAST = Boolean.parseBoolean(args[1]);
        boolean THAW_FAST = Boolean.parseBoolean(args[2]);
        assert !THAW_FAST || FREEZE_FAST : "THAW_FAST requires FREEZE_FAST";

        OSRTest test = new OSRTest(TEST_OSR_AT_BOTTOM, FREEZE_FAST, THAW_FAST);
        test.runTest();
    }

    public OSRTest(boolean osrAtBottom, boolean freezeFast, boolean thawFast) {
        this.osrAtBottom = osrAtBottom;
        this.freezeFast = freezeFast;
        this.thawFast = thawFast;
    }

    public void runTest() {
        Runnable testCase =  osrAtBottom ? ()-> testOSRAtStackBottom() : ()-> TestOSRNotAtStackBottom();
        Continuation cont = new Continuation(FOO, testCase);

        while (!cont.isDone()) {
            cont.run();
            if (freezeFast && !thawFast && fooCallCount == 2) {
                // All frames frozen in last yield should be compiled
                // including OSR version of foo. Invoke full GC now so
                // that chunk is marked and we force thaw slow path.
                System.gc();
                fooCallCount++; // Don't call again
            }
        }
    }

    public void testOSRAtStackBottom() {
        if (freezeFast) {
            // Trigger compilation of Continuation.yield/yield0
            for (int i = 0; i < 10_000; i++) {
                Continuation.yield(FOO);
            }
        }
        for (int i = 0; i < 2; i++) {
            if (freezeFast && !thawFast) {
                foo(new Object(), new Object(), new Object(), new Object(), new Object(),
                    new Object(), new Object(), new Object(), new Object(), new Object(),
                    1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.6f, 1.7f);
            } else {
                fooBigFrame(new Object(), new Object(), new Object(), new Object(), new Object(),
                            new Object(), new Object(), new Object(), new Object(), new Object(),
                            1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.6f, 1.7f);
            }
        }
    }

    // Declare many parameters and unused locals so that size of interpreter frame is bigger
    // than size of OSR frame + size of Continuation.yield/yield0 frames. This is so that once
    // foo is OSR, on yield we clear cont_fastpath() forcing the freeze fast path.
    public void foo(Object o1, Object o2, Object o3, Object o4, Object o5,
                    Object o6, Object o7, Object o8, Object o9, Object o10,
                    float f1, float f2, float f3, float f4, float f5, float f6, float f7) {
        int i1 = 1;
        int i2 = i1 + 1;
        int i3 = i2 + 1;
        int i4 = i3 + 1;
        int i5 = i4 + 1;
        int i6 = i5 + 1;
        int i7 = i6 + 1;
        long ll = 2*(long)i1;
        float ff = ll + 1.2f;
        double dd = ff + 1.3D;

        if (osrAtBottom) {
            // freeze all frames so that we only run with foo on the stack
            Continuation.yield(FOO);
        }

        // Provoke OSR compilation. After we verified the method was compiled keep looping
        // until we trigger the _backedge_counter overflow to actually trigger OSR.
        for (int i = 0; fooCallCount > 0 && (!wb.isMethodCompiled(foo, true) || i++ < 2_000);) {
        }
        fooCallCount++;

        if (freezeFast) {
            Continuation.yield(FOO);
        } else {
            bar();
        }
    }

    public void bar() {
        Continuation.yield(FOO);
    }

    public double fooBigFrame(Object o1, Object o2, Object o3, Object o4, Object o5,
                              Object o6, Object o7, Object o8, Object o9, Object o10,
                              float f1, float f2, float f3, float f4, float f5, float f6, float f7) {
        double d1=1,d2=2,d3=3,d4=4,d5=5,d6=6,d7=7,d8=8,d9=9,d10=10,d11=11,d12=12,d13=13,d14=14,d15=15,d16=16,d17=17,d18=18,d19=19,d20=20,d21=21,d22=22,d23=23,d24=24,d25=25;
        double d26=26,d27=27,d28=28,d29=29,d30=30,d31=31,d32=32,d33=33,d34=34,d35=35,d36=36,d37=37,d38=38,d39=39,d40=40,d41=41,d42=42,d43=43,d44=44,d45=45,d46=46,d47=47,d48=48,d49=49,d50=50;
        double d51=51,d52=52,d53=53,d54=54,d55=55,d56=56,d57=57,d58=58,d59=59,d60=60,d61=61,d62=62,d63=63,d64=64,d65=65,d66=66,d67=67,d68=68,d69=69,d70=70,d71=71,d72=72,d73=73,d74=74,d75=75;
        double d76=76,d77=77,d78=78,d79=79,d80=80,d81=81,d82=82,d83=83,d84=84,d85=85,d86=86,d87=87,d88=88,d89=89,d90=90,d91=91,d92=92,d93=93,d94=94,d95=95,d96=96,d97=97,d98=98,d99=99,d100=100;
        double d101=101,d102=102,d103=103,d104=104,d105=105,d106=106,d107=107,d108=108,d109=109,d110=110,d111=111,d112=112,d113=113,d114=114,d115=115,d116=116,d117=117,d118=118,d119=119,d120=120,d121=121,d122=122,d123=123,d124=124,d125=125;
        double d126=126,d127=127,d128=128,d129=129,d130=130,d131=131,d132=132,d133=133,d134=134,d135=135,d136=136,d137=137,d138=138,d139=139,d140=140,d141=141,d142=142,d143=143,d144=144,d145=145,d146=146,d147=147,d148=148,d149=149,d150=150;
        double d151=151,d152=152,d153=153,d154=154,d155=155,d156=156,d157=157,d158=158,d159=159,d160=160,d161=161,d162=162,d163=163,d164=164,d165=165,d166=166,d167=167,d168=168,d169=169,d170=170,d171=171,d172=172,d173=173,d174=174,d175=175;
        double d176=176,d177=177,d178=178,d179=179,d180=180,d181=181,d182=182,d183=183,d184=184,d185=185,d186=186,d187=187,d188=188,d189=189,d190=190,d191=191,d192=192,d193=193,d194=194,d195=195,d196=196,d197=197,d198=198,d199=199,d200=200;
        double d201=201,d202=202,d203=203,d204=204,d205=205,d206=206,d207=207,d208=208,d209=209,d210=210,d211=211,d212=212,d213=213,d214=214,d215=215,d216=216,d217=217,d218=218,d219=219,d220=220,d221=221,d222=222,d223=223,d224=224,d225=225;
        double d226=226,d227=227,d228=228,d229=229,d230=230,d231=231,d232=232,d233=233,d234=234,d235=235,d236=236,d237=237,d238=238,d239=239,d240=240,d241=241,d242=242,d243=243,d244=244,d245=245,d246=246,d247=247,d248=248,d249=249,d250=250;
        double d251=251,d252=252,d253=253,d254=254,d255=255,d256=256,d257=257,d258=258,d259=259,d260=260,d261=261,d262=262,d263=263,d264=264,d265=265,d266=266,d267=267,d268=268,d269=269,d270=270,d271=271,d272=272,d273=273,d274=274,d275=275;
        double d276=276,d277=277,d278=278,d279=279,d280=280,d281=281,d282=282,d283=283,d284=284,d285=285,d286=286,d287=287,d288=288,d289=289,d290=290,d291=291,d292=292,d293=293,d294=294,d295=295,d296=296,d297=297,d298=298,d299=299,d300=300;

        // freeze all frames so that we only run with fooBigFrame on the stack
        Continuation.yield(FOO);

        // Provoke OSR compilation. After we verified the method was compiled keep looping
        // until we trigger the _backedge_counter overflow to actually trigger OSR.
        for (int i = 0; fooCallCount > 0 && (!wb.isMethodCompiled(fooBigFrame, true) || i++ < 2_000);) {
        }
        fooCallCount++;

        Continuation.yield(FOO);

        // For the thaw fast case we want to trigger the case of thawing one
        // frame at a time. Because the OSR frame is at the bottom we have to
        // make its size > 500 words, so we use a lot of locals. We also want
        // the interpreted frame size be bigger than OSR frame + size of
        // Continuation.yield/yield0, so that we clear cont_fastpath() on yield
        // forcing the freeze fast path (same as with foo). For that, we just
        // declare more locals than the ones we use after OSR happens.
        // For the freeze slow case we also want the interpreted frame size to
        // be bigger than OSR frame + size of Continuation.yield/yield0, so the
        // last technique serves for this case too.
        double res = d1*d2*d3*d4*d5*d6*d7*d8*d9*d10*d11*d12*d13*d14*d15*d16*d17*d18*d19*d20*d21*d22*d23*d24*d25*d26*d27*d28*d29*d30*d31*d32*d33*d34*d35*d36*d37*d38*d39*d40*d41*d42*d43*d44*d45*d46*d47*d48*d49*d50*
                     d51*d52*d53*d54*d55*d56*d57*d58*d59*d60*d61*d62*d63*d64*d65*d66*d67*d68*d69*d70*d71*d72*d73*d74*d75*d76*d77*d78*d79*d80*d81*d82*d83*d84*d85*d86*d87*d88*d89*d90*d91*d92*d93*d94*d95*d96*d97*d98*d99*d100*
                     d101*d102*d103*d104*d105*d106*d107*d108*d109*d110*d111*d112*d113*d114*d115*d116*d117*d118*d119*d120*d121*d122*d123*d124*d125*d126*d127*d128*d129*d130*d131*d132*d133*d134*d135*d136*d137*d138*d139*d140*
                     d141*d142*d143*d144*d145*d146*d147*d148*d149*d150*d151*d152*d153*d154*d155*d156*d157*d158*d159*d160*d161*d162*d163*d164*d165*d166*d167*d168*d169*d170*d171*d172*d173*d174*d175*d176*d177*d178*d179*d180*
                     d181*d182*d183*d184*d185*d186*d187*d188*d189*d190*d191*d192*d193*d194*d195*d196*d197*d198*d199*d200*d201*d202*d203*d204*d205*d206*d207*d208*d209*d210*d211*d212*d213*d214*d215*d216*d217*d218*d219*d220*
                     d221*d222*d223*d224*d225*d226*d227*d228*d229*d230*d231*d232*d233*d234*d235*d236*d237*d238*d239*d240*d241*d242*d243*d244*d245*d246*d247*d248*d249*d250*d251*d252*d253*d254*d255*d256*d257*d258*d259*d260*
                     d261*d262*d263*d264*d265*d266*d267*d268*d269*d270*d271*d272*d273*d274*d275;
        return res;
    }

    public void TestOSRNotAtStackBottom() {
        // freeze all frames currently in the stack
        boolean res = Continuation.yield(FOO);

        for (int i = 1; i < 100000; i++) {
            // When testing the thaw fast path make recursion big enough so that
            // the size of all frames at freeze time is more than 500 words. This
            // way we later force thawing one frame at a time.
            recurse(thawFast ? 60 : 5, i);
        }
    }

    public void recurse(int depth, int iteration) {
        if (depth > 0) {
            recurse(depth - 1, iteration);
        } else {
            // Make compiler see this branch but not enough times to avoid foo
            // getting compiled, since we want the OSR version.
            if (iteration % 45000 == 0) {
                foo(new Object(), new Object(), new Object(), new Object(), new Object(),
                    new Object(), new Object(), new Object(), new Object(), new Object(),
                    1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.6f, 1.7f);
            } else {
                Continuation.yield(FOO);
            }
        }
    }

    static Method getMethod(String method) {
        try {
            return OSRTest.class.getMethod(method, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class,
                                           Object.class, Object.class, Float.TYPE, Float.TYPE, Float.TYPE, Float.TYPE, Float.TYPE, Float.TYPE, Float.TYPE);
        } catch (Exception e) {
            throw new RuntimeException("Exception: couldn't found method " + method + ". " + e.getMessage());
        }
    }
}