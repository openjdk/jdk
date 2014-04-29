/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import sun.tools.jinfo.JInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;

import static org.testng.Assert.*;

/**
 * @test
 * @bug 8039080
 * @run testng JInfoLauncherTest
 * @summary Test JInfo launcher argument parsing
 */
@Test
public class JInfoLauncherTest {
    public static final String VALIDATION_EXCEPTION_CLSNAME =
                                IllegalArgumentException.class.getName();

    private Constructor<JInfo> jInfoConstructor;
    private Field fldUseSA;

    @BeforeClass
    public void setup() throws Exception {
        jInfoConstructor = JInfo.class.getDeclaredConstructor(String[].class);
        jInfoConstructor.setAccessible(true);
        fldUseSA = JInfo.class.getDeclaredField("useSA");
        fldUseSA.setAccessible(true);
    }

    private JInfo newJInfo(String[] args) throws Exception {
        try {
            return jInfoConstructor.newInstance((Object) args);
        } catch (Exception e) {
            if (isValidationException(e.getCause())) {
                throw (Exception)e.getCause();
            }
            throw e;
        }
    }

    private boolean getUseSA(JInfo jinfo) throws Exception {
        return fldUseSA.getBoolean(jinfo);
    }

    private void cmdPID(String cmd, String ... params) throws Exception {
        int offset = (cmd != null ? 1 : 0);
        String[] args = new String[offset + params.length];
        args[0] = cmd;
        System.arraycopy(params, 0, args, offset, params.length);
        JInfo j = newJInfo(args);
        assertFalse(getUseSA(j), "Local jinfo must not forward to SA");
    }

    private void cmdCore(String cmd, String ... params) throws Exception {
        int offset = (cmd != null ? 1 : 0);
        String[] args = new String[offset + params.length];
        args[0] = cmd;
        System.arraycopy(params, 0, args, offset, params.length);
        JInfo j = newJInfo(args);
        assertTrue(getUseSA(j), "Core jinfo must forward to SA");
    }

    private void cmdRemote(String cmd, String ... params) throws Exception {
        int offset = (cmd != null ? 1 : 0);
        String[] args = new String[offset + params.length];
        args[0] = cmd;
        System.arraycopy(params, 0, args, offset, params.length);
        JInfo j = newJInfo(args);
        assertTrue(getUseSA(j), "Remote jinfo must forward to SA");
    }

    private void cmdExtraArgs(String cmd, int argsLen) throws Exception {
        String[] args = new String[argsLen + 1 + (cmd != null ? 1 : 0)];
        Arrays.fill(args, "a");
        if (cmd != null) {
            args[0] = cmd;
        } else {
            cmd = "default";
        }
        try {
            JInfo j = newJInfo(args);
            fail("\"" + cmd + "\" does not support more than " + argsLen +
                 " arguments");
        } catch (Exception e) {
            if (!isValidationException(e)) {
                throw e;
            }
            // ignore
        }
    }

    private void cmdMissingArgs(String cmd, int reqArgs) throws Exception {
        String[] args = new String[reqArgs - 1 + (cmd != null ? 1 : 0)];
        Arrays.fill(args, "a");
        if (cmd != null) {
            args[0] = cmd;
        } else {
            cmd = "default";
        }
        try {
            JInfo j = newJInfo(args);
            fail("\"" + cmd + "\" requires at least " + reqArgs + " argument");
        } catch (Exception e) {
            if (!isValidationException(e)) {
                throw e;
            }
            // ignore
        }
    }

    public void testDefaultPID() throws Exception {
        cmdPID(null, "1234");
    }

    public void testFlagsPID() throws Exception {
        cmdPID("-flags", "1234");
    }

    public void testSyspropsPID() throws Exception {
        cmdPID("-sysprops", "1234");
    }

    public void testReadFlagPID() throws Exception {
        cmdPID("-flag", "SomeManagementFlag", "1234");
    }

    public void testSetFlag1PID() throws Exception {
        cmdPID("-flag", "+SomeManagementFlag", "1234");
    }

    public void testSetFlag2PID() throws Exception {
        cmdPID("-flag", "-SomeManagementFlag", "1234");
    }

    public void testSetFlag3PID() throws Exception {
        cmdPID("-flag", "SomeManagementFlag=314", "1234");
    }

    public void testDefaultCore() throws Exception {
        cmdCore(null, "myapp.exe", "my.core");
    }

    public void testFlagsCore() throws Exception {
        cmdCore("-flags", "myapp.exe", "my.core");
    }

