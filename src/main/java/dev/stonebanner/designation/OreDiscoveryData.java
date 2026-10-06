package dev.stonebanner.designation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** Per-dimension, persistent exposed ore findings, shared permission and per-player dismissal. */
public final class OreDiscoveryData extends SavedData {
    public static final int MAX_FINDINGS = 128;
    public static final int MAX_BLOCKS = 256;
    private final LinkedHashMap<Long, Finding> findings = new LinkedHashMap<>();
    private final Map<Long, Long> positionIndex = new HashMap<>();
    private long nextId = 1;

    public static OreDiscoveryData forLevel(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(OreDiscoveryData::load, OreDiscoveryData::new,
                "stonebanner_ore_discoveries");
    }

    public Optional<Finding> at(BlockPos pos) {
        Long id = positionIndex.get(pos.asLong());
        return id == null ? Optional.empty() : get(id);
    }

    public Optional<Finding> get(long id) { return Optional.ofNullable(findings.get(id)); }
    public List<Finding> findings() { return List.copyOf(findings.values()); }

    /** Only explicitly exposed positions enter this registry; no hidden ore is persisted or sent. */
    public Finding record(String blockId, Collection<BlockPos> positions) {
        if (positions == null || positions.isEmpty() || positions.size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("Invalid exposed ore cluster");
        }
        Finding finding = positions.stream().map(this::at).flatMap(Optional::stream)
                .filter(f -> f.blockId.equals(blockId) && f.positions.size() < MAX_BLOCKS).findFirst().orElse(null);
        if (finding == null) {
            // Bounded recent history. Forgotten ore must be rediscovered, never implicitly approved.
            if (findings.size() >= MAX_FINDINGS) {
                Finding oldest = findings.remove(findings.keySet().iterator().next());
                oldest.positions.forEach(pos -> positionIndex.remove(pos.asLong(), oldest.id));
            }
            finding = new Finding(nextId++, blockId);
            findings.put(finding.id, finding);
        }
        boolean added = false;
        for (BlockPos pos : positions) {
            if (finding.positions.size() >= MAX_BLOCKS) break;
            Long owner = positionIndex.get(pos.asLong());
            if (owner != null && owner != finding.id) continue;
            if (finding.positions.add(pos.immutable())) {
                positionIndex.put(pos.asLong(), finding.id);
                added = true;
            }
        }
        if (finding.positions.isEmpty()) {
            findings.remove(finding.id);
            setDirty();
            return null;
        }
        if (added) {
            // Approval covers the surveyed part only. Newly exposed blocks need a fresh decision.
            finding.approved = false;
            finding.hiddenFor.clear();
            setDirty();
        }
        return finding;
    }

    public boolean approve(long id) {
        Finding finding = findings.get(id);
        if (finding == null) return false;
        if (!finding.approved) { finding.approved = true; setDirty(); }
        return true;
    }

    public boolean hide(long id, UUID player) {
        Finding finding = findings.get(id);
        if (finding == null) return false;
        if (finding.hiddenFor.add(player)) setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        root.putLong("NextId", nextId);
        ListTag entries = new ListTag();
        for (Finding finding : findings.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Id", finding.id);
            entry.putString("Block", finding.blockId);
            entry.putBoolean("Approved", finding.approved);
            entry.putLongArray("Positions", finding.positions.stream().mapToLong(BlockPos::asLong).toArray());
            ListTag hidden = new ListTag();
            for (UUID player : finding.hiddenFor) {
                CompoundTag tag = new CompoundTag(); tag.putUUID("Player", player); hidden.add(tag);
            }
            entry.put("Hidden", hidden); entries.add(entry);
        }
        root.put("Findings", entries);
        return root;
    }

    static OreDiscoveryData load(CompoundTag root) {
        OreDiscoveryData data = new OreDiscoveryData();
        ListTag entries = root.getList("Findings", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size() && data.findings.size() < MAX_FINDINGS; i++) {
            CompoundTag entry = entries.getCompound(i);
            long id = entry.getLong("Id");
            if (id <= 0 || id == Long.MAX_VALUE || data.findings.containsKey(id)) continue;
            String blockId = entry.getString("Block");
            if (net.minecraft.resources.ResourceLocation.tryParse(blockId) == null) continue;
            Finding finding = new Finding(id, blockId);
            finding.approved = entry.getBoolean("Approved");
            for (long packed : entry.getLongArray("Positions")) {
                if (finding.positions.size() >= MAX_BLOCKS) break;
                if (!data.positionIndex.containsKey(packed)) {
                    finding.positions.add(BlockPos.of(packed));
                    data.positionIndex.put(packed, id);
                }
            }
            ListTag hidden = entry.getList("Hidden", Tag.TAG_COMPOUND);
            for (int j = 0; j < hidden.size(); j++) {
                if (hidden.getCompound(j).hasUUID("Player")) finding.hiddenFor.add(hidden.getCompound(j).getUUID("Player"));
            }
            if (finding.positions.isEmpty()) continue;
            data.findings.put(id, finding); data.nextId = Math.max(data.nextId, id + 1);
        }
        // Old Reported entries included hidden vein blocks. Deliberately rediscover only exposed ore.
        return data;
    }

    public static final class Finding {
        private final long id;
        private final String blockId;
        private final LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
        private final Set<UUID> hiddenFor = new HashSet<>();
        private boolean approved;
        private Finding(long id, String blockId) { this.id = id; this.blockId = blockId; }
        public long id() { return id; }
        public String blockId() { return blockId; }
        public List<BlockPos> positions() { return List.copyOf(positions); }
        public boolean approved() { return approved; }
        public boolean hiddenFor(UUID player) { return hiddenFor.contains(player); }
    }
}
