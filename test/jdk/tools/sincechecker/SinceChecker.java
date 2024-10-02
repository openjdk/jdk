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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.Runtime.Version;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.lang.model.element.*;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.*;
import javax.tools.JavaFileManager.Location;
import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.util.Pair;
import jtreg.SkippedException;

/*
This checker checks the values of the `@since` tag found in the documentation comment for an element against
the release in which the element first appeared.
The source code containing the documentation comments is read from `src.zip` in the release of JDK used to run the test.
The releases used to determine the expected value of `@since` tags are taken from the historical data built into `javac`.

The `@since` checker works as a two-step process:
In the first step, we process JDKs 9-current, only classfiles,
  producing a map `<unique-Element-ID`> => `<version(s)-where-it-was-introduced>`.
    - "version(s)", because we handle versioning of Preview API, so there may be two versions
     (we use a class with two fields for preview and stable),
    one when it was introduced as a preview, and one when it went out of preview. More on that below.
    - For each Element, we compute the unique ID, look into the map, and if there's nothing,
     record the current version as the originating version.
    - At the end of this step we have a map of the Real since values

In the second step, we look at "effective" `@since` tags in the mainline sources, from `src.zip`
 (if the test run doesn't have it, we throw a `jtreg.SkippedException`)
    - We only check the specific MODULE whose name was passed as an argument in the test.
      In that module, we look for unqualified exports and test those packages.
    - The `@since` checker verifies that for every API element, the real since value and
      the effective since value are the same, and reports an error if they are not.

Important note : We only check code written since JDK 9 as the releases used to determine the expected value
                 of @since tags are taken from the historical data built into javac which only goes back that far

note on rules for Real and effective `@since:

Real since value of an API element is computed as the oldest release in which the given API element was introduced.
That is:
- for modules, packages, classes and interfaces, the release in which the element with the given qualified name was introduced
- for constructors, the release in which the constructor with the given VM descriptor was introduced
- for methods and fields, the release in which the given method or field with the given VM descriptor became a member
  of its enclosing class or interface, whether direct or inherited

Effective since value of an API element is computed as follows:
- if the given element has a @since tag in its javadoc, it is used
- in all other cases, return the effective since value of the enclosing element


Special Handling for preview method, as per JEP 12:
- When an element is still marked as preview, the `@since` should be the first JDK release where the element was added.
- If the element is no longer marked as preview, the `@since` should be the first JDK release where it was no longer preview.

note on legacy preview: Until JDK 14, the preview APIs were not marked in any machine-understandable way.
                        It was deprecated, and had a comment in the javadoc.
                        and the use of `@PreviewFeature` only became standard in JDK 17.
                        So the checker has an explicit knowledge of these preview elements.

note: The `<unique-Element-ID>` for methods looks like
      `method: <erased-return-descriptor> <binary-name-of-enclosing-class>.<method-name>(<ParameterDescriptor>)`.
it is somewhat inspired from the VM Method Descriptors. But we use the erased return so that methods
that were later generified remain the same.

usage: the checker is run from a module specific test
        `@run main SinceChecker <moduleName> [--exclude package1,package2 | --exclude package1 package2]`
*/

public class SinceChecker {
    private final Map<String, Set<String>> LEGACY_PREVIEW_METHODS = new HashMap<>();
    private final Map<String, IntroducedIn> classDictionary = new HashMap<>();
    private final JavaCompiler tool;
    private int errorCount = 0;

    // packages to skip during the test
    private static final Set<String> EXCLUDE_LIST = new HashSet<>();

    public static class IntroducedIn {
        public String introducedPreview;
        public String introducedStable;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Test module not specified");
        }
        String moduleName = args[0];
        boolean excludeFlag = false;

        for (int i = 1; i < args.length; i++) {
            if ("--exclude".equals(args[i])) {
                excludeFlag = true;
                continue;
            }

            if (excludeFlag) {
                if (args[i].contains(",")) {
                    EXCLUDE_LIST.addAll(Arrays.asList(args[i].split(",")));
                } else {
                    EXCLUDE_LIST.add(args[i]);
                }
            }
        }

