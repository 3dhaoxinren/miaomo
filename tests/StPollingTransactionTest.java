package dev.inknfclab;

import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public final class StPollingTransactionTest {
    static class Device implements StPollingTransaction.Link,StPollingTransaction.Store {
        byte[] frames={0},saved;
        int writes,pauses,resumes,reads;
        boolean rejectQuery,rejectPause,rejectSave,failAfterWrite,failRestore,corruptFirstReadback;
        final List<String> order=new ArrayList<>();
        public void pause()throws Exception{pauses++;if(rejectPause)throw new IOException("no permission");order.add("pause");}
        public void resume(){resumes++;order.add("resume");}
        public byte[] load(){return saved==null?null:saved.clone();}
        public void save(byte[] value)throws Exception{if(rejectSave)throw new IOException("storage unavailable");saved=value.clone();order.add("save");}
        public void clear(){saved=null;order.add("clear");}
        public byte[] exchange(byte[] cmd)throws Exception{
            int op=cmd[0]&255;
            if(op==0x15){
                reads++;if(rejectQuery)return new byte[]{0x15,1};
                byte[] value=frames;if(corruptFirstReadback&&writes==1){value=new byte[]{0};corruptFirstReadback=false;}
                byte[] response=new byte[value.length+2];response[0]=0x15;System.arraycopy(value,0,response,2,value.length);return response;
            }
            require(op==0x13);writes++;order.add("write");
            if(failRestore&&writes>1)throw new IOException("restore rejected");
            frames=Arrays.copyOfRange(cmd,1,cmd.length);
            if(failAfterWrite&&writes==1)throw new IOException("lost response after applying frame");
            return new byte[]{0x13,0};
        }
    }
    static int checks;
    static void require(boolean good){checks++;if(!good)throw new AssertionError("check "+checks);}
    interface Action {void run()throws Exception;}
    static void fails(Action run)throws Exception{try{run.run();}catch(IOException expected){checks++;return;}throw new AssertionError("Expected bounded failure");}
    public static void main(String[] args)throws Exception{
        Device d=new Device();StPollingTransaction t=new StPollingTransaction(d,d);
        require(Arrays.equals(t.start(),new byte[]{0}));require(Arrays.equals(d.frames,Protocol.polling7A()));require(d.saved!=null);require(d.order.indexOf("save")<d.order.indexOf("write"));
        t.restore();require(Arrays.equals(d.frames,new byte[]{0})&&d.saved==null&&!t.needsRecovery());require(d.pauses==d.resumes);
        d=new Device();d.rejectQuery=true;StPollingTransaction q=new StPollingTransaction(d,d);fails(q::start);require(d.writes==0&&d.saved==null&&d.resumes==1);
        d=new Device();d.rejectPause=true;StPollingTransaction p=new StPollingTransaction(d,d);fails(p::start);require(d.writes==0&&d.reads==0);
        d=new Device();d.rejectSave=true;StPollingTransaction storage=new StPollingTransaction(d,d);fails(storage::start);require(d.writes==0);
        d=new Device();d.failAfterWrite=true;StPollingTransaction lost=new StPollingTransaction(d,d);fails(lost::start);require(Arrays.equals(d.frames,new byte[]{0})&&d.saved==null);
        d=new Device();d.corruptFirstReadback=true;StPollingTransaction corrupt=new StPollingTransaction(d,d);fails(corrupt::start);require(Arrays.equals(d.frames,new byte[]{0})&&d.saved==null);
        d=new Device();d.failAfterWrite=true;d.failRestore=true;StPollingTransaction recovery=new StPollingTransaction(d,d);fails(recovery::start);require(d.saved!=null&&recovery.needsRecovery());d.failRestore=false;recovery.restore();require(d.saved==null&&Arrays.equals(d.frames,new byte[]{0}));
        d=new Device();d.saved=new byte[]{0};d.frames=Protocol.polling7A();StPollingTransaction recovered=new StPollingTransaction(d,d);recovered.start();recovered.restore();require(d.saved==null&&Arrays.equals(d.frames,new byte[]{0}));
        d=new Device();d.frames=Protocol.polling7A();StPollingTransaction other=new StPollingTransaction(d,d);fails(other::start);require(d.writes==0&&d.saved==null);
        require(NfcControllerProfile.select("ST54L",-1,true)==NfcControllerProfile.Kind.ST54L_FRAMES);
        fails(()->NfcControllerProfile.select("ST54L",-1,false));fails(()->NfcControllerProfile.select("ST54T",16,true));
        require(NfcControllerProfile.select("0xc1",16,false)==NfcControllerProfile.Kind.NXP_QTAG);fails(()->NfcControllerProfile.select("unknown",-1,true));
        fails(()->StPollingTransaction.response(0x15,new byte[]{0x13,0,0}));fails(()->StPollingTransaction.response(0x15,new byte[]{0x15,1}));
        System.out.println("PASS StPollingTransactionTest checks="+checks+": snapshot before write; exact 7A readback; restore on timeout/mismatch; persisted recovery; controller profile separation. SIMULATED_TRANSPORT_ONLY");
    }
}
