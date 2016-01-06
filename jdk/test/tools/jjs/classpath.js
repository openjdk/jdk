/*
 * This is the test JavaScript program used in jjs-cpTest.sh
 */

var v = new Packages.Hello();
if (v.string != 'hello') {
    throw new Error("Unexpected property value");
}
