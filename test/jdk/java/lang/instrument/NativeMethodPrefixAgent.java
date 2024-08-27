/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

import asmlib.Instrumentor;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.io.*;

import static java.lang.constant.ConstantDescs.*;

class NativeMethodPrefixAgent {

    static ClassFileTransformer t0, t1, t2;
    static Instrumentation inst;
    private static Throwable agentError; // to be accessed/updated in a synchronized block

    private static final String CLASS_TO_TRANSFORM = "NativeMethodPrefixApp$Dummy";

    public static synchronized void checkErrors() {
        if (agentError != null) {
            throw new RuntimeException("Agent error", agentError);
        }
    }

    private static synchronized void trackError(final Throwable t) {
        if (agentError == null) {
            agentError = t;
            return;
        }
        if (agentError != t) {
            agentError.addSuppressed(t);
        }
    }

    static class Tr implements ClassFileTransformer {
        private static final ClassDesc CD_StringIdCallbackReporter = ClassDesc.ofInternalName("bootreporter/StringIdCallbackReporter");
        private static final MethodTypeDesc MTD_void_String_int = MethodTypeDesc.of(CD_void, CD_String, CD_int);
        final String trname;
        final int transformId;
        private final String nativeMethodPrefix;

        Tr(int transformId) {
            this.trname = "tr" + transformId;
            this.transformId = transformId;
            this.nativeMethodPrefix = "wrapped_" + trname + "_";
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {

            try {
                // we only transform a specific application class
                if (!className.equals(CLASS_TO_TRANSFORM)) {
                    return null;
                }
                if (classBeingRedefined != null) {
                    return null;
                }
                // use a byte code generator which creates wrapper methods,
                // with a configured native method prefix, for each native method on the
                // class being transformed
                final Instrumentor byteCodeGenerator = Instrumentor.instrFor(classfileBuffer)
                        .addNativeMethodTrackingInjection(nativeMethodPrefix,
                                (name, cb) -> {
                                    cb.loadConstant(name);
                                    cb.loadConstant(transformId);
                                    cb.invokestatic(CD_StringIdCallbackReporter,
                                            "tracker", MTD_void_String_int);
                                });
                // generate the bytecode
                return byteCodeGenerator.apply();
            } catch (Throwable t) {
                trackError(t);
                return null;
            }
        }
    }

    // for debugging
    static void write_buffer(String fname, byte[]buffer) {
        try {
            File f = new File(fname);
            if (!f.getParentFile().exists()) {
                f.getParentFile().mkdirs();
            }
            try (FileOutputStream outStream = new FileOutputStream(f)) {
                outStream.write(buffer, 0, buffer.length);
            }
        } catch (IOException ex) {
            System.err.println("EXCEPTION in write_buffer: " + ex);
        }
    }

    public static void
    premain (String agentArgs, Instrumentation instArg)
        throws IOException, IllegalClassFormatException,
        ClassNotFoundException, UnmodifiableClassException {
        inst = instArg;
        System.out.println("Premain");

        t1 = new Tr(1);
        t2 = new Tr(2);
        t0 = new Tr(0);
        inst.addTransformer(t1, true);
        inst.addTransformer(t2, false);
        inst.addTransformer(t0, true);
        instArg.setNativeMethodPrefix(t0, "wrapped_tr0_");
        instArg.setNativeMethodPrefix(t1, "wrapped_tr1_");
        instArg.setNativeMethodPrefix(t2, "wrapped_tr2_");

        // warm up: cause load of transformer classes before used during class load
        instArg.retransformClasses(Runtime.class);
    }
}
