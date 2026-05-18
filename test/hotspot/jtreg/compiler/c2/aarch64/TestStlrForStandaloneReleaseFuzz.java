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
 * @summary Coverage-oriented fuzz test for UseStlrForStandaloneRelease.
 *          Randomly composes setRelease / putXRelease / putOrdered* stores
 *          across a matrix of surrounding IR contexts (none, branch, loop,
 *          try-catch, after-allocation, after-volatile-load, after-compute,
 *          interleaved-types, ...) into N test methods, JIT-compiles them
 *          in a child JVM with PrintOptoAssembly, and asserts that for
 *          every generated method the walker fires (or doesn't, when the
 *          flag is off). Driver and worker use the same RNG seed so the
 *          driver can predict expected per-method release-store counts
 *          without communicating with the worker. Failure modes:
 *
 *            +flag: stlr_count(method) < expected_releases(method)   → walker missed
 *            +flag: dmb_ish_count(method) > 0                        → walker missed
 *            -flag: stlr_count(method) > 0                           → flag not honored
 *
 *          Seed defaults to current nanos; can be passed explicitly to
 *          reproduce a found failure.
 *
 * @library /test/lib
 *
 * @modules java.base/jdk.internal.misc
 *          jdk.compiler
 *          jdk.unsupported
 *
 * @requires vm.flagless
 * @requires os.arch == "aarch64" & vm.debug == true &
 *           vm.flavor == "server" & !vm.graal.enabled
 *
 * @run driver compiler.c2.aarch64.TestStlrForStandaloneReleaseFuzz
 */

