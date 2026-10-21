package dev.inknfclab;

public final class NciDiscoveryTest {
    private static int checked;
    private static void expect(String packet,boolean expected) {
        if(NciDiscovery.hasQPoll(packet)!=expected)throw new AssertionError("Unexpected Q-Poll verdict: "+packet);
        checked++;
    }
    public static void main(String[] args) {
        expect("210303017101",true);
        expect("21030703000171018001",true);
        expect("2103050271010001",true);
        expect("21030D06000101010201060180018201",false);
        expect("21030100",false);
        expect("210303017001",false);
        expect("210303010071",false); // Frequency 0x71 is not technology 0x71.
        expect("2103030171ff",true);
        expect(null,false);
        expect("",false);
        expect("2103",false);
        expect("2103030171",false); // Truncated.
        expect("21030301710100",false); // Trailing data.
        expect("210303027101",false); // Wrong count.
        expect("210305017101",false); // Wrong payload length.
        expect("2103030171ZZ",false);
        expect("21030301ZZ71",false);
        expect("210303017101 ",false);
        expect("410303017101",false); // Response is not discovery command.
        expect("610303017101",false); // Notification is not discovery command.
        System.out.println("PASS NciDiscoveryTest checks="+checked);
    }
}
