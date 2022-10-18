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

/*
 * @test
 * @summary Testing Classfile on small Corpus.
 * @build helpers.* testdata.*
 * @run junit/othervm -Djunit.jupiter.execution.parallel.enabled=true CorpusTest
 */
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import helpers.ClassRecord;
import helpers.ClassRecord.CompatibilityFilter;
import helpers.Transforms;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.ByteArrayInputStream;
import java.util.*;

import static com.sun.tools.classfile.ConstantPool.CONSTANT_Double;
import static com.sun.tools.classfile.ConstantPool.CONSTANT_Long;
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
import jdk.classfile.Attributes;
import jdk.classfile.BufWriter;
import jdk.classfile.Classfile;
import jdk.classfile.ClassTransform;
import jdk.classfile.impl.DirectCodeBuilder;
import jdk.classfile.impl.UnboundAttribute;
import jdk.classfile.instruction.LineNumber;
import jdk.classfile.instruction.LocalVariable;
import jdk.classfile.instruction.LocalVariableType;

/**
 * CorpusTest
 */
@Execution(ExecutionMode.CONCURRENT)
class CorpusTest {

    protected static final FileSystem JRT = FileSystems.getFileSystem(URI.create("jrt:/"));
    protected static final String testFilter = null; //"modules/java.base/java/util/function/Supplier.class";

    static void splitTableAttributes(String sourceClassFile, String targetClassFile) throws IOException, URISyntaxException {
        var root = Paths.get(URI.create(CorpusTest.class.getResource("CorpusTest.class").toString())).getParent();
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
                                     ? Classfile.parse(bytes, Classfile.Option.generateStackmap(false))
                                                .transform(m.classTransform)
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
                            ClassFile cf = ClassFile.read(new ByteArrayInputStream(transformed));
                            com.sun.tools.classfile.ConstantPool pool = cf.constant_pool;
                            System.out.println(String.format("Incremental dups in file %s (%s): %s / %s", path, m, baseDups, newDups));
                            for (Map.Entry<Integer, Integer> entry : newDups.entrySet()) {
                                System.out.println(String.format("  %d: %s", entry.getKey(), pool.get(entry.getKey())));
                                System.out.println(String.format("  %d: %s", entry.getValue(), pool.get(entry.getValue())));
                            }
                        }
                        compareCp(bytes, transformed);
                        break;
                    case UNSHARED_1, UNSHARED_2, UNSHARED_3:
                        if (!newDups.isEmpty()) {
                            ClassFile cf = ClassFile.read(new ByteArrayInputStream(transformed));
                            com.sun.tools.classfile.ConstantPool pool = cf.constant_pool;
                            System.out.println(String.format("Dups in file %s (%s): %s", path, m, newDups));

                            for (Map.Entry<Integer, Integer> entry : newDups.entrySet()) {
                                System.out.println(String.format("  %d: %s", entry.getKey(), pool.get(entry.getKey())));
                                System.out.println(String.format("  %d: %s", entry.getValue(), pool.get(entry.getValue())));
                            }
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
    void testReadAndTransform(Path path) throws IOException, ConstantPoolException {
        byte[] bytes = Files.readAllBytes(path);

        var classModel = Classfile.parse(bytes);
        var classFile = ClassFile.read(new ByteArrayInputStream(bytes));
        assertEqualsDeep(ClassRecord.ofClassModel(classModel), ClassRecord.ofClassFile(classFile),
                         "ClassModel (actual) vs ClassFile (expected)");

        assertEqualsDeep(ClassRecord.ofStreamingElements(classModel), ClassRecord.ofClassFile(classFile),
                "StreamingElements (actual) vs ClassFile (expected)");

        byte[] newBytes = Classfile.build(
                classModel.thisClass().asSymbol(),
                classModel::forEachElement);
        var newModel = Classfile.parse(newBytes);
        assertEqualsDeep(ClassRecord.ofClassModel(newModel, CompatibilityFilter.By_ClassBuilder),
                ClassRecord.ofClassModel(classModel, CompatibilityFilter.By_ClassBuilder),
                "ClassModel[%s] transformed by ClassBuilder (actual) vs ClassModel before transformation (expected)".formatted(path));

        assertEmpty(newModel.verify(null));
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
        try {
            ClassFile c1 = ClassFile.read(new ByteArrayInputStream(orig));
            ClassFile c2 = ClassFile.read(new ByteArrayInputStream(transformed));

            List<ConstantPool.CPInfo> entryList1 = cpiEntries(c1.constant_pool);
            List<ConstantPool.CPInfo> entryList2 = cpiEntries(c2.constant_pool);

            for (int i=0; i<entryList1.size(); i++) {
                assertEquals(cpiToString(entryList1.get(i)), cpiToString(entryList2.get(i)));
            }

            if (entryList1.size() != entryList2.size()) {
                StringBuilder failMsg = new StringBuilder("Extra entries in constant pool (" + (entryList2.size() - entryList1.size()) + "): ");
                for (int i=entryList1.size(); i < entryList2.size(); i++)
                    failMsg.append("\n").append(entryList2.get(i));
                fail(failMsg.toString());
            }
        }
        catch (IOException | ConstantPoolException e) {
            throw new RuntimeException(e);
        }
    }

    private static String cpiToString(ConstantPool.CPInfo e) {
        if (e == null)
            return "";
        String s = e.toString();
        if (e instanceof ConstantPool.CONSTANT_Utf8_info ue)
            s = "CONSTANT_Utf8_info[value: \"%s\"]".formatted(ue.value);
        return s;
    }

    private static List<ConstantPool.CPInfo> cpiEntries(ConstantPool pool) {
        List<ConstantPool.CPInfo> entryList = new ArrayList<>();
        entryList.add(null);
        for (com.sun.tools.classfile.ConstantPool.CPInfo e : pool.entries()) {
            entryList.add(e);
            if (e.getTag() == CONSTANT_Double || e.getTag() == CONSTANT_Long)
                entryList.add(null);
        }
        return entryList;
    }

    private static Map<Integer, Integer> findDups(byte[] bytes) {
        try {
            Map<Integer, Integer> dups = new HashMap<>();
            ClassFile cf = ClassFile.read(new ByteArrayInputStream(bytes));
            com.sun.tools.classfile.ConstantPool pool = cf.constant_pool;

            List<ConstantPool.CPInfo> entryList = cpiEntries(pool);
            assertEquals(entryList.size(), pool.size());

            Set<String> entryStrings = new HashSet<>();
            for (int i=1; i<pool.size(); i++) {
                ConstantPool.CPInfo e = entryList.get(i);
                if (e == null)
                    continue;
                String s = cpiToString(e);
                if (entryStrings.contains(s)) {
                    for (int j=1; j<i; j++) {
                        ConstantPool.CPInfo e2 = entryList.get(j);
                        if (e2 == null)
                            continue;
                        if (s.equals(cpiToString(e2)))
                            dups.put(i, j);
                    }
                }
                entryStrings.add(s);
            }

            return dups;
        }
        catch (ConstantPoolException | IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
