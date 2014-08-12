/*
 * Copyright (c) 1995, 2014, Oracle and/or its affiliates. All rights reserved.
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

package sun.applet;

import java.awt.Toolkit;
import java.awt.Image;
import java.lang.ref.SoftReference;
import sun.awt.image.URLImageSource;
import java.net.URL;

class AppletImageRef {
    private SoftReference<Image> soft = null;

    URL url;

    /**
     * Returns a pointer to the object referenced by this Ref.  If the object
     * has been thrown away by the garbage collector, it will be
     * reconstituted. This method does everything necessary to ensure that the garbage
     * collector throws things away in Least Recently Used(LRU) order.  Applications should
     * never override this method. The get() method effectively caches calls to
     * reconstitute().
     */
    public synchronized Image get() {
        Image t = check();
        if (t == null) {
            t = reconstitute();
            setThing(t);
        }
        return t;
    }

    /**
     * Create the Ref
     */
    AppletImageRef(URL url) {
        this.url = url;
    }

    /**
     * Flushes the cached object.  Forces the next invocation of get() to
     * invoke reconstitute().
     */
    public synchronized void flush() {
        SoftReference<Image> s = soft;
        if (s != null) s.clear();
        soft = null;
    }

    /**
     * Sets the thing to the specified object.
     * @param thing the specified object
     */
    public synchronized void setThing(Image thing) {
        flush();
        soft = new SoftReference<>(thing);
    }

    /**
     * Checks to see what object is being pointed at by this Ref and returns it.
     */
    public synchronized Image check() {
        SoftReference<Image> s = soft;
        if (s == null) return null;
        return s.get();
    }

    /**
     * Reconsitute the image.  Only called when the ref has been flushed.
     */
    public Image reconstitute() {
        Image img = Toolkit.getDefaultToolkit().createImage(new URLImageSource(url));
        return img;
    }
}
