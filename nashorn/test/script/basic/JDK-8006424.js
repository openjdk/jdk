/**
 * JDK-8006424 : Passing null or undefined to adapter class constructors results in NPE or ClassCastException
 *
 * @test
 * @run
 */

function check(callback) {
    try {
        callback();
        fail("should have thrown exception");
    } catch (e) {
        if (! (e instanceof TypeError)) {
            fail("TypeError expected, but got " + e);
        }
    }
}

check(function() { new java.lang.ClassLoader(null) });
check(function() { new java.lang.ClassLoader(undefined) });
check(function() { new java.lang.Runnable(null) });
check(function() { new java.lang.Runnable(undefined) });
