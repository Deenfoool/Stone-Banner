package dev.stonebanner.control;

/** Only accepted ground clicks participate; UI, entities, queued clicks and mode changes reset it. */
public final class GroundClickTracker {
    private long lastAt = -1;
    private double lastX, lastY;
    public boolean click(long now, double x, double y, int windowMs) {
        boolean twice=lastAt>=0 && now>=lastAt && now-lastAt<=windowMs
                && Math.hypot(x-lastX,y-lastY)<=6;
        lastAt=twice?-1:now;lastX=x;lastY=y;
        return twice;
    }
    public void reset(){lastAt=-1;}
}
