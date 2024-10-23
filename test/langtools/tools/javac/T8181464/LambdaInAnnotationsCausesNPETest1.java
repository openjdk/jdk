/* @test /nodynamiccopyright/
 * @bug 8181464
 * @summary Invalid lambda in annotation causes NPE in Lint.augment
 * @modules java.compiler
 *          jdk.compiler
 * @compile Anno.java AnnoProcessor.java
 * @compile -J-XX:+UnlockExperimentalVMOptions -J-XX:hashCode=2 Anno.java AnnoProcessor.java
 * @compile/fail/ref=LambdaInAnnotationsCausesNPETest1.out -XDrawDiagnostics -processor AnnoProcessor -proc:only LambdaInAnnotationsCausesNPETest1.java
 */

@Anno(value = x -> x)
class LambdaInAnnotationsCausesNPETest1 {}
