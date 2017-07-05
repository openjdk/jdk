/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test exclude VM plugin
 * @author Jean-Francois Denise
 * @modules jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.internal.plugins
 * @run main ExcludeVMPluginTest
 */
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import jdk.tools.jlink.internal.PoolImpl;

import jdk.tools.jlink.internal.plugins.ExcludeVMPlugin;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.Pool.ModuleData;
import jdk.tools.jlink.plugin.Pool.ModuleDataType;
import jdk.tools.jlink.plugin.TransformerPlugin;

public class ExcludeVMPluginTest {

    private static final String TAG = "# orig in test\n";

    private static final String[] ARCHITECTURES = {"/", "/amd64/", "/i386/", "/arm/",
        "/aarch64/", "/toto/"};

    private static final String[] CLIENT = {"client/" + jvmlib(),};
    private static final String[] SERVER = {"server/" + jvmlib()};
    private static final String[] MINIMAL = {"minimal/" + jvmlib()};
    private static final String[] ALL = {CLIENT[0], SERVER[0], MINIMAL[0]};
    private static final String JVM_CFG_ALL = TAG + "-server KNOWN\n-client KNOWN\n-minimal KNOWN\n";
    private static final String JVM_CFG_CLIENT = TAG + "-client KNOWN\n";
    private static final String JVM_CFG_SERVER = TAG + "-server KNOWN\n";
    private static final String JVM_CFG_SERVER_ALIAS_OTHERS = TAG + "-server KNOWN\n-client ALIASED_TO -server\n-minimal ALIASED_TO -server\n";
    private static final String JVM_CFG_CLIENT_ALIAS_OTHERS = TAG + "-client KNOWN\n-server ALIASED_TO -client\n-minimal ALIASED_TO -client\n";
    private static final String JVM_CFG_MINIMAL_ALIAS_OTHERS = TAG + "-minimal KNOWN\n-server ALIASED_TO -minimal\n-client ALIASED_TO -minimal\n";
    private static final String JVM_CFG_MINIMAL = TAG + "-minimal KNOWN\n";

    public static void main(String[] args) throws Exception {
        new ExcludeVMPluginTest().test();
    }

    public void test() throws Exception {
        boolean failed = false;

        try {
            checkVM("toto", ALL, JVM_CFG_ALL, ALL, JVM_CFG_ALL);
            failed = true;
            throw new Exception("Should have failed");
        } catch (Exception ex) {
            if (failed) {
                throw ex;
            }
        }

        checkVM("all", ALL, JVM_CFG_ALL, ALL, JVM_CFG_ALL);
        checkVM("all", CLIENT, JVM_CFG_CLIENT, CLIENT, JVM_CFG_CLIENT);
        checkVM("all", SERVER, JVM_CFG_SERVER, SERVER, JVM_CFG_SERVER);
        checkVM("all", MINIMAL, JVM_CFG_MINIMAL, MINIMAL, JVM_CFG_MINIMAL);

        checkVM("server", ALL, JVM_CFG_ALL, SERVER, JVM_CFG_SERVER_ALIAS_OTHERS);
        checkVM("server", SERVER, JVM_CFG_SERVER, SERVER, JVM_CFG_SERVER);
        try {
            checkVM("server", CLIENT, JVM_CFG_CLIENT, SERVER, JVM_CFG_SERVER);
            failed = true;
            throw new Exception("Should have failed");
        } catch (Exception ex) {
            if (failed) {
                throw ex;
            }
        }
        try {
            checkVM("server", MINIMAL, JVM_CFG_MINIMAL, SERVER, JVM_CFG_SERVER);
            failed = true;
            throw new Exception("Should have failed");
        } catch (Exception ex) {
            if (failed) {
                throw ex;
            }
        }

        checkVM("client", ALL, JVM_CFG_ALL, CLIENT, JVM_CFG_CLIENT_ALIAS_OTHERS);
        checkVM("client", CLIENT, JVM_CFG_CLIENT, CLIENT, JVM_CFG_CLIENT);
        try {
            checkVM("client", SERVER, JVM_CFG_SERVER, CLIENT, JVM_CFG_CLIENT);
            failed = true;
            throw new Exception("Should have failed");
        } catch (Exception ex) {
            if (failed) {
                throw ex;
            }
        }
        try {
            checkVM("client", MINIMAL, JVM_CFG_MINIMAL, CLIENT, JVM_CFG_CLIENT);
            failed = true;
            throw new Exception("Should have failed");
        } catch (Exception ex) {
            if (failed) {
                throw ex;
            }
        }

        checkVM("minimal", ALL, JVM_CFG_ALL, MINIMAL, JVM_CFG_MINIMAL_ALIAS_OTHERS);
        checkVM("minimal", MINIMAL, JVM_CFG_MINIMAL, MINIMAL, JVM_CFG_MINIMAL);
        try {
            checkVM("minimal", SERVER, JVM_CFG_SERVER, MINIMAL, JVM_CFG_MINIMAL);
            failed = true;
            throw new Exception("Should have failed");
        } catch (Exception ex) {
            if (failed) {
                throw ex;
            }
        }
        try {
            checkVM("minimal", CLIENT, JVM_CFG_CLIENT, MINIMAL, JVM_CFG_MINIMAL);
            failed = true;
            throw new Exception("Should have failed");
        } catch (Exception ex) {
            if (failed) {
                throw ex;
            }
        }

    }

