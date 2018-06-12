/*
 * Copyright (c) 2002-2016, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline.internal;

import java.util.regex.Pattern;

/**
 * Ansi support.
 *
 * @author <a href="mailto:gnodet@gmail.com">Guillaume Nodet</a>
 * @since 2.13
 */
public class Ansi {

    public static String stripAnsi(String str) {
        if (str == null) return "";
        return ANSI_CODE_PATTERN.matcher(str).replaceAll("");
        //was:
//        try {
//            ByteArrayOutputStream baos = new ByteArrayOutputStream();
//            AnsiOutputStream aos = new AnsiOutputStream(baos);
//            aos.write(str.getBytes());
//            aos.close();
//            return baos.toString();
//        } catch (IOException e) {
//            return str;
//        }
    }

    public static final Pattern ANSI_CODE_PATTERN = Pattern.compile("\033\\[[\060-\077]*[\040-\057]*[\100-\176]");

}
