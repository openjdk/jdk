/**
 * @test /nodynamiccopyright/
 * @bug 6470588
 * @summary Verify that \\@SuppressWarnings("deprecation") works OK for all parts
 *          of class/method/field "header", including (declaration) annotations
 * @build VerifySuppressWarnings
 * @compile/ref=T6480588.out -XDrawDiagnostics -Xlint:unchecked,deprecation,cast T6480588.java
 * @run main VerifySuppressWarnings T6480588.java
 */

@DeprecatedAnnotation
class T6480588 extends DeprecatedClass implements DeprecatedInterface {
    @DeprecatedAnnotation
    public DeprecatedClass method(DeprecatedClass param) throws DeprecatedClass {
        DeprecatedClass lv = new DeprecatedClass();
        @Deprecated
        DeprecatedClass lvd = new DeprecatedClass();
        return null;
    }

    @Deprecated
    public void methodD() {
    }

    @DeprecatedAnnotation
    DeprecatedClass field = new DeprecatedClass();

    @DeprecatedAnnotation
    class Inner extends DeprecatedClass implements DeprecatedInterface {
    }

}

@Deprecated class DeprecatedClass extends Throwable { }
@Deprecated interface DeprecatedInterface { }
@Deprecated @interface DeprecatedAnnotation { }
