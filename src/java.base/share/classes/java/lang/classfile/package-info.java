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
 * <h2>Provides classfile parsing, generation, and transformation library.</h2>
 * The {@code java.lang.classfile} package contains classes for reading, writing, and
 * modifying Java class files, as specified in Chapter {@jvms 4} of the
 * <cite>Java Virtual Machine Specification</cite>.
 *
 * <h2>Reading classfiles</h2>
 * The main class for reading classfiles is {@link java.lang.classfile.ClassModel}; we
 * convert bytes into a {@link java.lang.classfile.ClassModel} with {@link
 * java.lang.classfile.ClassFile#parse(byte[])}:
 *
 * {@snippet lang=java :
 * ClassModel cm = ClassFile.of().parse(bytes);
 * }
 *
 * There are several additional overloads of {@code parse} that let you specify
 * various processing options.
 * <p>
 * A {@link java.lang.classfile.ClassModel} is an immutable description of a class
 * file.  It provides accessor methods to get at class metadata (e.g., {@link
 * java.lang.classfile.ClassModel#thisClass()}, {@link java.lang.classfile.ClassModel#flags()}),
 * as well as subordinate classfile entities ({@link java.lang.classfile.ClassModel#fields()},
 * {@link java.lang.classfile.ClassModel#attributes()}). A {@link
 * java.lang.classfile.ClassModel} is inflated lazily; most parts of the classfile are
 * not parsed until they are actually needed.
 * <p>
 * We can enumerate the names of the fields and methods in a class by:
 * {@snippet lang="java" class="PackageSnippets" region="enumerateFieldsMethods1"}
 * <p>
 * When we enumerate the methods, we get a {@link java.lang.classfile.MethodModel} for each method; like a
 * {@code ClassModel}, it gives us access to method metadata and
 * the ability to descend into subordinate entities such as the bytecodes of the
 * method body. In this way, a {@code ClassModel} is the root of a
 * tree, with children for fields, methods, and attributes, and {@code MethodModel} in
 * turn has its own children (attributes, {@code CodeModel}, etc.)
 * <p>
 * Methods like {@link java.lang.classfile.ClassModel#methods} allows us to traverse the class structure
 * explicitly, going straight to the parts we are interested in.  This is useful
 * for certain kinds of analysis, but if we wanted to process the whole
 * classfile, we may want something more organized.  A {@link
 * java.lang.classfile.ClassModel} also provides us with a view of the classfile as a
 * series of class <em>elements</em>, which may include methods, fields, attributes,
 * and more, and which can be distinguished with pattern matching.  We could
 * rewrite the above example as:
 * {@snippet lang="java" class="PackageSnippets" region="enumerateFieldsMethods2"}
 * <p>
 * The models returned as elements from traversing {@code ClassModel} can in
 * turn be sources of elements.  If we wanted to
 * traverse a classfile and enumerate all the classes for which we access fields
 * and methods, we can pick out the class elements that describe methods, then
 * in turn pick out the method elements that describe the code attribute, and
 * finally pick out the code elements that describe field access and invocation
 * instructions:
 * {@snippet lang="java" class="PackageSnippets" region="gatherDependencies1"}
 * <p>
 * This same query could alternately be processed as a stream pipeline over
 * class elements:
 * {@snippet lang="java" class="PackageSnippets" region="gatherDependencies2"}
 *
 * <h3>Models and elements</h3>
 * The view of classfiles presented by this API is framed in terms of
 * <em>models</em> and <em>elements</em>.  Models represent complex structures,
 * such as classes, methods, fields, record elements, or the code body of a
 * method.  Models can be explored either via random-access navigation (such as
 * the {@link java.lang.classfile.ClassModel#methods()} accessor) or as a linear
 * sequence of <em>elements</em>. (Elements can in turn also be models; a {@link
 * java.lang.classfile.FieldModel} is also an element of a class.) For each model type
 * (e.g., {@link java.lang.classfile.MethodModel}), there is a corresponding element
 * type ({@link java.lang.classfile.MethodElement}).  Models and elements are immutable
 * and are inflated lazily so creating a model does not necessarily require
 * processing its entire content.
 *
 * <h3>The constant pool</h3>
 * Much of the interesting content in a classfile lives in the <em>constant
 * pool</em>. {@link java.lang.classfile.ClassModel} provides a lazily-inflated,
 * read-only view of the constant pool via {@link java.lang.classfile.ClassModel#constantPool()}.
 * Descriptions of classfile content is often exposed in the form of various
 * subtypes of {@link java.lang.classfile.constantpool.PoolEntry}, such as {@link
 * java.lang.classfile.constantpool.ClassEntry} or {@link java.lang.classfile.constantpool.Utf8Entry}.
 * <p>
 * Constant pool entries are also exposed through models and elements; in the
 * above traversal example, the {@link java.lang.classfile.instruction.InvokeInstruction}
 * element exposed a method for {@code owner} that corresponds to a {@code
 * Constant_Class_info} entry in the constant pool.
 *
 * <h3>Attributes</h3>
 * Much of the contents of a classfile is stored in attributes; attributes are
 * found on classes, methods, fields, record components, and on the {@code Code}
 * attribute.  Most attributes are surfaced as elements; for example, {@link
 * java.lang.classfile.attribute.SignatureAttribute} is a {@link
 * java.lang.classfile.ClassElement}, {@link java.lang.classfile.MethodElement}, and {@link
 * java.lang.classfile.FieldElement} since it can appear in all of those places, and is
 * included when iterating the elements of the corresponding model.
 * <p>
 * Some attributes are not surfaced as elements; these are attributes that are
 * tightly coupled to -- and logically part of -- other parts of the class file.
 * These include the {@code BootstrapMethods}, {@code LineNumberTable}, {@code
 * StackMapTable}, {@code LocalVariableTable}, and {@code
 * LocalVariableTypeTable} attributes.  These are processed by the library and
 * treated as part of the structure they are coupled to (the entries of the
 * {@code BootstrapMethods} attribute are treated as part of the constant pool;
 * line numbers and local variable metadata are modeled as elements of {@link
 * java.lang.classfile.CodeModel}.)
 * <p>
 * The {@code Code} attribute, in addition to being modeled as a {@link
 * java.lang.classfile.MethodElement}, is also a model in its own right ({@link
 * java.lang.classfile.CodeModel}) due to its complex structure.
 * <p>
 * Each standard attribute has an interface (in {@code java.lang.classfile.attribute})
 * which exposes the contents of the attribute and provides factories to
 * construct the attribute.  For example, the {@code Signature} attribute is
 * defined by the {@link java.lang.classfile.attribute.SignatureAttribute} class, and
 * provides accessors for {@link java.lang.classfile.attribute.SignatureAttribute#signature()}
 * as well as factories taking {@link java.lang.classfile.constantpool.Utf8Entry} or
 * {@link java.lang.String}.
 *
 * <h3>Custom attributes</h3>
 * Attributes are converted between their classfile form and their corresponding
 * object form via an {@link java.lang.classfile.AttributeMapper}.  An {@code
 * AttributeMapper} provides the
 * {@link java.lang.classfile.AttributeMapper#readAttribute(AttributedElement,
 * ClassReader, int)} method for mapping from the classfile format
 * to an attribute instance, and the
 * {@link java.lang.classfile.AttributeMapper#writeAttribute(java.lang.classfile.BufWriter,
 * java.lang.Object)} method for mapping back to the classfile format.  It also
 * contains metadata including the attribute name, the set of classfile entities
 * where the attribute is applicable, and whether multiple attributes of the
 * same kind are allowed on a single entity.
 * <p>
 * There are built-in attribute mappers (in {@link java.lang.classfile.Attributes}) for
 * each of the attribute types defined in section {@jvms 4.7} of <cite>The Java Virtual
 * Machine Specification</cite>, as well as several common nonstandard attributes used by the
 * JDK such as {@code CharacterRangeTable}.
 * <p>
 * Unrecognized attributes are delivered as elements of type {@link
 * java.lang.classfile.attribute.UnknownAttribute}, which provide access only to the
 * {@code byte[]} contents of the attribute.
 * <p>
 * For nonstandard attributes, user-provided attribute mappers can be specified
 * through the use of the {@link
 * java.lang.classfile.ClassFile.AttributeMapperOption#of(java.util.function.Function)}}
 * classfile option.  Implementations of custom attributes should extend {@link
 * java.lang.classfile.CustomAttribute}.
 *
 * <h3>Options</h3>
 * <p>
 * {@link java.lang.classfile.ClassFile#of(java.lang.classfile.ClassFile.Option[])}
 * accepts a list of options.  {@link java.lang.classfile.ClassFile.Option} is a base interface
 * for some statically enumerated options, as well as factories for more complex options,
 * including:
 * <ul>
 *   <li>{@link java.lang.classfile.ClassFile.AttributeMapperOption#of(java.util.function.Function)}
 * -- specify format of custom attributes</li>
 *   <li>{@link java.lang.classfile.ClassFile.AttributesProcessingOption}
 * -- unrecognized or problematic original attributes (default is {@code PASS_ALL_ATTRIBUTES})</li>
 *   <li>{@link java.lang.classfile.ClassFile.ClassHierarchyResolverOption#of(java.lang.classfile.ClassHierarchyResolver)}
 * -- specify a custom class hierarchy resolver used by stack map generation</li>
 *   <li>{@link java.lang.classfile.ClassFile.ConstantPoolSharingOption}}
 * -- share constant pool when transforming (default is {@code SHARED_POOL})</li>
 *   <li>{@link java.lang.classfile.ClassFile.DeadCodeOption}}
 * -- patch out unreachable code (default is {@code PATCH_DEAD_CODE})</li>
 *   <li>{@link java.lang.classfile.ClassFile.DeadLabelsOption}}
 * -- filter unresolved labels (default is {@code FAIL_ON_DEAD_LABELS})</li>
 *   <li>{@link java.lang.classfile.ClassFile.DebugElementsOption}
 * -- processing of debug information, such as local variable metadata (default is {@code PASS_DEBUG}) </li>
 *   <li>{@link java.lang.classfile.ClassFile.LineNumbersOption}
 * -- processing of line numbers (default is {@code PASS_LINE_NUMBERS}) </li>
 *   <li>{@link java.lang.classfile.ClassFile.ShortJumpsOption}
 * -- automatically rewrite short jumps to long when necessary (default is {@code FIX_SHORT_JUMPS})</li>
 *   <li>{@link java.lang.classfile.ClassFile.StackMapsOption}
 * -- generate stackmaps (default is {@code STACK_MAPS_WHEN_REQUIRED})</li>
 * </ul>
 * <p>
 * Most options allow you to request that certain parts of the classfile be
 * skipped during traversal, such as debug information or unrecognized
 * attributes.  Some options allow you to suppress generation of portions of the
 * classfile, such as stack maps.  Many of these options are to access
 * performance tradeoffs; processing debug information and line numbers has a
 * cost (both in writing and reading.)  If you don't need this information, you
 * can suppress it with options to gain some performance.
 *
 * <h2>Writing classfiles</h2>
 * ClassFile generation is accomplished through <em>builders</em>.  For each
 * entity type that has a model, there is also a corresponding builder type;
 * classes are built through {@link java.lang.classfile.ClassBuilder}, methods through
 * {@link java.lang.classfile.MethodBuilder}, etc.
 * <p>
 * Rather than creating builders directly, builders are provided as an argument
 * to a user-provided lambda.  To generate the familiar "hello world" program,
 * we ask for a class builder, and use that class builder to create method
 * builders for the constructor and {@code main} method, and in turn use the
 * method builders to create a {@code Code} attribute and use the code builders
 * to generate the instructions:
 * {@snippet lang="java" class="PackageSnippets" region="helloWorld1"}
 * <p>
 * The convenience methods {@code ClassBuilder.buildMethodBody} allows us to ask
 * {@link ClassBuilder} to create code builders to build method bodies directly,
 * skipping the method builder custom lambda:
 * {@snippet lang="java" class="PackageSnippets" region="helloWorld2"}
 * <p>
 * Builders often support multiple ways of expressing the same entity at
 * different levels of abstraction.  For example, the {@code invokevirtual}
 * instruction invoking {@code println} could have been generated with {@link
 * java.lang.classfile.CodeBuilder#invokevirtual(java.lang.constant.ClassDesc,
 * java.lang.String, java.lang.constant.MethodTypeDesc) CodeBuilder.invokevirtual}, {@link
 * java.lang.classfile.CodeBuilder#invoke(java.lang.classfile.Opcode,
 * java.lang.constant.ClassDesc, java.lang.String, java.lang.constant.MethodTypeDesc,
 * boolean) CodeBuilder.invokeInstruction}, or {@link
 * java.lang.classfile.CodeBuilder#with(java.lang.classfile.ClassFileElement)
 * CodeBuilder.with}.
 * <p>
 * The convenience method {@code CodeBuilder.invokevirtual} behaves as if it calls
 * the convenience method {@code CodeBuilder.invokeInstruction}, which in turn behaves
 * as if it calls method {@code CodeBuilder.with}. This composing of method calls on the
 * builder enables the composing of transforms (as described later).
 * <p>
 * Unless otherwise noted, passing a {@code null} argument to a constructor
 * or method of any Class-File API class or interface will cause a {@link
 * java.lang.NullPointerException NullPointerException} to be thrown. Additionally,
 * invoking a method with an array or collection containing a {@code null} element
 * will cause a {@code NullPointerException}, unless otherwise specified. </p>
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
 * <h2>API conventions</h2>
 * <p>
 * The API is largely derived from a <a href="#data_model"><em>data model</em></a>
 * for the classfile format, which defines each element kind (which includes models and
 * attributes) and its properties.  For each element kind, there is a
 * corresponding interface to describe that element, and factory methods to
 * create that element.  Some element kinds also have convenience methods on the
 * corresponding builder (e.g., {@link
 * java.lang.classfile.CodeBuilder#invokevirtual(java.lang.constant.ClassDesc,
 * java.lang.String, java.lang.constant.MethodTypeDesc)}).
 * <p>
 * Most symbolic information in elements is represented by constant pool entries
 * (for example, the owner of a field is represented by a {@link
 * java.lang.classfile.constantpool.ClassEntry}.) Factories and builders also
 * accept nominal descriptors from {@code java.lang.constant} (e.g., {@link
 * java.lang.constant.ClassDesc}.)
 *
 * <h2><a id="data_model"></a>Data model</h2>
 * We define each kind of element by its name, an optional arity indicator (zero
 * or more, zero or one, exactly one), and a list of components.  The elements
 * of a class are fields, methods, and the attributes that can appear on
 * classes:
 *
 * {@snippet lang="text" :
 * ClassElement =
 *     FieldModel*(UtfEntry name, Utf8Entry descriptor)
 *     | MethodModel*(UtfEntry name, Utf8Entry descriptor)
 *     | ModuleAttribute?(int flags, ModuleEntry moduleName, UtfEntry moduleVersion,
 *                        List<ModuleRequireInfo> requires, List<ModuleOpenInfo> opens,
 *                        List<ModuleExportInfo> exports, List<ModuleProvidesInfo> provides,
 *                        List<ClassEntry> uses)
 *     | ModulePackagesAttribute?(List<PackageEntry> packages)
 *     | ModuleTargetAttribute?(Utf8Entry targetPlatform)
 *     | ModuleHashesAttribute?(Utf8Entry algorithm, List<HashInfo> hashes)
 *     | ModuleResolutionAttribute?(int resolutionFlags)
 *     | SourceFileAttribute?(Utf8Entry sourceFile)
 *     | SourceDebugExtensionsAttribute?(byte[] contents)
 *     | CompilationIDAttribute?(Utf8Entry compilationId)
 *     | SourceIDAttribute?(Utf8Entry sourceId)
 *     | NestHostAttribute?(ClassEntry nestHost)
 *     | NestMembersAttribute?(List<ClassEntry> nestMembers)
 *     | RecordAttribute?(List<RecordComponent> components)
 *     | EnclosingMethodAttribute?(ClassEntry className, NameAndTypeEntry method)
 *     | InnerClassesAttribute?(List<InnerClassInfo> classes)
 *     | PermittedSubclassesAttribute?(List<ClassEntry> permittedSubclasses)
 *     | DeclarationElement*
 * }
 *
 * where {@code DeclarationElement} are the elements that are common to all declarations
 * (classes,  methods, fields) and so are factored out:
 *
 * {@snippet lang="text" :
 * DeclarationElement =
 *     SignatureAttribute?(Utf8Entry signature)
 *     | SyntheticAttribute?()
 *     | DeprecatedAttribute?()
 *     | RuntimeInvisibleAnnotationsAttribute?(List<Annotation> annotations)
 *     | RuntimeVisibleAnnotationsAttribute?(List<Annotation> annotations)
 *     | CustomAttribute*
 *     | UnknownAttribute*
 * }
 *
 * Fields and methods are models with their own elements.  The elements of fields
 * and methods are fairly simple; most of the complexity of methods lives in the
 * {@link java.lang.classfile.CodeModel} (which models the {@code Code} attribute
 * along with the code-related attributes: stack map table, local variable table,
 * line number table, etc.)
 *
 * {@snippet lang="text" :
 * FieldElement =
 *     DeclarationElement
 *     | ConstantValueAttribute?(ConstantValueEntry constant)
 *
 * MethodElement =
 *     DeclarationElement
 *     | CodeModel?()
 *     | AnnotationDefaultAttribute?(ElementValue defaultValue)
 *     | MethodParametersAttribute?(List<MethodParameterInfo> parameters)
 *     | ExceptionsAttribute?(List<ClassEntry> exceptions)
 * }
 *
 * {@link java.lang.classfile.CodeModel} is unique in that its elements are <em>ordered</em>.
 * Elements of {@code Code} include ordinary bytecodes, as well as a number of pseudo-instructions
 * representing branch targets, line number metadata, local variable metadata, and
 * catch blocks.
 *
 * {@snippet lang="text" :
 * CodeElement = Instruction | PseudoInstruction
 *
 * Instruction =
 *     LoadInstruction(TypeKind type, int slot)
 *     | StoreInstruction(TypeKind type, int slot)
 *     | IncrementInstruction(int slot, int constant)
 *     | BranchInstruction(Opcode opcode, Label target)
 *     | LookupSwitchInstruction(Label defaultTarget, List<SwitchCase> cases)
 *     | TableSwitchInstruction(Label defaultTarget, int low, int high,
 *                              List<SwitchCase> cases)
 *     | ReturnInstruction(TypeKind kind)
 *     | ThrowInstruction()
 *     | FieldInstruction(Opcode opcode, FieldRefEntry field)
 *     | InvokeInstruction(Opcode opcode, MemberRefEntry method, boolean isInterface)
 *     | InvokeDynamicInstruction(InvokeDynamicEntry invokedynamic)
 *     | NewObjectInstruction(ClassEntry className)
 *     | NewReferenceArrayInstruction(ClassEntry componentType)
 *     | NewPrimitiveArrayInstruction(TypeKind typeKind)
 *     | NewMultiArrayInstruction(ClassEntry componentType, int dims)
 *     | ArrayLoadInstruction(Opcode opcode)
 *     | ArrayStoreInstruction(Opcode opcode)
 *     | TypeCheckInstruction(Opcode opcode, ClassEntry className)
 *     | ConvertInstruction(TypeKind from, TypeKind to)
 *     | OperatorInstruction(Opcode opcode)
 *     | ConstantInstruction(ConstantDesc constant)
 *     | StackInstruction(Opcode opcode)
 *     | MonitorInstruction(Opcode opcode)
 *     | NopInstruction()
 *
 * PseudoInstruction =
 *     | LabelTarget(Label label)
 *     | LineNumber(int line)
 *     | ExceptionCatch(Label tryStart, Label tryEnd, Label handler, ClassEntry exception)
 *     | LocalVariable(int slot, UtfEntry name, Utf8Entry type, Label startScope, Label endScope)
 *     | LocalVariableType(int slot, Utf8Entry name, Utf8Entry type, Label startScope, Label endScope)
 *     | CharacterRange(int rangeStart, int rangeEnd, int flags, Label startScope, Label endScope)
 * }
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
package java.lang.classfile;

import jdk.internal.javac.PreviewFeature;
