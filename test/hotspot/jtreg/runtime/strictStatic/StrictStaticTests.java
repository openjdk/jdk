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

/* @test id=default
 * @bug 8888888
 * @summary test tracking of strict static fields
 * @run main/othervm strictStatic.StrictStaticTests
 *
 * @test id=C1only
 * @run main/othervm -XX:TieredStopAtLevel=2 -Xcomp -Xbatch strictStatic.StrictStaticTests
 *
 * @test id=C2only
 * @run main/othervm -XX:-TieredCompilation -Xcomp -Xbatch strictStatic.StrictStaticTests
 *
 * @test id=EnforceStrictStatics-2
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:EnforceStrictStatics=2 -DXX_EnforceStrictStatics=2 strictStatic.StrictStaticTests
 *
 * @test id=EnforceStrictStatics-3
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:EnforceStrictStatics=3 -DXX_EnforceStrictStatics=3 strictStatic.StrictStaticTests
 */

package strictStatic;

import java.lang.classfile.*;
import java.lang.constant.*;
import java.lang.reflect.*;
import java.lang.invoke.*;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;
import static java.lang.constant.ConstantDescs.*;

public class StrictStaticTests {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final String THIS_PACKAGE = LOOKUP.lookupClass().getPackageName();

    static Class<?> buildClass(String className,
                               Class<?> staticType,
                               int writeCount,   // how many putstatics? (0=>error)
                               byte readFlag,    // read before (-1) or after (1)?
                               int extraCount,   // how many extra strict statics?
                               boolean finals    // make them all finals?
                               ) {
        ClassDesc cn = ClassDesc.of(THIS_PACKAGE+"."+className);
        ClassDesc CD_Integer = Integer.class.describeConstable().orElseThrow();
        String    VO_NAME = "valueOf";
        MethodTypeDesc VO_TYPE = MethodTypeDesc.of(CD_Integer, CD_int);
        String    SS_NAME = "SS";
        ClassDesc SS_TYPE = staticType.describeConstable().orElseThrow();
        String    XS_NAME = "EXTRAS";
        ClassDesc XS_TYPE = CD_boolean;
        String    PS_NAME = "PLAIN";
        ClassDesc PS_TYPE = CD_byte;

        boolean prim = staticType.isPrimitive();
        boolean pop2 = staticType == double.class;
        final ConstantDesc SS_INIT, SS_INIT_2;
        if (!prim) {
            SS_INIT = "foo";
            SS_INIT_2 = null;
        } else if (!pop2) {
            SS_INIT = 1;
            SS_INIT_2 = 0;
        } else {
            SS_INIT = 3.14;
            SS_INIT_2 = 0.0;
        }
        int mods = (ClassFile.ACC_STATIC | ClassFile.ACC_STRICT |
                    (finals ? ClassFile.ACC_FINAL : 0));
        byte[] classBytes = ClassFile.of().build(cn, clb -> {
                clb.withField(SS_NAME, SS_TYPE, mods);
                for (int i = 0; i < extraCount; i++) {
                    clb.withField(XS_NAME+i, XS_TYPE, mods);
                    clb.withField(PS_NAME+i, PS_TYPE, mods & ~ClassFile.ACC_STRICT);
                }
                clb.withMethodBody(CLASS_INIT_NAME, MTD_void, ClassFile.ACC_STATIC, cob -> {
                        // always store into the extra strict static(s)
                        for (int i = 0; i < extraCount/2; i++) {
                            cob.loadConstant(i&1);
                            cob.putstatic(cn, XS_NAME+i, XS_TYPE);
                        }
                        if (readFlag < 0) {
                            // perform an early read, which must fail
                            cob.getstatic(cn, SS_NAME, SS_TYPE);
                            if (pop2) cob.pop2(); else cob.pop();
                        }
                        // perform any writes on the test field
                        ConstantDesc initializer = SS_INIT;
                        for (int i = 0; i < writeCount; i++) {
                            if (i == 1 && readFlag > 0) {
                                // do an extra read after the first write
                                if (readFlag > 0) {
                                    cob.getstatic(cn, SS_NAME, SS_TYPE);
                                    if (pop2) cob.pop2(); else cob.pop();
                                }
                            }
                            if (i > 0) initializer = SS_INIT_2;
                            cob.loadConstant(initializer);
                            cob.putstatic(cn, SS_NAME, SS_TYPE);
                            // if we write zero times we must fail
                        }
                        if (readFlag > 0) {
                            // do more extra reads
                            cob.getstatic(cn, SS_NAME, SS_TYPE);
                            if (prim) {
                                if (pop2) cob.pop2(); else cob.pop();
                            } else {
                                cob.loadConstant(initializer);
                                var L_skip = cob.newLabel();
                                cob.if_acmpeq(L_skip);
                                cob.loadConstant(null);
                                cob.athrow();  // NPE!
                                cob.labelBinding(L_skip);
                            }
                        }
                        // finish storing into the extra strict static(s)
                        for (int i = extraCount/2; i < extraCount; i++) {
                            cob.loadConstant(i&1);
                            cob.putstatic(cn, XS_NAME+i, XS_TYPE);
                        }
                        cob.return_();
                    });
            });
        var vererrs = ClassFile.of().verify(classBytes);
        if (vererrs != null && !vererrs.isEmpty()) {
            System.out.println(vererrs);
            var cm = ClassFile.of().parse(classBytes);
            System.out.println("" + cm + cm.fields() + cm.methods() + cm.methods().get(0).code().orElseThrow().elementList());
        }
        ++COUNT;
        try {
            return LOOKUP.defineHiddenClass(classBytes, false).lookupClass();
        } catch (IllegalAccessException ex) {
            throw new AssertionError(ex);
        }
    }

