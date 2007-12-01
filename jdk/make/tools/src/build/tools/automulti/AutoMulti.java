/*
 * Copyright 1998-2001 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package build.tools.automulti;

import java.lang.reflect.*;
import java.util.*;
import java.io.*;

/**
 * Automatically generates the Multiplexing UI classes
 * for Swing.
 * <p>
 * To use, type 'java AutoMulti <plafdir> <prefix>' where <plafdir>
 * is the directory containing the source for Swing's UI classes and
 * <prefix> is the package prefix to use before ".swing.plaf.multi".
 * For example:
 *
 * <pre>
 * cd TEST
 * ../../../../build/solaris-sparc/bin/java AutoMulti ../../../../src/share/classes/javax/swing/plaf javax
 * </pre>
 *
 * AutoMulti will scour the plaf directory for *UI.java files and
 * generate Multi*UI.java files that do the multiplexing thing.
 * <p>
 * NOTE:  This tool depends upon the existence of <plafdir> and on the
 * compiled classes from <plafdir> being somewhere in the class path.
 *
 * @author Willie Walker
 */
public class AutoMulti {
    static String importLines;

    /**
     * A silly list of parameter names to use.  Skips "i" because we use
     * it as a 'for' loop counter.  If you want to get fancy, please feel
     * to change how parameter names are obtained.  This will break if
     * someone decides to create a UI method that takes more than 8
     * parameters.  Which one is a bug (this breaking or having a method
     * with more than eight parameters) is a subjective thing.
     */
    public static String[] paramNames = {"a","b","c","d","e","f","g","h"};

    /**
     * Removes the package names (e.g., javax.swing) from the name.
     */
    public static String unqualifyName(String name) {
        StringTokenizer parser = new StringTokenizer(name,".");
        String unqualifiedName = null;
        while (parser.hasMoreTokens()) {
            unqualifiedName = parser.nextToken();
        }
        return removeDollars(unqualifiedName);
    }

    /**
     * Strips the extension from the filename.
     */
    public static String stripExtension(String name) {
        StringTokenizer parser = new StringTokenizer(name,".");
        return parser.nextToken();
    }

    /**
     * Adds some spaces.
     */
    public static void indent(StringBuffer s, int i) {
        while (i > 0) {
            s.append(" ");
            i--;
        }
    }

    /**
     * Spits out all the beginning stuff.
     */
    public static StringBuffer createPreamble(String prefixName) {
        StringBuffer s = new StringBuffer();
        s.append("/*\n");
        s.append(" *\n");
        s.append(" * Copyright 1997-2007 Sun Microsystems, Inc. All Rights Reserved.\n");
        s.append(" * \n");
        s.append(" * This software is the proprietary information of Sun Microsystems, Inc.  \n");
        s.append(" * Use is subject to license terms.\n");
        s.append(" * \n");
        s.append(" */\n");
        s.append("package " + prefixName + ".swing.plaf.multi;\n");
        s.append("\n");
        return s;
    }

    /**
     * Replaces 'Xxx$Yyy' with "Xxx'.  Used by addImport because you
     * can't import nested classes directly.
     */
    public static String removeNestedClassName(String s) {
        int dollarPosition = s.indexOf('$');

        if (dollarPosition >= 0) {    // s contains '$'
            StringBuffer sb = new StringBuffer(s);
            sb.setLength(dollarPosition);
            return sb.toString();
        } else {                      // no '$'
            return s;
        }
    }

    /**
     * Replaces '$' with ".'.  Needed for printing inner class names
     * for argument and return types.
     */
    public static String removeDollars(String s) {
        int dollarPosition = s.indexOf('$');

        if (dollarPosition >= 0) {    // s contains '$'
            StringBuffer sb = new StringBuffer(s);
            while (dollarPosition >= 0) {
                //XXX: will there ever be more than one '$'?
                sb.replace(dollarPosition, dollarPosition+1, ".");
                dollarPosition = sb.indexOf("$", dollarPosition);
            }
            return sb.toString();
        } else {                     // no $
            return s;
        }
    }

    /**
     * Adds an import line to the String.
     */
    public static void addImport(String s, Class theClass) {
        if (!theClass.isPrimitive() && (theClass != Object.class)) {
            String className = removeNestedClassName(theClass.getName());
            String importLine = new String("import " + className + ";\n");
            if (importLines.indexOf(importLine) == -1) {
                importLines += importLine;
            }
        }
    }

