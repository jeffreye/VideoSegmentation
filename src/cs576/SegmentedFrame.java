package cs576;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import static cs576.Utils.*;
import static cs576.VideoEncoder.MACROBLOCK_LENGTH;

/**
 * Created by Jeffreye on 4/15/2017.
 */
public class SegmentedFrame extends Frame {

    SegmentedFrame referenceFrame;

    /**
     * motion vectors that are related to last frame
     */
    private ArrayList<Macroblock> motionVectors;

    private float[][] dctValues;

    public SegmentedFrame(byte[] imageBuffer, int height, int width) {
        super(imageBuffer, height, width);

        this.dctValues = new float[getDctValueSize(height, width)][64];
        forwardDCT(imageY, imageU, imageV, dctValues);
    }

    public SegmentedFrame computeDiff(SegmentedFrame referenceFrame, int searchRange) {
        this.motionVectors = new ArrayList<>();
        this.referenceFrame = referenceFrame;

        computeMotionVectors(searchRange);
        groupRegions();

        return this;
    }

    public SegmentedFrame(int height, int width, DataInput inputStream) throws IOException {
        super(height, width);

        for (Macroblock mc : this.macroblocks) {
            mc.setLayer(inputStream.readInt());
        }

        int size = inputStream.readInt();
        this.motionVectors = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            int index = inputStream.readInt();
            Macroblock mb = this.getBlock(index);

            int x = inputStream.readInt();
            int y = inputStream.readInt();
            mb.setMotionVector(new MotionVector(x, y));

            this.motionVectors.add(mb);
        }


