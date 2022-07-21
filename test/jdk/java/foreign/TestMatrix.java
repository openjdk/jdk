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

/*
 * Note: to run this test manually, you need to build the tests first to get native
 * libraries compiled, and then execute it with plain jtreg, like:
 *
 *  $ bin/jtreg -jdk:<path-to-tested-jdk> \
 *              -nativepath:<path-to-build-dir>/support/test/jdk/jtreg/native/manual/lib/ \
 *              -concurrency:auto \
 *              ./test/jdk/java/foreign/TestMatrix.java
 */

/* @test id=UpcallHighArity-FF
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-TF
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-FT
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-TT
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   TestUpcallHighArity
 */

/* @test id=Downcall-F
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestDowncall
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   TestDowncall
 */

/* @test id=Downcall-T
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestDowncall
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   TestDowncall
 */

/* @test id=UpcallScope-FF
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   TestUpcallScope
 */

/* @test id=UpcallScope-TF
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   TestUpcallScope
 */

/* @test id=UpcallScope-FT
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   TestUpcallScope
 */

/* @test id=UpcallScope-TT
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   TestUpcallScope
 */

/* @test id=UpcallAsync-FF
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   TestUpcallAsync
 */

/* @test id=UpcallAsync-TF
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   TestUpcallAsync
 */

/* @test id=UpcallAsync-FT
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   TestUpcallAsync
 */

/* @test id=UpcallAsync-TT
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   TestUpcallAsync
 */

/* @test id=UpcallStack-FF
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   TestUpcallStack
 */

/* @test id=UpcallStack-TF
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   TestUpcallStack
 */

/* @test id=UpcallStack-FT
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   TestUpcallStack
 */

/* @test id=UpcallStack-TT
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   TestUpcallStack
 */
