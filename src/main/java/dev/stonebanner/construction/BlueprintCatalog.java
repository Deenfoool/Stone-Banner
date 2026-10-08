package dev.stonebanner.construction;

import dev.stonebanner.StoneAndBanner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Server-only discovery. Clients receive validated geometry, never paths or executable block-entity NBT. */
@Mod.EventBusSubscriber(modid=StoneAndBanner.MOD_ID)
public final class BlueprintCatalog {
    public static final int LIMIT=128;
    public record Entry(String id,String title,String problem,BuildingBlueprint blueprint){}
    private static final Map<MinecraftServer,BlueprintCatalog> SERVERS=new WeakHashMap<>();
    private final Map<String,Entry> entries=new LinkedHashMap<>();
    private BlueprintCatalog(){var cottage=BuildingBlueprint.cottage();entries.put(cottage.id(),new Entry(cottage.id(),cottage.title(),"",cottage));load();}
    public static BlueprintCatalog forLevel(ServerLevel level){return SERVERS.computeIfAbsent(level.getServer(),ignored->new BlueprintCatalog());}
    public List<Entry> entries(){return List.copyOf(entries.values());}
    public BuildingBlueprint get(String id){var entry=entries.get(id);return entry==null?null:entry.blueprint();}
    public static BuildingBlueprint forPlan(ServerLevel level,ConstructionData.Plan plan){
        var blueprint=forLevel(level).get(plan.blueprintId);
        if(blueprint==null||!blueprint.fingerprint().equals(plan.fingerprint)){plan.status="blueprint_changed";return null;}return blueprint;
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){SERVERS.remove(event.getServer());}
    private void load(){
        Path folder=FMLPaths.CONFIGDIR.get().resolve("stonebanner/blueprints");
        try{Files.createDirectories(folder);}catch(IOException e){StoneAndBanner.LOGGER.warn("Cannot create blueprint directory: {}",e.getMessage());}
        discover(folder,"local",false);
        for(String mod:new String[]{"minecolonies","stylecolonies"}){
            var file=ModList.get().getModFileById(mod);
            if(file!=null)discover(file.getFile().findResource("blueprints",mod),mod,true);
        }
    }
    private void discover(Path root,String namespace,boolean housesOnly){
        if(!Files.isDirectory(root))return;
        try(var paths=Files.walk(root,12)){
            var candidates=paths.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&p.getFileName().toString().endsWith(".blueprint"))
                    .filter(p->!housesOnly||p.getFileName().toString().matches("(?i).*(residence|citizen|house|home).*\\.blueprint"))
                    .limit(2048).sorted().toList();
            for(var path:candidates){
                if(entries.size()>=LIMIT)break;
                // Symlinks are not followed by the walk; never escape an operator's configured folder.
                String relative=root.relativize(path).toString().replace('\\','/');String id=namespace+":"+relative;
                if(id.length()>256)continue;
                String title=relative.substring(0,relative.length()-10);if(title.length()>160)title=title.substring(title.length()-160);
                try(var stream=Files.newInputStream(path)){
                    var bytes=stream.readNBytes(MineColoniesBlueprintReader.MAX_BYTES+1);
                    var blueprint=MineColoniesBlueprintReader.read(id,title,bytes);entries.put(id,new Entry(id,title,"",blueprint));
                }catch(IOException|RuntimeException e){
                    String reason=e.getMessage()==null?"Invalid blueprint":e.getMessage();if(reason.length()>256)reason=reason.substring(0,256);
                    entries.put(id,new Entry(id,title,reason,null));StoneAndBanner.LOGGER.warn("Skipped blueprint {}: {}",id,reason);
                }
            }
        }catch(IOException e){StoneAndBanner.LOGGER.warn("Cannot scan blueprint pack {}: {}",namespace,e.getMessage());}
    }
}
