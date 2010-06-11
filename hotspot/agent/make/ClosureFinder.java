/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import java.io.*;
import java.util.*;


/**
<p> This class finds transitive closure of dependencies from a given
root set of classes. If your project has lots of .class files and you
want to ship only those .class files which are used (transitively)
from a root set of classes, then you can use this utility.  </p> <p>
How does it work?</p>

<p> We walk through all constant pool entries of a given class and
find all modified UTF-8 entries. Anything that looks like a class name is
considered as a class and we search for that class in the given
classpath. If we find a .class of that name, then we add that class to
list.</p>

<p> We could have used CONSTANT_ClassInfo type constants only. But
that will miss classes used through Class.forName or xyz.class
construct.  But, if you refer to a class name in some other string we
would include it as dependency :(. But this is quite unlikely
anyway. To look for exact Class.forName argument(s) would involve
bytecode analysis. Also, we handle only simple reflection. If you
accept name of a class from externally (for eg properties file or
command line args for example, this utility will not be able to find
that dependency. In such cases, include those classes in the root set.
</p>
*/

public class ClosureFinder {
    private Collection roots;            // root class names Collection<String>
    private Map        visitedClasses;   // set of all dependencies as a Map
    private String     classPath;        // classpath to look for .class files
    private String[]   pathComponents;   // classpath components
    private static final boolean isWindows = File.separatorChar != '/';

    public ClosureFinder(Collection roots, String classPath) {
        this.roots = roots;
        this.classPath = classPath;
        parseClassPath();
    }

    // parse classPath into pathComponents array
    private void parseClassPath() {
        List paths = new ArrayList();
        StringTokenizer st = new StringTokenizer(classPath, File.pathSeparator);
        while (st.hasMoreTokens())
            paths.add(st.nextToken());

        Object[] arr = paths.toArray();
        pathComponents = new String[arr.length];
        System.arraycopy(arr, 0, pathComponents, 0, arr.length);
    }

    // if output is aleady not computed, compute it now
    // result is a map from class file name to base path where the .class was found
    public Map find() {
        if (visitedClasses == null) {
            visitedClasses = new HashMap();
            computeClosure();
        }
        return visitedClasses;
    }

    // compute closure for all given root classes
    private void computeClosure() {
        for (Iterator rootsItr = roots.iterator(); rootsItr.hasNext();) {
            String name = (String) rootsItr.next();
            name = name.substring(0, name.indexOf(".class"));
            computeClosure(name);
        }
    }


    // looks up for .class in pathComponents and returns
    // base path if found, else returns null
    private String lookupClassFile(String classNameAsPath) {
        for (int i = 0; i < pathComponents.length; i++) {
            File f =  new File(pathComponents[i] + File.separator +
                               classNameAsPath + ".class");
            if (f.exists()) {
                if (isWindows) {
                    String name = f.getName();
                    // Windows reports special devices AUX,NUL,CON as files
                    // under any directory. It does not care about file extention :-(
                    if (name.compareToIgnoreCase("AUX.class") == 0 ||
                        name.compareToIgnoreCase("NUL.class") == 0 ||
                        name.compareToIgnoreCase("CON.class") == 0) {
                        return null;
                    }
                }
                return pathComponents[i];
            }
        }
        return null;
    }


    // from JVM spec. 2'nd edition section 4.4
    private static final int CONSTANT_Class = 7;
    private static final int CONSTANT_FieldRef = 9;
    private static final int CONSTANT_MethodRef = 10;
    private static final int CONSTANT_InterfaceMethodRef = 11;
    private static final int CONSTANT_String = 8;
    private static final int CONSTANT_Integer = 3;
    private static final int CONSTANT_Float = 4;
    private static final int CONSTANT_Long = 5;
    private static final int CONSTANT_Double = 6;
    private static final int CONSTANT_NameAndType = 12;
    private static final int CONSTANT_Utf8 = 1;

    // whether a given string may be a class name?
    private boolean mayBeClassName(String internalClassName) {
        int len = internalClassName.length();
        for (int s = 0; s < len; s++) {
            char c = internalClassName.charAt(s);
            if (!Character.isJavaIdentifierPart(c) && c != '/')
                return false;
        }
        return true;
    }

    // compute closure for a given class
    private void computeClosure(String className) {
        if (visitedClasses.get(className) != null) return;
        String basePath = lookupClassFile(className);
        if (basePath != null) {
            visitedClasses.put(className, basePath);
            try {
                File classFile = new File(basePath + File.separator + className + ".class");
                FileInputStream fis = new FileInputStream(classFile);
                DataInputStream dis = new DataInputStream(fis);
                // look for .class signature
                if (dis.readInt() != 0xcafebabe) {
                    System.err.println(classFile.getAbsolutePath() + " is not a valid .class file");
                    return;
                }

                // ignore major and minor version numbers
                dis.readShort();
                dis.readShort();

                // read number of constant pool constants
                int numConsts = (int) dis.readShort();
                String[] strings = new String[numConsts];

                // zero'th entry is unused
                for (int cpIndex = 1; cpIndex < numConsts; cpIndex++) {
                    int constType = (int) dis.readByte();
                    switch (constType) {
                    case CONSTANT_Class:
                    case CONSTANT_String:
                        dis.readShort(); // string name index;
                        break;

                    case CONSTANT_FieldRef:
                    case CONSTANT_MethodRef:
                    case CONSTANT_InterfaceMethodRef:
                    case CONSTANT_NameAndType:
                    case CONSTANT_Integer:
                    case CONSTANT_Float:
                        // all these are 4 byte constants
                        dis.readInt();
                        break;

                    case CONSTANT_Long:
                    case CONSTANT_Double:
                        // 8 byte constants
                        dis.readLong();
                        // occupies 2 cp entries
                        cpIndex++;
                        break;


                    case CONSTANT_Utf8: {
                        strings[cpIndex] = dis.readUTF();
                        break;
                    }

                    default:
                        System.err.println("invalid constant pool entry");
                        return;
                    }
                }

            // now walk thru the string constants and look for class names
            for (int s = 0; s < numConsts; s++) {
                if (strings[s] != null && mayBeClassName(strings[s]))
                    computeClosure(strings[s].replace('/', File.separatorChar));
            }

            } catch (IOException exp) {
                // ignore for now
            }

        }
    }

    // a sample main that accepts roots classes in a file and classpath as args
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: ClosureFinder <root class file> <class path>");
            System.exit(1);
        }

        List roots = new ArrayList();
        try {
            FileInputStream fis = new FileInputStream(args[0]);
            DataInputStream dis = new DataInputStream(fis);
            String line = null;
            while ((line = dis.readLine()) != null) {
                if (isWindows) {
                    line = line.replace('/', File.separatorChar);
                }
                roots.add(line);
            }
        } catch (IOException exp) {
            System.err.println(exp.getMessage());
            System.exit(2);
        }

        ClosureFinder cf = new ClosureFinder(roots, args[1]);
        Map out = cf.find();
        Iterator res = out.keySet().iterator();
        for(; res.hasNext(); ) {
            String className = (String) res.next();
            System.out.println(className + ".class");
        }
    }
}
