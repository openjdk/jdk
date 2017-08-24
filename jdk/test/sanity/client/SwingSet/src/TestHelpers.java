
import java.awt.Dimension;
import java.awt.Point;

import org.netbeans.jemmy.operators.ComponentOperator;

public class TestHelpers {

    public static void checkChangeLocation(ComponentOperator component,
            Point finalLocation) {
        Point initialLocation = component.getLocation();
        component.setLocation(finalLocation);
        component.waitComponentLocation(finalLocation);
        component.setLocation(initialLocation);
    }

    public static void checkChangeSize(ComponentOperator component,
            Dimension dimensionFinal) {
        Dimension dimensionInitial = component.getSize();
        component.setSize(dimensionFinal);
        component.waitComponentSize(dimensionFinal);
        component.setSize(dimensionInitial);
    }
}
