/*
 * This is the test JavaScript program used in jjs-strictTest.sh
 */

try {
    v = "hello";
    throw new Error("should have thrown ReferenceError");
} catch (e) {
    if (! (e instanceof ReferenceError)) {
        throw new Error("ReferenceError expected, got " + e);
    }
}
