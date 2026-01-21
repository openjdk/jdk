/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Test LinuxApplicationLayout
 * @requires (os.family == "linux")
 * @compile/module=jdk.jpackage -Xlint:all -Werror
 *    jdk/jpackage/internal/LinuxApplicationLayoutTest.java
 *    ../../share/jdk.jpackage/jdk/jpackage/internal/model/AppImageLayoutTest.java
 *    ../../share/jdk.jpackage/jdk/jpackage/internal/model/ApplicationLayoutTest.java
 * @run junit jdk.jpackage/jdk.jpackage.internal.LinuxApplicationLayoutTest
 */

/* @test
 * @summary Test LinuxSystemEnvironment
 * @requires (os.family == "linux")
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.mock.*
 * @compile/module=jdk.jpackage -Xlint:all -Werror
 *    jdk/jpackage/internal/LinuxSystemEnvironmentTest.java
 *    ../../share/jdk.jpackage/jdk/jpackage/internal/MockUtils.java
 * @run junit jdk.jpackage/jdk.jpackage.internal.LinuxSystemEnvironmentTest
 */

/* @test
 * @summary Test LibProvidersLookup
 * @requires (os.family == "linux")
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.mock.*
 * @compile/module=jdk.jpackage -Xlint:all -Werror
 *    jdk/jpackage/internal/LibProvidersLookupTest.java
 * @run junit jdk.jpackage/jdk.jpackage.internal.LibProvidersLookupTest
 */

/* @test
 * @summary Test LinuxPackageArch
 * @requires (os.family == "linux")
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.mock.*
 * @compile/module=jdk.jpackage -Xlint:all -Werror
 *    jdk/jpackage/internal/LinuxPackageArchTest.java
 *    ../../share/jdk.jpackage/jdk/jpackage/internal/MockUtils.java
 * @run junit jdk.jpackage/jdk.jpackage.internal.LinuxPackageArchTest
 */
