package cs576;

import java.io.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Jeffreye on 4/1/2017.
 */
public class VideoDecoder {

    private String inputFile;
    private String outputFile;
    private int width;
    private int height;

    public VideoDecoder(String inputFile, String outputFile) throws FileNotFoundException {
        this.inputFile = inputFile;
        this.outputFile = outputFile;
    }

    public void decode() throws IOException {
        DataInputStream inputStream = new DataInputStream(new FileInputStream(this.inputFile));
        width = inputStream.readInt();
        height = inputStream.readInt();
        int frameNumbers = inputStream.readInt();

        int frameCount = 0;
        Frame lastFrame = null;

        FileOutputStream outputStream = new FileOutputStream(outputFile);

        while (inputStream.available() != 0) {
            int type = inputStream.read();
            if (type == Frame.INTERFRAME) {
                lastFrame = new Interframe(height, width, inputStream);

            } else if (type == Frame.PREDICTIVEFRAME) {
                lastFrame = new PredictiveFrame(lastFrame, inputStream);
            }

            outputStream.write(lastFrame.getRawImage());
            outputStream.flush();

            frameCount++;
            System.out.print("\rFrame " + Integer.toString(frameCount) + "/" + Integer.toString(frameNumbers) + " Processed.");

        }


        inputStream.close();
        outputStream.close();
    }


    public static void main(String[] argv) {
        if (argv.length == 1 && argv[0].endsWith(".cmp")) {
            try {
                String input = argv[0];
                String output = input.substring(0, input.length() - 4).concat(".raw");
                VideoDecoder decoder = new VideoDecoder(input, output);
                decoder.decode();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }

        } else {
            System.err.println("Invalid arguments");
            System.err.println("Only accepts file name as argument");
        }
    }
}
