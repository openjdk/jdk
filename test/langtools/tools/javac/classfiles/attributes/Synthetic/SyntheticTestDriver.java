/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.lang.reflect.AccessFlag;

import java.lang.classfile.*;
import java.lang.classfile.attribute.SyntheticAttribute;

/**
 * The tests work as follows. Firstly, it looks through the test cases
 * and extracts the appropriate compiled classes. Each test case contains
 * a set of expected classes, methods and fields. Those class members must not have
 * the Synthetic attribute, while other found classes, methods and fields must have
 * the Synthetic attribute if they are not in the set of expected class members.
 *
 * Each test executes SyntheticTestDriver specifying the name of test cases and
 * the number of expected synthetic classes. Each test class is annotated by
 * annotations which contains non-synthetic class members.
 *
 * See the appropriate class for more information about a test case.
 */
public class SyntheticTestDriver extends TestResult {
    private final String testCaseName;
    private final Map<String, ClassModel> classes;
    private final Map<String, ExpectedClass> expectedClasses;

    public static void main(String[] args)
            throws TestFailedException, IOException, ClassNotFoundException {
        if (args.length != 1 && args.length != 2) {
            throw new IllegalArgumentException("Usage: SyntheticTestDriver <class-name> [<number-of-synthetic-classes>]");
        }
        int numberOfSyntheticClasses = args.length == 1 ? 0 : Integer.parseInt(args[1]);
        new SyntheticTestDriver(args[0]).test(numberOfSyntheticClasses);
    }

    public SyntheticTestDriver(String testCaseName) throws IOException, ClassNotFoundException {
        Class<?> clazz = Class.forName(testCaseName);
        this.testCaseName = testCaseName;
        this.expectedClasses = Stream.of(clazz.getAnnotationsByType(ExpectedClass.class))
                .collect(Collectors.toMap(ExpectedClass::className, Function.identity()));
        this.classes = new HashMap<>();
        Path classDir = getClassDir().toPath();
        Pattern filePattern = Pattern.compile(Pattern.quote(testCaseName.replace('.', File.separatorChar)) + ".*\\.class");
        List<Path> paths = Files.walk(classDir)
                .map(p -> classDir.relativize(p.toAbsolutePath()))
                .filter(p -> filePattern.matcher(p.toString()).matches())
                .toList();
        for (Path path : paths) {
            String className = path.toString().replace(".class", "").replace(File.separatorChar, '.');
            classes.put(className, readClassFile(classDir.resolve(path).toFile()));
        }
        if (classes.isEmpty()) {
            throw new RuntimeException("Classes have not been found.");
        }
        boolean success = classes.entrySet().stream()
                .allMatch(e -> e.getKey().startsWith(testCaseName));
        if (!success) {
            classes.forEach((className, $) -> printf("Found class: %s\n", className));
            throw new RuntimeException("Found classes are not from the test case : " + testCaseName);
        }
    }

    private String getMethodName(MethodModel method) {
        StringBuilder methodName = new StringBuilder(method.methodName().stringValue() + "(");
        List<ClassDesc> paras = method.methodTypeSymbol().parameterList();
        for (int i = 0; i < method.methodTypeSymbol().parameterCount(); ++i) {
            if (i != 0) {
                methodName.append(", ");
            }
            ClassDesc para = paras.get(i);
            String prefix = para.componentType() == null? para.packageName(): para.componentType().packageName();
            methodName.append(prefix).append(Objects.equals(prefix, "") ? "":".").append(para.displayName());
        }
        methodName.append(")");
        return methodName.toString();
    }

