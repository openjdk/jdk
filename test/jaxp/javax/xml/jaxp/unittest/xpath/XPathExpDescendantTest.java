/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package xpath;


import org.testng.annotations.DataProvider;

/*
 * @test
 * @bug 8289510
 * @library /javax/xml/jaxp/unittest
 * @run testng/othervm xpath.XPathExpDescendantTest
 * @summary Tests for XPath descendant/descendant-or-self axis specifier.
 */
public class XPathExpDescendantTest extends XPathTestBase {

    /*
     * DataProvider: provides XPath descendant expressions and expected number of nodes returned
     */
    @DataProvider(name = "namespaceXpath")
    public Object[][] getNamespaceXpathExpression() {
        return new Object[][] {
                {"/Customers/namespace::foo", "foo", "xmlns", "xmlns:foo","www.foo.com"},
                {"/Customers/namespace::xml", "xml", "xml", "xmlns:xml", "http://www.w3.org/XML/1998/namespace"},
                {"//Customer/Name/namespace::foo", "foo", "xmlns", "xmlns:foo","www.foo.com"},
                {"/Customers/Customer/Name/namespace::foo", "foo", "xmlns", "xmlns:foo","www.foo.com"},
                {"//Customer/Name/namespace::xml", "xml", "xml", "xmlns:xml","http://www.w3.org/XML/1998/namespace"},
                {"/Customers/Customer/Name/namespace::xml", "xml", "xml", "xmlns:xml","http://www.w3.org/XML/1998/namespace"},
                {"//Customer/Name/namespace::dog", "dog", "xmlns", "xmlns:dog","www.pets.com"},
                {"/Customers/Customer/Name/namespace::dog", "dog", "xmlns", "xmlns:dog","www.pets.com"},
                {"//www.foo.com:Customer/namespace::foo", "foo", "xmlns", "xmlns:foo", "www.foo.com"},
                {"/Customers/*[name() = 'foo:Customer']/namespace::foo", "foo", "xmlns", "xmlns:foo", "www.foo.com"},
                {"/Customers/*[namespace-uri() = 'www.foo.com']/namespace::foo", "foo", "xmlns", "xmlns:foo", "www.foo.com"},
                {"/Customers/*[contains(name(.), 'foo:')]/namespace::foo", "foo", "xmlns", "xmlns:foo", "www.foo.com"},
                {"/Customers/*[starts-with(name(.), 'foo:')]/namespace::foo", "foo", "xmlns", "xmlns:foo", "www.foo.com"},
                {"//*[local-name()='Customer' and namespace-uri() = 'www.foo.com']/namespace::foo", "foo", "xmlns", "xmlns:foo", "www.foo.com"},
                {"/Customers/VendCustomer/default-namespace-uri:Address/namespace::*[name()='']", "xmlns", null, "xmlns","default-namespace-uri"},
                {"/Customers/VendCustomer/default-namespace-uri:Address/redeclared-namespace-uri:City/namespace::*[name()='']", "xmlns", null, "xmlns","redeclared-namespace-uri"}
        };
    }


}
