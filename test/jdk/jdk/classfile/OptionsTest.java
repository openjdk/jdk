/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @summary Testing Classfile options on small Corpus.
 * @run junit/othervm -Djunit.jupiter.execution.parallel.enabled=true OptionsTest
 */
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import static org.junit.jupiter.api.Assertions.*;


import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import jdk.internal.classfile.AttributeMapper;
import jdk.internal.classfile.AttributedElement;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.ClassfileElement;
import jdk.internal.classfile.CompoundElement;

/**
 * OptionsTest
 */
@Execution(ExecutionMode.CONCURRENT)
class OptionsTest {

    protected static final FileSystem JRT = FileSystems.getFileSystem(URI.create("jrt:/"));

    static Path[] corpus() throws IOException, URISyntaxException {
        return Files.walk(JRT.getPath("modules/java.base/java/util"))
                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class"))
                .toArray(Path[]::new);
    }


    @ParameterizedTest
    @MethodSource("corpus")
    void testAttributesProcessingOption(Path path) throws Exception {
        testNoHazmat(path, Classfile.of(Classfile.AttributesProcessingOption.DROP_HAZMAT_ATRIBUTES).parse(path));
    }

    void testNoHazmat(Path path, ClassfileElement e) {
        if (e instanceof AttributedElement ae) ae.attributes().forEach(a ->
                assertTrue(AttributeMapper.AttributeStability.HAZMAT.ordinal() >= a.attributeMapper().attributeStability().ordinal(),
                           () -> "class " + path + " contains unexpected " + a));
        if (e instanceof CompoundElement ce) ce.forEachElement(ee -> testNoHazmat(path, (ClassfileElement)ee));
    }
}
