package com.sun.org.apache.xml.internal.security.c14n.implementations;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public class UtfHelpper {

        final static void writeByte(final String str,final OutputStream out,Map cache) throws IOException {
                   byte []result=(byte[]) cache.get(str);
                   if (result==null) {
                           result=getStringInUtf8(str);
                           cache.put(str,result);
                   }

                   out.write(result);

           }

        final static void writeCharToUtf8(final char c,final OutputStream out) throws IOException{
                if (c < 0x80) {
                out.write(c);
                return;
            }
                if ((c >= 0xD800 && c <= 0xDBFF) || (c >= 0xDC00 && c <= 0xDFFF) ){
                //No Surrogates in sun java
                out.write(0x3f);
                return;
        }
            int bias;
            int write;
            char ch;
            if (c > 0x07FF) {
                ch=(char)(c>>>12);
                write=0xE0;
                if (ch>0) {
                    write |= ( ch & 0x0F);
                }
                out.write(write);
                write=0x80;
                bias=0x3F;
            } else {
                write=0xC0;
                bias=0x1F;
            }
            ch=(char)(c>>>6);
            if (ch>0) {
                 write|= (ch & bias);
            }
            out.write(write);
            out.write(0x80 | ((c) & 0x3F));

           }

        final static void writeStringToUtf8(final String str,final OutputStream out) throws IOException{
                final int length=str.length();
                int i=0;
            char c;
                while (i<length) {
                        c=str.charAt(i++);
                if (c < 0x80)  {
                    out.write(c);
                    continue;
                }
                if ((c >= 0xD800 && c <= 0xDBFF) || (c >= 0xDC00 && c <= 0xDFFF) ){
                        //No Surrogates in sun java
                        out.write(0x3f);
                        continue;
                }
                char ch;
                int bias;
                int write;
                if (c > 0x07FF) {
                    ch=(char)(c>>>12);
                    write=0xE0;
                    if (ch>0) {
                        write |= ( ch & 0x0F);
                    }
                    out.write(write);
                    write=0x80;
                    bias=0x3F;
                } else {
                        write=0xC0;
                        bias=0x1F;
                }
                ch=(char)(c>>>6);
                if (ch>0) {
                     write|= (ch & bias);
                }
                out.write(write);
                out.write(0x80 | ((c) & 0x3F));

                }

           }
        public final static byte[] getStringInUtf8(final String str) {
                   final int length=str.length();
                   boolean expanded=false;
                   byte []result=new byte[length];
                        int i=0;
                        int out=0;
                    char c;
                        while (i<length) {
                                c=str.charAt(i++);
                        if ( c < 0x80 ) {
                            result[out++]=(byte)c;
                            continue;
                        }
                        if ((c >= 0xD800 && c <= 0xDBFF) || (c >= 0xDC00 && c <= 0xDFFF) ){
                                   //No Surrogates in sun java
                                   result[out++]=0x3f;

                                continue;
                        }
                        if (!expanded) {
                                byte newResult[]=new byte[3*length];
                                        System.arraycopy(result, 0, newResult, 0, out);
                                        result=newResult;
                                        expanded=true;
                        }
                        char ch;
                        int bias;
                        byte write;
                        if (c > 0x07FF) {
                            ch=(char)(c>>>12);
                            write=(byte)0xE0;
                            if (ch>0) {
                                write |= ( ch & 0x0F);
                            }
                            result[out++]=write;
                            write=(byte)0x80;
                            bias=0x3F;
                        } else {
                                write=(byte)0xC0;
                                bias=0x1F;
                        }
                        ch=(char)(c>>>6);
                        if (ch>0) {
                             write|= (ch & bias);
                        }
                        result[out++]=write;
                        result[out++]=(byte)(0x80 | ((c) & 0x3F));/**/

                        }
                        if (expanded) {
                                byte newResult[]=new byte[out];
                                System.arraycopy(result, 0, newResult, 0, out);
                                result=newResult;
                        }
                        return result;
           }



}
