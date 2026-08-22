/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package common.access;

import jdk.xml.internal.AccessRule;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


/*
 * @test
 * @bug 8357394
 * @summary Verifies access rules defined by property jdk.xml.resource.access
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest /test/lib
 * @modules java.xml/jdk.xml.internal
 * @run junit/othervm common.access.AccessRuleTest
 */
public class AccessRuleTest {
    /**
     * Returns test data for testAccessRule.
     * Data: rules, URI strings, result (true if allowed, false otherwise)
     * @return test data for testAccessRule
     */
    private static Stream<Arguments> testData() {

        return Stream.of(
            Arguments.of("*", "http://all.access", true),
            Arguments.of("", "http://no.access", false),
            Arguments.of("http:*; http:/*; http://*", "http://all.http.access", true),
            Arguments.of("http://*.oracle.com", "http://subdomains.oracle.com/dtds/example.dtd", true),
            Arguments.of("https:*;https:/*;https://*", "https://all.https.access", true),
            Arguments.of("https://*.oracle.com", "https://subdomains.oracle.com/dtds/example.dtd", true),
            Arguments.of("file:*;file:/*;file://*;file:///*", "file://all.file.access", true),
            Arguments.of("http://www.oracle.com", "http://www.oracle.com/dtds/example.dtd", true),
            Arguments.of("http://www.oracle.com, http://*.oracle.com",
                "http://www.oracle.com/dtds/example.dtd; http://subdomains.oracle.com/dtds/example.dtd", true),
            Arguments.of("file:/dtds/dtd1.dtd", "file:/dtds/dtd1.dtd", true),
            Arguments.of("file:/dtds/dtd1.dtd, file:/xsds/*", "file:/dtds/dtd1.dtd; file:/xsds/example.xsd", true),
            Arguments.of("file:/dir/*", "file:/dir/child.dtd", true),
            Arguments.of("file:/dir/*", "file:/dir/../foo.dtd; file:/dir/%2e%2e/foo.dtd", false),
            Arguments.of("file:/*", "file:/dir/../../foo.dtd; file:/../foo.dtd", true),
            Arguments.of("http://www.oracle.com, file:/dtds/dtd1.dtd, file:/xsds/*",
                "http://www.oracle.com/dtds/example.dtd; file:/dtds/dtd1.dtd; file:/xsds/example.xsd", true),
            Arguments.of("http://[2001:db8::1]",
                "http://[2001:0db8:0000:0000:0000:0000:0000:0001]/dtds/example.dtd; "
                    + "http://[2001:db8:0:0:0:0:0:1]/dtds/example.dtd", true),
            Arguments.of("http://[2001:0db8:0000:0000:0000:0000:0000:0001]",
                "http://[2001:db8::1]/dtds/example.dtd", true),
            Arguments.of("jrt:*; jrt:/java.xml/*", "jrt:/java.xml/jdk/xml/internal/jdkcatalog/JDKCatalog.xml", true),
            Arguments.of("file:/tmp/foo.jar",
                "jar:file:/tmp/foo.jar!/dtds/example.dtd; jar:file:/tmp/foo.jar!/xsds/example.xsd", true),
            Arguments.of("file:/tmp/foo.jar", "jar:file:/tmp/bar.jar!/dtds/example.dtd", false)
        );
    }

    /**
     * Returns test data for testInvalidRules.
     * Data: rules, exception class
     * @return test data for testInvalidRules
     */
    private static Stream<Arguments> testInvalidInput() {

        return Stream.of(
            Arguments.of("http://:8080", IllegalArgumentException.class),
            Arguments.of("http:///dtds", IllegalArgumentException.class),
            Arguments.of("scheme", IllegalArgumentException.class),
            Arguments.of("http", IllegalArgumentException.class),
            Arguments.of("http:", IllegalArgumentException.class),
            Arguments.of("http://:8080", IllegalArgumentException.class),
            Arguments.of("http://example.com:", IllegalArgumentException.class),
            Arguments.of("http:///dtds", IllegalArgumentException.class),
            Arguments.of("http://example.com, , file:*", IllegalArgumentException.class),
            Arguments.of("http://example.com, *, file:*", IllegalArgumentException.class),
            Arguments.of("jar:file:/tmp/foo.jar!/dtds/*", IllegalArgumentException.class)
        );
    }

    /**
     * Verifies that the Access External Properties are supported throughout the
     * JAXP APIs.
     * @param rules the access rules separate by ";"
     * @param systemIds system IDs represented as semicolon-separated URI strings
     * @param permitted the flag indicating whether the rules permit the resource
     *                  represented by the systemId
     * @throws Exception if the test fails due to test configuration issues other
     * than the expected result
     */
    @ParameterizedTest
    @MethodSource("testData")
    public void testAccessExternalProperties(String rules, String systemIds, boolean permitted)
        throws Exception {
        String[] accessRules = rules.split(";");
        for (String rule : accessRules) {
            AccessRule accessRule = new AccessRule(rule.trim());
            String[] ids = systemIds.split(";");
            for (String systemId : ids) {
                assertEquals(accessRule.allows(URI.create(systemId.trim())), permitted);
            }
        }
    }

    /**
     * Verifies that the specified rule is invalid.
     * @param rule indicates whether there is a custom resolver
     * @param expectedType the expected throw type
     * @throws Exception if the test fails other than the expected Exception, which
     * would indicate an issue in configuring the test
     */
    @ParameterizedTest
    @MethodSource("testInvalidInput")
    public void testAccessRule(String rule, Class<Throwable> expectedType) throws Exception {
            assertThrows(expectedType, () -> parseRules(rule));

    }

    /**
     * Attempts to parse an access rule
     * @param rule the access rule

     * @throws Exception if the test fails due to test configuration issues other
     * than the expected result
     */
    private void parseRules(String rule) {
        AccessRule accessRule = new AccessRule(rule.trim());
    }
}
