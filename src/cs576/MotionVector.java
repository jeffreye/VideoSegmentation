package cs576;

/**
 * Created by Jeffreye on 4/1/2017.
 */
public class MotionVector {
    public final int x, y;

    public MotionVector(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public double magnitude(){
        return Math.sqrt(x*x+y*y);
    }

    public double toAngle(){
        return Math.atan2(x, y);
    }
}