    public void checkVM(String vm, String[] input, String jvmcfg, String[] expectedOutput, String expectdJvmCfg) throws Exception {

        for (String arch : ARCHITECTURES) {
            String[] winput = new String[input.length];
            String[] woutput = new String[expectedOutput.length];
            for (int i = 0; i < input.length; i++) {
                winput[i] = "/java.base/native" + arch + input[i];
            }
            for (int i = 0; i < expectedOutput.length; i++) {
                woutput[i] = "/java.base/native" + arch + expectedOutput[i];
            }
            doCheckVM(vm, winput, jvmcfg, woutput, expectdJvmCfg);
        }
    }

    private void doCheckVM(String vm, String[] input, String jvmcfg, String[] expectedOutput, String expectdJvmCfg) throws Exception {
        // Create a pool with jvm.cfg and the input paths.
        byte[] jvmcfgContent = jvmcfg.getBytes();
        Pool pool = new PoolImpl();
        pool.add(Pool.newImageFile("java.base", "/java.base/native/jvm.cfg",
                ModuleDataType.NATIVE_LIB, new ByteArrayInputStream(jvmcfgContent), jvmcfgContent.length));
        for (String in : input) {
            pool.add(Pool.newImageFile("java.base", in,
                    ModuleDataType.NATIVE_LIB, new ByteArrayInputStream(new byte[0]), 0));
        }
        Pool out = new PoolImpl();

        TransformerPlugin p = new ExcludeVMPlugin();
        Map<String, String> config = new HashMap<>();
        if (vm != null) {
            config.put(ExcludeVMPlugin.NAME, vm);
        }
        p.configure(config);
        p.visit(pool, out);

        String newContent = new String(out.get("/java.base/native/jvm.cfg").stream().readAllBytes());

        if (!expectdJvmCfg.equals(newContent)) {
            throw new Exception("Got content " + newContent + " expected " + expectdJvmCfg);
        }

        if (out.getContent().size() != (expectedOutput.length + 1)) {
            for (ModuleData m : out.getContent()) {
                System.err.println(m.getPath());
            }
            throw new Exception("Invalid output size " + out.getContent().size() + " expected " + (expectedOutput.length + 1));
        }

        for (ModuleData md : out.getContent()) {
            if (md.getPath().equals("/java.base/native/jvm.cfg")) {
                continue;
            }
            boolean contained = false;
            for (String o : expectedOutput) {
                if (md.getPath().equals(o)) {
                    contained = true;
                    break;
                }
            }
            if (!contained) {
                throw new Exception(md.getPath() + " not expected");
            }
        }

    }

    private static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    private static boolean isMac() {
        return System.getProperty("os.name").startsWith("Mac OS");
    }

    private static String jvmlib() {
        String lib = "libjvm.so";
        if (isWindows()) {
            lib = "jvm.dll";
        } else if (isMac()) {
            lib = "libjvm.dylib";
        }
        return lib;
    }
}
