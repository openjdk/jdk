/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.bind.v2.bytecode;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.xml.internal.bind.Util;

/**
 * Replaces a few constant pool tokens from a class "template" and then loads it into the VM.
 *
 * @author Kohsuke Kawaguchi
 */
public final class ClassTailor {

    private ClassTailor() {} // no instanciation please

    private static final Logger logger = Util.getClassLogger();

    /**
     * Returns the class name in the JVM format (such as "java/lang/String")
     */
    public static String toVMClassName( Class c ) {
        assert !c.isPrimitive();
        if(c.isArray())
            // I have no idea why it is designed like this, but javap says so.
            return toVMTypeName(c);
        return c.getName().replace('.','/');
    }

    public static String toVMTypeName( Class c ) {
        if(c.isArray()) {
            // TODO: study how an array type is encoded.
            return '['+toVMTypeName(c.getComponentType());
        }
        if(c.isPrimitive()) {
            if(c==Boolean.TYPE)     return "Z";
            if(c==Character.TYPE)   return "C";
            if(c==Byte.TYPE)        return "B";
            if(c==Double.TYPE)      return "D";
            if(c==Float.TYPE)       return "F";
            if(c==Integer.TYPE)     return "I";
            if(c==Long.TYPE)        return "J";
            if(c==Short.TYPE)       return "S";

            throw new IllegalArgumentException(c.getName());
        }
        return 'L'+c.getName().replace('.','/')+';';
    }



    public static byte[] tailor( Class templateClass, String newClassName, String... replacements ) {
        String vmname = toVMClassName(templateClass);
        return tailor(
            templateClass.getClassLoader().getResourceAsStream(vmname+".class"),
            vmname, newClassName, replacements );
    }


    /**
     * Customizes a class file by replacing constant pools.
     *
     * @param image
     *      The image of the template class.
     * @param replacements
     *      A list of pair of strings that specify the substitution
     *      {@code String[]{search_0, replace_0, search_1, replace_1, ..., search_n, replace_n }}
     *
     *      The search strings found in the constant pool will be replaced by the corresponding
     *      replacement string.
     */
    public static byte[] tailor( InputStream image, String templateClassName, String newClassName, String... replacements ) {
        DataInputStream in = new DataInputStream(image);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
            DataOutputStream out = new DataOutputStream(baos);

            // skip until the constant pool count
            long l = in.readLong();
            out.writeLong(l);

            // read the constant pool size
            short count = in.readShort();
            out.writeShort(count);

            // replace constant pools
            for( int i=0; i<count; i++ ) {
                byte tag = in.readByte();
                out.writeByte(tag);
                switch(tag) {
                case 0:
                    // this isn't described in the spec,
                    // but class files often seem to have this '0' tag.
                    // we can apparently just ignore it, but not sure
                    // what this really means.
                    break;

                case 1: // CONSTANT_UTF8
                    {
                        String value = in.readUTF();
                        if(value.equals(templateClassName))
                            value = newClassName;
                        else {
                            for( int j=0; j<replacements.length; j+=2 )
                                if(value.equals(replacements[j])) {
                                    value = replacements[j+1];
                                    break;
                                }
                        }
                        out.writeUTF(value);
                    }
                break;

                case 3: // CONSTANT_Integer
                case 4: // CONSTANT_Float
                    out.writeInt(in.readInt());
                    break;

                case 5: // CONSTANT_Long
                case 6: // CONSTANT_Double
                    i++; // doubles and longs take two entries
                    out.writeLong(in.readLong());
                    break;

                case 7: // CONSTANT_Class
                case 8: // CONSTANT_String
                    out.writeShort(in.readShort());
                    break;

                case 9: // CONSTANT_Fieldref
                case 10: // CONSTANT_Methodref
                case 11: // CONSTANT_InterfaceMethodref
                case 12: // CONSTANT_NameAndType
                    out.writeInt(in.readInt());
                    break;

                default:
                    throw new IllegalArgumentException("Unknown constant type "+tag);
                }
            }

            // then copy the rest
            byte[] buf = new byte[512];
            int len;
            while((len=in.read(buf))>0)
                out.write(buf,0,len);

            in.close();
            out.close();

            // by now we got the properly tailored class file image
            return baos.toByteArray();

        } catch( IOException e ) {
            // never happen
            logger.log(Level.WARNING,"failed to tailor",e);
            return null;
        }
    }
}
