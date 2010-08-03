/*
 * @test  /nodynamiccopyright/
 * @bug     6362067
 * @summary Messager methods do not print out source position information
 * @build   T6362067
 * @compile -processor T6362067 -proc:only T6362067.java
 * @compile/ref=T6362067.out -XDrawDiagnostics -processor T6362067 -proc:only T6362067.java
 */

import java.util.Set;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import static javax.tools.Diagnostic.Kind.*;

@Deprecated // convenient test annotation
@SupportedAnnotationTypes("*")
public class T6362067 extends AbstractProcessor {
    public boolean process(Set<? extends TypeElement> annos,
                           RoundEnvironment roundEnv) {
        Messager msgr = processingEnv.getMessager();
        for (Element e: roundEnv.getRootElements()) {
            msgr.printMessage(NOTE, "note:elem", e);
            for (AnnotationMirror a: e.getAnnotationMirrors()) {
                msgr.printMessage(NOTE, "note:anno", e, a);
                for (AnnotationValue v: a.getElementValues().values()) {
                    msgr.printMessage(NOTE, "note:value", e, a, v);
                }

            }
        }
        if (roundEnv.processingOver())
            msgr.printMessage(NOTE, "note:nopos");
        return true;
    }

    @Override
    public javax.lang.model.SourceVersion getSupportedSourceVersion() {
        return javax.lang.model.SourceVersion.latest();
    }
}