        this.dctValues = new float[getDctValueSize(height, width)][64];
        for (int i = 0; i < dctValues.length; i++) {
            for (int j = 0; j < 64; j++) {
                dctValues[i][j] = inputStream.readFloat();
            }
        }
    }

    public SegmentedFrame reconstruct(SegmentedFrame referenceFrame, int foregroundQuantizationValue, int backgroundQuantizationValue) {
//        this.referenceFrame = referenceFrame;


        int blockIndex;
        int macroblockWidth = 1 + (width - 1) / MACROBLOCK_LENGTH;
        int dctIndex = 0;
        for (int i = 0; i < height; i += DCT_BLOCK_LENGTH) {
            for (int j = 0; j < width; j += DCT_BLOCK_LENGTH) {
                blockIndex = i / MACROBLOCK_LENGTH * macroblockWidth + j / MACROBLOCK_LENGTH;
                int quantizationValue =
                        macroblocks[blockIndex].isBackgroundLayer() ?
                                backgroundQuantizationValue :
                                foregroundQuantizationValue;

                for (int k = 0; k < 3; k++) {
                    quantize(dctValues[dctIndex], quantizationValue);
                    dequantize(dctValues[dctIndex], quantizationValue);
                    dctIndex++;
                }

            }
        }

        assert dctIndex == dctValues.length;

        inverseDCT(dctValues, this.imageY, this.imageU, this.imageV);
        return this;
    }

    /**
     * Compute motion vectors using block based SAD(sum of absolute difference brute search)
     * or fast motion estimation(FME), and then compute error frame
     *
     * @param k Search Range
     */
    private void computeMotionVectors(int k) {
        float[][] previousY = referenceFrame.imageY;

        for (Macroblock b1 : this.getBlocks()) {

            int deltaX = 0, deltaY = 0;

            // Same block
            float min = 0;
            for (int y = b1.getY(); y <= b1.getY() + MACROBLOCK_LENGTH - 1; y++) {
                for (int x = b1.getX(); x <= b1.getX() + MACROBLOCK_LENGTH - 1; x++) {
                    if (y >= height || x >= width)
                        continue;
                    min += Math.abs(previousY[y][x] - imageY[y][x]);
                }
            }

            if (min != 0){
                for (int i = -k; i <= k; i++) {
                    for (int j = -k; j <= k; j++) {
                        if (i == 0 && j == 0)
                            continue;

                        int left = b1.getX() + i;
                        int right = left + MACROBLOCK_LENGTH - 1;

                        int top = b1.getY() + j;
                        int bottom = top + MACROBLOCK_LENGTH - 1;


                        if (left < 0 || top < 0 || bottom >= height || right >= width)
                            continue;

                        // do macroblock searches only using data of one component, which should be the Y component
                        float sum = 0;
                        for (int y = top; y <= bottom; y++) {
                            for (int x = left; x <= right; x++) {
                                if (y - j >= height || x- i >= width)
                                    continue;
                                sum += Math.abs(previousY[y][x] - imageY[y - j][x- i]);
                            }
                        }

                        if (sum < min) {
                            deltaX = i;
                            deltaY = j;
                            min = sum;
                        }
                    }
                }
            }

            if (min == Float.MAX_VALUE) {
                // TODO: no matching block
                System.err.println("no matching block found");
            } else if (deltaX == 0 && deltaY == 0) {
                motionVectors.add(b1);
            } else {
                b1.setMotionVector(new MotionVector(deltaX, deltaY));
                motionVectors.add(b1);
            }
        }
    }

    /**
     * Assign all macroblocks a layer index
     */
    public void groupRegions() {
        /**
         * •Contiguous or adjacent
         •The motion vectors are all consistent – important!
         The consistency of the motion vector direction gives you an indication
         that all the macroblocks probably belong to the same object and are moving in a certain direction
         */
        double tolerantRate = 10;

        int macroblockWidth = 1 + (width - 1) / MACROBLOCK_LENGTH;
        int totalX = 0;
        int totalY = 0;
        int count = 0;
        for (Macroblock eachBlock : motionVectors) {
            totalX += eachBlock.getMotionVector().x;
            totalY += eachBlock.getMotionVector().y;
            count++;
        }
        for (Macroblock eachBlock : motionVectors) {
            int layerIndex = Macroblock.BACKGROUND_LAYER;
            if (eachBlock.getMotionVector().magnitude() >= tolerantRate) {
                layerIndex = 1;
            }

            if (referenceFrame.referenceFrame == null && layerIndex != Macroblock.BACKGROUND_LAYER) {
                // assign layer values
                int x = eachBlock.getX() + eachBlock.getMotionVector().x;
                int y = eachBlock.getY() + eachBlock.getMotionVector().y;

                int blockIndex = y / MACROBLOCK_LENGTH * macroblockWidth + x / MACROBLOCK_LENGTH;
                Macroblock mb = referenceFrame.getBlock(blockIndex);
                mb.setLayer(layerIndex);
            }
        }
    }

    @Override
    public int getFrameType() {
        return SEGMENTEDFRAME;
    }

    @Override
    public void serialize(DataOutput os) throws IOException {
        // Serialize all marcobloks' layers
        for (Macroblock mc : this.macroblocks) {
            os.writeInt(mc.getLayer());
        }

        // Serialize motion vectors
        if (motionVectors == null) {
            os.writeInt(0);
        } else {
            os.writeInt(motionVectors.size());
            for (Macroblock mb : motionVectors) {

                os.writeInt(mb.getBlockIndex());

                MotionVector v = mb.getMotionVector();
                os.writeInt(v.x);
                os.writeInt(v.y);
            }
        }

        // Serialize "JPEG" Image
        ByteBuffer buffer = ByteBuffer.allocate(dctValues.length*64*4);
        for (int i = 0; i < dctValues.length; i++) {
            for (int j = 0; j < 64; j++) {
//                os.writeFloat(dctValues[i][j]);
                buffer.putFloat(dctValues[i][j]);
            }
        }
        os.write(buffer.array());
    }

    @Override
    public byte[] getRawImage() {
        byte[] rawImage = super.getRawImage();

        for (Macroblock mb : macroblocks) {
            if (mb.isBackgroundLayer())
                continue;

            // Mark by green
            int x = mb.getX();
            int y = mb.getY();
            for (int i = 0; i < MACROBLOCK_LENGTH; i++) {
                for (int j = 0; j < MACROBLOCK_LENGTH; j++) {
                    rawImage[height * width + (y + i) * width + (x + j)] = (byte) 255;
                }
            }
        }

        return rawImage;
    }
}
