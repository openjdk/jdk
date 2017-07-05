/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
package javax.management;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to allow an MBean to provide its name.
 * This annotation can be used on the following types:
 * <ul>
 *   <li>MBean or MXBean Java interface.</li>
 *   <li>Java class annotated with {@link javax.management.MBean &#64;MBean}</code>
 * annotation.</li>
 *   <li>Java class annotated with {@link javax.management.MXBean &#64;MXBean}</code>
 * annotation.</li>
 * </ul>
 *
 * <p>The value of this annotation is used to build the <code>ObjectName</code>
 * when instances of the annotated type are registered in
 * an <code>MBeanServer</code> and no explicit name is given to the
 * {@code createMBean} or {@code registerMBean} method (the {@code ObjectName}
 * is {@code null}).</p>
 *
 * <p>For Dynamic MBeans, which define their own {@code MBeanInfo}, you can
 * produce the same effect as this annotation by including a field
 * <a href="Descriptor.html#objectNameTemplate">{@code objectNameTemplate}</a>
 * in the {@link Descriptor} for the {@code MBeanInfo} returned by
 * {@link DynamicMBean#getMBeanInfo()}.</p>
 *
 * <p>For Standard MBeans and MXBeans, this annotation automatically produces
 * an {@code objectNameTemplate} field in the {@code Descriptor}.</p>
 *
 * <p>The template can contain variables so that the name of the MBean
 * depends on the value of one or more of its attributes.
 * A variable that identifies an MBean attribute is of the form
 * <code>{<em>attribute name</em>}</code>. For example, to make an MBean name
 * depend on the <code>Name</code> attribute, use the variable
 * <code>{Name}</code>. Attribute names are case sensitive.
 * Naming attributes can be of any type. The <code>String</code> returned by
 * <code>toString()</code> is included in the constructed name.</p>
 *
 * <p>If you need the attribute value to be quoted
 * by a call to {@link ObjectName#quote(String) ObjectName.quote},
 * surround the variable with quotes. Quoting only applies to key values.
 * For example, <code>@ObjectNameTemplate("java.lang:type=MemoryPool,name=\"{Name}\"")</code>,
 * quotes the <code>Name</code> attribute value. You can notice the "\"
 * character needed to escape a quote within a <code>String</code>. A name
 * produced by this template might look like
 * {@code java.lang:type=MemoryPool,name="Code Cache"}.</p>
 *
 * <p>Variables can be used anywhere in the <code>String</code>.
 * Be sure to make the template derived name comply with
 * {@link ObjectName ObjectName} syntax.</p>
 *
 * <p>If an MBean is registered with a null name and it implements
 * {@link javax.management.MBeanRegistration MBeanRegistration}, then
 * the computed name is provided to the <code>preRegister</code> method.
 * Similarly,
 * if the MBean uses <a href="MBeanRegistration.html#injection">resource
 * injection</a> to discover its name, it is the computed name that will
 * be injected.</p>
 * <p>All of the above can be used with the {@link StandardMBean} class and
 * the annotation is effective in that case too.</p>
 * <p>If any exception occurs (such as unknown attribute, invalid syntax or
 * exception
 * thrown by the MBean) when the name is computed it is wrapped in a
 * <code>NotCompliantMBeanException</code>.</p>
 * <p>Some ObjectName template examples:
 * <ul><li>"com.example:type=Memory". Fixed ObjectName. Used to name a
 * singleton MBean.</li>
 * <li>"com.example:type=MemoryPool,name={Name}". Variable ObjectName.
 * <code>Name</code> attribute is retrieved to compose the <code>name</code>
 * key value.</li>
 * <li>"com.example:type=SomeType,name={InstanceName},id={InstanceId}".
 * Variable ObjectName.
 * <code>InstanceName</code> and <code>InstanceId</code> attributes are
 * retrieved to compose respectively
 * the <code>name</code> and <code>id</code> key values.</li>
 * <li>"com.example:type=OtherType,name=\"{ComplexName}\"". Variable ObjectName.
 * <code>ComplexName</code> attribute is retrieved to compose the
 * <code>name</code> key quoted value.</li> </li>
 * <li>"com.example:{TypeKey}=SomeOtherType". Variable ObjectName.
 * <code>TypeKey</code> attribute is retrieved to compose the
 * first key name.</li>
 * * <li>"{Domain}:type=YetAnotherType". Variable ObjectName.
 * <code>Domain</code> attribute is retrieved to compose the
 * management domain.</li>
 * <li>"{Naming}". Variable ObjectName.
 * <code>Naming</code> attribute is retrieved to compose the
 * complete name.</li>
 * </ul>
 * </p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ObjectNameTemplate {

    /**
     * The MBean name template.
     * @return The MBean name template.
     */
    @DescriptorKey("objectNameTemplate")
    public String value();
}