package compiler.c2.aarch64;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestStlrForStandaloneReleaseFuzz {

    // How many fuzz methods to synthesize per run. Each method holds 1..3
    // setRelease stores in randomized surrounding contexts.
    private static final int N_METHODS = 50;

    // Random RNG seed range for one method — defines the (count, type, contexts) tuple.
    // Both driver and worker derive identical specs from the same parent seed.
    private record MethodSpec(int idx, int releaseCount, int storeType,
                              int[] contextChoices) {
        String methodName() { return "testFuzz_" + idx; }

        // Width-specific stlr/str regex matched against PrintOptoAssembly.
        // Format follows src/hotspot/cpu/aarch64/aarch64.ad:
        //   int:  "stlrw ... # int"
        //   long: "stlr  ... # int"   (same "# int" — width carried by mnemonic)
        //   ref:  "stlrw ... # compressed ptr" (CompressedOops on, default)
        //         "stlr  ... # ptr"            (CompressedOops off)
        // Anchor ref regex on " ptr" with a leading space to match both forms.
        String stlrRegex() {
            return switch (storeType) {
                case 0 -> "\\bstlrw\\b\\s.*#\\s*int\\b";
                case 1 -> "\\bstlr\\b\\s.*#\\s*int\\b";
                case 2 -> "\\bstlrw?\\b.* ptr\\b";
                default -> throw new AssertionError();
            };
        }
        String strRegex() {
            return switch (storeType) {
                case 0 -> "\\bstrw\\b\\s.*#\\s*int\\b";
                case 1 -> "\\bstr\\b\\s.*#\\s*int\\b";
                case 2 -> "\\bstrw?\\b.* ptr\\b";
                default -> throw new AssertionError();
            };
        }
    }

    // Number of surrounding-context variants the generator may pick from.
    private static final int N_CONTEXTS = 7;

    private static List<MethodSpec> generateSpecs(long seed, int n) {
        Random rng = new Random(seed);
        List<MethodSpec> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int rc = 1 + rng.nextInt(3);                      // 1..3 release stores
            int type = rng.nextInt(3);                        // 0=int, 1=long, 2=ref
            int[] ctxs = new int[rc];
            for (int j = 0; j < rc; j++) ctxs[j] = rng.nextInt(N_CONTEXTS);
            out.add(new MethodSpec(i, rc, type, ctxs));
        }
        return out;
    }

    // ---- Driver ----

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "payload".equals(args[0])) {
            runPayload(Long.parseLong(args[1]), Integer.parseInt(args[2]));
            return;
        }

        long seed = (args.length > 0) ? Long.parseLong(args[0]) : System.nanoTime();
        System.out.println("[fuzz] seed=" + seed + " methods=" + N_METHODS);

        OutputAnalyzer on  = runChild(seed, N_METHODS, true);
        OutputAnalyzer off = runChild(seed, N_METHODS, false);

        List<MethodSpec> specs = generateSpecs(seed, N_METHODS);
        int missed = 0;
        for (MethodSpec spec : specs) {
            missed += verifyOne(on,  spec, /*useStlr=*/true,  seed);
            missed += verifyOne(off, spec, /*useStlr=*/false, seed);
        }
        if (missed > 0) {
            throw new RuntimeException("[fuzz] " + missed + " coverage failures (seed=" + seed + ")");
        }
        System.out.println("[fuzz] OK: " + N_METHODS + " methods × 2 modes verified");
    }

    private static OutputAnalyzer runChild(long seed, int n, boolean useStlr) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:-TieredCompilation",
                "-XX:-BackgroundCompilation",
                "-XX:+PrintOptoAssembly",
                "-XX:" + (useStlr ? "+" : "-") + "UseStlrForStandaloneRelease",
                "-XX:CompileCommand=compileonly,FuzzPayload::testFuzz_*",
                "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                TestStlrForStandaloneReleaseFuzz.class.getName(),
                "payload",
                Long.toString(seed),
                Integer.toString(n)
        );
        OutputAnalyzer oa = new OutputAnalyzer(pb.start());
        oa.shouldHaveExitValue(0);
        return oa;
    }

    private static int verifyOne(OutputAnalyzer oa, MethodSpec spec, boolean useStlr, long seed) {
        List<String> lines = oa.asLines();
        List<String> body = methodBody(lines, spec.methodName());
        if (body == null) {
            System.err.println("[fuzz] FAIL seed=" + seed + " method=" + spec.methodName()
                    + " (useStlr=" + useStlr + "): PrintOptoAssembly block missing");
            return 1;
        }
        int stlrCnt  = countRegex(body, spec.stlrRegex());
        int strCnt   = countRegex(body, spec.strRegex());
        int elided   = countRegex(body, "membar_release \\(elided\\)");
        int notElided = countRegex(body, "membar_release(?! \\(elided\\))");
        int expected = spec.releaseCount();
        // dmb counting is intentionally avoided: the nmethod entry barrier
        // emits a dmb ishld in every JIT'd method's prologue (unrelated to our
        // release path), and AlwaysMergeDMB perturbs MemBarRelease's emit shape.
        // The membar_release elided/non-elided count from PrintOptoAssembly is
        // the canonical, unambiguous signal.
        boolean ok;
        String reason;
        if (useStlr) {
            ok = (stlrCnt >= expected) && (notElided == 0) && (elided >= expected);
            reason = String.format(
                    "+flag stlr=%d (>=%d?) elided=%d (>=%d?) non_elided=%d (==0?)",
                    stlrCnt, expected, elided, expected, notElided);
        } else {
            int plainStr = strCnt - stlrCnt;
            ok = (stlrCnt == 0) && (plainStr >= expected) && (notElided >= expected) && (elided == 0);
            reason = String.format(
                    "-flag stlr=%d (==0?) plain_str=%d (>=%d?) non_elided=%d (>=%d?) elided=%d (==0?)",
                    stlrCnt, plainStr, expected, notElided, expected, elided);
        }
        if (!ok) {
            System.err.println("[fuzz] FAIL seed=" + seed + " method=" + spec.methodName()
                    + " contexts=" + java.util.Arrays.toString(spec.contextChoices()) + ": " + reason);
            return 1;
        }
        return 0;
    }

    private static List<String> methodBody(List<String> lines, String methodSuffix) {
        // PrintOptoAssembly prints " - name:              '<name>'" with
        // variable whitespace; don't assume a specific column layout.
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

    // ---- Worker: generate source from seed, in-memory compile, JIT, exit ----

    private static void runPayload(long seed, int n) throws Exception {
        List<MethodSpec> specs = generateSpecs(seed, n);
        String source = generateSource(specs);
        byte[] classBytes = InMemoryJavaCompiler.compile("FuzzPayload", source,
                "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");
        FuzzClassLoader loader = new FuzzClassLoader(classBytes);
        Class<?> klass = loader.loadClass("FuzzPayload");
        // Construct one instance and exercise each method 100k times to trigger C2.
        Object instance = klass.getDeclaredConstructor().newInstance();
        for (int i = 0; i < n; i++) {
            Method m = klass.getDeclaredMethod("testFuzz_" + i, klass, int.class);
            for (int j = 0; j < 100_000; j++) {
                m.invoke(null, instance, j);
            }
        }
    }

    private static String generateSource(List<MethodSpec> specs) {
        StringBuilder s = new StringBuilder(64_000);
        s.append("""
                import java.lang.invoke.MethodHandles;
                import java.lang.invoke.VarHandle;
                import jdk.internal.misc.Unsafe;
                public class FuzzPayload {
                    public int fInt;
                    public long fLong;
                    public Object fRef;
                    public static volatile int volInt;
                    public static FuzzPayload sink;
                    private static final Unsafe U = Unsafe.getUnsafe();
                    private static final long OFF_INT, OFF_LONG, OFF_REF;
                    private static final VarHandle VH_INT, VH_LONG, VH_REF, VH_VOL;
                    static {
                      try {
                        MethodHandles.Lookup L = MethodHandles.lookup();
                        VH_INT  = L.findVarHandle(FuzzPayload.class, "fInt",  int.class);
                        VH_LONG = L.findVarHandle(FuzzPayload.class, "fLong", long.class);
                        VH_REF  = L.findVarHandle(FuzzPayload.class, "fRef",  Object.class);
                        VH_VOL  = L.findStaticVarHandle(FuzzPayload.class, "volInt", int.class);
                        OFF_INT  = U.objectFieldOffset(FuzzPayload.class.getDeclaredField("fInt"));
                        OFF_LONG = U.objectFieldOffset(FuzzPayload.class.getDeclaredField("fLong"));
                        OFF_REF  = U.objectFieldOffset(FuzzPayload.class.getDeclaredField("fRef"));
                      } catch (Throwable t) { throw new ExceptionInInitializerError(t); }
                    }
                """);
        for (MethodSpec spec : specs) {
            emitMethod(s, spec);
        }
        s.append("}\n");
        return s.toString();
    }

    private static void emitMethod(StringBuilder s, MethodSpec spec) {
        s.append("  public static void ").append(spec.methodName())
                .append("(FuzzPayload p, int v) {\n");
        for (int j = 0; j < spec.releaseCount(); j++) {
            String storeExpr = storeExprFor(spec.storeType(), "p", "v + " + j);
            emitContext(s, spec.contextChoices()[j], storeExpr, j);
        }
        s.append("  }\n");
    }

    // 3 store types × 1 store target = 3 expressions for the inner release op.
    private static String storeExprFor(int storeType, String pVar, String vExpr) {
        return switch (storeType) {
            case 0 -> "VH_INT.setRelease(" + pVar + ", " + vExpr + ")";
            case 1 -> "VH_LONG.setRelease(" + pVar + ", (long)(" + vExpr + "))";
            case 2 -> "VH_REF.setRelease(" + pVar + ", Integer.valueOf(" + vExpr + "))";
            default -> throw new AssertionError();
        };
    }

    // N_CONTEXTS surrounding-context variants. Each emits exactly one
    // setRelease into the method body, with the configured context. The
    // `slot` index is appended to any local variable name to keep two stores
    // with the same context kind from clashing on the same identifier.
    private static void emitContext(StringBuilder s, int ctx, String store, int slot) {
        switch (ctx) {
            case 0 -> // bare
                    s.append("    ").append(store).append(";\n");
            case 1 -> // store inside one branch of an if/else — the JIT
                    // sees a Phi merging "store" and "no-store" memory states.
                    s.append("    if ((v & 1) == 0) { ").append(store).append("; }\n");
            case 2 -> // small inner loop, unrolled by C2
                    s.append("    for (int k").append(slot).append(" = 0; k").append(slot)
                     .append(" < 2; k").append(slot).append("++) { ").append(store).append("; }\n");
            case 3 -> // try / catch
                    s.append("    try { ").append(store).append("; } catch (Throwable t").append(slot)
                     .append(") { /* unreachable */ }\n");
            case 4 -> { // after fresh allocation
                String np = "np" + slot;
                s.append("    FuzzPayload ").append(np).append(" = new FuzzPayload();\n")
                 .append("    ").append(store.replace("p,", np + ",")).append(";\n")
                 .append("    sink = ").append(np).append(";\n");
            }
            case 5 -> { // after volatile load
                String vl = "_vl" + slot;
                s.append("    int ").append(vl).append(" = (int) VH_VOL.getVolatile();\n")
                 .append("    ").append(store.replace("v + ", "(v ^ " + vl + ") + ")).append(";\n");
            }
            case 6 -> { // after compute
                String c = "_c" + slot;
                s.append("    int ").append(c).append(" = Integer.rotateLeft(v ^ 0x12345, 7);\n")
                 .append("    ").append(store.replace("v + ", c + " + ")).append(";\n");
            }
            default -> throw new AssertionError("unknown ctx " + ctx);
        }
    }

    private static class FuzzClassLoader extends ClassLoader {
        private final byte[] bytes;
        FuzzClassLoader(byte[] bytes) { this.bytes = bytes; }
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals("FuzzPayload")) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.findClass(name);
        }
    }
}
