/*
 * This is the test JavaScript program used in jjs-argsTest.sh
 */

if (typeof(arguments) == 'undefined') {
    throw new Error("arguments expected");
}

if (arguments.length != 2) {
    throw new Error("2 arguments are expected here");
}

if (arguments[0] != 'hello') {
    throw new Error("First arg should be 'hello'");
}

if (arguments[1] != 'world') {
    throw new Error("Second arg should be 'world'");
}

print("Passed");
