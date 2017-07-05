/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.ws.processor.generator;

import com.sun.tools.internal.ws.processor.model.*;
import com.sun.tools.internal.ws.processor.model.java.JavaInterface;
import com.sun.tools.internal.ws.processor.model.java.JavaStructureMember;
import com.sun.tools.internal.ws.processor.model.jaxb.JAXBProperty;
import com.sun.tools.internal.ws.processor.util.DirectoryUtil;
import com.sun.tools.internal.ws.processor.util.ProcessorEnvironment;
import com.sun.tools.internal.ws.util.ClassNameInfo;
import com.sun.xml.internal.ws.streaming.PrefixFactory;
import com.sun.xml.internal.ws.streaming.PrefixFactoryImpl;
import com.sun.xml.internal.ws.util.StringUtils;
import com.sun.xml.internal.bind.api.JAXBRIContext;

import javax.xml.namespace.QName;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Names provides utility methods used by other wscompile classes
 * for dealing with identifiers.
 *
 * @author WS Development Team
 */
public class Names implements GeneratorConstants{

    public Names() {
    }

    public String stubFor(Port port, String infix) {
        String result =
            (String) port.getProperty(ModelProperties.PROPERTY_STUB_CLASS_NAME);
        if (result == null) {
            result =
                makeDerivedClassName(
                    port.getJavaInterface(),
                    STUB_SUFFIX,
                    infix);
        }
        return result;
    }

    public String tieFor(Port port, String infix) {
        String result =
            (String) port.getProperty(ModelProperties.PROPERTY_TIE_CLASS_NAME);
        if (result == null) {
            result =
                makeDerivedClassName(
                    port.getJavaInterface(),
                    TIE_SUFFIX,
                    infix);
        }
        return result;
    }

    public String makeDerivedClassName(
        JavaInterface javaInterface,
        String suffix,
        String infix) {

        String name =
            ClassNameInfo.replaceInnerClassSym(javaInterface.getRealName());
        return name + (infix == null ? "" : UNDERSCORE + infix) + suffix;
    }

    public static String getPortName(Port port) {
        String javaPortName =
            (String) port.getProperty(ModelProperties.PROPERTY_JAVA_PORT_NAME);
        if (javaPortName != null) {
            return javaPortName;
        } else {
            QName portName =
                (QName) port.getProperty(
                    ModelProperties.PROPERTY_WSDL_PORT_NAME);
            if (portName != null) {
                return portName.getLocalPart();
            } else {
                String name = stripQualifier(port.getJavaInterface().getName());
                return ClassNameInfo.replaceInnerClassSym(name);
            }
        }
    }

    public static String stripQualifier(Class classObj) {
        String name = classObj.getName();
        return stripQualifier(name);
    }

    public static String stripQualifier(String name) {
        return ClassNameInfo.getName(name);
    }

    public static String getPackageName(String className) {
        String packageName = ClassNameInfo.getQualifier(className);
        return packageName != null ? packageName : "";
    }

    public static String getUnqualifiedClassName(String className) {
        return ClassNameInfo.getName(className).replace('$', '.');
    }

    /**
     * Return the File object that should be used as the source file
     * for the given Java class, using the supplied destination
     * directory for the top of the package hierarchy.
     */
    public File sourceFileForClass(
        String className,
        String outputClassName,
        File destDir,
        ProcessorEnvironment env)
        throws GeneratorException {
        File packageDir =
            DirectoryUtil.getOutputDirectoryFor(className, destDir, env);
        String outputName = stripQualifier(outputClassName);

        String outputFileName = outputName + JAVA_SRC_SUFFIX;
        return new File(packageDir, outputFileName);
    }

    public static String getPackageName(Service service) {
        String portPackage =
            getPackageName(service.getJavaInterface().getName());
        return portPackage;
    }

    public String customJavaTypeClassName(JavaInterface intf) {
        String intName = intf.getName();
        return intName;
    }

    public String customExceptionClassName(Fault fault) {
        String typeName = fault.getJavaException().getName();
        return typeName;
    }

    public String getExceptionClassMemberName(){
        return FAULT_CLASS_MEMBER_NAME;
    }

