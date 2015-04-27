/*
 * Copyright (c) 2002, 2015, Oracle and/or its affiliates. All rights reserved.
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

package test.astro;

import static java.lang.String.valueOf;
import static org.testng.Assert.assertEquals;
import static test.astro.AstroConstants.ASTROCAT;
import static test.astro.AstroConstants.GOLDEN_DIR;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import javax.xml.transform.sax.TransformerHandler;

import jaxp.library.JAXPFileBaseTest;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/*
 * @summary run astro application, test xslt
 *
 * There are vast amounts of textual astronomical data, typically user is
 * interested in a small subset, which is the result from carrying out a query.
 * A query can be composed of one or more filters, for example, the user could
 * query the database for all stars of visual magnitude down to 6.5 that lie
 * between right ascensions 0 h to 2 h, and between declinations of 45 to 90 degrees.
 *
 * Astro application uses JAXP to query astronomical data saved in an XML dataset.
 * A FilterFactory implementation creates filter(A filter is an instance of a JAXP
 * TransformerHandler) from an XSL stylesheet.
 * A InputSourceFactory implementation creates a new sax input source from an XML file.
 * AstroProcessor leverages InputSourceFactory to parse catalog.xml, which saves
 * textual astronomical data, and then creates filters with specified parameters
 * from FilterFactory, all of the filters are chained together, AstroProcessor
 * appends the HTML filter at the end of filter chain, and hooks up the chain to
 * the input source, finally processes and outputs to the user specified output file.
 *
 * AstroTest drives AstroProcessor to run the specified queries(total 4 in setup),
 * and then compares the output with the golden files to determine PASS or FAIL.
 * It provides variant implementations of FilterFactory and InputSourceFactory to
 * AstroProcessor to test different JAXP classes and features.
 *
 */
public class AstroTest extends JAXPFileBaseTest {
    private FiltersAndGolden[] data;

    @BeforeClass
    public void setup() throws Exception {
        data = new FiltersAndGolden[4];
        data[0] = new FiltersAndGolden(getGoldenFileContent(1), astro -> astro.getRAFilter(0.106, 0.108));
        data[1] = new FiltersAndGolden(getGoldenFileContent(2), astro -> astro.getStellarTypeFilter("K0IIIbCN-0.5"));
        data[2] = new FiltersAndGolden(getGoldenFileContent(3), astro -> astro.getStellarTypeFilter("G"), astro -> astro.getDecFilter(-5.0, 60.0));
        data[3] = new FiltersAndGolden(getGoldenFileContent(4), astro -> astro.getRADECFilter(0.084, 0.096, -5.75, 14.0));
    }

    /*
     * Provide permutations of InputSourceFactory and FilterFactory for test.
     */
    @DataProvider(name = "factories")
    public Object[][] getQueryFactories() {
        return new Object[][] {
                { StreamFilterFactoryImpl.class, InputSourceFactoryImpl.class },
                { SAXFilterFactoryImpl.class, InputSourceFactoryImpl.class },
                { DOMFilterFactoryImpl.class, InputSourceFactoryImpl.class },
                { TemplatesFilterFactoryImpl.class, InputSourceFactoryImpl.class },
                { StreamFilterFactoryImpl.class, DOML3InputSourceFactoryImpl.class } };
    }

    @Test(dataProvider = "factories")
    public void test(Class<FilterFactory> fFactClass, Class<InputSourceFactory> isFactClass) throws Exception {
        System.out.println(fFactClass.getName() +" : " + isFactClass.getName());
        AstroProcessor astro = new AstroProcessor(fFactClass, ASTROCAT, isFactClass);

        for (int i = 0; i < data.length; i++) {
            runProcess(astro, valueOf(i + 1), data[i].getGolden(), data[i].getFilters());
        }
    }

    private void runProcess(AstroProcessor astro, String processNum, List<String> goldenfileContent, FilterCreator... filterCreators) throws Exception {
        System.out.println("run process " + processNum);
        TransformerHandler[] filters = new TransformerHandler[filterCreators.length];
        for (int i = 0; i < filterCreators.length; i++)
            filters[i] = filterCreators[i].createFilter(astro);

        String outputfile = Files.createTempFile(Paths.get("").toAbsolutePath(), "query" + processNum + ".out.", null).toString();
        System.out.println("output file: " + outputfile);
        astro.process(outputfile, filters);
        assertEquals(Files.readAllLines(Paths.get(outputfile)), goldenfileContent);
    }

    private List<String>  getGoldenFileContent(int num) throws IOException {
        return Files.readAllLines(Paths.get(GOLDEN_DIR + "query" + num + ".out"));
    }

    @FunctionalInterface
    private interface FilterCreator {
        TransformerHandler createFilter(AstroProcessor astro) throws Exception;
    }

    private static class FiltersAndGolden {
        private FilterCreator[] filters;
        private List<String> golden;

        FiltersAndGolden(List<String> golden, FilterCreator... filters) {
            this.filters = filters;
            this.golden = golden;
        }

        FilterCreator[] getFilters() {
            return filters;
        }

        List<String> getGolden() {
            return golden;
        }
    }
}
