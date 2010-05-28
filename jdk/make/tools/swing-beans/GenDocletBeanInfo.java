/*
 * Copyright (c) 1998, 2001, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.Tag;

import java.beans.Introspector;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * Properties supported and tag syntax:
 *
 * @beaninfo
 *      bound: flag
 *      constrained: flag
 *      expert: flag
 *      hidden: flag
 *      preferred: flag
 *      description: string
 *      displayname: string
 *      propertyeditorclass: string (with dots: foo.bar.MyPropertyEditor
 *      customizerclass: string (w/dots: foo.bar.MyCustomizer)
 *      attribute: key1 value1
 *      attribute: key2 value2
 *
 * TODO: getValue and genDocletInfo needs some cleaning.
 *
 * @author Hans Muller
 * @author Rich Schiavi
 * @author Mark Davidson
 */
public class GenDocletBeanInfo {

    static String[] ATTRIBUTE_NAMES = { "bound",
                                     "constrained",
                                     "expert",
                                     "hidden",
                                     "preferred",
                                     "displayname",
                                     "propertyeditorclass",
                                     "customizerclass",
                                     "displayname",
                                     "description",
                                     "enum",
                                     "attribute" };
    private static boolean DEBUG = false;

    private static String fileDir = "";
    private static String templateDir = "";

    public static final String TRUE = "true";
    public static final String FALSE = "false";

    /**
     * Method called from the javadoc environment to determint the options length.
     * Doclet options:
     *      -t template location
     *      -d outputdir
     *      -x true Enable debug output.
     */
    public static int optionLength(String option) {
        // remind: this needs to be cleaned up
        if (option.equals("-t"))
            return 2;
        if (option.equals("-d"))
            return 2;
        if (option.equals("-x"))
            return 2;
        return 0;
    }

    /** @beaninfo
     * bound:true
     * constrained:false
     * expert:true
     * hidden:true
     * preferred:false
     * description: the description of this method can
     *              do all sorts of funky things. if it \n
     *              is indented like this, we have to remove
     *              all char spaces greater than 2 and also any hard-coded \n
     *              newline characters and all newlines
     * displayname: theString
     * propertyeditorclass: foo.bar.MyPropertyEditorClass
     * customizerclass: foo.bar.MyCustomizerClass
     * attribute:key1 value1
     * attribute: key2  value2
     *
     */
    public static boolean start(RootDoc doc) {
        readOptions(doc.options());

        if (templateDir.length() == 0) {
            System.err.println("-t option not specified");
            return false;
        }
        if (fileDir.length() == 0) {
            System.err.println("-d option not specified");
            return false;
        }

        GenSwingBeanInfo generator = new GenSwingBeanInfo(fileDir, templateDir, DEBUG);
        Hashtable dochash = new Hashtable();
        DocBeanInfo dbi;

        /* "javadoc Foo.java Bar.java" will return:
        *         "Foo Foo.I1 Foo.I2 Bar Bar.I1 Bar.I2"
        * i.e., with all the innerclasses of classes specified in the command
        * line.  We don't want to generate BeanInfo for any of these inner
        * classes, so we ignore these by remembering what the last outer
        * class was.  A hack, I admit, but makes the build faster.
        */
        String previousClass = null;

        ClassDoc[] classes = doc.classes();

        for (int cnt = 0; cnt < classes.length; cnt++) {
            String className = classes[cnt].qualifiedName();
            if (previousClass != null &&
                className.startsWith(previousClass) &&
                className.charAt(previousClass.length()) == '.') {
                continue;
            }
            previousClass = className;

            // XXX - debug
            System.out.println("\n>>> Generating beaninfo for " + className + "...");

            // Examine the javadoc tags and look for the the @beaninfo tag
            // This first block looks at the javadoc for the class
            Tag[] tags = classes[cnt].tags();
            for (int i = 0; i < tags.length; i++) {
                if (tags[i].kind().equalsIgnoreCase("@beaninfo")) {
                    if (DEBUG)
                       System.out.println("GenDocletBeanInfo: found @beaninfo tagged Class: " + tags[i].text());
                    dbi = genDocletInfo(tags[i].text(), classes[cnt].name());
                    dochash.put(dbi.name, dbi);
                    break;
                }
            }

            // This block looks at the javadoc for the class methods.
            int startPos = -1;
            MethodDoc[] methods = classes[cnt].methods();
            for (int j = 0; j < methods.length; j++) {
                // actually don't "introspect" - look for all
                // methods with a @beaninfo tag
                tags = methods[j].tags();
                for (int x = 0; x < tags.length; x++){
                    if (tags[x].kind().equalsIgnoreCase("@beaninfo")){
                        if ((methods[j].name().startsWith("get")) ||
                            (methods[j].name().startsWith("set")))
                            startPos = 3;
                        else if (methods[j].name().startsWith("is"))
                            startPos = 2;
                        else
                            startPos = 0;
                        String propDesc =
                            Introspector.decapitalize((methods[j].name()).substring(startPos));
                        if (DEBUG)
                            System.out.println("GenDocletBeanInfo: found @beaninfo tagged Method: " + tags[x].text());
                        dbi = genDocletInfo(tags[x].text(), propDesc);
                        dochash.put(dbi.name, dbi);
                        break;
                    }
                }
            }
            if (DEBUG) {
                // dump our classes doc beaninfo
                System.out.println(">>>>DocletBeanInfo for class: " + classes[cnt].name());
                Enumeration e = dochash.elements();
                while (e.hasMoreElements()) {
                    DocBeanInfo db = (DocBeanInfo)e.nextElement();
                    System.out.println(db.toString());
                }
            }

            // Use the generator to create the beaninfo code for the class.
            generator.genBeanInfo(classes[cnt].containingPackage().name(),
                                        classes[cnt].name(), dochash);
            // reset the values!
            dochash.clear();
        } // end for loop
        return true;
    }

