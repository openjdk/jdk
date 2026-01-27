import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@MyTypeAnnotation
public record MyRecord(@MyTypeUseAnnotation String filter) {
    public static MyRecord parse(String param) {
        if (param == null) {
            throw new IllegalArgumentException("Filter cannot be null");
        }
        return new MyRecord(param);
    }
}

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@interface MyTypeAnnotation {
}

@Target({ ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
@interface MyTypeUseAnnotation {
}
