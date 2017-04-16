package cs576;

import java.io.OutputStream;

/**
 * Created by Jeffreye on 4/16/2017.
 */
public interface Frame {
    void serialize(OutputStream os)throws java.io.IOException;
}
