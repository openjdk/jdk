/*
 * Copyright (c) 2002-2016, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.org.jline.reader;

import jdk.internal.org.jline.utils.AttributedString;

public interface Highlighter {

    AttributedString highlight(LineReader reader, String buffer);
}
