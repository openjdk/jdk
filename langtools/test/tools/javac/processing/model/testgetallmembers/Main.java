/*
 * Copyright (c) 2006, 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug     6374357 6308351 6707027
 * @summary PackageElement.getEnclosedElements() throws ClassReader$BadClassFileException
 * @author  Peter von der Ah\u00e9
 * @run main/othervm -Xmx256m Main
 */

import java.io.File;
import java.util.*;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.*;
import com.sun.source.util.JavacTask;

import static javax.tools.StandardLocation.CLASS_PATH;
import static javax.tools.StandardLocation.PLATFORM_CLASS_PATH;
import static javax.tools.JavaFileObject.Kind.CLASS;


public class Main {

    public static PackageElement getPackage(TypeElement type) {
        Element owner = type;
        while (owner.getKind() != ElementKind.PACKAGE)
            owner = owner.getEnclosingElement();
        return (PackageElement)owner;
    }

    static int progress = 0;
    static JavaCompiler tool;
    static JavacTask javac;
    static Elements elements;

    public static void main(String[] args) throws Exception {
        if (haveAltRt()) {
            System.out.println("Warning: alt-rt.jar detected, test skipped");
            return;
        }
        JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fm = tool.getStandardFileManager(null, null, null);
        fm.setLocation(CLASS_PATH, Collections.<File>emptyList());
        JavacTask javac = (JavacTask)tool.getTask(null, fm, null, null, null, null);
        Elements elements = javac.getElements();

        final Set<String> packages = new LinkedHashSet<String>();

        int nestedClasses = 0;
        int classes = 0;

        for (JavaFileObject file : fm.list(PLATFORM_CLASS_PATH, "", EnumSet.of(CLASS), true)) {
            String type = fm.inferBinaryName(PLATFORM_CLASS_PATH, file);
            if (type.endsWith("package-info"))
                continue;
            try {
                TypeElement elem = elements.getTypeElement(type);
                if (elem == null && type.indexOf('$') > 0) {
                    nestedClasses++;
                    type = null;
                    continue;
                }
                classes++;
                packages.add(getPackage(elem).getQualifiedName().toString());
                elements.getTypeElement(type).getKind(); // force completion
                type = null;
            } finally {
                if (type != null)
                    System.err.println("Looking at " + type);
            }
        }
        javac = null;
        elements = null;

        javac = (JavacTask)tool.getTask(null, fm, null, null, null, null);
        elements = javac.getElements();

        for (String name : packages) {
            PackageElement pe = elements.getPackageElement(name);
            for (Element e : pe.getEnclosedElements()) {
                e.getSimpleName().getClass();
            }
        }
        /*
         * A few sanity checks based on current values:
         *
         * packages: 775, classes: 12429 + 5917
         *
         * As the platform evolves the numbers are likely to grow
         * monotonically but in case somebody gets a clever idea for
         * limiting the number of packages exposed, this number might
         * drop.  So we test low values.
         */
        System.out.format("packages: %s, classes: %s + %s%n",
                          packages.size(), classes, nestedClasses);
        if (classes < 9000)
            throw new AssertionError("Too few classes in PLATFORM_CLASS_PATH ;-)");
        if (packages.size() < 530)
            throw new AssertionError("Too few packages in PLATFORM_CLASS_PATH ;-)");
        if (nestedClasses < 3000)
            throw new AssertionError("Too few nested classes in PLATFORM_CLASS_PATH ;-)");
    }
    /*
     * If -XX:+AggressiveOpts has been used to test, the option currently
     * instructs the VM to prepend alt-rt.jar onto the bootclasspath. This
     * overrides the default TreeMap implemation in rt.jar causing symbol
     * resolution problems (caused by inconsistent inner class), although
     * alt-rt.jar is being eliminated, we have this sanity check to detect this
     * case and skip the test.
     */
    static boolean haveAltRt() {
        String bootClassPath = System.getProperty("sun.boot.class.path");
        for (String cp : bootClassPath.split(File.pathSeparator)) {
            if (cp.endsWith("alt-rt.jar")) {
                System.err.println("Warning: detected alt-rt.jar in "
                        + "sun.boot.class.path");
                return true;
            }
        }
        return false;
    }
}