    /**
     * Reads the command line options.
     * Side Effect, sets class variables templateDir, fileDir and DEBUG
     */
    private static void readOptions(String[][] options)  {
        // Parse the command line args
        for (int i = 0; i < options.length; i++){
            if (options[i][0].equals("-t")) {
                templateDir = options[i][1];
            } else if (options[i][0].equals("-d")) {
                fileDir = options[i][1];
            } else if (options[i][0].equals("-x")){
                if (options[i][1].equals("true"))
                    DEBUG=true;
                else
                    DEBUG=false;
            }
        }
    }

    /**
     * Create a "BeanInfo" data structure from the tag. This is a data structure
     * which contains all beaninfo data for a method or a class.
     *
     * @param text All the text after the @beaninfo tag.
     * @param name Name of the property i.e., mnemonic for setMnemonic
     */
    private static DocBeanInfo genDocletInfo(String text, String name) {
        int beanflags = 0;
        String desc = "null";
        String displayname = "null";
        String propertyeditorclass = "null";
        String customizerclass = "null";
        String value = "null";
        HashMap attribs = null;
        HashMap enums = null;

        int index;

        for (int j = 0; j < ATTRIBUTE_NAMES.length; j++){
            index = 0;
            if ((index = text.indexOf(ATTRIBUTE_NAMES[j])) != -1){
                value = getValue((text).substring(index),ATTRIBUTE_NAMES[j]);

                if (ATTRIBUTE_NAMES[j].equalsIgnoreCase("attribute")) {
                    attribs = getAttributeMap(value, " ");
                }
                if (ATTRIBUTE_NAMES[j].equalsIgnoreCase("enum")) {
                    enums = getAttributeMap(value, " \n");
                }
                else if (ATTRIBUTE_NAMES[j].equals("displayname")){
                    displayname = value;
                }
                else if (ATTRIBUTE_NAMES[j].equalsIgnoreCase("propertyeditorclass")) {
                    propertyeditorclass = value;
                }
                else if (ATTRIBUTE_NAMES[j].equalsIgnoreCase("customizerclass")){
                    customizerclass = value;
                }
                else if ((ATTRIBUTE_NAMES[j].equalsIgnoreCase("bound"))
                         && (value.equalsIgnoreCase(TRUE)))
                    beanflags = beanflags | DocBeanInfo.BOUND;
                else if ((ATTRIBUTE_NAMES[j].equalsIgnoreCase("expert"))
                         && (value.equalsIgnoreCase(TRUE)))
                    beanflags = beanflags | DocBeanInfo.EXPERT;
                else if ((ATTRIBUTE_NAMES[j].equalsIgnoreCase("constrained"))
                         && (value.equalsIgnoreCase(TRUE)))
                    beanflags = beanflags | DocBeanInfo.CONSTRAINED;
                else if ((ATTRIBUTE_NAMES[j].equalsIgnoreCase("hidden"))
                         && (value.equalsIgnoreCase(TRUE)))
                    beanflags = beanflags | DocBeanInfo.HIDDEN;
                else if ((ATTRIBUTE_NAMES[j].equalsIgnoreCase("preferred"))
                         && (value.equalsIgnoreCase(TRUE)))
                    beanflags = beanflags | DocBeanInfo.PREFERRED;
                else if (ATTRIBUTE_NAMES[j].equalsIgnoreCase("description")){
                    desc = value;
                }
            }
        }
        /** here we create our doclet-beaninfo data structure, which we read in
         *  later if it has anything worthwhile
         */

        // Construct a new Descriptor class
        return new DocBeanInfo(name, beanflags, desc,displayname,
                                         propertyeditorclass, customizerclass,
                                         attribs, enums);
    }

