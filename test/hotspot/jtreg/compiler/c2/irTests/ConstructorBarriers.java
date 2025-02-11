/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8300148
 * @summary Test barriers emitted in constructors
 * @library /test/lib /
 * @requires os.arch=="aarch64" | os.arch=="riscv64" | os.arch=="x86_64" | os.arch=="amd64"
 * @run main compiler.c2.irTests.ConstructorBarriers
 */
public class ConstructorBarriers {
    public static void main(String[] args) {
        TestFramework.run();
    }

    // Checks the barrier coalescing/optimization around field initializations.
    // Uses long fields to avoid store merging.

    public static class PlainPlain {
        long f1;
        long f2;
        public PlainPlain(long i) {
            f1 = i;
            f2 = i;
        }
    }

    private static class FinalPlain {
        final long f1;
        long f2;
        public FinalPlain(long i) {
            f1 = i;
            f2 = i;
        }
    }

    private static class PlainFinal {
        long f1;
        final long f2;
        public PlainFinal(long i) {
            f1 = i;
            f2 = i;
        }
    }

    private static class FinalFinal {
        final long f1;
        final long f2;
        public FinalFinal(long i) {
            f1 = i;
            f2 = i;
        }
    }

    private static class PlainVolatile {
        long f1;
        volatile long f2;
        public PlainVolatile(long i) {
            f1 = i;
            f2 = i;
        }
    }

    private static class VolatilePlain {
        volatile long f1;
        long f2;
        public VolatilePlain(long i) {
            f1 = i;
            f2 = i;
        }
    }

    private static class FinalVolatile {
        final long f1;
        volatile long f2;
        public FinalVolatile(long i) {
            f1 = i;
            f2 = i;
        }
    }

    private static class VolatileFinal {
        volatile long f1;
        final long f2;
        public VolatileFinal(long i) {
            f1 = i;
            f2 = i;
        }
    }

    private static class VolatileVolatile {
        volatile long f1;
        volatile long f2;
        public VolatileVolatile(long i) {
            f1 = i;
            f2 = i;
        }
    }

    long l = 42;

    @DontInline
    public void consume(Object o) {}

    @Test
    @IR(counts = {IRNode.MEMBAR_STORESTORE, "1"})
    @IR(failOn = IRNode.MEMBAR_RELEASE)
    @IR(failOn = IRNode.MEMBAR_VOLATILE)
    public long escaping_plainPlain() {
        PlainPlain c = new PlainPlain(l);
        consume(c);
        return c.f1 + c.f2;
    }

    @Test
    @IR(counts = {IRNode.MEMBAR_STORESTORE, "1"})
    @IR(failOn = IRNode.MEMBAR_RELEASE)
    @IR(failOn = IRNode.MEMBAR_VOLATILE)
    public long escaping_plainFinal() {
        PlainFinal c = new PlainFinal(l);
        consume(c);
        return c.f1 + c.f2;
    }

    @Test
    @IR(counts = {IRNode.MEMBAR_STORESTORE, "1"})
    @IR(failOn = IRNode.MEMBAR_RELEASE)
    @IR(failOn = IRNode.MEMBAR_VOLATILE)
    public long escaping_finalPlain() {
        FinalPlain c = new FinalPlain(l);
        consume(c);
        return c.f1 + c.f2;
    }

    @Test
    @IR(counts = {IRNode.MEMBAR_STORESTORE, "1"})
    @IR(failOn = IRNode.MEMBAR_RELEASE)
    @IR(failOn = IRNode.MEMBAR_VOLATILE)
    public long escaping_finalFinal() {
        FinalFinal c = new FinalFinal(l);
        consume(c);
        return c.f1 + c.f2;
    }

    @Test
    @IR(counts = {IRNode.MEMBAR_RELEASE, "1"})
    @IR(counts = {IRNode.MEMBAR_STORESTORE, "1"})
    @IR(counts = {IRNode.MEMBAR_VOLATILE, "1"})
    public long escaping_plainVolatile() {
        PlainVolatile c = new PlainVolatile(l);
        consume(c);
        return c.f1 + c.f2;
    }

    @Test
    @IR(counts = {IRNode.MEMBAR_RELEASE, "1"})
    @IR(counts = {IRNode.MEMBAR_STORESTORE, "1"})
    @IR(counts = {IRNode.MEMBAR_VOLATILE, "1"})
    public long escaping_volatilePlain() {
        VolatilePlain c = new VolatilePlain(l);
        consume(c);
        return c.f1 + c.f2;
    }

