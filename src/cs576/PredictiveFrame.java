package cs576;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;

import static cs576.Utils.*;
import static cs576.Macroblock.MACROBLOCK_LENGTH;

/**
 * Created by Jeffreye on 4/15/2017.
 */
public class PredictiveFrame extends Frame {
    Frame referenceFrame;
    private ArrayList<Macroblock> motionVectors;

    // Error Image
    private float[][] errorDctValues;

    public PredictiveFrame(Frame referenceFrame, byte[] imageBuffer, int searchRange) {
        super(imageBuffer, referenceFrame.height, referenceFrame.width);

        computeDiff(referenceFrame, searchRange);
    }

    public PredictiveFrame(byte[] imageBuffer, int height, int width) {
        super(imageBuffer, height, width);
    }

    public PredictiveFrame computeDiff(Frame referenceFrame, int searchRange) {
        this.motionVectors = new ArrayList<>();
        this.referenceFrame = referenceFrame;

        computeMotionVectors(searchRange);
        computeErrorFrame();
        groupRegions();

        return this;
    }

    public PredictiveFrame(int height, int width, DataInput inputStream) throws IOException {
        super(height, width);

        int blockIndex = 0;
        for (int i = 0; i < height; i += MACROBLOCK_LENGTH) {
            for (int j = 0; j < width; j += MACROBLOCK_LENGTH) {
                macroblocks[blockIndex].setLayer(inputStream.readInt());
                blockIndex++;
            }
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


        this.errorDctValues = new float[getDctValueSize(height, width)][64];
        for (int i = 0; i < errorDctValues.length; i++) {
            for (int j = 0; j < 64; j++) {
                errorDctValues[i][j] = inputStream.readFloat();
            }
        }
    }

    public PredictiveFrame(Frame referenceFrame, DataInput inputStream, int foregroundQuantizationValue, int backgroundQuantizationValue) throws IOException {
        super(referenceFrame.height, referenceFrame.width);

        int blockIndex = 0;
        for (int i = 0; i < height; i += MACROBLOCK_LENGTH) {
            for (int j = 0; j < width; j += MACROBLOCK_LENGTH) {
                macroblocks[blockIndex].setLayer(inputStream.readInt());
                blockIndex++;
            }
        }

        this.referenceFrame = referenceFrame;
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

        this.errorDctValues = new float[getDctValueSize(height, width)][64];
        for (int i = 0; i < errorDctValues.length; i++) {
            for (int j = 0; j < 64; j++) {
                errorDctValues[i][j] = inputStream.readFloat();
            }
        }
        // Done reading

        reconstruct(referenceFrame,foregroundQuantizationValue, backgroundQuantizationValue);
    }

    public PredictiveFrame reconstruct(Frame referenceFrame,int foregroundQuantizationValue, int backgroundQuantizationValue) {
        this.referenceFrame = referenceFrame;

        int blockIndex;
        float[][] errorImageY = new float[height][width];
        float[][] errorImageU = new float[height][width];
        float[][] errorImageV = new float[height][width];

        //TODO: decide which quantization step we should use
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
                    quantize(errorDctValues[dctIndex], quantizationValue);
                    dequantize(errorDctValues[dctIndex], quantizationValue);
                    dctIndex++;
                }
            }
        }

        inverseDCT(errorDctValues, errorImageY, errorImageU, errorImageV);


        compensateFrame(imageY, imageU, imageV, motionVectors, height, width, referenceFrame);

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                imageY[i][j] += errorImageY[i][j];
                imageU[i][j] += errorImageU[i][j];
                imageV[i][j] += errorImageV[i][j];
            }
        }

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
                            sum += Math.abs(previousY[y][x] - imageY[y][x]);
                        }
                    }

                    if (sum < min) {
                        deltaX = i;
                        deltaY = j;
                        min = sum;
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


    private void computeErrorFrame() {
        // A blank image
        float[][] reconstructedImageY = new float[height][width];
        float[][] reconstructedImageU = new float[height][width];
        float[][] reconstructedImageV = new float[height][width];
        // Compute error image
        float[][] errorImageY = new float[height][width];
        float[][] errorImageU = new float[height][width];
        float[][] errorImageV = new float[height][width];

        compensateFrame(reconstructedImageY, reconstructedImageU, reconstructedImageV,
                motionVectors, height, width, referenceFrame);

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                errorImageY[i][j] = imageY[i][j] - reconstructedImageY[i][j];
                errorImageU[i][j] = imageU[i][j] - reconstructedImageU[i][j];
                errorImageV[i][j] = imageV[i][j] - reconstructedImageV[i][j];
            }
        }

        this.errorDctValues = new float[getDctValueSize(height, width)][64];
        forwardDCT(
                errorImageY, errorImageU, errorImageV,
                errorDctValues);

    }

    /**
     * Reconstruct last frame by using motion vectors
     *
     * @param reconstructedImageY
     * @param reconstructedImageU
     * @param reconstructedImageV
     * @param motionVectors
     * @param height
     * @param width
     * @param referenceFrame
     */
    private static void compensateFrame(float[][] reconstructedImageY, float[][] reconstructedImageU, float[][] reconstructedImageV, ArrayList<Macroblock> motionVectors, int height, int width, Frame referenceFrame) {
        for (Macroblock mb : motionVectors) {
            int x = mb.getX();
            int y = mb.getY();
            int originalX = x + mb.getMotionVector().x;
            int originalY = y + mb.getMotionVector().y;

            // Fill pixels from macroblocks
            for (int i = 0; i < MACROBLOCK_LENGTH; i++) {
                for (int j = 0; j < MACROBLOCK_LENGTH; j++) {
                    if (i + y >= height || j + x >= width || originalY + i >= height || originalX + j >= width) {
                        continue;
                    } else {
                        reconstructedImageY[y + i][x + j] = referenceFrame.imageY[originalY + i][originalX + j];
                        reconstructedImageU[y + i][x + j] = referenceFrame.imageU[originalY + i][originalX + j];
                        reconstructedImageV[y + i][x + j] = referenceFrame.imageV[originalY + i][originalX + j];
                    }
                }
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
            if (eachBlock.getMotionVector().getDistance(totalX * 1.0 / count, totalY * 1.0 / count) >= tolerantRate) {
                layerIndex = 1;
            }

            if (referenceFrame.getFrameType() == INTERFRAME && layerIndex != Macroblock.BACKGROUND_LAYER){
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
        return PREDICTIVEFRAME;
    }

    @Override
    public void serialize(DataOutput os) throws java.io.IOException {
        // serialize motion vectors


        // Serialize all marcobloks' layers
        for (Macroblock mc : this.macroblocks) {
            os.writeInt(mc.getLayer());
        }

        // Serialize motion vectors
        os.writeInt(motionVectors.size());
        for (Macroblock mb : motionVectors) {

            os.writeInt(mb.getBlockIndex());

            MotionVector v = mb.getMotionVector();
            os.writeInt(v.x);
            os.writeInt(v.y);
        }

        // Serialize error "JPEG" Image
        for (int i = 0; i < errorDctValues.length; i++) {
            for (int j = 0; j < 64; j++) {
                os.writeFloat(errorDctValues[i][j]);
            }
        }
    }

}
