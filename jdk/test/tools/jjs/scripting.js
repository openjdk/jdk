/*
 * This is the test JavaScript program used in jjs-scriptingTest.sh
 */

var str = <<END
Multi line string
works in scripting
END

var n = "Nashorn";
var hello = "Hello, ${n}";
if (hello != "Hello, Nashorn") {
    throw new Error("string interpolation didn't work");
}

if (typeof readFully != "function") {
    throw new Error("readFully is defined in -scripting");
}
