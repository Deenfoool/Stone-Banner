package dev.stonebanner.village;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Independent villages, local trust, finite authored quests and recruitment contracts. */
public final class VillageData extends SavedData {
    private final Map<UUID, Village> villages = new LinkedHashMap<>();
    public static VillageData forLevel(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(VillageData::load, VillageData::new, "stonebanner_villages");
    }
    public Collection<Village> villages() { return List.copyOf(villages.values()); }
    public Optional<Village> get(UUID id) { return Optional.ofNullable(villages.get(id)); }
    public Optional<Village> at(BlockPos pos) {
        return villages.values().stream().filter(v -> v.center.distSqr(pos) <= 64 * 64)
                .min(Comparator.comparingDouble(v -> v.center.distSqr(pos)));
    }
    public Optional<Village> resident(UUID id) { return villages.values().stream().filter(v -> v.residents.contains(id)).findFirst(); }
    public Village discover(BlockPos center, Collection<UUID> residents) {
        var nearby = at(center).orElse(null);
        if (nearby != null) { for (UUID id : residents) addResident(nearby, id); return nearby; }
        if (villages.size() >= 128 || residents.size() < 3) return null;
        var v = new Village(UUID.randomUUID(), center, "Village " + (villages.size() + 1));
        villages.put(v.id, v);
        for (UUID id : residents) addResident(v, id);
        v.elder = v.residents.stream().min(UUID::compareTo).orElse(null);
        for (QuestType type : QuestType.values()) v.authors.put(type, v.elder);
        setDirty(); return v;
    }
    public void addResident(Village v, UUID id) {
        if (v.residents.size() < 256 && resident(id).isEmpty() && v.residents.add(id)) setDirty();
    }
    public void removeResident(UUID id) {
        resident(id).ifPresent(v -> {
            v.residents.remove(id);
            if (id.equals(v.elder)) v.elder = v.residents.stream().min(UUID::compareTo).orElse(null);
            setDirty();
        });
    }
    public void board(Village v, BlockPos pos) { v.board = pos.immutable(); setDirty(); }
    public void author(Village v, QuestType type, UUID resident) {
        if (v.residents.contains(resident)) { v.authors.put(type, resident); setDirty(); }
    }
    public int reputation(Village v, UUID player) { return v.reputation.getOrDefault(player, 0); }
    public void reputation(Village v, UUID player, int change) {
        v.reputation.put(player, Math.max(-100, Math.min(100, reputation(v, player) + change))); setDirty();
    }
    public boolean accept(Village v, UUID player, QuestType type) {
        var key = new QuestKey(player, type);
        if (v.quests.size() >= 4096 || v.quests.containsKey(key)) return false;
        v.quests.put(key, new Progress(0, false)); setDirty(); return true;
    }
    public Optional<Progress> progress(Village v, UUID player, QuestType type) { return Optional.ofNullable(v.quests.get(new QuestKey(player, type))); }
    public void kill(Village v, UUID player) {
        var p = v.quests.get(new QuestKey(player, QuestType.DEFENCE));
        if (p != null && !p.complete && p.count < QuestType.DEFENCE.amount) { p.count++; setDirty(); }
    }
    public boolean complete(Village v, UUID player, QuestType type) {
        var p = v.quests.get(new QuestKey(player, type));
        if (p == null || p.complete || type == QuestType.DEFENCE && p.count < type.amount) return false;
        p.complete = true; p.count = type.amount; reputation(v, player, type.trust); setDirty(); return true;
    }
    public void contract(Village v, UUID citizen, UUID owner) { v.contracts.put(citizen, owner); setDirty(); }
    public void contract(Village v, UUID citizen, UUID owner, CompoundTag origin) {
        contract(v,citizen,owner);v.origins.put(citizen,origin.copy());setDirty();
    }
    public Optional<Village> contractHome(UUID citizen) { return villages.values().stream().filter(v->v.contracts.containsKey(citizen)).findFirst(); }
    public void beginReturn(Village v, UUID citizen) { if(v.contracts.containsKey(citizen)){v.returning.add(citizen);setDirty();} }
    public void closeContract(UUID citizen) {
        contractHome(citizen).ifPresent(v->{v.contracts.remove(citizen);v.origins.remove(citizen);v.returning.remove(citizen);setDirty();});
    }
    public boolean finishReturn(Village v, UUID citizen, UUID resident) {
        if(!v.returning.contains(citizen)||v.residents.size()>=256||resident(resident).isPresent())return false;
        v.residents.add(resident);if(v.elder==null)v.elder=resident;
        v.contracts.remove(citizen);v.origins.remove(citizen);v.returning.remove(citizen);setDirty();return true;
    }
    public enum QuestType {
        FOOD(12, 4, 12), TIMBER(24, 6, 12), IRON(6, 8, 16), DEFENCE(3, 10, 20);
        public final int amount, emeralds, trust;
        QuestType(int amount, int emeralds, int trust) { this.amount = amount; this.emeralds = emeralds; this.trust = trust; }
    }
    private record QuestKey(UUID player, QuestType type) {}
    public static final class Progress {
        private int count; private boolean complete;
        private Progress(int count, boolean complete) { this.count = count; this.complete = complete; }
        public int count() { return count; } public boolean complete() { return complete; }
    }
    public static final class Village {
        private final UUID id; private final BlockPos center; private final String name;
        private BlockPos board; private UUID elder;
        private final Set<UUID> residents = new LinkedHashSet<>();
        private final Map<UUID, Integer> reputation = new HashMap<>();
        private final Map<QuestType, UUID> authors = new EnumMap<>(QuestType.class);
        private final Map<QuestKey, Progress> quests = new LinkedHashMap<>();
        private final Map<UUID, UUID> contracts = new HashMap<>();
        private final Map<UUID, CompoundTag> origins = new HashMap<>();
        private final Set<UUID> returning = new HashSet<>();
        private Village(UUID id, BlockPos center, String name) { this.id=id; this.center=center.immutable(); this.name=name; }
        public UUID id() { return id; } public BlockPos center() { return center; } public String name() { return name; }
        public BlockPos board() { return board; } public UUID elder() { return elder; }
        public UUID author(QuestType type) { return authors.get(type); }
        public Set<UUID> residents() { return Set.copyOf(residents); }
        public Map<UUID, UUID> contracts() { return Map.copyOf(contracts); }
        public Optional<CompoundTag> origin(UUID citizen) { return Optional.ofNullable(origins.get(citizen)).map(CompoundTag::copy); }
        public boolean returning(UUID citizen) { return returning.contains(citizen); }
    }
    @Override public CompoundTag save(CompoundTag root) {
        var list = new ListTag();
        for (var v : villages.values()) {
            var t = new CompoundTag(); t.putUUID("Id",v.id); t.putLong("Center",v.center.asLong()); t.putString("Name",v.name);
            if(v.board!=null)t.putLong("Board",v.board.asLong()); if(v.elder!=null)t.putUUID("Elder",v.elder);
            var residents=new ListTag(); for(var id:v.residents){var r=new CompoundTag();r.putUUID("Id",id);residents.add(r);}t.put("Residents",residents);
            var trust=new ListTag();v.reputation.forEach((id,value)->{var r=new CompoundTag();r.putUUID("Id",id);r.putInt("Value",value);trust.add(r);});t.put("Trust",trust);
            var quests=new ListTag();v.quests.forEach((key,p)->{var q=new CompoundTag();q.putUUID("Player",key.player);q.putString("Type",key.type.name());q.putInt("Count",p.count);q.putBoolean("Complete",p.complete);quests.add(q);});t.put("Quests",quests);
            var authors=new CompoundTag();v.authors.forEach((type,id)->authors.putUUID(type.name(),id));t.put("Authors",authors);
            var contracts=new ListTag();v.contracts.forEach((id,owner)->{var c=new CompoundTag();c.putUUID("Id",id);c.putUUID("Owner",owner);
                if(v.origins.containsKey(id))c.put("Origin",v.origins.get(id).copy());c.putBoolean("Returning",v.returning.contains(id));contracts.add(c);});t.put("Contracts",contracts);
            list.add(t);
        }
        root.put("Villages",list);return root;
    }
    public static VillageData load(CompoundTag root) {
        var data=new VillageData();var list=root.getList("Villages",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(128,list.size());i++){
            var t=list.getCompound(i);if(!t.hasUUID("Id"))continue;
            var v=new Village(t.getUUID("Id"),BlockPos.of(t.getLong("Center")),t.getString("Name"));
            if(t.contains("Board",Tag.TAG_LONG))v.board=BlockPos.of(t.getLong("Board"));if(t.hasUUID("Elder"))v.elder=t.getUUID("Elder");
            var residents=t.getList("Residents",Tag.TAG_COMPOUND);for(int j=0;j<Math.min(256,residents.size());j++){var r=residents.getCompound(j);if(r.hasUUID("Id")&&data.resident(r.getUUID("Id")).isEmpty())v.residents.add(r.getUUID("Id"));}
            if(!v.residents.contains(v.elder))v.elder=v.residents.stream().min(UUID::compareTo).orElse(null);
            var trust=t.getList("Trust",Tag.TAG_COMPOUND);for(int j=0;j<Math.min(4096,trust.size());j++){var r=trust.getCompound(j);if(r.hasUUID("Id"))v.reputation.put(r.getUUID("Id"),Math.max(-100,Math.min(100,r.getInt("Value"))));}
            var quests=t.getList("Quests",Tag.TAG_COMPOUND);for(int j=0;j<Math.min(4096,quests.size());j++){var q=quests.getCompound(j);try{var type=QuestType.valueOf(q.getString("Type"));if(q.hasUUID("Player"))v.quests.put(new QuestKey(q.getUUID("Player"),type),new Progress(Math.max(0,Math.min(type.amount,q.getInt("Count"))),q.getBoolean("Complete")));}catch(IllegalArgumentException ignored){}}
            var authors=t.getCompound("Authors");for(var type:QuestType.values())if(authors.hasUUID(type.name()))v.authors.put(type,authors.getUUID(type.name()));else if(v.elder!=null)v.authors.put(type,v.elder);
            var contracts=t.getList("Contracts",Tag.TAG_COMPOUND);for(int j=0;j<Math.min(256,contracts.size());j++){var c=contracts.getCompound(j);if(c.hasUUID("Id")&&c.hasUUID("Owner")){
                UUID citizen=c.getUUID("Id");v.contracts.put(citizen,c.getUUID("Owner"));
                if(c.contains("Origin",Tag.TAG_COMPOUND))v.origins.put(citizen,c.getCompound("Origin").copy());if(c.getBoolean("Returning"))v.returning.add(citizen);
            }}
            data.villages.put(v.id,v);
        }return data;
    }
}
