/*
 * @test  /nodynamiccopyright/
 * @bug 4336282 4785453
 * @summary Verify that extending an erray class does not crash the compiler.
 *
 * @compile/fail/ref=ExtendArray.out -XDstdout -XDdiags=%b:%l:%_%m ExtendArray.java
 */

// Note that an error is expected, but not a crash.

public class ExtendArray extends Object[] {}
