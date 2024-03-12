/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing ClassFile on small Corpus.
 * @build helpers.* testdata.*
 * @run junit/othervm/timeout=480 -Djunit.jupiter.execution.parallel.enabled=true CorpusTest
 */
import helpers.ClassRecord;
import helpers.ClassRecord.CompatibilityFilter;
import helpers.Transforms;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.ByteArrayInputStream;
import java.util.*;

import static helpers.ClassRecord.assertEqualsDeep;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.*;
import static helpers.TestUtil.assertEmpty;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import java.lang.classfile.Attributes;
import java.lang.classfile.BufWriter;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.constantpool.ConstantPool;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.DirectCodeBuilder;
import jdk.internal.classfile.impl.UnboundAttribute;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.LocalVariableType;

/**
 * CorpusTest
 */
@Execution(ExecutionMode.CONCURRENT)
class CorpusTest {

    protected static final FileSystem JRT = FileSystems.getFileSystem(URI.create("jrt:/"));
    protected static final String testFilter = null; //"modules/java.base/java/util/function/Supplier.class";

    static void splitTableAttributes(String sourceClassFile, String targetClassFile) throws IOException, URISyntaxException {
        var root = Paths.get(URI.create(CorpusTest.class.getResource("CorpusTest.class").toString())).getParent();
        var cc = ClassFile.of();
        Files.write(root.resolve(targetClassFile), cc.transform(cc.parse(root.resolve(sourceClassFile)), ClassTransform.transformingMethodBodies((cob, coe) -> {
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
                        lv.writeTo(b);
                    }
                });
                case LocalVariableType lvt -> dcob.writeAttribute(new UnboundAttribute.AdHocAttribute<>(Attributes.LOCAL_VARIABLE_TYPE_TABLE) {
                    @Override
                    public void writeBody(BufWriter b) {
                        b.writeU2(1);
                        lvt.writeTo(b);
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

    static Path[] corpus() throws IOException, URISyntaxException {
        splitTableAttributes("testdata/Pattern2.class", "testdata/Pattern2-split.class");
        return Stream.of(
                Files.walk(JRT.getPath("modules/java.base/java")),
                Files.walk(JRT.getPath("modules"), 2).filter(p -> p.endsWith("module-info.class")),
                Files.walk(Paths.get(URI.create(CorpusTest.class.getResource("CorpusTest.class").toString())).getParent()))
                .flatMap(p -> p)
                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class") && !p.endsWith("DeadCodePattern.class"))
                .filter(p -> testFilter == null || p.toString().equals(testFilter))
                .toArray(Path[]::new);
    }


    @ParameterizedTest
    @MethodSource("corpus")
    void testNullAdaptations(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);

        Optional<ClassRecord> oldRecord;
        Optional<ClassRecord> newRecord;
        Map<Transforms.NoOpTransform, Exception> errors = new HashMap<>();
        Map<Integer, Integer> baseDups = findDups(bytes);

        for (Transforms.NoOpTransform m : Transforms.NoOpTransform.values()) {
            if (m == Transforms.NoOpTransform.ARRAYCOPY
                || m == Transforms.NoOpTransform.SHARED_3_NO_STACKMAP
                || m.name().startsWith("ASM"))
                continue;

            try {
                byte[] transformed = m.shared && m.classTransform != null
                                     ? ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
                                                .transform(ClassFile.of().parse(bytes), m.classTransform)
                                     : m.transform.apply(bytes);
                Map<Integer, Integer> newDups = findDups(transformed);
                oldRecord = m.classRecord(bytes);
                newRecord = m.classRecord(transformed);
                if (oldRecord.isPresent() && newRecord.isPresent())
                    assertEqualsDeep(newRecord.get(), oldRecord.get(),
                            "Class[%s] with %s".formatted(path, m.name()));
                switch (m) {
                    case SHARED_1, SHARED_2, SHARED_3, SHARED_3L, SHARED_3P:
                        if (newDups.size() > baseDups.size()) {
                            System.out.println(String.format("Incremental dups in file %s (%s): %s / %s", path, m, baseDups, newDups));
                        }
                        compareCp(bytes, transformed);
                        break;
                    case UNSHARED_1, UNSHARED_2, UNSHARED_3:
                        if (!newDups.isEmpty()) {
                            System.out.println(String.format("Dups in file %s (%s): %s", path, m, newDups));
                        }
                        break;
                }
            }
            catch (Exception ex) {
                System.err.printf("Error processing %s with %s: %s.%s%n", path, m.name(),
                                  ex.getClass(), ex.getMessage());
                ex.printStackTrace(System.err);
                errors.put(m, ex);
            }
        }

        if (!errors.isEmpty()) {
            String msg = String.format("Failures for %s:%n", path)
                         + errors.entrySet().stream()
                                 .map(e -> {
                                     Exception exception = e.getValue();
                                     StackTraceElement[] trace = exception.getStackTrace();
                                     return String.format("    Mode %s: %s (%s:%d)",
                                                   e.getKey(), exception.toString(),
                                                   trace.length > 0 ? trace[0].getClassName() : "unknown",
                                                   trace.length > 0 ? trace[0].getLineNumber() : 0);
                                 })
                                 .collect(joining("\n"));
            fail(String.format("Errors in testNullAdapt: %s", msg));
        }
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void testReadAndTransform(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        var cc = ClassFile.of();
        var classModel = cc.parse(bytes);
        assertEqualsDeep(ClassRecord.ofClassModel(classModel), ClassRecord.ofStreamingElements(classModel),
                         "ClassModel (actual) vs StreamingElements (expected)");

        byte[] newBytes = cc.build(
                classModel.thisClass().asSymbol(),
                classModel::forEachElement);
        var newModel = cc.parse(newBytes);
        assertEqualsDeep(ClassRecord.ofClassModel(newModel, CompatibilityFilter.By_ClassBuilder),
                ClassRecord.ofClassModel(classModel, CompatibilityFilter.By_ClassBuilder),
                "ClassModel[%s] transformed by ClassBuilder (actual) vs ClassModel before transformation (expected)".formatted(path));

        assertEmpty(cc.verify(newModel));

        //testing maxStack and maxLocals are calculated identically by StackMapGenerator and StackCounter
        byte[] noStackMaps = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
                                      .transform(newModel,
                                                         ClassTransform.transformingMethodBodies(CodeTransform.ACCEPT_ALL));
        var noStackModel = cc.parse(noStackMaps);
        var itStack = newModel.methods().iterator();
        var itNoStack = noStackModel.methods().iterator();
        while (itStack.hasNext()) {
            assertTrue(itNoStack.hasNext());
            var m1 = itStack.next();
            var m2 = itNoStack.next();
            var text1 = m1.methodName().stringValue() + m1.methodType().stringValue() + ": "
                      + m1.code().map(c -> c.maxLocals() + " / " + c.maxStack()).orElse("-");
            var text2 = m2.methodName().stringValue() + m2.methodType().stringValue() + ": "
                      + m2.code().map(c -> c.maxLocals() + " / " + c.maxStack()).orElse("-");
            assertEquals(text1, text2);
        }
        assertFalse(itNoStack.hasNext());
    }

//    @Test(enabled = false)
//    public void checkDups() {
        // Checks input files for dups -- and there are.  Not clear this test has value.
        // Tests above
//        Map<Integer, Integer> dups = findDups(bytes);
//        if (!dups.isEmpty()) {
//            String dupsString = dups.entrySet().stream()
//                                    .map(e -> String.format("%d -> %d", e.getKey(), e.getValue()))
//                                    .collect(joining(", "));
//            System.out.println(String.format("Duplicate entries in input file %s: %s", path, dupsString));
//        }
//    }

    private void compareCp(byte[] orig, byte[] transformed) {
        var cc = ClassFile.of();
        var cp1 = cc.parse(orig).constantPool();
        var cp2 = cc.parse(transformed).constantPool();

        for (int i = 1; i < cp1.size(); i += cp1.entryByIndex(i).width()) {
            assertEquals(cpiToString(cp1.entryByIndex(i)), cpiToString(cp2.entryByIndex(i)));
        }

        if (cp1.size() != cp2.size()) {
            StringBuilder failMsg = new StringBuilder("Extra entries in constant pool (" + (cp2.size() - cp1.size()) + "): ");
            for (int i = cp1.size(); i < cp2.size(); i += cp2.entryByIndex(i).width())
                failMsg.append("\n").append(cp2.entryByIndex(i));
            fail(failMsg.toString());
        }
    }

    private static String cpiToString(PoolEntry e) {
        String s = e.toString();
        if (e instanceof Utf8Entry ue)
            s = "CONSTANT_Utf8_info[value: \"%s\"]".formatted(ue.stringValue());
        return s;
    }

    private static Map<Integer, Integer> findDups(byte[] bytes) {
        Map<Integer, Integer> dups = new HashMap<>();
        var cf = ClassFile.of().parse(bytes);
        var pool = cf.constantPool();
        Set<String> entryStrings = new HashSet<>();
        for (int i = 1; i < pool.size(); i += pool.entryByIndex(i).width()) {
            String s = cpiToString(pool.entryByIndex(i));
            if (entryStrings.contains(s)) {
                for (int j=1; j<i; j += pool.entryByIndex(j).width()) {
                    var e2 = pool.entryByIndex(j);
                    if (s.equals(cpiToString(e2)))
                        dups.put(i, j);
                }
            }
            entryStrings.add(s);
        }
        return dups;
    }
}
