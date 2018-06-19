import java.awt.Dimension;
import java.awt.Point;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import javax.swing.UIManager;
import org.netbeans.jemmy.operators.ComponentOperator;
import org.testng.annotations.DataProvider;

public class TestHelpers {

    /**
     * A DataProvider having the class name of all the available look and feels
     *
     * @return a 2d Object array containing the class name of all the available
     * look and feels
     */
    @DataProvider(name = "availableLookAndFeels")
    public static Object[][] provideAvailableLookAndFeels() {
        UIManager.LookAndFeelInfo LookAndFeelInfos[]
                = UIManager.getInstalledLookAndFeels();
        Object[][] lookAndFeels = new Object[LookAndFeelInfos.length][1];
        for (int i = 0; i < LookAndFeelInfos.length; i++) {
            lookAndFeels[i][0] = LookAndFeelInfos[i].getClassName();
        }
        return lookAndFeels;
    }

    public static void checkChangeLocation(ComponentOperator component,
            Point finalLocation) {
        Point initialLocation = component.getLocation();
        component.setLocation(finalLocation);
        component.waitComponentLocation(finalLocation);
        component.setLocation(initialLocation);
        component.waitComponentLocation(initialLocation);
    }

    public static void checkChangeSize(ComponentOperator component,
            Dimension dimensionFinal) {
        Dimension dimensionInitial = component.getSize();
        component.setSize(dimensionFinal);
        component.waitComponentSize(dimensionFinal);
        component.setSize(dimensionInitial);
        component.waitComponentSize(dimensionInitial);
    }

}
