import java.awt.*;


class NextFramePositionCalculator {

    private final Frame currentFrame;

    public NextFramePositionCalculator(Frame currentFrame) {
        this.currentFrame = currentFrame;
    }

    public int getNextLocationX() {
        return currentFrame.getX() + currentFrame.getWidth();
    }

    public int getNextLocationY() {
        return currentFrame.getY();
    }

}
