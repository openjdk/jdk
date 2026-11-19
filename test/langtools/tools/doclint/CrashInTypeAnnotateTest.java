/*
 * @test /nodynamiccopyright/
 * @bug 8370237
 * @summary AssertionError in Annotate.fromAnnotations with -Xdoclint and type annotations
 * @compile/fail/ref=CrashInTypeAnnotateTest.out -Xdoclint:all,-missing -XDrawDiagnostics CrashInTypeAnnotateTest.java
 */

import java.util.List;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE_PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@interface A {}

/** {@link List<@A String>}
 */
class CrashInTypeAnnotateTest {
}
