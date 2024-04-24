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

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.util.Pair;
import jtreg.SkippedException;
import javax.lang.model.element.*;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.*;
import javax.tools.JavaFileManager.Location;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.Runtime.Version;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/*
The `@since` checker verifies that for every API element, the real since value and the effective since value are the same, and reports an error if they are not.

 Real since value of an API element is computed as the oldest release in which the given API element was introduced. That is:
- for modules, classes and interfaces, the release in which the element with the given qualified name was introduced
- for constructors, the release in which the constructor with the given VM descriptor was introduced
- for methods and fields, the release in which the given method or field with the given VM descriptor became a member of its enclosing class or interface, whether direct or inherited

Effective since value of an API element is computed as follows:
- if the given element has a `@since` tag in its javadoc, it is used
- in all other cases, return the effective since value of the enclosing element

Special Handling for preview method:
- When an element is still marked as preview, the `@since` should be the first JDK release where the element was added.
- If the element is no longer marked as preview, the `@since` should be the first JDK release where it was no longer preview.
*/


public class SinceValidator {
    private final Map<String, Set<String>> LEGACY_PREVIEW_METHODS = new HashMap<>();
    private final Map<String, IntroducedIn> classDictionary = new HashMap<>();
    private final JavaCompiler tool;
    private final List<String> wrongTagsList = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new SkippedException("Test module not specified");
        }
        SinceValidator sinceCheckerTestHelper = new SinceValidator();
        sinceCheckerTestHelper.testThisModule(args[0]);
    }

    private SinceValidator() throws IOException {
        tool = ToolProvider.getSystemJavaCompiler();
        for (int i = 9; i <= Runtime.version().feature(); i++) {
            JavacTask ct = (JavacTask) tool.getTask(null, null, null,
                    List.of("--release", String.valueOf(i)),
                    null,
                    Collections.singletonList(SimpleJavaFileObject.forSource(URI.create("myfo:/Test.java"), "")));
            ct.analyze();

            String version = String.valueOf(i);
            Elements elements = ct.getElements();
            elements.getModuleElement("java.base"); // forces module graph to be instantiated
            elements.getAllModuleElements().forEach(me ->
                    processModuleRecord(me, version, ct));
        }
    }

    private void processModuleRecord(ModuleElement moduleElement, String releaseVersion, JavacTask ct) {
        for (ModuleElement.ExportsDirective ed : ElementFilter.exportsIn(moduleElement.getDirectives())) {
            persistElement(moduleElement, moduleElement, ct.getTypes(), releaseVersion);
            if (ed.getTargetModules() == null) {
                analyzePackageRecord(ed.getPackage(), releaseVersion, ct);
            }
        }
    }

    private void analyzePackageRecord(PackageElement pe, String releaseVersion, JavacTask ct) {
        persistElement(pe, pe, ct.getTypes(), releaseVersion);
        List<TypeElement> typeElements = ElementFilter.typesIn(pe.getEnclosedElements());
        for (TypeElement te : typeElements) {
            analyzeClassRecord(te, releaseVersion, ct.getTypes(), ct.getElements());
        }
    }

    private void analyzeClassRecord(TypeElement te, String version, Types types, Elements elements) {
        Set<Modifier> classModifiers = te.getModifiers();
        if (!(classModifiers.contains(Modifier.PUBLIC) || classModifiers.contains(Modifier.PROTECTED))) {
            return;
        }
        persistElement(te.getEnclosingElement(), te, types, version);
        elements.getAllMembers(te).stream()
                .filter(element -> element.getModifiers().contains(Modifier.PUBLIC) || element.getModifiers().contains(Modifier.PROTECTED))
                .filter(element -> element.getKind().isField()
                        || element.getKind() == ElementKind.METHOD
                        || element.getKind() == ElementKind.CONSTRUCTOR)
                .forEach(element -> persistElement(te, element, types, version));
        te.getEnclosedElements().stream()
                .filter(element -> element.getKind().isDeclaredType())
                .map(TypeElement.class::cast)
                .forEach(nestedClass -> analyzeClassRecord(nestedClass, version, types, elements));
    }

    public void persistElement(Element explicitOwner, Element element, Types types, String version) {
        String uniqueId = getElementName(explicitOwner, element, types);
        IntroducedIn introduced = classDictionary.computeIfAbsent(uniqueId, i -> new IntroducedIn());
        if (isPreview(element, uniqueId, version)) {
            if (introduced.introducedPreview == null) {
                introduced.introducedPreview = version;
            }
        } else {
            if (introduced.introducedStable == null) {
                introduced.introducedStable = version;
            }
        }
    }

    private boolean isPreview(Element el, String uniqueId, String currentVersion) {
        while (el != null) {
            Symbol s = (Symbol) el;
            if ((s.flags() & Flags.PREVIEW_API) != 0) {
                return true;
            }
            el = el.getEnclosingElement();
        }

        return LEGACY_PREVIEW_METHODS.containsKey(currentVersion)
                && LEGACY_PREVIEW_METHODS.get(currentVersion).contains(uniqueId);
    }

    private void testThisModule(String moduleName) throws Exception {
        Path home = Paths.get(System.getProperty("java.home"));
        Path srcZip = home.resolve("lib").resolve("src.zip");
        if (Files.notExists(srcZip)) {
            //possibly running over an exploded JDK build, attempt to find a
            //co-located full JDK image with src.zip:
            Path testJdk = Paths.get(System.getProperty("test.jdk"));
            srcZip = testJdk.getParent().resolve("images").resolve("jdk").resolve("lib").resolve("src.zip");
        }
        File f = new File(srcZip.toUri());
        if (!f.exists() && !f.isDirectory()) {
            throw new SkippedException("Skipping Test because src.zip wasn't found");
        }
        if (Files.isReadable(srcZip)) {
            URI uri = URI.create("jar:" + srcZip.toUri());
            try (FileSystem zipFO = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                Path root = zipFO.getRootDirectories().iterator().next();
                Path packagePath = root.resolve(moduleName);
                try (StandardJavaFileManager fm =
                             tool.getStandardFileManager(null, null, null)) {
                    JavacTask ct = (JavacTask) tool.getTask(null,
                            fm,
                            null,
                            List.of("--add-modules", moduleName, "-d", "."),
                            null,
                            Collections.singletonList(SimpleJavaFileObject.forSource(URI.create("myfo:/Test.java"), "")));
                    ct.analyze();
                    Elements elements = ct.getElements();
                    elements.getModuleElement("java.base");
                    try (EffectiveSourceSinceHelper javadocHelper = EffectiveSourceSinceHelper.create(ct, List.of(root), this)) {
                        processModuleCheck(elements.getModuleElement(moduleName), ct, packagePath, javadocHelper);
                    } catch (Exception e) {
                        e.printStackTrace();
                        wrongTagsList.add("Initiating javadocHelperFailed " + e.getMessage());
                    }
                    if (!wrongTagsList.isEmpty()) {
                        throw new Exception(wrongTagsList.toString());
                    }
                }
            }
        }
    }

    private void processModuleCheck(ModuleElement moduleElement, JavacTask ct, Path packagePath, EffectiveSourceSinceHelper javadocHelper) {
        if (moduleElement == null) {
            wrongTagsList.add("Module element: was null because `elements.getModuleElement(moduleName)` returns null." +
                    "fixes are needed for this Module");
        }
        String moduleVersion = getModuleVersionFromFile(packagePath);
        checkModuleOrPackage(moduleVersion, moduleElement, ct, "Module: ");
        for (ModuleElement.ExportsDirective ed : ElementFilter.exportsIn(moduleElement.getDirectives())) {
            if (ed.getTargetModules() == null) {
                String packageVersion = getPackageVersionFromFile(packagePath, ed);
                checkModuleOrPackage(packageVersion, ed.getPackage(), ct, "Package: ");
                analyzePackageCheck(ed.getPackage(), ct, javadocHelper);
            }
        }
    }

    private void checkModuleOrPackage(String moduleVersion, Element moduleElement, JavacTask ct, String elementCategory) {
        String id = getElementName(moduleElement, moduleElement, ct.getTypes());
        String introducedVersion = classDictionary.get(id).introducedStable;
        if (moduleVersion == null) {
            wrongTagsList.add("Unable to retrieve `@since` for " + elementCategory + ": " + id + "\n");
        } else {
            checkEquals(moduleVersion, introducedVersion, id);
        }
    }

    private String getModuleVersionFromFile(Path packagePath) {
        Path moduleInfoFile = packagePath.resolve("module-info.java");
        String version = null;
        if (Files.exists(moduleInfoFile)) {
            try {
                byte[] moduleInfoAsBytes = Files.readAllBytes(moduleInfoFile);
                String moduleInfoContent = new String(moduleInfoAsBytes, StandardCharsets.UTF_8);
                version = extractSinceVersionFromText(moduleInfoContent).toString();
            } catch (IOException | NullPointerException e) {
                wrongTagsList.add("module-info.java not found or couldn't be opened AND this module has no unqualified exports\n");
            }
        }
        return version;
    }

    private String getPackageVersionFromFile(Path packagePath, ModuleElement.ExportsDirective ed) {
        Path pkgInfo = packagePath.resolve(ed.getPackage()
                        .getQualifiedName()
                        .toString()
                        .replaceAll("\\.", "/")
                )
                .resolve("package-info.java");

        String packageTopVersion = null;
        if (Files.exists(pkgInfo)) {
            try {
                byte[] packageAsBytes = Files.readAllBytes(pkgInfo);
                String packageContent = new String(packageAsBytes, StandardCharsets.UTF_8);
                packageTopVersion = extractSinceVersionFromText(packageContent).toString();
            } catch (IOException | NullPointerException e) {
                wrongTagsList.add("package-info.java not found or couldn't be opened\n");
            }
        }
        return packageTopVersion;
    }


    private void analyzePackageCheck(PackageElement pe, JavacTask ct, EffectiveSourceSinceHelper javadocHelper) {
        List<TypeElement> typeElements = ElementFilter.typesIn(pe.getEnclosedElements());
        for (TypeElement te : typeElements) {
            analyzeClassCheck(te, null, javadocHelper, ct.getTypes(), ct.getElements());
        }
    }

    private void analyzeClassCheck(TypeElement te, String version, EffectiveSourceSinceHelper javadocHelper,
                                   Types types, Elements elementUtils) {
        String currentjdkVersion = String.valueOf(Runtime.version().feature());
        Set<Modifier> classModifiers = te.getModifiers();
        if (!(classModifiers.contains(Modifier.PUBLIC) || classModifiers.contains(Modifier.PROTECTED))) {
            return;
        }
        checkElement(te.getEnclosingElement(), te, types, javadocHelper, version, elementUtils);
        te.getEnclosedElements().stream().filter(element -> element.getModifiers().contains(Modifier.PUBLIC)
                        || element.getModifiers().contains(Modifier.PROTECTED))
                .filter(element -> element.getKind().isField()
                        || element.getKind() == ElementKind.METHOD
                        || element.getKind() == ElementKind.CONSTRUCTOR)
                .forEach(element -> checkElement(te, element, types, javadocHelper, version, elementUtils));
        te.getEnclosedElements().stream()
                .filter(element -> element.getKind().isDeclaredType())
                .map(TypeElement.class::cast)
                .forEach(nestedClass -> analyzeClassCheck(nestedClass, currentjdkVersion, javadocHelper, types, elementUtils));
    }


    private void checkElement(Element explicitOwner, Element element, Types types,
                              EffectiveSourceSinceHelper javadocHelper, String currentVersion, Elements elementUtils) {
        String uniqueId = getElementName(explicitOwner, element, types);

        if (element.getKind() == ElementKind.METHOD &&
                element.getEnclosingElement().getKind() == ElementKind.ENUM &&
                (uniqueId.contains(".values()") || uniqueId.contains(".valueOf(java.lang.String)"))) {
            //mandated enum type methods
            return;
        }
        String sinceVersion = javadocHelper.effectiveSinceVersion(explicitOwner, element, types, elementUtils).toString();
        IntroducedIn mappedVersion = classDictionary.get(uniqueId);
        String realMappedVersion = null;
        try {
            realMappedVersion = isPreview(element, uniqueId, currentVersion) ?
                    mappedVersion.introducedPreview :
                    mappedVersion.introducedStable;
        } catch (Exception e) {
            wrongTagsList.add("For element " + element + "mappedVersion" + mappedVersion + " is null" + e + "\n");
        }
        checkEquals(sinceVersion, realMappedVersion, uniqueId);
    }

    private Version extractSinceVersionFromText(String documentation) {
        Pattern pattern = Pattern.compile("@since\\s+(\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(documentation);
        if (matcher.find()) {
            String versionString = matcher.group(1);
            try {
                if (versionString.equals("1.0")) {
                    versionString = "1"; //ended up being necessary
                } else if (versionString.startsWith("1.")) {
                    versionString = versionString.substring(2);
                }
                return Version.parse(versionString);
            } catch (NumberFormatException ex) {
                wrongTagsList.add("`@since` value that cannot be parsed: " + versionString + "\n");
                return null;
            }
        } else {
            return null;
        }
    }

    private void checkEquals(String sinceVersion, String mappedVersion, String id) {
        if (sinceVersion == null || mappedVersion == null) {
            wrongTagsList.add("For " + id + " NULL value for either mapped or real `@since` . mapped version is="
                    + mappedVersion + " while the `@since` in the source code is= " + sinceVersion);
            return;
        }
        if (Integer.parseInt(sinceVersion) < 9) {
            sinceVersion = "9";
        }
        if (!sinceVersion.equals(mappedVersion)) {
            String message = getWrongSinceMessage(sinceVersion, mappedVersion, id);
            wrongTagsList.add(message);
        }
    }

    private static String getWrongSinceMessage(String sinceVersion, String mappedVersion, String elementSimpleName) {
        String message;
        if (mappedVersion.equals("9")) {
            message = "For " + elementSimpleName +
                    " Wrong `@since` version " + sinceVersion + " But the element exists before JDK 10\n";
        } else {
            message = "For " + elementSimpleName +
                    " Wrong `@since` version is " + sinceVersion + " instead of " + mappedVersion + "\n";
        }
        return message;
    }

    private static String getElementName(Element owner, Element element, Types types) {
        String prefix = "";
        String suffix = "";
        ElementKind kind = element.getKind();
        if (kind.isField()) {
            TypeElement te = (TypeElement) owner;
            prefix = "field";
            suffix = ": " + te.getQualifiedName() + ":" + element.getSimpleName();
        } else if (kind == ElementKind.METHOD || kind == ElementKind.CONSTRUCTOR) {
            prefix = "method";
            TypeElement te = (TypeElement) owner;
            ExecutableElement executableElement = (ExecutableElement) element;
            String returnType = types.erasure(executableElement.getReturnType()).toString();
            String methodName = executableElement.getSimpleName().toString();
            String descriptor = executableElement.getParameters().stream()
                    .map(p -> types.erasure(p.asType()).toString())
                    .collect(Collectors.joining(",", "(", ")"));
            suffix = ": " + returnType + " " + te.getQualifiedName() + "." + methodName + descriptor;
        } else if (kind.isDeclaredType()) {
            if (kind.isClass()) {
                prefix = "class";
            } else if (kind.isInterface()) {
                prefix = "interface";
            }
            suffix = ": " + ((TypeElement) element).getQualifiedName();
        } else if (kind == ElementKind.PACKAGE) {
            prefix = "package";
            suffix = ": " + ((PackageElement) element).getQualifiedName();
        } else if (kind == ElementKind.MODULE) {
            prefix = "module";
            suffix = ": " + ((ModuleElement) element).getQualifiedName();
        }
        return prefix + suffix;
    }

    public static class IntroducedIn {
        public String introducedPreview;
        public String introducedStable;
    }

    //these were preview in before the introduction of the @PreviewFeature
    {
        LEGACY_PREVIEW_METHODS.put("12", Set.of(
                "method: com.sun.source.tree.ExpressionTree com.sun.source.tree.BreakTree.getValue()",
                "method: java.util.List com.sun.source.tree.CaseTree.getExpressions()",
                "method: com.sun.source.tree.Tree com.sun.source.tree.CaseTree.getBody()",
                "method: com.sun.source.tree.CaseTree.CaseKind com.sun.source.tree.CaseTree.getCaseKind()",
                "class: com.sun.source.tree.CaseTree.CaseKind",
                "field: com.sun.source.tree.Tree.Kind:SWITCH_EXPRESSION",
                "interface: com.sun.source.tree.SwitchExpressionTree",
                "method: java.lang.Object com.sun.source.tree.TreeVisitor.visitSwitchExpression(com.sun.source.tree.SwitchExpressionTree,java.lang.Object)",
                "method: java.lang.Object com.sun.source.util.TreeScanner.visitSwitchExpression(com.sun.source.tree.SwitchExpressionTree,java.lang.Object)",
                "method: java.lang.Object com.sun.source.util.SimpleTreeVisitor.visitSwitchExpression(com.sun.source.tree.SwitchExpressionTree,java.lang.Object)"
        ));

        LEGACY_PREVIEW_METHODS.put("13", Set.of(
                "method: java.util.List com.sun.source.tree.CaseTree.getExpressions()",
                "method: com.sun.source.tree.Tree com.sun.source.tree.CaseTree.getBody()",
                "method: com.sun.source.tree.CaseTree.CaseKind com.sun.source.tree.CaseTree.getCaseKind()",
                "class: com.sun.source.tree.CaseTree.CaseKind",
                "field: com.sun.source.tree.Tree.Kind:SWITCH_EXPRESSION",
                "interface: com.sun.source.tree.SwitchExpressionTree",
                "method: java.lang.Object com.sun.source.tree.TreeVisitor.visitSwitchExpression(com.sun.source.tree.SwitchExpressionTree,java.lang.Object)",
                "method: java.lang.Object com.sun.source.util.TreeScanner.visitSwitchExpression(com.sun.source.tree.SwitchExpressionTree,java.lang.Object)",
                "method: java.lang.Object com.sun.source.util.SimpleTreeVisitor.visitSwitchExpression(com.sun.source.tree.SwitchExpressionTree,java.lang.Object)",
                "method: java.lang.String java.lang.String.stripIndent()",
                "method: java.lang.String java.lang.String.translateEscapes()",
                "method: java.lang.String java.lang.String.formatted(java.lang.Object[])",
                "class: javax.swing.plaf.basic.motif.MotifLookAndFeel",
                "field: com.sun.source.tree.Tree.Kind:YIELD",
                "interface: com.sun.source.tree.YieldTree",
                "method: java.lang.Object com.sun.source.tree.TreeVisitor.visitYield(com.sun.source.tree.YieldTree,java.lang.Object)",
                "method: java.lang.Object com.sun.source.util.SimpleTreeVisitor.visitYield(com.sun.source.tree.YieldTree,java.lang.Object)",
                "method: java.lang.Object com.sun.source.util.TreeScanner.visitYield(com.sun.source.tree.YieldTree,java.lang.Object)"
        ));

        LEGACY_PREVIEW_METHODS.put("14", Set.of(
                "class: javax.swing.plaf.basic.motif.MotifLookAndFeel",
                "method: java.lang.Object com.sun.source.tree.TreeVisitor.visitYield(com.sun.source.tree.YieldTree,java.lang.Object)",
                "method: java.lang.Object com.sun.source.util.SimpleTreeVisitor.visitYield(com.sun.source.tree.YieldTree,java.lang.Object)",
                "method: java.lang.Object com.sun.source.util.TreeScanner.visitYield(com.sun.source.tree.YieldTree,java.lang.Object)",
                "field: jdk.jshell.Snippet.SubKind:RECORD_SUBKIND",
                "class: javax.lang.model.element.RecordComponentElement",
                "method: java.lang.Object javax.lang.model.element.ElementVisitor.visitRecordComponent(javax.lang.model.element.RecordComponentElement,java.lang.Object)",
                "class: javax.lang.model.util.ElementScanner14",
                "class: javax.lang.model.util.AbstractElementVisitor14",
                "class: javax.lang.model.util.SimpleElementVisitor14",
                "method: java.lang.Object javax.lang.model.util.ElementKindVisitor6.visitTypeAsRecord(javax.lang.model.element.TypeElement,java.lang.Object)",
                "class: javax.lang.model.util.ElementKindVisitor14",
                "method: javax.lang.model.element.RecordComponentElement javax.lang.model.util.Elements.recordComponentFor(javax.lang.model.element.ExecutableElement)",
                "method: java.util.List javax.lang.model.util.ElementFilter.recordComponentsIn(java.lang.Iterable)",
                "method: java.util.Set javax.lang.model.util.ElementFilter.recordComponentsIn(java.util.Set)",
                "method: java.util.List javax.lang.model.element.TypeElement.getRecordComponents()",
                "field: javax.lang.model.element.ElementKind:RECORD",
                "field: javax.lang.model.element.ElementKind:RECORD_COMPONENT",
                "field: javax.lang.model.element.ElementKind:BINDING_VARIABLE",
                "field: com.sun.source.tree.Tree.Kind:RECORD",
                "field: sun.reflect.annotation.TypeAnnotation.TypeAnnotationTarget:RECORD_COMPONENT",
                "class: java.lang.reflect.RecordComponent",
                "class: java.lang.runtime.ObjectMethods",
                "field: java.lang.annotation.ElementType:RECORD_COMPONENT",
                "method: boolean java.lang.Class.isRecord()",
                "method: java.lang.reflect.RecordComponent[] java.lang.Class.getRecordComponents()",
                "class: java.lang.Record"
        ));

        LEGACY_PREVIEW_METHODS.put("15", Set.of(
                "field: jdk.jshell.Snippet.SubKind:RECORD_SUBKIND",
                "class: javax.lang.model.element.RecordComponentElement",
                "method: java.lang.Object javax.lang.model.element.ElementVisitor.visitRecordComponent(javax.lang.model.element.RecordComponentElement,java.lang.Object)",
                "class: javax.lang.model.util.ElementScanner14",
                "class: javax.lang.model.util.AbstractElementVisitor14",
                "class: javax.lang.model.util.SimpleElementVisitor14",
                "method: java.lang.Object javax.lang.model.util.ElementKindVisitor6.visitTypeAsRecord(javax.lang.model.element.TypeElement,java.lang.Object)",
                "class: javax.lang.model.util.ElementKindVisitor14",
                "method: javax.lang.model.element.RecordComponentElement javax.lang.model.util.Elements.recordComponentFor(javax.lang.model.element.ExecutableElement)",
                "method: java.util.List javax.lang.model.util.ElementFilter.recordComponentsIn(java.lang.Iterable)",
                "method: java.util.Set javax.lang.model.util.ElementFilter.recordComponentsIn(java.util.Set)",
                "method: java.util.List javax.lang.model.element.TypeElement.getRecordComponents()",
                "field: javax.lang.model.element.ElementKind:RECORD",
                "field: javax.lang.model.element.ElementKind:RECORD_COMPONENT",
                "field: javax.lang.model.element.ElementKind:BINDING_VARIABLE",
                "field: com.sun.source.tree.Tree.Kind:RECORD",
                "field: sun.reflect.annotation.TypeAnnotation.TypeAnnotationTarget:RECORD_COMPONENT",
                "class: java.lang.reflect.RecordComponent",
                "class: java.lang.runtime.ObjectMethods",
                "field: java.lang.annotation.ElementType:RECORD_COMPONENT",
                "class: java.lang.Record",
                "method: boolean java.lang.Class.isRecord()",
                "method: java.lang.reflect.RecordComponent[] java.lang.Class.getRecordComponents()",
                "field: javax.lang.model.element.Modifier:SEALED",
                "field: javax.lang.model.element.Modifier:NON_SEALED",
                "method: javax.lang.model.element.TypeElement:getPermittedSubclasses:()",
                "method: java.util.List com.sun.source.tree.ClassTree.getPermitsClause()",
                "method: boolean java.lang.Class.isSealed()",
                "method: java.lang.constant.ClassDesc[] java.lang.Class.permittedSubclasses()"
        ));

        LEGACY_PREVIEW_METHODS.put("16", Set.of(
                "field: jdk.jshell.Snippet.SubKind:RECORD_SUBKIND",
                "field: javax.lang.model.element.Modifier:SEALED",
                "field: javax.lang.model.element.Modifier:NON_SEALED",
                "method: javax.lang.model.element.TypeElement:getPermittedSubclasses:()",
                "method: java.util.List com.sun.source.tree.ClassTree.getPermitsClause()",
                "method: boolean java.lang.Class.isSealed()",
                "method: java.lang.constant.ClassDesc[] java.lang.Class.permittedSubclasses()"
        ));

        // java.lang.foreign existed since JDK 19 and wasn't annotated - went out of preview in JDK 22
        LEGACY_PREVIEW_METHODS.put("19", Set.of(
                "package: java.lang.foreign"
        ));
        LEGACY_PREVIEW_METHODS.put("20", Set.of(
                "package: java.lang.foreign"
        ));
        LEGACY_PREVIEW_METHODS.put("21", Set.of(
                "package: java.lang.foreign"
        ));
    }

    private final class EffectiveSourceSinceHelper implements AutoCloseable {
        private static final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        private final JavaFileManager baseFileManager;
        private final StandardJavaFileManager fm;
        private final Set<String> seenLookupElements = new HashSet<>();
        private final Map<String, Version> signature2Source = new HashMap<>();

        public static EffectiveSourceSinceHelper create(JavacTask mainTask, Collection<? extends Path> sourceLocations, SinceValidator validator) {
            StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null);
            try {
                fm.setLocationFromPaths(StandardLocation.MODULE_SOURCE_PATH, sourceLocations);
                return validator.new EffectiveSourceSinceHelper(mainTask, fm);
            } catch (IOException ex) {
                try {
                    fm.close();
                } catch (IOException closeEx) {
                }
                throw new UncheckedIOException(ex);
            }
        }

        private EffectiveSourceSinceHelper(JavacTask mainTask, StandardJavaFileManager fm) {
            this.baseFileManager = ((JavacTaskImpl) mainTask).getContext().get(JavaFileManager.class);
            this.fm = fm;
        }

        public Version effectiveSinceVersion(Element owner, Element element, Types typeUtils, Elements elementUtils) {
            String handle = getElementName(owner, element, typeUtils);
            Version since = signature2Source.get(handle);

            if (since == null) {
                try {
                    Element lookupElement = switch (element.getKind()) {
                        case MODULE, PACKAGE -> element;
                        default -> elementUtils.getOutermostTypeElement(element);
                    };

                    if (lookupElement == null)
                        return null;

                    String lookupHandle = getElementName(owner, element, typeUtils);

                    if (!seenLookupElements.add(lookupHandle)) {
                        //we've already processed this top-level, don't try to compute
                        //the values again:
                        return null;
                    }

                    Pair<JavacTask, CompilationUnitTree> source = findSource(lookupElement, elementUtils);

                    if (source == null)
                        return null;

                    fillElementCache(source.fst, source.snd, source.fst.getTypes(), source.fst.getElements());
                    since = signature2Source.get(handle);


                } catch (IOException ex) {
                    wrongTagsList.add("JavadocHelper failed for " + element + "\n");
                }
            }

            return since;
        }

        //where:
        private void fillElementCache(JavacTask task, CompilationUnitTree cut, Types typeUtils, Elements elementUtils) {
            Trees trees = Trees.instance(task);

            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitMethod(MethodTree node, Void p) {
                    handleDeclaration();
                    return null;
                }

                @Override
                public Void visitClass(ClassTree node, Void p) {
                    handleDeclaration();
                    return super.visitClass(node, p);
                }

                @Override
                public Void visitVariable(VariableTree node, Void p) {
                    handleDeclaration();
                    return null;
                }

                @Override
                public Void visitModule(ModuleTree node, Void p) {
                    handleDeclaration();
                    return null;
                }

                @Override
                public Void visitBlock(BlockTree node, Void p) {
                    return null;
                }

                @Override
                public Void visitPackage(PackageTree node, Void p) {
                    if (cut.getSourceFile().isNameCompatible("package-info", JavaFileObject.Kind.SOURCE)) {
                        handleDeclaration();
                    }
                    return super.visitPackage(node, p);
                }

                private void handleDeclaration() {
                    Element currentElement = trees.getElement(getCurrentPath());

                    if (currentElement != null) {
                        signature2Source.put(getElementName(currentElement.getEnclosingElement(), currentElement, typeUtils), computeSinceVersion(currentElement, typeUtils, elementUtils));
                    }
                }
            }.scan(cut, null);
        }

        private Version computeSinceVersion(Element element, Types types,
                                            Elements elementUtils) {
            String docComment = elementUtils.getDocComment(element);
            Version version = null;
            if (docComment != null) {
                version = extractSinceVersionFromText(docComment);
            }

            if (version != null) {
                return version; //explicit @since has an absolute priority
            }

            if (element.getKind() != ElementKind.MODULE) {
                version = effectiveSinceVersion(element.getEnclosingElement().getEnclosingElement(), element.getEnclosingElement(), types, elementUtils);
            }
            if (version == null) {
                //may be null for private elements
                //TODO: can we be more careful here?
            }
            return version;

        }

        private Pair<JavacTask, CompilationUnitTree> findSource(Element forElement, Elements elementUtils) throws IOException {
            String moduleName = elementUtils.getModuleOf(forElement).getQualifiedName().toString();
            String binaryName = switch (forElement.getKind()) {
                case MODULE -> "module-info";
                case PACKAGE -> ((QualifiedNameable) forElement).getQualifiedName() + ".package-info";
                default -> elementUtils.getBinaryName((TypeElement) forElement).toString();
            };
            Location packageLocationForModule = fm.getLocationForModule(StandardLocation.MODULE_SOURCE_PATH, moduleName);
            JavaFileObject jfo = fm.getJavaFileForInput(packageLocationForModule,
                    binaryName,
                    JavaFileObject.Kind.SOURCE);

            if (jfo == null)
                return null;

            List<JavaFileObject> jfos = Arrays.asList(jfo);
            JavaFileManager patchFM = moduleName != null
                    ? new PatchModuleFileManager(baseFileManager, jfo, moduleName)
                    : baseFileManager;
            JavacTaskImpl task = (JavacTaskImpl) compiler.getTask(null, patchFM, d -> {
            }, null, null, jfos);
            Iterable<? extends CompilationUnitTree> cuts = task.parse();

            task.enter();

            return Pair.of(task, cuts.iterator().next());
        }

        @Override
        public void close() throws IOException {
            fm.close();
        }

        private static final class PatchModuleFileManager
                extends ForwardingJavaFileManager<JavaFileManager> {

            private final JavaFileObject file;
            private final String moduleName;

            public PatchModuleFileManager(JavaFileManager fileManager,
                                          JavaFileObject file,
                                          String moduleName) {
                super(fileManager);
                this.file = file;
                this.moduleName = moduleName;
            }

            @Override
            public Location getLocationForModule(Location location,
                                                 JavaFileObject fo) throws IOException {
                return fo == file
                        ? PATCH_LOCATION
                        : super.getLocationForModule(location, fo);
            }

            @Override
            public String inferModuleName(Location location) throws IOException {
                return location == PATCH_LOCATION
                        ? moduleName
                        : super.inferModuleName(location);
            }

            @Override
            public boolean hasLocation(Location location) {
                return location == StandardLocation.PATCH_MODULE_PATH ||
                        super.hasLocation(location);
            }

            private static final Location PATCH_LOCATION = new Location() {
                @Override
                public String getName() {
                    return "PATCH_LOCATION";
                }

                @Override
                public boolean isOutputLocation() {
                    return false;
                }

                @Override
                public boolean isModuleOrientedLocation() {
                    return false;
                }
            };
        }
    }
}