package dev.stonebanner.geology;

/** Prototype suitability index for actual soil, not a promise of vanilla crop speed. */
public final class FertilityRules {
    private FertilityRules(){}
    public static int score(boolean soil,boolean farmland,int moisture,float rainfall,float temperature,boolean water) {
        if(!soil)return 0;
        double climate=Math.max(0,Math.min(1,rainfall))*35;
        double warmth=Math.max(0,1-Math.abs(temperature-.7))*20;
        double hydration=farmland?Math.max(0,Math.min(7,moisture))*4:water?28:5;
        return (int)Math.max(1,Math.min(100,15+climate+warmth+hydration));
    }
}
