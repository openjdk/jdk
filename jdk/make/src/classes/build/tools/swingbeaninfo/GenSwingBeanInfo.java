/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.swingbeaninfo;

import java.beans.BeanInfo;
import java.beans.BeanDescriptor;
import java.beans.Introspector;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;

import java.io.*;

import java.util.Hashtable;
import java.util.HashMap;
import java.util.Iterator;

/**
 * A utlity for generating a BeanInfo source file from a template and a
 * Hashtable with hints that were generated from a doclet.
 * it's neccessary to write things like the per property descriptions
 * by hand.  To run the application:
 * <pre>
 * java GenSwingBeanInfo <class name>
 * </pre>
 * Code for a bean info class is written to out.  If the class is
 * swing package, you don't need to fully specify its name.
 *
 * @author Hans Muller
 * @author Rich Schiavi
 * @author Mark Davidson
 */
public class GenSwingBeanInfo {
    private final static String BEANINFO_SUFFIX = "BeanInfo.java";

    // Tokens in @(...)
    private final static String TOK_BEANPACKAGE = "BeanPackageName";
    private final static String TOK_BEANCLASS = "BeanClassName";
    private final static String TOK_BEANOBJECT = "BeanClassObject";
    private final static String TOK_CLASSDESC = "ClassDescriptors";
    private final static String TOK_BEANDESC = "BeanDescription";
    private final static String TOK_PROPDESC = "BeanPropertyDescriptors";
    private final static String TOK_ENUMVARS = "EnumVariables";

    private String enumcode;  // Generated code for enumerated properties.

    private boolean DEBUG = false;

    private String fileDir;
    private String templateFilename;

    /**
     * Public constructor
     * @param fileDir Location to put the generated source files.
     * @param templateFilename Location of the BeanInfo template
     * @param debug Flag to turn on debugging
     */
    public GenSwingBeanInfo(String fileDir, String templateFilename, boolean debug)  {
        this.fileDir = fileDir;
        this.templateFilename = templateFilename;
        this.DEBUG = debug;
    }

    /**
     * Opens a BeanInfo PrintStream for the class.
     */
    private PrintStream initOutputFile(String classname) {
        try {
            OutputStream out = new FileOutputStream(fileDir + File.separator + classname + BEANINFO_SUFFIX);
            BufferedOutputStream bout = new BufferedOutputStream(out);
            return new PrintStream(out);
        } catch (IOException e){
        //    System.err.println("GenSwingBeanInfo: " + e.toString());
        }
        return null;
    }

    private static void messageAndExit(String msg) {
        System.err.println("\n" + msg);
        System.exit(1);
    }


    /**
     * Load the contents of the BeanInfo template into a string and
     * return the string.
     */
    private String loadTemplate() {
        String template = "<no template>";

        try {
            File file = new File(templateFilename);
            DataInputStream stream = new DataInputStream(new FileInputStream(file));
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            StringBuffer buffer = new StringBuffer();

            int c;
            while((c = reader.read()) != -1) {
                buffer.append((char)c);
            }

            template = buffer.toString();
            reader.close();
        } catch (IOException e) {
            System.out.println(e.getMessage());
            messageAndExit("GenSwingBeanInfo: Couldn't load template: " + templateFilename + e);
        }
        return template;
    }


    /**
     * Generates a string for the BeanDescriptor
     */
    private String genBeanDescriptor(DocBeanInfo dbi) {
        String code = "";
        int beanflags = dbi.beanflags;

        // we support export, hidden, preferred
        if ((beanflags & DocBeanInfo.EXPERT) != 0)
            code += " sun.swing.BeanInfoUtils.EXPERT, Boolean.TRUE,\n";
        if ((beanflags & DocBeanInfo.HIDDEN) !=0)
            code += "                    sun.swing.BeanInfoUtils.HIDDEN, Boolean.TRUE,\n";
        /* 1.2 only - make sure build flag build using 1.2 */
        if ((beanflags & DocBeanInfo.PREFERRED) !=0)
            code += "                 sun.swing.BeanInfoUtils.PREFERRED, Boolean.TRUE,\n";
        if (!(dbi.customizerclass.equals("null")))
            code += "            sun.swing.BeanInfoUtils.CUSTOMIZERCLASS, " + dbi.customizerclass + ".class,\n";

        if (dbi.attribs != null)  {
            code += genAttributes(dbi.attribs);
        }

        return code;
    }

