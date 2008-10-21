/*
 * MyList.java
 *
 * Created on Oct 23, 2007, 2:45:57 PM
 *
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author sjiang
 */

import java.io.IOException;
import java.util.ArrayList;
import javax.management.event.FetchingEventForwarder;

public class MyFetchingEventForwarder extends FetchingEventForwarder {

    public MyFetchingEventForwarder() {
        super(1000);
        shared = this;
        setList(myList);
    }

    public void setAgain() {
        setList(myList);
    }

    public void setClientId(String clientId) throws IOException {
        used = true;
        super.setClientId(clientId);
    }

    public boolean isUsed() {
        return used;
    }

    private class MyList<TargetedNotification>
            extends ArrayList<TargetedNotification> {

        public boolean add(TargetedNotification e) {
            used = true;

            return super.add(e);
        }
    }

    public MyList myList = new MyList();
    public static MyFetchingEventForwarder shared;
    private boolean used = false;
}
