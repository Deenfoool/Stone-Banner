package dev.stonebanner.control;

/** One press/order has a finite chase; checking reach never extends its lifetime. */
public final class HeroActionBudget {
    public static final int MAX_TICKS=200;
    public static final double MAX_TRAVEL=24,MAX_TARGET_DISTANCE=32;
    private HeroActionBudget(){}
    public static boolean permits(long elapsedTicks,double travelledSquared,double targetDistanceSquared) {
        return elapsedTicks>=0 && elapsedTicks<MAX_TICKS && Double.isFinite(travelledSquared)
                && travelledSquared>=0 && travelledSquared<=MAX_TRAVEL*MAX_TRAVEL && Double.isFinite(targetDistanceSquared)
                && targetDistanceSquared>=0 && targetDistanceSquared<=MAX_TARGET_DISTANCE*MAX_TARGET_DISTANCE;
    }
    public static float turn(float current,float target,float maxStep) {
        float delta=(target-current)%360;
        if(delta>=180)delta-=360;if(delta< -180)delta+=360;
        return current+Math.max(-maxStep,Math.min(maxStep,delta));
    }
}
