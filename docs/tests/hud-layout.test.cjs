"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const L=require("../assets/hud-layout.js");
const source=fs.readFileSync(path.join(__dirname,"../../src/main/java/dev/stonebanner/client/hud/StoneBannerHudLayout.java"),"utf8");
for(const [key,value] of Object.entries(L.C)){
  const match=source.match(new RegExp("public static final int "+key+"\\s*=\\s*(\\d+)\\s*;"));
  assert.ok(match,"Java layout constant missing: "+key);
  assert.equal(value,+match[1],"Java/browser drift: "+key);
}
const R=(x,y,width,height)=>({x,y,width,height});
assert.deepEqual(L.topBar(1280),R(6,6,1268,34));
assert.deepEqual(L.bottomDock(1280,720,true),R(393,608,560,106));
assert.deepEqual(L.bottomDock(1280,720,false),R(393,680,560,34));
assert.deepEqual(L.citizenCard(1280,720,true),R(6,528,210,186));
assert.deepEqual(L.rightRail(1280,720),R(1130,558,144,156));
assert.deepEqual(L.miniMap(1280,720),R(1135,642,134,67));
assert.deepEqual(L.bottomToggle(1280,720,true),R(649,595,48,15));
assert.deepEqual(L.alerts(1280,3),R(1054,48,220,60));
assert.equal(L.sideBySide(690),true);
assert.equal(L.sideBySide(689),false);
assert.equal(L.canExpandCitizen(320,240),false);
const compact=L.layout(320,240,{selected:1});
assert.equal(compact.effectiveFold,true);
assert.ok(!L.overlap(compact.citizen,compact.rail),"Tiny-screen corners overlap");
assert.ok(!L.overlap(compact.citizen,compact.dock),"Tiny-screen inspector overlaps dock");
assert.ok(!L.overlap(compact.rail,compact.dock),"Tiny-screen right rail overlaps dock");
assert.equal(L.shortRail(L.rightRail(320,240)),true);
const short=L.miniMap(320,240);
assert.equal(short.width,53);
for(const [w,h] of [[1920,1080],[1280,720],[900,600],[690,500],[689,500],[600,360],[390,540],[320,240]]){
  for(const expanded of [true,false]){
    const d=L.layout(w,h,{expanded,selected:1});
    assert.ok(d.dock.width>0 && d.rail.width>0);
    assert.ok(d.dock.x>=0 && d.dock.x+d.dock.width<=w+2);
    assert.ok(d.citizen.x>=0 && d.citizen.x+d.citizen.width<=w+2);
    assert.ok(d.rail.x>=0 && d.rail.x+d.rail.width<=w+2);
    assert.ok(!L.overlap(d.dock,d.rail),"Rail overlaps dock at "+w+"×"+h);
    assert.ok(!L.overlap(d.dock,d.citizen),"Citizen overlaps dock at "+w+"×"+h);
    assert.ok(!L.overlap(d.citizen,d.rail),"Corner panels overlap at "+w+"×"+h);
    assert.ok(L.hotbarSlot(w,h,expanded,8).width>0);
  }
}
const elements={};
const stub=id=>elements[id]??=(Object.assign({style:{},classList:{toggle(){}},querySelectorAll:()=>[]}, {id}));
const d=L.apply({getElementById:stub},1280,720,{expanded:true,selected:1});
assert.equal(elements.head.style.height,"34px");
assert.equal(elements.panelwrap.style.width,"560px");
assert.equal(elements.rail.style.width,"144px");
assert.equal(elements.info.style.height,"186px");
assert.equal(elements.minimap.style.width,"134px");
assert.equal(d.effectiveFold,false);
console.log("HUD browser ↔ Java layout: constants, geometry, breakpoints, hit areas — PASS");
