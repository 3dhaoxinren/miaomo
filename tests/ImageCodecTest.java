package dev.inknfclab;

import java.util.List;

public final class ImageCodecTest {
    public static void main(String[] args) {
        byte[] codes = new byte[ImageCodec.PIXEL_COUNT];
        for (int index = 0; index < codes.length; index++) codes[index] = (byte) ((index / ImageCodec.WIDTH) & 3);
        byte[] packed = ImageCodec.pack(codes);
        require(packed.length == 105984);
        require((packed[0] & 255) == 0xe4);
        require((packed[packed.length - 1] & 255) == 0xe4);
        byte[] literal = ImageCodec.lzoLiteralBlock(new byte[]{0, 1, 2, 3});
        require(Protocol.hex(literal).equals("1500010203110000"));
        byte[] large = new byte[2000];
        for (int index = 0; index < large.length; index++) large[index] = (byte) (index * 17);
        byte[] largeLiteral = ImageCodec.lzoLiteralBlock(large);
        require(largeLiteral.length == 2012);
        require(Protocol.hex(java.util.Arrays.copyOf(largeLiteral, 9)).equals("0000000000000000C5"));
        require(Protocol.hex(java.util.Arrays.copyOfRange(largeLiteral, largeLiteral.length - 3, largeLiteral.length)).equals("110000"));
        List<ImageCodec.Packet> packets = ImageCodec.packets(new byte[ImageCodec.PACKED_LENGTH]);
        require(packets.size() == 476);
        require(Protocol.hex(java.util.Arrays.copyOf(packets.get(0).apdu, 7)).equals("F0D30000FC0000"));
        require(packets.get(7).apdu[3] == 0);
        require(packets.get(8).apdu[3] == 1);
        require((packets.get(475).apdu[5] & 255) == 52);
        require((packets.get(475).apdu[3] & 255) == 1);
        List<ImageCodec.Packet> shortPackets = ImageCodec.packets(new byte[ImageCodec.PACKED_LENGTH], 8);
        for (ImageCodec.Packet packet : shortPackets) {
            require(packet.subpacket < 256);
            require(packet.apdu.length <= 15);
        }
        try {
            ImageCodec.packets(new byte[ImageCodec.PACKED_LENGTH], 7);
            throw new AssertionError("Oversized subpacket index was accepted");
        } catch (IllegalArgumentException expected) {}
        System.out.println("Image codec tests passed");
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError();
    }
}
