/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package helpers;

import jdk.classfile.Classfile;
import jdk.classfile.impl.UnboundAttribute;
import jdk.classfile.instruction.LineNumber;
import jdk.classfile.instruction.LocalVariable;
import jdk.classfile.instruction.LocalVariableType;
import org.testng.annotations.DataProvider;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import jdk.classfile.BufWriter;
import jdk.classfile.ClassTransform;
import jdk.classfile.Attributes;
import jdk.classfile.impl.DirectCodeBuilder;

public class CorpusTestHelper  {

    protected static final FileSystem JRT = FileSystems.getFileSystem(URI.create("jrt:/"));
    protected static final String testFilter = null; //"modules/java.base/java/util/function/Supplier.class";

    static void splitTableAttributes(String sourceClassFile, String targetClassFile) throws IOException, URISyntaxException {
        var root = Paths.get(URI.create(CorpusTestHelper.class.getResource("CorpusTestHelper.class").toString())).getParent().getParent();
        Files.write(root.resolve(targetClassFile), Classfile.parse(root.resolve(sourceClassFile)).transform(ClassTransform.transformingMethodBodies((cob, coe) -> {
            var dcob = (DirectCodeBuilder)cob;
            var curPc = dcob.curPc();
            switch (coe) {
                case LineNumber ln -> dcob.writeAttribute(new UnboundAttribute.AdHocAttribute<>(Attributes.LINE_NUMBER_TABLE) {
                    @Override
                    public void writeBody(BufWriter b) {
                        b.writeU2(1);
                        b.writeU2(curPc);
                        b.writeU2(ln.line());
                    }
                });
                case LocalVariable lv -> dcob.writeAttribute(new UnboundAttribute.AdHocAttribute<>(Attributes.LOCAL_VARIABLE_TABLE) {
                    @Override
                    public void writeBody(BufWriter b) {
                        b.writeU2(1);
                        lv.writeTo(b, dcob);
                    }
                });
                case LocalVariableType lvt -> dcob.writeAttribute(new UnboundAttribute.AdHocAttribute<>(Attributes.LOCAL_VARIABLE_TYPE_TABLE) {
                    @Override
                    public void writeBody(BufWriter b) {
                        b.writeU2(1);
                        lvt.writeTo(b, dcob);
                    }
                });
                default -> cob.with(coe);
            }
        })));
//        ClassRecord.assertEqualsDeep(
//                ClassRecord.ofClassModel(ClassModel.of(Files.readAllBytes(root.resolve(targetClassFile)))),
//                ClassRecord.ofClassModel(ClassModel.of(Files.readAllBytes(root.resolve(sourceClassFile)))));
//        ClassPrinter.toYaml(ClassModel.of(Files.readAllBytes(root.resolve(targetClassFile))), ClassPrinter.Verbosity.TRACE_ALL, System.out::print);
    }

    @DataProvider(name = "corpus")
    public static Object[] provide() throws IOException, URISyntaxException {
        splitTableAttributes("testdata/Pattern2.class", "testdata/Pattern2-split.class");
        return Stream.of(
                Files.walk(JRT.getPath("modules/java.base/java")),
                Files.walk(JRT.getPath("modules"), 2).filter(p -> p.endsWith("module-info.class")),
                Files.walk(Paths.get(URI.create(CorpusTestHelper.class.getResource("CorpusTestHelper.class").toString())).getParent().getParent()))
                .flatMap(p -> p)
                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class") && !p.endsWith("DeadCodePattern.class"))
                .filter(p -> testFilter == null || p.toString().equals(testFilter))
                .toArray();
    }


    protected final Path path;
    protected final byte[] bytes;

    public CorpusTestHelper(Path path) throws IOException {
        this.path = path;
        this.bytes = Files.readAllBytes(path);
    }
}
