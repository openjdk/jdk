/*
  @test %I% %E%
  @bug 6315717
  @summary verifies that MouseEvent CTOR accepts extra mouse button numbers
  @author Andrei Dmitriev : area=awt.event
  @run main AcceptExtraButton
 */

//package acceptextrabutton;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;

public class AcceptExtraButton extends Frame {
    static int [] eventID = new int []{MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED};

    public static void main(String []s){
        AcceptExtraButton f = new AcceptExtraButton();
        f.setSize(300, 300);
        f.setVisible(true);

        for (int buttonId = 0; buttonId<eventID.length; buttonId++) {
            for (int button = 0; button <= MouseInfo.getNumberOfButtons(); button++){
                System.out.println("button == "+button);
                MouseEvent me = new MouseEvent(f,
                                               eventID[buttonId],
                                               System.currentTimeMillis(),
                                               0, //MouseEvent.BUTTON1_DOWN_MASK, modifiers
                                               100, 100, // x, y
                                               150, 150, // x, y on screen
                                               1,        //clickCount
                                               false,              //popupTrigger
                                               button );// MouseEvent.NOBUTTON : button

                System.out.println("dispatching >>>"+me);
                f.dispatchEvent( ( AWTEvent )me );
            }
        }
        MouseAdapter ma1 = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    System.out.println("PRESSED "+e);
                }
                public void mouseReleased(MouseEvent e) {
                    System.out.println("RELEASED "+e);
                }
                public void mouseClicked(MouseEvent e) {
                    System.out.println("CLICKED "+e);
                }
            };
        f.addMouseListener(ma1);
    }
}
