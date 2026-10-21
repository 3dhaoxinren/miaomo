package dev.inknfclab;
import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Scripted transport only: validates writes, refresh acceptance, failure, and cancellation. */
public final class FullTransferTest {
    static class Clock implements ImageTransfer.RefreshClock {
        long now;
        public long now() { return now; }
        public void sleep(long ms) { now+=ms; }
    }
    static class Link implements ImageTransfer.TransferLink {
        final List<byte[]> commands=new ArrayList<>();
        int max=512,timeout=3000,failAt=-1,failStatus=-1,polls;
        boolean connected=true;
        public boolean isConnected() { return connected; }
        public int getMaxTransceiveLength() { return max; }
        public int getTimeout() { return timeout; }
        public void setTimeout(int value) { timeout=value; }
        public byte[] transceive(byte[] command) throws Exception {
            commands.add(command.clone());
            if(commands.size()==failAt) throw new IOException("scripted disconnect");
            if(commands.size()==failStatus) return new byte[]{0x69,(byte)0x92};
            if((command[1]&255)==0xde) return new byte[]{(byte)(polls++==0 ? 1 : 0),(byte)0x90,0};
            return new byte[]{(byte)0x90,0};
        }
    }
    static class Progress implements ImageTransfer.Progress {
        final Link link;
        boolean complete;
        int cancelAfter=Integer.MAX_VALUE;
        Progress(Link link) { this.link=link; }
        public void update(String message,int done,int total) { if(message.equals("屏幕刷新完成")) complete=true; }
        public boolean cancelled() { return link.commands.size()>=cancelAfter; }
        public void trace(String text) {}
    }
    static void require(boolean value) { if(!value) throw new AssertionError(); }
    static int count(Link link,int instruction) { int n=0; for(byte[] c:link.commands) if((c[1]&255)==instruction) n++; return n; }
    static byte[] data() { byte[] b=new byte[105984]; for(int i=0;i<b.length;i++) b[i]=(byte)(i*37+11); return b; }
    static void run(Link link,Progress progress) throws Exception { ImageTransfer.send(link,data(),progress,new Clock()); }
    static void payloadRoundTrip(Link link) throws Exception {
        ByteArrayOutputStream raw=new ByteArrayOutputStream(),block=new ByteArrayOutputStream(); int current=0,sub=0;
        for(byte[] c:link.commands) if((c[1]&255)==0xd3) {
            require(c.length<=link.max && (c[4]&255)==c.length-5 && (c[5]&255)==current && (c[6]&255)==sub++);
            block.write(c,7,c.length-7);
            if(c[3]==1) {
                byte[] b=block.toByteArray(); int at=0,length=b[at++]&255;
                if(length==0) { length=18; while(b[at]==0) { length+=255; at++; } length+=b[at++]&255; }
                else length-=17;
                require(at+length+3==b.length && b[b.length-3]==0x11 && b[b.length-2]==0 && b[b.length-1]==0);
                raw.write(b,at,length); current++; sub=0; block.reset();
            }
        }
        require(current==53 && sub==0 && block.size()==0 && Arrays.equals(raw.toByteArray(),data()));
    }
    public static void main(String[] args) throws Exception {
        Link link=new Link(); Progress p=new Progress(link); run(link,p);
        require(p.complete && count(link,0x20)==2 && count(link,0xd3)==476 && count(link,0xd4)==1 && count(link,0xde)==2 && link.timeout==3000); payloadRoundTrip(link);
        require(Protocol.hex(link.commands.get(0)).equals("00200001055265699919"));
        require(Protocol.hex(link.commands.get(478)).equals("F0D4948000"));
        link=new Link(); link.max=64; p=new Progress(link); run(link,p); require(p.complete); payloadRoundTrip(link);
        link=new Link(); link.failStatus=1; p=new Progress(link);
        try { run(link,p); throw new AssertionError(); } catch(IllegalStateException expected) {} require(link.commands.size()==1 && !p.complete);
        link=new Link(); link.failStatus=6; p=new Progress(link);
        try { run(link,p); throw new AssertionError(); } catch(IllegalStateException expected) {} require(link.commands.size()==6 && count(link,0xd4)==0 && !p.complete);
        link=new Link(); link.failAt=6; p=new Progress(link);
        try { run(link,p); throw new AssertionError(); } catch(IOException expected) {} require(link.commands.size()==6 && count(link,0xd4)==0);
        link=new Link(); p=new Progress(link); p.cancelAfter=2;
        try { run(link,p); throw new AssertionError(); } catch(CancellationException expected) {} require(link.commands.size()==2 && count(link,0xd3)==0);
        link=new Link(); p=new Progress(link); p.cancelAfter=480;
        try { run(link,p); throw new AssertionError(); } catch(CancellationException expected) {} require(count(link,0xd4)==1 && count(link,0xde)==1 && !p.complete && link.timeout==3000);
        link=new Link(); link.max=14; p=new Progress(link);
        try { run(link,p); throw new AssertionError(); } catch(IllegalArgumentException expected) {} require(link.commands.isEmpty());
        link=new Link(); link.connected=false; p=new Progress(link);
        try { run(link,p); throw new AssertionError(); } catch(IllegalStateException expected) {} require(link.commands.isEmpty());
        System.out.println("Full transfer tests passed: 9 scenarios; 53 LZO blocks round-trip; auth/D3/D4/DE; no retry after failure/cancel. SIMULATED_TRANSPORT_ONLY");
    }
}
