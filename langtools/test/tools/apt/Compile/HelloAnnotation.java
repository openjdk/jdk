/* /nodynamiccopyright/ */
import java.lang.annotation.*;
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@HelloAnnotation
@interface HelloAnnotation {
    Target value() default @Target(ElementType.METHOD);
}
