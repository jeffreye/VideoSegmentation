package cs576;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by Jeffreye on 4/1/2017.
 */
public class VideoEncoder {

    static final int blocksize = 16;


    FileInputStream inputStream;
    FileOutputStream outputStream;
    int width;
    int height;
    int k = 10;

    byte[] bytes;

    int macroblockWidth;
    int macroblockHeight;

    ArrayList<Frame> frames;

    public VideoEncoder(String inputFile,String outputFile,int width,int height) throws FileNotFoundException{
        inputStream = new FileInputStream(inputFile);
        outputStream = new FileOutputStream(outputFile);
        this.width = width;
        this.height = height;
        macroblockWidth = 1 + (width - 1) / blocksize; // ceiling
        macroblockHeight = 1 + (height - 1) / blocksize;
        frames = new ArrayList<>();
    }


    byte[] readImage() throws IOException {
        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length && (numRead = inputStream.read(bytes, offset, bytes.length - offset)) >= 0) {
            offset += numRead;
        }
        return bytes;
    }

    public void encode() throws  IOException {
        bytes = new byte[width * height * 3];
        segment(4);

        int frameCount = 0;
        for (Frame f : frames){
            f.serialize(outputStream);
            System.out.print("Frame "+ Integer.toString(++frameCount) + "/" + Integer.toString(frames.size()) + " Serialized.\r");
        }

        inputStream.close();
        outputStream.close();
        bytes = null;
    }

    /**
     * Semantic Layering of Video
     * @param predictFrames the number of predict frames after an Intraframe
     */
    private void segment(int predictFrames) throws IOException {

        // Mark first frame as an I frame
        int count = 1;
        Interframe previous = null;
        int frameCount = 0;
        int frameNumbers = inputStream.available() / (3 * height * width);
        while (inputStream.available() != 0){

            if (--count <= 0){
                // I frame
                Interframe current = divideImageFrame(readImage());
                previous = current;
                count = predictFrames;


                frames.add(current);
            }
            else{
                // P frame
                PredictiveFrame frame = new PredictiveFrame(previous,readImage(),k,height,width);

                frames.add(frame);
            }
            System.out.print("Frame "+ Integer.toString(++frameCount) + "/" + Integer.toString(frameNumbers) + " Processed.\r");
        }
        inputStream.close();
        System.out.println();
        // Organize into background and foregrounds
        groupRegions();
    }

    /**
     * Assign all macroblocks a layer index
     */
    private void groupRegions() {
        /**
         * •Contiguous or adjacent
           •The motion vectors are all consistent – important!
            The consistency of the motion vector direction gives you an indication
            that all the macroblocks probably belong to the same object and are moving in a certain direction
         */
    }


    private Interframe divideImageFrame(byte[] buffer) {
        Interframe frame = new Interframe(macroblockHeight,macroblockWidth);

        int ind = 0;
        for (int y = 0; y < height; y++) {
            int my = y / blocksize;
            int block_y = y - my * blocksize;
            for (int x = 0; x < width; x++) {

                byte r = buffer[ind];
                byte g = buffer[ind + height * width];
                byte b = buffer[ind + height * width * 2];
                ind++;

                int mx = x / blocksize;
                int block_x = x - mx * blocksize;
                frame.getBlock(mx,my).setPixel(block_x,block_y,r,g,b);
            }
        }
        return frame;
    }

    public static void main(String[] argv){
        if (argv.length == 1 && argv[0].endsWith(".rgb")){
            try{
                String input = argv[0];
                String output = input.substring(0,input.length() - 4).concat(".cmp");
                VideoEncoder encoder = new VideoEncoder(input,output,960,540);
                encoder.encode();
            }
            catch (Exception e){
                e.printStackTrace(System.err);
            }

        }
        else{
            System.err.println("Invalid arguments");
            System.err.println("Only accepts file name as argument");
        }
    }
}
