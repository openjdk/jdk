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
 * @summary Functional differential test for UseStlrForStandaloneRelease.
 *          Spawns two child JVMs running the same fuzz-generated workload —
 *          one with -XX:+UseStlrForStandaloneRelease and one with -XX:-...
 *          — and asserts that both produce byte-identical observable output
 *          (a fingerprint accumulated over all release-store sites). The
 *          optimization must NEVER change observable single-threaded program
 *          behavior; this test backs that contract regardless of build flavor
 *          (release build is supported because no PrintOptoAssembly is used).
 *
 *          Complements TestStlrForStandaloneReleaseFuzz (which verifies the
 *          codegen but requires vm.debug). Failure of this test would indicate
 *          a miscompile.
 *
 * @library /test/lib
 *
 * @modules java.base/jdk.internal.misc
 *          jdk.compiler
 *          jdk.unsupported
 *
 * @requires vm.flagless
 * @requires os.arch == "aarch64" & vm.flavor == "server" & !vm.graal.enabled
 *
 * @run driver compiler.c2.aarch64.TestStlrForStandaloneReleaseDifferential
 */

package compiler.c2.aarch64;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestStlrForStandaloneReleaseDifferential {

    private static final int N_METHODS = 30;
    private static final int ITERATIONS_PER_METHOD = 50_000;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "payload".equals(args[0])) {
            long seed = Long.parseLong(args[1]);
            int n = Integer.parseInt(args[2]);
            int iters = Integer.parseInt(args[3]);
            runPayload(seed, n, iters);
            return;
        }
        long seed = (args.length > 0) ? Long.parseLong(args[0]) : System.nanoTime();
        System.out.println("[diff] seed=" + seed + " methods=" + N_METHODS
                           + " iters=" + ITERATIONS_PER_METHOD);

        String onFp  = runWorker(seed, true);
        String offFp = runWorker(seed, false);
        if (!onFp.equals(offFp)) {
            throw new RuntimeException("[diff] MISMATCH: +flag=" + onFp + " -flag=" + offFp
                    + " (seed=" + seed + ")");
        }
        System.out.println("[diff] OK: fingerprints match (" + onFp + ")");
    }

    private static String runWorker(long seed, boolean useStlr) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-XX:" + (useStlr ? "+" : "-") + "UseStlrForStandaloneRelease",
                "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                TestStlrForStandaloneReleaseDifferential.class.getName(),
                "payload",
                Long.toString(seed),
                Integer.toString(N_METHODS),
                Integer.toString(ITERATIONS_PER_METHOD)
        );
        OutputAnalyzer oa = new OutputAnalyzer(pb.start());
        oa.shouldHaveExitValue(0);
        // The worker prints exactly one line of the form "FP=<hex>" at end.
        for (String l : oa.asLines()) {
            if (l.startsWith("FP=")) return l.substring(3);
        }
        throw new RuntimeException("[diff] worker output had no FP= line, useStlr="
                + useStlr + "\n" + oa.getOutput());
    }

    // ---- Worker ----

    private record Slot(int storeType, int ctx, int valueSalt) {}

    private static List<Slot> plan(long seed, int n) {
        Random rng = new Random(seed);
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int storeType = rng.nextInt(3);  // 0=int, 1=long, 2=ref-as-int-tag
            int ctx       = rng.nextInt(5);   // 0=bare 1=branch 2=loop 3=try 4=compute
            int salt      = rng.nextInt();
            slots.add(new Slot(storeType, ctx, salt));
        }
        return slots;
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static void runPayload(long seed, int n, int iters) throws Exception {
        List<Slot> plan = plan(seed, n);
        String src = generateSource(plan);
        byte[] bytes = InMemoryJavaCompiler.compile("DiffPayload", src,
                "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");
        DiffClassLoader cl = new DiffClassLoader(bytes);
        Class<?> kls = cl.loadClass("DiffPayload");
        Object instance = kls.getDeclaredConstructor().newInstance();

        // Run each generated method `iters` times so C2 kicks in, then call
        // the deterministic checksum() method which reads back state.
        for (int i = 0; i < n; i++) {
            Method m = kls.getDeclaredMethod("test" + i, kls, int.class);
            for (int j = 0; j < iters; j++) {
                m.invoke(null, instance, j);
            }
        }
        long fp = (long) kls.getDeclaredMethod("checksum", kls).invoke(null, instance);
        // Print the single line the driver greps for.
        System.out.println("FP=" + Long.toHexString(fp));
    }

    private static String generateSource(List<Slot> plan) {
        StringBuilder s = new StringBuilder(16_000);
        s.append("""
                import java.lang.invoke.MethodHandles;
                import java.lang.invoke.VarHandle;
                import jdk.internal.misc.Unsafe;
                public class DiffPayload {
                    public int    fInt;
                    public long   fLong;
                    public Object fRef;
                    private static final Unsafe U = Unsafe.getUnsafe();
                    private static final VarHandle VH_INT, VH_LONG, VH_REF;
                    static {
                      try {
                        MethodHandles.Lookup L = MethodHandles.lookup();
                        VH_INT  = L.findVarHandle(DiffPayload.class, "fInt",  int.class);
                        VH_LONG = L.findVarHandle(DiffPayload.class, "fLong", long.class);
                        VH_REF  = L.findVarHandle(DiffPayload.class, "fRef",  Object.class);
                      } catch (Throwable t) { throw new ExceptionInInitializerError(t); }
                    }
                    public static long checksum(DiffPayload p) {
                      long h = p.fInt;
                      h = h * 1315423911L ^ p.fLong;
                      h = h * 1315423911L ^ (p.fRef == null ? 0 : p.fRef.hashCode());
                      return h;
                    }
                """);
        for (int i = 0; i < plan.size(); i++) {
            Slot slot = plan.get(i);
            emitMethod(s, i, slot);
        }
        s.append("}\n");
        return s.toString();
    }

    private static void emitMethod(StringBuilder s, int idx, Slot slot) {
        // Method body: do one release store. Value computed from v ^ salt so each
        // method has a distinct value sequence, accumulating into the fingerprint.
        String op = switch (slot.storeType()) {
            case 0 -> "VH_INT.setRelease(p, v ^ " + slot.valueSalt() + ")";
            case 1 -> "VH_LONG.setRelease(p, ((long)v) ^ " + slot.valueSalt() + "L)";
            case 2 -> "VH_REF.setRelease(p, Integer.valueOf(v ^ " + slot.valueSalt() + "))";
            default -> throw new AssertionError();
        };
        s.append("  public static void test").append(idx)
         .append("(DiffPayload p, int v) {\n");
        switch (slot.ctx()) {
            case 0 -> s.append("    ").append(op).append(";\n");
            case 1 -> s.append("    if ((v & 1) == 0) { ").append(op).append("; }\n");
            case 2 -> s.append("    for (int k = 0; k < 2; k++) { ").append(op).append("; }\n");
            case 3 -> s.append("    try { ").append(op).append("; } catch (Throwable t) {}\n");
            case 4 -> s.append("    int _c = Integer.rotateLeft(v ^ 0x12345, 7);\n")
                       .append("    ").append(op.replaceAll("\\bv\\b", "_c")).append(";\n");
            default -> throw new AssertionError();
        }
        s.append("  }\n");
    }

    private static class DiffClassLoader extends ClassLoader {
        private final byte[] bytes;
        DiffClassLoader(byte[] bytes) { this.bytes = bytes; }
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals("DiffPayload")) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.findClass(name);
        }
    }
}
