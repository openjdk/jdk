/**
 * JDK-8008305: ScriptEngine.eval should offer the ability to provide a codebase
 *
 * @test
 * @run
 */

var URLReader = Java.type("jdk.nashorn.api.scripting.URLReader");
var URL = Java.type("java.net.URL");
var File = Java.type("java.io.File");
var JString = Java.type("java.lang.String");
var SourceHelper = Java.type("jdk.nashorn.test.models.SourceHelper");

var url = new File(__FILE__).toURI().toURL();
var reader = new URLReader(url);

// check URLReader.getURL() method
//Assert.assertEquals(url, reader.getURL());

// check URL read
// read URL content by directly reading from URL
var str = SourceHelper.readFully(url);
// read URL content via URLReader
var content = new JString(SourceHelper.readFully(reader));

// assert that the content is same
Assert.assertEquals(str, content);
