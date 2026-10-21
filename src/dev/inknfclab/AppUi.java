package dev.inknfclab;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

final class AppUi {
    static final int BG=0xfff7f7f5,SURFACE=0xffffffff,INK=0xff202124,MUTED=0xff85868a,ACCENT=0xff202124,SOFT=0xffeeefec,LINE=0xffe5e5e1;
    static final int DARK=0xff111214,PANEL=0xff1f2023,CONTROL=0xff2d2e32,WHITE=0xfff2f2ef,DIM=0xff9a9b9e,LIME=0xffd8ec9d;
    static int dp(Context c,float value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable shape(int color,int radius,Context c){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static TextView text(Context c,String value,int size,int color,boolean bold){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");if(bold)t.setTypeface(Typeface.create("sans-serif-medium",0));t.setLineSpacing(dp(c,3),1);return t;}
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static LinearLayout card(Context c){LinearLayout l=column(c);l.setPadding(dp(c,16),dp(c,16),dp(c,16),dp(c,16));GradientDrawable bg=shape(SURFACE,14,c);bg.setStroke(dp(c,1),LINE);l.setBackground(bg);return l;}
    static void gap(LinearLayout p,int size){View v=new View(p.getContext());p.addView(v,new LinearLayout.LayoutParams(1,dp(p.getContext(),size)));}
    static void add(LinearLayout p,View v){p.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    static Button button(Context c,String label,boolean primary,Runnable action){Button b=new Button(c);b.setText(label);b.setAllCaps(false);b.setTextSize(13);b.setTypeface(Typeface.create("sans-serif-medium",0));b.setTextColor(primary?SURFACE:INK);b.setBackgroundTintList(null);GradientDrawable bg=shape(primary?INK:SURFACE,10,c);if(!primary)bg.setStroke(dp(c,1),LINE);b.setBackground(bg);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(c,42));b.setMinimumHeight(dp(c,42));b.setPadding(dp(c,10),dp(c,7),dp(c,10),dp(c,7));b.setStateListAnimator(null);b.setOnClickListener(v->action.run());return b;}
    static Button darkButton(Context c,String label,boolean selected,Runnable action){Button b=button(c,label,false,action);b.setTextColor(selected?DARK:WHITE);b.setBackground(shape(selected?LIME:CONTROL,8,c));b.setTextSize(12);b.setMinHeight(dp(c,36));b.setMinimumHeight(dp(c,36));return b;}
    static Button ghost(Context c,String label,boolean dark,Runnable action){Button b=button(c,label,false,action);b.setBackground(shape(0x00000000,8,c));b.setTextColor(dark?WHITE:MUTED);return b;}
    static LinearLayout pair(Context c,View first,View second){LinearLayout row=new LinearLayout(c);LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,-2,1);a.rightMargin=dp(c,8);row.addView(first,a);row.addView(second,new LinearLayout.LayoutParams(0,-2,1));return row;}
    static TextView label(Context c,String value){return text(c,value,12,MUTED,true);}
    static void title(LinearLayout parent,String title,String detail){Context c=parent.getContext();parent.addView(text(c,title,24,INK,true));gap(parent,5);parent.addView(text(c,detail,12,MUTED,false));gap(parent,16);}
    private AppUi(){}
}