    static int COUNT = 0;

    private static final Class<?> STATIC_TYPES[] = {
        Object.class,
        String.class,
        int.class,
        boolean.class,
        double.class
    };

    static boolean isSSFailure(Throwable ex) {
        return ex instanceof ExceptionInInitializerError &&
            ex.getCause() instanceof IllegalStateException;
    }
    static void testPositives() {
        testPositives(false);
        testPositives(true);
    }
    static void testPositives(boolean finals) {
        for (var staticType : STATIC_TYPES) {
            for (int writeCount = 1; writeCount <= 3; writeCount++) {
                for (byte readFlag = 0; readFlag <= 1; readFlag++) {
                    if (writeCount > readFlag && XX_EnforceStrictStatics >= 2)
                        continue;
                    for (int extraCount = 0; extraCount <= 3; extraCount++) {
                        if (extraCount > 0 && staticType != String.class)  continue;
                        var cn = String.format("Positive_T%s%s_W%d%s%s",
                                               staticType.getSimpleName(),
                                               (finals ? "_SSFinal" : ""),
                                               writeCount,
                                               (readFlag > 0 ? "_Rafter" :
                                                readFlag < 0 ? "_Rbefore" : ""),
                                               (extraCount > 0 ? "_E"+extraCount : ""));
                        var cls = buildClass(cn, staticType, writeCount, readFlag, extraCount, finals);
                        try {
                            LOOKUP.ensureInitialized(cls);
                            if (VERBOSE)
                                System.out.printf("ok: %s: no throw\n", cn);
                        } catch (Throwable ex) {
                            reportThrow(false, ex, cn);
                        }
                    }
                }
            }
        }
    }
    static void testFailedWrites() {
        testFailedWrites(false);
        testFailedWrites(true);
    }
    static void testFailedWrites(boolean finals) {
        for (var staticType : STATIC_TYPES) {
            for (int writeCount = 0; writeCount <= 2; writeCount++) {
                for (byte readFlag = 0; readFlag <= 1; readFlag++) {
                    if (readFlag > 0 || writeCount > 0) {
                        if (XX_EnforceStrictStatics <= 1 || !finals || writeCount < 2)
                            continue;
                        if (XX_EnforceStrictStatics == 2 && readFlag <= 0)
                            continue;  // Mode 2 fails only with R between 2W: W-R-W
                    }
                    for (int extraCount = 0; extraCount <= 3; extraCount++) {
                        if (extraCount > 0 && staticType != String.class)  continue;
                        var cn = String.format("BadWrite_T%s%s_W%d%s%s",
                                               staticType.getSimpleName(),
                                               (finals ? "_SSFinal" : ""),
                                               writeCount,
                                               (readFlag > 0 ? "_Rafter" :
                                                readFlag < 0 ? "_Rbefore" : ""),
                                               (extraCount > 0 ? "_E"+extraCount : ""));
                        var cls = buildClass(cn, staticType, writeCount, readFlag, extraCount, finals);
                        try {
                            LOOKUP.ensureInitialized(cls);
                            throw new AssertionError(cn);
                        } catch (Throwable ex) {
                            reportThrow(isSSFailure(ex), ex, cn);
                        }
                    }
                }
            }
        }
    }
    static void testFailedReads() {
        testFailedReads(false);
        testFailedReads(true);
    }
    static void testFailedReads(boolean finals) {
        for (var staticType : STATIC_TYPES) {
            for (int writeCount = 0; writeCount <= 1; writeCount++) {
                for (byte readFlag = -1; readFlag <= -1; readFlag++) {
                    for (int extraCount = 0; extraCount <= 3; extraCount++) {
                        if (extraCount > 0 && staticType != String.class)  continue;
                        var cn = String.format("BadRead_T%s%s_W%d_%s%s",
                                               staticType.getSimpleName(),
                                               (finals ? "_SSFinal" : ""),
                                               writeCount,
                                               (readFlag > 0 ? "_Rafter" :
                                                readFlag < 0 ? "_Rbefore" : ""),
                                               (extraCount > 0 ? "_E"+extraCount : ""));
                        var cls = buildClass(cn, staticType, writeCount, readFlag, extraCount, finals);
                        try {
                            LOOKUP.ensureInitialized(cls);
                            throw new AssertionError(cn);
                        } catch (Throwable ex) {
                            reportThrow(isSSFailure(ex), ex, cn);
                        }
                    }
                }
            }
        }
    }

    static boolean VERBOSE = true;

    private static void reportThrow(boolean ok, Throwable ex, String cn) {
        if (!ok)  throw new AssertionError(ex);
        if (VERBOSE) {
            if (ex instanceof ExceptionInInitializerError && ex.getCause() != null)
                ex = ex.getCause();
            String exs = ex.toString();
            exs = exs.replaceFirst("^[^ ]*IllegalStateException: ", "ISE: ");
            exs = exs.replaceFirst(" strictStatic[.][^ ]+/0x[^ ]+", "...");
            System.out.printf("%s: %s: %s\n", ok ? "ok" : "FAIL", cn, exs);
        }
    }

    static final int XX_EnforceStrictStatics
        = Integer.getInteger("XX_EnforceStrictStatics", 1);

    public static void main(String... av) {
        System.out.printf("-XX:EnforceStrictStatics=%d\n", XX_EnforceStrictStatics);
        testPositives();
        testFailedWrites();
        testFailedReads();
        System.out.println("tested "+COUNT+" classes");
    }
}
