/*
  @test
  @bug 4101435 8238436
  @summary Frame.setLocation(int,int)works unstable when the Frame is visible
  @author mohamed@siptech.co.in  : area= awt.Component
  @key headful
*/

import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Robot;

/**
 * This test  sets location of frame 100 times and
 * counts how many times the location was set incorrectly.
 * if the Frame location is set incorrectly,
 * the test will throw the exception."
 */
public class FrameLocationTest {

    //Declare things used in the test, like buttons and labels here


    private static int count = 0;
    private static Frame my_frame;
    private static Robot robot;

    public static void main(final String[] args) throws Exception {
        FrameLocationTest app = new FrameLocationTest();
        app.init();
        app.start();
    }

    public void init() throws Exception {

        //Create instructions for the user here, as well as set up
        // the environment -- set the layout manager, add buttons,
        // etc.
        // the frame instance is created with title 'TestFrame'.
        my_frame  = new Frame("TestFrame");

        //frame size setting
        my_frame.setSize(150,100);

        robot = new Robot();
        robot.setAutoDelay(100);

    }  //End  init()


    public void start () {

        //Get things going.  Request focus, set size, et cetera

        //Visiblity setting for frame.
        my_frame.setVisible(true);
        robot.waitForIdle();
        robot.delay(200);

        // the following loop calls user defined method,it will return boolean value.
        // if it is true(ie., the Frame locations are set correctly) the bug is not reproducible.
        // false,  bug is reproducible(ie., the Frame locations are set incorrectly ).

        setFrameLocation(my_frame);
    }


    // user defined method
    // This method  sets location of frame 100 times and
    // counts how many times the location was set incorrectly.
    // if count >0 this method will  return false ,otherwise true
    public void setFrameLocation(Frame frame) {
        int height, width;
        int x, y;
        int actualX, actualY;
        int i;

        int minimumX = frame.getLocation().x;
        int minimumY = frame.getLocation().y;

        height = Toolkit.getDefaultToolkit().getScreenSize().height;
        width = Toolkit.getDefaultToolkit().getScreenSize().width;

        // This loop will execute 100 times generating random values
        // which are used to set the frame Location
        for (i=0; i<100; i++) {
            // Random values
            x = (new Double(Math.random()*(width-300))).intValue();
            y = (new Double(Math.random()*(height-400))).intValue();

            // The x, y can not be less than minimum possible values for the
            //platform.
            if (x < minimumX || y < minimumY) {
                i--;
                continue;
            }

            // Based on x& y,the frame location will set
            frame.setLocation(x,y);
            robot.waitForIdle();
            robot.delay(200);

            actualX = frame.getLocation().x;
            actualY = frame.getLocation().y;

            // if setLoction() is set incorrectly, the value of count will be increased
            if (actualX != x || actualY != y) {
                System.out.println("Failure.  Expected: (" + x + ", " + y + ")" +
                        "  Saw: (" + actualX + ", " + actualY + ")");
                count++;
            }
        }

        frame.dispose();
        if (count > 0)
            throw new RuntimeException("Frame.setLocation(int, int) failed");
    }

} //class FrameLocationTest end
