/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8327818
 * @summary reimplement debug option in Krb5LoginModule
 * @library /test/lib
 */
import com.sun.security.auth.module.Krb5LoginModule;
import jdk.test.lib.process.ProcessTools;

import java.util.Map;
import javax.security.auth.Subject;

public class LoginModuleDebug {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            // debug option set to true
            ProcessTools.executeTestJava("LoginModuleDebug",
                            "debug", "true")
                    .stdoutShouldBeEmpty()
                    .stderrShouldContain("krb5loginmodule:");
            // debug option set to false
            ProcessTools.executeTestJava("LoginModuleDebug",
                            "debug", "false")
                    .stdoutShouldBeEmpty()
                    .stderrShouldNotContain("krb5loginmodule:");
            // no debug option
            ProcessTools.executeTestJava("LoginModuleDebug",
                            "foo", "bar")
                    .stdoutShouldBeEmpty()
                    .stderrShouldNotContain("krb5loginmodule:");
        } else {
            test(args[0], args[1]);
        }
    }

    static void test(String key, String prop)
            throws Exception {
        new Krb5LoginModule().initialize(
                new Subject(), null, Map.of(), Map.of(key, prop));
    }
}
