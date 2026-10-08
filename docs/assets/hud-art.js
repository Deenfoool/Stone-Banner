/* Stone & Banner HUD Playground art adaptor. Original self-hosted sprites. */
(function(){
"use strict";
const sheet=new Image();let loaded=false;sheet.onload=()=>loaded=true;sheet.onerror=()=>loaded=false;sheet.src="./assets/terrain-atlas.svg";
const tiles=["grass","meadow","darkgrass","path","dirt","water","sand","stone","flowers","forest","farmland","snow","tree","pine","hall","storage","farm","cottage","rubble","boulder","builder","farmer","miner","logger","guard","geologist","hero"];
const atlas=new Map(tiles.map((n,i)=>[n,i]));
const portraitNames={"🔨":"builder","🌾":"farmer","⛏":"miner","🪓":"logger","🛡":"guard","🔎":"geologist"};
const iconPairs=[
["🪵","wood"],["🪨","stone"],["⚙","iron"],["🔻","redstone"],["🌾","wheat"],
["🍞","bread"],["🛡","shield"],["⚔","combat"],["🗡","sword"],["🏹","bow"],
["🧭","compass"],["🗺","map"],["🏗","build"],["⚒","craft"],["🪓","chop"],
["⛏","pickaxe"],["🔎","geology"],["♟","people"],["⌂","house"],["⚑","flag"],
["☀","sun"],["☷","orders"],["▥","zone"],["▦","build"],["▣","borders"],
["◆","ore"],["♣","fertility"],["✦","research"],["✂","clear"],["▤","excavate"],
["⌑","tunnel"],["✕","cancel"],["◈","map"],["✚","health"],["❤","health"],
["☾","fatigue"],["⏱","clock"],["⚠","warning"],["📖","overview"],
["📦","inventory"],["🔨","build"],["⚙️","settings"],["⚙","settings"],
["Ⅱ","pause"],["▶▶","fast"],["▶","play"],["»»","faster"],
["❓","help"],["♜","camp"],["×","cancel"],["←","back"],["⚒️","craft"]
];
const icons=iconPairs.sort((a,b)=>b[0].length-a[0].length);
function icon(kind){
 const ns="http://www.w3.org/2000/svg";
 const svg=document.createElementNS(ns,"svg");svg.setAttribute("viewBox","0 0 24 24");svg.setAttribute("class","sb-ico");svg.setAttribute("aria-hidden","true");
 const use=document.createElementNS(ns,"use");use.setAttribute("href","./assets/hud-icons.svg#i-"+kind);svg.appendChild(use);return svg;
}
function paint(target,name,x,y,w,h){
 if(!loaded||!target)return false;
 let k=atlas.get(name);
 if(k===undefined)k=atlas.get(portraitNames[name]);
 if(k===undefined)return false;
 target.drawImage(sheet,k%6*32,Math.floor(k/6)*32,32,32,x,y,w,h);return true;
}
function noise(x,y){let v=Math.sin(x*127.1+y*311.7)*43758.5453;return v-Math.floor(v);}
function ground(x,y){
 if(x<0||y<0||x>=40||y>=30)return "darkgrass";
 if(y>=25&&(x+Math.sin(x/3)*1.4)>7)return "water";
 if(x>26&&y<8)return noise(x,y)>.45?"stone":"sand";
 if((x===18||y===13)&&x>=8&&x<=30&&y>=6&&y<=23)return "path";
 const z=noise(x,y);return z<.10?"flowers":z<.27?"meadow":z>.84?"darkgrass":"grass";
}
function portrait(e){
 if(!e.classList||!e.classList.contains("face"))return false;
 const role=portraitNames[e.textContent.trim()];
 if(!role)return false;
 let idx=atlas.get(role);
 e.textContent="";const item=document.createElement("span");item.className="sb-portrait";item.style.backgroundPosition=`-${idx%6*48}px -${Math.floor(idx/6)*48}px`;e.appendChild(item);return true;
}
function replaceGlyphs(text){
 if(!text||!text.parentElement)return;
 const parent=text.parentElement;
 if(/^(SCRIPT|STYLE|SVG|USE|TITLE|TEXTAREA|OPTION)$/.test(parent.tagName)||parent.closest("svg"))return;
 const source=text.nodeValue;if(!source||!icons.some(a=>source.includes(a[0])))return;
 const nodes=[];let i=0,start=0;
 while(i<source.length){
 let hit=icons.find(pair=>source.startsWith(pair[0],i));
 if(hit){if(start<i)nodes.push(document.createTextNode(source.slice(start,i)));nodes.push(icon(hit[1]));i+=hit[0].length;start=i;}
 else i++;
 }
 if(start<source.length)nodes.push(document.createTextNode(source.slice(start)));
 if(nodes.length){const f=document.createDocumentFragment();nodes.forEach(n=>f.appendChild(n));text.replaceWith(f);}
}
function decorate(root){
 if(!root||!root.nodeType)return;
 if(root.nodeType===1&&root.matches?.(".face")&&portrait(root))return;
 if(root.nodeType===3){replaceGlyphs(root);return;}
 if(root.nodeType!==1&&root.nodeType!==9&&root.nodeType!==11)return;
 root.querySelectorAll?.(".face").forEach(portrait);
 const walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);let pending=[],current;
 while((current=walker.nextNode()))pending.push(current);
 pending.forEach(replaceGlyphs);
}
const observe=()=>{decorate(document.body);const observer=new MutationObserver(changes=>{
 const seen=new Set();
 changes.forEach(c=>{
   if(c.type==="characterData"){seen.add(c.target);}
   c.addedNodes.forEach(n=>seen.add(n));
 });
 seen.forEach(decorate);
});observer.observe(document.body,{subtree:true,childList:true,characterData:true});};
if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",observe,{once:true});else observe();
window.StoneBannerArt={paint,ground,icon,ready:()=>loaded};
})();