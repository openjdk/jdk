/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package compiler.lib.template_framework;

import java.util.Map;

/**
 * TODO public?
 */
public abstract class CodeGenerator {
    public abstract String name();
    public abstract int fuelCost();
    public abstract void instantiate(Scope scope, Parameters parameters);

    public final class Instantiator {
        CodeGenerator codeGenerator;
        Parameters parameters;
        boolean isUsed;

	Instantiator(CodeGenerator codeGenerator) {
            this.codeGenerator = codeGenerator;
            parameters = new Parameters();
        }

        public Instantiator where(String paramKey, String paramValue) {
            parameters.add(paramKey, paramValue);
            return this;
        }

        public Instantiator where(Map<String,String> argumentsMap) {
            parameters.add(argumentsMap);
            return this;
        }

        public String instantiate() {
            if (isUsed) {
                throw new TemplateFrameworkException("Repeated use of Instantiator not allowed.");
            }
            isUsed = true;
            BaseScope scope = new BaseScope();
            codeGenerator.instantiate(scope, parameters);
            scope.close();
            return scope.toString();
        }

        public void instantiate(Scope scope) {
            if (isUsed) {
                throw new TemplateFrameworkException("Repeated use of Instantiator not allowed.");
            }
            isUsed = true;
            Scope nestedScope = new Scope(scope, scope.fuel);
            codeGenerator.instantiate(nestedScope, parameters);
            nestedScope.close();
            scope.stream.addCodeStream(nestedScope.stream);
        }
    }

    public final Instantiator where(String paramKey, String paramValue) {
        return new Instantiator(this).where(paramKey, paramValue);
    }

    public final Instantiator where(Map<String,String> argumentsMap) {
        return new Instantiator(this).where(argumentsMap);
    }

    public final String instantiate() {
        return new Instantiator(this).instantiate();
    }

    public final void instantiate(Scope scope) {
        new Instantiator(this).instantiate(scope);
    }
}
