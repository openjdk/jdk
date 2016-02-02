/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * A utility used to invoke and test the javadoc tool.
 *
 * @author Scott Seligman
 */


import java.io.*;
import java.util.*;

import javax.lang.model.SourceVersion;


public class Tester {

    protected final String TEST_SRC = System.getProperty("test.src", ".");
    protected final String TEST_CLASSES = System.getProperty("test.classes",
                                                             ".");
    private final String DEFAULT_ARGS[] = {
        "-sourcepath", TEST_SRC,
    };

    private final File outputFile = new File(TEST_CLASSES, "testrun.out");
    private final File expectedOutputFile = new File(TEST_SRC, "expected.out");

    private String docletName;
    private String[] args;
    private Writer out = null;


    /*
     * Individual tests can extend this to create generics-aware doclets.
     */
    public abstract class Doclet extends jdk.javadoc.doclet.Doclet {

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return super.getSourceVersion();
        }

    }


    public Tester(String docletName) {
        this(docletName, new String[0]);
    }

    public Tester(String docletName, String... additionalArgs) {
        this.docletName = docletName;
        List<String> args = new ArrayList<>();
        args.addAll(Arrays.asList(DEFAULT_ARGS));
        args.addAll(Arrays.asList(additionalArgs));

        try {
            out = new BufferedWriter(new FileWriter(outputFile));
        } catch (IOException e) {
            throw new Error("Could not open output file " + outputFile);
        }
    }

    public void run() throws IOException {
        try {
            if (Main.execute("javadoc", docletName, getClass().getClassLoader(), args) != 0) {
                throw new Error("Javadoc errors encountered.");
            }
            System.out.println("--> Output written to " + outputFile);
        } finally {
            out.close();
        }
    }

    /*
     * Compare output of test run to expected output.
     * Throw an Error if they don't match.
     */
    public void verify() throws IOException {
        BufferedReader thisRun =
            new BufferedReader(new FileReader(outputFile));
        BufferedReader expected =
            new BufferedReader(new FileReader(expectedOutputFile));

        for (int lineNum = 1; true; lineNum++) {
            String line1 = thisRun.readLine();
            String line2 = expected.readLine();
            if (line1 == null && line2 == null) {
                return;         // EOF with all lines matching
            }
            if (line1 == null || !line1.equals(line2)) {
                throw new Error(outputFile + ":" + lineNum +
                                ": output doesn't match");
            }
        }
    }


    public void println(Object o) throws IOException {
        prln(0, o);
    }

    public void println() throws IOException {
        prln();
    }
