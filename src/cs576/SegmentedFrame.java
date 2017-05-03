package cs576;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;

import static cs576.Macroblock.BACKGROUND_LAYER;
import static cs576.Macroblock.MACROBLOCK_LENGTH;
import static cs576.Utils.*;

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

    ByteBuffer byteBuffer;

    public SegmentedFrame(int height, int width) {
        super(height, width);
        byteBuffer = ByteBuffer.allocateDirect(getDctValueSize(height, width) * 64 * 4);
    }

    public SegmentedFrame(byte[] imageBuffer, int height, int width) {
        super(imageBuffer, height, width);
        setData();
        byteBuffer = ByteBuffer.allocateDirect(getDctValueSize(height, width) * 64 * 4);
    }

    public SegmentedFrame setData() {
        for (Macroblock mb : macroblocks)
            mb.setMotionVector(null);
        this.dctValues = new float[getDctValueSize(height, width)][64];
        forwardDCT(imageY, imageU, imageV, dctValues);
        return this;
    }

    public SegmentedFrame setData(byte[] imageBuffer) {
        convertToYUV(imageBuffer, height, width, imageY, imageU, imageV);
        return setData();
    }

    public SegmentedFrame computeDiff(SegmentedFrame referenceFrame, int searchRange) {
        this.motionVectors = new ArrayList<>();
        this.referenceFrame = referenceFrame;

        float[][] previousY = referenceFrame.imageY;
        previousY = referenceFrame.imageY;
        if (referenceFrame != null) {
//            if (referenceFrame.referenceFrame != null) {
//                previousY = referenceFrame.referenceFrame.imageY;
//                if (referenceFrame.referenceFrame.referenceFrame != null) {
//                    previousY = referenceFrame.referenceFrame.referenceFrame.imageY;
//                    computeMotionVectors(searchRange, previousY);
//                    if (referenceFrame.referenceFrame.referenceFrame.referenceFrame != null) {
//                        if (referenceFrame.referenceFrame.referenceFrame.referenceFrame.referenceFrame != null) {
//                            previousY = referenceFrame.referenceFrame.referenceFrame.referenceFrame.referenceFrame.imageY;
//                            computeMotionVectors(searchRange, previousY);
//                        }
//                    }
//                }
//            }
        }
        computeMotionVectors(searchRange, previousY);

        groupRegions();

        return this;
    }

    public SegmentedFrame(int height, int width, DataInput inputStream) throws IOException {
        super(height, width);

        //TODO:
//        loadFrom(inputStream);
    }


    public boolean loadFrom(FileChannel inputStream) throws IOException {
        for (Macroblock mb : macroblocks)
            mb.setMotionVector(null);

        long startPos = inputStream.position();
        byteBuffer.clear();
        byteBuffer.limit(macroblocks.length * 4);
        int read = inputStream.read(byteBuffer);
        if (read == 0)
            return false;
        byteBuffer.flip();
        IntBuffer layers = byteBuffer.asIntBuffer();
        for (Macroblock mc : this.macroblocks) {
            mc.setLayer(layers.get());
        }

        byteBuffer.clear();
        byteBuffer.limit(4);
        inputStream.read(byteBuffer);
        byteBuffer.flip();

        int size = byteBuffer.getInt();

        this.motionVectors = new ArrayList<>(size);

        byteBuffer.clear();
        byteBuffer.limit(size * 3 * 4);
        inputStream.read(byteBuffer);
        byteBuffer.flip();

        IntBuffer vectors = byteBuffer.asIntBuffer();
        for (int i = 0; i < size; i++) {
            int index = vectors.get();
            Macroblock mb = this.getBlock(index);

            int x = vectors.get();
            int y = vectors.get();
            mb.setMotionVector(new MotionVector(x, y));

            this.motionVectors.add(mb);
        }


        byteBuffer.clear();
        inputStream.read(byteBuffer);
        byteBuffer.flip();
        FloatBuffer dctBuffer = byteBuffer.asFloatBuffer();
        this.dctValues = new float[getDctValueSize(height, width)][64];
        for (int i = 0; i < dctValues.length; i++) {
            dctBuffer.get(dctValues[i]);
        }


        return true;
    }

    public SegmentedFrame reconstruct() {
        inverseDCT(dctValues, this.imageY, this.imageU, this.imageV);
        return this;
    }

    public SegmentedFrame reconstruct(int foregroundQuantizationValue, int backgroundQuantizationValue) {

        // Quantize and Dequantize
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

        inverseDCT(dctValues, this.imageY, this.imageU, this.imageV);
        return this;
    }

    /**
     * Compute motion vectors using block based SAD(sum of absolute difference brute search)
     * or fast motion estimation(FME), and then compute error frame
     *
     * @param k Search Range
     */
    private void computeMotionVectors(int k, float[][] previousY) {

        float[][] blockSums = calcualteBlockSums(previousY);

        for (Macroblock b1 : this.getBlocks()) {

            int deltaX = 0, deltaY = 0;

            // Same block
            float min = 0;
            float macroblockSum = 0;
            for (int y = b1.getY(); y <= b1.getY() + MACROBLOCK_LENGTH - 1; y++) {
                for (int x = b1.getX(); x <= b1.getX() + MACROBLOCK_LENGTH - 1; x++) {
                    if (y >= height || x >= width)
                        continue;
                    min += Math.abs(previousY[y][x] - imageY[y][x]);
                    macroblockSum += imageY[y][x];
                }
            }

            if (min != 0) {
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

                        // Check lower bound
                        if (Math.abs(macroblockSum - blockSums[top][left]) > min)
                            continue;

                        // do macroblock searches only using data of one component, which should be the Y component
                        float sum = 0;
                        for (int y = top; y <= bottom; y++) {
                            for (int x = left; x <= right; x++) {
                                if (y - j >= height || x - i >= width)
                                    continue;
                                sum += Math.abs(previousY[y][x] - imageY[y - j][x - i]);
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

            if (min > 5000) {
                // See this block as background if there is no matching block
//                System.err.println("no matching block found");
            } else if (deltaX == 0 && deltaY == 0) {
                b1.setMotionVector(new MotionVector(0, 0));
                motionVectors.add(b1);
            } else {
                b1.setMotionVector(new MotionVector(deltaX, deltaY));
                motionVectors.add(b1);
            }
        }
    }


    static final ThreadLocal<float[][]> threadedBlockSum = new ThreadLocal<>();
    static final ThreadLocal<float[][]> threadedRowSum = new ThreadLocal<>();

    private float[][] calcualteBlockSums(float[][] previousY) {
        int height = this.height - MACROBLOCK_LENGTH + 1;
        int width = this.width - MACROBLOCK_LENGTH + 1;


        float[][] rowSums = threadedRowSum.get();
        if (rowSums == null) {
            rowSums = new float[this.height][this.width];
            threadedRowSum.set(rowSums);
        }

        float[][] blockSums = threadedBlockSum.get();
        if (blockSums == null) {
            blockSums = new float[height][width];
            threadedBlockSum.set(blockSums);
        }

        // Rows
        for (int i = 0; i < height; i++) {
            float s = 0;
            for (int j = 0; j < MACROBLOCK_LENGTH; j++)
                s += previousY[i][j];

            rowSums[i][0] = s;

            for (int j = 1; j < width; j++) {
                s -= previousY[i][j - 1];
                s += previousY[i][j - 1 + MACROBLOCK_LENGTH];
                rowSums[i][j] = s;
            }
        }

        // Columns
        for (int j = 0; j < width; j++) {

            float s = 0;
            for (int i = 0; i < MACROBLOCK_LENGTH; i++)
                s += rowSums[i][j];
            blockSums[0][j] = s;

            for (int i = 1; i < height; i++) {
                s -= rowSums[i - 1][j];
                s += rowSums[i - 1 + MACROBLOCK_LENGTH][j];
                blockSums[i][j] = s;
            }

        }

        return blockSums;
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

//        //v1.0
//        double tolerantRate = 5;
//
//        int macroblockWidth = 1 + (width - 1) / MACROBLOCK_LENGTH;
//        for (Macroblock eachBlock : motionVectors) {
//            int layerIndex = Macroblock.BACKGROUND_LAYER;
//            if (eachBlock.getMotionVector().magnitude() >= tolerantRate) {
//                layerIndex = 1;
//            }
//
//            if (referenceFrame.referenceFrame == null && layerIndex != Macroblock.BACKGROUND_LAYER) {
//                // assign layer values
//                int x = eachBlock.getX() + eachBlock.getMotionVector().x;
//                int y = eachBlock.getY() + eachBlock.getMotionVector().y;
//
//                int blockIndex = y / MACROBLOCK_LENGTH * macroblockWidth + x / MACROBLOCK_LENGTH;
//                Macroblock mb = referenceFrame.getBlock(blockIndex);
//                mb.setLayer(layerIndex);
//            }
//        }


//        //v2.0
//        double tolerantRate=3;
//        double totalX=0;
//        double totalY=0;
//        int count=0;
//        for (Macroblock eachBlock : motionVectors) {
//            totalX+=eachBlock.getMotionVector().x;
//            totalY+=eachBlock.getMotionVector().y;
//            count++;
//        }
//        for (Macroblock eachBlock : motionVectors) {
//            if(eachBlock.getMotionVector().getDistance(totalX*1.0/count,totalY*1.0/count) < tolerantRate){
//                eachBlock.setLayer(0);
//            }
//            else{
//                eachBlock.setLayer(1);
//            }
//        }


//        //v3.0
//        final double bgMinRadius=3;
//        double tolerantRate=1;
//        boolean recalculate=true;
//
//        double bgTotalX=0;
//        double bgTotalY=0;
//        int bgCount=0;
//        double bgLayerCentroidX=0;
//        double bgLayerCentroidY=0;
//
//        double fgTotalX=0;
//        double fgTotalY=0;
//        int fgCount=0;
//        double fgLayerCentroidX=0;
//        double fgLayerCentroidY=0;
//
//        while (recalculate){
//            recalculate=false;
//            for (Macroblock eachBlock : motionVectors) {
//                if( eachBlock.getX()<18 || eachBlock.getX()> (height-26) || eachBlock.getX()<18 || eachBlock.getX()> (width-26) ) {
//                    bgTotalX += eachBlock.getMotionVector().x;
//                    bgTotalY += eachBlock.getMotionVector().y;
//                    bgCount++;
//                }
//            }
//            bgLayerCentroidX=bgTotalX/bgCount;
//            bgLayerCentroidY=bgTotalY/bgCount;
//
//            bgCount=0;
//            for (Macroblock eachBlock : motionVectors) {
//                if(eachBlock.getMotionVector().getDistance(bgLayerCentroidX,bgLayerCentroidY) < tolerantRate){
//                    eachBlock.setLayer(Macroblock.BACKGROUND_LAYER);
//                    bgCount++;
//                }
//                else{
//                    eachBlock.setLayer(1);
//                    fgTotalX+=eachBlock.getMotionVector().x;
//                    fgTotalY+=eachBlock.getMotionVector().y;
//                    fgCount++;
//                }
//            }
//            fgLayerCentroidX=fgTotalX/fgCount;
//            fgLayerCentroidY=fgTotalY/fgCount;
//
//            if(bgCount==0){
//                recalculate=true;
//                tolerantRate=tolerantRate*2;
//                bgTotalX=0;
//                bgTotalY=0;
//                bgCount=0;
//                fgTotalX=0;
//                fgTotalY=0;
//                fgCount=0;
//            }
//        }
//
//        //clustering
//        boolean changed=true;
//        while (changed){
//            changed=false;
//            for (Macroblock eachBlock : motionVectors) {
//                int previousLayer=eachBlock.getLayer();
//                if(eachBlock.getMotionVector().getDistance(fgLayerCentroidX,fgLayerCentroidY)-eachBlock.getMotionVector().getDistance(bgLayerCentroidX,bgLayerCentroidY) >0
//                        || eachBlock.getMotionVector().getDistance(bgLayerCentroidX,bgLayerCentroidY) < bgMinRadius){
//                    eachBlock.setLayer(Macroblock.BACKGROUND_LAYER);
//                }
//                else{
//                    eachBlock.setLayer(1);
//                }
//                if(previousLayer != eachBlock.getLayer()){
//                    changed=true;
//                }
//            }
//            if (changed){//calculate new centroids
//                bgTotalX=0;
//                bgTotalY=0;
//                bgCount=0;
//                fgTotalX=0;
//                fgTotalY=0;
//                fgCount=0;
//                for (Macroblock eachBlock : motionVectors) {
//                    if(eachBlock.getLayer()==Macroblock.BACKGROUND_LAYER){
//                        bgTotalX+=eachBlock.getMotionVector().x;
//                        bgTotalY+=eachBlock.getMotionVector().y;
//                        bgCount++;
//                    }
//                    else{
//                        fgTotalX+=eachBlock.getMotionVector().x;
//                        fgTotalY+=eachBlock.getMotionVector().y;
//                        fgCount++;
//                    }
//                }
//                bgLayerCentroidX=bgTotalX/bgCount;
//                bgLayerCentroidY=bgTotalY/bgCount;
//                fgLayerCentroidX=fgTotalX/fgCount;
//                fgLayerCentroidY=fgTotalY/fgCount;
//            }
//        }


//        //v4.0
//        double bgMinRadius=4; //more background when larger, should be positive
//        double tolerantRate=0; //more foreground when larger, can be negative
//
////        //random assigning
////        for (Macroblock eachBlock : macroblocks) {
////            int ran = ThreadLocalRandom.current().nextInt(0, 3);
////            eachBlock.setReferenceLayer(ran);
////        }
//
//        //initiating
//        double layer0CentroidX=10;
//        double layer0CentroidY=10;
//        double layer0TotalX=0;
//        double layer0TotalY=0;
//        int layer0Count=0;
//
//        double layer1CentroidX=10;
//        double layer1CentroidY=-10;
//        double layer1TotalX=0;
//        double layer1TotalY=0;
//        int layer1Count=0;
//
//        double layer2CentroidX=-10;
//        double layer2CentroidY=10;
//        double layer2TotalX=0;
//        double layer2TotalY=0;
//        int layer2Count=0;
//
//        double layer3CentroidX=-10;
//        double layer3CentroidY=-10;
//        double layer3TotalX=0;
//        double layer3TotalY=0;
//        int layer3Count=0;
//
//        //clustering
//        boolean changed=true;
//        while (changed){
//            changed=false;
//
//            //assigning
//            for (Macroblock eachBlock : macroblocks) {
//                if (eachBlock.getMotionVector() != null) {
//                    int originalLayer = eachBlock.getReferenceLayer();
//                    //get recentCentroid value
//                    double recentCentroidX;
//                    double recentCentroidY;
//                    if (eachBlock.getReferenceLayer() == 1) {
//                        recentCentroidX = layer1CentroidX;
//                        recentCentroidY = layer1CentroidY;
//                    } else if (eachBlock.getReferenceLayer() == 2) {
//                        recentCentroidX = layer2CentroidX;
//                        recentCentroidY = layer2CentroidY;
//                    } else if (eachBlock.getReferenceLayer() == 3) {
//                        recentCentroidX = layer3CentroidX;
//                        recentCentroidY = layer3CentroidY;
//                    } else {
//                        recentCentroidX = layer0CentroidX;
//                        recentCentroidY = layer0CentroidY;
//                    }
//
//                    if (eachBlock.getMotionVector().getDistance(recentCentroidX, recentCentroidY) - eachBlock.getMotionVector().getDistance(layer1CentroidX, layer1CentroidY) > 0) {
//                        eachBlock.setReferenceLayer(1);
//                        recentCentroidX = layer1CentroidX;
//                        recentCentroidY = layer1CentroidY;
//                    }
//                    if (eachBlock.getMotionVector().getDistance(recentCentroidX, recentCentroidY) - eachBlock.getMotionVector().getDistance(layer2CentroidX, layer2CentroidY) > 0) {
//                        eachBlock.setReferenceLayer(2);
//                        recentCentroidX = layer2CentroidX;
//                        recentCentroidY = layer2CentroidY;
//                    }
//                    if (eachBlock.getMotionVector().getDistance(recentCentroidX, recentCentroidY) - eachBlock.getMotionVector().getDistance(layer3CentroidX, layer3CentroidY) > 0) {
//                        eachBlock.setReferenceLayer(3);
//                        recentCentroidX = layer3CentroidX;
//                        recentCentroidY = layer3CentroidY;
//                    }
//                    if (eachBlock.getMotionVector().getDistance(recentCentroidX, recentCentroidY) - eachBlock.getMotionVector().getDistance(layer0CentroidX, layer0CentroidY) > 0) {
//                        eachBlock.setReferenceLayer(0);
//                    }
//
//                    if (originalLayer != eachBlock.getReferenceLayer()) {
//                        changed = true;
//                    }
//                }
//            }
//            //calculate centroids
//            for (Macroblock eachBlock : macroblocks) {
//                if (eachBlock.getMotionVector() != null) {
//                    if (eachBlock.getReferenceLayer() == 1) {
//                        layer1TotalX += eachBlock.getMotionVector().x;
//                        layer1TotalY += eachBlock.getMotionVector().y;
//                        layer1Count++;
//                    } else if (eachBlock.getReferenceLayer() == 2) {
//                        layer2TotalX += eachBlock.getMotionVector().x;
//                        layer2TotalY += eachBlock.getMotionVector().y;
//                        layer2Count++;
//                    } else if (eachBlock.getReferenceLayer() == 3) {
//                        layer3TotalX += eachBlock.getMotionVector().x;
//                        layer3TotalY += eachBlock.getMotionVector().y;
//                        layer3Count++;
//                    } else {
//                        layer0TotalX += eachBlock.getMotionVector().x;
//                        layer0TotalY += eachBlock.getMotionVector().y;
//                        layer0Count++;
//                    }
//                }
//            }
//            layer0CentroidX=layer0TotalX/layer0Count;
//            layer0CentroidY=layer0TotalY/layer0Count;
//            layer1CentroidX=layer1TotalX/layer1Count;
//            layer1CentroidY=layer1TotalY/layer1Count;
//            layer2CentroidX=layer2TotalX/layer2Count;
//            layer2CentroidY=layer2TotalY/layer2Count;
//            layer3CentroidX=layer3TotalX/layer3Count;
//            layer3CentroidY=layer3TotalY/layer3Count;
//        }
//        //find out background layer cluster
//        layer0Count=0;
//        layer1Count=0;
//        layer2Count=0;
//        layer3Count=0;
//        for (Macroblock eachBlock : macroblocks) {
//            if (eachBlock.getMotionVector() != null) {
//                if (eachBlock.getReferenceLayer() == 1) {
//                    layer1Count++;
//                } else if (eachBlock.getReferenceLayer() == 2) {
//                    layer2Count++;
//                } else if (eachBlock.getReferenceLayer() == 3) {
//                    layer3Count++;
//                } else if (eachBlock.getReferenceLayer() == 0){
//                    layer0Count++;
//                }
//            }
//        }
//        //if layer1 is background layer, switch it with layer0
//        if (Math.max(Math.max(Math.max(layer1Count,layer2Count),layer3Count),layer0Count)==layer1Count){
//            for (Macroblock eachBlock : macroblocks) {
//                if (eachBlock.getMotionVector() != null) {
//                    if (eachBlock.getReferenceLayer() == 0) {
//                        eachBlock.setReferenceLayer(1);
//                    } else if (eachBlock.getReferenceLayer() == 1) {
//                        eachBlock.setReferenceLayer(0);
//                    }
//                }
//            }
//        }
//        //if layer2 is background layer, switch it with layer0
//        else if (Math.max(Math.max(Math.max(layer1Count,layer2Count),layer3Count),layer0Count)==layer2Count){
//            for (Macroblock eachBlock : macroblocks) {
//                if (eachBlock.getMotionVector() != null) {
//                    if (eachBlock.getReferenceLayer() == 0) {
//                        eachBlock.setReferenceLayer(2);
//                    } else if (eachBlock.getReferenceLayer() == 2) {
//                        eachBlock.setReferenceLayer(0);
//                    }
//                }
//            }
//        }
//        //if layer3 is background layer, switch it with layer0
//        else if (Math.max(Math.max(Math.max(layer1Count,layer2Count),layer3Count),layer0Count)==layer3Count){
//            for (Macroblock eachBlock : macroblocks) {
//                if (eachBlock.getMotionVector() != null) {
//                    if (eachBlock.getReferenceLayer() == 0) {
//                        eachBlock.setReferenceLayer(3);
//                    } else if (eachBlock.getReferenceLayer() == 3) {
//                        eachBlock.setReferenceLayer(0);
//                    }
//                }
//            }
//        }
//
//        //re-clustering
//        changed=true;
//        while (changed){
//            changed=false;
//            //reset values
//            layer0CentroidX=0;
//            layer0CentroidY=0;
//            layer0TotalX=0;
//            layer0TotalY=0;
//            layer0Count=0;
//
//            layer1CentroidX=0;
//            layer1CentroidY=0;
//            layer1TotalX=0;
//            layer1TotalY=0;
//            layer1Count=0;
//
//            layer2CentroidX=0;
//            layer2CentroidY=0;
//            layer2TotalX=0;
//            layer2TotalY=0;
//            layer2Count=0;
//
//            layer3CentroidX=0;
//            layer3CentroidY=0;
//            layer3TotalX=0;
//            layer3TotalY=0;
//            layer3Count=0;
//
//            //calculate centroids
//            for (Macroblock eachBlock : macroblocks) {
//                if (eachBlock.getMotionVector() != null) {
//                    if (eachBlock.getReferenceLayer() == 1) {
//                        layer1TotalX += eachBlock.getMotionVector().x;
//                        layer1TotalY += eachBlock.getMotionVector().y;
//                        layer1Count++;
//                    } else if (eachBlock.getReferenceLayer() == 2) {
//                        layer2TotalX += eachBlock.getMotionVector().x;
//                        layer2TotalY += eachBlock.getMotionVector().y;
//                        layer2Count++;
//                    } else if (eachBlock.getReferenceLayer() == 3) {
//                        layer3TotalX += eachBlock.getMotionVector().x;
//                        layer3TotalY += eachBlock.getMotionVector().y;
//                        layer3Count++;
//                    } else {
//                        layer0TotalX += eachBlock.getMotionVector().x;
//                        layer0TotalY += eachBlock.getMotionVector().y;
//                        layer0Count++;
//                    }
//                }
//            }
//            layer0CentroidX=layer0TotalX/layer0Count;
//            layer0CentroidY=layer0TotalY/layer0Count;
//            layer1CentroidX=layer1TotalX/layer1Count;
//            layer1CentroidY=layer1TotalY/layer1Count;
//            layer2CentroidX=layer2TotalX/layer2Count;
//            layer2CentroidY=layer2TotalY/layer2Count;
//            layer3CentroidX=layer3TotalX/layer3Count;
//            layer3CentroidY=layer3TotalY/layer3Count;
//
//            //reassigning
//            for (Macroblock eachBlock : macroblocks) {
//                if (eachBlock.getMotionVector() != null) {
//                    int originalLayer = eachBlock.getReferenceLayer();
//                    //get recentCentroid value
//                    double recentCentroidX;
//                    double recentCentroidY;
//                    if (eachBlock.getReferenceLayer() == 1) {
//                        recentCentroidX = layer1CentroidX;
//                        recentCentroidY = layer1CentroidY;
//                    } else if (eachBlock.getReferenceLayer() == 2) {
//                        recentCentroidX = layer2CentroidX;
//                        recentCentroidY = layer2CentroidY;
//                    } else if (eachBlock.getReferenceLayer() == 3) {
//                        recentCentroidX = layer3CentroidX;
//                        recentCentroidY = layer3CentroidY;
//                    } else {
//                        recentCentroidX = layer0CentroidX;
//                        recentCentroidY = layer0CentroidY;
//                    }
//
//                    if (eachBlock.getMotionVector().getDistance(recentCentroidX, recentCentroidY) - eachBlock.getMotionVector().getDistance(layer1CentroidX, layer1CentroidY) >= 0) {
//                        eachBlock.setReferenceLayer(1);
//                        recentCentroidX = layer1CentroidX;
//                        recentCentroidY = layer1CentroidY;
//                    }
//                    if (eachBlock.getMotionVector().getDistance(recentCentroidX, recentCentroidY) - eachBlock.getMotionVector().getDistance(layer2CentroidX, layer2CentroidY) >= 0) {
//                        eachBlock.setReferenceLayer(2);
//                        recentCentroidX = layer2CentroidX;
//                        recentCentroidY = layer2CentroidY;
//                    }
//                    if (eachBlock.getMotionVector().getDistance(recentCentroidX, recentCentroidY) - eachBlock.getMotionVector().getDistance(layer3CentroidX, layer3CentroidY) >= 0) {
//                        eachBlock.setReferenceLayer(3);
//                        recentCentroidX = layer3CentroidX;
//                        recentCentroidY = layer3CentroidY;
//                    }
//                    if (eachBlock.getMotionVector().getDistance(recentCentroidX, recentCentroidY) - eachBlock.getMotionVector().getDistance(layer0CentroidX, layer0CentroidY) >= tolerantRate
//                            || eachBlock.getMotionVector().getDistance(layer0CentroidX, layer0CentroidY) < bgMinRadius) {
//                        eachBlock.setReferenceLayer(0);
//                    }
//
//                    if (originalLayer != eachBlock.getReferenceLayer()) {
//                        changed = true;
//                    }
//                }
//                else{
//                    eachBlock.setReferenceLayer(0);
//                }
//            }
//        }
//
//        //remove foreground layers that are probably background
//        layer0Count=0;
//        layer1Count=0;
//        layer2Count=0;
//        layer3Count=0;
//        for (Macroblock eachBlock : macroblocks) {
//            if (eachBlock.getMotionVector() != null) {
//                if(eachBlock.getReferenceLayer() == 1){
//                    layer1Count++;
//                }
//                else if(eachBlock.getReferenceLayer() == 2){
//                    layer2Count++;
//                }
//                else if(eachBlock.getReferenceLayer() == 3){
//                    layer3Count++;
//                }
//            }
//        }
//        if(layer1Count>960*540/1536){
//            for (Macroblock eachBlock : macroblocks) {
//                if (eachBlock.getReferenceLayer()==1) {
//                    eachBlock.setReferenceLayer(0);
//                }
//            }
//        }
//        if(layer2Count>960*540/1536){
//            for (Macroblock eachBlock : macroblocks) {
//                if (eachBlock.getReferenceLayer()==2) {
//                    eachBlock.setReferenceLayer(0);
//                }
//            }
//        }
//        if(layer3Count>960*540/1536){
//            for (Macroblock eachBlock : macroblocks) {
//                if (eachBlock.getReferenceLayer()==3) {
//                    eachBlock.setReferenceLayer(0);
//                }
//            }
//        }
////        if(Math.sqrt(Math.pow(layer0CentroidX-layer1CentroidX,2)+Math.pow(layer0CentroidY-layer1CentroidY,2))<1){
////            for (Macroblock eachBlock : macroblocks) {
////                if (eachBlock.getReferenceLayer()==1) {
////                    eachBlock.setReferenceLayer(0);
////                }
////            }
////        }
////        if(Math.sqrt(Math.pow(layer0CentroidX-layer2CentroidX,2)+Math.pow(layer0CentroidY-layer2CentroidY,2))<1){
////            for (Macroblock eachBlock : macroblocks) {
////                if (eachBlock.getReferenceLayer()==2) {
////                    eachBlock.setReferenceLayer(0);
////                }
////            }
////        }
////        if(Math.sqrt(Math.pow(layer0CentroidX-layer3CentroidX,2)+Math.pow(layer0CentroidY-layer3CentroidY,2))<1){
////            for (Macroblock eachBlock : macroblocks) {
////                if (eachBlock.getReferenceLayer()==3) {
////                    eachBlock.setReferenceLayer(0);
////                }
////            }
////        }
//
//        //Decide layers
//        for (Macroblock eachBlock : macroblocks) {
//            int bgCount=0;
//            if (referenceFrame!=null&&eachBlock.getReferenceLayer()==0) {
//                bgCount++;
//                if (referenceFrame.referenceFrame != null && referenceFrame.getBlock(eachBlock.getBlockIndex()).getReferenceLayer() == 0) {
//                    bgCount++;
//                    if (referenceFrame.referenceFrame.referenceFrame != null && referenceFrame.referenceFrame.getBlock(eachBlock.getBlockIndex()).getReferenceLayer() == 0) {
//                        bgCount++;
//                        if (referenceFrame.referenceFrame.referenceFrame.referenceFrame != null && referenceFrame.referenceFrame.referenceFrame.getBlock(eachBlock.getBlockIndex()).getReferenceLayer() == 0) {
//                            bgCount++;
//                            if (referenceFrame.referenceFrame.referenceFrame.referenceFrame.referenceFrame != null && referenceFrame.referenceFrame.referenceFrame.referenceFrame.getBlock(eachBlock.getBlockIndex()).getReferenceLayer() == 0) {
//                                bgCount++;
//                            }
//                        }
//                    }
//                }
//            }
//            if (bgCount>3){
//                eachBlock.setLayer(0);
//            }
//            else if(bgCount<2){
//                eachBlock.setLayer(1);
//            }
//            else{
//                eachBlock.setLayer(eachBlock.getReferenceLayer());
//            }
//        }


        //Jeff's v4.0
//        int NUM_LAYERS = 8; //Number of layers to divide the vectors into
//        double bgMinRadius=2; //more background when larger, should be positive, originally set to 3, I have had good results with 2
//        double tolerantRate=0; //more foreground when larger, can be negative
//        int BACKGROUND_COUNT_TOLERANCE = 3; //Originally set to 3
//        int FOREGROUND_COUNT_TOLERANCE = 2; //Originally set to 1, I have had good results with 2
//        //random assigning
//        for (Macroblock eachBlock : macroblocks) {
//            int ran = ThreadLocalRandom.current().nextInt(0, NUM_LAYERS);
//            eachBlock.setReferenceLayer(ran);
//        }
//        double[] centroidsX = new double[NUM_LAYERS];
//        double[] centroidsY = new double[NUM_LAYERS];
//        int[] totalX = new int[NUM_LAYERS];
//        int[] totalY = new int[NUM_LAYERS];
//        int[] layerCount = new int[NUM_LAYERS];
//        for (int i =0;i<NUM_LAYERS;i++){
//            centroidsX[i] = 0;
//            centroidsY[i] = 0;
//            totalX[i] = 0;
//            totalY[i] = 0;
//            layerCount[i] = 0;
//        }
//        int thislayer;
//        //clustering
//        boolean changed=true;
//        while (changed){
//            changed=false;
//
//            //calculate centroids
//            for (Macroblock eachBlock : macroblocks) {
//                if (eachBlock.getMotionVector() != null) {
//                    thislayer = eachBlock.getReferenceLayer();
//                    totalX[thislayer] += eachBlock.getMotionVector().x;
//                    totalY[thislayer] += eachBlock.getMotionVector().y;
//                    layerCount[thislayer]++;
//                }
//            }
//
//            for (int i =0;i<NUM_LAYERS;i++){
//                if(layerCount[i] == 0){
//                    centroidsX[i] = -99999;
//                    centroidsY[i] = -99999;
//                }
//                else {
//                    centroidsX[i] = totalX[i] / layerCount[i];
//                    centroidsY[i] = totalY[i] / layerCount[i];
//                }
//            }
//
//            //reassigning
//            for (Macroblock eachBlock : macroblocks) {
//                if (eachBlock.getMotionVector() != null) {
//                    int originalLayer = eachBlock.getReferenceLayer();
//                    //get recentCentroid value
//                    double minDist = eachBlock.getMotionVector().getDistance2(centroidsX[originalLayer], centroidsY[originalLayer]);
//                    double thisDist;
//                    for (int i =0;i<NUM_LAYERS;i++){
//                        thisDist = eachBlock.getMotionVector().getDistance2(centroidsX[i], centroidsY[i]);
//                        if(thisDist<minDist){
//                            minDist = thisDist;
//                            eachBlock.setReferenceLayer(i);
//                        }
//                    }
//
//                    if (originalLayer != eachBlock.getReferenceLayer()) {
//                        changed = true;
//                    }
//                }
//            }
//
//        }
//
//        //find out background layer cluster
//        for (int i =0;i<NUM_LAYERS;i++){
//            layerCount[i] = 0;
//        }
//
//        for (Macroblock eachBlock : macroblocks) {
//            if (eachBlock.getMotionVector() != null) {
//                layerCount[eachBlock.getReferenceLayer()]++;
//            }
//        }
//
//        int max = layerCount[0];
//        int maxlayer = 0;
//        for (int i =0;i<NUM_LAYERS;i++){
//            if(layerCount[i]>max){
//                max = layerCount[i];
//                maxlayer = i;
//            }
//        }
//
//        if(maxlayer != 0){
//            for (Macroblock eachBlock : macroblocks) {
//                if (eachBlock.getMotionVector() != null) {
//                    if (eachBlock.getReferenceLayer() == 0) {
//                        eachBlock.setReferenceLayer(maxlayer);
//                    } else if (eachBlock.getReferenceLayer() == maxlayer) {
//                        eachBlock.setReferenceLayer(0);
//                    }
//                }
//            }
//        }
//
//
//        //re-clustering
//        changed=true;
//        while (changed){
//            changed=false;
//            //reset values
//            for (int i =0;i<NUM_LAYERS;i++){
//                centroidsX[i] = 0;
//                centroidsY[i] = 0;
//                totalX[i] = 0;
//                totalY[i] = 0;
//                layerCount[i] = 0;
//            }
//
//            //calculate centroids
//            for (Macroblock eachBlock : macroblocks) {
//                if (eachBlock.getMotionVector() != null) {
//                    thislayer = eachBlock.getReferenceLayer();
//                    totalX[thislayer] += eachBlock.getMotionVector().x;
//                    totalY[thislayer] += eachBlock.getMotionVector().y;
//                    layerCount[thislayer]++;
//                }
//            }
//
//            for (int i =0;i<NUM_LAYERS;i++){
//                if(layerCount[i] == 0){
//                    centroidsX[i] = -99999;
//                    centroidsY[i] = -99999;
//                }
//                else {
//                    centroidsX[i] = totalX[i] / layerCount[i];
//                    centroidsY[i] = totalY[i] / layerCount[i];
//                }
//            }
//
//            //reassigning
//            for (Macroblock eachBlock : macroblocks) {
//                if (eachBlock.getMotionVector() != null) {
//                    int originalLayer = eachBlock.getReferenceLayer();
//                    //get recentCentroid value
//                    double minDist = eachBlock.getMotionVector().getDistance2(centroidsX[originalLayer], centroidsY[originalLayer]);
//                    double thisDist;
//                    for (int i =0;i<NUM_LAYERS;i++){
//                        thisDist = eachBlock.getMotionVector().getDistance2(centroidsX[i], centroidsY[i]);
//                        if(thisDist<minDist){
//                            minDist = thisDist;
//                            eachBlock.setReferenceLayer(i);
//                        }
//                    }
//
//                    if (originalLayer != eachBlock.getReferenceLayer()) {
//                        changed = true;
//                    }
//                }
//            }
//        }
//
//        //remove foreground layers that are probably background
//        double layer0CentroidX = centroidsX[0];
//        double layer0CentroidY = centroidsY[0];
//        for (int i = 1;i<NUM_LAYERS;i++){
//            if(Math.sqrt(Math.pow(layer0CentroidX-centroidsX[i],2)+Math.pow(layer0CentroidY-centroidsY[i],2))<bgMinRadius){
//                for (Macroblock eachBlock : macroblocks) {
//                    if (eachBlock.getReferenceLayer()==i) {
//                        eachBlock.setReferenceLayer(0);
//                    }
//                }
//            }
//        }
//
//        //Decide layers
//        for (Macroblock eachBlock : macroblocks) {
//            int bgCount=0;
//            if (referenceFrame!=null&&eachBlock.getReferenceLayer()==0) {
//                bgCount++;
//                if (referenceFrame.referenceFrame != null && referenceFrame.getBlock(eachBlock.getBlockIndex()).getReferenceLayer() == 0) {
//                    bgCount++;
//                    if (referenceFrame.referenceFrame.referenceFrame != null && referenceFrame.referenceFrame.getBlock(eachBlock.getBlockIndex()).getReferenceLayer() == 0) {
//                        bgCount++;
//                        if (referenceFrame.referenceFrame.referenceFrame.referenceFrame != null && referenceFrame.referenceFrame.referenceFrame.getBlock(eachBlock.getBlockIndex()).getReferenceLayer() == 0) {
//                            bgCount++;
//                            if (referenceFrame.referenceFrame.referenceFrame.referenceFrame.referenceFrame != null && referenceFrame.referenceFrame.referenceFrame.referenceFrame.getBlock(eachBlock.getBlockIndex()).getReferenceLayer() == 0) {
//                                bgCount++;
//                            }
//                        }
//                    }
//                }
//            }
//            if (bgCount>BACKGROUND_COUNT_TOLERANCE){
//                eachBlock.setLayer(0);
//            }
//            else if(bgCount<FOREGROUND_COUNT_TOLERANCE){
//                eachBlock.setLayer(1);
//            }
//            else{
//                eachBlock.setLayer(eachBlock.getReferenceLayer());
//            }
//        }


        //v5.0

        int macroblockHeight = 1 + (height - 1) / MACROBLOCK_LENGTH;
        int macroblockWidth = 1 + (width - 1) / MACROBLOCK_LENGTH;

        int NUM_LAYERS = 8; //Number of layers to divide the vectors into

//        double tolerantRate=0; //more foreground when larger, can be negative
//        double bgMinRadius = 1.5; //more background when larger, should be positive, originally set to 3, I have had good results with 2
        int PREDICT_MARK_RADIUS = 8; //better be around 8, marking without considering motion vectors when larger than 16
        double BACKGROUND_ENFORCED_RADIUS = 3; //more background when larger, should be positive, less aggressive then bgMinRadius
        double BACKGROUND_ENFORCED_FACTOR = 1; //more background when larger, should be positive, around 1
        int BACKGROUND_COUNT_TOLERANCE = 4; //Originally set to 3
        int FOREGROUND_COUNT_TOLERANCE = 2; //Originally set to 1, I have had good results with 2

//        //random assigning
//        for (Macroblock eachBlock : macroblocks) {
//            int ran = ThreadLocalRandom.current().nextInt(0, NUM_LAYERS);
//            eachBlock.setReferenceLayer(ran);
//        }

        //initiating
        double[] centroidsX = new double[NUM_LAYERS];
        double[] centroidsY = new double[NUM_LAYERS];
        int[] totalX = new int[NUM_LAYERS];
        int[] totalY = new int[NUM_LAYERS];
        int[] layerCount = new int[NUM_LAYERS];

        //assigning initial centroids
//        centroidsX[0] = 0;
//        centroidsY[0] = 0;
        centroidsX[1] = 10;
        centroidsY[1] = 0;
        centroidsX[2] = -10;
        centroidsY[2] = 0;
        centroidsX[3] = 0;
        centroidsY[3] = 10;
        centroidsX[4] = 0;
        centroidsY[4] = -10;
        centroidsX[5] = 10;
        centroidsY[5] = 10;
        centroidsX[6] = -10;
        centroidsY[6] = 10;
        centroidsX[7] = 10;
        centroidsY[7] = -10;
        centroidsX[0] = -10;
        centroidsY[0] = -10;


        if (referenceFrame != null) {
            for (Macroblock eachBlock : macroblocks) {
                if (eachBlock.getMotionVector() != null) {
                    int previousLayer = referenceFrame.macroblocks[eachBlock.estimateBlockIndexAtLastFrame()].getLayer();

                    totalX[previousLayer] += eachBlock.getMotionVector().x;
                    totalY[previousLayer] += eachBlock.getMotionVector().y;
                    layerCount[previousLayer]++;
                    eachBlock.setReferenceLayer(previousLayer);
                }
            }

            for (int i = 0; i < NUM_LAYERS; i++) {

                // Too much blocks in same layer
                if (layerCount[i] > macroblocks.length * 0.8f) {
                    // let centroid stay the same so that clustering could work regularly
                } else if(layerCount[i] != 0) {
                    centroidsX[i] = totalX[i] / layerCount[i];
                    centroidsY[i] = totalY[i] / layerCount[i];
                }

                totalX[i] = 0;
                totalY[i] = 0;
                layerCount[i] = 0;
            }
        }

        int thislayer;
        //clustering
        boolean changed = true;
        while (changed) {
            changed = false;

            //assigning
            for (Macroblock eachBlock : macroblocks) {
                if (eachBlock.getMotionVector() != null) {
                    int originalLayer = eachBlock.getReferenceLayer();
                    //get recentCentroid value
                    double minDist = eachBlock.getMotionVector().getDistance2(centroidsX[originalLayer], centroidsY[originalLayer]);
                    double thisDist;
                    for (int i = 0; i < NUM_LAYERS; i++) {
                        thisDist = eachBlock.getMotionVector().getDistance2(centroidsX[i], centroidsY[i]);
                        if (thisDist < minDist) {
                            minDist = thisDist;
                            eachBlock.setReferenceLayer(i);
                        }
                    }

                    if (originalLayer != eachBlock.getReferenceLayer()) {
                        changed = true;
                    }
                }
            }

            //reset values
            for (int i = 0; i < NUM_LAYERS; i++) {
                centroidsX[i] = 0;
                centroidsY[i] = 0;
                totalX[i] = 0;
                totalY[i] = 0;
                layerCount[i] = 0;
            }

            //calculate centroids
            for (Macroblock eachBlock : macroblocks) {
                if (eachBlock.getMotionVector() != null) {
                    thislayer = eachBlock.getReferenceLayer();
                    totalX[thislayer] += eachBlock.getMotionVector().x;
                    totalY[thislayer] += eachBlock.getMotionVector().y;
                    layerCount[thislayer]++;
                }
            }

            for (int i = 0; i < NUM_LAYERS; i++) {
                if (layerCount[i] == 0) {
                    centroidsX[i] = -99999;
                    centroidsY[i] = -99999;
                } else {
                    centroidsX[i] = totalX[i] / layerCount[i];
                    centroidsY[i] = totalY[i] / layerCount[i];
                }
            }

        }

        //find out background layer cluster
        for (int i = 0; i < NUM_LAYERS; i++) {
            layerCount[i] = 0;
        }

        for (Macroblock eachBlock : macroblocks) {
            if (eachBlock.getMotionVector() != null) {
                layerCount[eachBlock.getReferenceLayer()]++;
            }
        }

        int max = layerCount[0];
        int maxlayer = 0;
        for (int i = 0; i < NUM_LAYERS; i++) {
            if (layerCount[i] > max) {
                max = layerCount[i];
                maxlayer = i;
            }
        }

        if (maxlayer != 0) {
            for (Macroblock eachBlock : macroblocks) {
                if (eachBlock.getMotionVector() != null) {
                    if (eachBlock.getReferenceLayer() == 0) {
                        eachBlock.setReferenceLayer(maxlayer);
                    } else if (eachBlock.getReferenceLayer() == maxlayer) {
                        eachBlock.setReferenceLayer(0);
                    }
                }
            }
        }


        //re-clustering
        changed = true;
        while (changed) {
            changed = false;
            //reset values
            for (int i = 0; i < NUM_LAYERS; i++) {
                centroidsX[i] = 0;
                centroidsY[i] = 0;
                totalX[i] = 0;
                totalY[i] = 0;
                layerCount[i] = 0;
            }

            //calculate centroids
            for (Macroblock eachBlock : macroblocks) {
                if (eachBlock.getMotionVector() != null) {
                    thislayer = eachBlock.getReferenceLayer();
                    totalX[thislayer] += eachBlock.getMotionVector().x;
                    totalY[thislayer] += eachBlock.getMotionVector().y;
                    layerCount[thislayer]++;
                }
            }

            for (int i = 0; i < NUM_LAYERS; i++) {
                if (layerCount[i] == 0) {
                    centroidsX[i] = -99999;
                    centroidsY[i] = -99999;
                } else {
                    centroidsX[i] = totalX[i] / layerCount[i];
                    centroidsY[i] = totalY[i] / layerCount[i];
                }
            }

            //reassigning with BACKGROUND_ENFORCED_RADIUS
            for (Macroblock eachBlock : macroblocks) {
                if (eachBlock.getMotionVector() != null) {
                    int originalLayer = eachBlock.getReferenceLayer();
                    //get recentCentroid value
                    double minDist = eachBlock.getMotionVector().getDistance2(centroidsX[originalLayer], centroidsY[originalLayer]);
                    double thisDist;
                    if (eachBlock.getMotionVector().getDistance2(centroidsX[0], centroidsY[0]) < BACKGROUND_ENFORCED_RADIUS) {
                        eachBlock.setReferenceLayer(0);
                    } else {
                        for (int i = 0; i < NUM_LAYERS; i++) {
                            thisDist = eachBlock.getMotionVector().getDistance2(centroidsX[i], centroidsY[i]);
                            if (thisDist < minDist) {
                                minDist = thisDist;
                                eachBlock.setReferenceLayer(i);
                            }
                        }
                    }
                    if (originalLayer != eachBlock.getReferenceLayer()) {
                        changed = true;
                    }
                }
            }
        }

        //remove foreground layers that are probably background
        int presentNum_Layers = 0;
        for (int i = 0; i < NUM_LAYERS; i++) {
            if (layerCount[i] != 0) {
                presentNum_Layers++;
            }
            layerCount[i] = 0;
        }

        for (Macroblock eachBlock : macroblocks) {
            if (eachBlock.getMotionVector() != null) {
                thislayer = eachBlock.getReferenceLayer();
                layerCount[thislayer]++;
            }
        }

        // Decide layers by last frame
//        if (referenceFrame != null && referenceFrame.referenceFrame != null){
//            for (int layer = 0; layer < NUM_LAYERS; layer++) {
//                int blockCount = 0;
//                for (Macroblock eachBlock : macroblocks) {
//                    if (eachBlock.getMotionVector() != null && eachBlock.getReferenceLayer() == layer) {
//                        blockStats[referenceFrame.macroblocks[eachBlock.estimateBlockIndexAtLastFrame()].getLayer()]++;
//                        blockCount++;
//                    }
//                }
//
//                int popularLayer = 0;
//                int popularLayerNums = blockStats[0];
//                for (int i = 1; i < NUM_LAYERS; i++) {
//                    if (blockStats[i] > popularLayerNums) {
//                        popularLayerNums = blockStats[i];
//                        popularLayer = i;
//                    }
//                }
//
//                if (popularLayerNums >= blockCount * 0.8f) {
//                    for (Macroblock eachBlock : macroblocks) {
//                        if (eachBlock.getMotionVector() != null && eachBlock.getReferenceLayer() == layer) {
//                            eachBlock.setReferenceLayer(popularLayer);
//                        }
//                    }
//                }
//
//                Arrays.fill(blockStats, 0);
//            }
//
//        }

        int[] horizontal = new int[NUM_LAYERS];
        int[] vertical = new int[NUM_LAYERS];
        for (Macroblock eachBlock : macroblocks) {
            if (eachBlock.getMotionVector() != null) {
                int index = eachBlock.getBlockIndex();

                // Up
                if (index - macroblockWidth > 0 && macroblocks[index - macroblockWidth].getMotionVector() != null) {
                    vertical[macroblocks[index - macroblockWidth].getReferenceLayer()]++;
                }

                // Down
                if (index + macroblockWidth < macroblocks.length && macroblocks[index + macroblockWidth].getMotionVector() != null) {
                    vertical[macroblocks[index + macroblockWidth].getReferenceLayer()]++;
                }

                // Left
                if ((index % macroblockWidth) > 1 && macroblocks[index - 1].getMotionVector() != null) {
                    horizontal[macroblocks[index - 1].getReferenceLayer()]++;
                }
                // Right
                if (((index + 1) % macroblockWidth) != 0 && macroblocks[index + 1].getMotionVector() != null) {
                    horizontal[macroblocks[index + 1].getReferenceLayer()]++;
                }

                for (int layer = 0; layer < NUM_LAYERS; layer++) {
                    if ((horizontal[layer] >= 2 || vertical[layer] >= 2) && horizontal[eachBlock.getReferenceLayer()] == 0 && vertical[eachBlock.getReferenceLayer()] == 0) {
                        eachBlock.setReferenceLayer(layer);
                        break;
                    }
                }
                Arrays.fill(horizontal, 0);
                Arrays.fill(vertical, 0);
            }
        }


        for (int i = 1; i < NUM_LAYERS; i++) {
            if (layerCount[i] > macroblocks.length / (NUM_LAYERS * BACKGROUND_ENFORCED_FACTOR)) {
                for (Macroblock eachBlock : macroblocks) {
                    if (eachBlock.getReferenceLayer() == i) {
                        eachBlock.setReferenceLayer(0);
                    }
                }
            }
        }

//        double layer0CentroidX = centroidsX[0];
//        double layer0CentroidY = centroidsY[0];
//        for (int i = 1;i<NUM_LAYERS;i++){
//            if((Math.pow(layer0CentroidX-centroidsX[i],2)+Math.pow(layer0CentroidY-centroidsY[i],2))<bgMinRadius){
//                for (Macroblock eachBlock : macroblocks) {
//                    if (eachBlock.getReferenceLayer()==i) {
//                        eachBlock.setReferenceLayer(0);
//                    }
//                }
//            }
//        }

        //Decide layers by marking the location of the same block of previous frame
        for (Macroblock eachBlock : macroblocks) {
            int bgCount = 0;
            //track macroblock motion
            int blockVectorX=0;
            int blockVectorY=0;
            int blockMotionX=0;
            int blockMotionY=0;
            int blockIndex=eachBlock.getBlockIndex();

            if (referenceFrame != null && eachBlock.getReferenceLayer() == 0) {
                bgCount++;
                if (this.getBlock(blockIndex).getMotionVector()!=null){
                    blockVectorX+=getBlock(blockIndex).getMotionVector().x;
                    blockVectorY+=getBlock(blockIndex).getMotionVector().y;
                }
                blockMotionX=blockVectorX/MACROBLOCK_LENGTH+(blockVectorX%MACROBLOCK_LENGTH)/PREDICT_MARK_RADIUS;
                blockMotionY=blockVectorY/MACROBLOCK_LENGTH+(blockVectorY%MACROBLOCK_LENGTH)/PREDICT_MARK_RADIUS;
                blockIndex=eachBlock.getBlockIndex()+(1 + (width - 1) / MACROBLOCK_LENGTH)*blockMotionY+blockMotionX;
                if (referenceFrame.referenceFrame != null && referenceFrame.getBlock(blockIndex).getReferenceLayer() == 0) {
                    bgCount++;
                    if (referenceFrame.getBlock(blockIndex).getMotionVector()!=null){
                        blockVectorX+=referenceFrame.getBlock(blockIndex).getMotionVector().x;
                        blockVectorY+=referenceFrame.getBlock(blockIndex).getMotionVector().y;
                    }
                    blockMotionX=blockVectorX/MACROBLOCK_LENGTH+(blockVectorX%MACROBLOCK_LENGTH)/PREDICT_MARK_RADIUS;
                    blockMotionY=blockVectorY/MACROBLOCK_LENGTH+(blockVectorY%MACROBLOCK_LENGTH)/PREDICT_MARK_RADIUS;
                    blockIndex=eachBlock.getBlockIndex()+(1 + (width - 1) / MACROBLOCK_LENGTH)*blockMotionY+blockMotionX;
                    if (referenceFrame.referenceFrame.referenceFrame != null && referenceFrame.referenceFrame.getBlock(blockIndex).getReferenceLayer() == 0) {
                        bgCount++;
                        if (referenceFrame.referenceFrame.getBlock(blockIndex).getMotionVector()!=null){
                            blockVectorX+=referenceFrame.referenceFrame.getBlock(blockIndex).getMotionVector().x;
                            blockVectorY+=referenceFrame.referenceFrame.getBlock(blockIndex).getMotionVector().y;
                        }
                        blockMotionX=blockVectorX/MACROBLOCK_LENGTH+(blockVectorX%MACROBLOCK_LENGTH)/PREDICT_MARK_RADIUS;
                        blockMotionY=blockVectorY/MACROBLOCK_LENGTH+(blockVectorY%MACROBLOCK_LENGTH)/PREDICT_MARK_RADIUS;
                        blockIndex=eachBlock.getBlockIndex()+(1 + (width - 1) / MACROBLOCK_LENGTH)*blockMotionY+blockMotionX;
                        if (referenceFrame.referenceFrame.referenceFrame.referenceFrame != null && referenceFrame.referenceFrame.referenceFrame.getBlock(blockIndex).getReferenceLayer() == 0) {
                            bgCount++;
                            if (referenceFrame.referenceFrame.referenceFrame.getBlock(blockIndex).getMotionVector()!=null){
                                blockVectorX+=referenceFrame.referenceFrame.referenceFrame.getBlock(blockIndex).getMotionVector().x;
                                blockVectorY+=referenceFrame.referenceFrame.referenceFrame.getBlock(blockIndex).getMotionVector().y;
                            }
                            blockMotionX=blockVectorX/MACROBLOCK_LENGTH+(blockVectorX%MACROBLOCK_LENGTH)/PREDICT_MARK_RADIUS;
                            blockMotionY=blockVectorY/MACROBLOCK_LENGTH+(blockVectorY%MACROBLOCK_LENGTH)/PREDICT_MARK_RADIUS;
                            blockIndex=eachBlock.getBlockIndex()+(1 + (width - 1) / MACROBLOCK_LENGTH)*blockMotionY+blockMotionX;
                            if (referenceFrame.referenceFrame.referenceFrame.referenceFrame.referenceFrame != null && referenceFrame.referenceFrame.referenceFrame.referenceFrame.getBlock(blockIndex).getReferenceLayer() == 0) {
                                bgCount++;
                                if (referenceFrame.referenceFrame.referenceFrame.referenceFrame.getBlock(blockIndex).getMotionVector()!=null){
                                    blockVectorX+=referenceFrame.referenceFrame.referenceFrame.referenceFrame.getBlock(blockIndex).getMotionVector().x;
                                    blockVectorY+=referenceFrame.referenceFrame.referenceFrame.referenceFrame.getBlock(blockIndex).getMotionVector().y;
                                }
                                blockMotionX=blockVectorX/MACROBLOCK_LENGTH+(blockVectorX%MACROBLOCK_LENGTH)/PREDICT_MARK_RADIUS;
                                blockMotionY=blockVectorY/MACROBLOCK_LENGTH+(blockVectorY%MACROBLOCK_LENGTH)/PREDICT_MARK_RADIUS;
                                blockIndex=eachBlock.getBlockIndex()+(1 + (width - 1) / MACROBLOCK_LENGTH)*blockMotionY+blockMotionX;
                                if (referenceFrame.referenceFrame.referenceFrame.referenceFrame.referenceFrame.getBlock(blockIndex).getReferenceLayer() == 0) {
                                    bgCount++;
                                }
                            }
                        }
                    }
                }
            }
            if (bgCount > BACKGROUND_COUNT_TOLERANCE) {
                eachBlock.setLayer(0);
            } else if (bgCount < FOREGROUND_COUNT_TOLERANCE) {
                eachBlock.setLayer(1);
            } else {
                eachBlock.setLayer(eachBlock.getReferenceLayer());
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
        ByteBuffer buffer = ByteBuffer.allocate(dctValues.length * 64 * 4);
        for (int i = 0; i < dctValues.length; i++) {
            for (int j = 0; j < 64; j++) {
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

    public void serialize(FileChannel os) throws IOException {
        // Serialize all marcobloks' layers
        byteBuffer.clear();
        for (Macroblock mc : this.macroblocks) {
            byteBuffer.putInt(mc.getLayer());
        }
        byteBuffer.flip();
        os.write(byteBuffer);

        // Serialize motion vectors
        byteBuffer.clear();
        if (motionVectors == null) {
            byteBuffer.putInt(0);
        } else {
            byteBuffer.putInt(motionVectors.size());

            for (Macroblock mb : motionVectors) {

                byteBuffer.putInt(mb.getBlockIndex());

                MotionVector v = mb.getMotionVector();
                byteBuffer.putInt(v.x);
                byteBuffer.putInt(v.y);
            }
        }
        byteBuffer.flip();
        os.write(byteBuffer);

        // Serialize "JPEG" Image
        byteBuffer.clear();
        FloatBuffer dctBuffer = byteBuffer.asFloatBuffer();
        for (int i = 0; i < dctValues.length; i++) {
            dctBuffer.put(dctValues[i]);
        }

        byteBuffer.limit(byteBuffer.capacity());
        os.write(byteBuffer);
    }


    public void getRawImage(int[] rawImage) {
        Utils.convertToRGB(imageY, imageU, imageV, rawImage);

//        for (Macroblock mb : macroblocks) {
//            if (mb.isBackgroundLayer())
//                continue;
//
//            // Mark by green
//            int x = mb.getX();
//            int y = mb.getY();
//            for (int i = 0; i < MACROBLOCK_LENGTH; i++) {
//                for (int j = 0; j < MACROBLOCK_LENGTH; j++) {
//                    rawImage[(y + i) * width + (x + j)] |= (0xff << 8);
//                }
//            }
//        }
    }
}
