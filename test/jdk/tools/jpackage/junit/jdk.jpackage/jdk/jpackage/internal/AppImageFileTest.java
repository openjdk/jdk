/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.junit.Assert;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.function.ThrowingRunnable;

public class AppImageFileTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testIdentity() throws IOException {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(Arguments.CLIOptions.NAME.getId(), "Foo");
        params.put(Arguments.CLIOptions.APPCLASS.getId(), "TestClass");
        params.put(Arguments.CLIOptions.VERSION.getId(), "2.3");
        params.put(Arguments.CLIOptions.DESCRIPTION.getId(), "Duck is the King");
        AppImageFile aif = create(params);

        Assert.assertEquals("Foo", aif.getLauncherName());
    }

    @Test
    public void testInvalidCommandLine() throws IOException {
        // Just make sure AppImageFile will tolerate jpackage params that would
        // never create app image at both load/save phases.
        // People would edit this file just because they can.
        // We should be ready to handle curious minds.
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("invalidParamName", "randomStringValue");
        params.put(Arguments.CLIOptions.APPCLASS.getId(), "TestClass");
        params.put(Arguments.CLIOptions.MAIN_JAR.getId(), "test.jar");
        create(params);

        params = new LinkedHashMap<>();
        params.put(Arguments.CLIOptions.NAME.getId(), "foo");
        params.put(Arguments.CLIOptions.APPCLASS.getId(), "TestClass");
        params.put(Arguments.CLIOptions.VERSION.getId(), "1.0");
        create(params);
    }

    @Test
    public void testInavlidXml() throws IOException {
        assertInvalid(() -> createFromXml("<foo/>"));
        assertInvalid(() -> createFromXml("<jpackage-state/>"));
        assertInvalid(() -> createFromXml(JPACKAGE_STATE_OPEN, "</jpackage-state>"));
        assertInvalid(() -> createFromXml(
                JPACKAGE_STATE_OPEN,
                    "<main-launcher></main-launcher>",
                "</jpackage-state>"));
        assertInvalid(() -> createFromXml(
                JPACKAGE_STATE_OPEN,
                    "<main-launcher>Foo</main-launcher>",
                    "<main-class></main-class>",
                "</jpackage-state>"));
        assertInvalid(() -> createFromXml(
                JPACKAGE_STATE_OPEN,
                    "<launcher>A</launcher>",
                    "<launcher>B</launcher>",
                "</jpackage-state>"));
    }

    @Test
    public void testValidXml() throws IOException {
        Assert.assertEquals("Foo", (createFromXml(
                JPACKAGE_STATE_OPEN,
                    "<app-version>1.0</app-version>",
                    "<main-launcher>Foo</main-launcher>",
                    "<main-class>main.Class</main-class>",
                    "<signed>false</signed>",
                    "<app-store>false</app-store>",
                "</jpackage-state>")).getLauncherName());

        Assert.assertEquals("Boo", (createFromXml(
                JPACKAGE_STATE_OPEN,
                    "<app-version>1.0</app-version>",
                    "<main-launcher>Boo</main-launcher>",
                    "<main-launcher>Bar</main-launcher>",
                    "<main-class>main.Class</main-class>",
                    "<signed>false</signed>",
                    "<app-store>false</app-store>",
                "</jpackage-state>")).getLauncherName());

        var file = createFromXml(
                JPACKAGE_STATE_OPEN,
                    "<app-version>1.0</app-version>",
                    "<main-launcher>Foo</main-launcher>",
                    "<main-class>main.Class</main-class>",
                    "<signed>false</signed>",
                    "<app-store>false</app-store>",
                    "<launcher></launcher>",
                "</jpackage-state>");
        Assert.assertEquals("Foo", file.getLauncherName());

        Assert.assertEquals(0, file.getAddLaunchers().size());
    }

    @Test
    public void testMainLauncherName() throws IOException {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Foo");
        params.put("main-class", "main.Class");
        params.put("description", "Duck App Description");
        AppImageFile aif = create(params);

        Assert.assertEquals("Foo", aif.getLauncherName());
    }

    @Test
    public void testMainClass() throws IOException {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Foo");
        params.put("main-class", "main.Class");
        params.put("description", "Duck App Description");
        AppImageFile aif = create(params);

        Assert.assertEquals("main.Class", aif.getMainClass());
    }

    @Test
    public void testMacSign() throws IOException {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Foo");
        params.put("main-class", "main.Class");
        params.put("description", "Duck App Description");
        params.put("mac-sign", Boolean.TRUE);
        AppImageFile aif = create(params);

        Assert.assertTrue(aif.isSigned());
    }

    @Test
    public void testCopyAsSigned() throws IOException {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Foo");
        params.put("main-class", "main.Class");
        params.put("description", "Duck App Description");
        params.put("mac-sign", Boolean.FALSE);

        AppImageFile aif = create(params);
        Assert.assertFalse(aif.isSigned());

        aif = aif.copyAsSigned();
        Assert.assertTrue(aif.isSigned());
    }

    @Test
    public void testMacAppStore() throws IOException {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Foo");
        params.put("main-class", "main.Class");
        params.put("description", "Duck App Description");
        params.put("mac-app-store", Boolean.TRUE);
        AppImageFile aif = create(params);

        Assert.assertTrue(aif.isAppStore());
    }

    @Test
    public void testAddLaunchers() throws IOException {
        Map<String, Object> params = new LinkedHashMap<>();
        List<Map<String, Object>> launchersAsMap = new ArrayList<>();

        Map<String, Object> addLauncher2Params = new LinkedHashMap<>();
        addLauncher2Params.put("name", "Launcher2Name");
        launchersAsMap.add(addLauncher2Params);

        Map<String, Object> addLauncher3Params = new LinkedHashMap<>();
        addLauncher3Params.put("name", "Launcher3Name");
        launchersAsMap.add(addLauncher3Params);

        params.put("name", "Duke App");
        params.put("main-class", "main.Class");
        params.put("description", "Duke App Description");
        params.put("add-launcher", launchersAsMap);
        AppImageFile aif = create(params);

        List<AppImageFile.LauncherInfo> addLaunchers = aif.getAddLaunchers();
        Assert.assertEquals(2, addLaunchers.size());
        List<String> names = new ArrayList<>();
        names.add(addLaunchers.get(0).getName());
        names.add(addLaunchers.get(1).getName());

        Assert.assertTrue(names.contains("Launcher2Name"));
        Assert.assertTrue(names.contains("Launcher3Name"));
    }

    private AppImageFile create(Map<String, Object> params) throws IOException {
        AppImageFile.save(tempFolder.getRoot().toPath(), params);
        return AppImageFile.load(tempFolder.getRoot().toPath());
    }

    private void assertInvalid(ThrowingRunnable action) {
        Exception ex = Assert.assertThrows(RuntimeException.class, action);
        Assert.assertTrue(ex instanceof RuntimeException);
        Assert.assertTrue(ex.getMessage()
                .contains("generated by another jpackage version or malformed"));
        Assert.assertTrue(ex.getMessage()
                .endsWith(".jpackage.xml\""));
    }

    private AppImageFile createFromXml(String... xmlData) throws IOException {
        Path directory = tempFolder.getRoot().toPath();
        Path path = AppImageFile.getPathInAppImage(directory);
        path.toFile().mkdirs();
        Files.delete(path);

        List<String> data = new ArrayList<>();
        data.add("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>");
        data.addAll(List.of(xmlData));

        Files.write(path, data, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

        AppImageFile image = AppImageFile.load(directory);
        return image;
    }

    private final static String JPACKAGE_STATE_OPEN = String.format(
            "<jpackage-state platform=\"%s\" version=\"%s\">",
            AppImageFile.getPlatform(), AppImageFile.getVersion());

}
