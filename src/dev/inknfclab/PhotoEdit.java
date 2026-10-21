package dev.inknfclab;
import java.io.*;
import java.util.*;

/** Normalized edit state; landscape compositions rotate into the fixed portrait NFC frame. */
final class PhotoEdit {
    boolean landscape,mirror,cover=true;
    int rotation;
    double zoom=1,offsetX,offsetY;
    int frameStyle,frameColor=0xffffffff;
    double frameWidth=.035;
    final ArrayList<TextLayer> texts=new ArrayList<>();
    static final int MAX_TEXTS=20;
    static final class TextLayer {
        String text="写下这一刻";
        double x=.5,y=.5,size=.065,rotation;
        int color=0xff000000,background,align=1,font;
        boolean bold=true,outline;
        TextLayer copy(){TextLayer t=new TextLayer();t.text=text;t.x=x;t.y=y;t.size=size;t.rotation=rotation;t.color=color;t.background=background;t.align=align;t.font=font;t.bold=bold;t.outline=outline;return t;}
        void normalize(){if(text==null)text="";if(text.length()>500)text=text.substring(0,500);x=clamp(x,0,1);y=clamp(y,0,1);size=clamp(size,.025,.2);rotation=clamp(rotation,-180,180);align=Math.max(0,Math.min(2,align));font=Math.max(0,Math.min(2,font));}
    }
    PhotoEdit copy(){PhotoEdit e=new PhotoEdit();e.landscape=landscape;e.mirror=mirror;e.cover=cover;e.rotation=rotation;e.zoom=zoom;e.offsetX=offsetX;e.offsetY=offsetY;e.frameStyle=frameStyle;e.frameColor=frameColor;e.frameWidth=frameWidth;for(TextLayer t:texts)e.texts.add(t.copy());return e;}
    int width(){return landscape?768:552;}
    int height(){return landscape?552:768;}
    void normalize(){rotation=((Math.round(rotation/90f)*90)%360+360)%360;zoom=clamp(zoom,1,4);offsetX=Double.isFinite(offsetX)?clamp(offsetX,-1,1):0;offsetY=Double.isFinite(offsetY)?clamp(offsetY,-1,1):0;frameStyle=Math.max(0,Math.min(5,frameStyle));frameWidth=clamp(frameWidth,.01,.12);while(texts.size()>MAX_TEXTS)texts.remove(texts.size()-1);for(TextLayer t:texts)t.normalize();}
    void reset(){landscape=false;mirror=false;cover=true;rotation=0;zoom=1;offsetX=offsetY=0;frameStyle=0;frameColor=0xffffffff;frameWidth=.035;texts.clear();}
    byte[] encode()throws IOException{
        PhotoEdit e=copy();e.normalize();ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(0x494E4B45);out.writeInt(1);out.writeBoolean(e.landscape);out.writeBoolean(e.mirror);out.writeBoolean(e.cover);out.writeInt(e.rotation);out.writeDouble(e.zoom);out.writeDouble(e.offsetX);out.writeDouble(e.offsetY);out.writeInt(e.frameStyle);out.writeInt(e.frameColor);out.writeDouble(e.frameWidth);out.writeInt(e.texts.size());
        for(TextLayer t:e.texts){out.writeUTF(t.text);out.writeDouble(t.x);out.writeDouble(t.y);out.writeDouble(t.size);out.writeDouble(t.rotation);out.writeInt(t.color);out.writeInt(t.background);out.writeInt(t.align);out.writeInt(t.font);out.writeBoolean(t.bold);out.writeBoolean(t.outline);}out.flush();return bytes.toByteArray();
    }
    static PhotoEdit decode(byte[] bytes)throws IOException{
        if(bytes==null||bytes.length>100000)throw new IOException("编辑参数无效");DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));if(in.readInt()!=0x494E4B45||in.readInt()!=1)throw new IOException("未知编辑版本");
        PhotoEdit e=new PhotoEdit();e.landscape=in.readBoolean();e.mirror=in.readBoolean();e.cover=in.readBoolean();e.rotation=in.readInt();e.zoom=in.readDouble();e.offsetX=in.readDouble();e.offsetY=in.readDouble();e.frameStyle=in.readInt();e.frameColor=in.readInt();e.frameWidth=in.readDouble();int count=in.readInt();if(count<0||count>MAX_TEXTS)throw new IOException("文字图层数量无效");
        for(int i=0;i<count;i++){TextLayer t=new TextLayer();t.text=in.readUTF();t.x=in.readDouble();t.y=in.readDouble();t.size=in.readDouble();t.rotation=in.readDouble();t.color=in.readInt();t.background=in.readInt();t.align=in.readInt();t.font=in.readInt();t.bold=in.readBoolean();t.outline=in.readBoolean();e.texts.add(t);}if(in.available()!=0)throw new IOException("编辑参数尾部异常");e.normalize();return e;
    }
    static double clamp(double v,double lo,double hi){return Double.isFinite(v)?Math.max(lo,Math.min(hi,v)):lo;}
}
