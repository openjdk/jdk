/*
 * Copyright 1994-1999 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.net.www.content.image;

import java.net.URL;
import java.net.URLConnection;
import java.net.*;
import sun.awt.image.*;
import java.io.InputStream;
import java.io.IOException;
import java.awt.Image;
import java.awt.Toolkit;


public class gif extends ContentHandler {
    public Object getContent(URLConnection urlc) throws java.io.IOException {
        return new URLImageSource(urlc);
    }

    public Object getContent(URLConnection urlc, Class[] classes) throws IOException {
        for (int i = 0; i < classes.length; i++) {
          if (classes[i].isAssignableFrom(URLImageSource.class)) {
                return new URLImageSource(urlc);
          }
          if (classes[i].isAssignableFrom(Image.class)) {
            Toolkit tk = Toolkit.getDefaultToolkit();
            return tk.createImage(new URLImageSource(urlc));
          }
        }
        return null;
    }
}
