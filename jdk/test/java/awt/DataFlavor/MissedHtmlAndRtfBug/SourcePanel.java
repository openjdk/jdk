import java.awt.dnd.DragSource;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.*;

public class SourcePanel extends Panel {

    private final MyDragGestureListener dragGestureListener =
            new MyDragGestureListener();

    public SourcePanel() {
        setPreferredSize(new Dimension(200, 200));
        DragSource defaultDragSource =
                DragSource.getDefaultDragSource();
        defaultDragSource.createDefaultDragGestureRecognizer(this,
                DnDConstants.ACTION_COPY_OR_MOVE, dragGestureListener);
        setBackground(Color.RED);
    }

    private class MyDragGestureListener implements DragGestureListener {
        public void dragGestureRecognized(DragGestureEvent dge) {
            dge.startDrag(null, new MyTransferable());
        }
    }
}
