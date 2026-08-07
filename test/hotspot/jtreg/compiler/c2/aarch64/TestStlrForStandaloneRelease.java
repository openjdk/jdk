/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8384941
 * @summary Verify aarch64 standalone release stores lower to stlr when
 *          UseStlrForStandaloneRelease is on, and to dmb ish + str when off.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          jdk.unsupported
 * @requires vm.flagless
 * @requires os.arch == "aarch64" & vm.debug == true &
 *           vm.flavor == "server" & !vm.graal.enabled
 * @run driver compiler.c2.aarch64.TestStlrForStandaloneRelease
 */

package compiler.c2.aarch64;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.List;
import java.util.regex.Pattern;

import jdk.internal.misc.Unsafe;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestStlrForStandaloneRelease {

    @SuppressWarnings({"deprecation", "removal"})
    public static class Payload {
        private static final Unsafe U = Unsafe.getUnsafe();
        private static final sun.misc.Unsafe SUN_U;

        public int    fInt;
        public long   fLong;
        public Object fRef;

        public static final int[]    intArr = new int[1];
        public static final Object[] refArr = new Object[1];

        static final VarHandle VH_INT, VH_LONG, VH_REF;
        static final VarHandle VH_INT_ARR =
                MethodHandles.arrayElementVarHandle(int[].class);
        static final VarHandle VH_REF_ARR =
                MethodHandles.arrayElementVarHandle(Object[].class);

        static final long OFF_INT, OFF_LONG, OFF_REF;

        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                VH_INT  = l.findVarHandle(Payload.class, "fInt",  int.class);
                VH_LONG = l.findVarHandle(Payload.class, "fLong", long.class);
                VH_REF  = l.findVarHandle(Payload.class, "fRef",  Object.class);
                OFF_INT  = U.objectFieldOffset(Payload.class.getDeclaredField("fInt"));
                OFF_LONG = U.objectFieldOffset(Payload.class.getDeclaredField("fLong"));
                OFF_REF  = U.objectFieldOffset(Payload.class.getDeclaredField("fRef"));
                Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                SUN_U = (sun.misc.Unsafe) f.get(null);
            } catch (Throwable t) {
                throw new ExceptionInInitializerError(t);
            }
        }

        public static void testVhIntRelease(Payload p, int v)    { VH_INT.setRelease(p, v); }
        public static void testVhLongRelease(Payload p, long v)  { VH_LONG.setRelease(p, v); }
        public static void testVhRefRelease(Payload p, Object v) { VH_REF.setRelease(p, v); }
        public static void testVhIntArrRelease(int v)            { VH_INT_ARR.setRelease(intArr, 0, v); }
        public static void testVhRefArrRelease(Object v)         { VH_REF_ARR.setRelease(refArr, 0, v); }

        public static void testUnsafeIntRelease(Payload p, int v)    { U.putIntRelease(p, OFF_INT, v); }
        public static void testUnsafeLongRelease(Payload p, long v)  { U.putLongRelease(p, OFF_LONG, v); }
        public static void testUnsafeRefRelease(Payload p, Object v) { U.putReferenceRelease(p, OFF_REF, v); }

        public static void testPutOrderedInt(Payload p, int v)       { SUN_U.putOrderedInt(p, OFF_INT, v); }
        public static void testPutOrderedLong(Payload p, long v)     { SUN_U.putOrderedLong(p, OFF_LONG, v); }
        public static void testPutOrderedObject(Payload p, Object v) { SUN_U.putOrderedObject(p, OFF_REF, v); }

        public static void testLoopRelease(Payload p, int v) {
            for (int i = 0; i < 4; i++) {
                VH_INT.setRelease(p, v + i);
            }
        }

        // Constructor-exit MemBarRelease for final-field publication must
        // not be elided regardless of the flag, otherwise the final write
        // could be observed before the object is published.
        public static final class WithFinal {
            final int x;
            WithFinal(int v) { this.x = v; }
        }
        public static WithFinal sink;
        public static void testCtorFinal(int v) {
            sink = new WithFinal(v);
        }

        public static void run() {
            Payload p = new Payload();
            for (int i = 0; i < 200_000; i++) {
                testVhIntRelease(p, i);
                testVhLongRelease(p, (long) i);
                testVhRefRelease(p, Integer.valueOf(i));
                testVhIntArrRelease(i);
                testVhRefArrRelease(Integer.valueOf(i));
                testUnsafeIntRelease(p, i);
                testUnsafeLongRelease(p, (long) i);
                testUnsafeRefRelease(p, Integer.valueOf(i));
                testPutOrderedInt(p, i);
                testPutOrderedLong(p, (long) i);
                testPutOrderedObject(p, Integer.valueOf(i));
                testLoopRelease(p, i);
                testCtorFinal(i);
            }
            if (p.fInt == 0 || p.fLong != 199_999L || !(p.fRef instanceof Integer)
                    || sink == null || sink.x == 0) {
                throw new RuntimeException("bad payload result");
            }
        }
    }

    // mustKeepBarrier=true means the method's MemBarRelease must NEVER be
    // elided, regardless of the flag (e.g. ctor-exit final-field publication).
    private record Spec(String method, String stlrRegex, String strRegex,
                        boolean mustKeepBarrier) {
        Spec(String m, String stlr, String str) { this(m, stlr, str, false); }
    }

    // PrintOptoAssembly emits "stlrw ... # int" / "stlr ... # int" /
    // "stlrw ... # compressed ptr" (or "# ptr" with -UseCompressedOops).
    // The " ptr" anchor matches both compressed and uncompressed forms.
    private static final List<Spec> SPECS = List.of(
            new Spec("testVhIntRelease",      "\\bstlrw\\b.*#\\s*int\\b", "\\bstrw\\b.*#\\s*int\\b"),
            new Spec("testVhLongRelease",     "\\bstlr\\b.*#\\s*int\\b",  "\\bstr\\b.*#\\s*int\\b"),
            new Spec("testVhRefRelease",      "\\bstlrw?\\b.* ptr\\b",    "\\bstrw?\\b.* ptr\\b"),
            new Spec("testVhIntArrRelease",   "\\bstlrw\\b.*#\\s*int\\b", "\\bstrw\\b.*#\\s*int\\b"),
            new Spec("testVhRefArrRelease",   "\\bstlrw?\\b.* ptr\\b",    "\\bstrw?\\b.* ptr\\b"),
            new Spec("testUnsafeIntRelease",  "\\bstlrw\\b.*#\\s*int\\b", "\\bstrw\\b.*#\\s*int\\b"),
            new Spec("testUnsafeLongRelease", "\\bstlr\\b.*#\\s*int\\b",  "\\bstr\\b.*#\\s*int\\b"),
            new Spec("testUnsafeRefRelease",  "\\bstlrw?\\b.* ptr\\b",    "\\bstrw?\\b.* ptr\\b"),
            new Spec("testPutOrderedInt",     "\\bstlrw\\b.*#\\s*int\\b", "\\bstrw\\b.*#\\s*int\\b"),
            new Spec("testPutOrderedLong",    "\\bstlr\\b.*#\\s*int\\b",  "\\bstr\\b.*#\\s*int\\b"),
            new Spec("testPutOrderedObject",  "\\bstlrw?\\b.* ptr\\b",    "\\bstrw?\\b.* ptr\\b"),
            new Spec("testLoopRelease",       "\\bstlrw\\b.*#\\s*int\\b", "\\bstrw\\b.*#\\s*int\\b"),
            new Spec("testCtorFinal",         "",                          "",                         true)
    );

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "payload".equals(args[0])) {
            Payload.run();
            return;
        }
        check(runJit(true),  true);
        check(runJit(false), false);
    }

    private static OutputAnalyzer runJit(boolean useStlr) throws Exception {
        // -UseStoreStoreForCtor forces ctor exit to emit Op_MemBarRelease
        // instead of Op_MemBarStoreStore, which is what the walker has to
        // discriminate from in the standalone setRelease pattern.
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:-TieredCompilation",
                "-XX:-BackgroundCompilation",
                "-XX:+PrintOptoAssembly",
                "-XX:-UseStoreStoreForCtor",
                "-XX:" + (useStlr ? "+" : "-") + "UseStlrForStandaloneRelease",
                "-XX:CompileCommand=compileonly,"
                    + "compiler.c2.aarch64.TestStlrForStandaloneRelease$Payload::test*",
                "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                TestStlrForStandaloneRelease.class.getName(),
                "payload"
        );
        OutputAnalyzer oa = new OutputAnalyzer(pb.start());
        oa.shouldHaveExitValue(0);
        return oa;
    }

    private static void check(OutputAnalyzer oa, boolean useStlr) {
        List<String> lines = oa.asLines();
        for (Spec s : SPECS) {
            List<String> body = methodBody(lines, s.method());
            if (body == null) {
                throw new RuntimeException("PrintOptoAssembly missing block for "
                        + s.method() + " (useStlr=" + useStlr + ")");
            }
            int elided    = countRegex(body, "membar_release \\(elided\\)");
            int notElided = countRegex(body, "membar_release(?! \\(elided\\))");
            if (s.mustKeepBarrier()) {
                if (notElided < 1 || elided != 0) {
                    fail(s, body, "ctor barrier must stay: elided=" + elided
                            + " notElided=" + notElided);
                }
                continue;
            }
            int stlr = countRegex(body, s.stlrRegex());
            int str  = countRegex(body, s.strRegex());
            if (useStlr) {
                if (stlr < 1 || elided < 1 || notElided != 0) {
                    fail(s, body, "useStlr=on: stlr=" + stlr + " elided=" + elided
                            + " notElided=" + notElided);
                }
            } else {
                int plain = str - stlr;
                if (plain < 1 || notElided < 1 || elided != 0) {
                    fail(s, body, "useStlr=off: plain_str=" + plain
                            + " notElided=" + notElided + " elided=" + elided);
                }
            }
        }
    }

    private static List<String> methodBody(List<String> lines, String name) {
        String anchor = "'" + name + "'";
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.contains("- name:") && l.contains(anchor)) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return null;
        }
        int end = lines.size();
        for (int i = start + 1; i < lines.size(); i++) {
            if (lines.get(i).contains("{method}")) {
                end = i;
                break;
            }
        }
        return lines.subList(start, end);
    }

    private static int countRegex(List<String> body, String regex) {
        Pattern p = Pattern.compile(regex);
        int n = 0;
        for (String l : body) {
            if (p.matcher(l).find()) n++;
        }
        return n;
    }

    private static void fail(Spec s, List<String> body, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append(reason).append(" -- method ").append(s.method()).append('\n');
        for (String l : body) sb.append(l).append('\n');
        throw new RuntimeException(sb.toString());
    }
}
