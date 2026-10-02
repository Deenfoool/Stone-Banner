package dev.stonebanner.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Persistent home-community anchor and soft travel boundary for one Citizen. */
public final class CitizenHome {
    public static final int DEFAULT_LOCAL_RADIUS_CHUNKS = 2;

    private static final String TAG_COMMUNITY_ID = "CommunityId";
    private static final String TAG_HAS_HOME = "HasHome";
    private static final String TAG_HOME_POS = "HomePos";
    private static final String TAG_RADIUS_CHUNKS = "RadiusChunks";

    private String communityId = "";
    private BlockPos homePos = BlockPos.ZERO;
    private int radiusChunks = DEFAULT_LOCAL_RADIUS_CHUNKS;
    private boolean hasHome;

    public boolean hasHome() {
        return hasHome;
    }

    public String communityId() {
        return communityId;
    }

    public BlockPos homePos() {
        return homePos;
    }

    public int radiusChunks() {
        return radiusChunks;
    }

    public void assign(String communityId, BlockPos homePos, int radiusChunks) {
        this.communityId = communityId == null ? "" : communityId;
        this.homePos = (homePos == null ? BlockPos.ZERO : homePos).immutable();
        this.radiusChunks = Math.max(0, radiusChunks);
        this.hasHome = true;
    }

    public void clear() {
        communityId = "";
        homePos = BlockPos.ZERO;
        radiusChunks = DEFAULT_LOCAL_RADIUS_CHUNKS;
        hasHome = false;
    }

    public boolean contains(BlockPos position) {
        if (!hasHome || position == null) {
            return true;
        }
        int homeChunkX = homePos.getX() >> 4;
        int homeChunkZ = homePos.getZ() >> 4;
        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        return Math.max(Math.abs(chunkX - homeChunkX), Math.abs(chunkZ - homeChunkZ)) <= radiusChunks;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_HAS_HOME, hasHome);
        tag.putString(TAG_COMMUNITY_ID, communityId);
        tag.putLong(TAG_HOME_POS, homePos.asLong());
        tag.putInt(TAG_RADIUS_CHUNKS, radiusChunks);
        return tag;
    }

    public void load(CompoundTag tag) {
        hasHome = tag.getBoolean(TAG_HAS_HOME);
        communityId = tag.getString(TAG_COMMUNITY_ID);
        if (tag.contains(TAG_HOME_POS, Tag.TAG_LONG)) {
            homePos = BlockPos.of(tag.getLong(TAG_HOME_POS));
        }
        if (tag.contains(TAG_RADIUS_CHUNKS, Tag.TAG_INT)) {
            radiusChunks = Math.max(0, tag.getInt(TAG_RADIUS_CHUNKS));
        }
    }
}