    /**
     * Spits out the class header information.
     */
    public static void addHeader(StringBuffer s, String className) {
        s.append("/**\n");
        s.append(" * A multiplexing UI used to combine <code>" + className + "</code>s.\n");
        s.append(" * \n");
        s.append(" * <p>This file was automatically generated by AutoMulti.\n");
        s.append(" *\n");
        s.append(" * @author  Otto Multey\n");                  // Get it?  I crack myself up.
        s.append(" */\n");
        s.append("public class Multi" + className + " extends " + className + " {\n");
        s.append("\n");
        s.append("    /**\n");
        s.append("     * The vector containing the real UIs.  This is populated \n");
        s.append("     * in the call to <code>createUI</code>, and can be obtained by calling\n");
        s.append("     * the <code>getUIs</code> method.  The first element is guaranteed to be the real UI \n");
        s.append("     * obtained from the default look and feel.\n");
        s.append("     */\n");
        s.append("    protected Vector uis = new Vector();\n");
        s.append("\n");
        s.append("////////////////////\n");
        s.append("// Common UI methods\n");
        s.append("////////////////////\n");
        s.append("\n");
        s.append("    /**\n");
        s.append("     * Returns the list of UIs associated with this multiplexing UI.  This \n");
        s.append("     * allows processing of the UIs by an application aware of multiplexing \n");
        s.append("     * UIs on components.\n");
        s.append("     */\n");
        s.append("    public ComponentUI[] getUIs() {\n");
        s.append("        return MultiLookAndFeel.uisToArray(uis);\n");
        s.append("    }\n");
    }

