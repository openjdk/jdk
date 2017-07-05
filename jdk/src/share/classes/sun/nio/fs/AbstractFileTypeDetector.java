/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.fs;

import java.nio.file.FileRef;
import java.nio.file.spi.FileTypeDetector;
import java.io.IOException;
import sun.nio.fs.MimeType;

/**
 * Base implementation of FileTypeDetector
 */

public abstract class AbstractFileTypeDetector
    extends FileTypeDetector
{
    protected AbstractFileTypeDetector() {
        super();
    }

    /**
     * Invokes the implProbeContentType method to guess the file's content type,
     * and this validates that the content type's syntax is valid.
     */
    @Override
    public final String probeContentType(FileRef file) throws IOException {
        if (file == null)
            throw new NullPointerException("'file' is null");
        String result = implProbeContentType(file);
        if (result != null) {
            // check the content type
            try {
                MimeType.parse(result);
            } catch (IllegalArgumentException ignore) {
                result = null;
            }
        }
        return result;
    }

    /**
     * Probes the given file to guess its content type.
     */
    protected abstract String implProbeContentType(FileRef file)
        throws IOException;
}