    /**
     * Parses the substring and returns the cleaned up value for the attribute.
     * @param substring Full String of the attrib tag.
     *       i.e., "attribute: visualUpdate true" will return "visualUpdate true";
     */
    private static String getValue(String substring, String prop) {
        StringTokenizer t;
        String value = "null";

        try {
            /** if the ATTRIBUTE_NAMES is NOT the description, then we
             *  parse until newline
             *  if it is the description we read until the next token
             *  and then look for a match in the last MAXMATCH index
             *  and truncate the description
             *  if it is the attribute we wead until no more
             */
            if (prop.equalsIgnoreCase("attribute")){
                StringBuffer tmp = new StringBuffer();
                try {
                    t = new StringTokenizer(substring, " :\n");
                    t.nextToken().trim();//the prop
                    // we want to return : key1 value1 key2 value2
                    while (t.hasMoreTokens()){
                        tmp.append(t.nextToken().trim()).append(" ");
                        tmp.append(t.nextToken().trim()).append(" ");
                        String test = t.nextToken().trim();
                        if (!(test.equalsIgnoreCase("attribute")))
                            break;
                    }
                } catch (Exception e){
                }
                value = tmp.toString();
            }
            else if (prop.equalsIgnoreCase("enum")){
                t = new StringTokenizer(substring, ":");
                t.nextToken().trim(); // the prop we already know
                StringBuffer tmp = new StringBuffer(t.nextToken().trim());
                for (int i = 0; i < ATTRIBUTE_NAMES.length; i++){
                    if (tmp.toString().endsWith(ATTRIBUTE_NAMES[i])){
                        int len = ATTRIBUTE_NAMES[i].length();
                        // trim off that
                        tmp.setLength(tmp.length() - len);
                        break;
                    }
                }
                value = tmp.toString();
            }
            else if (prop.equalsIgnoreCase("description")){
                t = new StringTokenizer(substring, ":");
                t.nextToken().trim(); // the prop we already know
                StringBuffer tmp = new StringBuffer(t.nextToken().trim());
                for (int i = 0; i < ATTRIBUTE_NAMES.length; i++){
                    if (tmp.toString().endsWith(ATTRIBUTE_NAMES[i])){
                        int len = ATTRIBUTE_NAMES[i].length();
                        // trim off that
                        tmp.setLength(tmp.length() - len);
                        break;
                    }
                }
                value = hansalizeIt(tmp.toString());
            }
            else {
                // Single value properties like bound: true
                t = new StringTokenizer(substring, ":\n");
                t.nextToken().trim(); // the prop we already know
                value = t.nextToken().trim();
            }

            // now we need to look for a match of any of the
            // property

            return value;
        }
        catch (Exception e){
            return "invalidValue";
        }
    }

    /**
     * Creates a HashMap containing the key value pair for the parsed values
     * of the "attributes" and "enum" tags.
     * ie. For attribute value: visualUpdate true
     *     The HashMap will have key: visualUpdate, value: true
     */
    private static HashMap getAttributeMap(String str, String delim)  {
        StringTokenizer t = new StringTokenizer(str, delim);
        HashMap map = null;
        String key;
        String value;

        int num = t.countTokens()/2;
        if (num > 0)  {
            map = new HashMap();
            for (int i = 0; i < num; i++) {
                key = t.nextToken().trim();
                value = t.nextToken().trim();
                map.put(key, value);
            }
        }
        return map;
    }

    // looks for extra spaces, \n hard-coded and invisible,etc
    private static String hansalizeIt(String from){
        char [] chars = from.toCharArray();
        int len = chars.length;
        int toss = 0;

        // remove double spaces
        for (int i = 0; i < len; i++){
            if ((chars[i] == ' ')) {
                if (i+1 < len) {
                    if ((chars[i+1] == ' ' ) || (chars[i+1] == '\n'))
                        {
                            --len;
                            System.arraycopy(chars,i+1,chars,i,len-i);
                            --i;
                        }
                }
            }

            if (chars[i] == '\n'){
                chars[i] = ' ';
                i -= 2;
            }

            if (chars[i] == '\\') {
                if (i+1 < len) {
                    if (chars[i+1] == 'n'){
                        chars[i+1] = ' ';
                        --len;
                        System.arraycopy(chars,i+1, chars,i, len-i);
                        --i;
                    }
                }
            }
        }
        return new String(chars,0,len);
    }

}