    /**
     * Prints out the code for a method.  This is pretty specific to the
     * Multiplexing UI code, so don't get any fancy ideas.
     */
    public static void addMethod(StringBuffer s, Method m, String origName, String className) {

        // Get the method name and the return type.  Be a little careful about arrays.
        //
        String methodName = unqualifyName(m.getName());
        String returnType;
        if (!m.getReturnType().isArray()) {
            returnType = unqualifyName(m.getReturnType().toString());
            addImport(importLines,m.getReturnType());
        } else {
            returnType = unqualifyName(m.getReturnType().getComponentType().toString())
                         + "[]";
            addImport(importLines,m.getReturnType().getComponentType());
        }

        // Print the javadoc
        //
        s.append("\n");
        if (methodName.equals("createUI")) {
            s.append("    /**\n");
            s.append("     * Returns a multiplexing UI instance if any of the auxiliary\n");
            s.append("     * <code>LookAndFeel</code>s supports this UI.  Otherwise, just returns the \n");
            s.append("     * UI object obtained from the default <code>LookAndFeel</code>.\n");
            s.append("     */\n");
        } else if (!returnType.equals("void")) {
            s.append("    /**\n");
            s.append("     * Invokes the <code>" + methodName + "</code> method on each UI handled by this object.\n");
            s.append("     * \n");
            s.append("     * @return the value obtained from the first UI, which is\n");
            s.append("     * the UI obtained from the default <code>LookAndFeel</code>\n");
            s.append("     */\n");
        } else {
            s.append("    /**\n");
            s.append("     * Invokes the <code>" + methodName
                     + "</code> method on each UI handled by this object.\n");
            s.append("     */\n");
        }

        // Print the method signature
        //
        s.append("    public");
        if (Modifier.isStatic(m.getModifiers())) {
            s.append(" static");
        }
        s.append(" " + returnType);
        s.append(" " + methodName);
        s.append("(");

        Class[] params = m.getParameterTypes();
        Class temp;
        String braces;
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                s.append(", ");
            }
            temp = params[i];
            braces = new String("");
            while (temp.isArray()) {
                braces += "[]";
                temp = temp.getComponentType();
            }
            s.append(unqualifyName(temp.getName()) + braces + " " + paramNames[i]);
            addImport(importLines,temp);
        }
        s.append(")");

        // Don't forget about exceptions
        //
        Class exceptions[] = m.getExceptionTypes();
        String throwsString = new String("");

        if (exceptions.length > 0) {
            s.append("\n");
            indent(s,12);
            s.append("throws ");
            for (int i = 0; i < exceptions.length; i++) {
                if (i > 0) {
                    s.append(", ");
                }
                s.append(unqualifyName(exceptions[i].getName()));
                addImport(importLines,exceptions[i]);
            }
        }
        s.append(throwsString + " {\n");

        // Now print out the contents of the method.  We do a special thing
        // for the createUI method, another thing if the method returns 'void'
        // and a third thing if we don't do either of the first two.  If
        // you want to squash this down, feel free.
        //
        if (methodName.equals("createUI")) {
            indent(s,8);
            s.append("ComponentUI mui = new Multi" + origName + "();\n");
            indent(s,8);
            s.append("return MultiLookAndFeel.createUIs(mui,\n");
            indent(s,42);
            s.append("((Multi" + origName +") mui).uis,\n");
            indent(s,42);
            for (int i = 0; i < params.length; i++) {
                if (i > 0) {
                    s.append(",");
                }
                s.append(paramNames[i]);
            }
            s.append(");\n");
        } else if (!returnType.equals("void")) {
            indent(s,8);
            s.append(returnType + " returnValue = \n");
            indent(s,12);
            s.append("((" + className + ") (uis.elementAt(0)))."
                     + methodName + "(");
            for (int i = 0; i < params.length; i++) {
                if (i > 0) {
                    s.append(",");
                }
                s.append(paramNames[i]);
            }
            s.append(");\n");
            indent(s,8);
            s.append("for (int i = 1; i < uis.size(); i++) {\n");
            indent(s,12);
            s.append("((" + className + ") (uis.elementAt(i)))."
                     + methodName + "(");
            for (int i = 0; i < params.length; i++) {
                if (i > 0) {
                    s.append(",");
                }
                s.append(paramNames[i]);
            }
            s.append(");\n");
            indent(s,8);
            s.append("}\n");
            indent(s,8);
            s.append("return returnValue;\n");
        } else {
            indent(s,8);
            s.append("for (int i = 0; i < uis.size(); i++) {\n");
            indent(s,12);
            s.append("((" + className + ") (uis.elementAt(i)))."
                     + methodName + "(");
            for (int i = 0; i < params.length; i++) {
                if (i > 0) {
                    s.append(",");
                }
                s.append(paramNames[i]);
            }
            s.append(");\n");
            indent(s,8);
            s.append("}\n");
        }
        indent(s,4);
        s.append("}\n");
    }

    /**
     * Takes a plaf class name (e.g., "MenuUI") and generates the corresponding
     * Multiplexing UI Java source code (e.g., "MultiMenuUI.java").
     */
    public static void generateFile(String prefixName, String className) {
        try {
            FileOutputStream fos;
            PrintWriter outFile;

            importLines = new String();
            importLines += new String("import java.util.Vector;\n");

            StringBuffer body = new StringBuffer();
            Class wee = Class.forName(prefixName + ".swing.plaf." + className);
            String weeName = unqualifyName(wee.getName());
            addImport(importLines,wee);
            while (!weeName.equals("Object")) {
                body.append("\n");
                body.append("////////////////////\n");
                body.append("// " + weeName + " methods\n");
                body.append("////////////////////\n");
                Method[] methods = wee.getDeclaredMethods();
                for (int i=0; i < methods.length; i++) {
                    if (Modifier.isPublic(methods[i].getModifiers())) {
                        addMethod(body,methods[i],className,weeName);
                    }
                }
                wee = wee.getSuperclass();
                weeName = unqualifyName(wee.getName());
                addImport(importLines,wee);
            }

            fos = new FileOutputStream("Multi" + className + ".java");
            outFile = new PrintWriter(fos);
            StringBuffer outText = createPreamble(prefixName);
            outText.append(importLines.toString() + "\n");
            addHeader(outText,className);
            outText.append(body.toString());
            outText.append("}\n");
            outFile.write(outText.toString());
            outFile.flush();
            outFile.close();
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    /**
     * D'Oh!  Something bad happened.
     */
    public static void usage(String s) throws IOException {
        System.err.println("Usage:  AutoMulti <plafdir> [com.sun]");
        throw new IllegalArgumentException(s);
    }

    /**
     * Takes the plaf directory name and generates the multiplexing UI
     * source code.
     */
    public static void main(String[] args) throws IOException {

        if (args.length < 1) {
            usage("");
        }

        String dirName = args[0];
        File dir = new File(dirName);
        if (!dir.isDirectory()) {
            System.err.println("No such directory:  " + dirName);
            usage("");
        }

        String prefixName;
        if (args.length > 1) {
            prefixName = args[1];
        } else {
            prefixName = "com.sun.java";
        }

        String plafUIs[] = dir.list(new UIJavaFilter());
        for (int i = 0; i < plafUIs.length; i++) {
            generateFile(prefixName,stripExtension(plafUIs[i]));
        }
    }
}

/**
 * Only accepts file names of the form *UI.java.  The one exception
 * is not accepting ComponentUI.java because we don't need to generate
 * a multiplexing class for it.
 */
class UIJavaFilter implements FilenameFilter {
    public boolean accept(File dir, String name) {
        if (name.equals("ComponentUI.java")) {
            return false;
        } else if (name.endsWith("UI.java")) {
            return true;
        } else {
            return false;
        }
    }
}
