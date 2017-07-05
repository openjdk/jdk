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

package com.sun.tools.internal.ws.processor.util;

import java.io.File;

/**
 * A container to hold info on the files that get
 * generated.
 *
 * @author WS Development Team
 */
public class GeneratedFileInfo {

    /**
     * local variables
     */
    private File file = null;
    private String type = null;

    /* constructor */
    public GeneratedFileInfo() {}

    /**
     * Adds the file object to the container
     *
     * @param file instance of the file to be added
     */
    public void setFile( File file ) {
        this.file = file;
    }

    /**
     * Adds the type of file it is the container
     *
     * @param type string which specifices the type
     */
    public void setType( String type ) {
        this.type = type;
    }

    /**
     * Gets the file that got added
     *
     * @return File that got added
     */
    public File getFile() {
        return( file );
    }

    /**
     * Get the file type that got added
     *
     * @return File type of datatype String
     */
    public String getType() {
        return ( type );
    }
}
