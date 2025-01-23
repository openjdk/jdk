/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * <h2>Provides {@code class} file parsing, generation, and transformation.</h2>
 * The {@code java.lang.classfile} package contains API models for reading, writing, and modifying {@code class} files,
 * as specified in Chapter {@jvms 4} of the <cite>Java Virtual Machine Specification</cite>.  This package, together
 * with {@link java.lang.classfile.attribute}, {@link java.lang.classfile.constantpool}, and {@link
 * java.lang.classfile.instruction}, forms the Class-File API.
 *
 * <h2 id="conventions">API conventions</h2>
 * The modeling interfaces of the Class-File API largely derives from structures in the {@code class} file format.
 * These models are immutable, but may or may not be thread safe, as they may be {@linkplain ##reading read} from {@code
 * class} files.  Additionally, invocations to accessor methods on models may lead to {@link IllegalArgumentException}
 * due to the {@linkplain ##lazy-reading lazy nature} of {@code class} file reading.
 * <p>
 * Many models have factory methods defined in the modeling interfaces; other models specify how they can be created.
 * For example, constant pool entries are created though {@link ConstantPoolBuilder} exclusively.
 * <p>
 * An {@link IllegalArgumentException} is thrown if malformed {@code class} file data is encountered during reading, or
 * an invalid model is provided during {@code class} file writing.  Due to the lazy nature of the Class-File API, an
 * {@code IllegalArgumentException} may arise in the invocation of any accessor method on Class-File API models.
 * <p>
 * Unless otherwise noted, passing a {@code null} argument to a constructor or a method of any Class-File API class or
 * interface will cause a {@link NullPointerException} to be thrown.  Additionally, passing an array or collection
 * containing a {@code null} element will cause a {@code NullPointerException}, unless otherwise specified.
 *
 * <h2 id="options">Options</h3>
 * The functionalities of the Class-File API are provided through {@link ClassFile}, which holds processing options.
 * {@link ClassFile.Option} is the base interface for all configurable options in the Class-File API.
 * <p>
 * Two options are critical to correct {@code class} file reading or writing:
 * <ul>
 * <li>{@link ClassFile.AttributeMapperOption} must be set to read custom attributes.  Otherwise, they are represented
 * as {@link UnknownAttribute}.
 * <li>{@link ClassFile.ClassHierarchyResolverOption} must be set to write {@code class} files whose method bodies refer
 * to classes or interfaces not visible to the {@linkplain ClassLoader#getSystemClassLoader() system class loader}.<br>
 * In addition, the resolver must be {@link ClassHierarchyResolver#ofResourceParsing(ClassLoader)} instead of the
 * default if no loading of system classes is desired, such as in the usage of agents in instrumentation.
 * </ul>
 * Most other options are tradeoffs between accuracy of the read API models or written {@code class} files and
 * processing performance or {@code class} file correctness.  Their modeling classes specify their exact effects.
 *
 * <h2 id="reading">Reading {@code class} Files</h2>
 * {@link ClassModel} models a complete {@code class} file; we convert bytes into a {@code ClassModel} with {@link
 * ClassFile#parse(byte[])}, assuming there is no custom attribute:
 * {@snippet lang=java :
 * ClassModel cm = ClassFile.of().parse(bytes);
 * }
 * <p>
 * Accessor methods like {@link ClassModel#methods} allows us to random access the class structure explicitly, going
 * straight to the parts we are interested in.  We can enumerate the names of the fields and methods in a class by:
 * {@snippet lang="java" class="PackageSnippets" region="enumerateFieldsMethods1"}
 * <p>
 * A {@link ClassModel} and the models it provides access to are inflated lazily; most parts of the {@code class} file
 * are not parsed until they are actually needed, that is when the specific accessor methods are called.  This
 * significantly improves processing speed, but as a consequence, errors in the {@code class} files are also reported
 * lazily, which the user should be aware of when calling the accessor methods.
 *
 * <h3 id="constantPool">The Constant Pool</h3>
 * To faithfully represent the exact structure of a {@code class} file, {@link ClassModel} provides a lazily-inflated,
 * read-only view of the constant pool via {@link ClassModel#constantPool()}.  Many structures in the {@code class} file
 * format refers to various subtypes of {@link PoolEntry}, such as {@link ClassEntry} or {@link Utf8Entry}, directly
 * accessible through accessor methods.  See {@link java.lang.classfile.constantpool} for an overview about the constant
 * pool and its entries.
 * <p>
 * Constant pool entries in the {@code class} file format represent symbolic information; {@link java.lang.constant}
 * provides nominal descriptors that better model them without dependency on specific {@code class} files, such as
 * {@link String} versus {@link Utf8Entry} or {@link ClassDesc} versus {@link ClassEntry}.  Models that include constant
 * pool references often define additional factory or accessor overloads to accept or return nominal descriptors; the
 * entry themselves also provide accessors to return nominal descriptors, such as {@link ClassEntry#asSymbol()}.
 *
 * <h3 id="attributes">Attributes</h3>
 * Much of the contents of a {@code class} file is stored in attributes; attributes are found on classes, methods,
 * fields, record components, and the {@code Code} attribute, modeled by {@link AttributedElement}, which provides
 * basic read access.  See {@link java.lang.classfile.attribute} for an overview about attributes.
 * <p>
 * The type of an attribute is determined by its {@link Attribute#attributeMapper() attributeMapper}; {@link Attributes}
 * holds the mappers for all standard attributes and some JDK-specific nonstandard attributes, which also refer to their
 * modeling interfaces.  Mappers may be implemented by users to define custom attributes as well; they should extend
 * {@link CustomAttribute}, which also has more detailed information about implementing user-defined attributes.
 * Finally, user-defined attributes must be registered to {@link ClassFile.AttributeMapperOption}.
 *
 * <h3 id="traversal">Traversal of Models</h3>
 * Structures in the {@code class} file format often have many members.  With only accessors and factory methods, if we
 * want to add a new field to a class, we need to manually extract and preserve the other fields, all methods, all
 * attributes, and other {@code ClassFile} structure properties like versions and access flags in the factory call.
 * The interface {@link CompoundElement} provides a solution: these properties to preserve all implement an interface
 * indicating their membership, such as {@link ClassElement} for member element of a class, and they can be traversed
 * as a uniform stream, both to users and for building {@code class} files.
 * <p>
 * In the Class-File API, {@link ClassModel}, {@link MethodModel}, {@link FieldModel}, and {@link CodeModel} support
 * traversal.  In a traversal, every member element of a model is delivered to a {@link Consumer} handler in {@link
 * CompoundElement#forEach}; the order of delivery is significant in {@link CodeModel}, while members elements of other
 * models usually have no behavior associated with the order they appear in their owner structure.
 *
 * <h2 id="writing">Writing {@code class} Files</h2>
 * ClassFile generation is accomplished through <em>builders</em>.  For each {@link CompoundElement}, there is also a
 * corresponding builder type, such as {@link ClassBuilder} for {@link ClassModel}.
 * <p>
 * Users cannot create builders directly; builders are provided as an argument to a user-provided handler.  To generate
 * the familiar "hello world" program, we ask for a class builder, and use that class builder to create method builders
 * for the constructor and {@code main} method, and in turn use the method builders to create a {@code Code} attribute
 * and use the code builders to generate the instructions:
 * {@snippet lang="java" class="PackageSnippets" region="helloWorld1"}
 * <p>
 * The convenience methods {@link ClassBuilder#withMethodBody} allows us to ask {@link ClassBuilder} to create code
 * builders to build method bodies directly for methods that only has a body and access flags, skipping the method
 * building handlers:
 * {@snippet lang="java" class="PackageSnippets" region="helloWorld2"}
 * <p>
 * Builders often support multiple ways of expressing the same entity at different levels of abstraction.  For example,
 * the {@code invokevirtual} instruction invoking {@code println} could have been generated with {@link
 * CodeBuilder#invokevirtual(ClassDesc, String, MethodTypeDesc) CodeBuilder.invokevirtual}, {@link
 * CodeBuilder#invoke(Opcode, ClassDesc, String, MethodTypeDesc, boolean) CodeBuilder.invoke}, or {@link
 * CodeBuilder#with(ClassFileElement) CodeBuilder.with}.
 * <p>
 * The convenience method {@code CodeBuilder.invokevirtual} behaves as if it calls
 * the convenience method {@code CodeBuilder.invoke}, which in turn behaves
 * as if it calls method {@code CodeBuilder.with}. This composing of method calls on the
 * builder enables the composing of transforms (as described later).

 *
 * <h3>Symbolic information</h3>
 * To describe symbolic information for classes and types, the API uses the
 * nominal descriptor abstractions from {@code java.lang.constant} such as {@link
 * java.lang.constant.ClassDesc} and {@link java.lang.constant.MethodTypeDesc},
 * which is less error-prone than using raw strings.
 * <p>
 * If a constant pool entry has a nominal representation then it provides a
 * method returning the corresponding nominal descriptor type e.g.
 * method {@link java.lang.classfile.constantpool.ClassEntry#asSymbol} returns
 * {@code ClassDesc}.
 * <p>
 * Where appropriate builders provide two methods for building an element with
 * symbolic information, one accepting nominal descriptors, and the other
 * accepting constant pool entries.
 *
 * <h3>Consistency checks, syntax checks and verification</h3>
 * No consistency checks are performed while building or transforming classfiles
 * (except for null arguments checks). All builders and classfile elements factory
 * methods accepts the provided information without implicit validation.
 * However, fatal inconsistencies (like for example invalid code sequence or
 * unresolved labels) affects internal tools and may cause exceptions later in
 * the classfile building process.
 * <p>
 * Using nominal descriptors assures the right serial form is applied by the
 * ClassFile API library based on the actual context. Also these nominal
 * descriptors are validated during their construction, so it is not possible to
 * create them with invalid content by mistake. Following example pass class
 * name to the {@link java.lang.constant.ClassDesc#of} method for validation
 * and the library performs automatic conversion to the right internal form of
 * the class name when serialized in the constant pool as a class entry.
 * {@snippet lang=java :
 * var validClassEntry = constantPoolBuilder.classEntry(ClassDesc.of("mypackage.MyClass"));
 * }
 * <p>
 * On the other hand it is possible to use builders methods and factories accepting
 * constant pool entries directly. Constant pool entries can be constructed also
 * directly from raw values, with no additional conversions or validations.
 * Following example uses intentionally wrong class name form and it is applied
 * without any validation or conversion.
 * {@snippet lang=java :
 * var invalidClassEntry = constantPoolBuilder.classEntry(
 *                             constantPoolBuilder.utf8Entry("mypackage.MyClass"));
 * }
 * <p>
 * More complex verification of a classfile can be achieved by invocation of
 * {@link java.lang.classfile.ClassFile#verify}.
 *
 * <h2>Transforming classfiles</h2>
 * ClassFile Processing APIs are most frequently used to combine reading and
 * writing into transformation, where a classfile is read, localized changes are
 * made, but much of the classfile is passed through unchanged.  For each kind
 * of builder, {@code XxxBuilder} has a method {@code with(XxxElement)} so that
 * elements that we wish to pass through unchanged can be handed directly back
 * to the builder.
 * <p>
 * If we wanted to strip out methods whose names starts with "debug", we could
 * get an existing {@link java.lang.classfile.ClassModel}, build a new classfile that
 * provides a {@link java.lang.classfile.ClassBuilder}, iterate the elements of the
 * original {@link java.lang.classfile.ClassModel}, and pass through all of them to
 * the builder except the methods we want to drop:
 * {@snippet lang="java" class="PackageSnippets" region="stripDebugMethods1"}
 * <p>
 * This hands every class element, except for those corresponding to methods
 * whose names start with {@code debug}, back to the builder.  Transformations
 * can of course be more complicated, diving into method bodies and instructions
 * and transforming those as well, but the same structure is repeated at every
 * level, since every entity has corresponding model, builder, and element
 * abstractions.
 * <p>
 * Transformation can be viewed as a "flatMap" operation on the sequence of
 * elements; for every element, we could pass it through unchanged, drop it, or
 * replace it with one or more elements.  Because transformation is such a
 * common operation on classfiles, each model type has a corresponding {@code
 * XxxTransform} type (which describes a transform on a sequence of {@code
 * XxxElement}) and each builder type has {@code transformYyy} methods for transforming
 * its child models.  A transform is simply a functional interface that takes a
 * builder and an element, and an implementation "flatMap"s elements
 * into the builder.  We could express the above as:
 * {@snippet lang="java" class="PackageSnippets" region="stripDebugMethods2"}
 * <p>
 * {@code ClassTransform.dropping} convenience method allow us to simplify the same
 * transformation construction and express the above as:
 * {@snippet lang="java" class="PackageSnippets" region="stripDebugMethods3"}
 *
 * <h3>Lifting transforms</h3>
 * While the example using transformations are only slightly shorter, the
 * advantage of expressing transformation in this way is that the transform
 * operations can be more easily combined.  Suppose we want to redirect
 * invocations of static methods on {@code Foo} to the corresponding method on
 * {@code Bar} instead.  We could express this as a transformation on {@link
 * java.lang.classfile.CodeElement}:
 * {@snippet lang="java" class="PackageSnippets" region="fooToBarTransform"}
 * <p>
 * We can then <em>lift</em> this transformation on code elements into a
 * transformation on method elements.  This intercepts method elements that
 * correspond to a {@code Code} attribute, dives into its code elements, and
 * applies the code transform to them, and passes other method elements through
 * unchanged:
 * {@snippet lang=java :
 * MethodTransform mt = MethodTransform.transformingCode(fooToBar);
 * }
 * <p>
 * and further lift the transform on method elements into one on class
 * elements:
 * {@snippet lang=java :
 * ClassTransform ct = ClassTransform.transformingMethods(mt);
 * }
 * <p>
 * or lift the code transform into the class transform directly:
 * {@snippet lang=java :
 * ClassTransform ct = ClassTransform.transformingMethodBodiess(fooToBar);
 * }
 * <p>
 * and then transform the classfile:
 * {@snippet lang=java :
 * var cc = ClassFile.of();
 * byte[] newBytes = cc.transform(cc.parse(bytes), ct);
 * }
 * <p>
 * This is much more concise (and less error-prone) than the equivalent
 * expressed by traversing the classfile structure directly:
 * {@snippet lang="java" class="PackageSnippets" region="fooToBarUnrolled"}
 *
 * <h3>Composing transforms</h3>
 * Transforms on the same type of element can be composed in sequence, where the
 * output of the first is fed to the input of the second.  Suppose we want to
 * instrument all method calls, where we print the name of a method before
 * calling it:
 * {@snippet lang="java" class="PackageSnippets" region="instrumentCallsTransform"}
 * <p>
 * Then we can compose {@code fooToBar} and {@code instrumentCalls} with {@link
 * java.lang.classfile.CodeTransform#andThen(java.lang.classfile.CodeTransform)}:
 *
 * {@snippet lang=java :
 * var cc = ClassFile.of();
 * byte[] newBytes = cc.transform(cc.parse(bytes),
 *                                ClassTransform.transformingMethods(
 *                                    MethodTransform.transformingCode(
 *                                        fooToBar.andThen(instrumentCalls))));
 * }
 *
 * Transform {@code instrumentCalls} will receive all code elements produced by
 * transform {@code forToBar}, either those code elements from the original classfile
 * or replacements (replacing static invocations to {@code Foo} with those to {@code Bar}).
 *
 * <h3>Constant pool sharing</h3>
 * Transformation doesn't merely handle the logistics of reading, transforming
 * elements, and writing.  Most of the time when we are transforming a
 * classfile, we are making relatively minor changes.  To optimize such cases,
 * transformation seeds the new classfile with a copy of the constant pool from
 * the original classfile; this enables significant optimizations (methods and
 * attributes that are not transformed can be processed by bulk-copying their
 * bytes, rather than parsing them and regenerating their contents.)  If
 * constant pool sharing is not desired it can be suppressed
 * with the {@link java.lang.classfile.ClassFile.ConstantPoolSharingOption} option.
 * Such suppression may be beneficial when transformation removes many elements,
 * resulting in many unreferenced constant pool entries.
 *
 * <h3>Transformation handling of unknown classfile elements</h3>
 * Custom classfile transformations might be unaware of classfile elements
 * introduced by future JDK releases. To achieve deterministic stability,
 * classfile transforms interested in consuming all classfile elements should be
 * implemented strictly to throw exceptions if running on a newer JDK, if the
 * transformed class file is a newer version, or if a new and unknown classfile
 * element appears. As for example in the following strict compatibility-checking
 * transformation snippets:
 * {@snippet lang="java" class="PackageSnippets" region="strictTransform1"}
 * {@snippet lang="java" class="PackageSnippets" region="strictTransform2"}
 * {@snippet lang="java" class="PackageSnippets" region="strictTransform3"}
 * <p>
 * Conversely, classfile transforms that are only interested in consuming a portion
 * of classfile elements do not need to concern with new and unknown classfile
 * elements and may pass them through. Following example shows such future-proof
 * code transformation:
 * {@snippet lang="java" class="PackageSnippets" region="benevolentTransform"}
 *
 * @since 24
 */
package java.lang.classfile;

import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.util.function.*;
