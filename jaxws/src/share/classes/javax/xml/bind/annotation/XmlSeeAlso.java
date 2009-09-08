/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package javax.xml.bind.annotation;

import javax.xml.bind.JAXBContext;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * Instructs JAXB to also bind other classes when binding this class.
 *
 * <p>
 * Java makes it impractical/impossible to list all sub-classes of
 * a given class. This often gets in a way of JAXB users, as it JAXB
 * cannot automatically list up the classes that need to be known
 * to {@link JAXBContext}.
 *
 * <p>
 * For example, with the following class definitions:
 *
 * <pre>
 * class Animal {}
 * class Dog extends Animal {}
 * class Cat extends Animal {}
 * </pre>
 *
 * <p>
 * The user would be required to create {@link JAXBContext} as
 * <tt>JAXBContext.newInstance(Dog.class,Cat.class)</tt>
 * (<tt>Animal</tt> will be automatically picked up since <tt>Dog</tt>
 * and <tt>Cat</tt> refers to it.)
 *
 * <p>
 * {@link XmlSeeAlso} annotation would allow you to write:
 * <pre>
 * &#64;XmlSeeAlso({Dog.class,Cat.class})
 * class Animal {}
 * class Dog extends Animal {}
 * class Cat extends Animal {}
 * </pre>
 *
 * <p>
 * This would allow you to do <tt>JAXBContext.newInstance(Animal.class)</tt>.
 * By the help of this annotation, JAXB implementations will be able to
 * correctly bind <tt>Dog</tt> and <tt>Cat</tt>.
 *
 * @author Kohsuke Kawaguchi
 * @since JAXB2.1
 * @version $Revision: 1.1 $
 */
@Target({ElementType.TYPE})
@Retention(RUNTIME)
public @interface XmlSeeAlso {
    Class[] value();
}
