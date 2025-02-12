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

/* @test
 * @bug 8888888
 * @summary test tracking of strict static fields
 * @run main strictStatic.StrictStaticTests
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
                               int extraCount    // how many extra strict statics?
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
        byte[] classBytes = ClassFile.of().build(cn, clb -> {
                clb.withField(SS_NAME, SS_TYPE, ClassFile.ACC_STATIC|ClassFile.ACC_STRICT);
                for (int i = 0; i < extraCount; i++) {
                    clb.withField(XS_NAME+i, XS_TYPE, ClassFile.ACC_STATIC|ClassFile.ACC_STRICT);
                    clb.withField(PS_NAME+i, PS_TYPE, ClassFile.ACC_STATIC);
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
                            if (i > 0) initializer = SS_INIT_2;
                            cob.loadConstant(initializer);
                            cob.putstatic(cn, SS_NAME, SS_TYPE);
                            // if we write zero times we must fail
                        }
                        if (readFlag < 0) {
                            // perform a late read, which must work
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
        for (var staticType : STATIC_TYPES) {
            for (int writeCount = 1; writeCount <= 3; writeCount++) {
                for (byte readFlag = 0; readFlag <= 1; readFlag++) {
                    for (int extraCount = 0; extraCount <= 3; extraCount++) {
                        var cn = String.format("Positive%sW%d%sE%d",
                                               staticType.getSimpleName(),
                                               writeCount,
                                               readFlag > 0 ? "Rafter" : readFlag < 0 ? "Rbefore" : "",
                                               extraCount);
                        var cls = buildClass(cn, staticType, writeCount, readFlag, extraCount);
                        try {
                            LOOKUP.ensureInitialized(cls);
                        } catch (Throwable ex) {
                            reportThrow(false, ex, cn);
                        }
                    }
                }
            }
        }
    }
    static void testFailedWrites() {
        for (var staticType : STATIC_TYPES) {
            for (int writeCount = 0; writeCount <= 0; writeCount++) {
                for (byte readFlag = 0; readFlag <= 0; readFlag++) {
                    for (int extraCount = 0; extraCount <= 3; extraCount++) {
                        var cn = String.format("NoWrite%sW%d%sE%d",
                                               staticType.getSimpleName(),
                                               writeCount,
                                               readFlag > 0 ? "Rafter" : readFlag < 0 ? "Rbefore" : "",
                                               extraCount);
                        var cls = buildClass(cn, staticType, writeCount, readFlag, extraCount);
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
        for (var staticType : STATIC_TYPES) {
            for (int writeCount = 0; writeCount <= 1; writeCount++) {
                for (byte readFlag = -1; readFlag <= -1; readFlag++) {
                    for (int extraCount = 0; extraCount <= 3; extraCount++) {
                        var cn = String.format("BadRead%sW%d%sE%d",
                                               staticType.getSimpleName(),
                                               writeCount,
                                               readFlag > 0 ? "Rafter" : readFlag < 0 ? "Rbefore" : "",
                                               extraCount);
                        var cls = buildClass(cn, staticType, writeCount, readFlag, extraCount);
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

    static boolean VERBOSE = false;

    private static void reportThrow(boolean ok, Throwable ex, String cn) {
        if (!ok)  throw new AssertionError(ex);
        if (VERBOSE) {
            if (ex instanceof ExceptionInInitializerError && ex.getCause() != null)
                ex = ex.getCause();
            System.out.printf("%s: %s: %s\n", ok ? "ok" : "FAIL", cn, ex);
        }
    }

    public static void main(String... av) {
        testPositives();
        testFailedWrites();
        testFailedReads();
        System.out.println("tested "+COUNT+" classes");
    }
}
