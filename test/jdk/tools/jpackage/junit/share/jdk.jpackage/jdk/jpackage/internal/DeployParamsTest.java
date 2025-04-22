/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jdk.jpackage.internal.model.PackagerException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;


public class DeployParamsTest {

    @Test
    public void testValidAppName() throws PackagerException {
        initParamsAppName();

        setAppNameAndValidate("Test");

        setAppNameAndValidate("Test Name");

        setAppNameAndValidate("Test - Name !!!");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Test\nName", "Test\rName", "TestName\\", "Test \" Name"})
    public void testInvalidAppName(String appName) throws PackagerException {
        initParamsAppName();
        var ex = assertThrowsExactly(PackagerException.class, () -> setAppNameAndValidate(appName));

        assertTrue(ex.getMessage().startsWith("Error: Invalid Application name"));
    }

    // Returns deploy params initialized to pass all validation, except for
    // app name
    private void initParamsAppName() {
        params = new DeployParams();

        params.addBundleArgument(Arguments.CLIOptions.APPCLASS.getId(),
                "TestClass");
        params.addBundleArgument(Arguments.CLIOptions.MAIN_JAR.getId(),
                "test.jar");
        params.addBundleArgument(Arguments.CLIOptions.INPUT.getId(), "input");
    }

    private void setAppNameAndValidate(String appName) throws PackagerException {
        params.addBundleArgument(Arguments.CLIOptions.NAME.getId(), appName);
        params.validate();
    }

    private DeployParams params;
}