//
//    public void printPackage(PackageDoc p) throws IOException {
//        prPackage(0, p);
//    }
//
//    public void printClass(ClassDoc cd) throws IOException {
//        if (cd.isAnnotationType())
//            printAnnotationType((AnnotationTypeDoc)cd);
//        else
//            prClass(0, cd);
//    }
//
//    public void printAnnotationType(AnnotationTypeDoc at) throws IOException {
//        prAnnotationType(0, at);
//    }
//
//    public void printField(FieldDoc f) throws IOException {
//        prField(0, f);
//    }
//
//    public void printParameter(Parameter p) throws IOException {
//        prParameter(0, p);
//    }
//
//    public void printMethod(MethodDoc m) throws IOException {
//        prln(0, "method " + m);
//        prMethod(0, m);
//    }
//
//    public void printAnnotationTypeElement(AnnotationTypeElementDoc e)
//                                                        throws IOException {
//        prln(0, "element " + e);
//        prMethod(0, e);
//    }
//
//    public void printConstructor(ConstructorDoc c) throws IOException {
//        prln(0, "constructor " + c);
//        prExecutable(0, c);
//    }
//
//
//    private void prPackage(int off, PackageDoc p) throws IOException {
//        prln(off, "package " + p);
//        prAnnotations(off + 2, p.annotations());
//    }
//
//    private void prClass(int off, ClassDoc cd) throws IOException {
//        prln(off,
//             (cd.isInterface() ? "interface" : cd.isEnum() ? "enum" : "class")
//             + " " + cd);
//        prln(off + 2, "name: " + cd.simpleTypeName() + " / " +
//             cd.typeName() + " / " + cd.qualifiedTypeName());
//        prAnnotations(off + 2, cd.annotations());
//        prLabel(off + 2, "type parameters");
//        for (Type t : cd.typeParameters())
//            prln(off + 4, t);
//        prParamTags(off + 2, cd.typeParamTags());
//        prLabel(off + 2, "nested in");
//        prln(off + 4, cd.containingClass());
//        prLabel(off + 2, "superclass");
//        prln(off + 4, cd.superclassType());
//        prLabel(off + 2, "interfaces");
//        Type[] ts = cd.interfaceTypes();
//        Arrays.sort(ts);
//        for (Type t : ts)
//            prln(off + 4, t);
//        prLabel(off + 2, "enum constants");
//        for (FieldDoc f : cd.enumConstants())
//            prln(off + 4, f.name());
//        prLabel(off + 2, "fields");
//        for (FieldDoc f : cd.fields())
//            prln(off + 4, f.type() + " " + f.name());
//        prLabel(off + 2, "constructors");
//        for (ConstructorDoc c : cd.constructors())
//            prln(off + 4, c.name() + c.flatSignature());
//        prLabel(off + 2, "methods");
//        for (MethodDoc m : cd.methods())
//            prln(off + 4, typeUseString(m.returnType()) + " " +
//                          m.name() + m.flatSignature());
//    }
//
//    private void prAnnotationType(int off, AnnotationTypeDoc at)
//                                                        throws IOException {
//        prln(off, "@interface " + at);
//        prAnnotations(off + 2, at.annotations());
//        prLabel(off + 2, "elements");
//        for (AnnotationTypeElementDoc e : at.elements()) {
//            String def = (e.defaultValue() == null)
//                                ? ""
//                                : " default " + e.defaultValue();
//            prln(off + 4, typeUseString(e.returnType()) + " " + e.name() +
//                          e.flatSignature() + def);
//        }
//    }
//
//    private void prField(int off, FieldDoc f) throws IOException {
//        prln(off, "field " + typeUseString(f.type()) + " " + f.name());
//        prAnnotations(off + 2, f.annotations());
//    }
//
//    private void prParameter(int off, Parameter p) throws IOException {
//        prln(off, "parameter " + p);
//        prAnnotations(off + 2, p.annotations());
//    }
//
//    private void prMethod(int off, MethodDoc m) throws IOException {
//        prExecutable(off, m);
//        prLabel(off + 2, "returns");
//        prln(off + 4, typeUseString(m.returnType()));
//        prLabel(off + 2, "overridden type");
//        prln(off + 4, m.overriddenType());
//    }
//
//    private void prExecutable(int off, ExecutableMemberDoc m)
//                                                        throws IOException {
//        if (!m.isAnnotationTypeElement()) {
//            prln(off + 2, "signature: " + m.flatSignature());
//            prln(off + 2, "           " + m.signature());
//        }
//        prAnnotations(off + 2, m.annotations());
//        prParamTags(off + 2, m.typeParamTags());
//        prParamTags(off + 2, m.paramTags());
//        prLabel(off + 2, "type parameters");
//        for (Type t : m.typeParameters())
//            prln(off + 4, t);
//        prLabel(off + 2, "throws");
//        Type[] ts = m.thrownExceptionTypes();
//        Arrays.sort(ts);
//        for (Type t : ts)
//            prln(off + 4, t);
//    }
//
//    private void prAnnotations(int off, AnnotationDesc[] as)
//                                                        throws IOException {
//        prLabel(off, "annotations");
//        for (AnnotationDesc a : as)
//            prln(off + 2, a.toString());
//    }
//
//    private void prParamTags(int off, ParamTag tags[]) throws IOException {
//        for (ParamTag tag : tags)
//            prParamTag(off, tag);
//    }
//
//    private void prParamTag(int off, ParamTag tag) throws IOException {
//        String name = tag.parameterName();
//        if (tag.isTypeParameter()) name = "<" + name + ">";
//        prln(off, "@param " + name + " " + tag.parameterComment());
//    }
//
//
//    private String typeUseString(Type t) {
//        return (t instanceof ClassDoc || t instanceof TypeVariable)
//                ? t.typeName()
//                : t.toString();
//    }


    // Labels queued for possible printing.  Innermost is first in list.
    List<Line> labels = new ArrayList<Line>();

    // Print label if its section is nonempty.
    void prLabel(int off, String s) {
        while (!labels.isEmpty() && labels.get(0).off >= off)
            labels.remove(0);
        labels.add(0, new Line(off, s));
    }

    // Print queued labels with offsets less than "off".
    void popLabels(int off) throws IOException {
        while (!labels.isEmpty()) {
            Line label = labels.remove(0);
            if (label.off < off)
                prln(label.off, label.o + ":");
        }
    }

    // Print "o" at given offset.
    void pr(int off, Object o) throws IOException {
        popLabels(off);
        for (int i = 0; i < off; i++)
            out.write(' ');
        if (o != null)
            out.write(o.toString());
    }

    // Print "o" (if non-null) at given offset, then newline.
    void prln(int off, Object o) throws IOException {
        if (o != null) {
            pr(off, o);
            prln();
        }
    }

    // Print newline.
    void prln() throws IOException {
        out.write('\n');        // don't want platform-dependent separator
    }


    static class Line {
        int off;
        Object o;
        Line(int off, Object o) { this.off = off; this.o = o; }
    }
}
