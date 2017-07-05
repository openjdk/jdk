/*
 * This is the test JavaScript program used in jjs-DTest.sh
 */

var Sys = java.lang.System;
if (Sys.getProperty("jjs.foo") == "bar") {
    print("Passed");
} else {
    // unexpected value
    throw new Error("Unexpected System property value");
}
