package dev.inknfclab;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class Protocol {
    public static final byte[] READ_INFO = {0, (byte) 0xd1, 0, 0, 0};
    public static final byte[] READ_STATUS = {(byte) 0xf0, (byte) 0xde, 0, 0, 1};

    public static final class DeviceInfo {
        public final int width;
        public final int height;
        public final int colors;
        public final int firmware;
        public final boolean compression;
        public final boolean uidMatches;

        private DeviceInfo(int width, int height, int colors, int firmware, boolean compression, boolean uidMatches) {
            this.width = width;
            this.height = height;
            this.colors = colors;
            this.firmware = firmware;
            this.compression = compression;
            this.uidMatches = uidMatches;
        }

        public boolean supportsImageTransfer() {
            return uidMatches && width == 552 && height == 768 && colors == 4 && compression;
        }
    }

    public static String hex(byte[] data) {
        if (data == null) return "null";
        StringBuilder result = new StringBuilder();
        for (byte value : data) result.append(String.format(Locale.ROOT, "%02X", value & 255));
        return result.toString();
    }

    public static byte[] polling7A() {
        return new byte[]{1, 0x2a, 2, 10, 0x7a};
    }

    public static void validatePolling(byte[] payload) {
        if (payload == null || payload.length == 0 || (payload[0] & 255) > 4)
            throw new IllegalArgumentException("无有效轮询配置，停止设置");
        int offset = 1;
        for (int frame = 0; frame < (payload[0] & 255); frame++) {
            if (offset + 3 > payload.length) throw new IllegalArgumentException("轮询 TLV 头截断");
            int length = payload[offset + 1] & 255;
            if (length < 2 || offset + length + 2 > payload.length)
                throw new IllegalArgumentException("轮询 TLV 长度错误");
            offset += length + 2;
        }
        if (offset != payload.length) throw new IllegalArgumentException("轮询配置尾部数据异常");
    }

    public static String deviceInfo(byte[] response, byte[] tagUid) {
        DeviceInfo info = parseDeviceInfo(response, tagUid);
        return "D1 读取成功\n" + info.width + " × " + info.height + " / " + info.colors + " 色"
                + "\n固件 " + info.firmware + " / UID " + (info.uidMatches ? "匹配" : "不匹配")
                + "\n刷图配置兼容检查：" + (info.supportsImageTransfer() ? "通过" : "未通过");
    }

    public static DeviceInfo parseDeviceInfo(byte[] response, byte[] tagUid) {
        if (response == null || response.length < 2) throw new IllegalArgumentException("D1 响应没有状态字");
        if (response[response.length - 2] != (byte) 0x90 || response[response.length - 1] != 0)
            throw new IllegalArgumentException("D1 状态字 " + hex(Arrays.copyOfRange(response, response.length - 2, response.length)));
        Map<Integer, byte[]> fields = new LinkedHashMap<>();
        int end = response.length - 2;
        for (int offset = 0; offset < end;) {
            if (offset + 2 > end) throw new IllegalArgumentException("D1 TLV 头截断");
            int tag = response[offset++] & 255;
            int length = response[offset++] & 255;
            if (fields.containsKey(tag) || offset + length > end)
                throw new IllegalArgumentException("D1 TLV 重复或长度异常");
            fields.put(tag, Arrays.copyOfRange(response, offset, offset + length));
            offset += length;
        }
        int[] tags = {0xa0, 0xb1, 0xb2, 0xc0, 0xc1};
        int[] lengths = {7, 1, 1, 4, 4};
        for (int index = 0; index < tags.length; index++) {
            byte[] field = fields.get(tags[index]);
            if (field == null || field.length != lengths[index])
                throw new IllegalArgumentException("D1 字段缺失或格式不符: " + Integer.toHexString(tags[index]));
        }
        byte[] geometry = fields.get(0xa0);
        int width = (geometry[5] & 255) * 256 + (geometry[6] & 255);
        int height = (geometry[3] & 255) * 256 + (geometry[4] & 255);
        int colors = fields.get(0xb1)[0] & 255;
        if (width == 552 && height == 1536 && colors == 2) { height = 768; colors = 4; }
        boolean uidMatches = Arrays.equals(tagUid, fields.get(0xc1));
        return new DeviceInfo(width, height, colors, fields.get(0xb2)[0] & 255,
                fields.get(0xc0)[3] != 0, uidMatches);
    }

    public static byte[] authentication(byte[] pin) {
        if (pin == null || pin.length != 5) throw new IllegalArgumentException("认证值必须为5字节");
        byte[] command = {0, 0x20, 0, 1, 5, 0, 0, 0, 0, 0};
        System.arraycopy(pin, 0, command, 5, 5);
        return command;
    }

    public static void requireSuccess(byte[] response, String context) {
        if (response == null || response.length < 2 || response[response.length - 2] != (byte) 0x90 || response[response.length - 1] != 0) {
            String status = response == null || response.length < 2 ? "缺失" : hex(Arrays.copyOfRange(response, response.length - 2, response.length));
            String meaning = status.equals("6992") ? "（数据解压失败）" : status.equals("6993") ? "（解压长度错误）"
                    : status.equals("68CA") ? "（低电压报警）" : status.equals("68CB") ? "（屏幕刷新超时）" : "";
            throw new IllegalStateException(context + "失败，状态字：" + status + meaning);
        }
    }

    public static boolean parseRefreshStatus(byte[] response) {
        requireSuccess(response, "刷新状态查询");
        if (response.length != 3 || (response[0] != 0 && response[0] != 1))
            throw new IllegalStateException("设备返回未知刷新状态");
        return response[0] == 0;
    }

    private Protocol() {}
}
