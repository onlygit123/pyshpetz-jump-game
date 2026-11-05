// Генератор рисованных спрайтов (Chromium Canvas). Всё в «юнитах» игры (экран 480x800), scale = пикселей на юнит.
(function(){
const INK='#2f2821';
function rng(seed){let s=seed>>>0;return()=>{s=(Math.imul(s,1664525)+1013904223)>>>0;return s/4294967296}}
function mk(w,h,scale){const c=document.createElement('canvas');c.width=Math.round(w*scale);c.height=Math.round(h*scale);const x=c.getContext('2d');x.scale(scale,scale);x.lineJoin='round';x.lineCap='round';return {c,x,w,h,scale}}
// ---- геометрия ----
function catmull(pts,closed,seg){const out=[];const n=pts.length;const g=i=>closed?pts[(i+n)%n]:pts[Math.max(0,Math.min(n-1,i))];
 const last=closed?n:n-1;for(let i=0;i<last;i++){const p0=g(i-1),p1=g(i),p2=g(i+1),p3=g(i+2);for(let k=0;k<seg;k++){const t=k/seg,t2=t*t,t3=t2*t;
 out.push([0.5*((2*p1[0])+(-p0[0]+p2[0])*t+(2*p0[0]-5*p1[0]+4*p2[0]-p3[0])*t2+(-p0[0]+3*p1[0]-3*p2[0]+p3[0])*t3),
           0.5*((2*p1[1])+(-p0[1]+p2[1])*t+(2*p0[1]-5*p1[1]+4*p2[1]-p3[1])*t2+(-p0[1]+3*p1[1]-3*p2[1]+p3[1])*t3)])}}
 if(!closed)out.push(pts[n-1]);return out}
function resample(poly,closed,step){const pts=closed?poly.concat([poly[0]]):poly;const out=[];let acc=0;out.push(pts[0]);
 for(let i=1;i<pts.length;i++){let a=pts[i-1],b=pts[i];let dx=b[0]-a[0],dy=b[1]-a[1];let d=Math.hypot(dx,dy);let pos=step-acc;
  while(pos<=d){out.push([a[0]+dx*pos/d,a[1]+dy*pos/d]);pos+=step}acc=d-(pos-step)}
 return out}
function wobble(poly,closed,amp,seed,freq=0.02){const R=rng(seed);const ph=[R()*6.28,R()*6.28,R()*6.28];const st=window.STEP||2.2;freq=freq*2.2/st;const pts=resample(poly,closed,st);const out=[];let s=0;
 for(let i=0;i<pts.length;i++){const a=pts[Math.max(0,i-1)],b=pts[Math.min(pts.length-1,i+1)];let tx=b[0]-a[0],ty=b[1]-a[1];const l=Math.hypot(tx,ty)||1;tx/=l;ty/=l;
  if(i>0)s+=Math.hypot(pts[i][0]-pts[i-1][0],pts[i][1]-pts[i-1][1]);
  const n=(Math.sin(s*freq*6.28+ph[0])*0.6+Math.sin(s*freq*14.1+ph[1])*0.28+Math.sin(s*freq*31+ph[2])*0.07)*amp;
  out.push([pts[i][0]-ty*n,pts[i][1]+tx*n])}
 return out}
function rrPts(x,y,w,h,r){const p=[];const arc=(cx,cy,a0)=>{for(let k=0;k<=8;k++){const a=a0+k*Math.PI/16;p.push([cx+Math.cos(a)*r,cy+Math.sin(a)*r])}};
 arc(x+w-r,y+r,-Math.PI/2);arc(x+w-r,y+h-r,0);arc(x+r,y+h-r,Math.PI/2);arc(x+r,y+r,Math.PI);return p}
function ellPts(cx,cy,rx,ry,n=48){const p=[];for(let i=0;i<n;i++){const a=i/n*6.2832;p.push([cx+Math.cos(a)*rx,cy+Math.sin(a)*ry])}return p}
function path(x,pts,closed){x.beginPath();pts.forEach((p,i)=>i?x.lineTo(p[0],p[1]):x.moveTo(p[0],p[1]));if(closed)x.closePath()}
function smooth(pts,closed=true){return catmull(pts,closed,10)}
// ---- рисование ----
function fillShape(x,pts,color){path(x,pts,true);x.fillStyle=color;x.fill()}
function inkLine(x,pts,closed,width,seed,color=INK){
 const R=rng(seed);const p=closed?pts.concat([pts[0],pts[1]]):pts;
 x.strokeStyle=color;
 for(let pass=0;pass<2;pass++){const off=pass?[R()*1.2-.6,R()*1.2-.6]:[0,0];x.globalAlpha=pass?0.3:1;
  for(let i=1;i<p.length;i++){const w=width*(pass?0.5:1)*(0.9+0.16*Math.sin(i*0.05+seed+pass*2));x.lineWidth=w;x.beginPath();x.moveTo(p[i-1][0]+off[0],p[i-1][1]+off[1]);x.lineTo(p[i][0]+off[0],p[i][1]+off[1]);x.stroke()}}
 x.globalAlpha=1}
function hatch(x,pts,color,alpha,spacing,angle,seed,lw=1.1,bbox){const R=rng(seed);x.save();path(x,pts,true);x.clip();x.strokeStyle=color;x.globalAlpha=alpha;x.lineWidth=lw;
 let minx=1e9,miny=1e9,maxx=-1e9,maxy=-1e9;pts.forEach(p=>{minx=Math.min(minx,p[0]);maxx=Math.max(maxx,p[0]);miny=Math.min(miny,p[1]);maxy=Math.max(maxy,p[1])});
 const cx=(minx+maxx)/2,cy=(miny+maxy)/2,L=Math.hypot(maxx-minx,maxy-miny);x.translate(cx,cy);x.rotate(angle);
 for(let o=-L/2;o<L/2;o+=spacing){const j=R()*2-1;x.beginPath();x.moveTo(-L/2+R()*10,o+j);x.lineTo(L/2-R()*10,o+j+R()*2-1);x.stroke()}
 x.restore()}
function grain(x,w,h,seed,n,alpha,color='#8a6a3a'){const R=rng(seed);x.save();x.fillStyle=color;for(let i=0;i<n;i++){x.globalAlpha=alpha*(0.3+R()*0.7);const s=0.3+R()*0.9;x.fillRect(R()*w,R()*h,s,s)}x.restore()}
function shadowOf(x,pts,dx,dy,alpha){x.save();x.translate(dx,dy);path(x,pts,true);x.fillStyle=`rgba(60,40,20,${alpha})`;x.fill();x.restore()}
// «Бумажная» плашка/кнопка: тень, заливка, штриховка, блики, неровный контур
function blob(w,h,scale,o){window.STEP=4.5;const {c,x}=mk(w,h,scale);const m=o.margin||6;
 const base=rrPts(m,m,w-2*m-4,h-2*m-4,o.r);const pts=wobble(base,true,o.amp,o.seed);
 shadowOf(x,pts,3.2,4.2,0.22);
 fillShape(x,pts,o.fill);
 // карандашная штриховка внизу и справа
 x.save();path(x,pts,true);x.clip();
 const g=x.createLinearGradient(0,0,w*0.35,h);g.addColorStop(0,'rgba(255,255,255,0.55)');g.addColorStop(0.45,'rgba(255,255,255,0)');g.addColorStop(1,'rgba(120,80,30,0.16)');x.fillStyle=g;x.fillRect(0,0,w,h);x.restore();
 hatch(x,pts.filter((p,i)=>true),o.hatch||'#9a6b2f',0.17,4.2,-0.7,o.seed+3,0.9);
 // светлая полоса, чтобы hatch не покрывал верх
 x.save();path(x,pts,true);x.clip();const g2=x.createLinearGradient(0,0,0,h);g2.addColorStop(0,o.fill);g2.addColorStop(0.5,o.fill+'00');x.fillStyle=g2;x.globalAlpha=0.95;x.fillRect(0,0,w,h*0.55);x.restore();
 // белые «чёрточки-блики» как на платформах
 x.strokeStyle='rgba(255,255,255,0.85)';x.lineWidth=1.8;const R=rng(o.seed+9);
 for(let i=0;i<(o.hl||4);i++){const hx=m+o.r*0.7+R()*(w-2*m-o.r*1.8),hy=m+4.5+R()*2;x.beginPath();x.moveTo(hx,hy);x.lineTo(hx+7+R()*6,hy-1.5);x.stroke()}
 inkLine(x,pts,true,o.lw,o.seed+1);
 grain(x,w,h,o.seed+5,w*h/18,0.10);
 window.STEP=0;
 return c}
// ---------- Пингвин (оригинальный персонаж) ----------
function penguin(pose,seed){
 const W=100,H=90,S=4;window.STEP=7;const {c,x}=mk(W,H,1);c.width=W*S;c.height=H*S;x.setTransform(1,0,0,1,0,0);x.lineJoin='round';x.lineCap='round';x.translate(40,0); // координаты 400x360
 const dark='#2d3645',dark2='#232b38',belly='#fff7e2',orange='#f7931e',cap='#2fa89f',cap2='#7fd6c8',pom='#fff4d6';
 // тень-контур всей фигуры будет сформирован обводками
 x.save();
 const sq=pose.squash||1;x.translate(170,338);x.scale(1+(1-sq)*0.7,sq);x.translate(-170,-338);
 // крылья (за телом)
 function wing(side,ang){x.save();const px=side<0?92:248,py=205;x.translate(px,py);x.rotate(side<0?ang:-ang);
  const base=[[0,-6],[side*-22,6],[side*-34,44],[side*-30,92],[side*-14,112],[side*-2,84],[side*6,30]];
  const pts=wobble(smooth(base,true),true,1.2,31+(side<0?0:5));fillShape(x,pts,dark2);
  hatch(x,pts,'#000',0.14,5,0.7,4);inkLine(x,pts,true,6.5,33);x.restore()}
 const wa=(pose.wing||0)*Math.PI/180;
 wing(1,wa); // дальнее крыло (справа)
 // лапы
 function foot(cx,cy){const pts=wobble(smooth([[cx-32,cy-6],[cx-36,cy+12],[cx-22,cy+18],[cx-12,cy+26],[cx,cy+18],[cx+12,cy+26],[cx+22,cy+18],[cx+36,cy+12],[cx+30,cy-6]],true),true,0.8,cx);fillShape(x,pts,orange);
  hatch(x,pts,'#a04a00',0.2,5,-0.7,cx);inkLine(x,pts,true,5.2,cx+2)}
 const fy=pose.feet||0;foot(128,326+fy);foot(212,326+fy);
 // тело
 const body=wobble(smooth([[170,68],[224,84],[252,140],[262,220],[246,292],[196,334],[134,334],[88,296],[70,222],[82,142],[116,86]]),true,1.4,11);
 fillShape(x,body,dark);
 hatch(x,body,'#000',0.16,5.5,0.75,2);
 // блик на спине
 x.save();path(x,body,true);x.clip();x.strokeStyle='rgba(180,200,230,0.5)';x.lineWidth=5;x.lineCap='round';x.beginPath();x.moveTo(236,150);x.quadraticCurveTo(250,185,247,232);x.stroke();x.restore();
 // пузо
 const bel=wobble(smooth([[112,196],[158,176],[206,196],[232,248],[218,304],[172,328],[124,316],[94,268],[96,222]]),true,1.2,17);
 fillShape(x,bel,belly);
 hatch(x,bel,'#c9a35a',0.18,6,-0.7,5);
 x.save();path(x,bel,true);x.clip();x.strokeStyle='rgba(255,255,255,0.9)';x.lineWidth=4;x.beginPath();x.moveTo(112,236);x.quadraticCurveTo(108,262,122,290);x.stroke();x.restore();
 inkLine(x,bel,true,4.2,19);
 inkLine(x,body,true,7.5,13);
 // щёчки
 x.fillStyle='rgba(255,120,110,0.45)';x.beginPath();x.ellipse(150,182,15,9,-0.1,0,6.3);x.fill();
 // глаза
 function eye(cx,cy,r,sd){const e=wobble(ellPts(cx,cy,r,r*1.08),true,0.8,sd);fillShape(x,e,'#ffffff');inkLine(x,e,true,4.4,sd+1);
  const open=pose.blink?0.35:1;x.fillStyle=INK;x.beginPath();x.ellipse(cx-r*0.28,cy+r*0.08,r*0.46,r*0.52*open,0,0,6.3);x.fill();
  x.fillStyle='#fff';x.beginPath();x.arc(cx-r*0.42,cy-r*0.2,r*0.16,0,6.3);x.fill()}
 eye(104,148,26,41);eye(168,144,22,47);
 // брови
 x.strokeStyle=INK;x.lineWidth=5;x.lineCap='round';x.beginPath();x.moveTo(78,116);x.quadraticCurveTo(100,106,126,118);x.stroke();x.beginPath();x.moveTo(150,114);x.quadraticCurveTo(168,108,190,114);x.stroke();
 // клюв
 const beak=wobble(smooth([[102,164],[66,166],[34,180],[62,198],[108,196]]),true,0.9,23);fillShape(x,beak,orange);
 hatch(x,beak,'#a04a00',0.2,5,-0.7,23);inkLine(x,beak,true,4.6,24);
 x.strokeStyle=INK;x.lineWidth=3.2;x.beginPath();x.moveTo(104,180);x.quadraticCurveTo(76,186,48,181);x.stroke();
 x.strokeStyle='rgba(255,255,255,0.8)';x.lineWidth=2.6;x.beginPath();x.moveTo(78,170);x.lineTo(96,168);x.stroke();
 // шапка с помпоном (оригинальная деталь персонажа)
 const hat=wobble(smooth([[84,126],[92,92],[124,62],[172,52],[214,66],[240,100],[246,128],[168,138]],true),true,1.1,29);
 fillShape(x,hat,cap);hatch(x,hat,'#0b5d56',0.22,5,0.75,6);
 x.save();path(x,hat,true);x.clip();x.strokeStyle=cap2;x.lineWidth=7;x.beginPath();x.moveTo(110,96);x.quadraticCurveTo(140,70,184,68);x.stroke();x.restore();
 // резинка шапки
 const band=wobble(smooth([[80,128],[84,106],[170,110],[246,106],[252,130],[168,142]],true),true,1,37);fillShape(x,band,cap2);
 x.save();path(x,band,true);x.clip();x.strokeStyle='rgba(20,100,95,0.45)';x.lineWidth=2.2;for(let i=0;i<9;i++){x.beginPath();x.moveTo(92+i*19,100);x.lineTo(92+i*19,146);x.stroke()}x.restore();
 inkLine(x,band,true,4.6,38);inkLine(x,hat,true,6,30);
 const pp=wobble(ellPts(170,48,22,21),true,1.4,43);fillShape(x,pp,pom);hatch(x,pp,'#c9a35a',0.3,4,0.7,3);inkLine(x,pp,true,5,44);
 x.restore();
 // ближнее крыло поверх тела
 x.save();x.translate(0,0);x.restore();
 wing(-1,wa);
 window.STEP=0;
 return c}
// ---------- Горы ----------
function ridge(W,H,S,peaks,o){const {c,x}=mk(W,H,S);
 const top=[];peaks.forEach(p=>top.push(p));
 const line=wobble(catmull(top,false,3),false,o.amp,o.seed,0.03);
 const poly=[[0,H+10]].concat(line,[[W,H+10]]);
 fillShape(x,poly,o.fill);
 // тень на правых склонах (штриховка) — для каждого пика отдельный клин
 for(let i=1;i<peaks.length-1;i++){const p=peaks[i];if(p[1]<peaks[i-1][1]&&p[1]<peaks[i+1][1]){
   const dx=(peaks[i+1][0]-p[0]);const wedge=[[p[0],p[1]],[p[0]+dx*0.95,peaks[i+1][1]+6],[p[0]+dx*0.55,peaks[i+1][1]+50],[p[0]+10,p[1]+(peaks[i+1][1]-p[1])*0.8]];
   const wp=wobble(wedge,true,1.2,o.seed+i);hatch(x,wp,o.shade,0.55,4.5,-0.95,o.seed+i,1.2);
   // белая снежная шапка
   if(o.snow&&p[1]<o.snowY){const sd=[[p[0],p[1]-1],[p[0]+(peaks[i+1][0]-p[0])*0.34,p[1]+(peaks[i+1][1]-p[1])*0.34],[p[0]+(peaks[i+1][0]-p[0])*0.2,p[1]+(peaks[i+1][1]-p[1])*0.28+9],[p[0]+3,p[1]+34],[p[0]-(p[0]-peaks[i-1][0])*0.18,p[1]+(peaks[i-1][1]-p[1])*0.18+14],[p[0]-(p[0]-peaks[i-1][0])*0.34,p[1]+(peaks[i-1][1]-p[1])*0.34]];
     const sp=wobble(sd,true,0.9,o.seed+20+i);fillShape(x,sp,'#fbfdf6');hatch(x,sp,'#8fb3c7',0.35,4,-0.9,o.seed+i,1);inkLine(x,sp,true,2.4,o.seed+30+i,o.ink)}}}
 // подсветка склонов и контур
 x.save();path(x,poly,true);x.clip();x.globalAlpha=0.28;const g=x.createLinearGradient(0,0,0,H);g.addColorStop(0,'#fff');g.addColorStop(0.6,'rgba(255,255,255,0)');x.fillStyle=g;x.fillRect(0,0,W,H);x.restore();
 inkLine(x,line,false,o.lw,o.seed+99,o.ink);
 grain(x,W,H,o.seed+3,W*H/12,0.07,'#254');
 return c}
// ---------- Облака ----------
function cloud(variant,seed){const W=220,H=120,S=2.5;const {c,x}=mk(W,H,S);
 const sets=[[[60,78,34],[100,56,44],[146,62,38],[176,84,28],[118,84,34],[78,92,22]],[[48,84,28],[86,62,38],[132,48,44],[172,70,34],[110,86,36],[150,92,24]],[[70,76,32],[112,58,40],[150,74,30],[96,92,26],[132,94,26]]][variant%3];
 const base=[];const circles=sets.map((s,i)=>wobble(ellPts(s[0],s[1],s[2],s[2]*0.92),true,1.3,seed+i));
 x.save();
 // тень снизу
 circles.forEach(p=>{x.save();x.translate(0,4);path(x,p,true);x.fillStyle='rgba(60,110,160,0.18)';x.fill();x.restore()});
 // контур (толстый), затем заливка: остаётся только внешняя кромка
 circles.forEach((p,i)=>{path(x,p,true);x.lineWidth=6;x.strokeStyle='#5f9cc6';x.stroke()});
 circles.forEach((p,i)=>{path(x,p,true);x.fillStyle='#ffffff';x.fill()});
 x.restore();
 // мягкая тень снизу (по объединённому контуру)
 x.save();x.beginPath();circles.forEach(p=>{p.forEach((q,i)=>i?x.lineTo(q[0],q[1]):x.moveTo(q[0],q[1]));x.closePath()});x.clip();
 const gg=x.createLinearGradient(0,50,0,112);gg.addColorStop(0,'rgba(150,195,230,0)');gg.addColorStop(1,'rgba(150,195,230,0.55)');x.fillStyle=gg;x.fillRect(0,0,W,H);
 x.strokeStyle='rgba(110,165,210,0.5)';x.lineWidth=1.3;const R=rng(seed+7);
 for(let k=0;k<22;k++){const hx=40+R()*140,hy=86+R()*22;x.beginPath();x.moveTo(hx,hy);x.lineTo(hx+7,hy-4.5);x.stroke()}
 x.restore();
 // белые блики
 x.strokeStyle='rgba(255,255,255,1)';x.lineWidth=3;sets.slice(0,3).forEach(s=>{x.beginPath();x.arc(s[0],s[1],s[2]*0.7,3.5,4.4);x.stroke()});
 return c}
// ---------- Прочее ----------
function pauseBtn(){const {c,x}=mk(56,56,4);const pts=wobble(ellPts(28,28,22,22),true,1.1,71);shadowOf(x,pts,2.5,3.2,0.22);fillShape(x,pts,'#fff3d2');
 hatch(x,pts,'#9a6b2f',0.14,4,-0.7,3);inkLine(x,pts,true,3,72);
 [[20,19,6,19],[31,19,6,19]].forEach((b,i)=>{const bp=wobble(rrPts(b[0],b[1],b[2],b[3],2.5),true,0.5,80+i);fillShape(x,bp,INK)});
 return c}
function knob(){const {c,x}=mk(48,48,4);const pts=wobble(ellPts(24,24,17,17),true,0.9,91);shadowOf(x,pts,2,3,0.22);fillShape(x,pts,'#fff8e6');
 const dot=wobble(ellPts(24,24,7,7),true,0.5,92);fillShape(x,dot,'#6fc24b');inkLine(x,dot,true,1.8,93);inkLine(x,pts,true,3,94);return c}
function track(fill,seed){const W=312,H=22;const {c,x}=mk(W,H,3);const pts=wobble(rrPts(4,4,W-8,H-8,7),true,0.7,seed);fillShape(x,pts,fill);
 hatch(x,pts,'#4a3a22',0.18,3.5,-0.7,seed+2,0.8);inkLine(x,pts,true,2.4,seed+1);
 x.strokeStyle='rgba(255,255,255,0.6)';x.lineWidth=1.4;x.beginPath();x.moveTo(14,8);x.lineTo(W/2,7.5);x.stroke();return c}
function paper(){const W=480,H=800,S=2;const {c,x}=mk(W,H,S);
 x.fillStyle='#fff6e0';x.fillRect(0,0,W,H);
 const g=x.createRadialGradient(W/2,H*0.45,120,W/2,H/2,H*0.75);g.addColorStop(0,'rgba(255,255,255,0.35)');g.addColorStop(1,'rgba(214,170,100,0.30)');x.fillStyle=g;x.fillRect(0,0,W,H);
 grain(x,W,H,5,70000,0.12);grain(x,W,H,6,12000,0.09,'#6a4a22');
 // волокна бумаги
 const R=rng(12);x.strokeStyle='rgba(150,110,60,0.07)';x.lineWidth=0.7;for(let i=0;i<260;i++){const a=R()*W,b=R()*H,l=6+R()*14,t=R()*6.28;x.beginPath();x.moveTo(a,b);x.lineTo(a+Math.cos(t)*l,b+Math.sin(t)*l);x.stroke()}
 // карандашные каракули по краям
 function star(cx,cy,r,sd,rot){const p=[];for(let i=0;i<10;i++){const a=rot+i*Math.PI/5-Math.PI/2,rr=i%2?r*0.45:r;p.push([cx+Math.cos(a)*rr,cy+Math.sin(a)*rr])}inkLine(x,wobble(p,true,0.7,sd,0.08),true,2,sd,'rgba(70,55,40,0.55)')}
 star(46,300,15,3,0.2);star(436,262,11,4,-0.3);star(30,570,9,5,0.5);
 function swirl(cx,cy,sd){const p=[];for(let i=0;i<40;i++){const a=i*0.38,r=2+i*0.55;p.push([cx+Math.cos(a)*r,cy+Math.sin(a)*r])}inkLine(x,wobble(p,false,0.5,sd,0.06),false,1.8,sd,'rgba(70,55,40,0.5)')}
 swirl(436,640,7);swirl(44,700,8);
 function plus(cx,cy,s,sd){inkLine(x,[[cx-s,cy],[cx+s,cy]],false,1.8,sd,'rgba(70,55,40,0.5)');inkLine(x,[[cx,cy-s],[cx,cy+s]],false,1.8,sd+1,'rgba(70,55,40,0.5)')}
 plus(60,420,6,9);plus(424,470,7,10);plus(420,150,6,11);plus(56,160,5,12);
 // вмятина-виньетка
 return c}
function titleScribble(){const W=300,H=26,S=3;const {c,x}=mk(W,H,S);const p=[];for(let i=0;i<=40;i++){p.push([10+i*7.2,13+Math.sin(i*0.9)*4+ (i%2?1:-1)*0.8])}inkLine(x,wobble(p,false,0.6,3,0.05),false,4,3,'#c0392b');return c}
// панели
const P={
 btn_primary:()=>blob(300,76,3,{r:34,amp:1.5,seed:101,fill:'#ffe3a0',lw:3.4,hl:4}),
 btn_secondary:()=>blob(300,76,3,{r:34,amp:1.5,seed:131,fill:'#fff4d8',lw:3.4,hl:4}),
 panel_hud:()=>blob(260,124,3,{r:30,amp:1.7,seed:201,fill:'#fff6df',lw:3.2,hl:5}),
 panel_banner:()=>blob(352,72,3,{r:28,amp:1.7,seed:211,fill:'#fff6df',lw:3.2,hl:5}),
 panel_big:()=>blob(424,420,2.5,{r:36,amp:2.4,seed:221,fill:'#fff7e2',lw:3.6,hl:7}),
 panel_settings:()=>blob(430,470,2.5,{r:38,amp:2.4,seed:231,fill:'#fff7e2',lw:3.6,hl:7}),
 panel_records:()=>blob(410,380,2.5,{r:38,amp:2.4,seed:241,fill:'#fff7e2',lw:3.6,hl:7})
};
window.makeAll=function(){const out={};const add=(n,c)=>out[n]=c.toDataURL('image/png');
 add('penguin_rise',penguin({wing:18,feet:-6,squash:1.0},1));
 add('penguin_fall',penguin({wing:112,feet:6,squash:1.0},2));
 add('penguin_land',penguin({wing:55,feet:-10,squash:0.86},3));
 Object.keys(P).forEach(k=>add(k,P[k]()));
 add('btn_pause',pauseBtn());add('slider_knob',knob());add('slider_empty',track('#f1e4c4',301));add('slider_full',track('#8fd16a',311));
 add('paper_bg',paper());add('title_scribble',titleScribble());
 add('mountains_far',ridge(480,300,2,[[0,150],[40,112],[84,150],[135,72],[188,136],[236,104],[292,40],[346,118],[392,92],[440,132],[480,100]],{fill:'#98c2d4',shade:'#4f7f95',ink:'#2f5566',amp:1.4,lw:2.6,seed:401,snow:true,snowY:128}));
 add('mountains_near',ridge(480,330,2,[[0,185],[58,150],[112,196],[168,120],[222,188],[270,150],[330,70],[386,172],[430,140],[480,176]],{fill:'#6fae9c',shade:'#2f6a63',ink:'#25463f',amp:1.7,lw:3,seed:431,snow:true,snowY:150}));
 for(let i=0;i<3;i++)add('cloud_'+(i+1),cloud(i,500+i*40));
 return out};
window.ART={INK};
})();
