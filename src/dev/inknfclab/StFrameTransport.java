package dev.inknfclab;

import java.io.IOException;
import java.util.Arrays;

/** ST bridge (OID 01 / 15,13) and native custom-polling (OID 1E,1D).
 * Native mapping follows ST's stpropnci_prop_st.cc. Only reads negotiate a route.
 */
final class StFrameTransport {
    interface Wire { byte[] exchange(int oid,byte[] payload)throws Exception; }
    private final Wire wire;
    private String channel="bridge";
    private boolean pinned,readConfirmed;
    StFrameTransport(Wire wire){this.wire=wire;}
    String channel(){return channel;}
    void restoreChannel(String saved)throws IOException{
        if(!"bridge".equals(saved)&&!"native".equals(saved))throw new IOException("Unknown ST snapshot transport: "+saved);
        if(pinned&&!channel.equals(saved))throw new IOException("Cannot switch ST transport while recovery is pending");
        channel=saved;pinned=true;readConfirmed=true;
    }
    void pin()throws IOException{
        if(!readConfirmed)throw new IOException("ST snapshot channel must be read and validated before a setter");
        pinned=true;
    }
    byte[] exchange(byte[] logical)throws Exception{
        if(logical==null||logical.length==0)throw new IOException("Missing ST logical command");
        int op=logical[0]&255;
        if(op!=0x15&&op!=0x13)throw new IOException("Unsupported ST polling operation");
        if(op==0x15&&logical.length!=1)throw new IOException("ST frame query takes no payload");
        if(op==0x13&&!pinned)throw new IOException("ST frame setter requires a pinned recovery snapshot");
        if(channel.equals("native"))return nativeExchange(op,logical);
        byte[] response=wire.exchange(1,logical.clone());
        if(op==0x15&&!pinned&&Arrays.equals(response,new byte[]{0x15,0x0b})){
            // OID01/15 is a software-extension request on supported ST stacks.
            // Ask the documented native getter first; do not enable passthrough or write RF config.
            byte[] nativeResponse=wire.exchange(0x1e,new byte[0]);
            byte[] normalized=normalize(op,nativeResponse);
            if(normalized.length>=3&&normalized[1]==0){
                validateRead(normalized);channel="native";return normalized;
            }
            return normalized; // Nonzero stays a failure in StPollingTransaction.
        }
        if(op==0x15&&response!=null&&response.length>=3&&response[0]==0x15&&response[1]==0)validateRead(response);
        return response;
    }
    private byte[] nativeExchange(int op,byte[] logical)throws Exception{
        byte[] response=normalize(op,wire.exchange(op==0x15?0x1e:0x1d,Arrays.copyOfRange(logical,1,logical.length)));
        if(op==0x15&&response.length>=3&&response[1]==0)validateRead(response);
        return response;
    }
    private void validateRead(byte[] response)throws IOException{
        byte[] frames=Arrays.copyOfRange(response,2,response.length);
        try{Protocol.validatePolling(frames);}catch(IllegalArgumentException malformed){throw new IOException("ST native/bridge polling snapshot is malformed",malformed);}
        if(frames.length>250)throw new IOException("ST polling snapshot exceeds restore bound");
        readConfirmed=true;
    }
    static byte[] normalize(int op,byte[] nativeResponse)throws IOException{
        if(nativeResponse==null||nativeResponse.length==0)throw new IOException("ST native polling response is empty");
        byte[] result=new byte[nativeResponse.length+1];result[0]=(byte)op;System.arraycopy(nativeResponse,0,result,1,nativeResponse.length);return result;
    }
}
