/*
 * @test /nodynamiccopyright/
 * @bug 8006248
 * @summary DocLint should report unknown tags
 * @build DocLintTester
 * @run main DocLintTester CustomTagTest.java
 * @run main DocLintTester -XcustomTags: -ref CustomTagTest.out CustomTagTest.java
 * @run main DocLintTester -XcustomTags:customTag -ref CustomTagTestWithOption.out CustomTagTest.java
 * @run main DocLintTester -XcustomTags:customTag,anotherCustomTag -ref CustomTagTestWithOption.out CustomTagTest.java
 * @author bpatel
 */

/**
 * @customTag Text for a custom tag.
 * @unknownTag Text for an unknown tag.
 */
public class CustomTagTest {
}

