package dev.stonebanner.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Communities survive their physical banner. Placement alone never creates or activates a community. */
public final class SettlementData extends SavedData {
    public static final int MAX_COMMUNITIES = 128;
    public static final int MAX_RESIDENTS = 256;
    private final LinkedHashMap<UUID, Community> communities = new LinkedHashMap<>();
    public static SettlementData forLevel(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SettlementData::load, SettlementData::new,
                "stonebanner_settlements");
    }
    public List<Community> communities() { return List.copyOf(communities.values()); }
    public Optional<Community> ownedBy(UUID owner) {
        return communities.values().stream().filter(c -> c.owner.equals(owner)).findFirst();
    }
    public Optional<Community> atBanner(BlockPos pos) {
        return communities.values().stream().filter(c -> c.banner.equals(pos)).findFirst();
    }
    public Optional<Community> residentHome(UUID resident) {
        return communities.values().stream().filter(c -> c.residents.contains(resident)).findFirst();
    }
    public static String validName(String text) {
        if (text == null) return "";
        String name = text.strip();
        if (name.length() < 2 || name.length() > 32 || name.codePoints().anyMatch(cp -> Character.isISOControl(cp) || cp == 0x00A7)) return "";
        return name;
    }
    public Result create(UUID owner, String text, BlockPos banner) {
        String name = validName(text);
        if (name.isEmpty()) return Result.INVALID_NAME;
        if (ownedBy(owner).isPresent()) return Result.ALREADY_OWNED;
        if (communities.size() >= MAX_COMMUNITIES) return Result.LIMIT;
        int cx = banner.getX() >> 4, cz = banner.getZ() >> 4;
        if (communities.values().stream().anyMatch(c -> c.overlaps(cx, cz))) return Result.OVERLAP;
        var community = new Community(UUID.randomUUID(), owner, name, banner, cx, cz);
        communities.put(community.id, community); setDirty(); return Result.CREATED;
    }
    public Result relocate(UUID owner, BlockPos banner) {
        var community = ownedBy(owner).orElse(null);
        if (community == null) return Result.NOT_FOUND;
        if (!community.contains(banner)) return Result.OUTSIDE_TERRITORY;
        var linked = atBanner(banner).orElse(null);
        if (linked != null && linked != community) return Result.OVERLAP;
        community.banner = banner.immutable(); community.bannerActive = true; setDirty(); return Result.MOVED;
    }
    public void bannerRemoved(BlockPos pos) {
        atBanner(pos).ifPresent(c -> {
            if (c.bannerActive) { c.bannerActive = false; setDirty(); }
        });
    }
    public Result addResident(UUID owner, UUID resident) {
        var community = ownedBy(owner).orElse(null);
        if (community == null) return Result.NOT_FOUND;
        var home = residentHome(resident).orElse(null);
        if (home != null) return home == community ? Result.ALREADY_RESIDENT : Result.FOREIGN_RESIDENT;
        if (community.residents.size() >= MAX_RESIDENTS) return Result.LIMIT;
        community.residents.add(resident); setDirty(); return Result.JOINED;
    }
    public void removeResident(UUID resident) {
        communities.values().forEach(c -> { if (c.residents.remove(resident)) setDirty(); });
    }
    public Result promote(UUID owner, Readiness readiness) {
        var community = ownedBy(owner).orElse(null);
        if (community == null) return Result.NOT_FOUND;
        if (community.settlement) return Result.ALREADY_SETTLEMENT;
        if (!community.bannerActive || !readiness.ready()) return Result.NOT_READY;
        community.settlement = true; setDirty(); return Result.PROMOTED;
    }
    /** Minimum prototype provision: one resident, beds for everyone and four food items per person. */
    public record Readiness(int residents, int beds, int food, int stores, int assigned) {
        public Readiness {
            if (residents < 0 || assigned < residents || assigned > MAX_RESIDENTS || beds < 0 || food < 0 || stores < 0)
                throw new IllegalArgumentException("Invalid community provisions");
        }
        public Readiness(int residents, int beds, int food, int stores) { this(residents, beds, food, stores, residents); }
        public int people() { return assigned + 1; }
        public int requiredFood() { return people() * 4; }
        public boolean ready() { return residents >= 1 && beds >= people() && stores >= 1 && food >= requiredFood(); }
    }
    @Override public CompoundTag save(CompoundTag root) {
        ListTag entries = new ListTag();
        for (var c : communities.values()) {
            var tag = new CompoundTag(); tag.putUUID("Id", c.id); tag.putUUID("Owner", c.owner);
            tag.putString("Name", c.name); tag.putLong("Banner", c.banner.asLong());
            tag.putInt("CenterX", c.centerX); tag.putInt("CenterZ", c.centerZ);
            tag.putBoolean("BannerActive", c.bannerActive); tag.putBoolean("Settlement", c.settlement);
            ListTag residents = new ListTag();
            for (var id : c.residents) { var entry = new CompoundTag(); entry.putUUID("Id", id); residents.add(entry); }
            tag.put("Residents", residents); entries.add(tag);
        }
        root.put("Communities", entries); return root;
    }
    static SettlementData load(CompoundTag root) {
        var data = new SettlementData(); var entries = root.getList("Communities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size() && data.communities.size() < MAX_COMMUNITIES; i++) {
            var tag = entries.getCompound(i); String name = validName(tag.getString("Name"));
            if (!tag.hasUUID("Id") || !tag.hasUUID("Owner") || name.isEmpty()) continue;
            UUID id = tag.getUUID("Id"), owner = tag.getUUID("Owner");
            if (data.communities.containsKey(id) || data.ownedBy(owner).isPresent()) continue;
            var c = new Community(id, owner, name, BlockPos.of(tag.getLong("Banner")), tag.getInt("CenterX"), tag.getInt("CenterZ"));
            if (!c.contains(c.banner) || data.communities.values().stream().anyMatch(existing -> existing.overlaps(c.centerX, c.centerZ))) continue;
            c.bannerActive = tag.getBoolean("BannerActive"); c.settlement = tag.getBoolean("Settlement");
            ListTag residents = tag.getList("Residents", Tag.TAG_COMPOUND);
            for (int j = 0; j < residents.size() && c.residents.size() < MAX_RESIDENTS; j++) {
                if (residents.getCompound(j).hasUUID("Id")) {
                    UUID resident = residents.getCompound(j).getUUID("Id");
                    if (data.residentHome(resident).isEmpty()) c.residents.add(resident);
                }
            }
            data.communities.put(id, c);
        }
        return data;
    }
    public enum Result {
        CREATED, MOVED, JOINED, PROMOTED, INVALID_NAME, ALREADY_OWNED, LIMIT, OVERLAP,
        NOT_FOUND, OUTSIDE_TERRITORY, ALREADY_RESIDENT, FOREIGN_RESIDENT, ALREADY_SETTLEMENT, NOT_READY
    }
    public static final class Community {
        private final UUID id, owner;
        private final String name;
        private BlockPos banner;
        private final int centerX, centerZ;
        private final LinkedHashSet<UUID> residents = new LinkedHashSet<>();
        private boolean bannerActive = true, settlement;
        private Community(UUID id, UUID owner, String name, BlockPos banner, int cx, int cz) {
            this.id = id; this.owner = owner; this.name = name; this.banner = banner.immutable(); centerX = cx; centerZ = cz;
        }
        public UUID id() { return id; } public UUID owner() { return owner; } public String name() { return name; }
        public BlockPos banner() { return banner; } public int centerX() { return centerX; } public int centerZ() { return centerZ; }
        public boolean bannerActive() { return bannerActive; } public boolean settlement() { return settlement; }
        public Set<UUID> residents() { return Set.copyOf(residents); }
        public boolean contains(BlockPos pos) {
            return Math.abs((long)(pos.getX() >> 4) - centerX) <= 1 && Math.abs((long)(pos.getZ() >> 4) - centerZ) <= 1;
        }
        private boolean overlaps(int cx, int cz) { return Math.abs((long)cx - centerX) <= 2 && Math.abs((long)cz - centerZ) <= 2; }
    }
}
