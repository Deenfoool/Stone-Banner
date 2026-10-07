package dev.stonebanner.client.camera;

import java.util.UUID;

/** Entity IDs may be reused after unloading; follow only the original identity in the loaded neighbourhood. */
public record CameraFollowTarget(int entityId, UUID identity) {
    public static final double MAX_DISTANCE = 64;

    public boolean matches(int id, UUID uuid, boolean alive, boolean loaded, double distanceSquared) {
        return entityId == id && identity.equals(uuid) && alive && loaded
                && Double.isFinite(distanceSquared) && distanceSquared >= 0
                && distanceSquared <= MAX_DISTANCE * MAX_DISTANCE;
    }
}
