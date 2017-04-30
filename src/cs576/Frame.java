package cs576;

import java.io.DataOutput;

import static cs576.Macroblock.MACROBLOCK_LENGTH;
import static cs576.Utils.convertToRGB;
import static cs576.Utils.convertToYUV;

/**
 * Created by Jeffreye on 4/16/2017.
 */
public abstract class Frame {
    public static final int INTERFRAME = 0;
    public static final int PREDICTIVEFRAME = 1;
    public static final int SEGMENTEDFRAME = 2;

    public final int height;
    public final int width;

    protected Macroblock[] macroblocks;

    /***
     * Image (Y Channel)
     */
    float[][] imageY;

    /***
     * Image (U Channel)
     */
    float[][] imageU;

    /***
     * Image (V Channel)
     */
    float[][] imageV;

    protected Frame(byte[] imageBuffer, int height, int width) {
        this(height,width);

        convertToYUV(imageBuffer, height, width, imageY, imageU, imageV);
    }

    protected Frame(int height, int width) {
        this.height = height;
        this.width = width;

        imageY = new float[height][width];
        imageU = new float[height][width];
        imageV = new float[height][width];

        int macroblockHeight = 1 + (height - 1) / MACROBLOCK_LENGTH;
        int macroblockWidth = 1 + (width - 1) / MACROBLOCK_LENGTH;
        this.macroblocks = new Macroblock[macroblockWidth * macroblockHeight];

        // Initialize blocks
        int blockIndex = 0;
        for (int i = 0; i < height; i += MACROBLOCK_LENGTH) {
            for (int j = 0; j < width; j += MACROBLOCK_LENGTH) {
                macroblocks[blockIndex] = new Macroblock(this, j, i, blockIndex);
                blockIndex++;
            }
        }
    }

    public Macroblock[] getBlocks() {
        return this.macroblocks;
    }

    public Macroblock getBlock(int index) {
        return this.macroblocks[index];
    }

    abstract  int getFrameType();
    abstract  void serialize(DataOutput os)throws java.io.IOException;



    public byte[] getRawImage() {
        return convertToRGB(imageY, imageU, imageV);
    }
}
