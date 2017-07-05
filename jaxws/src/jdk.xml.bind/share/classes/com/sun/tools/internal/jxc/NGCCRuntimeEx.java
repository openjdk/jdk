/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.jxc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.sun.tools.internal.jxc.gen.config.NGCCRuntime;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;


/**
 * Controls the  validating and converting  of values obtained
 * from the config file.
 *
 * @author
 *     Bhakti Mehta (bhakti.mehta@sun.com)
 */
public final class NGCCRuntimeEx extends NGCCRuntime {
    /**
     * All the errors shall be sent to this object.
     */
    private final ErrorHandler errorHandler;

    public NGCCRuntimeEx(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    /**
     *  This will check if the baseDir provided by the user
     *  in the config file exists. If not it throws an error
     * @param baseDir
     *    The baseDir attribute passed by the user in the xml config file as a path
     * @return
     *     The file representation of the path name
     */
    public File getBaseDir(String baseDir) throws SAXException {
        File dir = new File(baseDir);
        if (dir.exists()) {
            return dir;
        } else {
            SAXParseException e = new SAXParseException(
                                Messages.BASEDIR_DOESNT_EXIST.format(dir.getAbsolutePath()),
                                getLocator());
            errorHandler.error(e);
            throw e;    // we can't recover from this error
        }
    }

    /**
     * This takes the include list provided by the user in the config file
     * It converts the user values to {@link Pattern}
     * @param includeContent
     *        The include list specified by the user
     * @return
     *        A list of regular expression patterns {@link Pattern}
     */
    public List<Pattern> getIncludePatterns(List<String> includeContent ) {
        List<Pattern> includeRegexList = new ArrayList<Pattern>();
        for (String includes : includeContent) {
            String regex = convertToRegex(includes);
            Pattern pattern = Pattern.compile(regex);
            includeRegexList.add(pattern);
        }
        return includeRegexList;
    }


    /**
     * This takes the exclude list provided by the user in the config file
     * It converts the user values to {@link Pattern}
     * @param excludeContent
     *        The exclude list specified by the user
     * @return
     *        A list of regular expression patterns {@link Pattern}
     */
    public List getExcludePatterns(List<String> excludeContent ) {
        List<Pattern> excludeRegexList = new ArrayList<Pattern>();
        for (String excludes : excludeContent) {
            String regex = convertToRegex(excludes);
            Pattern pattern = Pattern.compile(regex);
            excludeRegexList.add(pattern);
        }
        return excludeRegexList;
    }


    /**
     * This will tokenize the pattern and convert it into a regular expression
     * @param pattern
     */
    private String convertToRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        char nc = ' ';
        if (pattern.length() >0 ) {

            for ( int i = 0 ; i < pattern.length(); i ++ ) {
                char c = pattern.charAt(i);
                nc = ' ';
                if ((i +1) != pattern.length()) {
                    nc = pattern.charAt(i +1);
                }
                //escape single '.'
                if (c == '.' &&  nc != '.'){
                    regex.append('\\');
                    regex.append('.');
                    //do not allow patterns like a..b
                } else if (c == '.'){
                    continue;
                    // "**" gets replaced by ".*"
                } else if ((c=='*') && (nc == '*')) {
                    regex.append(".*");
                    break;
                    //'*' replaced by anything but '.' i.e [^\\.]+
                } else if (c=='*') {
                    regex.append("[^\\.]+");
                    continue;
                    //'?' replaced by anything but '.' i.e [^\\.]
                } else if (c=='?') {
                    regex.append("[^\\.]");
                    //else leave the chars as they occur in the pattern
                } else
                    regex.append(c);
            }

        }

        return regex.toString();
    }

    protected void unexpectedX(String token) throws SAXException {
        errorHandler.error(
            new SAXParseException(Messages.UNEXPECTED_NGCC_TOKEN.format(
                token, getLocator().getLineNumber(), getLocator().getColumnNumber()),
                getLocator()));
    }
}
