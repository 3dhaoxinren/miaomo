package dev.inknfclab;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Small, consistent stroke icons for the native tool rail. */
final class UiIcon extends View {
    private final String name;private final int color;private final Paint p=new Paint(3);
    UiIcon(Context c,String name,int color){super(c);this.name=name;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    protected void onDraw(Canvas c){c.save();c.translate(getWidth()/2f,getHeight()/2f);float s=Math.min(getWidth(),getHeight())/28f;c.scale(s,s);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        if(name.equals("text")){c.drawLine(-8,-8,8,-8,p);c.drawLine(0,-8,0,9,p);c.drawLine(-5,9,5,9,p);c.drawLine(-8,-8,-8,-5,p);c.drawLine(8,-8,8,-5,p);}
        else if(name.equals("crop")){c.drawLine(-5,-11,-5,6,p);c.drawLine(-5,6,11,6,p);c.drawLine(-11,-6,6,-6,p);c.drawLine(6,-6,6,11,p);}
        else if(name.equals("frame")){c.drawRoundRect(-9,-11,9,11,1.5f,1.5f,p);c.drawRect(-6,-8,6,5,p);c.drawLine(-2,8,2,8,p);}
        else if(name.equals("rotate")){c.drawArc(-8,-8,8,8,-140,290,false,p);c.drawLine(-9,-6,-9,0,p);c.drawLine(-9,-6,-3,-6,p);}
        else if(name.equals("mirror")){c.drawLine(0,-11,0,11,p);Path a=new Path();a.moveTo(-9,7);a.lineTo(-3,7);a.lineTo(-3,-7);a.close();c.drawPath(a,p);a.reset();a.moveTo(9,7);a.lineTo(3,7);a.lineTo(3,-7);a.close();c.drawPath(a,p);}
        else if(name.equals("undo")||name.equals("redo")){if(name.equals("redo"))c.scale(-1,1);c.drawLine(-9,-4,-3,-10,p);c.drawLine(-9,-4,-3,2,p);c.drawLine(-9,-4,2,-4,p);c.drawArc(-3,-4,10,9,-90,230,false,p);}
        else if(name.equals("compare")){c.drawRoundRect(-9,-9,9,9,2,2,p);c.drawLine(0,-12,0,12,p);p.setStyle(Paint.Style.FILL);c.drawRect(-7,-7,-2,7,p);}
        else if(name.equals("image")){c.drawRoundRect(-10,-9,10,9,2,2,p);c.drawCircle(4,-3,2,p);Path a=new Path();a.moveTo(-10,6);a.lineTo(-3,-2);a.lineTo(3,5);a.lineTo(7,1);a.lineTo(10,5);c.drawPath(a,p);}
        else if(name.equals("save")){c.drawLine(0,-10,0,4,p);c.drawLine(-4,0,0,4,p);c.drawLine(0,4,4,0,p);c.drawLine(-9,2,-9,9,p);c.drawLine(-9,9,9,9,p);c.drawLine(9,9,9,2,p);}
        else if(name.equals("reset")){c.drawCircle(0,0,8,p);c.drawLine(-3,0,3,0,p);}
        else if(name.equals("more")){p.setStyle(Paint.Style.FILL);for(int i=-1;i<=1;i++)c.drawCircle(i*6,0,1.4f,p);}
        else if(name.equals("back")){c.drawLine(4,-8,-4,0,p);c.drawLine(-4,0,4,8,p);}
        else if(name.equals("close")){c.drawLine(-6,-6,6,6,p);c.drawLine(-6,6,6,-6,p);}
        else if(name.equals("plus")){c.drawLine(-8,0,8,0,p);c.drawLine(0,-8,0,8,p);}
        else if(name.equals("check")){c.drawLine(-8,0,-2,6,p);c.drawLine(-2,6,9,-7,p);}
        else if(name.equals("nfc")){c.drawRoundRect(-7,-11,7,11,2,2,p);c.drawLine(-2,7,2,7,p);c.drawArc(-3,-6,9,6,-60,120,false,p);}
        else{c.drawCircle(0,0,8,p);}c.restore();
    }
    static LinearLayout tool(Context c,String icon,String label,boolean selected,boolean dark,Runnable action){
        LinearLayout item=AppUi.column(c);item.setGravity(Gravity.CENTER);item.setPadding(AppUi.dp(c,4),AppUi.dp(c,8),AppUi.dp(c,4),AppUi.dp(c,7));int color=dark?(selected?AppUi.LIME:AppUi.DIM):(selected?AppUi.INK:AppUi.MUTED);
        UiIcon mark=new UiIcon(c,icon,color);item.addView(mark,new LinearLayout.LayoutParams(AppUi.dp(c,25),AppUi.dp(c,25)));AppUi.gap(item,4);TextView text=AppUi.text(c,label,11,color,selected);item.addView(text);item.setContentDescription(label);item.setClickable(true);item.setFocusable(true);item.setBackground(AppUi.shape(selected?(dark?0xff282d23:AppUi.SOFT):0,10,c));item.setOnClickListener(v->action.run());return item;
    }
    static View iconButton(Context c,String icon,String label,boolean dark,Runnable action){android.widget.FrameLayout box=new FrameLayout(c);UiIcon mark=new UiIcon(c,icon,dark?AppUi.WHITE:AppUi.INK);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(AppUi.dp(c,23),AppUi.dp(c,23),Gravity.CENTER);box.addView(mark,lp);box.setContentDescription(label);box.setClickable(true);box.setFocusable(true);box.setOnClickListener(v->action.run());box.setMinimumWidth(AppUi.dp(c,42));box.setMinimumHeight(AppUi.dp(c,42));return box;}
}
