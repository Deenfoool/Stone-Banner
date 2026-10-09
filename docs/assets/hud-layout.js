/* Stone & Banner HUD Playground geometry — mirrors StoneBannerHudLayout.java (alpha.49).
 * All dimensions are GUI-independent CSS px. Gameplay is still a standalone simulation.
 * Run: node docs/tests/hud-layout.test.cjs
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  if (root) root.StoneBannerLayout = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";
  const C = Object.freeze({
    SCREEN_MARGIN: 6, TOP_HEIGHT: 34,
    CITIZEN_WIDTH: 210, CITIZEN_EXPANDED_HEIGHT: 186,
    CITIZEN_COLLAPSED_HEIGHT: 42, CITIZEN_TAB_HEIGHT: 22,
    RIGHT_RAIL_WIDTH: 144, RIGHT_RAIL_HEIGHT: 156, TIME_BUTTON_HEIGHT: 20,
    BOTTOM_DOCK_MAX_WIDTH: 560, BOTTOM_DOCK_MIN_WIDTH: 300,
    BOTTOM_DOCK_EXPANDED_HEIGHT: 106, BOTTOM_DOCK_COLLAPSED_HEIGHT: 34,
    BOTTOM_TAB_HEIGHT: 24, BOTTOM_TOGGLE_WIDTH: 48,
    BOTTOM_TOGGLE_HEIGHT: 15, HOTBAR_HEIGHT: 28, HOTBAR_SLOTS: 9,
    ALERT_WIDTH: 220
  });
  const rect = (x,y,width,height) => ({x,y,width,height});
  const overlap = (a,b) => a.x < b.x+b.width && a.x+a.width > b.x
    && a.y < b.y+b.height && a.y+a.height > b.y;
  const sideBySide = w => w >= C.CITIZEN_WIDTH+C.RIGHT_RAIL_WIDTH+C.BOTTOM_DOCK_MIN_WIDTH+36;
  const canExpandCitizen = (w,h) => h >= C.TOP_HEIGHT+24+C.CITIZEN_EXPANDED_HEIGHT
    +(sideBySide(w) ? 0 : C.BOTTOM_DOCK_EXPANDED_HEIGHT+18);
  function bottomDock(w,h,expanded=true) {
    const sides=sideBySide(w), left=sides?C.CITIZEN_WIDTH+C.SCREEN_MARGIN+12:C.SCREEN_MARGIN;
    const right=sides?C.RIGHT_RAIL_WIDTH+C.SCREEN_MARGIN+12:C.SCREEN_MARGIN;
    const available=Math.max(1,w-left-right), desired=Math.min(C.BOTTOM_DOCK_MAX_WIDTH,available);
    const width=available>=C.BOTTOM_DOCK_MIN_WIDTH?Math.max(C.BOTTOM_DOCK_MIN_WIDTH,desired)
      :Math.max(1,w-C.SCREEN_MARGIN*2);
    const height=expanded?C.BOTTOM_DOCK_EXPANDED_HEIGHT:C.BOTTOM_DOCK_COLLAPSED_HEIGHT;
    const x=sides && available>=C.BOTTOM_DOCK_MIN_WIDTH
      ?left+Math.max(0,Math.floor((available-width)/2))
      :Math.max(C.SCREEN_MARGIN,Math.floor((w-width)/2));
    return rect(x,Math.max(C.TOP_HEIGHT+14,h-height-C.SCREEN_MARGIN),width,height);
  }
  function cornerBottom(w,h) {
    return sideBySide(w)?h-C.SCREEN_MARGIN:bottomDock(w,h,true).y-18;
  }
  function citizenCard(w,h,expanded=true) {
    const width=Math.min(C.CITIZEN_WIDTH,Math.max(1,w-(sideBySide(w)?C.SCREEN_MARGIN*2:
      C.RIGHT_RAIL_WIDTH+C.SCREEN_MARGIN*3)));
    const height=expanded?C.CITIZEN_EXPANDED_HEIGHT:C.CITIZEN_COLLAPSED_HEIGHT;
    return rect(C.SCREEN_MARGIN,Math.max(C.TOP_HEIGHT+16,cornerBottom(w,h)-height),width,height);
  }
  function groupCard(w,h) {
    const c=citizenCard(w,h,false);
    return rect(c.x,Math.max(C.TOP_HEIGHT+16,cornerBottom(w,h)-58),c.width,58);
  }
  function rightRail(w,h) {
    const width=Math.min(C.RIGHT_RAIL_WIDTH,Math.max(1,w-C.SCREEN_MARGIN*2));
    const bottom=cornerBottom(w,h);
    const height=Math.min(C.RIGHT_RAIL_HEIGHT,Math.max(1,bottom-C.TOP_HEIGHT-12));
    return rect(Math.max(C.SCREEN_MARGIN,w-width-C.SCREEN_MARGIN),
      Math.max(C.TOP_HEIGHT+12,bottom-height),width,height);
  }
  const shortRail=r=>r.height<112;
  function timeButton(w,h,index) {
    if(index<0||index>=4)throw Error("Invalid speed index");
    const r=rightRail(w,h),width=Math.floor(((shortRail(r)?r.width-66:r.width-10)-9)/4);
    return rect(r.x+5+index*(width+3),r.y+5,width,C.TIME_BUTTON_HEIGHT);
  }
  function layerButton(w,h,index) {
    if(index<0||index>=3)throw Error("Invalid layer index");
    const r=rightRail(w,h),width=Math.floor(((shortRail(r)?r.width-66:r.width-10)-6)/3);
    return rect(r.x+5+index*(width+3),r.y+28,width,20);
  }
  function miniMap(w,h) {
    const r=rightRail(w,h);
    return shortRail(r)
      ?rect(r.x+r.width-58,r.y+5,53,Math.min(53,Math.max(1,r.height-10)))
      :rect(r.x+5,r.y+84,r.width-10,Math.max(1,r.height-89));
  }
  function clockPanel(w,h) {
    const r=rightRail(w,h);
    return rect(r.x+5,r.y+51,r.width-10,30);
  }
  function bottomToggle(w,h,expanded=true) {
    const d=bottomDock(w,h,expanded);
    return rect(d.x+Math.floor((d.width-C.BOTTOM_TOGGLE_WIDTH)/2),
      Math.max(0,d.y-C.BOTTOM_TOGGLE_HEIGHT+2),
      C.BOTTOM_TOGGLE_WIDTH,C.BOTTOM_TOGGLE_HEIGHT);
  }
  function hotbarArea(w,h,expanded=true) {
    const d=bottomDock(w,h,expanded);
    return rect(d.x+5,d.y+d.height-C.HOTBAR_HEIGHT-3,Math.max(1,d.width-10),C.HOTBAR_HEIGHT);
  }
  function hotbarSlot(w,h,expanded,index) {
    if(index<0||index>=C.HOTBAR_SLOTS)throw Error("Invalid hotbar index");
    const a=hotbarArea(w,h,expanded),gap=2;
    const slot=Math.max(1,Math.min(28,Math.floor((a.width-gap*(C.HOTBAR_SLOTS-1))/C.HOTBAR_SLOTS)));
    const used=slot*C.HOTBAR_SLOTS+gap*(C.HOTBAR_SLOTS-1);
    return rect(a.x+Math.max(0,Math.floor((a.width-used)/2))+index*(slot+gap),a.y,slot,a.height);
  }
  function tabRect(w,h,index,count) {
    if(index<0||index>=count||count<=0)throw Error("Invalid tab index");
    const d=bottomDock(w,h,true),gap=3,avail=d.width-10-gap*(count-1);
    const cell=Math.max(1,Math.floor(avail/count));
    const used=cell*count+gap*(count-1),x=d.x+Math.floor((d.width-used)/2);
    return rect(x+index*(cell+gap),d.y+5,cell,C.BOTTOM_TAB_HEIGHT);
  }
  function toolRect(w,h,index,count) {
    if(index<0||index>=count||count<=0)throw Error("Invalid tool index");
    const d=bottomDock(w,h,true),hot=hotbarArea(w,h,true),gap=3;
    const y=d.y+C.BOTTOM_TAB_HEIGHT+10;
    const available=d.width-10-gap*(count-1),cell=Math.max(1,Math.floor(available/count));
    const used=cell*count+gap*(count-1);
    return rect(d.x+Math.floor((d.width-used)/2)+index*(cell+gap),
      y,Math.max(1,cell),Math.max(1,hot.y-y-4));
  }
  function alerts(w,lines) {
    const count=Math.max(1,Math.min(3,lines));
    const width=Math.min(C.ALERT_WIDTH,Math.max(1,w-2*C.SCREEN_MARGIN));
    return rect(Math.max(C.SCREEN_MARGIN,w-width-C.SCREEN_MARGIN),
      C.TOP_HEIGHT+C.SCREEN_MARGIN+8,width,6+count*18);
  }
  const topBar=w=>rect(C.SCREEN_MARGIN,C.SCREEN_MARGIN,Math.max(0,w-2*C.SCREEN_MARGIN),C.TOP_HEIGHT);
  function layout(w,h,{expanded=true,selected=0,folded=false}={}) {
    w=Math.max(1,Math.floor(w));h=Math.max(1,Math.floor(h));
    const effectiveFold=folded||!canExpandCitizen(w,h);
    return { top:topBar(w), dock:bottomDock(w,h,expanded),
      toggle:bottomToggle(w,h,expanded),
      citizen:selected>1?groupCard(w,h):citizenCard(w,h,selected===1&&!effectiveFold),
      rail:rightRail(w,h), clock:clockPanel(w,h),minimap:miniMap(w,h),
      alert:alerts(w,3), effectiveFold,sideBySide:sideBySide(w) };
  }
  function place(el,r) {
    if(!el)return;
    Object.assign(el.style,{position:"absolute",left:r.x+"px",top:r.y+"px",
      width:r.width+"px",height:r.height+"px",right:"auto",bottom:"auto",transform:"none"});
  }
  function apply(doc,w,h,opts={}) {
    if(!doc || !doc.getElementById) return null;
    const d=layout(w,h,opts);
    place(doc.getElementById("head"),d.top);
    place(doc.getElementById("info"),d.citizen);
    place(doc.getElementById("rail"),d.rail);
    const wrap=doc.getElementById("panelwrap");
    const toggle=doc.getElementById("panelToggle");
    if(wrap) {
      place(wrap,rect(d.dock.x,d.toggle.y,d.dock.width,d.dock.height+d.dock.y-d.toggle.y));
      wrap.classList.toggle("closed",!opts.expanded);
    }
    if(toggle) {
      toggle.style.width=C.BOTTOM_TOGGLE_WIDTH+"px";
      toggle.style.height=C.BOTTOM_TOGGLE_HEIGHT+"px";
      toggle.style.margin="0 auto -2px"; // Java toggle overlaps the dock frame by 2 px
    }
    const panel=doc.getElementById("dock");
    if(panel){panel.style.height=d.dock.height+"px";}
    const tabs=doc.getElementById("tabs");
    if(tabs)tabs.style.height=C.BOTTOM_TAB_HEIGHT+"px";
    const hot=doc.getElementById("hotbar");
    if(hot) {
      hot.style.height=C.HOTBAR_HEIGHT+"px";
      for(const [i,b] of Array.from(hot.querySelectorAll("[data-hot]")).entries()) {
        if(i<9) {
          const r=hotbarSlot(w,h,opts.expanded!==false,i);
          b.style.flex="0 0 "+Math.max(1,r.width)+"px";
          b.style.height=r.height+"px";
          b.style.width=r.width+"px";
        }
      }
    }
    const right=doc.getElementById("rail");
    const speed=doc.getElementById("speeds"),layers=doc.getElementById("layers");
    if(speed){speed.style.position="absolute";speed.style.left="5px";speed.style.top="5px";
      speed.style.width=(shortRail(d.rail)?d.rail.width-66:d.rail.width-10)+"px";}
    if(layers){layers.style.position="absolute";layers.style.left="5px";layers.style.top="28px";
      layers.style.width=(shortRail(d.rail)?d.rail.width-66:d.rail.width-10)+"px";layers.style.marginTop="0";}
    if(right)right.style.overflow="hidden";
    const clock=doc.getElementById("clock");
    if(clock){clock.style.position="absolute";clock.style.left="5px";clock.style.top="51px";
      clock.style.width=d.clock.width+"px";clock.style.height=d.clock.height+"px";clock.style.margin="0";
      clock.style.display=shortRail(d.rail)?"none":"";}
    const map=doc.getElementById("minimap");
    if(map){map.style.position="absolute";map.style.left=(d.minimap.x-d.rail.x)+"px";
      map.style.top=(d.minimap.y-d.rail.y)+"px";map.style.width=d.minimap.width+"px";
      map.style.height=d.minimap.height+"px";map.style.margin="0";}
    const alert=doc.getElementById("alerts");
    if(alert){alert.style.top=d.alert.y+"px";alert.style.width=d.alert.width+"px";}
    const actions=doc.getElementById("actions");
    if(actions){actions.style.top=Math.max(d.top.y+d.top.height+10,d.dock.y-43)+"px";actions.style.bottom="auto";}
    return d;
  }
  return Object.freeze({C,topBar,sideBySide,canExpandCitizen,bottomDock,bottomToggle,citizenCard,
    groupCard,rightRail,shortRail,timeButton,layerButton,clockPanel,miniMap,hotbarArea,hotbarSlot,
    tabRect,toolRect,alerts,layout,overlap,apply});
});
