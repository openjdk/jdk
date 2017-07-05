/*
 * This is the test JavaScript program used in jjs-es6Test.sh
 */

const X = 4;
try {
    X = 55;
    throw new Error("should have thrown TypeError");
} catch (e) {
    if (! (e instanceof TypeError)) {
        throw new Error("TypeError expected, got " + e);
    }
}
