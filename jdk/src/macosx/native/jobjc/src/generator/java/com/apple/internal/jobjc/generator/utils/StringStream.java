/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.internal.jobjc.generator.utils;

import java.io.StringWriter;

/**
 * Stream-like class backed by a string. Useful for parsers.
 */
public class StringStream{
    private String data;
    private int pos;

    public StringStream(String s){
        QA.nonNull(s);
        this.data = s;
        this.pos = 0;
    }

    /**
     * Number of characters left.
     */
    public int left(){ return data.length() - pos; }

    /**
     * Are there any characters left?
     */
    public boolean atEOF(){ return left() <= 0; }

    /**
     * Read next character.
     */
    public char read(){ return data.charAt(pos++); }

    /**
     * Read n characters and return string.
     */
    public String readN(int n){
        String s = data.substring(pos, pos + n);
        pos += n;
        return s;
    }

    /**
     * Read until the next char is c, and return the string.
     */
    public String readUntil(char c){
        int ix = data.indexOf(c, pos);
        if(ix == -1) throw new RuntimeException("readUntil did not find character '" + c + "'");
        return readN(data.indexOf(c, pos) - pos);
    }

    /**
     * Read until the next char is one in s, and return the string.
     */
    public String readUntilEither(String s) {
        int ix = Integer.MAX_VALUE;

        for(char c : s.toCharArray()){
            int ixx = data.indexOf(c, pos);
            if(ixx >= 0 && ixx < ix)
                ix = ixx;
        }

        if(ix == -1) throw new RuntimeException("readUntilEither did not find any character in '" + s + "'");
        return readN(ix - pos);
    }

    public String readWhile(String s) {
        StringWriter sw = new StringWriter();
        while(s.indexOf(peek()) != -1)
            sw.append(read());
        return sw.toString();
    }

    public String readWhileDigits() {
        return readWhile("0123456789");
    }

    /**
     * @return the nth char from the current position.
     */
    public char peekAt(int n){ return data.charAt(pos + n); }

    /**
     * @return the next n chars.
     */
    public String peekN(int n){ return data.substring(pos, pos + n); }

    /**
     * @return the next char.
     */
    public char peek(){ return peekAt(0); }

    /**
     * Skip n chars ahead.
     */
    public void seekN(int n){ pos += n; }

    /**
     * Skip 1 char ahead.
     */
    public void seek(){ seekN(1); }

    /**
     * If the next character is c, seek over it. Otherwise throw RuntimeException.
     */
    public void eat(char c) {
        if(peek() != c) throw new RuntimeException("Parser expected '" + c + "' but got '" + peek() + "'.");
        seek();
    }

    /**
     * If the next characters are the same as those in s, seek over them. Otherwise throw RuntimeException.
     */
    public void eat(String s) {
        String pn = peekN(s.length());
        if(!pn.equals(s)) throw new RuntimeException("Parser expected '" + s + "' but got '" + pn + "'.");
        seekN(s.length());
    }

    @Override
    public String toString(){
        return data;
    }

    /**
     * @return the remaining characters as a String.
     */
    public String remainingToString() {
        return data.substring(pos);
    }
}
