package dev.stonebanner.control;

/** Scalar acceleration/braking leaves direction in world space and does not steer through walls. */
public final class MovementResponse {
    private float magnitude;
    public float update(float target) {
        target=Math.max(0,Math.min(1,target));
        float step=target>magnitude?.25f:.35f;
        magnitude+=Math.max(-step,Math.min(step,target-magnitude));
        return magnitude;
    }
    public void reset(){magnitude=0;}
    public static float arrivalScale(double distance) {
        return (float)Math.max(.15,Math.min(1,(distance-.1)/1.5));
    }
}
