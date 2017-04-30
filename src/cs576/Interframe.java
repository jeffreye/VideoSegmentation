package cs576;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static cs576.Macroblock.MACROBLOCK_LENGTH;
import static cs576.Utils.*;

/**
 * Created by Jeffreye on 4/1/2017.
 */
public class Interframe extends Frame {
    private float[][] dctValues;

//    private byte[] outputBuffer;

    public Interframe(byte[] imageBuffer, int height, int width) {
        super(imageBuffer, height, width);

        this.dctValues = new float[getDctValueSize(height, width)][64];
        forwardDCT(imageY, imageU, imageV, dctValues);
    }

    public Interframe(int height, int width, DataInput inputStream) throws IOException {
        super(height, width);

        int blockIndex = 0;
        for (int i = 0; i < height; i += MACROBLOCK_LENGTH) {
            for (int j = 0; j < width; j += MACROBLOCK_LENGTH) {
                macroblocks[blockIndex].setLayer(inputStream.readInt());
                blockIndex++;
            }
        }

        this.dctValues = new float[getDctValueSize(height, width)][64];
        for (int i = 0; i < dctValues.length; i++) {
            for (int j = 0; j < 64; j++) {
                dctValues[i][j] = inputStream.readFloat();
            }
        }
    }


    public Interframe(int height, int width, DataInput inputStream,int foregroundQuantizationValue, int backgroundQuantizationValue) throws IOException {
        super(height, width);

        int blockIndex = 0;
        for (int i = 0; i < height; i += MACROBLOCK_LENGTH) {
            for (int j = 0; j < width; j += MACROBLOCK_LENGTH) {
                macroblocks[blockIndex].setLayer(inputStream.readInt());
                blockIndex++;
            }
        }

        this.dctValues = new float[getDctValueSize(height, width)][64];
        for (int i = 0; i < dctValues.length; i++) {
            for (int j = 0; j < 64; j++) {
                dctValues[i][j] = inputStream.readFloat();
            }
        }
        // Done reading


        reconstruct(foregroundQuantizationValue, backgroundQuantizationValue);
    }

    public Interframe reconstruct(int foregroundQuantizationValue, int backgroundQuantizationValue) {
        int blockIndex;
        int macroblockWidth = 1 + (width - 1) / MACROBLOCK_LENGTH;
        int dctIndex = 0;
        for (int i = 0; i < height; i += DCT_BLOCK_LENGTH) {
            for (int j = 0; j < width; j += DCT_BLOCK_LENGTH) {
                blockIndex = i / MACROBLOCK_LENGTH * macroblockWidth + j / MACROBLOCK_LENGTH;
                int quantizationValue =
                        macroblocks[blockIndex].isBackgroundLayer() ?
                        backgroundQuantizationValue:
                        foregroundQuantizationValue;

                for (int k = 0; k < 3; k++) {
                    quantize(dctValues[dctIndex],quantizationValue);
                    dequantize(dctValues[dctIndex],quantizationValue);
                    dctIndex++;
                }

            }
        }

        inverseDCT(dctValues, this.imageY, this.imageU, this.imageV);
        return this;
    }

    @Override
    public int getFrameType() {
        return INTERFRAME;
    }

    @Override
    public void serialize(DataOutput os) throws java.io.IOException {
        // save them to files

        // Serialize all marcobloks' layers
        for (Macroblock mc : this.macroblocks) {
            os.writeInt(mc.getLayer());
        }

        // Serialize "JPEG" Image

        for (int i = 0; i < dctValues.length; i++) {
            for (int j = 0; j < 64; j++) {
                os.writeFloat(dctValues[i][j]);
            }
        }
    }

    @Override
    public byte[] getRawImage() {
        return convertToRGB(imageY, imageU, imageV);
    }
}
