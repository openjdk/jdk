/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 6786682
 * @summary This test verifies the use of lang attribute by <HTML>.
 * @author Bhavesh Patel
 * @library ../lib
 * @modules jdk.javadoc
 * @build JavadocTester
 * @run main TestHtmlTag
 */

import java.util.Locale;

public class TestHtmlTag extends JavadocTester {
    private static final String defaultLanguage = Locale.getDefault().getLanguage();
    public static void main(String... args) throws Exception {
        TestHtmlTag tester = new TestHtmlTag();
        tester.runTests();
    }
    @Test
    void test_default() {
        javadoc("-locale", defaultLanguage,
                "-d", "out-default",
                "-sourcepath", testSrc,
                "pkg1");

        checkExit(Exit.OK);

        checkOutput("pkg1/C1.html", true,
            "<html lang=\"" + defaultLanguage + "\">");

        checkOutput("pkg1/package-summary.html", true,
            "<html lang=\"" + defaultLanguage + "\">");

        checkOutput("pkg1/C1.html", false,
                "<html>");
    }

    @Test
    void test_ja() {
        // TODO: why does this test need/use pkg2; why can't it use pkg1
        // like the other two tests, so that we can share the check methods?
        javadoc("-locale", "ja",
                "-d", "out-ja",
                "-sourcepath", testSrc,
                "pkg2");
        checkExit(Exit.OK);

        checkOutput("pkg2/C2.html", true,
                "<html lang=\"ja\">");

        checkOutput("pkg2/package-summary.html", true,
                "<html lang=\"ja\">");

        checkOutput("pkg2/C2.html", false,
                "<html>");
    }

    @Test
    void test_en_US() {
        javadoc("-locale", "en_US",
                "-d", "out-en_US",
                "-sourcepath", testSrc,
                "pkg1");
        checkExit(Exit.OK);

        checkOutput("pkg1/C1.html", true,
                "<html lang=\"en\">");

        checkOutput("pkg1/package-summary.html", true,
                "<html lang=\"en\">");

        checkOutput("pkg1/C1.html", false,
                "<html>");
    }

    @Test
    void test_other() {
        javadoc("-locale", "en_US",
                "-d", "out-other",
                "-sourcepath", testSrc,
                "pkg3");
        checkExit(Exit.OK);

        checkOutput("pkg3/package-summary.html", true,
                "<h2 title=\"Package pkg3 Description\">Package pkg3 Description</h2>\n"
                + "<div class=\"block\"><p>This is the first line."
                + " Note the newlines before the &lt;p&gt; is relevant.</div>");

        checkOutput("pkg3/A.DatatypeFactory.html", true,
                "<div class=\"block\"><p>\n"
                + " Factory that creates new <code>javax.xml.datatype</code>\n"
                + " <code>Object</code>s that map XML to/from Java <code>Object</code>s.</p>\n"
                + "\n"
                + " <p>\n"
                + " A new instance of the <code>DatatypeFactory</code> is created through the\n"
                + " <a href=\"../pkg3/A.DatatypeFactory.html#newInstance--\"><code>newInstance()</code></a> method that uses the following implementation\n"
                + " resolution mechanisms to determine an implementation:</p>\n"
                + " <ol>\n"
                + " <li>\n"
                + " If the system property specified by <a href=\"../pkg3/A.DatatypeFactory.html#DATATYPEFACTORY_PROPERTY\"><code>DATATYPEFACTORY_PROPERTY</code></a>,\n"
                + " \"<code>javax.xml.datatype.DatatypeFactory</code>\", exists, a class with\n"
                + " the name of the property value is instantiated. Any Exception thrown\n"
                + " during the instantiation process is wrapped as a\n"
                + " <code>IllegalStateException</code>.\n"
                + " </li>\n"
                + " <li>\n"
                + " If the file ${JAVA_HOME}/lib/jaxp.properties exists, it is loaded in a\n"
                + " <code>Properties</code> <code>Object</code>. The\n"
                + " <code>Properties</code> <code>Object </code> is then queried for the\n"
                + " property as documented in the prior step and processed as documented in\n"
                + " the prior step.\n"
                + " </li>\n"
                + " <li>\n"
                + " Uses the service-provider loading facilities, defined by the\n"
                + " <code>ServiceLoader</code> class, to attempt to locate and load an\n"
                + " implementation of the service using the default loading mechanism:\n"
                + " the service-provider loading facility will use the current thread's context class loader\n"
                + " to attempt to load the service. If the context class loader is null, the system class loader will be used.\n"
                + " <br>\n"
                + " In case of <code>service configuration error</code> a\n"
                + " <code>DatatypeConfigurationException</code> will be thrown.\n"
                + " </li>\n"
                + " <li>\n"
                + " The final mechanism is to attempt to instantiate the <code>Class</code>\n"
                + " specified by <a href=\"../pkg3/A.DatatypeFactory.html#DATATYPEFACTORY_IMPLEMENTATION_CLASS\">"
                + "<code>DATATYPEFACTORY_IMPLEMENTATION_CLASS</code></a>. Any Exception\n"
                + " thrown during the instantiation process is wrapped as a\n"
                + " <code>IllegalStateException</code>.\n"
                + " </li>\n"
                + " </ol></div>");

        checkOutput("pkg3/A.ActivationDesc.html", true,
                "<pre>public class <span class=\"typeNameLabel\">A.ActivationDesc</span>\n"
                + "extends java.lang.Object\n"
                + "implements java.io.Serializable</pre>\n"
                + "<div class=\"block\">An activation descriptor contains the information necessary to activate\n"
                + " an object: <ul>\n"
                + " <li> the object's group identifier,\n"
                + " <li> the object's fully-qualified class name,\n"
                + " <li> the object's code location (the location of the class), a codebase\n"
                + " URL path,\n"
                + " <li> the object's restart \"mode\", and,\n"
                + " <li> a \"marshalled\" object that can contain object specific\n"
                + " initialization data. </ul>\n"
                + "\n"
                + " <p>\n"
                + " A descriptor registered with the activation system can be used to\n"
                + " recreate/activate the object specified by the descriptor. The\n"
                + " <code>MarshalledObject</code> in the object's descriptor is passed as the\n"
                + " second argument to the remote object's constructor for object to use\n"
                + " during reinitialization/activation.</div>");

         checkOutput("pkg3/A.ActivationGroupID.html", true,
                 "<pre>public class <span class=\"typeNameLabel\">A.ActivationGroupID</span>\n"
                 + "extends java.lang.Object\n"
                 + "implements java.io.Serializable</pre>\n"
                 + "<div class=\"block\">The identifier for a registered activation group serves several purposes:\n"
                 + " <ul>\n"
                 + " <li>identifies the group uniquely within the activation system, and\n"
                 + " <li>contains a reference to the group's activation system so that the\n"
                 + " group can contact its activation system when necessary.</ul><p>\n"
                 + "\n"
                 + " The <code>ActivationGroupID</code> is returned from the call to\n"
                 + " <code>ActivationSystem.registerGroup</code> and is used to identify the\n"
                 + " group within the activation system. This group id is passed as one of the\n"
                 + " arguments to the activation group's special constructor when an\n"
                 + " activation group is created/recreated.</div>\n"
                 + "<dl>");
    }
}
