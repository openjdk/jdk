/*
 * Note: to run this test manually, you need to build the tests first to get native
 * libraries compiled, and then execute it with plain jtreg, like:
 *
 *  $ bin/jtreg -jdk:<path-to-tested-jdk> \
 *              -nativepath:<path-to-build-dir>/support/test/jdk/jtreg/native/lib/ \
 *              -concurrency:auto \
 *              ./test/jdk/java/foreign/TestMatrix.java
 */

/*
 * @test id=UpcallHighArity-FFTT
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=true
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-TFTT
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=true
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-FTTT
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=true
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-TTTT
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=true
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-FFTF
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=false
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-TFTF
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=false
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-FTTF
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=false
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-TTTF
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=false
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-FFFT
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=true
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-TFFT
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=true
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-FTFT
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=true
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-TTFT
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=true
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-FFFF
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=false
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-TFFF
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=false
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-FTFF
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=false
 *   TestUpcallHighArity
 */

/* @test id=UpcallHighArity-TTFF
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallHighArity
 *
 * @run testng/othervm/native/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=false
 *   TestUpcallHighArity
 */

/* @test id=Downcall-FF
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestDowncall
 *
 * @run testng/othervm/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=false
 *   TestDowncall
 */

/* @test id=Downcall-TF
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestDowncall
 *
 * @run testng/othervm/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=false
 *   TestDowncall
 */

/* @test id=Downcall-FT
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestDowncall
 *
 * @run testng/othervm/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   TestDowncall
 */

/* @test id=Downcall-TT
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestDowncall
 *
 * @run testng/othervm/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   TestDowncall
 */

/* @test id=Upcall-TFTT
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcall
 *
 * @run testng/othervm/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=true
 *   TestUpcall
 */

/* @test id=Upcall-FTTT
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcall
 *
 * @run testng/othervm/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=true
 *   TestUpcall
 */

/* @test id=Upcall-TTTT
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcall
 *
 * @run testng/othervm/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=true
 *   TestUpcall
 */

/* @test id=Upcall-TFTF
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcall
 *
 * @run testng/othervm/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=false
 *   TestUpcall
 */

/* @test id=Upcall-FTTF
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcall
 *
 * @run testng/othervm/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=false
 *   TestUpcall
 */

/* @test id=Upcall-TTTF
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcall
 *
 * @run testng/othervm/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=false
 *   TestUpcall
 */

/* @test id=Upcall-TFFT
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcall
 *
 * @run testng/othervm/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=true
 *   TestUpcall
 */

/* @test id=Upcall-FTFT
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcall
 *
 * @run testng/othervm/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=true
 *   TestUpcall
 */

/* @test id=Upcall-TTFT
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcall
 *
 * @run testng/othervm/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=true
 *   TestUpcall
 */

/* @test id=Upcall-TFFF
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcall
 *
 * @run testng/othervm/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=false
 *   TestUpcall
 */

/* @test id=Upcall-FTFF
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcall
 *
 * @run testng/othervm/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=false
 *   TestUpcall
 */

/* @test id=Upcall-TTFF
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcall
 *
 * @run testng/othervm/manual
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableUpcallHandler.USE_INTRINSICS=false
 *   TestUpcall
 */
