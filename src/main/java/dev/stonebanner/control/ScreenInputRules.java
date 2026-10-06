package dev.stonebanner.control;
/** Screen-space math shared by edge scrolling and rectangle selection. */
public final class ScreenInputRules {
    private ScreenInputRules() {}
    public static double[] edgePan(double x,double y,int width,int height,double margin) {
        if(width<=0||height<=0||x<0||y<0||x>width||y>height)return new double[]{0,0};
        double left=x<margin?1:x>width-margin?-1:0;
        double forward=y<margin?1:y>height-margin?-1:0;
        double length=Math.hypot(left,forward);
        return length>1?new double[]{left/length,forward/length}:new double[]{left,forward};
    }
    public static boolean inRectangle(double x,double y,double x1,double y1,double x2,double y2) {
        return x>=Math.min(x1,x2)&&x<=Math.max(x1,x2)&&y>=Math.min(y1,y2)&&y<=Math.max(y1,y2);
    }
}
