/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

package catalog;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import jdk.test.lib.net.URIBuilder;
import jdk.test.lib.util.JarUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.InputSource;

import javax.xml.catalog.Catalog;
import javax.xml.catalog.CatalogException;
import javax.xml.catalog.CatalogFeatures;
import javax.xml.catalog.CatalogManager;
import javax.xml.catalog.CatalogResolver;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8151154 8171243
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest /test/lib
 * @run junit/othervm catalog.CatalogFileInputTest
 * @summary Verifies that the Catalog API accepts valid URIs only;
 *          Verifies that the CatalogFeatures' builder throws
 *          IllegalArgumentException on invalid file inputs.
 *          This test was splitted from CatalogTest.java due to
 *          JDK-8168968, it has to only run without SecurityManager
 *          because an ACE will be thrown for invalid path.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class CatalogFileInputTest extends CatalogSupportBase {

    static final CatalogFeatures FEATURES = CatalogFeatures.builder().
            with(CatalogFeatures.Feature.PREFER, "system").build();
    static String CLS_DIR = System.getProperty("test.classes");
    static String SRC_DIR = System.getProperty("test.src");
    static String JAR_CONTENT = "META-INF";
    final static String SCHEME_JARFILE = "jar:";
    static final String REMOTE_FILE_LOCATION = "/jar/META-INF";
    static final String DOCROOT = SRC_DIR;
    private HttpServer httpserver;
    private String remoteFilePath;
    private ExecutorService executor;

    /*
     * Initializing fields
     */
    @BeforeAll
    public void setUpClass() throws Exception {
        super.setUp();
        // set up HttpServer
        httpserver = SimpleFileServer.createFileServer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                Path.of(DOCROOT), SimpleFileServer.OutputLevel.INFO);
        executor = Executors.newCachedThreadPool();
        httpserver.setExecutor(executor);
        httpserver.start();
        remoteFilePath = URIBuilder.newBuilder()
                .scheme("http")
                .host(httpserver.getAddress().getAddress())
                .port(httpserver.getAddress().getPort())
                .build().toString() + REMOTE_FILE_LOCATION;
    }

    @AfterAll
    protected void tearDown() {
        if (httpserver != null) {
            httpserver.stop(0);
            executor.shutdown();
        }
    }

    /*
     * Verifies that the Catalog can be created with file system paths including JAR
     * and http URL, and used to resolve a systemId as expected.
     */
    @ParameterizedTest
    @MethodSource("acceptedURI")
    public void testMatch(String uri, String sysId, String pubId, String expectedId, String msg) {
        CatalogResolver cr = CatalogManager.catalogResolver(FEATURES, URI.create(uri));
        InputSource is = cr.resolveEntity(pubId, sysId);
        assertNotNull(is, msg);
        assertEquals(expectedId, is.getSystemId(), msg);
    }

    @ParameterizedTest
    @MethodSource("invalidCatalog")
    public void testEmptyCatalog(String uri, String publicId, String msg) {
        Catalog c = CatalogManager.catalog(FEATURES, uri != null ? URI.create(uri) : null);
        assertNull(c.matchSystem(publicId), msg);
    }

    @ParameterizedTest
    @MethodSource("invalidCatalog")
    public void testCatalogResolverWEmptyCatalog(String uri, String publicId, String msg) {
        CatalogResolver cr = CatalogManager.catalogResolver(
                CatalogFeatures.builder().with(CatalogFeatures.Feature.RESOLVE, "strict").build(),
                uri != null ? URI.create(uri) : null);
        assertThrows(CatalogException.class, () -> cr.resolveEntity(publicId, ""));
    }

    @ParameterizedTest
    @MethodSource("invalidCatalog")
    public void testCatalogResolverWEmptyCatalog1(String uri, String publicId, String msg) {
        CatalogResolver cr = CatalogManager.catalogResolver(
                CatalogFeatures.builder().with(CatalogFeatures.Feature.RESOLVE, "continue").build(),
                uri != null ? URI.create(uri) : null);
        assertNull(cr.resolveEntity(publicId, ""), msg);
    }

    @ParameterizedTest
    @MethodSource("invalidInput")
    public void testFileInput(String file) {
        assertThrows(
                IllegalArgumentException.class,
                () -> CatalogFeatures.builder().with(CatalogFeatures.Feature.FILES, file));
    }

    @ParameterizedTest
    @MethodSource("invalidInput")
    public void testInvalidUri(String file) {
        URI uri = file != null ? URI.create(file) : null;
        assertThrows(IllegalArgumentException.class, () -> CatalogManager.catalogResolver(FEATURES, uri));
    }

    @ParameterizedTest
    @MethodSource("invalidInput")
    public void testInvalidUri1(String file) {
        URI uri = file != null ? URI.create(file) : null;
        assertThrows(IllegalArgumentException.class, () -> CatalogManager.catalog(FEATURES, uri));
    }

    @Test
    public void testNull() {
        assertThrows(
                NullPointerException.class,
                () -> CatalogFeatures.builder().with(CatalogFeatures.Feature.FILES, null));
    }

    @Test
    public void testNullUri() {
        assertThrows(NullPointerException.class, () -> CatalogManager.catalogResolver(FEATURES, (URI) null));
        assertThrows(NullPointerException.class, () -> CatalogManager.catalog(FEATURES, (URI) null));
    }

    private static final String systemId = "http://www.sys00test.com/rewrite.dtd";
    private static final String publicId = "PUB-404";
    private static final String expected = "http://www.groupxmlbase.com/dtds/rewrite.dtd";
    private static final String errMsg = "Relative rewriteSystem with xml:base at group level failed";

    /*
        DataProvider: used to verify CatalogResolver's resolveEntity function.
        Data columns:
        catalog, systemId, publicId, expectedUri, msg
     */
    Object[][] acceptedURI() throws IOException {
        String filename = "rewriteSystem_id.xml";
        String urlFile = getClass().getResource(filename).toExternalForm();
        String urlHttp = remoteFilePath + "/jax-ws-catalog.xml";
        String remoteXSD = remoteFilePath + "/catalog/ws-addr.xsd";
        File file = new File(CLS_DIR + "/JDK8171243.jar!/META-INF/jax-ws-catalog.xml");
        String jarPath = SCHEME_JARFILE + file.toURI().toString();
        String xsd = jarPath.substring(0, jarPath.lastIndexOf("/")) + "/catalog/ws-addr.xsd";

        // create JAR file
        JarUtils.createJarFile(Paths.get(CLS_DIR + "/JDK8171243.jar"), Paths.get(SRC_DIR + "/jar"), JAR_CONTENT);

        return new Object[][]{
            // URL
            {urlFile, systemId, publicId, expected, errMsg},
            {urlHttp, "http://www.w3.org/2006/03/addressing/ws-addr.xsd", "", remoteXSD, "http test failed."},
            // JAR file
            {jarPath, "http://www.w3.org/2006/03/addressing/ws-addr.xsd", "", xsd, "jar file test failed."},
        };
    }

    /*
     *  DataProvider: invalid catalog result in empty catalog
     *  Note: the difference from invalidInput is that invalidInput is syntactically
     *  rejected with an IAE.
     */
    public Object[][] invalidCatalog() {
        String catalogUri = getClass().getResource("catalog_invalid.xml").toExternalForm();
        return new Object[][]{
            {catalogUri, "-//W3C//DTD XHTML 1.0 Strict//EN",
                "The catalog is invalid, attempting to match the public entry shall return null."},
            {"file:/../../..", "-//W3C//DTD XHTML 1.0 Strict//EN",
                "The catalog is invalid, attempting to match the public entry shall return null."}
        };
    }

    /*
     *  DataProvider: a list of invalid inputs, expects IAE
     *  Note: exclude null since NPE would have been expected
     */
    public Object[][] invalidInput() throws Exception {
        String filename = "rewriteSystem_id.xml";
        copyFile(Paths.get(SRC_DIR + "/" + filename), Paths.get(filename));
        String absolutePath = getClass().getResource(filename).getFile();

        return new Object[][] {
                { "c:/te:t" },
                { "c:/te?t" },
                { "c/te*t" },
                { "shema:invalid.txt" },
                // relative file path
                { filename },
                // absolute file path
                { absolutePath }
        };
    }

    private static void copyFile(final Path src, final Path target) throws Exception {
        try (InputStream in = Files.newInputStream(src);
                BufferedReader reader
                = new BufferedReader(new InputStreamReader(in));
                OutputStream out = new BufferedOutputStream(
                        Files.newOutputStream(target, CREATE, APPEND));
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out))) {
            String line;
            while ((line = reader.readLine()) != null) {
                bw.write(line);
            }
        } catch (IOException x) {
            throw new Exception(x.getMessage());
        }
    }
}
