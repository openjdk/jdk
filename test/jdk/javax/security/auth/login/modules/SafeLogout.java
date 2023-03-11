/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import javax.security.auth.Subject;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.util.*;

import static javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag.*;

/*
 * @test
 * @bug 8282730
 * @key randomness
 * @summary Check that all LoginModule implementations don't throw NPE
 *          from logout method after login failure
 * @modules jdk.security.auth
 *          java.management
 */
public class SafeLogout {

    static Random r = new Random();

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 100; i++) {
            test(i);
        }
    }

    static void test(int pos) throws Exception {
        // The content of the principals and credentials sets depends on
        // combinations of (possibly multiple) login modules configurations,
        // and it is difficult to find only a "typical" subset to test on,
        // Therefore we use a random number to choose login module names,
        // flag for each, and whether to perform a login at the beginning.
        // Each config is printed out so that any failure can be reproduced.
        boolean login = r.nextBoolean();
        Map<String, ?> empty = Collections.emptyMap();
        AppConfigurationEntry[] result = new AppConfigurationEntry[r.nextInt(4) + 1];
        for (int i = 0; i < result.length; i++) {
            result[i] = new AppConfigurationEntry(randomModule(), randomControl(), empty);
        }

        System.out.println(pos + " " + login);
        Arrays.stream(result)
                .forEach(a -> System.out.println(a.getLoginModuleName() + ":" + a.getControlFlag()));

        LoginContext lc = new LoginContext("a", new Subject(), null, new Configuration() {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                return result;
            }
        });

        try {
            if (login) {
                lc.login();
            }
        } catch (LoginException e) {
            // Don't care
        } finally {
            try {
                lc.logout();
            } catch (LoginException le) {
                if (!le.getMessage().contains("all modules ignored")) {
                    throw le;
                }
            }
        }
    }

    static AppConfigurationEntry.LoginModuleControlFlag[] allControls = {
            REQUIRED,
            REQUISITE,
            SUFFICIENT,
            OPTIONAL
    };

    static AppConfigurationEntry.LoginModuleControlFlag randomControl() {
        return allControls[r.nextInt(allControls.length)];
    }

    static String[] allModules = {
            "com.sun.security.auth.module.Krb5LoginModule",
            "com.sun.security.auth.module.UnixLoginModule",
            "com.sun.security.auth.module.JndiLoginModule",
            "com.sun.security.auth.module.KeyStoreLoginModule",
            "com.sun.security.auth.module.NTLoginModule",
            "com.sun.security.auth.module.LdapLoginModule",
            "com.sun.jmx.remote.security.FileLoginModule"
    };

    static String randomModule() {
        return allModules[r.nextInt(allModules.length)];
    }
}
