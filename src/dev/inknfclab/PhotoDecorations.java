package dev.inknfclab;
import android.graphics.*;
import android.text.*;

/** One renderer shared by the interactive editor and the exported logical canvas. */
final class PhotoDecorations {
    static final String[] FRAMES={"无相框","留白","细线","双线","拍立得","胶片"};
    static final int[] COLORS={0xff000000,0xffffffff,0xffc80000,0xffe8b000};
    private static TextPaint paint(PhotoEdit.TextLayer t,int w,int h){TextPaint p=new TextPaint(3);p.setColor(t.color);p.setTextSize((float)(Math.min(w,h)*t.size));String family=t.font==1?"serif":t.font==2?"monospace":"sans-serif";p.setTypeface(Typeface.create(family,t.bold?Typeface.BOLD:Typeface.NORMAL));return p;}
    private static StaticLayout layout(PhotoEdit.TextLayer t,TextPaint p,int w){float measured=0;for(String line:t.text.split("\n",-1))measured=Math.max(measured,p.measureText(line));int wrap=Math.max(1,Math.min((int)(w*.86),(int)Math.ceil(measured)));return StaticLayout.Builder.obtain(t.text,0,t.text.length(),p,wrap).setAlignment(t.align==0?Layout.Alignment.ALIGN_NORMAL:t.align==2?Layout.Alignment.ALIGN_OPPOSITE:Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setLineSpacing(2,1.05f).build();}
    static void draw(Canvas canvas,PhotoEdit edit,int width,int height,int selected,boolean interactive){
        frame(canvas,edit,width,height);
        for(int i=0;i<edit.texts.size();i++)drawText(canvas,edit.texts.get(i),width,height,interactive&&i==selected);
    }
    private static void frame(Canvas c,PhotoEdit e,int w,int h){
        if(e.frameStyle==0)return;Paint p=new Paint(3);float b=(float)(Math.min(w,h)*e.frameWidth);p.setColor(e.frameColor);
        if(e.frameStyle==1||e.frameStyle==4){float bottom=e.frameStyle==4?Math.max(b,h*.15f):b;c.drawRect(0,0,w,b,p);c.drawRect(0,h-bottom,w,h,p);c.drawRect(0,b,b,h-bottom,p);c.drawRect(w-b,b,w,h-bottom,p);}
        if(e.frameStyle==2||e.frameStyle==3){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,b*.12f));c.drawRect(b,b,w-b,h-b,p);if(e.frameStyle==3)c.drawRect(b*1.5f,b*1.5f,w-b*1.5f,h-b*1.5f,p);}
        if(e.frameStyle==5){p.setColor(0xff000000);c.drawRect(0,0,b*1.5f,h,p);c.drawRect(w-b*1.5f,0,w,h,p);p.setColor(0xffffffff);float step=Math.max(14,b*1.3f);for(float y=b*.4f;y<h;y+=step){c.drawRoundRect(b*.4f,y,b*1.1f,y+step*.55f,b*.1f,b*.1f,p);c.drawRoundRect(w-b*1.1f,y,w-b*.4f,y+step*.55f,b*.1f,b*.1f,p);}}
    }
    private static void drawText(Canvas c,PhotoEdit.TextLayer t,int w,int h,boolean selected){
        TextPaint p=paint(t,w,h);StaticLayout text=layout(t,p,w);float boxWidth=text.getWidth(),boxHeight=text.getHeight(),pad=Math.min(w,h)*.015f;
        c.save();c.translate((float)(t.x*w),(float)(t.y*h));c.rotate((float)t.rotation);
        if(t.background!=0){Paint bg=new Paint(3);bg.setColor(t.background);c.drawRoundRect(-boxWidth/2-pad,-boxHeight/2-pad,boxWidth/2+pad,boxHeight/2+pad,pad*.5f,pad*.5f,bg);}
        c.translate(-boxWidth/2,-boxHeight/2);
        if(t.outline){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,p.getTextSize()*.055f));p.setColor(t.color==0xffffffff?0xff000000:0xffffffff);text.draw(c);p.setStyle(Paint.Style.FILL);p.setColor(t.color);}
        text.draw(c);
        if(selected){Paint border=new Paint(3);border.setColor(AppUi.ACCENT);border.setStyle(Paint.Style.STROKE);border.setStrokeWidth(Math.max(1,w/400f));c.drawRect(-pad,-pad,boxWidth+pad,boxHeight+pad,border);}c.restore();
    }
    static int hit(PhotoEdit edit,int w,int h,float x,float y){
        for(int i=edit.texts.size()-1;i>=0;i--){PhotoEdit.TextLayer t=edit.texts.get(i);TextPaint p=paint(t,w,h);StaticLayout text=layout(t,p,w);double r=Math.toRadians(-t.rotation),dx=x-t.x*w,dy=y-t.y*h;double tx=dx*Math.cos(r)-dy*Math.sin(r),ty=dx*Math.sin(r)+dy*Math.cos(r);float pad=Math.min(w,h)*.03f;if(Math.abs(tx)<text.getWidth()/2f+pad&&Math.abs(ty)<text.getHeight()/2f+pad)return i;}return -1;
    }
    private PhotoDecorations(){}
}
