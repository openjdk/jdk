/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing ClassFile options on small Corpus.
 * @run junit/othervm -Djunit.jupiter.execution.parallel.enabled=true OptionsTest
 */
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import static org.junit.jupiter.api.Assertions.*;


import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.classfile.*;

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
    void testAttributesProcessingOptionOnTransform(Path path) throws Exception {
        testNoUnstable(path, ClassFile.of().parse(
                ClassFile.of(ClassFile.AttributesProcessingOption.DROP_UNSTABLE_ATRIBUTES).transformClass(
                            ClassFile.of().parse(path),
                            ClassTransform.transformingMethodBodies(CodeTransform.ACCEPT_ALL))));
    }

    static class StrangeAttribute extends CustomAttribute<StrangeAttribute> {
        public StrangeAttribute() {
            super(STRANGE_ATTRIBUTE_MAPPER);
        }
    }

    static final AttributeMapper<StrangeAttribute> STRANGE_ATTRIBUTE_MAPPER = new AttributeMapper<>() {

        @Override
        public String name() {
            return "StrangeAttribute";
        }

        @Override
        public StrangeAttribute readAttribute(AttributedElement enclosing, ClassReader cf, int pos) {
            return new StrangeAttribute();
        }

        @Override
        public void writeAttribute(BufWriter buf, StrangeAttribute attr) {
            buf.writeIndex(buf.constantPool().utf8Entry(name()));
            buf.writeInt(0);
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeMapper.AttributeStability.STATELESS;
        }
    };

    @Test
    void testUnknownAttribute() throws Exception {
        var classBytes = ClassFile.of(ClassFile.AttributeMapperOption.of(e -> {
            return e.equalsString(STRANGE_ATTRIBUTE_MAPPER.name()) ? STRANGE_ATTRIBUTE_MAPPER : null;
        })).build(ClassDesc.of("StrangeClass"), clb -> clb.with(new StrangeAttribute()));

        //test default
        assertFalse(ClassFile.of().parse(classBytes).attributes().isEmpty());

        //test drop unknown at transform
        assertTrue(ClassFile.of().parse(
                ClassFile.of(ClassFile.AttributesProcessingOption.DROP_UNKNOWN_ATTRIBUTES).transformClass(
                        ClassFile.of().parse(classBytes),
                        ClassTransform.ACCEPT_ALL)).attributes().isEmpty());
    }

    void testNoUnstable(Path path, ClassFileElement e) {
        if (e instanceof AttributedElement ae) ae.attributes().forEach(a ->
                assertTrue(AttributeMapper.AttributeStability.UNSTABLE.ordinal() >= a.attributeMapper().stability().ordinal(),
                           () -> "class " + path + " contains unexpected " + a));
        if (e instanceof CompoundElement ce) ce.forEach(ee -> testNoUnstable(path, (ClassFileElement)ee));
    }
}
