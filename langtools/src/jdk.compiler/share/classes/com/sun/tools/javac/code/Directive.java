/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.code;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.ModuleSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;
import com.sun.tools.javac.util.List;


/**
 *  Root class for the directives that may appear in module compilation units.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public abstract class Directive implements ModuleElement.Directive {

    /** Flags for RequiresDirective. */
    public enum RequiresFlag {
        PUBLIC(0x0020),
        SYNTHETIC(0x1000),
        MANDATED(0x8000),
        EXTRA(0x10000);

        // overkill? move to ClassWriter?
        public static int value(Set<RequiresFlag> s) {
            int v = 0;
            for (RequiresFlag f: s)
                v |= f.value;
            return v;
        }

        RequiresFlag(int value) {
            this.value = value;
        }

        public final int value;
    }

    /**
     * 'exports' Package ';'
     */
    public static class ExportsDirective extends Directive
            implements ModuleElement.ExportsDirective {
        public final PackageSymbol packge;
        public final List<ModuleSymbol> modules;

        public ExportsDirective(PackageSymbol packge, List<ModuleSymbol> modules) {
            this.packge = packge;
            this.modules = modules;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public ModuleElement.DirectiveKind getKind() {
            return ModuleElement.DirectiveKind.EXPORTS;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public PackageElement getPackage() {
            return packge;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public java.util.List<? extends ModuleElement> getTargetModules() {
            return Collections.unmodifiableList(modules);
        }

        @Override
        public String toString() {
            if (modules == null)
                return "Exports[" + packge + "]";
            else
                return "Exports[" + packge + ":" + modules + "]";
        }
    }

    /**
     * 'provides' ServiceName 'with' QualifiedIdentifer ';'
     */
    public static class ProvidesDirective extends Directive
            implements ModuleElement.ProvidesDirective {
        public final ClassSymbol service;
        public final ClassSymbol impl;

        public ProvidesDirective(ClassSymbol service, ClassSymbol impl) {
            this.service = service;
            this.impl = impl;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public ModuleElement.DirectiveKind getKind() {
            return ModuleElement.DirectiveKind.PROVIDES;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public TypeElement getService() {
            return service;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public TypeElement getImplementation() {
            return impl;
        }

        @Override
        public String toString() {
            return "Provides[" + service + "," + impl + "]";
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ProvidesDirective)) {
                return false;
            }
            ProvidesDirective other = (ProvidesDirective)obj;
            return service == other.service && impl == other.impl;
        }

        @Override
        public int hashCode() {
            return service.hashCode() * 31 + impl.hashCode() * 37;
        }
    }

    /**
     * 'requires' ['public'] ModuleName ';'
     */
    public static class RequiresDirective extends Directive
            implements ModuleElement.RequiresDirective {
        public final ModuleSymbol module;
        public final Set<RequiresFlag> flags;

        public RequiresDirective(ModuleSymbol module) {
            this(module, EnumSet.noneOf(RequiresFlag.class));
        }

        public RequiresDirective(ModuleSymbol module, Set<RequiresFlag> flags) {
            this.module = module;
            this.flags = flags;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public ModuleElement.DirectiveKind getKind() {
            return ModuleElement.DirectiveKind.REQUIRES;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public boolean isPublic() {
            return flags.contains(RequiresFlag.PUBLIC);
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public ModuleElement getDependency() {
            return module;
        }

        @Override
        public String toString() {
            return "Requires[" + flags + "," + module + "]";
        }
    }

    /**
     * 'uses' ServiceName ';'
     */
    public static class UsesDirective extends Directive
            implements ModuleElement.UsesDirective {
        public final ClassSymbol service;

        public UsesDirective(ClassSymbol service) {
            this.service = service;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public ModuleElement.DirectiveKind getKind() {
            return ModuleElement.DirectiveKind.USES;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public TypeElement getService() {
            return service;
        }

        @Override
        public String toString() {
            return "Uses[" + service + "]";
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof UsesDirective)) {
                return false;
            }
            UsesDirective other = (UsesDirective)obj;
            return service == other.service;
        }

        @Override
        public int hashCode() {
            return service.hashCode() * 31;
        }
    }
}
