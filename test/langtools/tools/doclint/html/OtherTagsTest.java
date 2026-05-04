/*
 * @test /nodynamiccopyright/
 * @bug 8006251 8022173 8247957
 * @summary test other tags
 * @library ..
 * @modules jdk.javadoc/jdk.javadoc.internal.doclint
 * @build DocLintTester
 * @run main DocLintTester -Xmsgs:all,-missing -ref OtherTagsTest.out OtherTagsTest.java
 */

/** */
public class OtherTagsTest {
    /**
     *  <body> <p> abc </body>
     *  <frame>
     *  <frameset> </frameset>
     *  <head> </head>
     *  <hr width="50%">
     *  <link rel="http://example.com/stylesheet.css">
     *  <meta>
     *  <noframes> </noframes>
     *  <script> </script>
     *  <title> </title>
     */
    public void knownInvalidTags() { }
}
