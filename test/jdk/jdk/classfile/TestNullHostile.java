/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.ref
 *          java.base/jdk.internal.classfile.impl
 * @build testdata.*
 * @run testng/othervm TestNullHostile
 *
 */

import com.sun.source.util.JavacTask;
import jdk.internal.ref.CleanerFactory;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.NoInjection;
import org.testng.annotations.Test;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.Constable;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.ref.Cleaner;
import java.lang.reflect.*;
import java.net.URI;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * This test makes sure that public API classes under {@link java.lang.classfile} throws NPEs whenever
 * nulls are provided. The test looks at all the public methods in all the listed classes, and injects
 * values automatically. If an API takes a reference, the test will try to inject nulls. For APIs taking
 * either reference arrays, or collections, the framework will also generate additional <em>replacements</em>
 * (e.g. other than just replacing the array, or collection with null), such as an array or collection
 * with null elements. The test can be customized by adding/removing classes to the {@link #CLASSES} array,
 * by adding/removing default mappings for standard carrier types (see {@link #DEFAULT_VALUES} or by
 * adding/removing custom replacements (see {@link #REPLACEMENT_VALUES}).
 *
 * The public API is taken from the data built into the javac
 */
public class TestNullHostile {
    static final List<Class<?>> CLASSES = new ArrayList<>();
    static final Set<String> OBJECT_METHODS = Stream.of(Object.class.getMethods())
            .map(Method::getName)
            .collect(Collectors.toSet());
    static final Map<Class<?>, Object> DEFAULT_VALUES = new HashMap<>();
    static final Map<Class<?>, Object[]> REPLACEMENT_VALUES = new HashMap<>();
    static final String targPathname = System.getProperty("test.classes") + File.separator + "testdata" + File.separator + "TestClass.class";


    static {
        addDefaultMapping(char.class, (char) 0);
        addDefaultMapping(byte.class, (byte) 0);
        addDefaultMapping(short.class, (short) 0);
        addDefaultMapping(int.class, 0);
        addDefaultMapping(float.class, 0f);
        addDefaultMapping(long.class, 0L);
        addDefaultMapping(double.class, 0d);
        addDefaultMapping(boolean.class, true);

        addDefaultMapping(ClassFileFormatVersion.class, ClassFileFormatVersion.RELEASE_24);
        addDefaultMapping(Enum.class, ClassFileFormatVersion.RELEASE_24);
        addDefaultMapping(ByteOrder.class, ByteOrder.nativeOrder());
        addDefaultMapping(Thread.class, Thread.currentThread());
        addDefaultMapping(Cleaner.class, CleanerFactory.cleaner());
        addDefaultMapping(Buffer.class, ByteBuffer.wrap(new byte[10]));
        addDefaultMapping(ByteBuffer.class, ByteBuffer.wrap(new byte[10]));
        addDefaultMapping(Path.class, Path.of("nonExistent"));
        addDefaultMapping(FileChannel.MapMode.class, FileChannel.MapMode.PRIVATE);
        addDefaultMapping(UnaryOperator.class, UnaryOperator.identity());
        addDefaultMapping(String.class, "Hello!");
        addDefaultMapping(Constable.class, "Hello!");
        addDefaultMapping(Class.class, String.class);
        addDefaultMapping(Runnable.class, () -> {});
        addDefaultMapping(Object.class, new Object());
        addDefaultMapping(VarHandle.class, JAVA_INT.varHandle());
        addDefaultMapping(MethodHandle.class, MethodHandles.identity(int.class));
        addDefaultMapping(List.class, List.of());
        addDefaultMapping(Charset.class, Charset.defaultCharset());
        addDefaultMapping(Consumer.class, x -> {});
        addDefaultMapping(MethodType.class, MethodType.methodType(void.class));
        addDefaultMapping(Supplier.class, () -> null);
        addDefaultMapping(ClassLoader.class, TestNullHostile.class.getClassLoader());
        addDefaultMapping(Thread.UncaughtExceptionHandler.class, (thread, ex) -> {});
    }

    static {
        addReplacements(Collection.class, null, Stream.of(new Object[]{null}).collect(Collectors.toList()));
        addReplacements(List.class, null, Stream.of(new Object[]{null}).collect(Collectors.toList()));
        addReplacements(Set.class, null, Stream.of(new Object[]{null}).collect(Collectors.toSet()));
    }

    static <Z> void addDefaultMapping(Class<Z> carrier, Z value) {
        DEFAULT_VALUES.putIfAbsent(carrier, value);
    }

    @SafeVarargs
    static <Z> void addReplacements(Class<Z> carrier, Z... value) {
        REPLACEMENT_VALUES.putIfAbsent(carrier, value);
    }

    @BeforeSuite
    public void TestNPEs() throws IOException {
        fetchAllPublicClassFileAPI();
        populateClassFileMappings();
    }

    @Test(dataProvider = "cases")
    public void testNulls(String testName, @NoInjection Method meth, Object receiver, Object[] args) {
        try {
            meth.invoke(receiver, args);
            fail("Method invocation completed normally");
        } catch (InvocationTargetException ex) {
            Class<?> cause = ex.getCause().getClass();
            assertEquals(cause, NullPointerException.class, "got " + cause.getName() + " - expected NullPointerException");
        } catch (Throwable ex) {
            fail("Unexpected exception: " + ex);
        }
    }

    @DataProvider(name = "cases")
    static Iterator<Object[]> cases() {
        List<Object[]> cases = new ArrayList<>();
        for (Class<?> clazz : CLASSES) {
            for (Method m : clazz.getMethods()) {
                if (OBJECT_METHODS.contains(m.getName())) continue;
                boolean isStatic = (m.getModifiers() & Modifier.STATIC) != 0;
                List<Integer> refIndices = new ArrayList<>();
                for (int i = 0; i < m.getParameterCount(); i++) {
                    Class<?> param = m.getParameterTypes()[i];
                    if (!param.isPrimitive()) {
                        refIndices.add(i);
                    }
                }
                for (int i : refIndices) {
                    Object[] replacements = replacements(m.getParameterTypes()[i]);
                    for (int r = 0; r < replacements.length; r++) {
                        String testName = clazz.getName() + "/" + shortSig(m) + "/" + i + "/" + r;
                        Object[] args = new Object[m.getParameterCount()];
                        for (int j = 0; j < args.length; j++) {
                            args[j] = defaultValue(m.getParameterTypes()[j]);
                        }
                        args[i] = replacements[r];
                        Object receiver = isStatic ? null : defaultValue(clazz);
                        cases.add(new Object[]{testName, m, receiver, args});
                    }
                }
            }
        }
        return cases.iterator();
    }

    static String shortSig(Method m) {
        StringJoiner sj = new StringJoiner(",", m.getName() + "(", ")");
        for (Class<?> parameterType : m.getParameterTypes()) {
            sj.add(parameterType.getTypeName());
        }
        return sj.toString();
    }

    static Object defaultValue(Class<?> carrier) {
        if (carrier.isArray()) {
            return Array.newInstance(carrier.componentType(), 0);
        }
        Object value = DEFAULT_VALUES.get(carrier);
        if (value == null) {
            throw new UnsupportedOperationException(carrier.getName());
        }
        return value;
    }

    static Object[] replacements(Class<?> carrier) {
        if (carrier.isArray() && !carrier.getComponentType().isPrimitive()) {
            Object arr = Array.newInstance(carrier.componentType(), 1);
            Array.set(arr, 0, null);
            return new Object[]{null, arr};
        }
        return REPLACEMENT_VALUES.getOrDefault(carrier, new Object[]{null});
    }

    private void fetchAllPublicClassFileAPI() {
        JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        JavacTask ct = (JavacTask) tool.getTask(null,
                null,
                null,
                null,
                null,
                Collections.singletonList(SimpleJavaFileObject.forSource(URI.create("myfo:/Test.java"), "")));
        try {
            ct.analyze();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Elements elements = ct.getElements();
        elements.getModuleElement("java.base"); // forces module graph to be instantiated
        elements.getAllModuleElements().stream()
                .filter(me -> me.getQualifiedName().toString().equals("java.base"))
                .flatMap(moduleElement -> ElementFilter.exportsIn(moduleElement.getDirectives()).stream())
                .filter(ed -> ed.getTargetModules() == null)
                .filter(ed -> ed.getPackage().getQualifiedName().toString().startsWith("java.lang.classfile"))
                .flatMap(ed -> ElementFilter.typesIn(ed.getPackage().getEnclosedElements()).stream())
                .filter(te -> !te.getQualifiedName().toString().equals("java.lang.classfile.WritableElement"))//todo comment this one out once ct.sym is updated
                .forEach(this::processClazz);
    }

    private void processClazz(TypeElement te) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Class<?> clazz = Class.forName(getFullClassName(te), true, classLoader);
            CLASSES.add(clazz);
            te.getEnclosedElements().stream()
                    .filter(element -> element.getKind().isDeclaredType())
                    .map(TypeElement.class::cast)
                    .forEach(nestedClass -> processClazz(nestedClass));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static String getFullClassName(TypeElement te) {
        StringBuilder classNameBuilder = new StringBuilder(te.getSimpleName().toString());
        Element enclosingElement = te.getEnclosingElement();
        while (enclosingElement instanceof TypeElement) {
            classNameBuilder.insert(0, enclosingElement.getSimpleName().toString() + "$");
            enclosingElement = enclosingElement.getEnclosingElement();
        }
        if (enclosingElement != null) {
            classNameBuilder.insert(0, enclosingElement + ".");
        }
        return classNameBuilder.toString();
    }

    private static void populateClassFileMappings() throws IOException {
        Path classFilePath = Path.of(targPathname);
        byte[] classFileBytes = Files.readAllBytes(classFilePath);
        ClassModel cm = ClassFile.of().parse(classFileBytes);

        //TODO organize filling the hashmap in a single block

        Optional firstFlag = cm.flags().flags().stream().findFirst();
        firstFlag.ifPresent(flag -> {
            AccessFlag accessFlag = (AccessFlag) flag;
            AccessFlag.Location flagLocation = accessFlag.locations().iterator().next();
            addDefaultMapping(AccessFlag.class, accessFlag);
            addDefaultMapping(AccessFlag.Location.class, flagLocation);
        });

        var annotation = cm.findAttribute(Attributes.runtimeVisibleAnnotations()).get().annotations().getFirst();
        addDefaultMapping(Annotation.class, annotation);
        var anno = AnnotationValue.ofAnnotation(annotation);
        addDefaultMapping(AnnotationValue.OfAnnotation.class, anno);
        addDefaultMapping(AnnotationValue.OfConstant.class, AnnotationValue.ofBoolean(false));

        for (ClassElement ce : cm) {
            switch (ce) {
                case Attribute<?> a -> {
                    addDefaultMapping(AttributeMapper.class, a.attributeMapper());
                    addDefaultMapping(Attribute.class, a);
                }
                case AccessFlags af -> addDefaultMapping(AccessFlags.class, af);
                case Superclass sc -> addDefaultMapping(Superclass.class, sc);
                case Interfaces i -> addDefaultMapping(Interfaces.class, i);
                case FieldModel fm -> {
                    addDefaultMapping(FieldModel.class, fm);
                    addDefaultMapping(Utf8Entry.class, fm.fieldName());
                    addDefaultMapping(ClassDesc.class, fm.fieldTypeSymbol());
                    addDefaultMapping(AnnotationValue.class, AnnotationValue.of(fm.fieldTypeSymbol()));
                    addDefaultMapping(AnnotationValue.OfClass.class, AnnotationValue.ofClass(fm.fieldName()));
                }
                case MethodModel mm -> {
                    addDefaultMapping(MethodTypeDesc.class, mm.methodTypeSymbol());
                    addDefaultMapping(MethodModel.class, mm);
                    for (MethodElement me : mm) {
                        if (me instanceof CodeModel xm) {
                            for (CodeElement e : xm) {
                                switch (e) {
                                    case InvokeInstruction ii -> addDefaultMapping(InvokeInstruction.class, ii);
                                    case FieldInstruction fi -> addDefaultMapping(FieldInstruction.class, fi);
                                    default -> {
                                    }
                                }
                            }
                        }
                    }
                }
                default -> {
                }
            }
        }

        for (Attribute<?> a : cm.attributes()) {
            switch (a) {
                case UnknownAttribute attr -> addDefaultMapping(UnknownAttribute.class, attr);
                case AnnotationDefaultAttribute attr -> addDefaultMapping(AnnotationDefaultAttribute.class, attr);
                case BootstrapMethodsAttribute attr -> addDefaultMapping(BootstrapMethodsAttribute.class, attr);
                case CharacterRangeTableAttribute attr -> addDefaultMapping(CharacterRangeTableAttribute.class, attr);
                case CodeAttribute attr -> addDefaultMapping(CodeAttribute.class, attr);
                case CompilationIDAttribute attr -> addDefaultMapping(CompilationIDAttribute.class, attr);
                case ConstantValueAttribute attr -> addDefaultMapping(ConstantValueAttribute.class, attr);
                case DeprecatedAttribute attr -> addDefaultMapping(DeprecatedAttribute.class, attr);
                case EnclosingMethodAttribute attr -> addDefaultMapping(EnclosingMethodAttribute.class, attr);
                case ExceptionsAttribute attr -> addDefaultMapping(ExceptionsAttribute.class, attr);
                case InnerClassesAttribute attr -> addDefaultMapping(InnerClassesAttribute.class, attr);
                case LineNumberTableAttribute attr -> addDefaultMapping(LineNumberTableAttribute.class, attr);
                case LocalVariableTableAttribute attr -> addDefaultMapping(LocalVariableTableAttribute.class, attr);
                case LocalVariableTypeTableAttribute attr ->
                        addDefaultMapping(LocalVariableTypeTableAttribute.class, attr);
                case NestHostAttribute attr -> addDefaultMapping(NestHostAttribute.class, attr);
                case MethodParametersAttribute attr -> addDefaultMapping(MethodParametersAttribute.class, attr);
                case ModuleAttribute attr -> addDefaultMapping(ModuleAttribute.class, attr);
                case ModuleHashesAttribute attr -> addDefaultMapping(ModuleHashesAttribute.class, attr);
                case ModuleMainClassAttribute attr -> addDefaultMapping(ModuleMainClassAttribute.class, attr);
                case ModulePackagesAttribute attr -> addDefaultMapping(ModulePackagesAttribute.class, attr);
                case ModuleResolutionAttribute attr -> addDefaultMapping(ModuleResolutionAttribute.class, attr);
                case ModuleTargetAttribute attr -> addDefaultMapping(ModuleTargetAttribute.class, attr);
                case NestMembersAttribute attr -> addDefaultMapping(NestMembersAttribute.class, attr);
                case RecordAttribute attr -> addDefaultMapping(RecordAttribute.class, attr);
                case RuntimeVisibleAnnotationsAttribute attr ->
                        addDefaultMapping(RuntimeVisibleAnnotationsAttribute.class, attr);
                case RuntimeInvisibleAnnotationsAttribute attr ->
                        addDefaultMapping(RuntimeInvisibleAnnotationsAttribute.class, attr);
                case RuntimeVisibleTypeAnnotationsAttribute attr ->
                        addDefaultMapping(RuntimeVisibleTypeAnnotationsAttribute.class, attr);
                case RuntimeInvisibleTypeAnnotationsAttribute attr ->
                        addDefaultMapping(RuntimeInvisibleTypeAnnotationsAttribute.class, attr);
                case RuntimeVisibleParameterAnnotationsAttribute attr ->
                        addDefaultMapping(RuntimeVisibleParameterAnnotationsAttribute.class, attr);
                case RuntimeInvisibleParameterAnnotationsAttribute attr ->
                        addDefaultMapping(RuntimeInvisibleParameterAnnotationsAttribute.class, attr);
                case PermittedSubclassesAttribute attr -> addDefaultMapping(PermittedSubclassesAttribute.class, attr);
                case SignatureAttribute attr -> addDefaultMapping(SignatureAttribute.class, attr);
                case SourceDebugExtensionAttribute attr -> addDefaultMapping(SourceDebugExtensionAttribute.class, attr);
                case SourceFileAttribute attr -> addDefaultMapping(SourceFileAttribute.class, attr);
                case SourceIDAttribute attr -> addDefaultMapping(SourceIDAttribute.class, attr);
                case StackMapTableAttribute attr -> addDefaultMapping(StackMapTableAttribute.class, attr);
                case SyntheticAttribute attr -> addDefaultMapping(SyntheticAttribute.class, attr);

                default -> {}
            }
        }
    }
}
