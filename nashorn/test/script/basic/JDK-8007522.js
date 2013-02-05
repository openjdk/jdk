/**
 * JDK-8007522: IllegalStateException thrown from String.prototype.search function
 *
 * @test
 * @run
 */

var str = "hello";
// search used to result in IllegalStateException
if (str.search(/foo/g) != -1) {
    fail("String.prototype.search failed");
}