        SinceChecker sinceCheckerTestHelper = new SinceChecker(moduleName);
        sinceCheckerTestHelper.checkModule(moduleName);
    }

    private void error(String message) {
        System.err.println(message);
        errorCount++;
    }

    private SinceChecker(String moduleName) throws IOException {
        tool = ToolProvider.getSystemJavaCompiler();
        for (int i = 9; i <= Runtime.version().feature(); i++) {
            DiagnosticListener<? super JavaFileObject> noErrors = d -> {
                if (!d.getCode().equals("compiler.err.module.not.found")) {
                    error(d.getMessage(null));
                }
            };
            JavacTask ct = (JavacTask) tool.getTask(null,
                    null,
                    noErrors,
                    List.of("--add-modules", moduleName, "--release", String.valueOf(i)),
                    null,
                    Collections.singletonList(SimpleJavaFileObject.forSource(URI.create("myfo:/Test.java"), "")));
            ct.analyze();

            String version = String.valueOf(i);
            Elements elements = ct.getElements();
            elements.getModuleElement("java.base"); // forces module graph to be instantiated
            elements.getAllModuleElements().forEach(me ->
                    processModuleElement(me, version, ct));
        }
    }

    private void processModuleElement(ModuleElement moduleElement, String releaseVersion, JavacTask ct) {
        processElement(moduleElement, moduleElement, ct.getTypes(), releaseVersion);
        for (ModuleElement.ExportsDirective ed : ElementFilter.exportsIn(moduleElement.getDirectives())) {
            if (ed.getTargetModules() == null) {
                processPackageElement(ed.getPackage(), releaseVersion, ct);
            }
        }
    }

    private void processPackageElement(PackageElement pe, String releaseVersion, JavacTask ct) {
        processElement(pe, pe, ct.getTypes(), releaseVersion);
        List<TypeElement> typeElements = ElementFilter.typesIn(pe.getEnclosedElements());
        for (TypeElement te : typeElements) {
            processClassElement(te, releaseVersion, ct.getTypes(), ct.getElements());
        }
    }

    /// JDK documentation only contains public and protected declarations
    private boolean isDocumented(Element te) {
        Set<Modifier> mod = te.getModifiers();
        return mod.contains(Modifier.PUBLIC) || mod.contains(Modifier.PROTECTED);
    }

    private boolean isMember(Element e) {
        var kind = e.getKind();
        return kind.isField() || switch (kind) {
            case METHOD, CONSTRUCTOR -> true;
            default -> false;
        };
    }

    private void processClassElement(TypeElement te, String version, Types types, Elements elements) {
        if (!isDocumented(te)) {
            return;
        }
        processElement(te.getEnclosingElement(), te, types, version);
        elements.getAllMembers(te).stream()
                .filter(this::isDocumented)
                .filter(this::isMember)
                .forEach(element -> processElement(te, element, types, version));
        te.getEnclosedElements().stream()
                .filter(element -> element.getKind().isDeclaredType())
                .map(TypeElement.class::cast)
                .forEach(nestedClass -> processClassElement(nestedClass, version, types, elements));
    }

    private void processElement(Element explicitOwner, Element element, Types types, String version) {
        String uniqueId = getElementName(explicitOwner, element, types);
        IntroducedIn introduced = classDictionary.computeIfAbsent(uniqueId, _ -> new IntroducedIn());
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

        return LEGACY_PREVIEW_METHODS.getOrDefault(currentVersion, Set.of())
                .contains(uniqueId);
    }

    private void checkModule(String moduleName) throws Exception {
        Path home = Paths.get(System.getProperty("java.home"));
        Path srcZip = home.resolve("lib").resolve("src.zip");
        if (Files.notExists(srcZip)) {
            //possibly running over an exploded JDK build, attempt to find a
            //co-located full JDK image with src.zip:
            Path testJdk = Paths.get(System.getProperty("test.jdk"));
            srcZip = testJdk.getParent().resolve("images").resolve("jdk").resolve("lib").resolve("src.zip");
        }
        if (!Files.isReadable(srcZip)) {
            throw new SkippedException("Skipping Test because src.zip wasn't found or couldn't be read");
        }
        URI uri = URI.create("jar:" + srcZip.toUri());
        try (FileSystem zipFO = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
            Path root = zipFO.getRootDirectories().iterator().next();
            Path moduleDirectory = root.resolve(moduleName);
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
                    processModuleCheck(elements.getModuleElement(moduleName), ct, moduleDirectory, javadocHelper);
                } catch (Exception e) {
                    e.printStackTrace();
                    error("Initiating javadocHelper Failed " + e);
                }
                if (errorCount > 0) {
                    throw new Exception("The `@since` checker found " + errorCount + " problems");
                }
            }
        }
    }

    private boolean isExcluded(ModuleElement.ExportsDirective ed ){
        return EXCLUDE_LIST.stream().anyMatch(excludePackage ->
            ed.getPackage().toString().equals(excludePackage) ||
            ed.getPackage().toString().startsWith(excludePackage + "."));
    }

    private void processModuleCheck(ModuleElement moduleElement, JavacTask ct, Path moduleDirectory, EffectiveSourceSinceHelper javadocHelper) {
        if (moduleElement == null) {
            error("Module element: was null because `elements.getModuleElement(moduleName)` returns null." +
                    "fixes are needed for this Module");
        }
        String moduleVersion = getModuleVersionFromFile(moduleDirectory);
        checkModuleOrPackage(javadocHelper, moduleVersion, moduleElement, ct, "Module: ");
        for (ModuleElement.ExportsDirective ed : ElementFilter.exportsIn(moduleElement.getDirectives())) {
            if (ed.getTargetModules() == null) {
                String packageVersion = getPackageVersionFromFile(moduleDirectory, ed);
                if (packageVersion != null && !isExcluded(ed)) {
                    checkModuleOrPackage(javadocHelper, packageVersion, ed.getPackage(), ct, "Package: ");
                    analyzePackageCheck(ed.getPackage(), ct, javadocHelper);
                } // Skip the package if packageVersion is null
            }
        }
    }

    private void checkModuleOrPackage(EffectiveSourceSinceHelper javadocHelper, String moduleVersion, Element moduleElement, JavacTask ct, String elementCategory) {
        String id = getElementName(moduleElement, moduleElement, ct.getTypes());
        var elementInfo = classDictionary.get(id);
        if (elementInfo == null) {
            error("Element :" + id + " was not mapped");
            return;
        }
        String version = elementInfo.introducedStable;
        if (moduleVersion == null) {
            error("Unable to retrieve `@since` for " + elementCategory + id);
        } else {
            String position = javadocHelper.getElementPosition(id);
            checkEquals(position, moduleVersion, version, id);
        }
    }

    private String getModuleVersionFromFile(Path moduleDirectory) {
        Path moduleInfoFile = moduleDirectory.resolve("module-info.java");
        String version = null;
        if (Files.exists(moduleInfoFile)) {
            try {
                String moduleInfoContent = Files.readString(moduleInfoFile);
                var extractedVersion = extractSinceVersionFromText(moduleInfoContent);
                if (extractedVersion != null) {
                    version = extractedVersion.toString();
                }
            } catch (IOException e) {
                error("module-info.java not found or couldn't be opened AND this module has no unqualified exports");
            }
        }
        return version;
    }

    private String getPackageVersionFromFile(Path moduleDirectory, ModuleElement.ExportsDirective ed) {
        Path pkgInfo = moduleDirectory.resolve(ed.getPackage()
                        .getQualifiedName()
                        .toString()
                        .replace(".", File.separator)
                )
                .resolve("package-info.java");

        if (!Files.exists(pkgInfo)) {
            return null; // Skip if the file does not exist
        }

        String packageTopVersion = null;
        try {
            String packageContent = Files.readString(pkgInfo);
            var extractedVersion = extractSinceVersionFromText(packageContent);
            if (extractedVersion != null) {
                packageTopVersion = extractedVersion.toString();
            } else {
                error(ed.getPackage().getQualifiedName() + ": package-info.java exists but doesn't contain @since");
            }
        } catch (IOException e) {
            error(ed.getPackage().getQualifiedName() + ": package-info.java couldn't be opened");
        }
        return packageTopVersion;
    }

    private void analyzePackageCheck(PackageElement pe, JavacTask ct, EffectiveSourceSinceHelper javadocHelper) {
        List<TypeElement> typeElements = ElementFilter.typesIn(pe.getEnclosedElements());
        for (TypeElement te : typeElements) {
            analyzeClassCheck(te, null, javadocHelper, ct.getTypes(), ct.getElements());
        }
    }

    private boolean isNotCommonRecordMethod(TypeElement te, Element element, Types types) {
        var isRecord = te.getKind() == ElementKind.RECORD;
        if (!isRecord) {
            return true;
        }
        String uniqueId = getElementName(te, element, types);
        boolean isCommonMethod = uniqueId.endsWith(".toString()") ||
                uniqueId.endsWith(".hashCode()") ||
                uniqueId.endsWith(".equals(java.lang.Object)");
        if (isCommonMethod) {
            return false;
        }
        for (var parameter : te.getEnclosedElements()) {
            if (parameter.getKind() == ElementKind.RECORD_COMPONENT) {
                if (uniqueId.endsWith(String.format("%s.%s()", te.getSimpleName(), parameter.getSimpleName().toString()))) {
                    return false;
                }
            }
        }
        return true;
    }

    private void analyzeClassCheck(TypeElement te, String version, EffectiveSourceSinceHelper javadocHelper,
                                   Types types, Elements elementUtils) {
        String currentjdkVersion = String.valueOf(Runtime.version().feature());
        if (!isDocumented(te)) {
            return;
        }
        checkElement(te.getEnclosingElement(), te, types, javadocHelper, version, elementUtils);
        te.getEnclosedElements().stream().filter(this::isDocumented)
                .filter(this::isMember)
                .filter(element -> isNotCommonRecordMethod(te, element, types))
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
        String sinceVersion = null;
        var effectiveSince = javadocHelper.effectiveSinceVersion(explicitOwner, element, types, elementUtils);
        if (effectiveSince == null) {
            // Skip the element if the java file doesn't exist in src.zip
            return;
        }
        sinceVersion = effectiveSince.toString();
        IntroducedIn mappedVersion = classDictionary.get(uniqueId);
        if (mappedVersion == null) {
            error("Element: " + uniqueId + " was not mapped");
            return;
        }
        String realMappedVersion = null;
        try {
            realMappedVersion = isPreview(element, uniqueId, currentVersion) ?
                    mappedVersion.introducedPreview :
                    mappedVersion.introducedStable;
        } catch (Exception e) {
            error("For element " + element + "mappedVersion" + mappedVersion + " is null " + e);
        }
        String position = javadocHelper.getElementPosition(uniqueId);
        checkEquals(position, sinceVersion, realMappedVersion, uniqueId);
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
                error("`@since` value that cannot be parsed: " + versionString);
                return null;
            }
        } else {
            return null;
        }
    }

    private void checkEquals(String prefix, String sinceVersion, String mappedVersion, String name) {
        if (sinceVersion == null || mappedVersion == null) {
            error(name + ": NULL value for either real or effective `@since` . real/mapped version is="
                    + mappedVersion + " while the `@since` in the source code is= " + sinceVersion);
            return;
        }
        if (Integer.parseInt(sinceVersion) < 9) {
            sinceVersion = "9";
        }
        if (!sinceVersion.equals(mappedVersion)) {
            String message = getWrongSinceMessage(prefix, sinceVersion, mappedVersion, name);
            error(message);
        }
    }
    private static String getWrongSinceMessage(String prefix, String sinceVersion, String mappedVersion, String elementSimpleName) {
        String message;
        if (mappedVersion.equals("9")) {
            message = elementSimpleName + ": `@since` version is " + sinceVersion + " but the element exists before JDK 10";
        } else {
            message = elementSimpleName + ": `@since` version: " + sinceVersion + "; should be: " + mappedVersion;
        }
        return prefix + message;
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

    //these were preview in before the introduction of the @PreviewFeature
    {
        LEGACY_PREVIEW_METHODS.put("9", Set.of(
                "module: jdk.nio.mapmode",
                "module: java.transaction.xa",
                "module: jdk.unsupported.desktop",
                "module: jdk.jpackage",
                "module: java.net.http"
        ));
        LEGACY_PREVIEW_METHODS.put("10", Set.of(
                "module: jdk.nio.mapmode",
                "module: java.transaction.xa",
                "module: java.net.http",
                "module: jdk.unsupported.desktop",
                "module: jdk.jpackage"
        ));
        LEGACY_PREVIEW_METHODS.put("11", Set.of(
                "module: jdk.nio.mapmode",
                "module: jdk.jpackage"
        ));
        LEGACY_PREVIEW_METHODS.put("12", Set.of(
                "module: jdk.nio.mapmode",
                "module: jdk.jpackage",
                "method: com.sun.source.tree.ExpressionTree com.sun.source.tree.BreakTree.getValue()",
                "method: java.util.List com.sun.source.tree.CaseTree.getExpressions()",
                "method: com.sun.source.tree.Tree com.sun.source.tree.CaseTree.getBody()",
                "method: com.sun.source.tree.CaseTree.CaseKind com.sun.source.tree.CaseTree.getCaseKind()",
                "class: com.sun.source.tree.CaseTree.CaseKind",
                "field: com.sun.source.tree.CaseTree.CaseKind:STATEMENT",
                "field: com.sun.source.tree.CaseTree.CaseKind:RULE",
                "field: com.sun.source.tree.Tree.Kind:SWITCH_EXPRESSION",
                "interface: com.sun.source.tree.SwitchExpressionTree",
                "method: com.sun.source.tree.ExpressionTree com.sun.source.tree.SwitchExpressionTree.getExpression()",
                "method: java.util.List com.sun.source.tree.SwitchExpressionTree.getCases()",
                "method: java.lang.Object com.sun.source.tree.TreeVisitor.visitSwitchExpression(com.sun.source.tree.SwitchExpressionTree,java.lang.Object)",
                "method: java.lang.Object com.sun.source.util.TreeScanner.visitSwitchExpression(com.sun.source.tree.SwitchExpressionTree,java.lang.Object)",
                "method: java.lang.Object com.sun.source.util.SimpleTreeVisitor.visitSwitchExpression(com.sun.source.tree.SwitchExpressionTree,java.lang.Object)"
        ));

        LEGACY_PREVIEW_METHODS.put("13", Set.of(
                "module: jdk.nio.mapmode",
                "module: jdk.jpackage",
                "method: java.util.List com.sun.source.tree.CaseTree.getExpressions()",
                "method: com.sun.source.tree.Tree com.sun.source.tree.CaseTree.getBody()",
                "method: com.sun.source.tree.CaseTree.CaseKind com.sun.source.tree.CaseTree.getCaseKind()",
                "class: com.sun.source.tree.CaseTree.CaseKind",
                "field: com.sun.source.tree.CaseTree.CaseKind:STATEMENT",
                "field: com.sun.source.tree.CaseTree.CaseKind:RULE",
                "field: com.sun.source.tree.Tree.Kind:SWITCH_EXPRESSION",
                "interface: com.sun.source.tree.SwitchExpressionTree",
                "method: com.sun.source.tree.ExpressionTree com.sun.source.tree.SwitchExpressionTree.getExpression()",
                "method: java.util.List com.sun.source.tree.SwitchExpressionTree.getCases()",
                "method: java.lang.Object com.sun.source.tree.TreeVisitor.visitSwitchExpression(com.sun.source.tree.SwitchExpressionTree,java.lang.Object)",
                "method: java.lang.Object com.sun.source.util.TreeScanner.visitSwitchExpression(com.sun.source.tree.SwitchExpressionTree,java.lang.Object)",
                "method: java.lang.Object com.sun.source.util.SimpleTreeVisitor.visitSwitchExpression(com.sun.source.tree.SwitchExpressionTree,java.lang.Object)",
                "method: java.lang.String java.lang.String.stripIndent()",
                "method: java.lang.String java.lang.String.translateEscapes()",
                "method: java.lang.String java.lang.String.formatted(java.lang.Object[])",
                "class: javax.swing.plaf.basic.motif.MotifLookAndFeel",
                "field: com.sun.source.tree.Tree.Kind:YIELD",
                "interface: com.sun.source.tree.YieldTree",
                "method: com.sun.source.tree.ExpressionTree com.sun.source.tree.YieldTree.getValue()",
                "method: java.lang.Object com.sun.source.tree.TreeVisitor.visitYield(com.sun.source.tree.YieldTree,java.lang.Object)",
                "method: java.lang.Object com.sun.source.util.SimpleTreeVisitor.visitYield(com.sun.source.tree.YieldTree,java.lang.Object)",
                "method: java.lang.Object com.sun.source.util.TreeScanner.visitYield(com.sun.source.tree.YieldTree,java.lang.Object)"
        ));

        LEGACY_PREVIEW_METHODS.put("14", Set.of(
                "module: jdk.jpackage",
                "class: javax.swing.plaf.basic.motif.MotifLookAndFeel",
                "field: jdk.jshell.Snippet.SubKind:RECORD_SUBKIND",
                "class: javax.lang.model.element.RecordComponentElement",
                "method: javax.lang.model.type.TypeMirror javax.lang.model.element.RecordComponentElement.asType()",
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
                "class: java.lang.Record",
                "interface: com.sun.source.tree.PatternTree",
                "field: com.sun.source.tree.Tree.Kind:BINDING_PATTERN",
                "method: com.sun.source.tree.PatternTree com.sun.source.tree.InstanceOfTree.getPattern()",
                "interface: com.sun.source.tree.BindingPatternTree",
                "method: java.lang.Object com.sun.source.tree.TreeVisitor.visitBindingPattern(com.sun.source.tree.BindingPatternTree,java.lang.Object)"
        ));

        LEGACY_PREVIEW_METHODS.put("15", Set.of(
                "module: jdk.jpackage",
                "field: jdk.jshell.Snippet.SubKind:RECORD_SUBKIND",
                "class: javax.lang.model.element.RecordComponentElement",
                "method: javax.lang.model.type.TypeMirror javax.lang.model.element.RecordComponentElement.asType()",
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
                "method: java.lang.constant.ClassDesc[] java.lang.Class.permittedSubclasses()",
                "interface: com.sun.source.tree.PatternTree",
                "field: com.sun.source.tree.Tree.Kind:BINDING_PATTERN",
                "method: com.sun.source.tree.PatternTree com.sun.source.tree.InstanceOfTree.getPattern()",
                "interface: com.sun.source.tree.BindingPatternTree",
                "method: java.lang.Object com.sun.source.tree.TreeVisitor.visitBindingPattern(com.sun.source.tree.BindingPatternTree,java.lang.Object)"
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

    /**
     * Helper to find javadoc and resolve @inheritDoc and the effective since version.
     */

    private final class EffectiveSourceSinceHelper implements AutoCloseable {
        private static final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        private final JavaFileManager baseFileManager;
        private final StandardJavaFileManager fm;
        private final Set<String> seenLookupElements = new HashSet<>();
        private final Map<String, Version> signature2Source = new HashMap<>();
        private final Map<String, String> signature2Location = new HashMap<>();

        /**
         * Create the helper.
         *
         * @param mainTask JavacTask from which the further Elements originate
         * @param sourceLocations paths where source files should be searched
         * @param validator enclosing class of the helper, typically the object invoking this method
         * @return a EffectiveSourceSinceHelper
         */

        public static EffectiveSourceSinceHelper create(JavacTask mainTask, Collection<? extends Path> sourceLocations, SinceChecker validator) {
            StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null);
            try {
                fm.setLocationFromPaths(StandardLocation.MODULE_SOURCE_PATH, sourceLocations);
                return validator.new EffectiveSourceSinceHelper(mainTask, fm);
            } catch (IOException ex) {
                try {
                    fm.close();
                } catch (IOException closeEx) {
                    ex.addSuppressed(closeEx);
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
                    error("JavadocHelper failed for " + element);
                }
            }

            return since;
        }

        private String getElementPosition(String signature) {
            return signature2Location.getOrDefault(signature, "");
        }

        //where:
        private void fillElementCache(JavacTask task, CompilationUnitTree cut, Types typeUtils, Elements elementUtils) {
            Trees trees = Trees.instance(task);
            String fileName = cut.getSourceFile().getName();

            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitMethod(MethodTree node, Void p) {
                    handleDeclaration(node, fileName);
                    return null;
                }

                @Override
                public Void visitClass(ClassTree node, Void p) {
                    handleDeclaration(node, fileName);
                    return super.visitClass(node, p);
                }

                @Override
                public Void visitVariable(VariableTree node, Void p) {
                    handleDeclaration(node, fileName);
                    return null;
                }

                @Override
                public Void visitModule(ModuleTree node, Void p) {
                    handleDeclaration(node, fileName);
                    return null;
                }

                @Override
                public Void visitBlock(BlockTree node, Void p) {
                    return null;
                }

                @Override
                public Void visitPackage(PackageTree node, Void p) {
                    if (cut.getSourceFile().isNameCompatible("package-info", JavaFileObject.Kind.SOURCE)) {
                        handleDeclaration(node, fileName);
                    }
                    return super.visitPackage(node, p);
                }

                private void handleDeclaration(Tree node, String fileName) {
                    Element currentElement = trees.getElement(getCurrentPath());

                    if (currentElement != null) {
                        long startPosition = trees.getSourcePositions().getStartPosition(cut, node);
                        long lineNumber = cut.getLineMap().getLineNumber(startPosition);
                        String filePathWithLineNumber = String.format("src%s:%s ", fileName, lineNumber);

                        signature2Source.put(getElementName(currentElement.getEnclosingElement(), currentElement, typeUtils), computeSinceVersion(currentElement, typeUtils, elementUtils));
                        signature2Location.put(getElementName(currentElement.getEnclosingElement(), currentElement, typeUtils), filePathWithLineNumber);
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

        /**
         * Manages files within a patch module.
         * Provides custom behavior for handling file locations within a patch module.
         * Includes methods to specify module locations, infer module names and determine
         * if a location belongs to the patch module path.
         */
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
