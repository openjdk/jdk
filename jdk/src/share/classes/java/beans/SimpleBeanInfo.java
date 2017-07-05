/*
 * Copyright (c) 1996, 2011, Oracle and/or its affiliates. All rights reserved.
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

package java.beans;

/**
 * This is a support class to make it easier for people to provide
 * BeanInfo classes.
 * <p>
 * It defaults to providing "noop" information, and can be selectively
 * overriden to provide more explicit information on chosen topics.
 * When the introspector sees the "noop" values, it will apply low
 * level introspection and design patterns to automatically analyze
 * the target bean.
 */

public class SimpleBeanInfo implements BeanInfo {

    /**
     * Deny knowledge about the class and customizer of the bean.
     * You can override this if you wish to provide explicit info.
     */
    public BeanDescriptor getBeanDescriptor() {
        return null;
    }

    /**
     * Deny knowledge of properties. You can override this
     * if you wish to provide explicit property info.
     */
    public PropertyDescriptor[] getPropertyDescriptors() {
        return null;
    }

    /**
     * Deny knowledge of a default property. You can override this
     * if you wish to define a default property for the bean.
     */
    public int getDefaultPropertyIndex() {
        return -1;
    }

    /**
     * Deny knowledge of event sets. You can override this
     * if you wish to provide explicit event set info.
     */
    public EventSetDescriptor[] getEventSetDescriptors() {
        return null;
    }

    /**
     * Deny knowledge of a default event. You can override this
     * if you wish to define a default event for the bean.
     */
    public int getDefaultEventIndex() {
        return -1;
    }

    /**
     * Deny knowledge of methods. You can override this
     * if you wish to provide explicit method info.
     */
    public MethodDescriptor[] getMethodDescriptors() {
        return null;
    }

    /**
     * Claim there are no other relevant BeanInfo objects.  You
     * may override this if you want to (for example) return a
     * BeanInfo for a base class.
     */
    public BeanInfo[] getAdditionalBeanInfo() {
        return null;
    }

    /**
     * Claim there are no icons available.  You can override
     * this if you want to provide icons for your bean.
     */
    public java.awt.Image getIcon(int iconKind) {
        return null;
    }

    /**
     * This is a utility method to help in loading icon images.
     * It takes the name of a resource file associated with the
     * current object's class file and loads an image object
     * from that file.  Typically images will be GIFs.
     * <p>
     * @param resourceName  A pathname relative to the directory
     *          holding the class file of the current class.  For example,
     *          "wombat.gif".
     * @return  an image object.  May be null if the load failed.
     */
    public java.awt.Image loadImage(final String resourceName) {
        try {
            final Class<?> c = getClass();
            java.awt.image.ImageProducer ip = (java.awt.image.ImageProducer)
                java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction<Object>() {
                    public Object run() {
                        java.net.URL url;
                        if ((url = c.getResource(resourceName)) == null) {
                            return null;
                        } else {
                            try {
                                return url.getContent();
                            } catch (java.io.IOException ioe) {
                                return null;
                            }
                        }
                    }
            });

            if (ip == null)
                return null;
            java.awt.Toolkit tk = java.awt.Toolkit.getDefaultToolkit();
            return tk.createImage(ip);
        } catch (Exception ex) {
            return null;
        }
    }

}
