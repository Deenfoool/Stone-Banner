"use strict";
// Dependency-free browser-HUD smoke checks. Run: node docs/tests/hud-playground.test.cjs
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const html=fs.readFileSync(path.join(__dirname,"../index.html"),"utf8");
const mainMatch=html.match(/<script>\s*"use strict";([\s\S]*?)<\/script>/);
assert.ok(mainMatch,"HUD inline script not found");
const script='"use strict";'+mainMatch[1];
const L=require("../assets/hud-layout.js");
function run(w,h){
 const events=new Map(),els=new Map();
 const frameContext=new Proxy({}, {get:(o,k)=>o[k]||(o[k]=()=>{})});
 const element=id=>{
  if(!els.has(id)){
   const classes=new Set();
   els.set(id,{
    id,style:{},innerHTML:"",textContent:"",hidden:false,width:336,height:172,
    dataset:{},classList:{add(k){classes.add(k)},remove(k){classes.delete(k)},toggle(k,on){if(on)classes.add(k);else classes.delete(k)},contains:k=>classes.has(k)},
    getContext:()=>frameContext,addEventListener:(type,fn)=>events.set(id+":"+type,fn),
    setPointerCapture(){},getBoundingClientRect(){return{left:0,top:0,width:144,height:82}},
    querySelectorAll(){return[]}
   });
  }
  return els.get(id);
 };
 const doc={getElementById:element,activeElement:{tagName:"BODY"},addEventListener:(type,fn)=>events.set("document:"+type,fn)};
 let frame;
 const settings={document:doc,window:{addEventListener(){}},innerWidth:w,innerHeight:h,
   devicePixelRatio:1,performance:{now:()=>1000},
   requestAnimationFrame:fn=>{frame=fn;return 1},
   setTimeout:()=>1,clearTimeout:()=>{},
   StoneBannerLayout:L,StoneBannerArt:{paint:()=>false,ground:()=> "grass"},
   Math,Date,console};
 vm.runInNewContext(script,settings,{filename:"docs/index.html",timeout:2000});
 const click=(selector,data)=>{
  const f=events.get("document:click");
  f({target:{closest:query=>query===selector?{dataset:data}:null}});
 };
 const key=(letter,more={})=>events.get("document:keydown")({key:letter,preventDefault(){},...more});
 const pointer=(x,y,button=0,more={})=>events.get("field:pointerdown")({clientX:x,clientY:y,button,pointerId:1,preventDefault(){},...more});
 assert.ok(element("tabs").innerHTML.includes("Строительство"),"tabs missing");
 assert.ok(element("hotbar").innerHTML.includes('data-hot="8"'),"9 slots missing");
 assert.equal(element("head").style.height,"34px");
 assert.equal(element("dock").style.height,"106px");
 assert.equal(element("rail").style.width,"144px");
 assert.equal(element("info").style.height,w<400?"42px":"186px");
 assert.equal(element("panelwrap").style.transform,"none");
 assert.ok(element("minimap").style.width.endsWith("px"));
 frame(1016);
 click("[data-action]",{action:"mode"});
 assert.ok(element("mode").textContent.includes("HERO"),"Header Hero mode");
 click("[data-action]",{action:"mode"});
 assert.ok(element("mode").textContent.includes("ORDERS"),"Header Orders mode");
 key("Tab");
 assert.ok(element("mode").textContent.includes("HERO"),"Tab Hero mode");
 key("v");
 assert.ok(element("mode").textContent.includes("MOUSE"),"V profile");
 key("Tab");
 assert.ok(element("mode").textContent.includes("ORDERS"),"Return to Orders");
 // Current camera centres (19,15), Mira at tile (19,15).
 pointer(w/2+(19.5-19)*38,h/2+(15.5-15)*38);
 assert.ok(element("info").innerHTML.includes("Мира"),"NPC selection");
 pointer(w/2+70,h/2+50,2);
 assert.ok(element("info").innerHTML.includes("Приказ: движение"),"NPC direct order");
 click("[data-speed]",{speed:"0"});
 assert.ok(element("clock").innerHTML.includes("0×"),"Pause control");
 click("[data-layer]",{layer:"resources"});
 assert.ok(element("layers").innerHTML.includes("active"),"Layer toggle");
 click("[data-tab]",{tab:"build"});
 assert.ok(element("tools").innerHTML.includes("Коттедж"),"Build tab");
 click("[data-action]",{action:"settings"});
 assert.equal(element("overlay").hidden,false,"Settings modal opened");
 assert.ok(element("windowBody").innerHTML.includes("Профиль передвижения"));
 click("[data-setting]",{setting:"profile"});
 assert.ok(element("windowBody").innerHTML.includes("WASD"),"Settings toggle");
 element("close").onclick();
 assert.equal(element("overlay").hidden,true,"Settings close");
 click("[data-detail]",{detail:"inventory"});
 assert.ok(element("windowBody").innerHTML.includes("Личные предметы"),"Inspector inventory");
 element("close").onclick();
 click("[data-action]",{action:"reset"});
 assert.ok(element("mode").textContent.includes("ORDERS"),"Reset state");
 return{width:w,height:h,head:element("head").style.height,dock:element("dock").style.height,
   citizen:element("info").style.height,map:element("minimap").style.width};
}
for(const size of [[1280,720],[900,600],[689,500],[320,240]]){
 const r=run(...size);
 console.log("PASS "+r.width+"×"+r.height+" | HUD "+r.head+" | dock "+r.dock+" | citizen "+r.citizen+" | minimap "+r.map);
}
console.log("Stone & Banner HUD Playground interactions: PASS");