    /**
     * Generates the code for the attributes table.
     */
    private String genAttributes(HashMap attribs)  {
        StringBuffer code = new StringBuffer();
        String key;
        String value;

        Iterator iterator = attribs.keySet().iterator();
        while(iterator.hasNext())  {
            key = (String)iterator.next();
            value = (String)attribs.get(key);

            if (value.equals("true") || value.equals("false"))  {
                // Substitute the "true" and "false" for codegen Boolean values.
                if(value.equals("true"))
                    value = "Boolean.TRUE";
                else
                    value = "Boolean.FALSE";

                code.append("              \"").append(key).append("\", ").append(value).append(",\n");
            } else {
                code.append("              \"").append(key).append("\", \"").append(value).append("\",\n");
            }
        }
        return code.toString();
    }

    /**
     * Generates the code for the enumeration.
     * XXX - side effect: Modifies the enumcode field variable.
     */
    private String genEnumeration(String propName, HashMap enums)  {
        String objectName = propName + "Enumeration";
        String key;
        String value;

        StringBuffer code = new StringBuffer("\n\t\tObject[] ");
        code.append(objectName).append(" = new Object[] { \n");

        Iterator iterator = enums.keySet().iterator();
        while(iterator.hasNext())  {
            key = (String)iterator.next();
            value = (String)enums.get(key);

            code.append("\t\t\t\"").append(key).append("\" ,   new Integer(");
            code.append(value).append("), \"").append(value).append("\",\n");
        }
        // Close the statically initialized Object[]
        code.replace(code.length() - 2, code.length(), "\n\t\t};\n");

        // Add this string to the enumeration code.
        enumcode += code.toString();

        // Return the PropertyDescriptor init string;
        return "         \"enumerationValues\", " + objectName + ",\n";
    }

    /**
     * Generate the createPropertyDescriptor() calls, one per property.
     * A fully specified createPropertyDescriptor() call looks like this:
     * <pre>
     *      createPropertyDescriptor("contentPane", new Object[] {
     *                           BOUND, Boolean.TRUE,
     *               CONSTRAINED, Boolean.TRUE,
     *             PROPERTYEDITORCLASS, package.MyEditor.cl
     *               WRITEMETHOD, "setContentPane",
     *               DISPLAYNAME, "contentPane",
     *                          EXPERT, Boolean.FALSE,
     *                          HIDDEN, Boolean.FALSE,
     *                       PREFERRED, Boolean.TRUE,
     *                SHORTDESCRIPTION, "A top level window with a window manager border",
     *               "random attribute","random value"
     *              }
     *           );
     * </pre>
     *
     * @param info The actual BeanInfo class generated from from the Intospector.
     * @param dochash Set of DocBeanInfo pairs for each property. This information
     *          is used to suplement the instrospected properties.
     * @return A snippet of source code which would construct all the PropertyDescriptors.
     */
    private String genPropertyDescriptors(BeanInfo info, Hashtable dochash) {
        String code = "";
        enumcode = " "; // code for enumerated properties.
        PropertyDescriptor[] pds = info.getPropertyDescriptors();
        boolean hash_match = false;
        DocBeanInfo dbi = null;

        for(int i = 0; i < pds.length; i++) {
            if (pds[i].getReadMethod() != null) {
                code += "\ncreatePropertyDescriptor(\"" + pds[i].getName() + "\", new Object[] {\n";

                if (DEBUG)
                    System.out.println("Introspected propertyDescriptor:  " + pds[i].getName());

                if (dochash.size() > 0 && dochash.containsKey(pds[i].getName())) {
                    dbi = (DocBeanInfo)dochash.remove(pds[i].getName());
                    // override/set properties on this *introspected*
                    // BeanInfo pds using our DocBeanInfo class values
                    setDocInfoProps(dbi, pds[i]);
                    hash_match = true;
                    if (DEBUG)
                        System.out.println("DocBeanInfo class exists for propertyDescriptor: " + pds[i].getName() + "\n");
                } else {
                    hash_match = false;
                }

                // Do I need to do anything with this property descriptor
                if (hash_match) {
                    if ((dbi.beanflags & DocBeanInfo.BOUND) != 0) {
                        code += "               sun.swing.BeanInfoUtils.BOUND, Boolean.TRUE,\n";
                    } else {
                        code += "               sun.swing.BeanInfoUtils.BOUND, Boolean.FALSE,\n";
                    }
                }

                if (pds[i].isConstrained()) {
                    code += "         sun.swing.BeanInfoUtils.CONSTRAINED, Boolean.TRUE,\n";
                }

                if (pds[i].getPropertyEditorClass() != null) {
                    String className = pds[i].getPropertyEditorClass().getName();
                    code += " sun.swing.BeanInfoUtils.PROPERTYEDITORCLASS, " + className + ".class,\n";
                } else if ((hash_match) && (!(dbi.propertyeditorclass.equals("null")))) {
                    code += " sun.swing.BeanInfoUtils.PROPERTYEDITORCLASS, " + dbi.propertyeditorclass + ".class,\n";
                }

                if ((hash_match) && (!(dbi.customizerclass.equals("null")))) {
                    code += " sun.swing.BeanInfoUtils.CUSTOMIZERCLASS, " + dbi.customizerclass + ".class,\n";
                }

                if ((hash_match) && (dbi.enums != null))  {
                    code += genEnumeration(pds[i].getName(), dbi.enums);
                }

                if (!pds[i].getDisplayName().equals(pds[i].getName())) {
                    code += "         sun.swing.BeanInfoUtils.DISPLAYNAME, \"" + pds[i].getDisplayName() + "\",\n";
                }

                if (pds[i].isExpert()) {
                    code += "              sun.swing.BeanInfoUtils.EXPERT, Boolean.TRUE,\n";
                }

                if (pds[i].isHidden()) {
                    code += "              sun.swing.BeanInfoUtils.HIDDEN, Boolean.TRUE,\n";
                }

                if (pds[i].isPreferred()) {
                    code += "           sun.swing.BeanInfoUtils.PREFERRED, Boolean.TRUE,\n";
                }

                // user attributes
                if (hash_match) {
                    if (dbi.attribs != null)  {
                        code += genAttributes(dbi.attribs);
                    }
                }
                code += "    sun.swing.BeanInfoUtils.SHORTDESCRIPTION, \"" + pds[i].getShortDescription() + "\",\n";

                // Print the closing brackets.  If this is the last array initializer,
                // don't print the trailing comma.
                if (i == (pds.length - 1)) {
                    code += "  }\n)\n";
                } else {
                    code += "  }\n),\n";
                }

            } // end if ( readMethod != null )
        } // end for
        return code;
    }

