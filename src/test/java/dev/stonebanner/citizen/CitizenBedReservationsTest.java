package dev.stonebanner.citizen;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CitizenBedReservationsTest {
    @Test void oneBedOneCitizenAndRenewal() {
        var leases=new CitizenBedReservations();var a=UUID.randomUUID();var b=UUID.randomUUID();var bed=new BlockPos(1,2,3);
        assertTrue(leases.claim(bed,a,0));assertFalse(leases.claim(bed,b,1));assertTrue(leases.claim(bed,a,50));
        assertFalse(leases.claim(bed,b,70));assertTrue(leases.claim(bed,b,110));
    }
    @Test void ForeignReleaseDoesNotStealBed() {
        var leases=new CitizenBedReservations();var a=UUID.randomUUID();var b=UUID.randomUUID();var bed=BlockPos.ZERO;
        leases.claim(bed,a,0);leases.release(bed,b);assertFalse(leases.claim(bed,b,1));leases.release(bed,a);assertTrue(leases.claim(bed,b,2));
    }
    @Test void DistinctBedsAndMutablePositionSafe() {
        var leases=new CitizenBedReservations();var a=UUID.randomUUID();var b=UUID.randomUUID();var pos=new BlockPos.MutableBlockPos(1,2,3);
        leases.claim(pos,a,0);pos.set(4,5,6);assertTrue(leases.claim(pos,b,1));assertFalse(leases.claim(new BlockPos(1,2,3),b,2));
    }
}