    public void testSyspropsCore() throws Exception {
        cmdCore("-sysprops", "myapp.exe", "my.core");
    }

    public void testReadFlagCore() throws Exception {
        try {
            cmdCore("-flag", "SomeManagementFlag", "myapp.exe", "my.core");
            fail("Flags can not be read from core files");
        } catch (Exception e) {
            if (!isValidationException(e)) {
                throw e;
            }
            // ignore
        }
    }

    public void testSetFlag1Core() throws Exception {
        try {
            cmdCore("-flag", "+SomeManagementFlag", "myapp.exe", "my.core");
            fail("Flags can not be set in core files");
        } catch (Exception e) {
            if (!isValidationException(e)) {
                throw e;
            }
            // ignore
        }
    }

    public void testSetFlag2Core() throws Exception {
        try {
            cmdCore("-flag", "-SomeManagementFlag", "myapp.exe", "my.core");
            fail("Flags can not be set in core files");
        } catch (Exception e) {
            if (!isValidationException(e)) {
                throw e;
            }
            // ignore
        }
    }

    public void testSetFlag3Core() throws Exception {
        try {
            cmdCore("-flag", "SomeManagementFlag=314", "myapp.exe", "my.core");
            fail("Flags can not be set in core files");
        } catch (Exception e) {
            if (!isValidationException(e)) {
                throw e;
            }
            // ignore
        }
    }

    public void testDefaultRemote() throws Exception {
        cmdRemote(null, "serverid@host");
    }

    public void testFlagsRemote() throws Exception {
        cmdRemote("-flags", "serverid@host");
    }

    public void testSyspropsRemote() throws Exception {
        cmdRemote("-sysprops", "serverid@host");
    }

    public void testReadFlagRemote() throws Exception {
        try {
            cmdCore("-flag", "SomeManagementFlag", "serverid@host");
            fail("Flags can not be read from SA server");
        } catch (Exception e) {
            if (!isValidationException(e)) {
                throw e;
            }
            // ignore
        }
    }

    public void testSetFlag1Remote() throws Exception {
        try {
            cmdCore("-flag", "+SomeManagementFlag","serverid@host");
            fail("Flags can not be set on SA server");
        } catch (Exception e) {
            if (!isValidationException(e)) {
                throw e;
            }
            // ignore
        }
    }

    public void testSetFlag2Remote() throws Exception {
        try {
            cmdCore("-flag", "-SomeManagementFlag", "serverid@host");
            fail("Flags can not be read set on SA server");
        } catch (Exception e) {
            if (!isValidationException(e)) {
                throw e;
            }
            // ignore
        }
    }

    public void testSetFlag3Remote() throws Exception {
        try {
            cmdCore("-flag", "SomeManagementFlag=314", "serverid@host");
            fail("Flags can not be read set on SA server");
        } catch (Exception e) {
            if (!isValidationException(e)) {
                throw e;
            }
            // ignore
        }
    }

    public void testDefaultExtraArgs() throws Exception {
        cmdExtraArgs(null, 2);
    }

    public void testFlagsExtraArgs() throws Exception {
        cmdExtraArgs("-flags", 2);
    }

    public void testSyspropsExtraArgs() throws Exception {
        cmdExtraArgs("-sysprops", 2);
    }

    public void testFlagExtraArgs() throws Exception {
        cmdExtraArgs("-flag", 2);
    }

    public void testHelp1ExtraArgs() throws Exception {
        cmdExtraArgs("-h", 0);
    }

    public void testHelp2ExtraArgs() throws Exception {
        cmdExtraArgs("-help", 0);
    }

    public void testDefaultMissingArgs() throws Exception {
        cmdMissingArgs(null, 1);
    }

    public void testFlagsMissingArgs() throws Exception {
        cmdMissingArgs("-flags", 1);
    }

    public void testSyspropsMissingArgs() throws Exception {
        cmdMissingArgs("-sysprops", 1);
    }

    public void testFlagMissingArgs() throws Exception {
        cmdMissingArgs("-flag", 2);
    }

    public void testUnknownCommand() throws Exception {
        try {
            JInfo j = newJInfo(new String[]{"-unknown_command"});
            fail("JInfo accepts unknown commands");
        } catch (Exception e) {
            if (!isValidationException(e)) {
                throw e;
            }
            // ignore
        }
    }

    private static boolean isValidationException(Throwable e) {
        return e.getClass().getName().equals(VALIDATION_EXCEPTION_CLSNAME);
    }
}