    /**
     * Sets properties from the BeanInfo supplement on the
     * introspected PropertyDescriptor
     */
    private void setDocInfoProps(DocBeanInfo dbi, PropertyDescriptor pds) {
        int beanflags = dbi.beanflags;

        if ((beanflags & DocBeanInfo.BOUND) != 0)
            pds.setBound(true);
        if ((beanflags & DocBeanInfo.EXPERT) != 0)
            pds.setExpert(true);
        if ((beanflags & DocBeanInfo.CONSTRAINED) != 0)
            pds.setConstrained(true);
        if ((beanflags & DocBeanInfo.HIDDEN) !=0)
            pds.setHidden(true);
        if ((beanflags & DocBeanInfo.PREFERRED) !=0)
            pds.setPreferred(true);

        if (!(dbi.desc.equals("null"))){
            pds.setShortDescription(dbi.desc);
        }
        if (!(dbi.displayname.equals("null"))){
            pds.setDisplayName(dbi.displayname);
        }
    }

    /**
     * Generates the BeanInfo source file using instrospection and a
     * Hashtable full of hints. This the only public method in this class.
     *
     * @param classname Root name of the class. i.e., JButton
     * @param dochash A hashtable containing the DocBeanInfo.
     */
    public void genBeanInfo(String packageName, String classname, Hashtable dochash) {
        // The following initial values are just examples.  All of these
        // fields are initialized below.
        String beanClassName = "JInternalFrame";
        String beanClassObject = "javax.swing.JInternalFrame.class";
        String beanDescription = "<A description of this component>.";
        String beanPropertyDescriptors = "<createSwingPropertyDescriptor code>";
        String classPropertyDescriptors = "<createSwingClassPropertyDescriptor code>";

        Class cls = getClass(packageName, classname);
        if (cls == null){
            messageAndExit("Can't find class: " + classname);
        }

        // Get the output stream.
        PrintStream out = initOutputFile(classname);

        // Run the Introspector and initialize the variables

        BeanInfo beanInfo = null;
        BeanDescriptor beanDescriptor = null;

        try {
            if (cls == javax.swing.JComponent.class)  {
                // Go all the way up the heirarchy for JComponent
                beanInfo = Introspector.getBeanInfo(cls);
            } else {
                beanInfo = Introspector.getBeanInfo(cls, cls.getSuperclass());
            }
            beanDescriptor = beanInfo.getBeanDescriptor();
            beanDescription = beanDescriptor.getShortDescription();
        } catch (IntrospectionException e) {
            messageAndExit("Introspection failed for " + cls.getName() + " " + e);
        }

        beanClassName = beanDescriptor.getName();
        beanClassObject = cls.getName() + ".class";

        if (DEBUG){
            System.out.println(">>>>GenSwingBeanInfo class: "  + beanClassName);
        }
        // Generate the Class BeanDescriptor information first
        if (dochash.size() > 0) {
            if (dochash.containsKey(beanClassName)) {
                    DocBeanInfo dbi = (DocBeanInfo)dochash.remove(beanClassName);
                    classPropertyDescriptors = genBeanDescriptor(dbi);
                    if (DEBUG)
                        System.out.println("ClassPropertyDescriptors: " + classPropertyDescriptors);
                    if (!(dbi.desc.equals("null")))
                        beanDescription = dbi.desc;
            } else
                    beanDescription = beanDescriptor.getShortDescription();
        } else
            beanDescription = beanDescriptor.getShortDescription();

        // Generate the Property descriptors
        beanPropertyDescriptors = genPropertyDescriptors(beanInfo,dochash);

        // Dump the template to out, substituting values for
            // @(token) tokens as they're encountered.

        int currentIndex = 0;
        // not loading this to get around build issue for now
        String template = loadTemplate();

        // This loop substitutes the "@(...)" tags in the template with the ones for the
        // current class.
        while (currentIndex < template.length()) {
            // Find the Token
            int tokenStart = template.indexOf("@(", currentIndex);
            if (tokenStart != -1) {
                out.print(template.substring(currentIndex, tokenStart));

                int tokenEnd = template.indexOf(")", tokenStart);
                if (tokenEnd == -1) {
                    messageAndExit("Bad @(<token>) beginning at " + tokenStart);
                }
                String token = template.substring(tokenStart+2, tokenEnd);

                if (token.equals(TOK_BEANCLASS)) {
                    out.print(beanClassName);
                } else if (token.equals(TOK_CLASSDESC)) {
                    if (!(classPropertyDescriptors.equals("<createSwingClassPropertyDescriptor code>"))) {
                        printDescriptors(out, classPropertyDescriptors, template, tokenStart);
                    }
                } else if (token.equals(TOK_BEANPACKAGE)){
                    out.print(packageName);
                } else if (token.equals(TOK_BEANOBJECT)) {
                    out.print(beanClassObject);
                } else if (token.equals(TOK_BEANDESC)) {
                    out.print(beanDescription);
                } else if (token.equals(TOK_ENUMVARS)){
                    out.print(enumcode);
                } else if (token.equals(TOK_PROPDESC)) {
                    printDescriptors(out, beanPropertyDescriptors, template, tokenStart);
                } else if (token.equals("#")) {
                    // Ignore the @(#) Version Control tag if it exists.
                } else {
                    messageAndExit("Unrecognized token @(" + token + ")");
                }
                currentIndex = tokenEnd + 1;
            } else {
                // tokenStart == -1 - We are finsihed.
                out.print(template.substring(currentIndex, template.length()));
                break;
            }
        }
        out.close();
    }

