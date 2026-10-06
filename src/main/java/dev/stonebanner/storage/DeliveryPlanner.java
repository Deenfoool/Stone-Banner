package dev.stonebanner.storage;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Bounded nearest-first search; one rejected destination never hides the alternatives. */
public final class DeliveryPlanner {
    private DeliveryPlanner() {}

    public static <T, R> Optional<R> choose(List<T> candidates, int limit,
                                          Function<T, Optional<R>> route) {
        for (int i = 0; i < Math.min(Math.max(0, limit), candidates.size()); i++) {
            Optional<R> result = route.apply(candidates.get(i));
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }
}
