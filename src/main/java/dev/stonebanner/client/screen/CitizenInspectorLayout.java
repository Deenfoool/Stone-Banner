package dev.stonebanner.client.screen;

/** Shared dimensions for rendering and clicking, including every narrow-screen work column. */
public record CitizenInspectorLayout(int x,int y,int width,int height,int rows,int workColumns){
    public static CitizenInspectorLayout of(int screenWidth,int screenHeight){
        int w=Math.max(160,Math.min(680,screenWidth-24)),h=Math.max(150,Math.min(360,screenHeight-24));
        return new CitizenInspectorLayout((screenWidth-w)/2,(screenHeight-h)/2,w,h,Math.max(1,(h-100)/22),Math.max(1,(w-124)/38));
    }
    public int clampPage(int page,int total,int size){return Math.max(0,Math.min(page,Math.max(0,(total-1)/size)));}
    public int dataRows(boolean work){return work?Math.max(1,rows-1):rows;}
}
