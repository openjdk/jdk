/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8073872
 * @summary test that stackoverflow is not observable when element
 *          references containing class
 * @modules java.xml
 * @modules java.xml.bind
 * @compile Foo.java
 * @run testng/othervm SchemagenStackOverflow
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SchemagenStackOverflow {

    @Test
    public void schemagenStackOverflowTest() throws Exception {
        // Create new instance of JAXB context
        JAXBContext context = JAXBContext.newInstance(Foo.class);
        context.generateSchema(new TestOutputResolver());

        // Read schema content from file
        String content = Files.lines(resultSchemaFile).collect(Collectors.joining(""));
        System.out.println("Generated schema content:" + content);

        // Check if schema was generated: check class and list object names
        Assert.assertTrue(content.contains("name=\"Foo\""));
        Assert.assertTrue(content.contains("name=\"fooObject\""));
    }

    // Schemagen output resolver
    class TestOutputResolver extends SchemaOutputResolver {
        @Override
        public Result createOutput(String namespaceUri, String fileName)
                throws IOException {
            return new StreamResult(resultSchemaFile.toFile());
        }
    }

    // Schemagen output file name and path
    static final String SCHEMA_FILENAME = "generatedSchema.xsd";
    Path resultSchemaFile = Paths.get(System.getProperty("user.dir", "."))
            .resolve(SCHEMA_FILENAME);

}
