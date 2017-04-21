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

    public double getDistance(double x,double y){
        return Math.sqrt(Math.pow(x-this.x,2)+Math.pow(y-this.y,2));
    }
    public double getDistance(MotionVector anotherVector){
        return Math.sqrt(Math.pow(x-anotherVector.x,2)+Math.pow(y-anotherVector.y,2));
    }

    public double magnitude(){
        return Math.sqrt(x*x+y*y);
    }

    public double toAngle(){
        return Math.atan2(x, y);
    }
}
