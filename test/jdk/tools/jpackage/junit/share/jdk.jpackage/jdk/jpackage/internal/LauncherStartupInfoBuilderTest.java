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
package jdk.jpackage.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.LauncherJarStartupInfo;
import jdk.jpackage.internal.model.LauncherJarStartupInfoMixin;
import jdk.jpackage.internal.model.LauncherModularStartupInfo;
import jdk.jpackage.internal.model.LauncherModularStartupInfoMixin;
import jdk.jpackage.internal.model.LauncherStartupInfo;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.HelloApp;
import jdk.jpackage.test.JUnitAdapter;
import jdk.jpackage.test.JavaAppDesc;
import jdk.jpackage.test.ObjectMapper;
import jdk.jpackage.test.TKit;

public class LauncherStartupInfoBuilderTest extends JUnitAdapter {

    public record TestSpec(JavaAppDesc javaAppDesc, boolean withMainClass, Object expectedInfo) {
        public TestSpec {
            Objects.requireNonNull(javaAppDesc);
            Objects.requireNonNull(expectedInfo);
        }

        void test() throws ConfigException {
            final var workDir = TKit.createTempDirectory("input");

            HelloApp.createBundle(javaAppDesc, workDir);

            final var builder = new LauncherStartupInfoBuilder();

            builder.inputDir(workDir);

            if (withMainClass) {
                builder.mainClassName(javaAppDesc.className());
            }

            Optional.ofNullable(javaAppDesc.moduleName()).ifPresentOrElse(moduleName -> {
                if (javaAppDesc.isWithMainClass()) {
                    builder.moduleName(moduleName + "/" + javaAppDesc.className());
                } else {
                    builder.moduleName(moduleName);
                }
                builder.modulePath(List.of(workDir));
            }, () -> {
                builder.mainJar(Path.of(javaAppDesc.jarFileName()));
            });

            final var actualInfo = builder.create();

            assertEquals(expectedInfo, OM.map(actualInfo));
        }

        static final class Builder {
            TestSpec create() {
                return new TestSpec(javaAppDesc, withMainClass, OM.map(createInfo()));
            }

            Builder javaAppDesc(String v) {
                javaAppDesc = JavaAppDesc.parse(v);
                return this;
            }

            Builder withMainClass(boolean v) {
                withMainClass = v;
                return this;
            }

            private LauncherStartupInfo createInfo() {
                final var base = createBaseInfo();
                if (javaAppDesc.moduleName() != null) {
                    return LauncherModularStartupInfo.create(base, createModularMixin());
                } else {
                    return LauncherJarStartupInfo.create(base, createJarMixin());
                }
            }

            private LauncherStartupInfo createBaseInfo() {
                return new LauncherStartupInfo.Stub(javaAppDesc.className(), List.of(), List.of(), classPath);
            }

            private LauncherJarStartupInfoMixin createJarMixin() {
                return new LauncherJarStartupInfoMixin.Stub(Path.of(javaAppDesc.jarFileName()),
                        !withMainClass && javaAppDesc.isWithMainClass());
            }

            private LauncherModularStartupInfoMixin createModularMixin() {
                return new LauncherModularStartupInfoMixin.Stub(javaAppDesc.moduleName(),
                        Optional.ofNullable(javaAppDesc.moduleVersion()));
            }

            private JavaAppDesc javaAppDesc;
            private boolean withMainClass = true;
            private List<Path> classPath = new ArrayList<>();
        }
    }

    @Test
    @ParameterSupplier
    public static void test(TestSpec spec) throws ConfigException {
        spec.test();
    }

    public static Collection<Object[]> test() {
        return Stream.of(
                build(""),
                build("foo.jar:foo.bar.U"),
                build("foo.jar:foo.bar.U!"),
                build("foo.jar:foo.bar.U!").withMainClass(false),
                build("a.b/d.c.O").withMainClass(true),
                build("a.b/d.c.O@3.5.7-beta").withMainClass(true),
                build("a.b/d.c.O!"),
                build("a.b/d.c.O!").withMainClass(false)
        ).map(TestSpec.Builder::create).map(v -> {
            return new Object[] {v};
        }).toList();
    }

    private static TestSpec.Builder build(String javaAppDesc) {
        return new TestSpec.Builder().javaAppDesc(javaAppDesc);
    }

    private static final ObjectMapper OM = ObjectMapper.standard().create();
}
