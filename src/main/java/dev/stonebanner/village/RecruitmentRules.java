package dev.stonebanner.village;

/** Pure eligibility/price rules, shared by previews and authoritative recruitment. */
public final class RecruitmentRules {
    private RecruitmentRules() {}
    public static int price(boolean unemployed, int level, int reputation, boolean settler) {
        int base = (unemployed ? 8 : 16) + Math.max(0, Math.min(5, level) - 1) * 4 + (settler ? 8 : 0);
        return Math.max(4, base - Math.max(0, reputation) / 20);
    }
    public static boolean trusted(int reputation, boolean settler) { return reputation >= (settler ? 40 : 20); }
    public static boolean enoughPopulation(int population) { return population > 3; }
}
