package overskam.projectH.model;

public class AnnotatedFrame {

    private final int frameIndex;
    private final int polygonCount;

    public AnnotatedFrame(int frameIndex, int polygonCount) {
        this.frameIndex = frameIndex;
        this.polygonCount = polygonCount;
    }

    public int getFrameIndex() {
        return frameIndex;
    }

    public int getPolygonCount() {
        return polygonCount;
    }

    @Override
    public String toString() {
        return "Frame " + frameIndex + " (" + polygonCount + ")";
    }
}