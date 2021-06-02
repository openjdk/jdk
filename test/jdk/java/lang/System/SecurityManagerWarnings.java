/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8266459
 * @summary check various warnings
 * @library /test/lib
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.security.Permission;

public class SecurityManagerWarnings {
    public static void main(String args[]) throws Exception {
        if (args.length == 0) {
            run(null)
                    .shouldHaveExitValue(0)
                    .shouldContain("SM is enabled: false")
                    .shouldNotContain("Security Manager is deprecated")
                    .shouldContain("setSecurityManager is deprecated");
            run("allow")
                    .shouldHaveExitValue(0)
                    .shouldContain("SM is enabled: false")
                    .shouldNotContain("Security Manager is deprecated")
                    .shouldContain("setSecurityManager is deprecated");
            run("disallow")
                    .shouldNotHaveExitValue(0)
                    .shouldContain("SM is enabled: false")
                    .shouldNotContain("Security Manager is deprecated")
                    .shouldContain("UnsupportedOperationException");
            run("SecurityManagerWarnings$MySM")
                    .shouldHaveExitValue(0)
                    .shouldContain("SM is enabled: true")
                    .shouldContain("Security Manager is deprecated")
                    .shouldContain("setSecurityManager is deprecated");
            run("")
                    .shouldNotHaveExitValue(0)
                    .shouldContain("SM is enabled: true")
                    .shouldContain("Security Manager is deprecated")
                    .shouldContain("AccessControlException");
            run("default")
                    .shouldNotHaveExitValue(0)
                    .shouldContain("SM is enabled: true")
                    .shouldContain("Security Manager is deprecated")
                    .shouldContain("AccessControlException");
        } else {
            System.out.println("SM is enabled: " + (System.getSecurityManager() != null));
            System.setSecurityManager(new SecurityManager());
        }
    }

    static OutputAnalyzer run(String prop) throws Exception {
        if (prop == null) {
            return ProcessTools.executeTestJvm(
                    "SecurityManagerWarnings", "run");
        } else {
            return ProcessTools.executeTestJvm(
                    "-Djava.security.manager=" + prop,
                    "SecurityManagerWarnings", "run");
        }
    }

    // This SecurityManager allows everything!
    public static class MySM extends SecurityManager {
        @Override
        public void checkPermission(Permission perm) {
        }
    }
}
