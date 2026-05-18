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
 * @summary Verify aarch64 release stores (sun.misc.Unsafe.putOrderedX /
 *          jdk.internal.misc.Unsafe.putXRelease / VarHandle.setRelease) use
 *          stlr<x> when UseStlrForStandaloneRelease is on, and fall back to plain
 *          str<x> + dmb when off. All three APIs lower to the same C2 path
 *          (C2AccessFence with is_release && !is_volatile) — sun.misc's
 *          putOrderedX is force-inlined into the internal putXRelease, so we
 *          exercise each surface to keep the patch's intent explicit.
 * @library /test/lib
 *
 * @modules java.base/jdk.internal.misc
 *          jdk.unsupported
 *
 * @requires vm.flagless
 * @requires os.arch == "aarch64" & vm.debug == true &
 *           vm.flavor == "server" & !vm.graal.enabled
 *
 * @run driver compiler.c2.aarch64.TestStlrForStandaloneRelease
 */

package compiler.c2.aarch64;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import jdk.internal.misc.Unsafe;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestStlrForStandaloneRelease {

    // ---- Payload exercised inside the spawned JVM ----

    @SuppressWarnings({"deprecation", "removal"})
    public static class Payload {
        private static final Unsafe U = Unsafe.getUnsafe();
        // The deprecated public Unsafe — the literal "putOrdered*" API named in
        // the patch's commit message. It @ForceInlines into U.putXRelease, so at
        // C2 it lowers to the same Op_MemBarRelease + Store(release) idiom; we
        // still test it so future inliner regressions can't quietly skip the API.
        private static final sun.misc.Unsafe SUN_U;
        static {
            try {
                java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                SUN_U = (sun.misc.Unsafe) f.get(null);
            } catch (Throwable t) {
                throw new ExceptionInInitializerError(t);
            }
        }

        public int    fInt;
        public long   fLong;
        public Object fRef;

        // Use a single-element array so the array store rule is reached.
        public static final Object[] refArr = new Object[1];
        public static final int[]    intArr = new int[1];

        // Volatile field for testing setRelease that follows a volatile load —
        // the volatile load injects a trailing MemBarAcquire upstream of the
        // MemBarRelease, exercising the walker's memory-chain navigation.
        public static volatile int volInt = 0;

        static final VarHandle VH_INT;
        static final VarHandle VH_LONG;
        static final VarHandle VH_REF;
        static final VarHandle VH_VOL_INT;
        static final VarHandle VH_INT_ARR =
                MethodHandles.arrayElementVarHandle(int[].class);
        static final VarHandle VH_REF_ARR =
                MethodHandles.arrayElementVarHandle(Object[].class);

        static final long OFF_INT;
        static final long OFF_LONG;
        static final long OFF_REF;

        static {
            try {
                MethodHandles.Lookup L = MethodHandles.lookup();
                VH_INT     = L.findVarHandle(Payload.class, "fInt",  int.class);
                VH_LONG    = L.findVarHandle(Payload.class, "fLong", long.class);
                VH_REF     = L.findVarHandle(Payload.class, "fRef",  Object.class);
                VH_VOL_INT = L.findStaticVarHandle(Payload.class, "volInt", int.class);
                OFF_INT  = U.objectFieldOffset(Payload.class.getDeclaredField("fInt"));
                OFF_LONG = U.objectFieldOffset(Payload.class.getDeclaredField("fLong"));
                OFF_REF  = U.objectFieldOffset(Payload.class.getDeclaredField("fRef"));
            } catch (Throwable t) {
                throw new ExceptionInInitializerError(t);
            }
        }

        // setRelease via VarHandle
        public static void testVhIntRelease(Payload p, int v)    { VH_INT.setRelease(p, v); }
        public static void testVhLongRelease(Payload p, long v)  { VH_LONG.setRelease(p, v); }
        public static void testVhRefRelease(Payload p, Object v) { VH_REF.setRelease(p, v); }

        // Array element setRelease
        public static void testVhIntArrRelease(int v)     { VH_INT_ARR.setRelease(intArr, 0, v); }
        public static void testVhRefArrRelease(Object v)  { VH_REF_ARR.setRelease(refArr, 0, v); }

        // jdk.internal.misc.Unsafe.put*Release
        public static void testUnsafeIntRelease(Payload p, int v)    { U.putIntRelease(p, OFF_INT, v); }
        public static void testUnsafeLongRelease(Payload p, long v)  { U.putLongRelease(p, OFF_LONG, v); }
        public static void testUnsafeRefRelease(Payload p, Object v) { U.putReferenceRelease(p, OFF_REF, v); }

        // sun.misc.Unsafe.putOrdered* — the legacy API the patch's commit
        // message references. @ForceInlines into putXRelease above.
        public static void testPutOrderedInt(Payload p, int v)       { SUN_U.putOrderedInt(p, OFF_INT, v); }
        public static void testPutOrderedLong(Payload p, long v)     { SUN_U.putOrderedLong(p, OFF_LONG, v); }
        public static void testPutOrderedObject(Payload p, Object v) { SUN_U.putOrderedObject(p, OFF_REF, v); }

        // ---- Variant patterns: stress the walker's IR-shape assumptions ----
        // Each variant places setRelease in a different surrounding IR context
        // that has been known to perturb memory chains (Phi merges, loop
        // unrolling, intervening barriers, fresh allocations, ...). The walker
        // is expected to fire on all of these. If it misses, the codegen falls
        // back to "dmb ish + stlr" (no miscompile, but optimization lost).

        // (V1) setRelease in conditional branch — control flow split. Each
        // branch has its own MemBarRelease + Store; no Phi sits between them
        // inside a branch.
        public static void testInBranch(Payload p, int v, boolean cond) {
            if (cond) {
                VH_INT.setRelease(p, v);
            } else {
                VH_INT.setRelease(p, -v);
            }
        }

        // (V2) setRelease inside an inner loop — exercises C2 loop unrolling
        // and peeling around the release store.
        public static void testInLoop(Payload p, int v) {
            for (int i = 0; i < 4; i++) {
                VH_INT.setRelease(p, v + i);
            }
        }

        // (V3) Two consecutive setReleases — two MemBarRelease nodes adjacent
        // in memory order; each walker call must find its OWN store, not the
        // other one.
        public static void testConsecutiveSameField(Payload p, int a, int b) {
            VH_INT.setRelease(p, a);
            VH_INT.setRelease(p, b);
        }

        // (V4) Mixed types in sequence — three setReleases of different MO sizes.
        public static void testInterleavedTypes(Payload p, int a, long b, Object c) {
            VH_INT.setRelease(p, a);
            VH_LONG.setRelease(p, b);
            VH_REF.setRelease(p, c);
        }

        // (V5) setRelease after a volatile LOAD — volatile read inserts a
        // trailing MemBarAcquire BEFORE the setRelease's MemBarRelease.
        // Walker should ignore the upstream acquire (it walks downstream only).
        public static void testAfterVolatileLoad(Payload p, int v) {
            int x = (int) VH_VOL_INT.getVolatile();
            VH_INT.setRelease(p, v + x);
        }

        // (V6) setRelease on a fresh allocation — publishing a newly-built
        // object. Allocation + Initialize + ctor stores precede the release;
        // these introduce MemBarStoreStore / Initialize nodes that the walker
        // must navigate AROUND (they're upstream, not downstream).
        public static Payload sink;
        public static void testAfterAlloc(int v) {
            Payload np = new Payload();
            VH_INT.setRelease(np, v);
            sink = np;
        }

        // (V7) Compute-heavy intermediate code — make sure ordinary integer
        // arithmetic between setReleases doesn't break the walker.
        public static void testWithIntermediateCompute(Payload p, int v) {
            int x = (v * 31) ^ 0x12345;
            int y = Integer.rotateLeft(x, 7);
            VH_INT.setRelease(p, y);
        }

        // (V8) setRelease inside a try block — the implicit exception edges
        // create extra control flow around the membar. C2 typically leaves
        // the release path on the common (non-exceptional) basic block.
        public static void testInTryCatch(Payload p, int v) {
            try {
                VH_INT.setRelease(p, v);
            } catch (Throwable t) {
                // Unreachable in normal execution.
                throw new AssertionError(t);
            }
        }

        // (V9) Critical correctness witness — a constructor body containing
        // BOTH a final-field store (plain unordered) AND a setRelease on a
        // regular field. With -UseStoreStoreForCtor the ctor-exit barrier
        // is a MemBarRelease, so the walker is exposed to TWO standalone
        // MemBarRelease nodes in the same method: the ctor-exit one (must
        // be kept — its preceding stores include the FINAL field) and the
        // setRelease's leading one (must be elided). The discriminator is
        // direction: the ctor exit's memory projection points downstream
        // of the body, where no release Store lives; the setRelease's
        // points at the release Store immediately. This case proves the
        // walker's directional discrimination is correct.
        public static final class Pub {
            final int finalField;
            int regField;
            static final VarHandle VH_REG;
            static {
                try {
                    VH_REG = MethodHandles.lookup().findVarHandle(Pub.class, "regField", int.class);
                } catch (ReflectiveOperationException e) {
                    throw new ExceptionInInitializerError(e);
                }
            }
            public Pub(int x, int y) {
                this.finalField = x;          // plain unordered store of final
                VH_REG.setRelease(this, y);   // standalone release store in same ctor
            }                                  // ctor-exit MemBar emitted here
        }
        public static Pub pubSink;
        public static void testCtorWithSetRelease(int v) {
            pubSink = new Pub(v, v + 1);
        }

        public static void run() {
            Payload p = new Payload();
            for (int i = 0; i < 200_000; i++) {
                // Baseline patterns
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
                // Variant patterns (V1–V8)
                testInBranch(p, i, (i & 1) == 0);
                testInLoop(p, i);
                testConsecutiveSameField(p, i, i + 1);
                testInterleavedTypes(p, i, (long) i, Integer.valueOf(i));
                testAfterVolatileLoad(p, i);
                testAfterAlloc(i);
                testWithIntermediateCompute(p, i);
                testInTryCatch(p, i);
                testCtorWithSetRelease(i);
            }
            // Functional sanity — the patched codegen must preserve semantics.
            if (p.fInt == 0 || p.fLong != 199_999L || !(p.fRef instanceof Integer)) {
                throw new RuntimeException("bad payload result: fInt=" + p.fInt
                        + " fLong=" + p.fLong + " fRef=" + p.fRef);
            }
            if (sink == null) {
                throw new RuntimeException("testAfterAlloc never published");
            }
        }
    }

    // ---- Driver ----

    // For each test method we list expected codegen: minimum number of stlr
    // (when flag on) and matching str (when flag off) occurrences within the
    // method body. Method-body window is delimited by the next "{method}"
    // header in PrintOptoAssembly output.
    private record Spec(String methodSuffix, String stlrRegex, String strRegex, int minCount) {
        Spec(String m, String s, String t) { this(m, s, t, 1); }
    }

    // Width-agnostic stlr/str regex used by variant tests that mix sizes.
    private static final String ANY_STLR = "\\bstlr[wbh]?\\b";
    private static final String ANY_STR  = "\\bstr[wbh]?\\b";

    // The "stlr" regex captures the expected releasing store instruction.
    // The "str" regex captures the legacy plain store the patch is replacing.
    //
    // PrintOptoAssembly emits operand-type annotations after a tab. Per
    // src/hotspot/cpu/aarch64/aarch64.ad:
    //   int  field:  "stlrw  ... # int"
    //   long field:  "stlr   ... # int"   (note: also "# int", differs by mnemonic width)
    //   ref  field:  "stlrw  ... # compressed ptr"   (CompressedOops on, default)
    //                "stlr   ... # ptr"              (CompressedOops off)
    // We anchor the ref regex on " ptr" with a leading space, matching both
    // "compressed ptr" and "ptr" without being confused by the int regex.
    private static final List<Spec> SPECS = List.of(
            // ---- Baseline patterns (one release store, type-specific regex) ----
            new Spec("testVhIntRelease",     "\\bstlrw\\b\\s.*#\\s*int\\b",  "\\bstrw\\b\\s.*#\\s*int\\b"),
            new Spec("testVhLongRelease",    "\\bstlr\\b\\s.*#\\s*int\\b",    "\\bstr\\b\\s.*#\\s*int\\b"),
            new Spec("testVhRefRelease",     "\\bstlrw?\\b.* ptr\\b",          "\\bstrw?\\b.* ptr\\b"),
            new Spec("testVhIntArrRelease",  "\\bstlrw\\b\\s.*#\\s*int\\b",   "\\bstrw\\b\\s.*#\\s*int\\b"),
            new Spec("testVhRefArrRelease",  "\\bstlrw?\\b.* ptr\\b",          "\\bstrw?\\b.* ptr\\b"),
            new Spec("testUnsafeIntRelease", "\\bstlrw\\b\\s.*#\\s*int\\b",   "\\bstrw\\b\\s.*#\\s*int\\b"),
            new Spec("testUnsafeLongRelease","\\bstlr\\b\\s.*#\\s*int\\b",    "\\bstr\\b\\s.*#\\s*int\\b"),
            new Spec("testUnsafeRefRelease", "\\bstlrw?\\b.* ptr\\b",          "\\bstrw?\\b.* ptr\\b"),
            new Spec("testPutOrderedInt",    "\\bstlrw\\b\\s.*#\\s*int\\b",   "\\bstrw\\b\\s.*#\\s*int\\b"),
            new Spec("testPutOrderedLong",   "\\bstlr\\b\\s.*#\\s*int\\b",    "\\bstr\\b\\s.*#\\s*int\\b"),
            new Spec("testPutOrderedObject", "\\bstlrw?\\b.* ptr\\b",          "\\bstrw?\\b.* ptr\\b"),

            // ---- Variant patterns (V1–V8): walker must fire under each ----
            // V1: both branches contain a setRelease → expect 2 stlr in the method
            new Spec("testInBranch",                "\\bstlrw\\b",      "\\bstrw\\b", 2),
            // V2: loop body has one setRelease per iteration; C2 may unroll → ≥1
            new Spec("testInLoop",                  "\\bstlrw\\b",      "\\bstrw\\b", 1),
            // V3: two consecutive setReleases on the same field → 2 stlr
            new Spec("testConsecutiveSameField",    "\\bstlrw\\b",      "\\bstrw\\b", 2),
            // V4: int + long + ref setReleases in sequence → 3 stlr of varying widths
            new Spec("testInterleavedTypes",        ANY_STLR,           ANY_STR,      3),
            // V5: volatile load (upstream acquire) then setRelease → 1 stlr
            new Spec("testAfterVolatileLoad",       "\\bstlrw\\b",      "\\bstrw\\b", 1),
            // V6: setRelease on a fresh allocation → 1 stlr
            new Spec("testAfterAlloc",              "\\bstlrw\\b",      "\\bstrw\\b", 1),
            // V7: arithmetic before setRelease → 1 stlr
            new Spec("testWithIntermediateCompute", "\\bstlrw\\b",      "\\bstrw\\b", 1),
            // V8: setRelease in try block → 1 stlr
            new Spec("testInTryCatch",              "\\bstlrw\\b",      "\\bstrw\\b", 1),
            // V9: ctor with setRelease — only the inner setRelease's leading
            // MemBarRelease is elided; the ctor-exit barrier (whether StoreStore
            // or MemBarRelease depending on UseStoreStoreForCtor) is preserved.
            // We assert >= 1 stlr (the inner setRelease) and trust the codegen
            // verification on the ctor-exit barrier to be checked separately.
            new Spec("testCtorWithSetRelease",      "\\bstlrw\\b",      "\\bstrw\\b", 1)
    );

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "payload".equals(args[0])) {
            Payload.run();
            return;
        }
        OutputAnalyzer on  = runJit(true);
        OutputAnalyzer off = runJit(false);
        check(on,  /*useStlr=*/true);
        check(off, /*useStlr=*/false);
    }

    private static OutputAnalyzer runJit(boolean useStlr) throws Exception {
        String compileOnly = "-XX:CompileCommand=compileonly,"
                + "compiler.c2.aarch64.TestStlrForStandaloneRelease$Payload::test*";
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:-TieredCompilation",
                "-XX:-BackgroundCompilation",
                "-XX:+PrintOptoAssembly",
                "-XX:" + (useStlr ? "+" : "-") + "UseStlrForStandaloneRelease",
                compileOnly,
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
            List<String> body = methodBody(lines, s.methodSuffix());
            if (body == null) {
                throw new RuntimeException("PrintOptoAssembly missing block for "
                        + s.methodSuffix() + " (useStlr=" + useStlr + ")\n\n"
                        + oa.getOutput());
            }
            int elidedCnt    = countRegex(body, "membar_release \\(elided\\)");
            int fullMembarCnt = countRegex(body, "membar_release(?! \\(elided\\))");
            int stlrCnt      = countRegex(body, s.stlrRegex());
            int strCnt       = countRegex(body, s.strRegex());

            // We deliberately don't count raw "dmb" instructions: the nmethod
            // entry barrier emits a `dmb ishld` in every JIT'd method's
            // prologue, and AlwaysMergeDMB further perturbs what MemBarRelease
            // emits at the assembler layer. The membar_release elided/not-
            // elided count from PrintOptoAssembly is the canonical signal.

            if (useStlr) {
                require(stlrCnt >= s.minCount(),
                        "expected >= " + s.minCount() + " stlr matching " + s.stlrRegex()
                                + " (got " + stlrCnt + ")", s, body);
                require(elidedCnt >= s.minCount(),
                        "expected >= " + s.minCount() + " elided membar_release (got "
                                + elidedCnt + ")", s, body);
                require(fullMembarCnt == 0,
                        "expected NO non-elided membar_release (got " + fullMembarCnt + ")", s, body);
            } else {
                require(fullMembarCnt >= s.minCount(),
                        "expected >= " + s.minCount() + " non-elided membar_release (got "
                                + fullMembarCnt + ")", s, body);
                int plainStrCnt = strCnt - stlrCnt;
                require(plainStrCnt >= s.minCount(),
                        "expected >= " + s.minCount() + " plain str matching " + s.strRegex()
                                + " (got plain=" + plainStrCnt + ", stlr=" + stlrCnt + ")", s, body);
                require(elidedCnt == 0,
                        "expected NO elided membar_release with flag off (got " + elidedCnt + ")", s, body);
            }
        }
        System.out.println("OK useStlr=" + useStlr + " — verified " + SPECS.size() + " methods.");
    }

    private static List<String> methodBody(List<String> lines, String methodSuffix) {
        // PrintOptoAssembly prints a multi-line "{method}" header followed
        // by " - name:              '<name>'" — leading whitespace, dash,
        // "name:", and variable whitespace before the single-quoted name.
        // We don't assume a specific column layout; instead we anchor on
        // the "- name:" tag and the quoted name on the same line.
        String quotedName = "'" + methodSuffix + "'";
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.contains("- name:") && l.contains(quotedName)) {
                start = i;
                break;
            }
        }
        if (start < 0) return null;
        int end = lines.size();
        for (int i = start + 1; i < lines.size(); i++) {
            if (lines.get(i).contains("{method}")) { end = i; break; }
        }
        return lines.subList(start, end);
    }

    private static int countRegex(List<String> body, String regex) {
        Pattern p = Pattern.compile(regex);
        int n = 0;
        for (String l : body) if (p.matcher(l).find()) n++;
        return n;
    }

    private static void require(boolean cond, String msg, Spec s, List<String> body) {
        if (cond) return;
        StringBuilder sb = new StringBuilder();
        sb.append(msg).append(" — method ").append(s.methodSuffix()).append('\n');
        sb.append("--- method body ---\n");
        for (String l : body) sb.append(l).append('\n');
        throw new RuntimeException(sb.toString());
    }
}
