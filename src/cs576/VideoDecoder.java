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

    private ConcurrentSkipListMap<Integer, Frame> frames;
    private String inputFile;
    private String outputFile;
    private int width;
    private int height;
    private int k = 10;

    public VideoDecoder(String inputFile, String outputFile) throws FileNotFoundException {
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        frames = new ConcurrentSkipListMap<>();
    }

    public void decode() throws IOException {
        DataInputStream inputStream = new DataInputStream(new FileInputStream(this.inputFile));
        width = inputStream.readInt();
        height = inputStream.readInt();
        int frameNumbers = inputStream.readInt();

        CompletableFuture[] futures = new CompletableFuture[frameNumbers];
        int frameCount = 0;
        CompletableFuture<Interframe> lastInterframe = null;
        Interframe interframe = null;

        while (inputStream.available() != 0) {
            int type = inputStream.read();
            if (type == Frame.INTERFRAME) {
                int frameIndex = frameCount++;

//                futures[frameIndex] = lastInterframe =
//                        CompletableFuture
//                                .supplyAsync(() -> {
//                                    Interframe interframe = null;
                                    try {
                                        interframe = new Interframe(height, width, inputStream);
                                        frames.put(frameIndex, interframe);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
//                                    return current;
//                                });

            } else if (type == Frame.PREDICTIVEFRAME) {
                int predictFrameIndex = frameCount++;

//                futures[predictFrameIndex] =
//                        lastInterframe.thenAcceptAsync(interframe -> {
                            try {
                                frames.put(predictFrameIndex, new PredictiveFrame(interframe, inputStream));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
//                        });

            }
            System.out.print("\rFrame " + Integer.toString(frameCount) + "/" + Integer.toString(frameNumbers) + " Processed.");

        }


        inputStream.close();
//
//        CompletableFuture<Void> allfutures = CompletableFuture.allOf(futures);
//
//        while (!allfutures.isDone()) {
//            System.out.print("\r");
////            System.out.print("Frame " + Integer.toString(processedFrame.get()) + "/" + Integer.toString(frameNumbers) + " Processed.");
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//
//        if(allfutures.isCompletedExceptionally())
//            allfutures.exceptionally(throwable -> {throwable.printStackTrace(System.err); return null;});

        FileOutputStream outputStream = new FileOutputStream(outputFile);
        for (Map.Entry<Integer,Frame> entry : frames.entrySet()){
            outputStream.write(entry.getValue().getRawImage());
            outputStream.flush();
        }
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
