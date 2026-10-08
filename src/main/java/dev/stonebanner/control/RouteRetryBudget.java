package dev.stonebanner.control;

/** Retry allowance belongs to an order, not each rebuilt path. Progress never refills it. */
public final class RouteRetryBudget {
    private int attempts;
    private long nextAt;
    public enum Decision { WAIT, RETRY, EXHAUSTED }
    public Decision request(long tick) {
        if(tick<nextAt)return Decision.WAIT;
        if(attempts>=3)return Decision.EXHAUSTED;
        nextAt=tick+(20L<<attempts++);
        return Decision.RETRY;
    }
    public int attempts(){return attempts;}
    public void reset(){attempts=0;nextAt=0;}
}