    @Test
    @IR(counts = {IRNode.MEMBAR_RELEASE, "2"})
    @IR(counts = {IRNode.MEMBAR_STORESTORE, "1"})
    @IR(counts = {IRNode.MEMBAR_VOLATILE, "2"})
    public long escaping_volatileVolatile() {
        VolatileVolatile c = new VolatileVolatile(l);
        consume(c);
        return c.f1 + c.f2;
    }

    @Test
    @IR(counts = {IRNode.MEMBAR_RELEASE, "1"})
    @IR(counts = {IRNode.MEMBAR_STORESTORE, "1"})
    @IR(counts = {IRNode.MEMBAR_VOLATILE, "1"})
    public long escaping_finalVolatile() {
        FinalVolatile c = new FinalVolatile(l);
        consume(c);
        return c.f1 + c.f2;
    }

    @Test
    @IR(counts = {IRNode.MEMBAR_RELEASE, "1"})
    @IR(counts = {IRNode.MEMBAR_STORESTORE, "1"})
    @IR(counts = {IRNode.MEMBAR_VOLATILE, "1"})
    public long escaping_volatileFinal() {
        VolatileFinal c = new VolatileFinal(l);
        consume(c);
        return c.f1 + c.f2;
    }

    @Test
    @IR(failOn = IRNode.MEMBAR)
    public long non_escaping_plainPlain() {
        PlainPlain c = new PlainPlain(l);
        return c.f1 + c.f2;
    }

    @Test
    @IR(failOn = IRNode.MEMBAR)
    public long non_escaping_plainFinal() {
        PlainFinal c = new PlainFinal(l);
        return c.f1 + c.f2;
    }

    @Test
    @IR(failOn = IRNode.MEMBAR)
    public long non_escaping_finalPlain() {
        FinalPlain c = new FinalPlain(l);
        return c.f1 + c.f2;
    }

    @Test
    @IR(failOn = IRNode.MEMBAR)
    public long non_escaping_finalFinal() {
        FinalFinal c = new FinalFinal(l);
        return c.f1 + c.f2;
    }

    @Test
    @IR(failOn = IRNode.MEMBAR_RELEASE)
    @IR(failOn = IRNode.MEMBAR_STORESTORE)
    @IR(failOn = IRNode.MEMBAR_VOLATILE)
    @IR(counts = {IRNode.MEMBAR_ACQUIRE, "1"})
    public long non_escaping_plainVolatile() {
        PlainVolatile c = new PlainVolatile(l);
        return c.f1 + c.f2;
    }

    @Test
    @IR(failOn = IRNode.MEMBAR_RELEASE)
    @IR(failOn = IRNode.MEMBAR_STORESTORE)
    @IR(failOn = IRNode.MEMBAR_VOLATILE)
    @IR(counts = {IRNode.MEMBAR_ACQUIRE, "1"})
    public long non_escaping_volatilePlain() {
        VolatilePlain c = new VolatilePlain(l);
        return c.f1 + c.f2;
    }

    @Test
    @IR(failOn = IRNode.MEMBAR_RELEASE)
    @IR(failOn = IRNode.MEMBAR_STORESTORE)
    @IR(failOn = IRNode.MEMBAR_VOLATILE)
    @IR(counts = {IRNode.MEMBAR_ACQUIRE, "2"})
    public long non_escaping_volatileVolatile() {
        VolatileVolatile c = new VolatileVolatile(l);
        return c.f1 + c.f2;
    }

    @Test
    @IR(failOn = IRNode.MEMBAR_RELEASE)
    @IR(failOn = IRNode.MEMBAR_STORESTORE)
    @IR(failOn = IRNode.MEMBAR_VOLATILE)
    @IR(counts = {IRNode.MEMBAR_ACQUIRE, "1"})
    public long non_escaping_finalVolatile() {
        FinalVolatile c = new FinalVolatile(l);
        return c.f1 + c.f2;
    }

    @Test
    @IR(failOn = IRNode.MEMBAR_RELEASE)
    @IR(failOn = IRNode.MEMBAR_STORESTORE)
    @IR(failOn = IRNode.MEMBAR_VOLATILE)
    @IR(counts = {IRNode.MEMBAR_ACQUIRE, "1"})
    public long non_escaping_volatileFinal() {
        VolatileFinal c = new VolatileFinal(l);
        return c.f1 + c.f2;
    }

    @Setup
    Object[] stringBuilderSetup() {
        return new Object[] { "foo", "bar", "baz" };
    }

    @Test
    @Arguments(setup = "stringBuilderSetup")
    @IR(failOn = IRNode.MEMBAR_RELEASE)
    @IR(failOn = IRNode.MEMBAR_VOLATILE)
    @IR(counts = {IRNode.MEMBAR_STORESTORE, "3"})
    public String stringBuilder(String s1, String s2, String s3) {
        return new StringBuilder().append(s1).append(s2).append(s3).toString();
    }
}
