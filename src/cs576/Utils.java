package cs576;

/**
 * Created by Jeffreye on 4/15/2017.
 */
public class Utils {

    public static int convertToY(byte r, byte g,byte b){
        return Math.round(0.299f * (r & 0xff) + 0.587f * (g & 0xff) + 0.114f * (b & 0xff));
    }
}