    public String getQNameName(QName name) {
        String qname =
            getPrefix(name) + UNDERSCORE + name.getLocalPart() + QNAME_SUFFIX;
        return validInternalJavaIdentifier(qname);
    }

    public String getBlockQNameName(Operation operation, Block block) {
        QName blockName = block.getName();
        String qname = getPrefix(blockName);
        if (operation != null)
            qname += UNDERSCORE + operation.getUniqueName();
        qname += UNDERSCORE + blockName.getLocalPart() + QNAME_SUFFIX;
        return validInternalJavaIdentifier(qname);
    }

    public String getTypeQName(QName name) {
        String qname =
            getPrefix(name)
                + UNDERSCORE
                + name.getLocalPart()
                + TYPE_QNAME_SUFFIX;
        return validInternalJavaIdentifier(qname);
    }

    /* (non-Javadoc)
     * @see Names#validJavaClassName(String)
     */
    public String validJavaClassName(String name) {
        return JAXBRIContext.mangleNameToClassName(name);
    }

    /* (non-Javadoc)
     * @see Names#validJavaMemberName(String)
     */
    public String validJavaMemberName(String name) {
        return JAXBRIContext.mangleNameToVariableName(name);
    }

    public String validInternalJavaIdentifier(String name) {
        // return a valid java identifier without dropping characters (i.e. do not apply
        // the mapping of XML names to Java identifiers in the spec); it's only meant
        // to be used to generate internal identifiers (e.g. variable names in generated code)

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < name.length(); ++i) {
            char ch = name.charAt(i);
            if (i == 0) {
                if (Character.isJavaIdentifierStart(ch)) {
                    sb.append(ch);
                } else {
                    sb.append("_$");
                    sb.append(Integer.toHexString((int) ch));
                    sb.append("$");
                }
            } else {
                if (Character.isJavaIdentifierPart(ch)) {
                    sb.append(ch);
                } else {
                    sb.append("$");
                    sb.append(Integer.toHexString((int) ch));
                    sb.append("$");
                }
            }
        }

        String id = sb.toString();

