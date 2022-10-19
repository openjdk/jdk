/**
 * @test /nodynamiccopyright/
 * @bug 8072480
 * @summary Verify that javac rejects Java 17 program with --release 11
 * @compile ReleaseOption.java
 * @compile/fail/ref=ReleaseOption.out -XDrawDiagnostics --release 11 ReleaseOption.java
 */

interface ReleaseOption extends java.util.random.RandomGenerator {
}
