package cs576;

import java.io.DataOutput;

/**
 * Created by Jeffreye on 4/16/2017.
 */
public interface Frame {
    int INTERFRAME = 0;
    int PREDICTIVEFRAME = 1;

    int getFrameType();
    void serialize(DataOutput os)throws java.io.IOException;
    byte[] getRawImage();
}
