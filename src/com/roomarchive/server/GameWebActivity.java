package com.roomarchive.server;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;

/** 内嵌 HTML5 游戏容器 */
public class GameWebActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String game = getIntent() != null ? getIntent().getStringExtra("game") : "snake";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 247, 250));

        WebView wv = new WebView(this);
        wv.getSettings().setJavaScriptEnabled(true);
        wv.getSettings().setDomStorageEnabled(true);
        wv.setWebViewClient(new WebViewClient());
        root.addView(wv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        String html;
        if ("2048".equals(game)) {
            html = game2048();
        } else if ("tetris".equals(game)) {
            html = gameTetris();
        } else {
            html = gameSnake();
        }
        wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private String gameSnake() {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>"
            + "<style>body{margin:0;background:#1a1a2e;display:flex;flex-direction:column;align-items:center;padding-top:20px;font-family:sans-serif}"
            + "h2{color:#00d4ff;margin:0 0 10px}canvas{background:#16213e;border-radius:10px;touch-action:none}"
            + "p{color:#888;font-size:12px}</style></head><body>"
            + "<h2>贪吃蛇</h2><canvas id='c' width='300' height='300'></canvas>"
            + "<p>滑动屏幕控制方向</p>"
            + "<script>const c=document.getElementById('c'),x=c.getContext('2d');const g=15,n=20;"
            + "let s=[{x:10,y:10}],d={x:1,y:0},f={x:5,y:5},sc=0;"
            + "function loop(){x.fillStyle='#16213e';x.fillRect(0,0,300,300);"
            + "x.fillStyle='#e94560';s.forEach(p=>x.fillRect(p.x*n+1,p.y*n+1,n-2,n-2));"
            + "x.fillStyle='#00d4ff';x.fillRect(f.x*n+1,f.y*n+1,n-2,n-2);"
            + "let h={x:s[0].x+d.x,y:s[0].y+d.y};if(h.x<0||h.x>=g||h.y<0||h.y>=g||s.some(p=>p.x==h.x&&p.y==h.y)){alert('游戏结束 得分:'+sc);s=[{x:10,y:10}];sc=0;return;}"
            + "s.unshift(h);if(h.x==f.x&&h.y==f.y){sc++;f={x:Math.floor(Math.random()*g),y:Math.floor(Math.random()*g)}}else{s.pop()}}"
            + "setInterval(loop,150);"
            + "let ty=0,tx=0;c.addEventListener('touchstart',e=>{tx=e.touches[0].clientX;ty=e.touches[0].clientY});"
            + "c.addEventListener('touchmove',e=>{e.preventDefault();const dx=e.touches[0].clientX-tx,dy=e.touches[0].clientY-ty;"
            + "if(Math.abs(dx)>Math.abs(dy)){d=dx>0?{x:1,y:0}:{x:-1,y:0}}else{d=dy>0?{x:0,y:1}:{x:0,y:-1}}},{passive:false});</script></body></html>";
    }

    private String game2048() {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>"
            + "<style>body{margin:0;background:#faf8ef;display:flex;flex-direction:column;align-items:center;padding-top:20px;font-family:sans-serif}"
            + "h2{color:#776e65;margin:0 0 10px}.grid{display:grid;grid-template-columns:repeat(4,70px);gap:8px;background:#bbada0;padding:8px;border-radius:8px}"
            + ".cell{width:70px;height:70px;background:#cdc1b4;border-radius:4px;display:flex;align-items:center;justify-content:center;font-size:24px;font-weight:bold;color:#776e65}"
            + "p{color:#776e65;font-size:12px}</style></head><body>"
            + "<h2>2048</h2><div class='grid' id='g'></div><p>滑动合并数字</p>"
            + "<script>const g=document.getElementById('g');let b=Array(16).fill(0);"
            + "function r(){let e=[];b.forEach((v,i)=>{e.push('<div class=cell>'+(v||'')+'</div>')});g.innerHTML=e.join('')}"
            + "function add(){let z=[];b.forEach((v,i)=>{if(!v)z.push(i)});if(z.length)b[z[Math.floor(Math.random()*z.length)]]=2}"
            + "add();add();r();"
            + "let sx=0,sy=0;document.addEventListener('touchstart',e=>{sx=e.touches[0].clientX;sy=e.touches[0].clientY});"
            + "document.addEventListener('touchend',e=>{const dx=e.changedTouches[0].clientX-sx,dy=e.changedTouches[0].clientY-sy;"
            + "let mv=Math.abs(dx)>Math.abs(dy)?(dx>0?1:-1):(dy>0?4:-4);"
            + "for(let row=0;row<4;row++){let line=[];for(let c=0;c<4;c++){let i=mv==1?row*4+3-c:mv==-1?row*4+c:mv==4?c*4+3-row:c*4+row;line.push(b[i])}"
            + "line=line.filter(v=>v);for(let i=0;i<line.length-1;i++){if(line[i]==line[i+1]){line[i]*=2;line.splice(i+1,1)}}"
            + "while(line.length<4)line.push(0);for(let c=0;c<4;c++){let i=mv==1?row*4+3-c:mv==-1?row*4+c:mv==4?c*4+3-row:c*4+row;b[i]=line[c]}}"
            + "add();r()});</script></body></html>";
    }

    private String gameTetris() {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>"
            + "<style>body{margin:0;background:#0f0f23;display:flex;flex-direction:column;align-items:center;padding-top:20px;font-family:sans-serif}"
            + "h2{color:#00ff88;margin:0 0 10px}canvas{background:#1a1a3e;border-radius:8px;touch-action:none}"
            + "p{color:#666;font-size:12px}</style></head><body>"
            + "<h2>俄罗斯方块</h2><canvas id='c' width='200' height='300'></canvas>"
            + "<p>左右滑动移动，下滑加速</p>"
            + "<script>const c=document.getElementById('c'),x=c.getContext('2d');const W=10,H=15,s=20;"
            + "let b=Array(H).fill().map(()=>Array(W).fill(0));"
            + "let sh=[[1,1,1,1]],sx=3,sy=0,sc=0;"
            + "function draw(){x.fillStyle='#1a1a3e';x.fillRect(0,0,200,300);"
            + "b.forEach((row,y)=>row.forEach((v,xx)=>{if(v){x.fillStyle='#00ff88';x.fillRect(xx*s+1,y*s+1,s-2,s-2)}}));"
            + "sh.forEach((row,dy)=>row.forEach((v,dx)=>{if(v){x.fillStyle='#ff0066';x.fillRect((sx+dx)*s+1,(sy+dy)*s+1,s-2,s-2)}}))}"
            + "function tick(){let ok=true;for(let dy=0;dy<sh.length;dy++)for(let dx=0;dx<sh[dy].length;dx++){if(sh[dy][dx]){let ny=sy+dy+1;if(ny>=H||b[ny][sx+dx])ok=false}}"
            + "if(!ok){sh.forEach((row,dy)=>row.forEach((v,dx)=>{if(v)b[sy+dy][sx+dx]=1}));sh=[[1,1,1,1]];sx=3;sy=0;sc++;draw();return}"
            + "sy++;draw()}"
            + "setInterval(tick,500);draw();"
            + "let tx=0;c.addEventListener('touchstart',e=>tx=e.touches[0].clientX);"
            + "c.addEventListener('touchend',e=>{const dx=e.changedTouches[0].clientX-tx;if(dx>30)sx=Math.min(sx+1,W-sh[0].length);else if(dx<-30)sx=Math.max(sx-1,0);draw()});</script></body></html>";
    }
}
