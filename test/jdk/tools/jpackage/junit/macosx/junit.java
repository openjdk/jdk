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
 * @summary Test MacApplicationLayoutTest
 * @requires (os.family == "mac")
 * @compile/module=jdk.jpackage -Xlint:all -Werror
 *    jdk/jpackage/internal/MacApplicationLayoutTest.java
 *    ../../share/jdk.jpackage/jdk/jpackage/internal/model/AppImageLayoutTest.java
 *    ../../share/jdk.jpackage/jdk/jpackage/internal/model/ApplicationLayoutTest.java
 * @run junit jdk.jpackage/jdk.jpackage.internal.MacApplicationLayoutTest
 */

/* @test
 * @summary Test MacDmgSystemEnvironmentTest
 * @requires (os.family == "mac")
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.mock.*
 * @compile/module=jdk.jpackage -Xlint:all -Werror
 *    jdk/jpackage/internal/MacDmgSystemEnvironmentTest.java
 *    ../../share/jdk.jpackage/jdk/jpackage/internal/MockUtils.java
 * @run junit jdk.jpackage/jdk.jpackage.internal.MacDmgSystemEnvironmentTest
 */

/* @test
 * @summary Test MacDmgPackagerTest
 * @requires (os.family == "mac")
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.mock.*
 * @compile/module=jdk.jpackage -Xlint:all -Werror
 *    jdk/jpackage/internal/MacDmgPackagerTest.java
 *    ../../share/jdk.jpackage/jdk/jpackage/internal/MockUtils.java
 * @run junit jdk.jpackage/jdk.jpackage.internal.MacDmgPackagerTest
 */