        String tmp = (String) reservedWords.get(id);
        if (tmp != null)
            id = tmp;
        return id;
    }

    public String validExternalJavaIdentifier(String name) {
        return validInternalJavaIdentifier(name).replace('$', '_');
    }

    public String validJavaName(String name) {
        name = wordBreakString(name);
        name = removeWhiteSpace(name);

        String tmp = (String) reservedWords.get(name);
        if (tmp != null)
            name = tmp;
        return name;
    }

    public boolean isJavaReservedWord(String name) {
        return reservedWords.get(name) != null;
    }

    /* here we check on wether return values datatype is
       boolean. If its boolean, instead of a get method
       its set a is<MethodName> to comply with JavaBeans
       Pattern spec */
    public String getJavaMemberReadMethod(JavaStructureMember member) {
        String return_value = null;
        if ((member.getType().getRealName()) == "boolean") {
            return_value = IS + StringUtils.capitalize(member.getName());
        } else {
            return_value = GET + StringUtils.capitalize(member.getName());
        }
        return (return_value);
    }

    public String getJavaMemberWriteMethod(JavaStructureMember member) {
        return SET + StringUtils.capitalize(member.getName());
    }

    public static String getResponseName(String messageName) {
        return messageName + RESPONSE;
    }


    public String getJavaReadMethod(JAXBProperty prop){
        if(prop.getType().getName().equals("boolean"))
            return IS + StringUtils.capitalize(prop.getName());
        return getJavaReadMethod(prop.getName());
    }

    public String getJavaWriteMethod(JAXBProperty prop){
        return getJavaWriteMethod(prop.getName());
    }

    public String getJavaReadMethod(String prop){
        return GET + StringUtils.capitalize(prop);
    }

    public String getJavaWriteMethod(String prop){
        return SET + StringUtils.capitalize(prop);
    }

    public String removeWhiteSpace(String str) {
        String tmp = removeCharacter(' ', str);
        return tmp;
    }

    public String wordBreakString(String str) {
        StringBuffer buf = new StringBuffer(str);
        char ch;
        for (int i = 0; i < buf.length(); i++) {
            ch = buf.charAt(i);
            if (Character.isDigit(ch)) {
                if (i + 1 < buf.length()
                    && !Character.isDigit(buf.charAt(i + 1))) {
                    buf.insert(1 + i++, ' ');
                }
            } else if (Character.isSpaceChar(ch) || ch == '_') {
                continue;
            } else if (!Character.isJavaIdentifierPart(ch)) {
                buf.setCharAt(i, ' ');
            } else if (!Character.isLetter(ch)) {
                buf.setCharAt(i, ' ');
            }
        }
        return buf.toString();
    }

    public String removeCharacter(int ch, String str) {
        String tmp;
        int idx = str.indexOf(ch);
        while (idx >= 0) {
            str =
                str.substring(0, idx)
                    + StringUtils.capitalize(str.substring(idx + 1).trim());
            idx = str.indexOf(' ');
        }

        return str;
    }

    public String getPrefix(QName name) {
        return getPrefix(name.getNamespaceURI());
    }

    public String getPrefix(String uri) {
        return prefixFactory.getPrefix(uri);
    }

    public void resetPrefixFactory() {
        prefixFactory = new PrefixFactoryImpl(NS_PREFIX);
    }

    public void setSerializerNameInfix(String serNameInfix) {
        if (serNameInfix != null && serNameInfix.length() > 0)
            serializerNameInfix = UNDERSCORE + serNameInfix;
    }

    public String getSerializerNameInfix() {
        // Fix for bug 4811625 and 4778136, undoing what setter does (remove beginning underscore)
        String str = serializerNameInfix;
        if ((serializerNameInfix != null)
            && (serializerNameInfix.charAt(0) == '_'))
            str = serializerNameInfix.substring(1);
        return str;
    }

    protected String serializerNameInfix = null;
    protected PrefixFactory prefixFactory = new PrefixFactoryImpl(NS_PREFIX);
    protected static Map reservedWords;

    static {
        reservedWords = new HashMap();
        reservedWords.put("abstract", "_abstract");
        reservedWords.put("assert", "_assert");
        reservedWords.put("boolean", "_boolean");
        reservedWords.put("break", "_break");
        reservedWords.put("byte", "_byte");
        reservedWords.put("case", "_case");
        reservedWords.put("catch", "_catch");
        reservedWords.put("char", "_char");
        reservedWords.put("class", "_class");
        reservedWords.put("const", "_const");
        reservedWords.put("continue", "_continue");
        reservedWords.put("default", "_default");
        reservedWords.put("do", "_do");
        reservedWords.put("double", "_double");
        reservedWords.put("else", "_else");
        reservedWords.put("extends", "_extends");
        reservedWords.put("false", "_false");
        reservedWords.put("final", "_final");
        reservedWords.put("finally", "_finally");
        reservedWords.put("float", "_float");
        reservedWords.put("for", "_for");
        reservedWords.put("goto", "_goto");
        reservedWords.put("if", "_if");
        reservedWords.put("implements", "_implements");
        reservedWords.put("import", "_import");
        reservedWords.put("instanceof", "_instanceof");
        reservedWords.put("int", "_int");
        reservedWords.put("interface", "_interface");
        reservedWords.put("long", "_long");
        reservedWords.put("native", "_native");
        reservedWords.put("new", "_new");
        reservedWords.put("null", "_null");
        reservedWords.put("package", "_package");
        reservedWords.put("private", "_private");
        reservedWords.put("protected", "_protected");
        reservedWords.put("public", "_public");
        reservedWords.put("return", "_return");
        reservedWords.put("short", "_short");
        reservedWords.put("static", "_static");
        reservedWords.put("strictfp", "_strictfp");
        reservedWords.put("super", "_super");
        reservedWords.put("switch", "_switch");
        reservedWords.put("synchronized", "_synchronized");
        reservedWords.put("this", "_this");
        reservedWords.put("throw", "_throw");
        reservedWords.put("throws", "_throws");
        reservedWords.put("transient", "_transient");
        reservedWords.put("true", "_true");
        reservedWords.put("try", "_try");
        reservedWords.put("void", "_void");
        reservedWords.put("volatile", "_volatile");
        reservedWords.put("while", "_while");
        reservedWords.put("enum", "_enum");
    }
}
