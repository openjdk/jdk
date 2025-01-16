/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;
import java.util.List;

@SupportedAnnotationTypes("*")
public class Processor extends AbstractProcessor {

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  int round = 1;

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    processingEnv.getMessager().printNote("round " + round);
    Element t = processingEnv.getElementUtils().getTypeElement("T8268575");
    for (Element e : t.getEnclosedElements()) {
      if (e instanceof ExecutableElement) {
        for (VariableElement p : ((ExecutableElement) e).getParameters()) {
            List<? extends AnnotationMirror> annos = p.getAnnotationMirrors();
            if (annos.size() != 1) {
                throw new RuntimeException("Missing annotation in round " + round);
            }
        }
      }
    }
    if (round == 1) {
      String name = "A";
      try {
        JavaFileObject jfo = processingEnv.getFiler().createSourceFile(name);
        try (Writer w = jfo.openWriter()) {
          w.write("@interface " + name + " {}");
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    round++;
    return false;
  }
}
