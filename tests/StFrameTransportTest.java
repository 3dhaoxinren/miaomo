package dev.inknfclab;

import java.io.IOException;
import java.util.*;

/** Protocol fixtures only: exercises the production transport and transaction together. */
public final class StFrameTransportTest {
    static int checks;
    static void require(boolean ok){checks++;if(!ok)throw new AssertionError("check "+checks);}
    interface Action {void run()throws Exception;}
    static void fails(Action action)throws Exception{
        try{action.run();}catch(IOException|IllegalArgumentException expected){checks++;return;}
        throw new AssertionError("Expected bounded failure");
    }
    static final class Device implements StFrameTransport.Wire,StPollingTransaction.Link,StPollingTransaction.Store {
        final StFrameTransport transport=new StFrameTransport(this);
        final List<String> calls=new ArrayList<>();
        byte[] frames={0},saved,nativeReply;
        String savedChannel;
        boolean bridgeUnsupported=true,nativeUnsupported,nativeTimeout,corruptReadback,restoreFails,loseSetterReply,saveFails;
        int writes,pauses,resumes;
        public byte[] exchange(int oid,byte[] payload)throws Exception{
            calls.add(String.format(Locale.ROOT,"%02X:%s",oid,Protocol.hex(payload)));
            boolean query=oid==0x1e||oid==1&&payload[0]==0x15;
            if(oid==1&&bridgeUnsupported)return new byte[]{payload[0],0x0b};
            if(query){
                if(oid==0x1e){
                    require(payload.length==0);
                    if(nativeTimeout)throw new IOException("simulated native timeout");
                    if(nativeUnsupported)return new byte[]{0x0b};
                    if(nativeReply!=null)return nativeReply;
                }
                byte[] value=frames;
                if(corruptReadback&&writes==1){value=new byte[]{0};corruptReadback=false;}
                byte[] out=new byte[value.length+(oid==1?2:1)];
                if(oid==1)out[0]=0x15;
                System.arraycopy(value,0,out,oid==1?2:1,value.length);return out;
            }
            require(oid==0x1d||oid==1&&payload[0]==0x13);
            require(saved!=null&&savedChannel.equals(transport.channel()));
            writes++;
            if(restoreFails&&writes>1)throw new IOException("simulated restore rejection");
            frames=oid==1?Arrays.copyOfRange(payload,1,payload.length):payload.clone();
            if(loseSetterReply&&writes==1)throw new IOException("simulated setter response lost");
            return oid==1?new byte[]{0x13,0}:new byte[]{0};
        }
        public byte[] exchange(byte[] command)throws Exception{return transport.exchange(command);}
        public void pause(){pauses++;}
        public void resume(){resumes++;}
        public byte[] load()throws Exception{if(saved!=null)transport.restoreChannel(savedChannel);return saved;}
        public void save(byte[] value)throws Exception{transport.pin();if(saveFails)throw new IOException("storage failure");saved=value.clone();savedChannel=transport.channel();calls.add("SAVE:"+savedChannel);}
        public void clear(){saved=null;calls.add("CLEAR");}
        StPollingTransaction transaction(){return new StPollingTransaction(this,this);}
    }
    public static void main(String[] args)throws Exception{
        Device d=new Device();StPollingTransaction t=d.transaction();
        require(Arrays.equals(t.start(),new byte[]{0}));require(d.transport.channel().equals("native"));
        require(d.calls.subList(0,4).equals(Arrays.asList("01:15","1E:","SAVE:native","1D:012A020A7A")));
        require(Arrays.equals(d.frames,Protocol.polling7A()));t.restore();
        require(Arrays.equals(d.frames,new byte[]{0})&&d.saved==null&&!t.needsRecovery());require(d.pauses==d.resumes);
        require(d.calls.contains("1D:00"));

        d=new Device();d.bridgeUnsupported=false;t=d.transaction();t.start();t.restore();
        require(d.transport.channel().equals("bridge"));require(!d.calls.contains("1E:"));require(d.calls.contains("01:1300"));

        // Raw native frame includes CRC bytes and a waiting-time CRC bit: restore byte-for-byte.
        byte[] raw={1,0x2a,4,(byte)0x8a,0x26,0x12,0x34};
        d=new Device();d.frames=raw.clone();t=d.transaction();t.start();t.restore();
        require(Arrays.equals(d.frames,raw));require(d.calls.contains("1D:"+Protocol.hex(raw)));

        d=new Device();d.nativeUnsupported=true;t=d.transaction();fails(t::start);
        require(d.writes==0&&d.saved==null&&d.resumes==1);
        for(byte[] reply:new byte[][]{new byte[0],{0},{0,1,0x2a,2},{0,0,1}}){
            d=new Device();d.nativeReply=reply;t=d.transaction();fails(t::start);require(d.writes==0&&d.saved==null);
        }
        d=new Device();d.nativeTimeout=true;t=d.transaction();fails(t::start);require(d.writes==0&&d.saved==null&&d.calls.size()==2);
        d=new Device();d.saveFails=true;t=d.transaction();fails(t::start);require(d.writes==0);
        d=new Device();d.corruptReadback=true;t=d.transaction();fails(t::start);
        require(Arrays.equals(d.frames,new byte[]{0})&&d.saved==null&&d.writes==2);
        require(Collections.frequency(d.calls,"01:15")==1); // no route change after first write
        d=new Device();d.loseSetterReply=true;t=d.transaction();fails(t::start);require(d.saved==null&&Arrays.equals(d.frames,new byte[]{0}));
        d=new Device();d.loseSetterReply=true;d.restoreFails=true;t=d.transaction();fails(t::start);
        require(t.needsRecovery()&&d.saved!=null);d.restoreFails=false;t.restore();require(!t.needsRecovery()&&d.saved==null);

        for(String channel:new String[]{"bridge","native"}){
            d=new Device();d.bridgeUnsupported=false;d.saved=raw.clone();d.savedChannel=channel;d.frames=Protocol.polling7A();t=d.transaction();
            t.start();t.restore();require(Arrays.equals(d.frames,raw));require(d.transport.channel().equals(channel));
            require(d.calls.get(0).equals(channel.equals("native")?"1D:"+Protocol.hex(raw):"01:13"+Protocol.hex(raw)));
        }
        d=new Device();d.transport.restoreChannel("bridge");t=d.transaction();fails(t::start);
        require(d.calls.equals(Arrays.asList("01:15"))&&d.writes==0); // saved channel never negotiates
        final StFrameTransport unpinned=new StFrameTransport((oid,payload)->{throw new AssertionError("No wire access allowed");});
        fails(()->unpinned.exchange(new byte[]{0x13,0}));fails(unpinned::pin);
        fails(()->unpinned.restoreChannel("unknown"));unpinned.restoreChannel("native");fails(()->unpinned.restoreChannel("bridge"));
        require(Protocol.hex(Protocol.polling7A()).equals("012A020A7A"));
        System.out.println("PASS StFrameTransportTest checks="+checks+": bridge/native negotiation; snapshot before setter; exact raw restore; bounded failures; pinned recovery; SIMULATED_TRANSPORT_ONLY");
    }
}
