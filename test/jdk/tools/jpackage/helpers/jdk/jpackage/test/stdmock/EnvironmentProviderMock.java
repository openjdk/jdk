/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.test.stdmock;

import jdk.jpackage.internal.EnvironmentProvider;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public record EnvironmentProviderMock(
        Map<String, String> envVariables,
        Map<String, String> systemProperties) implements EnvironmentProvider {

    public EnvironmentProviderMock {
        envVariables.keySet().forEach(Objects::requireNonNull);
        envVariables.values().forEach(Objects::requireNonNull);

        systemProperties.keySet().forEach(Objects::requireNonNull);
        systemProperties.values().forEach(Objects::requireNonNull);
    }

    @Override
    public String getenv(String envVarName) {
        return envVariables.get(Objects.requireNonNull(envVarName));
    }

    @Override
    public String getProperty(String propertyName) {
        return systemProperties.get(Objects.requireNonNull(propertyName));
    }

    @Override
    public String toString() {
        var tokens = new ArrayList<String>();
        if (!envVariables.isEmpty()) {
            tokens.add(String.format("env=%s", envVariables));
        }
        if (!systemProperties.isEmpty()) {
            tokens.add(String.format("props=%s", systemProperties));
        }
        return String.join(", ", tokens);
    }
}
