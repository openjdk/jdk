import java.math.*;
import java.io.ObjectStreamClass;

public class TestRational {
    public static void main(String[] args) {
        ObjectStreamClass c = ObjectStreamClass.lookup(Rational.class);
        System.out.println(c.getSerialVersionUID());
    }
}
