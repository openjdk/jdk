/*
* Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
* @bug 8372591
* @summary Test freeze/thaw of OSR frame from method with many locals
* @requires vm.continuations
* @requires vm.compMode != "Xint" & vm.compMode != "Xcomp"
* @modules java.base/jdk.internal.vm
* @library /test/lib /test/hotspot/jtreg
* @build jdk.test.whitebox.WhiteBox
* @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
* @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI OSRWithManyLocals
*/

import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;

import jdk.test.lib.Asserts;
import java.lang.reflect.Method;
import jdk.test.whitebox.WhiteBox;

public class OSRWithManyLocals {
    static final WhiteBox wb = WhiteBox.getWhiteBox();
    static final ContinuationScope FOO = new ContinuationScope() {};
    static final Method foo = getMethod("foo");

    public static void main(String[] args) throws Exception {
        runCont(new Continuation(FOO, () -> warmUp()));
        runCont(new Continuation(FOO, () -> foo()));
    }

    public static void runCont(Continuation cont) {
        while (!cont.isDone()) {
            cont.run();
        }
    }

    public static void warmUp() {
        // Trigger compilation of Continuation.yield/yield0
        for (int i = 0; i < 10_000; i++) {
            Continuation.yield(FOO);
        }
    }

    public static void foo() {
        // Declare lots of locals so that size of OSR foo + Continuation.yield/yield0
        // frames is less than (foo_sender.unextended_sp() - foo_sender.sp()).
        double d1=1,d2=2,d3=3,d4=4,d5=5,d6=6,d7=7,d8=8,d9=9,d10=10,d11=11,d12=12,d13=13,d14=14,d15=15,d16=16,d17=17,d18=18;
        double d19=19,d20=20,d21=21,d22=22,d23=23,d24=24,d25=25,d26=26,d27=27,d28=28,d29=29,d30=30,d31=31,d32=32,d33=33,d34=34;
        double d35=35,d36=36,d37=37,d38=38,d39=39,d40=40,d41=41,d42=42,d43=43,d44=44,d45=45,d46=46,d47=47,d48=48,d49=49,d50=50;
        double d51=51,d52=52,d53=53,d54=54,d55=55,d56=56,d57=57,d58=58,d59=59,d60=60,d61=61,d62=62,d63=63,d64=64,d65=65,d66=66;
        double d67=67,d68=68,d69=69,d70=70,d71=71,d72=72,d73=73,d74=74,d75=75,d76=76,d77=77,d78=78,d79=79,d80=80,d81=81,d82=82;

        // Provoke OSR compilation. After we verified the method was compiled keep looping
        // until we trigger the _backedge_counter overflow to actually trigger OSR.
        for (int i = 0; !wb.isMethodCompiled(foo, true) || i++ < 2_000;) {
        }
        Continuation.yield(FOO);
    }

    static Method getMethod(String method) {
        try {
            return OSRWithManyLocals.class.getMethod(method);
        } catch (Exception e) {
            throw new RuntimeException("Exception: couldn't found method " + method + ". " + e.getMessage());
        }
    }
}