    public void test(int expectedNumberOfSyntheticClasses) throws TestFailedException {
        try {
            addTestCase(testCaseName);
            Set<String> foundClasses = new HashSet<>();

            int numberOfSyntheticClasses = 0;
            for (Map.Entry<String, ClassModel> entry : classes.entrySet()) {
                String className = entry.getKey();
                ClassModel classFile = entry.getValue();
                foundClasses.add(className);
                if (testAttribute(
                        classFile,
                        () -> classFile.findAttribute(Attributes.synthetic()).orElse(null),
                        classFile.flags()::flags,
                        expectedClasses.keySet(),
                        className,
                        "Testing class " + className)) {
                    ++numberOfSyntheticClasses;
                }
                ExpectedClass expectedClass = expectedClasses.get(className);
                Set<String> expectedMethods = expectedClass != null
                        ? toSet(expectedClass.expectedMethods())
                        : new HashSet<>();
                int numberOfSyntheticMethods = 0;
                Set<String> foundMethods = new HashSet<>();
                for (MethodModel method : classFile.methods()) {
                    String methodName = getMethodName(method);
                    foundMethods.add(methodName);
                    if (testAttribute(
                            classFile,
                            () -> method.findAttribute(Attributes.synthetic()).orElse(null),
                            method.flags()::flags,
                            expectedMethods,
                            methodName,
                            "Testing method " + methodName + " in class "
                                    + className)) {
                        ++numberOfSyntheticMethods;
                    }
                }
                checkContains(foundMethods, expectedMethods,
                        "Checking that all methods of class " + className
                                + " without Synthetic attribute have been found");
                checkEquals(numberOfSyntheticMethods,
                        expectedClass == null ? 0 : expectedClass.expectedNumberOfSyntheticMethods(),
                        "Checking number of synthetic methods in class: " + className);

                Set<String> expectedFields = expectedClass != null
                        ? toSet(expectedClass.expectedFields())
                        : new HashSet<>();
                int numberOfSyntheticFields = 0;
                Set<String> foundFields = new HashSet<>();
                for (FieldModel field : classFile.fields()) {
                    String fieldName = field.fieldName().stringValue();
                    foundFields.add(fieldName);
                    if (testAttribute(
                            classFile,
                            () -> field.findAttribute(Attributes.synthetic()).orElse(null),
                            field.flags()::flags,
                            expectedFields,
                            fieldName,
                            "Testing field " + fieldName + " in class "
                                    + className)) {
                        ++numberOfSyntheticFields;
                    }
                }
                checkContains(foundFields, expectedFields,
                        "Checking that all fields of class " + className
                                + " without Synthetic attribute have been found");
                checkEquals(numberOfSyntheticFields,
                        expectedClass == null ? 0 : expectedClass.expectedNumberOfSyntheticFields(),
                        "Checking number of synthetic fields in class: " + className);
            }
            checkContains(foundClasses, expectedClasses.keySet(),
                    "Checking that all classes have been found");
            checkEquals(numberOfSyntheticClasses, expectedNumberOfSyntheticClasses,
                    "Checking number of synthetic classes");
        } catch (Exception e) {
            addFailure(e);
        } finally {
            checkStatus();
        }
    }

    private boolean testAttribute(ClassModel classFile,
                               Supplier<SyntheticAttribute> getSyntheticAttribute,
                               Supplier<Set<AccessFlag>> getAccessFlags,
                               Set<String> expectedMembers, String memberName,
                               String info) {
        echo(info);
        String className = classFile.thisClass().name().stringValue();
        SyntheticAttribute attr = getSyntheticAttribute.get();
        Set<AccessFlag> flags = getAccessFlags.get();
        if (expectedMembers.contains(memberName)) {
            checkNull(attr, "Member must not have synthetic attribute : "
                    + memberName);
            checkFalse(flags.contains(AccessFlag.SYNTHETIC),
                    "Member must not have synthetic flag : " + memberName
                            + " in class : " + className);
            return false;
        } else {
            return checkNull(attr, "Synthetic attribute should not be generated")
                    && checkTrue(flags.contains(AccessFlag.SYNTHETIC), "Member must have synthetic flag : "
                                + memberName + " in class : " + className);
        }
    }

    private Set<String> toSet(String[] strings) {
        HashSet<String> set = new HashSet<>();
        Collections.addAll(set, strings);
        return set;
    }
}
