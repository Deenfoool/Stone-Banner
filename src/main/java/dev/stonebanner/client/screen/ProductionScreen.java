package dev.stonebanner.client.screen;

import dev.stonebanner.network.packet.ProductionSnapshotPacket;
import dev.stonebanner.production.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import java.util.Locale;

/** Minecraft widgets/font; server commands validate ownership, station, recipes and distance. */
public final class ProductionScreen extends Screen {
    private ProductionSnapshotPacket state;
    private final Screen parent;
    private int tab,page,rows,x,w,crop,mode,ticks;
    private long selected;
    private String from="",to="",recipe="minecraft:bread",amount="1";
    private EditBox fromBox,toBox,recipeBox,amountBox;
    private boolean pending;
    private int editMode;
    private String editAmount="1";
    private EditBox billAmountBox;
    private ProductionScreen(ProductionSnapshotPacket state,Screen parent){super(label("title"));this.state=state;this.parent=parent;}
    private static Component label(String key){return Component.translatable("production.stonebanner.ui."+key);}
    public static void open(ProductionSnapshotPacket p){
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(p.dimension()))return;
        if(mc.screen instanceof ProductionScreen screen){screen.capture();screen.state=p;screen.pending=false;screen.rebuildWidgets();return;}
        if(!p.opening())return;
        dev.stonebanner.client.control.PlayerCommandController.stop();var s=new ProductionScreen(p,mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen?null:mc.screen);
        var pos=mc.hitResult instanceof BlockHitResult hit?hit.getBlockPos():mc.player.blockPosition().below();s.from=pos.getX()+" "+pos.getY()+" "+pos.getZ();s.to=s.from;mc.setScreen(s);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void tick(){
        if(minecraft.level==null||!minecraft.level.dimension().location().equals(state.dimension())){onClose();return;}
        if(pending&&++ticks>=40){capture();pending=false;rebuildWidgets();}
    }
    private void capture(){if(fromBox!=null){from=fromBox.getValue();to=toBox.getValue();recipe=recipeBox.getValue();amount=amountBox.getValue();}if(billAmountBox!=null)editAmount=billAmountBox.getValue();}
    private void send(String suffix){
        if(minecraft.getConnection()==null)return;capture();pending=true;ticks=0;minecraft.getConnection().sendCommand("sbproduction "+suffix);
        // Ordered on the same connection: refresh follows the mutation, and never reopens a closed screen.
        if(!suffix.equals("refresh"))minecraft.getConnection().sendCommand("sbproduction refresh");rebuildWidgets();
    }
    private Button button(Component text,int bx,int by,int bw,Runnable action,boolean active){var b=addRenderableWidget(Button.builder(text,ignored->action.run()).bounds(bx,by,bw,20).build());b.active=active;return b;}
    private EditBox edit(int y,String value,String hint){var b=new EditBox(font,x+8,y,w-16,18,label(hint));b.setMaxLength(96);b.setValue(value);addRenderableWidget(b);return b;}
    private int count(){return tab==0?state.fields().size():state.bills().size();}
    private boolean paused(){return tab==0?state.fields().stream().anyMatch(f->f.id()==selected&&f.paused()):state.bills().stream().anyMatch(b->b.id()==selected&&b.paused());}
    private ProductionSnapshotPacket.Bill selectedBill(){return state.bills().stream().filter(b->b.id()==selected).findFirst().orElse(null);}
    private boolean canMove(int direction){
        var bill=selectedBill();if(bill==null)return false;
        var queue=state.bills().stream().filter(b->b.station().equals(bill.station())).toList();
        int target=queue.indexOf(bill)+direction;return target>=0&&target<queue.size();
    }
    private void editBill(){
        var bill=selectedBill();if(bill==null)return;
        editMode=bill.mode()==ProductionData.Mode.MAKE?0:1;editAmount=Integer.toString(bill.amount());tab=3;rebuildWidgets();
    }
    private void applyBill(){
        capture();if(!editAmount.matches("[0-9]{1,4}"))return;
        int target=Integer.parseInt(editAmount);if(target<1||target>4096)return;
        String command="update "+selected+" "+(editMode==0?"make":"maintain")+" "+target;
        tab=1;send(command);
    }
    @Override protected void init(){
        w=Math.min(520,width-16);x=(width-w)/2;rows=Math.max(1,(height-148)/32);page=Math.max(0,Math.min(page,Math.max(0,(count()-1)/rows)));
        fromBox=toBox=recipeBox=amountBox=billAmountBox=null;
        if(tab==3&&selectedBill()==null){tab=1;selected=0;}
        if(tab==0&&state.fields().stream().noneMatch(f->f.id()==selected)||tab==1&&state.bills().stream().noneMatch(b->b.id()==selected))selected=0;
        for(int i=0;i<3;i++){final int next=i;button(label(new String[]{"fields","bills","new"}[i]),x+8+i*(w-16)/3,30,(w-16)/3-2,()->{capture();tab=next;page=0;selected=0;rebuildWidgets();},tab!=i);}
        button(label("refresh"),x+w-154,height-28,80,()->send("refresh"),!pending);button(Component.translatable("gui.done"),x+w-70,height-28,62,this::onClose,true);
        if(tab==3){
            billAmountBox=edit(100,editAmount,"amount");billAmountBox.setMaxLength(4);
            button(label(editMode==0?"make":"maintain"),x+8,126,w-16,()->{capture();editMode=1-editMode;rebuildWidgets();},!pending);
            button(label("apply"),x+8,154,(w-18)/2,this::applyBill,!pending);
            button(Component.translatable("gui.back"),x+w/2,154,w/2-8,()->{capture();tab=1;rebuildWidgets();},!pending);
            return;
        }
        if(tab==2){
            fromBox=edit(62,from,"position");toBox=edit(88,to,"end");recipeBox=edit(114,recipe,"recipe");amountBox=edit(140,amount,"amount");
            fromBox.setHint(label("position"));toBox.setHint(label("end"));recipeBox.setHint(label("recipe"));amountBox.setHint(label("amount"));
            int y=164;
            button(Component.translatable("production.stonebanner.ui.crop."+FarmCrop.values()[crop].name().toLowerCase(Locale.ROOT)),x+8,y,(w-18)/2,()->{capture();crop=(crop+1)%4;rebuildWidgets();},!pending);
            button(label(mode==0?"make":"maintain"),x+w/2,y,w/2-8,()->{capture();mode=1-mode;rebuildWidgets();},!pending);
            button(label("create_field"),x+8,y+24,(w-18)/2,this::createField,!pending);
            button(label("create_bill"),x+w/2,y+24,w/2-8,this::createBill,!pending);
            return;
        }
        for(int i=0;i<rows&&page*rows+i<count();i++){
            int index=page*rows+i;long id=tab==0?state.fields().get(index).id():state.bills().get(index).id();
            button(Component.literal((selected==id?"> ":"")+"#"+id),x+8,60+i*32,64,()->{selected=id;rebuildWidgets();},!pending);
        }
        int y=height-78;button(label(paused()?"resume":"pause"),x+8,y,(w-20)/3,()->send((paused()?"resume ":"pause ")+selected),selected>0&&!pending);
        button(label("remove"),x+8+(w-16)/3,y,(w-20)/3,()->send("remove "+selected),selected>0&&!pending);
        button(label(tab==0?"fertilize":"edit"),x+8+2*(w-16)/3,y,(w-20)/3,()->{if(tab==0)send("fertilize "+selected);else editBill();},selected>0&&!pending);
        button(Component.literal("<"),x+8,height-52,30,()->{page--;rebuildWidgets();},page>0&&!pending);button(Component.literal(">"),x+42,height-52,30,()->{page++;rebuildWidgets();},(page+1)*rows<count()&&!pending);
        if(tab==1){
            int bw=(w-100)/2;
            button(label("up"),x+84,height-52,bw,()->send("up "+selected),!pending&&canMove(-1));
            button(label("down"),x+88+bw,height-52,bw,()->send("down "+selected),!pending&&canMove(1));
        }
    }
    private static boolean position(String text){return text.trim().matches("-?\\d{1,8} +-?\\d{1,8} +-?\\d{1,8}");}
    private void createField(){capture();if(!position(from)||!position(to))return;send("farm "+FarmCrop.values()[crop].name().toLowerCase(Locale.ROOT)+" "+from.trim()+" "+to.trim());}
    private void createBill(){capture();if(!position(from)||!recipe.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||!amount.matches("[0-9]{1,4}"))return;send("bill "+from.trim()+" "+recipe+" "+(mode==0?"make":"maintain")+" "+amount);}
    private void text(GuiGraphics g,Component value,int y){g.drawString(font,font.plainSubstrByWidth(value.getString(),w-96),x+80,y,0xE6DCC9,false);}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        renderBackground(g);g.fill(x,6,x+w,height-6,0xEC1C2429);g.drawString(font,title,x+8,14,0xF4E5BE,false);
        if(tab==3){
            var bill=selectedBill();
            if(bill!=null){text(g,Component.literal("#"+bill.id()+" "+bill.recipe()),60);text(g,Component.literal(bill.station().toShortString()+" · "+bill.made()),74);}
            g.drawString(font,label("amount"),x+8,88,0xE6DCC9,false);
            g.drawWordWrap(font,label("edit_hint"),x+8,184,w-16,0xB8B8B8);
        }else if(tab!=2){
            if(count()==0)g.drawString(font,label("empty"),x+8,66,0xE6DCC9,false);
            for(int i=0;i<rows&&page*rows+i<count();i++){
                int y=62+i*32;if(tab==0){var f=state.fields().get(page*rows+i);text(g,Component.translatable("production.stonebanner.ui.crop."+f.crop().name().toLowerCase(Locale.ROOT)).append(" "+f.min().toShortString()+" → "+f.max().toShortString()),y);text(g,Component.translatable("production.stonebanner."+(f.paused()?"paused":"ready")).append(f.fertilize()?" +":""),y+12);}
                else{var b=state.bills().get(page*rows+i);text(g,Component.literal(b.recipe()+" "+b.made()+"/"+b.amount()+" ").append(label(b.mode()==ProductionData.Mode.MAKE?"make":"maintain")),y);text(g,Component.translatable("production.stonebanner."+(b.paused()?"paused":b.status())).append(" @ "+b.station().toShortString()),y+12);}
            }
        }else{
            g.drawString(font,label("position"),x+8,52,0xE6DCC9,false);g.drawString(font,label("end"),x+8,78,0xE6DCC9,false);
            g.drawString(font,label("recipe"),x+8,104,0xE6DCC9,false);g.drawString(font,label("amount"),x+8,130,0xE6DCC9,false);
        }
        super.render(g,mx,my,delta);
    }
}
