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

/*
 * @test
 * @summary Testing ClassFile building module.
 * @run junit ModuleBuilderTest
 */
import java.lang.classfile.*;

import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.attribute.ModuleExportInfo;
import java.lang.classfile.attribute.ModuleMainClassAttribute;
import java.lang.classfile.attribute.ModuleOpenInfo;
import java.lang.classfile.attribute.ModulePackagesAttribute;
import java.lang.classfile.attribute.ModuleProvideInfo;
import java.lang.classfile.attribute.ModuleRequireInfo;
import java.lang.classfile.Attributes;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import org.junit.jupiter.api.Test;

import java.lang.constant.ClassDesc;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModuleBuilderTest {
    private final ModuleDesc modName = ModuleDesc.of("some.module.structure");
    private final String modVsn = "ab75";
    private final ModuleDesc require1 = ModuleDesc.of("1require.some.mod"); String vsn1 = "1the.best.version";
    private final ModuleDesc require2 = ModuleDesc.of("2require.some.mod"); String vsn2 = "2the.best.version";
    private final ModuleDesc[] et1 = new ModuleDesc[] {ModuleDesc.of("1t1"), ModuleDesc.of("1t2")};
    private final ModuleDesc[] et2 = new ModuleDesc[] {ModuleDesc.of("2t1")};
    private final ModuleDesc[] et3 = new ModuleDesc[] {ModuleDesc.of("3t1"), ModuleDesc.of("3t2"), ModuleDesc.of("3t3")};
    private final ModuleDesc[] ot3 = new ModuleDesc[] {ModuleDesc.of("t1"), ModuleDesc.of("t2")};

    private final ClassModel moduleModel;
    private final ModuleAttribute attr;

    public ModuleBuilderTest() {
        var cc = ClassFile.of();
        byte[] modInfo = cc.buildModule(
                ModuleAttribute.of(modName, mb -> mb
                        .moduleVersion(modVsn)

                        .requires(require1, 77, vsn1)
                        .requires(require2, 99, vsn2)

                        .exports(PackageDesc.of("0"), 0, et1)
                        .exports(PackageDesc.of("1"), 1, et2)
                        .exports(PackageDesc.of("2"), 2, et3)
                        .exports(PackageDesc.of("3"), 3)
                        .exports(PackageDesc.of("4"), 4)

                        .opens(PackageDesc.of("o0"), 0)
                        .opens(PackageDesc.of("o1"), 1)
                        .opens(PackageDesc.of("o2"), 2, ot3)

                        .uses(ClassDesc.of("some.Service"))
                        .uses(ClassDesc.of("another.Service"))

                        .provides(ClassDesc.of("some.nice.Feature"), ClassDesc.of("impl"), ClassDesc.of("another.impl"))),
                clb -> clb.with(ModuleMainClassAttribute.of(ClassDesc.of("main.Class")))
                          .with(ModulePackagesAttribute.ofNames(PackageDesc.of("foo.bar.baz"), PackageDesc.of("quux")))
                          .with(ModuleMainClassAttribute.of(ClassDesc.of("overwritten.main.Class"))));
        moduleModel = cc.parse(modInfo);
        attr = ((ModuleAttribute) moduleModel.attributes().stream()
                .filter(a -> a.attributeMapper() == Attributes.MODULE)
                .findFirst()
                .orElseThrow());
    }

    @Test
    void testCreateModuleInfo() {
        // Build the module-info.class bytes
        var cc = ClassFile.of();
        byte[] modBytes = cc.buildModule(ModuleAttribute.of(modName, mb -> mb.moduleVersion(modVsn)));

        // Verify
        var cm = cc.parse(modBytes);

        var attr =cm.findAttribute(Attributes.MODULE).get();
        assertEquals(attr.moduleName().name().stringValue(), modName.name());
        assertEquals(attr.moduleFlagsMask(), 0);
        assertEquals(attr.moduleVersion().get().stringValue(), modVsn);
    }

    @Test
    void testAllAttributes() {
        assertEquals(moduleModel.attributes().size(), 3);
    }

    @Test
    void testVerifyRequires() {
        assertEquals(attr.requires().size(), 2);
        ModuleRequireInfo r = attr.requires().get(0);
        assertEquals(r.requires().name().stringValue(), require1.name());
        assertEquals(r.requiresVersion().get().stringValue(), vsn1);
        assertEquals(r.requiresFlagsMask(), 77);

        r = attr.requires().get(1);
        assertEquals(r.requires().name().stringValue(), require2.name());
        assertEquals(r.requiresVersion().get().stringValue(), vsn2);
        assertEquals(r.requiresFlagsMask(), 99);
    }

    @Test
    void testVerifyExports() {
        List<ModuleExportInfo> exports = attr.exports();
        assertEquals(exports.size(),5);
        for (int i = 0; i < 5; i++) {
            assertEquals(exports.get(i).exportsFlagsMask(), i);
            assertEquals(exports.get(i).exportedPackage().name().stringValue(), String.valueOf(i));
        }
        assertEquals(exports.get(0).exportsTo().size(), 2);
        for (int i = 0; i < 2; i++)
            assertEquals(exports.get(0).exportsTo().get(i).name().stringValue(), et1[i].name());

        assertEquals(exports.get(1).exportsTo().size(), 1);
        assertEquals(exports.get(1).exportsTo().get(0).name().stringValue(), et2[0].name());

        assertEquals(exports.get(2).exportsTo().size(), 3);
        for (int i = 0; i < 3; i++)
            assertEquals(exports.get(2).exportsTo().get(i).name().stringValue(), et3[i].name());

        assertEquals(exports.get(3).exportsTo().size(), 0);
        assertEquals(exports.get(4).exportsTo().size(), 0);
    }

    @Test
    void testVerifyOpens() {
        List<ModuleOpenInfo> opens = attr.opens();
        assertEquals(opens.size(), 3);
        assertEquals(opens.get(0).opensTo().size(), 0);
        assertEquals(opens.get(1).opensTo().size(), 0);
        assertEquals(opens.get(2).opensTo().size(), 2);
        assertEquals(opens.get(2).opensFlagsMask(), 2);
        assertEquals(opens.get(2).opensTo().get(1).name().stringValue(), ot3[1].name());
    }

    @Test
    void testVerifyUses() {
        var uses = attr.uses();
        assertEquals(uses.size(), 2);
        assertEquals(uses.get(1).asInternalName(), "another/Service");
    }

    @Test
    void testVerifyProvides() {
        var provides = attr.provides();
        assertEquals(provides.size(), 1);
        ModuleProvideInfo p = provides.get(0);
        assertEquals(p.provides().asInternalName(), "some/nice/Feature");
        assertEquals(p.providesWith().size(), 2);
        assertEquals(p.providesWith().get(1).asInternalName(), "another/impl");
    }

    @Test
    void verifyPackages() {
        ModulePackagesAttribute a = moduleModel.findAttribute(Attributes.MODULE_PACKAGES).orElseThrow();
        assertEquals(a.packages().stream().map(pe -> pe.asSymbol().name()).toList(), List.of("foo.bar.baz", "quux"));
    }

    @Test
    void verifyMainclass() {
        ModuleMainClassAttribute a = moduleModel.findAttribute(Attributes.MODULE_MAIN_CLASS).orElseThrow();
        assertEquals(a.mainClass().asInternalName(), "overwritten/main/Class");
    }

    @Test
    void verifyIsModuleInfo() throws Exception {
        assertTrue(moduleModel.isModuleInfo());

        ClassModel m = ClassFile.of().parse(Paths.get(URI.create(ModuleBuilderTest.class.getResource("ModuleBuilderTest.class").toString())));
        assertFalse(m.isModuleInfo());
    }
}
