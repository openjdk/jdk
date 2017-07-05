/*
 * Copyright (c) 1998, 2000, Oracle and/or its affiliates. All rights reserved.
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

/**
 *
 */
import java.io.File;

/**
 * Setup static variables to represent properties in test environment.
 */
public class TestParams {

    /** variables that hold value property values */
    public static String testSrc = null;
    public static String testClasses = null;

    /** name of default security policy */
    public static String defaultPolicy = null;

    /** name of default security policy for RMID */
    public static String defaultRmidPolicy = null;

    /** name of default security policy for activation groups */
    public static String defaultGroupPolicy = null;

    /** name of default security manager */
    public static String defaultSecurityManager =
        "java.rmi.RMISecurityManager";


    /* Initalize commonly used strings */
    static {
        try {
            testSrc = TestLibrary.
                getProperty("test.src", ".");
            testClasses = TestLibrary.
                getProperty("test.classes", ".");

            // if policy file already set use it
            defaultPolicy = TestLibrary.
                getProperty("java.security.policy",
                            defaultPolicy);
            if (defaultPolicy == null) {
                defaultPolicy = testSrc + File.separatorChar +
                    "security.policy";
            }

            // if manager prop set use it
            defaultSecurityManager = TestLibrary.
                getProperty("java.security.manager",
                            defaultSecurityManager);

            defaultRmidPolicy =
                testSrc + File.separatorChar + "rmid.security.policy";

            defaultGroupPolicy = testSrc +
                File.separatorChar + "group.security.policy";

        } catch (SecurityException se) {
            TestLibrary.bomb("Security exception received" +
                             " during test initialization:",
                             se);
        }
    }
}
