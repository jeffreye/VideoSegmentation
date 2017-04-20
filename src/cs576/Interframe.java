package cs576;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static cs576.Utils.*;
import static cs576.VideoEncoder.MACROBLOCK_LENGTH;

/**
 * Created by Jeffreye on 4/1/2017.
 */
public class Interframe implements Frame {

    public final int height;
    public final int width;
    private Macroblock[] macroblocks;
    private int macroblockWidth;
    private int macroblockHeight;
    private byte[][] acValues;
    private int[] dcValues;

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

    public Interframe(byte[] imageBuffer, int height, int width) {
        this.macroblockHeight = 1 + (height - 1) / MACROBLOCK_LENGTH;
        this.macroblockWidth = 1 + (width - 1) / MACROBLOCK_LENGTH;
        this.height = height;
        this.width = width;
        this.macroblocks = new Macroblock[macroblockWidth * macroblockHeight];

        // Initialize blocks
        int blockIndex = 0;
        for (int i = 0; i < height; i += MACROBLOCK_LENGTH) {
            for (int j = 0; j < width; j += MACROBLOCK_LENGTH) {
                macroblocks[blockIndex] = new Macroblock(this, j, i, blockIndex);
                blockIndex++;
            }
        }

        imageY = new float[height][width];
        imageU = new float[height][width];
        imageV = new float[height][width];
        for (int k = 0; k < height; k++) {
            for (int l = 0; l < width; l++) {
                int pixelIndex = (k) * width + l;
                int r = imageBuffer[pixelIndex] & 0xFF;
                int g = imageBuffer[pixelIndex + height * width] & 0xFF;
                int b = imageBuffer[pixelIndex + height * width * 2] & 0xFF;

                imageY[k][l] = 0.2990f * r + 0.5870f * g + 0.1140f * b;
                imageU[k][l] = 128f - 0.1687f * r + 0.3313f * g + 0.5000f * b;
                imageV[k][l] = 128f + 0.5000f * r + 0.418688f * g + 0.081312f * b;
            }
        }


        this.acValues = new byte[getDctValueSize(height, width)][];
        this.dcValues = new int[acValues.length];
        calculateDCTValues(imageY, imageU, imageV, height, width, acValues, dcValues);
    }


    public Interframe(int height, int width, DataInput inputStream) throws IOException {
        this.macroblockHeight = 1 + (height - 1) / MACROBLOCK_LENGTH;
        this.macroblockWidth = 1 + (width - 1) / MACROBLOCK_LENGTH;
        this.height = height;
        this.width = width;
        this.macroblocks = new Macroblock[macroblockWidth * macroblockHeight];

        int blockIndex = 0;
        for (int i = 0; i < height; i += MACROBLOCK_LENGTH) {
            for (int j = 0; j < width; j += MACROBLOCK_LENGTH) {
                macroblocks[blockIndex] = new Macroblock(this, j, i, blockIndex, inputStream.readByte());
                blockIndex++;
            }
        }

        this.acValues = new byte[getDctValueSize(height, width)][];
        this.dcValues = new int[acValues.length];
        for (int i = 0; i < acValues.length; i++) {
            dcValues[i] = inputStream.readInt();
            inputStream.readFully(acValues[i] = new byte[64]);
        }

        // Done reading

        imageY = new float[height][width];
        imageU = new float[height][width];
        imageV = new float[height][width];
        calculateImage(acValues, dcValues, height, width, this.imageY, this.imageU, this.imageV);
    }

    public Macroblock[] getBlocks() {
        return this.macroblocks;
    }

    public Macroblock getBlock(int index) {
        return this.macroblocks[index];
    }

    @Override
    public int getFrameType() {
        return INTERFRAME;
    }

    @Override
    public void serialize(DataOutput os) throws java.io.IOException {
        // save them to files

        /**
         * File format:
         * R(coeff1 .. coeff64) G(coeff1 .. coeff64) B(coeff1 .. coeff64)
         * ...
         */
        for (Macroblock mc : this.macroblocks) {
            os.writeByte(mc.getLayer());
        }
        for (int i = 0; i < acValues.length; i++) {
            os.writeInt(dcValues[i]);
            os.write(acValues[i]);
        }
    }

    @Override
    public byte[] getRawImage() {
        return convertToRawImage(imageY, imageU, imageV);
    }
}
