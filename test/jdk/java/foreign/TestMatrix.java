/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * Note: to run this test manually, you need to build the tests first to get native
 * libraries compiled, and then execute it with plain jtreg, like:
 *
 *  $ bin/jtreg -jdk:<path-to-tested-jdk> \
 *              -nativepath:<path-to-build-dir>/images/test/jdk/jtreg/native/ \
 *              -concurrency:auto \
 *              ./test/jdk/java/foreign/TestMatrix.java
 */

/* @test id=UpcallHighArity-FF
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=false
 *   -Djdk.internal.foreign.UpcallLinker.USE_SPEC=false
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-TF
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=true
 *   -Djdk.internal.foreign.UpcallLinker.USE_SPEC=false
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-FT
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=false
 *   -Djdk.internal.foreign.UpcallLinker.USE_SPEC=true
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-TT
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=true
 *   -Djdk.internal.foreign.UpcallLinker.USE_SPEC=true
 *   TestUpcallHighArity
 */

/* @test id=DowncallScope-F
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestDowncallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=false
 *   TestDowncallScope
 */

/* @test id=DowncallScope-T
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestDowncallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=true
 *   TestDowncallScope
 */

/* @test id=DowncallStack-F
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestDowncallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=false
 *   TestDowncallStack
 */

/* @test id=DowncallStack-T
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestDowncallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=true
 *   TestDowncallStack
 */

/* @test id=UpcallScope-FF
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=false
 *   -Djdk.internal.foreign.UpcallLinker.USE_SPEC=false
 *   TestUpcallScope
 */

/* @test id=UpcallScope-TF
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=true
 *   -Djdk.internal.foreign.UpcallLinker.USE_SPEC=false
 *   TestUpcallScope
 */

/* @test id=UpcallScope-FT
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=false
 *   -Djdk.internal.foreign.UpcallLinker.USE_SPEC=true
 *   TestUpcallScope
 */

/* @test id=UpcallScope-TT
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=true
 *   -Djdk.internal.foreign.UpcallLinker.USE_SPEC=true
 *   TestUpcallScope
 */

/* @test id=UpcallAsync-FF
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=false
 *   -Djdk.internal.foreign.UpcallLinker.USE_SPEC=false
 *   TestUpcallAsync
 */

/* @test id=UpcallAsync-TF
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=true
 *   -Djdk.internal.foreign.UpcallLinker.USE_SPEC=false
 *   TestUpcallAsync
 */

/* @test id=UpcallAsync-FT
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=false
 *   -Djdk.internal.foreign.UpcallLinker.USE_SPEC=true
 *   TestUpcallAsync
 */

/* @test id=UpcallAsync-TT
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=true
 *   -Djdk.internal.foreign.UpcallLinker.USE_SPEC=true
 *   TestUpcallAsync
 */

/* @test id=UpcallStack-FF
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=false
 *   -Djdk.internal.foreign.UpcallLinker.USE_SPEC=false
 *   TestUpcallStack
 */

/* @test id=UpcallStack-TF
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=true
 *   -Djdk.internal.foreign.UpcallLinker.USE_SPEC=false
 *   TestUpcallStack
 */

/* @test id=UpcallStack-FT
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=false
 *   -Djdk.internal.foreign.UpcallLinker.USE_SPEC=true
 *   TestUpcallStack
 */

/* @test id=UpcallStack-TT
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.DowncallLinker.USE_SPEC=true
 *   -Djdk.internal.foreign.UpcallLinker.USE_SPEC=true
 *   TestUpcallStack
 */

/*
 * @test id=VarArgs
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   TestVarArgs
 */