    /**
     * Returns the class from the package name and the class root name.
     *
     * @param packageName The name of the package of the containing class.
     * @param rootname The root name of the class. i.e, JButton
     * @return The class instance or null.
     */
    private Class getClass(String packageName, String rootname)  {
        Class cls = null;
        String classname = rootname;

        if (packageName != null || !packageName.equals(""))  {
            classname = packageName + "." + rootname;
        }

        try {
            cls = Class.forName(classname);
        } catch (ClassNotFoundException e) {
            // Fail silently.
        }
        return cls;
    }

    /**
     * Prints the formated descriptors to the PrintStream
     * @param out Open PrintStream
     * @param s String descriptor
     * @param template Template
     * @param tokenStart Index into the template
     */
    private void printDescriptors(PrintStream out, String s,
                                String template, int tokenStart)  {
            String indent = "";

            // Find the newline that preceeds @(BeanPropertyDescriptors) to
            // calculate the indent.
            for (int i = tokenStart; i >= 0; i--) {
                if (template.charAt(i) == '\n') {
                        char[] chars = new char[tokenStart - i];
                        for (int j = 0; j < chars.length; j++) {
                            chars[j] = ' ';
                        }
                        indent = new String(chars);
                        break;
                }
            }

            int i = 0;
            while(i < s.length()) {
                int nlIndex = s.indexOf('\n', i);
                out.print(s.substring(i, nlIndex+1));
                out.print(indent);
                i = nlIndex + 1;
            }
    }


}
