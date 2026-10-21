package dev.inknfclab;

import java.util.Arrays;

public final class ProtocolTest {
    public static void main(String[] args) {
        byte[] snapshot = {1, 0x2a, 2, 10, 0x7d};
        Protocol.validatePolling(snapshot);
        Protocol.validatePolling(new byte[]{0});
        Protocol.validatePolling(Protocol.polling7A());
        require(Protocol.hex(Protocol.polling7A()).equals("012A020A7A"));
        require(Protocol.hex(Protocol.authentication(fromHex("5265699919"))).equals("00200001055265699919"));
        require(Protocol.parseRefreshStatus(fromHex("009000")));
        require(!Protocol.parseRefreshStatus(fromHex("019000")));
        rejectState(() -> Protocol.parseRefreshStatus(fromHex("029000")));
        rejectState(() -> Protocol.requireSuccess(fromHex("6992"), "测试"));
        require(Protocol.hex(snapshot).equals("012A020A7D"));
        reject(() -> Protocol.validatePolling(null));
        reject(() -> Protocol.validatePolling(new byte[]{0, 1}));
        reject(() -> Protocol.validatePolling(new byte[]{1, 0x2a, 4, 10, 0x7a}));
        reject(() -> Protocol.validatePolling(new byte[]{5}));
        byte[] response = fromHex("A00700000006000228B10102B20101C00400000001C104010203049000");
        require(Protocol.deviceInfo(response, fromHex("01020304")).contains("552 × 768 / 4 色"));
        require(Protocol.deviceInfo(response, fromHex("01020304")).contains("兼容检查：通过"));
        require(Protocol.deviceInfo(response, fromHex("FFFFFFFF")).contains("UID 不匹配"));
        require(Protocol.deviceInfo(response, fromHex("FFFFFFFF")).contains("兼容检查：未通过"));
        byte[] realBoard = fromHex("A007F0C72006000228A10700120030FFFFFFB10102B20114B30100C004201EA001C10411223344D107012000000000009000");
        String realInformation = Protocol.deviceInfo(realBoard, fromHex("11223344"));
        require(realInformation.contains("552 × 768 / 4 色"));
        require(realInformation.contains("固件 20 / UID 匹配"));
        require(realInformation.contains("兼容检查：通过"));
        byte[] failure = response.clone();
        failure[failure.length - 2] = 0x69;
        reject(() -> Protocol.deviceInfo(failure, fromHex("01020304")));
        reject(() -> Protocol.deviceInfo(new byte[]{(byte) 0x90, 0}, new byte[4]));
        reject(() -> Protocol.deviceInfo(Arrays.copyOf(response, 4), new byte[4]));
        System.out.println("Protocol tests passed");
    }

    private static byte[] fromHex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int index = 0; index < result.length; index++) result[index] = (byte) Integer.parseInt(value.substring(index * 2, index * 2 + 2), 16);
        return result;
    }
    private static void require(boolean condition) { if (!condition) throw new AssertionError(); }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejection");
    }
    private static void rejectState(Runnable action) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("Expected protocol status rejection");
    }
}
