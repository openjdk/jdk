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
package com.sun.xml.internal.rngom.dt;

import org.relaxng.datatype.DatatypeLibraryFactory;
import org.relaxng.datatype.DatatypeLibrary;
import org.relaxng.datatype.Datatype;
import org.relaxng.datatype.DatatypeBuilder;
import org.relaxng.datatype.DatatypeException;
import org.relaxng.datatype.ValidationContext;
import org.relaxng.datatype.DatatypeStreamingValidator;
import org.relaxng.datatype.helpers.StreamingValidatorImpl;

/**
 * {@link DatatypeLibraryFactory} implementation
 * that returns a dummy {@link Datatype}.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public final class DoNothingDatatypeLibraryFactoryImpl implements DatatypeLibraryFactory {
    public DatatypeLibrary createDatatypeLibrary(String s) {
        return new DatatypeLibrary() {

            public Datatype createDatatype(String s) throws DatatypeException {
                return createDatatypeBuilder(s).createDatatype();
            }

            public DatatypeBuilder createDatatypeBuilder(String s) throws DatatypeException {
                return new DatatypeBuilder() {
                    public void addParameter(String s, String s1, ValidationContext validationContext) throws DatatypeException {
                    }

                    public Datatype createDatatype() throws DatatypeException {
                        return new Datatype() {

                            public boolean isValid(String s, ValidationContext validationContext) {
                                return false;
                            }

                            public void checkValid(String s, ValidationContext validationContext) throws DatatypeException {
                            }

                            public DatatypeStreamingValidator createStreamingValidator(ValidationContext validationContext) {
                                return new StreamingValidatorImpl(this,validationContext);
                            }

                            public Object createValue(String s, ValidationContext validationContext) {
                                return null;
                            }

                            public boolean sameValue(Object o, Object o1) {
                                return false;
                            }

                            public int valueHashCode(Object o) {
                                return 0;
                            }

                            public int getIdType() {
                                return ID_TYPE_NULL;
                            }

                            public boolean isContextDependent() {
                                return false;
                            }
                        };
                    }
                };
            }
        };
    }
}
