/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.transform;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.testng.Assert;
import org.testng.annotations.Test;

/*
 * @bug 6505031
 * @summary Test transformer parses keys and their values coming from different xml documents.
 */
public class Bug6505031 {

    private String getResource(String s) {
        return getClass().getResource(s).toString();

    }

    @Test
    public void test() {
        Map params = new HashMap();

        params.put("config", getResource("config.xml"));
        params.put("mapsFile", getResource("maps.xml"));
        generate(getResource("template.xml"), getResource("transform.xsl"), params);
    }

    private void generate(String in, String xsl, Map params) {
        try {
            Transformer transformer = getTransformer(xsl);

            for (Iterator i = params.entrySet().iterator(); i.hasNext();) {
                Map.Entry entry = (Map.Entry) i.next();

                transformer.setParameter((String) entry.getKey(), entry.getValue());
            }
            transform(in, transformer);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    private Transformer getTransformer(String transform) throws Exception {
        TransformerFactory tfactory = TransformerFactory.newInstance();

        try {
            // tfactory.setAttribute("generate-translet", Boolean.TRUE);
        } catch (Exception e) {
            // Ignore
        }

        Transformer transformer = tfactory.newTransformer(new StreamSource(transform));
        return (transformer);
    }

    private void transform(String in, Transformer transformer) throws Exception {
        StringWriter sw = new StringWriter();
        transformer.transform(new StreamSource(in), new StreamResult(sw));
        String s = sw.toString();
        Assert.assertTrue(s.contains("map1key1value") && s.contains("map2key1value"));
    }

}
