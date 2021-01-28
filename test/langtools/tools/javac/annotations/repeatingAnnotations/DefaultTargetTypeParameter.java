import java.lang.annotation.*;

/**
 * @test /nodynamiccopyright/
 * @bug 8006547 8231436
 * @compile DefaultTargetTypeParameter.java
 */

@Target({
    ElementType.TYPE_PARAMETER,
})
@interface Container {
  DefaultTargetTypeParameter[] value();
}

@Repeatable(Container.class)
public @interface DefaultTargetTypeParameter {}
