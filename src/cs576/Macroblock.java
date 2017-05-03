package cs576;

/**
 * Created by Jeffreye on 4/2/2017.
 */
public class Macroblock {

    static final int MACROBLOCK_LENGTH = 16;

    public static final int BACKGROUND_LAYER = 0;

    //    public int vectorCount;
    private int layer;
    private int referenceLayer;
    private int x;
    private int y;
    private int index;
    private Frame frame;

    private MotionVector motionVector;

    public Macroblock(Frame frame, int x, int y, int blockIndex) {
        this.frame = frame;
        this.x = x;
        this.y = y;
        this.index = blockIndex;
        this.motionVector = null;
    }

    public int dist2(int x, int y) {
        return Math.max(Math.abs(x - this.x), Math.abs(y - this.y));
    }

    public int getLayer() {
        return layer;
    }

    public int getReferenceLayer() {
        return referenceLayer;
    }

    public boolean isBackgroundLayer() {
        return layer == BACKGROUND_LAYER;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }

    public void setReferenceLayer(int layer) {
        this.referenceLayer = layer;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getBlockIndex() {
        return index;
    }

    public MotionVector getMotionVector() {
        return motionVector;
    }

    public int estimateBlockIndexAtLastFrame() {
        if (motionVector == null)
            return index;

        int prevX = x + motionVector.x;
        int prevY = y + motionVector.y;

        int row = prevY / MACROBLOCK_LENGTH + (motionVector.y + MACROBLOCK_LENGTH / 2) / MACROBLOCK_LENGTH;
        int col = prevX / MACROBLOCK_LENGTH + (motionVector.x + MACROBLOCK_LENGTH / 2) / MACROBLOCK_LENGTH;

        int macroblockWidth = 1 + (frame.width - 1) / MACROBLOCK_LENGTH;
        return row * macroblockWidth + col;
    }

    public void setMotionVector(MotionVector motionVector) {
        this.motionVector = motionVector;
    }
}
